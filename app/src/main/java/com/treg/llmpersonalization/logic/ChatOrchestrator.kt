package com.treg.llmpersonalization.logic

import com.treg.llmpersonalization.engine.ExtractorBridge
import com.treg.llmpersonalization.engine.GenerationConfig
import com.treg.llmpersonalization.engine.LlamaBridge

/**
 * Ports Python chat_headless. Called on the UI thread; runs generation
 * on a background dispatcher by the caller.
 */
class ChatOrchestrator(
    private val llama: LlamaBridge.Loaded,
    private val extractor: ExtractorBridge,
    private val beliefs: BeliefStore
) {
    data class Trace(
        val user: String,
        val message: String,
        val kind: String,
        val mined: BeliefStore.Mined?,
        val conceptKey: String?,
        val conf: Float?,
        val source: String?,
        val gate: String?,
        val applied: String?,
        val gap: Float?,
        val offset: Float?,
        val skipped: String?,
        val response: String
    )

    fun chat(
        message: String,
        user: String,
        softGate: Boolean = true,
        strictGate: Boolean = true,
        useBeliefs: Boolean = true,
        useMining: Boolean = true,
        expanded: Boolean = false
    ): Trace {
        // 1. Extractor
        val ex = extractor.extract(message)

        // 2. Mining (unconditional)
        val mined = if (useMining) {
            beliefs.acquireBelief(message, if (ex.uri == "NONE") null else ex.uri)
        } else null

        // 3. Concept key
        val kind = MessageClassifier.classify(message)
        val subject = MessageClassifier.extractSubject(message)
        val conceptKey = if (ex.uri != "NONE" && !subject.isNullOrEmpty())
            beliefs.conceptKey(ex.uri, subject)
        else null

        // 4. Route
        var appliedTarget: String? = null
        var gateLabel: String? = null
        var skipped: String? = null
        var gap: Float? = null
        var offset: Float? = null
        var targetToken: Int? = null

        when {
            kind == MessageClassifier.Kind.ASSERTION -> {
                val (refTarget, label) = applyAssertionGate(message, conceptKey, softGate)
                gateLabel = label
                if (refTarget != null) {
                    val g = llamaComputeGap(message, refTarget)
                    gap = g
                    if (g > GenerationConfig.MAX_SAFE_GAP) {
                        skipped = "assertion gap $g > ${GenerationConfig.MAX_SAFE_GAP}"
                    } else {
                        offset = GenerationConfig.sigmoidOffset(g)
                        appliedTarget = refTarget
                        targetToken = llamaTokenizeSingle(refTarget)
                    }
                }
            }
            kind == MessageClassifier.Kind.QUESTION && useBeliefs && conceptKey != null -> {
                val eff = beliefs.getEffectiveBelief(conceptKey)
                gateLabel = "lookup: ${eff.source}"
                if (eff.target != null) {
                    var t = eff.target
                    val ref = beliefs.referenceFor(conceptKey)
                    if (strictGate && beliefs.isContradicted(conceptKey, t)) {
                        gateLabel = "BLOCKED '${t.trim()}' -> '${ref?.trim()}'"
                        t = ref!!
                    } else if (ref != null) {
                        gateLabel = "PASS (ref='${ref.trim()}')"
                    } else {
                        gateLabel = "PASS (no reference)"
                    }
                    val g = llamaComputeGap(message, t)
                    gap = g
                    if (g > GenerationConfig.MAX_SAFE_GAP) {
                        skipped = "gap $g > ${GenerationConfig.MAX_SAFE_GAP}"
                    } else {
                        offset = GenerationConfig.sigmoidOffset(g)
                        appliedTarget = t
                        targetToken = llamaTokenizeSingle(t)
                    }
                }
            }
        }

        // 5. Generate
        val route = GenerationConfig.route(hasBelief = appliedTarget != null, expanded = expanded)
        val rawResponse = LlamaBridge.generate(
            loaded = llama,
            prompt = message,
            targetToken = targetToken,
            offset = offset,
            maxTokens = route.maxTokens,
            temperature = route.temperature
        )

        // Strip Qwen think tags (empty by design here)
        val response = stripThink(rawResponse)

        return Trace(
            user = user, message = message,
            kind = kind.name.lowercase(),
            mined = mined,
            conceptKey = conceptKey,
            conf = ex.confidence,
            source = if (kind == MessageClassifier.Kind.QUESTION) gateLabel?.substringAfter("lookup: ") else null,
            gate = gateLabel,
            applied = appliedTarget,
            gap = gap,
            offset = offset,
            skipped = skipped,
            response = response
        )
    }

    private fun applyAssertionGate(
        message: String,
        conceptKey: String?,
        softGate: Boolean
    ): Pair<String?, String> {
        if (!softGate) return null to "soft_gate_off"
        val (subj, target) = MessageClassifier.parseAssertion(message)
        if (subj.isNullOrEmpty() || target.isNullOrEmpty()) return null to "unparseable_assertion"
        if (conceptKey == null) return null to "no_concept"
        val ref = beliefs.referenceFor(conceptKey) ?: return null to "not_in_reference"
        if (!beliefs.isContradicted(conceptKey, target)) return null to "consistent_with_reference"
        return ref to "ASSERTION_BLOCKED '${target.trim()}' -> '${ref.trim()}'"
    }

    private fun llamaComputeGap(prompt: String, target: String): Float {
        val tid = llamaTokenizeSingle(target) ?: return 999f
        return LlamaBridge.computeGap(llama, prompt, tid)
    }

    private fun llamaTokenizeSingle(text: String): Int? {
        val ids = LlamaBridge.tokenize(llama, text) ?: return null
        return if (ids.isEmpty()) null else ids[0]
    }

    private fun stripThink(s: String): String {
        val close = s.indexOf("</think>")
        return if (close >= 0) s.substring(close + 8).trim() else s.trim()
    }
}