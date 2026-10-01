package com.termux.app.api;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * In-app clipboard API (replaces separate Termux:API APK for our worker use-case).
 *
 * Supports:
 * 1. Official termux-api protocol via {@link ResultReturner} (SocketListener / libexec).
 * 2. Apollo simple mode: {@code --ez apollo_simple true --ez set true --es file /path}
 *    used by bundled {@code termux-clipboard-set}/{@code get} shims.
 */
public class ClipboardApiReceiver extends BroadcastReceiver {

    private static final String TAG = "ClipboardApiReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        ResultReturner.setContext(context.getApplicationContext());

        // Apollo simple path — no unix sockets required (Android 14 friendly)
        if (intent.getBooleanExtra("apollo_simple", false)) {
            handleSimple(context, intent);
            return;
        }

        String apiMethod = intent.getStringExtra("api_method");
        if (apiMethod == null) apiMethod = "Clipboard";
        if (!"Clipboard".equals(apiMethod)) {
            Log.w(TAG, "Unsupported api_method: " + apiMethod);
            ResultReturner.noteDone(this, intent);
            return;
        }

        handleOfficial(context, intent);
    }

    private void handleOfficial(Context context, Intent intent) {
        final ClipboardManager clipboard =
            (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            ResultReturner.noteDone(this, intent);
            return;
        }

        final ClipData clipData = clipboard.getPrimaryClip();
        boolean version2 = "2".equals(intent.getStringExtra("api_version"));

        if (version2) {
            boolean set = intent.getBooleanExtra("set", false);
            if (set) {
                ResultReturner.returnData(this, intent, new ResultReturner.WithStringInput() {
                    @Override
                    protected boolean trimInput() {
                        return false;
                    }

                    @Override
                    public void writeResult(PrintWriter out) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", inputString));
                    }
                });
            } else {
                ResultReturner.returnData(this, intent, out -> {
                    if (clipData == null) {
                        out.print("");
                    } else {
                        int itemCount = clipData.getItemCount();
                        for (int i = 0; i < itemCount; i++) {
                            CharSequence text = clipData.getItemAt(i).coerceToText(context);
                            if (!TextUtils.isEmpty(text)) out.print(text);
                        }
                    }
                });
            }
            return;
        }

        final String newClipText = intent.getStringExtra("text");
        if (newClipText != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("", newClipText));
        }
        ResultReturner.returnData(this, intent, out -> {
            if (newClipText == null) {
                if (clipData == null) {
                    out.print("");
                } else {
                    int itemCount = clipData.getItemCount();
                    for (int i = 0; i < itemCount; i++) {
                        CharSequence text = clipData.getItemAt(i).coerceToText(context);
                        if (!TextUtils.isEmpty(text)) out.print(text);
                    }
                }
            }
        });
    }

    private void handleSimple(Context context, Intent intent) {
        try {
            ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return;

            boolean set = intent.getBooleanExtra("set", false);
            String donePath = intent.getStringExtra("donefile");
            if (set) {
                String text = intent.getStringExtra("text");
                String filePath = intent.getStringExtra("file");
                if (filePath != null && !filePath.isEmpty()) {
                    text = readFile(filePath);
                }
                if (text == null) text = "";
                clipboard.setPrimaryClip(ClipData.newPlainText("", text));
                Log.i(TAG, "apollo_simple set ok, bytes=" + text.length());
            } else {
                String outPath = intent.getStringExtra("outfile");
                StringBuilder sb = new StringBuilder();
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null) {
                    for (int i = 0; i < clip.getItemCount(); i++) {
                        CharSequence t = clip.getItemAt(i).coerceToText(context);
                        if (!TextUtils.isEmpty(t)) sb.append(t);
                    }
                }
                if (outPath != null && !outPath.isEmpty()) {
                    writeFile(outPath, sb.toString());
                }
                Log.i(TAG, "apollo_simple get ok, bytes=" + sb.length());
            }
            if (donePath != null && !donePath.isEmpty()) {
                writeFile(donePath, "ok");
            }
        } catch (Exception e) {
            Log.e(TAG, "apollo_simple failed", e);
        }
    }

    private static String readFile(String path) throws Exception {
        File f = new File(path);
        // Only allow Termux home/tmp/prefix paths
        String abs = f.getCanonicalPath();
        if (!(abs.startsWith("/data/data/com.termux/")
            || abs.startsWith("/data/user/0/com.termux/"))) {
            throw new SecurityException("clipboard file outside termux data: " + abs);
        }
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) Math.min(f.length(), 8 * 1024 * 1024)];
            int n = in.read(buf);
            return n <= 0 ? "" : new String(buf, 0, n, StandardCharsets.UTF_8);
        }
    }

    private static void writeFile(String path, String content) throws Exception {
        File f = new File(path);
        String abs = f.getCanonicalPath();
        if (!(abs.startsWith("/data/data/com.termux/")
            || abs.startsWith("/data/user/0/com.termux/"))) {
            throw new SecurityException("clipboard outfile outside termux data: " + abs);
        }
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
