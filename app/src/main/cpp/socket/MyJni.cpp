//
// Created by ex-wangliwei on 2021/6/28.
//

#include <jni.h>
#include <string>
#include <sys/system_properties.h>
#include "include/Log.h"
#include "MyJni.h"
#include "MediaServer.h"
#include "MediaClient.h"
#include "MediaCodec.h"

// 这个是自定义的LOG的标识
#define LOG "player_alexander"

/***
 https://www.jianshu.com/p/259a31f628a4
 Android Studio+LLDB调试内核Binder

 https://www.jb51.net/article/183149.htm
 创建Android守护进程实例(底层服务)
 */

// 这个值在任何情况下都不要置为"NULL"
static JavaVM *gJavaVm = nullptr;

// JniObject的Class
jclass jniObject_jclass = nullptr;
jfieldID valueObject_jfieldID = nullptr;
jfieldID valueString_jfieldID = nullptr;
jfieldID valueInt_jfieldID = nullptr;
jfieldID valueLong_jfieldID = nullptr;
jfieldID valueByte_jfieldID = nullptr;
jfieldID valueBoolean_jfieldID = nullptr;
jfieldID valueFloat_jfieldID = nullptr;
jfieldID valueDouble_jfieldID = nullptr;
// array
jfieldID valueObjectArray_jfieldID = nullptr;
jfieldID valueStringArray_jfieldID = nullptr;
jfieldID valueIntArray_jfieldID = nullptr;
jfieldID valueLongArray_jfieldID = nullptr;
jfieldID valueByteArray_jfieldID = nullptr;
jfieldID valueBooleanArray_jfieldID = nullptr;
jfieldID valueFloatArray_jfieldID = nullptr;
jfieldID valueDoubleArray_jfieldID = nullptr;

// 下面的jobject,jmethodID按照java的反射过程去理解,套路(jni层调用java层方法)跟反射是一样的
// java层MyJni对象
jobject myJniJavaObject = nullptr;
jmethodID jni2JavaMethodID = nullptr;
jmethodID putDataMethodID = nullptr;

jobject serverFrame1Object = nullptr;
jobject serverFrame2Object = nullptr;
jobject clientFrameObject = nullptr;

extern int SERVER_PROT;
extern int what_is_device;
extern int MAXIMUM_NUMBER;
extern bool MEDIA_CODEC_GO_JNI;
extern bool isRecording;
extern bool ONLY_OUTPUT_KEY_FRAME;

/***
 called at the library loaded.
 这个方法只有放在这个文件里才有效,在其他文件不会被回调
 */
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGD("JNI_OnLoad()\n");
    gJavaVm = vm;
    /*int ret = av_jni_set_java_vm(vm, nullptr);
    if (ret < 0) {
        LOGE("JNI_OnLoad() av_jni_set_java_vm() error ret: %d\n", ret);
    }*/
    return JNI_VERSION_1_6;
}

// 这个方法只有放在这个文件里才有效,在其他文件调用失效
bool getEnv(JNIEnv **env) {
    bool isAttached = false;
    jint jResult = gJavaVm->GetEnv((void **) env, JNI_VERSION_1_6);
    if (jResult != JNI_OK) {
        if (jResult == JNI_EDETACHED) {
            if (gJavaVm->AttachCurrentThread(env, NULL) != JNI_OK) {
                LOGE("AttachCurrentThread Failed.\n");
                *env = nullptr;
                return isAttached;
            }
            isAttached = true;
        } else {
            LOGE("GetEnv Failed.\n");
            *env = nullptr;
            return isAttached;
        }
    }

    return isAttached;
}

void jni2Java(JNIEnv *jniEnv, int code, jobject jniObject) {
    if (jniEnv != nullptr
        && myJniJavaObject != nullptr
        && jni2JavaMethodID != nullptr) {
        jniEnv->CallVoidMethod(myJniJavaObject, jni2JavaMethodID, code, jniObject);
    }
}

void connect(int which_client) {
    JNIEnv *jniEnv;
    bool isAttached = getEnv(&jniEnv);
    jobject jniObject = jniEnv->AllocObject(jniObject_jclass);
    jniEnv->SetIntField(jniObject, valueInt_jfieldID, (jint) which_client);
    jniEnv->CallVoidMethod(myJniJavaObject, jni2JavaMethodID,
                           DO_SOMETHING_CODE_connected, jniObject);
    jniEnv->DeleteLocalRef(jniObject);
    if (isAttached) {
        gJavaVm->DetachCurrentThread();
    }
}

