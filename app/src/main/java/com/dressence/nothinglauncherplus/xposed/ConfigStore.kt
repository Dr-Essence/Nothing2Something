package com.dressence.nothinglauncherplus.xposed

import android.content.Intent
import android.content.SharedPreferences
import com.dressence.nothinglauncherplus.Config
import de.robv.android.xposed.XSharedPreferences
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The module's settings as a hooked app sees them: one store per process, made by the entry
 * that loaded the module there (LegacyEntry or ModernEntry, through HookLoader), and read by
 * every feature in that process.
 *
 * Where the settings come from depends on the framework (0.70.35):
 * - **Old API** ([file]): the settings app saves them to a file the framework lets every app
 *   read (LSPosed's "xposedsharedprefs"), and this reads that file with XSharedPreferences.
 * - **Modern API** ([remote]): the framework keeps its own copy (remote preferences), which the
 *   settings app writes through the framework's service and the framework sends to each hooked
 *   process, with every later change. No file is shared. LSPosed 2.2 warns that the shared
 *   file "will be removed soon"; this is what replaces it.
 *
 * Either way a feature reads [get], cheaply, and the store is read again with
 * [reloadIfChanged] or, when the settings app announces a save, [afterSave].
 */
class ConfigStore private constructor(private val source: Source) {

    @Volatile
    private var config: Config = Config.read(source.prefs)
    private val changeListeners = CopyOnWriteArrayList<Runnable>()

    fun get(): Config = config

    /**
     * Reads the settings again if they changed (a file stat, or a flag): not for every frame.
     * Never goes back to older settings than it has (a save carried in a broadcast can be newer
     * than what the framework has sent so far: [afterSave]).
     */
    fun reloadIfChanged() {
        if (source.takeChange()) {
            val read = Config.read(source.prefs)
            if (read.savedAt >= config.savedAt) {
                config = read
            }
        }
    }

    /**
     * Applies the save that sent [intent] (the settings app's ACTION_RELOAD or ACTION_FONT),
     * then runs [action] (on the receiver's thread). The broadcast carries the saved settings
     * ([Config.EXTRA_SETTINGS]); they are taken when the broadcast is [trusted] (sent by the
     * module app: Broadcasts.fromModule) and newer than what this process has.
     *
     * Why the settings travel with the broadcast (0.70.43): on the modern API the framework
     * keeps them and sends each change to the hooked processes, but LSPosed 2.2.1 never told
     * system_server (Dr.Essence's log: "the save did not arrive within 2000 ms", 79 times, all
     * in system_server; the launcher got every one). 0.70.35 to 0.70.42 waited 2 s for it and
     * then went on with the old settings, so a change for the system (rotation, the
     * controller's Home, the font, battery saver's refresh rate) waited for a reboot. Carried
     * in the broadcast, a save reaches every receiver at once, whatever the framework does.
     */
    fun afterSave(intent: Intent, trusted: Boolean, action: Runnable) {
        reloadIfChanged()
        val carried = if (trusted) intent.getBundleExtra(Config.EXTRA_SETTINGS) else null
        if (carried != null) {
            val saved = Config.read(BundlePreferences(carried))
            if (saved.savedAt > config.savedAt) {
                config = saved
            }
        }
        action.run()
    }

    /** Lets every feature that listens refresh, after a save ([afterSave]). */
    fun notifyChanged() {
        for (listener in changeListeners) {
            try {
                listener.run()
            } catch (t: Throwable) {
                Hooks.log("refresh failed: $t")
            }
        }
    }

    /** When the settings were last saved (by the settings app): a change of any setting. */
    fun stamp(): Long = config.savedAt

    fun addChangeListener(listener: Runnable) {
        changeListeners.add(listener)
    }

    /** Where the settings are read from: the shared file, or the framework's copy. */
    private interface Source {
        val prefs: SharedPreferences

        /** True once after the settings changed; the next [prefs] read gives the new ones. */
        fun takeChange(): Boolean
    }

    /** Old API: the settings app's file, read again when its time changes. */
    private class FileSource : Source {
        private val file = XSharedPreferences(Config.MODULE_PKG, Config.PREFS_NAME)
        override val prefs: SharedPreferences get() = file

        override fun takeChange(): Boolean {
            if (!file.hasFileChanged()) {
                return false
            }
            file.reload()
            return true
        }
    }

    /**
     * Modern API: the framework's copy, kept current by the framework itself. Until the settings
     * app has written it once (the first start after updating from a version that used the
     * file, ui/ModuleService moves them), it is empty, and the old file is read instead, so an
     * update never starts the module with its settings gone.
     */
    private class RemoteSource(private val remote: SharedPreferences) : Source {
        @Volatile
        private var useRemote = remote.all.isNotEmpty()
        private val oldFile: SharedPreferences? = if (useRemote) null else oldFile()
        @Volatile
        private var changed = false

        override val prefs: SharedPreferences
            get() = if (useRemote) remote else oldFile ?: remote

        // Held here: a framework may keep its listeners weakly, as the service library does.
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            useRemote = true
            changed = true
        }

        init {
            remote.registerOnSharedPreferenceChangeListener(listener)
        }

        override fun takeChange(): Boolean {
            if (!changed) {
                return false
            }
            changed = false
            return true
        }

        /** The file the old API used, if this framework still has it; null if not. */
        private fun oldFile(): SharedPreferences? = try {
            XSharedPreferences(Config.MODULE_PKG, Config.PREFS_NAME)
                .takeIf { it.file.canRead() }
                ?.also { Hooks.log("settings: read from the old file until the module app moves them") }
        } catch (t: Throwable) {
            Hooks.log("settings: the old file cannot be read: $t")
            null
        }
    }

    companion object {
        /** The store for the old API's entry: the shared file. */
        @JvmStatic
        fun file(): ConfigStore = ConfigStore(FileSource())

        /** The store for the modern API's entry: the framework's copy of the settings. */
        @JvmStatic
        fun remote(prefs: SharedPreferences): ConfigStore = ConfigStore(RemoteSource(prefs))
    }
}
