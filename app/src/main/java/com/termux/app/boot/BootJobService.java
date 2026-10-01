package com.termux.app.boot;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

/**
 * Runs a single {@code ~/.termux/boot/*} script via {@link com.termux.app.TermuxService}.
 * Ported from Termux:Boot so the main app no longer needs the Boot plugin APK.
 */
public class BootJobService extends JobService {

    public static final String SCRIPT_FILE_PATH = "com.termux.app.boot.script_path";

    private static final String TAG = "ApolloBoot";

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "Executing boot job " + params.getJobId());

        String filePath = params.getExtras().getString(SCRIPT_FILE_PATH);
        if (filePath == null || filePath.isEmpty()) {
            Log.w(TAG, "Missing script path");
            return false;
        }

        Uri scriptUri = new Uri.Builder()
            .scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
            .path(filePath)
            .build();

        Intent executeIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, scriptUri);
        executeIntent.setClassName(TermuxConstants.TERMUX_PACKAGE_NAME,
            TermuxConstants.TERMUX_APP.TERMUX_SERVICE_NAME);
        executeIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND, true);

        Context context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(executeIntent);
        } else {
            context.startService(executeIntent);
        }

        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.i(TAG, "Boot job " + params.getJobId() + " cancelled");
        return false;
    }
}
