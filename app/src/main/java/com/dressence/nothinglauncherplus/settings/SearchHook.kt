package com.dressence.nothinglauncherplus.settings

import android.content.Context
import android.content.pm.ResolveInfo
import android.net.Uri
import com.dressence.nothinglauncherplus.search.SearchIndexProvider
import com.dressence.nothinglauncherplus.xposed.Hooks
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/**
 * In Settings Intelligence (Nothing's Settings search): the module's settings become
 * searchable.
 *
 * Found in Nothing's Settings Intelligence (its class names are not obfuscated):
 * - `search.indexing.PreIndexDataCollector.isWellKnownProvider(ResolveInfo)` takes a search
 *   provider only if it is protected by `READ_SEARCH_INDEXABLES` and its app is a system app
 *   (`ApplicationInfo.FLAG_SYSTEM`). This module is not; the check is answered true for its
 *   own provider, with the permission still required, so nothing else changes.
 * - `search.indexing.IndexDatabaseHelper.buildProviderVersionedNames(Context, List)` makes the
 *   string it compares with the last indexing (each provider's package and version); a
 *   different string reads every provider again. The module's list changes without a new
 *   version (a setting shown or hidden, a folder added), so its stamp is added to the string.
 */
object SearchHook {

    private const val PERMISSION = "android.permission.READ_SEARCH_INDEXABLES"
    private const val PKG = "com.dressence.nothinglauncherplus"
    private const val INDEXING = "com.android.settings.intelligence.search.indexing"

    @JvmStatic
    fun install(cl: ClassLoader) {
        XposedHelpers.findAndHookMethod("$INDEXING.PreIndexDataCollector", cl,
            "isWellKnownProvider", ResolveInfo::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.result == true) return
                    val info = (param.args[0] as? ResolveInfo)?.providerInfo ?: return
                    if (info.packageName == PKG && info.authority == SearchIndexProvider.AUTHORITY
                        && info.readPermission == PERMISSION
                        && info.writePermission == PERMISSION) {
                        param.result = true
                    }
                }
            })
        XposedHelpers.findAndHookMethod("$INDEXING.IndexDatabaseHelper", cl,
            "buildProviderVersionedNames", Context::class.java, List::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val names = param.result as? String ?: return
                    val context = param.args[0] as Context
                    param.result = "$names,n2s-index:${stamp(context)}"
                }
            })
        Hooks.log("settings search: module settings searchable (2 hooks)")
    }

    /** The module's list stamp, from its provider; "none" if it cannot be read. */
    private fun stamp(context: Context): String = try {
        context.contentResolver.query(
            Uri.parse("content://${SearchIndexProvider.AUTHORITY}/settings/" +
                SearchIndexProvider.STAMP), null, null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else "none"
        } ?: "none"
    } catch (t: Throwable) {
        "none"
    }
}
