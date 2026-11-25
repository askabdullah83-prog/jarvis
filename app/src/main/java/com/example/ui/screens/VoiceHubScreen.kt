package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.HearingDisabled
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.JarvisUiState
import com.example.ui.components.CodeBlockCard
import com.example.ui.components.JarvisOrb
import com.example.ui.components.OrbVisualMode
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassCardBg
import com.example.ui.theme.NeuralSurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseListening
import com.example.ui.theme.SapphireContainer
import com.example.ui.theme.SapphireCore
import com.example.ui.theme.SolarGold
import com.example.ui.theme.SolarGoldContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VoiceHubScreen(
    uiState: JarvisUiState,
    isJarvisAwake: Boolean,
    isListening: Boolean,
    isSpeaking: Boolean,
    partialTranscript: String,
    lastSpokenInput: String,
    rmsLevel: Float,
    isBackgroundRunning: Boolean,
    wakeAvailable: Boolean,
    hasMicPermission: Boolean,
    useDirectAiAudio: Boolean,
    onToggleAudioMode: () -> Unit,
    onToggleStandby: () -> Unit,
    onOrbClick: () -> Unit,
    onQuickPrompt: (String) -> Unit,
    onTellTimeDirect: () -> Unit,
    onToggleBackgroundStatusClick: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    var clockText by remember { mutableStateOf("") }
    var quickInputText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("hh:mm a • EEE, d MMM", Locale.ENGLISH)
        while (true) {
            clockText = fmt.format(Date())
            delay(15_000L)
        }
    }

    val isBusy = uiState is JarvisUiState.Thinking || uiState is JarvisUiState.Streaming

    val orbMode = when {
        isListening -> OrbVisualMode.LISTENING
        isBusy -> OrbVisualMode.THINKING
        isSpeaking -> OrbVisualMode.SPEAKING
        !isJarvisAwake -> OrbVisualMode.STANDBY
        else -> OrbVisualMode.IDLE
    }

    val statusTitle = when (orbMode) {
        OrbVisualMode.STANDBY -> "জার্ভিস এখন বন্ধ (Standby Mode)"
        OrbVisualMode.LISTENING -> "শুনছি... কথা বলুন"
        OrbVisualMode.THINKING -> if (uiState is JarvisUiState.Streaming) "জার্ভিস উত্তর লিখছে..." else "জার্ভিস ভাবছে..."
        OrbVisualMode.SPEAKING -> "জার্ভিস কথা বলছে..."
        OrbVisualMode.IDLE -> "জার্ভিস চালু আছে (Active)"
    }

    val statusHint = when (orbMode) {
        OrbVisualMode.STANDBY -> "\"হ্যালো জার্ভিস\" বলুন চালু করতে • অথবা কথা বলতে গোল বোতামে ১ বার ট্যাপ করুন"
        OrbVisualMode.LISTENING -> "কথা শেষ হলে ২য় বার গোল বোতামে ট্যাপ করলে বলা বন্ধ হবে"
        OrbVisualMode.THINKING -> "দ্রুত উত্তর প্রস্তুত হচ্ছে..."
        OrbVisualMode.SPEAKING -> "জার্ভিসকে থামাতে গোল বোতামে ট্যাপ করুন"
        OrbVisualMode.IDLE -> "কথা বলতে ১ বার ট্যাপ করুন • বন্ধ করতে Standby ট্যাপ করুন"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        if (isJarvisAwake) Color(0xFF141B29) else Color(0xFF0C1018),
                        ObsidianBg,
                        Color(0xFF04060A)
                    )
                )
            )
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Redesigned HUD Top Bar
            Surface(
                color = NeuralSurface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "JARVIS",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (isJarvisAwake) SolarGold else TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = if (isJarvisAwake) EmeraldReady.copy(alpha = 0.18f) else SapphireContainer,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = if (isJarvisAwake) "ON" else "OFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isJarvisAwake) EmeraldReady else SapphireCore,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = clockText,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Power / Standby toggle pill
                        Surface(
                            shape = CircleShape,
                            color = if (isJarvisAwake) SolarGoldContainer else GlassCardBg,
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(
                                    1.dp,
                                    if (isJarvisAwake) SolarGold.copy(alpha = 0.5f) else GlassBorder,
                                    CircleShape
                                )
                                .clickable(onClick = onToggleStandby)
                                .testTag("standby_power_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PowerSettingsNew,
                                    contentDescription = "Toggle Jarvis active or standby",
                                    tint = if (isJarvisAwake) SolarGold else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isJarvisAwake) "বন্ধ করুন" else "চালু করুন",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isJarvisAwake) SolarGold else TextSecondary
                                )
                            }
                        }

                        // Background "হ্যালো জার্ভিস" Wake Listener pill
                        Surface(
                            shape = CircleShape,
                            color = if (isBackgroundRunning) EmeraldReady.copy(alpha = 0.16f) else GlassCardBg,
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(
                                    1.dp,
                                    if (isBackgroundRunning) EmeraldReady.copy(alpha = 0.45f) else GlassBorder,
                                    CircleShape
                                )
                                .clickable(onClick = onToggleBackgroundStatusClick)
                                .testTag("background_wake_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isBackgroundRunning) Icons.Rounded.Hearing else Icons.Rounded.HearingDisabled,
                                    contentDescription = "Background wake status",
                                    tint = if (isBackgroundRunning) EmeraldReady else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isBackgroundRunning) "Wake" else "Wake Off",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isBackgroundRunning) EmeraldReady else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            if (!hasMicPermission) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = RoseListening.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                        .border(1.dp, RoseListening.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "\"হ্যালো জার্ভিস\" ও ভয়েস কমান্ডের জন্য Mic এক্সেস দিন",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = onRequestMicPermission,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = RoseListening,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("grant_mic_button")
                        ) {
                            Text("Allow Mic")
                        }
                    }
                }
            } else if (!isJarvisAwake) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = SapphireContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                        .border(1.dp, SapphireCore.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Hearing,
                            contentDescription = null,
                            tint = SapphireCore,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isBackgroundRunning && wakeAvailable) {
                                "স্ট্যান্ডবাই চালু আছে: \"হ্যালো জার্ভিস\" বললেই চালু হবে"
                            } else {
                                "জার্ভিস বন্ধ আছে • চালু করতে Wake চালু করুন বা বোতামে ট্যাপ করুন"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Redesigned Arc-Core HUD Orb (Tap 1 = Talk, Tap 2 = Stop)
            JarvisOrb(
                mode = orbMode,
                rmsLevel = rmsLevel,
                onClick = onOrbClick,
                size = 216.dp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = statusTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when (orbMode) {
                    OrbVisualMode.STANDBY -> SapphireCore
                    OrbVisualMode.LISTENING -> RoseListening
                    OrbVisualMode.THINKING -> SapphireCore
                    OrbVisualMode.SPEAKING -> EmeraldReady
                    OrbVisualMode.IDLE -> SolarGold
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = statusHint,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Voice Engine Mode Toggle Pill
            Surface(
                color = if (useDirectAiAudio) SolarGoldContainer else GlassCardBg,
                shape = CircleShape,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(
                        1.dp,
                        if (useDirectAiAudio) SolarGold.copy(alpha = 0.45f) else GlassBorder,
                        CircleShape
                    )
                    .clickable(onClick = onToggleAudioMode)
                    .testTag("voice_engine_toggle_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = if (useDirectAiAudio) SolarGold else SapphireCore,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (useDirectAiAudio) "Tap-to-Toggle HD AI Audio" else "System STT Mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
            }

            val shownTranscript = when {
                isListening && partialTranscript.isNotBlank() -> partialTranscript
                lastSpokenInput.isNotBlank() && uiState !is JarvisUiState.Idle -> "\"$lastSpokenInput\""
                else -> ""
            }
            if (shownTranscript.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = shownTranscript,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SolarGold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Response / Live Streaming Card
            AnimatedVisibility(
                visible = isBusy ||
                    uiState is JarvisUiState.Success ||
                    uiState is JarvisUiState.Error,
                enter = fadeIn() + slideInVertically { it / 4 },
                exit = fadeOut()
            ) {
                when (uiState) {
                    is JarvisUiState.Thinking -> {
                        Surface(
                            color = GlassCardBg,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 620.dp)
                                .border(1.dp, SapphireCore.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = SolarGold,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "জার্ভিস দ্রুত উত্তর প্রস্তুত করছে...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    is JarvisUiState.Streaming -> {
                        Surface(
                            color = GlassCardBg,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 620.dp)
                                .border(1.dp, SolarGold.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        color = SolarGold,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Live Reply...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SolarGold
                                    )
                                }
                                Text(
                                    text = uiState.partialReply,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    is JarvisUiState.Success -> {
                        Surface(
                            color = GlassCardBg,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 620.dp)
                                .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (uiState.actionLabel.isNotBlank()) {
                                    Surface(
                                        color = if (uiState.actionSuccess) {
                                            EmeraldReady.copy(alpha = 0.16f)
                                        } else {
                                            RoseListening.copy(alpha = 0.16f)
                                        },
                                        shape = CircleShape
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (uiState.actionSuccess) {
                                                    Icons.Rounded.CheckCircle
                                                } else {
                                                    Icons.Rounded.ErrorOutline
                                                },
                                                contentDescription = null,
                                                tint = if (uiState.actionSuccess) EmeraldReady else RoseListening,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = uiState.actionLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (uiState.actionSuccess) EmeraldReady else RoseListening
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = uiState.reply,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary
                                )

                                if (uiState.codeContent.isNotBlank()) {
                                    CodeBlockCard(
                                        code = uiState.codeContent,
                                        language = uiState.codeLanguage
                                    )
                                }

                                if (uiState.suggestions.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        uiState.suggestions.forEach { suggestion ->
                                            AssistChip(
                                                onClick = { onQuickPrompt(suggestion) },
                                                enabled = !isBusy,
                                                label = { Text(suggestion) },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = ObsidianBg,
                                                    labelColor = SolarGold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is JarvisUiState.Error -> {
                        Surface(
                            color = RoseListening.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 620.dp)
                                .border(1.dp, RoseListening.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = RoseListening
                                )
                                Text(
                                    text = uiState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    else -> Unit
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Text Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = quickInputText,
                    onValueChange = { quickInputText = it },
                    placeholder = { Text("দ্রুত লিখুন বা প্রশ্ন করুন...", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (quickInputText.isNotBlank() && !isBusy) {
                                onQuickPrompt(quickInputText)
                                quickInputText = ""
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SolarGold,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = SolarGold
                    ),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("voice_hub_quick_input")
                )

                IconButton(
                    onClick = {
                        if (quickInputText.isNotBlank()) {
                            onQuickPrompt(quickInputText)
                            quickInputText = ""
                        }
                    },
                    enabled = quickInputText.isNotBlank() && !isBusy,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (quickInputText.isNotBlank() && !isBusy) SolarGold else GlassCardBg)
                        .testTag("voice_hub_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send",
                        tint = if (quickInputText.isNotBlank() && !isBusy) Color(0xFF261700) else TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Redesigned 2x2 Futuristic HUD Command Cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "তাৎক্ষণিক কমান্ড (0ms Instant Actions)",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HudQuickCard(
                        label = "এখন কয়টা বাজে?",
                        sublabel = "Tell Time",
                        icon = Icons.Rounded.Schedule,
                        accent = SolarGold,
                        enabled = true,
                        onClick = onTellTimeDirect,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chip_tell_time")
                    )
                    HudQuickCard(
                        label = "YouTube খোলো",
                        sublabel = "Launch App",
                        icon = Icons.Rounded.AutoAwesome,
                        accent = SapphireCore,
                        enabled = !isBusy,
                        onClick = { onQuickPrompt("ইউটিউব খোলো") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chip_open_youtube")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HudQuickCard(
                        label = "Python কোড লিখো",
                        sublabel = "Code AI",
                        icon = Icons.Rounded.Code,
                        accent = EmeraldReady,
                        enabled = !isBusy,
                        onClick = { onQuickPrompt("একটি সুন্দর Python calculator কোড লিখে দাও") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chip_write_code")
                    )
                    HudQuickCard(
                        label = "ফ্ল্যাশলাইট জ্বালাও",
                        sublabel = "Torch On",
                        icon = Icons.Rounded.FlashlightOn,
                        accent = SolarGold,
                        enabled = !isBusy,
                        onClick = { onQuickPrompt("ফ্ল্যাশলাইট জ্বালাও") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chip_flashlight")
                    )
                }

                Surface(
                    color = SapphireContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, SapphireCore.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .clickable(onClick = onOpenChat)
                        .testTag("chip_open_chat")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Jarvis Chat, Code & Suggestions",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "যেকোনো প্রশ্ন করুন এবং স্মার্ট সাজেসন পান",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Chat,
                            contentDescription = "Open chat",
                            tint = SapphireCore
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HudQuickCard(
    label: String,
    sublabel: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = GlassCardBg,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
