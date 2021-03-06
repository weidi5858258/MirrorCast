//
// Created by root on 21-6-28.
//

#include <stdio.h>
#include <stdlib.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <sys/mman.h>
#include <sys/uio.h>
#include <sys/time.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <netdb.h>
#include <fcntl.h>
#include <assert.h>
#include <errno.h>
#include <unistd.h>
#include <signal.h>
#include <string.h>
#include <strings.h>
// 多线程相关操作头文件，可移植众多平台
#include <pthread.h>
#include <sched.h>
#include <linux/in.h>

#include "include/Log.h"
#include "MediaClient.h"

#define LOG "player_alexander"

#define PORT 5858
#define DATA_BUFFER 2073600 // 1920*1080

// 服务器端地址结构
extern struct sockaddr_in server_addr;

extern int server_sock_fd;

extern void close_server_sock(int *server_sock_fd);

extern char IP[15];
extern int SERVER_PROT;

extern void setIP(const char *ip);

extern bool server_is_live;

int client_info_length = 0;
char client_info[1024];

//int device_name_length = 0;
//char device_name[100];
//int video_mime_length = 0;
//char video_mime[30];

bool client_connect() {
    if (IP == NULL) {
        LOGE("connect() IP is NULL\n");
        return false;
    }

    // 返回值
    int err;

    // 建立一个流式套接字
    LOGI("MediaClient socket(...) start\n");
    server_sock_fd = socket(AF_INET, SOCK_STREAM, 0);
    LOGI("MediaClient socket(...) end\n");
    if (server_sock_fd == -1) {
        fprintf(stderr, "socket error: %s\n", strerror(errno));
        LOGE("socket error: %s\n", strerror(errno));
        return false;
    }
    LOGI("MediaClient server_sock_fd: %d\n", server_sock_fd);

    // 设置服务器端地址
    // 清零
    bzero(&server_addr, sizeof(server_addr));
    // 协议族
    server_addr.sin_family = AF_INET;
    // IP地址为本地任意IP地址
    server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    // 服务器端口
    server_addr.sin_port = htons(SERVER_PROT);
    inet_aton(IP, &server_addr.sin_addr);

    // 绑定地址结构到套接字描述符
    LOGI("MediaClient connect(...) start\n");
    err = connect(server_sock_fd, (struct sockaddr *) &server_addr, sizeof(struct sockaddr));
    LOGI("MediaClient connect(...) end\n");
    if (err == -1) {
        fprintf(stderr, "connect error: %s\n", strerror(errno));
        LOGE("connect error: %s\n", strerror(errno));// Connection refused
        close_server_sock(&server_sock_fd);
        return false;
    }

    if (!server_is_live) {
        LOGE("connect() server_is_live is false\n");
        close_server_sock(&server_sock_fd);
        return false;
    }

    char *ip = inet_ntoa(server_addr.sin_addr);
    LOGI("服务端ip: %s\n", ip);

    // 发送"设备名称"给服务端
    // -128~127
    uint8_t dnl[4];
    dnl[0] = (uint8_t) client_info_length;
    dnl[1] = (uint8_t) (client_info_length >> 8);
    dnl[2] = (uint8_t) (client_info_length >> 16);
    dnl[3] = (uint8_t) (client_info_length >> 24);
    write(server_sock_fd, dnl, 4);
    write(server_sock_fd, client_info, client_info_length);

    return true;
}

void client_disconnect() {
    close_server_sock(&server_sock_fd);
    bzero(&server_addr, sizeof(server_addr));
}

ssize_t send_data(uint8_t *data_buffer, ssize_t length) {
    ssize_t write_size = -1;
    if (server_sock_fd != -1) {
        write_size = write(server_sock_fd, data_buffer, length);
    }
    return write_size;
}

void set_client_info(const char *info, int length) {
    client_info_length = length;
    memset(client_info, 0, sizeof(client_info));
    strncpy(client_info, info, length);
    LOGI("set_client_info() client_info: %s client_info_length: %d\n",
         client_info, client_info_length);
}

void client_close_all() {
    client_disconnect();
}

///////////////////////////////////UDP///////////////////////////////////

static void int2Bytes(uint8_t *frame, int length) {
    frame[0] = (uint8_t) length;
    frame[1] = (uint8_t) (length >> 8);
    frame[2] = (uint8_t) (length >> 16);
    frame[3] = (uint8_t) (length >> 24);
}

static int uint8tToInt(uint8_t *src) {
    int value;
    value = (int) ((src[0] & 0xFF)
                   | ((src[1] & 0xFF) << 8)
                   | ((src[2] & 0xFF) << 16)
                   | ((src[3] & 0xFF) << 24));
    return value;
}

