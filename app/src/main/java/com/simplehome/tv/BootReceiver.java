package com.simplehome.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After the stick restarts, open SimpleLauncher (if the Home button catcher is on). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        // Only needed on Fire TV; elsewhere Android opens the chosen home app itself.
        if (!Prefs.hook(context) || !Mode.fireTv(context)) return;
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Apps.tryStart(context, open);
    }
}
