#include <cstring>
#include <mutex>
#include "andplayer-jni.h"
#include "common.h"
#include "Opl.h"
#include "OboePlayer.h"
#include "SongInfo.h"

#define LOG_TAG "andplayer-jni"

static JavaVM* s_vm;
static jmethodID s_classLoaderID;
static jobject s_classLoaderObject;
static jobject s_thiz;
static int s_state; // Workaround for failing JNI-Java callbacks
static long s_ms; // Workaround for failing JNI-Java callbacks

static jobject getClassLoaderObject(JNIEnv* env, const char* name) {
    jclass clazz = env->FindClass(name);
    jclass objectClass = env->GetObjectClass(clazz);
    jmethodID classLoaderID = env->GetMethodID(objectClass, "getClassLoader", "()Ljava/lang/ClassLoader;");
    jobject classLoaderObject = env->CallObjectMethod(clazz, classLoaderID);
    return env->NewGlobalRef(classLoaderObject);
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    s_vm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) {
        jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
        s_classLoaderID = env->GetMethodID(classLoaderClass, "findClass", "(Ljava/lang/String;)Ljava/lang/Class;");
        s_classLoaderObject = getClassLoaderObject(env, "com/omicronapplications/andpluglib/AndPlayerJNI");
        if (env->ExceptionCheck()) {
            LOGE(LOG_TAG, "JNI_OnLoad: GetObjectClass failed");
            env->ExceptionClear();
        }
    }

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    s_vm = nullptr;
}

static std::mutex& adplugmtx() {
    static std::mutex s_adplugmtx;
    return s_adplugmtx;
}

static AndPlug* andplug() {
    static std::unique_ptr<AndPlug> s_andplug(new AndPlug(adplugmtx()));
    return s_andplug.get();
}

static Opl* opl() {
    static std::unique_ptr<Opl> s_opl(new Opl(andplug(), adplugmtx()));
    return s_opl.get();
}

static OboePlayer* oboe_player() {
    static std::unique_ptr<OboePlayer> s_oboe(new OboePlayer(opl()));
    return s_oboe.get();
}

