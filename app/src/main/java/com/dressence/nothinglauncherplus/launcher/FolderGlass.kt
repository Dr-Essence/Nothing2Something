package com.dressence.nothinglauncherplus.launcher

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.View
import com.dressence.nothinglauncherplus.xposed.ConfigStore
import com.dressence.nothinglauncherplus.xposed.Hooks
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/**
 * Nothing OS 5's frosted open folder on Nothing OS 4.
 *
 * Nothing OS 5 (Phone (2a) Pacman_C5.0 Launcher, read with dexdump): the open folder's
 * background, com.nothing.launcher.folder.l, draws a FrostedBackdropDrawable inside its rounded
 * shape INSTEAD of its solid colour (l.draw: frosted when l.y(), else drawRoundRect with its
 * paint). Folder.dispatchDraw first calls Launcher3's drawFolderBlur, which does nothing there
 * (Flags.blurOnMoreSurfaces is compiled to false), then that background, then the icons.
 *
 * Here: Nothing OS 4.1's folder background (4.0.15: com.nothing.launcher.folder.i, renamed in
 * every build, so taken from Folder.mBackground, never by name) draws its rounded shape with
 * its solid colour, animated as it opens and closes. That shape is drawn into a layer, and the
 * frosted glass (Frosted.kt, Nothing's recipe) is put into it with SRC_IN: the glass takes
 * exactly Nothing's shape, size and fade at every frame, and replaces only the colour, as on
 * Nothing OS 5. Nothing's drawing itself is not changed or repeated.
 *
 * Mistake recorded (0.70.27): the first version copied Android's Launcher3 folder blur (a
 * blurred recording of the home screen over Android's window blur, under Nothing's colour at
 * 40 %). Read further, Nothing OS 5 never runs that code; its glass is the frosted wallpaper.
 */
internal object FolderGlass {

    private const val FOLDER = "com.android.launcher3.folder.Folder"

    private val hookedBackgrounds = HashSet<Class<*>>()
    private val replace = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
    /** The folder drawing now (set around its dispatchDraw), whose background is drawn. */
    private var drawing: View? = null

    @JvmStatic
    fun install(cl: ClassLoader, store: ConfigStore) {
        if (Frosted.nativeIn(cl)) {
            Hooks.skipped("folder glass", "Nothing OS 5 has its own frosted glass")
            return
        }
        val folder = XposedHelpers.findClass(FOLDER, cl)
        // Looked up now: if an update renames it, the feature is left out here, once, with the
        // reason in Hook health, instead of failing at every frame.
        XposedHelpers.findField(folder, "mBackground")
        XposedHelpers.findAndHookMethod(folder, "dispatchDraw", Canvas::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    drawing = param.thisObject as View
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    drawing = null
                }
            })
        // Nothing's background class is known only from a folder: hooked when the first one
        // is made.
        XposedHelpers.findAndHookMethod(folder, "onFinishInflate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                Hooks.guard("folder glass") { hookBackground(param.thisObject as View, store) }.run()
            }
        })
    }

    private fun hookBackground(folder: View, store: ConfigStore) {
        val type = XposedHelpers.getObjectField(folder, "mBackground")?.javaClass ?: return
        if (!hookedBackgrounds.add(type)) {
            return
        }
        XposedHelpers.findAndHookMethod(type, "draw", Canvas::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val config = store.get()
                if (!config.folderGlass) {
                    return
                }
                val canvas = param.args[0] as Canvas
                // Only the background of the folder drawing now, nothing else of that class.
                val owner = drawing ?: return
                if (param.thisObject !== XposedHelpers.getObjectField(owner, "mBackground")) {
                    return
                }
                // A layer the size of the folder: Nothing's shape may grow past its bounds
                // while it animates.
                val area = RectF(0f, 0f, owner.width.toFloat(), owner.height.toFloat())
                param.setObjectExtra("layer", canvas.saveLayer(area, null))
                param.setObjectExtra("area", area)
                param.setObjectExtra("owner", owner)
                param.setObjectExtra("tint", config.folderGlassTint)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val layer = param.getObjectExtra("layer") as Int? ?: return
                val canvas = param.args[0] as Canvas
                Hooks.guard("folder glass draw") {
                    val owner = param.getObjectExtra("owner") as View
                    val glass = Frosted.paint(owner, param.getObjectExtra("tint") as Int)
                    glass.xfermode = replace.xfermode
                    canvas.drawRect(param.getObjectExtra("area") as RectF, glass)
                    glass.xfermode = null
                }.run()
                canvas.restoreToCount(layer)
            }
        })
    }
}
