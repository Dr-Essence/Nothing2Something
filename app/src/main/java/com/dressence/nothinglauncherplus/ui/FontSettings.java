package com.dressence.nothinglauncherplus.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.dressence.nothinglauncherplus.Config;
import com.dressence.nothinglauncherplus.UpdateReceiver;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings' "System font" row: lists Nothing's fonts on the phone, each previewed on a
 * dot-matrix screen in glowing green, and sets the chosen one as the system font. Moved out of
 * SettingsActivity unchanged (0.70.11, cleanup step 4).
 */
final class FontSettings {

    private final Activity activity;
    private final SettingsStore store;

    FontSettings(Activity activity, SettingsStore store) {
        this.activity = activity;
        this.store = store;
    }

    /** Finds the fonts off the main thread, then shows the chooser. */
    void choose() {
        new Thread(() -> {
            List<SystemFont.Choice> fonts = new ArrayList<>();
            fonts.add(null); // The phone's own font.
            fonts.addAll(SystemFont.find(activity));
            List<Typeface> faces = new ArrayList<>();
            List<Boolean> lowercase = new ArrayList<>();
            for (SystemFont.Choice font : fonts) {
                Typeface face = SystemFont.typeface(activity, font);
                faces.add(face);
                lowercase.add(face == null || FontPreview.hasLowercase(face));
            }
            activity.runOnUiThread(() -> showChooser(fonts, faces, lowercase));
        }).start();
    }

    private void showChooser(List<SystemFont.Choice> fonts, List<Typeface> faces,
            List<Boolean> lowercase) {
        // Destroyed too: turned while the fonts loaded, the old screen is destroyed without
        // finishing, and a dialog shown on it throws (0.70.14 audit).
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(activity, 20);
        list.setPadding(pad, Ui.dp(activity, 8), pad, Ui.dp(activity, 8));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(list);
        AlertDialog dialog = Dialogs.builder(activity)
                .setTitle("System font")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .create();
        for (int i = 0; i < fonts.size(); i++) {
            SystemFont.Choice font = fonts.get(i);
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, Ui.dp(activity, 10), 0, Ui.dp(activity, 10));
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), null,
                    new ColorDrawable(Color.WHITE)));
            TextView title = new TextView(activity);
            title.setText(font == null ? "Phone default" : font.title
                    + (lowercase.get(i) ? "" : "  · capitals only")
                    + (font.needsAdding() ? "  · after one restart" : ""));
            title.setTextColor(Ui.TEXT);
            title.setTextSize(15);
            title.setPadding(0, 0, 0, Ui.dp(activity, 6));
            row.addView(title);
            if (faces.get(i) != null) {
                FontPreview preview = new FontPreview(activity);
                preview.setTypeface(faces.get(i));
                row.addView(preview, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            row.setOnClickListener(v -> {
                dialog.dismiss();
                apply(font);
            });
            list.addView(row);
        }
        dialog.show();
    }

    /**
     * Sets the font at once, no reboot: the system gives it to every app from its next start
     * (System Framework in the module's scope), and System UI and the launcher are restarted
     * now (just those two, a second each) to show it.
     */
    private void apply(SystemFont.Choice choice) {
        if (choice != null && choice.needsAdding()) {
            add(choice);
            return;
        }
        // A font file by now: one with no path needs adding, and add() handles it above.
        store.config.systemFont = choice == null ? "" : "file:" + choice.path;
        store.save(activity);
        com.dressence.nothinglauncherplus.Broadcasts.send(activity,
                new android.content.Intent(Config.ACTION_FONT).setPackage("android")
                        .putExtra(Config.EXTRA_SAVED, store.config.savedAt));
        new Thread(() -> {
            SystemFont.removeOldModule();
            // Through SystemUiRestart, the one way System UI is restarted: it writes the
            // crash guard's "planned restart" note and allows one restart every 90 s. (Mistake
            // recorded, up to 0.65.2: the font had its own restart with neither, so choosing a
            // few fonts in a row could put LSPosed into safe mode, as in 0.54.1.)
            long wait = SystemUiRestart.waitSeconds(activity);
            boolean restarted = wait == 0 && SystemUiRestart.restart(activity);
            UpdateReceiver.restartLauncher();
            String name = choice == null ? "Phone font back" : choice.title + " set";
            String status = restarted ? ": status bar and launcher now use it."
                    : wait > 0 ? ": the launcher uses it now, the status bar after the next"
                            + " System UI restart (allowed again in " + wait + " s)."
                    : ": the launcher uses it now; root not available for the status bar.";
            activity.runOnUiThread(() -> Toast.makeText(activity, name + status
                    + " Other apps use it when they next open.", Toast.LENGTH_LONG).show());
        }).start();
    }

    /**
     * A font only Nothing's widgets app has: added to the system's fonts as a file (shown after
     * one restart of the phone), and chosen, so it is the system font from that restart on.
     */
    private void add(SystemFont.Choice choice) {
        new Thread(() -> {
            String error = SystemFont.addToSystem(activity, choice);
            activity.runOnUiThread(() -> {
                if (error != null) {
                    Toast.makeText(activity, "Could not add " + choice.title + ": " + error,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                store.config.systemFont = "file:" + SystemFont.addedPath(choice);
                store.save(activity);
                Toast.makeText(activity, choice.title + " added. Restart the phone once: it is"
                        + " the system font from then on.", Toast.LENGTH_LONG).show();
            });
        }).start();
    }
}
