package com.dressence.nothinglauncherplus.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View

/**
 * A game's artwork on Nothing Gaming Space's big dot screen: a still picture, or an animated
 * banner playing frame by frame.
 *
 * Every frame becomes one colour per dot, as the drawer folders' dot engine shows its covers:
 * the picture is placed as the game's "Adjust artwork" says ([Framing]) in a small picture with
 * one pixel per dot, then shown by [DotScreen] (glass, unlit dots, glow, lit dots added as
 * light). An animated banner is drawn again into that small picture for every frame it gives.
 * (Until 0.70.0 an animated banner was drawn at full size through the round dots, so each dot
 * showed a piece of the picture rather than one colour, and its black parts hid the unlit dots.)
 *
 * Until 0.70.1 this view also played a short animation for each category (Pong, invaders, a
 * globe...); Dr.Essence found them cheap, and they were removed: the big screen shows the
 * category's animated emoji from the folder engine instead.
 */
internal class DotArtworkView(context: Context) : View(context) {

    private val screen = DotScreen()
    private var corner = 0f
    /**
     * The dot spacing asked for, kept here: it is given before the view is measured, and the
     * screen is laid out only once the size is known. (Mistake recorded, 0.70.1: the spacing
     * was handed straight to [DotScreen.layout], which refused it at size 0 without keeping
     * it, so the screen was never laid out and every game's artwork stayed black, with the
     * icon hidden under it.)
     */
    private var dotPitch = 0f
    /** The frame: one pixel per dot. */
    private var frame: Bitmap? = null
    /** A still picture, as given. */
    private var still: Bitmap? = null
    /** An animated picture playing (see [showArtwork]). */
    private var moving: Drawable? = null
    private val smooth = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Where the game's picture sits in the banner (its "Adjust artwork" setting). */
    var framing = Framing.CENTRED
        set(value) {
            field = value
            render()
            invalidate()
        }

    /** Sets the dot spacing (the big screen's own) and the rounded screen it covers. */
    fun setGeometry(pitch: Float, screenCorner: Float) {
        dotPitch = pitch
        corner = screenCorner
        layoutScreen()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutScreen()
    }

    private fun layoutScreen() {
        if (!screen.layout(width, height, dotPitch)) return
        if (frame?.width != screen.cols || frame?.height != screen.rows) {
            frame = Bitmap.createBitmap(screen.cols, screen.rows, Bitmap.Config.ARGB_8888)
        }
        render()
    }

    /**
     * Shows a game's artwork (null: none): a still picture, or an animated one, which plays.
     * Shown as soon as there is one, even before the dots are laid out: a view that is GONE is
     * never measured, so its dots would never be made. (Mistake recorded, 0.61.0 and 0.61.1:
     * it was shown only once fitted, so it never was.)
     */
    fun showArtwork(art: Drawable?) {
        stopMoving()
        still = null
        if (art is BitmapDrawable) {
            still = art.bitmap
        } else if (art != null) {
            moving = art
            art.callback = this
            (art as? Animatable)?.start()
        }
        visibility = if (art != null) VISIBLE else GONE
        render()
        invalidate()
    }

    private fun stopMoving() {
        moving?.let {
            (it as? Animatable)?.stop()
            it.callback = null
        }
        moving = null
    }

    override fun verifyDrawable(who: Drawable): Boolean = who === moving || super.verifyDrawable(who)

    /** An animated picture asks for its next frame: it is drawn into the dots again. */
    override fun invalidateDrawable(drawable: Drawable) {
        if (drawable === moving) {
            render()
            invalidate()
        } else super.invalidateDrawable(drawable)
    }

    /** Draws the current picture (still or moving) into the one-pixel-per-dot frame. */
    private fun render() {
        val out = frame ?: return
        out.eraseColor(0)
        try {
            val canvas = Canvas(out)
            val w = screen.cols.toFloat()
            val h = screen.rows.toFloat()
            still?.let { source ->
                // A bitmap's own canvas cannot draw a hardware bitmap: a readable copy first.
                val readable = if (source.config == Bitmap.Config.HARDWARE)
                    source.copy(Bitmap.Config.ARGB_8888, false).also { still = it } else source
                val area = framing.place(readable.width.toFloat(), readable.height.toFloat(),
                    0f, 0f, w, h)
                canvas.drawBitmap(readable, null, area, smooth)
            }
            moving?.let { art ->
                val iw = art.intrinsicWidth.takeIf { it > 0 }?.toFloat() ?: w
                val ih = art.intrinsicHeight.takeIf { it > 0 }?.toFloat() ?: h
                val area = framing.place(iw, ih, 0f, 0f, w, h)
                art.setBounds(area.left.toInt(), area.top.toInt(), area.right.toInt(),
                    area.bottom.toInt())
                art.draw(canvas)
            }
        } catch (t: Throwable) {
            out.eraseColor(0) // a picture that cannot be shown leaves the screen dark, never crashes
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (still == null && moving == null) return
        screen.draw(canvas, frame ?: return, corner)
    }
}
