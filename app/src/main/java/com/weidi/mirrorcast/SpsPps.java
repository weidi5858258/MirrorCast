package com.weidi.mirrorcast;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.nio.ByteBuffer;

public class SpsPps {

    private static final String TAG =
            "player_alexander";

    public static int TIME_OUT = 10000;

    public interface Callback {
        boolean isFinished();

        int handleOutputBuffer(ByteBuffer room, MediaCodec.BufferInfo roomInfo);
    }

    /***
     *
     */
    public static boolean drainOutputBuffer(
            Callback callback,
            MediaCodec codec,
            boolean render) {
        MediaCodec.BufferInfo roomInfo = new MediaCodec.BufferInfo();
        ByteBuffer room = null;
        for (; ; ) {
            if (callback.isFinished()) {
                return true;
            }
            try {
                int roomIndex = codec.dequeueOutputBuffer(roomInfo, TIME_OUT);
                switch (roomIndex) {
                    case MediaCodec.INFO_TRY_AGAIN_LATER:// -1
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:// -2
                        break;
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:// -3
                        break;
                    default:
                        break;
                }
                if (roomIndex < 0) {
                    break;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    room = codec.getOutputBuffer(roomIndex);
                } else {
                    room = codec.getOutputBuffers()[roomIndex];
                }
                if (room != null && roomInfo != null) {
                    if ((roomInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // roomInfo.flags = 2 配置帧(sps pps)
                        callback.handleOutputBuffer(room, roomInfo);
                    }
                    room.clear();
                }

                codec.releaseOutputBuffer(roomIndex, render);
            } catch (MediaCodec.CryptoException
                    | IllegalStateException
                    | IllegalArgumentException
                    | NullPointerException e) {
                e.printStackTrace();
                return false;
            }
        }

        return true;
    }

}
