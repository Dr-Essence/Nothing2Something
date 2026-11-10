package com.dressence.nothinglauncherplus.search

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.dressence.nothinglauncherplus.ui.SettingsActivity

/**
 * Android's search-indexables provider, the one Settings Intelligence (Nothing's Settings
 * search) reads every app's searchable settings from. Found in Nothing's Settings Intelligence
 * (`search/indexing/PreIndexDataCollector`): it asks each provider answering
 * `android.content.action.SEARCH_INDEXABLES_PROVIDER` that is protected by
 * `READ_SEARCH_INDEXABLES` for `settings/indexables_xml_res`, `settings/indexables_raw`,
 * `settings/non_indexables_key` and `settings/site_map_pairs`, and reads the raw rows by
 * column position (title 1, summary 2-3, keywords 5, screen title 6, class 7, the intent's
 * action, package and class 9-11, key 12). It only asks system apps; `settings/SearchHook`
 * lets it ask this one.
 *
 * Every row is one setting from [SearchIndex]; a tap starts [SettingsActivity] with
 * [SettingsActivity.ACTION_SEARCH_RESULT] and the row's key, which opens its page at that row.
 */
class SearchIndexProvider : ContentProvider() {

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val context = context!!
        val columns = projection ?: arrayOf()
        return when (uri.lastPathSegment) {
            "indexables_raw" -> MatrixCursor(columns).apply {
                for (entry in SearchIndex.load(context)) {
                    addRow(rawRow(columns.size, entry, context.packageName))
                }
            }
            STAMP -> MatrixCursor(arrayOf("stamp")).apply {
                addRow(arrayOf<Any?>(SearchIndex.stamp(context)))
            }
            else -> MatrixCursor(columns) // xml resources, non-indexable keys, site map: none
        }
    }

    /** One raw row, by the positions Settings Intelligence reads (see the class comment). */
    private fun rawRow(width: Int, e: SearchIndex.Entry, pkg: String): Array<Any?> {
        val row = arrayOfNulls<Any?>(maxOf(width, 14))
        row[0] = 0                                        // rank
        row[1] = e.title
        row[2] = e.summary                                // summary on
        row[3] = e.summary                                // summary off
        row[5] = listOfNotNull("Nothing2Something", e.pageTitle, e.section)
            .joinToString(", ")                           // keywords
        row[6] = "Nothing2Something › " + e.pageTitle     // screen title, shown under the result
        row[7] = SettingsActivity::class.java.name            // class name
        row[8] = 0                                        // icon: the app's own
        row[9] = SettingsActivity.ACTION_SEARCH_RESULT
        row[10] = pkg
        row[11] = SettingsActivity::class.java.name
        row[12] = e.key
        row[13] = 0                                       // user id
        return if (width >= row.size) row else row.copyOf(width)
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
            selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "com.dressence.nothinglauncherplus.search"
        /** Not part of Android's contract: the list's stamp, read by `settings/SearchHook`. */
        const val STAMP = "index_stamp"
    }
}
