package com.weidi.mirrorcast;

import android.app.ActivityManager;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.sony.p2plib.activity.BaseActivity;
import com.sony.p2plib.p2phelper.WifiP2PHelper;
import com.sony.p2plib.p2phelper.WifiP2PListener;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;

import androidx.annotation.NonNull;

import static com.weidi.mirrorcast.Constants.ACCELEROMETER_ROTATION;
import static com.weidi.mirrorcast.Constants.IS_RECORDING;
import static com.weidi.mirrorcast.Constants.MAINACTIVITY_ON_RESUME;
import static com.weidi.mirrorcast.Constants.RELEASE;
import static com.weidi.mirrorcast.Constants.SET_ACTIVITY;
import static com.weidi.mirrorcast.Constants.SET_IP_AND_PORT;
import static com.weidi.mirrorcast.Constants.SET_MEDIAPROJECTION;
import static com.weidi.mirrorcast.Constants.START_MAINACTIVITY;
import static com.weidi.mirrorcast.Constants.START_RECORD_SCREEN;
import static com.weidi.mirrorcast.Constants.STOP_RECORD_SCREEN;
import static com.weidi.mirrorcast.MyJni.DO_SOMETHING_CODE_get_server_port;
import static com.weidi.mirrorcast.MyJni.DECODER_MEDIA_CODEC_GO_JNI;
import static com.weidi.mirrorcast.MyJni.DO_SOMETHING_CODE_only_output_key_frame;
import static com.weidi.mirrorcast.PlayerActivity.MAXIMUM_NUMBER;
import static com.weidi.mirrorcast.PlayerActivity.PLAYER_ACTIVITY_IS_LIVE;

public class MainActivity extends BaseActivity {

    private static final String TAG =
            "player_alexander";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate() savedInstanceState: " + savedInstanceState);
        setContentView(R.layout.activity_main);

