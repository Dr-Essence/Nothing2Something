package com.dressence.nothinglauncherplus.system

import android.content.pm.ApplicationInfo
import android.os.UserHandle
import com.dressence.nothinglauncherplus.Config
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/**
 * Lets "Show app icon" off really hide the icon (the app side is [com.dressence.nothinglauncherplus.ui.AppIcon]).
 *
 * The problem: since Android 10 an app cannot simply hide its icon. When an app switches its
 * launcher entry off, the system hands launchers a stand-in entry instead, one that opens the
 * app's info page, so that no app can run hidden from the user.
 *
 * How it was found, in the phone's services.jar: `LauncherAppsService$LauncherAppsImpl
 * .shouldShowSyntheticActivity(UserHandle, ApplicationInfo)` decides it. It answers false for
 * system apps and for managed-profile admins, and true only when the app asks for any
 * permission (`requestsPermissions`) and has a launcher entry that is enabled *in its manifest*
 * (`hasDefaultEnableLauncherActivity`: it queries MAIN/LAUNCHER with MATCH_DISABLED_COMPONENTS
 * and reads ActivityInfo.enabled, the manifest value, so switching the entry off at runtime does
 * not change it). This module asks for permissions and its icon is enabled in the manifest (it
 * must be, or a fresh install would have no icon), so the stand-in would always appear.
 *
 * What it does: that one answer becomes false for this module's own package, and every other
 * app is left to Android's rule. Nothing else changes: while the icon is on, Android never
 * looks for a stand-in, because the app has a real launcher entry.
 */
internal object HiddenAppIcon {

    private const val LAUNCHER_APPS = "com.android.server.pm.LauncherAppsService\$LauncherAppsImpl"

    @JvmStatic
    fun install(cl: ClassLoader) {
        XposedHelpers.findAndHookMethod(LAUNCHER_APPS, cl, "shouldShowSyntheticActivity",
            UserHandle::class.java, ApplicationInfo::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val app = param.args[1] as? ApplicationInfo ?: return
                    if (app.packageName == Config.MODULE_PKG) {
                        param.result = false
                    }
                }
            })
    }
}
