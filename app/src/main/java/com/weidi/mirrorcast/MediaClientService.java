package com.weidi.mirrorcast;

import android.app.Activity;
import android.app.Service;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.weidi.mirrorcast.Constants.ACCELEROMETER_ROTATION;
import static com.weidi.mirrorcast.Constants.IS_RECORDING;
import static com.weidi.mirrorcast.Constants.MAINACTIVITY_ON_RESUME;
import static com.weidi.mirrorcast.Constants.RELEASE;
import static com.weidi.mirrorcast.Constants.SET_ACTIVITY;
import static com.weidi.mirrorcast.Constants.SET_IP_AND_PORT;
import static com.weidi.mirrorcast.Constants.SET_MEDIAPROJECTION;
import static com.weidi.mirrorcast.Constants.START_RECORD_SCREEN;
import static com.weidi.mirrorcast.Constants.STOP_RECORD_SCREEN;
import static com.weidi.mirrorcast.MediaClient.int2Bytes;
import static com.weidi.mirrorcast.MyJni.DO_SOMETHING_CODE_find_createLandscapeVirtualDisplay;
import static com.weidi.mirrorcast.MyJni.DO_SOMETHING_CODE_find_createPortraitVirtualDisplay;
import static com.weidi.mirrorcast.MyJni.DO_SOMETHING_CODE_find_encoder_send_data_error;
import static com.weidi.mirrorcast.MyJni.DO_SOMETHING_CODE_fromLandscapeToPortrait;
import static com.weidi.mirrorcast.MyJni.DO_SOMETHING_CODE_fromPortraitToLandscape;
import static com.weidi.mirrorcast.MyJni.DO_SOMETHING_CODE_is_recording;
import static com.weidi.mirrorcast.MyJni.DO_SOMETHING_CODE_release_sps_pps;
import static com.weidi.mirrorcast.MyJni.DO_SOMETHING_CODE_start_record_screen;
import static com.weidi.mirrorcast.MyJni.DO_SOMETHING_CODE_stop_record_screen;
import static com.weidi.mirrorcast.MyJni.USE_MEDIACODEC_FOR_JNI;
import static com.weidi.mirrorcast.MyJni.USE_TCP;
import static com.weidi.mirrorcast.MyJni.USE_TRANSMISSION_FOR_JNI;

public class MediaClientService extends Service {

    public MediaClientService() {
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
    public int onStartCommand(Intent intent, int flags, int startId) {
        internalOnStartCommand(intent, flags, startId);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        internalOnDestroy();
        super.onDestroy();
    }

    private static final String TAG = "MediaClientService";
    private WindowManager mWindowManager;
    private DisplayMetrics mDisplayMetrics;
    private View mView;

    private void internalOnCreate() {
        Log.i(TAG, "MediaClientService internalOnCreate()");
        Phone.register(this);
        mContext = getApplicationContext();
        mMyJni = MyJni.getDefault();
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

        myOrientationListener = new OrientationListener(this);
        boolean autoRotateOn = android.provider.Settings.System.getInt(getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
        // 检查系统是否开启自动旋转
        if (autoRotateOn && !enable) {
            Log.i(TAG, "myOrientationListener.enable()");
            enable = true;
            myOrientationListener.enable();
        }

        int2Bytes(ORIENTATION_PORTRAIT, 1);
        int2Bytes(ORIENTATION_LANDSCAPE, 1);
        ORIENTATION_PORTRAIT[4] = -1;// 服务端读到"-1"表示需要竖屏
        ORIENTATION_LANDSCAPE[4] = -2;// 服务端读到"-2"表示需要横屏

        /*mUiHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                MediaClientService.this.uiHandleMessage(msg);
            }
        };
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mThreadHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                MediaClientService.this.threadHandleMessage(msg);
            }
        };*/

        UiModeManager uiModeManager =
                (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        whatIsDevice = uiModeManager.getCurrentModeType();
    }

    private void internalOnStartCommand(Intent intent, int flags, int startId) {

    }

    private void internalOnDestroy() {
        Log.i(TAG, "MediaClientService internalOnDestroy()");
        releaseAll();
        sps_pps_portrait = null;
        sps_pps_landscape = null;
        if (mWindowManager != null && mView != null) {
            mWindowManager.removeView(mView);
            mWindowManager = null;
            mView = null;
        }
        if (myOrientationListener != null && enable) {
            enable = false;
            myOrientationListener.disable();
        }
        /*if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
        }*/
        if (USE_MEDIACODEC_FOR_JNI) {
            // notify to jni for free sps_pps
            mMyJni.onTransact(DO_SOMETHING_CODE_release_sps_pps, null);
        }
        Phone.unregister(this);
    }

    ////////////////////////////////////////////////////////////////////////////

    public static final String FLAG = "@@@@@";

    private WeakReference<Activity> mReference;
    private Context mContext;
    private MyJni mMyJni;

    private Object lock = new Object();
    private boolean mIsRecording = false;
    private boolean mIsConnected = false;
    private boolean mAllowSendOrientation = false;
    private boolean mIsHandlingPortrait = false;
    private boolean mIsHandlingLandscape = false;
    private final Lock lockPortrait = new ReentrantLock();
    private final Condition conditionPortrait = lockPortrait.newCondition();
    private final Lock lockLandscape = new ReentrantLock();
    private final Condition conditionLandscape = lockLandscape.newCondition();
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    // 代表什么设备? 手机,平板,电视,手表
    private int whatIsDevice = 1;

    /*private HandlerThread mHandlerThread;
    private Handler mThreadHandler;
    private Handler mUiHandler;*/

    // 竖屏宽高
    private int mScreenWidthPortrait;
    private int mScreenHeightPortrait;
    // 横屏宽高
    private int mScreenWidthLandscape;
    private int mScreenHeightLandscape;
    private Surface mSurfacePortrait;
    private Surface mSurfaceLandscape;
    private String mVideoEncoderCodecName;
    private String mVideoMime;
    private MediaCodec mVideoEncoderMediaCodecPortrait;
    private MediaCodec mVideoEncoderMediaCodecLandscape;
    private MediaFormat mVideoEncoderMediaFormatPortrait;
    private MediaFormat mVideoEncoderMediaFormatLandscape;
    private ArrayBlockingQueue<byte[]> mPlayQueue = null;
    private String IP;
    private int PORT;
    private OrientationListener myOrientationListener;
    private boolean enable = false;
    private int mPreOrientation;
    private int mCurOrientation;
    private byte[] ORIENTATION_PORTRAIT = new byte[5];
    private byte[] ORIENTATION_LANDSCAPE = new byte[5];
    private boolean mIsGettingSpsPps = true;
    private byte[] sps_pps_portrait = null;
    private byte[] sps_pps_landscape = null;

    private boolean mIsKeyFrameWritePortrait = false;
    private boolean mIsKeyFrameWriteLandscape = false;

    private MediaClient mClient;

    private Object onEvent(int what, Object[] objArray) {
        Object result = null;
        switch (what) {
            case SET_ACTIVITY: {
                if (objArray != null && objArray.length > 0) {
                    Activity activity = (Activity) objArray[0];
                    mReference = new WeakReference<>(activity);
                } else {
                    if (mReference != null) {
                        Activity activity = mReference.get();
                        activity = null;
                        mReference = null;
                    }
                }
                break;
            }
            case SET_MEDIAPROJECTION: {
                if (objArray != null && objArray.length > 0) {
                    mMediaProjection = (MediaProjection) objArray[0];
                } else {
                    if (mMediaProjection != null) {
                        mMediaProjection.stop();
                        mMediaProjection.unregisterCallback(mMediaProjectionCallback);
                        mMediaProjection = null;
                    }
                }
                break;
            }
            case IS_RECORDING: {
                if (USE_MEDIACODEC_FOR_JNI) {
                    String str = mMyJni.onTransact(DO_SOMETHING_CODE_is_recording, null);
                    if (TextUtils.isEmpty(str)) {
                        return false;
                    }
                    return Boolean.parseBoolean(str);
                }
                return mIsRecording;
            }
            case SET_IP_AND_PORT: {
                if (objArray != null && objArray.length > 1) {
                    IP = (String) objArray[0];
                    PORT = (int) objArray[1];
                }
                break;
            }
            case START_RECORD_SCREEN: {
                if (USE_MEDIACODEC_FOR_JNI) {
                    if (!startRecordScreenForJni()) {
                        stopRecordScreenForJni();
                    }
                    break;
                }
                if ((whatIsDevice != Configuration.UI_MODE_TYPE_TELEVISION)
                        ? prepare()
                        : prepareForTV()) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            startRecordScreen();
                        }
                    }).start();
                } else {
                    stopRecordScreen();
                }
                break;
            }
            case STOP_RECORD_SCREEN: {
                if (USE_MEDIACODEC_FOR_JNI) {
                    stopRecordScreenForJni();
                    break;
                }
                stopRecordScreen();
                break;
            }
            case RELEASE: {
                releaseAll();
                break;
            }
            case ACCELEROMETER_ROTATION: {
                boolean autoRotateOn = android.provider.Settings.System.getInt(getContentResolver(),
                        Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
                // 检查系统是否开启自动旋转
                if (autoRotateOn && !enable) {
                    Log.i(TAG, "myOrientationListener.enable()");
                    enable = true;
                    myOrientationListener.enable();
                }
                break;
            }
            case DO_SOMETHING_CODE_find_createPortraitVirtualDisplay: {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                createPortraitVirtualDisplay();
                break;
            }
            case DO_SOMETHING_CODE_find_createLandscapeVirtualDisplay: {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                createLandscapeVirtualDisplay();
                break;
            }
            case DO_SOMETHING_CODE_find_encoder_send_data_error: {
                releaseAll();
                Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
                break;
            }
            default:
                break;
        }
        return result;
    }

