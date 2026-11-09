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
 * Since 2025 Steam no longer lists trailers as plain video files but as streams. Read from the
 * store's live answer (Portal 2, Nov 2026): each movie has `hls_h264` (an HLS master playlist,
 * `.m3u8`), `dash_h264` and `dash_av1`, and no mp4. The HLS playlists are version 7 with
 * fragmented MP4 segments (`EXT-X-MAP` init segment, then 3-second `.m4s` chunks) and the sound
 * in a separate stream.
 *
 * Rather than stream that (whether Android's own player handles every such HLS is not certain),
 * a short clip is made from it, once: the smallest video variant's init segment and its first
 * few chunks, joined, are a plain fragmented MP4 file (checked: 640 x 360, 15 s, 1.8 MB, a valid
 * H.264 video, frames of the trailer), saved in this app's files and played from there, looping,
 * muted. A movie with an mp4 link instead (older entries, microtrailers) is saved as it is.
 *
 * Each app's clip (or "none") is remembered for a week. Blocking: call off the main thread.
 */
internal class SteamTrailers(context: Context) {

    companion object {
        private const val STORE = "https://store.steampowered.com/api"
        private const val PREFS = "steam_trailers"
        private const val NONE = "none"
        private const val KEEP_MS = 7L * 24 * 60 * 60 * 1000
        private const val TIMEOUT_MS = 8000
        /** Chunks joined into the clip (3 s each). */
        private const val CHUNKS = 5
        /** No clip bigger than this is kept (a few MB of the phone's storage per game). */
        private const val MAX_BYTES = 6 * 1024 * 1024
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dir = java.io.File(context.filesDir, "trailers").also { it.mkdirs() }

    /** The saved clip of an app's game's trailer (a file path), or null if it has none. */
    fun find(pkg: String, label: String): String? {
        val clip = java.io.File(dir, "$pkg.mp4")
        if (clip.exists()) return clip.path
        val saved = prefs.getString(pkg, null)
        val at = prefs.getLong("$pkg@", 0L)
        if (saved == NONE && System.currentTimeMillis() - at < KEEP_MS) return null
        return try {
            val url = search(label)?.let { trailer(it) }
            val made = url?.let { save(it, clip) } ?: false
            prefs.edit().putString(pkg, if (made) clip.path else NONE)
                .putLong("$pkg@", System.currentTimeMillis()).apply()
            if (made) clip.path else null
        } catch (t: Throwable) {
            null // offline or Steam down: tried again next time
        }
    }

    /** Every game Steam's store finds for [name] (app id and name), for choosing by hand. */
    fun games(name: String): List<Pair<Int, String>> {
        val json = JSONObject(get("$STORE/storesearch/?term=" +
            URLEncoder.encode(name.trim(), "UTF-8") + "&l=english&cc=US"))
        val items = json.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { items.getJSONObject(it) }
            .filter { it.optInt("id") > 0 }
            .map { it.getInt("id") to it.optString("name") }
    }

    /** A game's trailers (name and the link a clip is made from), the highlighted first. */
    fun movies(app: Int): List<Pair<String, String>> {
        val json = JSONObject(get("$STORE/appdetails?appids=$app"))
        val movies = json.optJSONObject(app.toString())?.optJSONObject("data")
            ?.optJSONArray("movies") ?: return emptyList()
        return (0 until movies.length()).map { movies.getJSONObject(it) }
            .sortedByDescending { it.optBoolean("highlight") }
            .mapNotNull { movie ->
                val links = ArrayList<String>()
                collect(movie, links)
                val link = links.firstOrNull { it.contains(".m3u8") }
                    ?: links.firstOrNull { it.contains(".mp4") }
                link?.let { movie.optString("name", "Trailer") to it }
            }
    }

    /** Makes the clip of the chosen trailer [link] the app's own. Blocking. */
    fun use(pkg: String, link: String): Boolean {
        val clip = java.io.File(dir, "$pkg.mp4")
        clip.delete()
        val made = save(link, clip)
        prefs.edit().putString(pkg, if (made) clip.path else NONE)
            .putLong("$pkg@", System.currentTimeMillis()).apply()
        return made
    }

    /** Forgets an app's trailer (after a rename), so it is looked up again. */
    fun forget(pkg: String) {
        java.io.File(dir, "$pkg.mp4").delete()
        prefs.edit().remove(pkg).remove("$pkg@").apply()
    }

    /** Saves [url] (an HLS master playlist, or an mp4) as a short clip in [clip]. */
    private fun save(url: String, clip: java.io.File): Boolean {
        val part = java.io.File(clip.path + ".part")
        try {
            part.outputStream().use { out ->
                if (url.contains(".m3u8")) {
                    for (piece in hlsPieces(url)) {
                        out.write(bytes(piece))
                        if (part.length() > MAX_BYTES) break
                    }
                } else {
                    out.write(bytes(url))
                }
            }
            if (part.length() == 0L || part.length() > MAX_BYTES * 2) return false
            return part.renameTo(clip)
        } finally {
            part.delete()
        }
    }

    /**
     * The pieces of a short clip from an HLS master playlist: the smallest video variant's
     * init segment (`EXT-X-MAP`) and its first [CHUNKS] chunks, as full addresses.
     */
    private fun hlsPieces(master: String): List<String> {
        val lines = get(master).lines()
        var best: String? = null
        var bestRate = Long.MAX_VALUE
        lines.forEachIndexed { i, line ->
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val rate = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()
                    ?: Long.MAX_VALUE
                val uri = lines.getOrNull(i + 1)?.trim()
                if (uri != null && !uri.startsWith("#") && rate < bestRate) {
                    best = uri
                    bestRate = rate
                }
            }
        }
        val variant = resolve(master, best ?: return emptyList())
        val playlist = get(variant).lines()
        val init = playlist.firstOrNull { it.startsWith("#EXT-X-MAP") }
            ?.let { Regex("URI=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
        val chunks = playlist.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .take(CHUNKS)
        return listOfNotNull(init).map { resolve(variant, it) } + chunks.map { resolve(variant, it) }
    }

    /** [ref] relative to [base]'s folder (or as it is, if it is a full address). */
    private fun resolve(base: String, ref: String): String =
        if (ref.startsWith("http")) ref
        else base.substringBefore('?').substringBeforeLast('/') + "/" + ref

    private fun bytes(address: String): ByteArray {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Nothing2Something")
            if (connection.responseCode !in 200..299) {
                throw java.io.IOException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
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
