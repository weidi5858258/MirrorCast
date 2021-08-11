//
// Created by root on 19-8-13.
//

#ifndef USEFRAGMENTS_FFMPEG_H
#define USEFRAGMENTS_FFMPEG_H

enum {
    USE_MODE_MEDIA = 1,
    USE_MODE_ONLY_VIDEO = 2,
    USE_MODE_ONLY_AUDIO = 3,
    USE_MODE_AUDIO_VIDEO = 4,
    USE_MODE_AAC_H264 = 5,
    USE_MODE_MEDIA_4K = 6,
    USE_MODE_MEDIA_MEDIACODEC = 7,
    USE_MODE_MEDIA_FFPLAY = 8
};

enum DO_SOMETHING_CODE {
    // java ---> jni
    DO_SOMETHING_CODE_init = 1000,
    DO_SOMETHING_CODE_Server_accept = 1001,
    DO_SOMETHING_CODE_Client_connect = 1002,
    DO_SOMETHING_CODE_Client_disconnect = 1003,
    DO_SOMETHING_CODE_Client_send_data = 1004,
    DO_SOMETHING_CODE_Client_set_info = 1005,
    DO_SOMETHING_CODE_Server_set_ip = 1006,
    DO_SOMETHING_CODE_Server_close = 1007,
    DO_SOMETHING_CODE_get_server_port = 1008,
    DO_SOMETHING_CODE_close_all_clients = 1009,
    DO_SOMETHING_CODE_close_one_client = 1010,

    // jni ---> java
    DO_SOMETHING_CODE_connected = 2000,
    DO_SOMETHING_CODE_disconnected = 2001,
    DO_SOMETHING_CODE_change_window = 2002,
};

enum {
    MSG_ON_TRANSACT_VIDEO_PRODUCER = 0x1001,
    MSG_ON_TRANSACT_VIDEO_CONSUMER = 0x1002,
    MSG_ON_TRANSACT_AUDIO_PRODUCER = 0x1003,
    MSG_ON_TRANSACT_AUDIO_CONSUMER = 0x1004,
    MSG_ON_TRANSACT_INIT_VIDEO_MEDIACODEC = 0x1005,
};

void connect(int which_client);

void disconnect(int which_client);

void changeWindow(int which_client, int orientation);

void setData(int code, int which_client, const char *data, ssize_t size);

void putData(int which_client, unsigned char *encodedData, ssize_t size);

#endif //USEFRAGMENTS_FFMPEG_H
