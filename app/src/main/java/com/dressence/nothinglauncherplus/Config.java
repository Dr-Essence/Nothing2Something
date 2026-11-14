package com.dressence.nothinglauncherplus;

import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Custom drawer folder configuration, shared between the settings UI (module process) and the
 * launcher hooks (xposed/ConfigStore: the framework's copy, or on the old API a shared file).
 */
public final class Config {

    public static final String MODULE_PKG = "com.dressence.nothinglauncherplus";
    public static final String LAUNCHER_PKG = "com.nothing.launcher";
    public static final String SYSTEMUI_PKG = "com.android.systemui";
    /** Nothing Launcher's activity: Launcher3's class, and Nothing's subclass of it. */
    public static final String CLS_LAUNCHER = "com.android.launcher3.Launcher";
    public static final String CLS_NT_LAUNCHER = "com.nothing.launcher.NTLauncher";
    public static final String SETTINGS_ACTIVITY = MODULE_PKG + ".ui.SettingsActivity";
    /** Settings page to open (an extra of {@link #SETTINGS_ACTIVITY}); without it, the menu. */
    public static final String EXTRA_SETTINGS_PAGE = "page";
    public static final String PAGE_DRAWER = "drawer";
    public static final String FOLDER_ACTIVITY = MODULE_PKG + ".ui.FolderActivity";
    /** String extra naming the folder to edit in {@link #FOLDER_ACTIVITY}. */
    public static final String EXTRA_FOLDER_ID = "folder_id";

    public static final String PREFS_NAME = "config";
    public static final String KEY_FOLDERS = "folders";
    public static final String KEY_CUSTOM_FIRST = "custom_first";
    public static final String KEY_HIDE_BUILTIN = "hide_builtin";
    public static final String KEY_LOOSE_APPS = "loose_apps";
    public static final String KEY_DRAWER_OPACITY = "drawer_opacity";
    public static final String KEY_ANIMATE_COVERS = "animate_covers";
    public static final String KEY_EMOJI_COVERS = "emoji_covers";
    public static final String KEY_COVERS_OFF_IN_SAVER = "covers_off_in_saver";
    public static final String KEY_DOT_MATRIX = "dot_matrix";
    public static final String KEY_GREEN_DOTS = "green_dots";
    public static final String KEY_DOT_STYLE = "dot_style";
    public static final String KEY_ICON_DOT_STYLE = "icon_dot_style";
    public static final String KEY_NDOT_STATUS = "ndot_status_bar";
    public static final String KEY_STATUS_DATE = "status_bar_date";
    public static final String KEY_DUAL_STATUS = "dual_status_bar";
    public static final String KEY_DUAL_STATUS_LAND = "dual_status_bar_landscape";
    public static final String KEY_DUAL_MONTH_NUMBER = "dual_status_bar_month_number";
    public static final String KEY_DUAL_YEAR = "dual_status_bar_year";
    public static final String KEY_DUAL_DATE_END = "dual_status_bar_date_end";
    public static final String KEY_DUAL_NOTIF_END = "dual_status_bar_notifications_end";
    public static final String KEY_SB_SHIFT_SECONDS = "status_bar_shift_seconds";
    public static final String KEY_SB_SHIFT_DP = "status_bar_shift_dp";
    public static final String KEY_SB_HIDE_MINUTES = "status_bar_hide_minutes";
    public static final String KEY_SYSTEM_FONT = "system_font";
    public static final String KEY_NDOT_QS = "ndot_quick_settings";
    public static final String KEY_QS_AMBER = "qs_amber";
    public static final String KEY_UI_SOUNDS = "ui_sounds";
    public static final String KEY_MUSIC = "music";
    public static final String KEY_MUSIC_STYLE = "music_style";
    public static final String KEY_MUSIC_VOLUME = "music_volume";
    public static final String KEY_ALLOW_ROTATION = "allow_rotation";
    public static final String KEY_CONTROLLER_NAV = "controller_nav";
    public static final String KEY_CONTROLLER_HOME = "controller_home";
    public static final String KEY_RECENTS_MEMORY = "recents_memory";
    public static final String KEY_FOLDER_GLASS = "folder_glass";
    public static final String KEY_FOLDER_GLASS_TINT = "folder_glass_tint";
    public static final String KEY_FOLDER_ICON_GLASS = "folder_icon_glass";
    public static final String KEY_FOLDER_ICON_GLASS_TINT = "folder_icon_glass_tint";
    public static final String KEY_SEARCH_BAR_GLASS = "search_bar_glass";
    public static final String KEY_SEARCH_BAR_GLASS_TINT = "search_bar_glass_tint";
    public static final String KEY_WIDGET_GLASS = "widget_glass";
    public static final String KEY_WIDGET_GLASS_TINT = "widget_glass_tint";
    public static final String KEY_DRAWER_GLASS = "drawer_glass";
    public static final String KEY_DRAWER_GLASS_TINT = "drawer_glass_tint";
    public static final String KEY_SETTINGS_GLASS = "settings_glass";
    public static final String KEY_SETTINGS_GLASS_TINT = "settings_glass_tint";
    public static final String KEY_DRAWER_FOLDER_GLASS = "drawer_folder_glass";
    public static final String KEY_DRAWER_FOLDER_GLASS_TINT = "drawer_folder_glass_tint";
    public static final String KEY_TASK_MANAGER = "task_manager";
    public static final String KEY_LIVING_WORLDS_SOUNDS = "living_worlds_sounds";
    public static final String KEY_LIVING_WORLDS_TURN = "living_worlds_turn";
    public static final String KEY_LIVING_WORLDS_LANDSCAPE_Y = "living_worlds_landscape_y";
    public static final String KEY_PALETTE_CYCLE_SOUNDS = "palette_cycle_sounds";
    public static final String KEY_PALETTE_CYCLE_LANDSCAPE_Y = "palette_cycle_landscape_y";
    public static final String KEY_CONTROLLER_HOME_APP = "controller_home_app";
    public static final String KEY_LOCK_ROTATION = "lock_rotation";
    public static final String KEY_AOD_ROTATION = "aod_rotation";
    public static final String KEY_WAKE_ORIENTATION = "wake_orientation";
    public static final String KEY_LAUNCHER_ONLY = "launcher_only";
    public static final String KEY_HELIBOARD_CONTROLLER = "heliboard_controller";
    public static final String KEY_HELIBOARD_FULL_WIDTH = "heliboard_full_width";
    public static final String KEY_DIAGNOSTIC_LOGGING = "diagnostic_logging";
    public static final String KEY_SYSTEM_SCREENS = "system_screens";
    public static final String KEY_PERF_OVERLAY = "perf_overlay";
    public static final String KEY_UNLOCK_GAMES = "unlock_games";
    public static final String KEY_FULL_SCREEN_APPS = "full_screen_apps";
    public static final String KEY_GAMING_CATEGORIES = "gaming_categories";
    public static final String KEY_GAMING_WARNED = "gaming_warned";
    public static final String KEY_GAMING_ARTWORK = "gaming_artwork";
    public static final String KEY_GAMING_NAMES = "gaming_names";
    public static final String KEY_GAMING_ANIMATED = "gaming_artwork_animated";
    public static final String KEY_GAMING_TRAILERS = "gaming_trailers";
    public static final String KEY_GAMING_FRAMES = "gaming_frames";
    public static final String KEY_FULL_SCREEN_MODE = "full_screen_mode";

    /** Full-screen apps: when the picked apps hide the status bar. */
    public static final int FULL_SCREEN_OFF = 0;
    public static final int FULL_SCREEN_SAVER = 1;
    public static final int FULL_SCREEN_ALWAYS = 2;
    public static final String KEY_PERF_PROFILE = "perf_profile";
    public static final String KEY_THERMAL_OFF = "thermal_off";
    public static final String KEY_SAVER_FULL_REFRESH = "saver_full_refresh";

    /** Broadcast sent to the launcher and system_server after the configuration changes. */
    public static final String ACTION_RELOAD = MODULE_PKG + ".action.RELOAD";
    /**
     * When the settings were saved (ms), kept with them under this key by the settings app: a
     * hooked app never goes back to older settings (xposed/ConfigStore), and the crash-loop
     * guards' "a setting was saved since" mark (systemui/CrashGuard).
     */
    public static final String KEY_SAVED = "saved_at";
    /**
     * The saved settings themselves, carried by ACTION_RELOAD and ACTION_FONT (a Bundle), so a
     * save reaches the hooked apps at once (xposed/ConfigStore.afterSave).
     */
    public static final String EXTRA_SETTINGS = "settings";
    /** To the system: open the notification panel (the launcher may not). */
    public static final String ACTION_EXPAND_NOTIFICATIONS =
            MODULE_PKG + ".action.EXPAND_NOTIFICATIONS";
    /** To the system: open the app whose music is playing (the launcher can't see sessions). */
    public static final String ACTION_OPEN_MEDIA_APP = MODULE_PKG + ".action.OPEN_MEDIA_APP";
    /** To the launcher: bring back home items another grid's database still has. */
    public static final String ACTION_RESTORE_ITEMS = MODULE_PKG + ".action.RESTORE_ITEMS";
    /** Sent by the launcher when it is shown or left, for the wallpapers' sounds. */
    public static final String ACTION_HOME_SHOWN = MODULE_PKG + ".HOME_SHOWN";
    public static final String EXTRA_SHOWN = "shown";
    /** Sent by the recents memory button, for system_server to open Running services. */
    public static final String ACTION_RUNNING_SERVICES = MODULE_PKG + ".RUNNING_SERVICES";
    /** Sent when the system font choice changes, for system_server to rebuild its font map. */
    public static final String ACTION_FONT = MODULE_PKG + ".SYSTEM_FONT";

    /** Prefix of the category keys we inject into the Smart drawer. */
    public static final String CATEGORY_PREFIX = "nlplus:";

    /** Read-only provider serving folder cover images to the launcher. */
    public static final String COVER_AUTHORITY = MODULE_PKG + ".covers";

    public static final class Folder {
        public final String id;
        public String name;
        /** Flattened {@link android.content.ComponentName}s, in display order. */
        public final LinkedHashSet<String> apps = new LinkedHashSet<>();
        /** Emoji or short text shown as the tile cover; empty for none. */
        public String coverText = "";
        /** {@code anim.Motion} name played by the emoji cover; empty means not chosen yet. */
        public String coverMotion = "";
        /**
         * {@code anim.DotEffect} played inside the dots, as the emoji table gives it ("NAME" or
         * "NAME:anchor,points"); empty means not chosen yet.
         */
        public String coverEffect = "";
        /** Last-modified time of the cover image; 0 when the folder has no cover image. */
        public long coverImageVersion;

        public Folder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String categoryKey() {
            return CATEGORY_PREFIX + id;
        }

        public boolean hasCover() {
            return coverImageVersion != 0 || !coverText.isEmpty();
        }

        /** The version query parameter makes every image change a new cache key. */
        public Uri coverImageUri() {
            return new Uri.Builder().scheme("content").authority(COVER_AUTHORITY)
                    .appendPath(id).appendQueryParameter("v", Long.toString(coverImageVersion))
                    .build();
        }
    }

    public final List<Folder> folders = new ArrayList<>();
    public boolean customFirst = true;
    public boolean hideBuiltin = false;
    /** Show apps of the "Others" category as plain icons instead of a folder. */
    public boolean looseApps = false;
    /** App drawer background opacity in percent (100 = stock). */
    public int drawerOpacity = 100;
    /** Play emoji cover animations. */
    public boolean animateCovers = true;
    /** Show emoji covers on folder tiles (off: Nothing's app previews; the emoji is kept). */
    public boolean emojiCovers = true;
    /** Show Nothing's app previews instead of emoji covers while battery saver is on. */
    public boolean coversOffInSaver = false;
    /** Emoji covers drawn as a Nothing-style dot matrix. */
    public boolean dotMatrix = true;
    /** Dot-matrix emoji in shades of green only, like a one-colour LED screen (0.70.37). */
    public boolean greenDots = false;
    /**
     * How the dot screen lights (0.70.40, anim/DotMatrix): the look until now, or an emulated
     * panel where each dot is its own light (soft or strong glow, normal or smaller dots), or a
     * vacuum fluorescent display like an 80s car dashboard's. Kept while Dr.Essence compares
     * them on the phone; the ones not chosen will be removed.
     */
    public int dotStyle = DOT_STYLE_CLASSIC;
    /** The same five looks for the dot-matrix app icons (0.70.41, launcher/IconScreen). */
    public int iconDotStyle = DOT_STYLE_CLASSIC;
    public static final int DOT_STYLE_CLASSIC = 0;
    public static final int DOT_STYLE_LED_SOFT = 1;
    public static final int DOT_STYLE_LED_STRONG = 2;
    public static final int DOT_STYLE_LED_SMALL = 3;
    public static final int DOT_STYLE_VFD = 4;
    /** Status bar clock and battery in glowing green NDot, like the folder screens. */
    public boolean ndotStatusBar = false;
    /** The date beside the status bar clock. */
    public boolean statusBarDate = false;
    /** A second status bar row: notifications, and the date in full, under the clock. */
    public boolean dualStatusBar = false;
    /** The second row in landscape too. */
    public boolean dualStatusBarLandscape = false;
    /** The second row's date with the month as a number ("8/11"), not its name. */
    public boolean dualStatusBarMonthNumber = false;
    /** The second row's date with the year. */
    public boolean dualStatusBarYear = false;
    /** The second row's date at the end of the left half, opposite the notifications. */
    public boolean dualStatusBarDateAtEnd = false;
    /** The second row's notifications at the end of the left half. */
    public boolean dualStatusBarNotificationsAtEnd = false;
    /** OLED saver: how often the status bar's content moves, in seconds (0: never). */
    public int statusBarShiftSeconds = 0;
    /** OLED saver: how far it moves at most, in dp. */
    public int statusBarShiftDp = 2;
    /** OLED saver: the status bar's content fades out after this many minutes (-1: never). */
    public int statusBarHideMinutes = -1;
    /**
     * The font hooked apps draw their text in: "file:" and a font file's path, or empty for
     * the phone's own. (A font of Nothing's widgets app is added to the system's fonts as a
     * file first; the old "res:" form is refused by SystemFonts.load.)
     */
    public String systemFont = "";
    /** Quick settings tile icons drawn as dot-matrix icons. */
    public boolean ndotQuickSettings = false;
    /** Quick settings tiles that are on lit amber, as a retro car dashboard, not green. */
    public boolean qsAmber = false;
    public boolean uiSounds = false;
    public boolean music = false;
    /** A {@code sound.Style} name. */
    public String musicStyle = "CALM";
    /** 0..100. */
    public int musicVolume = 45;
    /** Let the home screen and app drawer follow auto-rotate into landscape. */
    public boolean allowRotation = false;
    /** Game controller buttons (Y, L1/R1, X, Start) and focus animations. */
    public boolean controllerNav = true;
    /** Recents shows free memory; a tap opens running services. */
    public boolean recentsMemory = true;
    /** Nothing OS 5's glass folders (launcher/FolderGlass.kt). */
    public boolean folderGlass = false;
    /** The frosted folder's tint, 0-100 % (Nothing OS 5: 40). */
    public int folderGlassTint = 40;
    /** Nothing OS 5's frosted closed folders and dock search bar (launcher/FrostedSurfaces.kt). */
    public boolean folderIconGlass = false;
    public int folderIconGlassTint = 40;
    public boolean searchBarGlass = false;
    public int searchBarGlassTint = 40;
    public boolean widgetGlass = false;
    public int widgetGlassTint = 40;
    /** Frosted glass for the app drawer's background and an open drawer folder (DrawerGlass). */
    public boolean drawerGlass = false;
    public int drawerGlassTint = 40;
    /** The module's own screens over the wallpaper, blurred live (ui/Ui.applyBackground). */
    public boolean settingsGlass = false;
    public int settingsGlassTint = 60;
    public boolean drawerFolderGlass = false;
    public int drawerFolderGlassTint = 40;
    /** Running services (Settings) as a task manager. */
    public boolean taskManager = false;
    /** Living Worlds' ambient sounds on the home screen and in the app drawer. */
    public boolean livingWorldsSounds = true;
    /** Living Worlds framed by the module: a turn is only a new framing. */
    public boolean livingWorldsTurn = true;
    /** Living Worlds in landscape: where on the scene, 0 top .. 100 bottom (50 its own). */
    public int livingWorldsLandscapeY = 50;
    /** Palette Cycle's worlds with the web demo's sounds on home and the drawer. */
    public boolean paletteCycleSounds = true;
    /** Palette Cycle in landscape: where on the picture, 0 top .. 100 bottom. */
    public int paletteCycleLandscapeY = 50;
    /** While a game controller is connected, home is the gaming launcher below. */
    public boolean controllerHome = false;
    /** Package of the gaming launcher ("" none picked). */
    public String controllerHomeApp = "";
    /** Test: the lock screen follows auto-rotate too, and its landscape layout is logged. */
    public boolean lockRotation = false;
    /** The always-on display follows how the phone is held too (the rotation sensor stays on). */
    public boolean aodRotation = true;
    /** As the screen comes on and on unlock, the screen turns to how the phone is held now. */
    public boolean wakeOrientation = false;
    /**
     * Only Nothing Launcher (and Nothing's widget apps) are changed, plus the system's part of
     * home screen rotation; System UI, the system font, Settings and the other apps are left
     * alone. Read as each process starts (the system's part at boot).
     */
    public boolean launcherOnly = false;
    /** Type in HeliBoard with a game controller. */
    public boolean heliboardController = true;
    /** HeliBoard across the whole width in landscape, also beside the camera. */
    public boolean heliboardFullWidth = false;
    /** Write the diagnostic lines (orientation, lock screen layout, fingerprint) to the log. */
    public boolean diagnosticLogging = false;
    /** Nothing's portrait-only system screens that work in landscape follow auto-rotate. */
    public boolean systemScreens = false;
    /** The Ndot performance overlay is on (CMF Phone 1). */
    public boolean perfOverlay = false;
    /** Packages of games shown a Galaxy S24 Ultra, to unlock their 120 fps (CMF Phone 1). */
    public final java.util.Set<String> unlockGames = new java.util.LinkedHashSet<>();
    /**
     * Nothing Gaming Space: apps moved to another category by hand, or hidden (package to
     * category name, see GamingLibrary).
     */
    public final java.util.Map<String, String> gamingCategories = new java.util.LinkedHashMap<>();
    /** Nothing Gaming Space showed its epilepsy warning once already. */
    public boolean gamingWarned = false;
    /** Nothing Gaming Space: game artwork from SteamGridDB (needs the user's own API key). */
    public boolean gamingArtwork = false;
    /** Nothing Gaming Space: an animated banner first, when a game has one. */
    public boolean gamingArtworkAnimated = true;
    /** Nothing Gaming Space: a game's Steam trailer plays on the big screen (streamed). */
    public boolean gamingTrailers = false;
    /** Nothing Gaming Space: where each game's picture sits in the banner ("x;y;zoom"). */
    public final java.util.Map<String, String> gamingFrames = new java.util.LinkedHashMap<>();
    /**
     * Nothing Gaming Space: names the user gave apps (package to name), shown instead of the
     * app's own and searched on SteamGridDB, so a game whose Play name differs is found.
     */
    public final java.util.Map<String, String> gamingNames = new java.util.LinkedHashMap<>();
    /** Apps (packages) that hide the status bar and use its space; see FullScreenApps. */
    public final java.util.Set<String> fullScreenApps = new java.util.LinkedHashSet<>();
    /** When they do: FULL_SCREEN_OFF, _SAVER (while the OLED saver hides the bar), _ALWAYS. */
    public int fullScreenMode = FULL_SCREEN_OFF;
    /** The PerfMTK profile last set from the Game Bar or the Performance page ("" none). */
    public String perfProfile = "";
    /** PerfMTK's thermal limits were last switched off. */
    public boolean thermalOff = false;
    /** Battery saver keeps the screen's own refresh rate (no 60 Hz cap). */
    public boolean saverFullRefresh = false;
    /** When these settings were saved ({@link #KEY_SAVED}); 0 before the first save. */
    public long savedAt = 0L;

    private final Map<String, Folder> byComponent = new HashMap<>();
    private final Map<String, Folder> byKey = new HashMap<>();

    public static Config read(SharedPreferences prefs) {
        Config config = new Config();
        config.readSettings(new Source() {
            @Override
            public boolean bool(String key, boolean otherwise) {
                return prefs.getBoolean(key, otherwise);
            }

            @Override
            public int number(String key, int otherwise) {
                return prefs.getInt(key, otherwise);
            }

            @Override
            public String text(String key, String otherwise) {
                return prefs.getString(key, otherwise);
            }
        });
        try {
            config.readFolders(new JSONArray(prefs.getString(KEY_FOLDERS, "[]")));
        } catch (JSONException ignored) {
            // Corrupt config: keep whatever was parsed so far.
        }
        config.reindex();
        config.savedAt = prefs.getLong(KEY_SAVED, 0L);
        return config;
    }

    public void write(SharedPreferences.Editor editor) {
        editor.putString(KEY_FOLDERS, foldersJson().toString());
        writeSettings(new Sink() {
            @Override
            public void bool(String key, boolean value) {
                editor.putBoolean(key, value);
            }

            @Override
            public void number(String key, int value) {
                editor.putInt(key, value);
            }

            @Override
            public void text(String key, String value) {
                editor.putString(key, value);
            }
        });
        reindex();
    }

    /** Everything as one JSON object, for backups. */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject().put(KEY_FOLDERS, foldersJson());
        writeSettings(new Sink() {
            @Override
            public void bool(String key, boolean value) {
                put(key, value);
            }

            @Override
            public void number(String key, int value) {
                put(key, value);
            }

            @Override
            public void text(String key, String value) {
                put(key, value);
            }

            private void put(String key, Object value) {
                try {
                    json.put(key, value);
                } catch (JSONException e) {
                    // Only for a null key or a NaN: neither is ever written here.
                    throw new IllegalArgumentException(key, e);
                }
            }
        });
        return json;
    }

    public static Config fromJson(JSONObject json) throws JSONException {
        Config config = new Config();
        config.readSettings(new Source() {
            @Override
            public boolean bool(String key, boolean otherwise) {
                return json.optBoolean(key, otherwise);
            }

            @Override
            public int number(String key, int otherwise) {
                return json.optInt(key, otherwise);
            }

            @Override
            public String text(String key, String otherwise) {
                return json.optString(key, otherwise);
            }
        });
        config.readFolders(json.getJSONArray(KEY_FOLDERS));
        config.reindex();
        return config;
    }

    /** Where settings are read from: the saved preferences, or a backup. */
    private interface Source {
        boolean bool(String key, boolean otherwise);

        int number(String key, int otherwise);

        String text(String key, String otherwise);
    }

    /** Where settings are written to. */
    private interface Sink {
        void bool(String key, boolean value);

        void number(String key, int value);

        void text(String key, String value);
    }

    /** Every setting but the folders, with its default: the one list of them. */
    private void readSettings(Source in) {
        customFirst = in.bool(KEY_CUSTOM_FIRST, true);
        hideBuiltin = in.bool(KEY_HIDE_BUILTIN, false);
        looseApps = in.bool(KEY_LOOSE_APPS, false);
        drawerOpacity = Math.max(0, Math.min(100, in.number(KEY_DRAWER_OPACITY, 100)));
        animateCovers = in.bool(KEY_ANIMATE_COVERS, true);
        emojiCovers = in.bool(KEY_EMOJI_COVERS, true);
        coversOffInSaver = in.bool(KEY_COVERS_OFF_IN_SAVER, false);
        dotMatrix = in.bool(KEY_DOT_MATRIX, true);
        greenDots = in.bool(KEY_GREEN_DOTS, false);
        dotStyle = Math.max(DOT_STYLE_CLASSIC, Math.min(DOT_STYLE_VFD,
                in.number(KEY_DOT_STYLE, DOT_STYLE_CLASSIC)));
        iconDotStyle = Math.max(DOT_STYLE_CLASSIC, Math.min(DOT_STYLE_VFD,
                in.number(KEY_ICON_DOT_STYLE, DOT_STYLE_CLASSIC)));
        ndotStatusBar = in.bool(KEY_NDOT_STATUS, false);
        statusBarDate = in.bool(KEY_STATUS_DATE, false);
        dualStatusBar = in.bool(KEY_DUAL_STATUS, false);
        dualStatusBarLandscape = in.bool(KEY_DUAL_STATUS_LAND, false);
        dualStatusBarMonthNumber = in.bool(KEY_DUAL_MONTH_NUMBER, false);
        dualStatusBarYear = in.bool(KEY_DUAL_YEAR, false);
        dualStatusBarDateAtEnd = in.bool(KEY_DUAL_DATE_END, false);
        dualStatusBarNotificationsAtEnd = in.bool(KEY_DUAL_NOTIF_END, false);
        statusBarShiftSeconds = in.number(KEY_SB_SHIFT_SECONDS, 0);
        statusBarShiftDp = in.number(KEY_SB_SHIFT_DP, 2);
        statusBarHideMinutes = in.number(KEY_SB_HIDE_MINUTES, -1);
        systemFont = in.text(KEY_SYSTEM_FONT, "");
        ndotQuickSettings = in.bool(KEY_NDOT_QS, false);
        qsAmber = in.bool(KEY_QS_AMBER, false);
        uiSounds = in.bool(KEY_UI_SOUNDS, false);
        music = in.bool(KEY_MUSIC, false);
        musicStyle = in.text(KEY_MUSIC_STYLE, "CALM");
        musicVolume = Math.max(0, Math.min(100, in.number(KEY_MUSIC_VOLUME, 45)));
        allowRotation = in.bool(KEY_ALLOW_ROTATION, false);
        controllerNav = in.bool(KEY_CONTROLLER_NAV, true);
        controllerHome = in.bool(KEY_CONTROLLER_HOME, false);
        recentsMemory = in.bool(KEY_RECENTS_MEMORY, true);
        folderGlass = in.bool(KEY_FOLDER_GLASS, false);
        folderGlassTint = Math.max(0, Math.min(100, in.number(KEY_FOLDER_GLASS_TINT, 40)));
        folderIconGlass = in.bool(KEY_FOLDER_ICON_GLASS, false);
        folderIconGlassTint = Math.max(0, Math.min(100, in.number(KEY_FOLDER_ICON_GLASS_TINT, 40)));
        searchBarGlass = in.bool(KEY_SEARCH_BAR_GLASS, false);
        searchBarGlassTint = Math.max(0, Math.min(100, in.number(KEY_SEARCH_BAR_GLASS_TINT, 40)));
        widgetGlass = in.bool(KEY_WIDGET_GLASS, false);
        widgetGlassTint = Math.max(0, Math.min(100, in.number(KEY_WIDGET_GLASS_TINT, 40)));
        drawerGlass = in.bool(KEY_DRAWER_GLASS, false);
        drawerGlassTint = Math.max(0, Math.min(100, in.number(KEY_DRAWER_GLASS_TINT, 40)));
        settingsGlass = in.bool(KEY_SETTINGS_GLASS, false);
        settingsGlassTint = Math.max(0, Math.min(100, in.number(KEY_SETTINGS_GLASS_TINT, 60)));
        drawerFolderGlass = in.bool(KEY_DRAWER_FOLDER_GLASS, false);
        drawerFolderGlassTint = Math.max(0, Math.min(100,
                in.number(KEY_DRAWER_FOLDER_GLASS_TINT, 40)));
        taskManager = in.bool(KEY_TASK_MANAGER, false);
        livingWorldsSounds = in.bool(KEY_LIVING_WORLDS_SOUNDS, true);
        livingWorldsTurn = in.bool(KEY_LIVING_WORLDS_TURN, true);
        livingWorldsLandscapeY = Math.max(0, Math.min(100,
                in.number(KEY_LIVING_WORLDS_LANDSCAPE_Y, 50)));
        paletteCycleSounds = in.bool(KEY_PALETTE_CYCLE_SOUNDS, true);
        paletteCycleLandscapeY = Math.max(0, Math.min(100,
                in.number(KEY_PALETTE_CYCLE_LANDSCAPE_Y, 50)));
        controllerHomeApp = in.text(KEY_CONTROLLER_HOME_APP, "");
        lockRotation = in.bool(KEY_LOCK_ROTATION, false);
        aodRotation = in.bool(KEY_AOD_ROTATION, true);
        wakeOrientation = in.bool(KEY_WAKE_ORIENTATION, false);
        launcherOnly = in.bool(KEY_LAUNCHER_ONLY, false);
        heliboardController = in.bool(KEY_HELIBOARD_CONTROLLER, true);
        heliboardFullWidth = in.bool(KEY_HELIBOARD_FULL_WIDTH, false);
        diagnosticLogging = in.bool(KEY_DIAGNOSTIC_LOGGING, false);
        // Before this switch, system screens followed Home screen rotation.
        systemScreens = in.bool(KEY_SYSTEM_SCREENS, allowRotation);
        perfOverlay = in.bool(KEY_PERF_OVERLAY, false);
        perfProfile = in.text(KEY_PERF_PROFILE, "");
        thermalOff = in.bool(KEY_THERMAL_OFF, false);
        saverFullRefresh = in.bool(KEY_SAVER_FULL_REFRESH, false);
        unlockGames.clear();
        for (String pkg : in.text(KEY_UNLOCK_GAMES, "").split(",")) {
            if (!pkg.isEmpty()) {
                unlockGames.add(pkg);
            }
        }
        fullScreenApps.clear();
        for (String pkg : in.text(KEY_FULL_SCREEN_APPS, "").split(",")) {
            if (!pkg.isEmpty()) {
                fullScreenApps.add(pkg);
            }
        }
        fullScreenMode = in.number(KEY_FULL_SCREEN_MODE, FULL_SCREEN_OFF);
        gamingCategories.clear();
        for (String pair : in.text(KEY_GAMING_CATEGORIES, "").split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                gamingCategories.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        gamingWarned = in.bool(KEY_GAMING_WARNED, false);
        gamingArtwork = in.bool(KEY_GAMING_ARTWORK, false);
        gamingArtworkAnimated = in.bool(KEY_GAMING_ANIMATED, true);
        gamingTrailers = in.bool(KEY_GAMING_TRAILERS, false);
        gamingFrames.clear();
        for (String pair : in.text(KEY_GAMING_FRAMES, "").split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                gamingFrames.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        gamingNames.clear();
        for (String pair : in.text(KEY_GAMING_NAMES, "").split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                // Names can hold commas and equals signs: stored URL-encoded.
                gamingNames.put(pair.substring(0, eq), java.net.URLDecoder.decode(
                        pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    private void writeSettings(Sink out) {
        out.bool(KEY_CUSTOM_FIRST, customFirst);
        out.bool(KEY_HIDE_BUILTIN, hideBuiltin);
        out.bool(KEY_LOOSE_APPS, looseApps);
        out.number(KEY_DRAWER_OPACITY, drawerOpacity);
        out.bool(KEY_ANIMATE_COVERS, animateCovers);
        out.bool(KEY_EMOJI_COVERS, emojiCovers);
        out.bool(KEY_COVERS_OFF_IN_SAVER, coversOffInSaver);
        out.bool(KEY_DOT_MATRIX, dotMatrix);
        out.bool(KEY_GREEN_DOTS, greenDots);
        out.number(KEY_DOT_STYLE, dotStyle);
        out.number(KEY_ICON_DOT_STYLE, iconDotStyle);
        out.bool(KEY_NDOT_STATUS, ndotStatusBar);
        out.bool(KEY_STATUS_DATE, statusBarDate);
        out.bool(KEY_DUAL_STATUS, dualStatusBar);
        out.bool(KEY_DUAL_STATUS_LAND, dualStatusBarLandscape);
        out.bool(KEY_DUAL_MONTH_NUMBER, dualStatusBarMonthNumber);
        out.bool(KEY_DUAL_YEAR, dualStatusBarYear);
        out.bool(KEY_DUAL_DATE_END, dualStatusBarDateAtEnd);
        out.bool(KEY_DUAL_NOTIF_END, dualStatusBarNotificationsAtEnd);
        out.number(KEY_SB_SHIFT_SECONDS, statusBarShiftSeconds);
        out.number(KEY_SB_SHIFT_DP, statusBarShiftDp);
        out.number(KEY_SB_HIDE_MINUTES, statusBarHideMinutes);
        out.text(KEY_SYSTEM_FONT, systemFont);
        out.bool(KEY_NDOT_QS, ndotQuickSettings);
        out.bool(KEY_QS_AMBER, qsAmber);
        out.bool(KEY_UI_SOUNDS, uiSounds);
        out.bool(KEY_MUSIC, music);
        out.text(KEY_MUSIC_STYLE, musicStyle);
        out.number(KEY_MUSIC_VOLUME, musicVolume);
        out.bool(KEY_ALLOW_ROTATION, allowRotation);
        out.bool(KEY_CONTROLLER_NAV, controllerNav);
        out.bool(KEY_CONTROLLER_HOME, controllerHome);
        out.bool(KEY_RECENTS_MEMORY, recentsMemory);
        out.bool(KEY_FOLDER_GLASS, folderGlass);
        out.number(KEY_FOLDER_GLASS_TINT, folderGlassTint);
        out.bool(KEY_FOLDER_ICON_GLASS, folderIconGlass);
        out.number(KEY_FOLDER_ICON_GLASS_TINT, folderIconGlassTint);
        out.bool(KEY_SEARCH_BAR_GLASS, searchBarGlass);
        out.number(KEY_SEARCH_BAR_GLASS_TINT, searchBarGlassTint);
        out.bool(KEY_WIDGET_GLASS, widgetGlass);
        out.number(KEY_WIDGET_GLASS_TINT, widgetGlassTint);
        out.bool(KEY_DRAWER_GLASS, drawerGlass);
        out.number(KEY_DRAWER_GLASS_TINT, drawerGlassTint);
        out.bool(KEY_SETTINGS_GLASS, settingsGlass);
        out.number(KEY_SETTINGS_GLASS_TINT, settingsGlassTint);
        out.bool(KEY_DRAWER_FOLDER_GLASS, drawerFolderGlass);
        out.number(KEY_DRAWER_FOLDER_GLASS_TINT, drawerFolderGlassTint);
        out.bool(KEY_TASK_MANAGER, taskManager);
        out.bool(KEY_LIVING_WORLDS_SOUNDS, livingWorldsSounds);
        out.bool(KEY_LIVING_WORLDS_TURN, livingWorldsTurn);
        out.number(KEY_LIVING_WORLDS_LANDSCAPE_Y, livingWorldsLandscapeY);
        out.bool(KEY_PALETTE_CYCLE_SOUNDS, paletteCycleSounds);
        out.number(KEY_PALETTE_CYCLE_LANDSCAPE_Y, paletteCycleLandscapeY);
        out.text(KEY_CONTROLLER_HOME_APP, controllerHomeApp);
        out.bool(KEY_LOCK_ROTATION, lockRotation);
        out.bool(KEY_AOD_ROTATION, aodRotation);
        out.bool(KEY_WAKE_ORIENTATION, wakeOrientation);
        out.bool(KEY_LAUNCHER_ONLY, launcherOnly);
        out.bool(KEY_HELIBOARD_CONTROLLER, heliboardController);
        out.bool(KEY_HELIBOARD_FULL_WIDTH, heliboardFullWidth);
        out.bool(KEY_DIAGNOSTIC_LOGGING, diagnosticLogging);
        out.bool(KEY_SYSTEM_SCREENS, systemScreens);
        out.bool(KEY_PERF_OVERLAY, perfOverlay);
        out.text(KEY_PERF_PROFILE, perfProfile);
        out.bool(KEY_THERMAL_OFF, thermalOff);
        out.bool(KEY_SAVER_FULL_REFRESH, saverFullRefresh);
        out.text(KEY_UNLOCK_GAMES, String.join(",", unlockGames));
        out.text(KEY_FULL_SCREEN_APPS, String.join(",", fullScreenApps));
        out.number(KEY_FULL_SCREEN_MODE, fullScreenMode);
        StringBuilder gaming = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : gamingCategories.entrySet()) {
            if (gaming.length() > 0) {
                gaming.append(',');
            }
            gaming.append(e.getKey()).append('=').append(e.getValue());
        }
        out.text(KEY_GAMING_CATEGORIES, gaming.toString());
        out.bool(KEY_GAMING_WARNED, gamingWarned);
        out.bool(KEY_GAMING_ARTWORK, gamingArtwork);
        out.bool(KEY_GAMING_ANIMATED, gamingArtworkAnimated);
        out.bool(KEY_GAMING_TRAILERS, gamingTrailers);
        StringBuilder frames = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : gamingFrames.entrySet()) {
            if (frames.length() > 0) {
                frames.append(',');
            }
            frames.append(e.getKey()).append('=').append(e.getValue());
        }
        out.text(KEY_GAMING_FRAMES, frames.toString());
        StringBuilder names = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : gamingNames.entrySet()) {
            if (names.length() > 0) {
                names.append(',');
            }
            names.append(e.getKey()).append('=').append(java.net.URLEncoder.encode(
                    e.getValue(), java.nio.charset.StandardCharsets.UTF_8));
        }
        out.text(KEY_GAMING_NAMES, names.toString());
    }

    private void readFolders(JSONArray array) throws JSONException {
        for (int i = 0; i < array.length(); i++) {
            // One damaged entry skipped, not every folder after it: the next save would have
            // written the shortened list (0.70.16 audit).
            JSONObject obj = array.optJSONObject(i);
            String id = obj != null ? obj.optString("id", "") : "";
            if (id.isEmpty()) {
                continue;
            }
            Folder folder = new Folder(id, obj.optString("name", "Folder"));
            folder.coverText = obj.optString("coverText", "");
            folder.coverMotion = obj.optString("coverMotion", "");
            folder.coverEffect = obj.optString("coverEffect", "");
            folder.coverImageVersion = obj.optLong("coverImage", 0);
            JSONArray apps = obj.optJSONArray("apps");
            if (apps != null) {
                for (int j = 0; j < apps.length(); j++) {
                    String app = apps.optString(j, "");
                    if (!app.isEmpty()) {
                        folder.apps.add(app);
                    }
                }
            }
            folders.add(folder);
        }
    }

    private JSONArray foldersJson() {
        JSONArray array = new JSONArray();
        try {
            for (Folder folder : folders) {
                JSONObject obj = new JSONObject();
                obj.put("id", folder.id);
                obj.put("name", folder.name);
                obj.put("apps", new JSONArray(folder.apps));
                if (!folder.coverText.isEmpty()) {
                    obj.put("coverText", folder.coverText);
                }
                if (!folder.coverMotion.isEmpty()) {
                    obj.put("coverMotion", folder.coverMotion);
                }
                if (!folder.coverEffect.isEmpty()) {
                    obj.put("coverEffect", folder.coverEffect);
                }
                if (folder.coverImageVersion != 0) {
                    obj.put("coverImage", folder.coverImageVersion);
                }
                array.put(obj);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return array;
    }

    /** An app lives in at most one custom folder; assigning it here removes it from the others. */
    public void assign(String component, Folder target) {
        for (Folder folder : folders) {
            if (folder != target) {
                folder.apps.remove(component);
            }
        }
        target.apps.add(component);
        reindex();
    }

    public void reindex() {
        byComponent.clear();
        byKey.clear();
        for (Folder folder : folders) {
            byKey.put(folder.categoryKey(), folder);
            for (String app : folder.apps) {
                byComponent.putIfAbsent(app, folder);
            }
        }
    }

    public Folder folderForComponent(String flattenedComponent) {
        return byComponent.get(flattenedComponent);
    }

    public Folder folderForKey(String categoryKey) {
        return byKey.get(categoryKey);
    }

    public Folder folderForId(String id) {
        return byKey.get(CATEGORY_PREFIX + id);
    }

    public List<String> categoryKeys() {
        if (folders.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> keys = new ArrayList<>(folders.size());
        for (Folder folder : folders) {
            keys.add(folder.categoryKey());
        }
        return keys;
    }
}
