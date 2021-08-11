//
// Created by alexander on 2019/12/6.
//

#ifndef USEFRAGMENTS_LOG_H
#define USEFRAGMENTS_LOG_H

#include <android/log.h>

#define NEED_LOG

#ifdef NEED_LOG
// 打印日志
#   define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG,__VA_ARGS__)  // 定义LOGI类型
#   define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG,__VA_ARGS__) // 定义LOGD类型
#   define LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG,__VA_ARGS__)  // 定义LOGW类型
#   define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG,__VA_ARGS__) // 定义LOGE类型
#   define LOGF(...)  __android_log_print(ANDROID_LOG_FATAL,LOG,__VA_ARGS__) // 定义LOGF类型
#else
// 不打印日志
#   define LOGD(...)
#   define LOGI(...)
#   define LOGW(...)
#   define LOGE(...)
#   define LOGF(...)
#endif

#endif //USEFRAGMENTS_LOG_H
