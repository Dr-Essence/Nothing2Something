package com.dressence.nothinglauncherplus.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import com.dressence.nothinglauncherplus.xposed.ConfigStore
import com.dressence.nothinglauncherplus.xposed.Hooks
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

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
 * - **The drawer's background** is the launcher's ScrimView, one full-screen view behind the
 *   drawer whose colour the launcher animates with its state (setBackgroundColor, the drawer's
 *   scrim colour at the Drawer background opacity; DrawerBackground). Its background becomes a
 *   [GlassScrim]: the glass at the scrim's own opacity while the drawer is open or opening, its
 *   plain colour otherwise (other screens use the scrim too). So the glass covers the whole
 *   screen, fades exactly as Nothing's colour does, and the Drawer background slider sets how
 *   strong it is.
 *
 * Mistakes recorded:
 * - 0.70.38 to 0.70.43 (Dr.Essence's recording): the drawer's glass was drawn for where the
 *   drawer rests, not followed, so it was off the wallpaper while the drawer moved.
 * - 0.70.38 to 0.70.44 (Dr.Essence's screenshots, 0.70.45): it was drawn inside the drawer's
 *   view (ActivityAllAppsContainerView), which only covers the folders' area; 0.70.44 faded the
 *   scrim out behind it, so outside that area the bare wallpaper showed once the opening ended.
 *   The glass is the scrim's now, and the scrim keeps its colour when the glass is off.
 */
internal object DrawerGlass {

    private const val SHEET = SmartDrawer.SMART_PKG + "SmartFullSheet"
    private const val SLIDE_IN = "com.android.launcher3.views.AbstractSlideInView"
    private const val SCRIM_VIEW = "com.android.launcher3.views.ScrimView"
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
     * The drawer's background: the launcher's ScrimView takes a [GlassScrim] as its background
     * the first time its colour is set (ScrimView.setBackgroundColor, declared there in the 4.1
     * launcher: super.setBackgroundColor, then mBackgroundColor). Android's
     * View.setBackgroundColor changes the colour of a ColorDrawable background in place, so
     * every later colour, the launcher's animation included, reaches the glass.
     */
    private fun installDrawer(cl: ClassLoader, store: ConfigStore) {
        val scrim = XposedHelpers.findClass(SCRIM_VIEW, cl)
        val allApps = XposedHelpers.getStaticObjectField(
            XposedHelpers.findClass(LAUNCHER_STATE, cl), "ALL_APPS")
        XposedHelpers.findAndHookMethod(scrim, "setBackgroundColor", Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as View
                    if (view.background is GlassScrim) {
                        return
                    }
                    Hooks.guard("drawer glass") {
                        view.background = GlassScrim(view, param.args[0] as Int, store, allApps)
                    }.run()
                }
            })
    }

    /**
     * The scrim's colour, drawn as glass at the colour's opacity while the drawer shows, as the
     * colour itself otherwise. A ColorDrawable, so the scrim's setBackgroundColor keeps
     * changing its colour in place.
     */
    private class GlassScrim(
        private val view: View,
        colour: Int,
        private val store: ConfigStore,
        private val allApps: Any,
    ) : ColorDrawable(colour) {

        override fun draw(canvas: Canvas) {
            val config = store.get()
            val alpha = Color.alpha(color)
            if (!config.drawerGlass || alpha == 0 || !drawerShown()) {
                super.draw(canvas)
                return
            }
            val glass = Frosted.paint(view, config.drawerGlassTint)
            glass.alpha = alpha
            canvas.drawRect(bounds, glass)
        }

        /**
         * The drawer is open, opening or closing: the launcher's state is ALL_APPS, or it is
         * still the last settled one (while the drawer closes, the state is already the next).
         */
        private fun drawerShown(): Boolean = try {
            val states = XposedHelpers.callMethod(view.context, "getStateManager")
            XposedHelpers.callMethod(states, "getState") === allApps ||
                XposedHelpers.callMethod(states, "getCurrentStableState") === allApps
        } catch (t: Throwable) {
            false
        }
    }
}
