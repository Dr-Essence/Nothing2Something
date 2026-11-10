package com.dressence.nothinglauncherplus.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The module's icon in the app drawer: shown or hidden.
 *
 * The icon is its own manifest entry (the `.ui.MainActivity` alias), so switching it off leaves
 * the settings reachable from LSPosed, the Nothing2Something row in Settings and Settings
 * search. The choice is kept by Android itself (the entry's enabled state), not in the module's
 * settings, so there is nothing to keep in step and a backup cannot bring back a stale value.
 *
 * Hiding needs the module active in the system ("android" scope, [com.dressence.nothinglauncherplus.system.HiddenAppIcon]):
 * without it, Android shows a stand-in icon that opens the app's info page.
 */
internal object AppIcon {

    /** The alias's name, kept from before 0.67.0 so home-screen icons stay valid. */
    private const val ALIAS = ".ui.MainActivity"

    private fun component(context: Context) =
        ComponentName(context.packageName, context.packageName + ALIAS)

    @JvmStatic
    fun shown(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /**
     * Shows or hides the icon. DONT_KILL_APP: without it Android stops this app, closing the
     * page the switch is on. "Shown" goes back to the manifest's default rather than forcing
     * enabled, so the manifest stays the one source of the default.
     */
    @JvmStatic
    fun setShown(context: Context, shown: Boolean) {
        val state = if (shown) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        context.packageManager.setComponentEnabledSetting(component(context), state,
            PackageManager.DONT_KILL_APP)
    }
}
