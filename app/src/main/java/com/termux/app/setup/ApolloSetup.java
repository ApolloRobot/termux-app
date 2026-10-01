package com.termux.app.setup;

import android.content.Context;
import android.content.SharedPreferences;
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
 * Clipboard is built into the main app (no separate Termux:API APK).
 */
public final class ApolloSetup {

    public static final String PREFS = "apollo_worker_setup";
    public static final String KEY_SETUP_DONE = "setup_done";
    public static final String KEY_BOOT_SCRIPTS_INSTALLED = "boot_scripts_installed";
    public static final String KEY_CLIPBOARD_SHIMS_INSTALLED = "clipboard_shims_installed";

    private static final String TAG = "ApolloSetup";
    private static final String ASSET_BOOT_DIR = "apollo/boot";
    private static final String ASSET_BIN_DIR = "apollo/bin";

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

    /** In-app clipboard shims present under $PREFIX/bin. */
    public static boolean isClipboardReady() {
        File set = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "termux-clipboard-set");
        File get = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "termux-clipboard-get");
        return set.isFile() && set.canExecute() && get.isFile() && get.canExecute();
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
        return installAssetScripts(context, ASSET_BOOT_DIR, bootScriptsDir(), false);
    }

    /**
     * Install/overwrite Apollo clipboard shims into $PREFIX/bin so
     * {@code termux-clipboard-set}/{@code get} work without Termux:API APK.
     * Overwrites so updates ship with the App.
     */
    public static int ensureClipboardShims(Context context) {
        File bin = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
        if (!bin.exists()) {
            Log.w(TAG, "PREFIX/bin not ready yet: " + bin.getAbsolutePath());
            return 0;
        }
        int n = installAssetScripts(context, ASSET_BIN_DIR, bin, true);
        if (n > 0 || isClipboardReady()) {
            prefs(context).edit().putBoolean(KEY_CLIPBOARD_SHIMS_INSTALLED, true).apply();
        }
        return n;
    }

    private static int installAssetScripts(Context context, String assetDir, File outDir, boolean overwrite) {
        if (!outDir.exists() && !outDir.mkdirs()) {
            Log.e(TAG, "Cannot create " + outDir.getAbsolutePath());
            return 0;
        }
        int written = 0;
        try {
            String[] names = context.getAssets().list(assetDir);
            if (names == null) return 0;
            for (String name : names) {
                File out = new File(outDir, name);
                if (out.exists() && !overwrite) continue;
                try (InputStream in = context.getAssets().open(assetDir + "/" + name);
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
                Log.i(TAG, "Installed script: " + out.getAbsolutePath());
            }
            if (ASSET_BOOT_DIR.equals(assetDir) && (written > 0 || hasBootScripts())) {
                prefs(context).edit().putBoolean(KEY_BOOT_SCRIPTS_INSTALLED, true).apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "installAssetScripts " + assetDir + " failed", e);
        }
        return written;
    }

    public static String readinessSummary(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("ADB: ").append(isAdbEnabled(context) ? "OK" : "MISSING").append('\n');
        sb.append("Boot scripts: ").append(hasBootScripts() ? "OK" : "MISSING").append('\n');
        sb.append("Clipboard (in-app): ").append(isClipboardReady() ? "OK" : "MISSING").append('\n');
        sb.append("OpenClaw: ").append(OpenClawBootstrap.isOpenClawInstalled() ? "OK" : "MISSING").append('\n');
        String sn = OpenClawBootstrap.getSavedSn(context);
        if (sn != null && !sn.isEmpty()) sb.append("SN: ").append(sn).append('\n');
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
