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

// window1
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
int orientation1 = 1;
bool isPlaying1 = false;
uint8_t *sps_pps_portrait1 = nullptr;
uint8_t *sps_pps_landscape1 = nullptr;
ssize_t sps_pps_size_portrait1 = 0;
ssize_t sps_pps_size_landscape1 = 0;
// window2
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
int orientation2 = 1;
bool isPlaying2 = false;
uint8_t *sps_pps_portrait2 = nullptr;
uint8_t *sps_pps_landscape2 = nullptr;
ssize_t sps_pps_size_portrait2 = 0;
ssize_t sps_pps_size_landscape2 = 0;

int handleSpsPps(AMediaFormat *pFormat, uint8_t *sps_pps, ssize_t size) {
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

void createMediaFormat(int which_client, int orientation) {
    char *mime = nullptr;
    int width = 0;
    int height = 0;
    int maxLength = 0;
    if (orientation == 1) {
        mime = mime1;
        width = width1;
        height = height1;
        maxLength = height1;
    } else {
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
        }*/
        if (info.flags & 2) {
            LOGI("info.flags 2: %d", info.flags);
        }
        if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
            LOGI("info.flags 4: %d", info.flags);
        }

        /*uint8_t *room = AMediaCodec_getOutputBuffer(codec, (size_t) roomIndex, &out_size);
        if (room == nullptr) {
            return false;
        }*/

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

void release1() {
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
    if (sps_pps_portrait1) {
        free(sps_pps_portrait1);
        sps_pps_portrait1 = nullptr;
    }
    if (sps_pps_landscape1) {
        free(sps_pps_landscape1);
        sps_pps_landscape1 = nullptr;
    }
    sps_pps_size_portrait1 = 0;
    sps_pps_size_landscape1 = 0;
    mimeLength1 = 0;
    codecNameLength1 = 0;
    LOGI("release1() end\n");
}

void release2() {
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
    if (sps_pps_landscape2) {
        free(sps_pps_landscape2);
        sps_pps_landscape2 = nullptr;
    }
    sps_pps_size_portrait2 = 0;
    sps_pps_size_landscape2 = 0;
    mimeLength2 = 0;
    codecNameLength2 = 0;
    LOGI("release2() end\n");
}
