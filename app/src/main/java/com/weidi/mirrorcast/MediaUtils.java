package com.weidi.mirrorcast;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.Range;
import android.util.SparseArray;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/***
 mimeType是已知的,都是通过mimeType去得到各种对象

 MediaCodec可以处理的数据
 压缩数据（即为经过H264,H265等编码的视频数据或AAC等编码的音频数据）
 原始音频数据
 原始视频数据

 audio:
 audio/mp4a-latm
 audio/raw

 video:
 video/hevc
 video/avc

 在Android上硬编码视频码率远远高于软编码，原因是Android只支持H264 Baseline。
 MP（Main Profile ）& HP（High Profile）可以大幅减少视频的码率
 要想使用MP要求Android系统版本在7.0及其以上
 在Android 7.0及其之后的系统中已经支持了MP和HP
 High profile（HP）可以比Main profile（MP）节省10%的码流量，比MPEG-2 MP节省60%的码流量
 H264 Profile
 简单理解就是H264有多个版本，版本越高编码效率和压缩率就越高，对应的版本是Profile。
 从低到高分别为：Baseline、Main、High

 android平台下使用相机默认图像格式是NV21属于YUV420SP格式
 YU12格式在android平台下也叫作I420格式
 x264编码的输入数据为I420格式

 摄像头数据 ---> H264
 NV21     ---> I420

 属于YUV420P格式的有 :YU12 YV12
 属于YUV420SP格式的有:NV21 NV12

 YUV420P   : YYYYYYYY UUVV
 YUV420SP  : YYYYYYYY UVUV

 YU12(I420): YYYYYYYY UUVV    =>    YUV420P
 YV12      : YYYYYYYY VVUU    =>    YUV420P

 NV12      : YYYYYYYY UVUV    =>    YUV420SP
 NV21      : YYYYYYYY VUVU    =>    YUV420SP

 YUY2      : YUYV YUYV YUYV

 YUV和RGB转换
 Y      =  (0.257 * R) + (0.504 * G) + (0.098 * B) + 16
 Cr = V =  (0.439 * R) - (0.368 * G) - (0.071 * B) + 128
 Cb = U = -(0.148 * R) - (0.291 * G) + (0.439 * B) + 128

 B = 1.164(Y - 16) + 2.018(U - 128)
 G = 1.164(Y - 16) - 0.813(V - 128) - 0.391(U - 128)
 R = 1.164(Y - 16) + 1.596(V - 128)

 手机端作为rtsp服务端，vlc作为客户端，通过rtp协议来传输视频流。这样做就省去了搭建流媒体服务的工作。
 */

public class MediaUtils {

    private static final String TAG =
            MediaUtils.class.getSimpleName();
    private static final boolean DEBUG = true;

    /***
     相同条件:
     mWidth = 720;
     mHeight = 1280;

     FRAME_RATE = 30;
     VIDEO_BIT_RATE = 800000;
     一分钟大概17.8MB
     FRAME_RATE = 30;
     VIDEO_BIT_RATE = 25000;
     一分钟大概12.4MB
     FRAME_RATE = 15;
     VIDEO_BIT_RATE = 800;
     一分钟大概20.8MB
     FRAME_RATE = 30;
     VIDEO_BIT_RATE = 800;
     一分钟大概15.5MB
     FRAME_RATE = 15;
     VIDEO_BIT_RATE = 800;
     一分钟大概19.1MB
     FRAME_RATE = 30;
     VIDEO_BIT_RATE = 30000;
     一分钟大概13.2MB
     FRAME_RATE = 30;
     VIDEO_BIT_RATE = 64000;
     一分钟大概15.9MB
     */
    // 想要的编码格式
    // video/avc(H264)
    public static final String VIDEO_MIME = MediaFormat.MIMETYPE_VIDEO_AVC;
    // audio/mp4a-latm(AAC)
    public static final String AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC;
    private static final int VIDEO_BIT_RATE = 64000;// 1200000 8000000 800000
    private static final int FRAME_RATE = 25;// 30
    private static final int IFRAME_INTERVAL = 1;
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    /***
     AC4 is not supported in AOSP and MTK added ENCODING_AC4 in AOSP as 15.
     It is supported in AOSP as API Level 28 or later.
     */
    public static final int ENCODING_AC4;

    static {
        if (Build.VERSION.SDK_INT >= 28) {
            ENCODING_AC4 = AudioFormat.ENCODING_AC4;
        } else {
            ENCODING_AC4 = AudioFormat.ENCODING_AAC_ELD;
        }
    }

    public static final Map<Integer, String> colorFormatMap;

    static {
        colorFormatMap = new HashMap<Integer, String>();
        colorFormatMap.put(1, "COLOR_FormatMonochrome");
        colorFormatMap.put(2, "COLOR_Format8bitRGB332");
        colorFormatMap.put(3, "COLOR_Format12bitRGB444");
        colorFormatMap.put(4, "COLOR_Format16bitARGB4444");
        colorFormatMap.put(5, "COLOR_Format16bitARGB1555");
        colorFormatMap.put(6, "COLOR_Format16bitRGB565");
        colorFormatMap.put(7, "COLOR_Format16bitBGR565");
        colorFormatMap.put(8, "COLOR_Format18bitRGB666");
        colorFormatMap.put(9, "COLOR_Format18bitARGB1665");
        colorFormatMap.put(10, "COLOR_Format19bitARGB1666");
        colorFormatMap.put(11, "COLOR_Format24bitRGB888");
        colorFormatMap.put(12, "COLOR_Format24bitBGR888");
        colorFormatMap.put(13, "COLOR_Format24bitARGB1887");
        colorFormatMap.put(14, "COLOR_Format25bitARGB1888");
        colorFormatMap.put(15, "COLOR_Format32bitBGRA8888");
        colorFormatMap.put(16, "COLOR_Format32bitARGB8888");
        colorFormatMap.put(17, "COLOR_FormatYUV411Planar");
        colorFormatMap.put(18, "COLOR_FormatYUV411PackedPlanar");
        colorFormatMap.put(19, "COLOR_FormatYUV420Planar");
        colorFormatMap.put(20, "COLOR_FormatYUV420PackedPlanar");
        colorFormatMap.put(21, "COLOR_FormatYUV420SemiPlanar");
        colorFormatMap.put(22, "COLOR_FormatYUV422Planar");
        colorFormatMap.put(23, "COLOR_FormatYUV422PackedPlanar");
        colorFormatMap.put(24, "COLOR_FormatYUV422SemiPlanar");
        colorFormatMap.put(25, "COLOR_FormatYCbYCr");
        colorFormatMap.put(26, "COLOR_FormatYCrYCb");
        colorFormatMap.put(27, "COLOR_FormatCbYCrY");
        colorFormatMap.put(28, "COLOR_FormatCrYCbY");
        colorFormatMap.put(29, "COLOR_FormatYUV444Interleaved");
        colorFormatMap.put(30, "COLOR_FormatRawBayer8bit");
        colorFormatMap.put(31, "COLOR_FormatRawBayer10bit");
        colorFormatMap.put(32, "COLOR_FormatRawBayer8bitcompressed");
        colorFormatMap.put(33, "COLOR_FormatL2");
        colorFormatMap.put(34, "COLOR_FormatL4");
        colorFormatMap.put(35, "COLOR_FormatL8");
        colorFormatMap.put(36, "COLOR_FormatL16");
        colorFormatMap.put(37, "COLOR_FormatL24");
        colorFormatMap.put(38, "COLOR_FormatL32");
        colorFormatMap.put(39, "COLOR_FormatYUV420PackedSemiPlanar");
        colorFormatMap.put(40, "COLOR_FormatYUV422PackedSemiPlanar");
        colorFormatMap.put(41, "COLOR_Format18BitBGR666");
        colorFormatMap.put(42, "COLOR_Format24BitARGB6666");
        colorFormatMap.put(43, "COLOR_Format24BitABGR6666");
        colorFormatMap.put(0x7f000100, "COLOR_TI_FormatYUV420PackedSemiPlanar");// 2130706688
        colorFormatMap.put(0x7F000789, "COLOR_FormatSurface");// 2130708361
        colorFormatMap.put(0x7F00A000, "COLOR_Format32bitABGR8888");// 2130747392
        colorFormatMap.put(0x7F420888, "COLOR_FormatYUV420Flexible");// 2135033992
        colorFormatMap.put(0x7F422888, "COLOR_FormatYUV422Flexible");// 2135042184
        colorFormatMap.put(0x7F444888, "COLOR_FormatYUV444Flexible");// 2135181448
        colorFormatMap.put(0x7F36B888, "COLOR_FormatRGBFlexible");// 2134292616
        colorFormatMap.put(0x7F36A888, "COLOR_FormatRGBAFlexible");// 2134288520
        colorFormatMap.put(0x7fa30c00, "COLOR_QCOM_FormatYUV420SemiPlanar");// 2141391872
        /***
         COLOR_FormatYUV420Flexible不是一种确定的YUV420格式,
         而是包含
         COLOR_FormatYUV411Planar
         COLOR_FormatYUV411PackedPlanar
         COLOR_FormatYUV420Planar
         COLOR_FormatYUV420PackedPlanar
         COLOR_FormatYUV420SemiPlanar
         COLOR_FormatYUV420PackedSemiPlanar
         */
    }

    /***
     MediaCodecList ---> MediaCodecInfo --->
     CodecCapabilities,AudioCapabilities,VideoCapabilities

     看我吧
     */
    public static void lookAtMe() {
        // 第一步
        MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        // 第二步
        MediaCodecInfo[] mediaCodecInfos = mediaCodecList.getCodecInfos();
        if (mediaCodecInfos == null) {
            return;
        }
        for (MediaCodecInfo mediaCodecInfo : mediaCodecInfos) {
            if (mediaCodecInfo == null) {
                continue;
            }
            if (DEBUG)
                Log.d(TAG,
                        "lookAtMe()===================================================");
            if (DEBUG) {
                Log.d(TAG,
                        "lookAtMe() mediaCodecInfo.name:      " + mediaCodecInfo.getName());
                Log.d(TAG,
                        "lookAtMe() mediaCodecInfo.isEncoder: " + mediaCodecInfo.isEncoder());
            }
            String[] types = mediaCodecInfo.getSupportedTypes();
            if (types == null) {
                continue;
            }
            // MediaCodecInfo.CodecCapabilities需要通过type得到
            for (String type : types) {
                if (TextUtils.isEmpty(type)) {
                    continue;
                }
                if (DEBUG)
                    Log.d(TAG,
                            "lookAtMe() type: " + type);
                MediaCodecInfo.CodecCapabilities codecCapabilities =
                        mediaCodecInfo.getCapabilitiesForType(type);
                if (codecCapabilities == null) {
                    continue;
                }
                // 好像是用在TV上,对4K 60FPS的视频进行播放的一种模式
                // 支持的话才能启用相关的代码.如AudioAttributes.FLAG_HW_AV_SYNC这个flag的设置
                boolean isTunneling = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        && codecCapabilities.isFeatureSupported(
                        MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback);
                if (DEBUG)
                    Log.d(TAG,
                            "lookAtMe() isTunneling: " + isTunneling);

                MediaCodecInfo.AudioCapabilities audioCapabilities =
                        codecCapabilities.getAudioCapabilities();
                MediaCodecInfo.VideoCapabilities videoCapabilities =
                        codecCapabilities.getVideoCapabilities();
                // 然后可以通过audioCapabilities和videoCapabilities得到各自的有关信息
                if (audioCapabilities != null) {
                    if (DEBUG)
                        Log.d(TAG,
                                "lookAtMe() 音频比特率范围: " +
                                        audioCapabilities.getBitrateRange().toString());
                    Range<Integer>[] sampleRateRanges =
                            audioCapabilities.getSupportedSampleRateRanges();
                    int sampleRateRangesCount = sampleRateRanges.length;
                    StringBuilder stringBuilder = new StringBuilder("[");
                    for (int i = 0; i < sampleRateRangesCount; i++) {
                        stringBuilder.append(sampleRateRanges[i].getLower());
                        if (i != sampleRateRangesCount - 1) {
                            stringBuilder.append(", ");
                        } else {
                            stringBuilder.append("]");
                        }
                    }
                    if (DEBUG)
                        Log.d(TAG,
                                "lookAtMe() 音频采样率范围: " +
                                        stringBuilder.toString());
                    if (DEBUG)
                        Log.d(TAG,
                                "lookAtMe() 音频最大输入通道数: " +
                                        audioCapabilities.getMaxInputChannelCount());
                }
                if (videoCapabilities != null) {
                    if (DEBUG)
                        Log.d(TAG,
                                "lookAtMe() 视频比特率范围:" +
                                        videoCapabilities.getBitrateRange().toString());
                    if (DEBUG)
                        Log.d(TAG,
                                "lookAtMe() 视频支持的宽度范围: " +
                                        videoCapabilities.getSupportedWidths().toString());
                    if (DEBUG)
                        Log.d(TAG,
                                "lookAtMe() 视频支持的高度范围: " +
                                        videoCapabilities.getSupportedHeights().toString());
                    if (DEBUG)
                        Log.d(TAG,
                                "lookAtMe() 视频帧率范围: " +
                                        videoCapabilities.getSupportedFrameRates().toString());
                }
            }
        }
    }

