package com.termux.app.setup;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;

/**
 * First-run wizard. ADB is a hard gate; OpenClaw is installed in-app after SN is set.
 */
public class SetupActivity extends AppCompatActivity {

    private static final String LOG_TAG = "SetupActivity";

    private TextView mStatus;
    private TextView mDetail;
    private TextView mLog;
    private EditText mSnInput;
    private Button mPrimary;
    private Button mSecondary;
    private Button mSkipTerminal;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mPollInstall;

    private enum Step {
        WELCOME,
        BATTERY,
        ADB,
        BOOT_SCRIPTS,
        CLIPBOARD_API,
        OPENCLAW,
        DONE
    }

    private Step mStep = Step.WELCOME;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apollo_setup);

        mStatus = findViewById(R.id.apollo_setup_status);
        mDetail = findViewById(R.id.apollo_setup_detail);
        mLog = findViewById(R.id.apollo_setup_log);
        mSnInput = findViewById(R.id.apollo_setup_sn);
        mPrimary = findViewById(R.id.apollo_setup_primary);
        mSecondary = findViewById(R.id.apollo_setup_secondary);
        mSkipTerminal = findViewById(R.id.apollo_setup_open_terminal);

        mPrimary.setOnClickListener(v -> onPrimary());
        mSecondary.setOnClickListener(v -> onSecondary());
        mSkipTerminal.setOnClickListener(v -> openTerminal(false));

        String savedSn = OpenClawBootstrap.getSavedSn(this);
        if (savedSn != null && !savedSn.isEmpty()) mSnInput.setText(savedSn);

        if (ApolloSetup.isSetupDone(this) && ApolloSetup.isAdbEnabled(this)
            && OpenClawBootstrap.isOpenClawInstalled()) {
            openTerminal(true);
            return;
        }
        if (ApolloSetup.isSetupDone(this) && !ApolloSetup.isAdbEnabled(this)) {
            mStep = Step.ADB;
            ApolloSetup.setSetupDone(this, false);
        } else if (ApolloSetup.isAdbEnabled(this) && !OpenClawBootstrap.isOpenClawInstalled()) {
            // Continue from OpenClaw if earlier steps done
            String st = OpenClawBootstrap.readStatus();
            if (st != null && (st.startsWith("RUNNING") || st.startsWith("QUEUED") || st.startsWith("OK"))) {
                mStep = Step.OPENCLAW;
            }
        }
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onDestroy() {
        stopPoll();
        super.onDestroy();
    }

    private void onPrimary() {
        switch (mStep) {
            case WELCOME:
                mStep = Step.BATTERY;
                break;
            case BATTERY:
                requestIgnoreBatteryOptimizations();
                mStep = Step.ADB;
                break;
            case ADB:
                if (!ApolloSetup.isAdbEnabled(this)) {
                    openDeveloperSettings();
                    Toast.makeText(this, R.string.apollo_setup_adb_required_toast, Toast.LENGTH_LONG).show();
                    return;
                }
                mStep = Step.BOOT_SCRIPTS;
                break;
            case BOOT_SCRIPTS:
                ApolloSetup.ensureDefaultBootScripts(this);
                mStep = Step.CLIPBOARD_API;
                break;
            case CLIPBOARD_API:
                ApolloSetup.ensureClipboardShims(this);
                mStep = Step.OPENCLAW;
                break;
            case OPENCLAW:
                if (OpenClawBootstrap.isOpenClawInstalled()
                    || "OK".equals(statusToken(OpenClawBootstrap.readStatus()))) {
                    mStep = Step.DONE;
                    break;
                }
                String sn = mSnInput.getText() == null ? "" : mSnInput.getText().toString().trim();
                if (sn.isEmpty()) {
                    Toast.makeText(this, R.string.apollo_setup_sn_required, Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    OpenClawBootstrap.startInstall(this, sn);
                    Toast.makeText(this, R.string.apollo_setup_openclaw_started, Toast.LENGTH_LONG).show();
                    startPoll();
                } catch (Exception e) {
                    Logger.logError(LOG_TAG, "OpenClaw install failed to start: " + e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
                break;
            case DONE:
                if (!ApolloSetup.isAdbEnabled(this)) {
                    mStep = Step.ADB;
                    Toast.makeText(this, R.string.apollo_setup_adb_required_toast, Toast.LENGTH_LONG).show();
                    break;
                }
                ApolloSetup.ensureDefaultBootScripts(this);
                ApolloSetup.ensureClipboardShims(this);
                ApolloSetup.setSetupDone(this, true);
                openTerminal(true);
                return;
        }
        render();
    }

    private void onSecondary() {
        switch (mStep) {
            case BATTERY:
                mStep = Step.ADB;
                break;
            case ADB:
                openDeveloperSettings();
                return;
            case OPENCLAW:
                // refresh status / retry install
                render();
                return;
            case DONE:
                mStep = Step.WELCOME;
                ApolloSetup.setSetupDone(this, false);
                break;
            default:
                break;
        }
        render();
    }

    private void render() {
        mSecondary.setVisibility(View.VISIBLE);
        mSkipTerminal.setVisibility(ApolloSetup.isSetupDone(this) ? View.VISIBLE : View.GONE);
        mSnInput.setVisibility(View.GONE);
        mLog.setVisibility(View.GONE);
        stopPoll();

        switch (mStep) {
            case WELCOME:
                mStatus.setText(R.string.apollo_setup_welcome_title);
                mDetail.setText(R.string.apollo_setup_welcome_body);
                mPrimary.setText(R.string.apollo_setup_next);
                mSecondary.setVisibility(View.GONE);
                break;
            case BATTERY:
                mStatus.setText(R.string.apollo_setup_battery_title);
                mDetail.setText(R.string.apollo_setup_battery_body);
                mPrimary.setText(R.string.apollo_setup_battery_action);
                mSecondary.setText(R.string.apollo_setup_skip);
                break;
            case ADB:
                boolean adb = ApolloSetup.isAdbEnabled(this);
                mStatus.setText(adb ? R.string.apollo_setup_adb_ok_title : R.string.apollo_setup_adb_title);
                mDetail.setText(adb ? R.string.apollo_setup_adb_ok_body : R.string.apollo_setup_adb_body);
                mPrimary.setText(adb ? R.string.apollo_setup_next : R.string.apollo_setup_adb_open_settings);
                mSecondary.setText(R.string.apollo_setup_adb_open_settings);
                mSecondary.setVisibility(adb ? View.GONE : View.VISIBLE);
                break;
            case BOOT_SCRIPTS:
                ApolloSetup.ensureDefaultBootScripts(this);
                mStatus.setText(R.string.apollo_setup_boot_title);
                mDetail.setText(getString(R.string.apollo_setup_boot_body,
                    ApolloSetup.bootScriptsDir().getAbsolutePath()));
                mPrimary.setText(R.string.apollo_setup_next);
                mSecondary.setVisibility(View.GONE);
                break;
            case CLIPBOARD_API:
                ApolloSetup.ensureClipboardShims(this);
                boolean clipOk = ApolloSetup.isClipboardReady();
                mStatus.setText(clipOk ? R.string.apollo_setup_clipboard_ok_title : R.string.apollo_setup_clipboard_title);
                mDetail.setText(clipOk ? R.string.apollo_setup_clipboard_ok_body : R.string.apollo_setup_clipboard_body);
                mPrimary.setText(R.string.apollo_setup_next);
                mSecondary.setVisibility(View.GONE);
                break;
            case OPENCLAW:
                mSnInput.setVisibility(View.VISIBLE);
                mLog.setVisibility(View.VISIBLE);
                String st = OpenClawBootstrap.readStatus();
                String token = statusToken(st);
                boolean installed = OpenClawBootstrap.isOpenClawInstalled() || "OK".equals(token);
                if ("RUNNING".equals(token) || "QUEUED".equals(token)) {
                    mStatus.setText(R.string.apollo_setup_openclaw_running_title);
                    mDetail.setText(R.string.apollo_setup_openclaw_running_body);
                    mPrimary.setText(R.string.apollo_setup_openclaw_wait);
                    mPrimary.setEnabled(false);
                    mSecondary.setText(R.string.apollo_setup_refresh);
                    startPoll();
                } else if (installed) {
                    mStatus.setText(R.string.apollo_setup_openclaw_ok_title);
                    mDetail.setText(getString(R.string.apollo_setup_openclaw_ok_body,
                        OpenClawBootstrap.getSavedSn(this)));
                    mPrimary.setEnabled(true);
                    mPrimary.setText(R.string.apollo_setup_next);
                    mSecondary.setVisibility(View.GONE);
                } else if ("FAILED".equals(token)) {
                    mStatus.setText(R.string.apollo_setup_openclaw_fail_title);
                    mDetail.setText(R.string.apollo_setup_openclaw_fail_body);
                    mPrimary.setEnabled(true);
                    mPrimary.setText(R.string.apollo_setup_openclaw_install);
                    mSecondary.setText(R.string.apollo_setup_refresh);
                } else {
                    mStatus.setText(R.string.apollo_setup_openclaw_title);
                    mDetail.setText(R.string.apollo_setup_openclaw_body);
                    mPrimary.setEnabled(true);
                    mPrimary.setText(R.string.apollo_setup_openclaw_install);
                    mSecondary.setVisibility(View.GONE);
                }
                mLog.setText(OpenClawBootstrap.readLogTail(1200));
                break;
            case DONE:
                mStatus.setText(R.string.apollo_setup_done_title);
                mDetail.setText(ApolloSetup.readinessSummary(this) + "\n\n" + getString(R.string.apollo_setup_done_body));
                mPrimary.setEnabled(true);
                mPrimary.setText(R.string.apollo_setup_enter);
                mSecondary.setText(R.string.apollo_setup_rerun);
                break;
        }
    }

    private void startPoll() {
        stopPoll();
        mPollInstall = new Runnable() {
            @Override
            public void run() {
                if (mStep != Step.OPENCLAW) return;
                String st = OpenClawBootstrap.readStatus();
                String token = statusToken(st);
                mLog.setText(OpenClawBootstrap.readLogTail(1200));
                if ("OK".equals(token) || OpenClawBootstrap.isOpenClawInstalled()) {
                    mPrimary.setEnabled(true);
                    render();
                    return;
                }
                if ("FAILED".equals(token)) {
                    mPrimary.setEnabled(true);
                    render();
                    return;
                }
                mHandler.postDelayed(this, 2000);
            }
        };
        mHandler.postDelayed(mPollInstall, 1500);
    }

    private void stopPoll() {
        if (mPollInstall != null) {
            mHandler.removeCallbacks(mPollInstall);
            mPollInstall = null;
        }
    }

    private static String statusToken(String status) {
        if (status == null || status.isEmpty()) return "";
        String first = status.trim().split("\\s+")[0];
        return first;
    }

    private void openTerminal(boolean finishSetup) {
        Intent i = new Intent(this, TermuxActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        if (finishSetup) finish();
    }

    private void requestIgnoreBatteryOptimizations() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "battery optimization request failed: " + e.getMessage());
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {}
        }
    }

    private void openDeveloperSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {}
        }
    }
}