static SongInfo* song_info() {
    static std::unique_ptr<SongInfo> s_info(new SongInfo());
    return s_info.get();
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

void setState(int request, int state, const char* info) {
    s_state = state;
    JNIEnv* env = nullptr;
    jint attached = s_vm->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (attached == JNI_EDETACHED) {
        if (s_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE(LOG_TAG, "setState: Failed to attach to the VM");
            return;
        }
    } else if (attached != JNI_OK) {
        LOGE(LOG_TAG, "setState: Failed to get environment");
        return;
    }

    jclass clazz = static_cast<jclass>(env->CallObjectMethod(s_classLoaderObject, s_classLoaderID, env->NewStringUTF("com/omicronapplications/andpluglib/AndPlayerJNI")));
    if (env->ExceptionCheck()) {
        LOGE(LOG_TAG, "setState: CallObjectMethod failed");
        env->ExceptionClear();
    } else {
        jmethodID id = env->GetMethodID(clazz, "setState", "(IILjava/lang/String;)V");
       if (env->ExceptionCheck()) {
//            LOGE(LOG_TAG, "setState: GetMethodID %p failed", clazz);
            env->ExceptionClear();
        } else {
            jstring str = env->NewStringUTF(info);
            env->CallVoidMethod(s_thiz, id, request, state, str);
        }
    }
    if (attached == JNI_EDETACHED) {
        s_vm->DetachCurrentThread();
    }
}

void setTime(long ms) {
    s_ms = ms;
}

extern "C"
{

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplInitialize(JNIEnv* env, jobject thiz, jint emu, jint rate, jboolean usestereo) {
    opl()->Initialize(emu, rate, usestereo);
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplUninitialize(JNIEnv* env, jobject thiz) {
    opl()->Uninitialize();
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplSetRepeat(JNIEnv* env, jobject thiz, jboolean repeat) {
    opl()->SetRepeat(repeat);
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplWrite(JNIEnv* env, jobject thiz, jint reg, jint val) {
    opl()->Write(reg, val);
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplSetchip(JNIEnv* env, jobject thiz, jint n) {
    opl()->SetChip(n);
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplGetchip(JNIEnv* env, jobject thiz) {
    return opl()->GetChip();
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplInit(JNIEnv* env, jobject thiz) {
    opl()->Init();
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplGettype(JNIEnv* env, jobject thiz) {
    return opl()->GetType();
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplUpdate(JNIEnv* env, jobject thiz, jshortArray array, jbyteArray debugArray, jint size) {
    if (array == nullptr) {
        return 0;
    }
    int samples = 0;
    jshort* buf = env->GetShortArrayElements(array, 0);
    jbyte* debugBuf = nullptr;
    if (debugArray != nullptr) {
        debugBuf = env->GetByteArrayElements(debugArray, 0);
    }

    samples = opl()->Opl::Update(buf, size);
    if (debugArray != nullptr) {
        memcpy(debugBuf, buf, 2 * size);
    }

    env->ReleaseShortArrayElements(array, buf, 0);
    if (debugArray != nullptr) {
        env->ReleaseByteArrayElements(debugArray, debugBuf, 0);
    }

    return samples;
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplDebugPath(JNIEnv* env, jobject thiz, jstring str){
    const char *filename = env->GetStringUTFChars(str, 0);
    opl()->DebugPath(filename);
    env->ReleaseStringUTFChars(str, filename);
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplOpenFile(JNIEnv* env, jobject thiz) {
    opl()->OpenFile();
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplCloseFile(JNIEnv* env, jobject thiz) {
    opl()->CloseFile();
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetversion(JNIEnv* env, jobject thiz) {
    std::string adplug_version = AndPlug::GetVersion();
    const char* version = adplug_version.c_str();
    return getJstring(env, version);
}

JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugLoad(JNIEnv *env, jobject thiz, jstring str) {
    const char *song = env->GetStringUTFChars(str, 0);
    bool isLoaded = false;
    Copl* copl = opl()->GetCopl();
    if (copl != nullptr) {
        isLoaded = andplug()->Load(song, copl);
    } else {
        LOGW(LOG_TAG, "plugLoad: failed to load song: %s", song);
    }
    env->ReleaseStringUTFChars(str, song);
    return isLoaded;
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugUnload(JNIEnv* env, jobject thiz) {
    andplug()->Unload();
}

JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugIsLoaded(JNIEnv* env, jobject thiz) {
    jboolean isLoaded = (jboolean) (andplug()->isLoaded() ? JNI_TRUE : JNI_FALSE);
    return isLoaded;
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugSeek(JNIEnv* env, jobject thiz, jlong ms) {
    AndPlug* plug = andplug();
    if (plug->isLoaded()) {
        plug->Seek(static_cast<unsigned long>(ms));
        oboe_player()->Seek(static_cast<long>(ms));
    }
}

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugRewind(JNIEnv* env, jobject thiz, jint subsong) {
    AndPlug* plug = andplug();
    if (plug->isLoaded()) {
        plug->Rewind(subsong);
    }
}

JNIEXPORT jlong JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugSonglength(JNIEnv* env, jobject thiz, jint subsong) {
    long length = -1;
    AndPlug* plug = andplug();
    if (plug->isLoaded()) {
        length = (long) plug->SongLength(subsong);
    }
    return length;
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGettype(JNIEnv* env, jobject thiz) {
    const char* type = nullptr;
    AndPlug* plug = andplug();
    if (plug->isLoaded()) {
        type = plug->GetType().c_str();
    }
    return getJstring(env, type);
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGettitle(JNIEnv* env, jobject thiz) {
    const char* title = nullptr;
    AndPlug* plug = andplug();
    if (plug->isLoaded()) {
        title = plug->GetTitle().c_str();
    }
    return getJstring(env, title);
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetauthor(JNIEnv* env, jobject thiz) {
    const char* author = nullptr;
    AndPlug* plug = andplug();
    if (plug->isLoaded()) {
        author = plug->GetAuthor().c_str();
    }
    return getJstring(env, author);
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetdesc(JNIEnv* env, jobject thiz) {
    const char* desc = nullptr;
    AndPlug* plug = andplug();
    if (plug->isLoaded()) {
        desc = plug->GetDesc().c_str();
    }
    return getJstring(env, desc);
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetsubsongs(JNIEnv* env, jobject thiz) {
    int numsubsongs = -1;
    AndPlug* plug = andplug();
    if (plug->isLoaded()) {
        numsubsongs = (int) plug->GetSubsongs();
    }
    return numsubsongs;
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetsubsong(JNIEnv* env, jobject thiz) {
    int cursubsong = -1;
    AndPlug* plug = andplug();
    if (plug->isLoaded()) {
        cursubsong = (int) plug->GetSubsong();
    }
    return cursubsong;
}

JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oboeInitialize(JNIEnv* env, jobject thiz, jint rate, jboolean usestereo) {
    s_state = 1;
    s_ms = 0;
    if (s_thiz != nullptr && env->GetObjectRefType(s_thiz) == JNIGlobalRefType) {
        env->DeleteGlobalRef(s_thiz);
    }
    s_thiz = env->NewGlobalRef(thiz);
    return oboe_player()->Initialize(rate, usestereo);
}

JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oboeUninitialize(JNIEnv* env, jobject thiz) {
    s_state = 0;
    s_ms = 0;
    return oboe_player()->Uninitialize();
}

JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oboeRestart(JNIEnv* env, jobject thiz) {
    s_state = 1;
    s_ms = 0;
    return oboe_player()->Restart();
}

JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oboePlay(JNIEnv* env, jobject thiz) {
    s_state = 3;
    return oboe_player()->Play();
}

JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oboePause(JNIEnv* env, jobject thiz) {
    s_state = 4;
    return oboe_player()->Pause();
}

JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oboeStop(JNIEnv* env, jobject thiz) {
    s_state = 5;
    s_ms = 0;
    return oboe_player()->Stop();
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oboeGetState(JNIEnv* env, jobject thiz) {
    return s_state;
}

JNIEXPORT jlong JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oboeGetTime(JNIEnv* env, jobject thiz) {
    return s_ms;
}

JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_infoLoad(JNIEnv *env, jobject thiz, jstring str) {
    const char *song = env->GetStringUTFChars(str, 0);
    bool isLoaded = song_info()->Load(song);
    env->ReleaseStringUTFChars(str, song);
    return isLoaded;
}

JNIEXPORT jlong JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_infoSonglength(JNIEnv* env, jobject thiz, jint subsong) {
    long length = -1;
    SongInfo* info = song_info();
    length = (long) info->SongLength(subsong);
    return length;
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_infoGettype(JNIEnv* env, jobject thiz) {
    const char* type = nullptr;
    SongInfo* info = song_info();
    type = info->GetType().c_str();
    return getJstring(env, type);
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_infoGettitle(JNIEnv* env, jobject thiz) {
    const char* title = nullptr;
    SongInfo* info = song_info();
    title = info->GetTitle().c_str();
    return getJstring(env, title);
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_infoGetauthor(JNIEnv* env, jobject thiz) {
    const char* author = nullptr;
    SongInfo* info = song_info();
    author = info->GetAuthor().c_str();
    return getJstring(env, author);
}

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_infoGetdesc(JNIEnv* env, jobject thiz) {
    const char* desc = nullptr;
    SongInfo* info = song_info();
    desc = info->GetDesc().c_str();
    return getJstring(env, desc);
}

JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_infoGetsubsongs(JNIEnv* env, jobject thiz) {
    int numsubsongs = -1;
    SongInfo* info = song_info();
    numsubsongs = (int) info->GetSubsongs();
    return numsubsongs;
}

} // extern "C"