    private void uiHandleMessage(Message msg) {
        if (msg == null) {
            return;
        }

        switch (msg.what) {
            case 0: {
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
            case START_RECORD_SCREEN: {
                if (USE_MEDIACODEC_FOR_JNI) {
                    startRecordScreenForJni();
                    return;
                }
                if ((whatIsDevice != Configuration.UI_MODE_TYPE_TELEVISION)
                        ? prepare()
                        : prepareForTV()) {
                    startRecordScreen();
                } else {
                    stopRecordScreen();
                }
                break;
            }
            case STOP_RECORD_SCREEN: {
                if (USE_MEDIACODEC_FOR_JNI) {
                    stopRecordScreenForJni();
                    return;
                }
                stopRecordScreen();
                break;
            }
            case RELEASE: {
                releaseAll();
                break;
            }
            default:
                break;
        }
    }

    private synchronized void releaseAll() {
        Log.i(TAG, "releaseAll start");
        mIsRecording = false;
        mIsConnected = false;
        mAllowSendOrientation = false;
        mIsHandlingPortrait = false;
        mIsHandlingLandscape = false;
        mIsKeyFrameWritePortrait = false;
        mIsKeyFrameWriteLandscape = false;
        if (mReference != null) {
            Activity activity = mReference.get();
            activity = null;
            mReference = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection.unregisterCallback(mMediaProjectionCallback);
            mMediaProjection = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        MediaUtils.releaseMediaCodec(mVideoEncoderMediaCodecPortrait);
        MediaUtils.releaseMediaCodec(mVideoEncoderMediaCodecLandscape);
        mVideoEncoderMediaCodecPortrait = null;
        mVideoEncoderMediaCodecLandscape = null;
        mVideoEncoderMediaFormatPortrait = null;
        mVideoEncoderMediaFormatLandscape = null;
        mSurfacePortrait = null;
        mSurfaceLandscape = null;
        mVideoEncoderCodecName = null;
        mVideoMime = null;
        if (mPlayQueue != null) {
            mPlayQueue.clear();
            mPlayQueue = null;
        }
        Log.i(TAG, "releaseAll end");
    }

    private synchronized boolean prepare() {
        Log.i(TAG, "prepare() start");
        if (mMediaProjection == null) {
            Log.e(TAG, "prepare() return for mMediaProjection is null");
            return false;
        }
        if (mIsRecording) {
            Log.e(TAG, "prepare() return for mIsRecording is true");
            return false;
        }

        mIsRecording = false;
        mIsConnected = false;
        mClient = MediaClient.getInstance();

        if (TextUtils.isEmpty(mVideoEncoderCodecName)) {
            mVideoEncoderCodecName = findEncoderCodecName(MediaFormat.MIMETYPE_VIDEO_AVC);
            mVideoMime = MediaFormat.MIMETYPE_VIDEO_AVC;
        }
        if (TextUtils.isEmpty(mVideoEncoderCodecName)) {
            mVideoEncoderCodecName = findEncoderCodecName(MediaFormat.MIMETYPE_VIDEO_HEVC);
            mVideoMime = MediaFormat.MIMETYPE_VIDEO_HEVC;
        }
        if (TextUtils.isEmpty(mVideoEncoderCodecName)) {
            Log.e(TAG, "prepare() mVideoEncoderCodecName is null");
            releaseAll();
            return false;
        }
        Log.i(TAG, "prepare() mVideoEncoderCodecName: " + mVideoEncoderCodecName
                + " mVideoMime: " + mVideoMime);

        MediaFormat format1 = null;
        MediaFormat format2 = null;
        int width = 0;
        int height = 0;
        // prepare时如果屏幕是竖屏,那么就先强制设置一下为竖屏,这样之后即使把手机横放,屏幕还是竖屏的.
        // 不然此时横竖屏不断的切换,不好处理.
        // 等到开始真正能投屏了,再进行横竖屏的切换.
        mPreOrientation = mCurOrientation = getResources().getConfiguration().orientation;
        mDisplayMetrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getRealMetrics(mDisplayMetrics);
        int tempOrientation = mCurOrientation;
        if (tempOrientation == Configuration.ORIENTATION_PORTRAIT) {
            // 竖屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            mScreenWidthPortrait = mDisplayMetrics.widthPixels;
            mScreenHeightPortrait = mDisplayMetrics.heightPixels;
            mScreenWidthLandscape = mScreenHeightPortrait;
            mScreenHeightLandscape = mScreenWidthPortrait;

            /*mScreenWidthPortrait = 1080;
            mScreenHeightPortrait = 1920;
            mScreenWidthLandscape = 1920;
            mScreenHeightLandscape = 1080;*/

            // TV
            /*mScreenWidthPortrait = 608;
            mScreenHeightPortrait = 1080;
            mScreenWidthLandscape = 1080;
            mScreenHeightLandscape = 608;*/

            /*int height_p = 1080;
            int width_p = mScreenWidthPortrait * height_p / mScreenHeightPortrait;
            if (width_p > 1080) {
                width_p = 1080;
                height_p = mScreenHeightPortrait * width_p / mScreenWidthPortrait;
            }
            width = width_p;
            height = height_p;
            Log.i(TAG, "prepare()" +
                    " width_p: " + width_p +
                    " height_p: " + height_p +
                    " height_l: " + height_p +
                    " width_l: " + width_p);*/

            width = mScreenWidthPortrait;
            height = mScreenHeightPortrait;
            Log.i(TAG, "prepare()" +
                    " mScreenWidthPortrait: " + mScreenWidthPortrait +
                    " mScreenHeightPortrait: " + mScreenHeightPortrait +
                    " mScreenWidthLandscape: " + mScreenWidthLandscape +
                    " mScreenHeightLandscape: " + mScreenHeightLandscape);

            format1 = MediaFormat.createVideoFormat(mVideoMime, width, height);
            format2 = MediaFormat.createVideoFormat(mVideoMime, height, width);
            format1.setInteger(MediaFormat.KEY_MAX_WIDTH, width);
            format1.setInteger(MediaFormat.KEY_MAX_HEIGHT, height);
            format2.setInteger(MediaFormat.KEY_MAX_WIDTH, height);
            format2.setInteger(MediaFormat.KEY_MAX_HEIGHT, width);
        } else if (tempOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 横屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            mScreenWidthLandscape = mDisplayMetrics.widthPixels;
            mScreenHeightLandscape = mDisplayMetrics.heightPixels;
            mScreenWidthPortrait = mScreenHeightLandscape;
            mScreenHeightPortrait = mScreenWidthLandscape;

            /*mScreenWidthLandscape = 1920;
            mScreenHeightLandscape = 1080;
            mScreenWidthPortrait = 1080;
            mScreenHeightPortrait = 1920;*/

            // TV
            /*mScreenWidthLandscape = 1080;
            mScreenHeightLandscape = 608;
            mScreenWidthPortrait = 608;
            mScreenHeightPortrait = 1080;*/

            /*int height_l = 1080;
            int width_l = mScreenWidthLandscape * height_l / mScreenHeightLandscape;
            width = width_l;
            height = height_l;
            Log.i(TAG, "prepare()" +
                    " width_l: " + width_l +
                    " height_l: " + height_l +
                    " height_p: " + height_l +
                    " width_p: " + width_l);*/

            width = mScreenWidthLandscape;
            height = mScreenHeightLandscape;
            Log.i(TAG, "prepare()" +
                    " mScreenWidthLandscape: " + mScreenWidthLandscape +
                    " mScreenHeightLandscape: " + mScreenHeightLandscape +
                    " mScreenWidthPortrait: " + mScreenWidthPortrait +
                    " mScreenHeightPortrait: " + mScreenHeightPortrait);

            format1 = MediaFormat.createVideoFormat(mVideoMime, width, height);
            format2 = MediaFormat.createVideoFormat(mVideoMime, height, width);
            format1.setInteger(MediaFormat.KEY_MAX_WIDTH, width);
            format1.setInteger(MediaFormat.KEY_MAX_HEIGHT, height);
            format2.setInteger(MediaFormat.KEY_MAX_WIDTH, height);
            format2.setInteger(MediaFormat.KEY_MAX_HEIGHT, width);
        }

        int maxBps = 1500;
        int fps = 25;
        int ifi = 1;
        if (Build.MANUFACTURER.equalsIgnoreCase("XIAOMI")) {
            maxBps = 500;
            fps = 10;
            ifi = 3;
        } else if (BlackListHelper.deviceInFpsBlacklisted()) {
            fps = 15;
        }

        format1.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height);
        format1.setInteger(MediaFormat.KEY_BIT_RATE, 10000000);  // maxBps * 1024 8000000
        format1.setInteger(MediaFormat.KEY_FRAME_RATE, 25);      // fps
        format1.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); // ifi
        format1.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        //format1.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / 45);
        /*format1.setInteger(MediaFormat.KEY_COMPLEXITY,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        if (Build.MANUFACTURER.equalsIgnoreCase("XIAOMI")) {
            format1.setInteger(MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);
        } else {
        }*/
        format1.setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);

