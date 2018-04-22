package com.example.hieubui.carcam;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainCam extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_cam);
    }

    public void captureVid(View mainview) {

    }

    public File getFilePath() {
        File mainFolder = new File("/storage/sdcard0/CarCam");
        if(!mainFolder.exists()) {
            mainFolder.mkdir();
        }
        File captureVideo = new File(mainFolder,"sample_vid");
        return null;
    }

}
