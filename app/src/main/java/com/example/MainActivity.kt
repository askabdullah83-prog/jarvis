package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.firebase.configureAppCheck
import com.example.service.JarvisBackgroundService
import com.example.ui.JarvisViewModel
import com.example.ui.screens.AccessSettingsScreen
import com.example.ui.screens.ActionsHubScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.VoiceHubScreen
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.NeuralSurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SolarGold
import com.example.ui.theme.SolarGoldContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private const val ROUTE_VOICE = "voice"
private const val ROUTE_CHAT = "chat"
private const val ROUTE_CONTROL = "control"
private const val ROUTE_ACCESS = "access"

private data class JarvisNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Required first: configure Firebase App Check with launch intent before UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        val triggerListen = intent?.getBooleanExtra(
            JarvisBackgroundService.EXTRA_START_VOICE_LISTEN,
            false
        ) == true
        val triggerWake = intent?.getBooleanExtra(
            JarvisBackgroundService.EXTRA_WAKE_TRIGGERED,
            false
        ) == true

        setContent {
            JarvisTheme {
                JarvisAppRoot(
                    autoListenOnLaunch = triggerListen,
                    wokeByVoiceCommand = triggerWake
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun JarvisAppRoot(
    autoListenOnLaunch: Boolean,
    wokeByVoiceCommand: Boolean,
    viewModel: JarvisViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_VOICE

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isJarvisAwake by viewModel.isJarvisAwake.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val lastSpokenInput by viewModel.lastSpokenInput.collectAsStateWithLifecycle()
    val rmsDb by viewModel.rmsDb.collectAsStateWithLifecycle()
    val availableVoices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val hasInstalledBengaliTts by viewModel.hasInstalledBengaliTts.collectAsStateWithLifecycle()
    val isBackgroundRunning by viewModel.isBackgroundRunning.collectAsStateWithLifecycle()
    val wakeAvailable by viewModel.wakeAvailable.collectAsStateWithLifecycle()
    val torchOn by viewModel.torchOn.collectAsStateWithLifecycle()

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
        if (granted) {
            viewModel.onOrbClicked()
        }
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    val callLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
        viewModel.refreshInstalledApps()
    }

    LaunchedEffect(autoListenOnLaunch, wokeByVoiceCommand) {
        if (wokeByVoiceCommand) {
            viewModel.onWakeTriggerIntent()
        }
        if (autoListenOnLaunch && permissions.hasMicrophone) {
            viewModel.startVoiceListening()
        }
    }

    val tabs = listOf(
        JarvisNavTab(
            route = ROUTE_VOICE,
            label = "Core",
            selectedIcon = Icons.Rounded.GraphicEq,
            unselectedIcon = Icons.Outlined.GraphicEq,
            testTag = "nav_voice"
        ),
        JarvisNavTab(
            route = ROUTE_CHAT,
            label = "Chat",
            selectedIcon = Icons.Rounded.ChatBubble,
            unselectedIcon = Icons.Outlined.ChatBubbleOutline,
            testTag = "nav_chat"
        ),
        JarvisNavTab(
            route = ROUTE_CONTROL,
            label = "Control",
            selectedIcon = Icons.Rounded.Widgets,
            unselectedIcon = Icons.Outlined.Widgets,
            testTag = "nav_control"
        ),
        JarvisNavTab(
            route = ROUTE_ACCESS,
            label = "Access",
            selectedIcon = Icons.Rounded.AdminPanelSettings,
            unselectedIcon = Icons.Outlined.AdminPanelSettings,
            testTag = "nav_access"
        )
    )

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val handleOrbTap: () -> Unit = {
        if (permissions.hasMicrophone) {
            viewModel.onOrbClicked()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = NeuralSurface,
                        contentColor = TextPrimary
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = SolarGold,
                                    selectedTextColor = SolarGold,
                                    indicatorColor = SolarGoldContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = NeuralSurface,
                        contentColor = TextPrimary
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = SolarGold,
                                    selectedTextColor = SolarGold,
                                    indicatorColor = SolarGoldContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_VOICE,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_VOICE) {
                        VoiceHubScreen(
                            uiState = uiState,
                            isJarvisAwake = isJarvisAwake,
                            isListening = isListening,
                            isSpeaking = isSpeaking,
                            partialTranscript = partialTranscript,
                            lastSpokenInput = lastSpokenInput,
                            rmsLevel = rmsDb,
                            isBackgroundRunning = isBackgroundRunning,
                            wakeAvailable = wakeAvailable,
                            hasMicPermission = permissions.hasMicrophone,
                            useDirectAiAudio = settings.useDirectAiAudio,
                            onToggleAudioMode = {
                                viewModel.setUseDirectAiAudio(!settings.useDirectAiAudio)
                            },
                            onToggleStandby = { viewModel.toggleStandbyState() },
                            onOrbClick = handleOrbTap,
                            onQuickPrompt = { prompt -> viewModel.submitQuery(prompt) },
                            onTellTimeDirect = { viewModel.speakCurrentTimeDirect() },
                            onToggleBackgroundStatusClick = {
                                if (!permissions.hasMicrophone) {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.setBackgroundWakeEnabled(!settings.backgroundWakeEnabled)
                                }
                            },
                            onRequestMicPermission = {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onOpenChat = { navigateToTab(ROUTE_CHAT) }
                        )
                    }

                    composable(ROUTE_CHAT) {
                        ChatScreen(
                            messages = messages,
                            uiState = uiState,
                            isListening = isListening,
                            onSendMessage = { msg -> viewModel.submitQuery(msg) },
                            onSpeakMessage = { text -> viewModel.speakMessageReplay(text) },
                            onMicClick = handleOrbTap,
                            onClearHistory = { viewModel.clearChatHistory() }
                        )
                    }

                    composable(ROUTE_CONTROL) {
                        ActionsHubScreen(
                            installedApps = installedApps,
                            torchOn = torchOn,
                            hasAccessibility = permissions.hasAccessibility,
                            onToggleFlashlight = { viewModel.toggleFlashlightDirect() },
                            onTellTime = { viewModel.speakCurrentTimeDirect() },
                            onSystemAction = { action -> viewModel.triggerQuickSystemAction(action) },
                            onQuickVoiceCommand = { prompt ->
                                navigateToTab(ROUTE_CHAT)
                                viewModel.submitQuery(prompt)
                            },
                            onLaunchApp = { app -> viewModel.launchInstalledApp(app) },
                            onOpenAppInfo = { pkg -> viewModel.openAppSettings(pkg) }
                        )
                    }

                    composable(ROUTE_ACCESS) {
                        AccessSettingsScreen(
                            settings = settings,
                            permissions = permissions,
                            isBackgroundRunning = isBackgroundRunning,
                            hasInstalledBengaliTts = hasInstalledBengaliTts,
                            availableVoices = availableVoices,
                            onToggleBackgroundWake = { enabled ->
                                if (enabled && !permissions.hasMicrophone) {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.setBackgroundWakeEnabled(enabled)
                                }
                            },
                            onToggleAutoSpeak = { viewModel.setAutoSpeak(it) },
                            onToggleDirectAiAudio = { viewModel.setUseDirectAiAudio(it) },
                            onToggleNeuralFemaleVoice = { viewModel.setUseNeuralFemaleVoice(it) },
                            onPitchChange = { viewModel.setVoicePitch(it) },
                            onSpeedChange = { viewModel.setVoiceSpeed(it) },
                            onLanguageChange = { viewModel.setPreferredLanguage(it) },
                            onSelectVoice = { viewModel.setSelectedVoiceName(it) },
                            onTestFemaleVoice = { viewModel.testFemaleVoice() },
                            onInstallTtsData = {
                                val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { context.startActivity(installIntent) }
                            },
                            onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            onRequestContacts = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
                            onRequestCallPhone = { callLauncher.launch(Manifest.permission.CALL_PHONE) },
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onOpenAccessibilitySettings = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}
