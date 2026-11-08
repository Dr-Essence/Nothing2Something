package com.dressence.nothinglauncherplus.ui

import com.dressence.nothinglauncherplus.perf.Device

/**
 * Restarts System UI, so options it reads when it starts (status bar, lock screen) apply now;
 * Android starts it again by itself in a moment.
 *
 * Before that, a "planned restart" note goes into System UI's own folder, where the module's
 * crash-loop guards keep their records (see CrashGuard): a start right after it is not
 * counted, so restarting a few times never turns a feature off. The note is written only if
 * that folder already exists: created by root, it would belong to root and System UI could
 * not use it.
 */
internal object SystemUiRestart {

    private const val DIR = "/data/user_de/0/com.android.systemui/files"
    private const val NOTE = "nlplus_planned_restart" // CrashGuard.PLANNED

    /** True if System UI was restarted; false without root. Call off the main thread. */
    @JvmStatic
    fun restart(): Boolean = Device.su(
        "[ -d $DIR ] && echo \"\$(date +%s)000\" > $DIR/$NOTE; pkill -f com.android.systemui"
    ) == null
}
