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
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.StatFs;
import android.preference.PreferenceManager;
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
import static com.example.hieubui.carcam.CameraService.cameraService;

public class MainCam extends Activity implements IBaseGpsListener {

    /** Setting Variable*/
    int recordDuration;
    int maxSpeed;
    int currentSpeed;
    /**GPS and speedometer object*/
    LocationManager locationManager;
    int GPSStatus;
    /**Sound alarm object*/
    MediaPlayer mp;
    int alarmCount;
    int maxAlarmCount;
    boolean mautoStart;
    boolean keepScreenOn;
    boolean maximumBrightness;
    boolean shutdownOnClose;
    boolean openOnStartup;
    boolean recordOnBackground;
    String emergencyNumber;
    SharedPreferences  mpref;
    /**Light and Sence mode*/
    static SensorManager mySensorManager;
    static SensorEventListener LightSensorListener;
    static boolean darkLight;
    static boolean extremeDarkLight;
    static int senceMode;
    /**Battery Broadcast Receiver*/
    boolean previousCharging = false;
    IntentFilter batteryFilter;
    BroadcastReceiver batteryBrodcastReceiver;
    /****************************/
    static MainCam MainCam;
    boolean firstRun;
    FrameLayout preview;
    CameraPreview mPreview;
    /**Camera Object*/
    Camera mCamera;
    Camera.Parameters params;
    MediaRecorder mMediaRecorder;
    boolean isRecording = false;
    /**Storage Object*/
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    static int availableStorage;
    /**Record Timer*/
    CountDownTimer recordTimer;
    /**Tinmer Service*/
    Timer mServiceTimer;
    TimerTask mServiceTimerTask;
    /**Canmera Service*/
    boolean mBound = false;
    Intent intentCameraService;
    ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,IBinder service) {
            // We've bound to CameraService, cast the IBinder and get CameraService instance
            CameraService.CameraBinder binder = (CameraService.CameraBinder) service;
            cameraService = binder.getService();
            mBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    /**Activity life cycle*/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_cam);
        MainCam = this;
        firstRun = true;
        mpref = PreferenceManager.getDefaultSharedPreferences(this);
        emergencyNumber = mpref.getString("emergencyNumber","+01911");

        // Create an instance of Camera
        mCamera = getCameraInstance();
        if (mCamera != null) {
            params = mCamera.getParameters();
        }

        // Initiate mediaRecorder and check for storage setting
        mMediaRecorder = new MediaRecorder();
        try {
            availableStorage = Integer.parseInt(mpref.getString("availableStorage", "700"));
        } catch (NumberFormatException e) {
            availableStorage = 700;
        }

        // Initiate Light sensor
        senceMode = Integer.parseInt(mpref.getString("senceMode", "-1"));
        darkLight = senceMode == 2;
        if (senceMode == -1) {
            LightSensorListener = new SensorEventListener() {
                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
                @Override
                public void onSensorChanged(SensorEvent event) {
                    if ((event.sensor.getType() == Sensor.TYPE_LIGHT) && (event.values[0] < 150) && (event.values[0] > 5)) {
                        darkLight = true;
                        extremeDarkLight = false;
                    } else if ((event.sensor.getType() == Sensor.TYPE_LIGHT) && (event.values[0] > 150)) {
                        //Bright light now
                        darkLight = false;
                        extremeDarkLight = false;
                    } else if((event.sensor.getType() == Sensor.TYPE_LIGHT) && (event.values[0] < 5)) {
                        //Extremely low light
                        extremeDarkLight = true;
                        darkLight = false;
                    }
                }
            };
            mySensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor LightSensor = mySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (LightSensor != null) {
                mySensorManager.registerListener(
                        LightSensorListener,
                        LightSensor,
                        90000000);
            } else {
                Log.d("MainCam", "Sensor.TYPE_LIGHT NOT Available");
            }
        }

        /**Set up keep screen on*/
        keepScreenOn = mpref.getBoolean("keepScreenOn", false);
        if(keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        /**Set up maximum brightness*/
        maximumBrightness = mpref.getBoolean("maximumBrightness", false);
        if(maximumBrightness) {
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
        intentCameraService = new Intent(this, CameraService.class);
        //bindService(intentCameraService, mConnection, Context.BIND_AUTO_CREATE);

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

        // Add listener to service button
        final ImageButton serviceButton = (ImageButton) findViewById(R.id.button_service);
        serviceButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (!isRecording) {
                            // Only start the service if it's not running
                            // If it's running then don't do anything
                            startCamera();
                            // Request GPS Update
                            if (ActivityCompat.checkSelfPermission(MainCam.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(MainCam.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                return;
                            }
                            if (locationManager != null) {
                                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, MainCam.this);
                            }
                            startScheduler(recordDuration);
                            startTimer();
                            serviceButton.setBackgroundResource(R.drawable.stop_record);
                        }else {
                            // If service is runnung and it's recording
                            // We will stop the service
                            stopCamera();
                            stopScheduler();
                            // Stop recordingTimer
                            stopTimer();
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
                        if (isRecording) {
                            // If Camera is running we stop everything first
                            stopCamera();
                            stopScheduler();
                            // Stop recordingTimer
                            stopTimer();
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
        batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryBrodcastReceiver = new BroadcastReceiver() {
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
            Intent batteryStatus = this.registerReceiver(batteryBrodcastReceiver, batteryFilter);
            // here we start another service to catch the battery signal.
            // This service in used to start this app. check with value from setting first
            Intent batteryServiceIntent = new Intent(this, BatteryService.class);
            if (!isMyServiceRunning(BatteryService.class)) {
                startService(batteryServiceIntent);
            }
        }

        // Create our Preview layout and set the preview as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        preview = (FrameLayout) findViewById(R.id.camera_preview);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // On resume you we update stuff again
        updateSetting();
        if(mPreview.getParent() == null) {
            preview.addView(mPreview);
        }
        if(mpref.getBoolean("recordOnBackground",false)){
            if(isRecording) {
                // If it's recording on background we stop it
                // Make sure we remove the view before add it to
                // Maincam preview
                unbindService(mConnection);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateSetting();
        if(mpref.getBoolean("recordOnBackground",false)) {
            if (isRecording) {
                stopCamera();
                stopTimer();
                stopScheduler();
                isRecording = true;
                preview.removeView(mPreview);
                bindService(intentCameraService, mConnection, Context.BIND_AUTO_CREATE);
                //startService(intentCameraService);
            }
        }else if(isRecording){
            stopCamera();
            stopTimer();
            stopScheduler();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Revert system setting change
        // Update new setting to Battery Service
        updateSetting();
        BatteryService.autoStartStop = mpref.getBoolean("autoStartStop", true);
        BatteryService.startOnDrive = mpref.getBoolean("startOnDrive", false);
        if (isRecording) {
            if (cameraService != null && cameraService.isServiceRun) {
                unbindService(mConnection);
            }else {
                stopCamera();
                stopScheduler();
                stopTimer();
            }
        }
        // Remove GPS update
        if (locationManager != null) {
            locationManager.removeUpdates(MainCam.this);
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

    /**Call back for GPS Listener*/
    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            float newSpeed = location.getSpeed();
            if (MainCam.currentSpeed - currentSpeed > 20.00) {
                Intent intent = new Intent(Intent.ACTION_CALL);
                intent.setData(Uri.parse("tel:" + emergencyNumber ));
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
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
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

        GPSStatus = status;
    }

    @Override
    public void onGpsStatusChanged(int event) {
    }

    /**Public class method*/
    public void updateSpeed(CLocation location) {
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

    public void startScheduler(int duration){
        initialScheduler();
        mServiceTimer.schedule(mServiceTimerTask, duration*60*1000,duration*60*1000);
        //mServiceTimer.schedule(mServiceTimerTask, 10*1000,10*1000);
    }

    public void stopScheduler() {
        mServiceTimer.cancel();
    }

    public void initialScheduler() {
        // Set up timer and timertask
        mServiceTimer = new Timer();
        mServiceTimerTask = new TimerTask() {
            @Override
            public void run() {
                //task to run for timmer.
                // This task will first check if camera is in used?
                // If yes, it will save stop the cam, save to file and start a new service
                Log.d("TimerTask","Starting service timer");
                if(isRecording) {
                    // Stop the camera when using camera
                    stopCamera();
                    Log.d("TimerTask", "stop camera in scheduler task");
                    // inform the user that recording has stopped
                    if(cameraService != null && !CameraService.cameraService.isServiceRun) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                findViewById(R.id.button_service).setBackgroundResource(android.R.drawable.presence_video_online);
                            }
                        });
                    }
                }
                Log.d("TimerTask","Start camera in service timer task");
                startCamera();
                startTimer();
                if(cameraService != null  && !CameraService.cameraService.isServiceRun) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            findViewById(R.id.button_service).setBackgroundResource(R.drawable.stop_record);
                        }
                    });
                }
            }
        };
    }

    public void startTimer() {
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

    public void stopTimer() {
        if(recordTimer != null) {
            recordTimer.cancel();
            TextView txtRecordTimer = (TextView) this.findViewById(R.id.txtRecordTimer);
            txtRecordTimer.setText("00:00" + "/" + recordDuration + ":00");
        }
    }

    public boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void stopCamera() {
        // This method will stop and save to vid file
        // used when timertask need to save camera to a file on scheduled time
        // Stop camera
        // stop recording and release camera
        mMediaRecorder.stop();  // stop the recording
        mCamera.lock();         // take camera access back from MediaRecorder
        isRecording = false;
    }

    public void startCamera() {
        // initialize video camera
        if (prepareVideoRecorder()) {
            // Camera is available and unlocked, MediaRecorder is prepared,
            // now you can start recording
            Log.d("MainCam", "Camera start now");

            mMediaRecorder.start();
            // inform the user that recording has started
            isRecording = true;
        } else {
            // prepare didn't work, release the camera
            Log.d("Camera Service", "Camera Service can not acquire a camera");
            releaseMediaRecorder();
            isRecording = false;
        }

    }

    public void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    public boolean prepareVideoRecorder(){
        // Step 1: Unlock and set camera to MediaRecorder
        if(mCamera == null) {
            mCamera = getCameraInstance();
            params = mCamera.getParameters();
        }
        mCamera.unlock();


        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P));

        // Step 4: Set output file
        if(getOutputMediaFile(MEDIA_TYPE_VIDEO,availableStorage) != null) {
            mMediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO,availableStorage).toString());
        }else {
            return false;
        }
        // Step 5: Set the preview output
        mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());

        // Step 6: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
            mCamera.lock();
            if(mySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null) {
                configSenseModeCamera(params, mCamera);
            }
            mCamera.unlock();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    public void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mCamera.lock();           // lock camera for later use
        }
    }

    public int getAvailableInternalMemorySize() {
        File path = new File("/storage/sdcard0/");
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return formatSize(availableBlocks * blockSize);
    }

    public File getOutputMediaFile(int type, int availableStorage){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File("/storage/sdcard0/carcam");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("carcam", "failed to create directory");
                return null;
            }
        }
        // Sorting Save directory and delete file if needed
        //sortSaveDir(mediaStorageDir,maxSize,recordDuration);
        // Check if we need to delete file
        // Storage will save 700mb at least for free space
        while(getAvailableInternalMemorySize() < availableStorage) {
            // If get have less than 1Gb free
            // We will start delete files
            File[] listFiles =  mediaStorageDir.listFiles();
            Arrays.sort(listFiles);
            if(listFiles.length > 1) {
                listFiles[0].delete();
            }else {
                return null;
            }
        }
        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+timeStamp+".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    public int formatSize(long size) {
        if (size >= 1024) {
            //Has more than 1 KB
            size /= 1024;
            if (size >= 1024) {
                // Has more than 1 MB
                size /= 1024;
                return (int)size;
            }else {
                return 1;
            }        }else {
            return 0;
        }
    }

    public static void configSenseModeCamera(Camera.Parameters params, Camera mCamera) {
        // set the focus mode
        List<String> focusModes = params.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        List<String> whiteBalance = params.getSupportedWhiteBalance();

        List<String> sceneMode = params.getSupportedSceneModes();

        if(darkLight) {
            params.setExposureCompensation((int)(params.getMaxExposureCompensation()/3*2));
            Log.d("Darklight", "Set to 2/3 of Max EC " + params.getMaxExposureCompensation() );

        }else if(extremeDarkLight) {
            params.setExposureCompensation(params.getMaxExposureCompensation());
            Log.d("Extreme Darklight", "Set to max EC " + params.getMaxExposureCompensation() );
        }
        else{
            params.setExposureCompensation(0);
            if (whiteBalance.contains(Camera.Parameters.WHITE_BALANCE_AUTO)) {
                params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            }
            if (sceneMode.contains(Camera.Parameters.SCENE_MODE_AUTO)) {
                params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
            }
        }
        if (params.isVideoStabilizationSupported()) {
            params.setVideoStabilization(true);
        }
        mCamera.setParameters(params);
    }

    public void updateSetting() {
        // Emergency Number
        emergencyNumber = mpref.getString("emergencyNumber","+01911");
        //Sence Mode
        senceMode = Integer.parseInt(mpref.getString("senceMode", "-1"));
        darkLight = senceMode == 2;
        if (senceMode == -1) {
            LightSensorListener = new SensorEventListener() {
                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
                @Override
                public void onSensorChanged(SensorEvent event) {
                    if ((event.sensor.getType() == Sensor.TYPE_LIGHT) && (event.values[0] < 150)) {
                        darkLight = true;
                    } else if ((event.sensor.getType() == Sensor.TYPE_LIGHT) && (event.values[0] > 150)) {
                        // Bright light now
                        darkLight = false;
                    }
                }
            };
            mySensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor LightSensor = mySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (LightSensor != null) {
                mySensorManager.registerListener(
                        LightSensorListener,
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
            availableStorage = Integer.parseInt(mpref.getString("availableStorage", "700"));
        } catch (NumberFormatException e) {
            availableStorage = 700;
        }

        //Maximum Brightness
        maximumBrightness = mpref.getBoolean("maximumBrightness", false);
        if(maximumBrightness) {
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

        //Update background run
        recordOnBackground = mpref.getBoolean("recordOnBackground",false);
    }
}
