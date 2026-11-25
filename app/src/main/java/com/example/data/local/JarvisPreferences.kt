package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_prefs")

data class JarvisSettings(
    val backgroundWakeEnabled: Boolean = true,
    val autoSpeakResponses: Boolean = true,
    val useDirectAiAudio: Boolean = true,
    val useNeuralFemaleVoice: Boolean = false,
    val voicePitch: Float = 1.14f,
    val voiceSpeed: Float = 1.05f,
    val preferredLanguage: String = "auto", // "auto", "bn", "en"
    val selectedVoiceName: String = ""
)

class JarvisPreferences(private val context: Context) {
    private object Keys {
        val BACKGROUND_WAKE = booleanPreferencesKey("background_wake_enabled")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak_responses")
        val USE_DIRECT_AI_AUDIO = booleanPreferencesKey("use_direct_ai_audio")
        val USE_NEURAL_FEMALE_VOICE = booleanPreferencesKey("use_neural_female_voice")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val SELECTED_VOICE_NAME = stringPreferencesKey("selected_voice_name")
    }

    val settingsFlow: Flow<JarvisSettings> = context.jarvisDataStore.data.map { prefs ->
        JarvisSettings(
            backgroundWakeEnabled = prefs[Keys.BACKGROUND_WAKE] ?: true,
            autoSpeakResponses = prefs[Keys.AUTO_SPEAK] ?: true,
            useDirectAiAudio = prefs[Keys.USE_DIRECT_AI_AUDIO] ?: true,
            useNeuralFemaleVoice = prefs[Keys.USE_NEURAL_FEMALE_VOICE] ?: false,
            voicePitch = prefs[Keys.VOICE_PITCH] ?: 1.14f,
            voiceSpeed = prefs[Keys.VOICE_SPEED] ?: 1.05f,
            preferredLanguage = prefs[Keys.PREFERRED_LANGUAGE] ?: "auto",
            selectedVoiceName = prefs[Keys.SELECTED_VOICE_NAME] ?: ""
        )
    }

    suspend fun setBackgroundWakeEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.BACKGROUND_WAKE] = enabled }
    }

    suspend fun setAutoSpeakResponses(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.AUTO_SPEAK] = enabled }
    }

    suspend fun setUseDirectAiAudio(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.USE_DIRECT_AI_AUDIO] = enabled }
    }

    suspend fun setUseNeuralFemaleVoice(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.USE_NEURAL_FEMALE_VOICE] = enabled }
    }

    suspend fun setVoicePitch(pitch: Float) {
        context.jarvisDataStore.edit { it[Keys.VOICE_PITCH] = pitch.coerceIn(0.8f, 1.45f) }
    }

    suspend fun setVoiceSpeed(speed: Float) {
        context.jarvisDataStore.edit { it[Keys.VOICE_SPEED] = speed.coerceIn(0.65f, 1.45f) }
    }

    suspend fun setPreferredLanguage(lang: String) {
        context.jarvisDataStore.edit { it[Keys.PREFERRED_LANGUAGE] = lang }
    }

    suspend fun setSelectedVoiceName(name: String) {
        context.jarvisDataStore.edit { it[Keys.SELECTED_VOICE_NAME] = name }
    }
}
