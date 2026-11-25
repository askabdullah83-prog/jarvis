package com.example.ai

import android.util.Log
import com.example.data.local.ChatMessageEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.ceil

enum class JarvisIntentType {
    CHAT,
    CODE,
    OPEN_APP,
    CLOSE_APP,
    TELL_TIME,
    CALL,
    SEND_MESSAGE,
    SEND_EMAIL,
    FLASHLIGHT_ON,
    FLASHLIGHT_OFF,
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_MUTE,
    SET_ALARM,
    SET_TIMER,
    WEB_SEARCH,
    GO_HOME,
    GO_BACK,
    OPEN_RECENTS,
    OPEN_NOTIFICATIONS,
    OPEN_SETTINGS,
    TAKE_SCREENSHOT
}

data class JarvisPlan(
    val intentType: JarvisIntentType,
    val transcribedText: String = "",
    val target: String = "",
    val subject: String = "",
    val messageBody: String = "",
    val viaWhatsApp: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
    val durationSeconds: Int = 60,
    val spokenReply: String,
    val englishFallbackReply: String = "",
    val codeContent: String = "",
    val codeLanguage: String = "",
    val suggestions: List<String> = emptyList()
)

class JarvisQuotaException(
    val retryAfterSeconds: Int,
    message: String
) : Exception(message)

class JarvisBrain {

    // Place intentType and spokenReply first so streaming text renders on the very first token chunk
    private val planSchema = Schema.obj(
        properties = mapOf(
            "intentType" to Schema.enumeration(
                values = JarvisIntentType.entries.map { it.name },
                description = "Exact action or CHAT/CODE."
            ),
            "spokenReply" to Schema.string(
                description = "Crisp, helpful female assistant reply in user's language (Bengali/English)."
            ),
            "transcribedText" to Schema.string(description = "User audio transcript if audio."),
            "target" to Schema.string(description = "App, contact, email, panel, or query."),
            "subject" to Schema.string(description = "Email subject."),
            "messageBody" to Schema.string(description = "SMS/WhatsApp/Email body."),
            "viaWhatsApp" to Schema.boolean(description = "True if WhatsApp."),
            "hour" to Schema.integer(description = "Alarm hour 0-23."),
            "minute" to Schema.integer(description = "Alarm minute 0-59."),
            "durationSeconds" to Schema.integer(description = "Timer seconds."),
            "englishFallbackReply" to Schema.string(description = "Brief English summary."),
            "codeContent" to Schema.string(description = "Clean code without fences if CODE."),
            "codeLanguage" to Schema.string(description = "Language if CODE."),
            "suggestions" to Schema.array(
                items = Schema.string(),
                description = "2 short follow-up prompts."
            )
        ),
        optionalProperties = listOf(
            "transcribedText",
            "target",
            "subject",
            "messageBody",
            "viaWhatsApp",
            "hour",
            "minute",
            "durationSeconds",
            "englishFallbackReply",
            "codeContent",
            "codeLanguage",
            "suggestions"
        )
    )

    private val systemPromptContent = content {
        text(
            """
            You are Jarvis (জার্ভিস), an ultra-fast, warm female AI assistant for Android.
            - Respond immediately in user's language (Bengali/Banglish -> Bengali; English -> English). Fill englishFallbackReply briefly.
            - Keep spokenReply concise and natural (1-3 sentences) with 2 short `suggestions`.
            - For audio: put user's speech in transcribedText and reply/act immediately.
            - Route actions: OPEN_APP, CLOSE_APP, TELL_TIME, CALL, SEND_MESSAGE, SEND_EMAIL, CODE (clean code in codeContent), FLASHLIGHT_ON/OFF, VOLUME_UP/DOWN/MUTE, SET_ALARM, SET_TIMER, WEB_SEARCH, OPEN_SETTINGS, GO_HOME, GO_BACK, OPEN_RECENTS, OPEN_NOTIFICATIONS, TAKE_SCREENSHOT.
            """.trimIndent()
        )
    }

