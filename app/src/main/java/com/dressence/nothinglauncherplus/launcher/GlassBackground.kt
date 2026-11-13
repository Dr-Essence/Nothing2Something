package com.dressence.nothinglauncherplus.launcher

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View

/**
 * Frosted glass (Frosted.kt, Nothing's recipe) as a view's background, in the rounded shape of
 * the [original] background it replaces, which is kept to put back when the glass is switched
 * off: Nothing OS 5's FrostedBackdropHelper does the same. The corner is the original's own
 * (a GradientDrawable's radius), else [cornerFallback], else half the height (a pill).
 *
 * Used by the dock's search bar and Nothing's widgets (FrostedSurfaces) and by an open drawer
 * folder (DrawerGlass, with a 0 corner: a full-screen sheet).
 */
internal class GlassBackground(
    private val view: View,
    val original: Drawable,
    var tintPercent: Int,
    private val cornerFallback: Float?,
) : Drawable() {

    private val area = RectF()
    private val location = IntArray(2)

    override fun draw(canvas: Canvas) {
        area.set(bounds)
        val radius = (original as? GradientDrawable)?.cornerRadius?.takeIf { it > 0f }
            ?: cornerFallback ?: (area.height() / 2f)
        view.getLocationOnScreen(location)
        canvas.drawRoundRect(area, radius, radius, Frosted.paint(view,
            location[0].toFloat(), location[1].toFloat(), view.scaleX, tintPercent))
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Drawable API") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
