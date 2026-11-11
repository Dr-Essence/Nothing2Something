package com.dressence.nothinglauncherplus.perf;

import com.dressence.nothinglauncherplus.Root;

import android.os.Build;

/**
 * The performance features are for the CMF Phone 1 only (MediaTek MT6878, its sensors and
 * clocks as read from the phone): this says whether the module runs on one, and runs root
 * commands for them.
 */
public final class Device {

    /** ro.product.device of the CMF Phone 1 ("Tetris", model A015). */
    private static final String CMF_PHONE_1 = "Tetris";
    /** Where Magisk / KernelSU keep the PerfMTK module. */
    private static final String PERFMTK_MODULE = "/data/adb/modules/perfmtk";

    private Device() {
    }

    public static boolean isCmfPhone1() {
        return CMF_PHONE_1.equals(Build.DEVICE);
    }

    /** Whether PerfMTK is installed (its module folder is readable only as root). */
    public static boolean hasPerfMtk() {
        return Root.run("[ -d " + PERFMTK_MODULE + " ] && [ ! -e " + PERFMTK_MODULE + "/disable ]")
                == null;
    }

}
