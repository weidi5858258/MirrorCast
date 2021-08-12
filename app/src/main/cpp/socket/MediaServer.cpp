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
#include "MyJni.h"
#include "MediaCodec.h"
#include "MediaData.h"

#define LOG "player_alexander"

#define PORT 5858
#define BACKLOG 1 // 2
#define DATA_BUFFER 2073600 // 1920*1080

extern char *mime1;
extern char *codecName1;
extern int mimeLength1;
extern int codecNameLength1;
extern int width1;
extern int height1;
extern int orientation1;
extern char *mime2;
extern char *codecName2;
extern int mimeLength2;
extern int codecNameLength2;
extern int width2;
extern int height2;
extern int orientation2;

extern void client_connect();

struct sockaddr_in server_addr;
struct sockaddr_in client_addr1;
struct sockaddr_in client_addr2;

int server_sock_fd = -1;
int client_sock_fd1 = -1;
int client_sock_fd2 = -1;

char IP[15];
int SERVER_PROT = 5857;
int what_is_device = 1;// 默认"1"是手机
int MAXIMUM_NUMBER = 1;
int which_replace = 1;

int client_info_length1 = 0;
int client_info_length2 = 0;
char client_info1[1024];
char client_info2[1024];

uint8_t read_buffer1[DATA_BUFFER];
uint8_t data_buffer1[DATA_BUFFER];
uint8_t read_buffer2[DATA_BUFFER];
uint8_t data_buffer2[DATA_BUFFER];

bool server_is_live = true;

bool MEDIA_CODEC_GO_JNI = true;

void *receive_data(void *arg);

// 关闭套接字文件描述符
void close_server_sock(int *server_sock_fd) {
    if (*server_sock_fd != -1) {
        close(*server_sock_fd);
        *server_sock_fd = -1;
    }
}

void close_client_sock(int *client_sock_fd) {
    if (*client_sock_fd != -1) {
        close(*client_sock_fd);
        *client_sock_fd = -1;
    }
}

void server_close_all() {
    close_client_sock(&client_sock_fd1);
    close_client_sock(&client_sock_fd2);
    close_server_sock(&server_sock_fd);
}

void setIP(const char *ip) {
    if (ip == NULL) {
        LOGE("setIP() ip is NULL\n");
        return;
    }
    memset(IP, 0, sizeof(IP));
    strncpy(IP, ip, sizeof(IP));
    LOGI("setIP()          IP: %s\n", IP);
    LOGI("setIP() SERVER_PROT: %d\n", SERVER_PROT);
}

int uint8tToInt(uint8_t *src) {
    int value;
    value = (int) ((src[0] & 0xFF)
                   | ((src[1] & 0xFF) << 8)
                   | ((src[2] & 0xFF) << 16)
                   | ((src[3] & 0xFF) << 24));
    return value;
}

int read_data(int client_sock_fd, ssize_t want_to_read_length,
              char *read_buffer, char *data_buffer) {
    ssize_t read_length = 0;
    ssize_t total_read_length = 0;
    while (total_read_length < want_to_read_length) {
        read_length = read(
                client_sock_fd, read_buffer, want_to_read_length - total_read_length);
        if (read_length > 0) {
            memcpy(data_buffer + total_read_length, read_buffer, read_length);
            total_read_length += read_length;
        } else {
            return EXIT_FAILURE;
        }
    }
    return EXIT_SUCCESS;
}

int read_data(int client_sock_fd, ssize_t want_to_read_length,
              uint8_t *read_buffer, uint8_t *data_buffer) {
    ssize_t read_length = 0;
    ssize_t total_read_length = 0;
    while (total_read_length < want_to_read_length) {
        read_length = read(
                client_sock_fd, read_buffer, want_to_read_length - total_read_length);
        if (read_length > 0) {
            memcpy(data_buffer + total_read_length, read_buffer, read_length);
            total_read_length += read_length;
        } else {
            return EXIT_FAILURE;
        }
    }
    return EXIT_SUCCESS;
}

