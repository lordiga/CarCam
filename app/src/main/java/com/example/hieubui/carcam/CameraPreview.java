package com.example.hieubui.carcam;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.widget.ImageButton;
import java.io.IOException;
import static android.content.ContentValues.TAG;
import static android.content.Context.WINDOW_SERVICE;
import static com.example.hieubui.carcam.MainCam.cameraId;
import static com.example.hieubui.carcam.MainCam.getCameraInstance;

/** A basic Camera preview class */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    public SurfaceHolder mHolder;
    public Camera mCamera;
    public Context mcontext;
    public Surface mSurface;
    public CameraPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;
        mcontext = context;
        mHolder = getHolder();
        mHolder.addCallback(this);
        mSurface = mHolder.getSurface();
    }

    public CameraPreview(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public CameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        Log.d("Camera Preview", "Camera Surface created !!!");
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

            if(MainCam.MainCam.mpref.getBoolean("recordOnPreview", false) ) {
                if(!MainCam.MainCam.isRecording) {
                    final ImageButton serviceButton = (ImageButton) MainCam.MainCam.findViewById(R.id.button_service);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            MainCam.MainCam.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    serviceButton.performClick();
                                }
                            });
                        }
                    }).start();
                }
            }
        } catch (IOException e) {
            Log.d("Camera Preview", "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
        Log.d("Camera Preview", "Camera Surface Destroyed !!!");

        // If we recording on background and get here we need to add a new camera preview to the main view
        // Removing camera preview already handled within service
        if(CameraService.cameraService != null && CameraService.cameraService.isServiceRun) {
            Log.d("Camera Preview", "Removing camera service !!!");
            CameraService.cameraService.isServiceRun = false;
            // Get a fresh camera and preview for main cam
            MainCam.MainCam.mCamera = getCameraInstance(cameraId);
            MainCam.MainCam.mPreview = new CameraPreview(MainCam.MainCam, MainCam.MainCam.mCamera);
            MainCam.MainCam.preview.addView(MainCam.MainCam.mPreview);
        }else {
            // If we not record on background at all. We need to remove the preview from main activity
            // This is the default behavior or surface detroy as camera server will die after this anyway
            if (MainCam.MainCam.isRecording) {
                MainCam.MainCam.stopCamera();
            }
            // Release the camera
            MainCam.MainCam.releaseCamera();
            MainCam.MainCam.mPreview = null;
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }
        // stop preview before making changes
        try {
            mCamera.stopPreview();
            mCamera.setDisplayOrientation(90);
            Display display = ((WindowManager)getContext().getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
            if(display.getRotation() == Surface.ROTATION_0) {
                mCamera.setDisplayOrientation(90);
            }

            if(display.getRotation() == Surface.ROTATION_90) {
                mCamera.setDisplayOrientation(0);
            }

            if(display.getRotation() == Surface.ROTATION_270) {
                mCamera.setDisplayOrientation(180);
            }
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }
        // set preview size and make any resize, rotate or
        // reformatting changes here
        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

        } catch (Exception e){
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

}