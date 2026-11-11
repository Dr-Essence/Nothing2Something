package com.dressence.nothinglauncherplus.ui;

import android.app.Activity;
import android.widget.Toast;

/**
 * The Diagnostics page's "Capture debug report" and "Stop capture" rows: asks how long to wait
 * and how long to capture, then runs DebugReport with the CaptureIndicator on screen. Moved out
 * of SettingsActivity unchanged (0.70.11, cleanup step 4).
 */
final class ReportSettings {

    /** Report wait and capture times to choose from, in seconds. */
    private static final int[] DELAYS = {10, 15, 30, 60, 120, 300, 600};
    private static final int[] LENGTHS = {15, 30, 60, 120, 300, 600};

    private ReportSettings() {
    }

    private static String duration(int seconds) {
        return seconds < 60 ? seconds + " s" : seconds / 60 + " min";
    }

    private static String[] durations(int[] seconds) {
        String[] labels = new String[seconds.length];
        for (int i = 0; i < seconds.length; i++) {
            labels[i] = duration(seconds[i]);
        }
        return labels;
    }

    /** Asks how long to wait before capturing, then how long to capture, then starts. */
    static void chooseTimes(Activity activity) {
        Dialogs.builder(activity)
                .setTitle("Start capturing after")
                .setItems(durations(DELAYS), (d, delay) -> Dialogs.builder(activity)
                        .setTitle("Capture for")
                        .setItems(durations(LENGTHS), (d2, length) -> capture(activity,
                                DELAYS[delay], LENGTHS[length]))
                        .setNegativeButton("Cancel", null)
                        .show())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void capture(Activity activity, int delay, int length) {
        new Thread(() -> {
            String failure = DebugReport.start(activity, delay, length);
            activity.runOnUiThread(() -> {
                if (failure == null) {
                    CaptureIndicator.start(activity, delay, length);
                }
                Toast.makeText(activity, failure == null
                        ? "Capturing in " + duration(delay) + " for " + duration(length)
                                + ": one buzz when it starts, two when the report is saved"
                        : "Failed: " + failure, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    static void stop(Activity activity) {
        new Thread(() -> {
            String failure = DebugReport.stop();
            activity.runOnUiThread(() -> {
                if (failure == null) {
                    // Straight to "saving": it ends when the report is saved.
                    CaptureIndicator.start(activity, 0, 0);
                }
                Toast.makeText(activity, failure == null
                        ? "Stopping; the report is saved in a few seconds"
                        : "Failed: " + failure, Toast.LENGTH_LONG).show();
            });
        }).start();
    }
}
