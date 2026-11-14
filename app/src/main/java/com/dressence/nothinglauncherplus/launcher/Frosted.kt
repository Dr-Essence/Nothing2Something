package com.dressence.nothinglauncherplus.launcher

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.os.SystemClock
import android.view.SurfaceControl
import android.os.Handler
import android.os.Looper
import android.view.View
import java.util.Collections
import java.util.WeakHashMap
import com.dressence.nothinglauncherplus.FrostedRecipe
import com.dressence.nothinglauncherplus.xposed.Hooks
import java.util.concurrent.Executors

/**
 * Nothing OS 5's frosted glass ("Lucent"), made the way Nothing makes it (0.70.28).
 *
 * Read from the Phone (2a)'s Nothing OS 5.0 Launcher (Pacman_C5.0-261015-1824) with dexdump:
 * com.nothing.cardhost.frosted.FrostedBackdropProvider builds ONE blurred picture of the
 * wallpaper, and every frosted surface (com.nothing.commBase.frosted.stdview
 * .FrostedBackdropDrawable) draws its own piece of it, lined up with where it is on the screen:
 *
 *  1. the wallpaper, made 3 times smaller (FrostedBackdropConfig.downscaleFactor 3,
 *     Bitmap.createScaledBitmap with filtering);
 *  2. blurred with a box blur of radius 3, first along the rows, then along the columns
 *     (blurRadius 3.0, i7.l.a/b/c), each pixel the average of the 7 around it, edges clamped;
 *  3. drawn through one colour filter: saturation x 1.15 (saturationFactor), then mixed with
 *     the tint: out = in x (1 - a) + tint x a, with the tint "container_frosted_backdrop",
 *     Lucent's #66FFFFFF in light mode and #66000000 in dark (a = 0x66 / 255 = 40 %);
 *  4. with no picture yet (or none possible), the tint alone (Nothing's "tint only" mode).
 *
 * Nothing OS 5 switches this on with its theme style (Settings.Secure nt_theme_style = 2); on
 * Nothing OS 4 the module's switches do. Android's own Launcher3 blur (blurOnMoreSurfaces) is
 * NOT part of it: that flag is compiled to false in Nothing OS 5's Launcher.
 *
 * A live wallpaper has no picture to read (WallpaperManager.getDrawable gives the phone's
 * default image instead: Dr.Essence's screenshot of 0.70.28, a grey ringed glass over a pixel-art
 * live wallpaper). Nothing OS 5 then captures the wallpaper layer as it is on screen
 * (com.nothing.launcher.setup.d0: WindowManagerGlobal.mirrorWallpaperSurface, reparented under
 * its own SurfaceControl, ScreenCaptureInternal.captureLayers; Android 16 names it
 * android.window.ScreenCapture), which the Launcher may do (it holds READ_FRAME_BUFFER, in
 * Nothing OS 4.1 too). The same is done here, again after a minute so a moving wallpaper's
 * glass follows it; if the capture fails, the glass is the tint alone.
 *
 * Not copied: Nothing moves the picture with the wallpaper's parallax and zoom
 * (Workspace.syncFrostedBackdropOffsets/Scale); here the picture is the wallpaper as the home
 * screen shows it at rest, centre-cropped to the screen, which is the same as long as the
 * wallpaper does not scroll.
 */
internal object Frosted {

    /**
     * Whether this launcher draws Nothing's frosted glass itself (Nothing OS 5 has
     * com.nothing.commBase.frosted). The module's glass is then left out, so the two never draw
     * over each other: on Nothing OS 5, Nothing's own theme style switches it.
     */
    fun nativeIn(cl: ClassLoader) = de.robv.android.xposed.XposedHelpers.findClassIfExists(
        "com.nothing.commBase.frosted.stdview.FrostedBackdropDrawable", cl) != null

