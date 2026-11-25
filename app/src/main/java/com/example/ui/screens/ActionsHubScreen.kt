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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.device.InstalledAppInfo
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassCardBg
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAura

@Composable
fun ActionsHubScreen(
    installedApps: List<InstalledAppInfo>,
    torchOn: Boolean,
    hasAccessibility: Boolean,
    onToggleFlashlight: () -> Unit,
    onTellTime: () -> Unit,
    onSystemAction: (String) -> Unit,
    onQuickVoiceCommand: (String) -> Unit,
    onLaunchApp: (InstalledAppInfo) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "ডিভাইস ও অ্যাপ কন্ট্রোল",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "কল, ইমেল, মেসেজ এবং ফোনের যেকোনো অ্যাপ নিয়ন্ত্রণ করুন",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Quick Control Tiles
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlTile(
                        title = "সময় বলো",
                        subtitle = "Speak Time",
                        icon = Icons.Rounded.Schedule,
                        accent = CyanPulse,
                        onClick = onTellTime,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_tell_time")
                    )
                    ControlTile(
                        title = if (torchOn) "Flash ON" else "Flash OFF",
                        subtitle = "Torchlight",
                        icon = if (torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                        accent = if (torchOn) AmberWarning else TextSecondary,
                        onClick = onToggleFlashlight,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_flashlight")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlTile(
                        title = "কল করুন",
                        subtitle = "Phone Call",
                        icon = Icons.Rounded.Call,
                        accent = EmeraldReady,
                        onClick = { onQuickVoiceCommand("ডায়ালার খোলো") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_call")
                    )
                    ControlTile(
                        title = "মেসেজ",
                        subtitle = "Draft SMS",
                        icon = Icons.Rounded.Sms,
                        accent = VioletAura,
                        onClick = { onQuickVoiceCommand("একটা নতুন মেসেজ ড্রাফট করো") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_sms")
                    )
                    ControlTile(
                        title = "ইমেল",
                        subtitle = "Compose Email",
                        icon = Icons.Rounded.Email,
                        accent = CyanPulse,
                        onClick = { onQuickVoiceCommand("একটি নতুন ইমেল ড্রাফট করো") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_email")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlTile(
                        title = "Vol +",
                        subtitle = "Raise",
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        accent = CyanPulse,
                        onClick = { onSystemAction("VOL_UP") },
                        modifier = Modifier.weight(1f)
                    )
                    ControlTile(
                        title = "Wi-Fi",
                        subtitle = "Panel",
                        icon = Icons.Rounded.Wifi,
                        accent = EmeraldReady,
                        onClick = { onSystemAction("WIFI") },
                        modifier = Modifier.weight(1f)
                    )
                    ControlTile(
                        title = "Settings",
                        subtitle = "System",
                        icon = Icons.Rounded.Settings,
                        accent = TextSecondary,
                        onClick = { onSystemAction("SETTINGS") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Accessibility Global Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlTile(
                        title = "Home",
                        subtitle = if (hasAccessibility) "Ready" else "Needs Access",
                        icon = Icons.Rounded.Home,
                        accent = VioletAura,
                        onClick = { onSystemAction("GO_HOME") },
                        modifier = Modifier.weight(1f)
                    )
                    ControlTile(
                        title = "Notify",
                        subtitle = "Shade",
                        icon = Icons.Rounded.Notifications,
                        accent = CyanPulse,
                        onClick = { onSystemAction("OPEN_NOTIFICATIONS") },
                        modifier = Modifier.weight(1f)
                    )
                    ControlTile(
                        title = "Capture",
                        subtitle = "Screenshot",
                        icon = Icons.Rounded.Screenshot,
                        accent = EmeraldReady,
                        onClick = { onSystemAction("TAKE_SCREENSHOT") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Apps, contentDescription = null, tint = CyanPulse)
                Text(
                    text = "ইনস্টল করা অ্যাপস (${filteredApps.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("অ্যাপ খুঁজুন (Search apps)...", color = TextMuted) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPulse,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_apps_field")
            )
        }

        items(filteredApps, key = { it.packageName }) { app ->
            Surface(
                color = GlassCardBg,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLaunchApp(app) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onOpenAppInfo(app.packageName) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "App info / Force close ${app.label}",
                                tint = TextSecondary
                            )
                        }
                        IconButton(
                            onClick = { onLaunchApp(app) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = "Open ${app.label}",
                                tint = CyanPulse
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = GlassCardBg,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = CyanContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}
