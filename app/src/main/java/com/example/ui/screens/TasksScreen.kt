package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Project
import com.example.data.model.SprintTask
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.viewmodel.RoleCapabilities

private enum class TaskFilter(val label: String) {
    ALL("All"),
    TODO("To Do"),
    IN_PROGRESS("Doing"),
    MILESTONES("Milestones"),
    DONE("Done")
}

@Composable
fun TasksScreen(
    project: Project?,
    tasks: List<SprintTask>,
    capabilities: RoleCapabilities,
    formatDeadline: (Long) -> String,
    onUpdateStatus: (SprintTask, TaskStatus) -> Unit,
    onToggleSubstep: (SprintTask, String) -> Unit,
    onPinFocus: (SprintTask) -> Unit,
    onDeleteTask: (SprintTask) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(TaskFilter.ALL) }
    var expandedTaskId by remember { mutableStateOf<String?>(null) }

    if (project == null) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Create or join a project from Focus to manage sprint tasks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val filtered = remember(tasks, selectedFilter) {
        when (selectedFilter) {
            TaskFilter.ALL -> tasks
            TaskFilter.TODO -> tasks.filter { it.taskStatus == TaskStatus.TODO }
            TaskFilter.IN_PROGRESS -> tasks.filter { it.taskStatus == TaskStatus.IN_PROGRESS }
            TaskFilter.MILESTONES -> tasks.filter { it.milestone }
            TaskFilter.DONE -> tasks.filter { it.taskStatus == TaskStatus.DONE }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sprint Tasks",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        if (!capabilities.canEditContent) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text("View only", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TaskFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.label) },
                                modifier = Modifier.testTag("task_filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Nothing in ${selectedFilter.label}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Tap + to break down a small, achievable step for your team.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { task ->
                    TaskItemCard(
                        task = task,
                        expanded = expandedTaskId == task.id,
                        deadlineLabel = formatDeadline(task.dueDateMs),
                        canEdit = capabilities.canEditContent,
                        onToggleExpand = {
                            expandedTaskId = if (expandedTaskId == task.id) null else task.id
                        },
                        onCycleStatus = {
                            val next = when (task.taskStatus) {
                                TaskStatus.TODO -> TaskStatus.IN_PROGRESS
                                TaskStatus.IN_PROGRESS -> TaskStatus.DONE
                                TaskStatus.DONE -> TaskStatus.TODO
                            }
                            onUpdateStatus(task, next)
                        },
                        onToggleSubstep = { step -> onToggleSubstep(task, step) },
                        onPinFocus = { onPinFocus(task) },
                        onDelete = { onDeleteTask(task) }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun TaskItemCard(
    task: SprintTask,
    expanded: Boolean,
    deadlineLabel: String,
    canEdit: Boolean,
    onToggleExpand: () -> Unit,
    onCycleStatus: () -> Unit,
    onToggleSubstep: (String) -> Unit,
    onPinFocus: () -> Unit,
    onDelete: () -> Unit
) {
    val isDone = task.taskStatus == TaskStatus.DONE
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (task.focused && !isDone) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onToggleExpand() }
            .testTag("task_item_card")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onCycleStatus,
                    enabled = canEdit,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    val icon = when (task.taskStatus) {
                        TaskStatus.DONE -> Icons.Filled.CheckCircle
                        TaskStatus.IN_PROGRESS -> Icons.Outlined.CheckCircleOutline
                        TaskStatus.TODO -> Icons.Outlined.RadioButtonUnchecked
                    }
                    val tint = when (task.taskStatus) {
                        TaskStatus.DONE -> MaterialTheme.colorScheme.primary
                        TaskStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
                        TaskStatus.TODO -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(icon, contentDescription = "Cycle status for ${task.title}", tint = tint)
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (task.milestone) {
                            Icon(
                                imageVector = Icons.Filled.Flag,
                                contentDescription = "Milestone",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${task.taskStatus.label} · ${task.estimateMinutes}m · $deadlineLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (task.githubIssueNumber > 0L) {
                            Text(
                                text = "GH #${task.githubIssueNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                if (canEdit && !isDone) {
                    IconButton(
                        onClick = onPinFocus,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CenterFocusStrong,
                            contentDescription = "Pin to Focus Now",
                            tint = if (task.focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (task.description.isNotBlank()) {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (task.assigneeName.isNotBlank()) {
                        Text(
                            text = "Assigned to ${task.assigneeName} · Priority: ${task.taskPriority.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (task.substeps.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            task.substeps.forEach { step ->
                                val checked = step in task.completedSubsteps
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = canEdit) { onToggleSubstep(step) }
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = if (canEdit) { { onToggleSubstep(step) } } else null
                                    )
                                    Text(
                                        text = step,
                                        style = MaterialTheme.typography.bodySmall,
                                        textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                }
                            }
                        }
                    }
                    if (canEdit) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDelete) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Remove", modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateTaskDialog(
    project: Project,
    currentUserId: String,
    currentUserName: String,
    onDismiss: () -> Unit,
    onCreate: (
        title: String,
        description: String,
        priority: TaskPriority,
        assigneeUid: String,
        assigneeName: String,
        daysUntilDue: Int,
        estimateMinutes: Int,
        milestone: Boolean,
        focused: Boolean,
        substepsRaw: String
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }
    var estimateMinutes by remember { mutableStateOf(25) }
    var daysUntilDueText by remember { mutableStateOf("2") }
    var milestone by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var substepsRaw by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Sprint Step", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    label = { Text("Task title") },
                    placeholder = { Text("Keep it small & specific") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("task_title_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(1000) },
                    label = { Text("Notes (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = substepsRaw,
                    onValueChange = { substepsRaw = it.take(600) },
                    label = { Text("Micro-steps (one per line)") },
                    placeholder = { Text("Sketch wireframe\nWire repository\nVerify offline") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Effort block (ADHD-friendly)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 25, 45, 60).forEach { mins ->
                        FilterChip(
                            selected = estimateMinutes == mins,
                            onClick = { estimateMinutes = mins },
                            label = { Text("${mins}m") }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = daysUntilDueText,
                    onValueChange = { daysUntilDueText = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("Due in days") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mark as Milestone", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Triggers automated team email on completion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = milestone,
                        onCheckedChange = { milestone = it },
                        modifier = Modifier.testTag("milestone_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pin to Focus Now", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = focused, onCheckedChange = { focused = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = daysUntilDueText.toIntOrNull()?.coerceIn(1, 365) ?: 2
                    onCreate(
                        title,
                        description,
                        priority,
                        currentUserId,
                        project.memberNames[currentUserId] ?: currentUserName,
                        days,
                        estimateMinutes,
                        milestone,
                        focused,
                        substepsRaw
                    )
                    onDismiss()
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_task_button")
            ) {
                Text("Add Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
