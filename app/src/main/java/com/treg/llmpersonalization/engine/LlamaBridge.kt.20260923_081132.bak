package com.treg.llmpersonalization.engine

object LlamaBridge {
    init { System.loadLibrary("jni_bridge") }

    external fun nativeLoad(modelPath: String): Long
    external fun nativeFree(handle: Long)
    external fun nativeGetVocabSize(handle: Long): Int
    external fun nativeGetContextSize(handle: Long): Int

    external fun nativeTokenize(handle: Long, text: String): IntArray?
    external fun nativeDetokenize(handle: Long, tokenId: Int): String

    external fun nativeComputeGap(handle: Long, prompt: String, targetToken: Int): Float

    external fun nativeGenerate(
        handle: Long, userText: String,
        targetToken: Int, offset: Float,
        maxTokens: Int, temperature: Float
    ): String

    external fun nativeInitBeliefSampler(targetToken: Int, offset: Float): Long
    external fun nativeFreeSampler(sampler: Long)

    data class Loaded(val handle: Long, val vocabSize: Int, val contextSize: Int)

    fun load(modelPath: String): Loaded? {
        val h = nativeLoad(modelPath)
        if (h == 0L) return null
        return Loaded(h, nativeGetVocabSize(h), nativeGetContextSize(h))
    }

    fun close(loaded: Loaded) = nativeFree(loaded.handle)

    fun tokenize(loaded: Loaded, text: String): IntArray? =
        nativeTokenize(loaded.handle, text)

    fun computeGap(loaded: Loaded, prompt: String, targetToken: Int): Float =
        nativeComputeGap(loaded.handle, prompt, targetToken)

    fun generate(
        loaded: Loaded, prompt: String,
        targetToken: Int? = null, offset: Float? = null,
        maxTokens: Int = 75, temperature: Float = 0.7f
    ): String = nativeGenerate(
        loaded.handle, prompt,
        targetToken ?: -1, offset ?: 0f,
        maxTokens, temperature
    )
}