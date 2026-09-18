package com.treg.llmpersonalization.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ChatStore private constructor(
    private val context: Context
) {
    private val chatsDir: File = File(context.filesDir, "chats").apply { mkdirs() }

    fun listForUser(userId: String): List<Chat> =
        chatsDir.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { loadFile(it) }
            ?.filter { it.userId == userId }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()

    fun create(userId: String, title: String = "New Chat"): Chat {
        val c = Chat(
            id = UUID.randomUUID().toString(),
            userId = userId,
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        save(c)
        return c
    }

    fun get(id: String): Chat? = loadFile(File(chatsDir, "$id.json"))

    fun save(chat: Chat) {
        chat.updatedAt = System.currentTimeMillis()
        try {
            val msgs = JSONArray()
            for (m in chat.messages) {
                msgs.put(JSONObject().apply {
                    put("role", m.role.name)
                    put("content", m.content)
                    if (m.think != null) put("think", m.think)
                    put("timestamp", m.timestamp)
                })
            }
            val o = JSONObject().apply {
                put("id", chat.id)
                put("userId", chat.userId)
                put("title", chat.title)
                put("createdAt", chat.createdAt)
                put("updatedAt", chat.updatedAt)
                put("messages", msgs)
            }
            File(chatsDir, "${chat.id}.json").writeText(o.toString(2))
        } catch (_: Exception) {}
    }

    fun rename(chat: Chat, newTitle: String) {
        chat.title = newTitle
        save(chat)
    }

    fun delete(chatId: String) {
        File(chatsDir, "$chatId.json").delete()
    }

    private fun loadFile(file: File): Chat? {
        if (!file.exists()) return null
        return try {
            val o = JSONObject(file.readText())
            val msgs = mutableListOf<Message>()
            val arr = o.optJSONArray("messages") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                msgs.add(Message(
                    role = MessageRole.valueOf(m.getString("role")),
                    content = m.getString("content"),
                    think = if (m.has("think")) m.getString("think") else null,
                    timestamp = m.getLong("timestamp")
                ))
            }
            Chat(
                id = o.getString("id"),
                userId = o.getString("userId"),
                title = o.getString("title"),
                createdAt = o.getLong("createdAt"),
                updatedAt = o.getLong("updatedAt"),
                messages = msgs
            )
        } catch (_: Exception) { null }
    }

    companion object {
        fun load(context: Context): ChatStore = ChatStore(context)
    }
}