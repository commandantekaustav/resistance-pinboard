package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.data.model.LocalSettings
import com.example.data.model.Project
import com.example.data.model.ThemeMode
import com.example.data.model.WidgetConfig
import com.example.data.model.WidgetStyle
import com.example.data.model.WidgetTheme
import com.example.notifications.AdhdReminderScheduler
import com.example.notifications.MilestoneNotifier
import com.example.widget.ClearSprintWidgetProvider

@Composable
fun FocusAndWidgetPanel(
    project: Project?,
    settings: LocalSettings,
    focusTaskTitle: String,
    sprintProgressPct: Int,
    deadlineLabel: String,
    onThemeModeChange: (ThemeMode) -> Unit,
    onFocusDurationChange: (Int) -> Unit,
    onReminderIntervalChange: (Int) -> Unit,
    onUpdateWidgetConfig: (WidgetConfig) -> Unit,
    onSendMilestoneDigest: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            AdhdReminderScheduler.postFocusNudgeNow(context, focusTaskTitle)
            onShowMessage("Gentle focus nudge sent!")
        } else {
            onShowMessage("Notification permission denied — reminders remain quiet.")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Dark Mode & Visual Comfort
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Appearance & Dark Mode", style = MaterialTheme.typography.titleMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // 2. ADHD-Friendly Reminders & Focus Blocks
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("ADHD Focus & Gentle Reminders", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "Encouraging check-ins without harsh alarms. Choose your focus sprint block:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 45).forEach { mins ->
                        FilterChip(
                            selected = settings.focusDurationMinutes == mins,
                            onClick = { onFocusDurationChange(mins) },
                            label = { Text("${mins}m block") }
                        )
                    }
                }
                Text("Gentle check-in cadence", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Off", 30 to "30m", 60 to "1h", 120 to "2h").forEach { (mins, label) ->
                        FilterChip(
                            selected = settings.reminderIntervalMinutes == mins,
                            onClick = {
                                if (mins > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !AdhdReminderScheduler.hasNotificationPermission(context)
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                onReminderIntervalChange(mins)
                            },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !AdhdReminderScheduler.hasNotificationPermission(context)
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            AdhdReminderScheduler.postFocusNudgeNow(context, focusTaskTitle)
                            onShowMessage("Sent a gentle focus check-in notification.")
                        }
                    },
                    modifier = Modifier.testTag("test_adhd_nudge_button")
                ) {
                    Text("Send Gentle Nudge Now")
                }
            }
        }

        // 3. Automated Milestone Email Notifications (Zero-Cost)
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.MailOutline, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text("Milestone Email Automation (Free)", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "Completing any Milestone automatically prepares a team email digest (via mailto:) and optionally posts to your free Google Apps Script webhook.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (project != null) {
                    Text(
                        text = "Recipients: ${project.milestoneEmails.ifEmpty { listOf("None configured — edit in Workspace") }.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (project.webhookUrl.startsWith("https://")) "Webhook: Connected" else "Webhook: Optional (using instant email compose)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = onSendMilestoneDigest,
                        enabled = project != null,
                        modifier = Modifier.testTag("preview_milestone_email_button")
                    ) {
                        Text("Send Sprint Digest")
                    }
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(MilestoneNotifier.FREE_APPS_SCRIPT_TEMPLATE))
                            onShowMessage("Copied free Google Apps Script email relay code.")
                        }
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Script")
                    }
                }
            }
        }

        // 4. Customizable Home Screen Widget + Live Preview
        val widgetCfg = settings.widgetConfig
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Home Screen Widget", style = MaterialTheme.typography.titleMedium)
                }

                // Live Preview of Home Screen Widget
                WidgetLivePreviewCard(
                    config = widgetCfg,
                    projectName = project?.name ?: "ClearSprint",
                    focusTitle = focusTaskTitle,
                    progressPct = sprintProgressPct,
                    deadlineLabel = deadlineLabel
                )

                Text("Widget Layout", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetStyle.entries.forEach { st ->
                        FilterChip(
                            selected = widgetCfg.style == st,
                            onClick = { onUpdateWidgetConfig(widgetCfg.copy(style = st)) },
                            label = { Text(st.label) }
                        )
                    }
                }

                Text("Widget Theme", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetTheme.entries.forEach { th ->
                        FilterChip(
                            selected = widgetCfg.theme == th,
                            onClick = { onUpdateWidgetConfig(widgetCfg.copy(theme = th)) },
                            label = { Text(th.label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show sprint progress bar", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = widgetCfg.showProgressBar,
                        onCheckedChange = { onUpdateWidgetConfig(widgetCfg.copy(showProgressBar = it)) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show deadline badge", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = widgetCfg.showDeadline,
                        onCheckedChange = { onUpdateWidgetConfig(widgetCfg.copy(showDeadline = it)) }
                    )
                }

                Button(
                    onClick = {
                        val pinned = ClearSprintWidgetProvider.requestPinWidget(context)
                        onShowMessage(
                            if (pinned) "Pin widget prompt opened!"
                            else "Add ClearSprint from your Android launcher's Widgets menu."
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("pin_home_widget_button")
                ) {
                    Text("Pin Widget to Home Screen")
                }
            }
        }
    }
}

@Composable
private fun WidgetLivePreviewCard(
    config: WidgetConfig,
    projectName: String,
    focusTitle: String,
    progressPct: Int,
    deadlineLabel: String
) {
    val bgColor = when (config.theme) {
        WidgetTheme.DARK -> Color(0xFF151B20)
        WidgetTheme.LIGHT -> Color(0xFFFAF9F5)
        WidgetTheme.SAGE -> Color(0xFF132B28)
    }
    val borderColor = when (config.theme) {
        WidgetTheme.DARK -> Color(0xFF26313A)
        WidgetTheme.LIGHT -> Color(0xFFE2DFD6)
        WidgetTheme.SAGE -> Color(0xFF234F4A)
    }
    val accentColor = when (config.theme) {
        WidgetTheme.DARK -> Color(0xFF5ED4C2)
        WidgetTheme.LIGHT -> Color(0xFF1A7368)
        WidgetTheme.SAGE -> Color(0xFF8CF0E0)
    }
    val textColor = when (config.theme) {
        WidgetTheme.LIGHT -> Color(0xFF1B1F23)
        else -> Color(0xFFF3F5F7)
    }
    val mutedColor = when (config.theme) {
        WidgetTheme.LIGHT -> Color(0xFF5A636C)
        else -> Color(0xFFA2B0BA)
    }
    val deadlineColor = if (config.theme == WidgetTheme.LIGHT) Color(0xFFB57227) else Color(0xFFF0B96B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = projectName, style = MaterialTheme.typography.labelLarge, color = accentColor)
            if (config.showDeadline) {
                Text(text = deadlineLabel, style = MaterialTheme.typography.labelSmall, color = deadlineColor)
            }
        }
        Text(
            text = if (config.style == WidgetStyle.DEADLINES) "Next Milestone: $deadlineLabel" else focusTitle,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            maxLines = 2
        )
        if (config.style == WidgetStyle.SPRINT_SNAPSHOT) {
            Text(
                text = "• Live synced from Firebase & offline cache",
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor
            )
        }
        if (config.showProgressBar) {
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { (progressPct / 100f).coerceIn(0f, 1f) },
                    color = accentColor,
                    trackColor = borderColor,
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(4.dp))
                )
                Text(text = "$progressPct%", style = MaterialTheme.typography.labelSmall, color = mutedColor)
            }
        }
    }
}
