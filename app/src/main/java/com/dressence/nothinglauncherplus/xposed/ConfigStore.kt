package com.dressence.nothinglauncherplus.xposed

import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
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

    init {
        source.onChange = ::onSourceChanged
    }

    fun get(): Config = config

    /** Reads the settings again if they changed (a file stat, or a flag): not for every frame. */
    fun reloadIfChanged() {
        if (source.takeChange()) {
            config = Config.read(source.prefs)
        }
    }

    /**
     * Runs [action] once the save that sent [intent] (the settings app's ACTION_RELOAD or
     * ACTION_FONT, carrying the save's time in [Config.EXTRA_SAVED]) has reached this process,
     * with the settings read again. Call it from a broadcast receiver: [action] runs on the
     * receiver's thread.
     *
     * Why it waits: the broadcast and the settings travel separately on the modern API (the
     * broadcast through the system, the settings from the framework), and nothing orders the
     * two. Acting on the broadcast alone could read the settings from before the save. If the
     * save never arrives (a framework that dropped it), [action] runs after [SAVE_WAIT_MS]
     * with what there is.
     */
    fun afterSave(intent: Intent, action: Runnable) {
        val wanted = intent.getLongExtra(Config.EXTRA_SAVED, 0L)
        reloadIfChanged()
        if (stamp() >= wanted) {
            action.run()
            return
        }
        val handler = Handler(Looper.myLooper() ?: Looper.getMainLooper())
        val waiting = Waiting(wanted, handler, action)
        pending.add(waiting)
        release() // In case the save arrived between the check above and now.
        handler.postDelayed({
            if (pending.remove(waiting)) {
                Hooks.log("settings: the save did not arrive within ${SAVE_WAIT_MS} ms")
                reloadIfChanged()
                action.run()
            }
        }, SAVE_WAIT_MS)
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

    private class Waiting(val wanted: Long, val handler: Handler, val action: Runnable)

    private val pending = CopyOnWriteArrayList<Waiting>()

    /** The source has new settings (modern API, on a binder thread): release waiting saves. */
    private fun onSourceChanged() {
        if (pending.isNotEmpty()) {
            Hooks.guard("settings arrived", ::release).run()
        }
    }

    /** Runs, on their own threads, the waiting saves the settings now include. */
    private fun release() {
        reloadIfChanged()
        for (waiting in pending) {
            if (stamp() >= waiting.wanted && pending.remove(waiting)) {
                waiting.handler.post(waiting.action)
            }
        }
    }

    /** Where the settings are read from: the shared file, or the framework's copy. */
    private interface Source {
        val prefs: SharedPreferences

        /** True once after the settings changed; the next [prefs] read gives the new ones. */
        fun takeChange(): Boolean

        /** Called when the framework sends a change (modern API only). */
        var onChange: () -> Unit
    }

    /** Old API: the settings app's file, read again when its time changes. */
    private class FileSource : Source {
        private val file = XSharedPreferences(Config.MODULE_PKG, Config.PREFS_NAME)
        override val prefs: SharedPreferences get() = file
        override var onChange: () -> Unit = {}

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
        override var onChange: () -> Unit = {}

        override val prefs: SharedPreferences
            get() = if (useRemote) remote else oldFile ?: remote

        // Held here: a framework may keep its listeners weakly, as the service library does.
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            useRemote = true
            changed = true
            onChange()
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
        /** How long [afterSave] waits for a save to arrive before acting anyway. */
        private const val SAVE_WAIT_MS = 2_000L

        /** The store for the old API's entry: the shared file. */
        @JvmStatic
        fun file(): ConfigStore = ConfigStore(FileSource())

        /** The store for the modern API's entry: the framework's copy of the settings. */
        @JvmStatic
        fun remote(prefs: SharedPreferences): ConfigStore = ConfigStore(RemoteSource(prefs))
    }
}
