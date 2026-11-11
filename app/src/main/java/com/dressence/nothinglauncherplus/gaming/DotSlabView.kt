package com.dressence.nothinglauncherplus.gaming

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A game's icon as a solid shape of dots that turns slowly: Nothing Gaming Space's look for
 * games whose SteamGridDB icon is a cut-out PNG (the logo itself, a clear background round it).
 * Every other app stays a box ([DotCubeView]).
 *
 * Asked by Dr.Essence after a preview drawn with these exact steps (GTA V's "V" and Hollow
 * Knight's icon at six angles) was shown first: the lesson of the dot ball of 0.69.0, built
 * before it was seen and removed.
 *
 * How it is drawn, without OpenGL: the icon is read as [GRID] by [GRID] cells; each cell that is
 * mostly opaque becomes a column of [LAYERS] dots through the slab's thickness: the front and
 * back layers in the icon's colour (the back mirrored, so it reads the right way round from
 * behind), the layers between only where the shape has an edge, darker, so the thickness shows.
 * Each frame every dot is turned round the vertical axis, leant towards the viewer as the boxes
 * are, and projected with perspective. Layers are drawn far to near (which end is near follows
 * from which face is turned to the viewer), so nearer dots cover farther ones without sorting.
 */
internal class DotSlabView(context: Context) : View(context), TurningIcon {

    companion object {
        private const val GRID = 40
        private const val LAYERS = 5
        /** Half the slab's thickness, in half icon widths. */
        private const val DEPTH = 0.12f
        private const val SPIN_ON = 40f
        private const val SPIN_OFF = 12f
        private const val TILT = 18f
        private const val EYE = 5f
        /** The shape's share of its view, leaving room for perspective and the lean. */
        private const val FILL = 0.78f
        /** A dot's radius, in dot spacings. */
        private const val DOT = 0.425f
        /** A cell this opaque is part of the shape. */
        private const val SOLID = 0.5f
        /** An icon with at least this share of clear pixels is a cut-out (GTA V's: 53%). */
        private const val CUT_OUT = 0.15f

        /** True if [icon] is a cut-out shape (a clear background), so it can turn as a slab. */
        fun isCutOut(icon: Bitmap): Boolean {
            val w = icon.width
            val h = icon.height
            if (w == 0 || h == 0 || !icon.hasAlpha()) return false
            val px = IntArray(w * h)
            icon.getPixels(px, 0, w, 0, 0, w, h)
            val clear = px.count { (it ushr 24) < 20 }
            return clear >= px.size * CUT_OUT
        }
    }

    /** The dots: position on the unturned slab, colour, and layer (0 front, LAYERS-1 back). */
    private var dx = FloatArray(0)
    private var dy = FloatArray(0)
    private var dz = FloatArray(0)
    private var colour = IntArray(0)
    /** Where each layer's dots start in the arrays (layer l: start[l] until start[l + 1]). */
    private val start = IntArray(LAYERS + 1)

