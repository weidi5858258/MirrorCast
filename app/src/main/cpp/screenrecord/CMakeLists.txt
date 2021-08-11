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

set(ANDROID_INCLUDE_PATH
        /root/mydev/android_source/valhallaR/android/r-sony)

# 找到所有cpp文件
# file(GLOB SOURCE src/main/cpp/*.cpp)
add_library(screenrecord

        SHARED

#        binder/ActivityManager.h
#        binder/AppOpsManager.h
#        binder/Binder.h
#        binder/BinderService.h
#        binder/BpBinder.h
#        binder/Debug.h
#        binder/Enums.h
#        binder/IActivityManager.h
#        binder/IAppOpsCallback.h
#        binder/IAppOpsService.h
#        binder/IBatteryStats.h
#        binder/IBinder.h
#        binder/IInterface.h
#        binder/IMediaResourceMonitor.h
#        binder/IMemory.h
#        binder/IPCThreadState.h
#        binder/IPermissionController.h
#        binder/IpPrefix.h
#        binder/IProcessInfoService.h
#        binder/IResultReceiver.h
#        binder/IServiceManager.h
#        binder/IShellCallback.h
#        binder/IUidObserver.h
#        binder/LazyServiceRegistrar.h
#        binder/MemoryBase.h
#        binder/MemoryDealer.h
#        binder/MemoryHeapBase.h
#        binder/Nullable.h
#        binder/Parcel.h
#        binder/Parcelable.h
#        binder/ParcelFileDescriptor.h
#        binder/PermissionCache.h
#        binder/PermissionController.h
#        binder/PersistableBundle.h
#        binder/ProcessInfoService.h
#        binder/ProcessState.h
#        binder/SafeInterface.h
#        binder/Stability.h
#        binder/Status.h
#        binder/TextOutput.h
#
#        cutils/android_filesystem_config.h
#        cutils/android_get_control_file.h
#        cutils/android_reboot.h
#        cutils/ashmem.h
#        cutils/atomic.h
#        cutils/bitops.h
#        cutils/compiler.h
#        cutils/config_utils.h
#        cutils/fs.h
#        cutils/hashmap.h
#        cutils/iosched_policy.h
#        cutils/klog.h
#        cutils/list.h
#        cutils/log.h
#        cutils/memory.h
#        cutils/misc.h
#        cutils/multiuser.h
#        cutils/native_handle.h
#        cutils/partition_utils.h
#        cutils/properties.h
#        cutils/qtaguid.h
#        cutils/record_stream.h
#        cutils/sched_policy.h
#        cutils/sockets.h
#        cutils/str_parms.h
#        cutils/threads.h
#        cutils/trace.h
#        cutils/uevent.h
#
#        gui/BLASTBufferQueue.h
#        gui/BufferHubConsumer.h
#        gui/BufferHubProducer.h
#        gui/BufferItem.h
#        gui/BufferItemConsumer.h
#        ${ANDROID_INCLUDE_PATH}/frameworks/native/libs/gui/include/gui/BufferQueue.h
#        gui/BufferQueueConsumer.h
#        gui/BufferQueueCore.h
#        gui/BufferQueueDefs.h
#        gui/BufferQueueProducer.h
#        gui/BufferSlot.h
#        gui/ConsumerBase.h
#        gui/CpuConsumer.h
#        gui/DebugEGLImageTracker.h
#        gui/DisplayEventDispatcher.h
#        gui/DisplayEventReceiver.h
#        gui/FrameTimestamps.h
#        gui/GLConsumer.h
#        gui/GuiConfig.h
#        gui/HdrMetadata.h
#        gui/IConsumerListener.h
#        gui/IDisplayEventConnection.h
#        gui/IGraphicBufferConsumer.h
#        gui/IGraphicBufferProducer.h
#        gui/IProducerListener.h
#        gui/IRegionSamplingListener.h
#        gui/ISurfaceComposer.h
#        gui/ISurfaceComposerClient.h
#        gui/ITransactionCompletedListener.h
#        gui/LayerDebugInfo.h
#        gui/LayerMetadata.h
#        gui/LayerState.h
#        gui/OccupancyTracker.h
#        gui/StreamSplitter.h
#        gui/Surface.h
#        gui/SurfaceComposerClient.h
#        gui/SurfaceControl.h
#
#        media/AVSyncSettings.h
#        media/BufferingSettings.h
#        media/CharacterEncodingDetector.h
#        media/CodecServiceRegistrant.h
#        media/CounterMetric.h
#        media/EventMetric.h
#        media/IMediaCodecList.h
#        media/IMediaDeathNotifier.h
#        media/IMediaHTTPConnection.h
#        media/IMediaHTTPService.h
#        media/IMediaLogService.h
#        media/IMediaMetadataRetriever.h
#        media/IMediaPlayer.h
#        media/IMediaPlayerClient.h
#        media/IMediaPlayerService.h
#        media/IMediaRecorder.h
#        media/IMediaRecorderClient.h
#        media/IMediaSource.h
#        media/IOMX.h
#        media/IRemoteDisplay.h
#        media/IRemoteDisplayClient.h
#        media/IStreamSource.h
#        media/JAudioAttributes.h
#        media/JAudioFormat.h
#        media/MediaBufferHolder.h
#        media/MediaCodecBuffer.h
#        media/MediaCodecInfo.h
#        media/MediaHTTPConnection.h
#        media/MediaHTTPService.h
#        media/mediametadataretriever.h
#        media/MediaMetadataRetrieverInterface.h
#        media/mediaplayer.h
#        media/mediaplayer_common.h
#        media/MediaProfiles.h
#        media/mediarecorder.h
#        media/MediaRecorderBase.h
#        media/MediaResource.h
#        media/MediaResourcePolicy.h
#        media/mediascanner.h
#        media/Metadata.h
#        media/MidiIoWrapper.h
#        media/Modulo.h
#        media/NdkMediaFormatPriv.h
#        media/NdkWrapper.h
#        media/OMXBuffer.h
#        media/OMXFenceParcelable.h
#        media/PatchBuilder.h
#        media/PluginLoader.h
#        media/PluginMetricsReporting.h
#        media/RingBuffer.h
#        media/StringArray.h
#        media/openmax/OMX_AsString.h
#        media/openmax/OMX_Audio.h
#        media/openmax/OMX_AudioExt.h
#        media/openmax/OMX_Component.h
#        media/openmax/OMX_ContentPipe.h
#        media/openmax/OMX_Core.h
#        media/openmax/OMX_Image.h
#        media/openmax/OMX_Index.h
#        media/openmax/OMX_IndexExt.h
#        media/openmax/OMX_IVCommon.h
#        media/openmax/OMX_Other.h
#        media/openmax/OMX_Types.h
#        media/openmax/OMX_Video.h
#        media/openmax/OMX_VideoExt.h
#        media/stagefright/AACWriter.h
#        media/stagefright/ACodec.h
#        media/stagefright/AHierarchicalStateMachine.h
#        media/stagefright/AMRWriter.h
#        media/stagefright/AudioSource.h
#        media/stagefright/BatteryChecker.h
#        media/stagefright/BufferProducerWrapper.h
#        media/stagefright/CallbackMediaSource.h
#        media/stagefright/CameraSource.h
#        media/stagefright/CameraSourceTimeLapse.h
#        media/stagefright/CodecBase.h
#        media/stagefright/ColorConverter.h
#        media/stagefright/DataSourceBase.h
#        media/stagefright/FoundationUtils.h
#        media/stagefright/FrameCaptureProcessor.h
#        media/stagefright/FrameRenderTracker.h
#        media/stagefright/InterfaceUtils.h
#        media/stagefright/JPEGSource.h
#        media/stagefright/MediaAdapter.h
#        media/stagefright/MediaBuffer.h
#        media/stagefright/MediaBufferBase.h
#        media/stagefright/MediaBufferGroup.h
#        media/stagefright/MediaClock.h
#        media/stagefright/MediaCodec.h
#        media/stagefright/MediaCodecConstants.h
#        media/stagefright/MediaCodecList.h
#        media/stagefright/MediaCodecListWriter.h
#        media/stagefright/MediaCodecSource.h
#        media/stagefright/MediaDefs.h
#        media/stagefright/MediaErrors.h
#        media/stagefright/MediaExtractor.h
#        media/stagefright/MediaExtractorFactory.h
#        media/stagefright/MediaFilter.h
#        media/stagefright/MediaMuxer.h
#        media/stagefright/MediaSource.h
#        media/stagefright/MediaSync.h
#        media/stagefright/MediaWriter.h
#        media/stagefright/MetaData.h
#        media/stagefright/MetaDataBase.h
#        media/stagefright/MetaDataUtils.h
#        media/stagefright/MPEG2TSWriter.h
#        media/stagefright/MPEG4Writer.h
#        media/stagefright/NuMediaExtractor.h
#        media/stagefright/OggWriter.h
#        media/stagefright/OMXClient.h
#        media/stagefright/OmxInfoBuilder.h
#        media/stagefright/PersistentSurface.h
#        media/stagefright/ProcessInfo.h
#        media/stagefright/ProcessInfoInterface.h
#        media/stagefright/RemoteDataSource.h
#        media/stagefright/RemoteMediaExtractor.h
#        media/stagefright/RemoteMediaSource.h
#        media/stagefright/RenderScriptWrapper.h
#        media/stagefright/SimpleDecodingSource.h
#        media/stagefright/SkipCutBuffer.h
#        media/stagefright/StagefrightMediaScanner.h
#        media/stagefright/SurfaceUtils.h
#        media/stagefright/Utils.h
#        media/stagefright/VideoFrameScheduler.h
#        media/stagefright/VideoFrameScheduler2.h
#        media/stagefright/VideoFrameSchedulerBase.h
#        media/stagefright/YUVCanvas.h
#        media/stagefright/YUVImage.h
#        media/stagefright/foundation/AAtomizer.h
#        media/stagefright/foundation/ABase.h
#        media/stagefright/foundation/ABitReader.h
#        media/stagefright/foundation/ABuffer.h
#        media/stagefright/foundation/AData.h
#        media/stagefright/foundation/ADebug.h
#        media/stagefright/foundation/AHandler.h
#        media/stagefright/foundation/AHandlerReflector.h
#        media/stagefright/foundation/ALookup.h
#        media/stagefright/foundation/ALooper.h
#        media/stagefright/foundation/ALooperRoster.h
#        media/stagefright/foundation/AMessage.h
#        media/stagefright/foundation/AString.h
#        media/stagefright/foundation/AStringUtils.h
#        media/stagefright/foundation/AudioPresentationInfo.h
#        media/stagefright/foundation/AUtils.h
#        media/stagefright/foundation/avc_utils.h
#        media/stagefright/foundation/base64.h
#        media/stagefright/foundation/ByteUtils.h
#        media/stagefright/foundation/ColorUtils.h
#        media/stagefright/foundation/FileDescriptor.h
#        media/stagefright/foundation/Flagged.h
#        media/stagefright/foundation/hexdump.h
#        media/stagefright/foundation/MediaDefs.h
#        media/stagefright/foundation/MediaKeys.h
#        media/stagefright/foundation/Mutexed.h
#        media/stagefright/foundation/OpusHeader.h
#        media/stagefright/foundation/TypeTraits.h
#
#        mediadrm/DrmUtils.h
#        mediadrm/ICrypto.h
#
#        ui/ANativeObjectBase.h
#        ui/BufferHubDefs.h
#        ui/BufferQueueDefs.h
#        ui/ColorSpace.h
#        ui/ConfigStoreTypes.h
#        ui/DebugUtils.h
#        ui/DeviceProductInfo.h
#        ui/DisplayConfig.h
#        ui/DisplayedFrameStats.h
#        ui/DisplayInfo.h
#        ui/DisplayState.h
#        ui/DisplayStatInfo.h
#        ui/FatVector.h
#        ui/Fence.h
#        ui/FenceTime.h
#        ui/FloatRect.h
#        ui/FrameStats.h
#        ui/Gralloc.h
#        ui/Gralloc2.h
#        ui/Gralloc3.h
#        ui/Gralloc4.h
#        ui/GraphicBuffer.h
#        ui/GraphicBufferAllocator.h
#        ui/GraphicBufferMapper.h
#        ui/GraphicTypes.h
#        ui/HdrCapabilities.h
#        ui/PhysicalDisplayId.h
#        ui/PixelFormat.h
#        ui/Point.h
#        ui/PublicFormat.h
#        ui/Rect.h
#        ui/Region.h
#        ui/Rotation.h
#        ui/Size.h
#        ui/Transform.h
#        ui/UiConfig.h
#
#        utils/AndroidThreads.h
#        utils/Atomic.h
#        utils/BitSet.h
#        utils/ByteOrder.h
#        utils/CallStack.h
#        utils/Compat.h
#        utils/Condition.h
#        utils/Debug.h
#        utils/Endian.h
#        utils/Errors.h
#        utils/FastStrcmp.h
#        utils/FileMap.h
#        utils/Flattenable.h
#        utils/Functor.h
#        utils/JenkinsHash.h
#        utils/KeyedVector.h
#        utils/LightRefBase.h
#        utils/List.h
#        utils/Log.h
#        utils/Looper.h
#        utils/LruCache.h
#        utils/misc.h
#        utils/Mutex.h
#        utils/NativeHandle.h
#        utils/Printer.h
#        utils/ProcessCallStack.h
#        utils/PropertyMap.h
#        utils/RefBase.h
#        utils/RWLock.h
#        utils/Singleton.h
#        utils/SortedVector.h
#        utils/StopWatch.h
#        utils/String8.h
#        utils/String16.h
#        utils/StrongPointer.h
#        utils/SystemClock.h
#        utils/Thread.h
#        utils/ThreadDefs.h
#        utils/threads.h
#        utils/Timers.h
#        utils/Tokenizer.h
#        utils/Trace.h
#        utils/TypeHelpers.h
#        utils/Unicode.h
#        utils/Vector.h
#        utils/VectorImpl.h

        EglWindow.h
        FontBitmap.h
        FrameOutput.h
        Overlay.h
        Program.h
        screenrecord.h
        TextRenderer.h

        EglWindow.cpp
        FrameOutput.cpp
        Overlay.cpp
        Program.cpp
        TextRenderer.cpp
        screenrecord.cpp

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
        screenrecord

        # ${log-lib}是log库的路径
        ${log-lib}
        ${android-lib}

        #[[${audioclient-lib}
        ${binder-lib}
        ${cutils-lib}
        ${utils-lib}]]

        # 编译当前库时需要引用的第三方库
        binder
        cutils
        EGL
        GLESv2
        gui
        jpeg
        media
        media_omx
        mediandk
        stagefright
        stagefright_foundation
        ui
        utils
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

