package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ai.JarvisBrain
import com.example.ai.JarvisIntentType
import com.example.ai.LocalCommandMatcher
import com.example.ai.LocalRouteResult
import com.example.data.local.AppDatabase
import com.example.data.local.ChatRepository
import com.example.data.local.JarvisPreferences
import com.example.data.local.JarvisSettings
import com.example.device.DeviceActionExecutor
import com.example.voice.JarvisVoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface JarvisWakeEvent {
    data class Activated(val replyOrStatus: String, val putToSleep: Boolean = false) : JarvisWakeEvent
}

class JarvisBackgroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var voiceEngine: JarvisVoiceEngine
    private lateinit var brain: JarvisBrain
    private lateinit var actionExecutor: DeviceActionExecutor
    private lateinit var chatRepository: ChatRepository
    private lateinit var preferences: JarvisPreferences

    private var wakeRecognizer: SpeechRecognizer? = null
    private var isAwaitingCommand = false
    private var isDestroyed = false
    private var currentSettings = JarvisSettings()
    private var useEnglishWakeLocale = false
    private var wakeHandledInCurrentTurn = false

    private val watchdogRunnable = Runnable {
        if (!isDestroyed && !voiceEngine.isSpeaking.value) {
            scheduleWakeListening(400L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        voiceEngine = JarvisVoiceEngine(this)
        brain = JarvisBrain()
        actionExecutor = DeviceActionExecutor(this)
        chatRepository = ChatRepository(AppDatabase.getInstance(this).chatDao())
        preferences = JarvisPreferences(this)

        serviceScope.launch {
            preferences.settingsFlow.collect { settings ->
                currentSettings = settings
                voiceEngine.updateSettings(settings)
            }
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            Log.w(TAG, "Cannot start microphone foreground service without RECORD_AUDIO permission")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(getString(R.string.bg_service_subtitle))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _isRunning.value = true
        scheduleWakeListening(delayMs = 350L)
        return START_STICKY
    }

    private fun scheduleWakeListening(delayMs: Long = 550L) {
        if (isDestroyed) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (!isDestroyed) {
                startPassiveRecognition()
            }
        }, delayMs)
    }

    private fun startPassiveRecognition() {
        if (isDestroyed || voiceEngine.isSpeaking.value) {
            scheduleWakeListening(800L)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "SpeechRecognizer unavailable on this device for background wake")
            _wakeAvailable.value = false
            return
        }
        _wakeAvailable.value = true

        destroyWakeRecognizer()
        wakeHandledInCurrentTurn = false
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        wakeRecognizer = recognizer

        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.postDelayed(watchdogRunnable, 11_000L)

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                mainHandler.removeCallbacks(watchdogRunnable)
                if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                ) {
                    useEnglishWakeLocale = !useEnglishWakeLocale
                }
                val backoff = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200L else 600L
                if (!isDestroyed) {
                    isAwaitingCommand = false
                    scheduleWakeListening(backoff)
                }
            }

            override fun onResults(results: Bundle?) {
                mainHandler.removeCallbacks(watchdogRunnable)
                if (wakeHandledInCurrentTurn) return
                val spoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()

                if (spoken.isBlank()) {
                    isAwaitingCommand = false
                    scheduleWakeListening(500L)
                    return
                }

                handleRecognizedSpeech(spoken)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (isAwaitingCommand || wakeHandledInCurrentTurn) return
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotEmpty() && exactWakeRegex.containsMatchIn(partial)) {
                    // Instant wake chime as soon as "হ্যালো জার্ভিস" is heard!
                    val remainder = partial.replace(exactWakeRegex, "").trim()
                    if (remainder.isEmpty()) {
                        wakeHandledInCurrentTurn = true
                        mainHandler.removeCallbacks(watchdogRunnable)
                        destroyWakeRecognizer()
                        handleWakeOnly(partial)
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val lang = if (useEnglishWakeLocale || currentSettings.preferredLanguage == "en") {
            "en-US"
        } else {
            "bn-BD"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 850L)
        }
        runCatching { recognizer.startListening(intent) }
            .onFailure {
                Log.e(TAG, "Failed to start wake recognizer", it)
                scheduleWakeListening(1200L)
            }
    }

    private fun handleWakeOnly(spoken: String) {
        playChime()
        isAwaitingCommand = true
        val isBn = spoken.any { it in '\u0980'..'\u09FF' } || currentSettings.preferredLanguage != "en"
        val ack = if (isBn) {
            "হ্যালো! আমি জার্ভিস, এখন চালু হয়েছি। বলুন কী করতে হবে?"
        } else {
            "Hello! Jarvis is now active. How can I help?"
        }
        _wakeEvents.tryEmit(JarvisWakeEvent.Activated(ack))
        updateNotification("Jarvis Active: Listening...")
        bringUiToForegroundIfNeeded()
        speakBgReply(ack, "Hello! Jarvis is now active. How can I help?") {
            scheduleWakeListening(300L)
        }
    }

    private fun handleRecognizedSpeech(spoken: String) {
        val match = exactWakeRegex.find(spoken)

        if (!isAwaitingCommand && match == null) {
            scheduleWakeListening(450L)
            return
        }

        val commandText = if (isAwaitingCommand) {
            isAwaitingCommand = false
            spoken
        } else {
            spoken.replace(exactWakeRegex, "").trim(' ', ',', '.', '?', '!', '।')
        }

        if (commandText.isBlank()) {
            handleWakeOnly(spoken)
            return
        }

        playChime()
        updateNotification("Executing: $commandText")
        executeBackgroundCommand(commandText)
    }

    private fun bringUiToForegroundIfNeeded() {
        runCatching {
            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_WAKE_TRIGGERED, true)
            }
            startActivity(openIntent)
        }
    }

    private fun executeBackgroundCommand(commandText: String) {
        serviceScope.launch {
            val isBn = commandText.any { it in '\u0980'..'\u09FF' }
            try {
                chatRepository.addUserMessage(commandText)

                // 0ms local instant check before calling Gemini
                val installedApps = actionExecutor.getInstalledLauncherApps()
                val localResult = LocalCommandMatcher.tryMatch(
                    input = commandText,
                    installedApps = installedApps,
                    actionExecutor = actionExecutor,
                    preferBn = isBn
                )

                if (localResult is LocalRouteResult.SleepCommand) {
                    val sleepMsg = if (isBn) {
                        "জার্ভিস বন্ধ হলো। আবার চালু করতে বলুন হ্যালো জার্ভিস।"
                    } else {
                        "Jarvis going to standby. Say Hello Jarvis to wake me."
                    }
                    _wakeEvents.tryEmit(JarvisWakeEvent.Activated(sleepMsg, putToSleep = true))
                    updateNotification(getString(R.string.bg_service_subtitle))
                    speakBgReply(sleepMsg, "Jarvis going to standby.") {
                        scheduleWakeListening(500L)
                    }
                    return@launch
                }

                if (localResult is LocalRouteResult.WakeCommandOnly) {
                    handleWakeOnly(commandText)
                    return@launch
                }

                val plan = if (localResult is LocalRouteResult.MatchedPlan) {
                    localResult.plan
                } else {
                    val history = chatRepository.getRecentContext(4)
                    val currentTime = actionExecutor.getCurrentTimeText(isBn)
                    brain.processInput(commandText, history, currentTime)
                }

                val execResult = when (plan.intentType) {
                    JarvisIntentType.TELL_TIME -> null
                    JarvisIntentType.OPEN_APP -> actionExecutor.openApp(plan.target, isBn)
                    JarvisIntentType.CLOSE_APP -> actionExecutor.closeAppOrGoHome(plan.target, isBn)
                    JarvisIntentType.CALL -> actionExecutor.makePhoneCall(plan.target, isBn)
                    JarvisIntentType.SEND_MESSAGE -> actionExecutor.sendMessage(
                        plan.target,
                        plan.messageBody,
                        plan.viaWhatsApp,
                        isBn
                    )
                    JarvisIntentType.SEND_EMAIL -> actionExecutor.sendEmail(
                        plan.target,
                        plan.subject,
                        plan.messageBody,
                        isBn
                    )
                    JarvisIntentType.FLASHLIGHT_ON -> actionExecutor.setFlashlight(true, isBn)
                    JarvisIntentType.FLASHLIGHT_OFF -> actionExecutor.setFlashlight(false, isBn)
                    JarvisIntentType.VOLUME_UP -> actionExecutor.adjustVolume("UP", isBn)
                    JarvisIntentType.VOLUME_DOWN -> actionExecutor.adjustVolume("DOWN", isBn)
                    JarvisIntentType.VOLUME_MUTE -> actionExecutor.adjustVolume("MUTE", isBn)
                    JarvisIntentType.SET_ALARM -> actionExecutor.setAlarm(plan.hour, plan.minute, plan.target, isBn)
                    JarvisIntentType.SET_TIMER -> actionExecutor.setTimer(plan.durationSeconds, plan.target, isBn)
                    JarvisIntentType.WEB_SEARCH -> actionExecutor.openWebSearch(plan.target.ifBlank { commandText }, isBn)
                    JarvisIntentType.OPEN_SETTINGS -> actionExecutor.openSystemSettings(plan.target, isBn)
                    JarvisIntentType.GO_HOME,
                    JarvisIntentType.GO_BACK,
                    JarvisIntentType.OPEN_RECENTS,
                    JarvisIntentType.OPEN_NOTIFICATIONS,
                    JarvisIntentType.TAKE_SCREENSHOT -> actionExecutor.executeAccessibilityAction(plan.intentType.name, isBn)
                    JarvisIntentType.CHAT, JarvisIntentType.CODE -> null
                }

                val currentTime = actionExecutor.getCurrentTimeText(isBn)
                val finalReply = when {
                    plan.intentType == JarvisIntentType.TELL_TIME -> currentTime
                    execResult?.spokenFeedback != null -> execResult.spokenFeedback
                    else -> plan.spokenReply
                }

                chatRepository.addJarvisMessage(
                    text = finalReply,
                    codeSnippet = plan.codeContent,
                    codeLanguage = plan.codeLanguage,
                    actionLabel = execResult?.actionLabel,
                    actionSuccess = execResult?.success ?: true,
                    suggestions = plan.suggestions
                )

                _wakeEvents.tryEmit(JarvisWakeEvent.Activated(finalReply))
                updateNotification(getString(R.string.bg_service_subtitle))
                speakBgReply(finalReply, plan.englishFallbackReply) {
                    scheduleWakeListening(500L)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background command execution failed", e)
                val errMsg = if (isBn) {
                    "দুঃখিত, এই মুহূর্তে উত্তর দিতে পারছি না।"
                } else {
                    "Sorry, I encountered an error processing that."
                }
                speakBgReply(errMsg, "Sorry, I encountered an error.") {
                    scheduleWakeListening(1000L)
                }
            }
        }
    }

    private fun speakBgReply(text: String, englishFallback: String, onDone: () -> Unit) {
        serviceScope.launch {
            val isBnText = text.any { it in '\u0980'..'\u09FF' }
            val shouldUseNeural = currentSettings.useNeuralFemaleVoice ||
                (isBnText && !voiceEngine.hasInstalledBengaliTts.value)
            if (shouldUseNeural) {
                val clip = text.trim().let { if (it.length > 140) it.take(140) + "..." else it }
                val pcm = brain.synthesizeFemaleSpeechPcm(clip)
                if (pcm != null && pcm.isNotEmpty()) {
                    voiceEngine.playPcm24kHz(pcm, onDone)
                    return@launch
                }
            }
            voiceEngine.speak(text, englishFallback, onDone)
        }
    }

    private fun playChime() {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 130)
            mainHandler.postDelayed({ runCatching { tg.release() } }, 300L)
        }
    }

    private fun stopSelfCleanly() {
        serviceScope.launch {
            preferences.setBackgroundWakeEnabled(false)
            stopSelf()
        }
    }

    private fun destroyWakeRecognizer() {
        runCatching { wakeRecognizer?.destroy() }
        wakeRecognizer = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.bg_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.bg_service_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(contentText: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_START_VOICE_LISTEN, true)
        }
        val openPending = PendingIntent.getActivity(
            this,
            101,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JarvisBackgroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.bg_service_title))
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(0, getString(R.string.action_ask_now), openPending)
            .addAction(0, getString(R.string.action_stop), stopPending)
            .build()
    }

    override fun onDestroy() {
        isDestroyed = true
        _isRunning.value = false
        mainHandler.removeCallbacksAndMessages(null)
        destroyWakeRecognizer()
        voiceEngine.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "JarvisBackgroundService"
        private const val CHANNEL_ID = "jarvis_wake_channel"
        private const val NOTIFICATION_ID = 707
        const val ACTION_STOP = "com.example.action.STOP_JARVIS_BG"
        const val EXTRA_START_VOICE_LISTEN = "start_voice_listen"
        const val EXTRA_WAKE_TRIGGERED = "wake_triggered"

        private val exactWakeRegex = Regex(
            pattern = "(হ্যালো\\s*জার্ভিস|হ্যালো\\s*জারভিস|হ্যালো\\s*জার্বিস|হালো\\s*জার্ভিস|হেলো\\s*জার্ভিস|হ্যালো\\s*যার্ভিস|hello\\s*jarvis|helo\\s*jarvis|halo\\s*jarvis|hallow\\s*jarvis|hello\\s*jervis|hello\\s*jarvish)",
            option = RegexOption.IGNORE_CASE
        )

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _wakeAvailable = MutableStateFlow(true)
        val wakeAvailable: StateFlow<Boolean> = _wakeAvailable.asStateFlow()

        private val _wakeEvents = MutableSharedFlow<JarvisWakeEvent>(extraBufferCapacity = 8)
        val wakeEvents: SharedFlow<JarvisWakeEvent> = _wakeEvents.asSharedFlow()

        fun start(context: Context) {
            val intent = Intent(context, JarvisBackgroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvisBackgroundService::class.java)
            context.stopService(intent)
        }
    }
}
