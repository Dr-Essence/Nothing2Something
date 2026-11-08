package com.dressence.nothinglauncherplus.systemui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.app.Notification
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.dressence.nothinglauncherplus.xposed.ConfigStore
import com.dressence.nothinglauncherplus.xposed.Hooks
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.WeakHashMap

/**
 * An OLED saver for the status bar: its content moves a few pixels on a schedule, so no
 * pixel stays lit in the same place for hours, and it can fade out after a set time.
 *
 * How it was found (Nothing's System UI 16, decompiled): everything the bar shows sits in
 * one view, `status_bar_contents` (Nothing's `NTStatusBarContentExt`). System UI only ever
 * sets its padding; neither System UI nor Nothing moves or fades it (they animate the views
 * inside it). So its translation and alpha are free for the saver: moving them never fights
 * System UI's own animations.
 *
 * Never past the edge: before each move the saver measures the room the visible content
 * really has inside the bar (the union of what is drawn), and limits the move to it. That
 * makes it work on any layout, whatever device or row setup.
 *
 * Back on screen (after hiding): a new notification (System UI's NotificationListener), the
 * shade starting to be pulled (ShadeExpansionStateManager) or the screen turning on; then it
 * hides again after the set time ("instant": after [INSTANT_MS]). A tap on the bar shows it for
 * [TAP_MS], whatever the set time (PhoneStatusBarView.onInterceptTouchEvent, which Android
 * calls for every finger down on the bar, also while its content is faded out: the saver only
 * watches it; System UI handles the touch as always).
 *
 * Full-screen apps (a game, a video): the bar is not shown at all, and a swipe from the top
 * brings it back for a moment ("transient", Android's own immersive mode). Faded out by the
 * saver, that swipe showed an empty strip and a tap was needed on top (reported on 0.54.6).
 * System UI hears of it on its status bar state (StatusBarModePerDisplayRepositoryImpl:
 * showTransient when the swipe brings it back, abortTransient or clearTransient when it goes
 * again, all on the main thread); the saver shows the bar for exactly that time and starts
 * the set time again after. Apps that keep their bar keep the set time as before.
 *
 * Mistake recorded (0.54.0): every notification *update* woke the bar too. System UI's
 * listener hears a download's progress as "posted" every second or two, so the bar came back
 * every 2 s on the home screen while a download ran (seen on the phone). Now only a
 * notification it has not seen, or an update that alerts again (a new message), wakes it;
 * updates that are ongoing or "alert only once" (progress, media) do not. The shade wakes it
 * when a pull starts, not on each frame of it.
 *
 * The settings are read once, when System UI starts (restart System UI after a change).
 */
internal object StatusBarSaver {

    private const val SYSUI = "com.android.systemui"
    private const val CLS_BAR = "com.android.systemui.statusbar.phone.PhoneStatusBarView"
    private const val CLS_LISTENER = "com.android.systemui.statusbar.NotificationListener"
    private const val CLS_SHADE = "com.android.systemui.shade.ShadeExpansionStateManager"
    private const val CLS_MODE =
        "com.android.systemui.statusbar.data.repository.StatusBarModePerDisplayRepositoryImpl"
    /** The repository's CommandQueue listener (a Kotlin object inside it). */
    private const val CLS_MODE_LISTENER = "$CLS_MODE\$commandQueueCallback\$1"

    /** How long "instant" leaves the bar on after it comes back. */
    private const val INSTANT_MS = 5_000L
    /** How long a tap on the bar shows it, whatever the hide setting. */
    private const val TAP_MS = 10_000L
    private const val FADE_MS = 400L
    private const val MOVE_MS = 800L

    /** The positions the content goes round: centre, then the eight around it. */
    private val RING = listOf(0 to 0, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1,
        0 to -1, 1 to -1)

    @Volatile private var shiftMs = 0L
    @Volatile private var shiftDp = 0
    /** -1 never hides, 0 hides at once, otherwise after this many ms. */
    @Volatile private var hideMs = -1L

    private val main = Handler(Looper.getMainLooper())

