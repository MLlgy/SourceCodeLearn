package com.example.mkio.importsource;

import android.app.Application;
import android.content.Context;


public class InitApp extends Application {

    public static Context AppContext;

    @Override
    public void onCreate() {
        super.onCreate();
        AppContext = getApplicationContext();
    }

}
