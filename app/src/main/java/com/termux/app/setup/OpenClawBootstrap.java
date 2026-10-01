package com.termux.app.setup;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Extract bundled OpenClaw assets and run the Termux-side bootstrap installer.
 */
public final class OpenClawBootstrap {

    private static final String TAG = "OpenClawBootstrap";
    public static final String KEY_SN = "device_sn";
    public static final String KEY_INSTALL_STARTED_AT = "openclaw_install_started_at";

    private OpenClawBootstrap() {}

    public static File assetStagingDir() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".apollo-assets");
    }

    public static File statusFile() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".apollo-logs/openclaw-install.status");
    }

    public static File logFile() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".apollo-logs/openclaw-install.log");
    }

    public static File bootstrapScript() {
        return new File(assetStagingDir(), "30-openclaw-bootstrap.sh");
    }

    public static boolean isOpenClawInstalled() {
        File oc = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, "lib/node_modules/openclaw/openclaw.mjs");
        File sn = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".openclaw/mno_device.json");
        return oc.isFile() && sn.isFile();
    }

    public static String readStatus() {
        return readSmallFile(statusFile());
    }

    public static String readLogTail(int maxChars) {
        String all = readSmallFile(logFile());
        if (all == null) return "";
        if (all.length() <= maxChars) return all;
        return all.substring(all.length() - maxChars);
    }

    public static String getSavedSn(Context context) {
        return ApolloSetup.prefs(context).getString(KEY_SN, "");
    }

    public static void saveSn(Context context, String sn) {
        ApolloSetup.prefs(context).edit().putString(KEY_SN, sn == null ? "" : sn.trim()).apply();
    }

    /**
     * Copy install assets from APK into {@code ~/.apollo-assets} (overwrite).
     */
    public static void stageAssets(Context context) throws Exception {
        File dir = assetStagingDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("mkdir " + dir);
        }
        copyAssetFile(context, "apollo/install/openclaw.json", new File(dir, "openclaw.json"));
        copyAssetFile(context, "apollo/install/30-openclaw-bootstrap.sh", new File(dir, "30-openclaw-bootstrap.sh"));
        // skill tarball
        String[] skillNames = context.getAssets().list("apollo/skill");
        if (skillNames != null) {
            for (String name : skillNames) {
                copyAssetFile(context, "apollo/skill/" + name, new File(dir, name));
            }
        }
        File script = bootstrapScript();
        //noinspection ResultOfMethodCallIgnored
        script.setExecutable(true, false);
    }

    /**
     * Write a wrapper that exports SN and runs the bootstrap, then execute via TermuxService.
     */
    public static void startInstall(Context context, String sn) throws Exception {
        String deviceSn = sn == null ? "" : sn.trim();
        if (deviceSn.isEmpty()) throw new IllegalArgumentException("SN required");

        File prefix = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH);
        if (!prefix.isDirectory()) {
            throw new IllegalStateException("Termux 环境尚未就绪，请先打开一次终端完成 bootstrap");
        }

        saveSn(context, deviceSn);
        stageAssets(context);

        File logs = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".apollo-logs");
        if (!logs.exists()) //noinspection ResultOfMethodCallIgnored
            logs.mkdirs();
        // reset status
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(statusFile()), StandardCharsets.UTF_8)) {
            w.write("QUEUED");
        }

        File wrapper = new File(assetStagingDir(), "run-openclaw-install.sh");
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(wrapper), StandardCharsets.UTF_8)) {
            w.write("#!/data/data/com.termux/files/usr/bin/bash\n");
            w.write("export PREFIX=/data/data/com.termux/files/usr\n");
            w.write("export HOME=/data/data/com.termux/files/home\n");
            w.write("export PATH=$PREFIX/bin:$PATH\n");
            w.write("export APOLLO_SN='" + deviceSn.replace("'", "") + "'\n");
            w.write("export APOLLO_ASSET_DIR='" + assetStagingDir().getAbsolutePath() + "'\n");
            w.write("exec bash '" + bootstrapScript().getAbsolutePath() + "'\n");
        }
        //noinspection ResultOfMethodCallIgnored
        wrapper.setExecutable(true, false);

        ApolloSetup.prefs(context).edit()
            .putLong(KEY_INSTALL_STARTED_AT, System.currentTimeMillis())
            .apply();

        Uri scriptUri = new Uri.Builder()
            .scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
            .path(wrapper.getAbsolutePath())
            .build();
        Intent executeIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, scriptUri);
        executeIntent.setClassName(TermuxConstants.TERMUX_PACKAGE_NAME,
            TermuxConstants.TERMUX_APP.TERMUX_SERVICE_NAME);
        executeIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND, true);
        executeIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, "Apollo OpenClaw Install");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(executeIntent);
        } else {
            context.startService(executeIntent);
        }
        Log.i(TAG, "Started OpenClaw install for SN=" + deviceSn);
    }

    private static void copyAssetFile(Context context, String assetPath, File out) throws Exception {
        try (InputStream in = context.getAssets().open(assetPath);
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        }
        Log.i(TAG, "staged " + out.getAbsolutePath());
    }

    private static String readSmallFile(File f) {
        if (f == null || !f.isFile()) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
        } catch (Exception e) {
            return null;
        }
        return sb.toString();
    }
}
