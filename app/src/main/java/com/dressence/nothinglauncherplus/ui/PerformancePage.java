package com.dressence.nothinglauncherplus.ui;

import android.app.Activity;
import android.widget.Toast;

import com.dressence.nothinglauncherplus.Config;
import com.dressence.nothinglauncherplus.Root;

import java.util.ArrayList;

/**
 * The settings' Performance page: the Ndot performance overlay, PerfMTK's profile and thermal
 * switch, and the 120 fps unlock's games. Moved out of SettingsActivity unchanged (0.70.11,
 * cleanup step 4).
 */
final class PerformancePage {

    private static final String[] PERFMTK_PROFILES =
            {"performance", "balanced", "powersave", "powersave+"};

    private final Activity activity;
    private final SettingsStore store;
    /** Lays the page out again (after the games are saved). */
    private final Runnable render;

    PerformancePage(Activity activity, SettingsStore store, Runnable render) {
        this.activity = activity;
        this.store = store;
        this.render = render;
    }

    void render(Ui ui, Config config) {
        ui.note("Experimental, CMF Phone 1 only, needs root.");
        if (config.perfOverlay && !ui.collecting()) {
            // After a reboot the overlay comes back when this page is opened.
            com.dressence.nothinglauncherplus.perf.OverlayService.start(activity);
        }
        ui.section("Overlay");
        ui.switchRow("Ndot performance overlay", "Each CPU core's clock, GPU clock and load, CPU,"
                + " GPU, SoC and battery temperatures, FPS · over every app and the lock screen",
                config.perfOverlay,
                checked -> {
                    config.perfOverlay = checked;
                    store.save(activity);
                    if (checked) {
                        // System UI draws it: no "display over other apps" needed.
                        com.dressence.nothinglauncherplus.perf.OverlayService.start(activity);
                    } else {
                        com.dressence.nothinglauncherplus.perf.OverlayService.stop(activity);
                    }
                });

        ui.section("PerfMTK");
        ui.row("Profile", "Performance, Balanced, Powersave or Powersave+ now · PerfMTK's own"
                + " app profiles still switch it when apps change", null, ui.chevron(),
                v -> chooseProfile());

        ui.row("Thermal limits", "Switch PerfMTK's thermal throttling on or off · off runs"
                + " hotter, for short gaming sessions", null, ui.chevron(),
                v -> chooseThermal());

        ui.section("Games");
        int n = config.unlockGames.size();
        ui.row("120 fps unlock", (n == 0 ? "No games" : n == 1 ? "1 game" : n + " games")
                + " · the game sees a Galaxy S24 Ultra · read the warning", null, ui.chevron(),
                v -> warnBeforeUnlock(config));
    }

    private void chooseProfile() {
        Dialogs.builder(activity)
                .setTitle("PerfMTK profile")
                .setItems(new String[] {"Performance", "Balanced", "Powersave", "Powersave+"},
                        (d, which) -> perfMtk(PERFMTK_PROFILES[which],
                                "PerfMTK: " + PERFMTK_PROFILES[which],
                                () -> store.config.perfProfile = PERFMTK_PROFILES[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** PerfMTK's thermal switch (`perfmtk --thermal enable|disable`, from its own help). */
    private void chooseThermal() {
        Dialogs.builder(activity)
                .setTitle("Thermal limits")
                .setMessage("Off lets the phone run hotter before it slows down. Use it for"
                        + " short sessions, and switch it back on after.")
                .setPositiveButton("On", (d, w) -> perfMtk("--thermal enable",
                        "Thermal limits on", () -> store.config.thermalOff = false))
                .setNegativeButton("Off", (d, w) -> perfMtk("--thermal disable",
                        "Thermal limits off", () -> store.config.thermalOff = true))
                .setNeutralButton("Cancel", null)
                .show();
    }

    /** Runs PerfMTK; on success {@code saved} records the state (shown in the Game Bar). */
    private void perfMtk(String arguments, String done, Runnable saved) {
        new Thread(() -> {
            String failure = com.dressence.nothinglauncherplus.perf.Device.hasPerfMtk()
                    ? Root.run("perfmtk " + arguments)
                    : "PerfMTK is not installed";
            activity.runOnUiThread(() -> {
                if (failure == null) {
                    saved.run();
                    store.save(activity);
                }
                Toast.makeText(activity, failure == null ? done : "Failed: " + failure,
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void warnBeforeUnlock(Config config) {
        Dialogs.builder(activity)
                .setTitle("120 fps unlock")
                .setMessage("Inside each game you pick, the phone presents itself as a Samsung"
                        + " Galaxy S24 Ultra, so games that choose their frame rate by phone"
                        + " offer 120 fps. Nothing outside those games changes.\n\n"
                        + "Use it only for offline or single-player games. Games with online"
                        + " play or anti-cheat can treat a changed device as cheating and ban"
                        + " the account; there is no way to rule that out.\n\n"
                        + "It does not help games that choose by their graphics chip. Tick each"
                        + " game in the module's LSPosed scope too, then restart the game.")
                .setPositiveButton("Pick games", (d, w) -> pickUnlockGames(config))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickUnlockGames(Config config) {
        java.util.List<Apps.App> all = Apps.all(activity);
        java.util.List<String> packages = new ArrayList<>();
        java.util.List<String> labels = new ArrayList<>();
        for (Apps.App app : all) {
            String pkg = android.content.ComponentName.unflattenFromString(app.component)
                    .getPackageName();
            if (!packages.contains(pkg) && !pkg.equals(activity.getPackageName())) {
                packages.add(pkg);
                labels.add(app.label);
            }
        }
        boolean[] checked = new boolean[packages.size()];
        for (int i = 0; i < packages.size(); i++) {
            checked[i] = config.unlockGames.contains(packages.get(i));
        }
        Dialogs.builder(activity)
                .setTitle("Games to unlock")
                .setMultiChoiceItems(labels.toArray(new String[0]), checked,
                        (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Save", (d, w) -> {
                    config.unlockGames.clear();
                    for (int i = 0; i < packages.size(); i++) {
                        if (checked[i]) {
                            config.unlockGames.add(packages.get(i));
                        }
                    }
                    store.save(activity);
                    render.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
