package com.treg.llmpersonalization.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class UserStore private constructor(
    private val file: File,
    private val users: MutableList<User>
) {
    fun all(): List<User> = users.toList()

    fun get(id: String): User? = users.find { it.id == id }

    fun add(name: String): User {
        val u = User(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            createdAt = System.currentTimeMillis()
        )
        users.add(u)
        persist()
        return u
    }

    fun remove(id: String) {
        users.removeAll { it.id == id }
        persist()
    }

    fun isEmpty(): Boolean = users.isEmpty()

    private fun persist() {
        try {
            val arr = JSONArray()
            for (u in users) {
                arr.put(JSONObject().apply {
                    put("id", u.id)
                    put("name", u.name)
                    put("createdAt", u.createdAt)
                })
            }
            file.writeText(arr.toString(2))
        } catch (_: Exception) {}
    }

    companion object {
        private const val FILE_NAME = "users.json"

        fun load(context: Context): UserStore {
            val file = File(context.filesDir, FILE_NAME)
            val users = mutableListOf<User>()
            if (file.exists()) {
                try {
                    val arr = JSONArray(file.readText())
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        users.add(User(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            createdAt = o.getLong("createdAt")
                        ))
                    }
                } catch (_: Exception) {}
            }
            return UserStore(file, users)
        }
    }
}