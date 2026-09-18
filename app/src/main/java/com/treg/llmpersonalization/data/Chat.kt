package com.treg.llmpersonalization.data

data class Chat(
    val id: String,
    val userId: String,
    var title: String,
    val createdAt: Long,
    var updatedAt: Long,
    val messages: MutableList<Message> = mutableListOf()
)