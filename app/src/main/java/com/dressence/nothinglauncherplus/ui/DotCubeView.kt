package com.dressence.nothinglauncherplus.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * A box that turns slowly, an app's dot screen on each of its four sides ([DotFace]; Nothing
 * Gaming Space's apps). The selected one turns faster and is fully lit; the others turn slowly,
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

        /**
         * How far the turning box reaches, over a whole turn, in half box sides: left, right,
         * up, down. Measured once by turning the corners through every degree, so the box is
         * sized to fit its view at every angle. (Until 0.68.1 it was sized for a reach of
         * 1.55 every way; it really reaches 1.60 across and 1.81 up and down, so at some
         * angles its corners went past the view's edge and were cut off.)
         */
        private val REACH: FloatArray by lazy {
            val r = floatArrayOf(0f, 0f, 0f, 0f)
            val x = FloatArray(8); val y = FloatArray(8); val z = FloatArray(8)
            for (deg in 0 until 360) {
                turn(deg.toFloat(), x, y, z)
                for (i in 0 until 8) {
                    r[0] = minOf(r[0], x[i]); r[1] = maxOf(r[1], x[i])
                    r[2] = minOf(r[2], y[i]); r[3] = maxOf(r[3], y[i])
                }
            }
            r
        }

        /**
         * The eight corners of a box two units wide, turned [angle] degrees round the vertical
         * axis, leant by [TILT] and projected with perspective: x and y in half box sides, and
         * depth.
         */
        private fun turn(angle: Float, x: FloatArray, y: FloatArray, z: FloatArray) {
            val a = Math.toRadians(angle.toDouble())
            val t = Math.toRadians(TILT.toDouble())
            val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
            val ct = cos(t).toFloat(); val st = sin(t).toFloat()
            for (i in 0 until 8) {
                val x0 = if (i == 1 || i == 2 || i == 5 || i == 6) 1f else -1f
                val y0 = if (i == 2 || i == 3 || i == 6 || i == 7) 1f else -1f
                val z0 = if (i >= 4) 1f else -1f
                // Round the vertical axis, then the lean.
                val x1 = x0 * ca + z0 * sa
                val z1 = -x0 * sa + z0 * ca
                val y2 = y0 * ct - z1 * st
                val z2 = y0 * st + z1 * ct
                val p = EYE / (EYE + z2)
                x[i] = x1 * p
                y[i] = y2 * p
                z[i] = z2
            }
        }
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
    private val matrix = Matrix()
    private val src = FloatArray(8)
    private val dst = FloatArray(8)
    /** The eight corners, turned and projected: x, y on screen and depth. */
    private val px = FloatArray(8)
    private val py = FloatArray(8)
    private val pz = FloatArray(8)

    /** The picture on each side: the app's dot screen ([DotFace]). */
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
        for (side in order) drawFace(canvas, picture, side, dim * (0.45f + 0.55f * frontness(side)))
        // The top is a dot screen too (it was a dark plain lid until 0.68.1), a little darker,
        // as lit from the front.
        if (facing(TOP)) drawFace(canvas, picture, TOP, dim * 0.75f)
        if (isShown) postInvalidateOnAnimation()
    }

    /** The face picture mapped onto one side's four projected corners, at [light] (0 to 1). */
    private fun drawFace(canvas: Canvas, picture: Bitmap, side: IntArray, light: Float) {
        for (k in 0 until 4) {
            dst[k * 2] = px[side[k]]
            dst[k * 2 + 1] = py[side[k]]
        }
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        paint.colorFilter = SHADES[(light.coerceIn(0f, 1f) * 32).toInt()]
        canvas.drawBitmap(picture, matrix, paint)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        lastFrame = 0L // no jump after a pause
        if (isVisible) invalidate()
    }

    /**
     * Turns the box's corners and places them in the view: as large as fits at every angle of
     * the turn ([REACH]), and centred on that reach, so no corner ever leaves the view.
     */
    private fun project() {
        turn(angle, px, py, pz)
        val r = REACH
        val scale = minOf(width / (r[1] - r[0]), height / (r[3] - r[2]))
        val cx = width / 2f - (r[0] + r[1]) / 2 * scale
        val cy = height / 2f - (r[2] + r[3]) / 2 * scale
        for (i in 0 until 8) {
            px[i] = cx + px[i] * scale
            py[i] = cy + py[i] * scale
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
        val r = REACH
        val full = 2f * minOf(width / (r[1] - r[0]), height / (r[3] - r[2])) // a side, straight on
        return (w.width() / full).coerceIn(0f, 1f)
    }
}

