package com.treg.llmpersonalization.data

enum class MessageRole { USER, ASSISTANT }

data class Message(
    val role: MessageRole,
    val content: String,
    val think: String? = null,     // captured <think>...</think> block, null if none
    val timestamp: Long
)