    /***
     * 最好是通过这个方法找到多个MediaCodecInfo对象,
     * 如果一个创建或者初始化失败,可以再使用下一个,这样能
     * 全部循环.如果使用getEncoderMediaCodecInfo(...)
     * 只能找到一个MediaCodecInfo对象.
     *
     *
     * Find an encoder supported specified MIME type
     * 查找支持"video/avc"这种MIME type的CodecName,
     * 然后就可以通过MediaCodec.createByCodecName(CodecName)
     * 这个方法得到一个可以MediaCodec对象.
     *
     * @param mime 如MediaFormat.MIMETYPE_VIDEO_AVC
     * @return Returns empty array if not found any encoder supported specified MIME type
     *
     * 在我的手机上找到的编码器
     * Video的CodecName(根据"video/avc"):
     * "OMX.google.h264.encoder"
     * "OMX.SEC.AVC.Encoder"
     *
     * Audio的CodecName(根据"audio/mp4a-latm"):
     * "OMX.google.aac.encoder"
     *
     * 解码器
     * "OMX.google.aac.decoder"
     */
    public static MediaCodecInfo[] findAllEncodersByMime(String mime) {
        // MediaCodecList.REGULAR_CODECS
        // MediaCodecList.ALL_CODECS
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        List<MediaCodecInfo> mediaCodecInfos = new ArrayList<MediaCodecInfo>();
        for (MediaCodecInfo mediaCodecInfo : codecList.getCodecInfos()) {
            // 过滤掉非编码器
            if (mediaCodecInfo == null || !mediaCodecInfo.isEncoder()) {
                continue;
            }
            try {
                MediaCodecInfo.CodecCapabilities codecCapabilities =
                        mediaCodecInfo.getCapabilitiesForType(mime);
                if (codecCapabilities == null) {
                    continue;
                }
                if (codecCapabilities.getVideoCapabilities() != null) {
                    Log.d(TAG, "findAllEncodersByMime() codecName: " +
                            mediaCodecInfo.getName() +
                            " 编码时支持的colorFormat start");
                    for (int colorFormat : codecCapabilities.colorFormats) {
                        Log.d(TAG, "findAllEncodersByMime() " + colorFormat +
                                " : " + colorFormatMap.get(colorFormat));
                    }
                    Log.d(TAG, "findAllEncodersByMime() codecName: " +
                            mediaCodecInfo.getName() +
                            " 编码时支持的colorFormat end");
                }
            } catch (IllegalArgumentException e) {
                // unsupported
                continue;
            }
            mediaCodecInfos.add(mediaCodecInfo);
        }

        return mediaCodecInfos.toArray(new MediaCodecInfo[mediaCodecInfos.size()]);
    }

    public static MediaCodecInfo[] findAllDecodersByMime(String mime) {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        List<MediaCodecInfo> mediaCodecInfos = new ArrayList<MediaCodecInfo>();
        for (MediaCodecInfo mediaCodecInfo : codecList.getCodecInfos()) {
            // 过滤掉编码器
            if (mediaCodecInfo == null || mediaCodecInfo.isEncoder()) {
                continue;
            }

            try {
                MediaCodecInfo.CodecCapabilities codecCapabilities =
                        mediaCodecInfo.getCapabilitiesForType(mime);
                if (codecCapabilities == null
                        || codecCapabilities.getVideoCapabilities() == null) {
                    continue;
                }

                if (DEBUG) {
                    Log.d(TAG, "findAllDecodersByMime() codecName: " +
                            mediaCodecInfo.getName() +
                            " 解码时支持的colorFormat start");
                    for (int colorFormat : codecCapabilities.colorFormats) {
                        Log.d(TAG, "findAllDecodersByMime() " + colorFormat +
                                " : " + colorFormatMap.get(colorFormat));
                    }
                    Log.d(TAG, "findAllDecodersByMime() codecName: " +
                            mediaCodecInfo.getName() +
                            " 解码时支持的colorFormat end");
                }
            } catch (IllegalArgumentException e) {
                // unsupported
                continue;
            }

            mediaCodecInfos.add(mediaCodecInfo);
        }

        return mediaCodecInfos.toArray(new MediaCodecInfo[mediaCodecInfos.size()]);
    }

    /***
     *
     * @param mime "video/avc" "audio/mp4a-latm"
     * @return
     */
    public static MediaCodecInfo getEncoderMediaCodecInfo(String mime) {
        if (TextUtils.isEmpty(mime)) {
            return null;
        }
        // 找到的都是编码器
        MediaCodecInfo[] infos = findAllEncodersByMime(mime);
        if (infos == null) {
            return null;
        }
        for (MediaCodecInfo mediaCodecInfo : infos) {
            if (mediaCodecInfo == null) {
                continue;
            }
            for (String type : mediaCodecInfo.getSupportedTypes()) {
                if (TextUtils.isEmpty(type)) {
                    continue;
                }
                if (type.equalsIgnoreCase(mime)) {
                    if (DEBUG)
                        Log.d(TAG,
                                "getEncoderMediaCodecInfo() the selected encoder is : " +
                                        mediaCodecInfo.getName());
                    return mediaCodecInfo;
                }
            }
        }
        return null;
    }

    public static MediaCodecInfo getDecoderMediaCodecInfo(String mime) {
        if (TextUtils.isEmpty(mime)) {
            return null;
        }
        // 找到的都是解码器
        MediaCodecInfo[] infos = findAllDecodersByMime(mime);
        if (infos == null) {
            return null;
        }
        for (MediaCodecInfo mediaCodecInfo : infos) {
            if (mediaCodecInfo == null) {
                continue;
            }
            for (String type : mediaCodecInfo.getSupportedTypes()) {
                if (TextUtils.isEmpty(type)) {
                    continue;
                }
                if (type.equalsIgnoreCase(mime)) {
                    if (DEBUG)
                        Log.d(TAG,
                                "getDecoderMediaCodecInfo() the selected decoder is : " +
                                        mediaCodecInfo.getName());
                    return mediaCodecInfo;
                }
            }
        }
        return null;
    }

    /***
     得到用于编码的MediaCodec对象的步骤:
     1.视频部分想要编码到什么,比如这里是想要编码成“video/avc”(mime)
     然后根据这个mime去找MediaCodecInfo对象,
     如果找到的话,说明当前手机支持此mime.
     2.然后从MediaCodecInfo对象中得到name,
     再根据MediaCodec.createByCodecName(name)
     方法就能得到用于编码的MediaCodec对象.
     * @return
     */
    public static MediaCodec getVideoEncoderMediaCodec(Surface surface) {
        MediaCodec encoder = null;
        MediaCodecInfo[] mediaCodecInfos = findAllEncodersByMime(VIDEO_MIME);
        for (MediaCodecInfo mediaCodecInfo : mediaCodecInfos) {
            if (mediaCodecInfo == null) {
                continue;
            }
            try {
                encoder = MediaCodec.createByCodecName(mediaCodecInfo.getName());
                encoder.configure(
                        getVideoEncoderMediaFormat(WIDTH, HEIGHT),
                        surface, null,
                        MediaCodec.CONFIGURE_FLAG_ENCODE);
                encoder.start();
                if (DEBUG)
                    Log.d(TAG, "getVideoEncoderMediaCodec() MediaCodec create success");
                break;
            } catch (NullPointerException
                    | IllegalArgumentException
                    | IOException e) {
                e.printStackTrace();
                releaseMediaCodec(encoder);
                encoder = null;
                continue;
            }
        }

        /***
         注意:
         编码器使用了surface作为输入源，则会选择surface模式，
         不会回调onInputBufferAvailable，
         我们同样不能手动输入数据给编码器，否则会发生错误。

         系统会首先回调onOutputFormatChanged方法，
         这个方法就是关于h264的sps和pps信息，
         这两个数据在解析h264的时候非常重要，不能丢失。
         sps和pps会存放在MediaForamt中，可以通过getByteBuffer来获取，
         他们的key分别是”csd-0”和”csd-1”，注意顺序不能取反。

         h264流屏幕在变化很小的时候产生的数据很小，
         变化很大的时候产生的数据较大，
         这些数据都是通过onOutputBufferAvailable函数回调回来。

         所有的数据都存入一个线程安全的阻塞队列(LinkedBlockingQueue)中，
         启动另一个线程不断的从这个队列中取出数据传递给jni包装的jrtplib，
         发送包装好的rtp数据给接收端。
         */
        // 使用同步方式的话,下面的代码不能执行
        /*encoder.setCallback(new MediaCodec.Callback() {

            @Override
            public void onInputBufferAvailable(
                    MediaCodec codec,
                    int index) {
                // 这个方法在使用surface模式的时候不会回调
            }

            @Override
            public void onOutputBufferAvailable(
                    MediaCodec codec,
                    int index,
                    MediaCodec.BufferInfo info) {
                ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                // 处理数据

                // 释放空间
                codec.releaseOutputBuffer(index, false);
            }

            @Override
            public void onError(
                    MediaCodec codec,
                    MediaCodec.CodecException e) {
                if (DEBUG)
                    Log.e(TAG, "onError()\n" + e.getDiagnosticInfo());
            }

            @Override
            public void onOutputFormatChanged(
                    MediaCodec codec,
                    MediaFormat format) {
                if (DEBUG)
                    Log.d(TAG, "onOutputFormatChanged() format: " + format);
                // getSpsPpsByteBuffer(mediaFormat);
            }
        });*/

        return encoder;
    }

    public static MediaCodec getVideoEncoderMediaCodec(MediaFormat format) {
        MediaCodec encoder = null;
        MediaCodecInfo[] mediaCodecInfos = findAllEncodersByMime(VIDEO_MIME);
        for (MediaCodecInfo mediaCodecInfo : mediaCodecInfos) {
            if (mediaCodecInfo == null) {
                continue;
            }
            try {
                encoder = MediaCodec.createByCodecName(mediaCodecInfo.getName());
                encoder.configure(
                        format,
                        null, null,
                        MediaCodec.CONFIGURE_FLAG_ENCODE);
                //encoder.start();
                if (DEBUG)
                    Log.d(TAG, "getVideoEncoderMediaCodec() MediaCodec create success");
                break;
            } catch (NullPointerException
                    | IllegalArgumentException
                    | IOException e) {
                e.printStackTrace();
                releaseMediaCodec(encoder);
                encoder = null;
                continue;
            }
        }

        return encoder;
    }


    // 只用于录屏,因为MediaFormat是录屏的参数
    public static MediaCodec getVideoEncoderMediaCodec() {
        MediaCodec encoder = null;
        MediaCodecInfo[] mediaCodecInfos = findAllEncodersByMime(VIDEO_MIME);
        for (MediaCodecInfo mediaCodecInfo : mediaCodecInfos) {
            if (mediaCodecInfo == null) {
                continue;
            }
            try {
                encoder = MediaCodec.createByCodecName(mediaCodecInfo.getName());
                encoder.configure(
                        getVideoEncoderMediaFormat(WIDTH, HEIGHT),
                        null, null,
                        MediaCodec.CONFIGURE_FLAG_ENCODE);
                encoder.start();
                if (DEBUG)
                    Log.d(TAG, "getVideoEncoderMediaCodec() MediaCodec create success");
                break;
            } catch (NullPointerException
                    | IllegalArgumentException
                    | IOException e) {
                e.printStackTrace();
                releaseMediaCodec(encoder);
                encoder = null;
                continue;
            }
        }

        return encoder;
    }

