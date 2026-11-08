package com.dressence.nothinglauncherplus.settings

import android.content.Intent
import android.content.pm.ResolveInfo
import com.dressence.nothinglauncherplus.Config
import com.dressence.nothinglauncherplus.xposed.Hooks
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Nothing2Something's row on the Settings home page, first in Nothing's own group (above
 * Intelligence toolkit, Lock screen and Customisation), in Nothing's row style.
 *
 * How it was found (Nothing's Settings, decompiled): the home page adds rows that apps declare
 * themselves, Android's standard Settings tiles (an activity answering
 * com.android.settings.action.EXTRA_SETTINGS with the homepage category, a title, an icon, an
 * order and a group_key: settingslib's TileUtils, then TopLevelSettings.addTilesPreference puts a
 * tile with a group_key into that group). Intelligence toolkit comes in this way. The group with
 * Lock screen and Customisation is `top_level_nt_custom_category` (top_level_settings_v2.xml),
 * and its rows are ordered -30 / -20 / -10; the tile's own order is used as is on that page (it
 * has no placeholder row), so -1000 puts it first.
 *
 * The app declares that tile in its manifest (the SettingsTile activity-alias). Settings only
 * accepts tiles from system apps (TileUtils: `if (resolveInfo.system)`), so here, in Settings,
 * this module's own tile, and nothing else, is answered as one when Settings looks the tiles
 * up. Everything after that (place, order, title, icon tinted like Nothing's, the tap opening
 * the app) is Nothing's own code.
 */
internal object SettingsTile {

    /** The actions Settings looks tiles up with (TileUtils.loadTilesForAction). */
    private val TILE_ACTIONS = setOf("com.android.settings.action.EXTRA_SETTINGS",
        "com.android.settings.action.IA_SETTINGS")

    @JvmStatic
    fun install(cl: ClassLoader) {
        val pm = XposedHelpers.findClass("android.app.ApplicationPackageManager", cl)
        val hooked = XposedBridge.hookAllMethods(pm, "queryIntentActivitiesAsUser",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val intent = param.args.firstOrNull() as? Intent ?: return
                    if (intent.action !in TILE_ACTIONS) return
                    try {
                        (param.result as? List<*>)?.forEach {
                            val info = it as? ResolveInfo ?: return@forEach
                            if (info.activityInfo?.packageName == Config.MODULE_PKG) {
                                XposedHelpers.setBooleanField(info, "system", true)
                            }
                        }
                    } catch (t: Throwable) {
                        Hooks.log("settings row: $t") // Settings without the row.
                    }
                }
            }).size
        Hooks.log("settings row: $hooked hooks")
    }
}
