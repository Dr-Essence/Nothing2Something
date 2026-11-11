package com.dressence.nothinglauncherplus.systemui;

import com.dressence.nothinglauncherplus.xposed.ConfigStore;
import com.dressence.nothinglauncherplus.xposed.Hooks;

import de.robv.android.xposed.XposedHelpers;

/**
 * Everything the module does in System UI: the status bar lit like the drawer's folder
 * screens (clock and date in green NDot, battery and icons green with a soft glow), and quick
 * settings tiles as a retro display.
 */
public final class SystemUi {

    private SystemUi() {
    }

    /** One of System UI's feature flags (com.android.systemui.Flags); false if unreadable. */
    private static boolean flag(ClassLoader cl, String name) {
        try {
            return Boolean.TRUE.equals(XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("com.android.systemui.Flags", cl), name));
        } catch (Throwable t) {
            return false;
        }
    }

    private static void installUnless(boolean newDesign, String flag, String name,
            Hooks.Installer installer) {
        if (newDesign) {
            Hooks.skipped(name, "System UI's " + flag + " design is on: built for the older one,"
                    + " left out");
        } else {
            Hooks.install(name, installer);
        }
    }

    public static void install(ClassLoader cl, ConfigStore store) {
        // Android's newer System UI designs, each switched by a flag (all off on the CMF Phone
        // 1's Nothing OS 4.1). When Nothing turns one on, the classes the features below are
        // built on go away; those features are then left out cleanly and listed in Hook health,
        // instead of being half applied over a System UI they no longer match (0.70.20, from
        // the dependency map of System UI).
        boolean scene = flag(cl, "sceneContainer");
        boolean newStatusBar = flag(cl, "statusBarRootModernization");
        boolean newBouncer = flag(cl, "composeBouncer");
        boolean newTiles = flag(cl, "qsUiRefactor") || flag(cl, "qsNewTiles");
        Hooks.install("lit texts", Led::install);
        installUnless(newStatusBar, "statusBarRootModernization", "status bar clock",
                () -> Clock.install(cl, store));
        installUnless(newStatusBar, "statusBarRootModernization", "dual status bar",
                () -> DualStatusBar.install(cl, store));
        installUnless(newStatusBar, "statusBarRootModernization", "status bar saver",
                () -> StatusBarSaver.install(cl, store));
        Hooks.install("status bar battery", () -> Battery.install(cl, store));
        Hooks.install("green icons", () -> GreenIcons.install(store));
        Hooks.install("green texts", () -> GreenTexts.install(store));
        installUnless(newTiles, "qsUiRefactor/qsNewTiles", "quick settings",
                () -> QuickSettings.install(cl, store));
        installUnless(scene || newBouncer, "sceneContainer/composeBouncer",
                "lock screen rotation", () -> LockScreenTurn.install(cl, store));
        installUnless(scene, "sceneContainer", "landscape fingerprint",
                () -> LandscapeFingerprint.install(cl, store));
        Hooks.install("always-on display", () -> AodLandscape.install(cl, store));
        installUnless(scene, "sceneContainer", "lock screen widgets fit",
                () -> LockScreenFit.install(cl, store));
        installUnless(scene, "sceneContainer", "landscape shade",
                () -> LandscapeShade.install(cl, store));
        if (com.dressence.nothinglauncherplus.perf.Device.isCmfPhone1()) {
            Hooks.install("module panels", () ->
                    com.dressence.nothinglauncherplus.perf.HudWindows.install(cl));
        }
    }
}
