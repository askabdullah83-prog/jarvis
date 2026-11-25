package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.JarvisBrain
import com.example.ai.JarvisIntentType
import com.example.ai.JarvisPlan
import com.example.ai.JarvisQuotaException
import com.example.ai.LocalCommandMatcher
import com.example.ai.LocalRouteResult
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatRepository
import com.example.data.local.JarvisPreferences
import com.example.data.local.JarvisSettings
import com.example.device.ActionExecutionResult
import com.example.device.DeviceActionExecutor
import com.example.device.InstalledAppInfo
import com.example.service.JarvisAccessibilityService
import com.example.service.JarvisBackgroundService
import com.example.service.JarvisWakeEvent
import com.example.voice.FemaleVoiceOption
import com.example.voice.JarvisVoiceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface JarvisUiState {
    data object Idle : JarvisUiState
    data object Listening : JarvisUiState
    data object Thinking : JarvisUiState
    data class Streaming(val partialReply: String) : JarvisUiState
    data class Success(
        val reply: String,
        val codeContent: String = "",
        val codeLanguage: String = "",
        val actionLabel: String = "",
        val actionSuccess: Boolean = true,
        val suggestions: List<String> = emptyList()
    ) : JarvisUiState
    data class Error(val message: String) : JarvisUiState
}

