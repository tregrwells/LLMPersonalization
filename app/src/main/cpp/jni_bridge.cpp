// jni_bridge.cpp
//
// JNI surface for the Kotlin LlamaBridge.
//
// Session 1 scope (fully implemented):
//   nativeLoad          - load GGUF, return context handle
//   nativeFree          - release context
//   nativeGetVocabSize  - vocab size (smoke test)
//   nativeGetContextSize- n_ctx (smoke test)
//   nativeInitBeliefSampler - custom sampler (unused until Session 2)
//   nativeFreeSampler   - release sampler
//
// Session 2 scope (to be added):
//   nativeTokenize
//   nativePrefill
//   nativeGetLogits
//   nativeGenerate

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#include <llama.h>
#include "belief_sampler.h"

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct LlamaHandle {
    llama_model   * model   = nullptr;
    llama_context * ctx     = nullptr;
    int32_t         n_vocab = 0;
    int32_t         n_ctx   = 0;
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_treg_llmpersonalization_engine_LlamaBridge_nativeLoad(
    JNIEnv * env, jobject /*this*/, jstring model_path)
{
    const char * cpath = env->GetStringUTFChars(model_path, nullptr);
    LOGI("nativeLoad: %s", cpath);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    llama_model * model = llama_model_load_from_file(cpath, mparams);
    env->ReleaseStringUTFChars(model_path, cpath);

    if (model == nullptr) {
        LOGE("nativeLoad: llama_model_load_from_file failed");
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx     = 2048;
    cparams.n_batch   = 512;
    cparams.n_threads = 4;

    llama_context * ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        LOGE("nativeLoad: llama_init_from_model failed");
        llama_model_free(model);
        return 0;
    }

    auto * h    = new LlamaHandle();
    h->model    = model;
    h->ctx      = ctx;
    h->n_vocab  = llama_vocab_n_tokens(llama_model_get_vocab(model));
    h->n_ctx    = llama_n_ctx(ctx);

    LOGI("nativeLoad: OK  vocab=%d  n_ctx=%d", h->n_vocab, h->n_ctx);
    return reinterpret_cast<jlong>(h);
}

extern "C" JNIEXPORT void JNICALL
Java_com_treg_llmpersonalization_engine_LlamaBridge_nativeFree(
    JNIEnv * /*env*/, jobject /*this*/, jlong handle)
{
    if (handle == 0) return;
    auto * h = reinterpret_cast<LlamaHandle *>(handle);
    if (h->ctx)   llama_free(h->ctx);
    if (h->model) llama_model_free(h->model);
    delete h;
    llama_backend_free();
    LOGI("nativeFree: done");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_treg_llmpersonalization_engine_LlamaBridge_nativeGetVocabSize(
    JNIEnv * /*env*/, jobject /*this*/, jlong handle)
{
    if (handle == 0) return -1;
    return reinterpret_cast<LlamaHandle *>(handle)->n_vocab;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_treg_llmpersonalization_engine_LlamaBridge_nativeGetContextSize(
    JNIEnv * /*env*/, jobject /*this*/, jlong handle)
{
    if (handle == 0) return -1;
    return reinterpret_cast<LlamaHandle *>(handle)->n_ctx;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_treg_llmpersonalization_engine_LlamaBridge_nativeInitBeliefSampler(
    JNIEnv * /*env*/, jobject /*this*/, jint target_token, jfloat offset)
{
    auto * smpl = llama_sampler_init_belief(
        (int32_t) target_token, (float) offset);
    LOGI("nativeInitBeliefSampler: tid=%d  offset=%.3f", target_token, offset);
    return reinterpret_cast<jlong>(smpl);
}

extern "C" JNIEXPORT void JNICALL
Java_com_treg_llmpersonalization_engine_LlamaBridge_nativeFreeSampler(
    JNIEnv * /*env*/, jobject /*this*/, jlong sampler)
{
    if (sampler == 0) return;
    llama_sampler_free(reinterpret_cast<llama_sampler *>(sampler));
}
