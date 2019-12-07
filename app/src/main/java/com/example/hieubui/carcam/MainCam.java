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
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;

import org.w3c.dom.Text;

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
import static android.hardware.SensorManager.AXIS_MINUS_X;
import static android.hardware.SensorManager.AXIS_MINUS_Y;
import static android.hardware.SensorManager.AXIS_X;
import static android.hardware.SensorManager.AXIS_Y;
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
    /**Build-in Compass*/
    static SensorManager mSensorManager;
    static SensorEventListener compassSensorListener;
    float[] inR = new float[16];
    float[] outR = new float[16];
    float[] I = new float[16];
    float[] gravity = new float[3];
    float[] geomag = new float[3];
    float[] orientVals = new float[3];
    double azimuth = 0;
    double pitch = 0;
    double roll = 0;
    /**Battery Broadcast Receiver*/
    boolean previousCharging = false;
    IntentFilter batteryFilter;
    BroadcastReceiver batteryBrodcastReceiver;
    /****************************/
    static MainCam MainCam;
    boolean firstRun;
    static FrameLayout preview;
    static CameraPreview mPreview;
    /**Camera Object*/
    static Camera mCamera;
    Camera.Parameters params;
    static MediaRecorder mMediaRecorder;
    boolean isRecording = false;
    static int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
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
        Log.d("Main Cam", "Main activity on create !!!");
        setContentView(R.layout.activity_main_cam);
        MainCam = this;
        firstRun = true;
        mpref = PreferenceManager.getDefaultSharedPreferences(this);
        emergencyNumber = mpref.getString("emergencyNumber","+01911");
        if (mpref.getBoolean("useFrontCamera",false)) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        }
        // Create an instance of Camera
        mCamera = getCameraInstance(cameraId);
        if (mCamera != null) {
            params = mCamera.getParameters();
        }
        // Initiate compass
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        compassSensorListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                // If the sensor data is unreliable return
                if (sensorEvent.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                    TextView direction = (TextView)findViewById(R.id.txtDirection);
                    direction.setText("Direction Not Available");
                    return;
                }
                // Gets the value of the sensor that has been changed
                switch (sensorEvent.sensor.getType()) {
                    case Sensor.TYPE_ACCELEROMETER:
                        gravity = sensorEvent.values.clone();
                        break;
                    case Sensor.TYPE_MAGNETIC_FIELD:
                        geomag = sensorEvent.values.clone();
                        break;
                }

                // If gravity and geomag have values then find rotation matrix
                if (gravity != null && geomag != null) {

                    // checks that the rotation matrix is found
                    boolean success = SensorManager.getRotationMatrix(inR, I,
                            gravity, geomag);
                    Display display = ((WindowManager)MainCam.getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
                    if(display.getRotation() == Surface.ROTATION_0) {
                        success = SensorManager.remapCoordinateSystem(inR, AXIS_X, AXIS_Y, outR);
                    }else if(display.getRotation() == Surface.ROTATION_90) {
                        success = SensorManager.remapCoordinateSystem(inR, AXIS_Y, AXIS_MINUS_X, outR);
                    }else if(display.getRotation() == Surface.ROTATION_270) {
                        success = SensorManager.remapCoordinateSystem(inR, AXIS_MINUS_Y, AXIS_X, outR);
                    }
                    if (success) {
                        SensorManager.getOrientation(outR, orientVals);
                        azimuth = Math.toDegrees(orientVals[0]);
                        pitch = Math.toDegrees(orientVals[1]);
                        roll = Math.toDegrees(orientVals[2]);
                    }
                    if((azimuth >= 0)&& (azimuth <= 25 )) {
                        TextView direction = (TextView)findViewById(R.id.txtDirection);
                        direction.setText(Math.round(azimuth) + "° North");
                    }else if((azimuth > 25)&& (azimuth <= 65 )) {
                        TextView direction = (TextView)findViewById(R.id.txtDirection);
                        direction.setText(Math.round(azimuth) + "° NorthEast");
                    }else if((azimuth > 65)&& (azimuth <= 115 )) {
                        TextView direction = (TextView)findViewById(R.id.txtDirection);
                        direction.setText(Math.round(azimuth) + "° East");
                    }else if((azimuth > 115)&& (azimuth <= 155 )) {
                        TextView direction = (TextView)findViewById(R.id.txtDirection);
                        direction.setText(Math.round(azimuth) + "° SouthEast");
                    }else if((azimuth > 155)&& (azimuth <= 205 )) {
                        TextView direction = (TextView)findViewById(R.id.txtDirection);
                        direction.setText(Math.round(azimuth) + "° South");
                    }else if((azimuth > 205)&& (azimuth <= 245 )) {
                        TextView direction = (TextView)findViewById(R.id.txtDirection);
                        direction.setText(Math.round(azimuth) + "° SouthWest");
                    }else if((azimuth > 245)&& (azimuth <= 295 )) {
                        TextView direction = (TextView)findViewById(R.id.txtDirection);
                        direction.setText(Math.round(azimuth) + "° West");
                    }else if((azimuth > 295)&& (azimuth <= 335 )) {
                        TextView direction = (TextView)findViewById(R.id.txtDirection);
                        direction.setText(Math.round(azimuth) + "° NorthWest");
                    }else if((azimuth > 335)&& (azimuth <= 360 )) {
                        TextView direction = (TextView)findViewById(R.id.txtDirection);
                        direction.setText(Math.round(azimuth) + "\" North");
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
            }
        };
        // Register this class as a listener for the accelerometer sensor
        mSensorManager.registerListener(compassSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
        // ...and the orientation sensor
        mSensorManager.registerListener(compassSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_NORMAL);

        // Initiate mediaRecorder and check for storage setting
        mMediaRecorder = new MediaRecorder();
        try {
            availableStorage = Integer.parseInt(mpref.getString("availableStorage", "700"));
        } catch (NumberFormatException e) {
            availableStorage = 700;
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
            Log.d("Main Cam", "Location service is not available");
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
                        if(isRecording) {
                            serviceButton.performClick();
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
        Log.d("Main Cam", "Main activity on resume !!!");
        // On resume you we update stuff again
        updateSetting();

        if(isRecording && (mpref.getBoolean("recordOnBackground",false))) {
            if(CameraService.cameraService != null && CameraService.cameraService.isServiceRun) {
                // If it' recording on background we stop it
                // Make sure we remove the view before add it to
                // Maincam preview
                unbindService(mConnection);
            }
        }else {
            if (mCamera == null) {
                mCamera = getCameraInstance(cameraId);
            }
            if (mPreview == null) {
                preview.removeAllViewsInLayout();
                mPreview = new CameraPreview(this, mCamera);
            }
            if (mPreview.getParent() == null) {
                preview.addView(mPreview);
            }
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("Main Cam", "Main activity on pause !!!");
        updateSetting();
        if(mpref.getBoolean("recordOnBackground",false)) {
            if (isRecording) {
                final ImageButton serviceButton = (ImageButton) findViewById(R.id.button_service);
                serviceButton.performClick();
                preview.removeAllViewsInLayout();
                mCamera = getCameraInstance(cameraId);
                mPreview = new CameraPreview(this, mCamera);
                // If we are using background recording
                // We start the service here
                bindService(intentCameraService, mConnection, Context.BIND_AUTO_CREATE);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("Main Cam", "Main activity on stop !!!");
        // Camera on stop.
        // If it's recording we will save data and remove
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("Main Cam", "Main activity on destroy !!!");
        // Unregister compass
        mSensorManager.unregisterListener(compassSensorListener);
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
        if(CameraService.cameraService != null && CameraService.cameraService.isServiceRun) {
            // If it' recording on background we stop it
            // Make sure we remove the view before add it to
            // Maincam preview
            unbindService(mConnection);
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
    }


    /**Call back for GPS Listener*/
    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            float newSpeed = location.getSpeed();
            if (MainCam.currentSpeed - newSpeed > 20.00) {
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
            Log.d("Main Cam", "Camera start now");

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

    public void releaseCamera() {
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    public static Camera getCameraInstance(int cameraId) {
        Camera c = null;
        try {
            c = Camera.open(cameraId); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
            Log.d("Main Cam", "Can not get camera instance");
        }
        return c; // returns null if camera is unavailable
    }

    public boolean prepareVideoRecorder() {
        // Step 1: Unlock and set camera to MediaRecorder
        if(mCamera == null) {
            mCamera = getCameraInstance(cameraId);
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
            if(cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
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

    public void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mCamera.lock();           // lock camera for later use
        }
    }

    public int getAvailableInternalMemorySize() {
        File path = new File("/storage/sdcard0/");
        if(mpref.getBoolean("saveOnSDCard", false)) {
            path = new File("/storage/sdcard1/");
        }
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return formatSize(availableBlocks * blockSize);
    }

    public File getOutputMediaFile(int type, int availableStorage){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        File mediaStorageDir = new File("/storage/sdcard0/CarCam");
        if(mpref.getBoolean("saveOnSDCard", false)) {
            mediaStorageDir = new File("/storage/sdcard1/CarCam");
        }

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

        params.setExposureCompensation(0);
        if (whiteBalance.contains(Camera.Parameters.WHITE_BALANCE_AUTO)) {
            params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        }
        if (sceneMode.contains(Camera.Parameters.SCENE_MODE_AUTO)) {
            params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
        }
        if (params.isVideoStabilizationSupported()) {
            params.setVideoStabilization(true);
        }
        mCamera.setParameters(params);
    }

    public void updateSetting() {
        // Emergency Number
        emergencyNumber = mpref.getString("emergencyNumber","+01911");
        // Use Front Camera
        if (mpref.getBoolean("useFrontCamera",false)) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        }else {
            cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
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
