package com.dressence.nothinglauncherplus.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.dressence.nothinglauncherplus.Config;

/** Loads and saves the configuration, and tells the launcher to pick changes up. */
public final class SettingsStore {

    final SharedPreferences prefs;
    /** False when LSPosed is not managing the prefs, i.e. the launcher cannot read them. */
    final boolean shared;
    public Config config;

    @SuppressWarnings("deprecation")
    public SettingsStore(Context context) {
        SharedPreferences p;
        boolean s;
        try {
            // LSPosed (xposedsharedprefs) makes this readable by the hooked launcher.
            p = context.getSharedPreferences(Config.PREFS_NAME, Context.MODE_WORLD_READABLE);
            s = true;
        } catch (SecurityException e) {
            p = context.getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE);
            s = false;
        }
        prefs = p;
        shared = s;
        config = Config.read(prefs);
        seen = prefs.getAll();
    }

    /** The settings as last read or written here, to tell whether someone else changed them. */
    private java.util.Map<String, ?> seen;

    /**
     * Reads the settings again, only if they changed since this store last read or wrote them
     * (another screen, Gaming Space, the overlay, an import).
     *
     * <p>Mistake recorded (found in the 0.70.14 audit): every screen reloaded on each resume,
     * which replaced every Config.Folder object even when nothing had changed. A dialog left
     * open over a screen-off (rename, animation, delete) then changed an object no longer in
     * the config, so the change was lost; deleting a folder that way removed its cover image
     * and kept the folder. Kept objects stay the ones the open dialogs hold.
     */
    public void reload() {
        java.util.Map<String, ?> now = prefs.getAll();
        if (!now.equals(seen)) {
            config = Config.read(prefs);
            seen = now;
        }
    }

    public void save(Context context) {
        SharedPreferences.Editor editor = prefs.edit();
        config.write(editor);
        editor.commit();
        seen = prefs.getAll();
        context.sendBroadcast(new Intent(Config.ACTION_RELOAD).setPackage(Config.LAUNCHER_PKG));
        context.sendBroadcast(new Intent(Config.ACTION_RELOAD).setPackage("android"));
    }
}