data class PermissionSnapshot(
    val hasMicrophone: Boolean = false,
    val hasContacts: Boolean = false,
    val hasCallPhone: Boolean = false,
    val hasNotifications: Boolean = true,
    val hasAccessibility: Boolean = false
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val chatRepository = ChatRepository(AppDatabase.getInstance(appContext).chatDao())
    private val preferences = JarvisPreferences(appContext)
    private val voiceEngine = JarvisVoiceEngine(appContext)
    private val brain = JarvisBrain()
    private val actionExecutor = DeviceActionExecutor(appContext)

    val messages: StateFlow<List<ChatMessageEntity>> = chatRepository.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<JarvisSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JarvisSettings())

    val isSpeaking: StateFlow<Boolean> = voiceEngine.isSpeaking
    val isListening: StateFlow<Boolean> = voiceEngine.isListening
    val partialTranscript: StateFlow<String> = voiceEngine.partialTranscript
    val rmsDb: StateFlow<Float> = voiceEngine.rmsDb
    val availableVoices: StateFlow<List<FemaleVoiceOption>> = voiceEngine.availableVoices
    val hasInstalledBengaliTts: StateFlow<Boolean> = voiceEngine.hasInstalledBengaliTts
    val isBackgroundRunning: StateFlow<Boolean> = JarvisBackgroundService.isRunning
    val wakeAvailable: StateFlow<Boolean> = JarvisBackgroundService.wakeAvailable

    // Jarvis always starts OFF / Standby until woken by "হ্যালো জার্ভিস" or manual Orb tap
    private val _isJarvisAwake = MutableStateFlow(false)
    val isJarvisAwake: StateFlow<Boolean> = _isJarvisAwake.asStateFlow()

    private val _uiState = MutableStateFlow<JarvisUiState>(JarvisUiState.Idle)
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _permissions = MutableStateFlow(PermissionSnapshot())
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    private val _torchOn = MutableStateFlow(false)
    val torchOn: StateFlow<Boolean> = _torchOn.asStateFlow()

    private val _lastSpokenInput = MutableStateFlow("")
    val lastSpokenInput: StateFlow<String> = _lastSpokenInput.asStateFlow()

    init {
        viewModelScope.launch {
            settings.collect { latest ->
                voiceEngine.updateSettings(latest)
                if (latest.backgroundWakeEnabled &&
                    _permissions.value.hasMicrophone &&
                    !_isJarvisAwake.value &&
                    !voiceEngine.isListening.value &&
                    !JarvisBackgroundService.isRunning.value
                ) {
                    JarvisBackgroundService.start(appContext)
                }
            }
        }
        viewModelScope.launch {
            JarvisBackgroundService.wakeEvents.collect { event ->
                when (event) {
                    is JarvisWakeEvent.Activated -> {
                        if (event.putToSleep) {
                            _isJarvisAwake.value = false
                            _uiState.value = JarvisUiState.Idle
                        } else {
                            _isJarvisAwake.value = true
                            _uiState.value = JarvisUiState.Success(
                                reply = event.replyOrStatus,
                                actionLabel = "হ্যালো জার্ভিস Active",
                                actionSuccess = true
                            )
                        }
                    }
                }
            }
        }
        refreshPermissions()
        refreshInstalledApps()
    }

    fun onWakeTriggerIntent() {
        _isJarvisAwake.value = true
    }

    fun toggleStandbyState() {
        if (_isJarvisAwake.value) {
            putJarvisToSleep()
        } else {
            wakeJarvisAndGreet()
        }
    }

    fun wakeJarvisAndGreet() {
        _isJarvisAwake.value = true
        val preferBn = settings.value.preferredLanguage != "en"
        val msg = if (preferBn) {
            "হ্যালো! আমি জার্ভিস, এখন চালু আছি। কথা বলতে গোল বোতামে একবার ট্যাপ করুন।"
        } else {
            "Hello! Jarvis is active. Tap the round button once to speak."
        }
        _uiState.value = JarvisUiState.Success(
            reply = msg,
            actionLabel = "Jarvis Active",
            actionSuccess = true
        )
        if (settings.value.autoSpeakResponses) {
            speakWithFemaleVoice(msg, "Hello! Jarvis is active.")
        }
    }

    fun putJarvisToSleep() {
        voiceEngine.stopSpeaking()
        if (isListening.value) {
            voiceEngine.stopListening()
        }
        _isJarvisAwake.value = false
        _uiState.value = JarvisUiState.Idle
        _lastSpokenInput.value = ""
        if (_permissions.value.hasMicrophone && settings.value.backgroundWakeEnabled) {
            JarvisBackgroundService.start(appContext)
        }
    }

    fun refreshPermissions() {
        val hasMic = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasContacts = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        val hasCall = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val hasAccess = JarvisAccessibilityService.isAccessibilitySettingEnabled(appContext)
        _permissions.value = PermissionSnapshot(
            hasMicrophone = hasMic,
            hasContacts = hasContacts,
            hasCallPhone = hasCall,
            hasNotifications = hasNotif,
            hasAccessibility = hasAccess
        )
        if (hasMic && settings.value.backgroundWakeEnabled && !voiceEngine.isListening.value) {
            JarvisBackgroundService.start(appContext)
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                actionExecutor.getInstalledLauncherApps()
            }
            _installedApps.value = apps
        }
    }

    /**
     * Push-to-toggle voice behavior:
     * - Tap 1 (when idle/standby): wakes Jarvis and starts recording/listening.
     * - Tap 2 (while listening): immediately stops listening and sends/processes!
     * - Tap while speaking: stops Jarvis from speaking immediately.
     */
    fun onOrbClicked() {
        if (isSpeaking.value) {
            stopSpeaking()
            return
        }
        if (isListening.value) {
            voiceEngine.stopListening()
            return
        }
        startVoiceListening()
    }

    fun startVoiceListening() {
        refreshPermissions()
        if (!_permissions.value.hasMicrophone) {
            _uiState.value = JarvisUiState.Error(
                "মাইক্রোফোন এক্সেস প্রয়োজন। উপরের Allow Mic বাটনে ট্যাপ করুন।"
            )
            return
        }

        if (isListening.value) {
            voiceEngine.stopListening()
            return
        }

        _isJarvisAwake.value = true
        val wasBgEnabled = settings.value.backgroundWakeEnabled
        if (JarvisBackgroundService.isRunning.value) {
            JarvisBackgroundService.stop(appContext)
        }

        _uiState.value = JarvisUiState.Listening

        if (settings.value.useDirectAiAudio) {
            beginDirectAudioCapture(resumeBackgroundAfter = wasBgEnabled)
        } else {
            val langTag = when (settings.value.preferredLanguage) {
                "en" -> "en-US"
                else -> "bn-BD"
            }
            voiceEngine.startListening(
                languageTag = langTag,
                onResult = { spoken ->
                    _lastSpokenInput.value = spoken
                    submitQuery(
                        input = spoken,
                        speakReply = true,
                        resumeBackgroundAfter = wasBgEnabled
                    )
                },
                onError = { errMsg ->
                    if (errMsg == "SYSTEM_STT_UNAVAILABLE") {
                        beginDirectAudioCapture(resumeBackgroundAfter = wasBgEnabled)
                    } else {
                        _uiState.value = JarvisUiState.Error(errMsg)
                        if (wasBgEnabled) {
                            JarvisBackgroundService.start(appContext)
                        }
                    }
                }
            )
        }
    }

    private fun beginDirectAudioCapture(resumeBackgroundAfter: Boolean) {
        _uiState.value = JarvisUiState.Listening
        voiceEngine.startDirectAudioCapture(
            onAudioCaptured = { audioBytes ->
                submitAudioQuery(audioBytes, resumeBackgroundAfter)
            },
            onError = { errMsg ->
                if (errMsg == "CANCELLED_EMPTY") {
                    _uiState.value = JarvisUiState.Idle
                } else {
                    _uiState.value = JarvisUiState.Error(errMsg)
                }
                if (resumeBackgroundAfter && settings.value.backgroundWakeEnabled) {
                    JarvisBackgroundService.start(appContext)
                }
            }
        )
    }

    private fun isBusy(): Boolean =
        _uiState.value is JarvisUiState.Thinking || _uiState.value is JarvisUiState.Streaming

    private fun submitAudioQuery(audioBytes: ByteArray, resumeBackgroundAfter: Boolean) {
        if (isBusy()) return
        viewModelScope.launch {
            _uiState.value = JarvisUiState.Thinking
            try {
                val history = chatRepository.getRecentContext(6)
                val preferBn = settings.value.preferredLanguage != "en"
                val timeNow = actionExecutor.getCurrentTimeText(preferBn)

                val plan = brain.processAudioInput(
                    audioBytes = audioBytes,
                    mimeType = "audio/mp4",
                    recentHistory = history,
                    currentTimeSummary = timeNow,
                    onPartialReply = { partial ->
                        _uiState.value = JarvisUiState.Streaming(partial)
                    }
                )

                val spokenTranscript = plan.transcribedText.trim()
                if (spokenTranscript.isNotBlank()) {
                    _lastSpokenInput.value = spokenTranscript
                    chatRepository.addUserMessage(spokenTranscript)

                    val localCheck = LocalCommandMatcher.tryMatch(
                        input = spokenTranscript,
                        installedApps = _installedApps.value,
                        actionExecutor = actionExecutor,
                        preferBn = preferBn
                    )
                    if (localCheck is LocalRouteResult.SleepCommand) {
                        putJarvisToSleep()
                        return@launch
                    }
                }

                val inferredBn = when (settings.value.preferredLanguage) {
                    "bn" -> true
                    "en" -> false
                    else -> spokenTranscript.any { it in '\u0980'..'\u09FF' } ||
                        plan.spokenReply.any { it in '\u0980'..'\u09FF' }
                }

                applyPlanAndRespond(
                    plan = plan,
                    rawUserInput = spokenTranscript.ifBlank { "Voice command" },
                    preferBn = inferredBn,
                    speakReply = true,
                    resumeBackgroundAfter = resumeBackgroundAfter
                )
            } catch (e: JarvisQuotaException) {
                _uiState.value = JarvisUiState.Error(e.message ?: "ফ্রি লিমিট পূর্ণ হয়েছে, একটু পর আবার চেষ্টা করুন।")
                if (resumeBackgroundAfter && settings.value.backgroundWakeEnabled) {
                    JarvisBackgroundService.start(appContext)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Jarvis audio request timed out", e)
                _uiState.value = JarvisUiState.Error(
                    "সার্ভার থেকে উত্তর আসতে বেশি সময় লাগছে (Timeout)। আবার চেষ্টা করুন।"
                )
                if (resumeBackgroundAfter && settings.value.backgroundWakeEnabled) {
                    JarvisBackgroundService.start(appContext)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Jarvis audio query failed", e)
                _uiState.value = JarvisUiState.Error(
                    e.localizedMessage ?: "Jarvis AI failed to process voice."
                )
                if (resumeBackgroundAfter && settings.value.backgroundWakeEnabled) {
                    JarvisBackgroundService.start(appContext)
                }
            }
        }
    }

    fun stopSpeaking() {
        voiceEngine.stopSpeaking()
        if (settings.value.backgroundWakeEnabled && _permissions.value.hasMicrophone) {
            JarvisBackgroundService.start(appContext)
        }
    }

    fun submitQuery(
        input: String,
        speakReply: Boolean = settings.value.autoSpeakResponses,
        resumeBackgroundAfter: Boolean = settings.value.backgroundWakeEnabled
    ) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        if (isBusy()) return

        _isJarvisAwake.value = true
        _lastSpokenInput.value = trimmed
        viewModelScope.launch {
            _uiState.value = JarvisUiState.Thinking
            try {
                val preferBn = when (settings.value.preferredLanguage) {
                    "bn" -> true
                    "en" -> false
                    else -> trimmed.any { it in '\u0980'..'\u09FF' } || containsBanglishCue(trimmed)
                }

                when (val localRoute = LocalCommandMatcher.tryMatch(
                    input = trimmed,
                    installedApps = _installedApps.value,
                    actionExecutor = actionExecutor,
                    preferBn = preferBn
                )) {
                    is LocalRouteResult.SleepCommand -> {
                        putJarvisToSleep()
                        return@launch
                    }
                    is LocalRouteResult.WakeCommandOnly -> {
                        wakeJarvisAndGreet()
                        return@launch
                    }
                    is LocalRouteResult.MatchedPlan -> {
                        chatRepository.addUserMessage(trimmed)
                        applyPlanAndRespond(
                            plan = localRoute.plan,
                            rawUserInput = trimmed,
                            preferBn = preferBn,
                            speakReply = speakReply,
                            resumeBackgroundAfter = resumeBackgroundAfter
                        )
                        return@launch
                    }
                    null -> {
                        chatRepository.addUserMessage(trimmed)
                        val history = chatRepository.getRecentContext(6)
                        val timeNow = actionExecutor.getCurrentTimeText(preferBn)
                        val plan = brain.processInput(
                            userInput = trimmed,
                            recentHistory = history,
                            currentTimeSummary = timeNow,
                            onPartialReply = { partial ->
                                _uiState.value = JarvisUiState.Streaming(partial)
                            }
                        )
                        applyPlanAndRespond(
                            plan = plan,
                            rawUserInput = trimmed,
                            preferBn = preferBn,
                            speakReply = speakReply,
                            resumeBackgroundAfter = resumeBackgroundAfter
                        )
                    }
                }
            } catch (e: JarvisQuotaException) {
                _uiState.value = JarvisUiState.Error(e.message ?: "ফ্রি লিমিট পূর্ণ হয়েছে, একটু পর আবার চেষ্টা করুন।")
                if (resumeBackgroundAfter && settings.value.backgroundWakeEnabled) {
                    JarvisBackgroundService.start(appContext)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Jarvis request timed out", e)
                _uiState.value = JarvisUiState.Error(
                    "সার্ভার থেকে উত্তর আসতে বেশি সময় লাগছে (Timeout)। আবার চেষ্টা করুন।"
                )
                if (resumeBackgroundAfter && settings.value.backgroundWakeEnabled) {
                    JarvisBackgroundService.start(appContext)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Jarvis query failed", e)
                _uiState.value = JarvisUiState.Error(
                    e.localizedMessage ?: "Jarvis AI failed to respond."
                )
                if (resumeBackgroundAfter && settings.value.backgroundWakeEnabled) {
                    JarvisBackgroundService.start(appContext)
                }
            }
        }
    }

    private suspend fun applyPlanAndRespond(
        plan: JarvisPlan,
        rawUserInput: String,
        preferBn: Boolean,
        speakReply: Boolean,
        resumeBackgroundAfter: Boolean
    ) {
        val timeNow = actionExecutor.getCurrentTimeText(preferBn)
        val execResult: ActionExecutionResult? = when (plan.intentType) {
            JarvisIntentType.TELL_TIME -> ActionExecutionResult(
                success = true,
                actionLabel = "Device Clock",
                spokenFeedback = timeNow
            )
            JarvisIntentType.OPEN_APP -> actionExecutor.openApp(plan.target, preferBn)
            JarvisIntentType.CLOSE_APP -> actionExecutor.closeAppOrGoHome(plan.target, preferBn)
            JarvisIntentType.CALL -> actionExecutor.makePhoneCall(plan.target, preferBn)
            JarvisIntentType.SEND_MESSAGE -> actionExecutor.sendMessage(
                target = plan.target,
                messageBody = plan.messageBody,
                viaWhatsApp = plan.viaWhatsApp,
                preferBengali = preferBn
            )
            JarvisIntentType.SEND_EMAIL -> actionExecutor.sendEmail(
                target = plan.target,
                subject = plan.subject,
                body = plan.messageBody,
                preferBengali = preferBn
            )
            JarvisIntentType.FLASHLIGHT_ON -> {
                val res = actionExecutor.setFlashlight(true, preferBn)
                _torchOn.value = actionExecutor.isTorchOn()
                res
            }
            JarvisIntentType.FLASHLIGHT_OFF -> {
                val res = actionExecutor.setFlashlight(false, preferBn)
                _torchOn.value = actionExecutor.isTorchOn()
                res
            }
            JarvisIntentType.VOLUME_UP -> actionExecutor.adjustVolume("UP", preferBn)
            JarvisIntentType.VOLUME_DOWN -> actionExecutor.adjustVolume("DOWN", preferBn)
            JarvisIntentType.VOLUME_MUTE -> actionExecutor.adjustVolume("MUTE", preferBn)
            JarvisIntentType.SET_ALARM -> actionExecutor.setAlarm(plan.hour, plan.minute, plan.target, preferBn)
            JarvisIntentType.SET_TIMER -> actionExecutor.setTimer(plan.durationSeconds, plan.target, preferBn)
            JarvisIntentType.WEB_SEARCH -> actionExecutor.openWebSearch(plan.target.ifBlank { rawUserInput }, preferBn)
            JarvisIntentType.OPEN_SETTINGS -> actionExecutor.openSystemSettings(plan.target, preferBn)
            JarvisIntentType.GO_HOME,
            JarvisIntentType.GO_BACK,
            JarvisIntentType.OPEN_RECENTS,
            JarvisIntentType.OPEN_NOTIFICATIONS,
            JarvisIntentType.TAKE_SCREENSHOT -> actionExecutor.executeAccessibilityAction(plan.intentType.name, preferBn)
            JarvisIntentType.CHAT, JarvisIntentType.CODE -> null
        }

        val finalReply = execResult?.spokenFeedback ?: plan.spokenReply

        chatRepository.addJarvisMessage(
            text = finalReply,
            codeSnippet = plan.codeContent,
            codeLanguage = plan.codeLanguage,
            actionLabel = execResult?.actionLabel,
            actionSuccess = execResult?.success ?: true,
            suggestions = plan.suggestions
        )

        _uiState.value = JarvisUiState.Success(
            reply = finalReply,
            codeContent = plan.codeContent,
            codeLanguage = plan.codeLanguage,
            actionLabel = execResult?.actionLabel.orEmpty(),
            actionSuccess = execResult?.success ?: true,
            suggestions = plan.suggestions
        )

        if (speakReply && settings.value.autoSpeakResponses) {
            speakWithFemaleVoice(
                text = finalReply,
                englishFallback = plan.englishFallbackReply
            ) {
                if (resumeBackgroundAfter && settings.value.backgroundWakeEnabled && _permissions.value.hasMicrophone) {
                    JarvisBackgroundService.start(appContext)
                }
            }
        } else if (resumeBackgroundAfter && settings.value.backgroundWakeEnabled && _permissions.value.hasMicrophone) {
            JarvisBackgroundService.start(appContext)
        }
    }

    fun speakMessageReplay(text: String) {
        speakWithFemaleVoice(text = text)
    }

    private fun speakWithFemaleVoice(
        text: String,
        englishFallback: String = "",
        onDone: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val isBnText = text.any { it in '\u0980'..'\u09FF' }
            val shouldUseNeural = settings.value.useNeuralFemaleVoice ||
                (isBnText && !voiceEngine.hasInstalledBengaliTts.value)

            if (shouldUseNeural) {
                val cleanForTts = text
                    .replace(Regex("```[\\s\\S]*?```"), " কোড ব্লক স্ক্রিনে দেখানো হয়েছে। ")
                    .trim()
                    .let { if (it.length > 140) it.take(140) + "..." else it }
                val pcm = brain.synthesizeFemaleSpeechPcm(cleanForTts)
                if (pcm != null && pcm.isNotEmpty()) {
                    voiceEngine.playPcm24kHz(pcm, onDone)
                    return@launch
                }
            }
            voiceEngine.speak(text, englishFallback, onDone)
        }
    }

    fun launchInstalledApp(app: InstalledAppInfo) {
        val intent = appContext.packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        }
    }

    fun openAppSettings(packageName: String) {
        actionExecutor.openAppInfoSettings(packageName)
    }

    fun toggleFlashlightDirect() {
        val res = actionExecutor.setFlashlight(!_torchOn.value, preferBengali = true)
        _torchOn.value = actionExecutor.isTorchOn()
        res.spokenFeedback?.let {
            if (settings.value.autoSpeakResponses) speakWithFemaleVoice(it)
        }
    }

    fun speakCurrentTimeDirect() {
        _isJarvisAwake.value = true
        val preferBn = settings.value.preferredLanguage != "en"
        val timeText = actionExecutor.getCurrentTimeText(preferBn)
        val enFallback = actionExecutor.getCurrentTimeText(false)
        viewModelScope.launch {
            chatRepository.addJarvisMessage(
                text = timeText,
                actionLabel = "Device Clock",
                actionSuccess = true
            )
            _uiState.value = JarvisUiState.Success(
                reply = timeText,
                actionLabel = "Device Clock",
                actionSuccess = true
            )
            speakWithFemaleVoice(timeText, enFallback)
        }
    }

    fun triggerQuickSystemAction(actionType: String) {
        val preferBn = settings.value.preferredLanguage != "en"
        val res = when (actionType) {
            "VOL_UP" -> actionExecutor.adjustVolume("UP", preferBn)
            "VOL_DOWN" -> actionExecutor.adjustVolume("DOWN", preferBn)
            "SETTINGS" -> actionExecutor.openSystemSettings("all", preferBn)
            "WIFI" -> actionExecutor.openSystemSettings("wifi", preferBn)
            else -> actionExecutor.executeAccessibilityAction(actionType, preferBn)
        }
        res.spokenFeedback?.let {
            if (settings.value.autoSpeakResponses) speakWithFemaleVoice(it)
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            chatRepository.clearHistory()
            _uiState.value = JarvisUiState.Idle
        }
    }

    fun setBackgroundWakeEnabled(enabled: Boolean) {
        refreshPermissions()
        viewModelScope.launch {
            if (enabled) {
                if (!_permissions.value.hasMicrophone) {
                    _uiState.value = JarvisUiState.Error(
                        "হ্যালো জার্ভিস লিসেনিং চালু করতে Microphone অনুমতি দিন।"
                    )
                    return@launch
                }
                preferences.setBackgroundWakeEnabled(true)
                JarvisBackgroundService.start(appContext)
            } else {
                preferences.setBackgroundWakeEnabled(false)
                JarvisBackgroundService.stop(appContext)
            }
        }
    }

    fun setAutoSpeak(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoSpeakResponses(enabled) }
    }

    fun setUseDirectAiAudio(enabled: Boolean) {
        viewModelScope.launch { preferences.setUseDirectAiAudio(enabled) }
    }

    fun setUseNeuralFemaleVoice(enabled: Boolean) {
        viewModelScope.launch { preferences.setUseNeuralFemaleVoice(enabled) }
    }

    fun setVoicePitch(pitch: Float) {
        viewModelScope.launch { preferences.setVoicePitch(pitch) }
    }

    fun setVoiceSpeed(speed: Float) {
        viewModelScope.launch { preferences.setVoiceSpeed(speed) }
    }

    fun setPreferredLanguage(lang: String) {
        viewModelScope.launch { preferences.setPreferredLanguage(lang) }
    }

    fun setSelectedVoiceName(name: String) {
        viewModelScope.launch { preferences.setSelectedVoiceName(name) }
    }

    fun testFemaleVoice() {
        val sample = if (settings.value.preferredLanguage == "en") {
            "Hello! I am Jarvis, your personal AI assistant. How can I help you today?"
        } else {
            "হ্যালো! আমি জার্ভিস। আপনার যেকোনো কাজে আমি সাহায্য করতে প্রস্তুত।"
        }
        speakWithFemaleVoice(
            text = sample,
            englishFallback = "Hello! I am Jarvis, your personal AI assistant."
        )
    }

    private fun containsBanglishCue(text: String): Boolean {
        val lower = text.lowercase()
        val cues = listOf("koro", "dao", "koyta", "baje", "amar", "khabar", "tumi", "bolo", "kotha", "khulo", "bondho")
        return cues.any { lower.contains(it) }
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
    }

    companion object {
        private const val TAG = "JarvisViewModel"
    }
}
