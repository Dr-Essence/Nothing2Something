package com.dressence.nothinglauncherplus.launcher

import android.graphics.Canvas
import android.graphics.Path
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
    private const val CARD_HOST = "com.nothing.cardhost.CardWidgetHostView"

    private val location = IntArray(2)
    private val shape = Path()
    /** Folder icons that drew glass: redrawn while the home screen scrolls. */
    private val glassIcons: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())

    @JvmStatic
    fun install(cl: ClassLoader, store: ConfigStore) {
        if (Frosted.nativeIn(cl)) {
            for (part in arrayOf("folder icon glass", "search bar glass", "widget glass")) {
                Hooks.skipped(part, "Nothing OS 5 has its own frosted glass")
            }
            return
        }
        Hooks.install("folder icon glass") { installFolderIcons(cl, store) }
        Hooks.install("search bar glass") { installSearchBar(cl, store) }
        Hooks.install("widget glass") { installWidgets(cl, store) }
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
                bar.background = GlassBackground(bar, current, config.searchBarGlassTint, null)
            }
        } else if (current is GlassBackground) {
            bar.background = current.original
        }
    }

    /** Nothing widget cards whose background is glass now: redrawn as the home screen scrolls. */
    private val glassCards: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())

    /**
     * Nothing's widgets ("cards", from Card Lab, the card service, weather and the AI widget).
     * Nothing OS 5's FrostedCardWidgetHostView.onParseFinished: once a card's views are made,
     * every IFrostedBackdropSupport view in it gets the frosted backdrop; in the 5.0 card apps
     * (firmware-inspect run 43265055010) that is the card's root container in almost every
     * widget (battery, clocks, date, weather, pedometer, media player, compass, news...), whose
     * background becomes the glass. Nothing OS 4.1's cards have no such views, so the card's
     * root background is given the glass here, in its own rounded shape, at the same point
     * (CardWidgetHostView.onParseFinished, empty in 4.1, called after every parse). Other
     * apps' widgets are not cards and stay as they are, as on Nothing OS 5.
     */
    private fun installWidgets(cl: ClassLoader, store: ConfigStore) {
        val host = XposedHelpers.findClass(CARD_HOST, cl)
        XposedHelpers.findAndHookMethod(host, "onParseFinished", View::class.java,
            View::class.java, Int::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    Hooks.guard("widget glass") { applyCard(param.args[0] as View?, store) }.run()
                }
            })
        store.addChangeListener {
            glassCards.toList().forEach { Hooks.guard("widget glass") { applyCard(it, store) }.run() }
        }
        val int = Int::class.javaPrimitiveType
        XposedHelpers.findAndHookMethod(XposedHelpers.findClass(PAGED_VIEW, cl), "onScrollChanged",
            int, int, int, int, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    glassCards.forEach { it.invalidate() }
                }
            })
    }

    /** The card's container: its root, or the root's first child, that has a background. */
    private fun applyCard(root: View?, store: ConfigStore) {
        val card = when {
            root == null -> return
            root.background != null -> root
            root is android.view.ViewGroup && root.childCount > 0 &&
                root.getChildAt(0).background != null -> root.getChildAt(0)
            else -> return
        }
        glassCards.add(card)
        val config = store.get()
        val current = card.background
        if (config.widgetGlass) {
            if (current is GlassBackground) {
                current.tintPercent = config.widgetGlassTint
                current.invalidateSelf()
            } else if (current != null) {
                // Android's own widget corner (system_app_widget_background_radius) when the
                // card's background does not say its own.
                val corner = card.resources.getDimension(
                    android.R.dimen.system_app_widget_background_radius)
                card.background = GlassBackground(card, current, config.widgetGlassTint, corner)
            }
        } else if (current is GlassBackground) {
            card.background = current.original
        }
    }
}
