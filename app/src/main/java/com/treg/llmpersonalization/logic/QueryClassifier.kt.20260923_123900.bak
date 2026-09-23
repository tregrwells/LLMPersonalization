package com.treg.llmpersonalization.logic

import android.util.Log

/**
 * Port of runtime v15 Query Classifier.
 * Source: Cell 1, CognitiveRuntime.classify() + match_concept().
 *
 * Produces a 4-tuple used by Retriever and PromptAssembler:
 *   qtype    - "fact" | "reasoning" | "chat"
 *   subject  - "user" | "other" | "unspecified"
 *   temporal - "current" | "past" | "unspecified"
 *   concept  - (relationUri, subjectKey) | null  e.g. ("P36", "france")
 */
object QueryClassifier {

    data class Concept(val uri: String, val subject: String)

    data class Ctx(
        val qtype: String,
        val subject: String,
        val temporal: String,
        val concept: Concept?
    )

    // ---- pattern tables (verbatim from Python) ----

    private val FACT_RE      = Regex("""\b(what|where|who|when|which|whose)\b""", RegexOption.IGNORE_CASE)
    private val REASON_RE    = Regex("""\b(calculate|solve|compute|how\s+many)\b""", RegexOption.IGNORE_CASE)
    private val OTHER_RE     = Regex("""\bmy\s+(friend|sister|brother|coworker|mother|father)\b""", RegexOption.IGNORE_CASE)
    private val USER_RE      = Regex("""\bmy\b|\bme\b|\bi\b""", RegexOption.IGNORE_CASE)
    private val CURRENT_RE   = Regex("""\b(currently|current|now|these\s+days|present)\b""", RegexOption.IGNORE_CASE)
    private val PAST_RE      = Regex("""\b(used\s+to|previous|formerly|old|former|past|before)\b""", RegexOption.IGNORE_CASE)

    private val CONCEPT_PATTERNS: List<Triple<String, Regex, String>> = listOf(
        Triple("P36", Regex("""\bfrance\b""",   RegexOption.IGNORE_CASE), "france"),
        Triple("P36", Regex("""\bjapan\b""",    RegexOption.IGNORE_CASE), "japan"),
        Triple("P36", Regex("""\bitaly\b""",    RegexOption.IGNORE_CASE), "italy"),
        Triple("P36", Regex("""\bgermany\b""",  RegexOption.IGNORE_CASE), "germany"),
        Triple("P36", Regex("""\bspain\b""",    RegexOption.IGNORE_CASE), "spain"),
        Triple("P36", Regex("""\bportugal\b""", RegexOption.IGNORE_CASE), "portugal"),
        Triple("P50", Regex("""\bhamlet\b""",   RegexOption.IGNORE_CASE), "hamlet"),
        Triple("P50", Regex("""\b1984\b""",     RegexOption.IGNORE_CASE), "1984"),
        Triple("P19", Regex("""\balbert\s+einstein\b""", RegexOption.IGNORE_CASE), "albert_einstein")
    )

    fun classify(query: String): Ctx {
        val ql = query.lowercase()

        val qt = when {
            REASON_RE.containsMatchIn(ql) -> "reasoning"
            FACT_RE.containsMatchIn(ql)   -> "fact"
            else                          -> "chat"
        }

        val sb = when {
            OTHER_RE.containsMatchIn(ql) -> "other"
            USER_RE.containsMatchIn(ql)  -> "user"
            else                         -> "unspecified"
        }

        val tp = when {
            CURRENT_RE.containsMatchIn(ql) -> "current"
            PAST_RE.containsMatchIn(ql)    -> "past"
            else                           -> "unspecified"
        }

        val concept = matchConcept(query)
        val ctx = Ctx(qt, sb, tp, concept)
        Log.i("QClassify", "q='${query.take(50)}' qtype=$qt subject=$sb temporal=$tp " +
            "concept=${concept?.let { "${it.uri}|${it.subject}" } ?: "-"}")
        return ctx
    }

    private fun matchConcept(q: String): Concept? {
        for ((uri, pat, subj) in CONCEPT_PATTERNS) {
            if (pat.containsMatchIn(q)) return Concept(uri, subj)
        }
        return null
    }

    fun conceptKey(c: Concept): String = "${c.uri}|${c.subject}"
}