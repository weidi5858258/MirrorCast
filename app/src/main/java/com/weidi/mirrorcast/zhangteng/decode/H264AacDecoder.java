package com.weidi.mirrorcast.zhangteng.decode;

import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;


/**
 * @Desc 解析H264和AAC的Decoder
 */

public class H264AacDecoder {

    private static final String TAG = "H264AacDecoder";

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

    //
    public final static int AUDIO = -2;

    private byte[] mSps = null;
    private byte[] mPps = null;
    private byte[] mKf = null;

    public void reset() {
        mSps = null;
        mPps = null;
        mKf = null;
        mPlayQueue.clear();
    }

    public void decodeH264(byte[] frame) {
        //todo h264帧解码
        boolean isKeyFrame = false;
        if (frame == null) {
            Log.e(TAG, "annexb not match.");
            return;
        }
        // ignore the nalu type aud(9)
        if (isAccessUnitDelimiter(frame)) {
            return;
        }
        //for pps and sps and keyframe
        if (isPpsAndSpsAndKeyFrame(frame)) {
            if (mPps != null && mSps != null && mKf != null) {
                onSpsPps(mSps, mPps);
                onVideo(mKf, Frame.KEY_FRAME);
            }
            return;
        }
        //for pps and sps
        if (isPpsAndSps(frame)) {
            if (mPps != null && mSps != null) {
                onSpsPps(mSps, mPps);
            }
            return;
        }
        // for pps
        if (isPps(frame)) {
            mPps = frame;
            if (mPps != null && mSps != null) {
                onSpsPps(mSps, mPps);
            }
            return;
        }
        // for sps
        if (isSps(frame)) {
            mSps = frame;
            if (mPps != null && mSps != null) {
                onSpsPps(mSps, mPps);
            }
            return;
        }
        if (isAudio(frame)) {
            byte[] temp = new byte[frame.length - 4];
            System.arraycopy(frame, 4, temp, 0, frame.length - 4);
            onVideo(temp, Frame.AUDIO_FRAME);
            return;
        }
        // for IDR frame
        if (isKeyFrame(frame)) {
            isKeyFrame = true;
        } else {
            isKeyFrame = false;
        }
        onVideo(frame, isKeyFrame ? Frame.KEY_FRAME : Frame.NORMAL_FRAME);
    }

    private boolean isAudio(byte[] frame) {
        if (frame.length < 5) {
            return false;
        }
        return frame[4] == ((byte) 0xFF) && frame[5] == ((byte) 0xF9);
    }

    private boolean isSps(byte[] frame) {
        if (frame.length < 5) {
            return false;
        }
        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
        int nal_unit_type = (frame[4] & 0x1f);
        return nal_unit_type == SPS;
    }

    private boolean isPps(byte[] frame) {
        if (frame.length < 5) {
            return false;
        }
        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
        int nal_unit_type = (frame[4] & 0x1f);
        return nal_unit_type == PPS;
    }

    private boolean isKeyFrame(byte[] frame) {
        if (frame.length < 5) {
            return false;
        }
        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
        int nal_unit_type = (frame[4] & 0x1f);
        return nal_unit_type == IDR;
    }

    private boolean isPpsAndSpsAndKeyFrame(byte[] frame) {
        if (frame.length <= 4 + 7 + 19) {
            return false;
        }
        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
        int pps_type = (frame[4] & 0x1f);
        int sps_type = (frame[4 + 7] & 0x1f);
        int key_type = (frame[4 + 7 + 19] & 0x1f);
        if ((pps_type == PPS && sps_type == SPS && (key_type == IDR || key_type == NonIDR || key_type == SEI))) {
            mPps = new byte[7];
            mSps = new byte[19];
            mKf = new byte[frame.length - 7 - 19];
            System.arraycopy(frame, 0, mPps, 0, mPps.length);
            System.arraycopy(frame, mPps.length, mSps, 0, mSps.length);
            System.arraycopy(frame, 7 + 19, mKf, 0, mKf.length);
            return true;
        }
        pps_type = (frame[4 + 19] & 0x1f);
        sps_type = (frame[4] & 0x1f);
        key_type = (frame[4 + 7 + 19] & 0x1f);
        if ((pps_type == PPS && sps_type == SPS && (key_type == IDR || key_type == NonIDR || key_type == SEI))) {
            mPps = new byte[7];
            mSps = new byte[19];
            mKf = new byte[frame.length - 7 - 19];
            System.arraycopy(frame, 0, mSps, 0, mSps.length);
            System.arraycopy(frame, mSps.length, mPps, 0, mPps.length);
            System.arraycopy(frame, 7 + 19, mKf, 0, mKf.length);
            return true;
        }
        return false;
    }

    private boolean isPpsAndSps(byte[] frame) {
        if (frame.length <= 7 + 19) {
            return false;
        }
        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
        int pps_type = (frame[4] & 0x1f);
        int sps_type = (frame[4 + 7] & 0x1f);
        if ((pps_type == PPS && sps_type == SPS)) {
            mPps = new byte[7];
            mSps = new byte[19];
            System.arraycopy(frame, 0, mPps, 0, mPps.length);
            System.arraycopy(frame, mPps.length, mSps, 0, mSps.length);
            return true;
        }
        pps_type = (frame[4 + 19] & 0x1f);
        sps_type = (frame[4] & 0x1f);
        if ((pps_type == PPS && sps_type == SPS)) {
            mPps = new byte[7];
            mSps = new byte[19];
            System.arraycopy(frame, 0, mSps, 0, mSps.length);
            System.arraycopy(frame, mSps.length, mPps, 0, mPps.length);
            return true;
        }
        return false;
    }

