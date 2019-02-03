package com.example.hieubui.carcam;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.StatFs;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static android.content.ContentValues.TAG;
import static com.example.hieubui.carcam.MainCam.mpref;

public class CameraService extends Service{

    // Binder given to clients
    private final IBinder mBinder = new CameraBinder();
    private boolean duplicateRun = false;
    static SensorManager mySensorManager;
    static SensorEventListener LightSensorListener;
    static boolean darkLight;
    static boolean extremeDarkLight;
    static int senceMode;
    static CameraPreview mPreview;
    static WindowManager windowManager;
    static WindowManager.LayoutParams layoutParams;
    static MediaRecorder mMediaRecorder;
    static boolean surfacePreviewDetroyed = false;
    static boolean isRecording = false;
    static boolean isServiceRun = false;
    static Camera mCamera;
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    static int availableStorage;
    static Camera.Parameters params;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        // Create an instance of Camera
        mCamera = getCameraInstance();
        if (mCamera != null) {
            params = mCamera.getParameters();
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
        mMediaRecorder = new MediaRecorder();
        // Create our Preview view.
        mPreview = new CameraPreview(this, mCamera);
        windowManager =(WindowManager)getSystemService(WINDOW_SERVICE);
        //FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        layoutParams = new WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.CENTER;

        windowManager.addView(mPreview, layoutParams);
        //preview.addView(mPreview);
        try {
            availableStorage = Integer.parseInt(mpref.getString("availableStorage", "700"));
        } catch (NumberFormatException e) {
            availableStorage = 700;
        }

        if((isRecording) || (isServiceRun)) {
            Log.d("Camera Service", "Camera is in used or Camera Service already run. Stop the camera first. Stop starting this service ");
            duplicateRun = true;
            stopSelf();
        }else {
            Log.d("Camera Service", "Service Starting!!! ");
             startCamera();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(duplicateRun) {
            // Check if we are destroying duplicate run
            // If yes, we don't do anything
            duplicateRun = false;
            Log.d("Camera Service","Destroying duplicate camera service");

        }else {
            if((isRecording) && (isServiceRun)) {
                Log.d("Camera Service","Destroying camera service");
                //We only stop recording if service is running and it's recording
                mMediaRecorder.stop();  // stop the recording
                mCamera.lock();         // take camera access back from MediaRecorder
                releaseMediaRecorder();       // if you are using MediaRecorder, release it first
                releaseCamera();              // release the camera immediately on pause event
                // inform the user that recording has started
                isRecording = false;
                isServiceRun = false;

            }
        }
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
            Log.d("Camera Service", "Camera Service start now");

            mMediaRecorder.start();
            // inform the user that recording has started
            isRecording = true;
            isServiceRun = true;
        } else {
            // prepare didn't work, release the camera
            Log.d("Camera Service", "Camera Service can not acquire a camera");
            releaseMediaRecorder();
            // inform the user that recording has started
            isRecording = false;
            isServiceRun = false;
            stopSelf();
        }

    }

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
            if (whiteBalance.contains(Camera.Parameters.WHITE_BALANCE_SHADE)) {
                params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_SHADE);
            }
            if (sceneMode.contains(Camera.Parameters.SCENE_MODE_NIGHT)) {
                params.setSceneMode(Camera.Parameters.SCENE_MODE_NIGHT);
            }
            Log.d("Darklight", "Set to 2/3 of Max EC " + params.getMaxExposureCompensation() );

        }else if(extremeDarkLight) {
            params.setExposureCompensation(params.getMaxExposureCompensation());
            if (whiteBalance.contains(Camera.Parameters.WHITE_BALANCE_SHADE)) {
                params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_SHADE);
            }
            if (sceneMode.contains(Camera.Parameters.SCENE_MODE_NIGHT)) {
                params.setSceneMode(Camera.Parameters.SCENE_MODE_NIGHT);
            }
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

    public static boolean prepareVideoRecorder(){
        // Step 1: Unlock and set camera to MediaRecorder
        if(mCamera == null) {
            mCamera = getCameraInstance();
            params = mCamera.getParameters();
        }
        mCamera.unlock();
        if(mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }
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
        if(!surfacePreviewDetroyed) {
            // Step 5: Set the preview output
            mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());
        }

        // Step 6: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
            mCamera.lock();
            configSenseModeCamera(params,mCamera);
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

    /** Create a File for saving an image or video */
    public static File getOutputMediaFile(int type, int availableStorage){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File("/storage/sdcard0/CarCam");
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

    public static int getAvailableInternalMemorySize() {
        File path = new File("/storage/sdcard0/");
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return formatSize(availableBlocks * blockSize);
    }

    public static int formatSize(long size) {
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

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class CameraBinder extends Binder {
        CameraService getService() {
            // Return this instance of LocalService so clients can call public methods
            return CameraService.this;
        }
    }

}