#if (${ANDROID_ABI} STREQUAL "arm64-v8a")
#    set(CPU_DIR arm64-v8a)
#endif (${ANDROID_ABI} STREQUAL "arm64-v8a")

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(CPU_DIR armeabi-v7a)
endif (${ANDROID_ABI} STREQUAL "armeabi-v7a")

#if (${ANDROID_ABI} STREQUAL "x86_64")
#    set(CPU_DIR x86_64)
#endif (${ANDROID_ABI} STREQUAL "x86_64")

#if (${ANDROID_ABI} STREQUAL "x86")
#    set(CPU_DIR x86)
#endif (${ANDROID_ABI} STREQUAL "x86")

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
add_library(binder
        SHARED
        IMPORTED)
set_target_properties(binder
        PROPERTIES IMPORTED_LOCATION
        ${LIBS_DIR}/${CPU_DIR}/libbinder.so)

add_library(cutils
        SHARED
        IMPORTED)
set_target_properties(cutils
        PROPERTIES IMPORTED_LOCATION
        ${LIBS_DIR}/${CPU_DIR}/libcutils.so)

add_library(EGL
        SHARED
        IMPORTED)
set_target_properties(EGL
        PROPERTIES IMPORTED_LOCATION
        ${LIBS_DIR}/${CPU_DIR}/libEGL.so)

