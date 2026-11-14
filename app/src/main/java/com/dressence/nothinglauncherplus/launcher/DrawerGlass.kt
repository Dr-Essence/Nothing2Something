package com.dressence.nothinglauncherplus.launcher

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.SurfaceControl
import android.view.View
import android.view.WindowManager
import com.dressence.nothinglauncherplus.Config
import java.lang.ref.WeakReference
import kotlin.math.roundToInt
import com.dressence.nothinglauncherplus.xposed.ConfigStore
import com.dressence.nothinglauncherplus.xposed.Hooks
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Glass for the app drawer: its background, and a drawer folder when it is open (0.70.38).
 * Nothing OS 5 frosts neither (its drawer and pop-ups stay solid); these extend its look to the
 * drawer, each with its own switch and tint.
 *
 * Where each surface's colour comes from, read in the CMF Phone 1's Nothing OS 4.1 launcher
 * (Tetris_B4.1-260928-1140, dexdump):
 * - **An open drawer folder** is a SmartFullSheet, an AbstractSlideInView. Its solid colour is
 *   not in its layout (smart_folder_full_sheet has no background): it is the slide-in view's
 *   colour scrim, one full-screen View that AbstractSlideInView's constructor makes
 *   (createColorScrim: setBackgroundColor(getScrimColor), the sheet's
 *   color_all_apps_content_background_color) and fades in and out with the sheet. Its
 *   background becomes the frosted glass (Frosted.kt): it must hide the drawer's apps under
 *   the folder, which a see-through blur would not. The fade stays Nothing's.
 * - **The drawer's background**, in one of two looks (Glass drawer look):
 *   Nothing OS 5's frosted glass ([installFrosted], the default), the frosted picture drawn
 *   as the launcher's ScrimView; or a live blur of the wallpaper behind the launcher's window,
 *   made by Android itself ([installDrawer]), the way the quick settings panel blurs, under
 *   the scrim in the tint. Either way every other colour the drawer paints from its scrim
 *   colour (the search's sheet, its band under the search bar, its page) is cleared, so
 *   nothing lies over the glass as a dark strip (DrawerBackground).
 *
 * Mistakes recorded:
 * - 0.70.38 to 0.70.43 (Dr.Essence's recording): the drawer's glass was drawn for where the
 *   drawer rests, not followed, so it was off the wallpaper while the drawer moved.
 * - 0.70.38 to 0.70.44 (Dr.Essence's screenshots, 0.70.45): it was drawn inside the drawer's
 *   view (ActivityAllAppsContainerView), which only covers the folders' area; 0.70.44 faded the
 *   scrim out behind it, so outside that area the bare wallpaper showed once the opening ended.
 * - 0.70.45 (Dr.Essence, 0.70.46): the glass was the frosted picture (a still, small blur of
 *   the wallpaper) drawn as the scrim, at the Drawer background opacity: at a strong opacity
 *   the search's band under its bar came back as a black strip, and the drawer looked like the
 *   module's widget glass, not the panel's live see-through. The black strip came from
 *   Nothing's colours left over the glass; they are cleared now. 0.70.53 (Dr.Essence: the
 *   drawer should be Nothing OS 5's frosted glass, as first asked) brings the frosted look
 *   back as the default, at Nothing's tint over the whole screen, with the live blur kept as
 *   the other look to compare.
 */
internal object DrawerGlass {

    private const val SHEET = SmartDrawer.SMART_PKG + "SmartFullSheet"
    private const val SLIDE_IN = "com.android.launcher3.views.AbstractSlideInView"
    private const val SCRIM_VIEW = "com.android.launcher3.views.ScrimView"
    private const val DEPTH_CONTROLLER = "com.android.quickstep.util.BaseDepthController"
    private const val SYSTEM_UI = "com.android.systemui"
    private const val LAUNCHER_STATE = "com.android.launcher3.LauncherState"

    @JvmStatic
    fun install(cl: ClassLoader, store: ConfigStore) {
        if (Frosted.nativeIn(cl)) {
            for (part in arrayOf("drawer glass", "drawer folder glass")) {
                Hooks.skipped(part, "Nothing OS 5's frosted look is left to Nothing")
            }
            return
        }
        Hooks.install("drawer folder glass") { installFolderSheet(cl, store) }
        Hooks.install("drawer glass") { installDrawer(cl, store) }
        Hooks.install("drawer frosted glass") { installFrosted(cl, store) }
    }

    /** The open drawer folder: its colour scrim's background becomes the glass. */
    private fun installFolderSheet(cl: ClassLoader, store: ConfigStore) {
        val sheet = XposedHelpers.findClass(SHEET, cl)
        // Looked up now, so a renamed field leaves the feature out once, with the reason.
        XposedHelpers.findField(XposedHelpers.findClass(SLIDE_IN, cl), "mColorScrim")
        // Each folder opened is a new sheet: the setting in force when it opens applies.
        XposedBridge.hookAllConstructors(sheet, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val config = store.get()
                if (!config.drawerFolderGlass) {
                    return
                }
                Hooks.guard("drawer folder glass") {
                    val scrim = XposedHelpers.getObjectField(param.thisObject, "mColorScrim")
                        as View? ?: return@guard
                    val colour = scrim.background ?: return@guard
                    // Its constructors call one another (Kotlin's defaults), so this runs more
                    // than once for one sheet: the glass is put in once.
                    if (colour is GlassBackground) {
                        return@guard
                    }
                    // A full-screen sheet: square corners.
                    scrim.background = GlassBackground(scrim, colour, config.drawerFolderGlassTint, 0f)
                }.run()
            }
        })
    }

    /**
     * The drawer's background: a live blur of the wallpaper behind the launcher's window, made
     * by Android itself (SurfaceFlinger), the way the quick settings panel blurs what is under
     * it. Launcher3 does this in BaseDepthController.applyDepthAndBlur, run each time the
     * launcher's "depth" moves (0 on the home screen, AllAppsState.getDepthUnchecked = 2 with
     * the drawer open on a phone, animated between): Nothing's 4.1 launcher keeps the
     * controller, its surface and its blur fields, but its applyDepthAndBlur only zooms the
     * wallpaper (setWallpaperZoomOut); the blur was left out. Here it is put back after
     * Nothing's own step, on the same depth, so it fades in and out with the drawer.
     */
    private fun installDrawer(cl: ClassLoader, store: ConfigStore) {
        val controller = XposedHelpers.findClass(DEPTH_CONTROLLER, cl)
        // Looked up now, so a renamed field leaves the feature out once, with the reason.
        XposedHelpers.findField(controller, "mDepth")
        XposedHelpers.findField(controller, "mLauncher")
        val allApps = XposedHelpers.getStaticObjectField(
            XposedHelpers.findClass(LAUNCHER_STATE, cl), "ALL_APPS")
        val blur = DrawerBlur(store, allApps)
        XposedHelpers.findAndHookMethod(controller, "applyDepthAndBlur", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                Hooks.guard("drawer glass") { blur.apply(param.thisObject) }.run()
            }
        })
        // Switched on or off with the drawer open: applied at once, not at the next move.
        store.addChangeListener { Hooks.guard("drawer glass") { blur.applyAgain() }.run() }
        installed = true
    }

    /** True once the drawer's live blur is in (not on Nothing OS 5, nor if a hook failed). */
    @Volatile
    private var installed = false

    /**
     * The drawer is open, opening or closing: the launcher's state is ALL_APPS, or it is still
     * the last settled one (while the drawer closes, the state is already the next).
     */
    private fun drawerShown(launcher: Context, allApps: Any): Boolean = try {
        val states = XposedHelpers.callMethod(launcher, "getStateManager")
        XposedHelpers.callMethod(states, "getState") === allApps ||
            XposedHelpers.callMethod(states, "getCurrentStableState") === allApps
    } catch (t: Throwable) {
        false
    }

    /**
     * The drawer's look Nothing OS 5's frosted glass (Glass drawer background, look "Nothing
     * OS 5 frosted", the default since 0.70.53): Nothing's frosted picture of the wallpaper
     * (Frosted.kt; a live wallpaper captured as Nothing OS 5 does, again each minute) drawn as
     * the launcher's ScrimView, the full-screen view behind the drawer whose colour the
     * launcher animates with its state. The scrim takes a [FrostedScrim] the first time its
     * colour is set (ScrimView.setBackgroundColor, declared there in the 4.1 launcher); Android's
     * View.setBackgroundColor then changes that ColorDrawable's colour in place, so the
     * launcher's own fade reaches the glass. While the drawer is frosted its scrim colour is
     * opaque ([scrimColour]), so the fade runs the glass from clear to full; the tint is
     * Nothing's own (Lucent: white in light mode, black in dark, at the slider).
     */
    private fun installFrosted(cl: ClassLoader, store: ConfigStore) {
        val scrim = XposedHelpers.findClass(SCRIM_VIEW, cl)
        val allApps = XposedHelpers.getStaticObjectField(
            XposedHelpers.findClass(LAUNCHER_STATE, cl), "ALL_APPS")
        XposedHelpers.findAndHookMethod(scrim, "setBackgroundColor", Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as View
                    if (view.background is FrostedScrim) {
                        return
                    }
                    Hooks.guard("drawer frosted glass") {
                        view.background = FrostedScrim(view, param.args[0] as Int, store, allApps)
                    }.run()
                }
            })
        installed = true
    }

    /**
     * The scrim's colour, drawn as Nothing's frosted glass at the colour's opacity while the
     * drawer shows with the frosted look, as the colour itself otherwise (other screens use
     * the scrim too).
     */
    private class FrostedScrim(
        private val view: View,
        colour: Int,
        private val store: ConfigStore,
        private val allApps: Any,
    ) : ColorDrawable(colour) {

        override fun draw(canvas: Canvas) {
            val config = store.get()
            val alpha = Color.alpha(color)
            if (!drawerIsGlass(config) || config.drawerGlassLook != Config.DRAWER_GLASS_FROSTED ||
                    alpha == 0 || !drawerShown(view.context, allApps)) {
                super.draw(canvas)
                return
            }
            val glass = Frosted.paint(view, config.drawerGlassTint)
            glass.alpha = alpha
            canvas.drawRect(bounds, glass)
        }
    }

    /**
     * The drawer's scrim colour while it is glass (AllAppsState.getWorkspaceScrimColor,
     * DrawerBackground): opaque for the frosted look, whose glass takes the colour's opacity
     * as its own; the tint itself for the live blur, which shows through it.
     */
    @JvmStatic
    fun scrimColour(context: Context, config: Config): Int =
        if (config.drawerGlassLook == Config.DRAWER_GLASS_FROSTED) 0xFF000000.toInt()
        else tint(context, config)

    /**
     * Whether the drawer is glass now: its own colours (DrawerBackground) are then all clear,
     * the search's band and page included, and only the tint ([tint]) lies over the blur.
     */
    @JvmStatic
    fun drawerIsGlass(config: Config): Boolean = installed && config.drawerGlass

    /**
     * The drawer's tint over the blur: black in dark mode, white in light, at the Glass drawer
     * background slider (Nothing OS 5's Lucent tint is 40 %).
     */
    @JvmStatic
    fun tint(context: Context, config: Config): Int {
        val night = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val alpha = (config.drawerGlassTint.coerceIn(0, 100) * 255 + 50) / 100
        return (alpha shl 24) or (if (night) 0x000000 else 0xFFFFFF)
    }

    /** Puts the blur radius for the current depth on the launcher window's surface. */
    private class DrawerBlur(private val store: ConfigStore, private val allApps: Any) {
        private var controller: WeakReference<Any>? = null
        private var surface: WeakReference<SurfaceControl>? = null
        private var radius = 0
        private var maxRadius = 0

        fun applyAgain() {
            controller?.get()?.let { apply(it) }
        }

        fun apply(controller: Any) {
            this.controller = WeakReference(controller)
            val launcher = XposedHelpers.getObjectField(controller, "mLauncher") as Activity
            val depth = XposedHelpers.getFloatField(controller, "mDepth")
            val wanted = radiusFor(launcher, depth)
            val target = surfaceOf(launcher) ?: return
            if (target === surface?.get() && wanted == radius) {
                return // Most depth steps outside the drawer: nothing to change.
            }
            val change = SurfaceControl.Transaction()
            // Hidden API (Launcher3 calls it too): the blur of what is behind this surface.
            XposedHelpers.callMethod(change, "setBackgroundBlurRadius", target, wanted)
            change.apply()
            surface = WeakReference(target)
            radius = wanted
        }

        /**
         * Full blur at the drawer's depth, less on the way, none outside the drawer, with the
         * setting off, or while Android has blurs off (battery saver, heat, Settings: the
         * tint alone stays, as Nothing OS 5's tint-only glass).
         */
        private fun radiusFor(launcher: Activity, depth: Float): Int {
            val config = store.get()
            if (!config.drawerGlass || config.drawerGlassLook != Config.DRAWER_GLASS_LIVE ||
                    !drawerShown(launcher, allApps)) {
                return 0
            }
            val windows = launcher.getSystemService(WindowManager::class.java)
            if (windows == null || !windows.isCrossWindowBlurEnabled) {
                return 0
            }
            val open = (XposedHelpers.callMethod(allApps, "getDepth", launcher) as Float)
                .takeIf { it > 0f } ?: 1f
            return (maxRadius(launcher) * (depth / open).coerceIn(0f, 1f)).roundToInt()
        }

        /**
         * The quick settings panel's own blur strength (System UI's max_window_blur_radius),
         * so the drawer looks like the panel; Launcher3's max_depth_blur_radius (23) if System
         * UI's cannot be read.
         */
        private fun maxRadius(launcher: Activity): Int {
            if (maxRadius == 0) {
                maxRadius = try {
                    val ui = launcher.packageManager.getResourcesForApplication(SYSTEM_UI)
                    val id = ui.getIdentifier("max_window_blur_radius", "dimen", SYSTEM_UI)
                    if (id != 0) ui.getDimensionPixelSize(id) else 0
                } catch (t: Throwable) {
                    0
                }
                if (maxRadius <= 0) {
                    maxRadius = launcher.resources.getInteger(launcher.resources.getIdentifier(
                        "max_depth_blur_radius", "integer", launcher.packageName)).coerceAtLeast(1)
                }
                Hooks.log("drawer glass: live blur up to $maxRadius px")
            }
            return maxRadius
        }

        /** The launcher window's surface (what Nothing's DepthController.onLauncherDraw uses). */
        private fun surfaceOf(launcher: Activity): SurfaceControl? {
            val root = XposedHelpers.callMethod(launcher.window.decorView, "getViewRootImpl")
                ?: return null
            val control = XposedHelpers.callMethod(root, "getSurfaceControl") as SurfaceControl?
            return control?.takeIf { it.isValid }
        }
    }
}