    private const val DOWNSCALE = FrostedRecipe.DOWNSCALE
    /** Lucent's container_frosted_backdrop: white at 40 % in light mode, black at 40 % in dark. */
    const val DEFAULT_TINT_PERCENT = 40
    /** A live wallpaper's glass is captured again when it is older than this. */
    private const val LIVE_REFRESH_MS = 60_000L
    private const val RETRY_MS = 10_000L
    /** Nothing OS 5's settle delay before the capture (setup.d0.c). */
    private const val SETTLE_MS = 150L

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    /** The blurred wallpaper at a third of the screen's size, or null (tint only). */
    @Volatile private var picture: Bitmap? = null
    private var pictureWidth = 0
    private var pictureHeight = 0
    private var building = false
    /** When the picture was made, and when making one last failed (tried again after 10 s). */
    private var pictureTime = 0L
    private var failedTime = 0L
    private var live = false
    private var listening = false
    /**
     * Every surface that drew glass, with where it was on the screen when last looked at
     * (x, y, scale), held weakly. Each frame, a surface that has moved is drawn again (#follow).
     */
    private val tracked = WeakHashMap<View, FloatArray>()
    /** The windows' view roots already watched, one listener each. */
    private val roots: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())
    private val globalMatrix = Matrix()
    private val globalValues = FloatArray(9)

    /**
     * The paint that fills a surface with frosted glass: the picture lined up with the screen,
     * through Nothing's colour filter, or the tint alone. [screenX]/[screenY] are where the
     * canvas's (0, 0) is on the screen, [scale] the canvas's scale there. [surface] is
     * redrawn when the picture is ready.
     */
    private fun paint(surface: View, screenX: Float, screenY: Float, scale: Float,
            tintPercent: Int): Paint {
        val context = surface.context
        ensurePicture(context)
        val tint = tint(context, tintPercent)
        val bitmap = picture
        // Made again only when the picture or the tint changes, not at every frame; one paint
        // for every surface, used at once on the main thread.
        if (bitmap == null) {
            shared.shader = null
            shared.colorFilter = null
            shared.color = tint
            return shared
        }
        if (bitmap !== shaderBitmap) {
            shaderBitmap = bitmap
            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        if (tint != filterTint || filter == null) {
            filterTint = tint
            filter = FrostedRecipe.colourFilter(tint)
        }
        // One picture pixel covers DOWNSCALE screen pixels; the canvas starts at
        // (screenX, screenY) and may be scaled (a folder while it opens).
        position.setScale(DOWNSCALE / scale, DOWNSCALE / scale)
        position.postTranslate(-screenX / scale, -screenY / scale)
        shader!!.setLocalMatrix(position)
        shared.color = 0xFF000000.toInt()
        shared.shader = shader
        shared.colorFilter = filter
        return shared
    }

    private val shared = Paint(Paint.ANTI_ALIAS_FLAG)
    private val position = Matrix()
    private var shaderBitmap: Bitmap? = null
    private var shader: BitmapShader? = null
    private var filter: ColorMatrixColorFilter? = null
    private var filterTint = 0

    /** Lucent's frosted backdrop tint for the current light or dark mode, at [percent]. */
    private fun tint(context: Context, percent: Int): Int {
        val night = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val alpha = (percent.coerceIn(0, 100) * 255 + 50) / 100
        return (alpha shl 24) or (if (night) 0x000000 else 0xFFFFFF)
    }

    /**
     * The glass filling [surface]'s own canvas, lined up with the wallpaper from where the
     * surface is on the screen now, its parents' moves and scale included (the home screen
     * scales as it returns from an app).
     */
    fun paint(surface: View, tintPercent: Int): Paint {
        follow(surface)
        val at = screenTransform(surface) ?: floatArrayOf(0f, 0f, 1f)
        return paint(surface, at[0], at[1], at[2], tintPercent)
    }

    /** Where [view]'s (0, 0) is on the screen, and its scale there; null if not attached. */
    private fun screenTransform(view: View): FloatArray? {
        if (!view.isAttachedToWindow) {
            return null
        }
        // View-local to screen: every parent's place, scroll and transform, and the window's.
        globalMatrix.reset()
        view.transformMatrixToGlobal(globalMatrix)
        globalMatrix.getValues(globalValues)
        return floatArrayOf(globalValues[Matrix.MTRANS_X], globalValues[Matrix.MTRANS_Y],
            globalValues[Matrix.MSCALE_X])
    }

    /**
     * Keeps [surface]'s glass lined up with the wallpaper as it moves: every frame its window
     * draws, a glass surface whose place or scale on the screen changed since the last frame is
     * drawn again, in that same frame (a pre-draw listener runs before the frame is drawn).
     * Nothing OS 5 does the same by events (Workspace.syncFrostedBackdropOffsets on scroll,
     * FrostedCardWidgetHostView.setTranslationX/Y and onLayoutChange, FrostedBackdropHelper
     * .onLayoutChanged, a drop): this catches every move, the launcher's own animations
     * included, with one check per glass surface per frame.
     *
     * Mistake recorded (0.70.42, Dr.Essence's recording): until now only a page scroll redrew
     * the glass. Coming back to the home screen from an app, the launcher animates the home
     * screen in by scaling it, which moves the widgets without drawing them again: their glass
     * stayed as drawn mid-animation, showing the wallpaper from another place, until a swipe.
     */
    private fun follow(surface: View) {
        if (!tracked.containsKey(surface)) {
            tracked[surface] = screenTransform(surface) ?: FloatArray(3)
        }
        val root = surface.rootView
        if (roots.add(root)) {
            root.viewTreeObserver.addOnPreDrawListener {
                Hooks.guard("frosted glass follow") { moved(root) }.run()
                true
            }
        }
    }

    /** Draws again every glass surface in [root]'s window that moved since the last frame. */
    private fun moved(root: View) {
        for ((view, last) in tracked) {
            if (view.rootView !== root) {
                continue
            }
            val now = screenTransform(view) ?: continue
            if (now[0] != last[0] || now[1] != last[1] || now[2] != last[2]) {
                now.copyInto(last)
                view.invalidate()
            }
        }
    }

    /** Builds the picture once (again after the wallpaper or the screen size changes). */
    private fun ensurePicture(context: Context) {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels / DOWNSCALE
        val height = metrics.heightPixels / DOWNSCALE
        val now = SystemClock.uptimeMillis()
        val fresh = !live || now - pictureTime < LIVE_REFRESH_MS
        if (picture != null && width == pictureWidth && height == pictureHeight && fresh) {
            return
        }
        if (building || width <= 0 || height <= 0 || now - failedTime < RETRY_MS) {
            return
        }
        building = true
        listen(context)
        val app = context.applicationContext
        worker.execute {
            val built = try {
                build(app, width, height)
            } catch (t: Throwable) {
                Hooks.skipped("frosted glass picture", "the wallpaper could not be read: $t")
                null
            }
            main.post {
                building = false
                if (built == null) {
                    failedTime = SystemClock.uptimeMillis()
                    return@post
                }
                pictureTime = SystemClock.uptimeMillis()
                picture = built
                pictureWidth = width
                pictureHeight = height
                // Every glass surface shows the new picture (also a live wallpaper's refresh).
                ArrayList(tracked.keys).forEach { it.invalidate() }
            }
        }
    }

    /** A new wallpaper: a new picture, built the next time a surface draws. */
    private fun listen(context: Context) {
        if (listening) {
            return
        }
        listening = true
        WallpaperManager.getInstance(context).addOnColorsChangedListener({ _, _ ->
            picture = null
        }, main)
    }

    /** The wallpaper at a third of the screen's size, blurred: read, or captured if it is live. */
    private fun build(context: Context, width: Int, height: Int): Bitmap? {
        val manager = WallpaperManager.getInstance(context)
        live = manager.wallpaperInfo != null
        val bitmap = if (live) capture(context, width, height) else FrostedRecipe.read(manager, width, height)
        bitmap?.let { FrostedRecipe.blur(it) }
        return bitmap
    }

    /**
     * A live wallpaper as it is on screen now, captured the way Nothing OS 5 does it
     * (com.nothing.launcher.setup.d0.b/c): a mirror of the wallpaper's layer is put under a
     * layer of our own with setLayer(0) and Transaction.apply(true), which waits until it is
     * applied; then 150 ms to settle; then the layer is captured at full size and made 3
     * times smaller with createScaledBitmap (FrostedBackdropProvider.x). Nothing OS 5 first
     * also waits up to 3 s for the wallpaper to be visible; here a capture is only asked for
     * while a home screen surface draws, so it is.
     *
     * Mistake recorded (0.70.29, Dr.Essence's screenshots of 0.70.30: every glass surface fully
     * clear): the mirror was reparented with apply(), which does not wait, and captured at
     * once, so the capture was taken before the mirror was there: an empty, transparent
     * picture, and Nothing's colour filter keeps the picture's alpha, so the glass was
     * invisible at any tint. An empty capture now counts as a failure (the tint alone).
     */
    private fun capture(context: Context, width: Int, height: Int): Bitmap? {
        val globalClass = Class.forName("android.view.WindowManagerGlobal")
        val global = globalClass.getMethod("getInstance").invoke(null)
        val mirror = globalClass.getMethod("mirrorWallpaperSurface", Int::class.javaPrimitiveType)
            .invoke(global, context.display?.displayId ?: 0) as SurfaceControl? ?: return null
        val holder = SurfaceControl.Builder().setName("nlplusFrostedCapture").build()
        try {
            val move = SurfaceControl.Transaction().reparent(mirror, holder).setLayer(mirror, 0)
            // apply(true): applied synchronously (hidden API, as Nothing calls it).
            try {
                SurfaceControl.Transaction::class.java
                    .getMethod("apply", Boolean::class.javaPrimitiveType).invoke(move, true)
            } catch (e: ReflectiveOperationException) {
                move.apply()
            }
            Thread.sleep(SETTLE_MS)
            val screen = Rect(0, 0, width * DOWNSCALE, height * DOWNSCALE)
            val captureLayers = (try {
                Class.forName("android.window.ScreenCapture")
            } catch (e: ClassNotFoundException) {
                Class.forName("android.window.ScreenCaptureInternal") // Android 17
            }).getMethod("captureLayers", SurfaceControl::class.java, Rect::class.java,
                Float::class.javaPrimitiveType)
            val buffer = captureLayers.invoke(null, holder, screen, 1f) ?: return null
            val shot = buffer.javaClass.getMethod("asBitmap").invoke(buffer) as Bitmap? ?: return null
            // A hardware bitmap: copied to one whose pixels can be read, then made smaller.
            val full = shot.copy(Bitmap.Config.ARGB_8888, false)
            shot.recycle()
            val small = Bitmap.createScaledBitmap(full, width, height, true)
                .copy(Bitmap.Config.ARGB_8888, true)
            full.recycle()
            if (isEmpty(small)) {
                small.recycle()
                Hooks.skipped("frosted glass picture", "the live wallpaper capture was empty")
                return null
            }
            Hooks.log("frosted glass: live wallpaper captured, ${width}x$height")
            return small
        } finally {
            // Taken off the screen's layer tree, then released.
            SurfaceControl.Transaction().reparent(holder, null).apply()
            mirror.release()
            holder.release()
        }
    }

    /** Whether most of the picture is transparent: a capture that saw nothing. */
    private fun isEmpty(bitmap: Bitmap): Boolean {
        var clear = 0
        var seen = 0
        for (y in 0 until bitmap.height step 8) {
            for (x in 0 until bitmap.width step 8) {
                seen++
                if (bitmap.getPixel(x, y) ushr 24 < 16) clear++
            }
        }
        return seen == 0 || clear * 2 > seen
    }

}