/**
 * One side of an app's box: the whole side is a dot screen, edge to edge (Dr.Essence on 0.67.0:
 * the launcher's round dot icon on a black plate left most of the side empty).
 *
 * The picture is the icon without the launcher's mask: an adaptive icon's two layers are drawn
 * as they are, filling the side (Android draws them 25% larger than the icon's bounds on every
 * side and masks them, AdaptiveIconDrawable.EXTRA_INSET_PERCENTAGE, so drawing the layers that
 * way without the mask gives the icon's full square). An older, non-adaptive icon is drawn
 * whole in the middle. Then every cell of a [GRID] by [GRID] grid becomes one dot in the
 * cell's colour, an unlit grey dot where the picture is dark, as on Nothing's dot screens.
 */
internal object DotFace {

    /** The face's size in pixels, and its dots across. */
    private const val SIZE = 384
    private const val GRID = 28
    /** A dot's radius, in cell sizes. */
    private const val DOT = 0.42f
    private const val GLASS = 0xFF050605.toInt()
    private const val UNLIT = 0xFF1C1D1C.toInt()
    private const val RIM = 0xFF2A2B2A.toInt()
    /** Darker than this (0-255 brightness), a dot stays unlit. */
    private const val DARK = 24

    fun of(icon: Drawable): Bitmap = dots(fullIcon(icon))

    /** The icon's full square, without the launcher's mask. */
    private fun fullIcon(icon: Drawable): Bitmap {
        val art = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(art)
        if (icon is AdaptiveIconDrawable) {
            val grow = SIZE / 4
            for (layer in listOf(icon.background, icon.foreground)) {
                val d = layer?.constantState?.newDrawable()?.mutate() ?: layer ?: continue
                d.setBounds(-grow, -grow, SIZE + grow, SIZE + grow)
                d.draw(canvas)
            }
        } else {
            val d = icon.constantState?.newDrawable()?.mutate() ?: icon
            val inset = SIZE / 12
            d.setBounds(inset, inset, SIZE - inset, SIZE - inset)
            d.draw(canvas)
        }
        return art
    }

    private fun dots(art: Bitmap): Bitmap {
        val cells = average(art)
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val corner = SIZE * 0.06f
        paint.color = GLASS
        canvas.drawRoundRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), corner, corner, paint)
        val pitch = SIZE / GRID.toFloat()
        for (y in 0 until GRID) for (x in 0 until GRID) {
            val c = cells[y * GRID + x]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val bright = (r * 299 + g * 587 + b * 114) / 1000
            paint.color = if (bright < DARK) UNLIT else Color.rgb(r, g, b)
            canvas.drawCircle((x + 0.5f) * pitch, (y + 0.5f) * pitch, pitch * DOT, paint)
        }
        // A thin rim, so the side reads as a screen's edge while the box turns.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = SIZE * 0.015f
        paint.color = RIM
        canvas.drawRoundRect(2f, 2f, SIZE - 2f, SIZE - 2f, corner, corner, paint)
        art.recycle()
        return out
    }

    /**
     * Each cell's mean colour, transparent parts counting as black. Done by hand: a filtered
     * Bitmap.createScaledBitmap samples only a few pixels per cell when it shrinks this much
     * (384 to 28), so the dots came out noisy instead of the cell's colour.
     */
    private fun average(art: Bitmap): IntArray {
        val px = IntArray(SIZE * SIZE)
        art.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        val out = IntArray(GRID * GRID)
        for (cy in 0 until GRID) for (cx in 0 until GRID) {
            val x0 = cx * SIZE / GRID; val x1 = (cx + 1) * SIZE / GRID
            val y0 = cy * SIZE / GRID; val y1 = (cy + 1) * SIZE / GRID
            var r = 0L; var g = 0L; var b = 0L
            for (y in y0 until y1) for (x in x0 until x1) {
                val c = px[y * SIZE + x]
                val a = c ushr 24
                r += ((c shr 16) and 0xFF) * a / 255
                g += ((c shr 8) and 0xFF) * a / 255
                b += (c and 0xFF) * a / 255
            }
            val n = ((x1 - x0) * (y1 - y0)).coerceAtLeast(1)
            out[cy * GRID + cx] = Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        }
        return out
    }
}
