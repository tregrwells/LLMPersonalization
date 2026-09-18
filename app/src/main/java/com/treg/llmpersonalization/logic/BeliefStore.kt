package com.treg.llmpersonalization.logic

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Per-user belief storage.
 *
 * - Preloaded tables and reference tier come from assets (read-only).
 * - Mined beliefs persist per-user at filesDir/beliefs/<userId>.json.
 * - switchUser() reloads mined state without recreating the store.
 */
class BeliefStore private constructor(
    private val preloaded: MutableMap<String, MutableMap<String, String>>,
    private val reference: Map<String, String>,
    private val beliefsDir: File,
    private var userId: String,
    private var mined: MutableMap<String, MutableMap<String, Int>>
) {
    companion object {
        const val MIN_BELIEF_COUNT = 2

        fun load(context: Context, beliefJson: String, userId: String): BeliefStore {
            val root = JSONObject(beliefJson)
            val tablesObj = root.getJSONObject("belief_tables")
            val refObj = root.getJSONObject("reference_tier")

            val preloaded = mutableMapOf<String, MutableMap<String, String>>()
            for (user in tablesObj.keys()) {
                val inner = tablesObj.getJSONObject(user)
                val map = mutableMapOf<String, String>()
                for (key in inner.keys()) map[key] = inner.getString(key)
                preloaded[user] = map
            }

            val reference = mutableMapOf<String, String>()
            for (key in refObj.keys()) reference[key] = refObj.getString(key)

            val beliefsDir = File(context.filesDir, "beliefs").apply { mkdirs() }

            val store = BeliefStore(preloaded, reference, beliefsDir, userId, mutableMapOf())
            store.reloadMined()
            return store
        }
    }

    fun userId(): String = userId

    fun switchUser(newUserId: String) {
        userId = newUserId
        reloadMined()
    }

    private fun minedFile(): File = File(beliefsDir, "$userId.json")

    private fun reloadMined() {
        mined = mutableMapOf()
        val f = minedFile()
        if (!f.exists()) return
        try {
            val root = JSONObject(f.readText())
            for (concept in root.keys()) {
                val targetsObj = root.getJSONObject(concept)
                val targetsMap = mutableMapOf<String, Int>()
                for (t in targetsObj.keys()) targetsMap[t] = targetsObj.getInt(t)
                mined[concept] = targetsMap
            }
        } catch (_: Exception) { mined = mutableMapOf() }
    }

    fun users(): List<String> = preloaded.keys.toList()

    fun conceptKey(uri: String, subject: String): String = "$uri|$subject"

    data class Mined(val concept: String, val target: String, val count: Int)

    fun acquireBelief(message: String, uri: String?): Mined? {
        if (MessageClassifier.isQuestion(message)) return null
        if (uri.isNullOrEmpty() || uri == "NONE") return null
        val (subject, target) = MessageClassifier.parseAssertion(message)
        if (subject.isNullOrEmpty() || target.isNullOrEmpty()) return null

        val key = conceptKey(uri, subject)
        val targetMap = mined.getOrPut(key) { mutableMapOf() }
        val newCount = (targetMap[target] ?: 0) + 1
        targetMap[target] = newCount
        persist()
        return Mined(key, target, newCount)
    }

    data class Effective(val target: String?, val source: String)

    fun getEffectiveBelief(key: String): Effective {
        val minedEntry = mined[key]
        if (minedEntry != null && minedEntry.isNotEmpty()) {
            val sorted = minedEntry.entries.sortedByDescending { it.value }
            val topCount = sorted[0].value
            val topTargets = sorted.filter { it.value == topCount }.map { it.key }

            if (topTargets.size > 1) {
                // Ties fall through to preloaded if this user has one
                val personal = preloaded[userId]?.get(key)
                return if (personal != null)
                    Effective(personal, "TIE_PRELOADED_WINS(mined=$topTargets)")
                else
                    Effective(null, "AMBIGUOUS($topTargets)")
            }
            if (topCount >= MIN_BELIEF_COUNT) {
                val top = sorted[0]
                return Effective(top.key, "MINED('${top.key.trim()}' x$topCount)")
            }
        }
        val personal = preloaded[userId]?.get(key)
        if (personal != null) return Effective(personal, "PRELOADED")
        return Effective(null, "NOT_FOUND")
    }

    fun referenceFor(key: String): String? = reference[key]

    fun isContradicted(key: String, target: String): Boolean {
        val ref = reference[key] ?: return false
        return canon(ref) != canon(target)
    }

    private fun canon(s: String): String =
        s.trim().lowercase().replace(Regex("[,.]"), "")

    /** Human-readable list for the belief management screen. */
    data class Entry(val key: String, val target: String, val count: Int, val active: Boolean)

    fun entries(): List<Entry> {
        val out = mutableListOf<Entry>()
        for ((key, targets) in mined.toSortedMap()) {
            for ((t, c) in targets.entries.sortedByDescending { it.value }) {
                out.add(Entry(key, t, c, c >= MIN_BELIEF_COUNT))
            }
        }
        return out
    }

    fun deleteEntry(key: String, target: String) {
        val m = mined[key] ?: return
        m.remove(target)
        if (m.isEmpty()) mined.remove(key)
        persist()
    }

    fun resetAll() {
        mined.clear()
        persist()
    }

    fun formatMined(): String {
        if (mined.isEmpty()) return "(no mined beliefs)"
        val sb = StringBuilder()
        for ((key, targets) in mined.toSortedMap()) {
            sb.append("  $key\n")
            for ((t, c) in targets.entries.sortedByDescending { it.value }) {
                val status = if (c >= MIN_BELIEF_COUNT) "ACTIVE" else "obs($c/$MIN_BELIEF_COUNT)"
                sb.append("    -> '${t.trim()}'  [$status]\n")
            }
        }
        return sb.toString().trimEnd()
    }

    private fun persist() {
        try {
            val root = JSONObject()
            for ((key, targetMap) in mined) {
                val targetsObj = JSONObject()
                for ((t, c) in targetMap) targetsObj.put(t, c)
                root.put(key, targetsObj)
            }
            minedFile().writeText(root.toString(2))
        } catch (_: Exception) {}
    }
}