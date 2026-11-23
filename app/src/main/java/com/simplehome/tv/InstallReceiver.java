package com.simplehome.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/**
 * Hears back from Android's installer during a software update: shows Android's "Install" confirmation,
 * and reports a failure to the update screen. On success Android replaces SimpleLauncher and
 * {@link BootReceiver} opens it again.
 */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (Apps.tryStart(context, confirm)) return;
            }
            report(context, false, "Your TV didn't show its install screen.");
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) return;   // SimpleLauncher is restarted on the new build
        boolean cancelled = status == PackageInstaller.STATUS_FAILURE_ABORTED;
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        report(context, cancelled, message);
    }

    private static void report(Context context, boolean cancelled, String message) {
        Prefs.get(context).edit().putBoolean(Updater.PREF_RELAUNCH, false).apply();
        Intent back = new Intent(context, UpdateActivity.class)
                .putExtra(UpdateActivity.EXTRA_INSTALL_RESULT, cancelled ? "cancelled" : "failed")
                .putExtra(UpdateActivity.EXTRA_INSTALL_MESSAGE, message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Apps.tryStart(context, back);
    }
}
