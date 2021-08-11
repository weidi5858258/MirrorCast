//
// Created by root on 2021/8/11.
//

#include <pthread.h>
#include <list>

#include "include/Log.h"
#include "MediaCodec.h"
#include "MediaData.h"

#define LOG "player_alexander"

pthread_mutex_t mutex1 = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t cond1 = PTHREAD_COND_INITIALIZER;
pthread_mutex_t mutex2 = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t cond2 = PTHREAD_COND_INITIALIZER;

typedef struct Data_ {
    unsigned char *data;
    ssize_t size;
} Data;

static std::list<Data> list1;
static std::list<Data> list2;

static uint8_t *sps_pps_portrait1 = nullptr;
static uint8_t *sps_pps_landscape1 = nullptr;
static uint8_t *sps_pps_portrait2 = nullptr;
static uint8_t *sps_pps_landscape2 = nullptr;
static ssize_t sps_pps_size_portrait1 = 0;
static ssize_t sps_pps_size_landscape1 = 0;
static ssize_t sps_pps_size_portrait2 = 0;
static ssize_t sps_pps_size_landscape2 = 0;

static bool isCurPortrait1 = true;
static bool isCurPortrait2 = true;
static bool isPrePortrait1 = true;
static bool isPrePortrait2 = true;
static bool isPlaying1 = false;
static bool isPlaying2 = false;

void drainFrame(std::list<Data> *list) {

}

void set_sps_pps(int which_client, int orientation, unsigned char *sps_pps, ssize_t size) {
    switch (which_client) {
        case 1: {
            if (orientation == 1) {
                sps_pps_portrait1 = (uint8_t *) malloc(size);
                memcpy(sps_pps_portrait1, sps_pps, size);
                sps_pps_size_portrait1 = size;
                isCurPortrait1 = true;
                isPrePortrait1 = true;
            } else {
                sps_pps_landscape1 = (uint8_t *) malloc(size);
                memcpy(sps_pps_landscape1, sps_pps, size);
                sps_pps_size_landscape1 = size;
                isCurPortrait1 = false;
                isPrePortrait1 = false;
            }
            break;
        }
        case 2: {
            if (orientation == 1) {
                sps_pps_portrait2 = (uint8_t *) malloc(size);
                memcpy(sps_pps_portrait2, sps_pps, size);
                sps_pps_size_portrait2 = size;
                isCurPortrait2 = true;
                isPrePortrait2 = true;
            } else {
                sps_pps_landscape2 = (uint8_t *) malloc(size);
                memcpy(sps_pps_landscape2, sps_pps, size);
                sps_pps_size_landscape2 = size;
                isCurPortrait2 = false;
                isPrePortrait2 = false;
            }
            break;
        }
        default:
            break;
    }
}

void set_orientation(int which_client, int orientation) {
    switch (which_client) {
        case 1: {
            if (orientation == 1) {
                isCurPortrait1 = true;
            } else {
                isCurPortrait1 = false;
            }
            break;
        }
        case 2: {
            if (orientation == 1) {
                isCurPortrait2 = true;
            } else {
                isCurPortrait2 = false;
            }
            break;
        }
        default:
            break;
    }
}

void putData(int which_client, unsigned char *encodedData, ssize_t size) {
    Data *data = (Data *) malloc(sizeof(Data));
    memset(data, 0, sizeof(Data));
    uint8_t *frame = (uint8_t *) malloc(size);
    memcpy(frame, encodedData, size);
    data->data = frame;
    data->size = size;
    switch (which_client) {
        case 1: {
            pthread_mutex_lock(&mutex1);
            list1.push_back(*data);
            pthread_mutex_unlock(&mutex1);
            break;
        }
        case 2: {
            pthread_mutex_lock(&mutex2);
            list2.push_back(*data);
            pthread_mutex_unlock(&mutex2);
            break;
        }
        default:
            break;
    }
}

Data *getData(int which_client) {
    Data *data = nullptr;
    switch (which_client) {
        case 1: {
            pthread_mutex_lock(&mutex1);
            drainFrame(&list1);
            data = &list1.front();
            list1.pop_front();
            pthread_mutex_unlock(&mutex1);
            break;
        }
        case 2: {
            pthread_mutex_lock(&mutex2);
            drainFrame(&list2);
            data = &list2.front();
            list2.pop_front();
            pthread_mutex_unlock(&mutex2);
            break;
        }
        default:
            break;
    }
    return data;
}

