package com.weidi.mirrorcast;

import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.LinearLayout;

import java.nio.ByteBuffer;
import java.util.Arrays;

import androidx.appcompat.app.AppCompatActivity;

import static com.weidi.mirrorcast.MyJni.DO_SOMETHING_CODE_set_surface;
import static com.weidi.mirrorcast.MyJni.DECODER_MEDIA_CODEC_GO_JNI;

public class PlayerActivity extends AppCompatActivity {

    private static final String TAG =
            "player_alexander";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);*/
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Log.i(TAG, "onCreate() savedInstanceState: " + savedInstanceState);
        setContentView(R.layout.activity_player);

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

    /////////////////////////////////////////////////////////////////

    private static final int MSG_UI_ADD_VIEW = 1000;
    private static final int MSG_UI_REMOVE_VIEW = 1001;
    private static final int MSG_UI_SURFACE_CHANGED = 1002;
    private static final int MSG_UI_HANDLE_LAYOUT = 1003;
    private static final int MSG_UI_CHANGE_WINDOW = 1004;
    private static final int MSG_UI_SURFACE_CREATED = 1005;
    private static final int MSG_UI_SURFACE_DESTROYED = 1006;

    private static final int MSG_THREAD_SET_INTENT = 2000;

    public static boolean PLAYER_ACTIVITY_IS_LIVE = false;

    // 最多投屏个数: 1 2
    public static int MAXIMUM_NUMBER = 1;

    // unknow
    private static final int MARK0 = 0;
    // 0 0 0 1
    private static final int MARK1 = 1;
    //   0 0 1
    private static final int MARK2 = 2;
    // ... 103 ... 104 ...
    private static final int MARK3 = 3;
    // ...  39 ... 40 ...
    private static final int MARK4 = 4;
    private int mark1 = MARK0;
    private int mark2 = MARK0;

    private HandlerThread mHandlerThread;
    private Handler mThreadHandler;
    private Handler mUiHandler;

    private int whatIsDevice = 1;

    private LinearLayout mPlayerLayout1;
    private LinearLayout mPlayerLayout2;
    private SurfaceView mSurfaceView1;
    private SurfaceView mSurfaceView2;
    private SurfaceCallback mSurfaceHolderCallback1;
    private SurfaceCallback mSurfaceHolderCallback2;
    private boolean mIsAddedView1 = false;
    private boolean mIsAddedView2 = false;
    private int mSurfaceCreatedCount1 = 0;
    private int mSurfaceCreatedCount2 = 0;

    public static PlayerActivity playerActivity = null;
    private int mScreenWidthPortrait;
    private int mScreenHeightPortrait;
    private int mScreenWidthLandscape;
    private int mScreenHeightLandscape;
    // 竖屏
    int PORTRAIT_WIDTH = 1080;
    int PORTRAIT_HEIGHT = 2244;// 1920
    // 横屏
    int LANDSCAPE_WIDTH = 1920;
    int LANDSCAPE_HEIGHT = 1080;

    public static String CODEC_NAME1;
    public static String CODEC_NAME2;
    public static String VIDEO_MIME1;
    public static String VIDEO_MIME2;
    // 竖屏宽高
    public static int WIDTH1_P;
    public static int HEIGHT1_P;
    // 横屏宽高
    public static int WIDTH1_L;
    public static int HEIGHT1_L;
    public static int WIDTH2_P;
    public static int HEIGHT2_P;
    public static int WIDTH2_L;
    public static int HEIGHT2_L;
    public static int ORIENTATION1;
    public static int ORIENTATION2;
    private byte[] sps_pps_portrait1 = null;
    private byte[] sps_pps_landscape1 = null;
    private byte[] sps_pps_portrait2 = null;
    private byte[] sps_pps_landscape2 = null;
    private MediaCodec mVideoDecoderMediaCodec1;
    private MediaFormat mVideoDecoderMediaFormat1;
    private MediaCodec mVideoDecoderMediaCodec2;
    private MediaFormat mVideoDecoderMediaFormat2;

    private boolean mWindow1IsPlaying = false;
    private boolean mWindow2IsPlaying = false;

    private VideoDataDecodeRunnable mVideoDataDecodeRunnable1;
    private VideoDataDecodeRunnable mVideoDataDecodeRunnable2;
    private EDCallback mEDCallback1;
    private EDCallback mEDCallback2;

    public void changeWindow(int which_client, int orientation) {
        Log.i(TAG, "changeWindow() which_client: " + which_client + " orientation: " + orientation);

        switch (which_client) {
            case 1: {
                switch (orientation) {
                    case Configuration.ORIENTATION_PORTRAIT: {
                        if (mVideoDataDecodeRunnable1 != null) {
                            mVideoDataDecodeRunnable1.isPortrait = true;
                        }
                        break;
                    }
                    case Configuration.ORIENTATION_LANDSCAPE: {
                        if (mVideoDataDecodeRunnable1 != null) {
                            mVideoDataDecodeRunnable1.isPortrait = false;
                        }
                        break;
                    }
                    default:
                        break;
                }
                break;
            }
            case 2: {
                switch (orientation) {
                    case Configuration.ORIENTATION_PORTRAIT: {
                        if (mVideoDataDecodeRunnable2 != null) {
                            mVideoDataDecodeRunnable2.isPortrait = true;
                        }
                        break;
                    }
                    case Configuration.ORIENTATION_LANDSCAPE: {
                        if (mVideoDataDecodeRunnable2 != null) {
                            mVideoDataDecodeRunnable2.isPortrait = false;
                        }
                        break;
                    }
                    default:
                        break;
                }
                break;
            }
            default:
                break;
        }

        mUiHandler.removeMessages(MSG_UI_CHANGE_WINDOW);
        Message msg = mUiHandler.obtainMessage();
        msg.what = MSG_UI_CHANGE_WINDOW;
        msg.arg1 = which_client;
        msg.arg2 = orientation;
        mUiHandler.sendMessage(msg);
        //mUiHandler.sendMessageDelayed(msg, 500);
    }

    private void internalOnCreate() {
        PLAYER_ACTIVITY_IS_LIVE = true;
        playerActivity = this;

        UiModeManager uiModeManager =
                (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        whatIsDevice = uiModeManager.getCurrentModeType();
        Log.i(TAG, "internalOnCreate() whatIsDevice: " + whatIsDevice);
        switch (whatIsDevice) {
            case Configuration.UI_MODE_TYPE_TELEVISION: {
                // TV
                MAXIMUM_NUMBER = 1;
                break;
            }
            case Configuration.UI_MODE_TYPE_NORMAL:
            default: {
                // Phone and Other
                MAXIMUM_NUMBER = 1;
                break;
            }
        }

        WindowManager windowManager =
                (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            // 竖屏
            mScreenWidthPortrait = displayMetrics.widthPixels;
            mScreenHeightPortrait = displayMetrics.heightPixels;
            mScreenWidthLandscape = mScreenHeightPortrait;
            mScreenHeightLandscape = mScreenWidthPortrait;
            Log.i(TAG, "internalOnCreate()" +
                    " mScreenWidthPortrait: " + mScreenWidthPortrait +
                    " mScreenHeightPortrait: " + mScreenHeightPortrait +
                    " mScreenWidthLandscape: " + mScreenWidthLandscape +
                    " mScreenHeightLandscape: " + mScreenHeightLandscape);
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mScreenWidthLandscape = displayMetrics.widthPixels;
            mScreenHeightLandscape = displayMetrics.heightPixels;
            mScreenWidthPortrait = mScreenHeightLandscape;
            mScreenHeightPortrait = mScreenWidthLandscape;
            Log.i(TAG, "internalOnCreate()" +
                    " mScreenWidthLandscape: " + mScreenWidthLandscape +
                    " mScreenHeightLandscape: " + mScreenHeightLandscape +
                    " mScreenWidthPortrait: " + mScreenWidthPortrait +
                    " mScreenHeightPortrait: " + mScreenHeightPortrait);
        }
        if (whatIsDevice == Configuration.UI_MODE_TYPE_TELEVISION) {
            mScreenWidthLandscape = displayMetrics.widthPixels;
            mScreenHeightLandscape = displayMetrics.heightPixels;
            mScreenWidthPortrait = mScreenWidthLandscape;
            mScreenHeightPortrait = mScreenHeightLandscape;
            Log.i(TAG, "internalOnCreate()" +
                    " mScreenWidthLandscape: " + mScreenWidthLandscape +
                    " mScreenHeightLandscape: " + mScreenHeightLandscape +
                    " mScreenWidthPortrait: " + mScreenWidthPortrait +
                    " mScreenHeightPortrait: " + mScreenHeightPortrait + " TV");
        }

        mUiHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                PlayerActivity.this.uiHandleMessage(msg);
            }
        };
        mHandlerThread = new HandlerThread("EventListener Thread");
        mHandlerThread.start();
        mThreadHandler = new Handler(mHandlerThread.getLooper()) {
            public void handleMessage(Message msg) {
                PlayerActivity.this.threadHandleMessage(msg);
            }
        };

        mPlayerLayout1 = findViewById(R.id.player_layout1);
        mPlayerLayout2 = findViewById(R.id.player_layout2);
        mSurfaceView1 = new SurfaceView(PlayerActivity.this);
        mSurfaceView2 = new SurfaceView(PlayerActivity.this);

        internalonNewIntent(getIntent());
    }

    private void internalOnResume() {

    }

    private void internalOnStop() {

    }

    private void internalOnDestroy() {
        PLAYER_ACTIVITY_IS_LIVE = false;
        playerActivity = null;
        CODEC_NAME1 = null;
        CODEC_NAME2 = null;
        VIDEO_MIME1 = null;
        VIDEO_MIME2 = null;
        sps_pps_portrait1 = null;
        sps_pps_landscape1 = null;
        MyJni.getDefault().onDestroy();
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
        }
    }

    private void internalonNewIntent(Intent intent) {
        Message msg = mUiHandler.obtainMessage();
        msg.what = MSG_THREAD_SET_INTENT;
        msg.obj = intent;
        mThreadHandler.sendMessageDelayed(msg, 500);
    }

    // 子线程
    private synchronized void setIntentImpl(Intent intent) {
        int which_client = intent.getIntExtra("which_client", 0);
        String do_something = intent.getStringExtra("do_something");
        Log.i(TAG, "internalonNewIntent()      which_client: " + which_client);
        Log.i(TAG, "internalonNewIntent()      do_something: " + do_something);
        Log.i(TAG, "internalonNewIntent() mWindow1IsPlaying: " + mWindow1IsPlaying);
        Log.i(TAG, "internalonNewIntent() mWindow2IsPlaying: " + mWindow2IsPlaying);

        switch (which_client) {
            case 1: {
                if (TextUtils.equals(do_something, "finish")) {
                    mWindow1IsPlaying = false;
                    removeView(MSG_UI_REMOVE_VIEW, 1);
                    break;
                }
                if (!TextUtils.equals(do_something, "playback")) {
                    return;
                }
                if (!mWindow1IsPlaying) {
                    mWindow1IsPlaying = true;
                    if (DECODER_MEDIA_CODEC_GO_JNI) {
                        addView(MSG_UI_ADD_VIEW, 1);
                    } else {
                        if (prepare1()) {
                            addView(MSG_UI_ADD_VIEW, 1);
                        }
                    }
                    break;
                }
                break;
            }
            case 2: {
                if (TextUtils.equals(do_something, "finish")) {
                    mWindow2IsPlaying = false;
                    removeView(MSG_UI_REMOVE_VIEW, 2);
                    break;
                }
                if (!TextUtils.equals(do_something, "playback")) {
                    return;
                }
                if (!mWindow2IsPlaying) {
                    mWindow2IsPlaying = true;
                    if (DECODER_MEDIA_CODEC_GO_JNI) {
                        addView(MSG_UI_ADD_VIEW, 2);
                    } else {
                        if (prepare2()) {
                            addView(MSG_UI_ADD_VIEW, 2);
                        }
                    }
                    break;
                }
                break;
            }
            default:
                break;
        }

        // 广播
        /*int count = 0;
        if (mWindow1IsPlaying && mWindow2IsPlaying) {
            count = 2;
        } else if (mWindow1IsPlaying && !mWindow2IsPlaying) {
            count = 1;
        } else if (!mWindow1IsPlaying && mWindow2IsPlaying) {
            count = 1;
        }
        Intent intent1 = new Intent();
        intent1.setAction("com.weidi.mirrorcast.PlayerActivity");
        intent1.putExtra("client_counts", count);
        sendBroadcast(intent1);
        Log.i(TAG, "internalonNewIntent()     client_counts: " + count);*/
    }

    private boolean prepare1() {
        if (TextUtils.isEmpty(VIDEO_MIME1)) {
            Log.e(TAG, "prepare1() VIDEO_MIME1 is empty");
            return false;
        }
        CODEC_NAME1 = MyJni.findDecoderCodecName(VIDEO_MIME1);
        Log.w(TAG, "prepare1() video VIDEO_MIME1: " + VIDEO_MIME1);
        Log.w(TAG, "prepare1() video CODEC_NAME1: " + CODEC_NAME1);
        if (TextUtils.isEmpty(CODEC_NAME1)) {
            Log.e(TAG, "prepare1() CODEC_NAME1 is empty");
            return false;
        }

        byte[] sps_pps = MyJni.getDefault().getData(1);
        if (sps_pps == null) {
            Log.e(TAG, "prepare1() sps_pps == null 1");
            return false;
        }

        if (ORIENTATION1 == Configuration.ORIENTATION_PORTRAIT) {
            sps_pps_portrait1 = new byte[sps_pps.length];
            System.arraycopy(sps_pps, 0, sps_pps_portrait1, 0, sps_pps.length);
        } else if (ORIENTATION1 == Configuration.ORIENTATION_LANDSCAPE) {
            sps_pps_landscape1 = new byte[sps_pps.length];
            System.arraycopy(sps_pps, 0, sps_pps_landscape1, 0, sps_pps.length);
        }

        sps_pps = MyJni.getDefault().getData(1);
        if (sps_pps == null) {
            Log.e(TAG, "prepare1() sps_pps == null 2");
            return false;
        }

        if (ORIENTATION1 == Configuration.ORIENTATION_PORTRAIT) {
            sps_pps_landscape1 = new byte[sps_pps.length];
            System.arraycopy(sps_pps, 0, sps_pps_landscape1, 0, sps_pps.length);
        } else if (ORIENTATION1 == Configuration.ORIENTATION_LANDSCAPE) {
            sps_pps_portrait1 = new byte[sps_pps.length];
            System.arraycopy(sps_pps, 0, sps_pps_portrait1, 0, sps_pps.length);
        }

        try {
            mVideoDecoderMediaCodec1 = MediaCodec.createByCodecName(CODEC_NAME1);
        } catch (Exception e) {
            e.printStackTrace();
            MediaUtils.releaseMediaCodec(mVideoDecoderMediaCodec1);
            mVideoDecoderMediaCodec1 = null;
            Log.e(TAG, "prepare1() e:\n" + e.toString());
            return false;
        }

        int width = 0;
        int height = 0;
        int maxLength = 0;
        if (ORIENTATION1 == Configuration.ORIENTATION_PORTRAIT) {
            width = WIDTH1_P;
            height = HEIGHT1_P;
            maxLength = HEIGHT1_P;
            sps_pps = sps_pps_portrait1;
        } else if (ORIENTATION1 == Configuration.ORIENTATION_LANDSCAPE) {
            width = WIDTH1_L;
            height = HEIGHT1_L;
            maxLength = WIDTH1_L;
            sps_pps = sps_pps_landscape1;
        }
        MediaFormat format1 = MediaFormat.createVideoFormat(VIDEO_MIME1, width, height);
        format1.setInteger(MediaFormat.KEY_MAX_WIDTH, maxLength);
        format1.setInteger(MediaFormat.KEY_MAX_HEIGHT, maxLength);
        format1.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxLength * maxLength);

        /*format1.setInteger(MediaFormat.KEY_BIT_RATE, 8000000);
        format1.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
        format1.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format1.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format1.setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);*/

        /***
         对于Sony电视来说,由于其MediaCodec做的比较恶心.只要高大于1080时会被认为是4K分辨率,
         然后底层又限制了4K分辨率在同一时间只能configure一个MediaCodec.
         因此竖屏的分辨率最大只能是&&& * 1080,而横屏分辨率可以大于&&& * 1080.
         如果使用横屏的MediaFormat,那么发送端传送过来的数据是竖屏时,就解码不成功,即使输了横屏的sps_pps也没用.
         不过测试下来发现,横屏几次后就可以了,只是刚开始解码不成功.
         send: 1920*1080 receive: 1920*1080(configure) 解码成功
         send: 1080*1920 receive: 1080*1920(configure) 解码成功
         send: 1080*1920 receive: 1920*1080(configure) 解码不成功,横竖屏切换后解码成功
         send: 1920*1080 receive: 1080*1920(configure) 解码不成功,横竖屏切换后解码成功

         Sony TV:
         第一次configure: 1080*1920 OK
         第二次configure: 1920*1080 ERROR

         第一次configure: 1920*1080 OK
         第二次configure: 1080*1920 ERROR

         第一次configure: 1920*1080 OK
         第二次configure: 1920*1080 ERROR
         */
        if (TextUtils.equals(VIDEO_MIME1, MediaFormat.MIMETYPE_VIDEO_HEVC)) {
            mark1 = MARK1;
            format1.setByteBuffer("csd-0", ByteBuffer.wrap(sps_pps));
        } else if (TextUtils.equals(VIDEO_MIME1, MediaFormat.MIMETYPE_VIDEO_AVC)) {
            mark1 = handleSpsPps(format1, sps_pps);
        }
        if (mark1 == MARK0) {
            Log.e(TAG, "prepare1() mark1 == MARK0");
            return false;
        }
        mVideoDecoderMediaFormat1 = format1;
        Log.i(TAG, "prepare1() MediaFormat:\n" + format1.toString());
        Log.i(TAG,
                "prepare1() sps_pps portrait\n  sps_pps: " + Arrays.toString(sps_pps_portrait1));
        Log.i(TAG,
                "prepare1() sps_pps landscape\n  sps_pps: " + Arrays.toString(sps_pps_landscape1));

        return true;
    }

    private boolean prepare2() {
        if (TextUtils.isEmpty(VIDEO_MIME2)) {
            Log.e(TAG, "prepare2() VIDEO_MIME2 is empty");
            return false;
        }
        CODEC_NAME2 = MyJni.findDecoderCodecName(VIDEO_MIME2);
        Log.w(TAG, "prepare2() video VIDEO_MIME2: " + VIDEO_MIME2);
        Log.w(TAG, "prepare2() video CODEC_NAME2: " + CODEC_NAME2);
        if (TextUtils.isEmpty(CODEC_NAME2)) {
            Log.e(TAG, "prepare2() CODEC_NAME2 is empty");
            return false;
        }

        byte[] sps_pps = MyJni.getDefault().getData(2);
        if (sps_pps == null) {
            Log.e(TAG, "prepare2() sps_pps == null 1");
            return false;
        }

        if (ORIENTATION2 == Configuration.ORIENTATION_PORTRAIT) {
            sps_pps_portrait2 = new byte[sps_pps.length];
            System.arraycopy(sps_pps, 0, sps_pps_portrait2, 0, sps_pps.length);
        } else if (ORIENTATION2 == Configuration.ORIENTATION_LANDSCAPE) {
            sps_pps_landscape2 = new byte[sps_pps.length];
            System.arraycopy(sps_pps, 0, sps_pps_landscape2, 0, sps_pps.length);
        }

        sps_pps = MyJni.getDefault().getData(2);
        if (sps_pps == null) {
            Log.e(TAG, "prepare2() sps_pps == null 2");
            return false;
        }

        if (ORIENTATION2 == Configuration.ORIENTATION_PORTRAIT) {
            sps_pps_landscape2 = new byte[sps_pps.length];
            System.arraycopy(sps_pps, 0, sps_pps_landscape2, 0, sps_pps.length);
        } else if (ORIENTATION2 == Configuration.ORIENTATION_LANDSCAPE) {
            sps_pps_portrait2 = new byte[sps_pps.length];
            System.arraycopy(sps_pps, 0, sps_pps_portrait2, 0, sps_pps.length);
        }

        try {
            mVideoDecoderMediaCodec2 = MediaCodec.createByCodecName(CODEC_NAME2);
        } catch (Exception e) {
            e.printStackTrace();
            MediaUtils.releaseMediaCodec(mVideoDecoderMediaCodec2);
            mVideoDecoderMediaCodec2 = null;
            Log.e(TAG, "prepare2() e:\n" + e.toString());
            return false;
        }

        int width = 0;
        int height = 0;
        int maxLength = 0;
        if (ORIENTATION2 == Configuration.ORIENTATION_PORTRAIT) {
            width = WIDTH2_P;
            height = HEIGHT2_P;
            maxLength = HEIGHT2_P;
            sps_pps = sps_pps_portrait2;
        } else if (ORIENTATION2 == Configuration.ORIENTATION_LANDSCAPE) {
            width = WIDTH2_L;
            height = HEIGHT2_L;
            maxLength = WIDTH2_L;
            sps_pps = sps_pps_landscape2;
        }
        MediaFormat format2 = MediaFormat.createVideoFormat(VIDEO_MIME2, width, height);
        format2.setInteger(MediaFormat.KEY_MAX_WIDTH, maxLength);
        format2.setInteger(MediaFormat.KEY_MAX_HEIGHT, maxLength);
        format2.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxLength * maxLength);

        if (TextUtils.equals(VIDEO_MIME2, MediaFormat.MIMETYPE_VIDEO_HEVC)) {
            mark2 = MARK2;
            format2.setByteBuffer("csd-0", ByteBuffer.wrap(sps_pps));
        } else if (TextUtils.equals(VIDEO_MIME2, MediaFormat.MIMETYPE_VIDEO_AVC)) {
            mark2 = handleSpsPps(format2, sps_pps);
        }
        if (mark2 == MARK0) {
            Log.e(TAG, "prepare2() mark2 == MARK0");
            return false;
        }
        mVideoDecoderMediaFormat2 = format2;
        Log.i(TAG, "prepare2() MediaFormat:\n" + format2.toString());
        Log.i(TAG,
                "prepare2() sps_pps portrait\n  sps_pps: " + Arrays.toString(sps_pps_portrait2));
        Log.i(TAG,
                "prepare2() sps_pps landscape\n  sps_pps: " + Arrays.toString(sps_pps_landscape2));

        return true;
    }

    private int handleSpsPps(MediaFormat format, byte[] sps_pps) {
        int mark = MARK0;
        try {
            int index = -1;
            if (sps_pps[0] == 0
                    && sps_pps[1] == 0
                    && sps_pps[2] == 0
                    && sps_pps[3] == 1) {
                // region
                for (int i = 1; i < sps_pps.length; i++) {
                    if (sps_pps[i] == 0
                            && sps_pps[i + 1] == 0
                            && sps_pps[i + 2] == 0
                            && sps_pps[i + 3] == 1) {
                        index = i;
                        mark = MARK1;
                        break;
                    }
                }
                // endregion
            } else if (sps_pps[0] == 0
                    && sps_pps[1] == 0
                    && sps_pps[2] == 1) {
                // region
                for (int i = 1; i < sps_pps.length; i++) {
                    if (sps_pps[i] == 0
                            && sps_pps[i + 1] == 0
                            && sps_pps[i + 2] == 1) {
                        index = i;
                        mark = MARK2;
                        break;
                    }
                }
                // endregion
            }

            byte[] sps = null;
            byte[] pps = null;
            if (index != -1) {
                // region
                sps = new byte[index];
                pps = new byte[sps_pps.length - index];
                System.arraycopy(sps_pps, 0, sps, 0, sps.length);
                System.arraycopy(sps_pps, index, pps, 0, pps.length);
                // endregion
            } else {
                // region
                // ... 103 ... 104 ...
                /***
                 sps_pps: [1, 66, 0, 30, -1, -31, 0, 26,// 多余数据
                 103, 66, -128, 30, -106, 82, 2, -32, -93, 96, 41,// sps
                 16, 0, 0, 3, 0, 16, 0, 0, 3, 3, 41, -38, 20, 42, 72,// sps
                 1, 0, 4,// 多余数据
                 104, -53, -115, 72]// pps

                 csd-0: [0, 0, 0, 1, 103, 66, -128, 30, -106, 82, 2, -32, -93, 96, 41,
                 16, 0, 0, 3, 0, 16, 0, 0, 3, 3, 41, -38, 20, 42, 72]
                 csd-1: [0, 0, 0, 1, 104, -53, -115, 72]
                 */
                mark = MARK3;
                int spsIndex = -1;
                int spsLength = 0;
                int ppsIndex = -1;
                int ppsLength = 0;
                for (int i = 0; i < sps_pps.length; i++) {
                    if (sps_pps[i] == 103) {
                        // 0x67 = 103
                        if (spsIndex == -1) {
                            spsIndex = i;
                            spsLength = sps_pps[i - 1];
                            if (spsLength <= 0) {
                                spsIndex = -1;
                            }
                        }
                    } else if (sps_pps[i] == 104) {
                        // 103后面可能有2个或多个104
                        // 0x68 = 104
                        ppsIndex = i;
                        ppsLength = sps_pps[i - 1];
                    }
                }

                // ... 39 ... 40 ...
                /***
                 sps_pps: [1, 100, 0, 31, -1, -31, 0, 16,// 多余数据
                 39, 100, 0, 31, -84, 86, -64, -120, 30, 123, -26, -96, 32, 32, 32, 64,// sps
                 1, 0, 4,// 多余数据
                 40, -18, 60, -80,// pps
                 -3, -8, -8, 0]// 多余数据

                 csd-0: [0, 0, 0, 1, 39, 100, 0, 31, -84, 86, -64, -120,
                 30, 123, -26, -96, 32, 32, 32, 64]
                 csd-1: [0, 0, 0, 1, 40, -18, 60, -80]
                 */
                if (spsIndex == -1 || ppsIndex == -1) {
                    mark = MARK4;
                    spsIndex = -1;
                    spsLength = 0;
                    ppsIndex = -1;
                    ppsLength = 0;
                    for (int i = 0; i < sps_pps.length; i++) {
                        if (sps_pps[i] == 39) {
                            if (spsIndex == -1) {
                                spsIndex = i;
                                spsLength = sps_pps[i - 1];
                                if (spsLength <= 0) {
                                    spsIndex = -1;
                                }
                            }
                        } else if (sps_pps[i] == 40) {
                            ppsIndex = i;
                            ppsLength = sps_pps[i - 1];
                        }
                    }
                }
                if (spsIndex != -1 && ppsIndex != -1) {
                    sps = new byte[spsLength + 4];
                    pps = new byte[ppsLength + 4];
                    // 0x00, 0x00, 0x00, 0x01
                    sps[0] = pps[0] = 0;
                    sps[1] = pps[1] = 0;
                    sps[2] = pps[2] = 0;
                    sps[3] = pps[3] = 1;
                    System.arraycopy(sps_pps, spsIndex, sps, 4, spsLength);
                    System.arraycopy(sps_pps, ppsIndex, pps, 4, ppsLength);
                }
                // endregion
            }

            if (sps != null && pps != null) {
                Log.i(TAG, "handleSpsPps() video \n  csd-0: " +
                        Arrays.toString(sps));
                Log.i(TAG, "handleSpsPps() video \n  csd-1: " +
                        Arrays.toString(pps));
                format.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
                format.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
            } else {
                // 实在找不到sps和pps的数据了
                mark = MARK0;
            }
        } catch (Exception e) {
            Log.e(TAG, "handleSpsPps() Exception: \n" + e);
            mark = MARK0;
        }

        return mark;
    }

    private void addView(int what, int which_client) {
        mUiHandler.removeMessages(what);
        Message msg = mUiHandler.obtainMessage();
        msg.what = what;
        msg.arg1 = which_client;
        mUiHandler.sendMessage(msg);
    }

    private void removeView(int what, int which_client) {
        mUiHandler.removeMessages(what);
        Message msg = mUiHandler.obtainMessage();
        msg.what = what;
        msg.arg1 = which_client;
        mUiHandler.sendMessage(msg);
    }

    private void handleLayoutImpl() {
        Log.i(TAG, "handleLayout() start");
        if (whatIsDevice != Configuration.UI_MODE_TYPE_TELEVISION) {
            handleLayoutImpl(1, ORIENTATION1, false);
            handleLayoutImpl(2, ORIENTATION2, false);
        } else {
            handleLayoutImplForTV(1, ORIENTATION1, false);
            handleLayoutImplForTV(2, ORIENTATION2, false);
        }

        if (!mWindow1IsPlaying
                && !mWindow2IsPlaying) {
            finish();
        }
        Log.i(TAG, "handleLayout() end");
    }

    private void handleLayoutImpl(int which_client, int orientation, boolean doReturn) {
        Log.i(TAG, "handleLayoutImpl()" +
                " which_client: " + which_client +
                "  orientation: " + orientation +
                " doReturn: " + doReturn);
        switch (which_client) {
            case 1: {
                ORIENTATION1 = orientation;
                break;
            }
            case 2: {
                ORIENTATION2 = orientation;
                break;
            }
            default:
                return;
        }
        Log.i(TAG, "handleLayoutImpl()" +
                " ORIENTATION1: " + ORIENTATION1 +
                " ORIENTATION2: " + ORIENTATION2);

        int layout1_width = 0;
        int layout1_height = 0;
        int layout2_width = 0;
        int layout2_height = 0;
        // region
        if (mWindow1IsPlaying && !mWindow2IsPlaying) {
            // region
            if (ORIENTATION1 == Configuration.ORIENTATION_PORTRAIT) {
                layout1_width = mScreenWidthPortrait;
                layout1_height = mScreenHeightPortrait;
            } else {
                layout1_width = mScreenWidthLandscape;
                layout1_height = mScreenHeightLandscape;
            }
            layout2_width = 1;
            layout2_height = 1;
            // endregion
        } else if (!mWindow1IsPlaying && mWindow2IsPlaying) {
            // region
            layout1_width = 1;
            layout1_height = 1;
            if (ORIENTATION2 == Configuration.ORIENTATION_PORTRAIT) {
                layout2_width = mScreenWidthPortrait;
                layout2_height = mScreenHeightPortrait;
            } else {
                layout2_width = mScreenWidthLandscape;
                layout2_height = mScreenHeightLandscape;
            }
            // endregion
        } else {
            // game over
            return;
        }
        // endregion

        Log.i(TAG, "handleLayoutImpl()" +
                " layout1_width: " + layout1_width +
                " layout1_height: " + layout1_height +
                " layout2_width: " + layout2_width +
                " layout2_height: " + layout2_height);

        LinearLayout.LayoutParams childParams = null;
        childParams = (LinearLayout.LayoutParams) mPlayerLayout1.getLayoutParams();
        if (childParams != null) {
            childParams.width = layout1_width;
            childParams.height = layout1_height;
            mPlayerLayout1.setLayoutParams(childParams);
        }
        childParams = (LinearLayout.LayoutParams) mPlayerLayout2.getLayoutParams();
        if (childParams != null) {
            childParams.width = layout2_width;
            childParams.height = layout2_height;
            mPlayerLayout2.setLayoutParams(childParams);
        }

        handleLayoutImpl1(layout1_width, layout1_height, ORIENTATION1, doReturn);
        handleLayoutImpl2(layout2_width, layout2_height, ORIENTATION2, doReturn);
    }

    private void handleLayoutImplForTV(int which_client, int orientation, boolean doReturn) {
        Log.i(TAG, "handleLayoutImplForTV()" +
                " which_client: " + which_client +
                "  orientation: " + orientation +
                " doReturn: " + doReturn);
        switch (which_client) {
            case 1: {
                ORIENTATION1 = orientation;
                break;
            }
            case 2: {
                ORIENTATION2 = orientation;
                break;
            }
            default:
                return;
        }
        Log.i(TAG, "handleLayoutImplForTV()" +
                " ORIENTATION1: " + ORIENTATION1 +
                " ORIENTATION2: " + ORIENTATION2);

        int layout1_width = 0;
        int layout1_height = 0;
        int layout2_width = 0;
        int layout2_height = 0;
        // region
        if (mWindow1IsPlaying && mWindow2IsPlaying) {
            // region
            if (ORIENTATION1 == 1 && ORIENTATION2 == 1) {
                // region layout1竖屏layout2竖屏
                layout1_width = mScreenWidthLandscape / 2;
                layout1_height = mScreenHeightLandscape;
                layout2_width = mScreenWidthLandscape / 2;
                layout2_height = mScreenHeightLandscape;
                // endregion
            } else if (ORIENTATION1 == 1 && ORIENTATION2 == 2) {
                // region layout1竖屏layout2横屏
                layout1_height = mScreenHeightLandscape;
                layout1_width = mScreenHeightLandscape * WIDTH1_P / HEIGHT1_P;
                layout2_width = mScreenWidthLandscape - layout1_width;
                layout2_height = mScreenHeightLandscape;
                // endregion
            } else if (ORIENTATION1 == 2 && ORIENTATION2 == 1) {
                // region layout1横屏layout2竖屏
                layout2_height = mScreenHeightLandscape;
                layout2_width = mScreenHeightLandscape * WIDTH2_P / HEIGHT2_P;
                layout1_width = mScreenWidthLandscape - layout2_width;
                layout1_height = mScreenHeightLandscape;
                // endregion
            } else if (ORIENTATION1 == 2 && ORIENTATION2 == 2) {
                // region layout1横屏layout2横屏
                layout1_width = mScreenWidthLandscape / 2;
                layout1_height = mScreenHeightLandscape;
                layout2_width = mScreenWidthLandscape / 2;
                layout2_height = mScreenHeightLandscape;
                // endregion
            }
            // endregion
        } else if (mWindow1IsPlaying && !mWindow2IsPlaying) {
            // region
            layout1_width = mScreenWidthLandscape;
            layout1_height = mScreenHeightLandscape;
            layout2_width = 1;
            layout2_height = 1;
            // endregion
        } else if (!mWindow1IsPlaying && mWindow2IsPlaying) {
            // region
            layout1_width = 1;
            layout1_height = 1;
            layout2_width = mScreenWidthLandscape;
            layout2_height = mScreenHeightLandscape;
            // endregion
        } else {
            // game over
            return;
        }
        // endregion

        Log.i(TAG, "handleLayoutImplForTV()" +
                " layout1_width: " + layout1_width +
                " layout1_height: " + layout1_height +
                " layout2_width: " + layout2_width +
                " layout2_height: " + layout2_height);

        LinearLayout.LayoutParams childParams = null;
        childParams = (LinearLayout.LayoutParams) mPlayerLayout1.getLayoutParams();
        if (childParams != null) {
            childParams.width = layout1_width;
            childParams.height = layout1_height;
            mPlayerLayout1.setLayoutParams(childParams);
        }
        childParams = (LinearLayout.LayoutParams) mPlayerLayout2.getLayoutParams();
        if (childParams != null) {
            childParams.width = layout2_width;
            childParams.height = layout2_height;
            mPlayerLayout2.setLayoutParams(childParams);
        }

        handleLayoutImpl1(layout1_width, layout1_height, ORIENTATION1, doReturn);
        handleLayoutImpl2(layout2_width, layout2_height, ORIENTATION2, doReturn);
    }

    private void handleLayoutImpl1(
            int layout1_width, int layout1_height, int orientation, boolean doReturn) {
        if (layout1_width == 1 && layout1_height == 1) {
            LinearLayout.LayoutParams childParams =
                    (LinearLayout.LayoutParams) mSurfaceView1.getLayoutParams();
            if (childParams != null) {
                childParams.width = 1;
                childParams.height = 1;
                mSurfaceView1.setLayoutParams(childParams);
            }
            return;
        }
        int width = 0;
        int height = 0;
        switch (orientation) {
            case Configuration.ORIENTATION_PORTRAIT: {
                // 竖屏
                // 根据高算出宽
                if (mVideoDataDecodeRunnable1 != null) {
                    mVideoDataDecodeRunnable1.isPortrait = true;
                }

                /*if (ORIENTATION1 == Configuration.ORIENTATION_PORTRAIT && doReturn) {
                    return;
                }
                ORIENTATION1 = Configuration.ORIENTATION_PORTRAIT;*/

                if (whatIsDevice != Configuration.UI_MODE_TYPE_TELEVISION) {
                    // 非TV时强制竖屏
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }

                LinearLayout.LayoutParams childParams =
                        (LinearLayout.LayoutParams) mSurfaceView1.getLayoutParams();
                if (childParams != null) {
                    // 相当于视频源宽高
                    width = WIDTH1_P;
                    height = HEIGHT1_P;

                    int parentHeight = layout1_height - getStatusBarHeight();
                    childParams.height = parentHeight;
                    childParams.width = width * parentHeight / height;
                    if (childParams.width > layout1_width) {
                        childParams.width = layout1_width;
                        childParams.height = height * layout1_width / width;
                    }
                    mSurfaceView1.setLayoutParams(childParams);
                    Log.i(TAG, "handleLayoutImpl1() 1       video  width: " + width);
                    Log.i(TAG, "handleLayoutImpl1() 1       video height: " + height);
                    Log.i(TAG, "handleLayoutImpl1() 1  childParams.width: " + childParams.width);
                    Log.i(TAG, "handleLayoutImpl1() 1 childParams.height: " + childParams.height);
                }
                break;
            }
            case Configuration.ORIENTATION_LANDSCAPE: {
                // 横屏
                // 根据宽算出高
                if (mVideoDataDecodeRunnable1 != null) {
                    mVideoDataDecodeRunnable1.isPortrait = false;
                }

                /*if (ORIENTATION1 == Configuration.ORIENTATION_LANDSCAPE && doReturn) {
                    return;
                }
                ORIENTATION1 = Configuration.ORIENTATION_LANDSCAPE;*/

                if (whatIsDevice != Configuration.UI_MODE_TYPE_TELEVISION) {
                    // 非TV时强制横屏
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }

                LinearLayout.LayoutParams childParams =
                        (LinearLayout.LayoutParams) mSurfaceView1.getLayoutParams();
                if (childParams != null) {
                    // 相当于视频源宽高
                    width = WIDTH1_L;
                    height = HEIGHT1_L;

                    int parentHeight = layout1_height - getStatusBarHeight();
                    childParams.width = layout1_width;
                    childParams.height = layout1_width * height / width;
                    if (childParams.height > parentHeight) {
                        childParams.height = parentHeight;
                        childParams.width = parentHeight * width / height;
                    }
                    mSurfaceView1.setLayoutParams(childParams);
                    Log.i(TAG,
                            "handleLayoutImpl1() 2       video  width: " + width);
                    Log.i(TAG,
                            "handleLayoutImpl1() 2       video height: " + height);
                    Log.i(TAG,
                            "handleLayoutImpl1() 2  childParams.width: " + childParams.width);
                    Log.i(TAG,
                            "handleLayoutImpl1() 2 childParams.height: " + childParams.height);
                }
                break;
            }
            default:
                break;
        }
    }

    private void handleLayoutImpl2(
            int layout2_width, int layout2_height, int orientation, boolean doReturn) {
        if (layout2_width == 1 && layout2_height == 1) {
            LinearLayout.LayoutParams childParams =
                    (LinearLayout.LayoutParams) mSurfaceView2.getLayoutParams();
            if (childParams != null) {
                childParams.width = 1;
                childParams.height = 1;
                mSurfaceView2.setLayoutParams(childParams);
            }
            return;
        }
        int width = 0;
        int height = 0;
        switch (orientation) {
            case Configuration.ORIENTATION_PORTRAIT: {
                // 根据高算出宽
                if (mVideoDataDecodeRunnable2 != null) {
                    mVideoDataDecodeRunnable2.isPortrait = true;
                }

                /*if (ORIENTATION2 == Configuration.ORIENTATION_PORTRAIT && doReturn) {
                    return;
                }
                ORIENTATION2 = Configuration.ORIENTATION_PORTRAIT;*/

                if (whatIsDevice != Configuration.UI_MODE_TYPE_TELEVISION) {
                    // 非TV时强制竖屏
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }

                LinearLayout.LayoutParams childParams =
                        (LinearLayout.LayoutParams) mSurfaceView2.getLayoutParams();
                if (childParams != null) {
                    // 相当于视频源宽高
                    width = WIDTH2_P;
                    height = HEIGHT2_P;

                    int parentHeight = layout2_height - getStatusBarHeight();
                    childParams.height = parentHeight;
                    childParams.width = width * parentHeight / height;
                    if (childParams.width > layout2_width) {
                        childParams.width = layout2_width;
                        childParams.height = height * layout2_width / width;
                    }
                    mSurfaceView2.setLayoutParams(childParams);
                    Log.i(TAG, "handleLayoutImpl2() 1       video  width: " + width);
                    Log.i(TAG, "handleLayoutImpl2() 1       video height: " + height);
                    Log.i(TAG, "handleLayoutImpl2() 1  childParams.width: " + childParams.width);
                    Log.i(TAG, "handleLayoutImpl2() 1 childParams.height: " + childParams.height);
                }
                break;
            }
            case Configuration.ORIENTATION_LANDSCAPE: {
                // 根据宽算出高
                if (mVideoDataDecodeRunnable2 != null) {
                    mVideoDataDecodeRunnable2.isPortrait = false;
                }

                /*if (ORIENTATION2 == Configuration.ORIENTATION_LANDSCAPE && doReturn) {
                    return;
                }
                ORIENTATION2 = Configuration.ORIENTATION_LANDSCAPE;*/

                if (whatIsDevice != Configuration.UI_MODE_TYPE_TELEVISION) {
                    // 非TV时强制横屏
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }

                LinearLayout.LayoutParams childParams =
                        (LinearLayout.LayoutParams) mSurfaceView2.getLayoutParams();
                if (childParams != null) {
                    // 相当于视频源宽高
                    width = WIDTH2_L;
                    height = HEIGHT2_L;

                    int parentHeight = layout2_height - getStatusBarHeight();
                    childParams.width = layout2_width;
                    childParams.height = layout2_width * height / width;
                    if (childParams.height > parentHeight) {
                        childParams.height = parentHeight;
                        childParams.width = parentHeight * width / height;
                    }
                    mSurfaceView2.setLayoutParams(childParams);
                    Log.i(TAG,
                            "handleLayoutImpl2() 2       video  width: " + width);
                    Log.i(TAG,
                            "handleLayoutImpl2() 2       video height: " + height);
                    Log.i(TAG,
                            "handleLayoutImpl2() 2  childParams.width: " + childParams.width);
                    Log.i(TAG,
                            "handleLayoutImpl2() 2 childParams.height: " + childParams.height);
                }
                break;
            }
            default:
                break;
        }
    }

    private void uiHandleMessage(Message msg) {
        if (msg == null) {
            return;
        }

        switch (msg.what) {
            case MSG_UI_ADD_VIEW: {
                switch (msg.arg1) {
                    case 1: {
                        if (!mIsAddedView1
                                && mPlayerLayout1 != null
                                && mSurfaceView1 != null) {
                            Log.i(TAG, "mPlayerLayout1.addView(mSurfaceView1)");
                            mIsAddedView1 = true;
                            mSurfaceHolderCallback1 = new SurfaceCallback(1);
                            mSurfaceView1.getHolder().addCallback(mSurfaceHolderCallback1);
                            mPlayerLayout1.addView(mSurfaceView1);
                        }
                        break;
                    }
                    case 2: {
                        if (!mIsAddedView2
                                && mPlayerLayout2 != null
                                && mSurfaceView2 != null) {
                            Log.i(TAG, "mPlayerLayout2.addView(mSurfaceView2)");
                            mIsAddedView2 = true;
                            mSurfaceHolderCallback2 = new SurfaceCallback(2);
                            mSurfaceView2.getHolder().addCallback(mSurfaceHolderCallback2);
                            mPlayerLayout2.addView(mSurfaceView2);
                        }
                    }
                    case 3: {
                        break;
                    }
                    default:
                        break;
                }

                break;
            }
            case MSG_UI_REMOVE_VIEW: {
                switch (msg.arg1) {
                    case 1: {
                        if (mIsAddedView1
                                && mPlayerLayout1 != null
                                && mSurfaceView1 != null) {
                            Log.i(TAG, "mPlayerLayout1.removeView(mSurfaceView1)");
                            mIsAddedView1 = false;
                            mPlayerLayout1.removeView(mSurfaceView1);
                            mSurfaceView1.getHolder().removeCallback(mSurfaceHolderCallback1);
                        }
                        break;
                    }
                    case 2: {
                        if (mIsAddedView2
                                && mPlayerLayout2 != null
                                && mSurfaceView2 != null) {
                            Log.i(TAG, "mPlayerLayout2.removeView(mSurfaceView2)");
                            mIsAddedView2 = false;
                            mPlayerLayout2.removeView(mSurfaceView2);
                            mSurfaceView2.getHolder().removeCallback(mSurfaceHolderCallback2);
                        }
                        break;
                    }
                    default:
                        break;
                }

                mUiHandler.removeMessages(MSG_UI_HANDLE_LAYOUT);
                mUiHandler.sendEmptyMessageDelayed(MSG_UI_HANDLE_LAYOUT, 500);
                break;
            }
            case MSG_UI_SURFACE_CHANGED: {
                break;
            }
            case MSG_UI_HANDLE_LAYOUT: {
                handleLayoutImpl();
                break;
            }
            case MSG_UI_CHANGE_WINDOW: {
                int which_client = msg.arg1;
                int orientation = msg.arg2;
                if (whatIsDevice != Configuration.UI_MODE_TYPE_TELEVISION) {
                    handleLayoutImpl(which_client, orientation, true);
                } else {
                    handleLayoutImplForTV(which_client, orientation, true);
                }
                break;
            }
            case MSG_UI_SURFACE_CREATED: {
                surfaceCreated(msg.arg1);
                break;
            }
            case MSG_UI_SURFACE_DESTROYED: {
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
            case MSG_THREAD_SET_INTENT: {
                setIntentImpl((Intent) msg.obj);
                break;
            }
            default:
                break;
        }
    }

    private void surfaceCreated(int which_client) {
        mUiHandler.removeMessages(MSG_UI_HANDLE_LAYOUT);
        mUiHandler.sendEmptyMessageDelayed(MSG_UI_HANDLE_LAYOUT, 500);

        switch (which_client) {
            case 1: {
                if (DECODER_MEDIA_CODEC_GO_JNI) {
                    JniObject jniObject = JniObject.obtain();
                    jniObject.valueInt = which_client;
                    jniObject.valueObject = mSurfaceView1.getHolder().getSurface();
                    MyJni.getDefault().onTransact(DO_SOMETHING_CODE_set_surface, jniObject);
                    return;
                }

                if (mVideoDataDecodeRunnable1 == null
                        && mVideoDecoderMediaCodec1 != null) {
                    Log.i(TAG, "surfaceCreated() mVideoDecoderMediaCodec1.configure");
                    try {
                        mVideoDecoderMediaCodec1.configure(
                                mVideoDecoderMediaFormat1,
                                mSurfaceView1.getHolder().getSurface(),
                                null, 0);
                    } catch (Exception e) {
                        Log.e(TAG, "surfaceCreated() portrait 1 " + e.toString());
                        MediaUtils.releaseMediaCodec(mVideoDecoderMediaCodec1);
                        mVideoDecoderMediaCodec1 = null;
                        return;
                    }
                    Log.i(TAG, "surfaceCreated() mVideoDecoderMediaCodec1.start");
                    try {
                        mVideoDecoderMediaCodec1.start();
                    } catch (Exception e) {
                        Log.e(TAG, "surfaceCreated() portrait 2 " + e.toString());
                        MediaUtils.releaseMediaCodec(mVideoDecoderMediaCodec1);
                        mVideoDecoderMediaCodec1 = null;
                        return;
                    }

                    Log.i(TAG, "surfaceCreated() new VideoDataDecodeRunnable");
                    mEDCallback1 = new EDCallback(1);
                    mVideoDataDecodeRunnable1 = new VideoDataDecodeRunnable(
                            mEDCallback1,
                            mVideoDecoderMediaCodec1,
                            1);
                    if (ORIENTATION1 == Configuration.ORIENTATION_PORTRAIT) {
                        mVideoDataDecodeRunnable1.isPortrait = true;
                    } else if (ORIENTATION1 == Configuration.ORIENTATION_LANDSCAPE) {
                        mVideoDataDecodeRunnable1.isPortrait = false;
                    }
                    new Thread(mVideoDataDecodeRunnable1).start();
                    Log.i(TAG, "surfaceCreated() video MediaCodec start");
                }
                break;
            }
            case 2: {
                if (DECODER_MEDIA_CODEC_GO_JNI) {
                    JniObject jniObject = JniObject.obtain();
                    jniObject.valueInt = which_client;
                    jniObject.valueObject = mSurfaceView2.getHolder().getSurface();
                    MyJni.getDefault().onTransact(DO_SOMETHING_CODE_set_surface, jniObject);
                    return;
                }

                if (mVideoDataDecodeRunnable2 == null
                        && mVideoDecoderMediaCodec2 != null) {
                    Log.i(TAG, "surfaceCreated() mVideoDecoderMediaCodec2.configure");

                    Bundle param = new Bundle();
                    param.putString("X-tv-output-path", "OUTPUT_GRAPHIC");
                    mVideoDecoderMediaCodec2.setParameters(param);

                    try {
                        mVideoDecoderMediaCodec2.configure(
                                mVideoDecoderMediaFormat2,
                                mSurfaceView2.getHolder().getSurface(),
                                null, 0);
                    } catch (Exception e) {
                        Log.e(TAG, "surfaceCreated() portrait 1 " + e.toString());
                        MediaUtils.releaseMediaCodec(mVideoDecoderMediaCodec2);
                        mVideoDecoderMediaCodec2 = null;
                        return;
                    }
                    Log.i(TAG, "surfaceCreated() mVideoDecoderMediaCodec2.start");
                    try {
                        mVideoDecoderMediaCodec2.start();
                    } catch (Exception e) {
                        Log.e(TAG, "surfaceCreated() portrait 2 " + e.toString());
                        MediaUtils.releaseMediaCodec(mVideoDecoderMediaCodec2);
                        mVideoDecoderMediaCodec2 = null;
                        return;
                    }

                    Log.i(TAG, "surfaceCreated() new VideoDataDecodeRunnable");
                    mEDCallback2 = new EDCallback(2);
                    mVideoDataDecodeRunnable2 = new VideoDataDecodeRunnable(
                            mEDCallback2,
                            mVideoDecoderMediaCodec2,
                            2);
                    if (ORIENTATION2 == Configuration.ORIENTATION_PORTRAIT) {
                        mVideoDataDecodeRunnable2.isPortrait = true;
                    } else if (ORIENTATION2 == Configuration.ORIENTATION_LANDSCAPE) {
                        mVideoDataDecodeRunnable2.isPortrait = false;
                    }
                    new Thread(mVideoDataDecodeRunnable2).start();
                    Log.i(TAG, "surfaceCreated() video MediaCodec start");
                }
                break;
            }
            default:
                break;
        }
    }

    private void surfaceDestroyed(int which_client) {
        MyJni.getDefault().closeClient(which_client);
        switch (which_client) {
            case 1: {
                mWindow1IsPlaying = false;
                MediaUtils.releaseMediaCodec(mVideoDecoderMediaCodec1);
                mVideoDecoderMediaCodec1 = null;
                mVideoDecoderMediaFormat1 = null;
                mEDCallback1 = null;
                if (mVideoDataDecodeRunnable1 != null) {
                    mVideoDataDecodeRunnable1.isPlaying = false;
                    mVideoDataDecodeRunnable1 = null;
                }
                sps_pps_portrait1 = null;
                sps_pps_landscape1 = null;
                break;
            }
            case 2: {
                mWindow2IsPlaying = false;
                MediaUtils.releaseMediaCodec(mVideoDecoderMediaCodec2);
                mVideoDecoderMediaCodec2 = null;
                mVideoDecoderMediaFormat2 = null;
                mEDCallback2 = null;
                if (mVideoDataDecodeRunnable2 != null) {
                    mVideoDataDecodeRunnable2.isPlaying = false;
                    mVideoDataDecodeRunnable2 = null;
                }
                sps_pps_portrait2 = null;
                sps_pps_landscape2 = null;
                break;
            }
            default:
                break;
        }
    }


    // 状态栏高度
    private int getStatusBarHeight() {
        int height = 0;
        Resources resources = getResources();
        int resourceId = resources.getIdentifier(
                "status_bar_height",
                "dimen",
                "android");
        if (resourceId > 0) {
            height = resources.getDimensionPixelSize(resourceId);
        }
        // getStatusBarHeight() height: 48 63 95
        // Log.i(TAG, "getStatusBarHeight() height: " + height);
        return height;
    }

    private class SurfaceCallback implements SurfaceHolder.Callback {

        private int which_client = 0;

        public SurfaceCallback(int which_client) {
            this.which_client = which_client;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.i(TAG, "surfaceCreated() which_client: " + which_client);
            //PlayerActivity.this.surfaceCreated(which_client);
            Message msg = mUiHandler.obtainMessage();
            msg.what = MSG_UI_SURFACE_CREATED;
            msg.arg1 = which_client;
            mUiHandler.sendMessage(msg);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "surfaceDestroyed() which_client: " + which_client);
            // 按"返回"键或者打开了其他App(比onStop的调用要早一点)
            PlayerActivity.this.surfaceDestroyed(which_client);
        }
    }

    private class EDCallback implements EDMediaCodec.Callback {

        private int which_client = 0;

        public EDCallback(int which_client) {
            this.which_client = which_client;
        }

        @Override
        public boolean isVideoFinished() {
            switch (which_client) {
                case 1: {
                    return !mWindow1IsPlaying;
                }
                case 2: {
                    return !mWindow2IsPlaying;
                }
                default:
                    break;
            }

            return true;
        }

        @Override
        public boolean isAudioFinished() {
            return false;
        }

        @Override
        public void handleVideoOutputFormat(MediaFormat mediaFormat) {
            Log.d(TAG, "handleVideoOutputFormat() mediaFormat:\n" + mediaFormat);
        }

        @Override
        public void handleAudioOutputFormat(MediaFormat mediaFormat) {

        }

        @Override
        public int handleVideoOutputBuffer(ByteBuffer room, MediaCodec.BufferInfo roomInfo,
                                           boolean isPortrait) {
            return 0;
        }

        @Override
        public int handleAudioOutputBuffer(ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
            return 0;
        }
    }

    private class VideoDataDecodeRunnable implements Runnable {

        private EDMediaCodec.Callback callback;
        private MediaCodec codec;
        private int which_client = 0;
        private boolean isPlaying = true;
        private boolean isPortrait = true;
        private boolean isPrePortrait = true;

        public VideoDataDecodeRunnable(EDMediaCodec.Callback callback,
                                       MediaCodec codec,
                                       int which_client) {
            this.callback = callback;
            this.codec = codec;
            this.which_client = which_client;
        }

        @Override
        public void run() {
            Log.i(TAG, "VideoDataDecodeRunnable start which_client: " + which_client);
            boolean feedInputBufferAndDrainOutputBuffer = false;

            long totalTakeCount = 0;
            long totalTakeTime = 0;
            long averageTakeTime = 0;
            isPrePortrait = isPortrait;
            byte[] frame = null;
            byte[] sps_pps_frame = null;
            boolean hasError = false;
            while (isPlaying) {
                // A
                frame = MyJni.getDefault().getData(which_client);
                if (frame != null) {
                    /***
                     得出一个很重要的结论:
                     竖屏数据不能用横屏的MediaCodec进行解码
                     横屏数据不能用竖屏的MediaCodec进行解码
                     */
                    if (frame[frame.length - 2] == 1 && !isPortrait) {
                        // 竖屏数据
                        continue;
                    } else if (frame[frame.length - 2] == 2 && isPortrait) {
                        // 横屏数据
                        continue;
                    }
                    if (isPrePortrait != isPortrait) {
                        // 横竖屏切换后需要写一下sps_pps数据
                        switch (which_client) {
                            case 1: {
                                if (isPortrait) {
                                    Log.w(TAG,
                                            "VideoDataDecodeRunnable PORTRAIT  which_client: " + which_client);
                                    sps_pps_frame = sps_pps_portrait1;
                                } else {
                                    Log.w(TAG,
                                            "VideoDataDecodeRunnable LANDSCAPE which_client: " + which_client);
                                    sps_pps_frame = sps_pps_landscape1;
                                }
                                break;
                            }
                            case 2: {
                                if (isPortrait) {
                                    Log.w(TAG,
                                            "VideoDataDecodeRunnable PORTRAIT  which_client: " + which_client);
                                    sps_pps_frame = sps_pps_portrait2;
                                } else {
                                    Log.w(TAG,
                                            "VideoDataDecodeRunnable LANDSCAPE which_client: " + which_client);
                                    sps_pps_frame = sps_pps_landscape2;
                                }
                                break;
                            }
                            default:
                                break;
                        }

                        /*Log.i(TAG, "VideoDataDecodeRunnable sps_pps:\n" +
                                Arrays.toString(sps_pps_frame));*/
                        EDMediaCodec.feedInputBufferAndDrainOutputBuffer(
                                callback,
                                EDMediaCodec.TYPE.TYPE_VIDEO,
                                codec,
                                sps_pps_frame,
                                0,
                                sps_pps_frame.length,
                                0,
                                0,
                                true,
                                true,
                                true);
                        isPrePortrait = isPortrait;
                    }
                    // B
                    //long start = SystemClock.uptimeMillis();
                    feedInputBufferAndDrainOutputBuffer =
                            EDMediaCodec.feedInputBufferAndDrainOutputBuffer(
                                    callback,
                                    EDMediaCodec.TYPE.TYPE_VIDEO,
                                    codec,
                                    frame,
                                    0,
                                    frame.length - 2,
                                    0,
                                    0,
                                    true,
                                    true,
                                    true);// isPortrait ? true : false
                    frame = null;
                    if (!feedInputBufferAndDrainOutputBuffer) {
                        hasError = true;
                        break;
                    }
                    // C
                    /*long end = SystemClock.uptimeMillis();
                    ++totalTakeCount;
                    totalTakeTime += (end - start);
                    averageTakeTime = totalTakeTime / totalTakeCount;
                    Log.i(TAG, "VideoDataDecodeRunnable video take time: " + averageTakeTime);*/
                    // 平均值稳定在26ms(A ---> C)
                    // 平均值稳定在16ms(B ---> C)
                    continue;
                }

                break;
            }

            if (hasError) {
                mWindow1IsPlaying = false;
                removeView(MSG_UI_REMOVE_VIEW, which_client);
            }

            Log.i(TAG, "VideoDataDecodeRunnable end   which_client: " + which_client);
        }
    }

}
