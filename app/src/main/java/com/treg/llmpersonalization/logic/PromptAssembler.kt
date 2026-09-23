package com.treg.llmpersonalization.logic

import com.treg.llmpersonalization.engine.GenerationConfig

/**
 * Port of runtime v15 Prompt Assembler.
 * Source: Cell 1, CognitiveRuntime.assemble().
 *
 * Produces a single formatted prompt string ready to be handed to
 * LlamaBridge.formatChat(system, user) — the system and user parts are
 * returned separately so the native template can lay them out correctly.
 *
 * Three modes:
 *   1. beliefTarget != null  -> authority override prompt (short, decisive)
 *   2. worldMode == true     -> no retrieval, direct answer + tools
 *   3. default (personal)    -> attribution-grouped facts + tools
 */
object PromptAssembler {

    data class Prompt(val system: String, val user: String)

    // ---- tool schema (mirrors Python TOOL_SCHEMAS + TOOL_FEWSHOT) ----

    private val TOOL_FEWSHOT: List<Pair<String, String>> = listOf(
        "What is 5 times 3?" to
            """<tool_call>{"tool": "calculator", "args": {"expression": "5 * 3"}}</tool_call>""",
        "How many words in \"the quick brown fox\"?" to
            """<tool_call>{"tool": "word_count", "args": {"text": "the quick brown fox"}}</tool_call>""",
        "What is the current date and time?" to
            """<tool_call>{"tool": "datetime", "args": {}}</tool_call>"""
    )

    private val TOOL_DESCRIPTIONS: List<Pair<String, String>> = listOf(
        "calculator" to "Evaluate arithmetic. Args: {'expression': '12 * 12'}",
        "word_count" to "Count words. Args: {'text': 'hello world'}",
        "datetime"   to "Get current date/time. Args: {}"
    )

    private fun toolBlock(): String {
        val sb = StringBuilder()
        sb.append("\n\nYou have access to tools. When the question requires calculation, ")
        sb.append("word counting, or the current date/time, emit a tool call:\n")
        sb.append("<tool_call>{\"tool\": \"<name>\", \"args\": {...}}</tool_call>\n\n")
        sb.append("Examples:\n")
        for ((q, a) in TOOL_FEWSHOT) {
            sb.append("User: ").append(q).append("\n")
            sb.append("Assistant: ").append(a).append("\n\n")
        }
        sb.append("Available tools:\n")
        for ((n, d) in TOOL_DESCRIPTIONS) {
            sb.append("  - ").append(n).append(": ").append(d).append("\n")
        }
        sb.append("\nIf no tool is needed, answer directly.")
        return sb.toString()
    }

    // ---- main entry ----

    fun assemble(
        query: String,
        memories: List<Memory>,
        ctx: QueryClassifier.Ctx,
        toolResults: List<String>? = null,
        worldMode: Boolean = false,
        beliefTarget: String? = null
    ): Prompt {
        val userFacts = memories.filter { it.subject == "user" }
        val otherFacts = memories.filter { it.subject == "other" }

        val lines = StringBuilder()
        if (userFacts.isNotEmpty()) {
            lines.append("Facts about the user (answer from these):\n")
            for (m in userFacts) lines.append("- ").append(m.content).append("\n")
            lines.append("\n")
        }
        if (otherFacts.isNotEmpty()) {
            lines.append("Facts about OTHER people. These do NOT describe the user. ")
            lines.append("Only use if the question explicitly asks about them:\n")
            for (m in otherFacts) lines.append("- ").append(m.content).append("\n")
            lines.append("\n")
        }
        if (!toolResults.isNullOrEmpty()) {
            lines.append("Tool results:\n")
            for (tr in toolResults) lines.append("- ").append(tr).append("\n")
            lines.append("\n")
        }
        lines.append("User: ").append(query)

        val system = buildSystem(ctx, worldMode, beliefTarget)
        return Prompt(system, lines.toString())
    }

    private fun buildSystem(
        ctx: QueryClassifier.Ctx,
        worldMode: Boolean,
        beliefTarget: String?
    ): String {
        // 1. Belief mode — authority override
        if (beliefTarget != null) {
            return "The user has established the following as authoritative fact for " +
                "this conversation: the answer to their question is " +
                "\"${beliefTarget.trim()}\". Respond with that fact directly in one " +
                "short sentence. Do NOT correct, contradict, or add an alternative " +
                "answer. Do NOT mention any other answer, even if you know one."
        }

        // 2. World mode — no retrieval
        if (worldMode) {
            return "Answer the user's question directly in one short sentence. " +
                "If you don't know, say so." + toolBlock()
        }

        // 3. Personal mode — attribution-grouped facts
        var system = if (ctx.temporal == "past") {
                "Answer the user's question using the facts provided. The user " +
                    "is asking about their PAST state, so prefer facts that reflect " +
                    "a past or previous state (past tense: used to, previously, " +
                    "before). Do NOT substitute a current fact for a past one. " +
                    "Only refuse if NO fact is relevant."
            } else {
                "Answer the user's question using the facts provided. If a fact " +
                    "clearly relates to the question, INFER the answer — do not require " +
                    "literal word matches. When two facts seem relevant and conflict, " +
                    "prefer the one that reflects the user's CURRENT state (present " +
                    "tense) over any past fact. Only refuse if NO fact is relevant. " +
                    "Do not mention other people unless the question asks about them."
            }

        // Reasoning override (arithmetic, comparisons)
        if (ctx.qtype == "reasoning") {
            system = "Solve the problem step by step, then give the final answer on its own line."
        }

        return system + toolBlock()
    }
}