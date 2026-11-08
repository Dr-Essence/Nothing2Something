package com.dressence.nothinglauncherplus.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.dressence.nothinglauncherplus.anim.AnimatedEmojiView
import com.dressence.nothinglauncherplus.anim.DotEffect
import com.dressence.nothinglauncherplus.launcher.IconScreen
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Nothing Gaming Space: a home screen for playing with a controller, in Nothing's dot-matrix
 * style. With "Gaming launcher with a controller" on and this picked as the gaming launcher,
 * Android's home becomes this screen while a controller is connected (see
 * system.ControllerHome), and Nothing Launcher comes back when it disconnects.
 *
 * What it shows:
 * - On start: a black screen, "NOTHING GAMING SPACE" glowing in green NDot, and the first time
 *   only, a photosensitivity warning that goes on by itself (A skips it).
 * - Then, in landscape, two halves: on top a big dot screen showing what is selected (a
 *   category's animated emoji, or an app's icon as a dot screen) with its name; below, a row of
 *   the categories (animated NDot emoji covers, the folder covers' engine), or the apps of the
 *   category opened.
 *
 * Controller: left and right move, A (or down) opens, B (or up) goes back, L1 and R1 change the
 * category, Y or the menu button moves the selected app to another category or hides it. The
 * stick works too: Android turns it into the same left and right presses for an app that does
 * not read the stick itself, with its own repeat. Touch works as well: a tap opens, a long press
 * on an app moves it.
 *
 * Categories: see [GamingLibrary].
 */
class GamingSpaceActivity : Activity() {

    companion object {
        private const val GREEN = 0xFF4CFF7A.toInt()
        private const val GLOW = 0xFF2BE86A.toInt()
        private const val DIM = 0xFF8A8A8A.toInt()
        private const val PANEL = 0xFF0B0C0B.toInt()
        private const val INTRO_IN_MS = 1200L
        private const val INTRO_HOLD_MS = 1400L
        private const val INTRO_OUT_MS = 600L
        /** The warning goes on by itself after this; A or a tap skips it. */
        private const val WARNING_MS = 8000L
        /** After the last controller goes: time for the home app to switch back. */
        private const val LEAVE_MS = 1500L

        fun open(context: Context) {
            context.startActivity(Intent(context, GamingSpaceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private enum class Phase { INTRO, WARNING, MAIN }

    private lateinit var store: SettingsStore
    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val dotIcons = LruCache<String, Bitmap>(64)
    private var ndot: Typeface? = null
    private var phase = Phase.INTRO
    private var skip: (() -> Unit)? = null

    private lateinit var root: FrameLayout
    private lateinit var bigEmoji: AnimatedEmojiView
    private lateinit var bigIcon: ImageView
    private lateinit var bigTitle: TextView
    private lateinit var bigInfo: TextView
    private lateinit var header: TextView
    private lateinit var scroller: HorizontalScrollView
    private lateinit var row: LinearLayout

    private var apps: List<GamingLibrary.App> = emptyList()
    /** 0: the categories row; 1: the apps of [category]. */
    private var level = 0
    private var category = 0
    private var focus = 0
    private val tiles = ArrayList<View>()

    private var inputManager: InputManager? = null
    private val controllers = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {}
        override fun onInputDeviceChanged(deviceId: Int) {}
        override fun onInputDeviceRemoved(deviceId: Int) {
            main.removeCallbacks(leaveIfNotHome)
            main.postDelayed(leaveIfNotHome, LEAVE_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)
        window.decorView.setBackgroundColor(Color.BLACK)
        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        setContentView(root)
        hideSystemBars()
        inputManager = getSystemService(InputManager::class.java)
        inputManager?.registerInputDeviceListener(controllers, main)
        // NDot is read from the phone (none ships with the module): off the main thread.
        worker.execute {
            val face = loadNdot()
            main.post {
                ndot = face
                if (!isDestroyed) intro()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (phase == Phase.MAIN) {
            store.reload()
            reloadApps() // an app installed or removed while away
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        inputManager?.unregisterInputDeviceListener(controllers)
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
    }

    private fun hideSystemBars() {
        window.insetsController?.let {
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsets.Type.systemBars())
        }
    }

    // -------------------------------------------------------------------------------------
    // Start: intro and the first-time warning
    // -------------------------------------------------------------------------------------

    private fun intro() {
        phase = Phase.INTRO
        val title = text("NOTHING\nGAMING SPACE", 46f, GREEN)
        title.gravity = Gravity.CENTER
        title.setLineSpacing(0f, 1.15f)
        title.setShadowLayer(dp(18f), 0f, 0f, GLOW) // the glow, as on the status bar
        title.alpha = 0f
        root.addView(title, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER))
        val next = Runnable {
            root.removeView(title)
            if (store.config.gamingWarned) showMain() else warning()
        }
        val out = Runnable {
            title.animate().alpha(0f).setDuration(INTRO_OUT_MS).withEndAction(next).start()
        }
        title.animate().alpha(1f).setDuration(INTRO_IN_MS).start()
        main.postDelayed(out, INTRO_IN_MS + INTRO_HOLD_MS)
        skip = {
            main.removeCallbacks(out)
            title.animate().cancel()
            next.run()
        }
    }

    private fun warning() {
        phase = Phase.WARNING
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        val pad = dp(48f).toInt()
        box.setPadding(pad, pad, pad, pad)
        val title = text("PHOTOSENSITIVITY WARNING", 24f, GREEN)
        title.gravity = Gravity.CENTER
        title.setShadowLayer(dp(10f), 0f, 0f, GLOW)
        box.addView(title)
        val body = TextView(this)
        body.text = "A very small number of people may have a seizure when exposed to certain" +
            " visual images, including flashing lights or patterns that may appear in video" +
            " games. Even people with no history of seizures or epilepsy may have an" +
            " undiagnosed condition.\n\nIf you or anyone in your family has an epileptic" +
            " condition, consult a doctor before playing. Stop playing at once and see a" +
            " doctor if you feel dizzy, have blurred vision, eye or muscle twitches, loss of" +
            " awareness, disorientation, or any involuntary movement."
        body.setTextColor(Color.WHITE)
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        body.gravity = Gravity.CENTER
        body.setPadding(0, dp(18f).toInt(), 0, dp(18f).toInt())
        box.addView(body, LinearLayout.LayoutParams(dp(640f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT))
        val hint = text("PRESS A TO CONTINUE", 13f, DIM)
        box.addView(hint)
        box.alpha = 0f
        root.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        box.animate().alpha(1f).setDuration(500).start()
        // Shown once: from now on only the intro plays.
        store.config.gamingWarned = true
        store.save(this)
        val next = Runnable {
            box.animate().alpha(0f).setDuration(400).withEndAction {
                root.removeView(box)
                showMain()
            }.start()
        }
        main.postDelayed(next, WARNING_MS)
        box.setOnClickListener { skip?.invoke() }
        skip = {
            main.removeCallbacks(next)
            skip = null
            next.run()
        }
    }

    // -------------------------------------------------------------------------------------
    // The main screen: the big dot screen on top, the row below
    // -------------------------------------------------------------------------------------

    private fun showMain() {
        phase = Phase.MAIN
        skip = null
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        val pad = dp(24f).toInt()
        page.setPadding(pad, pad, pad, dp(12f).toInt())

        // Top half: the big screen and what is selected.
        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        val display = FrameLayout(this)
        display.background = rounded(PANEL, dp(28f))
        bigEmoji = AnimatedEmojiView(this)
        bigEmoji.setDotMatrix(true)
        display.addView(bigEmoji, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        bigIcon = ImageView(this)
        bigIcon.scaleType = ImageView.ScaleType.FIT_CENTER
        val inset = dp(24f).toInt()
        display.addView(bigIcon, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).also {
            it.setMargins(inset, inset, inset, inset)
        })
        top.addView(display, LinearLayout.LayoutParams(0, 0)) // sized once laid out
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.setPadding(dp(32f).toInt(), 0, 0, 0)
        bigTitle = text("", 40f, GREEN)
        bigTitle.setShadowLayer(dp(12f), 0f, 0f, GLOW)
        bigTitle.maxLines = 2
        texts.addView(bigTitle)
        bigInfo = text("", 16f, DIM)
        bigInfo.setPadding(0, dp(8f).toInt(), 0, 0)
        texts.addView(bigInfo)
        top.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        top.addOnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob ->
            if (b - t != ob - ot) {
                val side = b - t
                display.layoutParams = LinearLayout.LayoutParams(side, side)
                bigEmoji.setTextSize(TypedValue.COMPLEX_UNIT_PX, side * 0.6f)
            }
        }

        // Bottom half: where we are, then the row.
        header = text("", 14f, DIM)
        header.setPadding(0, dp(16f).toInt(), 0, dp(8f).toInt())
        page.addView(header)
        scroller = HorizontalScrollView(this)
        scroller.isHorizontalScrollBarEnabled = false
        scroller.clipChildren = false
        row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.clipChildren = false
        val side = dp(24f).toInt()
        row.setPadding(side, 0, side, 0)
        scroller.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        page.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            0, 1f))

        page.alpha = 0f
        root.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        page.animate().alpha(1f).setDuration(500).start()
        reloadApps()
    }

    private fun reloadApps() {
        val config = store.config
        worker.execute {
            val loaded = GamingLibrary.load(this, config)
            main.post {
                if (isDestroyed) return@post
                apps = loaded
                if (level == 1 && appsOf(category).isEmpty()) level = 0
                buildRow()
            }
        }
    }

    private fun appsOf(index: Int): List<GamingLibrary.App> {
        val cat = GamingLibrary.order[index]
        return apps.filter { it.category == cat }
    }

    /** Fills the row: the categories, or the apps of the open category. */
    private fun buildRow() {
        row.removeAllViews()
        tiles.clear()
        if (level == 0) {
            GamingLibrary.order.forEachIndexed { i, cat ->
                val tile = categoryTile(cat)
                tile.setOnClickListener { focus = i; select() }
                add(tile)
            }
            focus = focus.coerceIn(0, tiles.size - 1)
        } else {
            appsOf(category).forEachIndexed { i, app ->
                val tile = appTile(app)
                tile.setOnClickListener { if (focus == i) select() else { focus = i; refresh() } }
                tile.setOnLongClickListener { focus = i; refresh(); menu(); true }
                add(tile)
            }
            focus = focus.coerceIn(0, maxOf(0, tiles.size - 1))
        }
        refresh()
    }

    private fun add(tile: View) {
        val lp = LinearLayout.LayoutParams(dp(150f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(10f).toInt(), 0, dp(10f).toInt(), 0)
        row.addView(tile, lp)
        tiles += tile
    }

    private fun categoryTile(cat: GamingLibrary.Category): View {
        val tile = LinearLayout(this)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER_HORIZONTAL
        val cover = AnimatedEmojiView(this)
        cover.setDotMatrix(true)
        val size = dp(120f)
        cover.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.6f)
        setCover(cover, cat.emoji, cat.ordinal.toLong())
        cover.background = rounded(PANEL, dp(20f))
        tile.addView(cover, LinearLayout.LayoutParams(size.toInt(), size.toInt()))
        tile.addView(label(cat.title))
        return tile
    }

    private fun appTile(app: GamingLibrary.App): View {
        val tile = LinearLayout(this)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER_HORIZONTAL
        val icon = ImageView(this)
        val size = dp(110f).toInt()
        icon.tag = app.packageName
        dotIcon(app.packageName) { if (icon.tag == app.packageName) icon.setImageBitmap(it) }
        tile.addView(icon, LinearLayout.LayoutParams(size, size))
        tile.addView(label(app.label))
        return tile
    }

    private fun label(name: String): TextView {
        val label = text(name, 14f, Color.WHITE)
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = android.text.TextUtils.TruncateAt.END
        label.setPadding(0, dp(10f).toInt(), 0, 0)
        return label
    }

    /** Lights the selected tile, dims the rest, and shows the selection on the big screen. */
    private fun refresh() {
        tiles.forEachIndexed { i, tile ->
            val on = i == focus
            tile.animate().scaleX(if (on) 1.12f else 1f).scaleY(if (on) 1.12f else 1f)
                .alpha(if (on) 1f else 0.55f).setDuration(150).start()
            ((tile as ViewGroup).getChildAt(1) as TextView).setTextColor(
                if (on) GREEN else Color.WHITE)
        }
        tiles.getOrNull(focus)?.let { tile ->
            tile.post {
                val target = tile.left + tile.width / 2 - scroller.width / 2
                scroller.smoothScrollTo(maxOf(0, target), 0)
            }
        }
        if (level == 0) {
            val cat = GamingLibrary.order[focus]
            val count = appsOf(focus).size
            showOnScreen(cat.emoji, null, cat.title.uppercase(),
                if (count == 1) "1 APP" else "$count APPS")
            header.text = "CATEGORIES   ·   A OPEN"
        } else {
            val cat = GamingLibrary.order[category]
            val app = appsOf(category).getOrNull(focus)
            header.text = "${cat.title.uppercase()}   ·   A PLAY   B BACK   L1 R1 CATEGORY" +
                "   Y MOVE"
            if (app == null) {
                showOnScreen(cat.emoji, null, cat.title.uppercase(), "NO APPS")
            } else {
                showOnScreen(null, app.packageName, app.label, cat.title.uppercase())
            }
        }
    }

    /** The big screen: an animated emoji, or an app's icon as a dot screen. */
    private fun showOnScreen(emoji: String?, pkg: String?, title: String, info: String) {
        bigTitle.text = title
        bigInfo.text = info
        if (emoji != null) {
            bigIcon.visibility = View.GONE
            bigEmoji.visibility = View.VISIBLE
            setCover(bigEmoji, emoji, emoji.hashCode().toLong())
        } else if (pkg != null) {
            bigEmoji.visibility = View.GONE
            bigIcon.visibility = View.VISIBLE
            bigIcon.tag = pkg
            bigIcon.setImageBitmap(dotIcons.get(pkg))
            dotIcon(pkg) { if (bigIcon.tag == pkg) bigIcon.setImageBitmap(it) }
        }
    }

    private fun setCover(view: AnimatedEmojiView, emoji: String, seed: Long) {
        val catalog = EmojiCatalog.get(this)
        val spec = catalog.effectFor(emoji)
        view.setEmoji(emoji, catalog.motionFor(emoji), DotEffect.parse(spec),
            DotEffect.anchor(spec), seed)
    }

    /** An app's icon as a dot screen (made once, off the main thread). */
    private fun dotIcon(pkg: String, done: (Bitmap) -> Unit) {
        dotIcons.get(pkg)?.let { done(it); return }
        worker.execute {
            val bitmap = try {
                val drawable = packageManager.getApplicationIcon(pkg)
                val size = 256
                val raw = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(Canvas(raw))
                IconScreen.dots(raw) ?: raw
            } catch (t: Throwable) {
                null
            }
            if (bitmap != null) main.post {
                dotIcons.put(pkg, bitmap)
                done(bitmap)
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Controller and keys
    // -------------------------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (phase != Phase.MAIN) {
            // The intro and the warning: a button skips; volume and other keys work as always.
            val skips = KeyEvent.isGamepadButton(keyCode) || keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
            if (!skips) return super.onKeyDown(keyCode, event)
            if (event.repeatCount == 0) skip?.invoke()
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> move(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(1)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (level == 0) select()
            KeyEvent.KEYCODE_DPAD_UP -> if (level == 1) back()
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_START ->
                if (event.repeatCount == 0) select()
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> if (event.repeatCount == 0) back()
            KeyEvent.KEYCODE_BUTTON_L1 -> switchCategory(-1)
            KeyEvent.KEYCODE_BUTTON_R1 -> switchCategory(1)
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_MENU ->
                if (event.repeatCount == 0) menu()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Taken on the way down; the way up is taken too, so Back does not leave home.
        return if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) true
        else super.onKeyUp(keyCode, event)
    }

    @Deprecated("Back is the controller's B here")
    override fun onBackPressed() {
        if (phase == Phase.MAIN) back() else skip?.invoke()
    }

    private fun move(d: Int) {
        if (tiles.isEmpty()) return
        val next = (focus + d).coerceIn(0, tiles.size - 1)
        if (next != focus) {
            focus = next
            refresh()
        }
    }

    private fun select() {
        if (level == 0) {
            category = focus
            level = 1
            focus = 0
            buildRow()
            return
        }
        val app = appsOf(category).getOrNull(focus) ?: return
        val launch = packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        try {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (t: Throwable) {
            bigInfo.text = "COULD NOT OPEN"
        }
    }

    private fun back() {
        if (level == 1) {
            level = 0
            focus = category
            buildRow()
        }
    }

    private fun switchCategory(d: Int) {
        if (level == 0) {
            move(d)
            return
        }
        val count = GamingLibrary.order.size
        category = (category + d + count) % count
        focus = 0
        buildRow()
    }

    /** Moves the selected app to another category, or hides it. */
    private fun menu() {
        if (level != 1) return
        val app = appsOf(category).getOrNull(focus) ?: return
        val cats = GamingLibrary.order
        val names = cats.map { it.title } + "Hide from Gaming Space"
        Dialogs.builder(this)
            .setTitle(app.label)
            .setSingleChoiceItems(names.toTypedArray(), cats.indexOf(app.category)) { d, which ->
                store.config.gamingCategories[app.packageName] =
                    if (which < cats.size) cats[which].name else GamingLibrary.HIDDEN
                store.save(this)
                d.dismiss()
                reloadApps()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // -------------------------------------------------------------------------------------
    // Leaving when the controller goes
    // -------------------------------------------------------------------------------------

    /**
     * After the last controller disconnects, ControllerHome puts the usual home app back; this
     * screen then hands over to it. Checked against what Android opens as home now, so it never
     * leaves while it is still the home app (that would open it again).
     */
    private val leaveIfNotHome = Runnable {
        if (controllerConnected()) return@Runnable
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(home, 0)?.activityInfo ?: return@Runnable
        if (resolved.packageName == packageName) return@Runnable
        startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun controllerConnected(): Boolean = InputDevice.getDeviceIds().any { id ->
        val device = InputDevice.getDevice(id) ?: return@any false
        val sources = device.sources
        !device.isVirtual && (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
    }

    // -------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------

    /** Nothing's NDot from the phone: NDot 77 first (Dr.Essence's pick), then any NDot. */
    private fun loadNdot(): Typeface? = try {
        val fonts = SystemFont.find(this).filter { it.title.lowercase().contains("ndot") }
        val choice = fonts.firstOrNull { it.title.contains("77") } ?: fonts.firstOrNull()
        choice?.let { SystemFont.typeface(this, it) }
    } catch (t: Throwable) {
        null
    }

    private fun text(value: String, sp: Float, color: Int): TextView {
        val view = TextView(this)
        view.text = value
        view.setTextColor(color)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        view.typeface = ndot ?: Typeface.MONOSPACE
        return view
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().also {
        it.setColor(color)
        it.cornerRadius = radius
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
