package com.dressence.nothinglauncherplus.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.dressence.nothinglauncherplus.Config
import com.dressence.nothinglauncherplus.xposed.Hooks
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * The settings app's link to the Xposed framework that loaded the module (0.70.35).
 *
 * - **Modern API** (LSPosed 2, Vector): the framework hands this app its service through the
 *   service library's provider (io.github.libxposed.service.XposedProvider, merged into the
 *   manifest), a moment after the app starts. Through it the settings are copied into the
 *   framework's own store ([publish]), from which the hooked apps get them.
 * - **Old API**: there is no service; the framework hooks [isLegacyActive] instead
 *   (xposed/LegacyEntry), and the settings are shared through a file (SettingsStore).
 *
 * When the service first connects after an update from a version that used the file, the
 * settings in that file are moved over once ([OldSettings]), so nothing set before is lost.
 */
object ModuleService {

    /** True when the old Xposed API loaded the module: xposed/LegacyEntry makes it return true. */
    @JvmStatic
    fun isLegacyActive(): Boolean = false

    @Volatile
    private var service: XposedService? = null
    private var appContext: Context? = null
    /** Why the settings from before the update could not be moved; null when nothing is wrong. */
    @Volatile
    @JvmStatic
    var moveProblem: String? = null
        private set

    /** One thread for everything that talks to the framework, so the saves keep their order. */
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "nlplus-module-service") }
    private val main = Handler(Looper.getMainLooper())
    private val changeListeners = CopyOnWriteArrayList<Runnable>()

    /** True once the modern API's service is connected. */
    @JvmStatic
    fun connected(): Boolean = service != null

    /** Starts listening for the framework's service; called by every SettingsStore, once is enough. */
    @JvmStatic
    @Synchronized
    fun start(context: Context) {
        if (appContext != null) {
            return
        }
        appContext = context.applicationContext
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(bound: XposedService) {
                service = bound
                worker.execute { onConnected(bound) }
            }

            override fun onServiceDied(dead: XposedService) {
                if (service === dead) {
                    service = null
                }
            }
        })
    }

    /** Runs [listener] on the main thread when the service connects or the settings are moved. */
    @JvmStatic
    fun addChangeListener(listener: Runnable) {
        changeListeners.add(listener)
    }

    @JvmStatic
    fun removeChangeListener(listener: Runnable) {
        changeListeners.remove(listener)
    }

    /** Copies [local] (the app's settings file) into the framework's store, off the main thread. */
    @JvmStatic
    fun publish(local: SharedPreferences) {
        val bound = service ?: return // Sent in full when the service connects (onConnected).
        val snapshot = HashMap(local.all)
        worker.execute { copy(bound, snapshot) }
    }

    private fun onConnected(bound: XposedService) {
        val context = appContext ?: return
        val local = context.getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
        moveProblem = OldSettings.moveInto(context, local)
        if (copy(bound, HashMap(local.all))) {
            OldSettings.removeOldFiles(context)
        }
        main.post { changeListeners.forEach(Runnable::run) }
    }

    /**
     * Makes the framework's copy equal to [wanted]: only the keys that differ are sent, so the
     * hooked apps never see the settings half gone in between. True if it is equal now.
     */
    private fun copy(bound: XposedService, wanted: Map<String, Any?>): Boolean {
        return try {
            val remote = bound.getRemotePreferences(Config.PREFS_NAME)
            val current = remote.all
            val editor = remote.edit()
            val gone = current.keys - wanted.keys
            val differ = wanted.filter { (key, value) -> current[key] != value }
            gone.forEach { editor.remove(it) }
            differ.forEach { (key, value) -> putValue(editor, key, value) }
            gone.isEmpty() && differ.isEmpty() || editor.commit()
        } catch (t: Throwable) {
            Hooks.log("settings: copying to the framework failed: $t")
            false
        }
    }
}

/** Puts [value] under [key] with the editor call for its type (a settings file's types). */
@Suppress("UNCHECKED_CAST")
internal fun putValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
    when (value) {
        is Boolean -> editor.putBoolean(key, value)
        is Int -> editor.putInt(key, value)
        is Long -> editor.putLong(key, value)
        is Float -> editor.putFloat(key, value)
        is String -> editor.putString(key, value)
        is Set<*> -> editor.putStringSet(key, value as Set<String>)
        else -> editor.remove(key)
    }
}
