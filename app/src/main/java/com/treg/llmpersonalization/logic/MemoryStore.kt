package com.treg.llmpersonalization.logic

import android.content.Context
import org.json.JSONObject

/**
 * Loads memories_v15.json from assets. Contains 26 facts with precomputed
 * 384-d embeddings (already L2-normalized by the embedder during export).
 *
 * Frozen at project build time. Runtime additions go through a different
 * path (see BeliefStore for user-asserted beliefs).
 */
class MemoryStore private constructor(
    val memories: List<Memory>
) {
    companion object {
        private const val ASSET = "memories_v15.json"

        fun load(context: Context): MemoryStore {
            val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val arr = root.getJSONArray("memories")
            val out = ArrayList<Memory>(arr.length())

            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val embArr = o.getJSONArray("embedding")
                val emb = FloatArray(embArr.length()) { embArr.getDouble(it).toFloat() }
                out.add(
                    Memory(
                        key = o.getString("key"),
                        content = o.getString("content"),
                        current = o.getBoolean("current"),
                        subject = o.getString("subject"),
                        embedding = emb
                    )
                )
            }
            return MemoryStore(out)
        }
    }
}

data class Memory(
    val key: String,
    val content: String,
    val current: Boolean,
    val subject: String,
    val embedding: FloatArray,
    var score: Float = 0f
) {
    // Equality on key only — safe for set operations during retrieval.
    override fun equals(other: Any?): Boolean = other is Memory && other.key == key
    override fun hashCode(): Int = key.hashCode()
}