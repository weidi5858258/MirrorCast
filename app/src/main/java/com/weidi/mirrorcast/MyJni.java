package com.weidi.mirrorcast;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.MediaCodec;
import android.util.Log;

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

    // jni ---> java
    public static final int DO_SOMETHING_CODE_connected = 2000;
    public static final int DO_SOMETHING_CODE_disconnected = 2001;
    public static final int DO_SOMETHING_CODE_change_window = 2002;

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
                int orientation = Integer.parseInt(orientationStr);
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

    private void putData(int which_client, byte[] frame, int length) {
        switch (which_client) {
            case 1: {
                if (mPlayQueue1 != null) {
                    try {
                        mPlayQueue1.put(frame);
                    } catch (Exception e) {
                        Log.e(TAG, "putData() exception : " + e.toString());
                    }
                }
                return;
            }
            case 2: {
                if (mPlayQueue2 != null) {
                    try {
                        mPlayQueue2.put(frame);
                    } catch (Exception e) {
                        Log.e(TAG, "putData() exception : " + e.toString());
                    }
                }
                return;
            }
            default:
                return;
        }
    }

    private void drainFrame(ArrayBlockingQueue<byte[]> queue, long takeCount) {
        int size1 = queue.size();
        if (size1 >= 5 && takeCount >= 10) {
            boolean firstKeyFrame = false;
            for (byte[] frame : queue) {
                firstKeyFrame = ((frame[frame.length - 1] & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0);
                if (firstKeyFrame) {
                    break;
                }
            }
            if (firstKeyFrame) {
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
                if (size2 >= 5 && size1 > size2) {
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