void start_decoder(int which_client) {
    bool *isCurPortrait = nullptr;
    bool *isPrePortrait = nullptr;
    bool *isPlaying = nullptr;
    switch (which_client) {
        case 1: {
            isCurPortrait = &isCurPortrait1;
            isPrePortrait = &isPrePortrait1;
            isPlaying1 = true;
            isPlaying = &isPlaying1;
            break;
        }
        case 2: {
            isCurPortrait = &isCurPortrait2;
            isPrePortrait = &isPrePortrait2;
            isPlaying2 = true;
            isPlaying = &isPlaying2;
            break;
        }
        default:
            break;
    }

    AMediaCodec *codec = getCodec();
    uint8_t *sps_pps_frame = nullptr;
    ssize_t sps_pps_frame_size = 0;
    bool ret = false;
    bool hasError = false;
    LOGI("start_decoder() start which_client: %d", which_client);
    while (*isPlaying) {
        Data *data = getData(1);
        if (data == nullptr) {
            LOGE("start_decoder() data is nullptr");
            break;
        }
        uint8_t *frame = data->data;
        ssize_t size = data->size;
        if (frame == nullptr) {
            LOGE("start_decoder() frame is nullptr");
            break;
        }

        if (frame[size - 2] == 1 && !(*isCurPortrait)) {
            // 竖屏数据
            continue;
        } else if (frame[size - 2] == 2 && (*isCurPortrait)) {
            // 横屏数据
            continue;
        }

        if ((*isPrePortrait) != (*isCurPortrait)) {
            // 横竖屏切换后需要写一下sps_pps数据
            switch (which_client) {
                case 1: {
                    if (*isCurPortrait) {
                        LOGW("VideoDataDecodeRunnable PORTRAIT  which_client: %d", which_client);
                        sps_pps_frame = sps_pps_portrait1;
                        sps_pps_frame_size = sps_pps_size_portrait1;
                    } else {
                        LOGW("VideoDataDecodeRunnable LANDSCAPE which_client: %d", which_client);
                        sps_pps_frame = sps_pps_landscape1;
                        sps_pps_frame_size = sps_pps_size_landscape1;
                    }
                    break;
                }
                case 2: {
                    if (*isCurPortrait) {
                        LOGW("VideoDataDecodeRunnable PORTRAIT  which_client: %d", which_client);
                        sps_pps_frame = sps_pps_portrait2;
                        sps_pps_frame_size = sps_pps_size_portrait2;
                    } else {
                        LOGW("VideoDataDecodeRunnable LANDSCAPE which_client: %d", which_client);
                        sps_pps_frame = sps_pps_landscape2;
                        sps_pps_frame_size = sps_pps_size_landscape2;
                    }
                    break;
                }
                default:
                    break;
            }

            feedInputBufferAndDrainOutputBuffer(
                    codec,
                    sps_pps_frame,
                    0,
                    sps_pps_frame_size,
                    0,
                    0,
                    true,
                    true);
            isPrePortrait = isCurPortrait;
        }

        ret = feedInputBufferAndDrainOutputBuffer(codec, frame, 0, size - 2,
                                                  0, 0, true, true);
        free(frame);
        frame = nullptr;
        free(data);
        data = nullptr;
        if (!ret) {
            hasError = true;
            LOGE("start_decoder() occur error");
            break;
        }
    }
    LOGI("start_decoder() end   which_client: %d", which_client);

    if (hasError) {
        //mWindow1IsPlaying = false;
        //removeView(MSG_UI_REMOVE_VIEW, which_client);
        // notify to java
        switch (which_client) {
            case 1: {
                isPlaying1 = false;
                break;
            }
            case 2: {
                isPlaying2 = false;
                break;
            }
            default:
                break;
        }
    }

    release();

    /*switch (which_client) {
        case 1: {
            release();
            break;
        }
        case 2: {
            isPlaying2 = false;
            break;
        }
        default:
            break;
    }*/
}

void free1() {
    isPlaying1 = false;
    int size = list1.size();

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
    isCurPortrait1 = true;
    isPrePortrait1 = true;
}

void free2() {
    isPlaying2 = false;
    int size = list2.size();

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
    isCurPortrait2 = true;
    isPrePortrait2 = true;
}

void freeAll() {
    free1();
    free2();
}