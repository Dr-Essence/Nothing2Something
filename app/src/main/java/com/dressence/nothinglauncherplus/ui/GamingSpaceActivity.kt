package com.dressence.nothinglauncherplus.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.dressence.nothinglauncherplus.anim.AnimatedEmojiView
import com.dressence.nothinglauncherplus.anim.DotEffect
import com.dressence.nothinglauncherplus.Palette
import com.dressence.nothinglauncherplus.launcher.IconScreen
import com.dressence.nothinglauncherplus.xposed.NdotFont
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

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
        private const val LED_GREEN = Palette.LED_GREEN
        /** The dot screen's share of the page's height; the cross gets the rest. */
        private const val SCREEN_SHARE = 0.5f
        /** A category's card, as a drawer folder's: size (dp), emoji share, corner. */
        private const val CARD_W = 92f
        private const val CARD_H = 96f
        private const val COVER_SCALE = 0.86f
        private const val CARD_CORNER = 0.085f
        /** An app's row in the column (dp). */
        private const val ITEM_H = 52f
        private const val ITEM_ICON = 46f
        /** "Adjust artwork": one step of the controller, in pan and in zoom. */
        private const val PAN_STEP = 0.1f
        private const val ZOOM_STEP = 0.1f
        private const val MOVE_MS = 180L
        /** How long a category's emoji stays after its animation, before its app comes up. */
        private const val CATEGORY_SHOW_MS = 900L
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
    /** Box sides by package (see [boxFace]). */
    private val faces = LruCache<String, Bitmap>(64)
    private var ndot: Typeface? = null
    private var phase = Phase.INTRO
    private var skip: (() -> Unit)? = null

    private lateinit var root: FrameLayout
    /** The top part: one dot screen, edge to edge. */
    private lateinit var screen: AnimatedEmojiView
    /** A category's animation, played over the whole screen when it is selected. */
    private lateinit var animation: DotAnimationView
    /** A game's artwork from SteamGridDB, when that is on (see [SteamGridArt]). */
    private lateinit var artwork: DotAnimationView
    private val art by lazy { SteamGridArt(this) }
    /** A game's trailer from Steam, when that is on (see [SteamTrailers]). */
    private lateinit var video: DotVideoView
    private val trailers by lazy { SteamTrailers(this) }
    /** "Adjust artwork": the app being adjusted and where its picture is now. */
    private var adjusting: GamingLibrary.App? = null
    private var adjustFrame = Framing.CENTRED
    private lateinit var adjustHint: TextView
    /** Menus in Gaming Space's own look, over the page. */
    private val sheet by lazy { NothingSheet(this, root) { ndot } }
    /** Touch: swipes move through categories and apps, a tap on the big screen plays. */
    private val gestures by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (phase != Phase.MAIN || sheet.isOpen) return false
                if (abs(vx) > abs(vy)) setCategory(category + if (vx < 0) 1 else -1)
                else setApp(appFocus[category] + if (vy < 0) 1 else -1)
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (phase != Phase.MAIN || sheet.isOpen || e.y > screenHeight) return false
                launch()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (phase == Phase.MAIN && !sheet.isOpen && e.y <= screenHeight) menu()
            }
        })
    }

    /** Touches are watched for swipes first; taps on cards and apps still reach them. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (adjusting != null) {
            adjustTouch(ev)
            return true
        }
        if (phase == Phase.MAIN && !sheet.isOpen && gestures.onTouchEvent(ev) &&
            ev.actionMasked == MotionEvent.ACTION_UP) {
            // A swipe ended here: cancel it for the views, so it is not also a tap.
            ev.action = MotionEvent.ACTION_CANCEL
        }
        return super.dispatchTouchEvent(ev)
    }
    private var screenHeight = 0
    /** The categories' cards across, and the open category's apps down. */
    private lateinit var bar: FrameLayout
    private lateinit var column: FrameLayout
    private val cards = ArrayList<View>()
    private val items = ArrayList<View>()

    private var apps: List<GamingLibrary.App> = emptyList()
    private var category = 0
    /** The selected app in each category, kept while moving between them. */
    private val appFocus = IntArray(GamingLibrary.order.size)

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
        if (phase == Phase.MAIN && screenHeight > 0) {
            store.reload()
            reloadApps() // an app installed or removed while away
        }
    }

    override fun onPause() {
        super.onPause()
        // A game opened from here: its trailer stops (no stream in the background).
        if (phase == Phase.MAIN && screenHeight > 0) video.play(null)
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
    // The main screen: one dot screen on top, the cross of categories and apps below
    // -------------------------------------------------------------------------------------

    /**
     * Lays out the top half as one dot screen (the drawer folders' engine, edge to edge) and,
     * below, the categories as folder-style dot cards across, and under the selected one its
     * apps down, as on the PlayStation's cross menu: the selected category stays in one place
     * and the bar moves under it.
     */
    private fun showMain() {
        phase = Phase.MAIN
        skip = null
        val page = FrameLayout(this)
        screen = AnimatedEmojiView(this)
        screen.setDotMatrix(true)
        page.addView(screen, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
        artwork = DotAnimationView(this)
        artwork.visibility = View.GONE
        page.addView(artwork, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
        video = DotVideoView(this)
        page.addView(video, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
        adjustHint = text("", 12f, GREEN)
        adjustHint.setShadowLayer(dp(8f), 0f, 0f, GLOW)
        adjustHint.visibility = View.GONE
        page.addView(adjustHint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        animation = DotAnimationView(this)
        animation.visibility = View.GONE
        page.addView(animation, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
        bar = FrameLayout(this)
        bar.clipChildren = false
        page.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        column = FrameLayout(this)
        column.clipChildren = false
        page.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        page.clipChildren = false
        page.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            // Sized after this layout pass, not inside it.
            if (r - l != or - ol || b - t != ob - ot) page.post { place(r - l, b - t) }
        }
        page.alpha = 0f
        root.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        page.animate().alpha(1f).setDuration(500).start()
        reloadApps()
    }

    /** Sizes the dot screen to the top part of the page; the cross is placed below it. */
    /**
     * The top part: one dot screen edge to edge, nothing round it (Dr.Essence on 0.64.0: no
     * title, no bezel, nothing behind it). The folders' engine puts a bezel inside its panel's
     * edge (DotMatrix.layout: the larger of 1.2 dots and 4.5% of the panel's smaller side), so
     * the panel is made larger than the screen by that much: the bezel falls outside the view
     * and only dots show. Artwork, trailers and the category animations use the same dots.
     */
    private fun place(width: Int, height: Int) {
        screenHeight = (height * SCREEN_SHARE).toInt()
        for (view in listOf(screen, animation, artwork, video)) {
            view.layoutParams = FrameLayout.LayoutParams(width, screenHeight)
        }
        val side = (screenHeight * 0.8f).toInt()
        val pitch = side / 32f
        screen.setTextSize(TypedValue.COMPLEX_UNIT_PX, side * 0.6f)
        val left = (width - side) / 2
        val top = (screenHeight - side) / 2
        // The bezel the engine would draw for a panel this tall, solved for the larger panel.
        val bezel = (maxOf(pitch * 1.2f, screenHeight * 0.045f / (1f - 0.09f)) + 2f).toInt()
        screen.setDotLabel(null, 0, null, null, 0f)
        screen.setDotPanel(Rect(left, top, left + side, top + side),
            Rect(-bezel, -bezel, width + bezel, screenHeight + bezel), 0f)
        animation.setGeometry(pitch, 0f)
        artwork.setGeometry(pitch, 0f)
        video.setGeometry(pitch, 0f)
        layoutCross()
        showSelection(false)
    }

    private fun reloadApps() {
        val config = store.config
        worker.execute {
            val loaded = GamingLibrary.load(this, config)
            main.post {
                if (isDestroyed || phase != Phase.MAIN) return@post
                apps = loaded
                buildCards()
                buildColumn()
                showSelection(false)
            }
        }
    }

    private fun appsOf(index: Int): List<GamingLibrary.App> {
        val cat = GamingLibrary.order[index]
        return apps.filter { it.category == cat }
    }

    /** The categories, each a dot card like a drawer folder: its emoji and name on the dots. */
    private fun buildCards() {
        bar.removeAllViews()
        cards.clear()
        val w = dp(CARD_W).toInt()
        val h = dp(CARD_H).toInt()
        // As a drawer folder: the name's strip on top, the emoji's screen under it.
        val labelH = dp(28f).toInt()
        val side = ((h - labelH) * COVER_SCALE).toInt()
        GamingLibrary.order.forEachIndexed { i, cat ->
            val card = AnimatedEmojiView(this)
            card.setDotMatrix(true)
            card.setTextSize(TypedValue.COMPLEX_UNIT_PX, side * 0.6f)
            setCover(card, cat.emoji, cat.ordinal.toLong())
            val left = (w - side) / 2
            val top = labelH + (h - labelH - side) / 2
            card.setDotPanel(Rect(left, top, left + side, top + side), Rect(0, 0, w, h),
                minOf(w, h) * CARD_CORNER)
            card.setDotLabel(cat.title, LED_GREEN, Rect(0, dp(4f).toInt(), w, labelH),
                NdotFont.get(this), dp(13f))
            card.setOnClickListener { setCategory(i) }
            bar.addView(card, FrameLayout.LayoutParams(w, h))
            cards += card
        }
        layoutCross()
    }

    /** The open category's apps, top to bottom: a dot icon and the name beside it. */
    private fun buildColumn() {
        column.removeAllViews()
        items.clear()
        val list = appsOf(category)
        if (list.isEmpty()) {
            val none = text("NO APPS HERE  ·  Y ON AN APP MOVES IT", 14f, DIM)
            column.addView(none, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
            items += none
        }
        list.forEachIndexed { j, app ->
            val item = LinearLayout(this)
            item.gravity = Gravity.CENTER_VERTICAL
            val cube = DotCubeView(this)
            cube.tag = app.packageName
            boxFace(app.packageName) { if (cube.tag == app.packageName) cube.setFace(it) }
            val size = dp(ITEM_ICON).toInt()
            item.addView(cube, LinearLayout.LayoutParams(size, size))
            val name = text(app.label, 18f, Color.WHITE)
            name.maxLines = 1
            name.ellipsize = android.text.TextUtils.TruncateAt.END
            name.setPadding(dp(16f).toInt(), 0, 0, 0)
            item.addView(name, LinearLayout.LayoutParams(dp(420f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT))
            item.setOnClickListener { if (appFocus[category] == j) launch() else setApp(j) }
            item.setOnLongClickListener { setApp(j); menu(); true }
            column.addView(item, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(ITEM_H).toInt()))
            items += item
        }
        layoutCross()
    }

    /**
     * The cross: the selected category's card in its fixed place, the others along the bar
     * beside it; under it, the selected app first and the next ones below, the earlier ones
     * gone above (out of sight), as on the cross menu.
     */
    private fun layoutCross() {
        if (screenHeight == 0) return
        val slot = dp(40f)
        val barY = screenHeight + dp(8f)
        val step = dp(CARD_W + 18f)
        cards.forEachIndexed { i, card ->
            val on = i == category
            card.animate().translationX(slot + (i - category) * step).translationY(barY)
                .scaleX(if (on) 1f else 0.82f).scaleY(if (on) 1f else 0.82f)
                .alpha(if (on) 1f else 0.45f).setDuration(MOVE_MS).start()
        }
        val focus = appFocus[category]
        val top = barY + dp(CARD_H + 8f)
        val x = slot + dp(CARD_W + 28f)
        items.forEachIndexed { j, item ->
            val off = j - focus
            val on = off == 0
            item.pivotX = 0f
            item.animate().translationX(x).translationY(top + maxOf(off, 0) * dp(ITEM_H))
                .alpha(if (off < 0) 0f else if (on) 1f else 0.5f)
                .scaleX(if (on) 1f else 0.85f).scaleY(if (on) 1f else 0.85f)
                .setDuration(MOVE_MS).start()
            ((item as? ViewGroup)?.getChildAt(1) as? TextView)?.setTextColor(
                if (on) GREEN else Color.WHITE)
            ((item as? ViewGroup)?.getChildAt(0) as? DotCubeView)?.lit = on
        }
    }

    private fun setCategory(index: Int) {
        val next = index.coerceIn(0, GamingLibrary.order.size - 1)
        if (next == category && column.childCount > 0) return
        category = next
        buildColumn()
        showSelection(true)
    }

    private fun setApp(index: Int) {
        val list = appsOf(category)
        if (list.isEmpty()) return
        val next = index.coerceIn(0, list.size - 1)
        if (next == appFocus[category]) return
        appFocus[category] = next
        layoutCross()
        showSelection(false)
    }

    /**
     * The big screen: on a new category, its emoji plays first, then the selected app's icon
     * comes up; moving between apps shows the app at once.
     */
    private fun showSelection(categoryFirst: Boolean) {
        main.removeCallbacks(showApp)
        if (adjusting != null) endAdjust(false)
        screen.visibility = View.VISIBLE
        artwork.showArtwork(null)
        video.play(null)
        val cat = GamingLibrary.order[category]
        val app = appsOf(category).getOrNull(appFocus[category])
        if (categoryFirst) {
            // Its animation over the whole screen; under it the emoji and name wait, and stay
            // a moment once it fades, before the selected app comes up.
            setCover(screen, cat.emoji, cat.ordinal * 31L)
            animation.play(cat) {
                if (appsOf(category).isNotEmpty()) main.postDelayed(showApp, CATEGORY_SHOW_MS)
            }
        } else if (app == null) {
            animation.stop()
            setCover(screen, cat.emoji, cat.ordinal * 31L)
        } else {
            animation.stop()
            showApp.run()
        }
    }

    private val showApp = Runnable {
        val app = appsOf(category).getOrNull(appFocus[category]) ?: return@Runnable
        rawIcon(app.packageName)?.let { screen.setImage(it, app.packageName.hashCode().toLong()) }
        showArtwork(app)
        showTrailer(app)
    }

    /** The game's framing in the banner, as adjusted (centred if never). */
    private fun framingOf(pkg: String) = Framing.parse(store.config.gamingFrames[pkg])

    /**
     * A game's trailer from Steam over its artwork, when trailers are on: looked up once,
     * streamed while the game is selected, muted and looping.
     */
    private fun showTrailer(app: GamingLibrary.App) {
        if (!store.config.gamingTrailers || !isGame(app)) return
        worker.execute {
            val url = trailers.find(app.packageName, app.label) ?: return@execute
            main.post {
                val now = appsOf(category).getOrNull(appFocus[category])
                if (!isDestroyed && now?.packageName == app.packageName &&
                    animation.visibility != View.VISIBLE) {
                    video.framing = framingOf(app.packageName)
                    video.onPlaying = { ok -> if (ok) screen.visibility = View.INVISIBLE }
                    video.play(url)
                }
            }
        }
    }

    private fun isGame(app: GamingLibrary.App) = app.category == GamingLibrary.Category.GAMES ||
        app.category == GamingLibrary.Category.EMULATORS

    /**
     * A game's (or emulator's) artwork over the icon, when artwork is on: from this app's
     * files, or looked up once on SteamGridDB; shown only if the app is still selected.
     */
    private fun showArtwork(app: GamingLibrary.App) {
        if (!store.config.gamingArtwork) return
        if (app.category != GamingLibrary.Category.GAMES &&
            app.category != GamingLibrary.Category.EMULATORS) return
        worker.execute {
            val picture = art.fetch(app.packageName, app.label,
                store.config.gamingArtworkAnimated) ?: return@execute
            main.post {
                val now = appsOf(category).getOrNull(appFocus[category])
                if (!isDestroyed && now?.packageName == app.packageName &&
                    animation.visibility != View.VISIBLE) {
                    artwork.framing = framingOf(app.packageName)
                    artwork.showArtwork(picture)
                    screen.visibility = View.INVISIBLE // nothing behind the artwork
                }
            }
        }
    }


    /** The icon as it is (for the big screen, which makes its own dots). */
    private fun rawIcon(pkg: String): Bitmap? = try {
        val drawable = packageManager.getApplicationIcon(pkg)
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        bitmap
    } catch (t: Throwable) {
        null
    }

    private fun setCover(view: AnimatedEmojiView, emoji: String, seed: Long) {
        val catalog = EmojiCatalog.get(this)
        val spec = catalog.effectFor(emoji)
        view.setEmoji(emoji, catalog.motionFor(emoji), DotEffect.parse(spec),
            DotEffect.anchor(spec), seed)
    }

    /**
     * The side of an app's box: its icon as a dot screen (the launcher's dot icons) on a dark
     * plate; made once, off the main thread.
     */
    private fun boxFace(pkg: String, done: (Bitmap) -> Unit) {
        faces.get(pkg)?.let { done(it); return }
        worker.execute {
            val raw = rawIcon(pkg) ?: return@execute
            val face = try {
                plate(IconScreen.dots(raw) ?: raw)
            } catch (t: Throwable) {
                plate(raw)
            }
            main.post {
                faces.put(pkg, face)
                done(face)
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Controller and keys
    // -------------------------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (sheet.isOpen) {
            return if (event.repeatCount == 0 || keyCode in arrayOf(KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT)) {
                sheet.onKey(keyCode) || super.onKeyDown(keyCode, event)
            } else true
        }
        if (adjusting != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> nudge(-PAN_STEP, 0f, 0f)
                KeyEvent.KEYCODE_DPAD_RIGHT -> nudge(PAN_STEP, 0f, 0f)
                KeyEvent.KEYCODE_DPAD_UP -> nudge(0f, -PAN_STEP, 0f)
                KeyEvent.KEYCODE_DPAD_DOWN -> nudge(0f, PAN_STEP, 0f)
                KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L2 -> nudge(0f, 0f, -ZOOM_STEP)
                KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2 -> nudge(0f, 0f, ZOOM_STEP)
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                    endAdjust(true)
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> endAdjust(false)
                else -> return super.onKeyDown(keyCode, event)
            }
            return true
        }
        if (phase != Phase.MAIN) {
            // The intro and the warning: a button skips; volume and other keys work as always.
            val skips = KeyEvent.isGamepadButton(keyCode) || keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
            if (!skips) return super.onKeyDown(keyCode, event)
            if (event.repeatCount == 0) skip?.invoke()
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BUTTON_L1 -> setCategory(category - 1)
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BUTTON_R1 -> setCategory(category + 1)
            KeyEvent.KEYCODE_DPAD_UP -> setApp(appFocus[category] - 1)
            KeyEvent.KEYCODE_DPAD_DOWN -> setApp(appFocus[category] + 1)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_START ->
                if (event.repeatCount == 0) launch()
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_MENU ->
                if (event.repeatCount == 0) menu()
            // Home has nowhere to go back to: B and Back are taken and do nothing.
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {}
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Taken on the way down; the way up is taken too, so Back does not leave home.
        return if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) true
        else super.onKeyUp(keyCode, event)
    }

    @Deprecated("Back does nothing on this home screen")
    override fun onBackPressed() {
        if (sheet.isOpen) sheet.close() else if (phase != Phase.MAIN) skip?.invoke()
    }

    private fun launch() {
        val app = appsOf(category).getOrNull(appFocus[category]) ?: return
        val intent = packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (t: Throwable) {
            sheet.message(app.label, "Could not open it.")
        }
    }

    /**
     * The selected app's menu, in Gaming Space's own look ([NothingSheet]): play it, move it
     * to another category, rename it, choose its artwork, or hide it.
     */
    private fun menu() {
        val app = appsOf(category).getOrNull(appFocus[category]) ?: return
        val canArt = store.config.gamingArtwork && SteamGridArt.key(this).isNotEmpty() &&
            (app.category == GamingLibrary.Category.GAMES ||
                app.category == GamingLibrary.Category.EMULATORS)
        val actions = ArrayList<Pair<String, () -> Unit>>()
        actions += "Play" to { launch() }
        actions += "Move to another category" to { moveTo(app) }
        actions += "Rename" to { rename(app) }
        if (canArt) actions += "Choose artwork" to { chooseArtwork(app) }
        if (store.config.gamingTrailers && isGame(app)) {
            actions += "Choose trailer" to { chooseTrailer(app) }
        }
        if (artwork.visibility == View.VISIBLE || video.visibility == View.VISIBLE) {
            actions += "Adjust artwork" to { startAdjust(app) }
        }
        actions += "Hide from Gaming Space" to {
            store.config.gamingCategories[app.packageName] = GamingLibrary.HIDDEN
            store.save(this)
            reloadApps()
        }
        sheet.list(app.label, actions.map { it.first }, 0) { actions[it].second() }
    }

    /**
     * "Adjust artwork": the picture (or trailer) is moved and zoomed in the banner, live. With
     * the controller: the D-pad moves it, L1/R1 (or L2/R2) zoom, A saves, B leaves it as it
     * was. With touch: drag to move, pinch to zoom, tap to save.
     */
    private fun startAdjust(app: GamingLibrary.App) {
        adjusting = app
        adjustFrame = framingOf(app.packageName)
        adjustHint.text = "ADJUST ARTWORK   ·   MOVE   ·   L1 R1 ZOOM   ·   A SAVE   ·   B CANCEL"
        adjustHint.translationX = dp(28f)
        adjustHint.translationY = screenHeight - dp(28f)
        adjustHint.visibility = View.VISIBLE
    }

    private fun nudge(dx: Float, dy: Float, dz: Float) {
        adjustFrame = Framing(adjustFrame.x + dx, adjustFrame.y + dy, adjustFrame.zoom + dz)
            .clamped()
        artwork.framing = adjustFrame
        video.framing = adjustFrame
    }

    private fun endAdjust(save: Boolean) {
        val app = adjusting ?: return
        adjusting = null
        adjustHint.visibility = View.GONE
        if (save) {
            store.config.gamingFrames[app.packageName] = adjustFrame.toString()
            store.save(this)
        } else {
            artwork.framing = framingOf(app.packageName)
            video.framing = framingOf(app.packageName)
        }
    }

    /** "Adjust artwork" by touch: drag moves, pinch zooms, a tap saves. */
    private val pinch by lazy {
        android.view.ScaleGestureDetector(this,
            object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(d: android.view.ScaleGestureDetector): Boolean {
                    nudge(0f, 0f, (d.scaleFactor - 1f) * adjustFrame.zoom)
                    return true
                }
            })
    }
    private var dragX = 0f
    private var dragY = 0f
    private var dragged = false

    private fun adjustTouch(ev: MotionEvent) {
        pinch.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dragX = ev.x; dragY = ev.y; dragged = false }
            MotionEvent.ACTION_MOVE -> if (!pinch.isInProgress && ev.pointerCount == 1) {
                val w = maxOf(1, root.width).toFloat()
                // Dragging right shows more of the left: the picture follows the finger.
                nudge(-(ev.x - dragX) / w * 2f, -(ev.y - dragY) / screenHeight * 2f, 0f)
                if (kotlin.math.hypot(ev.x - dragX, ev.y - dragY) > dp(4f)) dragged = true
                dragX = ev.x
                dragY = ev.y
            }
            MotionEvent.ACTION_UP -> if (!dragged && !pinch.isInProgress) endAdjust(true)
        }
    }

    private fun moveTo(app: GamingLibrary.App) {
        val cats = GamingLibrary.order
        sheet.list("Move to", cats.map { it.title }, cats.indexOf(app.category)) { which ->
            store.config.gamingCategories[app.packageName] = cats[which].name
            store.save(this)
            reloadApps()
        }
    }

    /**
     * Gives the app a name of the user's own: shown here and searched on SteamGridDB (a game
     * listed under another name there is then found). Its saved artwork and any "no match" are
     * forgotten, so it is looked up again at once. An empty name goes back to the app's own.
     */
    private fun rename(app: GamingLibrary.App) {
        sheet.text("Rename", app.label) { name ->
            if (name.isEmpty()) store.config.gamingNames.remove(app.packageName)
            else store.config.gamingNames[app.packageName] = name
            store.save(this)
            worker.execute {
                art.forget(app.packageName)
                trailers.forget(app.packageName)
            }
            reloadApps()
        }
    }

    /**
     * Choosing artwork by hand: SteamGridDB's games for the app's name (or a name typed in,
     * "Search another name"), then the chosen game's pictures, animated banners first; the
     * pick is saved and shown at once.
     */
    private fun chooseArtwork(app: GamingLibrary.App, name: String = app.label) {
        chooseGame("Artwork", name, { art.games(it) }, { chooseArtwork(app, it) }) { game ->
            sheet.message("Artwork", "Loading the pictures of \"${game.second}\"…")
            worker.execute {
                val previews = try {
                    art.choicesFor(game.first).take(14).map { it to art.preview(it) }
                } catch (t: Throwable) {
                    emptyList()
                }
                main.post {
                    if (isDestroyed) return@post
                    if (previews.isEmpty()) {
                        sheet.message("Artwork", "\"${game.second}\" has no artwork yet.")
                        return@post
                    }
                    sheet.pictures("Artwork · ${game.second}",
                        previews.map { (choice, picture) -> choice.kind to picture }) { which ->
                        worker.execute {
                            try {
                                art.save(app.packageName, previews[which].first)
                            } catch (t: Throwable) {
                                main.post { sheet.message("Artwork", "Could not download it.") }
                                return@execute
                            }
                            main.post { showSelection(false) }
                        }
                    }
                }
            }
        }
    }

    /**
     * Choosing the trailer by hand: Steam's games for the app's name (or a name typed in),
     * then the chosen game's trailers; the chosen one is made into the app's clip.
     */
    private fun chooseTrailer(app: GamingLibrary.App, name: String = app.label) {
        chooseGame("Trailer", name, { trailers.games(it) }, { chooseTrailer(app, it) }) { game ->
            sheet.message("Trailer", "Loading the trailers of \"${game.second}\"…")
            worker.execute {
                val movies = try { trailers.movies(game.first) } catch (t: Throwable) { emptyList() }
                main.post {
                    if (isDestroyed) return@post
                    if (movies.isEmpty()) {
                        sheet.message("Trailer", "\"${game.second}\" has no trailer on Steam.")
                        return@post
                    }
                    sheet.list("Trailer · ${game.second}", movies.map { it.first }, 0) { which ->
                        sheet.message("Trailer", "Saving \"${movies[which].first}\"…")
                        worker.execute {
                            val ok = try {
                                trailers.use(app.packageName, movies[which].second)
                            } catch (t: Throwable) {
                                false
                            }
                            main.post {
                                if (isDestroyed) return@post
                                if (ok) { sheet.close(); showSelection(false) }
                                else sheet.message("Trailer", "Could not download it.")
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The games a source finds for [name], to pick the right one; the last choice, "Search
     * another name", asks for a name and starts again with it ([again]).
     */
    private fun chooseGame(what: String, name: String, find: (String) -> List<Pair<Int, String>>,
            again: (String) -> Unit, chosen: (Pair<Int, String>) -> Unit) {
        sheet.message(what, "Searching for \"$name\"…")
        worker.execute {
            val games = try { find(name).take(12) } catch (t: Throwable) { emptyList() }
            main.post {
                if (isDestroyed) return@post
                val options = games.map { it.second } + "Search another name…"
                sheet.list("$what · which game?", options, 0) { which ->
                    if (which < games.size) chosen(games[which])
                    else sheet.text("Search another name", name) { typed ->
                        if (typed.isNotEmpty()) again(typed)
                    }
                }
            }
        }
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

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