    public static MediaCodec getAudioEncoderMediaCodec(MediaFormat format) {
        MediaCodec encoder = null;
        MediaCodecInfo[] mediaCodecInfos = findAllEncodersByMime(AUDIO_MIME);
        for (MediaCodecInfo mediaCodecInfo : mediaCodecInfos) {
            if (mediaCodecInfo == null) {
                continue;
            }
            try {
                encoder = MediaCodec.createByCodecName(mediaCodecInfo.getName());
                encoder.configure(
                        format,
                        null, null,
                        MediaCodec.CONFIGURE_FLAG_ENCODE);
                //encoder.start();
                if (DEBUG)
                    Log.d(TAG, "getAudioEncoderMediaCodec() MediaCodec create success: " +
                            mediaCodecInfo.getName());
                break;
            } catch (NullPointerException
                    | IllegalArgumentException
                    | IOException e) {
                e.printStackTrace();
                releaseMediaCodec(encoder);
                encoder = null;
                continue;
            }
        }

        return encoder;
    }

    /***
     与下面的getAudioEncoderMediaFormat()对应,因为用的mime都是AUDIO_MIME_TYPE
     @return
     */
    public static MediaCodec getAudioEncoderMediaCodec() {
        MediaCodec encoder = null;
        MediaCodecInfo[] mediaCodecInfos = findAllEncodersByMime(AUDIO_MIME);
        for (MediaCodecInfo mediaCodecInfo : mediaCodecInfos) {
            if (mediaCodecInfo == null) {
                continue;
            }
            try {
                encoder = MediaCodec.createByCodecName(mediaCodecInfo.getName());
                encoder.configure(
                        getAudioEncoderMediaFormat(),
                        null, null,
                        MediaCodec.CONFIGURE_FLAG_ENCODE);
                encoder.start();
                if (DEBUG)
                    Log.d(TAG, "getAudioEncoderMediaCodec() MediaCodec create success: " +
                            mediaCodecInfo.getName());
                break;
            } catch (NullPointerException
                    | IllegalArgumentException
                    | IOException e) {
                e.printStackTrace();
                releaseMediaCodec(encoder);
                encoder = null;
                continue;
            }
        }

        return encoder;
    }

    /***
     根据实际视频使用，因为mime不一定是“video/avc”
     * @return
     */
    public static MediaCodec getVideoDecoderMediaCodec() {
        return getVideoDecoderMediaCodec(getVideoDecoderMediaFormat(1280, 720));
        /***
         onInputBufferAvailable会一直回调来让我们不断的从队列中取出数据提交给解码器。
         此处一定要注意，假如用户通过dequeuinputbuffer方法获取了缓冲的索引，
         必须调用queueinputbuffer方法来释放缓冲区，
         将缓冲区的所有权交给mediacodec，
         否则后续将不会回调onInputBufferAvailable方法。

         队列中没有数据的时候可以提交空数据给解码器

         mc.queueInputBuffer(inputBufferId, 0,0, 0, 0);
         队列中有数据的时候首先要区分一下是不是sps或者pps，
         是的话就必须当做配置信息提交给解码器，
         不是的话就直接提交一帧完整的数据给解码器。
         */
        /*decoder.setCallback(new MediaCodec.Callback() {

            @Override
            public void onInputBufferAvailable(
                    MediaCodec codec, int index) {
                // 处理数据
            }

            @Override
            public void onOutputBufferAvailable(
                    MediaCodec codec,
                    int index,
                    MediaCodec.BufferInfo info) {
                // 直接释放即可
                codec.releaseOutputBuffer(index, true);
            }

            @Override
            public void onError(
                    MediaCodec codec,
                    MediaCodec.CodecException e) {

            }

            @Override
            public void onOutputFormatChanged(
                    MediaCodec codec,
                    MediaFormat format) {

            }
        });*/
    }

    public static MediaCodec getVideoDecoderMediaCodec(MediaFormat format) {
        return getVideoDecoderMediaCodec(VIDEO_MIME, format);
    }

    public static MediaCodec getVideoDecoderMediaCodec(String mime, MediaFormat mediaFormat) {
        return getVideoDecoderMediaCodec(mime, mediaFormat, null);
    }

    public static MediaCodec getVideoDecoderMediaCodec(
            String mime, MediaFormat mediaFormat, Surface surface) {
        if (TextUtils.isEmpty(mime)
                || mediaFormat == null
                || surface == null) {
            return null;
        }
        MediaCodec decoder = null;
        MediaCodecInfo[] mediaCodecInfos = findAllDecodersByMime(mime);
        for (MediaCodecInfo mediaCodecInfo : mediaCodecInfos) {
            if (mediaCodecInfo == null) {
                continue;
            }
            try {
                decoder = MediaCodec.createByCodecName(mediaCodecInfo.getName());
                decoder.configure(mediaFormat, surface, null, 0);
                decoder.start();
                Log.d(TAG, "getVideoDecoderMediaCodec() " +
                        "MediaCodec create success mime: " + mime +
                        " codecName: " + mediaCodecInfo.getName());
                break;
            } catch (NullPointerException
                    | IllegalArgumentException
                    | MediaCodec.CodecException
                    | IOException e) {
                e.printStackTrace();
                releaseMediaCodec(decoder);
                decoder = null;
                continue;
            }
        }

        return decoder;
    }

    public static MediaCodec getAudioDecoderMediaCodec() {
        return getAudioDecoderMediaCodec(getAudioDecoderMediaFormat());
    }

    public static MediaCodec getAudioDecoderMediaCodec(MediaFormat format) {
        return getAudioDecoderMediaCodec(AUDIO_MIME, format);
    }

    public static MediaCodec getAudioDecoderMediaCodec(String mime, MediaFormat mediaFormat) {
        if (TextUtils.isEmpty(mime)
                || mediaFormat == null) {
            return null;
        }
        MediaCodec decoder = null;
        MediaCodecInfo[] mediaCodecInfos = findAllDecodersByMime(mime);
        for (MediaCodecInfo mediaCodecInfo : mediaCodecInfos) {
            if (mediaCodecInfo == null) {
                continue;
            }
            try {
                decoder = MediaCodec.createByCodecName(mediaCodecInfo.getName());
                decoder.configure(mediaFormat, null, null, 0);
                decoder.start();
                Log.d(TAG, "getAudioDecoderMediaCodec() " +
                        "MediaCodec create success mime: " + mime +
                        " codecName: " + mediaCodecInfo.getName());
                break;
            } catch (NullPointerException
                    | IllegalArgumentException
                    | MediaCodec.CodecException
                    | IOException e) {
                e.printStackTrace();
                releaseMediaCodec(decoder);
                decoder = null;
                continue;
            }
        }

        return decoder;
    }

