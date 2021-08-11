//
// Created by root on 2021/8/11.
//

#ifndef MIRRORCAST_MEDIACODEC_H
#define MIRRORCAST_MEDIACODEC_H

#include "media/NdkMediaCodec.h"
#include "media/NdkMediaExtractor.h"

// for native surface JNI
#include <android/native_window_jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

void setSurface(JNIEnv *env, jobject surface);

ANativeWindow *getSurface();

AMediaCodec *getCodec();

bool
feedInputBufferAndDrainOutputBuffer(AMediaCodec *codec,
                                    unsigned char *data,
                                    off_t offset, size_t size,
                                    uint64_t time, uint32_t flags,
                                    bool render,
                                    bool needFeedInputBuffer);

void release();

#endif //MIRRORCAST_MEDIACODEC_H