package com.dressence.nothinglauncherplus.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * One hooked app's hook health report on a page of its own (opened from Diagnostics → Hook
 * health, where each app is one row): what did not install and why, what was left out and
 * why, and every feature that installed.
 */
class HookHealthActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val process = intent.getStringExtra(EXTRA_PROCESS)
        val report = HookHealth.reports(this).firstOrNull { it.process == process }
        if (report == null) {
            finish()
            return
        }
        val ui = Ui.page(this, report.process, true)
        val time = java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(report.time))
        ui.note((if (report.version.isEmpty()) "" else "Version ${report.version} · ") +
            "${report.total - report.problems.size} of ${report.total} installed · reported $time")

        if (report.failed.isNotEmpty()) {
            ui.section("Not installed · ${report.failed.size}")
            for ((name, why) in report.failed) ui.row("✗ $name", why, null, null, null)
        }
        if (report.skipped.isNotEmpty()) {
            ui.section("Left out · ${report.skipped.size}")
            for ((name, why) in report.skipped) ui.row("– $name", why, null, null, null)
        }
        ui.section("Installed · ${report.installed.size}")
        if (report.installed.isEmpty() && report.total > report.problems.size) {
            ui.note("Sent by an older version of the module: restart this app for a full list.")
        }
        for (name in report.installed.sorted()) ui.row("✓ $name", null, null, null, null)

        ui.section("Share")
        ui.row("Copy this report", "To paste into a message", null, null) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(
                "Hook health", "${report.process} ${report.version}\n\n${report.details()}"))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val EXTRA_PROCESS = "process"

        @JvmStatic
        fun open(context: Context, process: String) {
            context.startActivity(Intent(context, HookHealthActivity::class.java)
                .putExtra(EXTRA_PROCESS, process))
        }
    }
}
