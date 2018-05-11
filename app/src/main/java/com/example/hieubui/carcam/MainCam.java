package com.example.hieubui.carcam;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationManager;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import static android.content.ContentValues.TAG;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainCam extends Activity implements IBaseGpsListener {

    static Camera mCamera;
    static CameraPreview mPreview;
    static MediaRecorder mMediaRecorder;
    static boolean isRecording = false;
    static CameraService mCameraService;
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    static boolean mBound = false;
    static Timer mTimer;
    static TimerTask mTimerTask;
    static Timer mServiceTimer;
    static TimerTask mServiceTimerTask;
    static boolean isServiceRun = false;
    static int recordDuration;
    static int maxSize;
    static int maxSpeed;
    static int alarmCount;
    static int maxAlarmCount;
    static Camera.Parameters params;
    IntentFilter ifilter;
    BroadcastReceiver smsReceiver;

    /** Defines callbacks for service binding, passed to bindService() */
    static ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
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
    LocationManager locationManager;
    // Sound alarm object
    MediaPlayer mp;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_cam);

        // Create an instance of Camera
        mCamera = getCameraInstance();
        if(mCamera != null) {
            params = mCamera.getParameters();
            initialCamera(params, mCamera);
        }
        mMediaRecorder = new MediaRecorder();

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);

        //Binding CameraService to main thread
        // Bind to LocalService
        Intent intent = new Intent(this, CameraService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        //Initiate alarm and maxspeed
        mp = MediaPlayer.create(this, R.raw.alarm);
        maxSpeed =80;
        alarmCount = 0;
        maxAlarmCount = 5;
        //Initiate GPS and Speedometer
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        this.updateSpeed(null);

        //Initiate recorduration and maxsize and max speed
        recordDuration = 10;
        maxSize = 4;
        // Initiate and start service timer for the first run
        startServiceTimer(recordDuration);

        // Add a listener to the Capture button
        Button captureButton = (Button) findViewById(R.id.button_capture);
        captureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (isRecording && !isServiceRun) {
                            // Stop camera when service is not runing but camera is running
                            // stop recording and release camera
                            stopCamera();
                            // stop the timer
                            stopTimer();
                            // inform the user that recording has stopped
                            setCaptureButtonText((Button) findViewById(R.id.button_capture),"Capture");
                        }
                        else if(!isRecording && !isServiceRun) {
                            // Only start recording when service is not running and camera is not running
                            if(startCamera()){
                                // inform the user that recording has started
                                setCaptureButtonText((Button) findViewById(R.id.button_capture),"Stop");
                                startTimer(recordDuration);
                            }else{
                                Toast.makeText(MainCam.this,"Failed to acquire Camera",Toast.LENGTH_SHORT).show();
                            }
                        }else{
                            Toast.makeText(MainCam.this,"Failed to acquire Camera or Camera service is running",Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        Button serviceButton = (Button) findViewById(R.id.button_service);
        serviceButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if((!isServiceRun) && (!isRecording)) {
                            // Only start the service if it's not running
                            // If it's running then don't do anything
                            startCameraService();
                            startServiceTimer(recordDuration);
                            setCaptureButtonText((Button) findViewById(R.id.button_service), "Stop Service");
                        }else if( (isServiceRun) && (isRecording)) {
                            // If service is runnung and it's recording
                            // We will stop the service
                            stopCameraService();
                            stopServiceTimer();
                            setCaptureButtonText((Button) findViewById(R.id.button_service), "Start Service");
                        }
                    }
                }
        );

        // Create an intent filter to recieve batery charge event
        ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        BroadcastReceiver smsReceiver = new BroadcastReceiver(){

            @Override
            public void onReceive(Context context, Intent intent) {
                int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
                boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;
                if(usbCharge || acCharge ){
                    Toast.makeText(MainCam.this,"Phone is charging",Toast.LENGTH_LONG).show();
                    /*if(!MainCam.isServiceRun && !MainCam.isRecording && MainCam.mCameraService != null) {
                        // This check will make sure the first run will not kick off the service as
                        // it has been taken care
                        startCameraService();
                        startServiceTimer(recordDuration);
                        setCaptureButtonText((Button) findViewById(R.id.button_service), "Stop Service");
                    }*/
                }

            }
        };
        Intent batteryStatus = this.registerReceiver(smsReceiver, ifilter);

    }


    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onLocationChanged(Location location) {
        // TODO Auto-generated method stub
        if(location != null)
        {
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

    }

    @Override
    public void onGpsStatusChanged(int event) {
        // TODO Auto-generated method stub

    }

    /* Method Definition*/
    /** A safe way to get an instance of the Camera object. */
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

    public static void initialCamera(Camera.Parameters params, Camera mCamera) {
        // set the focus mode
        List<String> focusModes = params.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        List<String> whiteBalance = params.getSupportedWhiteBalance();
        if (whiteBalance.contains(Camera.Parameters.WHITE_BALANCE_AUTO)) {
            params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        }
        List<String> sceneMode = params.getSupportedSceneModes();
        if (sceneMode.contains(Camera.Parameters.SCENE_MODE_AUTO)) {
            params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
        }
        params.setExposureCompensation((int)((double)params.getMaxExposureCompensation() / 1.5));
        if (params.isVideoStabilizationSupported()) {
            params.setVideoStabilization(true);
        }
        mCamera.setParameters(params);
    }

    public static boolean prepareVideoRecorder(){

        // Step 1: Unlock and set camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P));

        // Step 4: Set output file
        mMediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());

        // Step 5: Set the preview output
        mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());

        // Step 6: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
            mCamera.lock();
            initialCamera(params,mCamera);
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

    public static void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    public static void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    /** Create a file Uri for saving an image or video */
    public static Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /** Create a File for saving an image or video */
    public static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), "CarCam");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("CarCam", "failed to create directory");
                return null;
            }
        }
        // Sorting Save directory and delete file if needed
        sortSaveDir(mediaStorageDir,maxSize,recordDuration);
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

    static public void sortSaveDir(File mainDir, int maxSize, int recordDuration) {
        // Size will be in Gb and time to 1000 to get to mb.
        // The reference point to determine how many file can be store is 10 min ~ 300 mB file
        // Duration will be determin in minute
        // Number of file = (maxSize * 1000) / (recordDuration * 300 / 10)

        int numberOfFile = (maxSize * 1000) / (recordDuration * 300 / 10);

        if(mainDir.exists()) {
            // If Maindir exists. we start checking
            File[] listFiles =  mainDir.listFiles();
            Arrays.sort(listFiles);
            if(listFiles.length >= numberOfFile) {
                // We delete the first file
                listFiles[listFiles.length - 1].delete();
            }else
                return;
        }else{
            // If mainDir doesn't exists do nothing
            return;
        }
    }

    public void setCaptureButtonText(Button button, String newText) {
        button.setText(newText);
    }

    public boolean startCamera() {
        // initialize video camera
        if (prepareVideoRecorder()) {
            // Camera is available and unlocked, MediaRecorder is prepared,
            // now you can start recording
            mMediaRecorder.start();
            isRecording = true;
            return true;
        } else {
            // prepare didn't work, release the camera
            releaseMediaRecorder();
            // inform user
            return false;
        }
    }

    public void stopCamera() {
        // Stop camera
        // stop recording and release camera
        mMediaRecorder.stop();  // stop the recording
        mCamera.lock();         // take camera access back from MediaRecorder
        isRecording = false;
    }

    public void startTimer(int duration) {
        initialTimer();
        mTimer.schedule(mTimerTask, duration*60*1000, duration*60*1000); //
    }

    public void stopTimer() {
        Toast.makeText(MainCam.this,"Stopping Timer Task",Toast.LENGTH_LONG).show();
        mTimer.cancel();
    }

    public void initialTimer() {
        // Set up timer and timertask
        mTimer = new Timer();
        mTimerTask = new TimerTask() {
            @Override
            public void run() {
                //task to run for timmer.
                // This task will first check if camera is in used?
                // If yes, it will save stop the cam, save to file and start a new service
                Log.d("TimerTask","Starting timer");
                if(isRecording){
                    stopCamera();
                    Log.d("TimerTask","stop camera in timer task");
                    // inform the user that recording has stopped
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setCaptureButtonText((Button) findViewById(R.id.button_capture),"Capture");
                        }
                    });
                    startCamera();
                    Log.d("TimerTask","Start camera in timer task");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setCaptureButtonText((Button) findViewById(R.id.button_capture),"Stop");
                        }
                    });
                }

            }
        };
    }

    public void startCameraService() {
        if(mCameraService != null) {
            mCameraService.startCamera();
            isServiceRun = true;
            isRecording = true;
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
                if(isRecording && isServiceRun){
                    // Stop the camera when service is running and using camera
                    stopCameraService();
                    Log.d("TimerTask","stop camera in service timer task");
                    // inform the user that recording has stopped
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setCaptureButtonText((Button) findViewById(R.id.button_service),"Start Service");
                        }
                    });

                    Log.d("TimerTask","Start camera in service timer task");
                    startCameraService();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setCaptureButtonText((Button) findViewById(R.id.button_service),"Stop Service");
                        }
                    });
                }

            }
        };
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
        String strUnits = "mph";

        TextView txtCurrentSpeed = (TextView) this.findViewById(R.id.txtCurrentSpeed);
        txtCurrentSpeed.setText(strCurrentSpeed + " " + strUnits);
    }
}
