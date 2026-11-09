package com.dressence.nothinglauncherplus.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout

/**
 * A game's trailer playing on Nothing Gaming Space's big dot screen: muted, looping, seen
 * through the screen's round dots.
 *
 * Android's own player ([MediaPlayer], which plays Steam's HLS streams and mp4 files) draws
 * into a [TextureView]; this frame draws that view through a mask of round dots at the screen's
 * dot size (`dispatchDraw`: the video in a layer, then the mask with DST_IN), over the unlit
 * dots, as the folders' dot engine shows its pictures. The picture covers the dots' area and
 * follows the game's "Adjust artwork" setting ([Framing]).
 */
internal class DotVideoView(context: Context) : FrameLayout(context),
        TextureView.SurfaceTextureListener {

    companion object {
        private const val UNLIT = 0x1AFFFFFF
        private const val GLASS = 0xF0000000.toInt()
        private const val DOT = 0.8f
    }

    private val texture = TextureView(context)
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var url: String? = null
    private var videoW = 0
    private var videoH = 0
    var framing = Framing.CENTRED
        set(value) {
            field = value
            fit()
        }

    private var pitch = 0f
    private var corner = 0f
    private var mask: Bitmap? = null
    private var cols = 0
    private var rows = 0
    private var gridLeft = 0f
    private var gridTop = 0f
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = GLASS }
    private val unlitPaint = Paint().also { it.color = UNLIT }
    private val maskPaint = Paint().also {
        it.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    /** Called back when the trailer starts showing (true) or cannot play (false). */
    var onPlaying: ((Boolean) -> Unit)? = null

    init {
        setWillNotDraw(false)
        texture.surfaceTextureListener = this
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        visibility = GONE
    }

    fun setGeometry(dotPitch: Float, screenCorner: Float) {
        pitch = dotPitch
        corner = screenCorner
        layoutGrid()
    }

    /** Plays [address] (null stops). */
    fun play(address: String?) {
        if (address == url) return
        stop()
        url = address
        if (address == null) return
        visibility = VISIBLE
        start()
    }

    fun stop() {
        player?.let {
            try { it.release() } catch (_: Throwable) {}
        }
        player = null
        url = null
        visibility = GONE
    }

    private fun start() {
        val target = surface ?: return // started once the surface exists
        val address = url ?: return
        val media = MediaPlayer()
        player = media
        try {
            media.setSurface(target)
            media.setVolume(0f, 0f) // a banner: no sound
            media.isLooping = true
            media.setOnVideoSizeChangedListener { _, w, h ->
                videoW = w
                videoH = h
                fit()
            }
            media.setOnPreparedListener {
                if (player === it) {
                    it.start()
                    onPlaying?.invoke(true)
                }
            }
            media.setOnErrorListener { mp, _, _ ->
                if (player === mp) {
                    stop()
                    onPlaying?.invoke(false)
                }
                true
            }
            media.setDataSource(address)
            media.prepareAsync()
        } catch (t: Throwable) {
            stop()
            onPlaying?.invoke(false)
        }
    }

    /** The video scaled to cover the dots' area, placed as the game's framing says. */
    private fun fit() {
        if (videoW == 0 || videoH == 0 || width == 0 || cols == 0) return
        val area = framing.place(videoW.toFloat(), videoH.toFloat(), gridLeft, gridTop,
            cols * pitch, rows * pitch)
        // TextureView stretches the video over the whole view; this maps it to `area`.
        val matrix = Matrix()
        matrix.setRectToRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), area,
            Matrix.ScaleToFit.FILL)
        texture.setTransform(matrix)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutGrid()
    }

    private fun layoutGrid() {
        if (pitch <= 0f || width == 0 || height == 0) return
        cols = ((width - pitch) / pitch).toInt()
        rows = ((height - pitch) / pitch).toInt()
        gridLeft = (width - cols * pitch) / 2f
        gridTop = (height - rows * pitch) / 2f
        val m = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val c = Canvas(m)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = pitch * DOT / 2f
        for (y in 0 until rows) for (x in 0 until cols) {
            c.drawCircle(gridLeft + (x + 0.5f) * pitch, gridTop + (y + 0.5f) * pitch, r, p)
        }
        mask = m
        fit()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val dots = mask
        if (dots == null) {
            super.dispatchDraw(canvas)
            return
        }
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), corner, corner,
            glassPaint)
        canvas.drawBitmap(dots, 0f, 0f, unlitPaint)
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.clipRect(gridLeft, gridTop, gridLeft + cols * pitch, gridTop + rows * pitch)
        super.dispatchDraw(canvas) // the video
        canvas.drawBitmap(dots, 0f, 0f, maskPaint)
        canvas.restoreToCount(layer)
        postInvalidateOnAnimation() // every frame the video gives
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        surface = Surface(st)
        start()
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = fit()

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        player?.let { try { it.release() } catch (_: Throwable) {} }
        player = null
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = invalidate()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }
}
