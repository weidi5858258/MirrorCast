package com.weidi.mirrorcast;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MediaClient {

    private static final String TAG =
            "player_alexander";

    private volatile static MediaClient sMediaClient;

    private Socket socket;
    private String ip = "192.168.49.1";// "192.168.49.1"
    // 不使用
    private InputStream inputStream;
    // 向服务器发送数据
    private OutputStream outputStream;
    public static boolean mIsConnected = false;

    private MediaCodec mVideoMC;
    private MediaCodec mAudioMC;
    private MediaFormat mVideoMF;
    private MediaFormat mAudioMF;
    private AudioTrack mAudioTrack;

    private MediaClient() {
    }

    public static MediaClient getInstance() {
        if (sMediaClient == null) {
            synchronized (MediaClient.class) {
                if (sMediaClient == null) {
                    sMediaClient = new MediaClient();
                }
            }
        }
        return sMediaClient;
    }

    public void setIp(String ipAddr) {
        ip = ipAddr;
        // ip = "localhost";
        // ip = "192.168.0.100";
    }

    public boolean connect() {
        if (TextUtils.isEmpty(ip)
                || socket != null
                || inputStream != null
                || outputStream != null) {
            Log.e(TAG, "MediaClient ip is empty");
            return false;
        }

        int i = 0;
        while (true) {
            try {
                mIsConnected = false;
                ip = "192.168.49." + (++i);
                socket = new Socket(ip, MediaServer.PORT);
                mIsConnected = true;
                Log.i(TAG, "MediaClient connect() success ip: " + ip);
                break;
            } catch (Exception e) {
                Log.e(TAG, "MediaClient connect() failure ip: " + ip);
                continue;
            }
        }

        if (mIsConnected) {
            try {
                inputStream = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "MediaClient getInputStream() failure");
                close();
                return false;
            }
        }

        if (mIsConnected) {
            try {
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "MediaClient getOutputStream() failure");
                close();
                return false;
            }
        }

        if (mIsConnected) {
            return true;
        }

        return false;
    }

    public void sendData(byte[] data, int offsetInBytes, int sizeInBytes) {
        if (outputStream != null) {
            try {
                outputStream.write(data, offsetInBytes, sizeInBytes);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "MediaClient sendData() failure");
                //close();
            }
        }
    }

    public void playVideo() {
        Log.i(TAG, "MediaClient playVideo() start");
        if (!mIsConnected) {
            Log.e(TAG, "MediaClient playVideo() isn't connected");
            return;
        }

        mVideoMF = MediaUtils.getVideoDecoderMediaFormat(720, 1280);
        mVideoMC = MediaUtils.getVideoDecoderMediaCodec(mVideoMF);
        if (mVideoMC == null) {
            Log.e(TAG, "MediaClient playVideo() mVideoMC is null");
            return;
        }

        final int VIDEO_FRAME_MAX_LENGTH = mVideoMF.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        final byte[] data = new byte[VIDEO_FRAME_MAX_LENGTH];
        int readCount = 0;
        while (mIsConnected) {
            Arrays.fill(data, (byte) 0);
            try {
                readCount = inputStream.read(data, 0, VIDEO_FRAME_MAX_LENGTH);
                Log.i(TAG, "MediaClient playVideo() readCount: " + readCount);
                if (readCount <= 0) {
                    Log.e(TAG, "MediaClient playVideo() readCount: " + readCount);
                    break;
                }

                long presentationTimeUs = System.nanoTime() / 1000;
                /*EDMediaCodec.feedInputBufferAndDrainOutputBuffer(
                        mCallback,
                        EDMediaCodec.TYPE.TYPE_VIDEO,
                        mVideoMC,
                        null,
                        data,
                        0,
                        readCount,
                        presentationTimeUs,
                        0,
                        false,
                        true);*/
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "MediaClient playVideo() " + e.toString());
                break;
            }
        }// while(...) end
        Log.i(TAG, "MediaClient playVideo() end");
    }

    public void playAudio() {
        Log.i(TAG, "MediaClient playAudio() start");
        if (!mIsConnected) {
            Log.e(TAG, "MediaClient playAudio() isn't connected");
            return;
        }

        mAudioMF = MediaUtils.getAudioDecoderMediaFormat();
        //mAudioMF.setInteger(MediaFormat.KEY_IS_ADTS, 1);
        // Android MediaCodec解码AAC，AudioTrack播放PCM音频
        // https://blog.csdn.net/lavender1626/article/details/80431902
        byte[] csd0 = new byte[]{(byte) 0x12, (byte) 0x10};
        mAudioMF.setByteBuffer("csd-0", ByteBuffer.wrap(csd0));
        /*mAudioMC = MediaUtils.getAudioDecoderMediaCodec(mAudioMF);
        if (mAudioMC == null) {
            Log.e(TAG, "MediaClient playAudio() mAudioMC is null");
            return;
        }*/

        // 创建AudioTrack
        // 1.
        int sampleRateInHz =
                mAudioMF.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        // 2.
        int channelCount =
                mAudioMF.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        // 3.
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        Log.d(TAG, "MediaClient playAudio()" +
                " sampleRateInHz: " + sampleRateInHz +
                " channelCount: " + channelCount +
                " audioFormat: " + audioFormat);
        mAudioTrack = MediaUtils.createAudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRateInHz, channelCount, audioFormat,
                AudioTrack.MODE_STREAM);
        if (mAudioTrack != null) {
            mAudioTrack.play();
        } else {
            Log.e(TAG, "MediaClient playAudio() mAudioTrack is null");
            return;
        }

        final int AUDIO_FRAME_MAX_LENGTH = mAudioMF.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        final byte[] data = new byte[AUDIO_FRAME_MAX_LENGTH];
        int readCount = 0;
        while (mIsConnected) {
            Arrays.fill(data, (byte) 0);
            try {
                readCount = inputStream.read(data, 0, AUDIO_FRAME_MAX_LENGTH);
                if (readCount <= 0) {
                    Log.e(TAG, "MediaClient playAudio() readCount: " + readCount);
                    break;
                }

                mAudioTrack.write(data, 0, readCount);

                /*long presentationTimeUs = System.nanoTime() / 1000;
                EDMediaCodec.feedInputBufferAndDrainOutputBuffer(
                        mCallback,
                        EDMediaCodec.TYPE.TYPE_AUDIO,
                        mAudioMC,
                        data,
                        0,
                        readCount,
                        presentationTimeUs,
                        false,
                        true);*/
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "MediaClient playAudio() " + e.toString());
                break;
            }
        }// while(...) end
        Log.i(TAG, "MediaClient playAudio() end");
    }

    public synchronized void close() {
        Log.i(TAG, "MediaClient close() start");
        mIsConnected = false;
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream = null;
        }
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            outputStream = null;
        }
        if (socket != null) {
            try {
                if (!socket.isInputShutdown() && !socket.isClosed()) {
                    socket.shutdownInput();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (!socket.isOutputShutdown() && !socket.isClosed()) {
                    socket.shutdownOutput();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            socket = null;
        }
        MediaUtils.releaseMediaCodec(mVideoMC);
        MediaUtils.releaseMediaCodec(mAudioMC);
        MediaUtils.releaseAudioTrack(mAudioTrack);
        mVideoMC = null;
        mAudioMC = null;
        mAudioTrack = null;

        mSps = null;
        mPps = null;
        mUploadPpsSps = true;
        mIsHeaderWrite = false;
        mIsKeyFrameWrite = false;
        Log.i(TAG, "MediaClient close() end");
    }

    ///////////////////////////////////////////////////////////////

    // Coded slice of a non-IDR picture slice_layer_without_partitioning_rbsp( )
    public final static int NonIDR = 1;
    // Coded slice of an IDR picture slice_layer_without_partitioning_rbsp( )
    public final static int IDR = 5;
    // Supplemental enhancement information (SEI) sei_rbsp( )
    public final static int SEI = 6;
    // Sequence parameter set seq_parameter_set_rbsp( )
    public final static int SPS = 7;
    // Picture parameter set pic_parameter_set_rbsp( )
    public final static int PPS = 8;
    // Access unit delimiter access_unit_delimiter_rbsp( )
    public final static int AccessUnitDelimiter = 9;

    //public static final int HEADER = 0;
    //public static final int METADATA = 1;
    public static final int FIRST_VIDEO = 2;
    public static final int KEY_FRAME = 5;
    public static final int INTER_FRAME = 6;

    private static final byte[] HEADER = {0x00, 0x00, 0x00, 0x01};

    private byte[] mSps = null;
    private byte[] mPps = null;
    private boolean mUploadPpsSps = true;
    private boolean mIsHeaderWrite = false;
    private boolean mIsKeyFrameWrite = false;

    /**
     * the search result for annexb.
     */
    private static class AnnexbSearch {
        public int startCode = 0;
        public boolean match = false;
    }

    public void analyseVideoDataonlyH264(ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
        boolean isKeyFrame = false;
        byte[] buffer = null;
        int writeIndex = 0;
        while (room.position() < roomInfo.offset + roomInfo.size) {
            // frame: 去掉[0 0 0 1]后剩下的数据
            byte[] frame = annexbDemux(room, roomInfo);
            if (frame == null) {
                Log.e(TAG, "annexb not match.");
                break;
            }
            // ignore the nalu type aud(9)
            if (isAccessUnitDelimiter(frame)) {
                continue;
            }
            // for sps
            if (mUploadPpsSps && isSps(frame)) {
                mSps = frame;
                continue;
            }
            // for pps
            if (mUploadPpsSps && isPps(frame)) {
                mPps = frame;
                continue;
            }
            if (!mIsKeyFrameWrite) {
                // 第一次关键帧发送后,就不需要再做了
                // for IDR frame
                if (isKeyFrame(frame)) {
                    isKeyFrame = true;
                } else {
                    isKeyFrame = false;
                }
            }
            if (buffer == null) {
                buffer = new byte[roomInfo.size + 4];
                System.arraycopy(int2Bytes(roomInfo.size), 0, buffer, writeIndex, 4);
                writeIndex += 4;
            }
            System.arraycopy(HEADER, 0, buffer, writeIndex, 4);
            writeIndex += 4;
            System.arraycopy(frame, 0, buffer, writeIndex, frame.length);
            writeIndex += frame.length;
        }
        if (mUploadPpsSps && mPps != null && mSps != null) {
            onSpsPps(mSps, mPps);
            mUploadPpsSps = false;
        }
        if (buffer == null) {
            return;
        }
        onVideo(buffer, isKeyFrame);
    }

    public void analyseVideoDataonlyH264_2(ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
        if (mUploadPpsSps) {
            handleSpsPps(room, roomInfo);
            mUploadPpsSps = false;
            return;
        }

        byte[] frame = null;
        switch (mark) {
            case MARK0:
            case MARK3:
            case MARK4: {
                Log.e(TAG, "I don't know how to do ^ $ ^");
                return;
            }
            case MARK1: {
                // 0 0 0 1 ...
                frame = new byte[roomInfo.size + 4];
                room.get(frame, 4, roomInfo.size);
                System.arraycopy(int2Bytes(roomInfo.size), 0, frame, 0, 4);
                break;
            }
            case MARK2: {
                //   0 0 1 ...
                frame = new byte[roomInfo.size + 5];
                room.get(frame, 5, roomInfo.size);
                System.arraycopy(int2Bytes(roomInfo.size + 1), 0, frame, 0, 4);
                break;
            }
            default:
                break;
        }

        if (!mIsKeyFrameWrite) {
            // 第一次关键帧发送后,就不需要再做了
            // for IDR frame
            if (frame != null) {
                byte bt = frame[8];
                boolean isKeyFrame = ((bt & 0x1f) == IDR);
                onVideo(frame, isKeyFrame);
                return;
            }
        }

        if (frame != null) {
            sendData(frame);
        }
    }

    private static final int MARK0 = 0;
    // 0 0 0 1
    private static final int MARK1 = 1;
    //   0 0 1
    private static final int MARK2 = 2;
    // ... 103 ... 104 ...
    private static final int MARK3 = 3;
    // ...  39 ... 40 ...
    private static final int MARK4 = 4;
    private int mark = MARK0;

    private void handleSpsPps(ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
        mSps = null;
        mPps = null;
        byte[] sps_pps = new byte[roomInfo.size];
        room.get(sps_pps, 0, roomInfo.size);
        room.position(roomInfo.offset);
        room.limit(roomInfo.offset + roomInfo.size);
        try {
            mark = MARK0;
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
                room.get(sps, 0, sps.length);
                room.get(pps, 0, pps.length);

                switch (mark) {
                    case MARK1: {
                        mSps = new byte[sps.length + 4];
                        System.arraycopy(int2Bytes(sps.length), 0, mSps, 0, 4);
                        System.arraycopy(sps, 0, mSps, 4, sps.length);

                        mPps = new byte[pps.length + 4];
                        System.arraycopy(int2Bytes(pps.length), 0, mPps, 0, 4);
                        System.arraycopy(pps, 0, mPps, 4, pps.length);
                        break;
                    }
                    case MARK2: {
                        mSps = new byte[sps.length + 5];
                        System.arraycopy(int2Bytes(sps.length + 1), 0, mSps, 0, 4);
                        System.arraycopy(sps, 0, mSps, 5, sps.length);

                        mPps = new byte[pps.length + 5];
                        System.arraycopy(int2Bytes(pps.length + 1), 0, mPps, 0, 4);
                        System.arraycopy(pps, 0, mPps, 5, pps.length);
                        break;
                    }
                    default:
                        break;
                }
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
                    byte[] tempSpsPps = new byte[sps_pps.length];
                    room.get(tempSpsPps, 0, sps_pps.length);
                    sps = new byte[spsLength + 4];
                    pps = new byte[ppsLength + 4];
                    // 0x00, 0x00, 0x00, 0x01
                    sps[0] = pps[0] = 0;
                    sps[1] = pps[1] = 0;
                    sps[2] = pps[2] = 0;
                    sps[3] = pps[3] = 1;
                    System.arraycopy(tempSpsPps, spsIndex, sps, 4, spsLength);
                    System.arraycopy(tempSpsPps, ppsIndex, pps, 4, ppsLength);

                    mSps = new byte[sps.length + 4];
                    System.arraycopy(int2Bytes(sps.length), 0, mSps, 0, 4);
                    System.arraycopy(sps, 0, mSps, 4, sps.length);

                    mPps = new byte[pps.length + 4];
                    System.arraycopy(int2Bytes(pps.length), 0, mPps, 0, 4);
                    System.arraycopy(pps, 0, mPps, 4, pps.length);
                } else {
                    // 实在找不到sps和pps的数据了
                    mark = MARK0;
                }
                // endregion
            }

            if (mSps != null && mPps != null) {
                Log.i(TAG, "handleSpsPps() video \n  csd-0: " +
                        Arrays.toString(mSps));
                Log.i(TAG, "handleSpsPps() video \n  csd-1: " +
                        Arrays.toString(mPps));
                sendData(mSps);
                sendData(mPps);
                mIsHeaderWrite = true;
            } else {
                Log.e(TAG, "handleSpsPps() sps/pps is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "handleSpsPps() Exception: \n" + e);
        }
    }

    /**
     * 从硬编出来的数据取出一帧nal
     *
     * @param room
     * @param roomInfo
     * @return
     */
    private byte[] annexbDemux(ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
        AnnexbSearch annexbSearch = new AnnexbSearch();
        avcStartWithAnnexb(annexbSearch, room, roomInfo);

        if (!annexbSearch.match || annexbSearch.startCode < 3) {
            return null;
        }

        for (int i = 0; i < annexbSearch.startCode; i++) {
            room.get();
        }

        ByteBuffer frameBuffer = room.slice();
        int pos = room.position();
        while (room.position() < roomInfo.offset + roomInfo.size) {
            avcStartWithAnnexb(annexbSearch, room, roomInfo);
            if (annexbSearch.match) {
                break;
            }
            room.get();
        }

        int size = room.position() - pos;
        byte[] frameBytes = new byte[size];
        frameBuffer.get(frameBytes);
        return frameBytes;
    }


    /**
     * 从硬编出来的byteBuffer中查找nal
     *
     * @param as
     * @param room
     * @param roomInfo
     */
    private void avcStartWithAnnexb(
            AnnexbSearch as, ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
        as.match = false;
        as.startCode = 0;
        int pos = room.position();
        while (pos < roomInfo.offset + roomInfo.size - 3) {
            // not match.
            if (room.get(pos) != 0x00 || room.get(pos + 1) != 0x00) {
                break;
            }

            // match N[00] 00 00 01, where N>=0
            if (room.get(pos + 2) == 0x01) {
                as.match = true;
                as.startCode = pos + 3 - room.position();
                break;
            }
            pos++;
        }
    }

    private boolean isSps(byte[] frame) {
        if (frame.length < 1) {
            return false;
        }
        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        // [7 : SPS] [8 : PPS] [5 : I Frame] [1 : P Frame]
        int nal_unit_type = (frame[0] & 0x1f);
        return nal_unit_type == SPS;
    }

    private boolean isPps(byte[] frame) {
        if (frame.length < 1) {
            return false;
        }
        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        // [7 : SPS] [8 : PPS] [5 : I Frame] [1 : P Frame]
        int nal_unit_type = (frame[0] & 0x1f);
        return nal_unit_type == PPS;
    }

    private boolean isKeyFrame(byte[] frame) {
        if (frame.length < 1) {
            return false;
        }
        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        // [7 : SPS] [8 : PPS] [5 : I Frame] [1 : P Frame]
        int nal_unit_type = (frame[0] & 0x1f);
        return nal_unit_type == IDR;
    }

    private static boolean isAccessUnitDelimiter(byte[] frame) {
        if (frame.length < 1) {
            return false;
        }
        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        // [7 : SPS] [8 : PPS] [5 : I Frame] [1 : P Frame]
        int nal_unit_type = (frame[0] & 0x1f);
        return nal_unit_type == AccessUnitDelimiter;
    }

    private void onSpsPps(byte[] sps, byte[] pps) {
        mSps = new byte[sps.length + 8];
        System.arraycopy(int2Bytes(sps.length + 4), 0, mSps, 0, 4);
        System.arraycopy(HEADER, 0, mSps, 4, 4);
        System.arraycopy(sps, 0, mSps, 8, sps.length);
        sendData(mSps);

        mPps = new byte[pps.length + 8];
        System.arraycopy(int2Bytes(pps.length + 4), 0, mPps, 0, 4);
        System.arraycopy(HEADER, 0, mPps, 4, 4);
        System.arraycopy(pps, 0, mPps, 8, pps.length);
        sendData(mPps);

        /*ByteBuffer byteBuffer0 = ByteBuffer.allocate(sps.length + 4);
        byteBuffer0.put(HEADER);
        byteBuffer0.put(sps);
        mSps = new byte[sps.length + 4];
        System.arraycopy(byteBuffer0.array(), 0, mSps, 0, sps.length + 4);
        onPacket(mSps, FIRST_VIDEO);

        ByteBuffer byteBuffer1 = ByteBuffer.allocate(pps.length + 4);
        byteBuffer1.put(HEADER);
        byteBuffer1.put(pps);
        mPps = new byte[pps.length + 4];
        System.arraycopy(byteBuffer1.array(), 0, mPps, 0, pps.length + 4);
        onPacket(mPps, FIRST_VIDEO);*/

        mIsHeaderWrite = true;
    }

    private void onVideo(byte[] video, boolean isKeyFrame) {
        if (!mIsHeaderWrite) {
            return;
        }
        //int packetType = INTER_FRAME;
        if (isKeyFrame) {
            mIsKeyFrameWrite = true;
            //packetType = KEY_FRAME;
        }
        //确保第一帧是关键帧，避免一开始出现灰色模糊界面
        if (!mIsKeyFrameWrite) {
            return;
        }
        /*ByteBuffer room;
        if (isKeyFrame) {
            room = ByteBuffer.allocate(video.length);
            room.put(video);
        } else {
            room = ByteBuffer.allocate(video.length);
            room.put(video);
        }
        onPacket(room.array(), packetType);*/
        sendData(video);
        //onPacket(video, packetType);
    }

    private void onPacket(byte[] data, int type) {
        sendData(data);

        /*Frame<Chunk> frame = null;
        Video video = new Video();
        video.setData(data);
        if (type == FIRST_VIDEO) {
            frame = new Frame(video, type, Frame.FRAME_TYPE_CONFIGURATION);
        } else if (type == KEY_FRAME) {
            frame = new Frame(video, type, Frame.FRAME_TYPE_KEY_FRAME);
        } else if (type == INTER_FRAME) {
            frame = new Frame(video, type, Frame.FRAME_TYPE_INTER_FRAME);
        }
        if (frame == null) {
            return;
        }
        if (frame.data instanceof Video) {
            sendData(((Video) frame.data).getData());
        }*/
    }

    /***
     第一次发送sps
     第二次发送pps
     第三次发送关键帧
     第四次发送...
     */
    private void sendData(byte[] buff) {
        try {
            //Encode encode = new Encode(buff);
            //outputStream.write(encode.buildSendContent());
            outputStream.write(buff);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "MediaClient sendData() fialure");
        }
    }

    /***
     * 将int转为长度为4的byte数组
     *
     * @param length
     * @return
     */
    private static byte[] int2Bytes(int length) {
        byte[] result = new byte[4];
        result[0] = (byte) length;
        result[1] = (byte) (length >> 8);
        result[2] = (byte) (length >> 16);
        result[3] = (byte) (length >> 24);
        return result;
    }

}