    private val fastGenConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = planSchema
        temperature = 0.35f
        maxOutputTokens = 750
    }

    private val codingGenConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = planSchema
        temperature = 0.4f
        maxOutputTokens = 1800
    }

    private val fastModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = fastGenConfig,
            systemInstruction = systemPromptContent
        )
    }

    private val codingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = codingGenConfig,
            systemInstruction = systemPromptContent
        )
    }

    @OptIn(PublicPreviewAPI::class)
    private val ttsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.1-flash-tts-preview",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(
                    voice = Voice("Aoede")
                )
            }
        )
    }

    suspend fun processInput(
        userInput: String,
        recentHistory: List<ChatMessageEntity>,
        currentTimeSummary: String,
        onPartialReply: ((String) -> Unit)? = null
    ): JarvisPlan {
        val historyContext = buildContextHeader(recentHistory, currentTimeSummary) +
            "\nU: $userInput"

        val preferCodingModel = isCodingQuery(userInput)
        return executeStreamingPlanWithFallback(preferCodingModel, onPartialReply) { model ->
            model.generateContentStream(historyContext)
        }
    }

    suspend fun processAudioInput(
        audioBytes: ByteArray,
        mimeType: String,
        recentHistory: List<ChatMessageEntity>,
        currentTimeSummary: String,
        onPartialReply: ((String) -> Unit)? = null
    ): JarvisPlan {
        val contextPrompt = buildContextHeader(recentHistory, currentTimeSummary) +
            "\nTranscribe audio to transcribedText and reply/act fast."

        val promptContent = content {
            inlineData(audioBytes, mimeType)
            text(contextPrompt)
        }

        return executeStreamingPlanWithFallback(preferCodingModel = false, onPartialReply) { model ->
            model.generateContentStream(promptContent)
        }
    }

    suspend fun synthesizeFemaleSpeechPcm(text: String): ByteArray? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val response = withTimeout(12_000L) {
                ttsModel.generateContent("Read aloud briskly in a warm female voice: $trimmed")
            }
            val part = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()
            if (part is InlineDataPart) {
                part.inlineData
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini neural TTS unavailable, falling back to device TTS", e)
            null
        }
    }

    private suspend fun executeStreamingPlanWithFallback(
        preferCodingModel: Boolean,
        onPartialReply: ((String) -> Unit)?,
        streamFactory: (GenerativeModel) -> kotlinx.coroutines.flow.Flow<com.google.firebase.ai.type.GenerateContentResponse>
    ): JarvisPlan {
        val primary = if (preferCodingModel) codingModel else fastModel
        val secondary = if (preferCodingModel) fastModel else codingModel

        return try {
            streamAndParse(primary, onPartialReply, streamFactory)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isQuotaOrRateLimitError(e)) {
                Log.w(TAG, "Primary model rate-limited; falling back to secondary model", e)
                try {
                    streamAndParse(secondary, onPartialReply, streamFactory)
                } catch (fallbackErr: CancellationException) {
                    throw fallbackErr
                } catch (fallbackErr: Exception) {
                    if (isQuotaOrRateLimitError(fallbackErr)) {
                        val waitSec = extractRetrySeconds(fallbackErr.message)
                            ?: extractRetrySeconds(e.message)
                            ?: 60
                        throw JarvisQuotaException(
                            retryAfterSeconds = waitSec,
                            message = "ফ্রি AI লিমিট সাময়িকভাবে পূর্ণ হয়েছে। অনুগ্রহ করে প্রায় $waitSec সেকেন্ড পর আবার চেষ্টা করুন।"
                        )
                    }
                    throw fallbackErr
                }
            } else {
                throw e
            }
        }
    }

    private suspend fun streamAndParse(
        model: GenerativeModel,
        onPartialReply: ((String) -> Unit)?,
        streamFactory: (GenerativeModel) -> kotlinx.coroutines.flow.Flow<com.google.firebase.ai.type.GenerateContentResponse>
    ): JarvisPlan {
        val fullJson = StringBuilder()
        var lastEmitted = ""
        try {
            withTimeout(35_000L) {
                streamFactory(model).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        fullJson.append(piece)
                        if (onPartialReply != null) {
                            val partial = extractPartialSpokenReply(fullJson.toString())
                            if (partial != null && partial != lastEmitted) {
                                lastEmitted = partial
                                onPartialReply(partial)
                            }
                        }
                    }
                }
            }

            val rawJson = fullJson.toString().trim()
            if (rawJson.isEmpty()) {
                throw IllegalStateException("Jarvis AI returned an empty response.")
            }

            val obj = JSONObject(rawJson)
            val intentName = obj.optString("intentType", JarvisIntentType.CHAT.name)
            val intentType = JarvisIntentType.entries.firstOrNull { it.name == intentName }
                ?: JarvisIntentType.CHAT

            val suggestionsArray = obj.optJSONArray("suggestions")
            val suggestionsList = if (suggestionsArray != null) {
                (0 until suggestionsArray.length())
                    .mapNotNull { idx -> suggestionsArray.optString(idx)?.trim() }
                    .filter { it.isNotEmpty() }
                    .take(2)
            } else {
                emptyList()
            }

            return JarvisPlan(
                intentType = intentType,
                transcribedText = obj.optString("transcribedText", ""),
                target = obj.optString("target", ""),
                subject = obj.optString("subject", ""),
                messageBody = obj.optString("messageBody", ""),
                viaWhatsApp = obj.optBoolean("viaWhatsApp", false),
                hour = obj.optInt("hour", 7),
                minute = obj.optInt("minute", 0),
                durationSeconds = obj.optInt("durationSeconds", 60),
                spokenReply = obj.optString("spokenReply", "জী, বলুন।"),
                englishFallbackReply = obj.optString("englishFallbackReply", ""),
                codeContent = obj.optString("codeContent", ""),
                codeLanguage = obj.optString("codeLanguage", ""),
                suggestions = suggestionsList
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini AI request timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI streaming/parsing failed", e)
            throw e
        }
    }

    private fun extractPartialSpokenReply(partialJson: String): String? {
        val marker = "\"spokenReply\""
        val idx = partialJson.indexOf(marker)
        if (idx < 0) return null
        val colonIdx = partialJson.indexOf(':', idx + marker.length)
        if (colonIdx < 0) return null
        val openQuote = partialJson.indexOf('"', colonIdx + 1)
        if (openQuote < 0) return null

        val sb = StringBuilder()
        var i = openQuote + 1
        while (i < partialJson.length) {
            val ch = partialJson[i]
            if (ch == '\\' && i + 1 < partialJson.length) {
                when (val next = partialJson[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    else -> sb.append(next)
                }
                i += 2
            } else if (ch == '"') {
                break
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString().takeIf { it.isNotEmpty() }
    }

    private fun isCodingQuery(text: String): Boolean {
        val lower = text.lowercase()
        val codeKeywords = listOf(
            "code", "কোড", "python", "kotlin", "java", "javascript", "html",
            "css", "c++", "program", "script", "algorithm", "function", "debug", "sql"
        )
        return codeKeywords.any { lower.contains(it) }
    }

    private fun isQuotaOrRateLimitError(e: Throwable): Boolean {
        val msg = (e.message.orEmpty() + " " + e.cause?.message.orEmpty()).lowercase()
        return msg.contains("quota") ||
            msg.contains("429") ||
            msg.contains("rate limit") ||
            msg.contains("rate-limit") ||
            msg.contains("resource_exhausted")
    }

    private fun extractRetrySeconds(msg: String?): Int? {
        if (msg.isNullOrBlank()) return null
        val match = Regex("retry in\\s*([0-9]+(?:\\.[0-9]+)?)s", RegexOption.IGNORE_CASE)
            .find(msg) ?: return null
        return match.groupValues.getOrNull(1)?.toDoubleOrNull()?.let { ceil(it).toInt().coerceAtLeast(5) }
    }

    private fun buildContextHeader(
        recentHistory: List<ChatMessageEntity>,
        currentTimeSummary: String
    ): String {
        return buildString {
            appendLine("Time: $currentTimeSummary")
            if (recentHistory.isNotEmpty()) {
                recentHistory.takeLast(2).forEach { msg ->
                    val speaker = if (msg.role == "user") "U" else "J"
                    appendLine("$speaker: ${msg.text.take(110)}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "JarvisBrain"
    }
}
