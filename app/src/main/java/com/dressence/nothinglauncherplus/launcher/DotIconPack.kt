package com.dressence.nothinglauncherplus.launcher

import com.dressence.nothinglauncherplus.Config
import android.app.Activity
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.dressence.nothinglauncherplus.xposed.Hooks
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.ConcurrentHashMap

/**
 * The dot-matrix icons as an icon pack: a "Dot matrix" entry in Nothing Launcher's own Icon Pack
 * picker (Customisation → Icon Pack), next to Default, Nothing and the installed packs. Only
 * while it is picked are icons drawn as dot screens; every other pack draws as it always does.
 * (Until 0.55.3 a switch in the module turned every icon into a dot screen, whatever pack was
 * picked; Dr.Essence asked for it to be a choice beside Nothing's own.)
 *
 * How the picker works (decompiled launcher; the short class names are this build's
 * obfuscation, so each hook is installed on its own and a renamed class only loses the entry):
 * - `h7.d.a(Context, ConcurrentHashMap)` fills the launcher's map of packs from the apps that
 *   answer the ADW icon-pack intents; the picker lists every entry of that map besides Default and
 *   Nothing (`la.e.w`), labelled with the entry's app name (`k7.b(packageName, appName)`).
 *   The module adds its own entry there, under its package name, so nothing is declared to other
 *   launchers.
 * - The picker shows a pack only if `d7.h.i(packageName, …)` gives it a preview bitmap. For this
 *   package that is the module's own app icon; it is shown here as a dot screen.
 * - A tap is Nothing's own: it stores the id in `Settings.System "nothing_icon_pack"` and the
 *   launcher reloads every icon (`ThemeManager` → `refreshAndReloadLauncher`). With this id the
 *   launcher finds no appfilter in the module and loads the Default icons, which the dot hook
 *   ([DotIcons]) then draws as screens.
 * - The module learns the choice by reading that same setting, and watching it.
 */
internal object DotIconPack {

    /** The pack's id: the module's package (installed, so the launcher can look it up). */
    private const val PACK_ID = "com.dressence.nothinglauncherplus"
    private const val LABEL = "Dot matrix"
    /** Where Nothing Launcher keeps the picked pack. */
    private const val SETTING = "nothing_icon_pack"

    /** True while "Dot matrix" is the picked pack; read on every icon draw. */
    @Volatile
    private var picked = false
    private var watching = false

    @JvmStatic
    fun active(): Boolean = picked

    /** [onChange] runs on the main thread when the pack is picked or left. */
    @JvmStatic
    fun install(cl: ClassLoader, onChange: Runnable) {
        Hooks.install("dot matrix icon pack: entry") { addEntry(cl) }
        Hooks.install("dot matrix icon pack: preview") { dotPreview(cl) }
        XposedHelpers.findAndHookMethod(Config.CLS_LAUNCHER, cl, "onCreate",
            Bundle::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    Hooks.guard("dot matrix icon pack: watch") {
                        watch(activity.applicationContext, onChange)
                    }.run()
                }
            })
    }

    /** Our entry goes into the launcher's map of packs each time it is filled. */
    private fun addEntry(cl: ClassLoader) {
        val entity = XposedHelpers.findClass("k7.b", cl)
        // Found by its shape when an update renames it (LauncherSymbols, 4.0.20: h7.d.a).
        XposedBridge.hookMethod(LauncherSymbols.readPacks(cl), object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val packs = param.args[1] as ConcurrentHashMap<Any, Any>
                        // The class of an entry already in Nothing's map, which is the right
                        // one by definition; "k7.b" (its name in 4.0.20, obfuscated) only when
                        // the map is empty. A later build giving that name to another class
                        // would have put a foreign object into Nothing's map (dependency map,
                        // F4).
                        val kind = packs.values.firstOrNull()?.javaClass ?: entity
                        packs.putIfAbsent(PACK_ID, XposedHelpers.newInstance(kind, PACK_ID, LABEL))
                    } catch (t: Throwable) {
                        Hooks.log("dot matrix icon pack: entry failed: $t")
                    }
                }
            })
    }

    /**
     * The picker's circle for our entry: the module's icon, as the dot screen it would become.
     * The picker builds its list off the main thread, so the conversion happens there too.
     */
    private fun dotPreview(cl: ClassLoader) {
        // Found by its shape when an update renames it (LauncherSymbols, 4.0.20: d7.h.i).
        XposedBridge.hookMethod(LauncherSymbols.iconForPack(cl),
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.args[0] != PACK_ID) return
                    val icon = param.result as? Bitmap ?: return
                    try {
                        IconScreen.of(icon).full?.let { param.result = it }
                    } catch (t: Throwable) {
                        Hooks.log("dot matrix icon pack: preview failed: $t") // plain icon stays
                    }
                }
            })
    }

    /** Reads the picked pack now and on every change (once per launcher process). */
    @Synchronized
    private fun watch(context: Context, onChange: Runnable) {
        if (watching) return
        watching = true
        val resolver = context.contentResolver
        picked = Settings.System.getString(resolver, SETTING) == PACK_ID
        Hooks.log("dot matrix icon pack: ${if (picked) "picked" else "not picked"}")
        resolver.registerContentObserver(Settings.System.getUriFor(SETTING), false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    Hooks.guard("dot matrix icon pack: change") {
                        val now = Settings.System.getString(resolver, SETTING) == PACK_ID
                        if (now != picked) {
                            picked = now
                            Hooks.log("dot matrix icon pack: ${if (now) "picked" else "left"}")
                            onChange.run()
                        }
                    }.run()
                }
            })
    }
}
