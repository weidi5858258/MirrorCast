//
// Created by root on 2021/8/11.
//

#include <assert.h>
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <limits.h>

#include "include/Log.h"
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
// window2
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

extern bool isPlaying1;
extern uint8_t *sps_pps_portrait1;
extern uint8_t *sps_pps_landscape1;
extern ssize_t sps_pps_size_portrait1;
extern ssize_t sps_pps_size_landscape1;
extern bool isPlaying2;
extern uint8_t *sps_pps_portrait2;
extern uint8_t *sps_pps_landscape2;
extern ssize_t sps_pps_size_portrait2;
extern ssize_t sps_pps_size_landscape2;

AMediaCodec *getCodec(int which_client) {
    if (which_client == 1) {
        return codec1;
    } else if (which_client == 2) {
        return codec2;
    }

    return nullptr;
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

void start(int which_client, uint32_t flags) {
    if (which_client == 1) {
        if (!codec1 || !format1 || !surface1) {
            return;
        }
        AMediaCodec_configure(codec1, format1, surface1, nullptr, flags);
        AMediaCodec_start(codec1);
        isPlaying1 = true;
    } else if (which_client == 2) {
        if (!codec2 || !format2 || !surface2) {
            return;
        }
        AMediaCodec_configure(codec2, format2, surface2, nullptr, flags);
        AMediaCodec_start(codec2);
        isPlaying2 = true;
    }


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
    memcpy(room, data, (size_t) size);
    AMediaCodec_queueInputBuffer(codec, roomIndex, offset, size, time, flags);
    return true;
}

bool
drainOutputBuffer(AMediaCodec *codec, bool render) {
    AMediaCodecBufferInfo info;
    size_t out_size = 0;
    for (;;) {
        if (!isPlaying1) {
            break;
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

        if (info.flags & 1) {
            LOGI("info.flags 1: %d", info.flags);
        }
        if (info.flags & 2) {
            LOGI("info.flags 2: %d", info.flags);
        }
        if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
            LOGI("info.flags 4: %d", info.flags);
        }

        uint8_t *room = AMediaCodec_getOutputBuffer(codec, (size_t) roomIndex, &out_size);
        if (room == nullptr) {
            return false;
        }

        AMediaCodec_releaseOutputBuffer(codec, roomIndex, render);
    }

    return true;
}

bool
feedInputBufferAndDrainOutputBuffer(AMediaCodec *codec,
                                    unsigned char *data,
                                    off_t offset, size_t size,
                                    uint64_t time, uint32_t flags,
                                    bool render,
                                    bool needFeedInputBuffer) {
    if (needFeedInputBuffer) {
        return feedInputBuffer(codec, data, offset, size, time, flags) &&
               drainOutputBuffer(codec, render);
    }

    return drainOutputBuffer(codec, render);
}

void release1() {
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
}

void release2() {
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
}
