package com.dressence.nothinglauncherplus.launcher

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import com.dressence.nothinglauncherplus.xposed.ConfigStore
import com.dressence.nothinglauncherplus.xposed.Hooks
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.WeakHashMap

/**
 * Nothing OS 5's glass folders on Nothing OS 4 (0.70.27).
 *
 * How Nothing OS 5 does it (its Launcher, Phone (2a) Pacman_C5.0, class
 * com.android.launcher3.util.QuickstepBackgroundBlurHelper, read with dexdump): when a folder
 * starts to open it records the home screen behind it into a RenderNode; while the folder
 * draws, it puts two layers under the folder's own background, inside the folder's shape:
 *   1. Android's BackgroundBlurDrawable (ViewRootImpl.createBackgroundBlurDrawable), which
 *      blurs what is behind the launcher's window: the wallpaper;
 *   2. that recording, blurred with RenderEffect.createBlurEffect, so the icons and widgets
 *      behind the folder show as soft glass instead of sharp through the tint.
 * Both at folder_blur_radius, 20 dp. When the folder has closed it drops the recording and
 * hides the blur. Nothing OS 4.1's Launcher (4.0.15 in the firmware, 4.0.20 on the CMF Phone 1)
 * has none of this, so nothing of Nothing's is switched off or drawn over: the two layers go in
 * just before Nothing's own background in Folder.dispatchDraw, and that background, the same
 * colour as always, is drawn at 40 % so the glass shows through (Nothing OS 5's frosted
 * backdrop, lucent_fallback_container_frosted_backdrop, is 40 %: #66FFFFFF / #66000000).
 *
 * Only AOSP Launcher3 names are used (Folder, its fields mBackground and mClipPath, and the
 * methods animateOpen, dispatchDraw, closeComplete): R8 keeps them, while Nothing's own folder
 * background class is renamed in every build (4.0.15: com.nothing.launcher.folder.i), so it is
 * taken from the folder itself, never by name.
 *
 * When the phone cannot blur now (battery saver, or blur not supported), Nothing's folder is
 * left exactly as it is: no glass and no change to its background.
 */
internal object FolderGlass {

    private const val FOLDER = "com.android.launcher3.folder.Folder"
    private const val BLUR_UTILS = "com.android.systemui.shared.system.BlurUtils"
    private const val CROSS_WINDOW_BLUR = "android.view.CrossWindowBlurListeners"

    /** Nothing OS 5's folder_blur_radius. */
    private const val BLUR_RADIUS_DP = 20f
    /** Nothing's folder colour at 40 %: Nothing OS 5's frosted backdrop alpha (0x66). */
    private const val BACKGROUND_ALPHA = 0x66
    /** The folder background's corner, if the launcher's own dimension is not found. */
    private const val FALLBACK_CORNER_DP = 16f

    /** One open folder's glass: the recording of what is behind it, and the window blur. */
    private class Glass {
        val behind = RenderNode("nlplusFolderGlass")
        var windowBlur: Drawable? = null
    }

    private val glasses = WeakHashMap<View, Glass>()
    private val hookedBackgrounds = HashSet<Class<*>>()
    /** The folder whose background is being drawn under glass now (set only around it). */
    private var drawingUnderGlass: View? = null
    /** Whether this launcher build blurs at all (BlurUtils.supportsBlursOnWindows). */
    private var supported = false
    private var crossWindowBlur: Any? = null
    /** Set when Android refused the window blur once: not asked again on every frame. */
    private var windowBlurRefused = false

