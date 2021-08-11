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
static ANativeWindow *surface;
static AMediaFormat *format;
static AMediaCodec *codec;
static bool isPlaying = true;

AMediaCodec *getCodec() {
    return codec;
}

void setSurface(JNIEnv *env, jobject surface_obj) {
    if (surface) {
        ANativeWindow_release(surface);
        surface = nullptr;
    }
    surface = ANativeWindow_fromSurface(env, surface_obj);
}

ANativeWindow *getSurface() {
    return surface;
}

void start(const char *name,
           const AMediaFormat *format,
           ANativeWindow *surface,
           AMediaCrypto *crypto,// nullptr
           uint32_t flags) {
    if (codec) {
        AMediaCodec_delete(codec);
        codec = nullptr;
    }
    codec = AMediaCodec_createCodecByName(name);
    AMediaCodec_configure(codec, format, surface, crypto, flags);
    AMediaCodec_start(codec);
    isPlaying = true;
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
        if (!isPlaying) {
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

void release() {
    isPlaying = false;
    if (surface) {
        ANativeWindow_release(surface);
        surface = nullptr;
    }
    if (format) {
        AMediaFormat_delete(format);
        format = nullptr;
    }
    if (codec) {
        AMediaCodec_delete(codec);
        codec = nullptr;
    }
}
