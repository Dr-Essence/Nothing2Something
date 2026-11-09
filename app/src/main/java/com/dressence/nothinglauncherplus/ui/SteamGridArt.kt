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
        private const val LAST = "last_result"
        private const val RETRY_MS = 7L * 24 * 60 * 60 * 1000
        private const val FILTERS = "nsfw=false&humor=false&epilepsy=false"
        private const val TIMEOUT_MS = 8000

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
    private val dir = File(context.filesDir, "artwork").also { it.mkdirs() }

    /** One picture SteamGridDB has for a game: its address, its small preview, and kind. */
    class Choice(val url: String, val thumb: String, val animated: Boolean, val kind: String)

    private fun file(pkg: String) = File(dir, "$pkg.img")

    /** Forgets an app's saved artwork and any "no match", so it is looked up again. */
    fun forget(pkg: String) {
        file(pkg).delete()
        File(dir, "$pkg.jpg").delete() // as 0.61.0 to 0.61.2 saved it
        prefs.edit().remove(NO_MATCH + pkg).apply()
    }

    /**
     * The app's saved artwork as a drawable, if it was fetched or chosen before: an animated
     * one ([android.graphics.drawable.AnimatedImageDrawable]) for an animated picture, else a
     * still one. Reads a file: off the main thread.
     */
    fun cached(pkg: String): Drawable? {
        val saved = file(pkg).takeIf { it.exists() } ?: File(dir, "$pkg.jpg").takeIf { it.exists() }
        ?: return null
        return try {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(saved))
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * The app's artwork: saved, or looked up by its name and saved; null if there is none.
     * With [animated] an animated banner is taken first when the game has one.
     */
    fun fetch(pkg: String, label: String, animated: Boolean): Drawable? {
        cached(pkg)?.let { return it }
        val key = prefs.getString(KEY, "") ?: ""
        if (key.isEmpty()) return null
        val missed = prefs.getLong(NO_MATCH + pkg, 0L)
        if (missed != 0L && System.currentTimeMillis() - missed < RETRY_MS) return null
        return try {
            val game = search(key, label)
            val pick = game?.let { choices(key, it.first, if (animated) 1 else 0).firstOrNull() }
            if (pick == null) {
                prefs.edit().putLong(NO_MATCH + pkg, System.currentTimeMillis())
                    .putString(LAST, if (game == null) "No match for \"$label\""
                        else "\"${game.second}\" has no artwork yet").apply()
                return null
            }
            save(pkg, pick)
            prefs.edit().putString(LAST, "Found \"${game.second}\"" +
                (if (pick.animated) " (animated)" else "") + " for \"$label\"").apply()
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
     * Every picture to choose from for an app's game, animated banners first, then still
     * banners, then wide covers, with the matched game's name. Blocking.
     */
    fun options(label: String): Pair<String, List<Choice>>? {
        val key = prefs.getString(KEY, "") ?: ""
        if (key.isEmpty()) return null
        val game = search(key, label) ?: return null
        return game.second to choices(key, game.first, 4, 8, 6)
    }

    /** Saves [choice] as the app's artwork (the full picture: animated ones move). Blocking. */
    fun save(pkg: String, choice: Choice) {
        val bytes = download(if (choice.animated) choice.url else choice.thumb.ifEmpty { choice.url },
            null)
        file(pkg).writeBytes(bytes)
        File(dir, "$pkg.jpg").delete()
        prefs.edit().remove(NO_MATCH + pkg).apply()
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
