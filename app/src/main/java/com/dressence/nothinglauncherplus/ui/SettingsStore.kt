package com.dressence.nothinglauncherplus.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.dressence.nothinglauncherplus.Broadcasts
import com.dressence.nothinglauncherplus.Config
import com.dressence.nothinglauncherplus.xposed.BundlePreferences

/**
 * Loads and saves the module's settings in its own app, and passes each save on to the hooked
 * apps, the way the framework that loaded the module wants it (0.70.35):
 *
 * - **Old Xposed API** ([ModuleService.isLegacyActive]): the settings file is opened
 *   world-readable. LSPosed's "xposedsharedprefs" puts it in a folder every app may read, where
 *   the hooks read it (xposed/ConfigStore.file).
 * - **Modern API**, or no framework yet: the file is the app's own, private, and every save is
 *   copied into the framework's own store ([ModuleService.publish]), from which the framework
 *   sends it to the hooked apps. No world-readable file is made, which LSPosed 2.2 flags as "a
 *   deprecated feature that will be removed soon".
 *
 * Then [Config.ACTION_RELOAD], carrying the saved settings, tells the launcher and the system
 * to apply them (xposed/ConfigStore.afterSave).
 */
class SettingsStore(context: Context) {

    /** The settings file in this app. */
    @JvmField
    val prefs: SharedPreferences = open(context)

    @JvmField
    var config: Config = Config.read(prefs)

    /** The settings as last read or written here, to tell whether someone else changed them. */
    private var seen: Map<String, *> = prefs.all

    init {
        ModuleService.start(context)
    }

    /**
     * True when a save here reaches the hooked apps: a framework loaded the module, with either
     * API. False until the modern API's service has connected, a moment after the app starts.
     */
    fun reachesHooks(): Boolean = ModuleService.isLegacyActive() || ModuleService.connected()

    /**
     * Reads the settings again, only if they changed since this store last read or wrote them
     * (another screen, Gaming Space, the overlay, an import, the settings moved at an update).
     *
     * Mistake recorded (found in the 0.70.14 audit): every screen reloaded on each resume,
     * which replaced every Config.Folder object even when nothing had changed. A dialog left
     * open over a screen-off (rename, animation, delete) then changed an object no longer in
     * the config, so the change was lost; deleting a folder that way removed its cover image
     * and kept the folder. Kept objects stay the ones the open dialogs hold.
     */
    fun reload() {
        val now = prefs.all
        if (now != seen) {
            config = Config.read(prefs)
            seen = now
        }
    }

    fun save(context: Context) {
        // Never lower than the last save's: the hooked apps keep only settings at least as new
        // as theirs (ConfigStore), so a save made after the clock went back (a date change, a
        // network time correction) was dropped everywhere until a restart (2026-11 audit).
        val savedAt = maxOf(System.currentTimeMillis(), prefs.getLong(Config.KEY_SAVED, 0) + 1)
        val editor = prefs.edit()
        config.write(editor)
        editor.putLong(Config.KEY_SAVED, savedAt)
        editor.commit()
        config.savedAt = savedAt
        seen = prefs.all
        if (!ModuleService.isLegacyActive()) {
            ModuleService.publish(prefs)
        }
        for (target in arrayOf(Config.LAUNCHER_PKG, "android")) {
            // With the sender's identity: the receivers take the settings only from this app.
            Broadcasts.send(context, Intent(Config.ACTION_RELOAD).setPackage(target)
                .putExtra(Config.EXTRA_SETTINGS, carried()))
        }
    }

    /** The saved settings, to carry in a broadcast (Config.EXTRA_SETTINGS). */
    fun carried(): android.os.Bundle = BundlePreferences.bundle(prefs.all)

    private companion object {
        @Suppress("DEPRECATION") // MODE_WORLD_READABLE: the old API's only way to share them.
        fun open(context: Context): SharedPreferences =
            if (ModuleService.isLegacyActive()) {
                context.getSharedPreferences(Config.PREFS_NAME, Context.MODE_WORLD_READABLE)
            } else {
                context.getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
            }
    }
}