    private var angle = (Math.random() * 360).toFloat()
    private var lastFrame = 0L
    override var lit = false
        set(value) {
            field = value
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Makes the slab from [icon] (any size; read as [GRID] cells). */
    fun setIcon(icon: Bitmap) {
        val cells = GRID * 8
        val scaled = Bitmap.createScaledBitmap(icon, cells, cells, true)
        val px = IntArray(cells * cells)
        scaled.getPixels(px, 0, cells, 0, 0, cells, cells)
        if (scaled !== icon) scaled.recycle()
        // Each cell's opacity and mean colour (by hand: see DotFace.average).
        val solid = BooleanArray(GRID * GRID)
        val cellColour = IntArray(GRID * GRID)
        for (cy in 0 until GRID) for (cx in 0 until GRID) {
            var a = 0L; var r = 0L; var g = 0L; var b = 0L
            for (y in cy * 8 until cy * 8 + 8) for (x in cx * 8 until cx * 8 + 8) {
                val c = px[y * cells + x]
                val ca = (c ushr 24).toLong()
                a += ca
                r += ((c shr 16) and 0xFF) * ca
                g += ((c shr 8) and 0xFF) * ca
                b += (c and 0xFF) * ca
            }
            val i = cy * GRID + cx
            solid[i] = a >= 64 * 255 * SOLID
            if (a > 0) cellColour[i] = Color.rgb((r / a).toInt(), (g / a).toInt(), (b / a).toInt())
        }
        fun isSolid(x: Int, y: Int) = x in 0 until GRID && y in 0 until GRID && solid[y * GRID + x]
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>(); val zs = ArrayList<Float>()
        val cs = ArrayList<Int>()
        for (l in 0 until LAYERS) {
            start[l] = xs.size
            val z = (l / (LAYERS - 1f) - 0.5f) * DEPTH * 2 // layer 0 nearest the viewer
            for (y in 0 until GRID) for (x in 0 until GRID) {
                if (!solid[y * GRID + x]) continue
                val edge = !isSolid(x + 1, y) || !isSolid(x - 1, y) || !isSolid(x, y + 1) ||
                    !isSolid(x, y - 1)
                val face = l == 0 || l == LAYERS - 1
                if (!face && !edge) continue
                xs += (x + 0.5f) / GRID * 2 - 1
                ys += (y + 0.5f) / GRID * 2 - 1
                zs += z
                // The back reads the right way round from behind: its colours are mirrored.
                cs += if (l == LAYERS - 1) cellColour[y * GRID + (GRID - 1 - x)].takeIf {
                    isSolid(GRID - 1 - x, y) } ?: cellColour[y * GRID + x] else cellColour[y * GRID + x]
            }
        }
        start[LAYERS] = xs.size
        dx = xs.toFloatArray(); dy = ys.toFloatArray(); dz = zs.toFloatArray()
        colour = cs.toIntArray()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (colour.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        if (lastFrame != 0L) angle += (now - lastFrame) / 1000f * if (lit) SPIN_ON else SPIN_OFF
        lastFrame = now
        val a = Math.toRadians(angle.toDouble())
        val t = Math.toRadians(TILT.toDouble())
        val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
        val ct = cos(t).toFloat(); val st = sin(t).toFloat()
        val scale = minOf(width, height) / 2f * FILL
        val r0 = 2f / GRID * scale * DOT
        val cx = width / 2f
        val cy = height / 2f
        val dim = if (lit) 1f else 0.7f
        // Lit from the front: a face is brightest when turned to the viewer; edges stay dim.
        val frontLight = dim * (0.35f + 0.65f * max(0f, ca))
        val backLight = dim * (0.35f + 0.65f * max(0f, -ca))
        val sideLight = dim * 0.35f
        // Far layers first: the back is far while the front faces the viewer (cos > 0).
        val layers = if (ca > 0) (LAYERS - 1 downTo 0) else (0 until LAYERS)
        for (l in layers) {
            val light = when (l) { 0 -> frontLight; LAYERS - 1 -> backLight; else -> sideLight }
            for (i in start[l] until start[l + 1]) {
                val x1 = dx[i] * ca + dz[i] * sa
                val z1 = -dx[i] * sa + dz[i] * ca
                val y2 = dy[i] * ct - z1 * st
                val z2 = dy[i] * st + z1 * ct
                val p = EYE / (EYE + z2)
                val c = colour[i]
                paint.color = Color.rgb((Color.red(c) * light).toInt(),
                    (Color.green(c) * light).toInt(), (Color.blue(c) * light).toInt())
                canvas.drawCircle(cx + x1 * p * scale, cy + y2 * p * scale, r0 * p, paint)
            }
        }
        if (isShown) postInvalidateOnAnimation()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        lastFrame = 0L // no jump after a pause
        if (isVisible) invalidate()
    }
}
