package com.weidi.mirrorcast;

import android.app.Application;
import android.content.Context;

/***
 Created by root on 21-6-29.
 */

public class MyApplication extends Application {

    private static final String TAG =
            MyApplication.class.getSimpleName();

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        MyJni.getDefault().setContext(this.getApplicationContext());
        MyJni.getDefault().onTransact(MyJni.DO_SOMETHING_CODE_init, null);
    }

}
