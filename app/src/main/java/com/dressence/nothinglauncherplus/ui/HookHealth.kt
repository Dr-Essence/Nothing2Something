package com.dressence.nothinglauncherplus.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dressence.nothinglauncherplus.xposed.HookReport
import org.json.JSONArray
import org.json.JSONObject

/**
 * The module app's side of the hook health report ([HookReport]): keeps the last report of
 * each hooked app (by process) in this app's own storage, and reads them back for the
 * Diagnostics page.
 */
class HookHealth : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val pkg = intent.getStringExtra(HookReport.EXTRA_PACKAGE) ?: return
            // From that app itself (Android tells who sent it), so no other app can write here.
            if (sentFromPackage != pkg) return
            val process = intent.getStringExtra(HookReport.EXTRA_PROCESS) ?: pkg
            val names = intent.getStringArrayExtra(HookReport.EXTRA_NAMES) ?: return
            val reasons = intent.getStringArrayExtra(HookReport.EXTRA_REASONS) ?: return
            val problems = JSONArray()
            for (i in names.indices) {
                val why = reasons.getOrNull(i).orEmpty()
                if (why.isNotEmpty()) problems.put(JSONObject().put("name", names[i]).put("why", why))
            }
            val report = JSONObject()
                .put("package", pkg)
                .put("version", intent.getStringExtra(HookReport.EXTRA_VERSION).orEmpty())
                .put("time", System.currentTimeMillis())
                .put("total", names.size)
                .put("problems", problems)
            prefs(context).edit().putString(process, report.toString()).apply()
        } catch (t: Throwable) {
            // A malformed report is dropped.
        }
    }

    /** One app's last report. */
    class Report(val process: String, val pkg: String, val version: String, val time: Long,
            val total: Int, val problems: List<Pair<String, String>>)

    companion object {
        private const val PREFS = "nlplus_hook_health"

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** Every app's last report, those with problems first. */
        @JvmStatic
        fun reports(context: Context): List<Report> = prefs(context).all.mapNotNull { (key, value) ->
            try {
                val o = JSONObject(value as String)
                val list = o.getJSONArray("problems")
                Report(key, o.getString("package"), o.optString("version"), o.optLong("time"),
                    o.optInt("total"), (0 until list.length()).map {
                        list.getJSONObject(it).getString("name") to list.getJSONObject(it).getString("why")
                    })
            } catch (t: Throwable) {
                null
            }
        }.sortedWith(compareByDescending<Report> { it.problems.size }.thenBy { it.process })
    }
}
