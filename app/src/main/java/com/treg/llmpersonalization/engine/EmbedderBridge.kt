package com.treg.llmpersonalization.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * Runs embedder_int8.onnx (all-MiniLM-L6-v2) on-device.
 * Output: 384-d L2-normalized mean-pooled embedding.
 * Matches the Phase B export exactly (EmbedderWrapper in Cell B2).
 */
class EmbedderBridge private constructor(
    private val session: OrtSession,
    private val env: OrtEnvironment,
    private val tokenizer: WordPieceTokenizer
) {
    fun embed(text: String): FloatArray {
        val (ids, mask) = tokenizer.tokenize(text, maxLen = MAX_LEN)

        val idsTensor = OnnxTensor.createTensor(
            env, LongBuffer.wrap(ids), longArrayOf(1, MAX_LEN.toLong())
        )
        val maskTensor = OnnxTensor.createTensor(
            env, LongBuffer.wrap(mask), longArrayOf(1, MAX_LEN.toLong())
        )

        try {
            val inputs = mapOf("input_ids" to idsTensor, "attention_mask" to maskTensor)
            session.run(inputs).use { output ->
                @Suppress("UNCHECKED_CAST")
                val emb = (output[0].value as Array<FloatArray>)[0]
                var norm = 0f
                for (v in emb) norm += v * v
                norm = sqrt(norm).coerceAtLeast(1e-12f)
                for (i in emb.indices) emb[i] /= norm
                return emb
            }
        } finally {
            idsTensor.close()
            maskTensor.close()
        }
    }

    fun close() {
        session.close()
        env.close()
    }

    companion object {
        const val DIM = 384
        private const val MAX_LEN = 128

        fun load(context: Context, modelFile: File): EmbedderBridge {
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val session = env.createSession(modelFile.absolutePath, opts)
            val tokenizer = WordPieceTokenizer.load(context)
            return EmbedderBridge(session, env, tokenizer)
        }
    }
}