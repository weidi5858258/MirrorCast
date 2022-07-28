package com.weidi.mirrorcast;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;

import static com.weidi.mirrorcast.MainActivity.FLAG;
import static com.weidi.mirrorcast.PlayerActivity.HEIGHT1_L;
import static com.weidi.mirrorcast.PlayerActivity.HEIGHT1_P;
import static com.weidi.mirrorcast.PlayerActivity.HEIGHT2_L;
import static com.weidi.mirrorcast.PlayerActivity.HEIGHT2_P;
import static com.weidi.mirrorcast.PlayerActivity.ORIENTATION1;
import static com.weidi.mirrorcast.PlayerActivity.ORIENTATION2;
import static com.weidi.mirrorcast.PlayerActivity.PLAYER_ACTIVITY_IS_LIVE;
import static com.weidi.mirrorcast.PlayerActivity.VIDEO_MIME1;
import static com.weidi.mirrorcast.PlayerActivity.VIDEO_MIME2;
import static com.weidi.mirrorcast.PlayerActivity.WIDTH1_L;
import static com.weidi.mirrorcast.PlayerActivity.WIDTH1_P;
import static com.weidi.mirrorcast.PlayerActivity.WIDTH2_L;
import static com.weidi.mirrorcast.PlayerActivity.WIDTH2_P;

/***
 http://anddymao.com/2019/10/16/2019-10-16-ndk-MediaCodec/
 NDK中使用MediaCodec编解码视频
 */
public class MyJni {

    private static final String TAG = "player_alexander";

    // jni  ---> java
    public static final JniObject serverFrame1 = JniObject.obtain();
    public static final JniObject serverFrame2 = JniObject.obtain();
    // java ---> jni
    public static final JniObject clientFrame = JniObject.obtain();

    static {
        try {
            System.loadLibrary("socket");
        } catch (UnsatisfiedLinkError error) {
            Log.e(TAG, "卧槽, socket库加载失败了!!!");
            error.printStackTrace();
        }
    }

    private volatile static MyJni sMyJni;

    private MyJni() {
    }

    public static MyJni getDefault() {
        if (sMyJni == null) {
            synchronized (MyJni.class) {
                if (sMyJni == null) {
                    sMyJni = new MyJni();
                }
            }
        }
        return sMyJni;
    }

    // java ---> jni
    public static final int DO_SOMETHING_CODE_init = 1000;
    public static final int DO_SOMETHING_CODE_Server_accept = 1001;
    public static final int DO_SOMETHING_CODE_Client_connect = 1002;
    public static final int DO_SOMETHING_CODE_Client_disconnect = 1003;
    public static final int DO_SOMETHING_CODE_Client_send_data = 1004;
    public static final int DO_SOMETHING_CODE_Client_set_info = 1005;
    public static final int DO_SOMETHING_CODE_Server_set_ip = 1006;
    public static final int DO_SOMETHING_CODE_Server_close = 1007;
    public static final int DO_SOMETHING_CODE_get_server_port = 1008;
    public static final int DO_SOMETHING_CODE_close_all_clients = 1009;
    public static final int DO_SOMETHING_CODE_close_one_client = 1010;
    public static final int DO_SOMETHING_CODE_set_surface = 1011;
    public static final int DO_SOMETHING_CODE_start_record_screen_prepare = 1012;
    public static final int DO_SOMETHING_CODE_start_record_screen = 1013;
    public static final int DO_SOMETHING_CODE_is_recording = 1014;
    public static final int DO_SOMETHING_CODE_stop_record_screen = 1015;
    public static final int DO_SOMETHING_CODE_fromPortraitToLandscape = 1016;
    public static final int DO_SOMETHING_CODE_fromLandscapeToPortrait = 1017;
    public static final int DO_SOMETHING_CODE_release_sps_pps = 1018;
    public static final int DO_SOMETHING_CODE_only_output_key_frame = 1019;

    // jni ---> java
    public static final int DO_SOMETHING_CODE_connected = 2000;
    public static final int DO_SOMETHING_CODE_disconnected = 2001;
    public static final int DO_SOMETHING_CODE_change_window = 2002;
    public static final int DO_SOMETHING_CODE_find_decoder_codec_name = 2003;
    public static final int DO_SOMETHING_CODE_find_createPortraitVirtualDisplay = 2004;
    public static final int DO_SOMETHING_CODE_find_createLandscapeVirtualDisplay = 2005;
    public static final int DO_SOMETHING_CODE_find_encoder_send_data_error = 2006;