add_library(GLESv2
        SHARED
        IMPORTED)
set_target_properties(GLESv2
        PROPERTIES IMPORTED_LOCATION
        ${LIBS_DIR}/${CPU_DIR}/libGLESv2.so)

add_library(gui
        SHARED
        IMPORTED)
set_target_properties(gui
        PROPERTIES IMPORTED_LOCATION
        ${LIBS_DIR}/${CPU_DIR}/libgui.so)

add_library(jpeg
        SHARED
        IMPORTED)
set_target_properties(jpeg
        PROPERTIES IMPORTED_LOCATION
        ${LIBS_DIR}/${CPU_DIR}/libjpeg.so)

add_library(media
        SHARED
        IMPORTED)
set_target_properties(media
        PROPERTIES IMPORTED_LOCATION
        ${LIBS_DIR}/${CPU_DIR}/libmedia.so)

add_library(media_omx
        SHARED
        IMPORTED)
set_target_properties(media_omx
        PROPERTIES IMPORTED_LOCATION
        ${LIBS_DIR}/${CPU_DIR}/libmedia_omx.so)

add_library(mediandk
        SHARED
        IMPORTED)
set_target_properties(mediandk
        PROPERTIES IMPORTED_LOCATION
        ${LIBS_DIR}/${CPU_DIR}/libmediandk.so)

add_library(stagefright
        SHARED
        IMPORTED)
set_target_properties(stagefright
        PROPERTIES IMPORTED_LOCATION
        ${LIBS_DIR}/${CPU_DIR}/libstagefright.so)

add_library(stagefright_foundation
        SHARED
        IMPORTED)
set_target_properties(stagefright_foundation
        PROPERTIES IMPORTED_LOCATION
        ${LIBS_DIR}/${CPU_DIR}/libstagefright_foundation.so)

add_library(ui
        SHARED
        IMPORTED)
set_target_properties(ui
        PROPERTIES IMPORTED_LOCATION
        ${LIBS_DIR}/${CPU_DIR}/libui.so)

add_library(utils
        SHARED
        IMPORTED)
set_target_properties(utils
        PROPERTIES IMPORTED_LOCATION
        ${LIBS_DIR}/${CPU_DIR}/libutils.so)