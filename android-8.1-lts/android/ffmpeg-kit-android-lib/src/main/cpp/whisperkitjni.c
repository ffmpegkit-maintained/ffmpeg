/*
 * WhisperKit JNI bridge for FFmpegKit 8.1 LTS.
 *
 * Exposes on-device speech recognition (transcription + translation) via
 * Whisper.cpp v1.7.5 to the Java/Kotlin WhisperKit API.
 * Available only in the Full and Full GPL tiers of the 8.1 line.
 *
 * This file is compiled only when libwhisper.a is present in the prebuilt
 * tree (enforced by the conditional block in android/jni/Android.mk).
 */

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include "whisper.h"

#define LOG_TAG "WhisperKit"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* Helper: millisecond timestamp (Whisper returns centiseconds) to SRT "HH:MM:SS,mmm". */
static void cs_to_srt_time(long long cs, char *buf, size_t len) {
    long long ms  = cs * 10;
    int h  = (int)(ms / 3600000); ms %= 3600000;
    int m  = (int)(ms / 60000);   ms %= 60000;
    int s  = (int)(ms / 1000);    ms %= 1000;
    snprintf(buf, len, "%02d:%02d:%02d,%03d", h, m, s, (int)ms);
}

/* ─────────────────────────────────────────────────────────────────────────── */

JNIEXPORT jlong JNICALL
Java_com_arthenica_ffmpegkit_WhisperKit_nativeInitContext(
        JNIEnv *env, jclass clazz, jstring model_path) {
    (void)clazz;
    const char *path = (*env)->GetStringUTFChars(env, model_path, NULL);
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    (*env)->ReleaseStringUTFChars(env, model_path, path);
    if (!ctx) {
        LOGE("Failed to load Whisper model from %s", path);
        return 0L;
    }
    LOGI("Whisper model loaded, context=%p", (void *)ctx);
    return (jlong)(intptr_t)ctx;
}

JNIEXPORT void JNICALL
Java_com_arthenica_ffmpegkit_WhisperKit_nativeFreeContext(
        JNIEnv *env, jclass clazz, jlong ctx_ptr) {
    (void)env; (void)clazz;
    if (ctx_ptr) {
        whisper_free((struct whisper_context *)(intptr_t)ctx_ptr);
        LOGI("Whisper context freed");
    }
}

/*
 * Runs inference on float PCM samples (16 kHz, mono, f32).
 * Returns 0 on success, non-zero on failure.
 * Results are stored inside the whisper_context and retrieved via
 * nativeGetSegmentCount / nativeGetSegmentText / nativeGetSegmentT0/T1.
 */
JNIEXPORT jint JNICALL
Java_com_arthenica_ffmpegkit_WhisperKit_nativeFullTranscribe(
        JNIEnv *env, jclass clazz,
        jlong ctx_ptr, jfloatArray audio_data,
        jboolean translate, jstring language, jint num_threads) {
    (void)clazz;
    struct whisper_context *ctx = (struct whisper_context *)(intptr_t)ctx_ptr;

    jfloat *samples  = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    jsize n_samples  = (*env)->GetArrayLength(env, audio_data);
    const char *lang = (*env)->GetStringUTFChars(env, language, NULL);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime   = false;
    params.print_progress   = false;
    params.print_timestamps = true;
    params.print_special    = false;
    params.translate        = (bool)translate;
    params.language         = lang;
    params.n_threads        = (int)num_threads > 0 ? (int)num_threads : 4;
    params.offset_ms        = 0;
    params.no_context       = true;
    params.single_segment   = false;

    whisper_reset_timings(ctx);
    int rc = whisper_full(ctx, params, samples, (int)n_samples);
    if (rc != 0) LOGE("whisper_full failed (rc=%d)", rc);

    (*env)->ReleaseStringUTFChars(env, language, lang);
    (*env)->ReleaseFloatArrayElements(env, audio_data, samples, JNI_ABORT);
    return (jint)rc;
}

JNIEXPORT jint JNICALL
Java_com_arthenica_ffmpegkit_WhisperKit_nativeGetSegmentCount(
        JNIEnv *env, jclass clazz, jlong ctx_ptr) {
    (void)env; (void)clazz;
    return (jint)whisper_full_n_segments((struct whisper_context *)(intptr_t)ctx_ptr);
}

JNIEXPORT jstring JNICALL
Java_com_arthenica_ffmpegkit_WhisperKit_nativeGetSegmentText(
        JNIEnv *env, jclass clazz, jlong ctx_ptr, jint index) {
    (void)clazz;
    const char *text = whisper_full_get_segment_text(
            (struct whisper_context *)(intptr_t)ctx_ptr, (int)index);
    return (*env)->NewStringUTF(env, text ? text : "");
}

/* Returns segment start time in centiseconds (Whisper's native unit). */
JNIEXPORT jlong JNICALL
Java_com_arthenica_ffmpegkit_WhisperKit_nativeGetSegmentT0(
        JNIEnv *env, jclass clazz, jlong ctx_ptr, jint index) {
    (void)env; (void)clazz;
    return (jlong)whisper_full_get_segment_t0(
            (struct whisper_context *)(intptr_t)ctx_ptr, (int)index);
}

/* Returns segment end time in centiseconds. */
JNIEXPORT jlong JNICALL
Java_com_arthenica_ffmpegkit_WhisperKit_nativeGetSegmentT1(
        JNIEnv *env, jclass clazz, jlong ctx_ptr, jint index) {
    (void)env; (void)clazz;
    return (jlong)whisper_full_get_segment_t1(
            (struct whisper_context *)(intptr_t)ctx_ptr, (int)index);
}

JNIEXPORT jstring JNICALL
Java_com_arthenica_ffmpegkit_WhisperKit_nativeGetSystemInfo(
        JNIEnv *env, jclass clazz) {
    (void)clazz;
    return (*env)->NewStringUTF(env, whisper_print_system_info());
}
