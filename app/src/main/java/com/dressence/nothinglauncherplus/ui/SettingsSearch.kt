package com.dressence.nothinglauncherplus.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import com.dressence.nothinglauncherplus.search.SearchIndex

/**
 * Search inside the module's own settings: a field at the top of the main menu that lists every
 * setting matching what is typed, from every page.
 *
 * Nothing new is indexed for it: it reads the same list Nothing's Settings search gets
 * ([SearchIndex], every row of every page as the app lays them out, saved each time the app
 * opens), and a result opens the same way a Settings search result does (an
 * [SettingsActivity.ACTION_SEARCH_RESULT] intent with the row's key: the page, scrolled to the row,
 * which flashes). So the three ways in (this field, Settings search, the launcher's search bar,
 * which asks Settings Intelligence for its top three settings) always agree.
 */
internal object SettingsSearch {

    /** At most this many results are listed; typing more narrows them. */
    private const val MAX_RESULTS = 40

    /** Adds the field and its result list to the page [ui] lays out. */
    @JvmStatic
    fun addTo(activity: SettingsActivity, ui: Ui) {
        val field = EditText(activity)
        field.hint = "Search settings"
        field.setHintTextColor(Ui.TEXT_SECONDARY)
        field.setTextColor(Ui.TEXT)
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        field.isSingleLine = true
        field.inputType = InputType.TYPE_CLASS_TEXT
        field.imeOptions = EditorInfo.IME_ACTION_SEARCH
        field.setPadding(ui.dp(20), ui.dp(14), ui.dp(20), ui.dp(14))
        field.background = GradientDrawable().apply {
            setColor(Ui.SURFACE)
            cornerRadius = ui.dp(28).toFloat()
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(ui.dp(16), 0, ui.dp(16), ui.dp(8))
        field.layoutParams = params
        ui.add(field)

        val results = LinearLayout(activity)
        results.orientation = LinearLayout.VERTICAL
        ui.add(results)
        val list = ui.into(results)

        // Read once, on the first letter typed: the list is rebuilt when the app opens.
        var entries: List<SearchIndex.Entry>? = null
        field.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(text: Editable?) {
                results.removeAllViews()
                val words = text.toString().trim().lowercase().split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }
                if (words.isEmpty()) return
                val all = entries ?: SearchIndex.load(activity).also { entries = it }
                val found = all.filter { entry ->
                    val hay = listOfNotNull(entry.title, entry.summary, entry.pageTitle,
                        entry.section).joinToString(" ").lowercase()
                    words.all { hay.contains(it) }
                }
                // Title matches first: what the user most likely means.
                    .sortedByDescending { entry -> words.all { entry.title.lowercase().contains(it) } }
                if (found.isEmpty()) {
                    list.note("No setting matches.")
                    return
                }
                for (entry in found.take(MAX_RESULTS)) {
                    val where = listOfNotNull(entry.pageTitle, entry.section).joinToString(" › ")
                    list.row(entry.title, where, null, list.chevron()) {
                        activity.startActivity(Intent(activity, SettingsActivity::class.java)
                            .setAction(SettingsActivity.ACTION_SEARCH_RESULT)
                            .putExtra(":settings:fragment_args_key", entry.key))
                    }
                }
                if (found.size > MAX_RESULTS) {
                    list.note("${found.size - MAX_RESULTS} more: type more to narrow them.")
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
}
