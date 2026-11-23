package com.simplehome.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After the stick restarts, open SimpleLauncher (if HomeCatch is on).
 * After a software update from Settings, open SimpleLauncher again on the new build.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            if (!Prefs.get(context).getBoolean(Updater.PREF_RELAUNCH, false)) return;
            Prefs.get(context).edit().putBoolean(Updater.PREF_RELAUNCH, false).apply();
            Intent open = new Intent(context, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Apps.tryStart(context, open);
            Apps.toast(context, "SimpleLauncher updated to build " + BuildConfig.BUILD_TAG);
            return;
        }
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        // Only needed on Fire TV; elsewhere Android opens the chosen home app itself.
        if (!Prefs.hook(context) || !Mode.fireTv(context)) return;
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Apps.tryStart(context, open);
    }
}
