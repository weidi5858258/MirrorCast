//
// Created by root on 2021/8/11.
//

#include <pthread.h>
#include <assert.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <limits.h>

#include "include/Log.h"
#include "MyJni.h"
#include "MediaClient.h"
#include "MediaData.h"
#include "MediaCodec.h"


#define LOG "player_alexander"

/***
 解码过程:
 1. setSurface(JNIEnv *env, jobject surface_obj)
 2. AMediaFormat *format = AMediaFormat_new();
 3. start(...)
 4. feedInputBufferAndDrainOutputBuffer(...)
 5. release()

 编码过程:

 编码过程(录制屏幕):
 */

static int TIME_OUT = 10000;

bool ONLY_OUTPUT_KEY_FRAME = false;
bool isRecording = false;

// window1 或者 竖屏参数
static int which_client1 = 1;
static ANativeWindow *surface1;
static AMediaFormat *format1;
static AMediaCodec *codec1;
char mime1[50];
char codecName1[50];
int mimeLength1 = 0;
int codecNameLength1 = 0;
int width1 = 0;
int height1 = 0;
int orientation1 = 0;
bool isPlaying1 = false;
uint8_t *sps_pps_portrait1 = nullptr;// 投屏时使用这个表示竖屏sps_pps
uint8_t *sps_pps_landscape1 = nullptr;
ssize_t sps_pps_size_portrait1 = 0;
ssize_t sps_pps_size_landscape1 = 0;
static uint8_t ORIENTATION_PORTRAIT[5];
static bool isKeyFrameWrite1 = false;

// window2 或者 横屏参数
static int which_client2 = 2;
static ANativeWindow *surface2;
static AMediaFormat *format2;
static AMediaCodec *codec2;
char mime2[50];
char codecName2[50];
int mimeLength2 = 0;
int codecNameLength2 = 0;
int width2 = 0;
int height2 = 0;
int orientation2 = 0;
bool isPlaying2 = false;
uint8_t *sps_pps_portrait2 = nullptr;
uint8_t *sps_pps_landscape2 = nullptr;// 投屏时使用这个表示横屏sps_pps
ssize_t sps_pps_size_portrait2 = 0;
ssize_t sps_pps_size_landscape2 = 0;
static uint8_t ORIENTATION_LANDSCAPE[5];
static bool isKeyFrameWrite2 = false;

extern pthread_mutex_t mutex1;

static void int2Bytes(uint8_t *frame, int length) {
    frame[0] = (uint8_t) length;
    frame[1] = (uint8_t) (length >> 8);
    frame[2] = (uint8_t) (length >> 16);
    frame[3] = (uint8_t) (length >> 24);
}

