package com.dressence.nothinglauncherplus.gaming

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

/**
 * Nothing Gaming Space's big dot screen, drawn the way the drawer folders' dot engine draws
 * its covers (anim.DotMatrix), so a game's banner or trailer looks like the same LED panel:
 *
 * 1. black glass;
 * 2. every dot faintly visible, unlit;
 * 3. the glow: the picture blurred into the glass around its dots (the picture stretched with
 *    smooth filtering, once at one pixel per dot and once at half that), as LEDs behind smoked
 *    glass light the glass round them;
 * 4. the lit dots, each one colour.
 *
 * The glow and the lit dots are **added** to what is under them (PorterDuff ADD), as light is.
 * That is the fix for Dr.Essence's report on 0.70.0, "black colour makes the pixel illusion
 * disappear": until then the picture was painted over the unlit dots, so a black part of it
 * painted opaque black dots that hid them, and the screen became a flat black hole. Added,
 * black adds nothing and the unlit dots stay, as in the folder engine, where a dark spot of
 * the emoji is simply a dot left unlit.
 *
 * A frame is a small picture with one pixel per dot ([cols] by [rows]); the views make it
 * ([DotArtworkView]) or give their own lit layer ([DotVideoView]) and call [draw].
 */
internal class DotScreen {

    companion object {
        /** Unlit dots: white at 10%, the folder engine's UNLIT_DARK. */
        private const val UNLIT = 0x1AFFFFFF
        private const val GLASS = 0xF0000000.toInt()
        /** Dot diameter in dot spacings, as the folder engine's. */
        private const val DOT = 0.8f
        /**
         * The near and the wide glow's strength (out of 255): under half the folder engine's
         * 70 and 120. Those suit an emoji on a mostly dark panel; under a picture that lights
         * the whole screen they fill the gaps between the dots and the picture reads blurred.
         * Picked from renders of a Steam microtrailer frame at 70/120, 30/50, 12/24 and none:
         * 30/50 kept every dot clear with a soft halo.
         */
        private const val GLOW_NEAR = 30
        private const val GLOW_FAR = 50
    }

    var pitch = 0f
        private set
    var cols = 0
        private set
    var rows = 0
        private set
    var left = 0f
        private set
    var top = 0f
        private set
    /** The round dots: the mask the lit layer is seen through. */
    var mask: Bitmap? = null
        private set
    /** The frame at half resolution, for the wide glow. */
    private var half: Bitmap? = null
    private var width = 0
    private var height = 0

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = GLASS }
    private val unlitPaint = Paint().also { it.color = UNLIT }
    private val nearest = Paint() // no filtering: one dot, one pixel
    private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint().also { it.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val addPaint = Paint().also { it.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val halfPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val rect = RectF()

    /** Lays the dots out for a view of [w] by [h] at [dotPitch]; false if nothing fits. */
    fun layout(w: Int, h: Int, dotPitch: Float): Boolean {
        if (dotPitch <= 0f || w == 0 || h == 0) return false
        if (w == width && h == height && dotPitch == pitch && mask != null) return true
        width = w
        height = h
        pitch = dotPitch
        cols = ((w - pitch) / pitch).toInt()
        rows = ((h - pitch) / pitch).toInt()
        if (cols <= 0 || rows <= 0) return false
        left = (w - cols * pitch) / 2f
        top = (h - rows * pitch) / 2f
        val m = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val c = Canvas(m)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = pitch * DOT / 2f
        for (y in 0 until rows) for (x in 0 until cols) {
            c.drawCircle(left + (x + 0.5f) * pitch, top + (y + 0.5f) * pitch, r, p)
        }
        mask = m
        half = Bitmap.createBitmap(maxOf(1, cols / 2), maxOf(1, rows / 2), Bitmap.Config.ARGB_8888)
        return true
    }

    /**
     * Draws the screen: glass, unlit dots, then [frame]'s glow (one pixel per dot; null: no
     * glow), then the lit dots, which [lit] draws into a layer (null: [frame] on its dots).
     * [corner] rounds the glass; [fade] (0..1) is the whole screen's opacity.
     */
    fun draw(canvas: Canvas, frame: Bitmap?, corner: Float, fade: Float = 1f,
            lit: ((Canvas) -> Unit)? = null) {
        val dots = mask ?: return
        val outer = if (fade < 1f) canvas.saveLayerAlpha(0f, 0f, width.toFloat(),
            height.toFloat(), (fade * 255).toInt()) else -1
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, corner, corner, glassPaint)
        canvas.drawBitmap(dots, 0f, 0f, unlitPaint)
        if (frame != null) glow(canvas, frame)
        // The lit dots, in a layer added onto the screen: light over the unlit dots.
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), addPaint)
        canvas.clipRect(left, top, left + cols * pitch, top + rows * pitch)
        if (lit != null) lit(canvas)
        else if (frame != null) {
            matrix.setScale(pitch * cols / frame.width, pitch * rows / frame.height)
            matrix.postTranslate(left, top)
            canvas.drawBitmap(frame, matrix, nearest)
        }
        canvas.drawBitmap(dots, 0f, 0f, maskPaint)
        canvas.restoreToCount(layer)
        if (outer >= 0) canvas.restoreToCount(outer)
    }

    /** The glow: [frame] stretched smoothly over the dots, and its half-size copy wider. */
    private fun glow(canvas: Canvas, frame: Bitmap) {
        val small = half ?: return
        val save = canvas.save()
        canvas.clipRect(left, top, left + cols * pitch, top + rows * pitch)
        glowPaint.xfermode = addPaint.xfermode
        glowPaint.alpha = GLOW_NEAR
        rect.set(left, top, left + cols * pitch, top + rows * pitch)
        canvas.drawBitmap(frame, null, rect, glowPaint)
        // The wide glow: half resolution, so it spreads over about two dots.
        Canvas(small).let {
            small.eraseColor(0)
            it.drawBitmap(frame, null, RectF(0f, 0f, small.width.toFloat(),
                small.height.toFloat()), halfPaint)
        }
        val spread = pitch
        rect.set(left - spread, top - spread, left + cols * pitch + spread,
            top + rows * pitch + spread)
        glowPaint.alpha = GLOW_FAR
        canvas.drawBitmap(small, null, rect, glowPaint)
        canvas.restoreToCount(save)
    }
}