    private static boolean isAccessUnitDelimiter(byte[] frame) {
        if (frame.length < 5) {
            return false;
        }
        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
        int nal_unit_type = (frame[4] & 0x1f);
        return nal_unit_type == AccessUnitDelimiter;
    }

    private void onSpsPps(byte[] sps, byte[] pps) {
        decoderH264(sps);
        decoderH264(pps);

        /*Frame spsPpsFrame = new Frame();
        spsPpsFrame.setType(Frame.SPSPPS);
        spsPpsFrame.setSps(sps);
        spsPpsFrame.setPps(pps);
        putFrame(spsPpsFrame);*/
    }

    private void onVideo(byte[] video, int type) {
        decoderH264(video);

        /*Frame frame = new Frame();
        switch (type) {
            case Frame.KEY_FRAME:
                frame.setType(Frame.KEY_FRAME);
                frame.setBytes(video);
                putFrame(frame);
                break;
            case Frame.NORMAL_FRAME:
                frame.setType(Frame.NORMAL_FRAME);
                frame.setBytes(video);
                putFrame(frame);
                break;
            case Frame.AUDIO_FRAME:
                frame.setType(Frame.AUDIO_FRAME);
                frame.setBytes(video);
                putFrame(frame);
                break;
            default:
                Log.e("AcceptH264MsgThread", "other video...");
                break;
        }*/
    }

    private ArrayBlockingQueue<byte[]> mPlayQueue = new ArrayBlockingQueue<>(10, true);
    private byte[] marker0 = new byte[]{0, 0, 0, 1};
    private byte[] dummyFrame = new byte[]{0x00, 0x00, 0x01, 0x20};

    public void decoderH264(byte[] buffer) {
        int startIndex = 0;
        int bufferLength = buffer.length;

        while (true) {
            if (startIndex >= bufferLength) {
                break;
            }

            // 0 0 0 1 ... 0(A) 0 0 1 ... 0(B) 0 0 1 ...
            // 如果buff中有多个0 0 0 1,那么nextFrameStart就是下一个0 0 0 1的位置,即A和B的位置
            // 如果buff中只有一个0 0 0 1,那么nextFrameStart为-1
            int nextFrameStart = KMPMatch(marker0, buffer, startIndex + 2, bufferLength);
            if (nextFrameStart == -1) {
                nextFrameStart = bufferLength;
            }

            if (nextFrameStart - startIndex > 0) {
                byte[] frame = new byte[nextFrameStart - startIndex];
                System.arraycopy(
                        buffer, startIndex, frame, startIndex, nextFrameStart - startIndex);
                try {
                    mPlayQueue.put(frame);
                } catch (Exception e) {
                    Log.e(TAG, "put bytes exception : " + e.toString());
                }
            }
            startIndex = nextFrameStart;
        }
    }

    private int KMPMatch(byte[] pattern, byte[] bytes, int start, int remain) {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int[] lsp = computeLspTable(pattern);

        int j = 0;  // Number of chars matched in pattern
        for (int i = start; i < remain; i++) {
            while (j > 0 && bytes[i] != pattern[j]) {
                // Fall back in the pattern
                j = lsp[j - 1];  // Strictly decreasing
            }
            if (bytes[i] == pattern[j]) {
                // Next char matched, increment position
                j++;
                if (j == pattern.length) {
                    return i - (j - 1);
                }
            }
        }

        return -1;  // Not found
    }

    private int[] computeLspTable(byte[] pattern) {
        int[] lsp = new int[pattern.length];
        lsp[0] = 0;  // Base case
        for (int i = 1; i < pattern.length; i++) {
            // Start by assuming we're extending the previous LSP
            int j = lsp[i - 1];
            while (j > 0 && pattern[i] != pattern[j]) {
                j = lsp[j - 1];
            }
            if (pattern[i] == pattern[j]) {
                j++;
            }
            lsp[i] = j;
        }
        return lsp;
    }

    private void putFrame(Frame frame) {
        switch (frame.getType()) {
            case Frame.KEY_FRAME:
            case Frame.NORMAL_FRAME:
                try {
                    decoderH264(frame.getBytes());
                } catch (Exception e) {
                    Log.e(TAG, "frame Exception : " + e.toString());
                }
                break;
            case Frame.SPSPPS:
                try {
                    ByteBuffer bb =
                            ByteBuffer.allocate(frame.getPps().length + frame.getSps().length);
                    bb.put(frame.getSps());
                    bb.put(frame.getPps());
                    decoderH264(bb.array());
                } catch (Exception e) {
                    Log.e(TAG, "sps pps Exception : " + e.toString());
                }
                break;
            default:
                break;
        }
    }

    public byte[] takePacket() {
        //Log.i("player_alexander", "takePacket() size: " + mPlayQueue.size());
        try {
            return mPlayQueue.take();
        } catch (Exception e) {
            Log.e(TAG, "take bytes exception : " + e.toString());
            return null;
        }
    }

}
