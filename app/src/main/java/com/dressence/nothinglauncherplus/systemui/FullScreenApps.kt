package com.dressence.nothinglauncherplus.systemui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.dressence.nothinglauncherplus.Config
import com.dressence.nothinglauncherplus.xposed.ConfigStore
import com.dressence.nothinglauncherplus.xposed.Hooks
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.util.WeakHashMap

/**
 * Full-screen apps: picked apps hide the status bar and use its space, also beside the camera,
 * either always or while the OLED saver has faded the bar out ([StatusBarSaver]). A swipe from
 * the top shows the bar for a moment, as in a game.
 *
 * How (and why this way): Android lets every app do exactly this itself, with
 * `WindowInsetsController.hide(statusBars())` and "show transient bars by swipe" (immersive
 * mode). The module does that on the app's behalf, inside the app (so the app has to be in the
 * module's LSPosed scope, like the 120 fps games). It was checked first whether the window
 * manager could hide the bar for an app from outside (`InsetsPolicy`, phone's services.jar):
 * the window that controls the bar also has to slide its surface away and report it, and only
 * the app's own side does that; faking it inside system_server would mean running Android's
 * insets machinery there by hand, so it was not done.
 *
 * The camera: in portrait the status bar is almost exactly the camera's strip (113 px against
 * 106 px on the CMF Phone 1), and Android keeps a window with the default cutout mode out of
 * that strip once the bar is gone (`WindowLayout.computeFrames`). So, while the bar is hidden,
 * a window left at the default mode asks for SHORT_EDGES (beside the camera on the short
 * edges, Android's own mode for this); an app that chose a mode itself keeps it. Both are put
 * back as they were when the bar comes back.
 *
 * The OLED saver lives in System UI. It sends [ACTION_STATE] when it fades the bar out or
 * brings it back; apps accept it only from a sender holding the status bar permission (System
 * UI), and ask for the current state with [ACTION_QUERY] when they start.
 */
internal object FullScreenApps {

    const val ACTION_STATE = "com.dressence.nothinglauncherplus.STATUS_BAR_FADED"
    const val ACTION_QUERY = "com.dressence.nothinglauncherplus.STATUS_BAR_QUERY"
    const val EXTRA_FADED = "faded"
    /** Held by System UI: only it can tell apps the bar faded out. */
    const val SENDER_PERMISSION = "android.permission.STATUS_BAR_SERVICE"
    private const val SYSUI = "com.android.systemui"

    /** What a window was before the module hid its bar, to put back. */
    private class Saved(val cutoutMode: Int, val behavior: Int)

    private lateinit var store: ConfigStore
    private lateinit var pkg: String
    /** Activities on screen now (resumed); main thread only. */
    private val resumed = WeakHashMap<Activity, Boolean>()
    /** Windows whose bar the module hid; main thread only. */
    private val hidden = WeakHashMap<Window, Saved>()
    /** The OLED saver's state, from System UI. */
    @Volatile private var faded = false
    private var listening = false

    /**
     * In an app's process (every app in the module's scope): the list is checked each time an
     * activity comes to the front, so an app picked later only needs to be opened again.
     */
    fun install(packageName: String, configStore: ConfigStore) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        store = configStore
        pkg = packageName
        XposedHelpers.findAndHookMethod(Activity::class.java, "onResume", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) = guarded {
                val activity = param.thisObject as Activity
                resumed[activity] = true
                store.reloadIfChanged() // the mode and list are read again when an app returns
                if (store.get().fullScreenApps.contains(pkg)) listen(activity.applicationContext)
                apply(activity)
            }
        })
        XposedHelpers.findAndHookMethod(Activity::class.java, "onPause", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) = guarded {
                resumed.remove(param.thisObject)
            }
        })
        // An app that shows its bar again by itself (on focus, after a dialog) is hidden again.
        XposedHelpers.findAndHookMethod(Activity::class.java, "onWindowFocusChanged",
            Boolean::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) = guarded {
                    if (param.args[0] == true) apply(param.thisObject as Activity)
                }
            })
        if (configStore.get().fullScreenApps.contains(packageName)) {
            Hooks.log("full-screen apps: $packageName")
        }
    }

    /** Listens to the OLED saver (once per process) and asks System UI where it is now. */
    private fun listen(context: Context) {
        if (listening) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = guarded {
                val now = intent.getBooleanExtra(EXTRA_FADED, false)
                if (now == faded) return@guarded
                faded = now
                resumed.keys.toList().forEach { apply(it) }
            }
        }
        context.registerReceiver(receiver, IntentFilter(ACTION_STATE), SENDER_PERMISSION, null,
            Context.RECEIVER_EXPORTED)
        listening = true // only once registered: a failure is tried again on the next resume
        context.sendBroadcast(Intent(ACTION_QUERY).setPackage(SYSUI))
    }

    /** Hides or shows this activity's status bar as the setting and the saver say now. */
    private fun apply(activity: Activity) {
        val config = store.get()
        val want = config.fullScreenApps.contains(pkg) && when (config.fullScreenMode) {
            Config.FULL_SCREEN_ALWAYS -> true
            Config.FULL_SCREEN_SAVER -> faded
            else -> false
        }
        val window = activity.window ?: return
        val saved = hidden[window]
        if (!want && saved == null) return // not picked, or already as the app left it
        val controller = window.insetsController ?: return
        if (want) {
            if (saved == null) {
                val attrs = window.attributes
                hidden[window] = Saved(attrs.layoutInDisplayCutoutMode, controller.systemBarsBehavior)
                if (attrs.layoutInDisplayCutoutMode ==
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT) {
                    attrs.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    window.attributes = attrs
                }
            }
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsets.Type.statusBars())
        } else if (saved != null) {
            hidden.remove(window)
            val attrs = window.attributes
            if (attrs.layoutInDisplayCutoutMode != saved.cutoutMode) {
                attrs.layoutInDisplayCutoutMode = saved.cutoutMode
                window.attributes = attrs
            }
            controller.systemBarsBehavior = saved.behavior
            controller.show(WindowInsets.Type.statusBars())
        }
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Hooks.log("full-screen apps: $t")
        }
    }

    // ---------------------------------------------------------------------------------------
    // System UI side, called by the OLED saver
    // ---------------------------------------------------------------------------------------

    @Volatile private var sysuiFaded = false
    private var answering = false

    /** In System UI: tells the apps the bar faded out (true) or came back (false). */
    fun barFaded(context: Context, now: Boolean) {
        sysuiFaded = now
        send(context)
    }

    /** In System UI: answers apps that start and ask where the saver is. */
    fun answerQueries(context: Context) {
        if (answering) return
        answering = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = guarded { send(c) }
        }
        context.registerReceiver(receiver, IntentFilter(ACTION_QUERY), Context.RECEIVER_EXPORTED)
    }

    private fun send(context: Context) = guarded {
        context.sendBroadcast(Intent(ACTION_STATE).putExtra(EXTRA_FADED, sysuiFaded)
            .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY))
    }
}
