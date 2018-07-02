package com.example.hieubui.carcam;

import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;
import java.util.List;

public class BatteryService extends Service{

    String previousCharging;
    IntentFilter ifilter ;
    BroadcastReceiver smsReceiver ;
    LocationManager locationManager;
    double currentSpeed;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Battery Service","Battery service is running");
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        previousCharging = "Initial";
        ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);;
        smsReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
                boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;
                if(usbCharge || acCharge ){
                    Log.d("Battery Service", "BatterySerive detect phone is charging");
                    if(previousCharging.compareTo("Not Charging") == 0) {
                        // If it's charing now and previous charging is not
                        // We start out application here
                        if(!isAppRunning("com.example.hieubui.carcam")) {
                            try {
                                Intent carcam = BatteryService.this.getPackageManager().getLaunchIntentForPackage("com.example.hieubui.carcam");
                                BatteryService.this.startActivity(carcam);
                            }catch (Exception e) {
                                Toast.makeText(BatteryService.this,"Can not start Carcam",Toast.LENGTH_SHORT).show();
                                Log.d("Battery Service","Can not start Carcam");
                            }
                        }
                    }
                    previousCharging = "Charging";
                }else{
                    previousCharging = "Not Charging";
                    Log.d("Battery Service", "BatterySerive detect phone is not charging");
                }
            }
        };
        Intent batteryStatus = this.registerReceiver(smsReceiver, ifilter);
    }

    public boolean isAppRunning(final String packageName) {
        final ActivityManager activityManager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningAppProcessInfo> procInfos = activityManager.getRunningAppProcesses();
        if (procInfos != null)
        {
            for (final ActivityManager.RunningAppProcessInfo processInfo : procInfos) {
                if (processInfo.processName.equals(packageName)) {
                    if(processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        return true;
                    }else {
                        return false;
                    }
                }
            }
        }
        return false;
    }
}
