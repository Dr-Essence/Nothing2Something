package com.dressence.nothinglauncherplus.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import com.dressence.nothinglauncherplus.launcher.IconScreen
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A ball of dots that turns slowly, an app's round icon on it: Nothing Gaming Space's look for
 * apps whose icon is only a circle (Dr.Essence on 0.68.1: on a box's square side such an icon
 * leaves the corners empty). Every other icon stays a box ([DotCubeView]).
 *
 * How it is drawn, without OpenGL: the dots are fixed on the ball's surface in rows of
 * latitude, each row with as many dots as fit round it, so they are evenly spaced. Each dot
 * takes its colour from the icon once: the icon is laid on the ball's front straight on (a
 * circle seen straight on is exactly the ball's outline, so a round icon covers the front half
 * whole) and again, mirrored, on the back, so it reads the right way round from both sides.
 * Each frame the dots are turned round the vertical axis, leant towards the viewer as the boxes
 * are ([DotCubeView]), and projected with perspective; only the ones on the half facing the
 * viewer are drawn, smaller and darker the more they turn away, as on a curved LED globe.
 */
internal class DotBallView(context: Context) : View(context), TurningIcon {

    companion object {
        /** Same turning, lean and perspective as the boxes, so the two read as one family. */
        private const val SPIN_ON = 40f
        private const val SPIN_OFF = 12f
        private const val TILT = 24f
        private const val EYE = 5f
        /**
         * Dots round the equator, and rows from pole to pole: about 30 across the half you
         * see, close to the boxes' 28. (40 and 20, tried first in a render of the same steps,
         * left the icon hard to read; about 600 dots are drawn a frame.)
         */
        private const val AROUND = 64
        private const val ROWS = 32
        /** A dot's radius, in dot spacings, straight on. */
        private const val DOT = 0.36f
        /** The ball's share of its view: a little smaller than the view, as the boxes look. */
        private const val FILL = 0.92f
        private const val GLASS = 0xFF050605.toInt()
        private const val RIM = 0xFF2A2B2A.toInt()
        private const val UNLIT = 0xFF1C1D1C.toInt()
        /** Darker than this (0-255 brightness), a dot stays unlit. */
        private const val DARK = 24
        /** The icon is read at this many cells across, each cell's mean colour. */
        private const val TEXTURE = 48
        private const val ART = 288

        /**
         * The icon's colours for a ball, or null when the icon should stay a box: only an icon
         * that is not adaptive and whose outline is a circle becomes a ball. (An adaptive
         * icon is a full square under the launcher's mask, which on this phone is a circle, so
         * the drawn icon of every adaptive app is round too: the test is on the icon's kind
         * first, then on its outline, [IconScreen.isCircle].)
         */
        fun texture(icon: Drawable): IntArray? {
            if (icon is AdaptiveIconDrawable) return null
            val art = Bitmap.createBitmap(ART, ART, Bitmap.Config.ARGB_8888)
            val d = icon.constantState?.newDrawable()?.mutate() ?: icon
            d.setBounds(0, 0, ART, ART)
            d.draw(Canvas(art))
            if (!IconScreen.isCircle(art)) {
                art.recycle()
                return null
            }
            val px = IntArray(ART * ART)
            art.getPixels(px, 0, ART, 0, 0, ART, ART)
            art.recycle()
            // Each cell's mean colour, transparent parts counting as black (as DotFace does).
            val out = IntArray(TEXTURE * TEXTURE)
            for (cy in 0 until TEXTURE) for (cx in 0 until TEXTURE) {
                val x0 = cx * ART / TEXTURE; val x1 = (cx + 1) * ART / TEXTURE
                val y0 = cy * ART / TEXTURE; val y1 = (cy + 1) * ART / TEXTURE
                var r = 0L; var g = 0L; var b = 0L
                for (y in y0 until y1) for (x in x0 until x1) {
                    val c = px[y * ART + x]
                    val a = c ushr 24
                    r += ((c shr 16) and 0xFF) * a / 255
                    g += ((c shr 8) and 0xFF) * a / 255
                    b += (c and 0xFF) * a / 255
                }
                val n = ((x1 - x0) * (y1 - y0)).coerceAtLeast(1)
                out[cy * TEXTURE + cx] = Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
            }
            return out
        }
    }