void disconnect(int which_client) {
    JNIEnv *jniEnv;
    bool isAttached = getEnv(&jniEnv);
    jobject jniObject = jniEnv->AllocObject(jniObject_jclass);
    jniEnv->SetIntField(jniObject, valueInt_jfieldID, (jint) which_client);
    jniEnv->CallVoidMethod(myJniJavaObject, jni2JavaMethodID,
                           DO_SOMETHING_CODE_disconnected, jniObject);
    jniEnv->DeleteLocalRef(jniObject);
    if (isAttached) {
        gJavaVm->DetachCurrentThread();
    }
}

void changeWindow(int which_client, int orientation) {
    JNIEnv *jniEnv;
    bool isAttached = getEnv(&jniEnv);
    jobject jniObject = jniEnv->AllocObject(jniObject_jclass);
    jniEnv->SetIntField(jniObject, valueInt_jfieldID, (jint) which_client);
    jniEnv->SetLongField(jniObject, valueLong_jfieldID, (jlong) orientation);
    jni2Java(jniEnv, DO_SOMETHING_CODE_change_window, jniObject);
    jniEnv->DeleteLocalRef(jniObject);
    if (isAttached) {
        gJavaVm->DetachCurrentThread();
    }
}

void setData(int code, int which_client, const char *data, ssize_t size) {
    JNIEnv *jniEnv;
    bool isAttached = getEnv(&jniEnv);
    jobject jniObject = jniEnv->AllocObject(jniObject_jclass);
    jniEnv->SetIntField(jniObject, valueInt_jfieldID, (jint) which_client);
    jniEnv->SetLongField(jniObject, valueLong_jfieldID, (jlong) size);
    jniEnv->SetObjectField(jniObject, valueString_jfieldID, (jobject) jniEnv->NewStringUTF(data));
    jni2Java(jniEnv, code, jniObject);
    jniEnv->DeleteLocalRef(jniObject);
    if (isAttached) {
        gJavaVm->DetachCurrentThread();
    }
}

void putDataToJava(int which_client, unsigned char *encodedData, ssize_t size) {
    JNIEnv *bufferEnv;
    bool isAttached = getEnv(&bufferEnv);
    if (bufferEnv != nullptr
        && myJniJavaObject != nullptr
        && putDataMethodID != nullptr) {
        jbyteArray data = bufferEnv->NewByteArray(size);
        bufferEnv->SetByteArrayRegion(
                data, 0, size, reinterpret_cast<const jbyte *>(encodedData));
        bufferEnv->CallVoidMethod(
                myJniJavaObject, putDataMethodID, which_client, data, (jint) size);
        bufferEnv->DeleteLocalRef(data);
    }
    if (isAttached) {
        gJavaVm->DetachCurrentThread();
    }
}

char *findDecoderCodecName(int code, int which_client, const char *data, ssize_t size) {
    JNIEnv *jniEnv;
    bool isAttached = getEnv(&jniEnv);
    jobject jniObject = jniEnv->AllocObject(jniObject_jclass);
    jniEnv->SetIntField(jniObject, valueInt_jfieldID, (jint) which_client);
    jniEnv->SetLongField(jniObject, valueLong_jfieldID, (jlong) size);
    jniEnv->SetObjectField(jniObject, valueString_jfieldID, (jobject) jniEnv->NewStringUTF(data));
    jni2Java(jniEnv, code, jniObject);

    jstring codecNameStr =
            static_cast<jstring>(jniEnv->GetObjectField(jniObject, valueString_jfieldID));
    const char *codecName = jniEnv->GetStringUTFChars(codecNameStr, 0);
    size_t length = strlen(codecName) + 1;// 千万注意,一定要加1
    LOGI("findDecoderCodecName() codecName: %s length: %d\n", codecName, length);
    char tmpStr[length];
    memcpy(tmpStr, codecName, length);
    jniEnv->ReleaseStringUTFChars(codecNameStr, codecName);

    jniEnv->DeleteLocalRef(jniObject);
    if (isAttached) {
        gJavaVm->DetachCurrentThread();
    }

    return tmpStr;
}