bool server_bind() {
    LOGI("MediaServer server_bind() start\n");
    int err = -1;
    while (1) {
        /////////////////////////////socket创建过程/////////////////////////////

        ++SERVER_PROT;

        // 建立一个流式套接字
        LOGI("MediaServer socket(...) start\n");
        // tcp: SOCK_STREAM
        // udp: SOCK_DGRAM
        server_sock_fd = socket(AF_INET, SOCK_STREAM, 0);
        LOGI("MediaServer socket(...) end\n");
        if (server_sock_fd == -1) {
            fprintf(stderr, "socket error: %s\n", strerror(errno));
            LOGE("socket error: %s\n", strerror(errno));
            return false;
        }

        // 解决在close之后会有一个WAIT_TIME,导致bind失败的问题
        int val = 1;// -1
        LOGI("MediaServer setsockopt(...) start\n");
        err = setsockopt(server_sock_fd,
                         SOL_SOCKET,
                         SO_REUSEADDR,
                         (void *) &val,
                         sizeof(int));
        LOGI("MediaServer setsockopt(...) end\n");
        if (err == -1) {
            fprintf(stderr, "setsockopt error: %s\n", strerror(errno));
            LOGE("setsockopt error: %s\n", strerror(errno));
            close_server_sock(&server_sock_fd);
            return false;
        }

        /////////////////////////////bind绑定过程/////////////////////////////

        memset(&server_addr, 0, sizeof(struct sockaddr_in));
        memset(&client_addr1, 0, sizeof(struct sockaddr_in));
        memset(&client_addr2, 0, sizeof(struct sockaddr_in));
        // 设置服务端地址
        // 清零
        // memset(&server_addr, 0, sizeof(server_addr));
        bzero(&server_addr, sizeof(server_addr));
        // 将server_addr.sin_zero保留字段置为0
        bzero(&(server_addr.sin_zero), 8);
        // 设置协议族,意思就是打算采用什么协议族.跟socket()函数中的__domain参数要一致
        server_addr.sin_family = AF_INET;
        // 服务器端口(65535).地址结构的端口地址,网络字节序(PORT为主机字节序,需要转化为网络字节序)
        // 我想使用这个端口之前,应不应该判断一下这个端口可不可以使用,有没有被其他进程占用
        server_addr.sin_port = htons(SERVER_PROT);
        // 设置为本地地址
        server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
        // 将字符串的IP地址转化为网络字节序
        // server_addr.sin_addr.s_addr = inet_addr(IP);

        // 绑定地址结构到套接字描述符
        LOGI("MediaServer bind(...) start\n");
        err = bind(server_sock_fd, (struct sockaddr *) &server_addr, sizeof(struct sockaddr));
        LOGI("MediaServer bind(...) end\n");
        if (err == -1) {
            fprintf(stderr, "bind error: %s\n", strerror(errno));
            LOGE("bind error: %s\n", strerror(errno));
            close_server_sock(&server_sock_fd);
            continue;
        }

        /////////////////////////////listen监听过程/////////////////////////////

        // 设置侦听
        LOGI("MediaServer listen(...) start\n");
        err = listen(server_sock_fd, BACKLOG);
        LOGI("MediaServer listen(...) end\n");
        if (err == -1) {
            fprintf(stderr, "listen error: %s\n", strerror(errno));
            LOGE("listen error: %s\n", strerror(errno));
            close_server_sock(&server_sock_fd);
            return false;
        }

        break;
    }
    LOGD("MediaServer server_bind() SERVER_PROT: %d\n", SERVER_PROT);
    LOGI("MediaServer server_bind() end\n");
    return true;
}

