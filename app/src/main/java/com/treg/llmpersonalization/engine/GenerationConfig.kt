package com.treg.llmpersonalization.engine

import kotlin.math.exp

/**
 * Ports the Python generation-length policy and offset formula.
 * Source of truth: Part 6 of the v1.9 handoff, Cell 2.
 */
object GenerationConfig {
    const val BELIEF_GEN_TOKENS   = 30
    const val FREE_GEN_TOKENS     = 75
    const val FREE_GEN_TOKENS_MAX = 150
    const val MAX_SAFE_GAP        = 25.0f

    fun sigmoidOffset(
        gap: Float,
        conf: Float = 0.822f,
        thr: Float = 0.6f,
        s: Float = 15.0f
    ): Float = maxOf(0.5f, gap * 1.15f) / (1f + exp(-s * (conf - thr)))

    data class Route(val maxTokens: Int, val temperature: Float, val belief: Boolean)

    fun route(hasBelief: Boolean, expanded: Boolean = false): Route =
        if (hasBelief) Route(BELIEF_GEN_TOKENS, 0f, true)
        else Route(
            if (expanded) FREE_GEN_TOKENS_MAX else FREE_GEN_TOKENS,
            0.7f, false
        )
}