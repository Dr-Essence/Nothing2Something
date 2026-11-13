package com.dressence.nothinglauncherplus.launcher

import android.graphics.Canvas
import android.view.View
import com.dressence.nothinglauncherplus.xposed.ConfigStore
import com.dressence.nothinglauncherplus.xposed.Hooks
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.Collections
import java.util.WeakHashMap

/**
 * Frosted glass (Frosted.kt, Nothing OS 5's recipe) for the app drawer: its background, and a
 * drawer folder when it is open (0.70.38). Nothing OS 5 frosts neither (its drawer and
 * pop-ups stay solid); these extend its look to the drawer, each with its own switch and tint.
 *
 * Where each surface's colour comes from, read in the CMF Phone 1's Nothing OS 4.1 launcher
 * (Tetris_B4.1-260928-1140, dexdump):
 * - **An open drawer folder** is a SmartFullSheet, an AbstractSlideInView. Its solid colour is
 *   not in its layout (smart_folder_full_sheet has no background): it is the slide-in view's
 *   colour scrim, one full-screen View that AbstractSlideInView's constructor makes
 *   (createColorScrim: setBackgroundColor(getScrimColor), the sheet's
 *   color_all_apps_content_background_color) and fades in and out with the sheet. Its
 *   background becomes the glass; the fade stays Nothing's.
 * - **The drawer's background** is the launcher's scrim behind the drawer (DrawerBackground),
 *   which other screens use too. The glass is drawn instead at the bottom of the drawer itself
 *   (before its children), so it shows only in the drawer and fades with it; the drawer's own
 *   colours are faded out over it (DrawerBackground.opacity).
 *
 * Mistake recorded (0.70.38 to 0.70.43, Dr.Essence's recording): the drawer's glass was drawn
 * for where the drawer rests, not followed, so it lined up with the wallpaper only once the
 * drawer stopped and stayed off when it moved; and the drawer's own colour stayed over it in
 * the strip under the status bar. Both fixed in 0.70.44.
 */
internal object DrawerGlass {

    private const val SHEET = SmartDrawer.SMART_PKG + "SmartFullSheet"
    private const val SLIDE_IN = "com.android.launcher3.views.AbstractSlideInView"
    private const val ALL_APPS = "com.android.launcher3.allapps.ActivityAllAppsContainerView"

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

    /** Drawers seen drawing, redrawn when the setting changes (held weakly). */
    private val drawers: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())

    /** The drawer: the glass under everything it draws. */
    private fun installDrawer(cl: ClassLoader, store: ConfigStore) {
        val drawer = XposedHelpers.findClass(SmartDrawer.CLS_ALL_APPS_CONTAINER, cl)
        // Nothing's drawer does not declare dispatchDraw: Launcher3's ActivityAllAppsContainerView
        // does (4.1 launcher, dexdump), so that is hooked, for Nothing's drawer only. (Mistake
        // recorded, 0.70.38: hooked on Nothing's class, it failed to install on every start,
        // "NoSuchMethodError ... NTLauncherAllAppsContainerView#dispatchDraw", Dr.Essence's log.)
        XposedHelpers.findAndHookMethod(XposedHelpers.findClass(ALL_APPS, cl), "dispatchDraw",
            Canvas::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!drawer.isInstance(param.thisObject)) {
                        return
                    }
                    drawers.add(param.thisObject as View)
                    val config = store.get()
                    if (!config.drawerGlass) {
                        return
                    }
                    Hooks.guard("drawer glass draw") {
                        val view = param.thisObject as View
                        // Lined up from where the drawer is on the screen now, and drawn again
                        // each frame it moves (Frosted.follow), as the widgets' glass is.
                        val canvas = param.args[0] as Canvas
                        canvas.drawRect(0f, 0f, view.width.toFloat(), view.height.toFloat(),
                            Frosted.paint(view, config.drawerGlassTint))
                    }.run()
                }
            })
        // A new setting shows in an open drawer at once.
        store.addChangeListener { drawers.forEach(View::invalidate) }
    }
}
