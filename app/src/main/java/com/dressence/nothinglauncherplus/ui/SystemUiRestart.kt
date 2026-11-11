package com.dressence.nothinglauncherplus.ui

import com.dressence.nothinglauncherplus.Root

/**
 * Restarts System UI, so options it reads when it starts (status bar, lock screen) apply now;
 * Android starts it again by itself in a moment.
 *
 * Before that, a "planned restart" note goes into System UI's own folder, where the module's
 * crash-loop guards keep their records (see CrashGuard): a start right after it is not
 * counted, so restarting a few times never turns a feature off. The note is written only if
 * that folder already exists: created by root, it would belong to root and System UI could
 * not use it.
 *
 * Mistake recorded (0.54.1): pressed three times within 30 s, it put LSPosed into safe mode.
 * LSPosed sees System UI (a persistent process) dying three times in a row as a crash loop;
 * it cannot tell a restart from a crash (seen on the phone: three deaths, no crash report,
 * then LSPosed's safe-mode notification). So it restarts at most once every [COOLDOWN_MS].
 */
internal object SystemUiRestart {

    private const val COOLDOWN_MS = 90_000L
    /**
     * When System UI was last restarted from here (wall clock ms), kept in this app's own
     * prefs: held only in memory, it was forgotten whenever the app was swiped away, and two
     * restarts in a row were possible again (0.70.14 audit).
     */
    private const val PREFS = "nlplus_systemui_restart"
    private const val LAST = "last"

    private fun prefs(context: android.content.Context) =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    /** Seconds until the next restart is allowed (0: now). */
    @JvmStatic
    fun waitSeconds(context: android.content.Context): Long {
        val last = prefs(context).getLong(LAST, 0L)
        // Clamped: a clock set back never makes it wait longer than the cooldown.
        val left = (COOLDOWN_MS - (System.currentTimeMillis() - last)).coerceAtMost(COOLDOWN_MS)
        return if (last == 0L || left <= 0) 0 else (left + 999) / 1000
    }


    private const val DIR = "/data/user_de/0/com.android.systemui/files"
    private const val NOTE = "nlplus_planned_restart" // CrashGuard.PLANNED

    /** True if System UI was restarted; false without root. Call off the main thread. */
    @JvmStatic
    fun restart(context: android.content.Context): Boolean {
        if (waitSeconds(context) > 0) return false
        val done = Root.run(
            "[ -d $DIR ] && echo \"\$(date +%s)000\" > $DIR/$NOTE; pkill -f com.android.systemui"
        ) == null
        // Only a restart that happened starts the cooldown: with root refused it was set all
        // the same, kept across app restarts since 0.70.16 (found in the same audit).
        if (done) prefs(context).edit().putLong(LAST, System.currentTimeMillis()).commit()
        return done
    }
}
