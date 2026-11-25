package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.JarvisSettings
import com.example.ui.PermissionSnapshot
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassCardBg
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseListening
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAura
import com.example.voice.FemaleVoiceOption

@Composable
fun AccessSettingsScreen(
    settings: JarvisSettings,
    permissions: PermissionSnapshot,
    isBackgroundRunning: Boolean,
    hasInstalledBengaliTts: Boolean,
    availableVoices: List<FemaleVoiceOption>,
    onToggleBackgroundWake: (Boolean) -> Unit,
    onToggleAutoSpeak: (Boolean) -> Unit,
    onToggleDirectAiAudio: (Boolean) -> Unit,
    onToggleNeuralFemaleVoice: (Boolean) -> Unit,
    onPitchChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onLanguageChange: (String) -> Unit,
    onSelectVoice: (String) -> Unit,
    onTestFemaleVoice: () -> Unit,
    onInstallTtsData: () -> Unit,
    onRequestMic: () -> Unit,
    onRequestContacts: () -> Unit,
    onRequestCallPhone: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "এক্সেস ও ভয়েস স্টুডিও",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "ব্যাকগ্রাউন্ড লিসেনিং, ফোন পারমিশন এবং মেয়ে কণ্ঠ সেটআপ করুন",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Background Wake Service Card
        item {
            Surface(
                color = GlassCardBg,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isBackgroundRunning) EmeraldReady.copy(alpha = 0.5f) else GlassBorder,
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = if (isBackgroundRunning) EmeraldReady.copy(alpha = 0.2f) else CyanContainer,
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Hearing,
                            contentDescription = null,
                            tint = if (isBackgroundRunning) EmeraldReady else CyanPulse,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "স্ট্যান্ডবাই ওয়েক (\"হ্যালো জার্ভিস\")",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isBackgroundRunning) {
                                "সক্রিয়: জার্ভিস বন্ধ থাকবে, \"হ্যালো জার্ভিস\" বললেই চালু হবে"
                            } else {
                                "জার্ভিস বন্ধ রেখে \"হ্যালো জার্ভিস\" কমান্ডে চালু করতে এটি অন করুন"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = settings.backgroundWakeEnabled,
                        onCheckedChange = onToggleBackgroundWake,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmeraldReady
                        ),
                        modifier = Modifier.testTag("switch_background_wake")
                    )
                }
            }
        }

        // Voice Capture & Female Voice Studio
        item {
            Surface(
                color = GlassCardBg,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RecordVoiceOver,
                                contentDescription = null,
                                tint = VioletAura
                            )
                            Text(
                                text = "মেয়ে কণ্ঠ (Female Voice)",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Switch(
                            checked = settings.autoSpeakResponses,
                            onCheckedChange = onToggleAutoSpeak,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = VioletAura
                            )
                        )
                    }

                    // HD Direct AI Audio Input toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = CyanPulse
                            )
                            Column {
                                Text(
                                    text = "HD Direct AI Voice Input",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "সব ফোনে বাংলা ও ইংরেজি ভয়েস সরাসরি শুনবে",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = settings.useDirectAiAudio,
                            onCheckedChange = onToggleDirectAiAudio,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CyanPulse
                            ),
                            modifier = Modifier.testTag("switch_direct_audio")
                        )
                    }

                    // HD Neural Female TTS toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "HD Neural Female Voice (Cloud)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "OFF রাখলে ফোনের ভয়েস দিয়ে সাথে সাথে বলবে (দ্রুততম)। ON করলে প্রতিবার Cloud মেয়ে কণ্ঠ আনবে।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = settings.useNeuralFemaleVoice,
                            onCheckedChange = onToggleNeuralFemaleVoice,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = VioletAura
                            ),
                            modifier = Modifier.testTag("switch_neural_voice")
                        )
                    }

                    // Language mode chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val langs = listOf("auto" to "Auto BN/EN", "bn" to "বাংলা", "en" to "English")
                        langs.forEach { (key, label) ->
                            FilterChip(
                                selected = settings.preferredLanguage == key,
                                onClick = { onLanguageChange(key) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanContainer,
                                    selectedLabelColor = CyanPulse
                                )
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Offline Voice Pitch: ${"%.2f".format(settings.voicePitch)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Slider(
                            value = settings.voicePitch,
                            onValueChange = onPitchChange,
                            valueRange = 0.90f..1.40f,
                            colors = SliderDefaults.colors(
                                thumbColor = VioletAura,
                                activeTrackColor = VioletAura
                            )
                        )
                    }

                    Column {
                        Text(
                            text = "Offline Speaking Speed: ${"%.2f".format(settings.voiceSpeed)}x",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Slider(
                            value = settings.voiceSpeed,
                            onValueChange = onSpeedChange,
                            valueRange = 0.75f..1.35f,
                            colors = SliderDefaults.colors(
                                thumbColor = CyanPulse,
                                activeTrackColor = CyanPulse
                            )
                        )
                    }

                    if (availableVoices.isNotEmpty()) {
                        Text(
                            text = "ডিভাইসের ইনস্টলড অফলাইন ভয়েস:",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            availableVoices.take(4).forEach { voice ->
                                val selected = settings.selectedVoiceName == voice.name
                                Surface(
                                    color = if (selected) CyanContainer else ObsidianBg,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectVoice(voice.name) }
                                ) {
                                    Text(
                                        text = voice.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected) CyanPulse else TextPrimary,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!hasInstalledBengaliTts) {
                        OutlinedButton(
                            onClick = onInstallTtsData,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, tint = CyanPulse)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "অফলাইন বাংলা TTS ভয়েস ডাউনলোড করুন",
                                color = CyanPulse
                            )
                        }
                    }

                    Button(
                        onClick = onTestFemaleVoice,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VioletAura,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_female_voice_button")
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("টেস্ট ভয়েস (Test Female Voice)")
                    }
                }
            }
        }

        // Permissions & Accessibility Access Checklist
        item {
            Text(
                text = "ফোন এক্সেস ও পারমিশন (Phone Access)",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PermissionRowCard(
                    title = "Microphone (ভয়েস ও ব্যাকগ্রাউন্ড ডাক)",
                    granted = permissions.hasMicrophone,
                    icon = Icons.Rounded.Mic,
                    onGrantClick = onRequestMic,
                    testTag = "perm_mic_card"
                )
                PermissionRowCard(
                    title = "Contacts (নাম ধরে কল, মেসেজ ও ইমেল)",
                    granted = permissions.hasContacts,
                    icon = Icons.Rounded.Contacts,
                    onGrantClick = onRequestContacts,
                    testTag = "perm_contacts_card"
                )
                PermissionRowCard(
                    title = "Phone Call (সরাসরি কল করার অনুমতি)",
                    granted = permissions.hasCallPhone,
                    icon = Icons.Rounded.Call,
                    onGrantClick = onRequestCallPhone,
                    testTag = "perm_call_card"
                )
                PermissionRowCard(
                    title = "Notifications (ব্যাকগ্রাউন্ড সার্ভিসের জন্য)",
                    granted = permissions.hasNotifications,
                    icon = Icons.Rounded.Notifications,
                    onGrantClick = onRequestNotifications,
                    testTag = "perm_notif_card"
                )
                PermissionRowCard(
                    title = "Accessibility Service (অ্যাপ কন্ট্রোল ও হোম/ব্যাক)",
                    granted = permissions.hasAccessibility,
                    icon = Icons.Rounded.AccessibilityNew,
                    onGrantClick = onOpenAccessibilitySettings,
                    actionLabel = if (permissions.hasAccessibility) "Active" else "Open Settings",
                    testTag = "perm_accessibility_card"
                )
            }
        }
    }
}

@Composable
private fun PermissionRowCard(
    title: String,
    granted: Boolean,
    icon: ImageVector,
    onGrantClick: () -> Unit,
    actionLabel: String? = null,
    testTag: String
) {
    Surface(
        color = GlassCardBg,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (granted) EmeraldReady.copy(alpha = 0.35f) else GlassBorder,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onGrantClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) EmeraldReady else RoseListening
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (granted) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Granted",
                    tint = EmeraldReady
                )
            } else {
                Surface(
                    color = RoseListening.copy(alpha = 0.2f),
                    shape = CircleShape
                ) {
                    Text(
                        text = actionLabel ?: "Grant",
                        style = MaterialTheme.typography.labelLarge,
                        color = RoseListening,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
