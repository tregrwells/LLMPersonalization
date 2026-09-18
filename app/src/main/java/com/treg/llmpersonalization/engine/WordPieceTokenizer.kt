package com.treg.llmpersonalization.engine

import android.content.Context
import java.text.Normalizer

/**
 * HuggingFace-compatible WordPiece tokenizer for DistilBERT.
 *
 * Pipeline: clean -> lowercase -> strip accents -> split on whitespace/punct
 *           -> WordPiece subword with ## prefix.
 *
 * Vocab is loaded once from assets/vocab.txt (232 KB, 30522 entries).
 */
class WordPieceTokenizer private constructor(
    private val vocab: Map<String, Int>
) {
    private val clsId = vocab["[CLS]"] ?: 101
    private val sepId = vocab["[SEP]"] ?: 102
    private val padId = vocab["[PAD]"] ?: 0
    private val unkId = vocab["[UNK]"] ?: 100

    val padTokenId: Int get() = padId

    fun tokenize(text: String, maxLen: Int = 96): Pair<LongArray, LongArray> {
        val ids = mutableListOf<Int>()
        ids.add(clsId)
        for (tok in basicTokenize(cleanText(text))) {
            wordPiece(tok, ids)
        }
        ids.add(sepId)

        val truncated = if (ids.size > maxLen) ids.subList(0, maxLen).toMutableList() else ids
        val mask = LongArray(maxLen) { if (it < truncated.size) 1L else 0L }
        val padded = LongArray(maxLen) { if (it < truncated.size) truncated[it].toLong() else padId.toLong() }

        if (truncated.isNotEmpty() && truncated.size == maxLen) {
            // Ensure last real token is SEP even if truncated
            padded[maxLen - 1] = sepId.toLong()
        }

        return padded to mask
    }

    // --- Internal ---

    private fun cleanText(text: String): String {
        val sb = StringBuilder()
        for (c in text) {
            when {
                c.isWhitespace() -> sb.append(' ')
                c.code == 0 || c.code == 0xFFFD || isControl(c) -> {}
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun isControl(c: Char): Boolean {
        if (c == '\t' || c == '\n' || c == '\r') return false
        val type = Character.getType(c)
        return type == Character.CONTROL.toInt()
    }

    private fun basicTokenize(text: String): List<String> {
        val lowered = text.lowercase()
        val stripped = stripAccents(lowered)

        val out = mutableListOf<String>()
        val sb = StringBuilder()

        for (c in stripped) {
            when {
                c.isWhitespace() -> {
                    if (sb.isNotEmpty()) { out.add(sb.toString()); sb.clear() }
                }
                c.isLetterOrDigit() -> sb.append(c)
                else -> {
                    if (sb.isNotEmpty()) { out.add(sb.toString()); sb.clear() }
                    out.add(c.toString())
                }
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    private fun stripAccents(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        val sb = StringBuilder(normalized.length)
        for (c in normalized) {
            if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun wordPiece(word: String, out: MutableList<Int>) {
        if (word.length > 100) {
            out.add(unkId)
            return
        }
        if (word.isEmpty()) return

        var start = 0
        val subTokens = mutableListOf<Int>()
        val maxChars = word.length

        while (start < maxChars) {
            var end = maxChars
            var curId = -1
            while (start < end) {
                val substr = if (start > 0) "##" + word.substring(start, end) else word.substring(start, end)
                val id = vocab[substr]
                if (id != null) { curId = id; break }
                end--
            }
            if (curId == -1) {
                out.add(unkId)
                return
            }
            subTokens.add(curId)
            start = end
        }
        out.addAll(subTokens)
    }

    companion object {
        fun load(context: Context, assetName: String = "vocab.txt"): WordPieceTokenizer {
            val vocab = HashMap<String, Int>(40000)
            context.assets.open(assetName).bufferedReader().useLines { seq ->
                seq.forEachIndexed { idx, line ->
                    vocab[line.trim()] = idx
                }
            }
            return WordPieceTokenizer(vocab)
        }
    }
}