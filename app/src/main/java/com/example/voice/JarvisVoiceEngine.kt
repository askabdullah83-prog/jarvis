package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.data.local.JarvisSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

data class FemaleVoiceOption(
    val name: String,
    val label: String,
    val localeTag: String
)

class JarvisVoiceEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var activeUtteranceId: String? = null
    private var pendingOnDone: (() -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var amplitudeJob: Job? = null
    private var recordingCallback: ((ByteArray) -> Unit)? = null
    private var recordingErrorCallback: ((String) -> Unit)? = null

    private var currentAudioTrack: AudioTrack? = null
    private var audioPlayJob: Job? = null

    private var currentSettings = JarvisSettings()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<FemaleVoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<FemaleVoiceOption>> = _availableVoices.asStateFlow()

    private val _hasInstalledBengaliTts = MutableStateFlow(false)
    val hasInstalledBengaliTts: StateFlow<Boolean> = _hasInstalledBengaliTts.asStateFlow()

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == activeUtteranceId) {
                            _isSpeaking.value = false
                            val callback = pendingOnDone
                            pendingOnDone = null
                            activeUtteranceId = null
                            if (callback != null) {
                                mainHandler.post { callback() }
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        handleTtsError(utteranceId)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        handleTtsError(utteranceId)
                    }
                })
                refreshAvailableVoices()
            } else {
                Log.e(TAG, "TextToSpeech init failed with status=$status")
            }
        }
    }

    private fun handleTtsError(utteranceId: String?) {
        if (utteranceId == activeUtteranceId) {
            _isSpeaking.value = false
            val callback = pendingOnDone
            pendingOnDone = null
            activeUtteranceId = null
            if (callback != null) {
                mainHandler.post { callback() }
            }
        }
    }

    fun updateSettings(settings: JarvisSettings) {
        currentSettings = settings
    }

    private fun refreshAvailableVoices() {
        val engine = tts ?: return
        val voices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        val installedVoices = voices.filter { isVoiceInstalled(it) }

        val bnAvailable = installedVoices.any {
            it.locale.language.equals("bn", ignoreCase = true)
        } || engine.isLanguageAvailable(Locale("bn", "BD")) >= TextToSpeech.LANG_AVAILABLE ||
            engine.isLanguageAvailable(Locale("bn", "IN")) >= TextToSpeech.LANG_AVAILABLE
        _hasInstalledBengaliTts.value = bnAvailable

        val filtered = installedVoices
            .filter { voice ->
                val lang = voice.locale.language.lowercase(Locale.ROOT)
                lang == "bn" || lang == "en"
            }
            .sortedByDescending { scoreFemaleVoice(it) }
            .take(12)
            .map { voice ->
                val tag = voice.locale.toLanguageTag()
                val femaleHint = if (isLikelyFemaleVoice(voice)) "Female" else "Voice"
                FemaleVoiceOption(
                    name = voice.name,
                    label = "${voice.locale.displayLanguage} ($tag) • $femaleHint",
                    localeTag = tag
                )
            }
        _availableVoices.value = filtered
    }

    fun playPcm24kHz(pcmBytes: ByteArray, onDone: (() -> Unit)? = null) {
        stopSpeaking()
        audioPlayJob = engineScope.launch {
            _isSpeaking.value = true
            try {
                withContext(Dispatchers.IO) {
                    val sampleRate = 24000
                    val minBuffer = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    ).coerceAtLeast(8192)

                    val track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(minBuffer)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                    currentAudioTrack = track
                    track.play()

                    var offset = 0
                    val chunkSize = 4096
                    while (offset < pcmBytes.size && isActive) {
                        val toWrite = minOf(chunkSize, pcmBytes.size - offset)
                        track.write(pcmBytes, offset, toWrite)
                        offset += toWrite
                    }

                    // Let remaining buffered samples finish cleanly
                    delay(250L)
                    runCatching {
                        track.stop()
                        track.release()
                    }
                    if (currentAudioTrack === track) {
                        currentAudioTrack = null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "PCM playback error", e)
            } finally {
                _isSpeaking.value = false
                onDone?.invoke()
            }
        }
    }

    fun speak(
        text: String,
        englishFallback: String = "",
        onDone: (() -> Unit)? = null
    ) {
        val cleanText = sanitizeForSpeech(text)
        if (cleanText.isBlank()) {
            onDone?.invoke()
            return
        }
        if (!ttsReady) {
            onDone?.invoke()
            return
        }

        val engine = tts ?: run {
            onDone?.invoke()
            return
        }

        val isBengaliText = cleanText.any { it in '\u0980'..'\u09FF' }
        val targetLocale = when {
            currentSettings.preferredLanguage == "bn" -> Locale("bn", "BD")
            currentSettings.preferredLanguage == "en" -> Locale.US
            isBengaliText -> Locale("bn", "BD")
            else -> Locale.US
        }

        var textToSpeak = cleanText
        var chosenVoice = pickBestFemaleVoice(engine, targetLocale)

        if (isBengaliText && chosenVoice == null) {
            val bnBdOk = engine.isLanguageAvailable(Locale("bn", "BD")) >= TextToSpeech.LANG_AVAILABLE
            val bnInOk = engine.isLanguageAvailable(Locale("bn", "IN")) >= TextToSpeech.LANG_AVAILABLE
            if (!bnBdOk && !bnInOk) {
                // Device lacks Bengali TTS voice pack -> use English fallback instead of silence!
                val fallbackClean = sanitizeForSpeech(englishFallback)
                if (fallbackClean.isNotBlank()) {
                    textToSpeak = fallbackClean
                    chosenVoice = pickBestFemaleVoice(engine, Locale.US)
                }
            }
        }

        if (chosenVoice != null) {
            engine.voice = chosenVoice
        } else {
            val langResult = engine.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (isBengaliText) {
                    val bnIn = Locale("bn", "IN")
                    val fallbackBn = engine.setLanguage(bnIn)
                    if (fallbackBn == TextToSpeech.LANG_MISSING_DATA || fallbackBn == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.US)
                    }
                } else {
                    engine.setLanguage(Locale.US)
                }
            }
        }

        val basePitch = currentSettings.voicePitch
        val appliedPitch = if (chosenVoice != null && isLikelyFemaleVoice(chosenVoice)) {
            basePitch.coerceIn(0.95f, 1.30f)
        } else {
            (basePitch + 0.08f).coerceIn(1.05f, 1.40f)
        }
        engine.setPitch(appliedPitch)
        engine.setSpeechRate(currentSettings.voiceSpeed)

        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceId = utteranceId
        pendingOnDone = onDone

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }
        val res = engine.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (res == TextToSpeech.ERROR) {
            handleTtsError(utteranceId)
        }
    }

    fun stopSpeaking() {
        audioPlayJob?.cancel()
        audioPlayJob = null
        runCatching {
            currentAudioTrack?.stop()
            currentAudioTrack?.release()
        }
        currentAudioTrack = null

        pendingOnDone = null
        activeUtteranceId = null
        tts?.stop()
        _isSpeaking.value = false
    }

    /**
     * Direct AI Audio Recording with live RMS amplitude and automatic end-of-speech silence detection.
     * Works on every Android device & emulator regardless of Google Speech Services availability.
     */
    fun startDirectAudioCapture(
        onAudioCaptured: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ) {
        mainHandler.post {
            stopSpeaking()
            stopListening()

            val outFile = File(context.cacheDir, "jarvis_voice_capture.m4a")
            runCatching { if (outFile.exists()) outFile.delete() }
            recordingFile = outFile
            recordingCallback = onAudioCaptured
            recordingErrorCallback = onError

            try {
                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                recorder.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(16000)
                    setAudioEncodingBitRate(32000)
                    setOutputFile(outFile.absolutePath)
                    prepare()
                    start()
                }
                mediaRecorder = recorder
                _isListening.value = true
                _partialTranscript.value = "শুনছি... কথা শেষে ২য় বার গোল বোতামে ট্যাপ করুন"
                startAmplitudeAndVadMonitor()
            } catch (e: Exception) {
                Log.e(TAG, "Direct audio capture failed to start", e)
                cleanupRecorder()
                onError("মাইক্রোফোন চালু করা যায়নি: ${e.localizedMessage ?: "Mic error"}")
            }
        }
    }

    fun finishDirectAudioCapture() {
        mainHandler.post {
            if (mediaRecorder == null) return@post
            amplitudeJob?.cancel()
            amplitudeJob = null

            val file = recordingFile
            val onCaptured = recordingCallback
            val onErr = recordingErrorCallback
            recordingCallback = null
            recordingErrorCallback = null

            val stoppedOk = runCatching {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            }.isSuccess
            mediaRecorder = null
            _isListening.value = false
            _rmsDb.value = 0f

            if (!stoppedOk || file == null || !file.exists() || file.length() < 800L) {
                onErr?.invoke("CANCELLED_EMPTY")
                return@post
            }

            val bytes = runCatching { file.readBytes() }.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                onErr?.invoke("CANCELLED_EMPTY")
            } else {
                onCaptured?.invoke(bytes)
            }
        }
    }

    private fun startAmplitudeAndVadMonitor() {
        amplitudeJob?.cancel()
        amplitudeJob = engineScope.launch {
            val startMs = System.currentTimeMillis()
            var speechDetected = false

            while (isActive && mediaRecorder != null) {
                delay(90L)
                val amp = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                val normalized = (amp / 11000f).coerceIn(0f, 1f)
                _rmsDb.value = normalized

                val now = System.currentTimeMillis()
                if (amp > 850) {
                    speechDetected = true
                }

                // Only auto-cancel if complete silence for 10 seconds with no speech
                if (!speechDetected && (now - startMs) > 10_000L) {
                    cleanupRecorder()
                    val onErr = recordingErrorCallback
                    recordingErrorCallback = null
                    recordingCallback = null
                    onErr?.invoke("CANCELLED_EMPTY")
                    break
                }

                // Safety cap of 25 seconds if user forgets to tap 2nd time
                if ((now - startMs) > 25_000L) {
                    finishDirectAudioCapture()
                    break
                }
            }
        }
    }

    private fun cleanupRecorder() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        runCatching {
            mediaRecorder?.stop()
        }
        runCatching {
            mediaRecorder?.release()
        }
        mediaRecorder = null
        _isListening.value = false
        _rmsDb.value = 0f
    }

    fun startListening(
        languageTag: String = "bn-BD",
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        allowLanguageFallback: Boolean = true
    ) {
        mainHandler.post {
            stopSpeaking()
            cleanupRecorder()

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("SYSTEM_STT_UNAVAILABLE")
                return@post
            }

            destroyRecognizerOnly()
            _partialTranscript.value = ""
            _rmsDb.value = 0f

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    _rmsDb.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _isListening.value = false
                    _rmsDb.value = 0f
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    _rmsDb.value = 0f
                    if (allowLanguageFallback &&
                        (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
                    ) {
                        startListening("en-US", onResult, onError, allowLanguageFallback = false)
                        return
                    }
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "SYSTEM_STT_UNAVAILABLE"
                        SpeechRecognizer.ERROR_CLIENT -> "SYSTEM_STT_UNAVAILABLE"
                        SpeechRecognizer.ERROR_NO_MATCH -> "কিছু শুনতে পাইনি, আবার বলুন।"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ভয়েস টাইমআউট, আবার ট্যাপ করে বলুন।"
                        else -> "SYSTEM_STT_UNAVAILABLE"
                    }
                    onError(msg)
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    _rmsDb.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()?.trim().orEmpty()
                    if (best.isNotEmpty()) {
                        _partialTranscript.value = best
                        onResult(best)
                    } else {
                        onError("কিছু শুনতে পাইনি, আবার বলুন।")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _partialTranscript.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            }
            _isListening.value = true
            recognizer.startListening(intent)
        }
    }

    fun stopListening() {
        mainHandler.post {
            if (mediaRecorder != null) {
                finishDirectAudioCapture()
                return@post
            }
            runCatching { speechRecognizer?.stopListening() }
            _isListening.value = false
            _rmsDb.value = 0f
        }
    }

    private fun destroyRecognizerOnly() {
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
        _isListening.value = false
        _rmsDb.value = 0f
    }

    fun release() {
        stopSpeaking()
        cleanupRecorder()
        destroyRecognizerOnly()
        engineScope.cancel()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }

    private fun pickBestFemaleVoice(engine: TextToSpeech, targetLocale: Locale): Voice? {
        val voices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        val installed = voices.filter { isVoiceInstalled(it) }
        if (installed.isEmpty()) return null

        if (currentSettings.selectedVoiceName.isNotBlank()) {
            val explicit = installed.firstOrNull {
                it.name == currentSettings.selectedVoiceName &&
                    it.locale.language.equals(targetLocale.language, ignoreCase = true)
            }
            if (explicit != null) return explicit
        }

        val matchingLanguage = installed.filter {
            it.locale.language.equals(targetLocale.language, ignoreCase = true)
        }
        return matchingLanguage.maxByOrNull { scoreFemaleVoice(it) }
    }

    private fun isVoiceInstalled(voice: Voice): Boolean {
        val features = voice.features ?: return true
        return !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) &&
            features.none { it.contains("notInstalled", ignoreCase = true) }
    }

    private fun scoreFemaleVoice(voice: Voice): Int {
        var score = 0
        if (!voice.isNetworkConnectionRequired) score += 500
        if (isLikelyFemaleVoice(voice)) score += 100
        score += voice.quality / 100
        if (voice.locale.country.equals("BD", ignoreCase = true)) score += 15
        if (voice.locale.country.equals("US", ignoreCase = true)) score += 12
        return score
    }

    private fun isLikelyFemaleVoice(voice: Voice): Boolean {
        val name = voice.name.lowercase(Locale.ROOT)
        val femaleTokens = listOf(
            "female", "woman", "girl", "-sfg", "-tpf", "-iob", "-iom",
            "-ban", "-bnf", "-x-f", "#female", "f00", "f01", "f02", "samantha"
        )
        return femaleTokens.any { name.contains(it) }
    }

    private fun sanitizeForSpeech(text: String): String {
        return text
            .replace(Regex("```[\\s\\S]*?```"), " কোড ব্লক স্ক্রিনে দেখানো হয়েছে। ")
            .replace("*", "")
            .replace("#", "")
            .replace("`", "")
            .trim()
    }

    companion object {
        private const val TAG = "JarvisVoiceEngine"
    }
}
