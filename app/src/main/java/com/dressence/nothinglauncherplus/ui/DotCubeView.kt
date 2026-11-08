package com.dressence.nothinglauncherplus.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small box that turns slowly, an app's dot screen on each of its four sides (Nothing Gaming
 * Space's app column). The selected one turns faster and is fully lit; the others turn slowly,
 * a little darker.
 *
 * How it is drawn, without OpenGL: the box's eight corners are turned in 3D (round the
 * vertical axis, tilted a little towards the viewer) and projected with perspective; each side
 * facing the viewer is then drawn as the face picture mapped onto its four projected corners
 * (`Matrix.setPolyToPoly` with four points makes exactly that perspective mapping). Sides are
 * drawn far ones first, and darker the more they turn away, as if lit from the front.
 */
internal class DotCubeView(context: Context) : View(context) {

    companion object {
        /** Turning speed in degrees a second: selected, and the rest. */
        private const val SPIN_ON = 40f
        private const val SPIN_OFF = 12f
        /**
         * How far the box leans towards the viewer, to show its top: positive brings the far
         * top edge up on screen (worked through: y' = y cos t - z sin t, far corners z > 0).
         */
        private const val TILT = 24f
        /** Shading steps: one colour filter each, made once, not one per side per frame. */
        private val SHADES = Array(33) { i ->
            val level = i * 255 / 32
            LightingColorFilter((0xFF shl 24) or (level shl 16) or (level shl 8) or level, 0)
        }
        /** Distance of the eye, in half box sides: smaller is stronger perspective. */
        private const val EYE = 5f

        /** The four side faces as corner indices (see [corners]), each going round its face. */
        private val SIDES = arrayOf(
            intArrayOf(0, 1, 2, 3), // front
            intArrayOf(1, 5, 6, 2), // right
            intArrayOf(5, 4, 7, 6), // back
            intArrayOf(4, 0, 3, 7), // left
        )
        private val TOP = intArrayOf(4, 5, 1, 0)
    }

    private var face: Bitmap? = null
    private var angle = (Math.random() * 360).toFloat()
    private var lastFrame = 0L
    var lit = false
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = 0xFF101110.toInt() }
    private val matrix = Matrix()
    private val src = FloatArray(8)
    private val dst = FloatArray(8)
    /** The eight corners, turned and projected: x, y on screen and depth. */
    private val px = FloatArray(8)
    private val py = FloatArray(8)
    private val pz = FloatArray(8)
    private val path = android.graphics.Path()

    /** The picture on each side: the app's dot screen on a dark plate (see [plate]). */
    fun setFace(bitmap: Bitmap?) {
        face = bitmap
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val picture = face ?: return
        // Advance the turn by the time since the last frame, so speed does not depend on fps.
        val now = SystemClock.uptimeMillis()
        if (lastFrame != 0L) {
            angle += (now - lastFrame) / 1000f * if (lit) SPIN_ON else SPIN_OFF
        }
        lastFrame = now
        project()

        // Sides facing the viewer, far ones first.
        val order = SIDES.filter { facing(it) }.sortedByDescending { s -> s.sumOf { pz[it].toDouble() } }
        src[0] = 0f; src[1] = 0f
        src[2] = picture.width.toFloat(); src[3] = 0f
        src[4] = picture.width.toFloat(); src[5] = picture.height.toFloat()
        src[6] = 0f; src[7] = picture.height.toFloat()
        val dim = if (lit) 1f else 0.7f
        for (side in order) {
            for (k in 0 until 4) {
                dst[k * 2] = px[side[k]]
                dst[k * 2 + 1] = py[side[k]]
            }
            matrix.setPolyToPoly(src, 0, dst, 0, 4)
            val light = (dim * (0.45f + 0.55f * frontness(side))).coerceIn(0f, 1f)
            paint.colorFilter = SHADES[(light * 32).toInt()]
            canvas.drawBitmap(picture, matrix, paint)
        }
        if (facing(TOP)) {
            path.reset()
            path.moveTo(px[TOP[0]], py[TOP[0]])
            for (k in 1 until 4) path.lineTo(px[TOP[k]], py[TOP[k]])
            path.close()
            canvas.drawPath(path, topPaint)
        }
        if (isShown) postInvalidateOnAnimation()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        lastFrame = 0L // no jump after a pause
        if (isVisible) invalidate()
    }

    /** Turns the box's corners and projects them into the view, centred, filling it. */
    private fun project() {
        val half = 1f
        val a = Math.toRadians(angle.toDouble())
        val t = Math.toRadians(TILT.toDouble())
        val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
        val ct = cos(t).toFloat(); val st = sin(t).toFloat()
        // Scale so the turning box stays inside the view: its widest diagonal is 2 * sqrt(2).
        val scale = minOf(width, height) / 2f / 1.55f
        for (i in 0 until 8) {
            val x0 = if (i == 1 || i == 2 || i == 5 || i == 6) half else -half
            val y0 = if (i == 2 || i == 3 || i == 6 || i == 7) half else -half
            val z0 = if (i >= 4) half else -half
            // Round the vertical axis, then the lean.
            val x1 = x0 * ca + z0 * sa
            val z1 = -x0 * sa + z0 * ca
            val y2 = y0 * ct - z1 * st
            val z2 = y0 * st + z1 * ct
            val p = EYE / (EYE + z2)
            px[i] = width / 2f + x1 * p * scale
            py[i] = height / 2f + y2 * p * scale
            pz[i] = z2
        }
    }

    /** True if the face's corners go round clockwise on screen: it faces the viewer. */
    private fun facing(f: IntArray): Boolean {
        val ax = px[f[1]] - px[f[0]]; val ay = py[f[1]] - py[f[0]]
        val bx = px[f[2]] - px[f[0]]; val by = py[f[2]] - py[f[0]]
        return ax * by - ay * bx > 0
    }

    /** How squarely a face looks at the viewer, 0 (edge on) to 1 (straight on). */
    private fun frontness(f: IntArray): Float {
        val w = RectF()
        w.left = minOf(px[f[0]], px[f[1]], px[f[2]], px[f[3]])
        w.right = maxOf(px[f[0]], px[f[1]], px[f[2]], px[f[3]])
        val full = minOf(width, height) / 1.55f
        return (w.width() / full).coerceIn(0f, 1f)
    }
}

/** An app's dot screen on a dark plate with a thin rim: one side of its box. */
internal fun plate(dots: Bitmap): Bitmap {
    val size = 256
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    paint.color = 0xFF070807.toInt()
    val r = size * 0.08f
    canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), r, r, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = size * 0.02f
    paint.color = 0xFF2A2B2A.toInt()
    canvas.drawRoundRect(2f, 2f, size - 2f, size - 2f, r, r, paint)
    // Hardware bitmaps cannot be drawn on a bitmap's canvas: a readable copy if needed.
    val picture = if (dots.config == Bitmap.Config.HARDWARE)
        dots.copy(Bitmap.Config.ARGB_8888, false) else dots
    val inset = size * 0.1f
    canvas.drawBitmap(picture, null, RectF(inset, inset, size - inset, size - inset),
        Paint(Paint.FILTER_BITMAP_FLAG))
    return out
}
