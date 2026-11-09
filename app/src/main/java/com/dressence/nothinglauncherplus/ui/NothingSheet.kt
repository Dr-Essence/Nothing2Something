package com.dressence.nothinglauncherplus.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Nothing Gaming Space's menus, in its own look instead of Android's dialogs: a black card
 * with a thin grey rim over the dimmed screen, the title glowing green in NDot, the choices in
 * NDot with the selected one green and marked with a dot. Three kinds: a list of choices, a row
 * of pictures (artwork to choose from), and a name to type.
 *
 * The controller works it like the rest of the launcher (the activity hands it the keys while
 * it is open): up and down (left and right for pictures) move, A picks, B closes. Touch: a tap
 * picks, a tap outside the card closes.
 */
internal class NothingSheet(private val context: Context, private val host: FrameLayout,
        private val font: () -> Typeface?) {

    companion object {
        private const val GREEN = 0xFF4CFF7A.toInt()
        private const val GLOW = 0xFF2BE86A.toInt()
        private const val CARD = 0xFF0B0C0B.toInt()
        private const val RIM = 0xFF2A2B2A.toInt()
        private const val DIM = 0xCC000000.toInt()
    }

    private var layer: FrameLayout? = null
    private var choices: List<View> = emptyList()
    private var focus = 0
    private var horizontal = false
    private var onPick: ((Int) -> Unit)? = null
    private var field: EditText? = null
    private var onText: ((String) -> Unit)? = null

    val isOpen: Boolean get() = layer != null

    /** A list of choices; [selected] starts highlighted. */
    fun list(title: String, options: List<String>, selected: Int, pick: (Int) -> Unit) {
        val card = card(title)
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        val rows = options.mapIndexed { i, option ->
            val row = text(option.uppercase(), 17f, Color.WHITE)
            row.setPadding(dp(12), dp(10), dp(24), dp(10))
            row.setOnClickListener { focus = i; choose() }
            column.addView(row)
            row
        }
        val scroll = ScrollView(context)
        scroll.addView(column)
        card.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        open(card, rows, selected.coerceIn(0, maxOf(0, rows.size - 1)), false, pick)
    }

    /** A row of pictures to choose from, each with a caption (artwork). */
    fun pictures(title: String, items: List<Pair<String, Bitmap?>>, pick: (Int) -> Unit) {
        val card = card(title)
        val row = LinearLayout(context)
        val tiles = items.mapIndexed { i, (caption, picture) ->
            val tile = LinearLayout(context)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER_HORIZONTAL
            tile.setPadding(dp(8), dp(8), dp(8), dp(8))
            val image = ImageView(context)
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.setImageBitmap(picture)
            image.background = rounded(0xFF151615.toInt(), dp(10).toFloat(), 0)
            image.clipToOutline = true
            tile.addView(image, LinearLayout.LayoutParams(dp(240), dp(78)))
            val label = text(caption.uppercase(), 12f, Color.WHITE)
            label.setPadding(0, dp(6), 0, 0)
            tile.addView(label)
            tile.setOnClickListener { focus = i; choose() }
            row.addView(tile)
            tile
        }
        val scroll = HorizontalScrollView(context)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.addView(row)
        card.addView(scroll, LinearLayout.LayoutParams(dp(820),
            ViewGroup.LayoutParams.WRAP_CONTENT))
        open(card, tiles, 0, true, pick)
    }

    /** A name to type; [done] gets it when saved (with A, Enter or the keyboard's Done). */
    fun text(title: String, initial: String, done: (String) -> Unit) {
        val card = card(title)
        val edit = EditText(context)
        edit.setText(initial)
        edit.setSelection(initial.length)
        edit.setSingleLine(true)
        edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        edit.imeOptions = EditorInfo.IME_ACTION_DONE
        edit.setTextColor(Color.WHITE)
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        font()?.let { edit.typeface = it }
        edit.background = rounded(0xFF151615.toInt(), dp(12).toFloat(), GREEN)
        edit.setPadding(dp(16), dp(12), dp(16), dp(12))
        edit.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { finishText(); true } else false
        }
        card.addView(edit, LinearLayout.LayoutParams(dp(560), ViewGroup.LayoutParams.WRAP_CONTENT))
        val hint = text("A OR DONE SAVES   ·   B CLOSES   ·   EMPTY: THE APP'S OWN NAME", 11f,
            0xFF8A8A8A.toInt())
        hint.setPadding(0, dp(10), 0, 0)
        card.addView(hint)
        field = edit
        onText = done
        open(card, emptyList(), 0, false, null)
        edit.requestFocus()
        edit.post {
            context.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** A message only (a wait, or a result); B or a tap closes it. */
    fun message(title: String, body: String) {
        val card = card(title)
        val line = text(body.uppercase(), 14f, Color.WHITE)
        line.maxWidth = dp(620)
        card.addView(line)
        open(card, emptyList(), 0, false, null)
    }

    fun close() {
        layer?.let { host.removeView(it) }
        field?.let {
            context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(it.windowToken, 0)
        }
        layer = null
        choices = emptyList()
        onPick = null
        field = null
        onText = null
    }

    /** The controller while open; true when the key was used here. */
    fun onKey(keyCode: Int): Boolean {
        if (!isOpen) return false
        val back = if (horizontal) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_UP
        val next = if (horizontal) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_DOWN
        when (keyCode) {
            back -> move(-1)
            next -> move(1)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> if (field != null) finishText() else choose()
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> close()
            else -> return field != null // typing goes to the field; other keys stay here
        }
        return true
    }

    private fun finishText() {
        val done = onText ?: return
        val value = field?.text?.toString()?.trim() ?: ""
        close()
        done(value)
    }

    private fun open(card: LinearLayout, items: List<View>, start: Int, across: Boolean,
            pick: ((Int) -> Unit)?) {
        close()
        val dim = FrameLayout(context)
        dim.setBackgroundColor(DIM)
        dim.isClickable = true
        dim.setOnClickListener { close() } // a tap outside the card
        card.isClickable = true // taps on the card stay on it
        dim.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        host.addView(dim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        layer = dim
        choices = items
        horizontal = across
        onPick = pick
        focus = start
        refresh()
    }

    private fun move(d: Int) {
        if (choices.isEmpty()) return
        focus = (focus + d).coerceIn(0, choices.size - 1)
        refresh()
        choices[focus].let { it.parent?.requestChildFocus(it, it) } // scrolls it into view
    }

    private fun choose() {
        val pick = onPick ?: return
        val index = focus
        close()
        pick(index)
    }

    private fun refresh() {
        choices.forEachIndexed { i, view ->
            val on = i == focus
            if (view is TextView) {
                view.setTextColor(if (on) GREEN else Color.WHITE)
                view.text = (if (on) "●  " else "    ") + view.text.toString().trimStart('●', ' ')
                if (on) view.setShadowLayer(dp(8).toFloat(), 0f, 0f, GLOW)
                else view.setShadowLayer(0f, 0f, 0f, 0)
            } else {
                view.background = if (on) rounded(0, dp(14).toFloat(), GREEN) else null
                view.alpha = if (on) 1f else 0.6f
            }
        }
    }

    private fun card(title: String): LinearLayout {
        val card = LinearLayout(context)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(28), dp(24), dp(28), dp(24))
        card.background = rounded(CARD, dp(28).toFloat(), RIM)
        val head = text(title.uppercase(), 22f, GREEN)
        head.setShadowLayer(dp(10).toFloat(), 0f, 0f, GLOW)
        head.setPadding(0, 0, 0, dp(14))
        card.addView(head)
        return card
    }

    private fun text(value: String, sp: Float, color: Int): TextView {
        val view = TextView(context)
        view.text = value
        view.setTextColor(color)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        view.typeface = font() ?: Typeface.MONOSPACE
        return view
    }

    private fun rounded(fill: Int, radius: Float, stroke: Int) = GradientDrawable().also {
        it.setColor(fill)
        it.cornerRadius = radius
        if (stroke != 0) it.setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
