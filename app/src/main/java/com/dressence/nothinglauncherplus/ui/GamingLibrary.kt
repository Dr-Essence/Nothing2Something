package com.dressence.nothinglauncherplus.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import com.dressence.nothinglauncherplus.Config

/**
 * Nothing Gaming Space's apps, each in one category.
 *
 * How an app's category is found, first match wins:
 * Names: the app's own, or the one the user gave it ([Config.gamingNames]).
 *
 * 1. The user's own choice ([Config.gamingCategories]), made with the controller's menu button
 *    or a long press; [HIDDEN] keeps an app out of every category.
 * 2. A known emulator ([EMULATORS]: package names, or the start of them for families such as
 *    RetroArch's builds). Android has no category for emulators, so they are listed.
 * 3. Android's own app category, which every app declares in its manifest
 *    (`ApplicationInfo.category`): GAME, AUDIO and VIDEO. Older games set the "is a game"
 *    flag instead (`FLAG_IS_GAME`).
 * 4. Browser: the apps Android offers to open a web link with.
 * 5. TV: apps made for TV screens too (they have a leanback launcher entry).
 * 6. Everything else is under [Category.APPS], so any app can still be found and moved.
 */
internal object GamingLibrary {

    /** The categories, in their order on screen, each with its animated cover's emoji. */
    enum class Category(val title: String, val emoji: String) {
        GAMES("Games", "🎮"),
        EMULATORS("Emulators", "👾"),
        BROWSER("Browser", "🌐"),
        MUSIC("Music", "🎵"),
        VIDEO("Video", "🎬"),
        TV("TV", "📺"),
        APPS("Apps", "📱"),
    }

    /** The user's choice that keeps an app out of every category. */
    const val HIDDEN = "HIDDEN"

    class App(val packageName: String, val label: String, val category: Category)

    /**
     * Emulators, by package name; a name ending in "*" matches every package starting with it.
     * Checked against each app's package as installed.
     */
    private val EMULATORS = listOf(
        "com.retroarch*", "org.ppsspp.*", "org.dolphinemu.*", "xyz.aethersx2.*",
        "xyz.nethersx2.*", "org.citra.*", "io.github.lime3ds.*", "org.azahar_emu.*",
        "io.github.borked3ds.*", "org.yuzu.*", "dev.eden.*", "org.sudachi.*", "dev.suyu.*",
        "emu.skyline", "org.skyline.*", "org.vita3k.*",
        "com.github.stenzek.duckstation", "org.mupen64plusae.*", "paulscode.android.mupen64plus*",
        "me.magnum.melonds", "com.dsemu.drastic", "com.explusalpha.*", "com.fms.*",
        "com.flycast.emulator", "com.reicast.*", "com.winlator*", "app.gamenative*",
        "com.seleuco.mame4droid*", "com.epsxe.*", "ru.vastness.altmer.*",
        "org.mm.jr", "com.swordfish.lemuroid", "info.cemu.*", "org.ryujinx.*",
        "com.github.yuzu.*", "io.github.mandarine3ds.*", "com.dolphinemu.*", "org.scummvm.*",
        "com.gamehub.*", "com.xiaoji.*", "com.mobox.*",
    )

    /** The categories in the order they are shown; empty ones are kept, so the row is stable. */
    val order: List<Category> = Category.entries

    /** Every launchable app with its category, by label; hidden apps left out. */
    fun load(context: Context, config: Config): List<App> {
        val pm = context.packageManager
        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val browsers = packages(pm, Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE))
        val tv = packages(pm, Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))
        val seen = HashSet<String>()
        val apps = ArrayList<App>()
        for (info in launchable) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName || !seen.add(pkg)) continue
            val chosen = config.gamingCategories[pkg]
            if (chosen == HIDDEN) continue
            val category = Category.entries.firstOrNull { it.name == chosen }
                ?: automatic(pkg, info.activityInfo.applicationInfo, browsers, tv)
            // The user's own name for it, if given (also what SteamGridDB is searched with).
            val label = config.gamingNames[pkg] ?: info.loadLabel(pm).toString()
            apps += App(pkg, label, category)
        }
        apps.sortBy { it.label.lowercase() }
        return apps
    }

    /** The category an app gets by itself (see the class comment, steps 2 to 6). */
    private fun automatic(pkg: String, app: ApplicationInfo, browsers: Set<String>,
            tv: Set<String>): Category {
        @Suppress("DEPRECATION")
        return when {
            isEmulator(pkg) -> Category.EMULATORS
            app.category == ApplicationInfo.CATEGORY_GAME ||
                app.flags and ApplicationInfo.FLAG_IS_GAME != 0 -> Category.GAMES
            pkg in browsers -> Category.BROWSER
            app.category == ApplicationInfo.CATEGORY_AUDIO -> Category.MUSIC
            app.category == ApplicationInfo.CATEGORY_VIDEO -> Category.VIDEO
            pkg in tv -> Category.TV
            else -> Category.APPS
        }
    }

    private fun isEmulator(pkg: String): Boolean = EMULATORS.any { pattern ->
        if (pattern.endsWith("*")) pkg.startsWith(pattern.dropLast(1)) else pkg == pattern
    }

    private fun packages(pm: PackageManager, intent: Intent): Set<String> =
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapTo(HashSet()) { it.activityInfo.packageName }
}