void createPortraitVirtualDisplay() {
    JNIEnv *jniEnv;
    bool isAttached = getEnv(&jniEnv);
    jniEnv->CallVoidMethod(myJniJavaObject, jni2JavaMethodID,
                           DO_SOMETHING_CODE_find_createPortraitVirtualDisplay, nullptr);
    if (isAttached) {
        gJavaVm->DetachCurrentThread();
    }
}

void createLandscapeVirtualDisplay() {
    JNIEnv *jniEnv;
    bool isAttached = getEnv(&jniEnv);
    jniEnv->CallVoidMethod(myJniJavaObject, jni2JavaMethodID,
                           DO_SOMETHING_CODE_find_createLandscapeVirtualDisplay, nullptr);
    if (isAttached) {
        gJavaVm->DetachCurrentThread();
    }
}

void sendDataError() {
    JNIEnv *jniEnv;
    bool isAttached = getEnv(&jniEnv);
    jniEnv->CallVoidMethod(myJniJavaObject, jni2JavaMethodID,
                           DO_SOMETHING_CODE_find_encoder_send_data_error, nullptr);
    if (isAttached) {
        gJavaVm->DetachCurrentThread();
    }
}

int getSdkVersion() {
    char sdk[128] = "0";

    // 获取版本号方法
    __system_property_get("ro.build.version.sdk", sdk);
    //__system_property_read_callback();

    //将版本号转为 int 值
    int sdk_verison = atoi(sdk);

    return sdk_verison;
}

void closeJni() {
    JNIEnv *env;
    bool isAttached = getEnv(&env);
    if (myJniJavaObject) {
        env->DeleteGlobalRef(myJniJavaObject);
        myJniJavaObject = nullptr;
    }
    if (serverFrame1Object) {
        env->DeleteGlobalRef(serverFrame1Object);
        serverFrame1Object = nullptr;
    }
    if (serverFrame2Object) {
        env->DeleteGlobalRef(serverFrame2Object);
        serverFrame2Object = nullptr;
    }
    if (clientFrameObject) {
        env->DeleteGlobalRef(clientFrameObject);
        clientFrameObject = nullptr;
    }
    if (jniObject_jclass) {
        env->DeleteGlobalRef(jniObject_jclass);
        jniObject_jclass = nullptr;
    }
    if (isAttached) {
        gJavaVm->DetachCurrentThread();
    }
}

/////////////////////////////////////////////////////////////////////////

