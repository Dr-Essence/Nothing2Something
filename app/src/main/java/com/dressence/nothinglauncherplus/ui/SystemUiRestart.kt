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
    @Volatile private var last = 0L

    /** Seconds until the next restart is allowed (0: now). */
    @JvmStatic
    fun waitSeconds(): Long {
        val left = COOLDOWN_MS - (android.os.SystemClock.elapsedRealtime() - last)
        return if (last == 0L || left <= 0) 0 else (left + 999) / 1000
    }


    private const val DIR = "/data/user_de/0/com.android.systemui/files"
    private const val NOTE = "nlplus_planned_restart" // CrashGuard.PLANNED

    /** True if System UI was restarted; false without root. Call off the main thread. */
    @JvmStatic
    fun restart(): Boolean {
        if (waitSeconds() > 0) return false
        last = android.os.SystemClock.elapsedRealtime()
        return Root.run(
            "[ -d $DIR ] && echo \"\$(date +%s)000\" > $DIR/$NOTE; pkill -f com.android.systemui"
        ) == null
    }
}