void server_accept() {
    LOGI("MediaServer accept() start\n");
    LOGI("MediaServer accept() IP: %s\n", IP);

    if (!server_bind()) {
        LOGE("MediaServer server_bind() failed\n");
        return;
    }

    /////////////////////////////appept连接过程/////////////////////////////

    server_is_live = true;
    while (server_is_live) {
        struct sockaddr_in client_addr;
        socklen_t addrlen = sizeof(struct sockaddr);
        memset(&client_addr, 0, sizeof(struct sockaddr_in));
        // 接收客户端连接
        LOGI("MediaServer accept(...) start\n");
        int client_sock_fd = accept(server_sock_fd,
                                    (struct sockaddr *) &client_addr,
                                    &addrlen);
        LOGI("MediaServer accept(...) end\n");
        if (client_sock_fd == -1) {
            fprintf(stderr, "accept error: %s ss: %d\n", strerror(errno), server_sock_fd);
            LOGE("accept error: %s ss: %d\n", strerror(errno), server_sock_fd);
            // 当客户端退出时,这里会报错,但是服务端并没有停止
            continue;
        }

        if (!server_is_live) {
            break;
        }

        int which_client = 0;
        bool onlyOne = true;
        if (what_is_device != 4) {
            // not TV

        } else {
            // TV
            if (MAXIMUM_NUMBER == 1) {

            } else {
                onlyOne = false;
            }
        }

        if (onlyOne) {
            LOGI("MediaServer accept() one device\n");
            if (client_sock_fd1 == -1) {
                which_client = 1;
                client_addr1 = client_addr;
                client_sock_fd1 = client_sock_fd;
                LOGI("MediaServer accept() 1\n");
            } else {
                // 替换过程
                close_client_sock(&client_sock_fd1);
                sleep(3);
                which_client = 1;
                client_addr1 = client_addr;
                client_sock_fd1 = client_sock_fd;
                LOGI("MediaServer accept() 1 replace\n");
            }
        } else {
            LOGI("MediaServer accept() two devices\n");
            if (client_sock_fd1 == -1) {
                which_client = 1;
                client_addr1 = client_addr;
                client_sock_fd1 = client_sock_fd;
                LOGI("MediaServer accept() 1\n");
            } else if (client_sock_fd2 == -1) {
                which_client = 2;
                client_addr2 = client_addr;
                client_sock_fd2 = client_sock_fd;
                LOGI("MediaServer accept() 2\n");
            } else {
                // 替换过程(替换哪一个)
                switch (which_replace) {
                    case 1: {
                        close_client_sock(&client_sock_fd1);
                        sleep(3);
                        which_client = 1;
                        client_addr1 = client_addr;
                        client_sock_fd1 = client_sock_fd;
                        LOGI("MediaServer accept() 1 replace\n");
                        break;
                    }
                    case 2: {
                        close_client_sock(&client_sock_fd2);
                        sleep(3);
                        which_client = 2;
                        client_addr2 = client_addr;
                        client_sock_fd2 = client_sock_fd;
                        LOGI("MediaServer accept() 2 replace\n");
                        break;
                    }
                    default:
                        break;
                }
            }
        }
        LOGI("MediaServer accept() client_sock_fd1: %d\n", client_sock_fd1);
        LOGI("MediaServer accept() client_sock_fd2: %d\n", client_sock_fd2);

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
        pthread_create(&p_tids_receive_data, &attr, receive_data, &which_client);

        // 第一种得到客户端ip地址的方式
        char *ip = inet_ntoa(client_addr.sin_addr);
        // 第二种得到客户端ip地址的方式
        char client_ip[20];
        socklen_t client_addr_len = sizeof(client_addr);
        getpeername(client_sock_fd, (sockaddr *) &client_addr, &client_addr_len);
        inet_ntop(AF_INET, &client_addr.sin_addr, client_ip, sizeof(client_ip));

        LOGI("客户端ip: %s\n", ip);
        LOGI("客户端ip: %s\n", client_ip);

        // 把主机信息保存在hostent中
        struct hostent *hptr = gethostbyaddr(
                (void *) &client_addr.sin_addr, sizeof(client_addr.sin_addr), AF_INET);
        if (hptr == NULL) {
            printf("h_errno: %d\n", h_errno);
            // LOGI("h_errno: %d\n", h_errno);// 1
        } else {
            // 正式主机名
            printf("主机名(HostName): %s\n", hptr->h_name);
            LOGI("主机名(HostName): %s\n", hptr->h_name);
        }
    }

    sleep(3);

    server_close_all();
    LOGI("MediaServer accept() end\n");
}

