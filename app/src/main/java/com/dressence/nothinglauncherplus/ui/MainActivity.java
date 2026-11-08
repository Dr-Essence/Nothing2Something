package com.dressence.nothinglauncherplus.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Display;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import com.dressence.nothinglauncherplus.Config;
import com.dressence.nothinglauncherplus.UpdateReceiver;
import com.dressence.nothinglauncherplus.anim.FrameRates;
import com.dressence.nothinglauncherplus.sound.SoundEngine;
import com.dressence.nothinglauncherplus.sound.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * Module settings: a menu of pages (app drawer, home screen, sound, status bar and quick
 * settings, lock screen, backup, diagnostics), each the same screen opened with its page.
 */
public class MainActivity extends Activity {

    /** Replaced with {@code true} by the module's own hook when LSPosed has it enabled. */
    public static boolean isModuleActive() {
        return false;
    }

    private static final int REQUEST_EXPORT = 1;
    private static final int REQUEST_IMPORT = 2;

    private SettingsStore store;

    /** Opened from a Settings search result: the page and the row to show. */
    public static final String ACTION_SEARCH_RESULT =
            "com.dressence.nothinglauncherplus.SEARCH_RESULT";
    /** The row a search result asked for, shown once the page is laid out. */
    private String highlightTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new SettingsStore(this);
        openSearchResult(getIntent());
        if (RunningServices.shortcutsOn(this)) {
            RunningServices.setShortcuts(this, true); // The icon's long-press shortcut.
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        store.reload();
        assignMissingMotions();
        render();
        // Once the page is up and the app idle: the settings as Settings search will see them.
        Looper.myQueue().addIdleHandler(() -> {
            buildSearchIndex();
            return false;
        });
    }

    /** Pages this screen shows (its {@link Config#EXTRA_SETTINGS_PAGE}); none: the menu. */
    private static final String PAGE_DRAWER = Config.PAGE_DRAWER;
    private static final String PAGE_HOME = "home";
    private static final String PAGE_SYSTEM_UI = "systemui";
    private static final String PAGE_LOCK = "lock";
    private static final String PAGE_SOUND = "sound";
    private static final String PAGE_BACKUP = "backup";
    private static final String PAGE_DIAGNOSTICS = "diagnostics";
    private static final String PAGE_PERFORMANCE = "performance";

    private void render() {
        String page = getIntent().getStringExtra(Config.EXTRA_SETTINGS_PAGE);
        if (page == null) {
            renderMenu(Ui.page(this, "Nothing2Something", false));
        } else {
            renderPage(page, Ui.page(this, pageTitle(page), true));
        }
        showSearchResult();
    }

    private static String pageTitle(String page) {
        switch (page) {
            case PAGE_DRAWER: return "App drawer";
            case PAGE_HOME: return "Home screen";
            case PAGE_SYSTEM_UI: return "Status bar & quick settings";
            case PAGE_LOCK: return "Lock screen";
            case PAGE_SOUND: return "Sound";
            case PAGE_PERFORMANCE: return "Performance";
            case PAGE_BACKUP: return "Backup";
            default: return "Diagnostics";
        }
    }

    private void renderPage(String page, Ui ui) {
        Config config = store.config;
        switch (page) {
            case PAGE_DRAWER:
                renderDrawer(ui, config);
                break;
            case PAGE_HOME:
                renderHome(ui, config);
                break;
            case PAGE_SYSTEM_UI:
                renderSystemUi(ui, config);
                break;
            case PAGE_LOCK:
                renderLock(ui, config);
                break;
            case PAGE_SOUND:
                addSoundSection(ui, config);
                break;
            case PAGE_PERFORMANCE:
                renderPerformance(ui, config);
                break;
            case PAGE_BACKUP:
                renderBackup(ui);
                break;
            default:
                renderDiagnostics(ui, config);
                break;
        }
    }

    /**
     * Every page this screen offers now, laid out off screen in the recording mode of
     * {@link Ui#collector}, saved for Settings search (search/SearchIndex.kt). Only the pages
     * the menu offers: "Launcher only" and other phones leave theirs out, as the menu does.
     * Never lets a failure reach the page on screen.
     */
    private void buildSearchIndex() {
        try {
            java.util.List<com.dressence.nothinglauncherplus.search.SearchIndex.Entry> found =
                    new java.util.ArrayList<>();
            renderMenu(Ui.collector(this, null, "Nothing2Something", found));
            java.util.List<String> pages = new java.util.ArrayList<>(java.util.Arrays.asList(
                    PAGE_DRAWER, PAGE_HOME, PAGE_SOUND));
            if (!store.config.launcherOnly) {
                pages.add(PAGE_SYSTEM_UI);
                pages.add(PAGE_LOCK);
                if (com.dressence.nothinglauncherplus.perf.Device.isCmfPhone1()) {
                    pages.add(PAGE_PERFORMANCE);
                }
            }
            pages.add(PAGE_BACKUP);
            pages.add(PAGE_DIAGNOSTICS);
            for (String page : pages) {
                renderPage(page, Ui.collector(this, page, pageTitle(page), found));
            }
            com.dressence.nothinglauncherplus.search.SearchIndex.save(this, found);
        } catch (Throwable t) {
            android.util.Log.w("Nothing2Something", "search index not built: " + t);
        }
    }

    /** A Settings search result: open its page, and remember its row to show. */
    private void openSearchResult(Intent intent) {
        if (intent == null || !ACTION_SEARCH_RESULT.equals(intent.getAction())) {
            return;
        }
        kotlin.Pair<String, String> target = com.dressence.nothinglauncherplus.search.SearchIndex
                .parseKey(intent.getStringExtra(":settings:fragment_args_key"));
        if (target == null) {
            return;
        }
        if (target.getFirst() != null) {
            intent.putExtra(Config.EXTRA_SETTINGS_PAGE, target.getFirst());
        } else {
            intent.removeExtra(Config.EXTRA_SETTINGS_PAGE);
        }
        highlightTitle = target.getSecond();
    }

