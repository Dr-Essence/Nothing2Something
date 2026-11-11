package com.dressence.nothinglauncherplus.systemui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.dressence.nothinglauncherplus.Config
import com.dressence.nothinglauncherplus.xposed.ConfigStore
import com.dressence.nothinglauncherplus.xposed.Hooks
import com.dressence.nothinglauncherplus.xposed.NdotFont
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * The status bar on two rows: everything on the top row as Nothing lays it out, the
 * notifications (and the date, in full, when the date is on) on a row under it.
 *
 * How it was found (Nothing's System UI 16, decompiled):
 *
 * 1. **Height.** Every part of System UI takes the bar's height from Android's
 *    `SystemBarUtils`: `getStatusBarHeight(Context)` for the bar, the shade and heads-up,
 *    `getStatusBarHeightForRotation` for the window's insets, which apps lay out under.
 *    Adding the second row's height there makes all of it follow, through Nothing's own
 *    updates.
 * 2. **Rows.** The clock and the notification area already share one row container of
 *    Nothing's (`status_bar_start_side_except_heads_up`). Turned vertical, the clock keeps
 *    the bar's original height on top (it centres its text in its box, so it stays exactly
 *    where it was) and the notification area sits under it.
 * 3. **Nothing moves to another parent.** System UI rebinds its notification icons on every
 *    notification; views moved out of their containers are what crash other dual status bar
 *    mods. Only sizes, an orientation and paddings are set, when the bar is built and when it
 *    rotates: never after System UI's own layout passes.
 *
 * Mistake recorded (0.53.0): the values saved per view were stored in the view's tags under
 * an Android-owned id, which Android refuses ("The key must be an application-specific
 * resource id"). They live in this object's weak maps instead.
 *
 * The settings are read once, when System UI starts: a change needs a System UI restart,
 * like the other status bar options.
 */
internal object DualStatusBar {

    private const val CLS_UTILS = "com.android.internal.policy.SystemBarUtils"
    private const val CLS_BAR = "com.android.systemui.statusbar.phone.PhoneStatusBarView"
    private const val CLS_CLOCK = "com.android.systemui.statusbar.policy.Clock"

    /** The gap left between the clock's text and the second row, in clock text heights. */
    private const val GAP = 0.3f

    // ---- the settings, as read when System UI started ------------------------------------

    @Volatile private var on = false
    @Volatile private var landscape = false
    @Volatile private var showDate = false
    @Volatile private var monthNumber = false
    @Volatile private var withYear = false
    @Volatile private var dateAtEnd = false
    @Volatile private var notificationsAtEnd = false
    @Volatile private var ndot = false

    /** SystemBarUtils.getStatusBarHeight, to ask its original answer (without our row). */
    private var heightMethod: Method? = null

    /** The clock's typeface once a bar is built: its text height sets the overlap. */
    @Volatile private var clockFace: Typeface? = null

    /*
     * What the module keeps for System UI's views. Not in the views' tags (see the mistake
     * above): weak maps, so a view System UI drops is let go. Main thread only.
     */
    private val saved = WeakHashMap<View, Saved>()
    /** The date shown for each bar, by its row container. */
    private val dates = WeakHashMap<View, DateRow>()
    /** Notification areas measured across the whole bar (Notifications at the end): their bar. */
    // The bar weakly too: it holds the area (its key), and a WeakHashMap holds values
    // strongly, so a strong bar kept its entry, and each status bar System UI inflated again
    // (a theme, font size or density change) was kept whole (0.70.14 audit).
    private val wide = WeakHashMap<View, java.lang.ref.WeakReference<ViewGroup>>()

    @JvmStatic
    fun install(cl: ClassLoader, store: ConfigStore) {
        val config: Config = store.get()
        if (!config.dualStatusBar || !CrashGuard("dual", "dual status bar").allows(store.stamp())) {
            return
        }
        landscape = config.dualStatusBarLandscape
        showDate = config.statusBarDate
        monthNumber = config.dualStatusBarMonthNumber
        withYear = config.dualStatusBarYear
        dateAtEnd = config.dualStatusBarDateAtEnd
        notificationsAtEnd = config.dualStatusBarNotificationsAtEnd
        ndot = config.ndotStatusBar
        on = true

        // 1. The height, where every part of System UI reads it.
        val utils = XposedHelpers.findClass(CLS_UTILS, null)
        val height = XposedHelpers.findMethodExact(utils, "getStatusBarHeight", Context::class.java)
        heightMethod = height
        XposedBridge.hookMethod(height, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val ctx = param.args[0] as Context
                grow(param, ctx, isLandscape(ctx))
            }
        })
        XposedHelpers.findAndHookMethod(utils, "getStatusBarHeightForRotation",
            Context::class.java, Int::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val rotation = param.args[1] as Int
                    grow(param, param.args[0] as Context,
                        rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)
                }
            })

        // 2. The rows, when the bar is built and when it rotates.
        val arrange = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                runCatching { arrange(param.thisObject as ViewGroup) }
                    .onFailure { Hooks.log("dual status bar: $it") }
            }
        }
        val bar = XposedHelpers.findClass(CLS_BAR, cl)
        XposedBridge.hookAllMethods(bar, "onFinishInflate", arrange)
        // Notifications at the end: the notification area measures as wide as the whole bar.
        XposedHelpers.findAndHookMethod(FrameLayout::class.java, "onMeasure",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val area = param.thisObject as View
                    val bar = wide[area]?.get() ?: return
                    runCatching {
                        fullWidth(bar)?.let {
                            param.args[0] = View.MeasureSpec.makeMeasureSpec(it,
                                View.MeasureSpec.EXACTLY)
                        }
                        // The icon container is put into the area by System UI after the bar is
                        // built (after onFinishInflate, where [arrange] runs): set at its end
                        // here, before this measure, so the same layout pass places it. Seen on
                        // the phone: set only in [arrange], it was missing and the icons
                        // stayed at the start.
                        endGravity(area)
                    }
                }
            })
        XposedBridge.hookAllMethods(bar, "onConfigurationChanged", arrange)

        // 3. The date follows the clock: its minute, its colour (light or dark over an app).
        val refresh = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                runCatching { dates.values.toList().forEach { it.refresh() } }
                    .onFailure { Hooks.log("dual status bar date: $it") }
            }
        }
        val clock = XposedHelpers.findClass(CLS_CLOCK, cl)
        XposedBridge.hookAllMethods(clock, "updateClock", refresh)
        XposedBridge.hookAllMethods(clock, "onDarkChanged", refresh)
        // ...and is shown only while the clock is. System UI hides the clock with GONE on the
        // lock screen, while dozing and with the shade closed (CollapsedStatusBarFragment
        // .clockHiddenMode). Mistake recorded (0.54.10, DebuG24): the date stayed drawn, and
        // with the clock's row gone the second row rose to the top, so on the landscape lock
        // screen its date showed cut in half along the screen's top edge. TextView's own
        // onVisibilityChanged (the clock is one) tells when to draw it again or not.
        XposedBridge.hookAllMethods(TextView::class.java, "onVisibilityChanged",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!clock.isInstance(param.thisObject)) return
                    runCatching {
                        dates.values.toList().forEach { it.clockChanged(param.thisObject as View) }
                    }.onFailure { Hooks.log("dual status bar date: $it") }
                }
            })
        Hooks.log("dual status bar on" + if (landscape) ", landscape too" else "")
    }

    /** True when the date is shown under [clock] instead of before the time (Clock asks). */
    @JvmStatic
    fun dateBelow(clock: View): Boolean =
        on && showDate && (landscape || !isLandscape(clock.context))

    private fun isLandscape(ctx: Context): Boolean =
        ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ---- the height --------------------------------------------------------------------

    /**
     * The two rows' measures for a bar [base] pixels high (its height without our row).
     * The second row is one icon row high, but the bar already has room under the clock's
     * text: the row starts there ([overlap]), leaving a gap of [GAP] text heights, and the
     * bar grows only by the rest ([growth]).
     */
    private class Rows(val row: Int, val overlap: Int) {
        val growth: Int get() = row - overlap
    }

    private fun rows(ctx: Context, base: Int): Rows? {
        val row = dimen(ctx, "status_bar_system_icons_height")
        val clockSize = dimen(ctx, "status_bar_clock_size")
        if (row <= 0 || clockSize <= 0) return null
        val text = textHeight(ctx, clockSize)
        // The clock is centred in the original bar: half of what its text leaves is under it.
        val spareUnderClock = (base - text) / 2f
        // The date (same text) is centred in its row: half of what it leaves is above it.
        val spareAboveDate = (row - text) / 2f
        val overlap = (spareUnderClock + spareAboveDate - text * GAP).roundToInt()
        return Rows(row, overlap.coerceIn(0, row))
    }

    /** The clock's line height: its size in its typeface (NDot when the module sets it). */
    private fun textHeight(ctx: Context, size: Int): Float {
        val paint = Paint()
        paint.textSize = size.toFloat()
        paint.typeface = (if (ndot) NdotFont.get(ctx) else null) ?: clockFace
        val metrics = paint.fontMetrics
        return metrics.descent - metrics.ascent
    }

    private fun dimen(ctx: Context, name: String): Int = try {
        val id = ctx.resources.getIdentifier(name, "dimen", Config.SYSTEMUI_PKG)
        if (id == 0) 0 else ctx.resources.getDimensionPixelSize(id)
    } catch (e: RuntimeException) {
        0 // Not System UI's resources: leave the height alone.
    }

    /** Adds the second row's growth to a height System UI asked for. */
    private fun grow(param: XC_MethodHook.MethodHookParam, ctx: Context, land: Boolean) {
        if (!on || (land && !landscape)) return
        val base = param.result as? Int ?: return
        val rows = rows(ctx, base) ?: return
        param.result = base + rows.growth
    }

    /** The bar's height without our row: SystemBarUtils' own answer. */
    private fun baseHeight(ctx: Context): Int? = runCatching {
        XposedBridge.invokeOriginalMethod(heightMethod, null, arrayOf(ctx)) as Int
    }.getOrNull()

    // ---- the rows ----------------------------------------------------------------------

    private fun find(root: View, name: String): View? {
        val id = root.resources.getIdentifier(name, "id", Config.SYSTEMUI_PKG)
        return if (id == 0) null else root.findViewById(id)
    }

    /** Lays the bar out on one row or two, for the orientation it is in now. */
    private fun arrange(bar: ViewGroup) {
        val start = find(bar, "status_bar_start_side_except_heads_up") as? LinearLayout
        val endSide = find(bar, "status_bar_end_side_container")
        val end = find(bar, "status_bar_end_side_content")
        val area = find(bar, "notification_icon_area")
        val clock = find(bar, "clock") as? TextView
        if (start == null || endSide == null || end == null || area == null || clock == null
            || clock.layoutParams !is LinearLayout.LayoutParams
            || area.layoutParams !is LinearLayout.LayoutParams
        ) {
            Hooks.skipped("dual status bar", "the status bar is not laid out as expected; left as is")
            return
        }
        clockFace = clock.typeface
        val keep = saved.getOrPut(start) { Saved(start, clock, area, end) }
        // The two rows' measures, or null when this orientation keeps one row.
        val rows = baseHeight(bar.context)?.let { rows(bar.context, it) }
            ?.takeIf { landscape || !isLandscape(bar.context) }

        val clockLp = clock.layoutParams as LinearLayout.LayoutParams
        val areaLp = area.layoutParams as LinearLayout.LayoutParams
        if (rows != null) {
            start.orientation = LinearLayout.VERTICAL
            // The clock takes what the row leaves: the original bar height.
            clockLp.height = 0
            clockLp.weight = 1f
            // The second row: one icon row, starting under the clock's text.
            areaLp.height = rows.row
            areaLp.weight = 0f
            areaLp.topMargin = -rows.overlap
            // The end side's icons stay level with the clock: its box ends where the bar did.
            end.setPadding(end.paddingLeft, end.paddingTop, end.paddingRight,
                keep.endBottom + rows.growth)
        } else {
            start.orientation = keep.orientation
            clockLp.height = keep.clockHeight
            clockLp.weight = keep.clockWeight
            areaLp.height = keep.areaHeight
            areaLp.weight = keep.areaWeight
            areaLp.topMargin = keep.areaTop
            end.setPadding(end.paddingLeft, end.paddingTop, end.paddingRight, keep.endBottom)
        }
        clock.layoutParams = clockLp
        area.layoutParams = areaLp
        // Notifications at the end: the area spans the whole bar's width (its containers
        // draw outside their bounds: clipChildren is off on all of them in Nothing's layout),
        // so its icons reach the bar's end, under the end side's icons, where the second row
        // is empty. Nothing's split of the bar between the two halves is untouched. Seen on
        // the phone before: at the end of the left half, the icons stopped short of the end in
        // landscape, and in portrait (the half ends at the camera) only the overflow dot fit.
        val atEnd = rows != null && notificationsAtEnd
        if (atEnd) wide[area] = java.lang.ref.WeakReference(bar) else wide.remove(area)
        alignIcons(area, atEnd)
        // The date: before the notifications, or (Date at the end) on the end side's second
        // row, under its icons, where the bar is empty, leaving the notifications the whole
        // left half (in portrait the camera gap ends that half, and the full date left the
        // icons no room: seen on the phone, only System UI's overflow dot showed).
        val host = if (dateAtEnd) endSide else area
        DateRow.attach(start, host, area, clock, keep, rows?.takeIf { showDate })
    }

    /**
     * The width of the bar's content (the status bar's width less its own and its content
     * view's paddings): the notification area's width with Notifications at the end. The
     * area starts at the content's start (the left half starts there), so it ends at the
     * content's end. Read from the display's width, which is current when System UI lays the
     * bar out after a rotation.
     */
    private fun fullWidth(bar: ViewGroup): Int? {
        val contents = find(bar, "status_bar_contents") ?: return null
        val w = bar.resources.displayMetrics.widthPixels - bar.paddingLeft - bar.paddingRight -
            contents.paddingLeft - contents.paddingRight
        return w.takeIf { it > 0 }
    }

    /** The icon container back at the start of its area (one row, or the option off). */
    private fun alignIcons(area: View, atEnd: Boolean) {
        if (atEnd) return // Set at each measure: see the onMeasure hook.
        val icons = find(area, "notificationIcons") ?: return
        val lp = icons.layoutParams as? FrameLayout.LayoutParams ?: return
        if (lp.gravity != Gravity.NO_GRAVITY) {
            lp.gravity = Gravity.NO_GRAVITY
            icons.layoutParams = lp
        }
    }

    /**
     * The icon container at the end of its area, set on its layout params in place during
     * the area's measure (no new layout request: this pass uses it).
     */
    private fun endGravity(area: View) {
        val icons = find(area, "notificationIcons") ?: return
        val lp = icons.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }

    /** What [arrange] changes, as Nothing set it: put back for one row. */
    private class Saved(start: LinearLayout, clock: View, area: View, end: View) {
        val orientation = start.orientation
        val clockHeight: Int
        val clockWeight: Float
        val areaHeight: Int
        val areaWeight: Float
        val areaTop: Int
        val endBottom = end.paddingBottom
        val areaStart = area.paddingStart
        val areaEnd = area.paddingEnd

        init {
            val c = clock.layoutParams as LinearLayout.LayoutParams
            clockHeight = c.height
            clockWeight = c.weight
            val a = area.layoutParams as LinearLayout.LayoutParams
            areaHeight = a.height
            areaWeight = a.weight
            areaTop = a.topMargin
        }
    }

    // ---- the date ----------------------------------------------------------------------

    /**
     * The date ("Sunday, 8 November", in the language's own order; the month as a number
     * and the year as set), in the clock's own paint (font, colour, glow), drawn on the
     * overlay of [host]: nothing is added inside System UI's views.
     *
     * - At the start ([host] is the notification area): in the room its start padding
     *   makes, before the icons.
     * - At the end ([host] is the end side's container): on its second row ([rows]),
     *   at its end, under the Wi-Fi, signal and battery icons.
     *
     * Mistake recorded (0.54.0): the year came out as "26". android.text.format.DateFormat
     * writes a lone "y" as two digits; ICU's formatter, made from the same skeleton, writes
     * the full year.
     */
    private class DateRow(
        host: View, area: View, clock: TextView, private val keep: Saved, val rows: Rows,
    ) : Drawable(), View.OnLayoutChangeListener {

        private val host = WeakReference(host)
        private val area = WeakReference(area)
        private val clock = WeakReference(clock)
        private val atEnd = dateAtEnd
        private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        private var formatLocale: Locale? = null
        private var format: android.icu.text.DateFormat? = null
        private var text = ""
        private var width = -1
        private var gap = 0

        fun hostIs(v: View) = host.get() === v

        /** The clock was shown or hidden: the date follows it. */
        fun clockChanged(v: View) {
            if (clock.get() === v) invalidateSelf()
        }

        companion object {
            /** Shows the date for the bar of [start] on [host] (or removes it: [rows] null). */
            fun attach(start: View, host: View, area: View, clock: TextView, keep: Saved,
                       rows: Rows?) {
                val old = dates[start]
                if (old != null && (rows == null || !old.hostIs(host) || old.rows.row != rows.row
                        || old.rows.overlap != rows.overlap)) {
                    old.detach()
                    dates.remove(start)
                }
                if (rows != null && dates[start] == null) {
                    val made = DateRow(host, area, clock, keep, rows)
                    dates[start] = made
                    host.overlay.add(made)
                    host.addOnLayoutChangeListener(made)
                    made.refresh()
                }
            }
        }

        fun detach() {
            host.get()?.let {
                it.overlay.remove(this)
                it.removeOnLayoutChangeListener(this)
            }
            area.get()?.let {
                it.setPaddingRelative(keep.areaStart, it.paddingTop, keep.areaEnd, it.paddingBottom)
            }
        }

        /** The date for today, in the language's order, from a skeleton ICU orders itself. */
        private fun today(): String {
            val locale = Locale.getDefault()
            if (locale != formatLocale || format == null) {
                formatLocale = locale
                val month = if (monthNumber) "M" else "MMMM"
                val year = if (withYear) "y" else ""
                format = android.icu.text.DateFormat.getInstanceForSkeleton(
                    "EEEEd$month$year", locale)
            }
            return format!!.format(Date())
        }

        /** New text or look; the room for it is made again only when its width changed. */
        fun refresh() {
            val h = host.get() ?: return
            val c = clock.get() ?: return
            text = today()
            paint.set(c.paint)
            paint.color = c.currentTextColor
            gap = (c.textSize * 0.6f).roundToInt()
            val w = paint.measureText(text).roundToInt() + gap
            if (w != width) {
                width = w
                // Room for the date in the notification area, so the icons never run under it:
                // at its start, or (date and notifications both at the end) at its end.
                area.get()?.let {
                    when {
                        !atEnd -> it.setPaddingRelative(keep.areaStart + w, it.paddingTop,
                            keep.areaEnd, it.paddingBottom)
                        notificationsAtEnd -> it.setPaddingRelative(keep.areaStart, it.paddingTop,
                            keep.areaEnd + w, it.paddingBottom)
                    }
                }
                place(h)
            }
            invalidateSelf()
        }

        /** The date's box: [width] wide, at the start of the area or at the end side's end. */
        private fun place(v: View) {
            val w = maxOf(width, 0)
            val rtl = v.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val atRight = atEnd != rtl
            // At the end, the second row is the bottom row's height of the end side.
            val top = if (atEnd) maxOf(v.height - rows.row, 0) else 0
            setBounds(if (atRight) v.width - w else 0, top, if (atRight) v.width else w, v.height)
        }

        override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int,
                                    or: Int, ob: Int) = place(v)

        override fun draw(canvas: Canvas) {
            if (text.isEmpty()) return
            // The date is the clock's: hidden with it (see clockChanged).
            val c = clock.get() ?: return
            if (c.visibility != View.VISIBLE || c.alpha == 0f) return
            val b = bounds
            val m = paint.fontMetrics
            // The gap is on the icons' side: after the date at the start, before it at the end.
            val x = if (atEnd) b.left + gap else b.left
            canvas.drawText(text, x.toFloat(), b.exactCenterY() - (m.ascent + m.descent) / 2f,
                paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(filter: ColorFilter?) {
            paint.colorFilter = filter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}

/**
 * A crash-loop guard for a System UI feature: false once System UI has started three times
 * within three minutes with the feature on, until a setting is saved again (the settings'
 * file time, [stamp], changes). Each start is recorded with that stamp in System UI's own
 * data folder (user 0, device storage). A start within a minute of the module app's
 * Restart System UI (its "planned restart" note, [PLANNED]) is not counted.
 */
internal class CrashGuard(private val key: String, private val name: String) {

    private val dir = File("/data/user_de/0/com.android.systemui/files")
    private val starts = File(dir, "nlplus_${key}_starts")
    private val failed = File(dir, "nlplus_${key}_failed")

    fun allows(stamp: Long): Boolean = try {
        if (read(failed) == stamp.toString()) {
            Hooks.log("$name left off: System UI kept restarting with it on; save any setting to try again")
            false
        } else if (System.currentTimeMillis() - (read(File(dir, PLANNED)).trim().toLongOrNull()
                ?: 0L) < PLANNED_MS) {
            true // Restarted from the module app on purpose: not a crash.
        } else if (lastExit()?.let { it !in CRASHES } == true) {
            true // The last System UI ended without crashing (restarted, stopped): not a loop.
        } else {
            val now = System.currentTimeMillis()
            val recent = read(starts).lines()
                .map { it.trim().split(" ") }
                .filter { it.size == 2 && it[1] == stamp.toString() }
                .filter { now - (it[0].toLongOrNull() ?: 0) < LOOP_MS }
                .map { it.joinToString(" ") }
            if (recent.size + 1 >= LOOP_STARTS) {
                write(failed, stamp.toString())
                write(starts, "")
                Hooks.log("$name turned off: System UI started ${recent.size + 1} times within " +
                    "${LOOP_MS / 60_000} minutes with it on")
                false
            } else {
                write(starts, (recent + "$now $stamp").joinToString("\n"))
                true
            }
        }
    } catch (t: Throwable) {
        Hooks.skipped("$name guard unavailable, left off", "$t") // Without a guard it stays off.
        false
    }

    /**
     * Why the previous System UI process ended, as Android recorded it (ApplicationExitInfo
     * .REASON_*), or null when that cannot be read. Mistake recorded (0.54.11, LSPosed log of
     * 05:03 to 05:07): System UI was restarted from outside three times within three minutes
     * (setting Nothing Tweaks' LSPosed scope; its restart and ours), none of them a crash
     * (am_proc_died, no exception, no tombstone), and the guard counted every start, so it
     * switched the dual status bar and the OLED saver off as if they crashed it. Only a start
     * after a crash counts now. Read through IActivityManager, as System UI is asking about
     * its own package (so allowed) before it has a Context.
     */
    private fun lastExit(): Int? = runCatching {
        val am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)
        val slice = am.javaClass.getMethod("getHistoricalProcessExitReasons", String::class.java,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType).invoke(am, Config.SYSTEMUI_PKG, 0, 8, 0)
        val list = slice.javaClass.getMethod("getList").invoke(slice) as List<*>
        list.filterIsInstance<android.app.ApplicationExitInfo>()
            .filter { it.processName == Config.SYSTEMUI_PKG }
            .maxByOrNull { it.timestamp }
            ?.takeIf { System.currentTimeMillis() - it.timestamp < LOOP_MS }
            ?.reason
    }.getOrNull()

    private fun read(f: File): String = runCatching { if (f.exists()) f.readText() else "" }
        .getOrDefault("")

    private fun write(f: File, s: String) {
        check(dir.isDirectory || dir.mkdirs()) { "no $dir" }
        f.writeText(s)
    }

    companion object {
        private const val LOOP_MS = 3 * 60_000L
        private const val LOOP_STARTS = 3
        /** The note the module app writes before it restarts System UI (ms since 1970). */
        const val PLANNED = "nlplus_planned_restart"
        private const val PLANNED_MS = 60_000L
        /** Exits that count towards a loop: crashes, ANRs and a failed start. */
        private val CRASHES = setOf(android.app.ApplicationExitInfo.REASON_CRASH,
            android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
            android.app.ApplicationExitInfo.REASON_ANR,
            android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE)
    }
}
