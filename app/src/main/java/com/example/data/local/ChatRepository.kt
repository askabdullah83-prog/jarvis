package com.example.data.local

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {
    val messages: Flow<List<ChatMessageEntity>> = chatDao.getAllMessages()

    suspend fun addUserMessage(text: String) {
        chatDao.insertMessage(
            ChatMessageEntity(
                role = "user",
                text = text
            )
        )
    }

    suspend fun addJarvisMessage(
        text: String,
        codeSnippet: String? = null,
        codeLanguage: String? = null,
        actionLabel: String? = null,
        actionSuccess: Boolean = true,
        suggestions: List<String> = emptyList()
    ) {
        val joinedSuggestions = suggestions
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(3)
            .joinToString("||")
            .takeIf { it.isNotBlank() }

        chatDao.insertMessage(
            ChatMessageEntity(
                role = "jarvis",
                text = text,
                codeSnippet = codeSnippet?.takeIf { it.isNotBlank() },
                codeLanguage = codeLanguage?.takeIf { it.isNotBlank() },
                actionLabel = actionLabel?.takeIf { it.isNotBlank() },
                actionSuccess = actionSuccess,
                suggestionsRaw = joinedSuggestions
            )
        )
    }

    suspend fun getRecentContext(limit: Int = 8): List<ChatMessageEntity> {
        return chatDao.getRecentMessages(limit).reversed()
    }

    suspend fun clearHistory() {
        chatDao.clearAll()
    }
}
