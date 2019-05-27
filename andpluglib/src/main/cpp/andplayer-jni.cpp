#include <cstring>
#include "andplayer-jni.h"
#include "common.h"
#include "Opl.h"

#define LOG_TAG "andplayer-jni"

extern "C"
{

static AndPlug* GetPlug(jlong ptr) {
    AndPlug* plug = NULL;
    Opl* opl = reinterpret_cast<Opl*>(ptr);
    if (opl != NULL) {
        plug = opl->GetPlug();
    }
    return plug;
}

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

JNIEXPORT jlong JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_create(JNIEnv* env, jobject thiz, jint rate, jboolean bit16, jboolean usestereo, jboolean left, jboolean right) {
    Opl *opl = new Opl(rate, bit16, usestereo, left, right);
    return reinterpret_cast<jlong>(opl);
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_destroy(JNIEnv* env, jobject thiz, jlong ptr) {
    Opl* opl = reinterpret_cast<Opl*>(ptr);
    if (opl != NULL) {
        delete opl;
    } else {
        LOGW(LOG_TAG, "destroy: no Opl instance");
    }
}

JNIEXPORT jlong JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_load(JNIEnv *env, jobject thiz, jlong ptr, jstring str) {
    const char *song = env->GetStringUTFChars(str, 0);

    Opl* opl = reinterpret_cast<Opl*>(ptr);
    CPlayer* p = NULL;
    if (opl != NULL) {
        p = opl->Load(song);
    } else {
        LOGW(LOG_TAG, "load: no Opl instance");
    }
    env->ReleaseStringUTFChars(str, song);
    return reinterpret_cast<jlong>(p);
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_unload(JNIEnv* env, jobject thiz, jlong ptr) {
    Opl* opl = reinterpret_cast<Opl*>(ptr);
    if (opl != NULL) {
        opl->Unload();
    } else {
        LOGW(LOG_TAG, "unload: no Opl instance");
    }
}

JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_isLoaded(JNIEnv* env, jobject thiz, jlong ptr) {
    jboolean isLoaded = JNI_FALSE;
    if (ptr != 0) {
        AndPlug *plug = GetPlug(ptr);
        isLoaded = (jboolean) (plug->isLoaded() ? JNI_TRUE : JNI_FALSE);
    }

    return isLoaded;
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplWrite(JNIEnv* env, jobject thiz, jlong ptr, jint reg, jint val) {
    Opl* opl = reinterpret_cast<Opl*>(ptr);
    if (opl != NULL) {
        opl->Write(reg, val);
    } else {
        LOGW(LOG_TAG, "write: no Opl instance");
    }
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplSetchip(JNIEnv* env, jobject thiz, jlong ptr, jint n) {
    Opl* opl = reinterpret_cast<Opl*>(ptr);
    if (opl != NULL) {
        opl->SetChip(n);
    } else {
        LOGW(LOG_TAG, "oplSetchip: no Opl instance");
    }
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplGetchip(JNIEnv* env, jobject thiz, jlong ptr) {
    jint chip = -1;
    Opl* opl = reinterpret_cast<Opl*>(ptr);
    if (opl != NULL) {
        chip = opl->GetChip();
    } else {
        LOGW(LOG_TAG, "oplGetchip: no Opl instance");
    }

    return chip;
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplInit(JNIEnv* env, jobject thiz, jlong ptr) {
    Opl* opl = reinterpret_cast<Opl*>(ptr);
    if (opl != NULL) {
        opl->Init();
    } else {
        LOGW(LOG_TAG, "init: no Opl instance");
    }
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplGettype(JNIEnv* env, jobject thiz, jlong ptr) {
    int type = -1;
    Opl* opl = reinterpret_cast<Opl*>(ptr);
    if (opl != NULL) {
        type = opl->GetType();
    } else {
        LOGW(LOG_TAG, "oplGettype: no Opl instance");
    }

    return type;
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplUpdate16(JNIEnv* env, jobject thiz, jlong ptr, jshortArray array, jint samples, jboolean repeat) {
    if ((array == 0) || (samples == 0)) {
        return 0;
    }
    unsigned long newsamples = 0;
    Opl* opl = reinterpret_cast<Opl*>(ptr);
    jshort* buf = env->GetShortArrayElements(array, 0);

    if (opl != NULL) {
        newsamples = opl->Opl::Update16(buf, samples, repeat);
    } else {
        LOGW(LOG_TAG, "update16: no Opl instance");
    }
    env->ReleaseShortArrayElements(array, buf, 0);

    return (jint) (newsamples & 0xFFFFFFFF);
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplUpdate8(JNIEnv* env, jobject thiz, jlong ptr, jbyteArray array, jint samples, jboolean repeat) {
    if ((array == 0) || (samples == 0)) {
        return 0;
    }
    unsigned long newsamples = 0;
    Opl* opl = reinterpret_cast<Opl*>(ptr);
    jbyte* buf = env->GetByteArrayElements(array, 0);

    if (opl != NULL) {
        newsamples = opl->Opl::Update8((char*) buf, samples, repeat);
    } else {
        LOGW(LOG_TAG, "update8: no Opl instance");
    }
    env->ReleaseByteArrayElements(array, buf, 0);

    return (jint) (newsamples & 0xFFFFFFFF);
}


JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetversion(JNIEnv* env, jobject thiz) {
    const char* version = AndPlug::GetVersion();
    jstring jversion = env->NewStringUTF(version);

    return jversion;
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugSeek(JNIEnv* env, jobject thiz, jlong ptr, jlong ms) {
    AndPlug* plug = GetPlug(ptr);
    if ((plug != NULL) && plug->isLoaded()) {
        plug->Seek(ms);
    }
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugRewind(JNIEnv* env, jobject thiz, jlong ptr, jint subsong) {
    AndPlug* plug = GetPlug(ptr);
    if ((plug != NULL) && plug->isLoaded()) {
        plug->Rewind(subsong);
    }
}

JNIEXPORT jlong JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugSonglength(JNIEnv* env, jobject thiz, jlong ptr, jint subsong) {
    long length = -1;
    AndPlug* plug = GetPlug(ptr);
    if ((plug != NULL) && plug->isLoaded()) {
        length = plug->SongLength(subsong);
    }

    return length;
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGettype(JNIEnv* env, jobject thiz, jlong ptr) {
    const char* type = NULL;
    AndPlug* plug = GetPlug(ptr);
    if ((plug != NULL) && plug->isLoaded()) {
        type = plug->GetType().c_str();
    }
    jstring jtype = getJstring(env, type);

    return jtype;
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGettitle(JNIEnv* env, jobject thiz, jlong ptr) {
    const char* title = NULL;
    AndPlug* plug = GetPlug(ptr);
    if ((plug != NULL) && plug->isLoaded()) {
        title = plug->GetTitle().c_str();
    }
    jstring jtitle = getJstring(env, title);

    return jtitle;
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetauthor(JNIEnv* env, jobject thiz, jlong ptr) {
    const char* author = NULL;
    AndPlug* plug = GetPlug(ptr);
    if ((plug != NULL) && plug->isLoaded()) {
        author = plug->GetAuthor().c_str();
    }
    jstring jauthor = getJstring(env, author);

    return jauthor;
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetdesc(JNIEnv* env, jobject thiz, jlong ptr) {
    const char* desc = NULL;
    AndPlug* plug = GetPlug(ptr);
    if ((plug != NULL) && plug->isLoaded()) {
        desc = plug->GetDesc().c_str();
    }
    jstring jdesc = getJstring(env, desc);

    return jdesc;
}
} // extern "C"
