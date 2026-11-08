package com.dressence.nothinglauncherplus.search

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The module's settings as Nothing's Settings search sees them.
 *
 * The list is not written by hand: each time the settings app is opened, every one of its
 * pages is laid out once more, off screen, in a recording mode of `ui.Ui`, which notes each
 * row's title, summary, section and page (`MainActivity.buildSearchIndex`). So a setting added,
 * renamed or hidden ("Launcher only") is found as the app shows it, with nothing to keep in
 * step. The list is kept in a file; [SearchIndexProvider] hands it to Settings Intelligence,
 * and its [stamp] tells Settings Intelligence when to read it again (`settings/SearchHook`).
 */
object SearchIndex {

    /** One setting: where it is, and what it says. [page] null is the main menu. */
    data class Entry(
        val page: String?,
        val pageTitle: String,
        val section: String?,
        val title: String,
        val summary: String?,
    ) {
        /** What a search result hands back to open this setting: the page and the title. */
        val key: String get() = (page ?: "") + "|" + title
    }

    private const val FILE = "search_index.json"

    /** Saves [entries] if they changed; the stamp changes with them. */
    @JvmStatic
    fun save(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        for (e in entries) {
            array.put(JSONObject().apply {
                put("page", e.page ?: "")
                put("pageTitle", e.pageTitle)
                put("section", e.section ?: "")
                put("title", e.title)
                put("summary", e.summary ?: "")
            })
        }
        val text = array.toString()
        val file = File(context.filesDir, FILE)
        if (file.exists() && file.readText() == text) return
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }

    @JvmStatic
    fun load(context: Context): List<Entry> {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Entry(
                    o.getString("page").ifEmpty { null },
                    o.getString("pageTitle"),
                    o.getString("section").ifEmpty { null },
                    o.getString("title"),
                    o.getString("summary").ifEmpty { null },
                )
            }
        } catch (e: Exception) {
            emptyList() // A broken file is replaced the next time the app opens.
        }
    }

    /** Changes whenever the list does: the file's length and modification time. */
    @JvmStatic
    fun stamp(context: Context): String {
        val file = File(context.filesDir, FILE)
        return if (file.exists()) "${file.length()}-${file.lastModified()}" else "none"
    }

    /** Splits a key from [Entry.key] into its page (null: the menu) and its title. */
    @JvmStatic
    fun parseKey(key: String?): Pair<String?, String>? {
        if (key == null) return null
        val bar = key.indexOf('|')
        if (bar < 0) return null
        return Pair(key.substring(0, bar).ifEmpty { null }, key.substring(bar + 1))
    }
}