// which_client: 1 2 3
void *receive_data(void *arg) {
    int *argc = static_cast<int *>(arg);

    // region 分配变量
    int which_client = 0;
    int client_sock_fd = 0;
    ssize_t want_to_read_length = 0;
    uint8_t *read_buffer = NULL;
    uint8_t *data_buffer = NULL;
    switch (*argc) {
        case 1: {
            which_client = 1;
            client_sock_fd = client_sock_fd1;
            read_buffer = read_buffer1;
            data_buffer = data_buffer1;
            client_info_length1 = 0;
            memset(client_info1, 0, sizeof(client_info1));
            break;
        }
        case 2: {
            which_client = 2;
            client_sock_fd = client_sock_fd2;
            read_buffer = read_buffer2;
            data_buffer = data_buffer2;
            client_info_length2 = 0;
            memset(client_info2, 0, sizeof(client_info2));
            break;
        }
        default:
            return NULL;
    }
    // endregion

    // 通知Java有设备连接上了
    LOGI("receive_data() connect which_client: %d\n", which_client);
    connect(which_client);
    LOGI("MediaServer receive_data() client_sock_fd1: %d\n", client_sock_fd1);
    LOGI("MediaServer receive_data() client_sock_fd2: %d\n", client_sock_fd2);

    // region 读取客户端的"设备名称"
    int width = 0;
    int height = 0;
    int orientation = 1;// 初始化时投屏端设备的方向(1为竖屏,2为横屏)
    char client_info[1024];
    char temp_client_info[1024];
    memset(client_info, 0, sizeof(client_info));
    memset(temp_client_info, 0, sizeof(temp_client_info));
    int ret = read_data(client_sock_fd, 4, read_buffer, data_buffer);
    LOGI("receive_data() ret: %d\n", ret);
    if (ret == EXIT_SUCCESS) {
        want_to_read_length = uint8tToInt(data_buffer);
        LOGI("receive_data() want_to_read_length: %d\n", want_to_read_length);
        if (want_to_read_length > 0) {
            ret = read_data(
                    client_sock_fd, want_to_read_length, temp_client_info, client_info);
            LOGI("receive_data() ret: %d\n", ret);
            if (ret == EXIT_SUCCESS) {
                switch (which_client) {
                    case 1: {
                        client_info_length1 = want_to_read_length;
                        memcpy(client_info1, client_info, want_to_read_length);
                        break;
                    }
                    case 2: {
                        client_info_length2 = want_to_read_length;
                        memcpy(client_info2, client_info, want_to_read_length);
                        break;
                    }
                    default:
                        break;
                }
                // device_name: ARS-AL00   device_name_length: 8
                // device_name: OnePlus 5T device_name_length: 10
                LOGI("receive_data() which_client: %d client_info: %s client_info_length: %d\n",
                     which_client, client_info, want_to_read_length);
                setData(DO_SOMETHING_CODE_Client_set_info,
                        which_client, client_info, want_to_read_length);
                if (MEDIA_CODEC_GO_JNI) {
                }
                // ARS-AL00@@@@@video/hevc@@@@@1080@@@@@2244@@@@@1
                // 需要知道开始投屏时的方向
                int count = 0;
                char *p = nullptr;
                char *buff = nullptr;
                buff = client_info;
                p = strsep(&buff, "@@@@@");
                while (p != nullptr) {
                    size_t length = strlen(p);
                    if (length > 0) {
                        ++count;
                        LOGI("receive_data() p: %s %d\n", p, length);
                        if (count == 1) {
                            // ARS-AL00
                        } else if (count == 2) {
                            // video/hevc
                            char *name = findDecoderCodecName(
                                    DO_SOMETHING_CODE_find_decoder_codec_name,
                                    which_client, p, length);
                            if (which_client == 1) {
                                mimeLength1 = length;
                                codecNameLength1 = strlen(name);
                                memcpy(mime1, p, mimeLength1);
                                memcpy(codecName1, name, codecNameLength1);
                            } else if (which_client == 2) {
                                mimeLength2 = length;
                                codecNameLength2 = strlen(name);
                                memcpy(mime2, p, mimeLength2);
                                memcpy(codecName2, name, codecNameLength2);
                            }
                            LOGI("receive_data() codecName: %s\n", name);
                        } else if (count == 3) {
                            // 1080
                            width = atoi(p);
                            if (which_client == 1) {
                                width1 = width;
                            } else if (which_client == 2) {
                                width2 = width;
                            }
                        } else if (count == 4) {
                            // 2244
                            height = atoi(p);
                            if (which_client == 1) {
                                height1 = height;
                            } else if (which_client == 2) {
                                height2 = height;
                            }
                        } else if (count == 5) {
                            // 1
                            orientation = atoi(p);
                            if (which_client == 1) {
                                orientation1 = orientation;
                            } else if (which_client == 2) {
                                orientation2 = orientation;
                            }
                        }
                    }
                    p = strsep(&buff, "@@@@@");
                }
            }
        }
    }
    // endregion

    // region 先读取sps_pps数据
    if (MEDIA_CODEC_GO_JNI) {
        // 第一个sps_pps
        ret = read_data(client_sock_fd, 4, read_buffer, data_buffer);
        if (ret == EXIT_SUCCESS) {
            want_to_read_length = uint8tToInt(data_buffer);
            ret = read_data(client_sock_fd, want_to_read_length, read_buffer, data_buffer);
            if (ret == EXIT_SUCCESS) {
                set_sps_pps(which_client, orientation, data_buffer, want_to_read_length);
            }
        }
        // 第二个sps_pps
        ret = read_data(client_sock_fd, 4, read_buffer, data_buffer);
        if (ret == EXIT_SUCCESS) {
            want_to_read_length = uint8tToInt(data_buffer);
            ret = read_data(client_sock_fd, want_to_read_length, read_buffer, data_buffer);
            if (ret == EXIT_SUCCESS) {
                set_sps_pps(which_client, orientation, data_buffer, want_to_read_length);
            }
        }

        createMediaCodec(which_client);
        createMediaFormat(which_client, orientation);
    }
    // endregion

    LOGI("receive_data() start which_client: %d\n", which_client);
    // region 读取数据
    for (;;) {
        // 从套接字中读取数据放到缓冲区buffer中
        //memset(buffer, 0, sizeof(buffer));
        ret = read_data(client_sock_fd, 4, read_buffer, data_buffer);
        if (ret == EXIT_SUCCESS) {
            want_to_read_length = uint8tToInt(data_buffer);
            if (want_to_read_length <= 0) {
                LOGI("receive_data() which_client: %d want_to_read_length: %d\n",
                     which_client, want_to_read_length);
                continue;
            }
            ret = read_data(client_sock_fd, want_to_read_length, read_buffer, data_buffer);
            if (ret == EXIT_SUCCESS) {
                //LOGI("receive_data() data_buffer[0]: %d\n", data_buffer[0]);
                if (data_buffer[0] == 255) {
                    LOGI("receive_data() which_client: %d orientation: 1\n", which_client);
                    changeWindow(which_client, 1);
                    if (MEDIA_CODEC_GO_JNI) {
                        set_orientation(which_client, 1);
                    }
                    continue;
                } else if (data_buffer[0] == 254) {
                    LOGI("receive_data() which_client: %d orientation: 2\n", which_client);
                    changeWindow(which_client, 2);
                    if (MEDIA_CODEC_GO_JNI) {
                        set_orientation(which_client, 2);
                    }
                    continue;
                }

                if (MEDIA_CODEC_GO_JNI) {
                    putData(which_client, data_buffer, want_to_read_length);
                } else {
                    // data_buffer want_to_read_length
                    // 把data_buffer传递到java层
                    putDataToJava(which_client, data_buffer, want_to_read_length);
                }
                continue;
            } else {
                LOGE("receive_data() break 1 which_client: %d\n", which_client);
                break;
            }
        } else {
            LOGE("receive_data() break 2 which_client: %d\n", which_client);
            break;
        }
    }
    // endregion
    LOGI("receive_data() end   which_client: %d\n", which_client);

    switch (which_client) {
        case 1: {
            close_client_sock(&client_sock_fd1);
            if (MEDIA_CODEC_GO_JNI) {
                free1();
            }
            break;
        }
        case 2: {
            close_client_sock(&client_sock_fd2);
            if (MEDIA_CODEC_GO_JNI) {
                free2();
            }
            break;
        }
    }
    LOGI("receive_data() which_client: %d client_sock_fd: %d\n", which_client, client_sock_fd);

    // 通知Java有设备断开连接了
    LOGI("receive_data() disconnect which_client: %d\n", which_client);
    disconnect(which_client);
}

void server_close() {
    if (!server_is_live) {
        return;
    }
    server_is_live = false;
    client_connect();
}

void close_all_clients() {
    close_client_sock(&client_sock_fd1);
    close_client_sock(&client_sock_fd2);
}

void close_client(int which_client) {
    switch (which_client) {
        case 1: {
            close_client_sock(&client_sock_fd1);
            break;
        }
        case 2: {
            close_client_sock(&client_sock_fd2);
            break;
        }
        default:
            break;
    }
}