    /** Scrolls to the row a search result asked for and flashes it, once. */
    private void showSearchResult() {
        String title = highlightTitle;
        highlightTitle = null;
        if (title == null) {
            return;
        }
        View row = findRow(getWindow().getDecorView(), title);
        if (row == null) {
            return;
        }
        row.post(() -> {
            android.graphics.Rect r = new android.graphics.Rect(0, 0, row.getWidth(),
                    row.getHeight());
            row.requestRectangleOnScreen(r, false);
            row.setPressed(true);
            row.postDelayed(() -> row.setPressed(false), 900);
        });
    }

    /** The row whose title is {@code title}: the title's TextView, two levels up. */
    private static View findRow(View view, String title) {
        if (view instanceof TextView && title.contentEquals(((TextView) view).getText())
                && view.getParent() instanceof View
                && ((View) view.getParent()).getParent() instanceof View) {
            return (View) ((View) view.getParent()).getParent();
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View row = findRow(group.getChildAt(i), title);
                if (row != null) {
                    return row;
                }
            }
        }
        return null;
    }

    private void openPage(String page) {
        startActivity(new Intent(this, MainActivity.class).putExtra(Config.EXTRA_SETTINGS_PAGE, page));
    }

    private void renderMenu(Ui ui) {
        if (com.dressence.nothinglauncherplus.UpdateReceiver.externalForced(this)) {
            ui.note("Developer options → \"Force allow apps on external\" is on: Android then"
                    + " installs updates of this module on the memory card, which LSPosed cannot"
                    + " read at boot (and Android deletes these settings on a reboot). Turn it"
                    + " off.");
        }
        if (com.dressence.nothinglauncherplus.UpdateReceiver.onMemoryCard(this)) {
            // On a memory card used as internal storage: mounted after LSPosed loads its
            // modules at boot, and Android deletes this app's settings then (UpdateReceiver).
            ui.note("Installed on the memory card, which LSPosed cannot read at boot: the module"
                    + " is not loaded and Android deletes these settings on a reboot. Moving it to"
                    + " the phone's storage now (root; this page closes). Without root, run: pm"
                    + " move-package " + getPackageName() + " internal");
            String pkg = getPackageName();
            if (!ui.collecting()) {
                new Thread(() -> com.dressence.nothinglauncherplus.UpdateReceiver
                        .moveToInternalStorage(pkg)).start();
            }
        }
        if (!isModuleActive() || !store.shared) {
            ui.note("Module not active. Enable it in LSPosed with Nothing Launcher in scope, then"
                    + " restart the launcher. Changes made now will not reach the launcher.");
        }

        ui.section("Mode");
        ui.switchRow("Launcher only", "Change only Nothing Launcher and Nothing's widget apps,"
                + " with home screen rotation; System UI, the lock screen, fonts and other apps are"
                + " left as Nothing made them. For other Nothing phones, or to keep the system"
                + " stock · the system's part follows after a reboot", store.config.launcherOnly,
                checked -> {
                    store.config.launcherOnly = checked;
                    store.save(this);
                    render();
                });

        ui.section("Nothing Launcher");
        ui.row("App drawer", "Custom folders, covers and emoji, background", null, ui.chevron(),
                v -> openPage(PAGE_DRAWER));
        ui.row("Home screen", "Icons, rotation, game controller, restart", null, ui.chevron(),
                v -> openPage(PAGE_HOME));
        ui.row("Sound", "Interface sounds and background music", null, ui.chevron(),
                v -> openPage(PAGE_SOUND));

        if (store.config.launcherOnly) {
            sectionModule(ui);
            return; // System and phone settings do nothing in "Launcher only".
        }

        ui.section("System");
        ui.row("Status bar & quick settings", "Ndot clock and date, glow, system screens", null,
                ui.chevron(), v -> openPage(PAGE_SYSTEM_UI));
        ui.row("Lock screen", "Landscape lock screen (test)", null, ui.chevron(),
                v -> openPage(PAGE_LOCK));
        ui.row("System font", "Use one of Nothing's fonts on this phone · no reboot", null,
                ui.chevron(), v -> chooseSystemFont());
        ui.switchRow("Full refresh rate in battery saver", "Battery saver keeps the screen's"
                + " refresh rate (120 Hz) instead of capping it at 60 Hz; the rest of battery"
                + " saver stays · uses more battery", store.config.saverFullRefresh, checked -> {
                    store.config.saverFullRefresh = checked;
                    store.save(this);
                });

        if (com.dressence.nothinglauncherplus.perf.Device.isCmfPhone1()) {
            ui.section("CMF Phone 1");
            ui.row("Performance", "Ndot overlay, PerfMTK profile, 120 fps unlock for games",
                    null, ui.chevron(), v -> openPage(PAGE_PERFORMANCE));
        }

        sectionModule(ui);
    }

    private void sectionModule(Ui ui) {
        ui.section("Module");
        ui.row("Backup", "Export or import folders, options and covers", null, ui.chevron(),
                v -> openPage(PAGE_BACKUP));
        ui.row("Diagnostics", "Debug report for fixing problems", null, ui.chevron(),
                v -> openPage(PAGE_DIAGNOSTICS));
    }

    private void renderDrawer(Ui ui, Config config) {
        ui.section("Drawer");
        ui.switchRow("Custom folders first", "Show your folders before Nothing's categories",
                config.customFirst, checked -> {
                    config.customFirst = checked;
                    store.save(this);
                });
        ui.switchRow("Hide automatic categories", "Apps outside your folders go to Others",
                config.hideBuiltin, checked -> {
                    config.hideBuiltin = checked;
                    store.save(this);
                });
        ui.switchRow("Apps without a folder as icons",
                "Replace the Others folder with plain app icons", config.looseApps, checked -> {
                    config.looseApps = checked;
                    store.save(this);
                });
        addOpacityRow(ui, config);

        ui.section("Covers");
        ui.switchRow("Emoji covers (beta)", "Off: folders show their apps as Nothing does; your emoji"
                + " stay saved for later · image covers stay", config.emojiCovers, checked -> {
                    config.emojiCovers = checked;
                    store.save(this);
                });
        ui.switchRow("No emoji covers in battery saver", "Folders show their apps while battery"
                + " saver is on, and their emoji again when it goes off", config.coversOffInSaver,
                checked -> {
                    config.coversOffInSaver = checked;
                    store.save(this);
                });
        ui.switchRow("Animate emoji covers", frameRateSummary(), config.animateCovers, checked -> {
            config.animateCovers = checked;
            store.save(this);
            render();
        });
        ui.switchRow("Dot-matrix emoji", "Covers drawn as a coloured Nothing dot matrix",
                config.dotMatrix, checked -> {
                    config.dotMatrix = checked;
                    store.save(this);
                    render();
                });
        ui.row("Animation test", "Play any emoji's animation on a big dot screen", null,
                ui.chevron(), v -> startActivity(EmojiPickerActivity.testIntent(this)));

        ui.section("Folders · " + config.folders.size());
        if (config.folders.isEmpty()) {
            ui.note("Create a folder, pick its apps, and it appears in the Smart (Beta) drawer.");
        }
        if (config.folders.size() > 1) {
            ui.note("Drag ≡ to change the order in the drawer: folders fill its rows two at a"
                    + " time, in this order.");
        }
        List<String> ids = new ArrayList<>();
        for (Config.Folder folder : config.folders) {
            ids.add(folder.id);
        }
        ui.content.addView(new ReorderList(ui, ids, 72, id -> {
            Config.Folder folder = config.folderForId(id);
            int n = folder.apps.size();
            return ui.detachedRow(folder.name, n == 1 ? "1 app" : n + " apps",
                    ui.coverPreview(folder, 40, config.animateCovers, config.dotMatrix),
                    null, v -> FolderActivity.open(this, folder.id, false));
        }, order -> {
            List<Config.Folder> sorted = new ArrayList<>();
            for (String id : order) {
                sorted.add(config.folderForId(id));
            }
            config.folders.clear();
            config.folders.addAll(sorted);
            store.save(this);
        }));
        ui.pill("New folder", true, v -> Dialogs.promptText(this, "New folder", "", "Name",
                name -> {
                    Config.Folder folder = new Config.Folder(
                            Long.toString(System.currentTimeMillis(), 36), name);
                    config.folders.add(folder);
                    store.save(this);
                    FolderActivity.open(this, folder.id, true);
                }));
    }

    /**
     * Nothing Launcher's Icon Pack picker (its exported activity), where "Dot matrix" is one of
     * the packs; if this launcher build has no such screen, the reason is shown.
     */
    private void openIconPackPicker() {
        try {
            startActivity(new Intent("com.nothing.launcher.icon_pack_picker")
                    .setPackage("com.nothing.launcher")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Nothing Launcher's Icon Pack picker was not found: open "
                    + "Customisation > Icon Pack", Toast.LENGTH_LONG).show();
        }
    }

    private void renderHome(Ui ui, Config config) {
        ui.section("Icons");
        // An icon pack in Nothing's own picker now (launcher/DotIconPack.kt), not a switch here.
        ui.row("Dot-matrix app icons",
                "Pick \"Dot matrix\" in Nothing's Icon Pack picker: every icon a little dot "
                        + "screen in its own shape, long names scroll", null, ui.chevron(),
                v -> openIconPackPicker());

        ui.section("Landscape & controllers");
        ui.switchRow("Home screen rotation", "Home and app drawer follow auto-rotate (beta)",
                config.allowRotation, checked -> {
                    config.allowRotation = checked;
                    store.save(this);
                });
        ui.switchRow("Game controller navigation",
                "Start app drawer · Y/X menu · L1/R1 pages · L2/R2 first/last · L3/R3 options",
                config.controllerNav, checked -> {
                    config.controllerNav = checked;
                    store.save(this);
                });
        ui.switchRow("Gaming launcher with a controller",
                "While a controller is connected (USB or Bluetooth), home and the controller's"
                        + " PS/Guide button open your gaming launcher; your home comes back"
                        + " when it disconnects",
                config.controllerHome, checked -> {
                    config.controllerHome = checked;
                    store.save(this);
                    if (checked && config.controllerHomeApp.isEmpty()) {
                        chooseGamingLauncher(config);
                    }
                });
        ui.row("Gaming launcher", gamingLauncherLabel(config.controllerHomeApp), null,
                ui.chevron(), v -> chooseGamingLauncher(config));
        ui.switchRow("Controller typing in HeliBoard", "Move over HeliBoard's keys, suggestions"
                + " and panels with the controller · HeliBoard in the module's LSPosed scope",
                config.heliboardController, checked -> {
                    config.heliboardController = checked;
                    store.save(this);
                });

        ui.section("Recents");
        ui.switchRow("Memory in recents", "Free and total memory beside Screenshot; tap it for"
                + " Running services",
                config.recentsMemory, checked -> {
                    config.recentsMemory = checked;
                    store.save(this);
                });
        ui.switchRow("Task manager in Running services", "CPU, temperatures,"
                + " memory, swap and network on top; each process's CPU and network; long-press"
                + " a process to stop it · Settings in the module's LSPosed scope",
                config.taskManager, checked -> {
                    config.taskManager = checked;
                    store.save(this);
                });
        ui.switchRow("Running services shortcut", "A Running services icon to place on the home"
                + " screen (in the widget list, like Settings shortcut) and on this app's"
                + " long-press · the quick settings tile is in the tile editor",
                RunningServices.shortcutsOn(this),
                checked -> RunningServices.setShortcuts(this, checked));

        ui.section("Wallpaper");
        ui.switchRow("Living Worlds sounds", "Its ambient sounds play while its live wallpaper"
                + " is on the home screen or app drawer, as in the app; never with other"
                + " wallpapers · Living Worlds in the module's LSPosed scope",
                config.livingWorldsSounds, checked -> {
                    config.livingWorldsSounds = checked;
                    store.save(this);
                });
        ui.switchRow("Palette Cycle sounds", "Each world's ambient sound from the Living"
                + " Worlds web demo while it is on the home screen or app drawer; downloaded"
                + " once · Palette Cycle in the module's LSPosed scope",
                config.paletteCycleSounds, checked -> {
                    config.paletteCycleSounds = checked;
                    store.save(this);
                });
        addSliderRow(ui, "Living Worlds in landscape", config.livingWorldsLandscapeY,
                value -> value <= 15 ? "Top of the scene (sky)"
                        : value >= 85 ? "Bottom of the scene (water, ground)"
                        : "Middle of the scene (" + value + "%)",
                value -> {
                    config.livingWorldsLandscapeY = value;
                    store.save(this);
                });
        addSliderRow(ui, "Palette Cycle in landscape", config.paletteCycleLandscapeY,
                value -> value <= 15 ? "Top of the picture (sky)"
                        : value >= 85 ? "Bottom of the picture (water, ground)"
                        : "Middle of the picture (" + value + "%)",
                value -> {
                    config.paletteCycleLandscapeY = value;
                    store.save(this);
                });
        ui.switchRow("Living Worlds smooth turning", "The module frames the live wallpaper:"
                + " turning only zooms it to the new screen, never black or starting over ·"
                + " the landscape slider needs it", config.livingWorldsTurn, checked -> {
                    config.livingWorldsTurn = checked;
                    store.save(this);
                });

        ui.section("Launcher");
        ui.row("Restart Nothing Launcher", "Applies everything immediately · needs root", null,
                null, v -> restartLauncher());
        ui.row("Restore missing home items", "Icons, folders and shortcuts the launcher still"
                + " has from another grid size, put back where they were · nothing is removed",
                null, null, v -> new android.app.AlertDialog.Builder(this)
                        .setTitle("Restore missing home items?")
                        .setMessage("Nothing Launcher keeps a separate home screen for each grid"
                                + " size. Icons, folders and shortcuts that the other grid of"
                                + " the same width still has, and yours does not, are added back"
                                + " at their places where those are free. Nothing is removed"
                                + " or moved. Widgets cannot come back this way: the launcher"
                                + " shows which ones to add again.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Restore", (d, w) -> sendBroadcast(
                                new android.content.Intent(Config.ACTION_RESTORE_ITEMS)
                                        .setPackage(Config.LAUNCHER_PKG)))
                        .show());
    }

    /** The installed home apps, other than Nothing Launcher: package to name. */
    private java.util.Map<String, String> homeApps() {
        java.util.Map<String, String> apps = new java.util.LinkedHashMap<>();
        android.content.pm.PackageManager pm = getPackageManager();
        for (android.content.pm.ResolveInfo info : pm.queryIntentActivities(
                new android.content.Intent(android.content.Intent.ACTION_MAIN)
                        .addCategory(android.content.Intent.CATEGORY_HOME), 0)) {
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(Config.LAUNCHER_PKG) && !pkg.equals("com.android.settings")
                    && !apps.containsKey(pkg)) {
                apps.put(pkg, String.valueOf(info.loadLabel(pm)));
            }
        }
        return apps;
    }

    private String gamingLauncherLabel(String pkg) {
        if (pkg.isEmpty()) {
            return "None picked";
        }
        String label = homeApps().get(pkg);
        return label != null ? label : pkg + " (not installed)";
    }

    /** Sets one setting from a list of choices; the row shows the current one. */
    private interface IntSetter {
        void set(int value);
    }

    /**
     * A row that opens a list of {@code labels}; picking one stores its {@code values}
     * entry through {@code setter} and saves.
     */
    private void choiceRow(Ui ui, String title, String summary, String[] labels, int[] values,
            int current, IntSetter setter) {
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                checked = i;
            }
        }
        final int selected = checked;
        ui.row(title, labels[selected] + " · " + summary, null, ui.chevron(), v ->
                Dialogs.builder(this)
                        .setTitle(title)
                        .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                            setter.set(values[which]);
                            store.save(this);
                            dialog.dismiss();
                            render();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());
    }

    private void chooseGamingLauncher(Config config) {
        java.util.Map<String, String> apps = homeApps();
        if (apps.isEmpty()) {
            Dialogs.builder(this)
                    .setTitle("Gaming launcher")
                    .setMessage("No other home app is installed. Install a launcher made for"
                            + " controllers (one that can be set as the home app), then pick"
                            + " it here.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        String[] packages = apps.keySet().toArray(new String[0]);
        String[] names = apps.values().toArray(new String[0]);
        int checked = java.util.Arrays.asList(packages).indexOf(config.controllerHomeApp);
        Dialogs.builder(this)
                .setTitle("Gaming launcher")
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    config.controllerHomeApp = packages[which];
                    store.save(this);
                    dialog.dismiss();
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void renderSystemUi(Ui ui, Config config) {
        restartSystemUiRow(ui);
        ui.section("Status bar");
        ui.switchRow("Ndot status bar",
                "Ndot clock and date, every status icon in glowing green",
                config.ndotStatusBar, checked -> {
                    config.ndotStatusBar = checked;
                    store.save(this);
                });
        ui.switchRow("Date in status bar", "The date before the clock · restart System UI",
                config.statusBarDate, checked -> {
                    config.statusBarDate = checked;
                    store.save(this);
                });
        ui.switchRow("Dual status bar",
                "Notifications on a second row under the clock, with the date in full when"
                        + " Date in status bar is on · restart System UI",
                config.dualStatusBar, checked -> {
                    config.dualStatusBar = checked;
                    store.save(this);
                });
        ui.switchRow("Dual status bar in landscape",
                "The second row in landscape too · restart System UI",
                config.dualStatusBarLandscape, checked -> {
                    config.dualStatusBarLandscape = checked;
                    store.save(this);
                });
        ui.switchRow("Month as a number", "The second row's date as 8/11, not 8 November"
                        + " · restart System UI",
                config.dualStatusBarMonthNumber, checked -> {
                    config.dualStatusBarMonthNumber = checked;
                    store.save(this);
                });
        ui.switchRow("Year in the date", "The second row's date with the year"
                        + " · restart System UI",
                config.dualStatusBarYear, checked -> {
                    config.dualStatusBarYear = checked;
                    store.save(this);
                });
        ui.switchRow("Date at the end",
                "On the second row, the date at the right end, under the Wi-Fi, signal and"
                        + " battery icons; with Notifications at the end, the icons stop before it"
                        + " · restart System UI",
                config.dualStatusBarDateAtEnd, checked -> {
                    config.dualStatusBarDateAtEnd = checked;
                    store.save(this);
                });
        ui.switchRow("Notifications at the end",
                "On the second row, the notifications at the right end of the bar, under the"
                        + " Wi-Fi, signal and battery icons · restart System UI",
                config.dualStatusBarNotificationsAtEnd, checked -> {
                    config.dualStatusBarNotificationsAtEnd = checked;
                    store.save(this);
                });

        ui.section("OLED saver");
        choiceRow(ui, "Move the status bar",
                "Its content shifts a few pixels so no pixel stays lit in one place; never past"
                        + " the screen's edges · restart System UI",
                new String[]{"Never", "Every 30 seconds", "Every minute", "Every 2 minutes",
                        "Every 5 minutes", "Every 10 minutes", "Every 15 minutes",
                        "Every 30 minutes"},
                new int[]{0, 30, 60, 120, 300, 600, 900, 1800},
                config.statusBarShiftSeconds, value -> config.statusBarShiftSeconds = value);
        choiceRow(ui, "How far it moves", "At most, on each side · restart System UI",
                new String[]{"1 dp", "2 dp", "3 dp", "4 dp", "6 dp"},
                new int[]{1, 2, 3, 4, 6},
                config.statusBarShiftDp, value -> config.statusBarShiftDp = value);
        choiceRow(ui, "Hide the status bar",
                "Fades out after this time; back on a new notification, when the shade is"
                        + " pulled or the screen turns on; a tap on the bar shows it for 10 s"
                        + " · restart System UI",
                new String[]{"Never", "Instantly (shown 5 s)", "After 1 minute",
                        "After 2 minutes", "After 5 minutes", "After 10 minutes",
                        "After 15 minutes", "After 20 minutes", "After 30 minutes"},
                new int[]{-1, 0, 1, 2, 5, 10, 15, 20, 30},
                config.statusBarHideMinutes, value -> config.statusBarHideMinutes = value);

        ui.section("Quick settings");
        ui.switchRow("Green quick settings", "Quick settings icons in glowing green",
                config.ndotQuickSettings, checked -> {
                    config.ndotQuickSettings = checked;
                    store.save(this);
                });
        ui.switchRow("Amber dashboard tiles",
                "Tiles that are on glow amber like a retro car dashboard, not green",
                config.qsAmber, checked -> {
                    config.qsAmber = checked;
                    store.save(this);
                });

        ui.section("System screens");
        ui.switchRow("System screens in landscape", "Nothing's portrait-only screens that work"
                + " in landscape follow auto-rotate (quick settings widget picker, Home settings, app"
                + " drawer settings, Customisation)",
                config.systemScreens, checked -> {
                    config.systemScreens = checked;
                    store.save(this);
                });
    }

    private void renderLock(Ui ui, Config config) {
        restartSystemUiRow(ui);
        ui.section("Landscape");
        ui.switchRow("Lock screen rotation (test)",
                "Lock screen, PIN screen and fingerprint follow auto-rotate",
                config.lockRotation, checked -> {
                    config.lockRotation = checked;
                    store.save(this);
                });
        ui.switchRow("Always-on display rotation",
                "The always-on display turns with the phone too; keeps the rotation sensor on"
                        + " while it shows (a little more battery)",
                config.aodRotation, checked -> {
                    config.aodRotation = checked;
                    store.save(this);
                });
        ui.switchRow("Check orientation on wake and unlock",
                "As the screen comes on, and again on unlock, the screen turns at once to how"
                        + " the phone is held, without waiting for it to be moved (auto-rotate"
                        + " still decides)",
                config.wakeOrientation, checked -> {
                    config.wakeOrientation = checked;
                    store.save(this);
                });
    }

    private void renderBackup(Ui ui) {
        ui.section("Backup");
        ui.row("Export", "Save folders, options and covers to a file", null, null,
                v -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/json")
                        .putExtra(Intent.EXTRA_TITLE, "nothing2something-backup.json"),
                        REQUEST_EXPORT));
        ui.row("Import", "Replace the current setup with a backup file", null, null,
                v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*"), REQUEST_IMPORT));
    }

    private void renderPerformance(Ui ui, Config config) {
        ui.note("Experimental, CMF Phone 1 only, needs root.");
        if (config.perfOverlay && !ui.collecting()) {
            // After a reboot the overlay comes back when this page is opened.
            com.dressence.nothinglauncherplus.perf.OverlayService.start(this);
        }
        ui.section("Overlay");
        ui.switchRow("Ndot performance overlay", "Each CPU core's clock, GPU clock and load, CPU,"
                + " GPU, SoC and battery temperatures, FPS · over every app and the lock screen",
                config.perfOverlay,
                checked -> {
                    config.perfOverlay = checked;
                    store.save(this);
                    if (checked) {
                        // System UI draws it: no "display over other apps" needed.
                        com.dressence.nothinglauncherplus.perf.OverlayService.start(this);
                    } else {
                        com.dressence.nothinglauncherplus.perf.OverlayService.stop(this);
                    }
                });

        ui.section("PerfMTK");
        ui.row("Profile", "Performance, Balanced, Powersave or Powersave+ now · PerfMTK's own"
                + " app profiles still switch it when apps change", null, ui.chevron(),
                v -> choosePerfMtkProfile());

        ui.row("Thermal limits", "Switch PerfMTK's thermal throttling on or off · off runs"
                + " hotter, for short gaming sessions", null, ui.chevron(),
                v -> chooseThermal());

        ui.section("Games");
        int n = config.unlockGames.size();
        ui.row("120 fps unlock", (n == 0 ? "No games" : n == 1 ? "1 game" : n + " games")
                + " · the game sees a Galaxy S24 Ultra · read the warning", null, ui.chevron(),
                v -> warnBeforeUnlock(config));
    }

    private static final String[] PERFMTK_PROFILES =
            {"performance", "balanced", "powersave", "powersave+"};

    private void choosePerfMtkProfile() {
        Dialogs.builder(this)
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
        Dialogs.builder(this)
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
                    ? com.dressence.nothinglauncherplus.perf.Device.su("perfmtk " + arguments)
                    : "PerfMTK is not installed";
            runOnUiThread(() -> {
                if (failure == null) {
                    saved.run();
                    store.save(this);
                }
                Toast.makeText(this, failure == null ? done : "Failed: " + failure,
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void warnBeforeUnlock(Config config) {
        Dialogs.builder(this)
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
        java.util.List<Apps.App> all = Apps.all(this);
        java.util.List<String> packages = new ArrayList<>();
        java.util.List<String> labels = new ArrayList<>();
        for (Apps.App app : all) {
            String pkg = android.content.ComponentName.unflattenFromString(app.component)
                    .getPackageName();
            if (!packages.contains(pkg) && !pkg.equals(getPackageName())) {
                packages.add(pkg);
                labels.add(app.label);
            }
        }
        boolean[] checked = new boolean[packages.size()];
        for (int i = 0; i < packages.size(); i++) {
            checked[i] = config.unlockGames.contains(packages.get(i));
        }
        Dialogs.builder(this)
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
                    store.save(this);
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void renderDiagnostics(Ui ui, Config config) {
        ui.section("Logging");
        ui.switchRow("Diagnostic logging", "Writes what is needed to fix problems to the LSPosed"
                + " log: orientation changes, lock screen layout, fingerprint events, portrait-only"
                + " screens", config.diagnosticLogging, checked -> {
                    config.diagnosticLogging = checked;
                    store.save(this);
                });

        ui.section("Debug report");
        ui.row("Capture debug report", "Waits while you set the problem up, then records the"
                + " screen and saves screenshots, layer, window and view layouts, logcat and the"
                + " LSPosed log to Download · needs root · may hold personal info", null, null,
                v -> chooseReportTimes());
        ui.row("Stop capture", "Ends a running capture now; it still saves what it has", null,
                null, v -> stopReport());

        ui.section("Test notifications");
        ui.row("Post test notifications", "Short, long, conversation, progress, picture and"
                + " reminder notifications, to check how the lock screen lays them out", null,
                null, v -> chooseTestNotifications());
        ui.row("Clear test notifications", "Removes the ones posted here", null, null, v -> {
            TestNotifications.clear(this);
            Toast.makeText(this, "Test notifications cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private static final int[] TEST_COUNTS = {1, 3, TestNotifications.KINDS, 12};
    private static final int[] TEST_DELAYS = {0, 10};
    private static final int NOTIFICATION_REQUEST = 7100;
    /** The test notifications waiting for the notification permission: {count, delay}. */
    private int[] pendingTest;

    private void chooseTestNotifications() {
        String[] counts = new String[TEST_COUNTS.length];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = TEST_COUNTS[i] + (TEST_COUNTS[i] == 1 ? " notification" : " notifications");
        }
        Dialogs.builder(this)
                .setTitle("Post")
                .setItems(counts, (d, count) -> Dialogs.builder(this)
                        .setTitle("When")
                        .setItems(new String[] {"Now", "In 10 seconds (lock the phone first)"},
                                (d2, when) -> postTestNotifications(TEST_COUNTS[count],
                                        TEST_DELAYS[when]))
                        .setNegativeButton("Cancel", null)
                        .show())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void postTestNotifications(int count, int delay) {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingTest = new int[] {count, delay};
            requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_REQUEST);
            return;
        }
        android.content.Context app = getApplicationContext();
        Runnable post = () -> {
            if (!TestNotifications.post(app, count)) {
                Toast.makeText(app, "Notifications are off for Nothing2Something",
                        Toast.LENGTH_LONG).show();
            }
        };
        if (delay == 0) {
            post.run();
            Toast.makeText(this, "Posted " + count + " test notifications",
                    Toast.LENGTH_SHORT).show();
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(post, delay * 1000L);
            Toast.makeText(this, "Posting in " + delay + " seconds: lock the phone now",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != NOTIFICATION_REQUEST || pendingTest == null) {
            return;
        }
        int[] test = pendingTest;
        pendingTest = null;
        if (grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            postTestNotifications(test[0], test[1]);
        } else {
            Toast.makeText(this, "Allow notifications for Nothing2Something to post test ones",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void addSoundSection(Ui ui, Config config) {
        ui.section("Sound");
        ui.switchRow("Interface sounds", "Console-style chimes for the drawer, folders, apps,"
                + " pages and menus", config.uiSounds, checked -> {
                    config.uiSounds = checked;
                    store.save(this);
                });
        ui.switchRow("Background music", "Nothing's Calm, Focus and Relax breathing sounds with"
                + " console-menu layers that breathe along. Plays on the home screen only and goes"
                + " quiet while other audio or a call plays", config.music,
                checked -> {
                    config.music = checked;
                    store.save(this);
                });
        Style style = Style.parse(config.musicStyle);
        ui.row("Music style", style.title + " · " + style.summary, null, ui.chevron(),
                v -> chooseStyle(config));
        addSliderRow(ui, "Music volume", config.musicVolume, value -> value + "%", value -> {
            config.musicVolume = value;
            store.save(this);
            if (preview().isMusicPlaying()) {
                preview().playMusic(Style.parse(config.musicStyle), value / 100f);
            }
        });
        boolean previewing = preview().isMusicPlaying();
        ui.row(previewing ? "Stop preview" : "Preview music",
                previewing ? "Playing " + style.title : "Listen to the selected style here",
                null, null, v -> {
                    if (preview().isMusicPlaying()) {
                        preview().stopMusic();
                    } else {
                        preview().playMusic(Style.parse(config.musicStyle),
                                config.musicVolume / 100f);
                    }
                    render();
                });
    }

    private void chooseStyle(Config config) {
        Style[] styles = Style.values();
        String[] names = new String[styles.length];
        int checked = 0;
        for (int i = 0; i < styles.length; i++) {
            names[i] = styles[i].title + "\n" + styles[i].summary;
            if (styles[i].name().equals(config.musicStyle)) {
                checked = i;
            }
        }
        Dialogs.builder(this)
                .setTitle("Music style")
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    config.musicStyle = styles[which].name();
                    store.save(this);
                    if (preview().isMusicPlaying()) {
                        preview().playMusic(styles[which], config.musicVolume / 100f);
                    }
                    dialog.dismiss();
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private SoundEngine preview() {
        return SoundEngine.get(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        preview().stopMusic(); // Previews never outlive the settings screen.
    }

    private interface IntLabel {
        String label(int value);
    }

    private interface IntChange {
        void changed(int value);
    }

    /** A titled 0..100 slider; {@code onDone} runs when the finger lifts. */
    private void addSliderRow(Ui ui, String title, int value, IntLabel label, IntChange onDone) {
        LinearLayout row = ui.row(title, label.label(value), null, null, null);
        TextView summary = (TextView) ((LinearLayout) row.getChildAt(0)).getChildAt(1);
        SeekBar slider = new SeekBar(this);
        slider.setMax(100);
        slider.setProgress(value);
        slider.setPadding(ui.dp(24), ui.dp(4), ui.dp(24), ui.dp(12));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                summary.setText(label.label(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                onDone.changed(seekBar.getProgress());
            }
        });
        ui.content.addView(slider);
    }

    private void addOpacityRow(Ui ui, Config config) {
        addSliderRow(ui, "Drawer background", config.drawerOpacity, MainActivity::opacityLabel,
                value -> {
                    config.drawerOpacity = value;
                    store.save(this);
                });
    }

    /** Reports what the panel offers, so it is clear what the animations will run at. */
    private String frameRateSummary() {
        Display display = getDisplay();
        int target = Math.round(FrameRates.target(display));
        int now = Math.round(FrameRates.current(display));
        return "Up to " + target + " fps on this " + target + " Hz panel"
                + (now != target ? " (idling at " + now + " Hz)" : "");
    }

    /** Emoji covers set before motions or effects existed get their automatic ones once. */
    private void assignMissingMotions() {
        boolean changed = false;
        for (Config.Folder folder : store.config.folders) {
            if (!folder.coverText.isEmpty() && folder.coverMotion.isEmpty()) {
                folder.coverMotion = EmojiCatalog.get(this).motionFor(folder.coverText).name();
                changed = true;
            }
            // Effects follow the emoji table (they aren't picked by hand), so a newer table
            // updates them.
            String effect = folder.coverText.isEmpty() ? ""
                    : EmojiCatalog.get(this).effectFor(folder.coverText);
            if (!effect.equals(folder.coverEffect)) {
                folder.coverEffect = effect;
                changed = true;
            }
        }
        if (changed) {
            store.save(this);
        }
    }

    private static String opacityLabel(int opacity) {
        return opacity >= 100 ? "Opaque (stock)"
                : opacity == 0 ? "Transparent" : opacity + "% opaque";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Uri uri = data != null ? data.getData() : null;
        if (resultCode != RESULT_OK || uri == null) {
            return;
        }
        if (requestCode == REQUEST_EXPORT) {
            Config snapshot = store.config;
            runInBackground(() -> Backup.export(this, snapshot, uri), "Backup saved", null);
        } else if (requestCode == REQUEST_IMPORT) {
            Dialogs.builder(this)
                    .setTitle("Import backup?")
                    .setMessage("Your current folders and options will be replaced.")
                    .setPositiveButton("Import", (d, w) -> {
                        Config[] imported = new Config[1];
                        runInBackground(() -> imported[0] = Backup.restore(this, uri),
                                "Backup imported", () -> {
                                    store.config = imported[0];
                                    store.save(this);
                                    render();
                                });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private interface Work {
        void run() throws Exception;
    }

    /** Runs {@code work} off the main thread, then {@code onSuccess} on it, and reports. */
    private void runInBackground(Work work, String successMessage, Runnable onSuccess) {
        new Thread(() -> {
            String error = null;
            try {
                work.run();
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            String failure = error;
            runOnUiThread(() -> {
                if (failure == null && onSuccess != null) {
                    onSuccess.run();
                }
                Toast.makeText(this, failure == null ? successMessage : "Failed: " + failure,
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    /**
     * Lists Nothing's fonts on the phone, each previewed on a dot-matrix screen in glowing
     * green, and sets the chosen one as the system font.
     */
    private void chooseSystemFont() {
        new Thread(() -> {
            List<SystemFont.Choice> fonts = new ArrayList<>();
            fonts.add(null); // The phone's own font.
            fonts.addAll(SystemFont.find(this));
            List<Typeface> faces = new ArrayList<>();
            List<Boolean> lowercase = new ArrayList<>();
            for (SystemFont.Choice font : fonts) {
                Typeface face = SystemFont.typeface(this, font);
                faces.add(face);
                lowercase.add(face == null || FontPreview.hasLowercase(face));
            }
            runOnUiThread(() -> showFontChooser(fonts, faces, lowercase));
        }).start();
    }

    private void showFontChooser(List<SystemFont.Choice> fonts, List<Typeface> faces,
            List<Boolean> lowercase) {
        if (isFinishing()) {
            return;
        }
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        list.setPadding(pad, Ui.dp(this, 8), pad, Ui.dp(this, 8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        AlertDialog dialog = Dialogs.builder(this)
                .setTitle("System font")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .create();
        for (int i = 0; i < fonts.size(); i++) {
            SystemFont.Choice font = fonts.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), null,
                    new ColorDrawable(Color.WHITE)));
            TextView title = new TextView(this);
            title.setText(font == null ? "Phone default" : font.title
                    + (lowercase.get(i) ? "" : "  · capitals only")
                    + (font.needsAdding() ? "  · after one restart" : ""));
            title.setTextColor(Ui.TEXT);
            title.setTextSize(15);
            title.setPadding(0, 0, 0, Ui.dp(this, 6));
            row.addView(title);
            if (faces.get(i) != null) {
                FontPreview preview = new FontPreview(this);
                preview.setTypeface(faces.get(i));
                row.addView(preview, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            row.setOnClickListener(v -> {
                dialog.dismiss();
                applyFont(font);
            });
            list.addView(row);
        }
        dialog.show();
    }

    /**
     * Sets the font at once, no reboot: the system gives it to every app from its next start
     * (System Framework in the module's scope), and System UI and the launcher are restarted
     * now (just those two, a second each) to show it.
     */
    private void applyFont(SystemFont.Choice choice) {
        if (choice != null && choice.needsAdding()) {
            addFont(choice);
            return;
        }
        store.config.systemFont = choice == null ? ""
                : choice.path != null ? "file:" + choice.path : "res:" + choice.title;
        store.save(this);
        sendBroadcast(new android.content.Intent(Config.ACTION_FONT).setPackage("android"));
        new Thread(() -> {
            SystemFont.removeOldModule();
            boolean restarted = SystemFont.restartSystemUi();
            runOnUiThread(() -> Toast.makeText(this, (choice == null ? "Phone font back"
                    : choice.title + " set")
                    + (restarted ? ": status bar and launcher now use it." : ".")
                    + " Other apps use it when they next open.", Toast.LENGTH_LONG).show());
        }).start();
    }

    /**
     * A font only Nothing's widgets app has: added to the system's fonts as a file (shown after
     * one restart of the phone), and chosen, so it is the system font from that restart on.
     */
    private void addFont(SystemFont.Choice choice) {
        new Thread(() -> {
            String error = SystemFont.addToSystem(this, choice);
            runOnUiThread(() -> {
                if (error != null) {
                    Toast.makeText(this, "Could not add " + choice.title + ": " + error,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                store.config.systemFont = "file:" + SystemFont.addedPath(choice);
                store.save(this);
                Toast.makeText(this, choice.title + " added. Restart the phone once: it is the"
                        + " system font from then on.", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    /** Report wait and capture times to choose from, in seconds. */
    private static final int[] REPORT_DELAYS = {10, 15, 30, 60, 120, 300, 600};
    private static final int[] REPORT_LENGTHS = {15, 30, 60, 120, 300, 600};

    private static String duration(int seconds) {
        return seconds < 60 ? seconds + " s" : seconds / 60 + " min";
    }

    private static String[] durations(int[] seconds) {
        String[] labels = new String[seconds.length];
        for (int i = 0; i < seconds.length; i++) {
            labels[i] = duration(seconds[i]);
        }
        return labels;
    }

    /** Asks how long to wait before capturing, then how long to capture, then starts. */
    private void chooseReportTimes() {
        Dialogs.builder(this)
                .setTitle("Start capturing after")
                .setItems(durations(REPORT_DELAYS), (d, delay) -> Dialogs.builder(this)
                        .setTitle("Capture for")
                        .setItems(durations(REPORT_LENGTHS), (d2, length) -> captureReport(
                                REPORT_DELAYS[delay], REPORT_LENGTHS[length]))
                        .setNegativeButton("Cancel", null)
                        .show())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void captureReport(int delay, int length) {
        new Thread(() -> {
            String failure = DebugReport.start(this, delay, length);
            runOnUiThread(() -> {
                if (failure == null) {
                    CaptureIndicator.start(this, delay, length);
                }
                Toast.makeText(this, failure == null
                        ? "Capturing in " + duration(delay) + " for " + duration(length)
                                + ": one buzz when it starts, two when the report is saved"
                        : "Failed: " + failure, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void stopReport() {
        new Thread(() -> {
            String failure = DebugReport.stop();
            runOnUiThread(() -> {
                if (failure == null) {
                    // Straight to "saving": it ends when the report is saved.
                    CaptureIndicator.start(this, 0, 0);
                }
                Toast.makeText(this, failure == null
                        ? "Stopping; the report is saved in a few seconds"
                        : "Failed: " + failure, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    /** A row restarting System UI, for the options on a page that it reads when it starts. */
    private void restartSystemUiRow(Ui ui) {
        ui.row("Restart System UI", "Applies the options marked \"restart System UI\" now"
                + " · needs root · once every 90 s", null, null, v -> {
                    long wait = SystemUiRestart.waitSeconds();
                    if (wait > 0) {
                        // More restarts in a row look like a crash loop to LSPosed, which
                        // then turns on its safe mode.
                        Toast.makeText(this, "Wait " + wait + " s: restarting System UI again"
                                + " so soon would put LSPosed in safe mode",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    new Thread(() -> {
                        boolean done = SystemUiRestart.restart();
                        runOnUiThread(() -> Toast.makeText(this, done ? "System UI restarted"
                                : "Root not available; force stop System UI from App info",
                                Toast.LENGTH_LONG).show());
                    }).start();
                });
    }

    private void restartLauncher() {
        new Thread(() -> {
            boolean success = UpdateReceiver.restartLauncher();
            runOnUiThread(() -> Toast.makeText(this, success ? "Launcher restarted"
                    : "Root not available; force stop Nothing Launcher from App info",
                    Toast.LENGTH_LONG).show());
        }).start();
    }

}
