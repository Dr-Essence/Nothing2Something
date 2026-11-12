package com.dressence.nothinglauncherplus.xposed;

import com.dressence.nothinglauncherplus.Config;
import com.dressence.nothinglauncherplus.keyboard.HeliBoardFullWidth;
import com.dressence.nothinglauncherplus.systemui.FullScreenApps;
import com.dressence.nothinglauncherplus.keyboard.HeliBoardGamepad;
import com.dressence.nothinglauncherplus.launcher.Launcher;
import com.dressence.nothinglauncherplus.perf.Device;
import com.dressence.nothinglauncherplus.perf.GameBar;
import com.dressence.nothinglauncherplus.perf.GameUnlock;
import com.dressence.nothinglauncherplus.system.OrientationProbe;
import com.dressence.nothinglauncherplus.system.WakeOrientation;
import com.dressence.nothinglauncherplus.system.HiddenAppIcon;
import com.dressence.nothinglauncherplus.settings.SearchHook;
import com.dressence.nothinglauncherplus.settings.SettingsTile;
import com.dressence.nothinglauncherplus.settings.TaskManager;
import com.dressence.nothinglauncherplus.system.ControllerHome;
import com.dressence.nothinglauncherplus.wallpaper.LivingWorlds;
import com.dressence.nothinglauncherplus.wallpaper.PaletteCycle;
import com.dressence.nothinglauncherplus.system.RotationSensor;
import com.dressence.nothinglauncherplus.system.SettingsReload;
import com.dressence.nothinglauncherplus.system.BatterySaverSync;
import com.dressence.nothinglauncherplus.system.SaverRefreshRate;
import com.dressence.nothinglauncherplus.system.SystemShortcuts;
import com.dressence.nothinglauncherplus.system.SystemFonts;
import com.dressence.nothinglauncherplus.systemui.SystemUi;
import com.dressence.nothinglauncherplus.widgets.CardOrientation;
import com.dressence.nothinglauncherplus.widgets.WidgetApps;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * The module's entry point (named in assets/xposed_init): which app is starting decides which
 * part of the module it gets.
 */
public final class ModuleEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String pkg = lpparam.packageName;
        ClassLoader cl = lpparam.classLoader;
        if (!Config.MODULE_PKG.equals(pkg) && !"android".equals(pkg)
                && lpparam.isFirstApplication) {
            // Each hooked app reports which features installed (HookReport, Diagnostics).
            Hooks.install("hook report", HookReport::install);
        }
        if (Config.MODULE_PKG.equals(pkg)) {
            // The settings app shows whether the module is active.
            XposedHelpers.findAndHookMethod(Config.SETTINGS_ACTIVITY, cl, "isModuleActive",
                    XC_MethodReplacement.returnConstant(true));
        } else if ("android".equals(pkg)) {
            ConfigStore store = new ConfigStore();
            // Home screen rotation's part in the system: kept in "Launcher only".
            Hooks.install("widget card orientation", () -> CardOrientation.install(cl, store));
            Hooks.install("orientation probe", () -> OrientationProbe.install(cl, store));
            Hooks.install("rotation sensor", () -> RotationSensor.install(cl, store));
            // Keeps this store current for the hooks that only read it (RotationSensor).
            Hooks.install("settings reload", () -> SettingsReload.install(cl, store));
            Hooks.install("wake orientation", () -> WakeOrientation.install(cl, store));
            // The module's own icon: kept in "Launcher only" (it changes only this app).
            Hooks.install("hidden app icon", () -> HiddenAppIcon.install(cl));
            // Kept in "Launcher only" too, where it only gives the user's own home back: left
            // out, a gaming launcher it had made home stayed home with no controller (found
            // in the 0.70.16 audit).
            Hooks.install("controller home", () -> ControllerHome.install(cl, store));
            if (launcherOnly(store)) {
                Hooks.log("launcher only: the system's other changes left out");
                return;
            }
            Hooks.install("system font", () -> SystemFonts.install(cl, store));
            Hooks.install("system shortcuts", () -> SystemShortcuts.install(cl));
            Hooks.install("saver refresh rate", () -> SaverRefreshRate.install(cl, store));
            Hooks.install("battery saver sync", () -> BatterySaverSync.install(cl));
        } else if (!lpparam.isFirstApplication) {
            return;
        } else if (Config.LAUNCHER_PKG.equals(pkg)) {
            // Its obfuscated names are found again after an update (Symbols, DexKit); DexKit
            // is closed once the hooks are in.
            Symbols.begin(lpparam.appInfo.sourceDir, lpparam.appInfo.dataDir, cl);
            try {
                Launcher.install(cl, new ConfigStore());
            } finally {
                Symbols.end();
            }
        } else if (WidgetApps.handles(pkg)) {
            // Nothing's widget apps: their cards open over the home screen (also in
            // "Launcher only").
            Hooks.install("widget apps", () -> WidgetApps.install(new ConfigStore()));
        } else if ("com.android.settings".equals(pkg)) {
            // The module's row on the Settings home page: also in "Launcher only" (it is the
            // way into the module and changes nothing else in Settings).
            Hooks.install("settings row", () -> SettingsTile.install(cl));
            if (!launcherOnly(new ConfigStore())) {
                Hooks.install("task manager", () -> TaskManager.install(cl, new ConfigStore()));
            }
        } else if ("com.android.settings.intelligence".equals(pkg)) {
            // Settings search finds the module's settings (also in "Launcher only": it only
            // lets search read the module's own list).
            Hooks.install("settings search", () -> SearchHook.install(cl));
        } else if (launcherOnly(new ConfigStore())) {
            return; // "Launcher only": every other app is left as it is.
        } else if (Config.SYSTEMUI_PKG.equals(pkg)) {
            SystemUi.install(cl, new ConfigStore());
        } else if (LivingWorlds.PKG.equals(pkg)) {
            Hooks.install("living worlds",
                    () -> LivingWorlds.install(cl, new ConfigStore()));
        } else if (PaletteCycle.PKG.equals(pkg)) {
            Hooks.install("palette cycle downloads", () -> PaletteCycle.install(cl, new ConfigStore()));
        } else if (HeliBoardGamepad.PKG.equals(pkg)) {
            Hooks.install("heliboard gamepad",
                    () -> HeliBoardGamepad.install(cl, new ConfigStore()));
            Hooks.install("heliboard full width",
                    () -> HeliBoardFullWidth.INSTANCE.install(cl, new ConfigStore()));
        } else if (GameBar.PKG.equals(pkg) && Device.isCmfPhone1()) {
            Hooks.install("game bar", () -> GameBar.install(cl, new ConfigStore()));
        } else {
            // An app the user added to the scope: full screen (picked in Full-screen apps) and,
            // on the CMF Phone 1, the 120 fps unlock (picked in Games).
            Hooks.install("full-screen apps",
                    () -> FullScreenApps.INSTANCE.install(pkg, new ConfigStore()));
            if (Device.isCmfPhone1()) {
                Hooks.install("120 fps unlock", () -> {
                    if (new ConfigStore().get().unlockGames.contains(pkg)) {
                        GameUnlock.install(pkg);
                    }
                });
            }
        }
    }

    /** "Launcher only" is on (read as this process starts); off if it cannot be read. */
    private static boolean launcherOnly(ConfigStore store) {
        try {
            return store.get().launcherOnly;
        } catch (Throwable t) {
            return false;
        }
    }
}