        format2.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height);
        format2.setInteger(MediaFormat.KEY_BIT_RATE, 10000000);
        format2.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
        format2.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format2.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        //format2.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / 45);
        /*format2.setInteger(MediaFormat.KEY_COMPLEXITY,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        if (Build.MANUFACTURER.equalsIgnoreCase("XIAOMI")) {
            format2.setInteger(MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);
        } else {
        }*/
        format2.setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);

        // 配置MediaCodec
        if (tempOrientation == Configuration.ORIENTATION_PORTRAIT) {
            mVideoEncoderMediaFormatPortrait = format1;  // 竖屏
            mVideoEncoderMediaFormatLandscape = format2; // 横屏
            try {
                mVideoEncoderMediaCodecPortrait =
                        MediaCodec.createByCodecName(mVideoEncoderCodecName);
                mVideoEncoderMediaCodecPortrait.configure(
                        format1,
                        null, null,
                        MediaCodec.CONFIGURE_FLAG_ENCODE);
                mVideoEncoderMediaCodecLandscape =
                        MediaCodec.createByCodecName(mVideoEncoderCodecName);
                mVideoEncoderMediaCodecLandscape.configure(
                        format2,
                        null, null,
                        MediaCodec.CONFIGURE_FLAG_ENCODE);
            } catch (Exception e) {
                e.printStackTrace();
                releaseAll();
                Log.e(TAG, "prepare() e:\n" + e.toString());
                return false;
            }
        } else if (tempOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            mVideoEncoderMediaFormatLandscape = format1; // 横屏
            mVideoEncoderMediaFormatPortrait = format2;  // 竖屏
            try {
                mVideoEncoderMediaCodecLandscape =
                        MediaCodec.createByCodecName(mVideoEncoderCodecName);
                mVideoEncoderMediaCodecLandscape.configure(
                        format1,
                        null, null,
                        MediaCodec.CONFIGURE_FLAG_ENCODE);
                mVideoEncoderMediaCodecPortrait =
                        MediaCodec.createByCodecName(mVideoEncoderCodecName);
                mVideoEncoderMediaCodecPortrait.configure(
                        format2,
                        null, null,
                        MediaCodec.CONFIGURE_FLAG_ENCODE);
            } catch (Exception e) {
                e.printStackTrace();
                releaseAll();
                Log.e(TAG, "prepare() e:\n" + e.toString());
                return false;
            }
        }

        Log.i(TAG, "prepare()\n" +
                "format1: " + format1.toString() +
                "\nformat2: " + format2.toString());

        Log.i(TAG, "prepare() end");
        return true;
    }

    /**
     * 把电视屏幕内容投给其他安卓设备
     * 电视只有横屏
     *
     * @return
     */
    private synchronized boolean prepareForTV() {
        Log.i(TAG, "prepareForTV() start");
        if (mMediaProjection == null) {
            Log.e(TAG, "prepareForTV() return for mMediaProjection is null");
            return false;
        }
        if (mIsRecording) {
            Log.e(TAG, "prepareForTV() return");
            return false;
        }

        mIsRecording = false;
        mIsConnected = false;
        mClient = MediaClient.getInstance();

        if (TextUtils.isEmpty(mVideoEncoderCodecName)) {
            // OMX.qcom.video.encoder.hevc
            mVideoEncoderCodecName = findEncoderCodecName(MediaFormat.MIMETYPE_VIDEO_HEVC);
            mVideoMime = MediaFormat.MIMETYPE_VIDEO_HEVC;
        }
        if (TextUtils.isEmpty(mVideoEncoderCodecName)) {
            // OMX.qcom.video.encoder.avc (硬解)
            // OMX.MTK.VIDEO.ENCODER.AVC  (硬解)
            // c2.android.avc.encoder     (软解)
            mVideoEncoderCodecName = findEncoderCodecName(MediaFormat.MIMETYPE_VIDEO_AVC);
            mVideoMime = MediaFormat.MIMETYPE_VIDEO_AVC;
        }
        if (TextUtils.isEmpty(mVideoEncoderCodecName)) {
            Log.e(TAG, "prepareForTV() mVideoEncoderCodecName is null");
            releaseAll();
            return false;
        }
        Log.i(TAG, "prepareForTV() mVideoEncoderCodecName: " + mVideoEncoderCodecName
                + " mVideoMime: " + mVideoMime);

        // TV就是横屏,值为2
        mPreOrientation = mCurOrientation = getResources().getConfiguration().orientation;
        mDisplayMetrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getRealMetrics(mDisplayMetrics);
        // 横屏
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        mScreenWidthLandscape = mDisplayMetrics.widthPixels;
        mScreenHeightLandscape = mDisplayMetrics.heightPixels;
        mScreenWidthPortrait = mScreenHeightLandscape;
        mScreenHeightPortrait = mScreenWidthLandscape;

        int width = mScreenWidthLandscape;
        int height = mScreenHeightLandscape;
        Log.i(TAG, "prepareForTV()" +
                " mScreenWidthLandscape: " + mScreenWidthLandscape +
                " mScreenHeightLandscape: " + mScreenHeightLandscape +
                " mScreenWidthPortrait: " + mScreenWidthPortrait +
                " mScreenHeightPortrait: " + mScreenHeightPortrait);

        MediaFormat format = MediaFormat.createVideoFormat(mVideoMime, width, height);
        format.setInteger(MediaFormat.KEY_MAX_WIDTH, width);
        format.setInteger(MediaFormat.KEY_MAX_HEIGHT, height);

        int maxBps = 1500;
        int fps = 25;
        int ifi = 1;
        if (Build.MANUFACTURER.equalsIgnoreCase("XIAOMI")) {
            maxBps = 500;
            fps = 10;
            ifi = 3;
        } else if (BlackListHelper.deviceInFpsBlacklisted()) {
            fps = 15;
        }

        if (Build.MANUFACTURER.equalsIgnoreCase("XIAOMI")) {
            format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);
        } else {
            format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        }
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height);
        format.setInteger(MediaFormat.KEY_BIT_RATE, maxBps * 1024);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, ifi);
        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / 45);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_COMPLEXITY,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);

        mVideoEncoderMediaFormatLandscape = format;
        try {
            mVideoEncoderMediaCodecLandscape =
                    MediaCodec.createByCodecName(mVideoEncoderCodecName);
            mVideoEncoderMediaCodecLandscape.configure(
                    format,
                    null, null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (Exception e) {
            e.printStackTrace();
            releaseAll();
            Log.e(TAG, "prepareForTV() e:\n" + e.toString());
            return false;
        }

        Log.i(TAG, "prepareForTV()\n" +
                "format: " + format.toString());

        Log.i(TAG, "prepareForTV() end");
        return true;
    }

    private synchronized void startRecordScreen() {
        if (mIsRecording) {
            Log.w(TAG, "startRecordScreen() return");
            return;
        }

        Log.i(TAG, "startRecordScreen()");

        // 启动编码器(我设计了一个横屏编码器和一个竖屏编码器,这样横竖屏才能切换),接下去就是先获得sps和pps.
        if (whatIsDevice != Configuration.UI_MODE_TYPE_TELEVISION) {
            mSurfaceLandscape = mVideoEncoderMediaCodecLandscape.createInputSurface();
            mVideoEncoderMediaCodecLandscape.start();
            mSurfacePortrait = mVideoEncoderMediaCodecPortrait.createInputSurface();
            mVideoEncoderMediaCodecPortrait.start();
        } else {
            // 电视机只需要横屏编码器,因为只有横屏
            mSurfaceLandscape = mVideoEncoderMediaCodecLandscape.createInputSurface();
            mVideoEncoderMediaCodecLandscape.start();
        }

        /***
         通过mSurface的串联，把mMediaProjection的输出内容放到了mSurface里面，
         而mSurface正是mVideoEncoderMediaCodec的输入源，
         这样就完成了对mMediaProjection输出内容的编码，
         也就是屏幕采集数据的编码
         */
        mMediaProjection.registerCallback(mMediaProjectionCallback, Phone.getThreadHandler());

        // 获取横竖屏的sps和pps
        int tempOrientation = mCurOrientation;
        if (sps_pps_portrait == null || sps_pps_landscape == null) {
            if (whatIsDevice != Configuration.UI_MODE_TYPE_TELEVISION) {
                getSpsPps();
            } else {
                getSpsPpsForTV();
            }
        } else {
            if (tempOrientation == Configuration.ORIENTATION_PORTRAIT) {
                mIsHandlingPortrait = true;
                mIsHandlingLandscape = false;
                createPortraitVirtualDisplay();
            } else if (tempOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                mIsHandlingPortrait = false;
                mIsHandlingLandscape = true;
                createLandscapeVirtualDisplay();
            }
        }

        Log.i(TAG, "startRecordScreen()" +
                " mIsHandlingPortrait: " + mIsHandlingPortrait +
                " mIsHandlingLandscape: " + mIsHandlingLandscape);
        Log.d(TAG, "startRecordScreen()\n" + mVirtualDisplay.getDisplay());

        String deviceName = Settings.Global.getString(
                getContentResolver(), Settings.Global.DEVICE_NAME);
        if (TextUtils.isEmpty(deviceName)) {
            deviceName = "MirrorCast";
        }

        // 先向服务端发送配置信息,服务端拿到这些信息可以先初始化一些东西
        StringBuilder sb = new StringBuilder();
        sb.append(deviceName);      // 设备名称
        sb.append(FLAG);
        sb.append(mVideoMime);      // mime
        sb.append(FLAG);
        if (tempOrientation == Configuration.ORIENTATION_PORTRAIT) {
            sb.append(mScreenWidthPortrait);
            sb.append(FLAG);
            sb.append(mScreenHeightPortrait);
        } else if (tempOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            sb.append(mScreenWidthLandscape);
            sb.append(FLAG);
            sb.append(mScreenHeightLandscape);
        }
        sb.append(FLAG);
        sb.append(tempOrientation); // 横屏还是竖屏

        Log.i(TAG, "startRecordScreen() sb: " + sb.toString() + " length: " + sb.length());
        Log.i(TAG, "startRecordScreen() IP: " + IP + " PORT: " + PORT);
        if (USE_TRANSMISSION_FOR_JNI) {
            JniObject jniObject = JniObject.obtain();
            jniObject.valueString = sb.toString();
            jniObject.valueInt = sb.length();
            mMyJni.onTransact(
                    MyJni.DO_SOMETHING_CODE_Client_set_info, jniObject);
            jniObject = null;

            // 连接服务端
            jniObject = JniObject.obtain();
            jniObject.valueString = IP;
            jniObject.valueInt = PORT;
            String str = mMyJni.onTransact(
                    MyJni.DO_SOMETHING_CODE_Client_connect, jniObject);
            jniObject = null;
            try {
                mIsConnected = Boolean.parseBoolean(str);
                if (!mIsConnected) {
                    Phone.callUi(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(
                                    mContext, "没有连接上服务端", Toast.LENGTH_SHORT).show();
                        }
                    });
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "startRecordScreen() Boolean.parseBoolean(str)");
                stopRecordScreen();
                return;
            }
        } else {
            mClient.setIpAndPort(IP, PORT);
            if (!mClient.connect()) {
                Log.e(TAG, "startRecordScreen() return for connect failure");
                return;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mClient.startServerSocket();
                }
            }).start();
            // 客户端的基本信息
            mClient.sendDataForUDP_Str(sb.toString(), tempOrientation);
        }

        mIsRecording = true;
        mAllowSendOrientation = false;
        if (whatIsDevice != Configuration.UI_MODE_TYPE_TELEVISION) {
            if (tempOrientation == Configuration.ORIENTATION_PORTRAIT) {
                if (USE_TRANSMISSION_FOR_JNI) {
                    sendData(sps_pps_portrait);
                    sendData(sps_pps_landscape);
                } else {
                    // portrait
                    mClient.sendDataForUDP_Sps_Pps(sps_pps_portrait, true);
                    // landscape
                    mClient.sendDataForUDP_Sps_Pps(sps_pps_landscape, false);
                }
            } else if (tempOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (USE_TRANSMISSION_FOR_JNI) {
                    sendData(sps_pps_landscape);
                    sendData(sps_pps_portrait);
                } else {
                    // landscape
                    mClient.sendDataForUDP_Sps_Pps(sps_pps_landscape, false);
                    // portrait
                    mClient.sendDataForUDP_Sps_Pps(sps_pps_portrait, true);
                }
            }

            // UDP测试时,这里需要延时一下,等待服务端初始化完成
            SystemClock.sleep(2000);

            new Thread(new VideoDataEncodePortraitRunnable()).start();
            new Thread(new VideoDataEncodeLandscapeRunnable()).start();
        } else {
            sendData(sps_pps_landscape);
            sendData(sps_pps_portrait);
            new Thread(new VideoDataEncodeLandscapeRunnable()).start();
        }
        mAllowSendOrientation = true;
    }

    private synchronized void stopRecordScreen() {
        Log.i(TAG, "stopRecordScreen() start");
        mIsRecording = false;
        lockPortrait.lock();
        conditionPortrait.signal();
        lockPortrait.unlock();
        lockLandscape.lock();
        conditionLandscape.signal();
        lockLandscape.unlock();
        // 断开服务端
        mMyJni.onTransact(MyJni.DO_SOMETHING_CODE_Client_disconnect, null);
        releaseAll();
        Log.i(TAG, "stopRecordScreen() end");
    }

    private synchronized boolean startRecordScreenForJni() {
        Log.i(TAG, "startRecordScreenForJni() start");

        /*if (mActivity == null) {
            Log.e(TAG, "startRecordScreenForJni() return for activity is null");
            return false;
        }*/

        if (mMediaProjection == null) {
            Log.e(TAG, "startRecordScreenForJni() return for mMediaProjection is null");
            return false;
        }

        if (TextUtils.isEmpty(mVideoEncoderCodecName)) {
            mVideoEncoderCodecName = findEncoderCodecName(MediaFormat.MIMETYPE_VIDEO_AVC);
            mVideoMime = MediaFormat.MIMETYPE_VIDEO_AVC;
        }
        if (TextUtils.isEmpty(mVideoEncoderCodecName)) {
            mVideoEncoderCodecName = findEncoderCodecName(MediaFormat.MIMETYPE_VIDEO_HEVC);
            mVideoMime = MediaFormat.MIMETYPE_VIDEO_HEVC;
        }
        if (TextUtils.isEmpty(mVideoEncoderCodecName)) {
            Log.e(TAG, "startRecordScreenForJni() mVideoEncoderCodecName is null");
            releaseAll();
            return false;
        }
        Log.i(TAG, "startRecordScreenForJni() mVideoEncoderCodecName: " + mVideoEncoderCodecName
                + " mVideoMime: " + mVideoMime);

        mPreOrientation = mCurOrientation = getResources().getConfiguration().orientation;
        mDisplayMetrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getRealMetrics(mDisplayMetrics);

        int width = 0;
        int height = 0;
        int tempOrientation = mCurOrientation;
        if (tempOrientation == Configuration.ORIENTATION_PORTRAIT) {
            mScreenWidthPortrait = mDisplayMetrics.widthPixels;
            mScreenHeightPortrait = mDisplayMetrics.heightPixels;
            mScreenWidthLandscape = mScreenHeightPortrait;
            mScreenHeightLandscape = mScreenWidthPortrait;
            width = mScreenWidthPortrait;
            height = mScreenHeightPortrait;
        } else if (tempOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            mScreenWidthLandscape = mDisplayMetrics.widthPixels;
            mScreenHeightLandscape = mDisplayMetrics.heightPixels;
            mScreenWidthPortrait = mScreenHeightLandscape;
            mScreenHeightPortrait = mScreenWidthLandscape;
            width = mScreenWidthLandscape;
            height = mScreenHeightLandscape;
        }

        String deviceName = Settings.Global.getString(
                getContentResolver(), Settings.Global.DEVICE_NAME);
        if (TextUtils.isEmpty(deviceName)) {
            deviceName = "MirrorCast";
        }
        // 先向服务端发送配置信息,服务端拿到这些信息可以先初始化一些东西
        StringBuilder sb = new StringBuilder();
        sb.append(deviceName); // 设备名称
        sb.append(FLAG);
        sb.append(mVideoMime); // mime
        sb.append(FLAG);
        if (tempOrientation == Configuration.ORIENTATION_PORTRAIT) {
            sb.append(mScreenWidthPortrait);
            sb.append(FLAG);
            sb.append(mScreenHeightPortrait);
        } else if (tempOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            sb.append(mScreenWidthLandscape);
            sb.append(FLAG);
            sb.append(mScreenHeightLandscape);
        }
        sb.append(FLAG);
        sb.append(tempOrientation); // 横屏还是竖屏

        // Test
        /*IP = "192.168.1.102";
        PORT = 5858;*/

        Log.i(TAG, "startRecordScreenForJni() IP: " + IP + " PORT: " + PORT);
        Log.i(TAG, "startRecordScreenForJni() sb: " + sb.toString() + " length: " + sb.length());
        JniObject jniObject = JniObject.obtain();
        jniObject.valueIntArray = new int[]{
                sb.length(), tempOrientation, width, height, PORT};
        jniObject.valueStringArray = new String[]{
                sb.toString(), mVideoMime, mVideoEncoderCodecName, IP};
        String str = mMyJni.onTransact(
                MyJni.DO_SOMETHING_CODE_start_record_screen_prepare, jniObject);
        if (TextUtils.isEmpty(str)) {
            return false;
        }
        boolean ret = Boolean.parseBoolean(str);
        if (!ret) {
            Phone.callUi(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(
                            mContext, "没有连接上服务端", Toast.LENGTH_SHORT).show();
                }
            });
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            releaseAll();
            return false;
        }

        mSurfacePortrait = (Surface) jniObject.valueObjectArray[0];
        mSurfaceLandscape = (Surface) jniObject.valueObjectArray[1];

        mMediaProjection.registerCallback(mMediaProjectionCallback, Phone.getThreadHandler());

        mAllowSendOrientation = false;
        mMyJni.onTransact(DO_SOMETHING_CODE_start_record_screen, null);
        mAllowSendOrientation = true;
        Log.i(TAG, "startRecordScreenForJni() end");
        return true;
    }

    private synchronized void stopRecordScreenForJni() {
        mMyJni.onTransact(DO_SOMETHING_CODE_stop_record_screen, null);
        releaseAll();
    }

    /***
     竖屏有竖屏的Surface,横屏有横屏的Surface.
     因此需要有竖屏的MediaCodec和横屏的MediaCodec.
     */
    private void createPortraitVirtualDisplay() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                "createPortraitVirtualDisplay",
                mScreenWidthPortrait,
                mScreenHeightPortrait,
                mDisplayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                mSurfacePortrait,
                null,
                null);
        Log.d(TAG, "createPortraitVirtualDisplay() " +
                "  mScreenWidthPortrait: " + mScreenWidthPortrait +
                "  mScreenHeightPortrait: " + mScreenHeightPortrait);
    }

    private void createLandscapeVirtualDisplay() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                "createLandscapeVirtualDisplay",
                mScreenWidthLandscape,
                mScreenHeightLandscape,
                mDisplayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                mSurfaceLandscape,
                null,
                null);
        Log.d(TAG, "createLandscapeVirtualDisplay()" +
                " mScreenWidthLandscape: " + mScreenWidthLandscape +
                " mScreenHeightLandscape: " + mScreenHeightLandscape);
    }

    private void getSpsPps() {
        int tempOrientation = mCurOrientation;
        SpsPps.Callback callback = new SpsPps.Callback() {
            @Override
            public boolean isFinished() {
                return !mIsGettingSpsPps;
            }

            @Override
            public int handleOutputBuffer(ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
                room.position(roomInfo.offset);
                room.limit(roomInfo.offset + roomInfo.size);
                byte[] sps_pps = null;
                if (USE_TCP) {
                    sps_pps = new byte[roomInfo.size + 4];
                    int2Bytes(sps_pps, roomInfo.size);
                    room.get(sps_pps, 4, roomInfo.size);
                } else {
                    sps_pps = new byte[roomInfo.size];
                    room.get(sps_pps, 0, roomInfo.size);
                }
                Log.i(TAG, "getSpsPps() video \nsps_pps: " + Arrays.toString(sps_pps));
                if (tempOrientation == Configuration.ORIENTATION_PORTRAIT) {
                    if (sps_pps_portrait == null) {
                        sps_pps_portrait = sps_pps;
                    } else {
                        sps_pps_landscape = sps_pps;
                    }
                } else if (tempOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                    if (sps_pps_landscape == null) {
                        sps_pps_landscape = sps_pps;
                    } else {
                        sps_pps_portrait = sps_pps;
                    }
                }
                //sendData(sps_pps);
                mIsGettingSpsPps = false;
                return 0;
            }
        };

        MediaCodec mediaCodec = null;
        mIsGettingSpsPps = true;
        if (tempOrientation == Configuration.ORIENTATION_PORTRAIT) {
            mediaCodec = mVideoEncoderMediaCodecPortrait;
            // 强制竖屏(先得到竖屏的sps_pps)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            createPortraitVirtualDisplay();
        } else if (tempOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            mediaCodec = mVideoEncoderMediaCodecLandscape;
            // 强制横屏(先得到横屏的sps_pps)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            createLandscapeVirtualDisplay();
        }

        Log.i(TAG, "startRecordScreen() drainOutputBuffer 1");
        while (mIsGettingSpsPps) {
            SpsPps.drainOutputBuffer(callback, mediaCodec, true);
        }
        Log.i(TAG, "startRecordScreen() drainOutputBuffer 2");

        mIsGettingSpsPps = true;
        if (tempOrientation == Configuration.ORIENTATION_PORTRAIT) {
            mediaCodec = mVideoEncoderMediaCodecLandscape;
            // 强制横屏(然后得到横屏的sps_pps)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            createLandscapeVirtualDisplay();
        } else if (tempOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            mediaCodec = mVideoEncoderMediaCodecPortrait;
            // 强制竖屏(然后得到竖屏的sps_pps)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            createPortraitVirtualDisplay();
        }

        Log.i(TAG, "startRecordScreen() drainOutputBuffer 3");
        while (mIsGettingSpsPps) {
            SpsPps.drainOutputBuffer(callback, mediaCodec, true);
        }
        Log.i(TAG, "startRecordScreen() drainOutputBuffer 4");

        // 然后再还原成原来屏幕的方向
        if (tempOrientation == Configuration.ORIENTATION_PORTRAIT) {
            mIsHandlingPortrait = true;
            mIsHandlingLandscape = false;
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            createPortraitVirtualDisplay();
        } else if (tempOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            mIsHandlingLandscape = true;
            mIsHandlingPortrait = false;
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            createLandscapeVirtualDisplay();
        }
    }

    private void getSpsPpsForTV() {
        mIsHandlingLandscape = true;
        mIsHandlingPortrait = false;
        mIsGettingSpsPps = true;
        final MediaCodec mediaCodec = mVideoEncoderMediaCodecLandscape;
        createLandscapeVirtualDisplay();
        SpsPps.Callback callback = new SpsPps.Callback() {
            @Override
            public boolean isFinished() {
                return !mIsGettingSpsPps;
            }

            @Override
            public int handleOutputBuffer(ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
                room.position(roomInfo.offset);
                room.limit(roomInfo.offset + roomInfo.size);
                byte[] sps_pps = new byte[roomInfo.size + 4];
                int2Bytes(sps_pps, roomInfo.size);
                room.get(sps_pps, 4, roomInfo.size);
                Log.i(TAG, "getSpsPps() video \nsps_pps: " + Arrays.toString(sps_pps));
                sps_pps_portrait = sps_pps;
                sps_pps_landscape = sps_pps;
                mIsGettingSpsPps = false;
                return 0;
            }
        };

        Log.i(TAG, "startRecordScreen() drainOutputBuffer 1");
        while (mIsGettingSpsPps) {
            SpsPps.drainOutputBuffer(callback, mediaCodec, true);
        }
        Log.i(TAG, "startRecordScreen() drainOutputBuffer 2");
    }

    private void handleVideoOutputBufferPortrait(ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
        room.position(roomInfo.offset);
        room.limit(roomInfo.offset + roomInfo.size);

        byte[] frame = null;
        if (USE_TCP) {
            frame = new byte[roomInfo.size + 6];
            int2Bytes(frame, roomInfo.size + 2);
            // "1"表示竖屏, "2"表示横屏
            frame[roomInfo.size + 4] = (byte) 1;
            // 用于在接收端判断是否是关键帧
            frame[roomInfo.size + 5] = (byte) roomInfo.flags;
            room.get(frame, 4, roomInfo.size);
        } else {
            frame = new byte[roomInfo.size];
            room.get(frame, 0, roomInfo.size);
        }

        if (!mIsKeyFrameWritePortrait) {
            // for IDR frame
            if ((roomInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                mIsKeyFrameWritePortrait = true;
                if (USE_TCP) {
                    sendData(frame);
                } else {
                    mClient.sendDataForUDP(frame, 0, frame.length, true, roomInfo.flags);
                }
                return;
            }
            frame = null;
            return;
        }

        if (USE_TCP) {
            sendData(frame);
        } else {
            mClient.sendDataForUDP(frame, 0, frame.length, true, roomInfo.flags);
        }
    }

    private void handleVideoOutputBufferLandscape(ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
        room.position(roomInfo.offset);
        room.limit(roomInfo.offset + roomInfo.size);

        byte[] frame = null;
        if (USE_TCP) {
            frame = new byte[roomInfo.size + 6];
            int2Bytes(frame, roomInfo.size + 2);
            frame[roomInfo.size + 4] = (byte) 2;
            frame[roomInfo.size + 5] = (byte) roomInfo.flags;
            room.get(frame, 4, roomInfo.size);
        } else {
            frame = new byte[roomInfo.size];
            room.get(frame, 0, roomInfo.size);
        }

        if (!mIsKeyFrameWriteLandscape) {
            // for IDR frame
            if ((roomInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                mIsKeyFrameWriteLandscape = true;
                if (USE_TCP) {
                    sendData(frame);
                } else {
                    mClient.sendDataForUDP(frame, 0, frame.length, false, roomInfo.flags);
                }
                return;
            }
            frame = null;
            return;
        }

        if (USE_TCP) {
            sendData(frame);
        } else {
            mClient.sendDataForUDP(frame, 0, frame.length, false, roomInfo.flags);
        }
    }

    private void fromPortraitToLandscape() {
        Log.w(TAG, "竖屏 ---> 横屏");
        createLandscapeVirtualDisplay();
        mIsHandlingLandscape = true;
        mIsHandlingPortrait = false;
        mIsKeyFrameWriteLandscape = false;
        sendData(ORIENTATION_LANDSCAPE);
        lockLandscape.lock();
        conditionLandscape.signal();
        lockLandscape.unlock();
    }

    private void fromLandscapeToPortrait() {
        Log.w(TAG, "横屏 ---> 竖屏");
        createPortraitVirtualDisplay();
        mIsHandlingPortrait = true;
        mIsHandlingLandscape = false;
        mIsKeyFrameWritePortrait = false;
        sendData(ORIENTATION_PORTRAIT);
        lockPortrait.lock();
        conditionPortrait.signal();
        lockPortrait.unlock();
    }

    private void setRequestedOrientation(int orientation) {
        if (mReference != null) {
            Activity activity = mReference.get();
            if (activity != null) {
                activity.setRequestedOrientation(orientation);
            }
        }
    }

    /***
     isPortrait true表示frame是竖屏的数据,false表示frame是横屏的数据
     */
    private synchronized void sendData(byte[] frame) {
        if (!mIsRecording) {
            Log.e(TAG, "VideoDataSendRunnable sendData() return");
            return;
        }

        // A
        // long start = SystemClock.uptimeMillis();
        if (USE_TRANSMISSION_FOR_JNI) {
            MyJni.clientFrame.valueByteArray = frame;
            MyJni.clientFrame.valueInt = frame.length;
            String info = mMyJni.onTransact(
                    MyJni.DO_SOMETHING_CODE_Client_send_data, MyJni.clientFrame);
            int sendLength = Integer.valueOf(info);
            if (sendLength <= 0) {
                Log.e(TAG, "sendData() sendLength: " + sendLength);
                stopRecordScreenForJni();
                Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
            }
            return;
        }
        // long end = SystemClock.uptimeMillis();
        // Log.i(TAG, "putDataToJava() video take time: " + (end - start));
        // B
        // 从A到B花费的时间大概为0~2ms
    }

    // 根据mime查找编码器
    private static String findEncoderCodecName(String mime) {
        String codecName = null;
        MediaCodecInfo[] mediaCodecInfos =
                MediaUtils.findAllEncodersByMime(mime);
        if (mediaCodecInfos == null) {
            return codecName;
        }
        for (MediaCodecInfo mediaCodecInfo : mediaCodecInfos) {
            if (mediaCodecInfo == null) {
                continue;
            }
            codecName = mediaCodecInfo.getName();
            if (TextUtils.isEmpty(codecName)) {
                continue;
            }

            String tempCodecName = codecName.toLowerCase();
            if (tempCodecName.startsWith("omx.google.")
                    || tempCodecName.startsWith("c2.android.")
                    // 用于加密的视频
                    || tempCodecName.endsWith(".secure")
                    || (!tempCodecName.startsWith("omx.") && !tempCodecName.startsWith("c2."))) {
                codecName = null;
                continue;
            }

            // 硬解
            break;

            // 软解
            /*if (!tempCodecName.endsWith(".secure")
                    && (tempCodecName.startsWith("omx.google.")
                    || tempCodecName.startsWith("c2.android."))) {
                break;
            }*/
        }
        return codecName;
    }

    private MediaProjection.Callback mMediaProjectionCallback =
            new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Log.i(TAG, "MediaProjection.Callback onStop()");
                }
            };

    public EDMediaCodec.Callback mEDMediaCodecCallback = new EDMediaCodec.Callback() {

        @Override
        public boolean isVideoFinished() {
            return !mIsRecording;
        }

        @Override
        public boolean isAudioFinished() {
            return false;
        }

        @Override
        public void handleVideoOutputFormat(MediaFormat mediaFormat) {

        }

        @Override
        public void handleAudioOutputFormat(MediaFormat mediaFormat) {

        }

        @Override
        public int handleVideoOutputBuffer(
                ByteBuffer room, MediaCodec.BufferInfo roomInfo, boolean isPortrait) {
            if (isPortrait) {
                handleVideoOutputBufferPortrait(room, roomInfo);
                return 0;
            }

            handleVideoOutputBufferLandscape(room, roomInfo);
            return 0;
        }

        @Override
        public int handleAudioOutputBuffer(
                ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
            return 0;
        }
    };

    private class VideoDataEncodePortraitRunnable implements Runnable {

        @Override
        public void run() {
            Log.i(TAG, "VideoDataEncodePortraitRunnable start");

            long totalTakeCount = 0;
            long totalTakeTime = 0;
            long averageTakeTime = 0;
            boolean feedInputBufferAndDrainOutputBuffer = false;
            while (mIsRecording) {
                if (!mIsHandlingPortrait) {
                    lockPortrait.lock();
                    try {
                        Log.d(TAG, "VideoDataEncodePortraitRunnable await");
                        conditionPortrait.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    lockPortrait.unlock();
                }
                //long start = SystemClock.uptimeMillis();
                feedInputBufferAndDrainOutputBuffer =
                        EDMediaCodec.feedInputBufferAndDrainOutputBuffer(
                                mEDMediaCodecCallback,
                                EDMediaCodec.TYPE.TYPE_VIDEO,
                                mVideoEncoderMediaCodecPortrait,
                                null,
                                0,
                                0,
                                0,
                                0,
                                false,
                                false,
                                true);
                if (!feedInputBufferAndDrainOutputBuffer) {
                    break;
                }
                /*long end = SystemClock.uptimeMillis();
                ++totalTakeCount;
                totalTakeTime += (end - start);
                averageTakeTime = totalTakeTime / totalTakeCount;
                Log.i(TAG, "VideoDataEncodeRunnable video take time: " + averageTakeTime);*/
                // 平均值稳定在14ms
            }

            Log.i(TAG, "VideoDataEncodePortraitRunnable end");
        }
    }

    private class VideoDataEncodeLandscapeRunnable implements Runnable {

        @Override
        public void run() {
            Log.i(TAG, "VideoDataEncodeLandscapeRunnable start");

            boolean feedInputBufferAndDrainOutputBuffer = false;
            while (mIsRecording) {
                if (!mIsHandlingLandscape) {
                    lockLandscape.lock();
                    try {
                        Log.d(TAG, "VideoDataEncodeLandscapeRunnable await");
                        conditionLandscape.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    lockLandscape.unlock();
                }
                feedInputBufferAndDrainOutputBuffer =
                        EDMediaCodec.feedInputBufferAndDrainOutputBuffer(
                                mEDMediaCodecCallback,
                                EDMediaCodec.TYPE.TYPE_VIDEO,
                                mVideoEncoderMediaCodecLandscape,
                                null,
                                0,
                                0,
                                0,
                                0,
                                false,
                                false,
                                false);
                if (!feedInputBufferAndDrainOutputBuffer) {
                    break;
                }
            }

            Log.i(TAG, "VideoDataEncodeLandscapeRunnable end");
        }
    }

    private class OrientationListener extends OrientationEventListener {

        public OrientationListener(Context context) {
            super(context);
        }

        public OrientationListener(Context context, int rate) {
            super(context, rate);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            //Log.d(TAG, "orention" + orientation);
            if (!mAllowSendOrientation) {
                return;
            }

            if (((orientation >= 0) && (orientation < 45)) || (orientation > 315)) {
                //Log.d(TAG, "设置竖屏");
                mCurOrientation = Configuration.ORIENTATION_PORTRAIT;
            } else if (orientation > 225 && orientation < 315) {
                //Log.d(TAG, "设置横屏");
                mCurOrientation = Configuration.ORIENTATION_LANDSCAPE;
            } else if (orientation > 45 && orientation < 135) {
                //Log.d(TAG, "反向横屏");
                mCurOrientation = Configuration.ORIENTATION_LANDSCAPE;
            } else if (orientation > 135 && orientation < 225) {
                //Log.d(TAG, "反向竖屏");
                mCurOrientation = Configuration.ORIENTATION_PORTRAIT;
            }

            if (mPreOrientation != mCurOrientation) {
                if (mPreOrientation == Configuration.ORIENTATION_PORTRAIT) {
                    // 竖屏 ---> 横屏
                    if (USE_MEDIACODEC_FOR_JNI) {
                        mMyJni.onTransact(DO_SOMETHING_CODE_fromPortraitToLandscape, null);
                    } else {
                        fromPortraitToLandscape();
                    }
                } else {
                    // 横屏 ---> 竖屏
                    if (USE_MEDIACODEC_FOR_JNI) {
                        mMyJni.onTransact(DO_SOMETHING_CODE_fromLandscapeToPortrait, null);
                    } else {
                        fromLandscapeToPortrait();
                    }
                }
                mPreOrientation = mCurOrientation;
            }
        }
    }

}