package com.dressence.nothinglauncherplus

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

/**
 * Nothing OS 5's frosted glass recipe ("Lucent"), the steps alone, shared by the launcher's
 * glass (launcher/Frosted.kt, which also follows each surface and captures live wallpapers)
 * and the module's own screens (ui/FrostedScreen.kt). Read from the Phone (2a)'s Nothing OS
 * 5.0 Launcher (FrostedBackdropProvider, FrostedBackdropDrawable; see Frosted.kt):
 * the wallpaper 3 times smaller, a box blur of radius 3 along the rows then the columns,
 * then one colour filter: saturation x 1.15, then the tint mixed in at its alpha.
 */
object FrostedRecipe {

    /** FrostedBackdropConfig.downscaleFactor. */
    const val DOWNSCALE = 3
    /** FrostedBackdropConfig blurRadius. */
    private const val BLUR_RADIUS = 3
    /** saturationFactor. */
    private const val SATURATION = 1.15f

    /** Blurs [bitmap] (already 3 times smaller) in place, as Nothing does. */
    fun blur(bitmap: Bitmap) = boxBlur(bitmap, BLUR_RADIUS)

    /** Nothing's FrostedBackdropDrawable.Companion.buildColorFilter: saturation, then the tint. */
    fun colourFilter(tint: Int): ColorMatrixColorFilter {
        val a = (tint ushr 24) / 255f
        val r = (tint shr 16 and 0xFF) / 255f
        val g = (tint shr 8 and 0xFF) / 255f
        val b = (tint and 0xFF) / 255f
        val keep = 1f - a
        val mix = ColorMatrix(floatArrayOf(
            keep, 0f, 0f, 0f, r * a * 255f,
            0f, keep, 0f, 0f, g * a * 255f,
            0f, 0f, keep, 0f, b * a * 255f,
            0f, 0f, 0f, 1f, 0f))
        val saturation = ColorMatrix().apply { setSaturation(SATURATION) }
        return ColorMatrixColorFilter(ColorMatrix().apply { setConcat(mix, saturation) })
    }

    /** A still wallpaper, centre-cropped to the screen at a third of its size. */
    fun read(manager: WallpaperManager, width: Int, height: Int): Bitmap? {
        val wallpaper = manager.drawable ?: return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = wallpaper.intrinsicWidth.coerceAtLeast(1)
        val h = wallpaper.intrinsicHeight.coerceAtLeast(1)
        // Fill the screen and centre it, as the home screen shows the wallpaper.
        val fill = maxOf(width.toFloat() / w, height.toFloat() / h)
        val left = ((width - w * fill) / 2).toInt()
        val top = ((height - h * fill) / 2).toInt()
        wallpaper.setBounds(left, top, left + (w * fill).toInt(), top + (h * fill).toInt())
        wallpaper.isFilterBitmap = true
        wallpaper.draw(canvas)
        return bitmap
    }

    /**
     * Nothing's blur (i7.l.a): a box blur along the rows, then along the columns, each pixel
     * the average of the 2 x [radius] + 1 pixels around it, the edge pixel repeated past the
     * edge.
     */
    private fun boxBlur(bitmap: Bitmap, radius: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        val out = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        blurPass(pixels, out, w, h, radius, 1, w)   // rows
        blurPass(out, pixels, h, w, radius, w, 1)   // columns
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /**
     * One pass over [lines] lines of [length] pixels: [step] moves along a line, [next] to the
     * next line. A running sum, so each pixel costs the same whatever the radius.
     */
    private fun blurPass(src: IntArray, dst: IntArray, length: Int, lines: Int, radius: Int,
            step: Int, next: Int) {
        val window = 2 * radius + 1
        for (line in 0 until lines) {
            val start = line * next
            var a = 0; var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) {
                val p = src[start + i.coerceIn(0, length - 1) * step]
                a += p ushr 24; r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF
            }
            for (i in 0 until length) {
                dst[start + i * step] = (a / window shl 24) or (r / window shl 16) or
                    (g / window shl 8) or (b / window)
                val leaving = src[start + (i - radius).coerceIn(0, length - 1) * step]
                val entering = src[start + (i + radius + 1).coerceIn(0, length - 1) * step]
                a += (entering ushr 24) - (leaving ushr 24)
                r += (entering shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
                g += (entering shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
                b += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }
}