    /** Notifications the bar has seen (by key): an update of one of them is not "new". */
    private val seen = object : LinkedHashMap<String, Boolean>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) =
            size > 500
    }
    private var lastFraction = 0f
    /** Whether the bar is brought back over a full-screen app right now (main thread). */
    private var transientShown = false
    /** When each wake reason was last logged, to log it at most every 10 s. */
    private val logged = HashMap<String, Long>()
    private val savers = WeakHashMap<View, Saver>()
    private var screenReceiver: BroadcastReceiver? = null

    @JvmStatic
    fun install(cl: ClassLoader, store: ConfigStore) {
        val config = store.get()
        shiftMs = config.statusBarShiftSeconds * 1000L
        shiftDp = config.statusBarShiftDp
        hideMs = if (config.statusBarHideMinutes < 0) -1 else config.statusBarHideMinutes * 60_000L
        if ((shiftMs <= 0 || shiftDp <= 0) && hideMs < 0) return
        if (!CrashGuard("saver", "status bar saver").allows(store.stamp())) return

        val bar = XposedHelpers.findClass(CLS_BAR, cl)
        XposedBridge.hookAllMethods(bar, "onAttachedToWindow", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                guarded { attach(param.thisObject as ViewGroup) }
            }
        })
        XposedBridge.hookAllMethods(bar, "onDetachedFromWindow", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                guarded { savers.remove(param.thisObject as View)?.stop() }
            }
        })
        if (hideMs >= 0) {
            XposedBridge.hookAllMethods(bar, "onInterceptTouchEvent", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val event = param.args.firstOrNull() as? MotionEvent ?: return
                    if (event.actionMasked != MotionEvent.ACTION_DOWN) return
                    guarded {
                        savers[param.thisObject as View]?.wake(TAP_MS)
                        logWake("tap")
                    }
                }
            })
            // New notifications arrive on System UI's listener thread: back to the main one.
            XposedBridge.hookAllMethods(XposedHelpers.findClass(CLS_LISTENER, cl),
                "onNotificationPosted", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val sbn = param.args.firstOrNull() as? StatusBarNotification
                            ?: return
                        main.post { guarded { if (isNew(sbn)) wakeAll("notification") } }
                    }
                })
            XposedBridge.hookAllMethods(XposedHelpers.findClass(CLS_SHADE, cl),
                "onPanelExpansionChanged", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val fraction = param.args.firstOrNull() as? Float ?: return
                        main.post {
                            guarded {
                                // A pull starting from closed, not every frame of it.
                                if (lastFraction == 0f && fraction > 0f) wakeAll("shade")
                                lastFraction = fraction
                            }
                        }
                    }
                })
        }
        if (hideMs >= 0) watchTransient(cl)
        Hooks.log("status bar saver on: move every ${shiftMs / 1000} s by $shiftDp dp, hide " +
            if (hideMs < 0) "never" else "after ${hideMs / 1000} s")
    }

    /**
     * The bar brought back over a full-screen app: shown while it is there. After each of
     * System UI's own transient calls, its repository's value says whether it is shown now
     * (System UI itself decides which calls are about the status bar of this display).
     */
    private fun watchTransient(cl: ClassLoader) {
        val repository = XposedHelpers.findClassIfExists(CLS_MODE, cl)
        val listener = XposedHelpers.findClassIfExists(CLS_MODE_LISTENER, cl)
        if (repository == null || listener == null) {
            Hooks.log("status bar saver: no transient bar state; full-screen swipes left as is")
            return
        }
        val after = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                guarded {
                    val repo = if (repository.isInstance(param.thisObject)) param.thisObject
                    else XposedHelpers.getSurroundingThis(param.thisObject)
                    val flow = XposedHelpers.getObjectField(repo, "_isTransientShown")
                    val shown = XposedHelpers.callMethod(flow, "getValue") == true
                    if (shown != transientShown) {
                        transientShown = shown
                        if (shown) logWake("full-screen swipe")
                        savers.values.toList().forEach { if (shown) it.hold() else it.wake() }
                    }
                }
            }
        }
        val hooked = XposedBridge.hookAllMethods(listener, "showTransient", after).size +
            XposedBridge.hookAllMethods(listener, "abortTransient", after).size +
            XposedBridge.hookAllMethods(repository, "showTransient", after).size +
            XposedBridge.hookAllMethods(repository, "clearTransient", after).size
        Hooks.log("status bar saver: full-screen swipes watched ($hooked hooks)")
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Hooks.log("status bar saver: $t")
        }
    }

    private fun attach(bar: ViewGroup) {
        val id = bar.resources.getIdentifier("status_bar_contents", "id", SYSUI)
        val contents = if (id == 0) null else bar.findViewById<ViewGroup>(id)
        if (contents == null) {
            Hooks.log("status bar saver: no status_bar_contents; left as is")
            return
        }
        savers.getOrPut(bar) { Saver(contents) }.start()
        if (hideMs >= 0 && screenReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) =
                    guarded { wakeAll("screen on") }
            }
            bar.context.applicationContext.registerReceiver(receiver,
                IntentFilter(Intent.ACTION_SCREEN_ON), Context.RECEIVER_NOT_EXPORTED)
            screenReceiver = receiver
        }
    }

    /**
     * True for a notification worth showing the bar for: one not seen before, or an update
     * that alerts again; not progress or media updates (ongoing, or "alert only once").
     */
    private fun isNew(sbn: StatusBarNotification): Boolean {
        val first = seen.put(sbn.key, true) == null
        if (first) return true
        val flags = sbn.notification.flags
        return flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_ONLY_ALERT_ONCE) == 0
    }

    private fun wakeAll(reason: String) {
        logWake(reason)
        savers.values.toList().forEach { it.wake() }
    }

    /** Logs why the bar was shown, each reason at most every 10 s. */
    private fun logWake(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - (logged[reason] ?: -60_000L) >= 10_000L) {
            logged[reason] = now
            Hooks.log("status bar saver: shown again ($reason)")
        }
    }

    /** The saver of one status bar. Main thread only. */
    private class Saver(private val contents: ViewGroup) {

        private var step = 0
        private var hidden = false

        private val move = object : Runnable {
            override fun run() {
                guarded { shift() }
                main.postDelayed(this, shiftMs)
            }
        }

        private val hide = Runnable {
            guarded {
                hidden = true
                contents.animate().alpha(0f).setDuration(FADE_MS).start()
            }
        }

        fun start() {
            stop()
            if (shiftMs > 0 && shiftDp > 0) main.postDelayed(move, shiftMs)
            wake()
        }

        fun stop() {
            main.removeCallbacks(move)
            main.removeCallbacks(hide)
        }

        /**
         * Shows the bar (if hidden) and starts the time until it hides again: [showMs] if
         * given (a tap), otherwise the set time.
         */
        fun wake(showMs: Long = -1) {
            if (hideMs < 0) return
            hold()
            if (transientShown) return // Over a full-screen app: stays until it goes.
            val delay = when {
                showMs >= 0 -> showMs
                hideMs == 0L -> INSTANT_MS
                else -> hideMs
            }
            main.postDelayed(hide, delay)
        }

        /** Shows the bar (if hidden) and keeps it until [wake] starts the set time again. */
        fun hold() {
            main.removeCallbacks(hide)
            if (hidden) {
                hidden = false
                contents.animate().alpha(1f).setDuration(FADE_MS).start()
            }
        }

        /** The next position on the ring, as far as the room inside the bar allows. */
        private fun shift() {
            if (!contents.isAttachedToWindow || contents.width == 0) return
            step = (step + 1) % RING.size
            val (ux, uy) = RING[step]
            val distance = shiftDp * contents.resources.displayMetrics.density
            val room = room()
            val dx = (ux * distance).coerceIn(-room[0], room[2])
            val dy = (uy * distance).coerceIn(-room[1], room[3])
            contents.animate().translationX(dx).translationY(dy).setDuration(MOVE_MS).start()
        }

        /**
         * The room the visible content has inside the bar on each side (left, top, right,
         * bottom), measured at its resting place: the union of the visible views that draw
         * (leaves, and the notification area, whose overlay may hold the date).
         */
        private fun room(): FloatArray {
            val base = IntArray(2)
            contents.getLocationInWindow(base)
            val ox = base[0] - contents.translationX
            val oy = base[1] - contents.translationY
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            val at = IntArray(2)
            fun visit(v: View) {
                if (v.visibility != View.VISIBLE || v.alpha <= 0f || v.width == 0) return
                val group = v as? ViewGroup
                val leaf = group == null || group.childCount == 0 || isArea(v)
                if (!leaf) {
                    for (i in 0 until group!!.childCount) visit(group.getChildAt(i))
                    return
                }
                v.getLocationInWindow(at)
                left = minOf(left, at[0] - ox)
                top = minOf(top, at[1] - oy)
                right = maxOf(right, at[0] - ox + v.width)
                bottom = maxOf(bottom, at[1] - oy + v.height)
            }
            for (i in 0 until contents.childCount) visit(contents.getChildAt(i))
            if (left == Float.MAX_VALUE) return FloatArray(4)
            return floatArrayOf(
                left.coerceAtLeast(0f),
                top.coerceAtLeast(0f),
                (contents.width - right).coerceAtLeast(0f),
                (contents.height - bottom).coerceAtLeast(0f))
        }

        private fun isArea(v: View): Boolean = v.id != View.NO_ID &&
            runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull() ==
            "notification_icon_area"
    }
}
