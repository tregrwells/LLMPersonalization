package com.treg.llmpersonalization.logic

import android.util.Log

import com.treg.llmpersonalization.engine.EmbedderBridge
import com.treg.llmpersonalization.engine.RerankerBridge

/**
 * Port of runtime v15 Memory Retriever.
 * Source: Cell 1, CognitiveRuntime.retrieve().
 *
 * Pipeline:
 *   1. Subject filter (user / other / all)
 *   2. Temporal filter (drop past memories unless query implies past)
 *   3. Multi-query expansion (original + simplified + keywords + topic expansions)
 *   4. Dense cosine top-5 per variant -> union of candidate keys
 *   5. Cross-encoder rerank on original query
 *   6. Lexical boost: 0.5 * word overlap count
 *   7. Sort, take top-k
 */
class Retriever(
    private val memoryStore: MemoryStore,
    private val embedder: EmbedderBridge,
    private val reranker: RerankerBridge
) {
    fun retrieve(query: String, ctx: QueryClassifier.Ctx, k: Int = 5): List<Memory> {
        val ql = query.lowercase()
        var pool: List<Memory> = memoryStore.memories
        if (pool.isEmpty()) return emptyList()

        // --- 1. Subject filter ---
        if (ctx.subject == "other") {
            val f = pool.filter { it.subject == "other" }
            if (f.isNotEmpty()) pool = f
        } else if (ctx.subject == "user" &&
                   ql.contains("my ") &&
                   !Regex("""\b(friend|sister|brother|coworker|mother|father)\b""").containsMatchIn(ql)) {
            val f = pool.filter { it.subject == "user" }
            if (f.isNotEmpty()) pool = f
        }

        // --- 2. Temporal filter ---
        val hasPast = Regex(
            """\b(old|previous|former|past|used to|history|had|before|earlier|ago)\b"""
        ).containsMatchIn(ql)
        if ((ctx.temporal == "unspecified" || ctx.temporal == "current") && !hasPast) {
            val f = pool.filter { it.current }
            if (f.isNotEmpty()) pool = f
        }

        // --- 3. Multi-query expansion ---
        val queries = mutableListOf(query)
        val simp = Regex("""\b(what|where|who|when|which|is|are|does|do|my)\b""")
            .replace(ql, "").trim()
        if (simp.isNotEmpty() && simp != ql) queries.add(simp)
        val kw = simp.split(" ").filter { it.length > 3 }.joinToString(" ")
        if (kw.isNotEmpty()) queries.add(kw)
        for (word in ql.split(" ")) {
            val w2 = word.replace(Regex("""[^\w]"""), "")
            TOPIC_EXPANSIONS[w2]?.let { queries.add(it.joinToString(" ")) }
        }

        // --- 4. Candidate gathering via dense top-5 per variant ---
        val candidateKeys = mutableSetOf<String>()
        for (qv in queries) {
            val qe = embedder.embed(qv)
            val scored = pool.map { m -> m to dot(qe, m.embedding) }
                .sortedByDescending { it.second }
                .take(5)
            for ((m, _) in scored) candidateKeys.add(m.key)
        }
        val candidates = pool.filter { it.key in candidateKeys }.ifEmpty { pool }

        // --- 5. Rerank on original query ---
        val pairs = candidates.map { it.content }
        val rerScores = reranker.scoreBatch(query, pairs)

        // --- 6. Lexical boost ---
        val qWords = Regex("""\w+""").findAll(ql).map { it.value }.filter { it.length > 3 }.toSet()
        val scored = candidates.mapIndexed { i, m ->
            var boost = 0f
            if (qWords.isNotEmpty()) {
                val mWords = Regex("""\w+""").findAll(m.content.lowercase())
                    .map { it.value }.toSet()
                boost = 0.5f * qWords.intersect(mWords).size
            }
            m to (rerScores[i] + boost)
        }.sortedByDescending { it.second }

        // --- 7. Top-k with score attached ---
        run {
            val top5 = scored.take(5).joinToString(" ") { (m, s) -> m.key + "=" + "%.3f".format(s) }
            Log.i("Retriever", "q=" + query.take(40) + " sub=" + ctx.subject + " temp=" + ctx.temporal + " k=" + k + " top5: " + top5)
        }
        return scored.take(k).map { (m, s) ->
            Memory(m.key, m.content, m.current, m.subject, m.embedding, s)
        }
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) s += a[i] * b[i]
        return s
    }

    companion object {
        // Multi-memory heuristic — ported from Python MULTI_MEMORY_PATTERNS
        private val MULTI_RE = Regex(
            """\b(both|either|compare|list|all my|tell me about|which of my|how many of my|""" +
            """family members|pets|hobbies|cars|languages|programming languages|cities|""" +
            """have i|do i have|have i (ever|had)|has my|changed|over my life|moved)\b""",
            RegexOption.IGNORE_CASE
        )

        private val TOPIC_EXPANSIONS: Map<String, List<String>> = mapOf(
            "hobbies"  to listOf("guitar", "climbing", "weekends"),
            "hobby"    to listOf("guitar", "climbing"),
            "old"      to listOf("past", "previous", "former"),
            "previous" to listOf("past", "old", "former"),
            "former"   to listOf("past", "old", "previous"),
            "brother"  to listOf("sibling"),
            "sister"   to listOf("sibling"),
            "coworker" to listOf("colleague"),
            "pet"      to listOf("dog", "cat"),
            "pets"     to listOf("dog", "cat"),
            "family"   to listOf("wife", "daughter", "son")
        )

        fun isMultiMemory(query: String): Boolean = MULTI_RE.containsMatchIn(query)
    }
}