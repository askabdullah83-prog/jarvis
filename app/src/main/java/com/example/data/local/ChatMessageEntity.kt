package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user", "jarvis", "action"
    val text: String,
    val codeSnippet: String? = null,
    val codeLanguage: String? = null,
    val actionLabel: String? = null,
    val actionSuccess: Boolean = true,
    val suggestionsRaw: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun suggestionList(): List<String> {
        val raw = suggestionsRaw.orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split("||").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
