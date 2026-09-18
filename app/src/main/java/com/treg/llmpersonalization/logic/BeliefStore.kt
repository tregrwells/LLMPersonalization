package com.treg.llmpersonalization.logic

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Belief tables: preloaded per-user entries, reference tier, and mined
 * beliefs with persistence to filesDir.
 *
 * Tuple keys (uri, subject) are stored as "P36|france" strings — matches
 * the JSON format produced in Phase B Cell 3.
 */
class BeliefStore private constructor(
    private val preloaded: MutableMap<String, MutableMap<String, String>>,
    private val reference: Map<String, String>,
    private val mined: MutableMap<String, MutableMap<String, MutableMap<String, Int>>>,
    private val minedFile: File
) {
    companion object {
        const val MIN_BELIEF_COUNT = 2

        fun load(context: Context, beliefJson: String): BeliefStore {
            val root = JSONObject(beliefJson)
            val tablesObj = root.getJSONObject("belief_tables")
            val refObj = root.getJSONObject("reference_tier")

            val preloaded = mutableMapOf<String, MutableMap<String, String>>()
            for (user in tablesObj.keys()) {
                val inner = tablesObj.getJSONObject(user)
                val map = mutableMapOf<String, String>()
                for (key in inner.keys()) {
                    map[key] = inner.getString(key)
                }
                preloaded[user] = map
            }

            val reference = mutableMapOf<String, String>()
            for (key in refObj.keys()) {
                reference[key] = refObj.getString(key)
            }

            // Load mined beliefs from filesDir if present
            val minedFile = File(context.filesDir, "beliefs_mined.json")
            val mined = mutableMapOf<String, MutableMap<String, MutableMap<String, Int>>>()
            if (minedFile.exists()) {
                try {
                    val minedRoot = JSONObject(minedFile.readText())
                    for (user in minedRoot.keys()) {
                        val userObj = minedRoot.getJSONObject(user)
                        val userMap = mutableMapOf<String, MutableMap<String, Int>>()
                        for (concept in userObj.keys()) {
                            val targetsObj = userObj.getJSONObject(concept)
                            val targetsMap = mutableMapOf<String, Int>()
                            for (t in targetsObj.keys()) {
                                targetsMap[t] = targetsObj.getInt(t)
                            }
                            userMap[concept] = targetsMap
                        }
                        mined[user] = userMap
                    }
                } catch (_: Exception) { /* corrupt file — start fresh */ }
            }

            return BeliefStore(preloaded, reference, mined, minedFile)
        }
    }

    fun users(): List<String> = preloaded.keys.toList()

    fun conceptKey(uri: String, subject: String): String = "$uri|$subject"

    data class Mined(val concept: String, val target: String, val count: Int)

    fun acquireBelief(user: String, message: String, uri: String?): Mined? {
        if (MessageClassifier.isQuestion(message)) return null
        if (uri.isNullOrEmpty() || uri == "NONE") return null
        val (subject, target) = MessageClassifier.parseAssertion(message)
        if (subject.isNullOrEmpty() || target.isNullOrEmpty()) return null

        val key = conceptKey(uri, subject)
        val userMap = mined.getOrPut(user) { mutableMapOf() }
        val targetMap = userMap.getOrPut(key) { mutableMapOf() }
        val newCount = (targetMap[target] ?: 0) + 1
        targetMap[target] = newCount

        persist()

        return Mined(key, target, newCount)
    }

    data class Effective(val target: String?, val source: String)

    fun getEffectiveBelief(user: String, key: String): Effective {
        val minedEntry = mined[user]?.get(key)
        if (minedEntry != null && minedEntry.isNotEmpty()) {
            val sorted = minedEntry.entries.sortedByDescending { it.value }
            val topCount = sorted[0].value
            val topTargets = sorted.filter { it.value == topCount }.map { it.key }

            if (topTargets.size > 1) {
                val personal = preloaded[user]?.get(key)
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
        val personal = preloaded[user]?.get(key)
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

    fun formatMined(user: String): String {
        val userMap = mined[user] ?: return "(no mined beliefs for $user)"
        if (userMap.isEmpty()) return "(no mined beliefs for $user)"
        val sb = StringBuilder()
        for ((key, targets) in userMap.toSortedMap()) {
            sb.append("  $key\n")
            for ((t, c) in targets.entries.sortedByDescending { it.value }) {
                val status = if (c >= MIN_BELIEF_COUNT) "ACTIVE" else "obs($c/$MIN_BELIEF_COUNT)"
                sb.append("    -> '${t.trim()}'  [$status]\n")
            }
        }
        return sb.toString().trimEnd()
    }

    fun resetMined() {
        mined.clear()
        persist()
    }

    private fun persist() {
        try {
            val root = JSONObject()
            for ((user, userMap) in mined) {
                val userObj = JSONObject()
                for ((key, targetMap) in userMap) {
                    val targetsObj = JSONObject()
                    for ((t, c) in targetMap) targetsObj.put(t, c)
                    userObj.put(key, targetsObj)
                }
                root.put(user, userObj)
            }
            minedFile.writeText(root.toString(2))
        } catch (_: Exception) { /* best-effort */ }
    }
}