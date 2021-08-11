# 根目录的CMakeLists.txt文件中主要负责：
# 1.声明CMake最低版本
# 2.编译出的so库文件存放的位置
# 3.各个子目录的路径

cmake_minimum_required(VERSION 3.4.1)
#add_definitions(-std=c++11)

# set(CMAKE_CXX_FLAGS "-Wno-error=format-security -Wno-error=pointer-sign"

# set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${PROJECT_SOURCE_DIR}/jniLibs/${ANDROID_ABI})

# 设置头文件搜索路径（和此txt同个路径的头文件无需设置）,可选
# INCLUDE_DIRECTORIES(${CMAKE_CURRENT_SOURCE_DIR}/common)

# 指定用到的系统库或者NDK库或者第三方库的搜索路径,可选
# LINK_DIRECTORIES(/usr/local/lib)

# 添加子目录,将自动找到子目录中的CMakeLists.txt
ADD_SUBDIRECTORY(${PROJECT_SOURCE_DIR}/src/main/cpp/socket)
#ADD_SUBDIRECTORY(${PROJECT_SOURCE_DIR}/src/main/cpp/screenrecord)
#################################################################