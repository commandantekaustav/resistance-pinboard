package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.MilestoneEmailDraft
import com.example.data.model.Project

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        description: String,
        sprintName: String,
        daysUntilDeadline: Int,
        githubOwner: String,
        githubRepo: String,
        milestoneEmails: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var sprintName by remember { mutableStateOf("Sprint 1 — Buildathon MVP") }
    var daysText by remember { mutableStateOf("7") }
    var githubOwner by remember { mutableStateOf("") }
    var githubRepo by remember { mutableStateOf("") }
    var emails by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Team Project",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("Project name") },
                    placeholder = { Text("Google Buildathon App") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("project_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(400) },
                    label = { Text("Calm goal (optional)") },
                    placeholder = { Text("One-sentence mission to stay focused") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = sprintName,
                        onValueChange = { sprintName = it.take(60) },
                        label = { Text("Sprint") },
                        singleLine = true,
                        modifier = Modifier.weight(1.6f)
                    )
                    OutlinedTextField(
                        value = daysText,
                        onValueChange = { daysText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Days") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = githubOwner,
                        onValueChange = { githubOwner = it.take(80) },
                        label = { Text("GitHub owner") },
                        placeholder = { Text("org or user") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = githubRepo,
                        onValueChange = { githubRepo = it.take(100) },
                        label = { Text("GitHub repo") },
                        placeholder = { Text("repo-name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = emails,
                    onValueChange = { emails = it.take(300) },
                    label = { Text("Milestone emails (comma separated)") },
                    placeholder = { Text("lead@team.com, dev@team.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = daysText.toIntOrNull()?.coerceIn(1, 365) ?: 7
                    onCreate(name, description, sprintName, days, githubOwner, githubRepo, emails)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_project_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun JoinProjectDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Join Team Workspace", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter the 6-character invite code shared by your project owner.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(12) },
                    label = { Text("Invite code") },
                    placeholder = { Text("e.g. K7M4P9") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("join_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onJoin(code)
                    onDismiss()
                },
                enabled = code.length >= 6,
                modifier = Modifier.testTag("confirm_join_project_button")
            ) {
                Text("Join")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditProjectSettingsDialog(
    project: Project,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        description: String,
        sprintName: String,
        sprintDeadlineMs: Long,
        githubOwner: String,
        githubRepo: String,
        webhookUrl: String,
        milestoneEmailsRaw: String
    ) -> Unit
) {
    var name by remember(project.id) { mutableStateOf(project.name) }
    var description by remember(project.id) { mutableStateOf(project.description) }
    var sprintName by remember(project.id) { mutableStateOf(project.sprintName) }
    val initialDays = remember(project.id) {
        val diff = ((project.sprintDeadlineMs - System.currentTimeMillis()) / 86_400_000L).toInt()
        diff.coerceIn(1, 365).toString()
    }
    var daysText by remember(project.id) { mutableStateOf(initialDays) }
    var githubOwner by remember(project.id) { mutableStateOf(project.githubOwner) }
    var githubRepo by remember(project.id) { mutableStateOf(project.githubRepo) }
    var webhookUrl by remember(project.id) { mutableStateOf(project.webhookUrl) }
    var emails by remember(project.id) { mutableStateOf(project.milestoneEmails.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Workspace Settings", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("Project name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(400) },
                    label = { Text("Description") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = sprintName,
                        onValueChange = { sprintName = it.take(60) },
                        label = { Text("Sprint name") },
                        singleLine = true,
                        modifier = Modifier.weight(1.6f)
                    )
                    OutlinedTextField(
                        value = daysText,
                        onValueChange = { daysText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Days left") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = githubOwner,
                        onValueChange = { githubOwner = it.take(80) },
                        label = { Text("GitHub owner") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = githubRepo,
                        onValueChange = { githubRepo = it.take(100) },
                        label = { Text("GitHub repo") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = emails,
                    onValueChange = { emails = it.take(300) },
                    label = { Text("Milestone email recipients") },
                    placeholder = { Text("teammate@gmail.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = webhookUrl,
                    onValueChange = { webhookUrl = it.take(300) },
                    label = { Text("Free Apps Script Email Webhook URL") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = daysText.toIntOrNull()?.coerceIn(1, 365) ?: 7
                    val deadlineMs = System.currentTimeMillis() + days * 86_400_000L
                    onSave(name, description, sprintName, deadlineMs, githubOwner, githubRepo, webhookUrl, emails)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_project_settings_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MilestoneEmailDialog(
    draft: MilestoneEmailDraft,
    onSendViaEmailApp: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Milestone Reached", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = draft.milestoneTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (draft.webhookStatusNote != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (draft.webhookDispatched) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = draft.webhookStatusNote,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Text(
                    text = "Recipients: ${draft.recipients.ifEmpty { listOf("Add team emails in Settings") }.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = draft.body.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSendViaEmailApp()
                    onDismiss()
                },
                modifier = Modifier.testTag("send_milestone_email_button")
            ) {
                Text("Send Email")
            }
        },
        dismissButton = {
            FilledTonalButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
