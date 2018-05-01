package com.example.hieubui.carcam;

import android.app.Service;
import android.content.Intent;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;



public class CameraService extends Service {

    // Binder given to clients
    private final IBinder mBinder = new CameraBinder();
    private boolean duplicateRun = false;

    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if((MainCam.isRecording) || (MainCam.isServiceRun)) {
            Log.d("Camera Service", "Camera is in used or Camera Service already run. Stop the camera first. Stop starting this service ");
            Toast.makeText(MainApp.getContext(),"Camera is in used or Camera Service already run. Stop starting this service ",Toast.LENGTH_LONG).show();
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
        if(duplicateRun){
            // Check if we are destroying duplicate run
            // If yes, we don't do anything
            duplicateRun = false;
            Toast.makeText(MainApp.getContext(), "Destroying duplicate camera service", Toast.LENGTH_LONG).show();

        }else {
            if ((MainCam.isRecording) && (MainCam.isServiceRun)) {
                Toast.makeText(MainApp.getContext(), "Destroying camera service", Toast.LENGTH_LONG).show();
                //We only stop recording if service is running and it's recording
                MainCam.mMediaRecorder.stop();  // stop the recording
                MainCam.mCamera.lock();         // take camera access back from MediaRecorder
                MainCam.releaseMediaRecorder();       // if you are using MediaRecorder, release it first
                MainCam.releaseCamera();              // release the camera immediately on pause event
                // inform the user that recording has started
                MainCam.isRecording = false;
                MainCam.isServiceRun = false;

            }
        }
    }

    public void stopCamera() {
        // This method will stop and save to vid file
        // used when timertask need to save camera to a file on scheduled time
        // Stop camera
        // stop recording and release camera
        MainCam.mMediaRecorder.stop();  // stop the recording
        MainCam.mCamera.lock();         // take camera access back from MediaRecorder
        MainCam.isRecording = false;
    }

    public void startCamera() {
        // initialize video camera
        if (MainCam.prepareVideoRecorder()) {
            // Camera is available and unlocked, MediaRecorder is prepared,
            // now you can start recording
            Log.d("Camera Service", "Camera Service start now");
            //Toast.makeText(MainApp.getContext(),"Camera Service start now",Toast.LENGTH_LONG).show();
            MainCam.mMediaRecorder.start();
            // inform the user that recording has started
            MainCam.isRecording = true;
            MainCam.isServiceRun = true;
        } else {
            // prepare didn't work, release the camera
            Log.d("Camera Service", "Camera Service can not acquire a camera");
            //Toast.makeText(MainApp.getContext(),"Camera Service can not acquire a camera",Toast.LENGTH_LONG).show();
            MainCam.releaseMediaRecorder();
            // inform the user that recording has started
            MainCam.isRecording = false;
            MainCam.isServiceRun = false;
            stopSelf();

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