static void getSpsPps() {
    LOGI("getSpsPps() start\n");
    bool isGettingSpsPps = true;
    AMediaCodecBufferInfo roomInfo;
    size_t out_size = 0;
    AMediaCodec *codec;
    if (orientation1 == 1) {
        codec = codec1;
        createPortraitVirtualDisplay();
    } else if (orientation2 == 2) {
        codec = codec2;
        createLandscapeVirtualDisplay();
    }
    LOGI("getSpsPps() 1\n");
    while (isGettingSpsPps) {
        for (;;) {
            ssize_t roomIndex = AMediaCodec_dequeueOutputBuffer(codec, &roomInfo, TIME_OUT);
            if (roomIndex < 0) {
                break;
            }
            uint8_t *room = AMediaCodec_getOutputBuffer(codec, (size_t) roomIndex, &out_size);
            if (room != nullptr && (roomInfo.flags & 2)) {// 配置帧
                isGettingSpsPps = false;
                if (orientation1 == 1) {
                    sps_pps_portrait1 = (uint8_t *) malloc(roomInfo.size + 4);
                    memset(sps_pps_portrait1, 0, roomInfo.size);
                    int2Bytes(sps_pps_portrait1, roomInfo.size);
                    memcpy(sps_pps_portrait1 + 4, room, roomInfo.size);
                    sps_pps_size_portrait1 = roomInfo.size + 4;
                } else if (orientation2 == 2) {
                    sps_pps_landscape2 = (uint8_t *) malloc(roomInfo.size + 4);
                    memset(sps_pps_landscape2, 0, roomInfo.size);
                    int2Bytes(sps_pps_landscape2, roomInfo.size);
                    memcpy(sps_pps_landscape2 + 4, room, roomInfo.size);
                    sps_pps_size_landscape2 = roomInfo.size + 4;
                }
                AMediaCodec_releaseOutputBuffer(codec, roomIndex, false);
                break;
            }
            AMediaCodec_releaseOutputBuffer(codec, roomIndex, false);
        }
    }
    LOGI("getSpsPps() 2\n");

    isGettingSpsPps = true;
    if (orientation1 == 1) {
        codec = codec2;
        createLandscapeVirtualDisplay();
    } else if (orientation2 == 2) {
        codec = codec1;
        createPortraitVirtualDisplay();
    }
    LOGI("getSpsPps() 3\n");
    while (isGettingSpsPps) {
        for (;;) {
            ssize_t roomIndex = AMediaCodec_dequeueOutputBuffer(codec, &roomInfo, TIME_OUT);
            if (roomIndex < 0) {
                break;
            }
            uint8_t *room = AMediaCodec_getOutputBuffer(codec, (size_t) roomIndex, &out_size);
            if (room != nullptr && (roomInfo.flags & 2)) {// 配置帧
                isGettingSpsPps = false;
                if (orientation1 == 1) {
                    sps_pps_landscape2 = (uint8_t *) malloc(roomInfo.size + 4);
                    memset(sps_pps_landscape2, 0, roomInfo.size);
                    int2Bytes(sps_pps_landscape2, roomInfo.size);
                    memcpy(sps_pps_landscape2 + 4, room, roomInfo.size);
                    sps_pps_size_landscape2 = roomInfo.size + 4;
                } else if (orientation2 == 2) {
                    sps_pps_portrait1 = (uint8_t *) malloc(roomInfo.size + 4);
                    memset(sps_pps_portrait1, 0, roomInfo.size);
                    int2Bytes(sps_pps_portrait1, roomInfo.size);
                    memcpy(sps_pps_portrait1 + 4, room, roomInfo.size);
                    sps_pps_size_portrait1 = roomInfo.size + 4;
                }
                AMediaCodec_releaseOutputBuffer(codec, roomIndex, false);
                break;
            }
            AMediaCodec_releaseOutputBuffer(codec, roomIndex, false);
        }
    }
    LOGI("getSpsPps() 4\n");

    LOGI("getSpsPps() sps_pps_size_portrait1: %d sps_pps_size_landscape2: %d\n",
         sps_pps_size_portrait1, sps_pps_size_landscape2);

    LOGI("getSpsPps() end\n");
}

