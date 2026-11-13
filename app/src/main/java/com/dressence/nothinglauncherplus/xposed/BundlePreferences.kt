package com.dressence.nothinglauncherplus.xposed

import android.content.SharedPreferences
import android.os.Bundle

/**
 * Saved settings carried in a broadcast (Config.EXTRA_SETTINGS), read as SharedPreferences so
 * Config.read takes them like any other settings file. Read-only.
 */
class BundlePreferences(private val values: Bundle) : SharedPreferences {

    @Suppress("DEPRECATION") // Bundle.get: the value's type is whatever was saved.
    private fun value(key: String?): Any? = if (key == null) null else values.get(key)

    override fun getAll(): Map<String, *> = values.keySet().associateWith { value(it) }
    override fun getString(key: String?, defValue: String?) = value(key) as? String ?: defValue
    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? =
        (value(key) as? ArrayList<*>)?.filterIsInstance<String>()?.toSet() ?: defValues
    override fun getInt(key: String?, defValue: Int) = value(key) as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = value(key) as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = value(key) as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = value(key) as? Boolean ?: defValue
    override fun contains(key: String?) = key != null && values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    companion object {
        /** [settings] (a settings file's values) as a Bundle to carry in a broadcast. */
        @JvmStatic
        fun bundle(settings: Map<String, *>): Bundle = Bundle().apply {
            for ((key, value) in settings) {
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> putStringArrayList(key, ArrayList(value.filterIsInstance<String>()))
                }
            }
        }
    }
}
