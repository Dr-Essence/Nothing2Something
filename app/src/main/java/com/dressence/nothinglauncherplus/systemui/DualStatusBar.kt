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
import android.text.format.DateFormat
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

    private const val SYSUI = "com.android.systemui"
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
    private val dates = WeakHashMap<View, DateRow>()

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
        val id = ctx.resources.getIdentifier(name, "dimen", SYSUI)
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
        val id = root.resources.getIdentifier(name, "id", SYSUI)
        return if (id == 0) null else root.findViewById(id)
    }

    /** Lays the bar out on one row or two, for the orientation it is in now. */
    private fun arrange(bar: ViewGroup) {
        val start = find(bar, "status_bar_start_side_except_heads_up") as? LinearLayout
        val startContent = find(bar, "status_bar_start_side_content")
        val end = find(bar, "status_bar_end_side_content")
        val area = find(bar, "notification_icon_area")
        val clock = find(bar, "clock") as? TextView
        if (start == null || startContent == null || end == null || area == null || clock == null
            || clock.layoutParams !is LinearLayout.LayoutParams
            || area.layoutParams !is LinearLayout.LayoutParams
        ) {
            Hooks.log("dual status bar: the status bar is not laid out as expected; left as is")
            return
        }
        clockFace = clock.typeface
        val keep = saved.getOrPut(start) { Saved(start, startContent, clock, area, end) }
        // The two rows' measures, or null when this orientation keeps one row.
        val rows = baseHeight(bar.context)?.let { rows(bar.context, it) }
            ?.takeIf { landscape || !isLandscape(bar.context) }

        val clockLp = clock.layoutParams as LinearLayout.LayoutParams
        val areaLp = area.layoutParams as LinearLayout.LayoutParams
        val contentLp = startContent.layoutParams
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
            if (notificationsAtEnd) {
                // The left half's content fills its half, so the icons can go to its end.
                contentLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                areaLp.width = ViewGroup.LayoutParams.MATCH_PARENT
            }
        } else {
            start.orientation = keep.orientation
            clockLp.height = keep.clockHeight
            clockLp.weight = keep.clockWeight
            areaLp.height = keep.areaHeight
            areaLp.weight = keep.areaWeight
            areaLp.topMargin = keep.areaTop
            end.setPadding(end.paddingLeft, end.paddingTop, end.paddingRight, keep.endBottom)
            contentLp.width = keep.contentWidth
            areaLp.width = keep.areaWidth
        }
        clock.layoutParams = clockLp
        area.layoutParams = areaLp
        startContent.layoutParams = contentLp
        alignIcons(area, rows != null && notificationsAtEnd)
        DateRow.attach(area as ViewGroup, clock, keep, rows != null && showDate)
    }

    /** The icon container at the end of its area (or back at its start). */
    private fun alignIcons(area: View, atEnd: Boolean) {
        val icons = find(area, "notificationIcons") ?: return
        val lp = icons.layoutParams as? FrameLayout.LayoutParams ?: return
        val gravity = if (atEnd) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
        if (lp.gravity != gravity) {
            lp.gravity = gravity
            icons.layoutParams = lp
        }
    }

    /** What [arrange] changes, as Nothing set it: put back for one row. */
    private class Saved(start: LinearLayout, content: View, clock: View, area: View, end: View) {
        val orientation = start.orientation
        val clockHeight: Int
        val clockWeight: Float
        val areaHeight: Int
        val areaWeight: Float
        val areaTop: Int
        val areaWidth: Int
        val contentWidth = content.layoutParams.width
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
            areaWidth = a.width
        }
    }

    // ---- the date ----------------------------------------------------------------------

    /**
     * The date ("Sunday, 8 November", in the language's own order; the month as a number
     * and the year as set) drawn at the start of the notification area, in the room its
     * start padding makes, in the clock's own paint (font, colour, glow). A drawable on the
     * area's overlay: nothing is added inside System UI's views.
     */
    private class DateRow(area: View, clock: TextView, private val keep: Saved) :
        Drawable(), View.OnLayoutChangeListener {

        private val area = WeakReference(area)
        private val clock = WeakReference(clock)
        private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        private var patternLocale: Locale? = null
        private var pattern = ""
        private var text = ""
        private var width = -1

        companion object {
            fun attach(area: ViewGroup, clock: TextView, keep: Saved, show: Boolean) {
                val row = dates[area]
                if (show && row == null) {
                    val made = DateRow(area, clock, keep)
                    dates[area] = made
                    area.overlay.add(made)
                    area.addOnLayoutChangeListener(made)
                    made.refresh()
                } else if (!show && row != null) {
                    area.overlay.remove(row)
                    area.removeOnLayoutChangeListener(row)
                    dates.remove(area)
                    area.setPaddingRelative(keep.areaStart, area.paddingTop, keep.areaEnd,
                        area.paddingBottom)
                }
            }
        }

        /** The date's pattern for the language, from a skeleton Android orders itself. */
        private fun pattern(): String {
            val locale = Locale.getDefault()
            if (locale != patternLocale) {
                patternLocale = locale
                val month = if (monthNumber) "M" else "MMMM"
                val year = if (withYear) "y" else ""
                pattern = DateFormat.getBestDateTimePattern(locale, "EEEEd$month$year")
            }
            return pattern
        }

        /** New text or look; the room for it is made again only when its width changed. */
        fun refresh() {
            val a = area.get() ?: return
            val c = clock.get() ?: return
            text = DateFormat.format(pattern(), Date()).toString()
            paint.set(c.paint)
            paint.color = c.currentTextColor
            val w = paint.measureText(text).roundToInt() + (c.textSize * 0.6f).roundToInt()
            if (w != width) {
                width = w
                a.setPaddingRelative(keep.areaStart + w, a.paddingTop, keep.areaEnd,
                    a.paddingBottom)
                place(a)
            }
            invalidateSelf()
        }

        private fun place(v: View) {
            val w = maxOf(width, 0)
            val rtl = v.layoutDirection == View.LAYOUT_DIRECTION_RTL
            setBounds(if (rtl) v.width - w else 0, 0, if (rtl) v.width else w, v.height)
        }

        override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int,
                                    or: Int, ob: Int) = place(v)

        override fun draw(canvas: Canvas) {
            if (text.isEmpty()) return
            val b = bounds
            val m = paint.fontMetrics
            canvas.drawText(text, b.left.toFloat(), b.exactCenterY() - (m.ascent + m.descent) / 2f,
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
 * data folder (user 0, device storage), which only System UI writes.
 */
internal class CrashGuard(private val key: String, private val name: String) {

    private val dir = File("/data/user_de/0/com.android.systemui/files")
    private val starts = File(dir, "nlplus_${key}_starts")
    private val failed = File(dir, "nlplus_${key}_failed")

    fun allows(stamp: Long): Boolean = try {
        if (read(failed) == stamp.toString()) {
            Hooks.log("$name left off: System UI kept restarting with it on; save any setting to try again")
            false
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
        Hooks.log("$name guard unavailable, left off: $t") // Without a guard it stays off.
        false
    }

    private fun read(f: File): String = runCatching { if (f.exists()) f.readText() else "" }
        .getOrDefault("")

    private fun write(f: File, s: String) {
        check(dir.isDirectory || dir.mkdirs()) { "no $dir" }
        f.writeText(s)
    }

    private companion object {
        const val LOOP_MS = 3 * 60_000L
        const val LOOP_STARTS = 3
    }
}
