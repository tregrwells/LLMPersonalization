package com.treg.llmpersonalization.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import org.json.JSONArray
import java.io.File
import java.nio.LongBuffer

/**
 * Runs extractor_int8.onnx on-device.
 *
 * Output: (relation_uri, confidence).
 * Span extraction is handled by regex in the logic layer — the ONNX
 * span heads are ignored here, matching the Python get_concept path.
 */
class ExtractorBridge private constructor(
    private val session: OrtSession,
    private val env: OrtEnvironment,
    private val tokenizer: WordPieceTokenizer,
    private val saturatedUris: List<String>,
    private val noneUri: String = "NONE"
) {
    data class Result(val uri: String, val confidence: Float)

    fun extract(text: String): Result {
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
                val relationLogits = output[0].value as Array<FloatArray>
                val row = relationLogits[0]

                var topIdx = 0
                var topVal = row[0]
                for (i in row.indices) {
                    if (row[i] > topVal) { topVal = row[i]; topIdx = i }
                }

                // Softmax over top for confidence
                var maxLogit = row[0]
                for (v in row) if (v > maxLogit) maxLogit = v
                var sum = 0.0
                for (v in row) sum += Math.exp((v - maxLogit).toDouble())
                val conf = (Math.exp((row[topIdx] - maxLogit).toDouble()) / sum).toFloat()

                val uri = when {
                    topIdx >= saturatedUris.size -> noneUri
                    else -> saturatedUris[topIdx]
                }
                return Result(uri, conf)
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
        private const val MAX_LEN = 96
        private const val MODEL_ASSET = "extractor_int8.onnx"
        private const val SCHEMA_ASSET = "rebel_schema.json"

        fun load(context: Context, modelFile: File, schemaJson: String): ExtractorBridge {
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val session = env.createSession(modelFile.absolutePath, opts)
            val tokenizer = WordPieceTokenizer.load(context)

            val schemaRoot = org.json.JSONObject(schemaJson)
            val arr: JSONArray = schemaRoot.getJSONArray("saturated_relations")
            val uris = (0 until arr.length()).map { arr.getString(it) }

            return ExtractorBridge(session, env, tokenizer, uris)
        }
    }
}