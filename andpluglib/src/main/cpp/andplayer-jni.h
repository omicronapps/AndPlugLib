#ifndef ANDPLAYER_JNI_H
#define ANDPLAYER_JNI_H

#include <jni.h>

extern "C"
{
JNIEXPORT jlong JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_create(JNIEnv* env, jobject thiz, jint rate, jboolean bit16, jboolean usestereo, jboolean left, jboolean right);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_destroy(JNIEnv* env, jobject thiz, jlong ptr);
JNIEXPORT jlong JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_load(JNIEnv *env, jobject thiz, jlong ptr,
                                                              jstring str);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_unload(JNIEnv* env, jobject thiz, jlong ptr);
JNIEXPORT jboolean JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_isLoaded(JNIEnv* env, jobject thiz, jlong ptr);

JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplWrite(JNIEnv* env, jobject thiz, jlong ptr, jint reg, jint val);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplSetchip(JNIEnv* env, jobject thiz, jlong ptr, jint n);
JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplGetchip(JNIEnv* env, jobject thiz, jlong ptr);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplInit(JNIEnv* env, jobject thiz, jlong ptr);
JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplGettype(JNIEnv* env, jobject thiz, jlong ptr);
JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplUpdate(JNIEnv* env, jobject thiz, jlong ptr, jshortArray array, jint samples, jboolean repeat);
JNIEXPORT jint JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_oplUpdate8(JNIEnv* env, jobject thiz, jlong ptr, jbyteArray array, jint samples, jboolean repeat);

JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetversion(JNIEnv* env, jobject thiz);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugSeek(JNIEnv* env, jobject thiz, jlong ptr, jlong ms);
JNIEXPORT void JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugRewind(JNIEnv* env, jobject thiz, jlong ptr, jint subsong);
JNIEXPORT jlong JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugSonglength(JNIEnv* env, jobject thiz, jlong ptr, jint subsong);
JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGettype(JNIEnv* env, jobject thiz, jlong ptr);
JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGettitle(JNIEnv* env, jobject thiz, jlong ptr);
JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetauthor(JNIEnv* env, jobject thiz, jlong ptr);
JNIEXPORT jstring JNICALL Java_com_omicronapplications_andpluglib_AndPlayerJNI_plugGetdesc(JNIEnv* env, jobject thiz, jlong ptr);
} // extern "C"

#endif // ANDPLAYER_JNI_H
