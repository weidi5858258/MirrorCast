package com.weidi.mirrorcast;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.nio.ByteBuffer;

public class EDMediaCodec {

    private static final String TAG =
            "player_alexander";

    /***
     jlong timeoutUs
     -1表示一直等, 0表示不等.
     根据CPU性能的不同,好的CPU还可以把时间再缩短一点
     */
    public static int TIME_OUT = 10000;// 10000(10ms)

    public enum TYPE {
        TYPE_VIDEO,
        TYPE_AUDIO,
        //TYPE_SUBTITLE
    }

    public interface Callback {
        boolean isVideoFinished();

        boolean isAudioFinished();

        void handleVideoOutputFormat(MediaFormat mediaFormat);

        void handleAudioOutputFormat(MediaFormat mediaFormat);

        int handleVideoOutputBuffer(ByteBuffer room, MediaCodec.BufferInfo roomInfo,
                                    boolean isPortrait);

        int handleAudioOutputBuffer(ByteBuffer room, MediaCodec.BufferInfo roomInfo);
    }

    /***
     *
     * @param callback               Callback
     * @param type                   TYPE.TYPE_VIDEO or TYPE.TYPE_AUDIO
     * @param mediaCodec             编解码器
     * @param data                   需要编解码的数据
     * @param offset                 一般为0
     * @param size                   编解码数据的大小
     * @param presentationTimeUs     时间戳
     * @param render                 TYPE.TYPE_AUDIO为false,TYPE.TYPE_VIDEO为true(录制屏幕时为false).
     * @param needFeedInputBuffer    一般为true.为false时,data,offset,size和presentationTimeUs随便写
     * @return
     */
    public static boolean feedInputBufferAndDrainOutputBuffer(
            Callback callback,
            TYPE type,
            MediaCodec mediaCodec,
            byte[] data,
            int offset,
            int size,
            long presentationTimeUs,
            int flags,
            boolean render,
            boolean needFeedInputBuffer,
            boolean isPortrait) {
        if (needFeedInputBuffer) {
            return feedInputBuffer(
                    type,
                    mediaCodec,
                    data,
                    offset,
                    size,
                    presentationTimeUs,
                    flags)
                    &&
                    drainOutputBuffer(callback, type, mediaCodec, render, isPortrait);
        }

        // 录制屏幕时,video是没有Input过程的
        return drainOutputBuffer(callback, type, mediaCodec, render, isPortrait);
    }

    /***
     sony这边的offset只能为0或者1的时候,数据才有效,大于等于2的时候就不行了
     */
    private static boolean feedInputBuffer(
            TYPE type,
            MediaCodec codec,
            byte[] data,
            int offset,
            int size,
            long presentationTimeUs,
            int flags) {
        try {
            int roomIndex = codec.dequeueInputBuffer(TIME_OUT);
            if (roomIndex < 0) {
                return true;
            }

            ByteBuffer room = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                room = codec.getInputBuffer(roomIndex);
            } else {
                room = codec.getInputBuffers()[roomIndex];
            }
            if (room == null) {
                return false;
            }

            room.clear();
            room.put(data, offset, size);

            codec.queueInputBuffer(
                    roomIndex,
                    offset,
                    size,
                    presentationTimeUs,
                    flags);

            // reset
            roomIndex = -1;
            room = null;
            return true;
        } catch (MediaCodec.CryptoException
                | IllegalStateException
                | IllegalArgumentException
                | NullPointerException e) {
            e.printStackTrace();
            if (type == TYPE.TYPE_VIDEO) {
                Log.e(TAG, "feedInputBuffer() Video Input occur exception: " + e);
            } else {
                Log.e(TAG, "feedInputBuffer() Audio Input occur exception: " + e);
            }
            return false;
        }
    }

    /***
     *
     */
    private static boolean drainOutputBuffer(
            Callback callback,
            TYPE type,
            MediaCodec codec,
            boolean render,
            boolean isPortrait) {
        MediaCodec.BufferInfo roomInfo = new MediaCodec.BufferInfo();
        ByteBuffer room = null;
        for (; ; ) {
            if (type == TYPE.TYPE_VIDEO) {
                if (callback.isVideoFinished()) {
                    break;
                }
            } else {
                if (callback.isAudioFinished()) {
                    break;
                }
            }

            try {
                int roomIndex = codec.dequeueOutputBuffer(roomInfo, TIME_OUT);
                if (roomIndex < 0) {
                    switch (roomIndex) {
                        case MediaCodec.INFO_TRY_AGAIN_LATER:// -1
                            break;
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:// -2
                            if (type == TYPE.TYPE_VIDEO) {
                                Log.w(TAG, "drainOutputBuffer() " +
                                        "Video Output MediaCodec.INFO_OUTPUT_FORMAT_CHANGED");
                                callback.handleVideoOutputFormat(codec.getOutputFormat());
                            } else {
                                Log.d(TAG, "drainOutputBuffer() " +
                                        "Audio Output MediaCodec.INFO_OUTPUT_FORMAT_CHANGED");
                                callback.handleAudioOutputFormat(codec.getOutputFormat());
                            }
                            break;
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:// -3
                            if (type == TYPE.TYPE_VIDEO) {
                                Log.w(TAG, "drainOutputBuffer() " +
                                        "Video Output MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
                            } else {
                                Log.d(TAG, "drainOutputBuffer() " +
                                        "Audio Output MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
                            }
                            break;
                        default:
                            break;
                    }
                    break;
                }

                /*if ((roomInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                    // roomInfo.flags = 1 关键帧
                    if (type == TYPE.TYPE_VIDEO) {
                        Log.w(TAG, "drainOutputBuffer() " +
                                "Video Output MediaCodec.BUFFER_FLAG_KEY_FRAME");
                    } else {
                        Log.d(TAG, "drainOutputBuffer() " +
                                "Audio Output MediaCodec.BUFFER_FLAG_KEY_FRAME");
                    }
                }*/
                if ((roomInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // roomInfo.flags = 2 配置帧(sps pps)
                    if (type == TYPE.TYPE_VIDEO) {
                        Log.w(TAG, "drainOutputBuffer() " +
                                "Video Output MediaCodec.BUFFER_FLAG_CODEC_CONFIG");
                    } else {
                        Log.d(TAG, "drainOutputBuffer() " +
                                "Audio Output MediaCodec.BUFFER_FLAG_CODEC_CONFIG");
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    room = codec.getOutputBuffer(roomIndex);
                } else {
                    room = codec.getOutputBuffers()[roomIndex];
                }
                if (room != null && roomInfo != null) {
                    if (type == TYPE.TYPE_VIDEO) {
                        callback.handleVideoOutputBuffer(room, roomInfo, isPortrait);
                    } else {
                        callback.handleAudioOutputBuffer(room, roomInfo);
                    }
                    room.clear();
                }

                codec.releaseOutputBuffer(roomIndex, render);
            } catch (MediaCodec.CryptoException
                    | IllegalStateException
                    | IllegalArgumentException
                    | NullPointerException e) {
                e.printStackTrace();
                if (type == TYPE.TYPE_VIDEO) {
                    Log.e(TAG, "drainOutputBuffer() Video Output occur exception: " + e);
                } else {
                    Log.e(TAG, "drainOutputBuffer() Audio Output occur exception: " + e);
                }
                return false;
            }
        }// for(;;) end

        return true;
    }

}
