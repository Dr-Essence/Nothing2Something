package com.dressence.nothinglauncherplus.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * A short animation over the whole of Nothing Gaming Space's big dot screen when a category is
 * selected, matching it: a Pong rally for Games, marching invaders for Emulators, a turning
 * globe for Browser, equaliser bars for Music, a running film strip for Video, static rolling
 * into colour bars for TV, and a wave of lit squares for Apps.
 *
 * How it is drawn: each frame is painted into a tiny picture with one pixel per dot (the
 * animations are written dot by dot, as on a real LED panel), then shown on the screen's dots
 * the way the folders' dot engine does it: the picture stretched without smoothing under a mask
 * of round dots, over the unlit dots. Two bitmap draws a frame, however many dots there are.
 */
internal class DotAnimationView(context: Context) : View(context) {

    companion object {
        const val DURATION_MS = 1800L
        private const val UNLIT = 0x1AFFFFFF
        private const val GLASS = 0xF0000000.toInt()
        private const val GREEN = 0xFF4CFF7A.toInt()
        private const val DOT = 0.8f
    }

    private var category: GamingLibrary.Category? = null
    private var start = 0L
    private var onEnd: (() -> Unit)? = null
    private var pitch = 0f
    private var cols = 0
    private var rows = 0
    private var left = 0f
    private var top = 0f
    private var corner = 0f
    private var frame: Bitmap? = null
    private var pixels = IntArray(0)
    private var mask: Bitmap? = null
    /** A still picture shown when no animation plays: as given, and fitted to the dots. */
    private var pictureSource: Bitmap? = null
    private var picture: Bitmap? = null
    private val random = Random(7)

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = GLASS }
    private val unlitPaint = Paint().also { it.color = UNLIT }
    private val framePaint = Paint() // no filtering: one dot, one pixel
    private val maskPaint = Paint().also {
        it.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val matrix = Matrix()

    /** Sets the dot spacing (the big screen's own) and the rounded screen it covers. */
    fun setGeometry(dotPitch: Float, screenCorner: Float) {
        pitch = dotPitch
        corner = screenCorner
        layoutGrid()
    }

    /** Plays [cat]'s animation once, then calls [end] (also when stopped early). */
    fun play(cat: GamingLibrary.Category, end: () -> Unit) {
        onEnd?.invoke()
        category = cat
        onEnd = end
        start = SystemClock.uptimeMillis()
        visibility = VISIBLE
        invalidate()
    }

    fun stop() {
        category = null
        visibility = if (picture != null) VISIBLE else GONE
        onEnd?.let { onEnd = null; it() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutGrid()
    }

    private fun layoutGrid() {
        if (pitch <= 0f || width == 0 || height == 0) return
        cols = ((width - pitch) / pitch).toInt()
        rows = ((height - pitch) / pitch).toInt()
        left = (width - cols * pitch) / 2f
        top = (height - rows * pitch) / 2f
        frame = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        pixels = IntArray(cols * rows)
        // The round dots, once: the mask the frame is shown through.
        val m = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val c = Canvas(m)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = pitch * DOT / 2f
        for (y in 0 until rows) for (x in 0 until cols) {
            c.drawCircle(left + (x + 0.5f) * pitch, top + (y + 0.5f) * pitch, r, p)
        }
        mask = m
        fitPicture()
    }

    /**
     * Shows a still picture (a game's artwork) on the dots until it is replaced or cleared:
     * cropped to the screen's shape from the middle and shrunk to one pixel per dot.
     */
    fun showPicture(source: Bitmap?) {
        pictureSource = source
        fitPicture()
        visibility = if (picture != null || category != null) VISIBLE else GONE
        invalidate()
    }

    private fun fitPicture() {
        val source = pictureSource
        if (source == null || cols == 0 || rows == 0) {
            picture = null
            return
        }
        val want = cols / rows.toFloat()
        val have = source.width / source.height.toFloat()
        val w = if (have > want) (source.height * want).toInt() else source.width
        val h = if (have > want) source.height else (source.width / want).toInt()
        val crop = Bitmap.createBitmap(source, (source.width - w) / 2, (source.height - h) / 2,
            maxOf(1, w), maxOf(1, h))
        picture = Bitmap.createScaledBitmap(crop, cols, rows, true)
    }

    override fun onDraw(canvas: Canvas) {
        val dots = mask ?: return
        val cat = category
        if (cat == null) {
            picture?.let { drawDots(canvas, it, dots, 1f) }
            return
        }
        val bitmap = frame ?: return
        val elapsed = SystemClock.uptimeMillis() - start
        if (elapsed >= DURATION_MS) {
            stop()
            return
        }
        val t = elapsed / 1000f
        pixels.fill(0)
        when (cat) {
            GamingLibrary.Category.GAMES -> pong(t)
            GamingLibrary.Category.EMULATORS -> invaders(t)
            GamingLibrary.Category.BROWSER -> globe(t)
            GamingLibrary.Category.MUSIC -> equaliser(t)
            GamingLibrary.Category.VIDEO -> film(t)
            GamingLibrary.Category.TV -> tv(t)
            GamingLibrary.Category.APPS -> wave(t)
        }
        bitmap.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        // Fade in and out at the ends, so it blends with the screen under it (drawn through a
        // translucent layer: setting the view's own alpha here would redraw it in a loop).
        val edge = 200f
        drawDots(canvas, bitmap, dots, minOf(1f, elapsed / edge, (DURATION_MS - elapsed) / edge))
        postInvalidateOnAnimation()
    }

    /**
     * One frame on the dots: the glass, the unlit dots, and [bitmap] (one pixel per dot) seen
     * only through the round dots; [fade] (0..1) for the whole of it.
     */
    private fun drawDots(canvas: Canvas, bitmap: Bitmap, dots: Bitmap, fade: Float) {
        val outer = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(),
            (fade * 255).toInt())
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), corner, corner,
            glassPaint)
        canvas.drawBitmap(dots, 0f, 0f, unlitPaint)
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        matrix.setScale(pitch, pitch)
        matrix.postTranslate(left, top)
        canvas.drawBitmap(bitmap, matrix, framePaint)
        canvas.drawBitmap(dots, 0f, 0f, maskPaint)
        canvas.restoreToCount(layer)
        canvas.restoreToCount(outer)
    }

    // -------------------------------------------------------------------------------------
    // The animations: each paints the dots for time t (seconds) with set()
    // -------------------------------------------------------------------------------------

    private fun set(x: Int, y: Int, color: Int) {
        if (x in 0 until cols && y in 0 until rows) pixels[y * cols + x] = color
    }

    private fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        for (j in y until y + h) for (i in x until x + w) set(i, j, color)
    }

    /** Two paddles and a ball bouncing between them, a dashed net in the middle. */
    private fun pong(t: Float) {
        val pad = maxOf(5, rows / 3)
        for (y in 0 until rows step 4) rect(cols / 2, y, 1, 2, 0xFF2A6B3A.toInt())
        // The ball crosses the court twice a second, bouncing off top and bottom.
        val span = cols - 8
        val phase = (t * 1.1f) % 2f
        val bx = 4 + (if (phase < 1f) phase else 2f - phase) * span
        val vy = (t * 1.7f) % 2f
        val by = (if (vy < 1f) vy else 2f - vy) * (rows - 3)
        rect(bx.roundToInt(), by.roundToInt(), 3, 3, 0xFFFFFFFF.toInt())
        // Paddles follow the ball, a little late.
        val ly = (by - pad / 2f + sin(t * 3f) * 2f).roundToInt().coerceIn(0, rows - pad)
        val ry = (by - pad / 2f - sin(t * 2.5f) * 2f).roundToInt().coerceIn(0, rows - pad)
        rect(1, ly, 3, pad, GREEN)
        rect(cols - 4, ry, 3, pad, GREEN)
    }

    /** The classic invader, two frames, 11 x 8 dots. */
    private val invader = arrayOf(
        arrayOf("00100000100", "00010001000", "00111111100", "01101110110",
            "11111111111", "10111111101", "10100000101", "00011011000"),
        arrayOf("00100000100", "10010001001", "10111111101", "11101110111",
            "11111111111", "01111111110", "00100000100", "01000000010"))

    /** Rows of invaders marching across and stepping down, a cannon firing from below. */
    private fun invaders(t: Float) {
        val sprite = invader[((t * 4).toInt()) % 2]
        val across = maxOf(1, (cols - 20) / 16)
        val march = ((t * 6).toInt() % 12) - 6
        val colours = intArrayOf(0xFFB45CFF.toInt(), 0xFF4CFF7A.toInt())
        for (row in 0 until 2) for (n in 0 until across) {
            val ox = 10 + n * 16 + march
            val oy = 2 + row * 10 + (t * 2).toInt()
            sprite.forEachIndexed { y, line ->
                line.forEachIndexed { x, c -> if (c == '1') set(ox + x, oy + y, colours[row]) }
            }
        }
        val cx = (cols / 2 + sin(t * 2f) * cols / 4).roundToInt()
        rect(cx - 3, rows - 3, 7, 2, GREEN)
        set(cx, rows - 4, GREEN)
        val shot = rows - 5 - ((t * 40).toInt() % rows)
        set(cx, shot, 0xFFFFFFFF.toInt())
        set(cx, shot + 1, 0xFFFFFFFF.toInt())
    }

    /**
     * A wireframe globe turning: its rim, the parallels, and meridians going round, the ones on
     * the far side left dark. The dots are square, so the globe is a true circle on them.
     */
    private fun globe(t: Float) {
        val blue = 0xFF2E9BFF.toInt()
        val light = 0xFF9AD2FF.toInt()
        val cx = cols / 2f
        val cy = rows / 2f
        val r = rows * 0.46f
        val steps = 240
        for (s in 0 until steps) {
            val a = s * 2 * PI / steps
            set((cx + cos(a) * r).roundToInt(), (cy + sin(a) * r).roundToInt(), blue)
        }
        for (lat in intArrayOf(-60, -30, 0, 30, 60)) {
            val phi = lat * PI / 180
            val half = r * cos(phi)
            val y = (cy + r * sin(phi)).roundToInt()
            for (x in (cx - half).roundToInt()..(cx + half).roundToInt()) set(x, y, blue)
        }
        for (m in 0 until 12) { // every 30 degrees round, the near half shown
            val lon = m * PI / 6 + t * 1.4
            if (cos(lon) < 0) continue // on the far side
            for (s in 0..steps / 2) {
                val phi = s * PI / (steps / 2) - PI / 2
                set((cx + r * cos(phi) * sin(lon)).roundToInt(),
                    (cy + r * sin(phi)).roundToInt(), light)
            }
        }
    }

    /** Equaliser bars across the screen, green at the foot, yellow, red at the top. */
    private fun equaliser(t: Float) {
        val bars = cols / 4
        for (b in 0 until bars) {
            val f = 3f + (b * 37 % 11)
            val level = 0.15f + 0.85f * abs(sin(t * f * 0.7f + b * 0.9f)) *
                (0.6f + 0.4f * sin(t * 2f + b * 0.3f).let { it * it })
            val h = (level * rows).roundToInt()
            for (y in 0 until h) {
                val k = y / rows.toFloat()
                val c = when {
                    k > 0.8f -> 0xFFFF4A3D.toInt()
                    k > 0.55f -> 0xFFFFD23D.toInt()
                    else -> GREEN
                }
                rect(b * 4, rows - 1 - y, 3, 1, c)
            }
        }
    }

    /** A film strip running past, sprocket holes above and below, then a play sign. */
    private fun film(t: Float) {
        val band = rows * 2 / 3
        val y0 = (rows - band) / 2
        val shift = (t * 30).toInt()
        for (x in 0 until cols) {
            val hole = (x + shift) % 5 < 2
            set(x, y0, if (hole) 0 else 0xFF8A8A8A.toInt())
            set(x, y0 + band - 1, if (hole) 0 else 0xFF8A8A8A.toInt())
        }
        val frameW = band + 4
        for (x in 0 until cols) {
            val k = (x + shift) % frameW
            if (k < 2) continue
            val shade = if (((x + shift) / frameW) % 2 == 0) 0xFF3A3F55.toInt() else 0xFF55402F.toInt()
            for (y in y0 + 2 until y0 + band - 2) set(x, y, shade)
        }
        if (t > 1.0f) { // the play sign comes up over it
            val h = band * 3 / 4
            val cx = cols / 2 - h / 4
            for (i in 0 until h) {
                val span = if (i < h / 2) i else h - 1 - i
                for (x in 0..span) set(cx + x, rows / 2 - h / 2 + i, 0xFFFFFFFF.toInt())
            }
        }
    }

    /** Static with a rolling bar, settling into colour bars. */
    private fun tv(t: Float) {
        if (t < 1.1f) {
            val roll = ((t * 25).toInt()) % rows
            for (y in 0 until rows) for (x in 0 until cols) {
                val v = random.nextInt(256)
                val dim = if (abs(y - roll) < 3) 0.4f else 1f
                val level = (v * dim).toInt()
                set(x, y, (0xFF shl 24) or (level shl 16) or (level shl 8) or level)
            }
        } else {
            val bars = intArrayOf(0xFFC0C0C0.toInt(), 0xFFC0C000.toInt(), 0xFF00C0C0.toInt(),
                0xFF00C000.toInt(), 0xFFC000C0.toInt(), 0xFFC00000.toInt(), 0xFF0000C0.toInt())
            val w = cols / bars.size + 1
            for (x in 0 until cols) {
                val c = bars[(x / w).coerceAtMost(bars.size - 1)]
                for (y in 0 until rows * 3 / 4) set(x, y, c)
            }
        }
    }

    /** Squares lighting up in a wave running across, like apps opening one after another. */
    private fun wave(t: Float) {
        val size = maxOf(3, rows / 4)
        val gap = 2
        val across = cols / (size + gap)
        val down = rows / (size + gap)
        for (j in 0 until down) for (i in 0 until across) {
            val phase = t * 3f - (i + j) * 0.25f
            val v = (sin(phase) * 0.5f + 0.5f)
            if (v < 0.2f) continue
            val level = (v * 255).toInt()
            val c = (0xFF shl 24) or ((0x4C * level / 255) shl 16) or (level shl 8) or
                (0x7A * level / 255)
            rect(i * (size + gap) + 1, j * (size + gap) + 1, size, size, c)
        }
    }
}
