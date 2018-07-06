package com.example.hieubui.carcam;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;
import java.io.IOException;
import static android.content.ContentValues.TAG;

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
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mSurface = mHolder.getSurface();
    }

    public CameraPreview(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // TODO Auto-generated constructor stub
    }

    public CameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TODO Auto-generated constructor stub
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
            if(!MainCam.isRecording && !MainCam.isServiceRun) {
                startCameraService();
                ImageButton serviceButton = (ImageButton) ((Activity) mcontext).findViewById(R.id.button_service);
                serviceButton.setBackgroundResource(R.drawable.stop_record);

            }
        } catch (IOException e) {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
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

    public void startCameraService() {
        Log.d("Camera Preview","Starting Service for first run");
        Intent intent = new Intent(mcontext, CameraService.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mcontext.startService(intent);
    }
}