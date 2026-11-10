package com.dressence.nothinglauncherplus.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
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
 * into a [TextureView]; this frame draws that view through the round dots of [DotScreen]
 * (glass, unlit dots, glow, the video added as light, as the folders' dot engine shows its
 * covers), so black parts of the video leave the unlit dots showing. The glow is made from a
 * small copy of the video's picture, one pixel per dot ([TextureView.getBitmap] into a bitmap
 * that size, at most [GLOW_EVERY_MS] apart; the copy has the view's transform applied, so it
 * follows the game's "Adjust artwork" setting, [Framing], as the video does). If the copy
 * cannot be made the video still plays, without glow.
 */
internal class DotVideoView(context: Context) : FrameLayout(context),
        TextureView.SurfaceTextureListener {

    companion object {
        /** The glow's copy of the picture is renewed at most this often (about 15 a second). */
        private const val GLOW_EVERY_MS = 66L
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

    private val screen = DotScreen()
    private var pitch = 0f
    private var corner = 0f
    /** The video's picture, one pixel per dot: the glow's source. */
    private var small: Bitmap? = null
    private var smallAt = 0L
    private var smallWorks = true

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
        if (videoW == 0 || videoH == 0 || width == 0 || screen.cols == 0) return
        val area = framing.place(videoW.toFloat(), videoH.toFloat(), screen.left, screen.top,
            screen.cols * pitch, screen.rows * pitch)
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
        if (!screen.layout(width, height, pitch)) return
        small = Bitmap.createBitmap(screen.cols, screen.rows, Bitmap.Config.ARGB_8888)
        fit()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (screen.mask == null) {
            super.dispatchDraw(canvas)
            return
        }
        screen.draw(canvas, glowFrame(), corner) { super.dispatchDraw(it) } // the video
        postInvalidateOnAnimation() // every frame the video gives
    }

    /** The glow's small copy of the video, renewed at most every [GLOW_EVERY_MS]; null if none. */
    private fun glowFrame(): Bitmap? {
        val copy = small ?: return null
        if (!smallWorks || player == null) return null
        val now = android.os.SystemClock.uptimeMillis()
        if (now - smallAt >= GLOW_EVERY_MS) {
            smallAt = now
            try {
                // The view's picture scaled into the small bitmap, its transform applied.
                texture.getBitmap(copy)
            } catch (t: Throwable) {
                smallWorks = false // no glow on this phone; the video plays on
                return null
            }
        }
        return copy
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
