package com.dressence.nothinglauncherplus.keyboard

import android.content.Context
import android.graphics.Insets
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import com.dressence.nothinglauncherplus.xposed.ConfigStore
import com.dressence.nothinglauncherplus.xposed.Hooks
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * HeliBoard across the whole width in landscape, also over the strip beside the camera.
 *
 * Why there is a gap (read from the phone's own framework and from HeliBoard 4.2 / 4.3-beta1):
 * - Android lays every window out in `WindowLayout.computeFrames`. Unless a window's
 *   `layoutInDisplayCutoutMode` is ALWAYS, its frame is kept out of the camera's strip on every
 *   side the system bars do not already cover. In landscape no bar covers the camera's side, so
 *   the keyboard window started 106 px in (dump: `frame=[106,103][2400,1080]`). Nothing's own
 *   helper (`NtPopUpViewHelperImpl.computeCutoutMode`) passes the window's mode through
 *   unchanged, except for Nothing's pop-up windows.
 * - `InputMethodService.onCreate` gives the keyboard window no cutout mode (DEFAULT).
 * - HeliBoard sizes its keys to the screen width minus the system bars and the cutout
 *   (`getDefaultKeyboardWidth`: `systemBars() | displayCutout()`), so the keys match that
 *   narrower window.
 *
 * With the setting on, both are changed at the source: the keyboard window asks for ALWAYS (as
 * Android lets any window do), and HeliBoard's width leaves out only the system bars. In
 * portrait the camera is above the keyboard, so nothing changes there.
 *
 * Mistake recorded (0.57.0): with only those two, the window covered the whole width and the
 * keys were sized to it, but they started 106 px in and the last key went past the right edge
 * (Dr.Essence's screenshot: `q`'s key from about 106 px, `p` cut off; window dump:
 * `frame=[0,103][2400,1080]`). The phone's `InsetsState.calculateInsets` gives views the
 * cutout as part of their legacy "system window" insets (`systemBars() | displayCutout()`), so
 * a view in the keyboard window that fits system windows padded itself by the camera's strip.
 * Now, while the setting is on, the insets given to the keyboard window's views leave the
 * cutout out (the window root's own insets listener; nothing else in the window changes).
 * A diagnostic line logs the views from the keyboard to the window root (left, width, left
 * padding) once per layout change, to confirm on the phone which view it was.
 */
internal object HeliBoardFullWidth {

    private const val CLS_IME = "helium314.keyboard.latin.LatinIME"

    /**
     * Where HeliBoard's width function is. The source has it in `ResourceUtils`, but release
     * builds are optimised by R8, which moved it into `KtxKt` in 4.3-beta1 (checked in the
     * released APK); both are tried, so a build that keeps either one works.
     */
    private val WIDTH_CLASSES = listOf(
        "helium314.keyboard.latin.utils.ResourceUtils",
        "helium314.keyboard.latin.utils.KtxKt",
    )

    /**
     * True once HeliBoard's width function is hooked. Without it the keys would stay narrow in
     * a wider window (an empty strip on the other side), so the window is left as it is.
     */
    private var widthHooked = false

    fun install(cl: ClassLoader, store: ConfigStore) {
        widthHooked = hookWidth(cl, store)
        if (!widthHooked) return
        val ime = XposedHelpers.findClass(CLS_IME, cl)
        // The window is made in onCreate; it is set right after, and again each time a field
        // opens, so the switch takes effect from the next keyboard without restarting HeliBoard.
        XposedHelpers.findAndHookMethod(ime, "onCreate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                applyCutoutMode(param.thisObject as InputMethodService, store)
            }
        })
        XposedBridge.hookAllMethods(ime, "onStartInputView", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                store.reloadIfChanged()
                applyCutoutMode(param.thisObject as InputMethodService, store)
            }
        })
    }

    /** The keyboard window may cover the camera's strip (ALWAYS) only while the setting is on. */
    private fun applyCutoutMode(service: InputMethodService, store: ConfigStore) {
        try {
            val window = service.window?.window ?: return
            val wanted = if (store.get().heliboardFullWidth) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                // What InputMethodService leaves it at.
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
            val attrs = window.attributes
            if (attrs.layoutInDisplayCutoutMode != wanted) {
                attrs.layoutInDisplayCutoutMode = wanted
                window.attributes = attrs // only when it changes: no extra relayouts
            }
            val decor = window.decorView
            val full = wanted == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            if (full != (decor.getTag(TAG_STRIPPING) == true)) {
                decor.setTag(TAG_STRIPPING, full)
                decor.setOnApplyWindowInsetsListener(if (full) STRIP_CUTOUT else null)
                decor.requestApplyInsets()
            }
            if (full) watchLayout(service, decor)
        } catch (t: Throwable) {
            Hooks.log("heliboard full width: window: $t")
        }
    }

    /** A tag key of the module's own (any id unused by the window's views). */
    private const val TAG_STRIPPING = 0x7e4e4c50

    /** The window root's insets without the cutout; everything else as Android gave it. */
    private val STRIP_CUTOUT = View.OnApplyWindowInsetsListener { view, insets ->
        val cutout = WindowInsets.Type.displayCutout()
        val stripped = WindowInsets.Builder(insets)
            .setInsets(cutout, Insets.NONE)
            .setInsetsIgnoringVisibility(cutout, Insets.NONE)
            .setDisplayCutout(null)
            .build()
        view.onApplyWindowInsets(stripped)
    }

    /** Diagnostic: the views from the keyboard to the window root, once per layout change. */
    private fun watchLayout(service: InputMethodService, decor: View) {
        if (decor.getTag(TAG_STRIPPING + 1) != null) return
        var last = ""
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            try {
                val id = service.resources.getIdentifier("keyboard_view", "id", service.packageName)
                var v: View? = if (id == 0) null else decor.findViewById(id)
                val chain = StringBuilder()
                while (v != null) {
                    chain.append(v.javaClass.simpleName).append('[').append(v.left).append('+')
                        .append(v.width).append(" pl").append(v.paddingLeft).append("] ")
                    v = v.parent as? View
                }
                val line = chain.toString().trim()
                if (line != last) {
                    last = line
                    Hooks.log("heliboard full width: views (left+width padLeft): $line")
                }
            } catch (t: Throwable) {
                Hooks.log("heliboard full width: views: $t")
            }
        }
        decor.setTag(TAG_STRIPPING + 1, listener)
        decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun hookWidth(cl: ClassLoader, store: ConfigStore): Boolean {
        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                // Before Android 15 HeliBoard takes the width from DisplayMetrics, which already
                // matches the window; only the 15+ path subtracts the cutout itself.
                if (Build.VERSION.SDK_INT < 35 || !store.get().heliboardFullWidth) return
                try {
                    val context = param.args[0] as Context
                    val metrics = context.getSystemService(WindowManager::class.java)
                        .currentWindowMetrics
                    // The same calculation as HeliBoard's, without displayCutout().
                    val bars = metrics.windowInsets
                        .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                    val width = metrics.bounds.width() - bars.left - bars.right
                    if (width > param.result as Int) param.result = width
                } catch (t: Throwable) {
                    Hooks.log("heliboard full width: width: $t")
                }
            }
        }
        val found = WIDTH_CLASSES.filter { name ->
            val cls = XposedHelpers.findClassIfExists(name, cl) ?: return@filter false
            try {
                XposedHelpers.findAndHookMethod(cls, "getDefaultKeyboardWidth",
                    Context::class.java, hook)
                true
            } catch (e: NoSuchMethodError) {
                false
            }
        }
        Hooks.log(if (found.isEmpty()) {
            "heliboard full width: no getDefaultKeyboardWidth found in this HeliBoard; left off"
        } else {
            "heliboard full width: width in ${found.joinToString()}"
        })
        return found.isNotEmpty()
    }
}
