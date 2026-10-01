package com.termux.app.boot;

import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import android.util.Log;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.Arrays;

/**
 * Schedule executable scripts under {@code ~/.termux/boot} after BOOT_COMPLETED.
 * Replaces the separate Termux:Boot APK for Apollo worker pads.
 */
public final class BootScriptScheduler {

    public static final int JOB_ID_BASE = 9100;

    private static final String TAG = "ApolloBoot";
    private static int sNextJobId = JOB_ID_BASE;

    private BootScriptScheduler() {}

    @SuppressLint("SdCardPath")
    public static void scheduleBootScripts(Context context) {
        File bootDir = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".termux/boot");
        File[] files = bootDir.listFiles();
        if (files == null) files = new File[0];
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler == null) {
            Log.e(TAG, "JobScheduler unavailable");
            return;
        }

        StringBuilder logMessage = new StringBuilder();
        for (File file : files) {
            if (!file.isFile()) continue;
            ensureReadableExecutable(file);

            if (logMessage.length() > 0) logMessage.append(", ");
            logMessage.append(file.getName());

            PersistableBundle extras = new PersistableBundle();
            extras.putString(BootJobService.SCRIPT_FILE_PATH, file.getAbsolutePath());

            JobInfo job = new JobInfo.Builder(sNextJobId++, new ComponentName(context, BootJobService.class))
                .setExtras(extras)
                .setOverrideDeadline(3_000)
                .build();
            jobScheduler.schedule(job);
        }

        if (logMessage.length() > 0) {
            Log.i(TAG, "Scheduled boot scripts: " + logMessage);
        } else {
            Log.i(TAG, "No files in " + bootDir.getAbsolutePath());
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void ensureReadableExecutable(File file) {
        if (!file.canRead()) file.setReadable(true);
        if (!file.canExecute()) file.setExecutable(true);
    }
}
