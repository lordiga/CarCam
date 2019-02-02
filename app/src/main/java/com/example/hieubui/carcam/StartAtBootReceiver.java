package com.example.hieubui.carcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


public class StartAtBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent serviceIntent = new Intent(context, BatteryService.class);
            context.startService(serviceIntent);
        }
    }
}
