package com.example.hieubui.carcam;

import android.app.Application;
import android.content.Context;

public class MainApp extends Application {
    private static Application mainApp;

    @Override
    public void onCreate() {
        super.onCreate();
        mainApp = this;
    }

    public static Context getContext() {
        return mainApp.getApplicationContext();
    }
}
