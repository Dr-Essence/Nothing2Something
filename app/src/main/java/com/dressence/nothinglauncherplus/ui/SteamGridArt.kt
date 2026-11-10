package com.dressence.nothinglauncherplus.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
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
 * Each app is looked up once: its picture is saved (in Nothing Gaming Space/SteamGridDB on the
 * phone's storage when allowed, else in this app's files: [GamingStorage]), and an app with no
 * match is remembered for a week, so the network is used once per game, not on every visit.
 * Every call here blocks: call it off the main thread.
 */
internal class SteamGridArt(context: Context) {

    companion object {
        private const val BASE = "https://www.steamgriddb.com/api/v2"
        private const val PREFS = "steamgriddb"
        private const val KEY = "api_key"
        private const val NO_MATCH = "none:"
        private const val LAST = "last_result"
        private const val RETRY_MS = 7L * 24 * 60 * 60 * 1000
        private const val FILTERS = "nsfw=false&humor=false&epilepsy=false"
        private const val TIMEOUT_MS = 8000
        /** The names a saved picture may have, looked for in this order. */
        private val EXTENSIONS = listOf("png", "jpg", "jpeg", "webp", "gif", "img")

        fun key(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

        fun setKey(context: Context, key: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, key.trim()).apply()
        }

        /** What the last lookup did, in words, for the settings ("" before any). */
        fun lastResult(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST, "") ?: ""

        /**
         * Tries the key with one search (blocking: off the main thread) and says what happened,
         * in words: the key works, it was refused, or the network failed and how.
         */
        fun check(context: Context): String {
            val key = key(context)
            if (key.isEmpty()) return "No key set."
            return try {
                val art = SteamGridArt(context)
                val json = JSONObject(String(art.download(
                    "$BASE/search/autocomplete/Portal%202", key)))
                val n = json.optJSONArray("data")?.length() ?: 0
                "The key works: a test search found $n games."
            } catch (e: HttpError) {
                when (e.code) {
                    401, 403 -> "SteamGridDB refused the key (HTTP ${e.code}). Check it was" +
                        " copied whole, from steamgriddb.com, Profile, Preferences, API."
                    else -> "SteamGridDB answered HTTP ${e.code}."
                }
            } catch (t: Throwable) {
                "Could not reach SteamGridDB: ${t.javaClass.simpleName}" +
                    (t.message?.let { ": $it" } ?: "") + ". Check the phone's internet, and" +
                    " that Nothing2Something may use the network (App info, Mobile data & Wi-Fi)."
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val context = context.applicationContext
    /** Looked up on each use: All files access may be granted while Gaming Space is open. */
    private val dir: File get() = GamingStorage.folder(context, GamingStorage.ART, "artwork")

    /** One picture SteamGridDB has for a game: its address, its small preview, and kind. */
    class Choice(val url: String, val thumb: String, val animated: Boolean, val kind: String)

    /**
     * The app's saved picture, if any. Named by package with the picture's own extension, so a
     * file manager can open it and a picture the user drops in is found too; `.img` is how
     * 0.62.0 to 0.64.1 saved it, `.jpg` 0.61.0 to 0.61.2.
     */
    private fun saved(pkg: String): File? {
        val folder = dir
        return EXTENSIONS.map { File(folder, "$pkg.$it") }.firstOrNull { it.exists() }
    }

    /** Forgets an app's saved artwork and any "no match", so it is looked up again. */
    fun forget(pkg: String) {
        val folder = dir
        for (ext in EXTENSIONS) File(folder, "$pkg.$ext").delete()
        prefs.edit().remove(NO_MATCH + pkg).apply()
    }

    /**
     * The app's saved artwork as a drawable, if it was fetched or chosen before: an animated
     * one ([android.graphics.drawable.AnimatedImageDrawable]) for an animated picture, else a
     * still one. Reads a file: off the main thread.
     */
    fun cached(pkg: String): Drawable? {
        val saved = saved(pkg) ?: return null
        return try {
            // In ordinary memory, not on the GPU: a still picture is drawn into the dots'
            // small bitmap, which a hardware bitmap cannot be. (Mistake recorded, 0.62.0 and
            // 0.63.0: decoded with the default, hardware, allocator, Gaming Space crashed as
            // soon as a game had saved artwork: "Software rendering doesn't support hardware
            // bitmaps" in DotAnimationView.fitPicture, seen in Dr.Essence's log.)
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(saved)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * The app's artwork: saved, or looked up and saved; null if there is none. In this order
     * (0.70.5): an animated banner from SteamGridDB when [animated] and a key is set; the
     * game's banner from its Google Play page ([PlayStore], found by package, so never the
     * wrong game, and without a key); then SteamGridDB's still banner or cover.
     */
    fun fetch(pkg: String, label: String, animated: Boolean): Drawable? {
        cached(pkg)?.let { return it }
        val key = prefs.getString(KEY, "") ?: ""
        val missed = prefs.getLong(NO_MATCH + pkg, 0L)
        if (missed != 0L && System.currentTimeMillis() - missed < RETRY_MS) return null
        return try {
            val game = if (key.isEmpty()) null else search(key, label)
            var pick: Choice? = null
            if (game != null && animated) {
                pick = list(key, "heroes/game/${game.first}?types=animated&$FILTERS", true,
                    "Animated banner").firstOrNull()
            }
            if (pick == null) pick = playBanner(pkg)
            if (pick == null && game != null) pick = choices(key, game.first, 0).firstOrNull()
            if (pick == null) {
                prefs.edit().putLong(NO_MATCH + pkg, System.currentTimeMillis())
                    .putString(LAST, "No artwork for \"$label\" (SteamGridDB" +
                        (if (key.isEmpty()) ": no key" else if (game == null) ": no match" else
                            ": none") + "; Google Play: none)").apply()
                return null
            }
            save(pkg, pick)
            prefs.edit().putString(LAST, "Found ${pick.kind.lowercase()}" +
                (game?.let { " of \"${it.second}\"" } ?: "") + " for \"$label\"").apply()
            cached(pkg)
        } catch (e: HttpError) {
            prefs.edit().putString(LAST, "\"$label\": SteamGridDB answered HTTP ${e.code}").apply()
            null
        } catch (t: Throwable) {
            // Offline or SteamGridDB down: the icon stays, and it is tried again next time.
            prefs.edit().putString(LAST, "\"$label\": ${t.javaClass.simpleName}" +
                (t.message?.let { ": $it" } ?: "")).apply()
            null
        }
    }

    /**
     * Every game SteamGridDB finds for [name] (id and name, best match first), for choosing
     * the right one by hand when the automatic match is wrong. Blocking.
     */
    fun games(name: String): List<Pair<Int, String>> {
        val key = prefs.getString(KEY, "") ?: ""
        if (key.isEmpty() || name.isBlank()) return emptyList()
        val json = JSONObject(String(download(
            "$BASE/search/autocomplete/${URLEncoder.encode(name.trim(), "UTF-8").replace("+", "%20")}",
            key)))
        val data = json.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).map {
            val game = data.getJSONObject(it)
            game.getInt("id") to game.optString("name", name)
        }
    }

    /** The pictures of one game (by its SteamGridDB id), animated banners first. Blocking. */
    fun choicesFor(game: Int): List<Choice> {
        val key = prefs.getString(KEY, "") ?: ""
        return if (key.isEmpty()) emptyList() else choices(key, game, 4, 8, 6)
    }

    /** The game's Google Play banner as a choice, or null (see [PlayStore]). Blocking. */
    fun playBanner(pkg: String): Choice? =
        PlayStore.banner(pkg)?.let { Choice(it, it, false, "Play Store banner") }

    /** Saves [choice] as the app's artwork (the full picture: animated ones move). Blocking. */
    fun save(pkg: String, choice: Choice) {
        val bytes = download(if (choice.animated) choice.url else choice.thumb.ifEmpty { choice.url },
            null)
        forget(pkg) // the previous picture, whatever its kind
        File(dir, "$pkg.${extension(bytes)}").writeBytes(bytes)
        prefs.edit().remove(NO_MATCH + pkg).apply()
    }

    /**
     * The picture's kind from its first bytes (its "magic number"), as the formats define them:
     * PNG starts 89 'P' 'N' 'G', JPEG FF D8, GIF "GIF8", WebP "RIFF" then "WEBP" at byte 8.
     */
    private fun extension(b: ByteArray): String = when {
        b.size > 3 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() -> "png"
        b.size > 2 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "jpg"
        b.size > 4 && String(b, 0, 4, Charsets.US_ASCII) == "GIF8" -> "gif"
        b.size > 12 && String(b, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
        else -> "img" // still decoded: ImageDecoder reads the bytes, not the name
    }

    /** A small preview picture for the chooser (the first frame of an animated one). */
    fun preview(choice: Choice): Bitmap? = try {
        val bytes = download(choice.thumb.ifEmpty { choice.url }, null)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (t: Throwable) {
        null
    }

    /**
     * Up to [animated] animated banners, [still] still banners and [covers] wide covers of a
     * game, in that order. SteamGridDB's `types` filter tells animated from still.
     */
    private fun choices(key: String, game: Int, animated: Int, still: Int = 1,
            covers: Int = 1): List<Choice> {
        val out = ArrayList<Choice>()
        if (animated > 0) out += list(key, "heroes/game/$game?types=animated&$FILTERS", true,
            "Animated banner").take(animated)
        out += list(key, "heroes/game/$game?types=static&$FILTERS", false, "Banner").take(still)
        if (out.size < animated + still) {
            out += list(key, "grids/game/$game?dimensions=920x430,460x215&$FILTERS", false,
                "Cover").take(covers)
        }
        return out
    }

    private fun list(key: String, path: String, animated: Boolean, kind: String): List<Choice> {
        val data = try {
            JSONObject(String(download("$BASE/$path", key))).optJSONArray("data")
        } catch (e: HttpError) {
            if (e.code == 404) null else throw e // 404: the game has none of these
        } ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val item = data.getJSONObject(i)
            val url = item.optString("url")
            if (url.isEmpty()) null else Choice(url, item.optString("thumb"), animated, kind)
        }
    }

    /**
     * The game's id for an app's name. The name as shown first; then without what Play
     * listings add after a colon or dash ("Game: Subtitle", "Game - Edition") and marks such as
     * the trademark sign.
     */
    private fun search(key: String, label: String): Pair<Int, String>? {
        val plain = label.replace(Regex("[™®©]"), "").replace(Regex("\\(.*?\\)"), "").trim()
        val names = linkedSetOf(plain, plain.split(':', '–', '-').first().trim())
        for (name in names) {
            if (name.length < 2) continue
            val json = JSONObject(String(download(
                "$BASE/search/autocomplete/${URLEncoder.encode(name, "UTF-8").replace("+", "%20")}",
                key)))
            val data = json.optJSONArray("data") ?: continue
            if (data.length() > 0) {
                val game = data.getJSONObject(0)
                return game.getInt("id") to game.optString("name", name)
            }
        }
        return null
    }

    /** An answer other than 2xx, with its code (401 or 403: the key was refused). */
    class HttpError(val code: Int) : java.io.IOException("HTTP $code")

    fun download(address: String, key: String?): ByteArray {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Nothing2Something")
            if (key != null) connection.setRequestProperty("Authorization", "Bearer $key")
            if (connection.responseCode !in 200..299) throw HttpError(connection.responseCode)
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
