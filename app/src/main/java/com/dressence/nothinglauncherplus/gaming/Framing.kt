package com.dressence.nothinglauncherplus.gaming

import android.graphics.RectF

/**
 * Where a picture sits in Nothing Gaming Space's banner, as the user set it with "Adjust
 * artwork": [x] and [y] from -1 (left, top) to 1 (right, bottom) over the part that does not
 * fit, and [zoom] from 1 (the picture just covers the banner) up to [MAX_ZOOM].
 */
internal data class Framing(val x: Float = 0f, val y: Float = 0f, val zoom: Float = 1f) {

    companion object {
        const val MAX_ZOOM = 3f
        val CENTRED = Framing()

        /** Read back from the settings ("x;y;zoom"); centred if it cannot be read. */
        fun parse(text: String?): Framing = try {
            val p = text!!.split(';').map { it.toFloat() }
            Framing(p[0], p[1], p[2]).clamped()
        } catch (t: Throwable) {
            CENTRED
        }
    }

    fun clamped() = Framing(x.coerceIn(-1f, 1f), y.coerceIn(-1f, 1f), zoom.coerceIn(1f, MAX_ZOOM))

    override fun toString() = "$x;$y;$zoom"

    /**
     * Where to draw a [sw] x [sh] picture so it covers a [dw] x [dh] area at [left], [top]:
     * scaled to cover, then by [zoom], and moved within what overhangs by [x] and [y].
     */
    fun place(sw: Float, sh: Float, left: Float, top: Float, dw: Float, dh: Float): RectF {
        val scale = maxOf(dw / sw, dh / sh) * zoom
        val w = sw * scale
        val h = sh * scale
        val cx = left + (dw - w) / 2 - x * (w - dw) / 2
        val cy = top + (dh - h) / 2 - y * (h - dh) / 2
        return RectF(cx, cy, cx + w, cy + h)
    }
}
