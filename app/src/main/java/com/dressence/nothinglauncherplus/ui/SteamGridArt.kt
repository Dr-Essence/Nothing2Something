package com.dressence.nothinglauncherplus.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Game artwork for Nothing Gaming Space's big dot screen, from SteamGridDB (a community library
 * of game artwork). Off unless turned on in the settings and given the user's own API key, which
 * stays in this app's private storage (never in the shared settings other apps' hooks read,
 * never in the code).
 *
 * The API, as SteamGridDB's own client uses it (github.com/SteamGridDB/node-steamgriddb):
 * `https://www.steamgriddb.com/api/v2`, the key sent as `Authorization: Bearer <key>`,
 * answers `{ "success": true, "data": ... }`.
 * - `GET /search/autocomplete/<name>`: games matching a name (`id`, `name`).
 * - `GET /heroes/game/<id>`: wide banners (best for the wide screen); `GET /grids/game/<id>`
 *   with landscape dimensions as the fallback. Each image has a `url` and a smaller `thumb`;
 *   the thumb is plenty for a screen a few hundred dots wide.
 * Asked with `nsfw=false&humor=false&epilepsy=false`: no adult, joke or flashing artwork.
 *
 * Each app is looked up once: its picture is saved in this app's files, and an app with no
 * match is remembered for a week, so the network is used once per game, not on every visit.
 * Every call here blocks: call it off the main thread.
 */
internal class SteamGridArt(context: Context) {

    companion object {
        private const val BASE = "https://www.steamgriddb.com/api/v2"
        private const val PREFS = "steamgriddb"
        private const val KEY = "api_key"
        private const val NO_MATCH = "none:"
        private const val RETRY_MS = 7L * 24 * 60 * 60 * 1000
        private const val FILTERS = "nsfw=false&humor=false&epilepsy=false"
        private const val TIMEOUT_MS = 8000

        fun key(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

        fun setKey(context: Context, key: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, key.trim()).apply()
        }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "artwork").also { it.mkdirs() }

    /** The app's saved artwork, if it was fetched before (reads a file: off the main thread). */
    fun cached(pkg: String): Bitmap? {
        val file = File(dir, "$pkg.jpg")
        return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    /** The app's artwork: saved, or looked up by its name and saved; null if there is none. */
    fun fetch(pkg: String, label: String): Bitmap? {
        cached(pkg)?.let { return it }
        val key = prefs.getString(KEY, "") ?: ""
        if (key.isEmpty()) return null
        val missed = prefs.getLong(NO_MATCH + pkg, 0L)
        if (missed != 0L && System.currentTimeMillis() - missed < RETRY_MS) return null
        return try {
            val game = search(key, label)
            val url = game?.let { image(key, "heroes/game/$it?$FILTERS") }
                ?: game?.let { image(key, "grids/game/$it?dimensions=920x430,460x215&$FILTERS") }
            if (url == null) {
                prefs.edit().putLong(NO_MATCH + pkg, System.currentTimeMillis()).apply()
                return null
            }
            val bytes = download(url, null)
            File(dir, "$pkg.jpg").writeBytes(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (t: Throwable) {
            null // offline, a bad key, or SteamGridDB down: the icon stays, tried again later
        }
    }

    /**
     * The game's id for an app's name. The name as shown first; then without what Play
     * listings add after a colon or dash ("Game: Subtitle", "Game - Edition") and marks such as
     * the trademark sign.
     */
    private fun search(key: String, label: String): Int? {
        val plain = label.replace(Regex("[™®©]"), "").replace(Regex("\\(.*?\\)"), "").trim()
        val names = linkedSetOf(plain, plain.split(':', '–', '-').first().trim())
        for (name in names) {
            if (name.length < 2) continue
            val json = JSONObject(String(download(
                "$BASE/search/autocomplete/${URLEncoder.encode(name, "UTF-8").replace("+", "%20")}",
                key)))
            val data = json.optJSONArray("data") ?: continue
            if (data.length() > 0) return data.getJSONObject(0).getInt("id")
        }
        return null
    }

    /** The first image's thumbnail (or full) address for [path], or null if it has none. */
    private fun image(key: String, path: String): String? {
        val data = JSONObject(String(download("$BASE/$path", key))).optJSONArray("data")
        if (data == null || data.length() == 0) return null
        val first = data.getJSONObject(0)
        return first.optString("thumb").ifEmpty { first.optString("url") }.ifEmpty { null }
    }

    private fun download(address: String, key: String?): ByteArray {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Nothing2Something")
            if (key != null) connection.setRequestProperty("Authorization", "Bearer $key")
            if (connection.responseCode !in 200..299) {
                throw java.io.IOException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