static int handleSpsPps(AMediaFormat *pFormat, uint8_t *sps_pps, ssize_t size) {
    // unknow
    static int MARK0 = 0;
    // 0 0 0 1
    static int MARK1 = 1;
    //   0 0 1
    static int MARK2 = 2;
    // ... 103 ... 104 ...
    static int MARK3 = 3;
    // ...  39 ... 40 ...
    static int MARK4 = 4;

    uint8_t *sps = nullptr;
    uint8_t *pps = nullptr;
    size_t sps_size = 0;
    size_t pps_size = 0;
    int mark = MARK0;
    int index = -1;
    if (sps_pps[0] == 0
        && sps_pps[1] == 0
        && sps_pps[2] == 0
        && sps_pps[3] == 1) {
        // region 0 0 0 1 ... 0 0 0 1 ...
        for (int i = 1; i < size; i++) {
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
        // region 0 0 1 ... 0 0 1 ...
        for (int i = 1; i < size; i++) {
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

    if (index != -1) {
        // region
        sps_size = index;
        pps_size = size - index;
        sps = (uint8_t *) malloc(sps_size);
        pps = (uint8_t *) malloc(pps_size);
        memset(sps, 0, sps_size);
        memset(pps, 0, pps_size);
        memcpy(sps, sps_pps, sps_size);
        memcpy(pps, sps_pps + index, pps_size);
        // endregion
    } else {
        // region ... 103 ... 104 ...
        mark = MARK3;
        int spsIndex = -1;
        int spsLength = 0;
        int ppsIndex = -1;
        int ppsLength = 0;
        for (int i = 0; i < size; i++) {
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
        // endregion

        // region ... 39 ... 40 ...
        if (spsIndex == -1 || ppsIndex == -1) {
            mark = MARK4;
            spsIndex = -1;
            spsLength = 0;
            ppsIndex = -1;
            ppsLength = 0;
            for (int i = 0; i < size; i++) {
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
        // endregion

        // region
        if (spsIndex != -1 && ppsIndex != -1) {
            sps_size = spsLength;
            pps_size = ppsLength;
            sps = (uint8_t *) malloc(spsLength + 4);
            pps = (uint8_t *) malloc(ppsLength + 4);
            memset(sps, 0, spsLength + 4);
            memset(pps, 0, ppsLength + 4);

            // 0x00, 0x00, 0x00, 0x01
            sps[0] = pps[0] = 0;
            sps[1] = pps[1] = 0;
            sps[2] = pps[2] = 0;
            sps[3] = pps[3] = 1;
            memcpy(sps + 4, sps_pps + spsIndex, spsLength);
            memcpy(pps + 4, sps_pps + ppsIndex, ppsLength);
        }
        // endregion
    }

    if (sps != nullptr && pps != nullptr) {
        AMediaFormat_setBuffer(pFormat, "csd-0", sps, sps_size);
        AMediaFormat_setBuffer(pFormat, "csd-1", pps, pps_size);
    } else {
        // 实在找不到sps和pps的数据了
        mark = MARK0;
    }

    return mark;
}

static void sendData(uint8_t *data_buffer, ssize_t length) {
    pthread_mutex_lock(&mutex1);
    if (isRecording) {
        ssize_t sendLength = send_data(data_buffer, length);
        if (sendLength <= 0) {
            isRecording = false;
            isPlaying1 = false;
            isPlaying2 = false;
            // notify to java
            sendDataError();
            LOGE("sendData() sendLength: %d", sendLength);
        }
    }
    pthread_mutex_unlock(&mutex1);
}

static void handleOutputBufferImpl(uint8_t *room, AMediaCodecBufferInfo roomInfo, bool isPortrait) {
    uint8_t *frame = (uint8_t *) malloc(roomInfo.size + 6);
    memset(frame, 0, roomInfo.size + 6);
    int2Bytes(frame, roomInfo.size + 2);
    memcpy(frame + 4, room, roomInfo.size);
    // 用于在接收端判断是否是关键帧
    frame[roomInfo.size + 5] = roomInfo.flags;
    // "1"表示竖屏, "2"表示横屏
    frame[roomInfo.size + 4] = isPortrait ? 1 : 2;

    if (isPortrait) {
        if (!isKeyFrameWrite1) {
            // for IDR frame
            if ((roomInfo.flags & 1) != 0) {
                isKeyFrameWrite1 = true;
                sendData(frame, roomInfo.size + 6);
                free(frame);
                frame = nullptr;
                return;
            }
            free(frame);
            frame = nullptr;
            return;
        }

        sendData(frame, roomInfo.size + 6);
        free(frame);
        frame = nullptr;
        return;
    }

    if (!isKeyFrameWrite2) {
        // for IDR frame
        if ((roomInfo.flags & 1) != 0) {
            isKeyFrameWrite2 = true;
            sendData(frame, roomInfo.size + 6);
            free(frame);
            frame = nullptr;
            return;
        }
        free(frame);
        frame = nullptr;
        return;
    }

    sendData(frame, roomInfo.size + 6);
    free(frame);
    frame = nullptr;
}

static void handleOutputBuffer(uint8_t *room, AMediaCodecBufferInfo roomInfo, bool isPortrait) {
    if (room == nullptr) {
        return;
    }

    if (ONLY_OUTPUT_KEY_FRAME) {// 效果不理想
        if (roomInfo.flags & 1) {
            //LOGI("handleOutputBuffer() roomInfo.flags 1: %d", roomInfo.flags);// 关键帧
            handleOutputBufferImpl(room, roomInfo, isPortrait);
        }
        return;
    }
    /*if (roomInfo.flags & 2) {
        LOGI("handleOutputBuffer() roomInfo.flags 2: %d", roomInfo.flags);// 配置帧
    }*/
    /*if (roomInfo.flags & 4) {
        LOGI("handleOutputBuffer() roomInfo.flags 4: %d", roomInfo.flags);// 结束帧
    }*/

    handleOutputBufferImpl(room, roomInfo, isPortrait);
}

AMediaCodec *getCodec(int which_client) {
    if (which_client == 1) {
        return codec1;
    } else if (which_client == 2) {
        return codec2;
    }

    return nullptr;
}

void setSpsPps(int which_client, int orientation, unsigned char *sps_pps, ssize_t size) {
    switch (which_client) {
        case 1: {
            if (orientation == 1) {
                sps_pps_portrait1 = (uint8_t *) malloc(size);
                memcpy(sps_pps_portrait1, sps_pps, size);
                sps_pps_size_portrait1 = size;
            } else {
                sps_pps_landscape1 = (uint8_t *) malloc(size);
                memcpy(sps_pps_landscape1, sps_pps, size);
                sps_pps_size_landscape1 = size;
            }
            break;
        }
        case 2: {
            if (orientation == 1) {
                sps_pps_portrait2 = (uint8_t *) malloc(size);
                memcpy(sps_pps_portrait2, sps_pps, size);
                sps_pps_size_portrait2 = size;
            } else {
                sps_pps_landscape2 = (uint8_t *) malloc(size);
                memcpy(sps_pps_landscape2, sps_pps, size);
                sps_pps_size_landscape2 = size;
            }
            break;
        }
        default:
            break;
    }
}

// 解码时使用
void setSurface(int which_client, JNIEnv *env, jobject surface_obj) {
    if (which_client == 1) {
        if (surface1) {
            ANativeWindow_release(surface1);
            surface1 = nullptr;
        }
        surface1 = ANativeWindow_fromSurface(env, surface_obj);
    } else if (which_client == 2) {
        if (surface2) {
            ANativeWindow_release(surface2);
            surface2 = nullptr;
        }
        surface2 = ANativeWindow_fromSurface(env, surface_obj);
    }

    startMediaCodec(which_client, 0);
}

// 创建编码器或者解码器
void createMediaCodec(int which_client) {
    if (which_client == 1) {
        if (codec1) {
            AMediaCodec_delete(codec1);
            codec1 = nullptr;
        }
        codec1 = AMediaCodec_createCodecByName(codecName1);
    } else if (which_client == 2) {
        if (codec2) {
            AMediaCodec_delete(codec2);
            codec2 = nullptr;
        }
        codec2 = AMediaCodec_createCodecByName(codecName2);
    }
}

// 解码时使用
void createMediaFormat(int which_client, int orientation) {
    char *mime = nullptr;
    int width = 0;
    int height = 0;
    int maxLength = 0;
    if (which_client == 1) {
        mime = mime1;
        width = width1;
        height = height1;
        maxLength = height1;
    } else if (which_client == 2) {
        mime = mime2;
        width = width2;
        height = height2;
        maxLength = width2;
    }
    AMediaFormat *pFormat = AMediaFormat_new();
    AMediaFormat_setString(pFormat, AMEDIAFORMAT_KEY_MIME, mime);
    AMediaFormat_setInt32(pFormat, AMEDIAFORMAT_KEY_WIDTH, width);
    AMediaFormat_setInt32(pFormat, AMEDIAFORMAT_KEY_HEIGHT, height);
    AMediaFormat_setInt32(pFormat, AMEDIAFORMAT_KEY_MAX_WIDTH, maxLength);
    AMediaFormat_setInt32(pFormat, AMEDIAFORMAT_KEY_MAX_HEIGHT, maxLength);
    AMediaFormat_setInt32(pFormat, AMEDIAFORMAT_KEY_MAX_INPUT_SIZE, maxLength * maxLength);
    if (strcmp(mime, "video/hevc") == 0) {
        if (which_client == 1) {
            if (orientation == 1) {
                AMediaFormat_setBuffer(
                        pFormat, "csd-0", sps_pps_portrait1, sps_pps_size_portrait1);
            } else {
                AMediaFormat_setBuffer(
                        pFormat, "csd-0", sps_pps_landscape1, sps_pps_size_landscape1);
            }
        } else if (which_client == 2) {
            if (orientation == 1) {
                AMediaFormat_setBuffer(
                        pFormat, "csd-0", sps_pps_portrait2, sps_pps_size_portrait2);
            } else {
                AMediaFormat_setBuffer(
                        pFormat, "csd-0", sps_pps_landscape2, sps_pps_size_landscape2);
            }
        }
    } else if (strcmp(mime, "video/avc") == 0) {
        int mark = 0;
        if (which_client == 1) {
            if (orientation == 1) {
                mark = handleSpsPps(pFormat, sps_pps_portrait1, sps_pps_size_portrait1);
            } else {
                mark = handleSpsPps(pFormat, sps_pps_landscape1, sps_pps_size_landscape1);
            }
        } else if (which_client == 2) {
            if (orientation == 1) {
                mark = handleSpsPps(pFormat, sps_pps_portrait2, sps_pps_size_portrait2);
            } else {
                mark = handleSpsPps(pFormat, sps_pps_landscape2, sps_pps_size_landscape2);
            }
        }
        if (mark == 0) {
            LOGE("createMediaFormat() video/avc 找不到相关的sps pps数据");
        }
    }

    if (which_client == 1) {
        if (format1) {
            AMediaFormat_delete(format1);
            format1 = nullptr;
        }
        format1 = pFormat;
    } else if (which_client == 2) {
        if (format2) {
            AMediaFormat_delete(format2);
            format2 = nullptr;
        }
        format2 = pFormat;
    }
}

void startMediaCodec(int which_client, uint32_t flags) {
    LOGI("startMediaCodec() start which_client: %d", which_client);
    if (which_client == 1) {
        if (isPlaying1) {
            return;
        }
        if (!codec1 || !format1 || !surface1) {
            return;
        }
        AMediaCodec_configure(codec1, format1, surface1, nullptr, flags);
        AMediaCodec_start(codec1);
        isPlaying1 = true;
    } else if (which_client == 2) {
        if (isPlaying2) {
            return;
        }
        if (!codec2 || !format2 || !surface2) {
            return;
        }
        AMediaCodec_configure(codec2, format2, surface2, nullptr, flags);
        AMediaCodec_start(codec2);
        isPlaying2 = true;
    }

    if (which_client == 1) {
        if (!isPlaying1) {
            return;
        }
    } else if (which_client == 2) {
        if (!isPlaying2) {
            return;
        }
    }

    // 开启线程不断地读取数据
    pthread_t p_tids_receive_data;
    // 定义一个属性
    pthread_attr_t attr;
    sched_param param;
    // 初始化属性值,均设为默认值
    pthread_attr_init(&attr);
    pthread_attr_getschedparam(&attr, &param);
    pthread_attr_setschedparam(&attr, &param);
    pthread_attr_setscope(&attr, PTHREAD_SCOPE_SYSTEM);
    if (which_client == 1) {
        pthread_create(&p_tids_receive_data, &attr, startDecoder, &which_client1);
    } else if (which_client == 2) {
        pthread_create(&p_tids_receive_data, &attr, startDecoder, &which_client2);
    }
    LOGI("startMediaCodec() end   which_client: %d", which_client);
}

bool
feedInputBuffer(AMediaCodec *codec,
                unsigned char *data, off_t offset, size_t size,
                uint64_t time, uint32_t flags) {
    ssize_t roomIndex = AMediaCodec_dequeueInputBuffer(codec, TIME_OUT);
    if (roomIndex < 0) {
        return true;
    }

    size_t out_size = 0;
    //auto room = AMediaCodec_getInputBuffer(codec, roomIndex, &out_size);
    uint8_t *room = AMediaCodec_getInputBuffer(codec, (size_t) roomIndex, &out_size);
    if (room == nullptr) {
        return false;
    }
    memcpy(room, data, size);
    AMediaCodec_queueInputBuffer(codec, roomIndex, offset, size, time, flags);
    return true;
}

bool
drainOutputBuffer(int which_client, AMediaCodec *codec, bool render) {
    AMediaCodecBufferInfo info;
    size_t out_size = 0;
    for (;;) {
        if (which_client == 1) {
            if (!isPlaying1) {
                break;
            }
        } else if (which_client == 2) {
            if (!isPlaying2) {
                break;
            }
        }

        ssize_t roomIndex = AMediaCodec_dequeueOutputBuffer(codec, &info, TIME_OUT);
        if (roomIndex < 0) {
            switch (roomIndex) {
                case AMEDIACODEC_INFO_TRY_AGAIN_LATER: {
                    break;
                }
                case AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED: {
                    auto format = AMediaCodec_getOutputFormat(codec);
                    LOGI("format changed to: %s", AMediaFormat_toString(format));
                    AMediaFormat_delete(format);
                    break;
                }
                case AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED: {
                    break;
                }
                default:
                    break;
            }
            break;
        }

        /*if (info.flags & 1) {
            LOGI("info.flags 1: %d", info.flags);// 关键帧
        }
        if (info.flags & 2) {
            LOGI("info.flags 2: %d", info.flags);
        }
        if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
            LOGI("info.flags 4: %d", info.flags);
        }*/

        uint8_t *room = AMediaCodec_getOutputBuffer(codec, (size_t) roomIndex, &out_size);
        if (room == nullptr) {
            return false;
        }

        AMediaCodec_releaseOutputBuffer(codec, roomIndex, render);
    }

    return true;
}

bool
drainOutputBufferForEncoder(int orientation, AMediaCodec *codec, bool render, bool isPortrait) {
    AMediaCodecBufferInfo roomInfo;
    size_t out_size = 0;
    for (;;) {
        if (orientation == 1) {
            if (!isPlaying1) {
                break;
            }
        } else if (orientation == 2) {
            if (!isPlaying2) {
                break;
            }
        }

        ssize_t roomIndex = AMediaCodec_dequeueOutputBuffer(codec, &roomInfo, TIME_OUT);
        if (roomIndex < 0) {
            switch (roomIndex) {
                case AMEDIACODEC_INFO_TRY_AGAIN_LATER: {
                    break;
                }
                case AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED: {
                    auto format = AMediaCodec_getOutputFormat(codec);
                    LOGI("format changed to: %s", AMediaFormat_toString(format));
                    AMediaFormat_delete(format);
                    break;
                }
                case AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED: {
                    break;
                }
                default:
                    break;
            }
            break;
        }

        uint8_t *room = AMediaCodec_getOutputBuffer(codec, (size_t) roomIndex, &out_size);
        handleOutputBuffer(room, roomInfo, isPortrait);

        AMediaCodec_releaseOutputBuffer(codec, roomIndex, render);
    }

    return true;
}

bool
feedInputBufferAndDrainOutputBuffer(int which_client,
                                    AMediaCodec *codec,
                                    unsigned char *data,
                                    off_t offset, size_t size,
                                    uint64_t time, uint32_t flags,
                                    bool render,
                                    bool needFeedInputBuffer) {
    if (needFeedInputBuffer) {
        return feedInputBuffer(codec, data, offset, size, time, flags) &&
               drainOutputBuffer(which_client, codec, render);
    }

    return drainOutputBuffer(which_client, codec, render);
}

void release1(bool isDecoder) {
    LOGI("release1() start\n");
    isPlaying1 = false;
    if (surface1) {
        ANativeWindow_release(surface1);
        surface1 = nullptr;
    }
    if (format1) {
        AMediaFormat_delete(format1);
        format1 = nullptr;
    }
    if (codec1) {
        AMediaCodec_delete(codec1);
        codec1 = nullptr;
    }
    if (isDecoder) {
        if (sps_pps_portrait1) {
            free(sps_pps_portrait1);
            sps_pps_portrait1 = nullptr;
        }
        sps_pps_size_portrait1 = 0;
    }
    if (sps_pps_landscape1) {
        free(sps_pps_landscape1);
        sps_pps_landscape1 = nullptr;
    }
    sps_pps_size_landscape1 = 0;
    mimeLength1 = 0;
    codecNameLength1 = 0;
    width1 = 0;
    height1 = 0;
    orientation1 = 0;
    isKeyFrameWrite1 = false;
    LOGI("release1() end\n");
}

void release2(bool isDecoder) {
    LOGI("release2() start\n");
    isPlaying2 = false;
    if (surface2) {
        ANativeWindow_release(surface2);
        surface2 = nullptr;
    }
    if (format2) {
        AMediaFormat_delete(format2);
        format2 = nullptr;
    }
    if (codec2) {
        AMediaCodec_delete(codec2);
        codec2 = nullptr;
    }
    if (sps_pps_portrait2) {
        free(sps_pps_portrait2);
        sps_pps_portrait2 = nullptr;
    }
    sps_pps_size_portrait2 = 0;
    if (isDecoder) {
        if (sps_pps_landscape2) {
            free(sps_pps_landscape2);
            sps_pps_landscape2 = nullptr;
        }
        sps_pps_size_landscape2 = 0;
    }
    mimeLength2 = 0;
    codecNameLength2 = 0;
    width2 = 0;
    height2 = 0;
    orientation2 = 0;
    isKeyFrameWrite2 = false;
    LOGI("release2() end\n");
}

//////////////////////////////////////////////////////////////////////////////////////////////
// 下面是有关编码过程
void createEncoderMediaFormat(const char *mime,
                              int orientation,
                              int width, int height) {
    LOGI("createEncoderMediaFormat() mime: %s orientation: %d width: %d height: %d\n",
         mime, orientation, width, height);
    if (format1) {
        AMediaFormat_delete(format1);
        format1 = nullptr;
    }
    if (format2) {
        AMediaFormat_delete(format2);
        format2 = nullptr;
    }

    if (orientation == 1) {
        // 竖屏
        width1 = width;
        height1 = height;
        orientation1 = 1;
        orientation2 = 0;
        format1 = AMediaFormat_new();
        AMediaFormat_setString(format1, AMEDIAFORMAT_KEY_MIME, mime);
        AMediaFormat_setInt32(format1, AMEDIAFORMAT_KEY_WIDTH, width);
        AMediaFormat_setInt32(format1, AMEDIAFORMAT_KEY_HEIGHT, height);
        AMediaFormat_setInt32(format1, AMEDIAFORMAT_KEY_MAX_WIDTH, width);
        AMediaFormat_setInt32(format1, AMEDIAFORMAT_KEY_MAX_HEIGHT, height);
        // 横屏
        format2 = AMediaFormat_new();
        AMediaFormat_setString(format2, AMEDIAFORMAT_KEY_MIME, mime);
        AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_WIDTH, height);
        AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_HEIGHT, width);
        AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_MAX_WIDTH, height);
        AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_MAX_HEIGHT, width);
    } else {
        // 横屏
        width2 = width;
        height2 = height;
        orientation1 = 0;
        orientation2 = 2;
        format2 = AMediaFormat_new();
        AMediaFormat_setString(format2, AMEDIAFORMAT_KEY_MIME, mime);
        AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_WIDTH, width);
        AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_HEIGHT, height);
        AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_MAX_WIDTH, width);
        AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_MAX_HEIGHT, height);
        // 竖屏
        format1 = AMediaFormat_new();
        AMediaFormat_setString(format1, AMEDIAFORMAT_KEY_MIME, mime);
        AMediaFormat_setInt32(format1, AMEDIAFORMAT_KEY_WIDTH, height);
        AMediaFormat_setInt32(format1, AMEDIAFORMAT_KEY_HEIGHT, width);
        AMediaFormat_setInt32(format1, AMEDIAFORMAT_KEY_MAX_WIDTH, height);
        AMediaFormat_setInt32(format1, AMEDIAFORMAT_KEY_MAX_HEIGHT, width);
    }

    AMediaFormat_setInt32(format1, AMEDIAFORMAT_KEY_MAX_INPUT_SIZE, width * height);
    AMediaFormat_setInt32(format1, AMEDIAFORMAT_KEY_COLOR_FORMAT, 0x7F000789);// 录制屏幕专用
    AMediaFormat_setInt32(format1, AMEDIAFORMAT_KEY_BIT_RATE, 8000000);
    AMediaFormat_setInt32(format1, AMEDIAFORMAT_KEY_FRAME_RATE, 25);
    AMediaFormat_setInt32(format1, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 1);

    AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_MAX_INPUT_SIZE, width * height);
    AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_COLOR_FORMAT, 0x7F000789);
    AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_BIT_RATE, 8000000);
    AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_FRAME_RATE, 25);
    AMediaFormat_setInt32(format2, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 1);
}