    /***
     Encoder时可以自己作主指定mime得到MediaFormat对象
     这个方法是给录屏设置的参数
     width和height是视频的尺寸，这个尺寸不能超过视频采集时采集到的尺寸，否则会直接crash
     width和height不能大于手机的宽高,大一个像素也不行.
     * @param width
     * @param height
     * @return
     */
    public static MediaFormat getVideoEncoderMediaFormat(int width, int height) {
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME, width, height);
        format.setInteger(MediaFormat.KEY_MAX_WIDTH, width);
        format.setInteger(MediaFormat.KEY_MAX_HEIGHT, height);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height);
        // 设置码率
        format.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 4);
        // 设置帧率
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        // 设置抽取关键帧的间隔，以s为单位，负数或者0表示不抽取关键帧
        // i-frame iinterval
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        // 必须设置为COLOR_FormatSurface，因为是用Surface作为输入源
        // COLOR_FormatSurface这里表明数据将是一个graphicbuffer元数据
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        /***
         BITRATE_MODE_CQ:  表示完全不控制码率，尽最大可能保证图像质量
         BITRATE_MODE_CBR: 表示编码器会尽量把输出码率控制为设定值
         BITRATE_MODE_VBR: 表示编码器会根据图像内容的复杂度（实际上是帧间变化量的大小）来动态调整输出码率，
         图像复杂则码率高，图像简单则码率低
         */
        // 这个值不设置的话,录制的视频很模糊.电视机不支持
        format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);

        /***
         横屏
         byte[] header_sps = {0, 0, 0, 1, 103, 66, -128, 31, -38, 1, 64, 22, -24, 6, -48, -95, 53};
         byte[] header_pps = {0, 0 ,0, 1, 104, -50, 6, -30};
         */
        /*byte[] header_sps = {0, 0, 0, 1, 103, 66, -128, 31, -38, 2, -48, 40, 104, 6, -48, -95,
        53};
        byte[] header_pps = {0, 0, 0, 1, 104, -50, 6, -30};
        format.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
        format.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));*/

        // 不能设置
        //format.setInteger(MediaFormat.KEY_PROFILE,
        // MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
        Log.d(TAG, "getVideoEncoderMediaFormat() created video format: " + format);

        return format;
    }

    /***
     初始化AAC编码器
     与上面的getAudioEncoderMediaCodec()对应,因为用的mime都是AUDIO_MIME_TYPE
     channelCount为2,表示双声道,也就是
     channelConfig = AudioFormat.CHANNEL_IN_STEREO.
     @return
     */
    public static MediaFormat getAudioEncoderMediaFormat() {
        // channel-mask=12, sample-rate=44100, mime=audio/mp4a-latm, channel-count=2,
        // aac-profile=2, bitrate=176400, max-input-size=14208
        MediaFormat format = MediaFormat.createAudioFormat(
                AUDIO_MIME, sampleRateInHz, channelCount);
        // AAC-HE
        format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
        format.setInteger(MediaFormat.KEY_CHANNEL_MASK, channelConfig);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, getMinBufferSize() * 2);
        Log.d(TAG, "getAudioEncoderMediaFormat() created audio format: " + format);
        return format;
    }

    /***
     Decoder时一般不能自己作主指定mime得到MediaFormat对象,
     应该根据实际视频得到mime,然后得到MediaFormat对象.
     * @param width
     * @param height
     * @return
     */
    public static MediaFormat getVideoDecoderMediaFormat(int width, int height) {
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME, width, height);
        format.setInteger(MediaFormat.KEY_MAX_WIDTH, width);
        format.setInteger(MediaFormat.KEY_MAX_HEIGHT, height);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height);
        // 设置码率
        format.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 4);
        // 设置帧率
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        // 设置抽取关键帧的间隔，以s为单位，负数或者0表示不抽取关键帧
        // i-frame iinterval
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        Log.d(TAG, "getVideoDecoderMediaFormat() created video format: " + format);

        return format;
    }

    public static MediaFormat getAudioDecoderMediaFormat() {
        MediaFormat format = MediaFormat.createAudioFormat(
                AUDIO_MIME, sampleRateInHz, channelCount);
        // AAC-HE
        format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
        format.setInteger(MediaFormat.KEY_CHANNEL_MASK, channelConfig);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, getMinBufferSize() * 2);
        if (DEBUG)
            Log.d(TAG, "getAudioDecoderMediaFormat() created Audio format: " + format);

        return format;
    }

    /***
     public static final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
     本来AudioRecord是使用AudioFormat.CHANNEL_IN_MONO的,
     但是AudioFormat.CHANNEL_IN_MONO不能使用于AudioTrack.
     我的手机AudioTrack可以使用AudioFormat.CHANNEL_IN_STEREO.
     只有创建AudioRecord对象和AudioTrack对象的三个参数
     (sampleRateInHz,channelConfig和audioFormat)一样时,
     录制是什么声音,播放才是什么声音.
     因此选择AudioFormat.CHANNEL_IN_STEREO(双声道)作为创建
     AudioRecord对象和AudioTrack对象的参数.
     */
    // 下面的参数为了得到默认的AudioRecord对象和AudioTrack对象而定义的
    // 兼容所有Android设备
    public static int sampleRateInHz = 44100;
    // 下面两个是对应关系,只是方法所需要的参数不一样而已
    public static int channelCount = 2;
    // 立体声(AudioFormat.CHANNEL_IN_STEREO = AudioFormat.CHANNEL_OUT_STEREO)
    public static final int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
    // 采样精度,数据位宽(兼容所有Android设备)
    public static int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    // AudioRecord(录音)
    private static final int audioSource = MediaRecorder.AudioSource.MIC;
    // AudioTrack(播放)
    private static final int streamType = AudioManager.STREAM_MUSIC;
    // private static final int mode = AudioTrack.MODE_STATIC
    private static final int mode = AudioTrack.MODE_STREAM;
    public static final int sessionId = AudioManager.AUDIO_SESSION_ID_GENERATE;
    // 比特率(audioFormat = 16 / 8,所以AUDIO_BIT_RATE的值不需要再除以8)
    // 没有除以8之前的单位是bit,除以8之后的单位是byte
    public static final int AUDIO_BIT_RATE = sampleRateInHz * channelCount * audioFormat;

    /***
     AudioTrack
     int sampleRateInHz = 44100;
     int channelCount = 1;
     int channelConfig = AudioFormat.CHANNEL_IN_MONO;// 16
     int audioFormat = AudioFormat.ENCODING_PCM_8BIT;
     int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
     java.lang.IllegalArgumentException: Unsupported channel configuration.

     int sampleRateInHz = 44100;
     int channelCount = 1;
     int channelConfig = AudioFormat.CHANNEL_OUT_MONO;// 4
     int audioFormat = AudioFormat.ENCODING_PCM_8BIT;
     int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
     getMinBufferSize(): Invalid channel configuration.
     */

    /***
     bufferSizeInBytes
     使用了默认值
     AudioRecord内部的音频缓冲区的大小,该缓冲区的值不能低于一帧“音频帧”（Frame）的大小.
     一帧音频帧的大小计算如下:
     int size = 采样率 x 位宽 x 采样时间 x 通道数
     采样时间一般取2.5ms~120ms之间,由厂商或者具体的应用决定.

     在Android中,一帧的大小可以根据下面的公式得到,
     但是音频缓冲区的大小则必须是一帧大小的2～N倍.
     */
    public static int getMinBufferSize(int sampleRateInHz,
                                       int channelConfig,
                                       int audioFormat) {
        return AudioTrack.getMinBufferSize(
                sampleRateInHz,
                channelConfig,
                audioFormat);
    }

    public static int getMinBufferSize() {
        return AudioTrack.getMinBufferSize(
                sampleRateInHz,
                channelConfig,
                audioFormat);
    }

    /***
     @param audioSource
     MediaRecorder.AudioSource.DEFAULT = 0
     MediaRecorder.AudioSource.MIC(比较常用)
     MediaRecorder.AudioSource.VOICE_UPLINK
     MediaRecorder.AudioSource.VOICE_DOWNLINK
     MediaRecorder.AudioSource.VOICE_CALL
     MediaRecorder.AudioSource.CAMCORDER
     MediaRecorder.AudioSource.VOICE_RECOGNITION
     MediaRecorder.AudioSource.VOICE_COMMUNICATION
     MediaRecorder.AudioSource.REMOTE_SUBMIX
     MediaRecorder.AudioSource.UNPROCESSED = 9
     @param sampleRateInHz
     8000
     11025
     16000
     22050
     32000
     44100(在所有设备上都能正常工作)
     48000
     一般蓝牙耳机无法达到44100Hz的采样率,
     所有在使用蓝牙耳机录音的时候,
     设置为8000Hz或者16000Hz.
     @param channelCount
     声道数
     @param audioFormat
     AudioFormat.ENCODING_PCM_16BIT(比较常用)
     AudioFormat.ENCODING_PCM_8BIT
     AudioFormat.ENCODING_PCM_FLOAT
     */
    public static AudioRecord createAudioRecord(
            int audioSource, int sampleRateInHz,
            int channelCount, int audioFormat) {
        /***
         channelConfig
         AudioFormat.CHANNEL_IN_STEREO
         AudioFormat.CHANNEL_IN_MONO(在所有设备上都能正常工作)
         */
        int channelConfig = AudioFormat.CHANNEL_IN_DEFAULT;
        switch (channelCount) {
            case 1:
                // 如果是单声道的话还不能确定是哪个值
                channelConfig = AudioFormat.CHANNEL_IN_MONO;// 16
                channelConfig = AudioFormat.CHANNEL_OUT_MONO;// 4
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;// 12
                break;
            case 2:
                channelConfig = AudioFormat.CHANNEL_IN_STEREO;// 12
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;// 12
                break;
            default:
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                break;
        }
        int bufferSizeInBytes = AudioRecord.getMinBufferSize(
                sampleRateInHz, channelConfig, audioFormat);
        if (DEBUG)
            Log.d(TAG, "createAudioRecord() bufferSizeInBytes: " + bufferSizeInBytes);
        if (bufferSizeInBytes <= 0) {
            if (DEBUG)
                Log.e(TAG, String.format(Locale.US,
                        "Bad arguments: getMinBufferSize(%d, %d, %d)",
                        sampleRateInHz, channelConfig, audioFormat));
            return null;
        }

        bufferSizeInBytes *= 2;
        AudioRecord audioRecord = null;
        try {
            audioRecord = new AudioRecord(
                    audioSource,
                    sampleRateInHz,
                    channelConfig,
                    audioFormat,
                    bufferSizeInBytes);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return null;
        }
        if (audioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
            if (DEBUG)
                Log.e(TAG, String.format(Locale.US,
                        "Bad arguments to new AudioRecord(%d, %d, %d)",
                        sampleRateInHz, channelConfig, audioFormat));
            return null;
        }

        return audioRecord;
    }

    /***
     一般使用这个就可以了(调用这个方法前必须先动态取得相应权限).
     <uses-permission android:name="android.permission.RECORD_AUDIO" />

     // 使用时需要开启子线程
     byte[] buffer = new byte[bufferSizeInBytes];
     // 录制的声音存放于buffer中
     int ret = audioRecord.read(buffer, 0, bufferSizeInBytes);
     if (ret == AudioRecord.ERROR_INVALID_OPERATION) {
     if (DEBUG)
     Log.e(TAG, "AudioRecord.ERROR_INVALID_OPERATION");
     } else if (ret == AudioRecord.ERROR_BAD_VALUE) {
     if (DEBUG)
     Log.e(TAG, "AudioRecord.ERROR_BAD_VALUE");
     } else {
     // do something
     }
     */
    public static AudioRecord createAudioRecord() {
        if (DEBUG)
            Log.d(TAG, "createAudioRecord() start");
        int bufferSizeInBytes = AudioRecord.getMinBufferSize(
                sampleRateInHz,
                channelConfig,
                audioFormat);
        if (DEBUG)
            Log.d(TAG, "createAudioRecord() bufferSizeInBytes: " + bufferSizeInBytes);
        if (bufferSizeInBytes <= 0) {
            //if (bufferSizeInBytes <= AudioRecord.ERROR_BAD_VALUE) {
            if (DEBUG)
                Log.e(TAG, String.format(Locale.US,
                        "Bad arguments: getMinBufferSize(%d, %d, %d)",
                        sampleRateInHz, channelConfig, audioFormat));
            return null;
        }

        bufferSizeInBytes *= 2;
        AudioRecord audioRecord = null;
        try {
            audioRecord = new AudioRecord(
                    audioSource,
                    sampleRateInHz,
                    channelConfig,
                    audioFormat,
                    bufferSizeInBytes);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return null;
        }
        // 此判断很关键
        if (audioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
            if (DEBUG)
                Log.e(TAG, String.format(Locale.US,
                        "Bad arguments to new AudioRecord(%d, %d, %d)",
                        sampleRateInHz, channelConfig, audioFormat));
            try {
                audioRecord.release();
            } finally {
                audioRecord = null;
            }
            return null;
        }

        if (DEBUG)
            Log.d(TAG, "createAudioRecord() end");
        return audioRecord;
    }

    public static void stopMediaCodec(MediaCodec mediaCodec) {
        if (mediaCodec == null) {
            return;
        }
        try {
            mediaCodec.stop();
        } catch (Exception e) {
            mediaCodec.release();
            mediaCodec = null;
        }
    }

    public static void releaseMediaCodec(MediaCodec mediaCodec) {
        stopMediaCodec(mediaCodec);
        if (mediaCodec == null) {
            return;
        }
        try {
            mediaCodec.release();
            mediaCodec = null;
        } catch (Exception e) {
            mediaCodec = null;
        }
    }

    public static void stopAudioRecord(AudioRecord audioRecord) {
        if (audioRecord == null) {
            return;
        }
        int recordingState = audioRecord.getRecordingState();
        if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            try {
                audioRecord.stop();
            } catch (Exception e) {
                audioRecord.release();
                audioRecord = null;
            }
        }
    }

    public static void releaseAudioRecord(AudioRecord audioRecord) {
        stopAudioRecord(audioRecord);
        if (audioRecord == null) {
            return;
        }
        try {
            audioRecord.release();
            audioRecord = null;
        } catch (Exception e) {
            audioRecord = null;
        }
    }

    /***
     使用AudioTrack播放的音频必须是解码后的PCM数据
     @param streamType AudioManager.STREAM_MUSIC
     @param sampleRateInHz 44100
     @param channelCount 声道数
     @param audioFormat AudioFormat.ENCODING_PCM_16BIT
     @param mode AudioTrack.MODE_STREAM or AudioTrack.MODE_STATIC
     @param sessionId AudioManager.AUDIO_SESSION_ID_GENERATE
     @return
     */
    public static AudioTrack createAudioTrack(
            int streamType,
            int sampleRateInHz, int channelCount,
            int audioFormat, int mode, int sessionId) {
        boolean isInputPcm = isEncodingLinearPcm(audioFormat);
        int channelConfig = getChannelConfig(channelCount, isInputPcm);
        int bufferSizeInBytes = AudioRecord.getMinBufferSize(
                sampleRateInHz, channelConfig, audioFormat);
        if (bufferSizeInBytes <= 0) {
            if (DEBUG)
                Log.e(TAG, String.format(Locale.US,
                        "Bad arguments: getMinBufferSize(%d, %d, %d)",
                        sampleRateInHz, channelConfig, audioFormat));
            // return null;
            bufferSizeInBytes = 4096;
        }

        if (DEBUG)
            Log.d(TAG, "createAudioTrack() bufferSizeInBytes: " + bufferSizeInBytes);
        bufferSizeInBytes *= 2;
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(sampleRateInHz)
                .setChannelMask(channelConfig)
                .setEncoding(audioFormat)
                .build();
        AudioTrack audioTrack = null;
        try {
            audioTrack = new AudioTrack(
                    attributes,
                    format,
                    bufferSizeInBytes,
                    mode,
                    sessionId);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return null;
        }
        if (audioTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
            audioTrack.release();
            if (DEBUG)
                Log.e(TAG, String.format(Locale.US,
                        "Bad arguments to new AudioTrack(%d, %d, %d, %d, %d)",
                        sampleRateInHz, channelConfig, audioFormat, mode, sessionId));
            return null;
        }

        return audioTrack;
    }

    public static AudioTrack createAudioTrack(
            int streamType,
            int sampleRateInHz,
            int channelCount,
            int audioFormat,
            int mode) {
        if (DEBUG)
            Log.d(TAG, "createAudioTrack(...) start");
        // 在我的手机上使用AudioFormat.CHANNEL_OUT_MONO创建不了AudioTrack
        boolean isInputPcm = isEncodingLinearPcm(audioFormat);
        int channelConfig = getChannelConfig(channelCount, isInputPcm);
        //int channelConfig = decideChannelConfig(channelCount, false, "");
        if (channelCount == 6) {
            channelConfig = 12;
        }
        int bufferSizeInBytes = getMinBufferSize(
                sampleRateInHz, channelConfig, audioFormat);
        if (channelCount == 6) {
            channelConfig = 252;
        }
        Log.d(TAG, "createAudioTrack(...)     channelConfig: " + channelConfig);
        Log.d(TAG, "createAudioTrack(...) bufferSizeInBytes: " + bufferSizeInBytes);
        if (bufferSizeInBytes <= 0) {
            if (DEBUG)
                Log.e(TAG, String.format(Locale.US,
                        "Bad arguments: getMinBufferSize(%d, %d, %d)",
                        sampleRateInHz, channelConfig, audioFormat));
            return null;
            // bufferSizeInBytes = 4096;
        }

        bufferSizeInBytes *= 2;
        AudioTrack audioTrack = null;
        try {
            // java.lang.IllegalArgumentException: Unsupported channel configuration.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                AudioFormat format = new AudioFormat.Builder()
                        .setSampleRate(sampleRateInHz)
                        .setChannelMask(channelConfig)
                        .setEncoding(audioFormat)
                        .build();
                audioTrack = new AudioTrack(
                        attributes,
                        format,
                        bufferSizeInBytes,
                        mode,
                        sessionId);
            } else {
                audioTrack = new AudioTrack(
                        streamType,
                        sampleRateInHz,
                        channelConfig,
                        audioFormat,
                        bufferSizeInBytes,
                        mode);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        if (audioTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
            if (DEBUG)
                Log.e(TAG, String.format(Locale.US,
                        "Bad arguments to new AudioTrack(%d, %d, %d, %d, %d)",
                        sampleRateInHz, channelConfig, audioFormat, mode, sessionId));
            MediaUtils.releaseAudioTrack(audioTrack);
            audioTrack = null;
            return null;
        }

        if (DEBUG)
            Log.d(TAG, "createAudioTrack(...) end");
        return audioTrack;
    }

    public static AudioTrack createAudioTrack(int sessionId) {
        if (sessionId < 0) {
            return null;
        }
        if (DEBUG)
            Log.d(TAG, "createAudioTrack() start");
        int bufferSizeInBytes = getMinBufferSize();
        if (DEBUG)
            Log.d(TAG, "createAudioTrack() bufferSizeInBytes: " + bufferSizeInBytes);
        if (bufferSizeInBytes <= 0) {
            if (DEBUG)
                Log.e(TAG, String.format(Locale.US,
                        "Bad arguments: getMinBufferSize(%d, %d, %d)",
                        sampleRateInHz, channelConfig, audioFormat));
            // return null;
            bufferSizeInBytes = 4096;
        }

        bufferSizeInBytes *= 2;
        AudioTrack audioTrack = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        // 一使用这个flag就出错
                        // .setFlags(AudioAttributes.FLAG_HW_AV_SYNC)
                        .build();
                AudioFormat format = new AudioFormat.Builder()
                        .setSampleRate(sampleRateInHz)
                        // 很关键的一个参数
                        .setChannelMask(channelConfig)
                        .setEncoding(audioFormat)
                        .build();
                audioTrack = new AudioTrack(
                        attributes,
                        format,
                        bufferSizeInBytes,
                        mode,
                        sessionId);
            } else {
                audioTrack = new AudioTrack(
                        streamType,
                        sampleRateInHz,
                        channelConfig,
                        audioFormat,
                        bufferSizeInBytes,
                        mode);
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return null;
        }
        if (audioTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
            if (DEBUG)
                Log.e(TAG, String.format(Locale.US,
                        "Bad arguments to new AudioTrack(%d, %d, %d, %d, %d)",
                        sampleRateInHz, channelConfig, audioFormat, mode, sessionId));
            try {
                audioTrack.release();
            } finally {
                audioTrack = null;
            }
            return null;
        }

        if (DEBUG)
            Log.d(TAG, "createAudioTrack() end");
        return audioTrack;
    }

    public static AudioTrack createAudioTrack() {
        if (DEBUG)
            Log.d(TAG, "createAudioTrack() start");
        int bufferSizeInBytes = getMinBufferSize();
        if (DEBUG)
            Log.d(TAG, "createAudioTrack() bufferSizeInBytes: " + bufferSizeInBytes);
        if (bufferSizeInBytes <= 0) {
            if (DEBUG)
                Log.e(TAG, String.format(Locale.US,
                        "Bad arguments: getMinBufferSize(%d, %d, %d)",
                        sampleRateInHz, channelConfig, audioFormat));
            // return null;
            bufferSizeInBytes = 4096;
        }

        bufferSizeInBytes *= 2;
        AudioTrack audioTrack = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                AudioFormat format = new AudioFormat.Builder()
                        .setSampleRate(sampleRateInHz)
                        // 很关键的一个参数
                        .setChannelMask(channelConfig)
                        .setEncoding(audioFormat)
                        .build();
                audioTrack = new AudioTrack(
                        attributes,
                        format,
                        bufferSizeInBytes,
                        mode,
                        sessionId);
            } else {
                audioTrack = new AudioTrack(
                        streamType,
                        sampleRateInHz,
                        channelConfig,
                        audioFormat,
                        bufferSizeInBytes,
                        mode);
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return null;
        }
        if (audioTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
            if (DEBUG)
                Log.e(TAG, String.format(Locale.US,
                        "Bad arguments to new AudioTrack(%d, %d, %d, %d, %d)",
                        sampleRateInHz, channelConfig, audioFormat, mode, sessionId));
            try {
                audioTrack.release();
            } finally {
                audioTrack = null;
            }
            return null;
        }

        if (DEBUG)
            Log.d(TAG, "createAudioTrack() end");
        return audioTrack;
    }

    public static void pauseAudioTrack(AudioTrack audioTrack) {
        if (audioTrack == null) {
            return;
        }
        int playState = audioTrack.getPlayState();
        if (playState == AudioTrack.PLAYSTATE_PLAYING) {
            try {
                audioTrack.pause();
            } catch (Exception e) {
                audioTrack.release();
                audioTrack = null;
            }
        }
    }

    public static void stopAudioTrack(AudioTrack audioTrack) {
        pauseAudioTrack(audioTrack);
        if (audioTrack == null) {
            return;
        }
        int playState = audioTrack.getPlayState();
        if (playState == AudioTrack.PLAYSTATE_PAUSED) {
            try {
                audioTrack.stop();
            } catch (Exception e) {
                audioTrack.release();
                audioTrack = null;
            }
        }
    }

    public static void releaseAudioTrack(AudioTrack audioTrack) {
        stopAudioTrack(audioTrack);
        if (audioTrack == null) {
            return;
        }
        int playState = audioTrack.getPlayState();
        if (playState == AudioTrack.PLAYSTATE_STOPPED) {
            try {
                audioTrack.release();
                audioTrack = null;
            } catch (Exception e) {
                audioTrack = null;
            }
        }
    }

    private static final int TIME_OUT = 10000;

    /***
     * 编解码一般就下面两个套路
     * feedInputBuffer(...)   --- Input
     * drainOutputBuffer(...) --- Output
     *
     * @param codec
     * @param data      需要编解码的数据
     * @param offset    从什么位置开始
     * @param size      有多少数据需要编解码
     * @param presentationTimeUs
     * @return ture is successed, and false is failed
     */
    public static boolean feedInputBuffer(
            MediaCodec codec,
            byte[] data,
            int offset,
            int size,
            long presentationTimeUs) {
        try {
            int roomIndex = codec.dequeueInputBuffer(TIME_OUT);
            if (roomIndex >= 0) {
                ByteBuffer room = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    room = codec.getInputBuffer(roomIndex);
                } else {
                    room = codec.getInputBuffers()[roomIndex];
                }
                if (room != null) {
                    room.clear();
                    room.put(data, offset, size);
                }
                int flags = 0;
                if (size == 0) {
                    presentationTimeUs = 0L;
                    flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                }
                codec.queueInputBuffer(
                        roomIndex,
                        offset,
                        size,
                        presentationTimeUs,
                        flags);
            }
        } catch (MediaCodec.CryptoException
                | IllegalStateException
                | NullPointerException e) {
            Log.e(TAG, "feedInputBuffer() Input occur exception: " + e);
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static boolean feedInputBuffer(
            MediaCodec codec,
            MediaUtils.Callback callback) {
        try {
            int roomIndex = codec.dequeueInputBuffer(TIME_OUT);
            if (roomIndex >= 0) {
                ByteBuffer room = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    room = codec.getInputBuffer(roomIndex);
                } else {
                    room = codec.getInputBuffers()[roomIndex];
                }
                InputBufferInfo info = new InputBufferInfo();
                if (room != null) {
                    room.clear();
                    if (callback != null) {
                        callback.onInputBuffer(roomIndex, room, info);
                    }
                }
                int flags = 0;
                if (info.size == 0) {
                    info.presentationTimeUs = 0L;
                    flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                }
                codec.queueInputBuffer(
                        roomIndex,
                        info.offset,
                        info.size,
                        info.presentationTimeUs,
                        flags);
            }
        } catch (MediaCodec.CryptoException
                | IllegalStateException
                | NullPointerException e) {
            Log.e(TAG, "feedInputBuffer() Input occur exception: " + e);
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static class InputBufferInfo {
        public int offset = 0;
        public int size = 0;
        public long presentationTimeUs = 0;
    }

    public interface Callback {
        void onFormatChanged(MediaFormat newMediaFormat);

        void onInputBuffer(int roomIndex, ByteBuffer room, InputBufferInfo info);

        void onOutputBuffer(int roomIndex, ByteBuffer room,
                            MediaCodec.BufferInfo roomInfo, int roomSize);
    }

    public static boolean drainOutputBuffer(
            MediaCodec codec,
            boolean render,
            MediaUtils.Callback callback) {
        return drainOutputBuffer(codec, render, false, callback);
    }

    public static int SLEEP_TIME = 90;

    public static long startTimeMs = 0;
    public static long paustTimeMs = 0;
    public static long progressTimeMs = 0;
    public static long variableValues = 0;
    public static int COUNT = 10;

    public static boolean drainOutputBuffer(
            MediaCodec codec,
            boolean render,
            boolean needToSleep,
            MediaUtils.Callback callback) {
        // 房间信息
        MediaCodec.BufferInfo roomInfo = new MediaCodec.BufferInfo();
        for (; ; ) {
            try {
                // 房间号
                int roomIndex = codec.dequeueOutputBuffer(roomInfo, TIME_OUT);
                switch (roomIndex) {
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        /*Log.d(TAG, "drainOutputBuffer() " +
                                "Output MediaCodec.INFO_OUTPUT_FORMAT_CHANGED");*/
                        if (callback != null) {
                            callback.onFormatChanged(codec.getOutputFormat());
                        }
                        break;
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        Log.d(TAG, "drainOutputBuffer() " +
                                "Output MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
                        break;
                    default:
                        break;
                }
                if (roomIndex < 0) {
                    break;
                }

                // 房间
                ByteBuffer room = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    room = codec.getOutputBuffer(roomIndex);
                } else {
                    room = codec.getOutputBuffers()[roomIndex];
                }
                // 房间大小
                int roomSize = roomInfo.size;
                if (room != null) {
                    // audio时room不为null
                    room.position(roomInfo.offset);
                    room.limit(roomInfo.offset + roomSize);
                    if (callback != null) {
                        callback.onOutputBuffer(roomIndex, room, roomInfo, roomSize);
                    }
                } else {
                    // video时room为null
                    if (needToSleep) {
                        if (callback != null) {
                            callback.onOutputBuffer(roomIndex, null, roomInfo, roomSize);
                        }
                    }
                }

                // 不要删除
                /*if (roomSize != 0) {
                    Image image = codec.getOutputImage(roomIndex);
                    // 需要输出什么类型
                    int outputImageFileType = FILE_TypeI420;
                    if (outputImageFileType != -1) {
                        switch (outputImageFileType) {
                            case FILE_TypeI420:
                                // *.yuv
                                // 需要保存的数据,把它写到输入流就行了
                                // getDataFromImage(image, COLOR_FormatI420);
                                break;
                            case FILE_TypeNV21:
                                // *.yuv
                                // getDataFromImage(image, COLOR_FormatNV21);
                                break;
                            case FILE_TypeJPEG:
                                // *.jpg
                                // compressToJpeg(fileName, image);
                                break;
                        }
                    }
                    image.close();
                }*/

                /*if (needToSleep) {
                    String elapsedTime = DateUtils.formatElapsedTime(
                            (roomInfo.presentationTimeUs1 / 1000) / 1000);
                    // roomInfo.presentationTimeUs1 / 1000(ms)
                    Log.d(TAG, "drainOutputBuffer() presentationTimeUs1: " +
                            roomInfo.presentationTimeUs1 / 1000);
                    Log.d(TAG, "drainOutputBuffer()        elapsedTime1: " +
                            elapsedTime);
                    // timeDifference(ms)
                    long timeDifference = System.currentTimeMillis()
                            - MediaUtils.startTimeMs
                            - MediaUtils.paustTimeMs
                            + MediaUtils.progressTimeMs
                            - MediaUtils.variableValues;
                    elapsedTime = DateUtils.formatElapsedTime(
                            timeDifference / 1000);
                    Log.i(TAG, "drainOutputBuffer() presentationTimeUs2: " +
                            timeDifference);
                    Log.i(TAG, "drainOutputBuffer()        elapsedTime2: " +
                            elapsedTime);

                    while (roomInfo.presentationTimeUs1 / 1000
                            > System.currentTimeMillis()
                            - MediaUtils.startTimeMs
                            - MediaUtils.paustTimeMs
                            + MediaUtils.progressTimeMs
                            - MediaUtils.variableValues) {
                        SystemClock.videoSleepTime(1);
                    }
                }*/

                if ((roomInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    Log.d(TAG, "drainOutputBuffer() " +
                            "Output MediaCodec.BUFFER_FLAG_CODEC_CONFIG");
                    codec.releaseOutputBuffer(roomIndex, render);
                    continue;
                }
                if ((roomInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "drainOutputBuffer() " +
                            "Output MediaCodec.BUFFER_FLAG_END_OF_STREAM");
                    return false;
                }

                codec.releaseOutputBuffer(roomIndex, render);
            } catch (IllegalStateException
                    | IllegalArgumentException
                    | NullPointerException e) {
                Log.e(TAG, "drainOutputBuffer() Output occur exception: " + e);
                e.printStackTrace();
                return false;
            }
        }// for(;;) end

        return true;
    }

    private static boolean isImageFormatSupported(Image image) {
        if (image == null) {
            return false;
        }
        switch (image.getFormat()) {
            case ImageFormat.NV21:
            case ImageFormat.YV12:
            case ImageFormat.YUV_420_888:
                return true;
            default:
                break;
        }
        return false;
    }

    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;
    public static final int FILE_TypeI420 = 1;
    public static final int FILE_TypeNV21 = 2;
    public static final int FILE_TypeJPEG = 3;

    // 对绝大多数设备和绝大多数视频编码格式，都可以解码得到NV21或I420格式帧数据
    private static byte[] getDataFromImage(Image image, int colorFormat) {
        if (image == null) {
            return null;
        }
        if (colorFormat != COLOR_FormatI420
                && colorFormat != COLOR_FormatNV21) {
            throw new IllegalArgumentException(
                    "only support COLOR_FormatI420 and COLOR_FormatNV21");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("Can't convert Image to byte array, format " +
                    image.getFormat());
        }
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        Log.i(TAG, "get data from " + planes.length + " planes");
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = width * height;
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height + 1;
                        outputStride = 2;
                    }
                    break;
                case 2:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = (int) (width * height * 1.25);
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height;
                        outputStride = 2;
                    }
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            if (DEBUG) {
                Log.v(TAG, "pixelStride " + pixelStride);
                Log.v(TAG, "rowStride " + rowStride);
                Log.v(TAG, "width " + width);
                Log.v(TAG, "height " + height);
                Log.v(TAG, "buffer size " + buffer.remaining());
            }
            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
            Log.v(TAG, "Finished reading data from plane " + i);
        }
        return data;
    }

    private static void compressToJpeg(String fileName, Image image) {
        if (TextUtils.isEmpty(fileName) || image == null) {
            return;
        }
        FileOutputStream outStream;
        try {
            outStream = new FileOutputStream(fileName);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create output file " + fileName, ioe);
        }
        Rect rect = image.getCropRect();
        YuvImage yuvImage = new YuvImage(
                getDataFromImage(image, COLOR_FormatNV21),
                ImageFormat.NV21,
                rect.width(),
                rect.height(),
                null);
        yuvImage.compressToJpeg(rect, 100, outStream);
        try {
            outStream.flush();
            outStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /***
     *
     * @param encoding MediaFormat改变后就能得到这个值,一般为2(pcm-encoding=2)
     * @return 一般为true
     */
    private static boolean isEncodingLinearPcm(int encoding) {
        return encoding == AudioFormat.ENCODING_PCM_8BIT
                || encoding == AudioFormat.ENCODING_PCM_16BIT
                || encoding == 0x80000000
                || encoding == 0x40000000
                || encoding == AudioFormat.ENCODING_PCM_FLOAT;
    }

    /***
     * ExoPlayer中的代码
     * @param channelCount
     * @param isInputPcm
     * @return
     */
    private static int getChannelConfig(int channelCount, boolean isInputPcm) {
        if (Build.VERSION.SDK_INT <= 28 && !isInputPcm) {
            // In passthrough mode the channel count used to configure the audio track doesn't
            // affect how
            // the stream is handled, except that some devices do overly-strict channel
            // configuration
            // checks. Therefore we override the channel count so that a known-working channel
            // configuration is chosen in all cases. See [Internal: b/29116190].
            if (channelCount == 7) {
                channelCount = 8;
            } else if (channelCount == 3 || channelCount == 4 || channelCount == 5) {
                channelCount = 6;
            }
        }

        // Workaround for Nexus Player not reporting support for mono passthrough.
        // (See [Internal: b/34268671].)
        if (Build.VERSION.SDK_INT <= 26
                && "fugu".equals(Build.DEVICE)
                && !isInputPcm
                && channelCount == 1) {
            channelCount = 2;
        }

        return getAudioTrackChannelConfig(channelCount);
    }

    private static int getAudioTrackChannelConfig(int channelCount) {
        switch (channelCount) {
            case 1:
                return AudioFormat.CHANNEL_OUT_MONO;
            case 2:
                return AudioFormat.CHANNEL_OUT_STEREO;
            case 3:
                return AudioFormat.CHANNEL_OUT_STEREO
                        | AudioFormat.CHANNEL_OUT_FRONT_CENTER;
            case 4:
                return AudioFormat.CHANNEL_OUT_QUAD;
            case 5:
                return AudioFormat.CHANNEL_OUT_QUAD
                        | AudioFormat.CHANNEL_OUT_FRONT_CENTER;
            case 6:
                return AudioFormat.CHANNEL_OUT_5POINT1;// 252
            case 7:
                return AudioFormat.CHANNEL_OUT_5POINT1
                        | AudioFormat.CHANNEL_OUT_BACK_CENTER;
            case 8:
                if (Build.VERSION.SDK_INT >= 23) {
                    return AudioFormat.CHANNEL_OUT_7POINT1_SURROUND;
                } else if (Build.VERSION.SDK_INT >= 21) {
                    // Equal to AudioFormat.CHANNEL_OUT_7POINT1_SURROUND,
                    // which is hidden before Android M.
                    return AudioFormat.CHANNEL_OUT_5POINT1
                            | AudioFormat.CHANNEL_OUT_SIDE_LEFT
                            | AudioFormat.CHANNEL_OUT_SIDE_RIGHT;
                } else {
                    // 8 ch output is not supported before Android L.
                    return AudioFormat.CHANNEL_INVALID;
                }
            default:
                return AudioFormat.CHANNEL_INVALID;
        }
    }

    private static int decideChannelConfig(
            int channelCount, boolean passthrough, String mimeType) {
        int channelConfig;
        switch (channelCount) {
            case 1:
                channelConfig = AudioFormat.CHANNEL_OUT_MONO;
                break;
            case 2:
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                break;
            case 3:
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO
                        | AudioFormat.CHANNEL_OUT_FRONT_CENTER;
                break;
            case 4:
                channelConfig = AudioFormat.CHANNEL_OUT_QUAD;
                break;
            case 5:
                if (mimeType.equals(MimeTypes.AUDIO_AC3)
                        || mimeType.equals(MimeTypes.AUDIO_E_AC3)
                        || mimeType.equals(MimeTypes.AUDIO_AC4)
                        || mimeType.equals(MimeTypes.AUDIO_E_AC3_JOC)
                        || mimeType.equals(MimeTypes.AUDIO_AC4_JOC)) {
                    channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
                } else {
                    channelConfig = AudioFormat.CHANNEL_OUT_QUAD
                            | AudioFormat.CHANNEL_OUT_FRONT_CENTER;
                }
                break;
            case 6:
                channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
                break;
            case 7:
                channelConfig = AudioFormat.CHANNEL_OUT_5POINT1
                        | AudioFormat.CHANNEL_OUT_BACK_CENTER;
                break;
            case 8:
                channelConfig = Build.VERSION.SDK_INT < 23
                        ? AudioFormat.CHANNEL_OUT_7POINT1
                        : AudioFormat.CHANNEL_OUT_7POINT1_SURROUND;
                break;
            default:
                throw new IllegalArgumentException("Unsupported channel count: " + channelCount);
        }

        // Workaround for overly strict channel configuration checks on nVidia Shield.
        if (Build.VERSION.SDK_INT <= 23
                && "foster".equals(Build.DEVICE)
                && "NVIDIA".equals(Build.MANUFACTURER)) {
            switch (channelCount) {
                case 7:
                    channelConfig = Build.VERSION.SDK_INT < 23
                            ? AudioFormat.CHANNEL_OUT_7POINT1
                            : AudioFormat.CHANNEL_OUT_7POINT1_SURROUND;
                    break;
                case 3:
                case 5:
                    channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
                    break;
                default:
                    break;
            }
        }

        // Workaround for Nexus Player not reporting support for mono passthrough.
        // (See [Internal: b/34268671].)
        if (Build.VERSION.SDK_INT <= 25
                && "fugu".equals(Build.DEVICE)
                && passthrough
                && channelCount == 1) {
            channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
        }

        return channelConfig;
    }

    public static void printMediaCodecInfos(MediaCodecInfo[] mediaCodecInfos,
                                            String mimeType) {
        for (MediaCodecInfo mediaCodecInfo : mediaCodecInfos) {
            MediaCodecInfo.CodecCapabilities codecCapabilities =
                    mediaCodecInfo.getCapabilitiesForType(mimeType);
            MediaCodecInfo.VideoCapabilities videoCapabilities =
                    codecCapabilities.getVideoCapabilities();
            MediaCodecInfo.AudioCapabilities audioCapabilities =
                    codecCapabilities.getAudioCapabilities();
            /***
             videoCapabilities不为null时,audioCapabilities为null
             videoCapabilities为null时,audioCapabilities不为null
             mediaCodecInfo.getName(): OMX.MTK.VIDEO.ENCODER.AVC
             mediaCodecInfo.getSupportedTypes(): [video/avc]
             videoCapabilities.getSupportedWidths(): [128, 1920]
             videoCapabilities.getSupportedHeights(): [96, 1072]
             videoCapabilities.getSupportedFrameRates(): [0, 960]
             videoCapabilities.getBitrateRange(): [1, 14000000]
             videoCapabilities.profileLevels:
             codecCapabilities.colorFormats:

             audioCapabilities.getSupportedSampleRates():
             [8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000]
             audioCapabilities.getBitrateRange(): [8000, 510000]
             audioCapabilities.getMaxInputChannelCount(): 6(声道)
             */
            StringBuilder builder = new StringBuilder(512);
            builder.append("Encoder '").append(mediaCodecInfo.getName()).append('\'')
                    .append("\n  supported : ")
                    .append(Arrays.toString(mediaCodecInfo.getSupportedTypes()));
            if (videoCapabilities != null) {
                builder.append("\n  Video capabilities:")
                        .append("\n  Widths: ").append(videoCapabilities.getSupportedWidths())
                        .append("\n  Heights: ").append(videoCapabilities.getSupportedHeights())
                        .append("\n  Frame Rates: ").append(videoCapabilities
                        .getSupportedFrameRates())
                        .append("\n  Bitrate: ").append(videoCapabilities.getBitrateRange());
                if (VIDEO_MIME.equals(mimeType)) {
                    MediaCodecInfo.CodecProfileLevel[] levels = codecCapabilities.profileLevels;

                    builder.append("\n  Profile-levels: ");
                    /*for (MediaCodecInfo.CodecProfileLevel level : levels) {
                        builder.append("\n  ").append(Utils.avcProfileLevelToString(level));
                    }*/
                }
                builder.append("\n  Color-formats: ");
                for (int c : codecCapabilities.colorFormats) {
                    builder.append("\n  ").append(printColorFormat(c));
                }
            }

            if (audioCapabilities != null) {
                builder.append("\n Audio capabilities:")
                        .append("\n Sample Rates: ").append(Arrays.toString(audioCapabilities
                        .getSupportedSampleRates()))
                        .append("\n Bit Rates: ").append(audioCapabilities.getBitrateRange())
                        .append("\n Max channels: ").append(audioCapabilities
                        .getMaxInputChannelCount());
            }
            Log.i(TAG, builder.toString());
        }
    }

    /***
     *
     * @param colorFormat codecCapabilities.colorFormats
     * @return 0x7f000200
    COLOR_FormatSurface
    0x7f000200
    COLOR_FormatYUV420Flexible
    COLOR_FormatYUV420Planar
    COLOR_Format16bitRGB565
    COLOR_Format24bitRGB888
    COLOR_Format32bitARGB8888
    0x7f000300
    COLOR_Format32bitBGRA8888
     */
    public static String printColorFormat(int colorFormat) {
        SparseArray<String> colorFormats = new SparseArray<>();
        colorFormats.clear();
        initColorFormatFields(colorFormats);

        int index = colorFormats.indexOfKey(colorFormat);
        if (index >= 0) {
            return colorFormats.valueAt(index);
        }
        return "0x" + Integer.toHexString(colorFormat);
    }

    private static void initColorFormatFields(SparseArray<String> colorFormats) {
        // COLOR_
        Field[] fields = MediaCodecInfo.CodecCapabilities.class.getFields();
        for (Field field : fields) {
            if ((field.getModifiers() & (Modifier.STATIC | Modifier.FINAL)) == 0) {
                continue;
            }
            String name = field.getName();
            if (!TextUtils.isEmpty(name) && name.startsWith("COLOR_")) {
                try {
                    int value = field.getInt(null);
                    colorFormats.put(value, name);
                } catch (IllegalAccessException e) {
                    // ignored
                }
            }
        }

    }

    public static final String VIDEO_SIZE_360 = "Size360";
    public static final String VIDEO_SIZE_720 = "Size720";
    public static final String VIDEO_SIZE_1080 = "Size1080";
    public static final String VIDEO_QUALITY_HIGH = "High";
    public static final String VIDEO_QUALITY_MIDDLE = "Middle";
    public static final String VIDEO_QUALITY_LOW = "Low";

    public static int getBitRate(String defaultVideoSize) {
        int bitRate = 10 * 1000 * 1000;
        String keyVideoSize = null;
        String quality = null;
        if (TextUtils.equals(keyVideoSize, VIDEO_SIZE_1080)) {
            if (TextUtils.equals(quality, VIDEO_QUALITY_HIGH)) {
                bitRate = 10000000;
            } else if (TextUtils.equals(quality, VIDEO_QUALITY_MIDDLE)) {
                bitRate = 3300000;
            } else if (TextUtils.equals(quality, VIDEO_QUALITY_LOW)) {
                bitRate = 1250000;
            }
        } else if (TextUtils.equals(keyVideoSize, VIDEO_SIZE_720)) {
            if (TextUtils.equals(quality, VIDEO_QUALITY_HIGH)) {
                bitRate = 8000000;
            } else if (TextUtils.equals(quality, VIDEO_QUALITY_MIDDLE)) {
                bitRate = 2700000;
            } else if (TextUtils.equals(quality, VIDEO_QUALITY_LOW)) {
                bitRate = 1000000;
            }
        } else if (TextUtils.equals(keyVideoSize, VIDEO_SIZE_360)) {
            if (TextUtils.equals(quality, VIDEO_QUALITY_HIGH)) {
                bitRate = 1200000;
            } else if (TextUtils.equals(quality, VIDEO_QUALITY_MIDDLE)) {
                bitRate = 400000;
            } else if (TextUtils.equals(quality, VIDEO_QUALITY_LOW)) {
                bitRate = 150000;
            }
        }
        return bitRate;
    }

    /***
     添加ADTS头到一帧的音频数据上
     freqIdx和chanCfg需要根据实际的采样率和声道数决定

     AAC的数据如果需要直接播放,
     则需要添加ADT头部,
     在每一帧AAC数据的前面添加ADTS,
     如果要将AAC合入到MP4中则不需要添加ADT头.
     下面的值在PCM编码成AAC,添加ADTS头时需要参照.
     int profile = 2;  // AAC LC
     int freqIdx = 11; // 8KHz
     int chanCfg = 1;  // CPE
     意思: AAC的格式对应2,8K的采样率对应参数是11,单声道是1
     采样率freqIdx参数：
     0: 96000 Hz
     1: 88200 Hz
     2: 64000 Hz
     3: 48000 Hz
     4: 44100 Hz
     5: 32000 Hz
     6: 24000 Hz
     7: 22050 Hz
     8: 16000 Hz
     9: 12000 Hz
     10: 11025 Hz
     11: 8000 Hz
     12: 7350 Hz
     13: Reserved
     14: Reserved
     15: frequency is written explictly
     声道数chanCfg参数： 
     0: Defined in AOT Specifc Config
     1: 1 channel: front-center
     2: 2 channels: front-left, front-right
     3: 3 channels: front-center, front-left, front-right
     4: 4 channels: front-center, front-left, front-right, back-center
     5: 5 channels: front-center, front-left, front-right, back-left, back-right
     6: 6 channels: front-center, front-left, front-right, back-left, back-right, LFE-channel
     7: 8 channels: front-center, front-left, front-right, side-left, side-right, back-left,
     back-right, LFE-channel
     8-15: Reserved

     @param packet        byte数组,长度为(7 + 编码后的aac数据长度)
     @param packetLength (7 + 编码后的aac数据长度)
     */
    public static void addADTStoFrame(byte[] packet, int packetLength) {
        int audioObjectType = 2;  // AAC LC
        // 如果上面的sampleRateInHz,channelConfig改变的话,下面的值也要相应的改变
        int sampleRateIndex = 4;  // 44.1KHz
        int channelConfig = 2;    // CPE

        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        // 解决ios不能播放问题
        packet[1] = (byte) 0xF1;// (byte) 0xF9;
        packet[2] = (byte) (((audioObjectType - 1) << 6) + (sampleRateIndex << 2) +
                (channelConfig >> 2));
        packet[3] = (byte) (((channelConfig & 3) << 6) + (packetLength >> 11));
        packet[4] = (byte) ((packetLength & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLength & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;

        /***
         不变的量: packet[0] packet[1] packet[2] packet[3] packet[6]
         packet[0]: -1 packet[1]: -15 packet[2]: 80 packet[3]: -128 packet[4]: 64 packet[5]: 31
         packet[6]: -4
         packet[0]: -1 packet[1]: -15 packet[2]: 80 packet[3]: -128 packet[4]: 64 packet[5]: 95
         packet[6]: -4
         packet[0]: -1 packet[1]: -15 packet[2]: 80 packet[3]: -128 packet[4]: 63 packet[5]: -97
         packet[6]: -4
         packet[0]: -1 packet[1]: -15 packet[2]: 80 packet[3]: -128 packet[4]: 66 packet[5]: -33
         packet[6]: -4
         */
        /*Log.d(TAG, "addADTStoFrame() " +
                " packet[0]: " + packet[0] +
                " packet[1]: " + packet[1] +
                " packet[2]: " + packet[2] +
                " packet[3]: " + packet[3] +
                " packet[4]: " + packet[4] +
                " packet[5]: " + packet[5] +
                " packet[6]: " + packet[6]);*/
    }

    public static byte[] buildAacAudioSpecificConfig() {
        // 对应addADTStoFrame方法中的相同参数
        // audioObjectType为固定值
        int audioObjectType = 2;
        int sampleRateIndex = 4;
        int channelConfig = 2;

        byte[] specificConfig = new byte[2];
        specificConfig[0] = (byte) (((audioObjectType << 3) & 0xF8) | ((sampleRateIndex >> 1) &
                0x07));
        specificConfig[1] = (byte) (((sampleRateIndex << 7) & 0x80) | ((channelConfig << 3) &
                0x78));
        return specificConfig;
    }

    public static byte[] buildAacAudioSpecificConfig(int sampleRateIndex, int channelConfigIndex) {
        // 对应addADTStoFrame方法中的相同参数
        // audioObjectType为固定值
        int audioObjectType = 2;

        byte[] specificConfig = new byte[2];
        specificConfig[0] = (byte) (((audioObjectType << 3) & 0xF8) | ((sampleRateIndex >> 1) &
                0x07));
        specificConfig[1] = (byte) (((sampleRateIndex << 7) & 0x80) | ((channelConfigIndex << 3) &
                0x78));
        return specificConfig;
    }

    public static void setCsdBuffers(MediaFormat format, List<byte[]> csdBuffers) {
        for (int i = 0; i < csdBuffers.size(); i++) {
            format.setByteBuffer("csd-" + i, ByteBuffer.wrap(csdBuffers.get(i)));
        }
    }

    /***
     加入wav文件头
     */
    public static void addWaveHeaderToPcmFile(FileOutputStream wavOS,
                                              long pcmSize,
                                              long sampleRateInHz,
                                              int channelCount,
                                              long bitRate) throws IOException {
        long wavSize = pcmSize + 36;
        byte[] header = new byte[44];
        header[0] = 'R';// RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (wavSize & 0xff);
        header[5] = (byte) ((wavSize >> 8) & 0xff);
        header[6] = (byte) ((wavSize >> 16) & 0xff);
        header[7] = (byte) ((wavSize >> 24) & 0xff);
        header[8] = 'W';// WAVE
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';// 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;// 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;// format = 1
        header[21] = 0;
        header[22] = (byte) channelCount;
        header[23] = 0;
        header[24] = (byte) (sampleRateInHz & 0xff);
        header[25] = (byte) ((sampleRateInHz >> 8) & 0xff);
        header[26] = (byte) ((sampleRateInHz >> 16) & 0xff);
        header[27] = (byte) ((sampleRateInHz >> 24) & 0xff);
        header[28] = (byte) (bitRate & 0xff);
        header[29] = (byte) ((bitRate >> 8) & 0xff);
        header[30] = (byte) ((bitRate >> 16) & 0xff);
        header[31] = (byte) ((bitRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        header[34] = 16;// bits per sample
        header[35] = 0;
        header[36] = 'd';// data
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (pcmSize & 0xff);
        header[41] = (byte) ((pcmSize >> 8) & 0xff);
        header[42] = (byte) ((pcmSize >> 16) & 0xff);
        header[43] = (byte) ((pcmSize >> 24) & 0xff);
        wavOS.write(header, 0, 44);
    }

    /***
     使用MediaMetadataRetriever获取视频的第一帧作为缩略图
     //获取第一帧原尺寸图片
     mmrc.getFrameAtTime();
     //获取指定位置的原尺寸图片 注意这里传的timeUs是微秒
     mmrc.getFrameAtTime(timeUs, option);
     //获取指定位置指定宽高的缩略图
     mmrc.getScaledFrameAtTime(
     timeUs, MediaMetadataRetrieverCompat.OPTION_CLOSEST, width, height);
     //获取指定位置指定宽高并且旋转的缩略图
     mmrc.getScaledFrameAtTime(
     timeUs, MediaMetadataRetrieverCompat.OPTION_CLOSEST, width, height, rotate);
     OPTION_CLOSEST    在给定的时间，检索最近一个帧,这个帧不一定是关键帧。
     OPTION_CLOSEST_SYNC    在给定的时间，检索最近一个同步与数据源相关联的的帧（关键帧）。
     OPTION_NEXT_SYNC  在给定时间之后检索一个同步与数据源相关联的关键帧。
     OPTION_PREVIOUS_SYNC   顾名思义，同上
     */
    private static Bitmap getVideoThumb(String path) {
        MediaMetadataRetriever media = new MediaMetadataRetriever();
        media.setDataSource(path);
        return media.getFrameAtTime();
    }

    /***
     * 使用ThumbnailUtils获取视频的第一帧作为缩略图
     * 获取视频的缩略图
     * 先通过ThumbnailUtils来创建一个视频的缩略图，然后再利用ThumbnailUtils来生成指定大小的缩略图。
     * 如果想要的缩略图的宽和高都小于MICRO_KIND，则类型要使用MICRO_KIND作为kind的值，这样会节省内存。
     * @param videoPath 视频的路径
     * @param width 指定输出视频缩略图的宽度
     * @param height 指定输出视频缩略图的高度度
     * @param kind 参照MediaStore.Images(Video).Thumbnails类中的常量MINI_KIND和MICRO_KIND。
     *            其中，MINI_KIND: 512 x 384，MICRO_KIND: 96 x 96
     * @return 指定大小的视频缩略图
     */
    private static Bitmap getVideoThumbnail(String videoPath, int width, int height, int kind) {
        Bitmap bitmap = null;
        // 获取视频的缩略图
        //調用ThumbnailUtils類的靜態方法createVideoThumbnail獲取視頻的截圖；
        bitmap = ThumbnailUtils.createVideoThumbnail(videoPath, kind);
        if (bitmap != null) {
            //調用ThumbnailUtils類的靜態方法extractThumbnail將原圖片（即上方截取的圖片）轉化為指定大小；
            bitmap = ThumbnailUtils.extractThumbnail(bitmap, width, height,
                    ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
        }
        return bitmap;
    }

    /***
     android硬编出来的第一帧数据就是sps和pps
     SPS(Sequence Parameter Set：序列参数集)
     PPS(Picture  Parameter Set：图像参数集)
     sps: 0, 0, 0, 1, 39, 66, -32, 30, -115, 104, 10, 3, -38, 108, -128,
     0, 0, 3, 0, -128, 0, 0, 12, -121, -118, 17, 80
     pps: 0, 0, 0, 1, 40, -50, 4, 73, 32
     0, 0, 0, 1是startcode
     获取到了sps和pps存起来碰到关键帧给他加上去就好了
     */
    private void onGetYuvFrame(MediaCodec encoder, byte[] yuvData) {
        // feed the encoder with yuv frame, got the encoded 264 es stream.
        ByteBuffer room = null;
        byte[] mediaHead = null;
        byte[] streamData = null;

        int roomIndex = encoder.dequeueInputBuffer(-1);
        if (roomIndex >= 0) {
            room = encoder.getInputBuffer(roomIndex);
            room.clear();
            room.put(yuvData, 0, yuvData.length);
            long presentationTimeUs = System.nanoTime() / 1000;
            encoder.queueInputBuffer(
                    roomIndex, 0, yuvData.length, presentationTimeUs, 0);
        }

        for (; ; ) {
            MediaCodec.BufferInfo roomInfo = new MediaCodec.BufferInfo();
            roomIndex = encoder.dequeueOutputBuffer(roomInfo, 0);
            if (roomIndex >= 0) {
                room = encoder.getOutputBuffer(roomIndex);
                int roomSize = roomInfo.size;
                byte[] encodedData = new byte[roomSize];
                room.get(encodedData);

                if (mediaHead == null) {
                    streamData = new byte[roomSize];
                } else {
                    streamData = new byte[roomSize + mediaHead.length];
                }
                if (mediaHead == null) {
                    ByteBuffer spsPpsBuffer = ByteBuffer.wrap(encodedData);
                    if (spsPpsBuffer.getInt() == 0x00000001) {
                        mediaHead = new byte[roomSize];
                        System.arraycopy(
                                encodedData, 0, mediaHead, 0, roomSize);
                    } else {
                        Log.e(TAG, "not found media head.");
                    }
                }

                if (mediaHead != null
                        // 判断该帧是否是关键帧(关键帧的数组长度比一般帧要长很多)
                        && ((encodedData[4] == 0x65) || (encodedData[4] == 0x25))) { // key
                    // frame
                    // 编码器生成关键帧时只有
                    // 00 00 00
                    // 01 65
                    // 没有pps sps 要加上
                    Log.i("Ifarme", "关键帧长度=" + encodedData.length + "*****"
                            + encodedData[4] + "**outByte长度**" + streamData.length
                            + "****" + mediaHead.length);
                    System.arraycopy(
                            mediaHead, 0, streamData, 0, mediaHead.length);
                    System.arraycopy(
                            encodedData, 0, streamData, mediaHead.length, encodedData.length);
                    if (streamData.length > 0) {
                        /*int writeStreamVideoData =
                                myNative.peekInstance().WriteStreamVideoData(
                                        streamData, streamData.length, 1);*/
                    }
                } else {
                    streamData = encodedData;
                    if (streamData.length > 0) {
                        /*int writeStreamVideoData =
                                myNative.peekInstance().WriteStreamVideoData(
                                        streamData, streamData.length, 0);*/
                    }
                }
                encoder.releaseOutputBuffer(roomIndex, false);
            } else {
                break;
            }
        }
    }

    /***
     * 从socket读byte数组
     *
     * @param in
     * @param length
     * @return
     */
    public static byte[] readBytes(InputStream in, long length) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read = 0;
        while (read < length) {
            int cur = 0;
            try {
                cur = in.read(buffer, 0, (int) Math.min(1024, length - read));
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (cur < 0) {
                break;
            }
            read += cur;
            baos.write(buffer, 0, cur);
        }
        return baos.toByteArray();
    }

    public static byte[] NV21ToI420(byte[] data, int width, int height) {
        byte[] ret = new byte[data.length];
        int total = width * height;

        ByteBuffer bufferY = ByteBuffer.wrap(ret, 0, total);
        ByteBuffer bufferU = ByteBuffer.wrap(ret, total, total / 4);
        ByteBuffer bufferV = ByteBuffer.wrap(ret, total + total / 4, total / 4);

        bufferY.put(data, 0, total);
        for (int i = total; i < data.length; i += 2) {
            bufferV.put(data[i]);
            bufferU.put(data[i + 1]);
        }

        return ret;
    }

    public static byte[] I420ToNV21(byte[] data, int width, int height) {
        byte[] ret = new byte[data.length];
        int total = width * height;

        ByteBuffer bufferY = ByteBuffer.wrap(ret, 0, total);
        ByteBuffer bufferV = ByteBuffer.wrap(ret, total, total / 4);
        ByteBuffer bufferU = ByteBuffer.wrap(ret, total + total / 4, total / 4);

        bufferY.put(data, 0, total);
        for (int i = 0; i < total / 4; i += 1) {
            bufferV.put(data[total + i]);
            bufferU.put(data[i + total + total / 4]);
        }

        return ret;
    }

    private void prepareEncoder() throws IOException {

        //MediaFormat这个类是用来定义视频格式相关信息的
        //video/avc,这里的avc是高级视频编码Advanced Video Coding
        //mWidth和mHeight是视频的尺寸，这个尺寸不能超过视频采集时采集到的尺寸，否则会直接crash
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME, 1, 1);
        //COLOR_FormatSurface这里表明数据将是一个graphicbuffer元数据
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        //设置码率，通常码率越高，视频越清晰，但是对应的视频也越大，这个值我默认设置成了2000000，也就是通常所说的2M
        format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE);
        //设置帧率，通常这个值越高，视频会显得越流畅，一般默认我设置成30，你最低可以设置成24，不要低于这个值，低于24会明显卡顿
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        //IFRAME_INTERVAL是指的帧间隔，这是个很有意思的值，它指的是，关键帧的间隔时间。通常情况下，你设置成多少问题都不大。
        //比如你设置成10，那就是10秒一个关键帧。但是，如果你有需求要做视频的预览，那你最好设置成1
        //因为如果你设置成10，那你会发现，10秒内的预览都是一个截图
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        Log.d(TAG, "created video format: " + format);
        //创建一个MediaCodec的实例
        //mEncoder = MediaCodec.createEncoderByType(VIDEO_MIME);
        //定义这个实例的格式，也就是上面我们定义的format，其他参数不用过于关注
        //mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        //这一步非常关键，它设置的，是MediaCodec的编码源，也就是说，我要告诉mEncoder，你给我解码哪些流。
        //很出乎大家的意料，MediaCodec并没有要求我们传一个流文件进去，而是要求我们指定一个surface
        //而这个surface，其实就是我们在上一讲MediaProjection中用来展示屏幕采集数据的surface
        //mSurface = mEncoder.createInputSurface();
    }

}

/***
 采样率44.1KHz,双声道,16Bit
 重采样为
 采样率8KHz,单声道,16Bit
 https://github.com/hutm/JSSRC
 只需要SSRC，I0Bessel，SplitRadixFft这三个类就可以实现转换采样率的功能

 MediaCodec 有两种方式触发输出关键帧，
 一是由配置时设置的 KEY_FRAME_RATE和KEY_I_FRAME_INTERVAL参数自动触发，
 二是运行过程中通过 setParameters 手动触发输出关键帧。
 自动触发实际是按照帧数触发的，
 例如设置帧率为 20 fps，关键帧间隔为 1s ，
 那就会每 20桢输出一个关键帧，一旦实际帧率低于配置帧率，
 那就会导致关键帧间隔时间变长。
 由于 MediaCodec 启动后就不能修改配置帧率/关键帧间隔了，
 所以如果希望改变关键帧间隔帧数，就必须重启编码器。
 手动触发输出关键帧：
 if (System.currentTimeMillis() - timeStamp >= 1000) {//1000毫秒后，设置参数
 timeStamp = System.currentTimeMillis();
 if (Build.VERSION.SDK_INT >= 23) {
 Bundle params = new Bundle();
 params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
 mMediaCodec.setParameters(params);
 }
 }
 关键帧踩坑
 有时候你会发现自动触发关键帧方式失效了
 经排查发现真正的原因是在于视频的输入源，如果是通过Camera的PreviewCallback的方式来获取视频数据再喂给MediaCodec的方式是无法控制输出关键帧的数量的。
 发现当选择支持颜色格式为yuv420p的编码器时，KEY_I_FRAME_INTERVAL 设置无效；
 选择支持yuv420sp的编码器时，KEY_I_FRAME_INTERVAL 设置有效；

 想要控制输出输出关键帧数量就必须通过调用MediaCodec.createInputSurface()
 方法获取输入Surface，再通过Opengl渲染后喂给MediaCodec才能真正控制关键帧的数量。
 //判断输出数据是否为关键帧的方法：
 boolean keyFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
 部分机型MediaCodec.dequeueOutputBuffer一直报IllegalStateException
 部分机型会一直卡在MediaCodec.INFO_TRY_AGAIN_LATER中，有的原因也是因为这个
 该机型硬解码最大配置分辨率低于当前视频流的分辨率
 关于 level、profile设置
 由于视频编码后显示的数据质量偏低，所以需要调整质量。这个时候需要在这个设置level、profile
 Profile是对视频压缩特性的描述（CABAC呀、颜色采样数等等）。
 Level是对视频本身特性的描述（码率、分辨率、fps）。
 简单来说，Profile越高，就说明采用了越高级的压缩特性。
 Level越高，视频的码率、分辨率、fps越高
 // 不支持设置Profile和Level，而应该采用默认设置
 mediaFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
 mediaFormat.setInteger("level", MediaCodecInfo.CodecProfileLevel.AVCLevel41); // Level 4.1

 DRM是英文Digital rights management的缩写,可以理解为版权保护
 public static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
 //获取sessionId
 MediaDrm mediaDrm = new MediaDrm();
 String sessionId = mediaDrm.openSession();
 //创建MediaCrypto
 sessionId 是串联MediaDrm和MediaCrypto的关键
 MediaCrypto ctypto = new MediaCrypto(WIDEVINE_UUID, sessionId)
 //用cypto对象来进行解密
 MediaCodec codec = new MediaCodec("xxxx")
 codec.configure(..,...,ctypto)
 注意的是license并不需要在configure之前获取，可以稍后再进行
 //网络连接
 byte[] license = HttpUrlConnection.connect().......
 mediaDrm.provideKeyResponse(xxx,license);
 所有工作结束，视频可以正常播放了

 Android Tunnel Mode
 https://blog.csdn.net/yingmuliuchuan/article/details/81807512
 https://developer.amazon.com/zh/docs/fire-tv/4k-tunnel-mode-playback.html

 Audio Object Types
 MPEG-4 Audio Object Types:

 0: Null
 1: AAC Main
 2: AAC LC (Low Complexity)
 3: AAC SSR (Scalable Sample Rate)
 4: AAC LTP (Long Term Prediction)
 5: SBR (Spectral Band Replication)
 6: AAC Scalable
 7: TwinVQ
 8: CELP (Code Excited Linear Prediction)
 9: HXVC (Harmonic Vector eXcitation Coding)
 10: Reserved
 11: Reserved
 12: TTSI (Text-To-Speech Interface)
 13: Main Synthesis
 14: Wavetable Synthesis
 15: General MIDI
 16: Algorithmic Synthesis and Audio Effects
 17: ER (Error Resilient) AAC LC
 18: Reserved
 19: ER AAC LTP
 20: ER AAC Scalable
 21: ER TwinVQ
 22: ER BSAC (Bit-Sliced Arithmetic Coding)
 23: ER AAC LD (Low Delay)
 24: ER CELP
 25: ER HVXC
 26: ER HILN (Harmonic and Individual Lines plus Noise)
 27: ER Parametric
 28: SSC (SinuSoidal Coding)
 29: PS (Parametric Stereo)
 30: MPEG Surround
 31: (Escape value)
 32: Layer-1
 33: Layer-2
 34: Layer-3
 35: DST (Direct Stream Transfer)
 36: ALS (Audio Lossless)
 37: SLS (Scalable LosslesS)
 38: SLS non-core
 39: ER AAC ELD (Enhanced Low Delay)
 40: SMR (Symbolic Music Representation) Simple
 41: SMR Main
 42: USAC (Unified Speech and Audio Coding) (no SBR)
 43: SAOC (Spatial Audio Object Coding)
 44: LD MPEG Surround
 45: USAC

 Sampling Frequencies
 There are 13 supported frequencies:

 0: 96000 Hz
 1: 88200 Hz
 2: 64000 Hz
 3: 48000 Hz
 4: 44100 Hz
 5: 32000 Hz
 6: 24000 Hz
 7: 22050 Hz
 8: 16000 Hz
 9: 12000 Hz
 10: 11025 Hz
 11: 8000 Hz
 12: 7350 Hz
 13: Reserved
 14: Reserved
 15: frequency is written explictly

 Channel Configurations
 These are the channel configurations:

 0: Defined in AOT Specifc Config
 1: 1 channel: front-center
 2: 2 channels: front-left, front-right
 3: 3 channels: front-center, front-left, front-right
 4: 4 channels: front-center, front-left, front-right, back-center
 5: 5 channels: front-center, front-left, front-right, back-left, back-right
 6: 6 channels: front-center, front-left, front-right, back-left, back-right, LFE-channel
 7: 8 channels: front-center, front-left, front-right, side-left, side-right, back-left,
 back-right, LFE-channel
 8-15: Reserved

 */