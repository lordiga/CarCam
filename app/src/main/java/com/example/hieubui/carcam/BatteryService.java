package com.example.hieubui.carcam;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Formatter;
import java.util.List;
import java.util.Locale;

public class BatteryService extends Service implements IBaseGpsListener{

    String previousCharging = "Initial";
    IntentFilter ifilter ;
    BroadcastReceiver smsReceiver ;
    LocationManager locationManager;
    Boolean noToYesCharge = false;
    SharedPreferences mpref;
    Handler timerHandler;
    Runnable timerRunnable;
    static boolean autoStartStop;
    static boolean startOnDrive;

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

        mpref = PreferenceManager.getDefaultSharedPreferences(this);
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        autoStartStop = mpref.getBoolean("autoStartStop",true);
        startOnDrive = mpref.getBoolean("startOnDrive",false);
        timerHandler = new Handler();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if(locationManager != null) {
                    locationManager.removeUpdates(BatteryService.this);
                }
            }
        };
        ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);;
        smsReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
                boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;
                startOnDrive = mpref.getBoolean("startOnDrive",startOnDrive);
                autoStartStop = mpref.getBoolean("autoStartStop",autoStartStop);
                if(!autoStartStop) {
                    return;
                }
                if(usbCharge || acCharge ){
                    Log.d("Battery Service", "BatterySerive detect phone is charging");
                    if(noToYesCharge) {
                        noToYesCharge = false;
                        previousCharging = "Charging";
                        if(locationManager != null && startOnDrive) {
                            // If phone support GPS
                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                return;
                            }
                            try {
                                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, BatteryService.this);
                                // Set a timer for 2 minutes to stop GPS
                                timerHandler.postDelayed(timerRunnable,300000);
                            } catch (Exception e) {
                                locationManager = null;
                                Log.d("MainCam", "Location service is no available");
                            }
                        }else {
                            // Phone doesn't support GPS
                            // So we just turn it on
                            // We start out application here
                            Toast.makeText(BatteryService.this, "GPS not support. Start application", Toast.LENGTH_SHORT).show();
                            if (!isAppRunning("com.example.hieubui.carcam")) {
                                Intent carcam = BatteryService.this.getPackageManager().getLaunchIntentForPackage("com.example.hieubui.carcam");
                                BatteryService.this.startActivity(carcam);
                            }
                        }
                    }
                    if(previousCharging.compareTo("Not Charging") == 0) {
                        // If it's charing now and previous charging is not
                        noToYesCharge = true;
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

    @Override
    public void onLocationChanged(Location location) {
        // We start out application here
        if (!isAppRunning("com.example.hieubui.carcam")) {
            // Application is not running
            try {
                Toast.makeText(BatteryService.this, "Car is driving. Start application", Toast.LENGTH_SHORT).show();
                Intent carcam = BatteryService.this.getPackageManager().getLaunchIntentForPackage("com.example.hieubui.carcam");
                BatteryService.this.startActivity(carcam);

            } catch (Exception e) {
                Toast.makeText(BatteryService.this, "Can not start Carcam", Toast.LENGTH_SHORT).show();
                Log.d("Battery Service", "Can not start Carcam");
            }
        }
        locationManager.removeUpdates(this);

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onGpsStatusChanged(int event) {

    }
}
