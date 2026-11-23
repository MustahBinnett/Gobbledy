// Small JNI bridge between Kotlin (app.pocketdictate.engine.NativeEngine)
// and the transcribe.cpp public C API (include/transcribe.h).

#include <jni.h>
#include <android/log.h>

#include <cstring>
#include <mutex>
#include <string>

#include "transcribe.h"

#define LOG_TAG "PocketDictateNative"

namespace {

void log_callback(transcribe_log_level level, const char *msg, void * /*userdata*/) {
    if (msg == nullptr) return;
    int prio = ANDROID_LOG_INFO;
    switch (level) {
        case TRANSCRIBE_LOG_LEVEL_ERROR: prio = ANDROID_LOG_ERROR; break;
        case TRANSCRIBE_LOG_LEVEL_WARN:  prio = ANDROID_LOG_WARN;  break;
        case TRANSCRIBE_LOG_LEVEL_DEBUG: prio = ANDROID_LOG_DEBUG; break;
        default:                         prio = ANDROID_LOG_INFO;  break;
    }
    __android_log_write(prio, LOG_TAG, msg);
}

std::once_flag g_init_once;

void ensure_init() {
    std::call_once(g_init_once, [] {
        transcribe_log_set(log_callback, nullptr);
        transcribe_init_backends_default();  // no-op for our static CPU build
    });
}

void throw_java(JNIEnv *env, const std::string &message) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls != nullptr) env->ThrowNew(cls, message.c_str());
}

std::string status_message(const char *what, int status) {
    const char *s = transcribe_status_string(status);
    return std::string(what) + ": " + (s ? s : "unknown error") + " (" + std::to_string(status) + ")";
}

// Build a java.lang.String from UTF-8 bytes. NewStringUTF expects "modified
// UTF-8" and mangles 4-byte characters, so go through String(byte[], "UTF-8").
jstring utf8_to_jstring(JNIEnv *env, const char *text) {
    if (text == nullptr) text = "";
    const auto len = static_cast<jsize>(std::strlen(text));
    jbyteArray bytes = env->NewByteArray(len);
    if (bytes == nullptr) return nullptr;
    env->SetByteArrayRegion(bytes, 0, len, reinterpret_cast<const jbyte *>(text));
    jclass string_cls = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(string_cls, "<init>", "([BLjava/lang/String;)V");
    jstring charset = env->NewStringUTF("UTF-8");
    auto result = static_cast<jstring>(env->NewObject(string_cls, ctor, bytes, charset));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(string_cls);
    return result;
}

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_app_pocketdictate_engine_NativeEngine_nativeVersion(JNIEnv *env, jobject /*thiz*/) {
    ensure_init();
    std::string v = std::string(transcribe_version() ? transcribe_version() : "?");
    const char *commit = transcribe_version_commit();
    if (commit != nullptr && commit[0] != '\0') v += " (" + std::string(commit) + ")";
    return utf8_to_jstring(env, v.c_str());
}

// Loads the GGUF model and opens a session. Returns an opaque handle.
JNIEXPORT jlong JNICALL
Java_app_pocketdictate_engine_NativeEngine_nativeLoad(JNIEnv *env, jobject /*thiz*/,
                                                       jstring jpath, jint n_threads) {
    ensure_init();
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (path == nullptr) return 0;

    transcribe_model_load_params load_params;
    transcribe_model_load_params_init(&load_params);
    load_params.backend = TRANSCRIBE_BACKEND_CPU;

    transcribe_session_params session_params;
    transcribe_session_params_init(&session_params);
    session_params.n_threads = n_threads > 0 ? n_threads : 0;

    transcribe_session *session = nullptr;
    const int status = transcribe_open(path, &load_params, &session_params, &session);
    env->ReleaseStringUTFChars(jpath, path);

    if (status != TRANSCRIBE_OK || session == nullptr) {
        throw_java(env, status_message("Model load failed", status));
        return 0;
    }
    const transcribe_model *model = transcribe_get_model(session);
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Loaded model: arch=%s backend=%s",
                        transcribe_model_arch_string(model), transcribe_model_backend(model));
    return reinterpret_cast<jlong>(session);
}

// Transcribes 16 kHz mono float PCM in [-1, 1]. Returns the transcript.
JNIEXPORT jstring JNICALL
Java_app_pocketdictate_engine_NativeEngine_nativeTranscribe(JNIEnv *env, jobject /*thiz*/,
                                                             jlong handle, jfloatArray jpcm,
                                                             jstring jlanguage) {
    auto *session = reinterpret_cast<transcribe_session *>(handle);
    if (session == nullptr) {
        throw_java(env, "Speech model is not loaded");
        return nullptr;
    }
    const jsize n = env->GetArrayLength(jpcm);
    if (n <= 0) return utf8_to_jstring(env, "");

    const char *language = jlanguage ? env->GetStringUTFChars(jlanguage, nullptr) : nullptr;

    transcribe_run_params run_params;
    transcribe_run_params_init(&run_params);
    run_params.language = language;  // copied by the library before returning

    jfloat *pcm = env->GetFloatArrayElements(jpcm, nullptr);
    const int status = transcribe_run(session, pcm, static_cast<int>(n), &run_params);
    env->ReleaseFloatArrayElements(jpcm, pcm, JNI_ABORT);
    if (language != nullptr) env->ReleaseStringUTFChars(jlanguage, language);

    if (status != TRANSCRIBE_OK) {
        throw_java(env, status_message("Transcription failed", status));
        return nullptr;
    }
    return utf8_to_jstring(env, transcribe_full_text(session));
}

JNIEXPORT void JNICALL
Java_app_pocketdictate_engine_NativeEngine_nativeFree(JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    auto *session = reinterpret_cast<transcribe_session *>(handle);
    // Sessions from transcribe_open own their model, so this frees both.
    if (session != nullptr) transcribe_session_free(session);
}

}  // extern "C"