        internalOnCreate();
    }

    /*********************************
     * Started
     *********************************/

    @Override
    public void onStart() {
        super.onStart();
        Log.i(TAG, "onStart()");
    }

    /*********************************
     * Resumed
     *********************************/

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume()");

        internalOnResume();
    }

    /*********************************
     * Paused
     *********************************/

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "onPause()");
    }

    /*********************************
     * Stopped
     *********************************/

    @Override
    public void onStop() {
        super.onStop();
        Log.i(TAG, "onStop()");

        internalOnStop();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        internalOnDestroy();

        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(TAG, "onNewIntent()");
        internalonNewIntent(intent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null) {
            Log.i(TAG, "onActivityResult()" +
                    " requestCode: " + requestCode +
                    " resultCode: " + resultCode +
                    " data: " + data.toString());
        }

        internalonActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //Log.i(TAG, "onSaveInstanceState() outState: " + outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.i(TAG, "onLowMemory()");
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        //Log.i(TAG, "onTrimMemory() level: " + level);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.i(TAG, "onRequestPermissionsResult() requestCode: " + requestCode);
    }

    @Override
    public void onBackPressed() {
        Object object = EventBusUtils.post(
                MediaClientService.class, IS_RECORDING, null);
        if (object != null) {
            boolean isRecording = (boolean) object;
            if (isRecording) {
                moveTaskToBack(true);
                return;
            }
        }

        super.onBackPressed();
        finish();
    }

    /*@Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP: {
                    Intent intent = new Intent();
                    Bundle bundle = new Bundle();
                    bundle.putString("OBJECT", "Server");
                    intent.putExtras(bundle);
                    internalonNewIntent(intent);
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_DOWN: {
                    Intent intent = new Intent();
                    Bundle bundle = new Bundle();
                    bundle.putString("OBJECT", "Client");
                    bundle.putString("IP", "192.168.0.120");
                    bundle.putInt("PORT", 5859);
                    intent.putExtras(bundle);
                    internalonNewIntent(intent);
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    break;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }*/

    protected WifiP2PListener getListener() {
        return mWifiP2PListener;
    }

    /////////////////////////////////////////////////////////////////

    public static final String MEDIASERVERSERVICE =
            "com.weidi.mirrorcast.MediaServerService";
    public static final String MEDIACLIENTSERVICE =
            "com.weidi.mirrorcast.MediaClientService";

    public static final String FLAG = "@@@@@";
    public static boolean NEED_TO_SAVE = false;

    private static final int REQUEST_CODE = 1000;
    private static final int PREPARE = 0x0001;
    private static final int REQUEST_PERMISSION = 0x0002;
    //private static final int START_RECORD_SCREEN = 0x0003;
    //private static final int STOP_RECORD_SCREEN = 0x0004;
    //private static final int RELEASE = 0x0005;
    private static final int GO_BACKGROUND = 0x0006;
    private static final int MSG_UI_CLICK_BTN1 = 0x0007;
    private static final int MSG_UI_CLICK_BTN2 = 0x0008;
    private static final int MSG_UI_CLICK_BTN3 = 0x0009;
    private static final int MSG_UI_CLICK_BTN4 = 0x0010;
    private static final int MSG_UI_CLICK_BTN5 = 0x0011;
    private static final int MSG_UI_CLICK_BTN6 = 0x0012;
    private static final int MSG_UI_CLICK_BTN7 = 0x0013;
    private static final int INTERNAL_ON_RESUME = 0x0014;
    private static final int ON_CONNECTION_INFO_AVAILABLE = 0x0015;
    private static final int ON_PEERS_AVAILABLE = 0x0016;
    private static final int DISCOVER = 0x0017;

    private TextView mTitleView;
    private EditText mIpET;
    private EditText mPortET;
    private Button mBtn1;
    private Button mBtn2;
    private Button mBtn3;
    private Button mBtn4;
    private Button mBtn5;
    private Button mBtn6;
    private Button mBtn7;
    private TextView mTv0;
    private TextView mTv1;
    private TextView mTv2;
    private TextView mTv3;
    private TextView mTv4;
    private TextView mTv5;
    private TextView mTv6;
    private TextView mTv7;
    private TextView mTv8;
    private TextView mTv9;
    private SparseArray mTvArray = new SparseArray();

    private NetWorkReceiver mNetWorkReceiver;
    private ConnectivityManager mConnectivityManager;
    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private int whatIsDevice = 1;
    private String IP;
    private int PORT;

    private HandlerThread mHandlerThread;
    private Handler mThreadHandler;
    private Handler mUiHandler;

    private boolean mIsServerLive;
    private boolean mIsClientLive;
    private Intent mMediaServerIntent;
    private Intent mMediaClientIntent;

    private long mStartTime;
    private long mEndTime;

    private void internalOnCreate() {
        activity = this;
        EventBusUtils.register(this);
        EventBusUtils.post(MediaClientService.class, SET_ACTIVITY, new Object[]{MainActivity.this});
        mTitleView = findViewById(R.id.title_tv);
        mIpET = findViewById(R.id.ip_et);
        mPortET = findViewById(R.id.port_et);
        mBtn1 = findViewById(R.id.btn1);
        mBtn2 = findViewById(R.id.btn2);
        mBtn3 = findViewById(R.id.btn3);
        mBtn4 = findViewById(R.id.btn4);
        mBtn5 = findViewById(R.id.btn5);
        mBtn6 = findViewById(R.id.btn6);
        mBtn7 = findViewById(R.id.btn7);
        mTv0 = findViewById(R.id.tv0);
        mTv1 = findViewById(R.id.tv1);
        mTv2 = findViewById(R.id.tv2);
        mTv3 = findViewById(R.id.tv3);
        mTv4 = findViewById(R.id.tv4);
        mTv5 = findViewById(R.id.tv5);
        mTv6 = findViewById(R.id.tv6);
        mTv7 = findViewById(R.id.tv7);
        mTv8 = findViewById(R.id.tv8);
        mTv9 = findViewById(R.id.tv9);
        mBtn1.setOnClickListener(mOnClickListener);
        mBtn2.setOnClickListener(mOnClickListener);
        mBtn3.setOnClickListener(mOnClickListener);
        mBtn4.setOnClickListener(mOnClickListener);
        mBtn5.setOnClickListener(mOnClickListener);
        mBtn6.setOnClickListener(mOnClickListener);
        mBtn7.setOnClickListener(mOnClickListener);
        mTv0.setOnClickListener(mOnClickListener);
        mTv1.setOnClickListener(mOnClickListener);
        mTv2.setOnClickListener(mOnClickListener);
        mTv3.setOnClickListener(mOnClickListener);
        mTv4.setOnClickListener(mOnClickListener);
        mTv5.setOnClickListener(mOnClickListener);
        mTv6.setOnClickListener(mOnClickListener);
        mTv7.setOnClickListener(mOnClickListener);
        mTv8.setOnClickListener(mOnClickListener);
        mTv9.setOnClickListener(mOnClickListener);
        mTvArray.put(0, mTv0);
        mTvArray.put(1, mTv1);
        mTvArray.put(2, mTv2);
        mTvArray.put(3, mTv3);
        mTvArray.put(4, mTv4);
        mTvArray.put(5, mTv5);
        mTvArray.put(6, mTv6);
        mTvArray.put(7, mTv7);
        mTvArray.put(8, mTv8);
        mTvArray.put(9, mTv9);

        mUiHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                MainActivity.this.uiHandleMessage(msg);
            }
        };
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mThreadHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                MainActivity.this.threadHandleMessage(msg);
            }
        };

        mMediaServerIntent = new Intent(this, MediaServerService.class);
        mMediaClientIntent = new Intent(this, MediaClientService.class);

        UiModeManager uiModeManager =
                (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        whatIsDevice = uiModeManager.getCurrentModeType();
        mMediaProjectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        NetworkRequest request = builder.build();
        mConnectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        mConnectivityManager.registerNetworkCallback(request, mNetworkCallback);

        // 注册网络状态监听广播
        /*mNetWorkReceiver = new NetWorkReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mNetWorkReceiver, filter);*/

        /*mWifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mWifiP2pManager.initialize(
                this, getMainLooper(), mChannelListener);
        mReceiver = new WiFiDirectBroadcastReceiver(mWifiP2pManager, mChannel);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        registerReceiver(mReceiver, intentFilter);*/

        /***
         NetWorkReceiver
         只发生在6.0这个版本和部分6.0.1版本中(SecurityException)
         跳转到应用程序设置页打开WRITE_SETTINGS这个权限
         */
        /*Intent goToSettings = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        goToSettings.setData(Uri.parse("package:" + getPackageName()));
        startActivity(goToSettings);*/

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 申请浮窗权限
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 0);
            }
            /*if (!isRunService(this, MEDIASERVERSERVICE)) {
                if (!Settings.canDrawOverlays(this)) {
                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, 0);
                } else {
                    if (I_AM_SERVER) {
                        startService(new Intent(this, MediaServerService.class));
                    } else {
                        startService(new Intent(this, MediaClientService.class));
                    }
                }
            }*/
        }

        internalonNewIntent(getIntent());
    }

    /***
     代码执行的内容跟onStart(),onResume()一样,
     因此在某些情况下要么执行onStart(),onResume()方法,要么执行onShow()方法.
     一般做的事是设置View的内容
     */
    private void internalOnResume() {
        mIsServerLive = isRunService(this, MEDIASERVERSERVICE);
        mIsClientLive = isRunService(this, MEDIACLIENTSERVICE);
        String IP = getIPAddress();
        String info = IP;
        if (!mIsServerLive && !mIsClientLive) {
            mIpET.setVisibility(View.GONE);
            mPortET.setVisibility(View.GONE);
            mBtn1.setVisibility(View.VISIBLE);
            mBtn2.setVisibility(View.VISIBLE);
            mBtn4.setVisibility(View.GONE);
            mBtn5.setVisibility(View.GONE);
            mBtn6.setVisibility(View.GONE);
            mBtn7.setVisibility(View.GONE);
            hideAllWifiP2pDevice();
            mBtn1.setText("作为服务端");
            mBtn2.setText("作为客户端");
        } else if (mIsServerLive) {
            mBtn1.setVisibility(View.VISIBLE);
            mBtn7.setVisibility(View.VISIBLE);
            mBtn2.setVisibility(View.GONE);
            mBtn4.setVisibility(View.GONE);
            mBtn5.setVisibility(View.GONE);
            mBtn6.setVisibility(View.GONE);
            mIpET.setVisibility(View.GONE);
            mPortET.setVisibility(View.GONE);
            mBtn1.setText("停用服务端");
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(IP)) {
                sb.append(IP);
                sb.append("    ");
            }
            sb.append(MyJni.getDefault().onTransact(DO_SOMETHING_CODE_get_server_port, null));
            info = sb.toString();
        } else if (mIsClientLive) {
            mIpET.setVisibility(View.VISIBLE);
            mPortET.setVisibility(View.VISIBLE);
            mBtn1.setVisibility(View.VISIBLE);
            mBtn2.setVisibility(View.VISIBLE);
            mBtn4.setVisibility(View.VISIBLE);
            mBtn5.setVisibility(View.VISIBLE);
            mBtn6.setVisibility(View.VISIBLE);
            mBtn7.setVisibility(View.GONE);
            mBtn1.setText("停用客户端");
            Object object = EventBusUtils.post(
                    MediaClientService.class, IS_RECORDING, null);
            if (object != null) {
                boolean isRecording = (boolean) object;
                if (isRecording) {
                    mBtn2.setText("停止录屏");
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                    EventBusUtils.post(
                            MediaClientService.class, SET_ACTIVITY, null);
                } else {
                    mBtn2.setText("开始录屏");
                }
            }
        }
        mTitleView.setText(info);
        mBtn3.setText("去后台");
        mBtn4.setVisibility(View.GONE);// 效果不理想,不展示
        mBtn4.setText("关键帧");
        mBtn5.setText("P2P扫描");
        mBtn6.setText("P2P断开");
        mBtn7.setText("Wifi Direct");

        EventBusUtils.post(
                MediaClientService.class, ACCELEROMETER_ROTATION, null);
    }

    private void internalOnStop() {

    }

    private void internalOnDestroy() {
        activity = null;
        mUiHandler.removeMessages(ON_CONNECTION_INFO_AVAILABLE);
        mUiHandler.removeMessages(ON_PEERS_AVAILABLE);
        mUiHandler = null;
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        if (mConnectivityManager != null) {
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
            mConnectivityManager = null;
            mNetworkCallback = null;
        }
        if (mNetWorkReceiver != null) {
            unregisterReceiver(mNetWorkReceiver);
        }

        /*Intent intent = new Intent();
        intent.setAction("com.weidi.mirrorcast.MainActivity");
        intent.putExtra("Activity", "finish");
        sendBroadcast(intent);*/

        EventBusUtils.post(MediaClientService.class, SET_ACTIVITY, null);

        WifiP2PHelper.getInstance(getApplicationContext()).cancelConnect();
        WifiP2PHelper.getInstance(getApplicationContext()).removeGroup();
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
        }
        EventBusUtils.unregister(this);
    }

    // Server Client
    private void internalonNewIntent(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            String object = bundle.getString("OBJECT", "Server");
            if (TextUtils.equals(object, "Server")) {
                mUiHandler.removeMessages(MSG_UI_CLICK_BTN1);
                mUiHandler.sendEmptyMessageDelayed(MSG_UI_CLICK_BTN1, 500);
            } else if (TextUtils.equals(object, "Client")) {
                startService(mMediaClientIntent);
                IP = bundle.getString("IP", "192.168.49.1");
                PORT = bundle.getInt("PORT", 5859);
                Log.i(TAG, "internalonNewIntent() IP: " + IP + " PORT: " + PORT);
                mUiHandler.removeMessages(REQUEST_PERMISSION);
                mUiHandler.sendEmptyMessageDelayed(REQUEST_PERMISSION, 500);
                mUiHandler.removeMessages(INTERNAL_ON_RESUME);
                mUiHandler.sendEmptyMessageDelayed(INTERNAL_ON_RESUME, 1000);
            }
        }
    }

    private void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn1: {
                mUiHandler.removeMessages(MSG_UI_CLICK_BTN1);
                mUiHandler.sendEmptyMessageDelayed(MSG_UI_CLICK_BTN1, 500);
                break;
            }
            case R.id.btn2: {
                mUiHandler.removeMessages(MSG_UI_CLICK_BTN2);
                mUiHandler.sendEmptyMessageDelayed(MSG_UI_CLICK_BTN2, 500);
                break;
            }
            case R.id.btn3: {
                mUiHandler.removeMessages(MSG_UI_CLICK_BTN3);
                mUiHandler.sendEmptyMessageDelayed(MSG_UI_CLICK_BTN3, 500);
                break;
            }
            case R.id.btn4: {
                mUiHandler.removeMessages(MSG_UI_CLICK_BTN4);
                mUiHandler.sendEmptyMessageDelayed(MSG_UI_CLICK_BTN4, 500);
                break;
            }
            case R.id.btn5: {
                mUiHandler.removeMessages(MSG_UI_CLICK_BTN5);
                mUiHandler.sendEmptyMessageDelayed(MSG_UI_CLICK_BTN5, 500);
                break;
            }
            case R.id.btn6: {
                mUiHandler.removeMessages(MSG_UI_CLICK_BTN6);
                mUiHandler.sendEmptyMessageDelayed(MSG_UI_CLICK_BTN6, 500);
                break;
            }
            case R.id.btn7: {
                mUiHandler.removeMessages(MSG_UI_CLICK_BTN7);
                mUiHandler.sendEmptyMessageDelayed(MSG_UI_CLICK_BTN7, 500);
                break;
            }
            case R.id.tv0: {
                mThreadHandler.removeMessages(DISCOVER);
                Message msg = mThreadHandler.obtainMessage();
                msg.what = DISCOVER;
                msg.arg1 = 0;
                mThreadHandler.sendMessageDelayed(msg, 500);
                break;
            }
            case R.id.tv1: {
                mThreadHandler.removeMessages(DISCOVER);
                Message msg = mThreadHandler.obtainMessage();
                msg.what = DISCOVER;
                msg.arg1 = 1;
                mThreadHandler.sendMessageDelayed(msg, 500);
                break;
            }
            case R.id.tv2: {
                mThreadHandler.removeMessages(DISCOVER);
                Message msg = mThreadHandler.obtainMessage();
                msg.what = DISCOVER;
                msg.arg1 = 2;
                mThreadHandler.sendMessageDelayed(msg, 500);
                break;
            }
            case R.id.tv3: {
                mThreadHandler.removeMessages(DISCOVER);
                Message msg = mThreadHandler.obtainMessage();
                msg.what = DISCOVER;
                msg.arg1 = 3;
                mThreadHandler.sendMessageDelayed(msg, 500);
                break;
            }
            case R.id.tv4: {
                mThreadHandler.removeMessages(DISCOVER);
                Message msg = mThreadHandler.obtainMessage();
                msg.what = DISCOVER;
                msg.arg1 = 4;
                mThreadHandler.sendMessageDelayed(msg, 500);
                break;
            }
            case R.id.tv5: {
                mThreadHandler.removeMessages(DISCOVER);
                Message msg = mThreadHandler.obtainMessage();
                msg.what = DISCOVER;
                msg.arg1 = 5;
                mThreadHandler.sendMessageDelayed(msg, 500);
                break;
            }
            case R.id.tv6: {
                mThreadHandler.removeMessages(DISCOVER);
                Message msg = mThreadHandler.obtainMessage();
                msg.what = DISCOVER;
                msg.arg1 = 6;
                mThreadHandler.sendMessageDelayed(msg, 500);
                break;
            }
            case R.id.tv7: {
                mThreadHandler.removeMessages(DISCOVER);
                Message msg = mThreadHandler.obtainMessage();
                msg.what = DISCOVER;
                msg.arg1 = 7;
                mThreadHandler.sendMessageDelayed(msg, 500);
                break;
            }
            case R.id.tv8: {
                mThreadHandler.removeMessages(DISCOVER);
                Message msg = mThreadHandler.obtainMessage();
                msg.what = DISCOVER;
                msg.arg1 = 8;
                mThreadHandler.sendMessageDelayed(msg, 500);
                break;
            }
            case R.id.tv9: {
                mThreadHandler.removeMessages(DISCOVER);
                Message msg = mThreadHandler.obtainMessage();
                msg.what = DISCOVER;
                msg.arg1 = 9;
                mThreadHandler.sendMessageDelayed(msg, 500);
                break;
            }
            default:
                break;
        }
    }

    private Object onEvent(int what, Object[] objArray) {
        Object result = null;
        switch (what) {
            case MAINACTIVITY_ON_RESUME: {
                mUiHandler.removeMessages(INTERNAL_ON_RESUME);
                mUiHandler.sendEmptyMessageDelayed(INTERNAL_ON_RESUME, 1000);
                break;
            }
            default:
                break;
        }

        return result;
    }

    /***
     mSurface=Surface(name=Sys2003:com.android.systemui/com.android.systemui.media
     .MediaProjectionPermissionActivity)
     mSurface=Surface(name=com.android.systemui/com.android.systemui.media
     .MediaProjectionPermissionActivity)
     mSurface=Surface(name=com.weidi.usefragments/com.weidi.usefragments.MainActivity1)

     调用下面代码后的现象:
     弹出一个框,有两个按钮("取消"和"立即开始"),还有一个选择框("不再提示")
     1.只点击"立即开始"按钮
     那么会回调onActivityResult()方法.
     由于没有选择"不再提示",因此下次调用下面代码时还会弹出框让用户进行确认
     2.选择"不再提示",并点击"立即开始"按钮
     那么会回调onActivityResult()方法.
     由于选择过"不再提示",因此下次调用下面代码时不会再弹出框让用户进行确认
     只会回调onActivityResult()方法.
     3.只点击"取消"按钮
     不会回调onActivityResult()方法.
     下次调用下面代码时还会弹出框让用户进行确认
     4.选择"不再提示",并点击"取消"按钮
     不会回调onActivityResult()方法.
     下次调用下面代码时还会弹出框让用户进行确认
     */
    private void requestPermission() {
        Log.i(TAG, "requestPermission()");
        Object object = EventBusUtils.post(
                MediaClientService.class, IS_RECORDING, null);
        if (object != null) {
            boolean isRecording = (boolean) object;
            if (isRecording) {
                Log.e(TAG, "requestPermission() return for is recording");
                Toast.makeText(getApplicationContext(), "正在录屏", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (mMediaProjectionManager != null) {
            startActivityForResult(
                    mMediaProjectionManager.createScreenCaptureIntent(),
                    REQUEST_CODE);
        }
    }

    private void internalonActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case 0: {
                // onActivityResult() requestCode: 0 resultCode: 0 data: null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this)) {
                        Log.i(TAG, "internalonActivityResult() 浮动窗口的权限已申请!!!");
                    } else {
                        Log.e(TAG, "internalonActivityResult() 浮动窗口的权限已拒绝!!!");
                    }
                }
                break;
            }
            case 1: {
                break;
            }
            case REQUEST_CODE: {
                // requestCode: 1000 resultCode: -1 data: Intent { (has extras) }
                // MediaProjection对象是这样来的,所以要得到MediaProjection对象,必须同意权限
                mMediaProjection =
                        mMediaProjectionManager.getMediaProjection(resultCode, data);
                if (mMediaProjection == null) {
                    Log.e(TAG, "internalonActivityResult() mMediaProjection is null");
                    EventBusUtils.post(
                            MediaClientService.class, SET_ACTIVITY,
                            null);
                    EventBusUtils.post(
                            MediaClientService.class, SET_MEDIAPROJECTION,
                            null);
                    EventBusUtils.post(
                            MediaClientService.class, RELEASE,
                            null);
                    return;
                }

                IP = mIpET.getText().toString();
                try {
                    PORT = Integer.parseInt(mPortET.getText().toString());
                } catch (Exception e) {
                    PORT = 5858;
                }

                // test
                //IP = "192.168.0.120";
                //PORT = 5858;

                EventBusUtils.post(
                        MediaClientService.class, SET_ACTIVITY,
                        new Object[]{MainActivity.this});
                EventBusUtils.post(
                        MediaClientService.class, SET_MEDIAPROJECTION,
                        new Object[]{mMediaProjection});
                EventBusUtils.post(
                        MediaClientService.class, SET_IP_AND_PORT,
                        new Object[]{IP, PORT});
                EventBusUtils.post(
                        MediaClientService.class, START_RECORD_SCREEN,
                        null);

                mUiHandler.removeMessages(INTERNAL_ON_RESUME);
                mUiHandler.sendEmptyMessageDelayed(INTERNAL_ON_RESUME, 3000);
                break;
            }
            default:
                break;
        }
    }

    private void hideAllWifiP2pDevice() {
        mTv0.setVisibility(View.GONE);
        mTv1.setVisibility(View.GONE);
        mTv2.setVisibility(View.GONE);
        mTv3.setVisibility(View.GONE);
        mTv4.setVisibility(View.GONE);
        mTv5.setVisibility(View.GONE);
        mTv6.setVisibility(View.GONE);
        mTv7.setVisibility(View.GONE);
        mTv8.setVisibility(View.GONE);
        mTv9.setVisibility(View.GONE);
    }

    private void uiHandleMessage(Message msg) {
        if (msg == null) {
            return;
        }

        switch (msg.what) {
            case REQUEST_PERMISSION: {
                requestPermission();
                break;
            }
            case MSG_UI_CLICK_BTN1: {
                if (!mIsServerLive && !mIsClientLive) {
                    JniObject jniObject = JniObject.obtain();
                    jniObject.valueInt = whatIsDevice;
                    jniObject.valueLong = MAXIMUM_NUMBER;
                    jniObject.valueString = "127.0.0.1";
                    jniObject.valueBoolean = DECODER_MEDIA_CODEC_GO_JNI;
                    MyJni.getDefault().onTransact(MyJni.DO_SOMETHING_CODE_Server_set_ip, jniObject);
                    jniObject = null;
                    startService(mMediaServerIntent);
                } else if (mIsServerLive) {
                    stopService(mMediaServerIntent);
                } else if (mIsClientLive) {
                    stopService(mMediaClientIntent);
                }
                mUiHandler.removeMessages(INTERNAL_ON_RESUME);
                mUiHandler.sendEmptyMessageDelayed(INTERNAL_ON_RESUME, 1000);
                break;
            }
            case MSG_UI_CLICK_BTN2: {
                if (!mIsServerLive && !mIsClientLive) {
                    startService(mMediaClientIntent);
                } else if (mIsServerLive) {
                } else if (mIsClientLive) {
                    Object object = EventBusUtils.post(
                            MediaClientService.class, IS_RECORDING, null);
                    if (object != null) {
                        boolean isRecording = (boolean) object;
                        if (isRecording) {
                            EventBusUtils.post(
                                    MediaClientService.class, STOP_RECORD_SCREEN, null);
                        } else {
                            mUiHandler.removeMessages(REQUEST_PERMISSION);
                            mUiHandler.sendEmptyMessageDelayed(REQUEST_PERMISSION, 500);
                        }
                    }
                }
                mUiHandler.removeMessages(INTERNAL_ON_RESUME);
                mUiHandler.sendEmptyMessageDelayed(INTERNAL_ON_RESUME, 1000);
                break;
            }
            case MSG_UI_CLICK_BTN3: {
                moveTaskToBack(true);
                break;
            }
            case MSG_UI_CLICK_BTN4: {
                JniObject jniObject = JniObject.obtain();
                MyJni.getDefault().onTransact(DO_SOMETHING_CODE_only_output_key_frame, jniObject);
                Toast.makeText(
                        MainActivity.this,
                        String.valueOf(jniObject.valueBoolean),
                        Toast.LENGTH_SHORT).show();
                jniObject = null;
                break;
            }
            case MSG_UI_CLICK_BTN5: {
                WifiP2PHelper.getInstance(getApplicationContext()).discover();
                mUiHandler.removeMessages(INTERNAL_ON_RESUME);
                mUiHandler.sendEmptyMessageDelayed(INTERNAL_ON_RESUME, 1000);
                break;
            }
            case MSG_UI_CLICK_BTN6: {
                WifiP2PHelper.getInstance(getApplicationContext()).cancelConnect();
                WifiP2PHelper.getInstance(getApplicationContext()).removeGroup();
                hideAllWifiP2pDevice();
                mUiHandler.removeMessages(INTERNAL_ON_RESUME);
                mUiHandler.sendEmptyMessageDelayed(INTERNAL_ON_RESUME, 1000);
                break;
            }
            case MSG_UI_CLICK_BTN7: {
                try {
                    /*if (ActivityCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.ACCESS_FINE_LOCATION) !=
                            PackageManager.PERMISSION_GRANTED) {
                        return;
                    }*/
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    ComponentName cm = null;
                    if (whatIsDevice == Configuration.UI_MODE_TYPE_TELEVISION) {
                        mStartTime = SystemClock.elapsedRealtime();
                        cm = new ComponentName(
                                "com.sony.dtv.networkapp.wifidirect",
                                "com.sony.dtv.networkapp.wifidirect.ui.screens" +
                                        ".HomeWaitingActivity");
                    } else {
                        /***
                         com.android.settings.SubSettings(Wifi Direct界面)
                         手机端只能打开无线网连接界面,不能打开Wifi Direct界面,会发生SecurityException.
                         */
                        cm = new ComponentName(
                                "com.android.settings",
                                "com.android.settings.Settings$WifiSettingsActivity");
                    }
                    intent.setComponent(cm);
                    startActivityForResult(intent, 1);
                } catch (Exception e) {
                    Log.e(TAG, "MSG_UI_CLICK_BTN6 " + e.toString());
                    e.printStackTrace();
                }
                break;
            }
            case INTERNAL_ON_RESUME: {
                internalOnResume();
                break;
            }
            case ON_CONNECTION_INFO_AVAILABLE: {
                if (msg.obj == null) {
                    return;
                }
                WifiP2pInfo wifiP2pInfo = (WifiP2pInfo) msg.obj;
                if (wifiP2pInfo != null
                        && wifiP2pInfo.groupFormed
                        && wifiP2pInfo.groupOwnerAddress != null) {
                    String hostAddress = wifiP2pInfo.groupOwnerAddress.getHostAddress();
                    String ip = getIPAddress();
                    Log.i(TAG, "ON_CONNECTION_INFO_AVAILABLE           hostAddress: " +
                            hostAddress);
                    Log.i(TAG, "ON_CONNECTION_INFO_AVAILABLE                    ip: " + ip);
                    mIpET.setText(hostAddress);
                    mPortET.setText("5858");

                    mIsServerLive = isRunService(this, MEDIASERVERSERVICE);
                    mIsClientLive = isRunService(this, MEDIACLIENTSERVICE);
                    if (mIsServerLive && !mIsClientLive) {
                        mEndTime = SystemClock.elapsedRealtime();
                        Log.i(TAG, "ON_CONNECTION_INFO_AVAILABLE mEndTime - mStartTime: " +
                                (mEndTime - mStartTime));
                        if ((mEndTime - mStartTime) >= 15000
                                && !mWifiP2pDeviceLists.isEmpty()
                                && !PLAYER_ACTIVITY_IS_LIVE) {
                            Log.i(TAG, "ON_CONNECTION_INFO_AVAILABLE START_MAINACTIVITY");
                            EventBusUtils.post(MediaServerService.class, START_MAINACTIVITY, null);
                        }
                    }
                }
                break;
            }
            case ON_PEERS_AVAILABLE: {
                if (msg.obj == null) {
                    return;
                }
                hideAllWifiP2pDevice();
                Collection<WifiP2pDevice> wifiP2pDeviceList = (Collection<WifiP2pDevice>) msg.obj;
                int size = wifiP2pDeviceList.size();
                Log.i(TAG, "ON_PEERS_AVAILABLE size: " + size);
                if (wifiP2pDeviceList.isEmpty()) {
                    return;
                }
                mWifiP2pDeviceLists.clear();
                mWifiP2pDeviceLists.addAll(wifiP2pDeviceList);
                for (int i = 0; i < size; i++) {
                    WifiP2pDevice device = mWifiP2pDeviceLists.get(i);
                    //Log.i(TAG, "-------------------------------------");
                    //Log.i(TAG, device.toString());
                    TextView tv = (TextView) mTvArray.get(i);
                    if (tv != null) {
                        tv.setVisibility(View.VISIBLE);
                        tv.setText(device.deviceName);
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    private void threadHandleMessage(Message msg) {
        if (msg == null) {
            return;
        }

        switch (msg.what) {
            case ON_CONNECTION_INFO_AVAILABLE: {
                if (msg.obj == null) {
                    return;
                }
                WifiP2pInfo wifiP2pInfo = (WifiP2pInfo) msg.obj;
                if (wifiP2pInfo != null
                        && wifiP2pInfo.groupFormed
                        && wifiP2pInfo.groupOwnerAddress != null) {
                    String hostAddress = wifiP2pInfo.groupOwnerAddress.getHostAddress();
                    String hostName = wifiP2pInfo.groupOwnerAddress.getHostName();
                    String hName = wifiP2pInfo.groupOwnerAddress.getCanonicalHostName();
                    Log.i(TAG, "ON_CONNECTION_INFO_AVAILABLE hostAddress: " + hostAddress);
                    Log.i(TAG, "ON_CONNECTION_INFO_AVAILABLE    hostName: " + hostName);
                    Log.i(TAG, "ON_CONNECTION_INFO_AVAILABLE       hName: " + hName);
                    String ip = getIPAddress();
                    Log.i(TAG, "ON_CONNECTION_INFO_AVAILABLE          ip: " + ip);
                }
                break;
            }
            case DISCOVER: {
                int i = msg.arg1;
                WifiP2pDevice device = mWifiP2pDeviceLists.get(i);
                WifiP2PHelper.getInstance(getApplicationContext()).cancelConnect();
                WifiP2PHelper.getInstance(getApplicationContext()).connect(device);
                break;
            }
            default:
                break;
        }
    }

    private static boolean isRunService(Context context, String serviceName) {
        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo
                service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (TextUtils.equals(serviceName, service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            MainActivity.this.onClick(v);
        }
    };

    private String getIPAddress() {
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = manager.getActiveNetworkInfo();
        if (info != null && info.isConnected()) {
            if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                // 当前使用2G/3G/4G网络
                try {
                    for (Enumeration<NetworkInterface> en =
                         NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                        NetworkInterface intf = en.nextElement();
                        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
                             enumIpAddr.hasMoreElements(); ) {
                            InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress()
                                    && inetAddress instanceof Inet4Address) {
                                return inetAddress.getHostAddress();
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                // 当前使用无线网络
                WifiManager wifiManager =
                        (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                // 得到IPV4地址
                String ipAddress = intIP2StringIP(wifiInfo.getIpAddress());
                return ipAddress;
            }
        } else {
            //当前无网络连接,请在设置中打开网络
        }

        return "";// "127.0.0.1"
    }

    /**
     * 将得到的int类型的IP转换为String类型
     *
     * @param ip
     * @return
     */
    private static String intIP2StringIP(int ip) {
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                (ip >> 24 & 0xFF);
    }

    private ArrayList<WifiP2pDevice> mWifiP2pDeviceLists = new ArrayList<>();
    private WifiP2PListener mWifiP2PListener = new WifiP2PListener() {
        @Override
        public void onDiscoverPeers(boolean isSuccess) {
            Log.i(TAG, "onDiscoverPeers() isSuccess: " + isSuccess);
        }

        @Override
        public void onWifiP2pEnabled(boolean isEnabled) {// 1
            Log.i(TAG, "onWifiP2pEnabled() isEnabled: " + isEnabled);
        }

        @Override
        public void onCreateGroup(boolean isSuccess) {
            Log.i(TAG, "onCreateGroup() isSuccess: " + isSuccess);
        }

        @Override
        public void onRemoveGroup(boolean isSuccess) {
            Log.i(TAG, "onRemoveGroup() isSuccess: " + isSuccess);
            String msg = isSuccess ? "移除群组成功" : "移除群组失败";
        }

        @Override
        public void onConnectCallChanged(boolean isConnected) {
            Log.i(TAG, "onConnectCallChanged() isConnected: " + isConnected);
            String msg = isConnected ? "调用连接成功" : "调用连接失败";
            Log.i(TAG, msg);
        }

        @Override
        public void onDisConnectCallChanged(boolean isDisConnected) {
            Log.i(TAG, "onDisConnectCallChanged() isDisConnected: " + isDisConnected);
            String msg = isDisConnected ? "断开连接成功" : "断开连接失败";
        }

        @Override
        public void onSelfDeviceAvailable(@NonNull WifiP2pDevice wifiP2pDevice) {// 3
            Log.i(TAG, "onSelfDeviceAvailable() " + wifiP2pDevice.deviceName);
        }

        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {// 2
            Log.i(TAG, "onConnectionInfoAvailable()");
            //p2p设备连接成功
            mUiHandler.removeMessages(ON_CONNECTION_INFO_AVAILABLE);
            Message msg = mUiHandler.obtainMessage();
            msg.what = ON_CONNECTION_INFO_AVAILABLE;
            msg.obj = wifiP2pInfo;
            mUiHandler.sendMessageDelayed(msg, 1000);
        }

        @Override
        public void onPeersAvailable(@NonNull Collection<WifiP2pDevice> wifiP2pDeviceList) {
            Log.i(TAG, "onPeersAvailable()");
            //p2p扫描结果存储集合
            mUiHandler.removeMessages(ON_PEERS_AVAILABLE);
            Message msg = mUiHandler.obtainMessage();
            msg.what = ON_PEERS_AVAILABLE;
            msg.obj = wifiP2pDeviceList;
            mUiHandler.sendMessageDelayed(msg, 1000);
        }

        @Override
        public void onGroupInfoAvailable(WifiP2pGroup group) {
            Log.i(TAG, "onGroupInfoAvailable()");
        }
    };

    private ConnectivityManager.NetworkCallback mNetworkCallback =
            new ConnectivityManager.NetworkCallback() {
                public void onAvailable(Network network) {
                    Log.i(TAG, "onAvailable()");
                }

                public void onLosing(Network network, int maxMsToLive) {
                    Log.i(TAG, "onLosing()");
                }

                public void onLost(Network network) {
                    // 断网
                    Log.i(TAG, "onLost()");
                    if (mIsServerLive && !mIsClientLive) {
                        if (PLAYER_ACTIVITY_IS_LIVE) {
                            Intent intent = new Intent();
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.setClass(MainActivity.this, PlayerActivity.class);
                            intent.putExtra("which_client", 1);
                            intent.putExtra("do_something", "finish");
                            startActivity(intent);

                            intent.putExtra("which_client", 2);
                            startActivity(intent);
                        }
                    } else if (!mIsServerLive && mIsClientLive) {
                        EventBusUtils.post(
                                MediaClientService.class, STOP_RECORD_SCREEN, null);
                    }
                }

                public void onUnavailable() {
                    Log.i(TAG, "onUnavailable()");
                }

                public void onCapabilitiesChanged(Network network,
                                                  NetworkCapabilities networkCapabilities) {
                    //Log.i(TAG, "onCapabilitiesChanged()");
                }

                public void onLinkPropertiesChanged(Network network,
                                                    LinkProperties linkProperties) {
                    Log.i(TAG, "onLinkPropertiesChanged()");
                }
            };

    //////////////////////////////////////////////////////////////////////////
    // WIFI Direct(WIFI直连)

    private WifiP2pManager mWifiP2pManager;
    private WifiP2pManager.Channel mChannel;
    private BroadcastReceiver mReceiver;
    public static MainActivity activity;

    // 获取可以连接点的列表 仅仅返回附近有哪些设备开启了wifi p2p
    public void discoverPeers() {
        Log.i(TAG, "discoverPeers()");
        mWifiP2pManager.discoverPeers(mChannel, mActionListener);
    }

    public void requestPeers() {
        Log.i(TAG, "requestPeers()");
        mWifiP2pManager.requestPeers(mChannel, mPeerListListener);
    }

    public void requestConnectionlnfo() {
        mWifiP2pManager.requestConnectionInfo(mChannel, mConnectionInfoListener);
    }

    public void updateThisDevice(WifiP2pDevice device) {
        Log.i(TAG, device.toString());
    }

    // 发现设备后,选择一个设备进行连接
    public void connect(WifiP2pDevice device) {
        Log.i(TAG, "connect() device: " + device.toString());
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        //config.groupOwnerIntent = 15;
        mWifiP2pManager.connect(mChannel, config, mActionListener);
    }

    public void disconnect() {
        mWifiP2pManager.removeGroup(mChannel, mActionListener);
    }

    private WifiP2pManager.ChannelListener mChannelListener =
            new WifiP2pManager.ChannelListener() {
                @Override
                public void onChannelDisconnected() {
                    Log.i(TAG, "mChannelListener onChannelDisconnected()");
                }
            };

    private WifiP2pManager.ActionListener mActionListener =
            new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    // When invoke connect method after, WiFiDirectBroadcastReceiver will notify us.
                    // Ignore for now.
                    Log.i(TAG, "mActionListener onSuccess()");
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "mActionListener onFailure() reason: " + reason);
                }
            };

    ArrayList<WifiP2pDevice> devices = new ArrayList<>();
    private WifiP2pManager.PeerListListener mPeerListListener =
            new WifiP2pManager.PeerListListener() {
                @Override
                public synchronized void onPeersAvailable(WifiP2pDeviceList peers) {
                    Log.i(TAG, "mPeerListListener onPeersAvailable()");
                    devices.clear();
                    devices.addAll(peers.getDeviceList());
                    for (WifiP2pDevice device : devices) {
                        Log.i(TAG, "-------------------------------------");
                        Log.i(TAG, device.toString());
                    }
                }
            };

    private WifiP2pManager.ConnectionInfoListener mConnectionInfoListener =
            new WifiP2pManager.ConnectionInfoListener() {
                @Override
                public void onConnectionInfoAvailable(WifiP2pInfo info) {
                    Log.i(TAG,
                            "mConnectionInfoListener onConnectionInfoAvailable() info: " + info.toString());
                    InetAddress groupOwnerAddress = info.groupOwnerAddress;
                    // 组群协商后,就可以确定群主
                    if (info.groupFormed && info.isGroupOwner) {
                        // 针对群主做某些任务
                        // 一种常用的做法是,创建一个服务器线程并接收连接请求
                    } else if (info.groupFormed) {
                        // 其他设备都作为客户端.在这种情况下,你会希望创建一个客户端线程来连接群主.
                    }
                }
            };

}