package com.example.hieubui.carcam;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;


public class CameraService extends Service{

    /**Binder given to clients*/
    private final IBinder mBinder = new CameraBinder();
    private boolean duplicateRun = false;
    boolean isServiceRun = false;
    /**WindowManager*/
    WindowManager windowManager;
    WindowManager.LayoutParams layoutParams;

    static CameraService  cameraService;

    /**Service life cycle*/
    @Override
    public void onCreate() {
        super.onCreate();
        cameraService = this;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d("Camera Service", "Service Starting!!! ");
        isServiceRun = true;
        windowManager =(WindowManager)getSystemService(WINDOW_SERVICE);
        layoutParams = new WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.CENTER;
        windowManager.addView(MainCam.MainCam.mPreview, layoutParams);
        MainCam.MainCam.startScheduler(MainCam.MainCam.recordDuration);
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if((MainCam.MainCam.isRecording) && (isServiceRun)) {
            Log.d("Camera Service","Destroying camera service");
            //We only stop recording if service is running and it's recording
            MainCam.MainCam.stopScheduler();
            MainCam.MainCam.stopCamera();
            windowManager.removeView(MainCam.MainCam.mPreview);
        }else {
            isServiceRun = false;
        }
        return super.onUnbind(intent);

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
