#include <cstring>
#include "andplayer-jni.h"
#include "common.h"
#include "Opl.h"

#define LOG_TAG "andplayer-jni"

static Opl opl;

static jstring getJstring(JNIEnv *env, const char *bytes) {
    char str[512];
    jstring jdesc;
    for (int i = 0; i < 512; i++) {
        str[i] = bytes[i];
        if (bytes[i] == '\0') {
            break;
        } else if ((bytes[i] < 0x20) || (bytes[i] > 0x7e)) {
            str[i] = '_';
        }
    }
    jdesc = env->NewStringUTF(str);
    return jdesc;
}

extern "C"
{

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_initialize(JNIEnv* env, jobject thiz, jint rate, jboolean bit16, jboolean usestereo, jboolean left, jboolean right) {
    opl.Initialize(rate, bit16, usestereo, left, right);
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_uninitialize(JNIEnv* env, jobject thiz) {
    opl.Uninitialize();
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_load(JNIEnv *env, jobject thiz, jstring str) {
    const char *song = env->GetStringUTFChars(str, 0);
    opl.Load(song);
    env->ReleaseStringUTFChars(str, song);
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_unload(JNIEnv* env, jobject thiz) {
    opl.Unload();
}

JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_isLoaded(JNIEnv* env, jobject thiz) {
    jboolean isLoaded = JNI_FALSE;
    AndPlug *plug = opl.GetPlug();
    if (plug != nullptr) {
        isLoaded = (jboolean) (plug->isLoaded() ? JNI_TRUE : JNI_FALSE);
    }
    return isLoaded;
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplWrite(JNIEnv* env, jobject thiz, jint reg, jint val) {
    opl.Write(reg, val);
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplSetchip(JNIEnv* env, jobject thiz, jint n) {
    opl.SetChip(n);
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplGetchip(JNIEnv* env, jobject thiz) {
    return opl.GetChip();
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplInit(JNIEnv* env, jobject thiz) {
    opl.Init();
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplGettype(JNIEnv* env, jobject thiz) {
    return opl.GetType();
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplUpdate16(JNIEnv* env, jobject thiz, jshortArray array, jint samples, jboolean repeat) {
    if ((array == 0) || (samples == 0)) {
        return 0;
    }
    unsigned long newsamples = 0;
    jshort* buf = env->GetShortArrayElements(array, 0);

    newsamples = opl.Opl::Update16(buf, samples, repeat);
    env->ReleaseShortArrayElements(array, buf, 0);

    return (jint) (newsamples & 0xFFFFFFFF);
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplUpdate8(JNIEnv* env, jobject thiz, jbyteArray array, jint samples, jboolean repeat) {
    if ((array == 0) || (samples == 0)) {
        return 0;
    }
    unsigned long newsamples = 0;
    jbyte* buf = env->GetByteArrayElements(array, 0);

    newsamples = opl.Opl::Update8((char*) buf, samples, repeat);
    env->ReleaseByteArrayElements(array, buf, 0);

    return (jint) (newsamples & 0xFFFFFFFF);
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplDebugPath(JNIEnv* env, jobject thiz, jstring str){
    const char *filename = env->GetStringUTFChars(str, 0);
    opl.DebugPath(filename);
    env->ReleaseStringUTFChars(str, filename);
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetversion(JNIEnv* env, jobject thiz) {
    const char* version = AndPlug::GetVersion();
    jstring jversion = env->NewStringUTF(version);
    return jversion;
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugSeek(JNIEnv* env, jobject thiz, jlong ms) {
    AndPlug* plug = opl.GetPlug();
    if (plug != nullptr && plug->isLoaded()) {
        plug->Seek(static_cast<unsigned long>(ms));
    }
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugRewind(JNIEnv* env, jobject thiz, jint subsong) {
    AndPlug* plug = opl.GetPlug();
    if (plug != nullptr && plug->isLoaded()) {
        plug->Rewind(subsong);
    }
}

JNIEXPORT jlong JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugSonglength(JNIEnv* env, jobject thiz, jint subsong) {
    long length = -1;
    AndPlug* plug = opl.GetPlug();
    if (plug != nullptr && plug->isLoaded()) {
        length = plug->SongLength(subsong);
    }
    return length;
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGettype(JNIEnv* env, jobject thiz) {
    const char* type = NULL;
    AndPlug* plug = opl.GetPlug();
    if (plug != nullptr && plug->isLoaded()) {
        type = plug->GetType().c_str();
    }
    return getJstring(env, type);
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGettitle(JNIEnv* env, jobject thiz) {
    const char* title = NULL;
    AndPlug* plug = opl.GetPlug();
    if (plug != nullptr && plug->isLoaded()) {
        title = plug->GetTitle().c_str();
    }
    return getJstring(env, title);
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetauthor(JNIEnv* env, jobject thiz) {
    const char* author = NULL;
    AndPlug* plug = opl.GetPlug();
    if (plug != nullptr && plug->isLoaded()) {
        author = plug->GetAuthor().c_str();
    }
    return getJstring(env, author);
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetdesc(JNIEnv* env, jobject thiz) {
    const char* desc = NULL;
    AndPlug* plug = opl.GetPlug();
    if (plug != nullptr && plug->isLoaded()) {
        desc = plug->GetDesc().c_str();
    }
    return getJstring(env, desc);
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetsubsongs(JNIEnv* env, jobject thiz) {
    int numsubsongs = -1;
    AndPlug* plug = opl.GetPlug();
    if (plug != nullptr && plug->isLoaded()) {
        numsubsongs = plug->GetSubsongs();
    }
    return numsubsongs;
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetsubsong(JNIEnv* env, jobject thiz) {
    int cursubsong = -1;
    AndPlug* plug = opl.GetPlug();
    if (plug != nullptr && plug->isLoaded()) {
        cursubsong = plug->GetSubsong();
    }
    return cursubsong;
}

} // extern "C"