static jint onTransact_init(JNIEnv *env, jobject myJniObject, jint code, jobject jniObject) {
    if (jniObject_jclass != nullptr) {
        env->DeleteGlobalRef(jniObject_jclass);
        jniObject_jclass = nullptr;
    }
    jclass tempJniObjectClass = env->FindClass("com/weidi/mirrorcast/JniObject");
    jniObject_jclass = reinterpret_cast<jclass>(env->NewGlobalRef(tempJniObjectClass));
    env->DeleteLocalRef(tempJniObjectClass);

    valueObject_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueObject", "Ljava/lang/Object;");
    valueString_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueString", "Ljava/lang/String;");
    valueInt_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueInt", "I");
    valueLong_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueLong", "J");
    valueByte_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueByte", "B");
    valueBoolean_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueBoolean", "Z");
    valueFloat_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueFloat", "F");
    valueDouble_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueDouble", "D");

    valueObjectArray_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueObjectArray", "[Ljava/lang/Object;");
    valueStringArray_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueStringArray", "[Ljava/lang/String;");
    valueIntArray_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueIntArray", "[I");
    valueLongArray_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueLongArray", "[J");
    valueByteArray_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueByteArray", "[B");
    valueBooleanArray_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueBooleanArray", "[Z");
    valueFloatArray_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueFloatArray", "[F");
    valueDoubleArray_jfieldID = env->GetFieldID(
            jniObject_jclass, "valueDoubleArray", "[D");

    /////////////////////////////////////////////////////////////////////////////////

    if (myJniJavaObject != nullptr) {
        env->DeleteGlobalRef(myJniJavaObject);
        myJniJavaObject = nullptr;
    }
    myJniJavaObject = reinterpret_cast<jobject>(env->NewGlobalRef(myJniObject));
    jclass MyJniClass = env->GetObjectClass(myJniObject);

    // 第三个参数: 括号中是java端方法的参数签名,括号后面是java端方法的返回值签名(V表示void)
    jni2JavaMethodID = env->GetMethodID(
            MyJniClass, "jni2Java", "(ILcom/weidi/mirrorcast/JniObject;)V");
    putDataMethodID = env->GetMethodID(
            MyJniClass, "putData", "(I[BI)V");

    jobject jni_object;
    jfieldID fieldID;
    //
    if (serverFrame1Object != nullptr) {
        env->DeleteGlobalRef(serverFrame1Object);
        serverFrame1Object = nullptr;
    }
    fieldID = env->GetStaticFieldID(MyJniClass, "serverFrame1",
                                    "Lcom/weidi/mirrorcast/JniObject;");
    jni_object = env->GetStaticObjectField(MyJniClass, fieldID);
    serverFrame1Object = reinterpret_cast<jobject>(env->NewGlobalRef(jni_object));
    env->DeleteLocalRef(jni_object);
    //
    if (serverFrame2Object != nullptr) {
        env->DeleteGlobalRef(serverFrame2Object);
        serverFrame2Object = nullptr;
    }
    fieldID = env->GetStaticFieldID(MyJniClass, "serverFrame2",
                                    "Lcom/weidi/mirrorcast/JniObject;");
    jni_object = env->GetStaticObjectField(MyJniClass, fieldID);
    serverFrame2Object = reinterpret_cast<jobject>(env->NewGlobalRef(jni_object));
    env->DeleteLocalRef(jni_object);
    //
    if (clientFrameObject != nullptr) {
        env->DeleteGlobalRef(clientFrameObject);
        clientFrameObject = nullptr;
    }
    fieldID = env->GetStaticFieldID(MyJniClass, "clientFrame",
                                    "Lcom/weidi/mirrorcast/JniObject;");
    jni_object = env->GetStaticObjectField(MyJniClass, fieldID);
    clientFrameObject = reinterpret_cast<jobject>(env->NewGlobalRef(jni_object));
    env->DeleteLocalRef(jni_object);

    env->DeleteLocalRef(MyJniClass);
    MyJniClass = nullptr;

    return (jint) 0;
}

static jint onTransact_closeJni(JNIEnv *env, jobject thiz,
                                jint code, jobject jniObject) {
    closeJni();
}

/////////////////////////////////////////////////////////////////////////

char *getStrFromDO_SOMETHING_CODE(DO_SOMETHING_CODE code) {
    char info[100];
    memset(info, '\0', sizeof(info));
    switch (code) {
        case DO_SOMETHING_CODE_init:
            //return "DO_SOMETHING_CODE_init";
            strncpy(info, "DO_SOMETHING_CODE_init",
                    strlen("DO_SOMETHING_CODE_init"));
            break;
        default:
            //return "DO_SOMETHING_CODE_nothing";
            strncpy(info, "DO_SOMETHING_CODE_nothing",
                    strlen("DO_SOMETHING_CODE_nothing"));
            break;
    }
    return info;
}

/***
 * jobject thiz 代表定义native方法的类的对象(如果native方法不是static的话)
 */
extern "C"
JNIEXPORT jstring JNICALL
Java_com_weidi_mirrorcast_MyJni_onTransact(JNIEnv *env, jobject thiz,
                                           jint code,
                                           jobject jniObject) {
    /*LOGI("onTransact() %s\n",
         getStrFromDO_SOMETHING_CODE(static_cast<DO_SOMETHING_CODE>(code)));*/
    const char ret[] = "0";
    switch (code) {
        case DO_SOMETHING_CODE_init: {
            return env->NewStringUTF(
                    std::to_string(onTransact_init(env, thiz, code, jniObject)).c_str());
        }
        case DO_SOMETHING_CODE_Server_set_ip: {
            jstring ipStr =
                    static_cast<jstring>(env->GetObjectField(jniObject, valueString_jfieldID));
            const char *ip = env->GetStringUTFChars(ipStr, 0);
            setIP(ip);
            env->ReleaseStringUTFChars(ipStr, ip);
            // 判断是手机还是电视机
            what_is_device = env->GetIntField(jniObject, valueInt_jfieldID);
            // 如果是电视机,最多投射的个数
            MAXIMUM_NUMBER = env->GetLongField(jniObject, valueLong_jfieldID);
            // 解码器是否走native路线
            MEDIA_CODEC_GO_JNI = env->GetBooleanField(jniObject, valueBoolean_jfieldID);

            return env->NewStringUTF(ret);
        }
        case DO_SOMETHING_CODE_Server_accept: {
            server_accept();
            // server_accept_udp();
            return env->NewStringUTF(ret);
        }
        case DO_SOMETHING_CODE_Server_close: {
            server_close();
            return env->NewStringUTF(ret);
        }
        case DO_SOMETHING_CODE_Client_connect: {
            jint port = env->GetIntField(jniObject, valueInt_jfieldID);
            SERVER_PROT = port;
            jstring ipStr =
                    static_cast<jstring>(env->GetObjectField(jniObject, valueString_jfieldID));
            const char *ip = env->GetStringUTFChars(ipStr, 0);
            setIP(ip);
            env->ReleaseStringUTFChars(ipStr, ip);
            if (client_connect()) {
                return env->NewStringUTF("true");
            }
            return env->NewStringUTF("false");
        }
        case DO_SOMETHING_CODE_Client_disconnect: {
            client_disconnect();
            return env->NewStringUTF(ret);
        }
        case DO_SOMETHING_CODE_Client_send_data: {
            jobject byteArrayObject = env->GetObjectField(jniObject, valueByteArray_jfieldID);
            if (byteArrayObject != nullptr) {
                jbyte *byteArray = reinterpret_cast<jbyte *>(
                        env->GetByteArrayElements(
                                static_cast<jbyteArray>(byteArrayObject), nullptr));
                jint length = env->GetIntField(jniObject, valueInt_jfieldID);
                ssize_t send_size = send_data(reinterpret_cast<uint8_t *>(byteArray), length);
                env->DeleteLocalRef(byteArrayObject);
                return env->NewStringUTF(std::to_string(send_size).c_str());
            }
            return env->NewStringUTF("-1");
        }
        case DO_SOMETHING_CODE_Client_set_info: {
            /***
             c++ string转char*乱码问题全面解决
             一.string转char*:
             char *strc = new char[strlen(str.c_str())+1];
             strcpy(strc, str.c_str());

             二.string转const char*:
             const string s;
             const char *str;
             str=strdup(s.c_str());
             */
            jstring temp_str =
                    static_cast<jstring>(env->GetObjectField(jniObject, valueString_jfieldID));
            const char *info = env->GetStringUTFChars(temp_str, 0);
            jint length = env->GetIntField(jniObject, valueInt_jfieldID);
            set_client_info(info, length);
            env->ReleaseStringUTFChars(temp_str, info);
            return env->NewStringUTF(ret);
        }
        case DO_SOMETHING_CODE_get_server_port: {
            return env->NewStringUTF(std::to_string(SERVER_PROT).c_str());
        }
        case DO_SOMETHING_CODE_close_all_clients: {
            close_all_clients();
            return env->NewStringUTF(ret);
        }
        case DO_SOMETHING_CODE_close_one_client: {
            jint which_client = env->GetIntField(jniObject, valueInt_jfieldID);
            close_client(which_client);
            return env->NewStringUTF(ret);
        }
        case DO_SOMETHING_CODE_set_surface: {
            jint which_client = env->GetIntField(jniObject, valueInt_jfieldID);
            jobject surfaceObject = env->GetObjectField(jniObject, valueObject_jfieldID);
            setSurface(which_client, env, surfaceObject);
            env->DeleteLocalRef(surfaceObject);
            return env->NewStringUTF(ret);
        }
        case DO_SOMETHING_CODE_start_record_screen_prepare: {
            jobject intArrayObject = env->GetObjectField(jniObject, valueIntArray_jfieldID);
            jobject stringArrayObject = env->GetObjectField(jniObject, valueStringArray_jfieldID);
            if (intArrayObject != nullptr && stringArrayObject != nullptr) {
                // int[]
                jint *intArray = reinterpret_cast<jint *>(
                        env->GetIntArrayElements(static_cast<jintArray>(intArrayObject), nullptr));
                int info_length = intArray[0];
                int orientation = intArray[1];
                int width = intArray[2];
                int height = intArray[3];
                int port = intArray[4];
                // String[]
                jobjectArray stringArray = reinterpret_cast<jobjectArray>(stringArrayObject);
                jstring info_ = static_cast<jstring>(env->GetObjectArrayElement(stringArray, 0));
                jstring mime_ = static_cast<jstring>(env->GetObjectArrayElement(stringArray, 1));
                jstring codec_name_ =
                        static_cast<jstring>(env->GetObjectArrayElement(stringArray, 2));
                jstring ip_ = static_cast<jstring>(env->GetObjectArrayElement(stringArray, 3));
                const char *info = env->GetStringUTFChars(info_, 0);
                const char *mime = env->GetStringUTFChars(mime_, 0);
                const char *codec_name = env->GetStringUTFChars(codec_name_, 0);
                const char *ip = env->GetStringUTFChars(ip_, 0);

                set_client_info(info, info_length);
                SERVER_PROT = port;
                setIP(ip);
                // 下面的按顺序调用
                createEncoderMediaFormat(mime, orientation, width, height);
                createEncoderMediaCodec(codec_name);
                createEncoderSurface();
                ANativeWindow *surface1_ = getSurface(1);
                ANativeWindow *surface2_ = getSurface(2);
                startEncoderMediaCodec();

                env->ReleaseStringUTFChars(info_, info);
                env->ReleaseStringUTFChars(mime_, mime);
                env->ReleaseStringUTFChars(codec_name_, codec_name);
                env->ReleaseStringUTFChars(ip_, ip);
                env->DeleteLocalRef(intArrayObject);
                env->DeleteLocalRef(stringArrayObject);

                if (client_connect()) {
                    // 设置Surface
                    jobject surface1 = ANativeWindow_toSurface(env, surface1_);
                    jobject surface2 = ANativeWindow_toSurface(env, surface2_);
                    jclass elementClass = env->FindClass("java/lang/Object");
                    jobjectArray objectArray = env->NewObjectArray(2, elementClass, nullptr);
                    env->SetObjectArrayElement(objectArray, 0, surface1);
                    env->SetObjectArrayElement(objectArray, 1, surface2);
                    env->SetObjectField(jniObject, valueObjectArray_jfieldID, objectArray);
                    // objectArray不确定要不要被释放
                    env->DeleteLocalRef(elementClass);
                    elementClass = nullptr;
                    return env->NewStringUTF("true");
                }
            }
            return env->NewStringUTF("false");
        }
        case DO_SOMETHING_CODE_start_record_screen: {
            startRecordScreen();
            return env->NewStringUTF(ret);
        }
        case DO_SOMETHING_CODE_is_recording: {
            if (isRecording) {
                return env->NewStringUTF("true");
            }
            return env->NewStringUTF("false");
        }
        case DO_SOMETHING_CODE_stop_record_screen: {
            client_disconnect();
            stopRecordScreen(false);
            return env->NewStringUTF(ret);
        }
        case DO_SOMETHING_CODE_fromPortraitToLandscape: {
            fromPortraitToLandscape();
            return env->NewStringUTF(ret);
        }
        case DO_SOMETHING_CODE_fromLandscapeToPortrait: {
            fromLandscapeToPortrait();
            return env->NewStringUTF(ret);
        }
        case DO_SOMETHING_CODE_release_sps_pps: {
            stopRecordScreen(true);
            return env->NewStringUTF(ret);
        }
        case DO_SOMETHING_CODE_only_output_key_frame: {
            ONLY_OUTPUT_KEY_FRAME = !ONLY_OUTPUT_KEY_FRAME;
            LOGI("DO_SOMETHING_CODE_only_output_key_frame ONLY_OUTPUT_KEY_FRAME: %d",
                 ONLY_OUTPUT_KEY_FRAME);
            env->SetBooleanField(jniObject, valueBoolean_jfieldID, ONLY_OUTPUT_KEY_FRAME);
            return env->NewStringUTF(ret);
        }

        default:
            break;
    }

    return env->NewStringUTF("-1");
}
