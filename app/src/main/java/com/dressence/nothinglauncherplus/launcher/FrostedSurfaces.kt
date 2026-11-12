package com.dressence.nothinglauncherplus.launcher

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.dressence.nothinglauncherplus.xposed.ConfigStore
import com.dressence.nothinglauncherplus.xposed.Hooks
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.Collections
import java.util.WeakHashMap

/**
 * Nothing OS 5's frosted closed folders and dock search bar on Nothing OS 4 (0.70.29), drawn
 * with Frosted.kt (Nothing's recipe). Each copies what the Phone (2a)'s Nothing OS 5.0 Launcher
 * does, read with dexdump.
 */
internal object FrostedSurfaces {

    private const val PREVIEW_BACKGROUND = "com.android.launcher3.folder.PreviewBackground"
    private const val HOTSEAT = "com.android.launcher3.Hotseat"
    private const val PAGED_VIEW = "com.android.launcher3.PagedView"

    private val location = IntArray(2)
    private val shape = Path()
    /** Folder icons that drew glass: redrawn while the home screen scrolls. */
    private val glassIcons: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())

    @JvmStatic
    fun install(cl: ClassLoader, store: ConfigStore) {
        Hooks.install("folder icon glass") { installFolderIcons(cl, store) }
        Hooks.install("search bar glass") { installSearchBar(cl, store) }
    }

    /**
     * A closed folder's background. Nothing OS 5's PreviewBackground.drawBackground: when
     * frosted, the icon's shape (getShape(circleType).addToPath at getOffsetX/Y and
     * getScaledRadius) is clipped and the frosted glass drawn in it instead of the solid
     * fill, then drawShadow as always. The same steps here, by the same AOSP names.
     */
    private fun installFolderIcons(cl: ClassLoader, store: ConfigStore) {
        val preview = XposedHelpers.findClass(PREVIEW_BACKGROUND, cl)
        for (field in arrayOf("circleType", "mInvalidateDelegate", "mDrawingDelegate")) {
            XposedHelpers.findField(preview, field)
        }
        XposedHelpers.findAndHookMethod(preview, "drawBackground", Canvas::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val config = store.get()
                    if (!config.folderIconGlass) {
                        return
                    }
                    Hooks.guard("folder icon glass draw") {
                        val background = param.thisObject
                        // The view whose canvas this is: the cell layout while an icon is
                        // dragged over the folder, else the folder icon.
                        val view = (XposedHelpers.getObjectField(background, "mDrawingDelegate")
                            ?: XposedHelpers.getObjectField(background, "mInvalidateDelegate"))
                            as View? ?: return@guard
                        val canvas = param.args[0] as Canvas
                        shape.reset()
                        val delegate = XposedHelpers.callMethod(background, "getShape",
                            XposedHelpers.getIntField(background, "circleType"))
                        XposedHelpers.callMethod(delegate, "addToPath", shape,
                            (XposedHelpers.callMethod(background, "getOffsetX") as Int).toFloat(),
                            (XposedHelpers.callMethod(background, "getOffsetY") as Int).toFloat(),
                            (XposedHelpers.callMethod(background, "getScaledRadius") as Int).toFloat())
                        view.getLocationOnScreen(location)
                        canvas.drawPath(shape, Frosted.paint(view, location[0].toFloat(),
                            location[1].toFloat(), view.scaleX, config.folderIconGlassTint))
                        XposedHelpers.callMethod(background, "drawShadow", canvas)
                        glassIcons.add(view)
                        param.result = null
                    }.run()
                }
            })
        // Nothing OS 5 redraws folder icons' glass as the home screen scrolls
        // (Workspace.syncFrostedBackdropOffsets -> invalidateFolderIconBackdrops): a scrolled
        // icon draws again from where it is now, not where it was first drawn.
        val int = Int::class.javaPrimitiveType
        XposedHelpers.findAndHookMethod(XposedHelpers.findClass(PAGED_VIEW, cl), "onScrollChanged",
            int, int, int, int, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    glassIcons.forEach { it.invalidate() }
                }
            })
        // A new setting shows on the icons at once.
        store.addChangeListener { glassIcons.forEach { it.invalidate() } }
    }

    /**
     * The dock's search bar (Hotseat.mQsb, layout nt_universal_search_bar_in_hotseat, tagged
     * "home_to_new_search"). In Nothing OS 5 its root is an NTFrostedRelativeLayout: its
     * FrostedBackdropHelper sets a FrostedBackdropDrawable as the view's background, in the
     * rounded shape of the original background, and puts the original back when frosted is
     * off. The same here, with the original kept to put back.
     */
    private fun installSearchBar(cl: ClassLoader, store: ConfigStore) {
        val hotseat = XposedHelpers.findClass(HOTSEAT, cl)
        XposedHelpers.findField(hotseat, "mQsb")
        val apply = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                Hooks.guard("search bar glass") { applySearchBar(param.thisObject as View, store) }.run()
            }
        }
        XposedBridge.hookAllConstructors(hotseat, apply)
        XposedHelpers.findAndHookMethod(hotseat, "updateQsbWidget", apply)
    }

    private val hotseats: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())
    private var listening = false

    private fun applySearchBar(hotseat: View, store: ConfigStore) {
        hotseats.add(hotseat)
        if (!listening) {
            listening = true
            store.addChangeListener {
                hotseats.forEach { Hooks.guard("search bar glass") { applySearchBar(it, store) }.run() }
            }
        }
        val bar = XposedHelpers.getObjectField(hotseat, "mQsb") as View? ?: return
        val tagId = bar.resources.getIdentifier("home_to_new_search", "string", bar.context.packageName)
        if (tagId == 0 || bar.tag?.toString() != bar.resources.getString(tagId)) {
            return // Another search bar style: not the one Nothing OS 5 frosts.
        }
        val config = store.get()
        val current = bar.background
        if (config.searchBarGlass) {
            if (current is GlassBackground) {
                current.tintPercent = config.searchBarGlassTint
                current.invalidateSelf()
            } else if (current != null) {
                bar.background = GlassBackground(bar, current, config.searchBarGlassTint)
            }
        } else if (current is GlassBackground) {
            bar.background = current.original
        }
    }

    /** Frosted glass in the original background's rounded shape (FrostedBackdropHelper). */
    private class GlassBackground(val view: View, val original: Drawable, var tintPercent: Int) :
        Drawable() {
        private val area = RectF()

        override fun draw(canvas: Canvas) {
            area.set(bounds)
            val radius = (original as? GradientDrawable)?.cornerRadius?.takeIf { it > 0f }
                ?: (area.height() / 2f)
            view.getLocationOnScreen(location)
            canvas.drawRoundRect(area, radius, radius, Frosted.paint(view,
                location[0].toFloat(), location[1].toFloat(), view.scaleX, tintPercent))
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Drawable API") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
