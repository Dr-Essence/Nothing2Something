package com.dressence.nothinglauncherplus.xposed

import android.content.pm.ApplicationInfo
import com.dressence.nothinglauncherplus.Config
import com.dressence.nothinglauncherplus.keyboard.HeliBoardFullWidth
import com.dressence.nothinglauncherplus.keyboard.HeliBoardGamepad
import com.dressence.nothinglauncherplus.launcher.Launcher
import com.dressence.nothinglauncherplus.perf.Device
import com.dressence.nothinglauncherplus.perf.GameBar
import com.dressence.nothinglauncherplus.perf.GameUnlock
import com.dressence.nothinglauncherplus.settings.SearchHook
import com.dressence.nothinglauncherplus.settings.SettingsTile
import com.dressence.nothinglauncherplus.settings.TaskManager
import com.dressence.nothinglauncherplus.system.BatterySaverSync
import com.dressence.nothinglauncherplus.system.ControllerHome
import com.dressence.nothinglauncherplus.system.HiddenAppIcon
import com.dressence.nothinglauncherplus.system.OrientationProbe
import com.dressence.nothinglauncherplus.system.RotationSensor
import com.dressence.nothinglauncherplus.system.SaverRefreshRate
import com.dressence.nothinglauncherplus.system.SettingsReload
import com.dressence.nothinglauncherplus.system.SystemFonts
import com.dressence.nothinglauncherplus.system.SystemShortcuts
import com.dressence.nothinglauncherplus.system.WakeOrientation
import com.dressence.nothinglauncherplus.systemui.FullScreenApps
import com.dressence.nothinglauncherplus.systemui.SystemUi
import com.dressence.nothinglauncherplus.wallpaper.LivingWorlds
import com.dressence.nothinglauncherplus.wallpaper.PaletteCycle
import com.dressence.nothinglauncherplus.widgets.CardOrientation
import com.dressence.nothinglauncherplus.widgets.WidgetApps

/**
 * Which part of the module each process gets, the same whichever Xposed API loaded it
 * (0.70.35): the old API's entry (LegacyEntry, assets/xposed_init) and the modern API's
 * (ModernEntry, META-INF/xposed/java_init.list) only describe the process starting and bring
 * the settings their own way, then come here.
 *
 * Until 0.70.34 this was ModuleEntry, the old API's entry itself.
 */
internal object HookLoader {

    /**
     * A package starting in a process: [pkg] ("android" for system_server), its [classLoader],
     * whether it is the process's own app ([firstApp]; a process can also load other apps'
     * code), and its [appInfo] (null for system_server).
     */
    class Start(
        val pkg: String,
        val classLoader: ClassLoader,
        val firstApp: Boolean,
        val appInfo: ApplicationInfo?,
    )

    /**
     * Installs the hooks for [start]. [settings] gives this process's one settings store,
     * made on first use (a process whose package the module leaves alone never reads them).
     */
    fun load(start: Start, settings: () -> ConfigStore) {
        val pkg = start.pkg
        val cl = start.classLoader
        if (pkg != Config.MODULE_PKG && pkg != "android" && start.firstApp) {
            // Each hooked app reports which features installed (HookReport, Diagnostics).
            Hooks.install("hook report", HookReport::install)
        }
        when {
            // The module's own app: only the old API's entry looks at it (LegacyEntry).
            pkg == Config.MODULE_PKG -> return
            pkg == "android" -> loadSystem(cl, settings())
            !start.firstApp -> return
            pkg == Config.LAUNCHER_PKG -> {
                // Its obfuscated names are found again after an update (Symbols, DexKit);
                // DexKit is closed once the hooks are in.
                val info = start.appInfo ?: return
                Symbols.begin(info.sourceDir, info.dataDir, cl)
                try {
                    Launcher.install(cl, settings())
                } finally {
                    Symbols.end()
                }
            }
            // Nothing's widget apps: their cards open over the home screen (also in
            // "Launcher only").
            WidgetApps.handles(pkg) ->
                Hooks.install("widget apps") { WidgetApps.install(settings()) }
            pkg == "com.android.settings" -> {
                // The module's row on the Settings home page: also in "Launcher only" (it is
                // the way into the module and changes nothing else in Settings).
                Hooks.install("settings row") { SettingsTile.install(cl) }
                if (!launcherOnly(settings())) {
                    Hooks.install("task manager") { TaskManager.install(cl, settings()) }
                }
            }
            // Settings search finds the module's settings (also in "Launcher only": it only
            // lets search read the module's own list).
            pkg == "com.android.settings.intelligence" ->
                Hooks.install("settings search") { SearchHook.install(cl) }
            launcherOnly(settings()) -> return // "Launcher only": every other app as it is.
            pkg == Config.SYSTEMUI_PKG -> SystemUi.install(cl, settings())
            pkg == LivingWorlds.PKG ->
                Hooks.install("living worlds") { LivingWorlds.install(cl, settings()) }
            pkg == PaletteCycle.PKG ->
                Hooks.install("palette cycle downloads") { PaletteCycle.install(cl, settings()) }
            pkg == HeliBoardGamepad.PKG -> {
                Hooks.install("heliboard gamepad") { HeliBoardGamepad.install(cl, settings()) }
                Hooks.install("heliboard full width") {
                    HeliBoardFullWidth.install(cl, settings())
                }
            }
            pkg == GameBar.PKG && Device.isCmfPhone1() ->
                Hooks.install("game bar") { GameBar.install(cl, settings()) }
            else -> {
                // An app the user added to the scope: full screen (picked in Full-screen
                // apps) and, on the CMF Phone 1, the 120 fps unlock (picked in Games).
                Hooks.install("full-screen apps") { FullScreenApps.install(pkg, settings()) }
                if (Device.isCmfPhone1()) {
                    Hooks.install("120 fps unlock") {
                        if (pkg in settings().get().unlockGames) {
                            GameUnlock.install(pkg)
                        }
                    }
                }
            }
        }
    }

    /** system_server ("android"). */
    private fun loadSystem(cl: ClassLoader, store: ConfigStore) {
        // Home screen rotation's part in the system: kept in "Launcher only".
        Hooks.install("widget card orientation") { CardOrientation.install(cl, store) }
        Hooks.install("orientation probe") { OrientationProbe.install(cl, store) }
        Hooks.install("rotation sensor") { RotationSensor.install(cl, store) }
        // Keeps this store current for the hooks that only read it (RotationSensor).
        Hooks.install("settings reload") { SettingsReload.install(cl, store) }
        Hooks.install("wake orientation") { WakeOrientation.install(cl, store) }
        // The module's own icon: kept in "Launcher only" (it changes only this app).
        Hooks.install("hidden app icon") { HiddenAppIcon.install(cl) }
        // Kept in "Launcher only" too, where it only gives the user's own home back: left
        // out, a gaming launcher it had made home stayed home with no controller (found in
        // the 0.70.16 audit).
        Hooks.install("controller home") { ControllerHome.install(cl, store) }
        if (launcherOnly(store)) {
            Hooks.log("launcher only: the system's other changes left out")
            return
        }
        Hooks.install("system font") { SystemFonts.install(cl, store) }
        Hooks.install("system shortcuts") { SystemShortcuts.install(cl) }
        Hooks.install("saver refresh rate") { SaverRefreshRate.install(cl, store) }
        Hooks.install("battery saver sync") { BatterySaverSync.install(cl) }
    }

    /** "Launcher only" is on (read as this process starts); off if it cannot be read. */
    private fun launcherOnly(store: ConfigStore): Boolean = try {
        store.get().launcherOnly
    } catch (t: Throwable) {
        false
    }
}
