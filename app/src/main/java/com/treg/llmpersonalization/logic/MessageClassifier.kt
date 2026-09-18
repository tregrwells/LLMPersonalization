package com.treg.llmpersonalization.logic

/**
 * Kotlin port of the Python classification helpers.
 * Source of truth: Part 6 of the v1.9 handoff, Cell 2 sections 2-3.
 */
object MessageClassifier {

    private val QUESTION_STARTERS = listOf(
        "what", "where", "who", "when", "why", "how",
        "is ", "are ", "can ", "do ", "does ", "will ",
        "could ", "would ", "should "
    )

    private val ASSERTION_CONNECTORS = listOf(" is ", " was ", " are ", " were ", " equals ")

    enum class Kind { QUESTION, ASSERTION, OTHER }

    fun classify(message: String): Kind {
        if (isQuestion(message)) return Kind.QUESTION
        val (subj, _) = parseAssertion(message)
        if (!subj.isNullOrEmpty()) return Kind.ASSERTION
        return Kind.OTHER
    }

    fun isQuestion(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.endsWith("?")) return true
        return QUESTION_STARTERS.any { t.startsWith(it) }
    }

    data class Assertion(val subject: String?, val target: String?)

    fun parseAssertion(message: String): Assertion {
        val msg = message.trimEnd('.', ',', ';', ':', '!', '?')
        for (conn in ASSERTION_CONNECTORS) {
            val idx = msg.indexOf(conn, ignoreCase = true)
            if (idx < 0) continue
            val left = msg.substring(0, idx).trim()
            val right = msg.substring(idx + conn.length).trim()
            if (left.isEmpty() || right.isEmpty()) continue

            var subj: String? = null
            for (word in left.split(" ").reversed()) {
                val cleaned = word.trim(',', '.', '!', '?', ';', ':', '\'', '"')
                if (cleaned.isNotEmpty() && cleaned[0].isUpperCase() && cleaned.length > 1) {
                    subj = cleaned.lowercase()
                    break
                }
            }
            if (subj == null) {
                val parts = left.split(" ")
                if (parts.isNotEmpty()) {
                    subj = parts.last().lowercase().trim(',', '.', '!', '?', ';', ':', '\'', '"')
                }
            }
            if (!subj.isNullOrEmpty()) {
                val target = if (right.startsWith(" ")) right else " $right"
                return Assertion(subj, target)
            }
        }
        return Assertion(null, null)
    }

    fun extractSubject(message: String): String? {
        return if (isQuestion(message)) {
            val msg = message.trimEnd('.', ',', ';', ':', '!', '?', ' ').lowercase()
            val ofMatch = Regex("""\bof\s+([a-z][a-z0-9_\-]{1,40})$""").find(msg)
            if (ofMatch != null) return ofMatch.groupValues[1].replace(" ", "_").replace("-", "_")
            val inMatch = Regex("""\bin\s+([a-z][a-z0-9_\-]{1,40})$""").find(msg)
            if (inMatch != null) return inMatch.groupValues[1].replace(" ", "_").replace("-", "_")
            null
        } else {
            parseAssertion(message).subject
        }
    }
}