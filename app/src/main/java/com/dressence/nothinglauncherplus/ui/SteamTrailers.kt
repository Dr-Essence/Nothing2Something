package com.dressence.nothinglauncherplus.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A game's trailer from Steam's store, to play (muted, looping) on Nothing Gaming Space's big
 * dot screen. Steam's store data needs no key.
 *
 * - `store.steampowered.com/api/storesearch/?term=<name>` finds the game (`items[].id`).
 * - `store.steampowered.com/api/appdetails?appids=<id>` gives its `movies`.
 *
 * Since 2025 Steam no longer lists trailers as plain video files but as streams (HLS
 * `.m3u8`, DASH `.mpd`) and short `microtrailer.mp4` clips (reported by LaunchBox, seen in
 * SteamDB's records; the store's live answer could not be read from where this was written).
 * So the trailer's entry is searched for links rather than read by field name: an HLS stream
 * first (Android's own player plays HLS), then any mp4. DASH is skipped (not played without a
 * separate player).
 *
 * Each app's link (or "none") is remembered for a week. Blocking: call off the main thread.
 */
internal class SteamTrailers(context: Context) {

    companion object {
        private const val STORE = "https://store.steampowered.com/api"
        private const val PREFS = "steam_trailers"
        private const val NONE = "none"
        private const val KEEP_MS = 7L * 24 * 60 * 60 * 1000
        private const val TIMEOUT_MS = 8000
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The trailer's address for an app's game, or null (none, or Steam not reachable). */
    fun find(pkg: String, label: String): String? {
        val saved = prefs.getString(pkg, null)
        val at = prefs.getLong("$pkg@", 0L)
        if (saved != null && System.currentTimeMillis() - at < KEEP_MS) {
            return saved.takeIf { it != NONE }
        }
        return try {
            val url = search(label)?.let { trailer(it) }
            prefs.edit().putString(pkg, url ?: NONE).putLong("$pkg@", System.currentTimeMillis())
                .apply()
            url
        } catch (t: Throwable) {
            null // offline or Steam down: tried again next time
        }
    }

    /** Forgets an app's trailer (after a rename), so it is looked up again. */
    fun forget(pkg: String) {
        prefs.edit().remove(pkg).remove("$pkg@").apply()
    }

    private fun search(label: String): Int? {
        val name = label.replace(Regex("[™®©]"), "").trim()
        val json = JSONObject(get("$STORE/storesearch/?term=" +
            URLEncoder.encode(name, "UTF-8") + "&l=english&cc=US"))
        val items = json.optJSONArray("items") ?: return null
        return if (items.length() > 0) items.getJSONObject(0).optInt("id").takeIf { it > 0 }
        else null
    }

    /** The first trailer's stream (HLS) or mp4 link. */
    private fun trailer(app: Int): String? {
        val json = JSONObject(get("$STORE/appdetails?appids=$app"))
        val movies = json.optJSONObject(app.toString())?.optJSONObject("data")
            ?.optJSONArray("movies") ?: return null
        // The highlighted trailer first, as on the store page.
        val ordered = (0 until movies.length()).map { movies.getJSONObject(it) }
            .sortedByDescending { it.optBoolean("highlight") }
        for (movie in ordered) {
            val links = ArrayList<String>()
            collect(movie, links)
            links.firstOrNull { it.contains(".m3u8") }?.let { return it }
            links.firstOrNull { it.contains(".mp4") }?.let { return it }
        }
        return null
    }

    /** Every web link in a JSON value, at any depth. */
    private fun collect(value: Any?, out: MutableList<String>) {
        when (value) {
            is JSONObject -> value.keys().forEach { collect(value.opt(it), out) }
            is JSONArray -> for (i in 0 until value.length()) collect(value.opt(i), out)
            is String -> if (value.startsWith("http")) out += value
        }
    }

    private fun get(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Nothing2Something")
            if (connection.responseCode !in 200..299) {
                throw java.io.IOException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.use { String(it.readBytes()) }
        } finally {
            connection.disconnect()
        }
    }
}
