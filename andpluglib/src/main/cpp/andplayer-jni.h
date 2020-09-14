#ifndef ANDPLAYER_JNI_H
#define ANDPLAYER_JNI_H

#include <jni.h>

void setState(int request, int state, const char* info);

extern "C"
{
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplInitialize(JNIEnv* env, jobject thiz, jint emu, jint rate, jboolean usestereo);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplUninitialize(JNIEnv* env, jobject thiz);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplSetRepeat(JNIEnv* env, jobject thiz, jboolean repeat);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplWrite(JNIEnv* env, jobject thiz, jint reg, jint val);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplSetchip(JNIEnv* env, jobject thiz, jint n);
JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplGetchip(JNIEnv* env, jobject thiz);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplInit(JNIEnv* env, jobject thiz);
JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplGettype(JNIEnv* env, jobject thiz);
JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplUpdate(JNIEnv* env, jobject thiz, jshortArray array, jbyteArray debugArray, jint size);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplDebugPath(JNIEnv* env, jobject thiz, jstring str);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplOpenFile(JNIEnv* env, jobject thiz);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplCloseFile(JNIEnv* env, jobject thiz);

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetversion(JNIEnv* env, jobject thiz);
JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugLoad(JNIEnv *env, jobject thiz, jstring str);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugUnload(JNIEnv* env, jobject thiz);
JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugIsLoaded(JNIEnv* env, jobject thiz);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugSeek(JNIEnv* env, jobject thiz, jlong ms);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugRewind(JNIEnv* env, jobject thiz, jint subsong);
JNIEXPORT jlong JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugSonglength(JNIEnv* env, jobject thiz, jint subsong);
JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGettype(JNIEnv* env, jobject thiz);
JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGettitle(JNIEnv* env, jobject thiz);
JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetauthor(JNIEnv* env, jobject thiz);
JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetdesc(JNIEnv* env, jobject thiz);
JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetsubsongs(JNIEnv* env, jobject thiz);
JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetsubsong(JNIEnv* env, jobject thiz);

JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oboeInitialize(JNIEnv* env, jobject thiz, jint rate, jboolean usestereo);
JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oboeUninitialize(JNIEnv* env, jobject thiz);
JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oboeRestart(JNIEnv* env, jobject thiz);
JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oboePlay(JNIEnv* env, jobject thiz);
JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oboePause(JNIEnv* env, jobject thiz);
JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oboeStop(JNIEnv* env, jobject thiz);
JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oboeGetState(JNIEnv* env, jobject thiz);

JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_infoLoad(JNIEnv *env, jobject thiz, jstring str);
JNIEXPORT jlong JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_infoSonglength(JNIEnv* env, jobject thiz, jint subsong);
JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_infoGettype(JNIEnv* env, jobject thiz);
JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_infoGettitle(JNIEnv* env, jobject thiz);
JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_infoGetauthor(JNIEnv* env, jobject thiz);
JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_infoGetdesc(JNIEnv* env, jobject thiz);
JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_infoGetsubsongs(JNIEnv* env, jobject thiz);
} // extern "C"

#endif // ANDPLAYER_JNI_H
