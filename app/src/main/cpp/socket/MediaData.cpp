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
    uint8_t *data;
    ssize_t size;
} Data;

// 泪的教训
// static std::list<Data> list1; 莫名其妙的错误
static std::list<Data *> list1;
static std::list<Data *> list2;

static bool isCurPortrait1 = true;
static bool isCurPortrait2 = true;
static bool isPrePortrait1 = true;
static bool isPrePortrait2 = true;

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

void drainFrame(std::list<Data *> *list) {

}

void setOrientation(int which_client, int orientation) {
    LOGI("setOrientation() which_client: %d orientation: %d", which_client, orientation);
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
    memset(data, '\0', sizeof(Data));
    uint8_t *frame = (uint8_t *) malloc(size);
    memcpy(frame, encodedData, size);
    data->data = frame;
    data->size = size;
    switch (which_client) {
        case 1: {
            pthread_mutex_lock(&mutex1);
            list1.push_back(data);

            /*Data *data1 = list1.front();
            list1.pop_front();
            bool ret = feedInputBufferAndDrainOutputBuffer(which_client, getCodec(which_client),
                                                           data1->data, 0, data1->size - 2,
                                                           0, 0, true, true);
            free(data1->data);
            data1->data = nullptr;
            free(data1);
            data1 = nullptr;*/

            pthread_cond_signal(&cond1);
            pthread_mutex_unlock(&mutex1);
            break;
        }
        case 2: {
            pthread_mutex_lock(&mutex2);
            list2.push_back(data);
            pthread_cond_signal(&cond2);
            pthread_mutex_unlock(&mutex2);
            break;
        }
        default:
            break;
    }
}

Data *getData(int which_client) {
    Data *data = nullptr;
    size_t size = 0;
    switch (which_client) {
        case 1: {
            pthread_mutex_lock(&mutex1);
            drainFrame(&list1);
            size = list1.size();
            if (size == 0) {
                pthread_cond_wait(&cond1, &mutex1);
                if (!isPlaying1) {
                    pthread_mutex_unlock(&mutex1);
                    return nullptr;
                }
            }
            data = list1.front();
            list1.pop_front();
            pthread_mutex_unlock(&mutex1);
            break;
        }
        case 2: {
            pthread_mutex_lock(&mutex2);
            drainFrame(&list2);
            size = list2.size();
            if (size == 0) {
                pthread_cond_wait(&cond2, &mutex2);
                if (!isPlaying2) {
                    pthread_mutex_unlock(&mutex2);
                    return nullptr;
                }
            }
            data = list2.front();
            list2.pop_front();
            pthread_mutex_unlock(&mutex2);
            break;
        }
        default:
            break;
    }
    return data;
}

void *startDecoder(void *arg) {
    int *argc = static_cast<int *>(arg);
    int which_client = 0;
    if (*argc == 1) {
        which_client = 1;
    } else if (*argc == 2) {
        which_client = 2;
    }

    bool *isCurPortrait = nullptr;
    bool *isPrePortrait = nullptr;
    bool *isPlaying = nullptr;
    switch (which_client) {
        case 1: {
            isPrePortrait1 = isCurPortrait1;
            isCurPortrait = &isCurPortrait1;
            isPrePortrait = &isPrePortrait1;
            isPlaying = &isPlaying1;
            break;
        }
        case 2: {
            isPrePortrait2 = isCurPortrait2;
            isCurPortrait = &isCurPortrait2;
            isPrePortrait = &isPrePortrait2;
            isPlaying = &isPlaying2;
            break;
        }
        default:
            return nullptr;
    }

    AMediaCodec *codec = getCodec(which_client);
    uint8_t *sps_pps_frame = nullptr;
    ssize_t sps_pps_frame_size = 0;
    bool ret = true;
    bool hasError = false;
    LOGI("startDecoder() start which_client: %d", which_client);
    while (*isPlaying) {
        Data *data = getData(which_client);
        if (data == nullptr) {
            LOGE("startDecoder() data is nullptr");
            break;
        }
        uint8_t *frame = data->data;
        ssize_t size = data->size;// 1433695599
        if (frame == nullptr) {
            LOGE("startDecoder() frame is nullptr");
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
                    which_client,
                    codec,
                    sps_pps_frame,
                    0,
                    sps_pps_frame_size,
                    0,
                    0,
                    true,
                    true);
            *isPrePortrait = *isCurPortrait;
        }

        ret = feedInputBufferAndDrainOutputBuffer(
                which_client, codec, frame, 0, size - 2,
                0, 0, true, true);

        free(frame);
        frame = nullptr;
        free(data);
        data = nullptr;

        if (!ret) {
            hasError = true;
            LOGE("startDecoder() occur error");
            break;
        }
    }
    LOGI("startDecoder() end   which_client: %d", which_client);

    if (hasError) {
        //mWindow1IsPlaying = false;
        //removeView(MSG_UI_REMOVE_VIEW, which_client);
        // notify to java
        switch (which_client) {
            case 1: {
                break;
            }
            case 2: {
                break;
            }
            default:
                break;
        }
    }

    switch (which_client) {
        case 1: {
            free1();
            break;
        }
        case 2: {
            free2();
            break;
        }
        default:
            break;
    }
}

void free1() {
    LOGI("free1() start\n");
    release1();
    int size = list1.size();
    if (size != 0) {
        LOGI("free1() list1 is not empty, %d\n", size);
        size = 0;
        std::list<Data *>::iterator iter;
        for (iter = list1.begin(); iter != list1.end(); iter++) {
            Data *data = *iter;
            if (data->data != nullptr) {
                free(data->data);
                data->data = nullptr;
            }
            free(&data);
            data = nullptr;
            size++;
        }
        list1.clear();
        LOGI("free1() list1 size: %d\n", size);
    }

    isCurPortrait1 = true;
    isPrePortrait1 = true;
    LOGI("free1() end\n");
}

void free2() {
    LOGI("free2() start\n");
    release2();
    int size = list2.size();
    if (size != 0) {
        LOGI("free2() list2 is not empty, %d\n", size);
        size = 0;
        std::list<Data *>::iterator iter;
        for (iter = list2.begin(); iter != list2.end(); iter++) {
            Data *data = *iter;
            if (data->data != nullptr) {
                free(data->data);
                data->data = nullptr;
            }
            free(&data);
            data = nullptr;
            size++;
        }
        list2.clear();
        LOGI("free2() list2 size: %d\n", size);
    }

    isCurPortrait2 = true;
    isPrePortrait2 = true;
    LOGI("free2() end\n");
}

void freeAll() {
    free1();
    free2();
}