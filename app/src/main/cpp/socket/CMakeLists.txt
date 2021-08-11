# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)
#add_definitions(-std=c++11)

set(CMAKE_BUILD_TYPE Debug)
#set(CMAKE_BUILD_TYPE Release)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

# 哪些cpp文件生成什么样的动态库
# 一个CMakeLists.txt只能生成一个动态库

#################################################################
# 引用头文件(所有头文件放在include这个目录)
include_directories(../include/)
#################################################################
# 找到所有cpp文件
# file(GLOB SOURCE src/main/cpp/*.cpp)
add_library(socket

        SHARED

        MyJni.cpp
        MediaServer.cpp
        MediaClient.cpp
        MediaCodec.cpp
        MediaData.cpp

        )
################################FFMPEG################################

# 第三方库使用add_library(...)和set_target_properties(...)
# Android平台上已存在的预构建的NDK库使用find_library(...)和target_link_libraries(...)

find_library(log-lib
        log)
find_library(android-lib
        android)

#[[find_library(audioclient-lib
        audioclient)
find_library(binder-lib
        binder)
find_library(cutils-lib
        cutils)
find_library(utils-lib
        utils)]]

#################################################################
# 关联库 # ffmpeg
# 好像只能出现一个target_link_libraries(...)
target_link_libraries(
        # Specifies the target library.
        socket

        # ${log-lib}是log库的路径
        ${log-lib}
        ${android-lib}

        #[[${audioclient-lib}
        ${binder-lib}
        ${cutils-lib}
        ${utils-lib}]]

        # 编译当前库时需要引用的第三方库
)
#################################################################

# 设置cpp目录路径
set(CPP_DIR ${CMAKE_SOURCE_DIR}/src/main/cpp)

# 设置jniLibs目录路径(jniLibs ---> nativeLibs)
set(LIBS_DIR ${CMAKE_SOURCE_DIR}/src/main/nativeLibs)

# 设置CPU目录
#if (${ANDROID_ABI} STREQUAL "armeabi")
#    set(CPU_DIR armeabi)
#endif (${ANDROID_ABI} STREQUAL "armeabi")

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(CPU_DIR arm64-v8a)
endif (${ANDROID_ABI} STREQUAL "arm64-v8a")

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(CPU_DIR armeabi-v7a)
endif (${ANDROID_ABI} STREQUAL "armeabi-v7a")

if (${ANDROID_ABI} STREQUAL "x86_64")
    set(CPU_DIR x86_64)
endif (${ANDROID_ABI} STREQUAL "x86_64")

if (${ANDROID_ABI} STREQUAL "x86")
    set(CPU_DIR x86)
endif (${ANDROID_ABI} STREQUAL "x86")

#################################################################
# 增加和引用第三方库(动态库)

# 添加库
#add_library(
#        # 库名称
#        ijkffmpeg
#        # 动态库
#        SHARED
#        # 表示该库是引用的不是生成的
#        IMPORTED)
# 引用库
#set_target_properties(
#        # 库名称
#        ijkffmpeg
#        # 库的路径
#        PROPERTIES IMPORTED_LOCATION
#        ${LIBS_DIR}/${CPU_DIR}/libijkffmpeg.so)
#################################################################