bool client_connect_udp() {
    if (IP == NULL) {
        LOGE("connect() IP is NULL\n");
        return false;
    }

    // 返回值
    int err;

    // 建立一个流式套接字
    LOGI("MediaClient socket(...) start\n");
    server_sock_fd = socket(AF_INET, SOCK_DGRAM, 0);
    LOGI("MediaClient socket(...) end\n");
    if (server_sock_fd == -1) {
        fprintf(stderr, "socket error: %s\n", strerror(errno));
        LOGE("socket error: %s\n", strerror(errno));
        return false;
    }
    LOGI("MediaClient server_sock_fd: %d\n", server_sock_fd);

    // 设置服务器端地址
    // 清零
    bzero(&server_addr, sizeof(server_addr));
    // 协议族
    server_addr.sin_family = AF_INET;
    // IP地址为本地任意IP地址
    server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    // 服务器端口
    server_addr.sin_port = htons(SERVER_PROT);
    inet_aton(IP, &server_addr.sin_addr);

    /*// 绑定地址结构到套接字描述符
    LOGI("MediaClient connect(...) start\n");
    err = connect(server_sock_fd, (struct sockaddr *) &server_addr, sizeof(struct sockaddr));
    LOGI("MediaClient connect(...) end\n");
    if (err == -1) {
        fprintf(stderr, "connect error: %s\n", strerror(errno));
        LOGE("connect error: %s\n", strerror(errno));// Connection refused
        close_server_sock(&server_sock_fd);
        return false;
    }*/

    if (!server_is_live) {
        LOGE("connect() server_is_live is false\n");
        close_server_sock(&server_sock_fd);
        return false;
    }

    char *ip = inet_ntoa(server_addr.sin_addr);
    LOGI("服务端ip: %s\n", ip);

    // 发送"设备名称"给服务端
    // -128~127
    uint8_t dnl[4];
    dnl[0] = (uint8_t) client_info_length;
    dnl[1] = (uint8_t) (client_info_length >> 8);
    dnl[2] = (uint8_t) (client_info_length >> 16);
    dnl[3] = (uint8_t) (client_info_length >> 24);
    int send_length;
    send_length = sendto(server_sock_fd, dnl, 4, 0, (struct sockaddr *) &server_addr,
                         sizeof(server_addr));
    LOGI("MediaClient send_length: %d\n", send_length);
    send_length = sendto(server_sock_fd, client_info, client_info_length,
                         0, (struct sockaddr *) &server_addr, sizeof(server_addr));
    LOGI("MediaClient send_length: %d\n", send_length);
    /*uint8_t responseData[4];
    memset(responseData, 0, 4);
    socklen_t addrlen = sizeof(struct sockaddr);
    recvfrom(server_sock_fd, responseData, 4,
             0, (struct sockaddr *) &server_addr, &addrlen);
    int want_to_read_length = uint8tToInt(responseData);
    LOGI("MediaClient want_to_read_length: %d\n", want_to_read_length);*/

    return true;
}

ssize_t send_data_udp(uint8_t *data_buffer, ssize_t length) {
    ssize_t write_size = -1;
    if (server_sock_fd != -1) {
        // 最大传65507字节
        LOGI("MediaClient send_data_udp() length: %d\n", (length - 4));
        if (length <= 65506) {
            sendto(server_sock_fd, data_buffer, length,
                   0, (struct sockaddr *) &server_addr, sizeof(server_addr));
            write_size = sendto(server_sock_fd, data_buffer, length,
                                0, (struct sockaddr *) &server_addr, sizeof(server_addr));
            LOGI("MediaClient send_data_udp() : %d %d %d %d %d %d %d %d %d %d\n",
                 data_buffer[0], data_buffer[1], data_buffer[2], data_buffer[3], data_buffer[4],
                 data_buffer[5], data_buffer[6], data_buffer[7], data_buffer[8],
                 data_buffer[9]);
            /*LOGI("MediaClient send_data_udp() 1 want_to_read_length: %d\n", 0);
            uint8_t responseData[4];
            memset(responseData, 0, 4);
            socklen_t addrlen = sizeof(struct sockaddr);
            recvfrom(server_sock_fd, responseData, 4,
                     0, (struct sockaddr *) &server_addr, &addrlen);
            int want_to_read_length = uint8tToInt(responseData);
            LOGI("MediaClient send_data_udp() 2 want_to_read_length: %d\n", want_to_read_length);*/
        } else {
            uint8_t frame[4];
            int2Bytes(frame, 65502); // 65506
            sendto(server_sock_fd, data_buffer, 65506,
                   0, (struct sockaddr *) &server_addr, sizeof(server_addr));

            int2Bytes(frame, length - 65506 - 4);
            write_size = sendto(server_sock_fd, data_buffer + 65506, length - 65506,
                                0, (struct sockaddr *) &server_addr, sizeof(server_addr));
        }
        if (write_size <= 0) {
            //  90(EMSGSIZE  +Message too long)
            // 101(ENETUNREACH  Networkis unreachable)
            LOGE("MediaClient send_data_udp() errno: %d\n", errno);
        }
        //sleep(5);
    }
    return write_size;
}