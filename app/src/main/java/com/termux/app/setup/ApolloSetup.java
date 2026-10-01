package com.termux.app.setup;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * First-run / readiness helpers for Apollo worker pads.
 * ADB is a hard requirement: Termux drives other apps through adb.
 */
public final class ApolloSetup {

    public static final String PREFS = "apollo_worker_setup";
    public static final String KEY_SETUP_DONE = "setup_done";
    public static final String KEY_BOOT_SCRIPTS_INSTALLED = "boot_scripts_installed";

    private static final String TAG = "ApolloSetup";
    private static final String ASSET_BOOT_DIR = "apollo/boot";

    private ApolloSetup() {}

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isSetupDone(Context context) {
        return prefs(context).getBoolean(KEY_SETUP_DONE, false);
    }

    public static void setSetupDone(Context context, boolean done) {
        prefs(context).edit().putBoolean(KEY_SETUP_DONE, done).apply();
    }

    /** USB debugging (adb) enabled — required for app automation. */
    public static boolean isAdbEnabled(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
        } catch (Exception e) {
            Log.w(TAG, "ADB check failed", e);
            return false;
        }
    }

    public static boolean isTermuxApiInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(TermuxConstants.TERMUX_API_PACKAGE_NAME, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static File bootScriptsDir() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".termux/boot");
    }

    public static boolean hasBootScripts() {
        File dir = bootScriptsDir();
        File[] files = dir.listFiles(File::isFile);
        return files != null && files.length > 0;
    }

    /**
     * Install default boot scripts from assets if missing.
     * Does not overwrite existing user scripts with the same name.
     */
    public static int ensureDefaultBootScripts(Context context) {
        File dir = bootScriptsDir();
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Cannot create " + dir.getAbsolutePath());
            return 0;
        }

        int written = 0;
        try {
            String[] names = context.getAssets().list(ASSET_BOOT_DIR);
            if (names == null) return 0;
            for (String name : names) {
                File out = new File(dir, name);
                if (out.exists()) continue;
                try (InputStream in = context.getAssets().open(ASSET_BOOT_DIR + "/" + name);
                     FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                }
                //noinspection ResultOfMethodCallIgnored
                out.setExecutable(true, false);
                //noinspection ResultOfMethodCallIgnored
                out.setReadable(true, false);
                written++;
                Log.i(TAG, "Installed boot script: " + out.getAbsolutePath());
            }
            if (written > 0 || hasBootScripts()) {
                prefs(context).edit().putBoolean(KEY_BOOT_SCRIPTS_INSTALLED, true).apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "ensureDefaultBootScripts failed", e);
        }
        return written;
    }

    public static String readinessSummary(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("ADB: ").append(isAdbEnabled(context) ? "OK" : "MISSING").append('\n');
        sb.append("Boot scripts: ").append(hasBootScripts() ? "OK" : "MISSING").append('\n');
        sb.append("Termux:API (clipboard): ").append(isTermuxApiInstalled(context) ? "OK" : "MISSING").append('\n');
        return sb.toString();
    }

    /** Small helper for writing a one-off script text (tests / debug). */
    public static void writeBootScript(String fileName, String content) throws Exception {
        File dir = bootScriptsDir();
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdir " + dir);
        File out = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        //noinspection ResultOfMethodCallIgnored
        out.setExecutable(true, false);
    }
}
