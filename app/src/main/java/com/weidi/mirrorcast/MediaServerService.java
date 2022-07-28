package com.weidi.mirrorcast;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import static com.weidi.mirrorcast.Constants.START_MAINACTIVITY;
import static com.weidi.mirrorcast.Constants.START_SERVER;
import static com.weidi.mirrorcast.Constants.STOP_SERVER;

public class MediaServerService extends Service {

    public MediaServerService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        internalOnCreate();
    }

    @Override
    public void onDestroy() {
        internalOnDestroy();
        super.onDestroy();
    }

    private static final String TAG = "player_alexander";
    private WindowManager mWindowManager;
    private View mView;

    private boolean mIsServerStarted = false;

    private void internalOnCreate() {
        Log.i(TAG, "MediaServerService internalOnCreate()");
        Phone.register(this);

        // 为了进程保活
        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        LayoutInflater inflater =
                (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mView = inflater.inflate(R.layout.transparent_layout, null);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        // 创建非模态,不可碰触
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        layoutParams.gravity = Gravity.TOP + Gravity.START;
        layoutParams.width = 1;
        layoutParams.height = 1;
        layoutParams.x = 0;
        layoutParams.y = 0;
        mWindowManager.addView(mView, layoutParams);

        onEvent(START_SERVER, null);
    }

    private void internalOnDestroy() {
        Log.i(TAG, "MediaServerService internalOnDestroy()");
        onEvent(STOP_SERVER, null);

        if (mWindowManager != null && mView != null) {
            mWindowManager.removeView(mView);
            mWindowManager = null;
            mView = null;
        }

        Phone.unregister(this);
    }

    private Object onEvent(int what, Object[] objArray) {
        Object result = null;
        switch (what) {
            case START_SERVER: {
                if (!mIsServerStarted) {
                    mIsServerStarted = true;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (MyJni.USE_TRANSMISSION_FOR_JNI) {
                                if (MyJni.USE_TCP) {
                                    MyJni.getDefault().onTransact(
                                            MyJni.DO_SOMETHING_CODE_Server_accept, null);
                                } else {

                                }
                            } else {
                                if (MyJni.USE_TCP) {

                                } else {
                                    MediaServer.getInstance().start();
                                }
                            }
                        }
                    }).start();
                }
                break;
            }
            case STOP_SERVER: {
                if (mIsServerStarted) {
                    mIsServerStarted = false;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (MyJni.USE_TRANSMISSION_FOR_JNI) {
                                if (MyJni.USE_TCP) {
                                    MyJni.getDefault().onTransact(
                                            MyJni.DO_SOMETHING_CODE_Server_close, null);
                                } else {

                                }
                            } else {
                                if (MyJni.USE_TCP) {

                                } else {
                                    MediaServer.getInstance().close();
                                }
                            }
                        }
                    }).start();
                }
                break;
            }
            case START_MAINACTIVITY: {
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setClass(getApplicationContext(), MainActivity.class);
                getApplicationContext().startActivity(intent);
                Log.i(TAG, "MediaServerService START_MAINACTIVITY");
                break;
            }
            default:
                break;
        }
        return result;
    }

}