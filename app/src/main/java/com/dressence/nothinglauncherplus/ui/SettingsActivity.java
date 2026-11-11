package com.dressence.nothinglauncherplus.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Display;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import com.dressence.nothinglauncherplus.Config;
import com.dressence.nothinglauncherplus.gaming.GamingStorage;
import com.dressence.nothinglauncherplus.gaming.SteamGridArt;
import com.dressence.nothinglauncherplus.gaming.GamingSpaceActivity;
import com.dressence.nothinglauncherplus.gaming.GamingLibrary;
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
public class SettingsActivity extends Activity {

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
        if (savedInstanceState != null) {
            // Turned while the notification permission was asked: its answer still posts them.
            pendingTest = savedInstanceState.getIntArray(STATE_PENDING_TEST);
        }
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
                new PerformancePage(this, store, this::render).render(ui, config);
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
        // Once per process, and again only when the settings changed: every page is its own
        // screen, and each return to one laid all eight pages out again on the main thread
        // (0.70.14 audit).
        java.util.Map<String, ?> now = store.prefs.getAll();
        if (now.equals(indexedFor)) {
            return;
        }
        indexedFor = now;
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

    /** The settings the search index was last built from, in this process. */
    private static java.util.Map<String, ?> indexedFor;
    /** The move off the memory card was asked for in this process (once is enough). */
    private static boolean moveAsked;

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
        startActivity(new Intent(this, SettingsActivity.class).putExtra(Config.EXTRA_SETTINGS_PAGE, page));
    }

    private void renderMenu(Ui ui) {
        if (!ui.collecting()) {
            SettingsSearch.addTo(this, ui); // not while recording: its rows are the index itself
        }
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
            if (!ui.collecting() && !moveAsked) {
                moveAsked = true; // Not a new root thread on every render of the menu.
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
                + " left as Nothing made them. If other features are buggy on your phone, or to"
                + " keep the system stock · the system's part follows after a reboot",
                store.config.launcherOnly,
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
                ui.chevron(), v -> new FontSettings(this, store).choose());
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
        ui.switchRow("Show app icon", "Off: the icon leaves the app drawer and home screen; open"
                + " the module from the Nothing2Something row in Settings, Settings search or"
                + " LSPosed · needs the module active in the system (after a reboot), or Android"
                + " shows an app info icon in its place", AppIcon.shown(this),
                checked -> AppIcon.setShown(this, checked));
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

    /**
     * The home screen page. In "Launcher only" the rows whose hooks are left out there
     * (ModuleEntry: system_server's controller home, HeliBoard, Settings' task manager, the
     * wallpapers) are not shown: they were, and did nothing (0.70.16 audit).
     */
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
        if (!config.launcherOnly) { // system_server part left out there
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
        }
        ui.row("Nothing Gaming Space (alpha) (demo)", "The module's own gaming launcher, in dot-matrix style:"
                        + " pick it above as the gaming launcher · tap to open it now",
                null, ui.chevron(), v -> GamingSpaceActivity.Companion.open(this));
        ui.switchRow("Game artwork", "Games and emulators show their artwork on the big dot"
                        + " screen, looked up once and saved on the phone: the game's banner"
                        + " from Google Play (no key needed), or SteamGridDB's with your own"
                        + " API key (animated banners) · uses the internet",
                config.gamingArtwork, checked -> {
                    config.gamingArtwork = checked;
                    store.save(this);
                });
        ui.switchRow("Game trailers from Steam (video)", "A game's trailer from Steam's"
                        + " store plays on the big dot screen, muted, while it is selected ·"
                        + " a short clip downloaded once per game (a few MB) · no key needed",
                config.gamingTrailers, checked -> {
                    config.gamingTrailers = checked;
                    store.save(this);
                });
        ui.switchRow("Animated artwork first", "When a game has an animated banner on"
                        + " SteamGridDB, it plays on the big dot screen · Y on a game, Choose"
                        + " artwork, picks another",
                config.gamingArtworkAnimated, checked -> {
                    config.gamingArtworkAnimated = checked;
                    store.save(this);
                });
        String lastArt = SteamGridArt.Companion.lastResult(this);
        ui.row("SteamGridDB API key", (SteamGridArt.Companion.key(this).isEmpty() ? "Not set ·"
                        + " make one at steamgriddb.com, Profile, Preferences, API"
                        : "Set · kept only in this app, never shared")
                        + (lastArt.isEmpty() ? "" : " · last lookup: " + lastArt), null,
                ui.chevron(), v -> askSteamGridKey());
        ui.row("Check the key", "Tries one search on SteamGridDB and says what happened", null,
                ui.chevron(), v -> checkSteamGridKey());
        // Saved pictures and videos in a folder of the phone's storage (see GamingStorage):
        // Android asks for "All files access" for a folder at the top of the storage.
        boolean sharedFolder = GamingStorage.INSTANCE.shared();
        ui.row("Save in a \"Nothing Gaming Space\" folder", sharedFolder
                        ? "On · artwork in " + GamingStorage.INSTANCE.rootPath() + "/"
                        + GamingStorage.ART + ", trailers in …/" + GamingStorage.VIDEOS
                        + " · a file named after an app's package is used for it"
                        : "Off · saved inside this app · tap to allow All files access and keep"
                        + " them in the phone's storage, in SteamGridDB and Videos folders",
                null, ui.chevron(), v -> GamingStorage.INSTANCE.askAccess(this));
        int hiddenGaming = java.util.Collections.frequency(config.gamingCategories.values(),
                GamingLibrary.HIDDEN);
        if (hiddenGaming > 0) {
            ui.row("Show hidden apps again", (hiddenGaming == 1 ? "1 app" : hiddenGaming + " apps")
                            + " hidden from Nothing Gaming Space", null, ui.chevron(), v -> {
                config.gamingCategories.values().removeIf(GamingLibrary.HIDDEN::equals);
                store.save(this);
                render();
            });
        }
        if (!config.launcherOnly) { // HeliBoard is not hooked there
            ui.switchRow("Controller typing in HeliBoard", "Move over HeliBoard's keys, suggestions"
                    + " and panels with the controller · HeliBoard in the module's LSPosed scope",
                    config.heliboardController, checked -> {
                        config.heliboardController = checked;
                        store.save(this);
                    });
            ui.switchRow("HeliBoard across the whole width", "In landscape the keyboard also covers"
                    + " the strip beside the camera that Android keeps it out of; from the next"
                    + " keyboard · HeliBoard in the module's LSPosed scope",
                    config.heliboardFullWidth, checked -> {
                        config.heliboardFullWidth = checked;
                        store.save(this);
                    });
        }

        ui.section("Recents");
        ui.switchRow("Memory in recents", "Free and total memory beside Screenshot; tap it for"
                + " Running services",
                config.recentsMemory, checked -> {
                    config.recentsMemory = checked;
                    store.save(this);
                });
        if (!config.launcherOnly) { // Settings is not hooked there
            ui.switchRow("Task manager in Running services", "CPU, temperatures,"
                    + " memory, swap and network on top; each process's CPU and network; long-press"
                    + " a process to stop it · Settings in the module's LSPosed scope",
                    config.taskManager, checked -> {
                        config.taskManager = checked;
                        store.save(this);
                    });
        }
        ui.switchRow("Running services shortcut", "A Running services icon to place on the home"
                + " screen (in the widget list, like Settings shortcut) and on this app's"
                + " long-press · the quick settings tile is in the tile editor",
                RunningServices.shortcutsOn(this),
                checked -> RunningServices.setShortcuts(this, checked));

        if (!config.launcherOnly) { // the wallpapers are not hooked there
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
        }

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

    /** Tries the key with one search, off the main thread, and shows what happened. */
    private void checkSteamGridKey() {
        android.app.AlertDialog waiting = Dialogs.builder(this)
                .setTitle("SteamGridDB")
                .setMessage("Checking…")
                .show();
        new Thread(() -> {
            String result = SteamGridArt.Companion.check(this);
            runOnUiThread(() -> {
                if (!isDestroyed()) {
                    waiting.setMessage(result);
                }
            });
        }, "steamgriddb-check").start();
    }

    /** The user's own SteamGridDB key, typed in; kept in this app's private storage only. */
    private void askSteamGridKey() {
        android.widget.EditText field = new android.widget.EditText(this);
        field.setSingleLine(true);
        field.setHint("API key");
        field.setText(SteamGridArt.Companion.key(this));
        int pad = Ui.dp(this, 20);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        box.addView(field);
        Dialogs.builder(this)
                .setTitle("SteamGridDB API key")
                .setMessage("Make one at steamgriddb.com: Profile, Preferences, API. It stays in"
                        + " this app only.")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    SteamGridArt.Companion.setKey(this, field.getText().toString());
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
                "Ndot clock and date, every status icon in glowing green · turned off, the"
                        + " texts stay green until System UI restarts",
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

        ui.section("Full-screen apps");
        choiceRow(ui, "Full screen",
                "Picked apps hide the status bar and use its space, also beside the camera; a"
                        + " swipe from the top shows the bar for a moment · from the next time an"
                        + " app opens",
                new String[]{"Off", "While the OLED saver hides the bar", "Always"},
                new int[]{Config.FULL_SCREEN_OFF, Config.FULL_SCREEN_SAVER,
                        Config.FULL_SCREEN_ALWAYS},
                config.fullScreenMode, value -> config.fullScreenMode = value);
        int fullScreen = config.fullScreenApps.size();
        ui.row("Apps", (fullScreen == 0 ? "No apps" : fullScreen == 1 ? "1 app"
                        : fullScreen + " apps")
                        + " · also tick each app in the module's LSPosed scope, then reopen it",
                null, ui.chevron(), v -> AppPickerActivity.openFullScreen(this));

        ui.section("Quick settings");
        ui.switchRow("Green quick settings", "Quick settings icons in glowing green · turned"
                + " off, the tile names stay green until System UI restarts",
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
                "Lock screen, PIN screen and fingerprint follow auto-rotate · the PIN screen's"
                        + " layout after a restart of System UI",
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
                v -> ReportSettings.chooseTimes(this));
        ui.row("Stop capture", "Ends a running capture now; it still saves what it has", null,
                null, v -> ReportSettings.stop(this));

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
    private static final String STATE_PENDING_TEST = "pendingTest";

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putIntArray(STATE_PENDING_TEST, pendingTest);
    }

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
        addSliderRow(ui, "Drawer background", config.drawerOpacity, SettingsActivity::opacityLabel,
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
                                    // PerfMTK's state and the overlay are this phone's state
                                    // now, not settings: kept, not taken from the backup (a
                                    // backup's "thermal off" made the Game Bar's first tap do
                                    // nothing; 0.70.16 audit).
                                    imported[0].perfProfile = store.config.perfProfile;
                                    imported[0].thermalOff = store.config.thermalOff;
                                    imported[0].perfOverlay = store.config.perfOverlay;
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
            } catch (Throwable e) {
                // Throwable: an error (out of memory) would end this thread and the app.
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

    /** A row restarting System UI, for the options on a page that it reads when it starts. */
    private void restartSystemUiRow(Ui ui) {
        ui.row("Restart System UI", "Applies the options marked \"restart System UI\" now"
                + " · needs root · once every 90 s", null, null, v -> {
                    long wait = SystemUiRestart.waitSeconds(this);
                    if (wait > 0) {
                        // More restarts in a row look like a crash loop to LSPosed, which
                        // then turns on its safe mode.
                        Toast.makeText(this, "Wait " + wait + " s: restarting System UI again"
                                + " so soon would put LSPosed in safe mode",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    new Thread(() -> {
                        boolean done = SystemUiRestart.restart(this);
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
