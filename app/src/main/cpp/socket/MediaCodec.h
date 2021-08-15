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

AMediaCodec *getCodec(int which_client);

void setSpsPps(int which_client, int orientation, unsigned char *sps_pps, ssize_t size);

void setSurface(int which_client, JNIEnv *env, jobject surface);

void createMediaCodec(int which_client);

void createMediaFormat(int which_client, int orientation);

void startMediaCodec(int which_client, uint32_t flags);

bool feedInputBufferAndDrainOutputBuffer(int which_client,
                                         AMediaCodec *codec,
                                         unsigned char *data,
                                         off_t offset, size_t size,
                                         uint64_t time, uint32_t flags,
                                         bool render,
                                         bool needFeedInputBuffer);

void release1(bool isDecoder);

void release2(bool isDecoder);

void createEncoderMediaFormat(const char *mime,
                              int orientation,
                              int width, int height);

void createEncoderMediaCodec(const char *codec_name);

void createEncoderSurface();

ANativeWindow *getSurface(int orientation);

void startEncoderMediaCodec();

void fromPortraitToLandscape();

void fromLandscapeToPortrait();

void startRecordScreen();

void stopRecordScreen(bool needToRelease);

#endif //MIRRORCAST_MEDIACODEC_H