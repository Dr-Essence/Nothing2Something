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
            val installed = JSONArray()
            for (i in names.indices) {
                val why = reasons.getOrNull(i).orEmpty()
                if (why.isNotEmpty()) problems.put(JSONObject().put("name", names[i]).put("why", why))
                else installed.put(names[i])
            }
            val report = JSONObject()
                .put("package", pkg)
                .put("version", intent.getStringExtra(HookReport.EXTRA_VERSION).orEmpty())
                .put("time", System.currentTimeMillis())
                .put("total", names.size)
                .put("problems", problems)
                .put("installed", installed)
            prefs(context).edit().putString(process, report.toString()).apply()
        } catch (t: Throwable) {
            // A malformed report is dropped.
        }
    }

    /**
     * One app's last report: what [installed], and the [problems] with why. A problem whose
     * reason starts with "skipped: " is a part a feature left out (Hooks.skipped); any other is
     * a feature that did not install (Hooks.install caught it).
     */
    class Report(val process: String, val pkg: String, val version: String, val time: Long,
            val total: Int, val problems: List<Pair<String, String>>,
            val installed: List<String>) {
        val failed get() = problems.filter { !it.second.startsWith(SKIPPED) }
        val skipped get() = problems.filter { it.second.startsWith(SKIPPED) }
            .map { it.first to it.second.removePrefix(SKIPPED) }

        /** The whole report as text: not installed, left out, installed. */
        fun details(): String = buildString {
            if (failed.isNotEmpty()) {
                append("NOT INSTALLED (").append(failed.size).append(")\n")
                failed.forEach { append("✗ ").append(it.first).append("\n   ").append(it.second).append("\n") }
                append("\n")
            }
            if (skipped.isNotEmpty()) {
                append("LEFT OUT (").append(skipped.size).append(")\n")
                skipped.forEach { append("– ").append(it.first).append("\n   ").append(it.second).append("\n") }
                append("\n")
            }
            append("INSTALLED (").append(installed.size).append(")\n")
            if (installed.isEmpty() && total > problems.size) {
                append("(sent by an older version: send a new report by restarting the app)\n")
            }
            installed.sorted().forEach { append("✓ ").append(it).append("\n") }
        }.trim()
    }

    companion object {
        private const val PREFS = "nlplus_hook_health"
        private const val SKIPPED = "skipped: "

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** Every app's last report, those with problems first. */
        @JvmStatic
        fun reports(context: Context): List<Report> = prefs(context).all.mapNotNull { (key, value) ->
            try {
                val o = JSONObject(value as String)
                val list = o.getJSONArray("problems")
                val ok = o.optJSONArray("installed") ?: JSONArray()
                Report(key, o.getString("package"), o.optString("version"), o.optLong("time"),
                    o.optInt("total"), (0 until list.length()).map {
                        list.getJSONObject(it).getString("name") to list.getJSONObject(it).getString("why")
                    }, (0 until ok.length()).map { ok.getString(it) })
            } catch (t: Throwable) {
                null
            }
        }.sortedWith(compareByDescending<Report> { it.problems.size }.thenBy { it.process })
    }
}