    // 上层编解码,上层传输
    // 上层编解码,底层传输
    // 底层编解码,上层传输
    // 底层编解码,底层传输

    // true表示使用native进行编解码,false表示使用java进行编解码
    public static final boolean USE_MEDIACODEC_FOR_JNI = false;
    // true表示使用native进行tcp或者udp传输,false表示使用java进行tcp或者udp传输
    public static final boolean USE_TRANSMISSION_FOR_JNI = false;
    // true表示使用TCP进行数据的传输,false表示使用UDP进行数据的传输,还需要跟底层一起统一(值还没有想好)
    public static final boolean USE_TCP = false;

    private Context mContext;
    private static final int QUEUE_LENGTH = 50;
    private ArrayBlockingQueue<byte[]> mPlayQueue1 = null;
    private ArrayBlockingQueue<byte[]> mPlayQueue2 = null;
    // 不能把sps_pps及第一个关键帧给remove掉
    private long mTakeCount1 = 0;
    private long mTakeCount2 = 0;

    public native String onTransact(int code, JniObject jniObject);

    public void setContext(Context context) {
        mContext = context;
        Phone.register(this);
    }

    public void onDestroy() {
        mTakeCount1 = 0;
        if (mPlayQueue1 != null) {
            mPlayQueue1.clear();
            mPlayQueue1 = null;
        }
        mTakeCount2 = 0;
        if (mPlayQueue2 != null) {
            mPlayQueue2.clear();
            mPlayQueue2 = null;
        }
        // 如果服务端主动退出的情况下,还需要向jni发送一个消息,表示要断开连接了
        onTransact(DO_SOMETHING_CODE_close_all_clients, null);
    }

    public void closeClient(int which_client) {
        JniObject jniObject = JniObject.obtain();
        jniObject.valueInt = which_client;
        onTransact(DO_SOMETHING_CODE_close_one_client, jniObject);
        switch (which_client) {
            case 1: {
                mTakeCount1 = 0;
                if (mPlayQueue1 != null) {
                    mPlayQueue1.clear();
                    mPlayQueue1 = null;
                }
                break;
            }
            case 2: {
                mTakeCount2 = 0;
                if (mPlayQueue2 != null) {
                    mPlayQueue2.clear();
                    mPlayQueue2 = null;
                }
                break;
            }
            default:
                break;
        }
        jniObject = null;
    }

    private synchronized void initQueue(int which_client) {
        switch (which_client) {
            case 1: {
                mTakeCount1 = 0;
                if (mPlayQueue1 == null) {
                    mPlayQueue1 = new ArrayBlockingQueue<>(QUEUE_LENGTH);
                }
                break;
            }
            case 2: {
                mTakeCount2 = 0;
                if (mPlayQueue2 == null) {
                    mPlayQueue2 = new ArrayBlockingQueue<>(QUEUE_LENGTH);
                }
                break;
            }
            default:
                break;
        }
    }

    private synchronized void clearQueue(int which_client) {
        switch (which_client) {
            case 1: {
                mTakeCount1 = 0;
                if (mPlayQueue1 != null) {
                    mPlayQueue1.clear();
                    mPlayQueue1 = null;
                }
                break;
            }
            case 2: {
                mTakeCount2 = 0;
                if (mPlayQueue2 != null) {
                    mPlayQueue2.clear();
                    mPlayQueue2 = null;
                }
                break;
            }
            default:
                break;
        }
    }

