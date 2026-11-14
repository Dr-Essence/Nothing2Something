package com.dressence.nothinglauncherplus.ui

import android.app.Activity
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Environment
import android.os.Handler
import android.os.Looper
import com.dressence.nothinglauncherplus.FrostedRecipe
import java.util.concurrent.Executors

/**
 * Nothing OS 5's frosted glass for the module's own screens (Glass settings screens, 0.70.52):
 * the wallpaper made into Nothing's frosted picture (FrostedRecipe: a third of the screen's
 * size, box blur radius 3, then saturation x 1.15 and the tint), stretched over the window,
 * as Nothing's launcher draws a frosted surface the size of the screen.
 *
 * Only a still wallpaper can be read by an app, and only with All files access (Android 13
 * and later: WallpaperManager.getDrawable needs MANAGE_EXTERNAL_STORAGE). A live wallpaper
 * has no picture to read (Nothing's launcher captures its layer, which needs a system
 * permission); Ui.applyBackground then shows it behind the window with Android's live blur
 * at Nothing's strength ([liveBlurPx]) and the same tint.
 */
internal object FrostedScreen {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    /** The last picture made (blurred, not yet tinted), and what it was made from. */
    @Volatile private var picture: Bitmap? = null
    @Volatile private var madeFor: String? = null

    /**
     * Nothing's blur, as Android's live blur radius in pixels: its box is 2 x 3 + 1 = 7
     * pixels wide on a picture 3 times smaller, 21 screen pixels.
     */
    fun liveBlurPx(): Int = FrostedRecipe.DOWNSCALE * 7

    /** Whether this phone's wallpaper can be made into the picture now. */
    fun canRead(activity: Activity): Boolean =
        Environment.isExternalStorageManager() &&
            WallpaperManager.getInstance(activity).wallpaperInfo == null

    /**
     * Gives [done] (on the main thread) the frosted picture of the still wallpaper with
     * [tint] in, as a drawable for the window, or null if it cannot be made. Made once per
     * wallpaper and screen size, off the main thread.
     */
    fun load(activity: Activity, tint: Int, done: (Drawable?) -> Unit) {
        val app = activity.applicationContext
        val metrics = activity.resources.displayMetrics
        val width = metrics.widthPixels / FrostedRecipe.DOWNSCALE
        val height = metrics.heightPixels / FrostedRecipe.DOWNSCALE
        val manager = WallpaperManager.getInstance(app)
        val key = "${manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)}:${width}x$height"
        val ready = picture
        if (ready != null && key == madeFor) {
            done(drawable(activity, ready, tint))
            return
        }
        worker.execute {
            val made = try {
                FrostedRecipe.read(manager, width, height)?.also { FrostedRecipe.blur(it) }
            } catch (t: Throwable) {
                null // No permission after all, or no picture: the live blur stays.
            }
            if (made != null) {
                picture = made
                madeFor = key
            }
            main.post { done(made?.let { drawable(activity, it, tint) }) }
        }
    }

    /** The picture stretched over the window, through Nothing's colour filter. */
    private fun drawable(activity: Activity, bitmap: Bitmap, tint: Int): Drawable =
        BitmapDrawable(activity.resources, bitmap).apply {
            isFilterBitmap = true
            colorFilter = FrostedRecipe.colourFilter(tint)
        }
}
