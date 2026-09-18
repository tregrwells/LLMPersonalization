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

static std::string jstring_to_std(JNIEnv * env, jstring s) {
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_treg_llmpersonalization_engine_LlamaBridge_nativeLoad(
    JNIEnv * env, jobject, jstring model_path)
{
    std::string path = jstring_to_std(env, model_path);
    LOGI("nativeLoad: %s", path.c_str());

    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;

    llama_model * model = llama_model_load_from_file(path.c_str(), mp);
    if (!model) { LOGE("model load failed"); return 0; }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx     = 2048;
    cp.n_batch   = 512;
    cp.n_threads = 4;

    llama_context * ctx = llama_init_from_model(model, cp);
    if (!ctx) { llama_model_free(model); return 0; }

    auto * h = new LlamaHandle();
    h->model   = model;
    h->ctx     = ctx;
    h->n_vocab = llama_vocab_n_tokens(llama_model_get_vocab(model));
    h->n_ctx   = llama_n_ctx(ctx);
    LOGI("nativeLoad: OK vocab=%d n_ctx=%d", h->n_vocab, h->n_ctx);
    return reinterpret_cast<jlong>(h);
}

extern "C" JNIEXPORT void JNICALL
Java_com_treg_llmpersonalization_engine_LlamaBridge_nativeFree(
    JNIEnv *, jobject, jlong handle)
{
    if (!handle) return;
    auto * h = reinterpret_cast<LlamaHandle *>(handle);
    if (h->ctx)   llama_free(h->ctx);
    if (h->model) llama_model_free(h->model);
    delete h;
    llama_backend_free();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_treg_llmpersonalization_engine_LlamaBridge_nativeGetVocabSize(
    JNIEnv *, jobject, jlong handle)
{
    return handle ? reinterpret_cast<LlamaHandle *>(handle)->n_vocab : -1;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_treg_llmpersonalization_engine_LlamaBridge_nativeGetContextSize(
    JNIEnv *, jobject, jlong handle)
{
    return handle ? reinterpret_cast<LlamaHandle *>(handle)->n_ctx : -1;
}

// ---------------------------------------------------------------
// Tokenize (debug / future use)
// ---------------------------------------------------------------
extern "C" JNIEXPORT jintArray JNICALL
Java_com_treg_llmpersonalization_engine_LlamaBridge_nativeTokenize(
    JNIEnv * env, jobject, jlong handle, jstring text)
{
    if (!handle) return nullptr;
    auto * h = reinterpret_cast<LlamaHandle *>(handle);
    std::string s = jstring_to_std(env, text);
    const llama_vocab * vocab = llama_model_get_vocab(h->model);

    int32_t n = -llama_tokenize(vocab, s.c_str(), s.size(), nullptr, 0, true, true);
    std::vector<llama_token> toks(n);
    llama_tokenize(vocab, s.c_str(), s.size(), toks.data(), toks.size(), true, true);

    jintArray arr = env->NewIntArray(n);
    env->SetIntArrayRegion(arr, 0, n, reinterpret_cast<jint*>(toks.data()));
    return arr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_treg_llmpersonalization_engine_LlamaBridge_nativeDetokenize(
    JNIEnv * env, jobject, jlong handle, jint token_id)
{
    if (!handle) return env->NewStringUTF("");
    auto * h = reinterpret_cast<LlamaHandle *>(handle);
    const llama_vocab * vocab = llama_model_get_vocab(h->model);
    char buf[256];
    int32_t n = llama_token_to_piece(vocab, (llama_token) token_id,
                                     buf, sizeof(buf), 0, false);
    if (n <= 0) return env->NewStringUTF("");
    return env->NewStringUTF(std::string(buf, n).c_str());
}

// ---------------------------------------------------------------
// Generate — full loop in C++. Returns generated text.
// target_token = -1 means no belief.
// ---------------------------------------------------------------
extern "C" JNIEXPORT jstring JNICALL
Java_com_treg_llmpersonalization_engine_LlamaBridge_nativeGenerate(
    JNIEnv * env, jobject,
    jlong handle, jstring user_text,
    jint target_token, jfloat offset,
    jint max_tokens, jfloat temperature)
{
    if (!handle) return env->NewStringUTF("[error: no handle]");
    auto * h = reinterpret_cast<LlamaHandle *>(handle);
    if (!h->ctx || !h->model) return env->NewStringUTF("[error: model not loaded]");

    std::string user = jstring_to_std(env, user_text);
    const llama_vocab * vocab = llama_model_get_vocab(h->model);

    // --- Chat template ---
    std::string formatted = user;
    const char * tmpl = llama_model_chat_template(h->model, nullptr);
    if (tmpl) {
        struct llama_chat_message msgs[1] = { {"user", user.c_str()} };
        int32_t need = llama_chat_apply_template(tmpl, msgs, 1, true, nullptr, 0);
        if (need > 0) {
            std::vector<char> buf(need + 1);
            llama_chat_apply_template(tmpl, msgs, 1, true, buf.data(), buf.size());
            formatted.assign(buf.data(), need);
        }
    }

    // --- Tokenize ---
    int32_t n_tok = -llama_tokenize(vocab, formatted.c_str(), formatted.size(),
                                     nullptr, 0, true, true);
    if (n_tok <= 0) return env->NewStringUTF("[error: tokenize failed]");
    std::vector<llama_token> tokens(n_tok);
    if (llama_tokenize(vocab, formatted.c_str(), formatted.size(),
                       tokens.data(), tokens.size(), true, true) < 0)
        return env->NewStringUTF("[error: tokenize buffer]");

    // --- Prefill ---
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    if (llama_decode(h->ctx, batch) != 0)
        return env->NewStringUTF("[error: prefill failed]");
    int32_t n_past = tokens.size();

    // --- Sampler chain ---
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler * chain = llama_sampler_chain_init(sparams);

    if (target_token >= 0) {
        // Belief path: forced-then-greedy
        llama_sampler_chain_add(chain,
            llama_sampler_init_belief((int32_t) target_token, (float) offset));
        llama_sampler_chain_add(chain, llama_sampler_init_greedy());
        LOGI("generate: belief tid=%d offset=%.3f", target_token, offset);
    } else if (temperature <= 0.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    // --- Loop ---
    std::string output;
    llama_token eos = llama_vocab_eos(vocab);

    for (int i = 0; i < max_tokens; ++i) {
        llama_token id = llama_sampler_sample(chain, h->ctx, -1);
        if (id == eos) break;

        char piece[256];
        int32_t np = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, false);
        if (np > 0) output.append(piece, np);

        struct llama_batch next = llama_batch_init(1, 0, 1);
        next.token[0]    = id;
        next.pos[0]      = n_past;
        next.n_seq_id[0] = 1;
        next.seq_id[0][0] = 0;
        next.logits[0]   = 1;
        next.n_tokens    = 1;
        if (llama_decode(h->ctx, next) != 0) {
            llama_batch_free(next);
            break;
        }
        llama_batch_free(next);
        n_past++;
    }

    llama_sampler_free(chain);
    LOGI("generate: %d tokens -> %zu chars", max_tokens, output.size());
    return env->NewStringUTF(output.c_str());
}

// ---------------------------------------------------------------
// Standalone sampler init (kept for Session 3 wiring)
// ---------------------------------------------------------------
extern "C" JNIEXPORT jlong JNICALL
Java_com_treg_llmpersonalization_engine_LlamaBridge_nativeInitBeliefSampler(
    JNIEnv *, jobject, jint target_token, jfloat offset)
{
    return reinterpret_cast<jlong>(
        llama_sampler_init_belief((int32_t) target_token, (float) offset));
}

extern "C" JNIEXPORT void JNICALL
Java_com_treg_llmpersonalization_engine_LlamaBridge_nativeFreeSampler(
    JNIEnv *, jobject, jlong sampler)
{
    if (sampler) llama_sampler_free(reinterpret_cast<llama_sampler *>(sampler));
}