    private void jni2Java(int code, JniObject jniObject) {
        switch (code) {
            case DO_SOMETHING_CODE_connected: {
                if (mContext != null) {
                    int which_client = jniObject.valueInt;
                    Log.d(TAG,
                            "jni2Java() DO_SOMETHING_CODE_connected which_client: " + which_client);
                    initQueue(which_client);
                    Intent intent = new Intent();
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setClass(mContext, PlayerActivity.class);
                    intent.putExtra("which_client", which_client);
                    intent.putExtra("do_something", "playback");
                    mContext.startActivity(intent);
                }
                jniObject = null;
                break;
            }
            case DO_SOMETHING_CODE_disconnected: {
                if (mContext != null) {
                    int which_client = jniObject.valueInt;
                    Log.d(TAG,
                            "jni2Java() DO_SOMETHING_CODE_disconnected which_client: " + which_client);
                    clearQueue(which_client);
                    if (PLAYER_ACTIVITY_IS_LIVE) {
                        Intent intent = new Intent();
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.setClass(mContext, PlayerActivity.class);
                        intent.putExtra("which_client", which_client);
                        intent.putExtra("do_something", "finish");
                        mContext.startActivity(intent);
                    }
                }
                jniObject = null;
                break;
            }
            case DO_SOMETHING_CODE_Client_set_info: {
                int which_client = jniObject.valueInt;
                Log.d(TAG,
                        "jni2Java() DO_SOMETHING_CODE_Client_set_info which_client: " + which_client);
                long length = jniObject.valueLong;
                String client_info = jniObject.valueString;
                // ARS-AL00@@@@@video/hevc@@@@@1080@@@@@2244@@@@@1
                Log.i(TAG, "jni2Java() client_info: " + client_info);
                String[] infos = client_info.split(FLAG);
                String device_name = infos[0];
                String video_mime = infos[1];
                String widthStr = infos[2];
                String heightStr = infos[3];
                String orientationStr = infos[4];
                int orientation = 1;
                try {
                    orientation = Integer.parseInt(orientationStr);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                initQueue(which_client);
                switch (which_client) {
                    case 1: {
                        VIDEO_MIME1 = video_mime;
                        ORIENTATION1 = orientation;
                        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                            WIDTH1_P = Integer.parseInt(widthStr);
                            HEIGHT1_P = Integer.parseInt(heightStr);
                            WIDTH1_L = HEIGHT1_P;
                            HEIGHT1_L = WIDTH1_P;
                        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            WIDTH1_L = Integer.parseInt(widthStr);
                            HEIGHT1_L = Integer.parseInt(heightStr);
                            WIDTH1_P = HEIGHT1_L;
                            HEIGHT1_P = WIDTH1_L;
                        }
                        Log.d(TAG, "jni2Java()" +
                                " VIDEO_MIME1: " + VIDEO_MIME1 +
                                " WIDTH1_P: " + WIDTH1_P +
                                " HEIGHT1_P: " + HEIGHT1_P +
                                " WIDTH1_L: " + WIDTH1_L +
                                " HEIGHT1_L: " + HEIGHT1_L +
                                " ORIENTATION1: " + ORIENTATION1);
                        break;
                    }
                    case 2: {
                        VIDEO_MIME2 = video_mime;
                        ORIENTATION2 = orientation;
                        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                            WIDTH2_P = Integer.parseInt(widthStr);
                            HEIGHT2_P = Integer.parseInt(heightStr);
                            WIDTH2_L = HEIGHT2_P;
                            HEIGHT2_L = WIDTH2_P;
                        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            WIDTH2_L = Integer.parseInt(widthStr);
                            HEIGHT2_L = Integer.parseInt(heightStr);
                            WIDTH2_P = HEIGHT2_L;
                            HEIGHT2_P = WIDTH2_L;
                        }
                        Log.d(TAG, "jni2Java()" +
                                " VIDEO_MIME2: " + VIDEO_MIME2 +
                                " WIDTH2_P: " + WIDTH2_P +
                                " HEIGHT2_P: " + HEIGHT2_P +
                                " WIDTH2_L: " + WIDTH2_L +
                                " HEIGHT2_L: " + HEIGHT2_L +
                                " ORIENTATION2: " + ORIENTATION2);
                        break;
                    }
                    default:
                        break;
                }
                break;
            }
            case DO_SOMETHING_CODE_change_window: {
                if (PlayerActivity.playerActivity != null && jniObject != null) {
                    switch (jniObject.valueInt) {
                        case 1: {
                            if (mPlayQueue1 != null) {
                                try {
                                    mPlayQueue1.clear();
                                } catch (Exception e) {
                                    Log.e(TAG, "jni2Java() DO_SOMETHING_CODE_change_window " +
                                            "exception: "
                                            + e.toString());
                                }
                            }
                            break;
                        }
                        case 2: {
                            if (mPlayQueue2 != null) {
                                try {
                                    mPlayQueue2.clear();
                                } catch (Exception e) {
                                    Log.e(TAG, "jni2Java() DO_SOMETHING_CODE_change_window " +
                                            "exception: "
                                            + e.toString());
                                }
                            }
                            break;
                        }
                        default:
                            break;
                    }

                    PlayerActivity.playerActivity.changeWindow(
                            jniObject.valueInt, (int) jniObject.valueLong);
                }
                break;
            }
            case DO_SOMETHING_CODE_find_decoder_codec_name: {
                if (jniObject == null) {
                    return;
                }
                String mime = jniObject.valueString;
                if (TextUtils.isEmpty(mime)) {
                    return;
                }
                String codecName = findDecoderCodecName(mime);
                jniObject.valueString = codecName;
                Log.i(TAG, "DO_SOMETHING_CODE_find_decoder_codec_name codecName: " +
                        codecName);
                break;
            }
            case DO_SOMETHING_CODE_find_createPortraitVirtualDisplay: {
                Phone.call(MediaClientService.class.getName(),
                        DO_SOMETHING_CODE_find_createPortraitVirtualDisplay, null);
                break;
            }
            case DO_SOMETHING_CODE_find_createLandscapeVirtualDisplay: {
                Phone.call(MediaClientService.class.getName(),
                        DO_SOMETHING_CODE_find_createLandscapeVirtualDisplay, null);
                break;
            }
            case DO_SOMETHING_CODE_find_encoder_send_data_error: {
                Phone.call(MediaClientService.class.getName(),
                        DO_SOMETHING_CODE_find_encoder_send_data_error, null);
                break;
            }
            default:
                break;
        }
    }

    public byte[] getData(int which_client) {
        switch (which_client) {
            case 1: {
                if (mPlayQueue1 != null) {
                    try {
                        drainFrame(mPlayQueue1, mTakeCount1++);
                        return mPlayQueue1.take();
                    } catch (Exception e) {
                        Log.e(TAG, "getData1() exception : " + e.toString());
                    }
                }
                break;
            }
            case 2: {
                if (mPlayQueue2 != null) {
                    try {
                        drainFrame(mPlayQueue2, mTakeCount2++);
                        return mPlayQueue2.take();
                    } catch (Exception e) {
                        Log.e(TAG, "getData1() exception : " + e.toString());
                    }
                }
                break;
            }
            default:
                break;
        }

        return null;
    }

    // 上层使用UDP时也会使用到这个函数
    public void putData(int which_client, byte[] frame, int length) {
        switch (which_client) {
            case 1: {
                if (mPlayQueue1 != null) {
                    try {
                        mPlayQueue1.put(frame);
                    } catch (Exception e) {
                        Log.e(TAG, "putDataToJava() exception : " + e.toString());
                    }
                }
                return;
            }
            case 2: {
                if (mPlayQueue2 != null) {
                    try {
                        mPlayQueue2.put(frame);
                    } catch (Exception e) {
                        Log.e(TAG, "putDataToJava() exception : " + e.toString());
                    }
                }
                return;
            }
            default:
                return;
        }
    }

    private ArrayList<byte[]> list = new ArrayList<>();

    public void drainFrame() {
        if (mPlayQueue1 != null && !mPlayQueue1.isEmpty()) {
            try {
                list.clear();
                list.addAll(mPlayQueue1);
                Log.i(TAG, "drainFrame() size1: " + mPlayQueue1.size());
                Log.i(TAG, "drainFrame() size1: " + list.size());
                int size = list.size();
                int index = -1;
                for (int i = size - 1; i >= 0; i--) {
                    byte[] frame = list.get(i);
                    if (frame[frame.length - 1] == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                        // 关键帧
                        index = i;
                        Log.i(TAG, "drainFrame()  size: " + size);
                        Log.i(TAG, "drainFrame() index: " + index);
                        break;
                    }
                }
                if (index == -1) {
                    // 队列里都是非关键帧,因为有非关键帧丢失了,所以与之在同一个GOP中的非关键帧都要抛弃
                    mPlayQueue1.clear();
                } else {
                    for (int i = index + 1; i < size; i++) {
                        // index之后的帧都是非关键帧,与被丢失的帧是同一个GOP,需要都主动抛弃
                        list.remove(i);
                    }
                    mPlayQueue1.clear();
                    mPlayQueue1.addAll(list);
                }
                Log.i(TAG, "drainFrame() size2: " + mPlayQueue1.size());
                Log.i(TAG, "drainFrame() size2: " + list.size());
            } catch (Exception e) {
                Log.e(TAG, "putDataToJava() exception : " + e.toString());
            }
        }
    }

    private void drainFrame(ArrayBlockingQueue<byte[]> queue, long takeCount) {
        int size1 = queue.size();
        if (/*size1 >= 5 && */takeCount >= 10) {
            boolean firstKeyFrame = false;
            int count = 0;
            for (byte[] frame : queue) {
                ++count;
                firstKeyFrame = ((frame[frame.length - 1] & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0);
                if (firstKeyFrame) {
                    break;
                }
            }
            if (firstKeyFrame && count > 1) {
                //Log.e(TAG, "drainFrame() size 1: " + size1);
                Iterator<byte[]> iterator = queue.iterator();
                while (iterator.hasNext()) {
                    byte[] frame = iterator.next();
                    if (((frame[frame.length - 1] & MediaCodec.BUFFER_FLAG_KEY_FRAME) == 0)) {
                        iterator.remove();
                    } else {
                        break;
                    }
                }
                int size2 = queue.size();
                //Log.i(TAG, "drainFrame() size 2: " + size2);
                if (/*size2 >= 5 && */size1 > size2) {
                    boolean secondKeyFrame = false;
                    for (byte[] frame : queue) {
                        firstKeyFrame =
                                ((frame[frame.length - 1] & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0);
                        if (firstKeyFrame && secondKeyFrame) {
                            break;
                        } else if (firstKeyFrame) {
                            secondKeyFrame = true;
                        }
                    }
                    if (firstKeyFrame && secondKeyFrame) {
                        secondKeyFrame = false;
                        iterator = queue.iterator();
                        while (iterator.hasNext()) {
                            byte[] frame = iterator.next();
                            if (((frame[frame.length - 1] & MediaCodec.BUFFER_FLAG_KEY_FRAME) == 0)) {
                                iterator.remove();
                            } else {
                                if (secondKeyFrame) {
                                    break;
                                }
                                secondKeyFrame = true;
                            }
                        }
                        //size1 = queue.size();
                        //Log.d(TAG, "drainFrame() size 3: " + size1);
                    }
                }
            }
        }
    }

    public static String findDecoderCodecName(String mime) {
        String codecName = null;
        // 查找解码器名称
        MediaCodecInfo[] mediaCodecInfos = MediaUtils.findAllDecodersByMime(mime);
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
                    || tempCodecName.endsWith(".secure")// 用于加密的视频
                    || (!tempCodecName.startsWith("omx.") && !tempCodecName.startsWith("c2."))) {
                codecName = null;
                continue;
            }
            break;
        }

        return codecName;
    }

    private Object onEvent(int what, Object[] objArray) {
        Object result = null;
        switch (what) {
            case DO_SOMETHING_CODE_Client_set_info: {
                if (objArray != null && objArray.length > 0) {
                    // DO_SOMETHING_CODE_connected
                    // DO_SOMETHING_CODE_Client_set_info
                    JniObject jniObject = JniObject.obtain();
                    jniObject.valueInt = 1;
                    jni2Java(DO_SOMETHING_CODE_connected, jniObject);

                    jniObject.valueString = (String) objArray[0];
                    jniObject.valueLong = jniObject.valueString.length();
                    jni2Java(DO_SOMETHING_CODE_Client_set_info, jniObject);
                }
                break;
            }
            default:
                break;
        }
        return result;
    }

    public MediaServer.OnClientListener mOnClientListener =
            new MediaServer.OnClientListener() {
                @Override
                public void onClient(int which_client, int type) {
                    switch (type) {
                        case CLIENT_TYPE_CONFIG: {
                            byte[] data = getData(which_client);
                            JniObject jniObject = JniObject.obtain();
                            jniObject.valueInt = which_client;
                            jniObject.valueString = new String(data);
                            jni2Java(DO_SOMETHING_CODE_Client_set_info, jniObject);
                            break;
                        }
                        case CLIENT_TYPE_CONNECT: {
                            JniObject jniObject = JniObject.obtain();
                            jniObject.valueInt = which_client;
                            jni2Java(DO_SOMETHING_CODE_connected, jniObject);
                            break;
                        }
                        case CLIENT_TYPE_DISCONNECT: {
                            JniObject jniObject = JniObject.obtain();
                            jniObject.valueInt = which_client;
                            jni2Java(DO_SOMETHING_CODE_disconnected, jniObject);
                            break;
                        }
                        default:
                            break;
                    }
                }
            };

}