void createEncoderMediaCodec(const char *codec_name) {
    if (codec1) {
        AMediaCodec_delete(codec1);
        codec1 = nullptr;
    }
    if (codec2) {
        AMediaCodec_delete(codec2);
        codec2 = nullptr;
    }

    codec1 = AMediaCodec_createCodecByName(codec_name);
    codec2 = AMediaCodec_createCodecByName(codec_name);

    AMediaCodec_configure(codec1, format1, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    AMediaCodec_configure(codec2, format2, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
}

void createEncoderSurface() {
    AMediaCodec_createInputSurface(codec1, &surface1);
    AMediaCodec_createInputSurface(codec2, &surface2);
}

// 录制屏幕时需要把Surface传递给java层使用
ANativeWindow *getSurface(int orientation) {
    if (orientation == 1) {
        return surface1;
    } else {
        return surface2;
    }
}

void startEncoderMediaCodec() {
    AMediaCodec_start(codec1);
    AMediaCodec_start(codec2);
}

void fromPortraitToLandscape() {
    LOGW("竖屏 ---> 横屏");
    createLandscapeVirtualDisplay();
    isPlaying1 = false;
    isPlaying2 = true;
    isKeyFrameWrite2 = false;
    sendData(ORIENTATION_LANDSCAPE, 5);
}

void fromLandscapeToPortrait() {
    LOGW("横屏 ---> 竖屏");
    createPortraitVirtualDisplay();
    isPlaying1 = true;
    isPlaying2 = false;
    isKeyFrameWrite1 = false;
    sendData(ORIENTATION_PORTRAIT, 5);
}

static void *startEncoder(void *arg) {
    LOGI("startEncoder() start\n");
    while (isRecording) {
        if (isPlaying1) {
            drainOutputBufferForEncoder(1, codec1, false, true);
        } else if (isPlaying2) {
            drainOutputBufferForEncoder(2, codec2, false, false);
        }
    }
    LOGI("startEncoder() end\n");

    pthread_mutex_lock(&mutex1);
    isRecording = false;
    release1(false);
    release2(false);
    pthread_mutex_unlock(&mutex1);
}

void startRecordScreen() {
    if (isRecording) {
        LOGE("startRecordScreen() return for isRecording is true\n");
        return;
    }

    LOGI("startRecordScreen() start\n");
    isRecording = true;
    int2Bytes(ORIENTATION_PORTRAIT, 1);
    int2Bytes(ORIENTATION_LANDSCAPE, 1);
    ORIENTATION_PORTRAIT[4] = -1; // 服务端读到"-1"表示投屏端设备已经竖屏了
    ORIENTATION_LANDSCAPE[4] = -2;// 服务端读到"-2"表示投屏端设备已经横屏了

    if (sps_pps_portrait1 == nullptr || sps_pps_landscape2 == nullptr) {
        getSpsPps();
    }

    if (orientation1 == 1) {
        isPlaying1 = true;
        isPlaying2 = false;
        isKeyFrameWrite1 = false;
        createPortraitVirtualDisplay();
        sendData(sps_pps_portrait1, sps_pps_size_portrait1);
        sendData(sps_pps_landscape2, sps_pps_size_landscape2);
    } else if (orientation2 == 2) {
        isPlaying1 = false;
        isPlaying2 = true;
        isKeyFrameWrite2 = false;
        createLandscapeVirtualDisplay();
        sendData(sps_pps_landscape2, sps_pps_size_landscape2);
        sendData(sps_pps_portrait1, sps_pps_size_portrait1);
    }

    // 开启线程不断地读取数据
    pthread_t p_tids_receive_data;
    // 定义一个属性
    pthread_attr_t attr;
    sched_param param;
    // 初始化属性值,均设为默认值
    pthread_attr_init(&attr);
    pthread_attr_getschedparam(&attr, &param);
    pthread_attr_setschedparam(&attr, &param);
    pthread_attr_setscope(&attr, PTHREAD_SCOPE_SYSTEM);
    pthread_create(&p_tids_receive_data, &attr, startEncoder, nullptr);
    LOGI("startRecordScreen() end\n");
}

void stopRecordScreen(bool needToRelease) {
    isRecording = false;
    isPlaying1 = false;
    isPlaying2 = false;
    if (needToRelease) {
        pthread_mutex_lock(&mutex1);
        release1(true);
        release2(true);
        pthread_mutex_unlock(&mutex1);
    }
}