    /** The dots on the unturned ball (unit radius): position, and colour (0 for unlit). */
    private var bx = FloatArray(0)
    private var by = FloatArray(0)
    private var bz = FloatArray(0)
    private var colour = IntArray(0)

    private var angle = (Math.random() * 360).toFloat()
    private var lastFrame = 0L
    override var lit = false
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Lays the dots out on the ball and colours them from [texture] (see [Companion.texture]). */
    fun setTexture(texture: IntArray) {
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>(); val zs = ArrayList<Float>()
        val cs = ArrayList<Int>()
        for (row in 0 until ROWS) {
            // Row centres from near one pole to near the other, evenly spaced in latitude.
            val lat = PI * ((row + 0.5) / ROWS - 0.5)
            val ring = cos(lat)
            val count = maxOf(1, (AROUND * ring).roundToInt())
            for (k in 0 until count) {
                val lon = 2 * PI * (k + 0.5 * (row % 2)) / count
                val x = (ring * sin(lon)).toFloat()
                val y = sin(lat).toFloat()
                val z = (-ring * cos(lon)).toFloat() // negative z faces the viewer
                // Straight-on onto the front half; mirrored across for the back half.
                val u = if (z <= 0) (x + 1) / 2 else (1 - x) / 2
                val v = (y + 1) / 2
                val c = texture[(v * (TEXTURE - 1)).roundToInt() * TEXTURE +
                    (u * (TEXTURE - 1)).roundToInt()]
                val bright = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                xs += x; ys += y; zs += z
                cs += if (bright < DARK) 0 else c
            }
        }
        bx = xs.toFloatArray(); by = ys.toFloatArray(); bz = zs.toFloatArray()
        colour = cs.toIntArray()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (colour.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        if (lastFrame != 0L) angle += (now - lastFrame) / 1000f * if (lit) SPIN_ON else SPIN_OFF
        lastFrame = now

        // A unit ball seen from EYE away looks slightly larger than unit: its outline's radius.
        val outline = EYE / sqrt(EYE * EYE - 1)
        val scale = minOf(width, height) / 2f * FILL / outline
        val cx = width / 2f
        val cy = height / 2f
        // The glass behind the dots, with a thin rim, so the ball reads as one screen.
        paint.style = Paint.Style.FILL
        paint.color = GLASS
        canvas.drawCircle(cx, cy, outline * scale, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = scale * 0.02f
        paint.color = RIM
        canvas.drawCircle(cx, cy, outline * scale, paint)
        paint.style = Paint.Style.FILL

        val a = Math.toRadians(angle.toDouble())
        val t = Math.toRadians(TILT.toDouble())
        val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
        val ct = cos(t).toFloat(); val st = sin(t).toFloat()
        val spacing = (2 * PI / AROUND).toFloat()
        val dim = if (lit) 1f else 0.7f
        for (i in colour.indices) {
            // Round the vertical axis, then the lean (the boxes' turn, DotCubeView.turn).
            val x1 = bx[i] * ca + bz[i] * sa
            val z1 = -bx[i] * sa + bz[i] * ca
            val y2 = by[i] * ct - z1 * st
            val z2 = by[i] * st + z1 * ct
            // Seen from the eye at (0, 0, -EYE), a point of the ball faces it when
            // 1 + EYE * z < 0 (the surface normal is the point itself).
            if (1 + EYE * z2 >= 0) continue
            val p = EYE / (EYE + z2)
            // How squarely the dot faces the viewer, 0 at the outline to 1 straight on.
            val facing = (-(1 + EYE * z2) / sqrt(x1 * x1 + y2 * y2 + (z2 + EYE) * (z2 + EYE)))
                .coerceIn(0f, 1f)
            val light = dim * (0.45f + 0.55f * facing)
            val c = colour[i]
            paint.color = if (c == 0) shade(UNLIT, light) else shade(c, light)
            val r = spacing * DOT * p * scale * (0.55f + 0.45f * facing)
            canvas.drawCircle(cx + x1 * p * scale, cy + y2 * p * scale, r, paint)
        }
        if (isShown) postInvalidateOnAnimation()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        lastFrame = 0L // no jump after a pause
        if (isVisible) invalidate()
    }

    private fun shade(c: Int, light: Float): Int = Color.rgb(
        (Color.red(c) * light).toInt(), (Color.green(c) * light).toInt(),
        (Color.blue(c) * light).toInt())
}
