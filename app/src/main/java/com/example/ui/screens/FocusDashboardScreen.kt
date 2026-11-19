package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Project
import com.example.data.model.SprintTask
import com.example.data.model.TaskStatus
import com.example.domain.SprintForecast
import com.example.ui.components.SprintForecastCard
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.FocusTimerState
import com.example.viewmodel.RoleCapabilities

@Composable
fun FocusDashboardScreen(
    project: Project?,
    tasks: List<SprintTask>,
    forecast: SprintForecast,
    capabilities: RoleCapabilities,
    calmModeEnabled: Boolean,
    timerState: FocusTimerState,
    formatDeadline: (Long) -> String,
    onToggleCalmMode: (Boolean) -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onCompleteTask: (SprintTask) -> Unit,
    onToggleSubstep: (SprintTask, String) -> Unit,
    onPinFocusTask: (SprintTask) -> Unit,
    onCreateProjectClick: () -> Unit,
    onJoinProjectClick: () -> Unit,
    onQuickAddTaskClick: () -> Unit
) {
    if (project == null) {
        EmptyWorkspaceHero(
            onCreateProjectClick = onCreateProjectClick,
            onJoinProjectClick = onJoinProjectClick
        )
        return
    }

    val openTasks = tasks.filter { it.taskStatus != TaskStatus.DONE }
    val doneCount = tasks.count { it.taskStatus == TaskStatus.DONE }
    val milestoneDone = tasks.count { it.milestone && it.taskStatus == TaskStatus.DONE }
    val milestoneTotal = tasks.count { it.milestone }
    val progressFraction = if (tasks.isEmpty()) 0f else doneCount.toFloat() / tasks.size.toFloat()
    val focusTask = openTasks.firstOrNull { it.focused } ?: openTasks.firstOrNull()
    val upcomingTasks = openTasks.filter { it.id != focusTask?.id }.take(if (calmModeEnabled) 2 else 5)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Minimalist Header + Calm Mode toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = project.sprintName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    FilterChip(
                        selected = calmModeEnabled,
                        onClick = { onToggleCalmMode(!calmModeEnabled) },
                        label = { Text("Calm") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.SelfImprovement,
                                contentDescription = "Toggle Calm Mode",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("calm_mode_toggle")
                    )
                }
            }

            // 1. ADHD Focus Now Hero Card (Always prioritized first!)
            item {
                FocusNowHeroCard(
                    focusTask = focusTask,
                    canEdit = capabilities.canEditContent,
                    timerState = timerState,
                    formatDeadline = formatDeadline,
                    onToggleTimer = onToggleTimer,
                    onResetTimer = onResetTimer,
                    onCompleteTask = onCompleteTask,
                    onToggleSubstep = onToggleSubstep,
                    onQuickAddTaskClick = onQuickAddTaskClick
                )
            }

            // 2. Sprint Progress, Forecast & Delay Estimator (hidden in Calm Mode to minimize stimulus)
            item {
                AnimatedVisibility(visible = !calmModeEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SprintProgressCard(
                            progress = progressFraction,
                            doneCount = doneCount,
                            totalCount = tasks.size,
                            milestoneDone = milestoneDone,
                            milestoneTotal = milestoneTotal,
                            deadlineLabel = formatDeadline(project.sprintDeadlineMs),
                            roleLabel = capabilities.role.label
                        )
                        SprintForecastCard(
                            forecast = forecast,
                            canEdit = capabilities.canEditContent,
                            onPinFocusTask = onPinFocusTask
                        )
                    }
                }
            }

            // 3. Up Next / Deadlines (strictly capped to reduce overwhelm)
            if (upcomingTasks.isNotEmpty()) {
                item {
                    Text(
                        text = if (calmModeEnabled) "NEXT TWO STEPS" else "UPCOMING DEADLINES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(upcomingTasks, key = { it.id }) { task ->
                    UpcomingTaskRow(
                        task = task,
                        deadlineText = formatDeadline(task.dueDateMs),
                        canEdit = capabilities.canEditContent,
                        onPinFocus = { onPinFocusTask(task) },
                        onMarkDone = { onCompleteTask(task) }
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun FocusNowHeroCard(
    focusTask: SprintTask?,
    canEdit: Boolean,
    timerState: FocusTimerState,
    formatDeadline: (Long) -> String,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onCompleteTask: (SprintTask) -> Unit,
    onToggleSubstep: (SprintTask, String) -> Unit,
    onQuickAddTaskClick: () -> Unit
) {
    val animatedTimerProgress by animateFloatAsState(
        targetValue = timerState.progress,
        animationSpec = spring(),
        label = "timerProgress"
    )
    val mins = timerState.remainingSeconds / 60
    val secs = timerState.remainingSeconds % 60
    val formattedClock = String.format("%02d:%02d", mins, secs)

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "FOCUS NOW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                if (focusTask != null) {
                    Text(
                        text = "${focusTask.estimateMinutes}m · ${formatDeadline(focusTask.dueDateMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (focusTask == null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Clear slate — nothing urgent.",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Add one small, concrete step to start your next Buildathon focus block.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (canEdit) {
                        Button(
                            onClick = onQuickAddTaskClick,
                            modifier = Modifier.testTag("empty_focus_add_task_button")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add first focus task")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (focusTask.milestone) {
                                Icon(
                                    imageVector = Icons.Filled.Flag,
                                    contentDescription = "Milestone",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = focusTask.title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (focusTask.description.isNotBlank()) {
                            Text(
                                text = focusTask.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Minimalist circular breathing focus timer
                    val ringTrack = MaterialTheme.colorScheme.surfaceVariant
                    val ringActive = MaterialTheme.colorScheme.primary
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(84.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 6.dp.toPx()
                            val diameter = size.minDimension - strokeWidth
                            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                            drawArc(
                                color = ringTrack,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = Size(diameter, diameter),
                                style = Stroke(width = strokeWidth)
                            )
                            drawArc(
                                color = ringActive,
                                startAngle = -90f,
                                sweepAngle = (animatedTimerProgress * 360f).coerceIn(0f, 360f),
                                useCenter = false,
                                topLeft = topLeft,
                                size = Size(diameter, diameter),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = formattedClock,
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // ADHD-friendly chunked micro-steps
                if (focusTask.substeps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        focusTask.substeps.take(4).forEach { step ->
                            val isChecked = step in focusTask.completedSubsteps
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable(enabled = canEdit) { onToggleSubstep(focusTask, step) }
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = if (canEdit) {
                                        { onToggleSubstep(focusTask, step) }
                                    } else null
                                )
                                Text(
                                    text = step,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (isChecked) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Timer + Complete controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onToggleTimer,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("focus_timer_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (timerState.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (timerState.running) "Pause timer" else "Start focus timer",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (timerState.running) "Pause" else "Start Focus")
                    }
                    FilledTonalIconButton(
                        onClick = onResetTimer,
                        modifier = Modifier.size(48.dp).testTag("focus_timer_reset_button")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Reset focus timer")
                    }
                    if (canEdit) {
                        FilledTonalButton(
                            onClick = { onCompleteTask(focusTask) },
                            modifier = Modifier.height(48.dp).testTag("complete_focus_task_button")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SprintProgressCard(
    progress: Float,
    doneCount: Int,
    totalCount: Int,
    milestoneDone: Int,
    milestoneTotal: Int,
    deadlineLabel: String,
    roleLabel: String
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(),
        label = "sprintArc"
    )
    val pct = (progress * 100).toInt()
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(74.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 7.dp.toPx()
                    val diameter = size.minDimension - stroke
                    val offset = Offset(stroke / 2f, stroke / 2f)
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = offset,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke)
                    )
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f,
                        sweepAngle = (animatedProgress * 360f).coerceIn(0f, 360f),
                        useCenter = false,
                        topLeft = offset,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Sprint Progress",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "$doneCount of $totalCount tasks completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = deadlineLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = "$milestoneDone/$milestoneTotal milestones",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Role: $roleLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun UpcomingTaskRow(
    task: SprintTask,
    deadlineText: String,
    canEdit: Boolean,
    onPinFocus: () -> Unit,
    onMarkDone: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = canEdit) { onPinFocus() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onMarkDone,
                enabled = canEdit,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircleOutline,
                    contentDescription = "Complete ${task.title}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
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
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${task.estimateMinutes}m · $deadlineText" +
                        if (task.assigneeName.isNotBlank()) " · ${task.assigneeName}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canEdit) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "Focus",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyWorkspaceHero(
    onCreateProjectClick: () -> Unit,
    onJoinProjectClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 400.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.SelfImprovement,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Text(
                text = "Start a calm sprint",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Create your Google Buildathon project or enter a teammate's invite code to sync in real time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onCreateProjectClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("empty_create_project_button")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create Buildathon Project")
            }
            OutlinedButton(
                onClick = onJoinProjectClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("empty_join_project_button")
            ) {
                Icon(Icons.Outlined.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Join with Invite Code")
            }
        }
    }
}
