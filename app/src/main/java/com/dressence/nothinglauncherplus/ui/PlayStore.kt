package com.dressence.nothinglauncherplus.ui

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A game's banner from its Google Play page, for Nothing Gaming Space: the "feature graphic"
 * (1024 x 500) a developer uploads for the top of the store page. Found by the app's package
 * name, so it is always the right game (SteamGridDB is searched by name, which phone games
 * often do not match), and needs no key.
 *
 * How it was found, in the real pages of 8 Ball Pool, GTA San Andreas and Among Us (Nov 2026):
 * - `play.google.com/store/apps/details?id=<package>&hl=en&gl=US` is one HTML page with the
 *   store's data in script blocks, `AF_initDataCallback({key: 'ds:N', ...})`.
 * - Images in that data are written `[null,2,[500,1024],[null,null,"<address>"]...]`, with
 *   their size. Block `ds:5` is the app's own details: in each of the three pages it held
 *   exactly one image of size 500/1024, the game's feature graphic (looked at: 8 Ball Pool's
 *   own banner). The 1024 x 500 images of other blocks belong to the "similar games" listed
 *   on the page, so only `ds:5` is read.
 * - Not the page's `<img>` tags: the one wide image there was a Play event card ("Update
 *   available"), not the banner.
 * - Google's image server gives any size by the address' ending: `=w1024` is full width.
 * The store page's video is a YouTube video ("yt:movie:..."), not a file: not used.
 *
 * Blocking: call off the main thread. An app not on Google Play (404) or a page without the
 * block gives null.
 */
internal object PlayStore {

    private const val PAGE = "https://play.google.com/store/apps/details?hl=en&gl=US&id="
    private const val TIMEOUT_MS = 10000
    /** A phone's browser: the store answers it with the full page. */
    private const val AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
    private val BLOCK = Regex("AF_initDataCallback\\(\\{key: 'ds:(\\d+)'")
    private val BANNER = Regex("\\[\\s*null\\s*,\\s*2\\s*,\\s*\\[\\s*500\\s*,\\s*1024\\s*\\]\\s*," +
        "\\s*\\[\\s*null\\s*,\\s*null\\s*,\\s*\"(https://play-lh\\.googleusercontent\\.com/[^\"]+)\"")

    /** The app's feature graphic at full width, or null if it has none on Google Play. */
    fun banner(pkg: String): String? {
        val page = get(PAGE + URLEncoder.encode(pkg, "UTF-8")) ?: return null
        val blocks = BLOCK.findAll(page).toList()
        val own = blocks.firstOrNull { it.groupValues[1] == "5" } ?: return null
        val end = blocks.firstOrNull { it.range.first > own.range.first }?.range?.first ?: page.length
        val found = BANNER.find(page.substring(own.range.first, end)) ?: return null
        return found.groupValues[1].substringBefore('=') + "=w1024"
    }

    private fun get(address: String): String? {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", AGENT)
            if (connection.responseCode == 404) return null // not on Google Play
            if (connection.responseCode !in 200..299) {
                throw java.io.IOException("Google Play answered HTTP ${connection.responseCode}")
            }
            return connection.inputStream.use { String(it.readBytes()) }
        } finally {
            connection.disconnect()
        }
    }
}
