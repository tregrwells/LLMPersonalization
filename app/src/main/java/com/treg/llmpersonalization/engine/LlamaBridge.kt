package com.treg.llmpersonalization.engine

/**
 * JNI bridge to llama.cpp.
 *
 * Session 1: load / free / vocab size.
 * Session 2 will add tokenize / prefill / generate / belief sampler wiring.
 *
 * Handle semantics: a non-zero Long returned from [load] is a native
 * context pointer. It must be passed back to every subsequent call and
 * released exactly once via [close].
 */
object LlamaBridge {

    init {
        System.loadLibrary("jni_bridge")
    }

    // --- Session 1 ---

    external fun nativeLoad(modelPath: String): Long
    external fun nativeFree(handle: Long)
    external fun nativeGetVocabSize(handle: Long): Int
    external fun nativeGetContextSize(handle: Long): Int

    // --- Session 2 (stubbed in native code) ---

    external fun nativeInitBeliefSampler(targetToken: Int, offset: Float): Long
    external fun nativeFreeSampler(sampler: Long)

    // --- Kotlin-friendly wrappers ---

    /** Result of a successful model load. */
    data class Loaded(
        val handle: Long,
        val vocabSize: Int,
        val contextSize: Int,
    )

    /**
     * Load a GGUF model. Returns null if the native load failed.
     * The caller is responsible for calling [close] when done.
     */
    fun load(modelPath: String): Loaded? {
        val h = nativeLoad(modelPath)
        if (h == 0L) return null
        return Loaded(
            handle = h,
            vocabSize = nativeGetVocabSize(h),
            contextSize = nativeGetContextSize(h),
        )
    }

    fun close(loaded: Loaded) {
        nativeFree(loaded.handle)
    }
}