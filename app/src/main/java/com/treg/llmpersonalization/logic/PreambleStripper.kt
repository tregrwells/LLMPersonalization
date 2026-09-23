package com.treg.llmpersonalization.logic

/**
 * Port of runtime v15 Preamble Stripper.
 * Source: Cell 1, PREAMBLE_PATTERNS + strip_preamble().
 *
 * Iteratively removes injection prefixes from the head of a query.
 * Returns the original string unchanged if stripping would leave it empty.
 */
object PreambleStripper {

    private val PATTERNS: List<Regex> = listOf(
        Regex("""^ignore\s+all\s+previous\s+instructions?\s+and\s+""", RegexOption.IGNORE_CASE),
        Regex("""^ignore\s+(all\s+)?previous\s+instructions?[.,;:\s]+""", RegexOption.IGNORE_CASE),
        Regex("""^forget\s+everything[.,;:\s]+""", RegexOption.IGNORE_CASE),
        Regex("""^pretend\s+you\s+have\s+no\s+rules[:\s]+""", RegexOption.IGNORE_CASE),
        Regex("""^system[:\s]+you\s+are\s+now\s+in\s+debug\s+mode[.,;:\s]+""", RegexOption.IGNORE_CASE),
        Regex("""^system[:\s]+""", RegexOption.IGNORE_CASE),
        Regex("""^new\s+instructions?[:\s]+""", RegexOption.IGNORE_CASE),
        Regex("""^new\s+system\s+prompt[:\s]+""", RegexOption.IGNORE_CASE),
        Regex("""^\[INST\][^\[]*\[/INST\]\s*""", RegexOption.IGNORE_CASE),
        Regex("""^</?system>\s*""", RegexOption.IGNORE_CASE),
        Regex("""^respond\s+only\s+in\s+\w+[.,;:\s]+""", RegexOption.IGNORE_CASE)
    )

    fun strip(query: String): String {
        var result = query
        var changed = true
        var guard = 0
        while (changed && guard < 20) {
            changed = false
            guard++
            for (pat in PATTERNS) {
                val m = pat.find(result) ?: continue
                if (m.range.first == 0 && m.value.isNotEmpty()) {
                    result = result.substring(m.value.length).trim()
                    changed = true
                }
            }
        }
        return if (result.isEmpty()) query else result
    }
}