    @JvmStatic
    fun install(cl: ClassLoader, store: ConfigStore) {
        val folder = XposedHelpers.findClass(FOLDER, cl)
        // Every field the draw reads, looked up now: if an update renames one, the feature is
        // left out here, once, with the reason in Hook health, instead of failing every frame.
        for (field in arrayOf("mBackground", "mClipPath", "mActivityContext")) {
            XposedHelpers.findField(folder, field)
        }
        // The launcher's own answer: blur is supported by the display, the device is not low
        // on graphics, and persist.sysui.disableBlur is not set. Fixed for this boot.
        supported = XposedHelpers.callStaticMethod(
            XposedHelpers.findClass(BLUR_UTILS, cl), "supportsBlursOnWindows") as Boolean
        if (!supported) {
            Hooks.skipped("folder glass", "this phone does not blur windows")
            return
        }
        // Whether blur is on right now (battery saver and the "disable window blurs" setting
        // switch it off): Android's own listener, read at each draw.
        crossWindowBlur = XposedHelpers.callStaticMethod(
            XposedHelpers.findClass(CROSS_WINDOW_BLUR, null), "getInstance")

        fun active() = store.get().folderGlass && blurOnNow()

        // 1. Opening: record what is behind the folder, before it is added to the drag layer.
        XposedBridge.hookAllMethods(folder, "animateOpen", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args.size == 2 && active()) {
                    Hooks.guard("folder glass open") { record(param.thisObject as View) }.run()
                }
            }
        })

        // 2. Drawing: the glass first, then Nothing's background (at 40 %) and the icons.
        XposedHelpers.findAndHookMethod(folder, "dispatchDraw", Canvas::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as View
                    Hooks.guard("folder glass draw") {
                        if (active()) {
                            hookBackground(view)
                            drawGlass(view, param.args[0] as Canvas)
                            drawingUnderGlass = view
                        } else {
                            glasses[view]?.windowBlur?.setVisible(false, false)
                        }
                    }.run()
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    drawingUnderGlass = null
                }
            })

        // 3. Closed (or taken off the screen): the recording and the blur go.
        val release = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                Hooks.guard("folder glass close") { release(param.thisObject as View) }.run()
            }
        }
        XposedBridge.hookAllMethods(folder, "closeComplete", release)
        XposedHelpers.findAndHookMethod(folder, "onDetachedFromWindow", release)
    }

    private fun blurOnNow(): Boolean = try {
        XposedHelpers.callMethod(crossWindowBlur, "isCrossWindowBlurEnabled") as Boolean
    } catch (t: Throwable) {
        false
    }

    /**
     * Records the drag layer (the home screen, its widgets, the dock) as it is now into this
     * folder's RenderNode: a list of drawing commands, not a picture, so it costs little memory.
     * A folder still attached (opened again while closing) is left without the recording, so
     * it never records itself.
     */
    private fun record(folder: View) {
        if (folder.parent != null) {
            return
        }
        val dragLayer = XposedHelpers.callMethod(
            XposedHelpers.getObjectField(folder, "mActivityContext"), "getDragLayer") as View
        if (dragLayer.width == 0 || dragLayer.height == 0) {
            return
        }
        val glass = glasses.getOrPut(folder) { Glass() }
        val canvas = glass.behind.beginRecording(dragLayer.width, dragLayer.height)
        try {
            dragLayer.draw(canvas)
        } finally {
            glass.behind.endRecording()
        }
        glass.behind.setPosition(0, 0, dragLayer.width, dragLayer.height)
        val radius = dp(folder, BLUR_RADIUS_DP)
        glass.behind.setRenderEffect(
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
    }

    /** The two glass layers, inside the folder's shape, under everything the folder draws. */
    private fun drawGlass(folder: View, canvas: Canvas) {
        if (!canvas.isHardwareAccelerated) {
            return
        }
        val glass = glasses.getOrPut(folder) { Glass() }
        val background = XposedHelpers.getObjectField(folder, "mBackground") as Drawable
        // The shape: the opening animation's reveal path while it runs, else the background.
        val clip = XposedHelpers.getObjectField(folder, "mClipPath") as Path?
        val corner = corner(folder)
        val bounds = RectF()
        if (clip != null) clip.computeBounds(bounds, true) else bounds.set(background.bounds)
        if (bounds.isEmpty) {
            return
        }

        // Layer 1: the wallpaper behind the window, blurred by Android itself.
        val windowBlur = glass.windowBlur ?: createWindowBlur(folder)?.also { glass.windowBlur = it }
        if (windowBlur != null) {
            val rect = Rect()
            bounds.roundOut(rect)
            windowBlur.bounds = rect
            XposedHelpers.callMethod(windowBlur, "setCornerRadius", corner)
            windowBlur.setVisible(true, false)
            windowBlur.draw(canvas)
        }

        // Layer 2: the home screen behind the folder, blurred. The canvas is in the folder's
        // own coordinates (moved and scaled by its opening animation); the recording is in the
        // drag layer's, so the folder's transform is undone for it.
        if (glass.behind.hasDisplayList()) {
            val saved = canvas.save()
            if (clip != null) {
                canvas.clipPath(clip)
            } else {
                canvas.clipPath(Path().apply { addRoundRect(bounds, corner, corner, Path.Direction.CW) })
            }
            val undo = Matrix()
            if (folder.matrix.invert(undo)) {
                canvas.concat(undo)
            }
            canvas.translate(-folder.left.toFloat(), -folder.top.toFloat())
            canvas.drawRenderNode(glass.behind)
            canvas.restoreToCount(saved)
        }
    }

    /** Android's window blur for this folder's window, or null if the launcher may not have it. */
    private fun createWindowBlur(folder: View): Drawable? = if (windowBlurRefused) null else try {
        val root = XposedHelpers.callMethod(folder, "getViewRootImpl")
        (XposedHelpers.callMethod(root, "createBackgroundBlurDrawable") as Drawable).also {
            XposedHelpers.callMethod(it, "setBlurRadius", dp(folder, BLUR_RADIUS_DP).toInt())
        }
    } catch (t: Throwable) {
        windowBlurRefused = true
        Hooks.skipped("folder glass window blur", "$t")
        null
    }

    /**
     * Nothing's folder background class, hooked once: while it draws under glass, it is drawn
     * into a layer at 40 %, so its own colour and its own fade-in stay as they are (the alphas
     * multiply) and nothing it set is changed.
     */
    private fun hookBackground(folder: View) {
        val type = XposedHelpers.getObjectField(folder, "mBackground")?.javaClass ?: return
        if (!hookedBackgrounds.add(type)) {
            return
        }
        XposedHelpers.findAndHookMethod(type, "draw", Canvas::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                // Only the folder's own background, not anything else drawn inside it.
                val folder = drawingUnderGlass ?: return
                if (param.thisObject !== XposedHelpers.getObjectField(folder, "mBackground")) {
                    return
                }
                val canvas = param.args[0] as Canvas
                val bounds = RectF((param.thisObject as Drawable).bounds)
                param.setObjectExtra("saved", canvas.saveLayerAlpha(bounds, BACKGROUND_ALPHA))
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                (param.getObjectExtra("saved") as Int?)?.let { (param.args[0] as Canvas).restoreToCount(it) }
            }
        })
    }

    private fun release(folder: View) {
        val glass = glasses[folder] ?: return
        glass.behind.discardDisplayList()
        glass.windowBlur?.setVisible(false, false)
    }

    /** The folder background's corner radius: Launcher3's folder_bg_round_rect_radius. */
    private fun corner(folder: View): Float {
        val res = folder.resources
        val id = res.getIdentifier("folder_bg_round_rect_radius", "dimen", folder.context.packageName)
        return if (id != 0) res.getDimension(id) else dp(folder, FALLBACK_CORNER_DP)
    }

    private fun dp(view: View, dp: Float) = dp * view.resources.displayMetrics.density
}
