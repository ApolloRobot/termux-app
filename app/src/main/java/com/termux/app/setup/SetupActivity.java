package com.termux.app.setup;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;

/**
 * First-run wizard. ADB is a hard gate — without it Termux cannot drive other apps.
 */
public class SetupActivity extends AppCompatActivity {

    private static final String LOG_TAG = "SetupActivity";

    private TextView mStatus;
    private TextView mDetail;
    private Button mPrimary;
    private Button mSecondary;
    private Button mSkipTerminal;

    private enum Step {
        WELCOME,
        BATTERY,
        ADB,
        BOOT_SCRIPTS,
        CLIPBOARD_API,
        DONE
    }

    private Step mStep = Step.WELCOME;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apollo_setup);

        mStatus = findViewById(R.id.apollo_setup_status);
        mDetail = findViewById(R.id.apollo_setup_detail);
        mPrimary = findViewById(R.id.apollo_setup_primary);
        mSecondary = findViewById(R.id.apollo_setup_secondary);
        mSkipTerminal = findViewById(R.id.apollo_setup_open_terminal);

        mPrimary.setOnClickListener(v -> onPrimary());
        mSecondary.setOnClickListener(v -> onSecondary());
        mSkipTerminal.setOnClickListener(v -> openTerminal(false));

        if (ApolloSetup.isSetupDone(this) && ApolloSetup.isAdbEnabled(this)) {
            openTerminal(true);
            return;
        }
        if (ApolloSetup.isSetupDone(this) && !ApolloSetup.isAdbEnabled(this)) {
            // Reboot often clears the mental model — force ADB step again
            mStep = Step.ADB;
            ApolloSetup.setSetupDone(this, false);
        }
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
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
                if (!ApolloSetup.isTermuxApiInstalled(this)) {
                    Toast.makeText(this, R.string.apollo_setup_api_missing_toast, Toast.LENGTH_LONG).show();
                    // allow continue but warn — clipboard input will fail until API APK installed
                }
                mStep = Step.DONE;
                break;
            case DONE:
                if (!ApolloSetup.isAdbEnabled(this)) {
                    mStep = Step.ADB;
                    Toast.makeText(this, R.string.apollo_setup_adb_required_toast, Toast.LENGTH_LONG).show();
                    break;
                }
                ApolloSetup.ensureDefaultBootScripts(this);
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
            case CLIPBOARD_API:
                // stay / re-check
                break;
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
                boolean api = ApolloSetup.isTermuxApiInstalled(this);
                mStatus.setText(api ? R.string.apollo_setup_api_ok_title : R.string.apollo_setup_api_title);
                mDetail.setText(api ? R.string.apollo_setup_api_ok_body : R.string.apollo_setup_api_body);
                mPrimary.setText(R.string.apollo_setup_next);
                mSecondary.setVisibility(View.GONE);
                break;
            case DONE:
                mStatus.setText(R.string.apollo_setup_done_title);
                mDetail.setText(ApolloSetup.readinessSummary(this) + "\n\n" + getString(R.string.apollo_setup_done_body));
                mPrimary.setText(R.string.apollo_setup_enter);
                mSecondary.setText(R.string.apollo_setup_rerun);
                break;
        }
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
