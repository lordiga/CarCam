package com.example.hieubui.carcam;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.*;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import static android.content.ContentValues.TAG;
import static com.example.hieubui.carcam.CameraService.isRecording;
import static com.example.hieubui.carcam.CameraService.isServiceRun;
import static com.example.hieubui.carcam.CameraService.layoutParams;
import static com.example.hieubui.carcam.CameraService.mPreview;
import static com.example.hieubui.carcam.CameraService.windowManager;


public class MainCam extends Activity implements IBaseGpsListener {

    static CountDownTimer recordTimer;

    static boolean mBound = false;
    static int recordDuration;
    static int maxSpeed;
    static int currentSpeed;
    static int GPSStatus;
    static int alarmCount;
    static int maxAlarmCount;
    static boolean previousCharging = false;
    IntentFilter ifilter;
    BroadcastReceiver smsReceiver;
    static CameraService mCameraService;
    static boolean mautoStart;
    boolean keepScreenOn;
    boolean maximumBrightness;
    static boolean shutdownOnClose;
    static boolean openOnStartup;
    int previousOnScreenDuration;
    static String emergencyNumber;
    static SharedPreferences  mpref;
    static MainCam MainCam;
    static boolean firstRun;
    static Timer mServiceTimer;
    static TimerTask mServiceTimerTask;
    /** Defines callbacks for service binding, passed to bindService() */
    static ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,IBinder service) {
            // We've bound to CameraService, cast the IBinder and get CameraService instance
            CameraService.CameraBinder binder = (CameraService.CameraBinder) service;
            mCameraService = binder.getService();
            mBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
    // GPS and speedometer object
    static LocationManager locationManager;
    // Sound alarm object
    MediaPlayer mp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_cam);
        MainCam = this;
        firstRun = true;
        mpref = PreferenceManager.getDefaultSharedPreferences(this);
        emergencyNumber = mpref.getString("emergencyNumber","+01911");
        /**Set up keep screen on*/
        keepScreenOn = mpref.getBoolean("keepScreenOn", false);
        if(keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        /**Set up maximum brightness*/
        maximumBrightness = mpref.getBoolean("maximumBrightness", false);
        if(keepScreenOn) {
            getWindow().getAttributes().screenBrightness = 1;
        }else {
            getWindow().getAttributes().screenBrightness = -1;
        }
        PackageManager pmOpenOnStartup  = this.getPackageManager();
        ComponentName cnOpenOnStartup = new ComponentName(this, StartAtBootReceiver.class);
        openOnStartup = mpref.getBoolean("openOnStartup", false);
        if(openOnStartup) {
            pmOpenOnStartup.setComponentEnabledSetting(cnOpenOnStartup,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        }else{
            pmOpenOnStartup.setComponentEnabledSetting(cnOpenOnStartup,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }

        /**Set up shutdown on close flag. This happen in onDestroy() call back*/
        shutdownOnClose = mpref.getBoolean("shutdownOnClose", false);

        // Binding CameraService to main thread
        // Bind to LocalService
        final Intent intent = new Intent(this, CameraService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        // Start camera service first time
        Log.d("Camera Preview","Starting Service for first run");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        this.startService(intent);

        //Initiate alarm and maxspeed
        mp = MediaPlayer.create(this, R.raw.alarm);
        try {
            maxSpeed = Integer.parseInt(mpref.getString("maxSpeed", "80"));
        } catch (NumberFormatException e) {
            maxSpeed = 80;
        }

        alarmCount = 0;
        maxAlarmCount = 5;
        //Initiate GPS and Speedometer
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, MainCam.this);
        } catch (Exception e) {
            locationManager = null;
            Log.d("MainCam", "Location service is no available");
        }
        if (locationManager != null) {
            this.updateSpeed(null);
        }

        //Initiate recorduration available storage
        try {
            recordDuration = Integer.parseInt(mpref.getString("recordDuration", "10"));
        } catch (NumberFormatException e) {
            recordDuration = 10;
        }
        // Initiate and start service timer for the first run
        startServiceTimer(recordDuration);
        startRecordTimer();
        // Add listener to service button
        final ImageButton serviceButton = (ImageButton) findViewById(R.id.button_service);
        serviceButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if ((!isServiceRun) && (!isRecording)) {
                            // Only start the service if it's not running
                            // If it's running then don't do anything
                            startCameraService();
                            // Request GPS Update
                            if (ActivityCompat.checkSelfPermission(MainCam.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(MainCam.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                // TODO: Consider calling
                                //    ActivityCompat#requestPermissions
                                // here to request the missing permissions, and then overriding
                                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                //                                          int[] grantResults)
                                // to handle the case where the user grants the permission. See the documentation
                                // for ActivityCompat#requestPermissions for more details.
                                return;
                            }
                            if (locationManager != null) {
                                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, MainCam.this);
                            }
                            startServiceTimer(recordDuration);
                            serviceButton.setBackgroundResource(R.drawable.stop_record);
                        } else if ((isServiceRun) && (isRecording)) {
                            // If service is runnung and it's recording
                            // We will stop the service
                            stopCameraService();
                            stopServiceTimer();
                            // Stop recordingTimer
                            stopRecordTimer();
                            recordTimer = null;
                            // Remove GPS update
                            if (locationManager != null) {
                                locationManager.removeUpdates(MainCam.this);
                            }
                            serviceButton.setBackgroundResource(android.R.drawable.presence_video_online);
                        }
                    }
                }
        );
        // Add Setting button
        final ImageButton settingButton = (ImageButton) findViewById(R.id.setting);
        settingButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if ((isServiceRun) && (isRecording)) {
                            // If Camera is running we stop everything first
                            stopCameraService();
                            stopServiceTimer();
                            // Stop recordingTimer
                            stopRecordTimer();
                            recordTimer = null;
                            // Remove GPS update
                            if (locationManager != null) {
                                locationManager.removeUpdates(MainCam.this);
                            }
                            serviceButton.setBackgroundResource(android.R.drawable.presence_video_online);
                        }
                        Intent settingIntent = new Intent(MainCam.this, SettingsActivity.class);
                        startActivity(settingIntent);
                    }
                }
        );

        // Create an intent filter to recieve batery charge event
        mautoStart = mpref.getBoolean("autoStartStop", true);
        ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        smsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
                boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;
                if (usbCharge || acCharge) {
                    previousCharging = true;
                    Log.d("Battery Service", "Charging");

                } else {
                    if (previousCharging) {
                        //If it's not charging now and it used to charge.
                        // We check if speed is 0. If speed is zero
                        // That means we stop so stop camera too
                        // sometime GPS is acting up and it keep the old speed
                        // Even though it's not working so we need to check for that case
                        Log.d("Battery Service in Main", "Not Charging");
                        if (locationManager != null) {
                            // MAke sure we don't get fooled by null location
                            if ((MainCam.currentSpeed < 1)||(GPSStatus != LocationProvider.AVAILABLE)) {
                                // Stop the camera
                                Log.d("Battery Service in Main", "Stopping the application");
                                finish();
                            } else {
                                // Car is driving. Check if user want to stop recording
                                if (!mpref.getBoolean("keepRecordOnDrive", true)) {
                                    finish();
                                }
                            }
                        } else {
                            finish();
                        }
                    }
                    previousCharging = false;
                }

            }
        };

        if (mautoStart) {
            Intent batteryStatus = this.registerReceiver(smsReceiver, ifilter);
            // here we start another service to catch the battery signal.
            // This service in used to start this app. check with value from setting first
            Intent batteryServiceIntent = new Intent(this, BatteryService.class);
            if (!isMyServiceRunning(BatteryService.class)) {
                startService(batteryServiceIntent);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Revert system setting change
        // Update new setting to Battery Service
        MainCam.firstRun = false;
        BatteryService.autoStartStop = mpref.getBoolean("autoStartStop", true);
        BatteryService.startOnDrive = mpref.getBoolean("startOnDrive", false);
        if ((mpref.getString("onScreenDuration", "Use System Setting").compareTo("Always On") != 0) &&
                (mpref.getString("onScreenDuration", "Use System Setting").compareTo("Use System Setting") != 0)) {
            // User did use setting for screen time
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                boolean canWrite = Settings.System.canWrite(getApplicationContext());
                if (canWrite && previousOnScreenDuration > 0 ) {
                    android.provider.Settings.System.putInt(
                            getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, previousOnScreenDuration);
                } else {
                    Intent systemCanWrite = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    systemCanWrite.setData(Uri.parse("package:" + getApplicationContext().getPackageName()));
                    startActivity(systemCanWrite);
                }
            } else {
                if(previousOnScreenDuration > 0) {
                    android.provider.Settings.System.putInt(
                            getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, previousOnScreenDuration);
                }
            }
        }
        if (isRecording) {
            if (isServiceRun) {
                stopCameraService();
                stopServiceTimer();
                // Stop recordingTimer
                stopRecordTimer();
                // Remove GPS update
                if (locationManager != null) {
                    locationManager.removeUpdates(MainCam.this);
                }
            }
        }
        if(shutdownOnClose) {
            try {
                Process proc = Runtime.getRuntime()
                        .exec(new String[]{ "su", "-c", "reboot -p" });
                proc.waitFor();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        // Here we start nullify everything`
        System.exit(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // On resume you we update stuff again
        updateSetting();
        if(layoutParams !=null) {
            layoutParams.height = 320;
            layoutParams.width = 240;
            windowManager.updateViewLayout(mPreview, layoutParams);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateSetting();
        if(layoutParams !=null) {
            layoutParams.height = 1;
            layoutParams.width = 1;
            windowManager.updateViewLayout(mPreview, layoutParams);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        // TODO Auto-generated method stub
        if (location != null) {
            float newSpeed = location.getSpeed();
            if (MainCam.currentSpeed - currentSpeed > 20.00) {
                Intent intent = new Intent(Intent.ACTION_CALL);
                intent.setData(Uri.parse("tel:" + emergencyNumber ));
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                this.startActivity(intent);
            }
            CLocation myLocation = new CLocation(location);
            this.updateSpeed(myLocation);
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub
        GPSStatus = status;
    }

    @Override
    public void onGpsStatusChanged(int event) {
        // TODO Auto-generated method stub

    }

    private void updateSpeed(CLocation location) {
        // TODO Auto-generated method stub
        float nCurrentSpeed = 0;
        if(location != null)
        {
            nCurrentSpeed = location.getSpeed();
        }

        Formatter fmt = new Formatter(new StringBuilder());
        fmt.format(Locale.US, "%5.1f", nCurrentSpeed);
        String strCurrentSpeed = fmt.toString();
        strCurrentSpeed = strCurrentSpeed.replace(' ', '0');
        double currentSpeed = Double.parseDouble(strCurrentSpeed);
        if((currentSpeed >= maxSpeed) && (alarmCount < maxAlarmCount)) {
            alarmCount++;
            mp.start();
        }else if(currentSpeed < maxSpeed) {
            alarmCount = 0;
        }
        MainCam.currentSpeed = (int) currentSpeed;
        String strUnits = "mph";
        TextView txtCurrentSpeed = (TextView) this.findViewById(R.id.txtCurrentSpeed);
        txtCurrentSpeed.setText(strCurrentSpeed + " " + strUnits);
    }

    public void startCameraService() {
        if(mCameraService != null) {
            mCameraService.startCamera();
            startRecordTimer();
        }
    }

    public void stopCameraService() {
        mCameraService.stopCamera();
        isRecording = false;
        isServiceRun = false;
    }

    public void startServiceTimer(int duration){
        initialServiceTimer();
        mServiceTimer.schedule(mServiceTimerTask, duration*60*1000,duration*60*1000);
        //mServiceTimer.schedule(mServiceTimerTask, 10*1000,10*1000);
    }

    public void stopServiceTimer() {
        mServiceTimer.cancel();
    }

    public void initialServiceTimer() {
        // Set up timer and timertask
        mServiceTimer = new Timer();
        mServiceTimerTask = new TimerTask() {
            @Override
            public void run() {
                //task to run for timmer.
                // This task will first check if camera is in used?
                // If yes, it will save stop the cam, save to file and start a new service
                Log.d("TimerTask","Starting service timer");
                if(isRecording && isServiceRun) {
                    // Stop the camera when service is running and using camera
                    stopCameraService();
                    Log.d("TimerTask", "stop camera in service timer task");
                    // inform the user that recording has stopped
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            findViewById(R.id.button_service).setBackgroundResource(android.R.drawable.presence_video_online);
                        }
                    });
                }
                Log.d("TimerTask","Start camera in service timer task");
                startCameraService();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        findViewById(R.id.button_service).setBackgroundResource(R.drawable.stop_record);
                    }
                });
            }
        };
    }

    public void startRecordTimer() {
        final TextView txtRecordTimer = (TextView) this.findViewById(R.id.txtRecordTimer);
        if(recordTimer != null) {
            // If waitTimer is not null First we cancel the current one
            // Then we start it up
            recordTimer.cancel();
            recordTimer.start();
        }else {
            recordTimer = new CountDownTimer(recordDuration * 60 * 1000, 1000) {
                long minute;
                long second;
                public void onTick(long millisUntilFinished) {
                    minute = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished);
                    second = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60;
                    txtRecordTimer.setText(minute + ":" + second + "/" + recordDuration + ":00");
                }

                public void onFinish() {
                    txtRecordTimer.setText("00:00" + "/" + recordDuration + ":00");
                }
            }.start();
        }
    }

    public void stopRecordTimer() {
        if(recordTimer != null) {
            recordTimer.cancel();
        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void updateSetting() {
        // Emergency Number
        emergencyNumber = mpref.getString("emergencyNumber","+01911");
        //Sence Mode
        CameraService.senceMode = Integer.parseInt(mpref.getString("senceMode", "-1"));
        CameraService.darkLight = CameraService.senceMode == 2;
        if (CameraService.senceMode == -1) {
            CameraService.LightSensorListener = new SensorEventListener() {
                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
                @Override
                public void onSensorChanged(SensorEvent event) {
                    if ((event.sensor.getType() == Sensor.TYPE_LIGHT) && (event.values[0] < 150)) {
                        CameraService.darkLight = true;
                    } else if ((event.sensor.getType() == Sensor.TYPE_LIGHT) && (event.values[0] > 150)) {
                        // Bright light now
                        CameraService.darkLight = false;
                    }
                }
            };
            CameraService.mySensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor LightSensor = CameraService.mySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (LightSensor != null) {
                CameraService.mySensorManager.registerListener(
                        CameraService.LightSensorListener,
                        LightSensor,
                        90000000);
            } else {
                Log.d("MainCam", "Sensor.TYPE_LIGHT NOT Available");
            }
        }
        //Max Speed
        try {
            maxSpeed = Integer.parseInt(mpref.getString("maxSpeed", "80"));
        } catch (NumberFormatException e) {
            maxSpeed = 80;
        }
        //Keep Screen On
        keepScreenOn = mpref.getBoolean("keepScreenOn", false);
        if (keepScreenOn) {
            //User prefer to keep screen always on
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        //Record Duration
        try {
            recordDuration = Integer.parseInt(mpref.getString("recordDuration", "10"));
        } catch (NumberFormatException e) {
            recordDuration = 10;
        }

        //Available Storage
        try {
            CameraService.availableStorage = Integer.parseInt(mpref.getString("availableStorage", "700"));
        } catch (NumberFormatException e) {
            CameraService.availableStorage = 700;
        }

        //Maximum Brightness
        maximumBrightness = mpref.getBoolean("maximumBrightness", false);
        if(keepScreenOn) {
            getWindow().getAttributes().screenBrightness = 1;
        }else {
            getWindow().getAttributes().screenBrightness = -1;
        }

        //Shutdown on Close
        shutdownOnClose = mpref.getBoolean("shutdownOnClose", false);

        //Open on Startup
        PackageManager pmOpenOnStartup  = this.getPackageManager();
        ComponentName cnOpenOnStartup = new ComponentName(this, StartAtBootReceiver.class);
        openOnStartup = mpref.getBoolean("openOnStartup", false);
        if(openOnStartup) {
            pmOpenOnStartup.setComponentEnabledSetting(cnOpenOnStartup,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
        }else{
            pmOpenOnStartup.setComponentEnabledSetting(cnOpenOnStartup,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }

        //Auto Start/Stop
        mautoStart = mpref.getBoolean("autoStartStop", true);
    }

}
