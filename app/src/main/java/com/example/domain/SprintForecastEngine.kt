package com.example.domain

import com.example.data.model.Project
import com.example.data.model.SprintTask
import com.example.data.model.TaskStatus
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

enum class ForecastStatus(val label: String) {
    NO_DATA("No tasks yet"),
    COMPLETED("Sprint complete"),
    WARMING_UP("Calibrating pace"),
    ON_TRACK("On track"),
    TIGHT_PACE("Tight pace"),
    DELAY_RISK("Delay risk")
}

data class AtRiskTaskItem(
    val task: SprintTask,
    val reason: String
)

data class SprintForecast(
    val status: ForecastStatus = ForecastStatus.NO_DATA,
    val statusBadge: String = "No tasks yet",
    val completedMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val remainingMinutes: Int = 0,
    val remainingFocusBlocks: Int = 0,
    val focusBlockMinutes: Int = 25,
    val requiredBlocksPerDay: Double = 0.0,
    val velocityMinutesPerDay: Double = 0.0,
    val velocityTasksPerDay: Double = 0.0,
    val projectedFinishMs: Long? = null,
    val estimatedDelayDays: Double = 0.0,
    val idealProgressFraction: Float = 0f,
    val actualProgressFraction: Float = 0f,
    val burnUpPoints: List<Float> = listOf(0f, 0f),
    val atRiskTasks: List<AtRiskTaskItem> = emptyList(),
    val calmSuggestion: String = "Add a few small tasks to unlock your sprint pace forecast."
)

object SprintForecastEngine {
    private const val DAY_MS = 86_400_000.0

    fun compute(
        project: Project?,
        tasks: List<SprintTask>,
        focusBlockMinutes: Int = 25,
        nowMs: Long = System.currentTimeMillis()
    ): SprintForecast {
        val safeBlockMin = focusBlockMinutes.coerceIn(5, 90)
        if (project == null || tasks.isEmpty()) {
            return SprintForecast(
                status = ForecastStatus.NO_DATA,
                statusBadge = "Add tasks",
                focusBlockMinutes = safeBlockMin,
                calmSuggestion = "Add a couple of sprint steps to estimate your finish date."
            )
        }

        val totalMinutes = tasks.sumOf { it.estimateMinutes.toInt().coerceAtLeast(5) }
        val completedMinutes = tasks.sumOf { task ->
            val est = task.estimateMinutes.toInt().coerceAtLeast(5)
            when (task.taskStatus) {
                TaskStatus.DONE -> est
                TaskStatus.IN_PROGRESS -> {
                    if (task.substeps.isNotEmpty()) {
                        val ratio = task.completedSubsteps.size.toDouble() / task.substeps.size.toDouble()
                        (est * ratio.coerceIn(0.0, 0.9)).roundToInt()
                    } else {
                        (est * 0.25).roundToInt()
                    }
                }
                TaskStatus.TODO -> 0
            }
        }
        val remainingMinutes = (totalMinutes - completedMinutes).coerceAtLeast(0)
        val remainingBlocks = if (remainingMinutes == 0) 0 else ceil(remainingMinutes.toDouble() / safeBlockMin).toInt()
        val doneCount = tasks.count { it.taskStatus == TaskStatus.DONE }
        val openTasks = tasks.filter { it.taskStatus != TaskStatus.DONE }

        val sprintStartMs = resolveSprintStartMs(project, tasks, nowMs)
        val elapsedDays = ((nowMs - sprintStartMs) / DAY_MS).coerceAtLeast(0.25)
        val hasDeadline = project.sprintDeadlineMs > 0L
        val daysLeft = if (hasDeadline) ((project.sprintDeadlineMs - nowMs) / DAY_MS) else 7.0
        val effectiveDaysLeft = daysLeft.coerceAtLeast(0.5)

        val actualProgress = if (totalMinutes > 0) {
            (completedMinutes.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val idealProgress = if (hasDeadline) {
            val span = (project.sprintDeadlineMs - sprintStartMs).coerceAtLeast(DAY_MS.toLong())
            ((nowMs - sprintStartMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        } else 0.5f

        val burnUpPoints = buildBurnUpTrajectory(tasks, sprintStartMs, nowMs, totalMinutes, actualProgress)
        val atRisk = findAtRiskTasks(openTasks, nowMs)
        val requiredBlocksPerDay = remainingBlocks / effectiveDaysLeft

        // All tasks done!
        if (openTasks.isEmpty()) {
            return SprintForecast(
                status = ForecastStatus.COMPLETED,
                statusBadge = "Complete",
                completedMinutes = totalMinutes,
                totalMinutes = totalMinutes,
                remainingMinutes = 0,
                remainingFocusBlocks = 0,
                focusBlockMinutes = safeBlockMin,
                requiredBlocksPerDay = 0.0,
                velocityMinutesPerDay = totalMinutes / elapsedDays,
                velocityTasksPerDay = doneCount / elapsedDays,
                projectedFinishMs = nowMs,
                estimatedDelayDays = 0.0,
                idealProgressFraction = idealProgress,
                actualProgressFraction = 1f,
                burnUpPoints = burnUpPoints,
                atRiskTasks = emptyList(),
                calmSuggestion = "Every task in this sprint is complete. Great team momentum!"
            )
        }

        val velocityMinPerDay = completedMinutes / elapsedDays
        val velocityTasksPerDay = doneCount / elapsedDays
        val teamCapacityMinPerDay = max(1, project.memberUids.size) * 90.0

        // Not enough completed history yet — calibrate gently using effort vs. remaining time
        if (doneCount == 0 && completedMinutes == 0) {
            val estDaysNeeded = remainingMinutes / teamCapacityMinPerDay
            val projectedMs = nowMs + (estDaysNeeded * DAY_MS).toLong()
            val delayDays = if (hasDeadline) (projectedMs - project.sprintDeadlineMs) / DAY_MS else 0.0
            val overdueCount = openTasks.count { it.dueDateMs in 1L until nowMs }

            val (status, badge, suggestion) = when {
                (hasDeadline && daysLeft < 0) || delayDays > 1.0 -> Triple(
                    ForecastStatus.DELAY_RISK,
                    "Est. +${ceil(max(1.0, delayDays)).toInt()}d delay",
                    "Start one small task today — ~${formatBlocks(requiredBlocksPerDay)} focus blocks/day will catch you up."
                )
                overdueCount > 0 || delayDays > -0.2 -> Triple(
                    ForecastStatus.TIGHT_PACE,
                    "Tight pace",
                    "Plan ~${formatBlocks(requiredBlocksPerDay)} focus blocks/day (${safeBlockMin}m each) to finish calmly on time."
                )
                else -> Triple(
                    ForecastStatus.WARMING_UP,
                    "Calibrating",
                    "Complete 1 task to lock in live velocity · ~${formatBlocks(requiredBlocksPerDay)} focus blocks/day needed."
                )
            }

            return SprintForecast(
                status = status,
                statusBadge = badge,
                completedMinutes = completedMinutes,
                totalMinutes = totalMinutes,
                remainingMinutes = remainingMinutes,
                remainingFocusBlocks = remainingBlocks,
                focusBlockMinutes = safeBlockMin,
                requiredBlocksPerDay = requiredBlocksPerDay,
                velocityMinutesPerDay = 0.0,
                velocityTasksPerDay = 0.0,
                projectedFinishMs = projectedMs,
                estimatedDelayDays = delayDays.coerceAtLeast(0.0),
                idealProgressFraction = idealProgress,
                actualProgressFraction = actualProgress,
                burnUpPoints = burnUpPoints,
                atRiskTasks = atRisk,
                calmSuggestion = suggestion
            )
        }

        // Live velocity projection
        val safeVelocity = velocityMinPerDay.coerceAtLeast(15.0)
        val projectedDaysToFinish = remainingMinutes / safeVelocity
        val projectedFinishMs = nowMs + (projectedDaysToFinish * DAY_MS).toLong()
        val delayDays = if (hasDeadline) {
            (projectedFinishMs - project.sprintDeadlineMs) / DAY_MS
        } else {
            0.0
        }

        val (status, badge, suggestion) = when {
            !hasDeadline || delayDays <= -0.25 -> Triple(
                ForecastStatus.ON_TRACK,
                "On track",
                "Steady pace — staying around ${formatBlocks(requiredBlocksPerDay)} focus blocks/day keeps you ahead."
            )
            delayDays <= 1.0 -> Triple(
                ForecastStatus.TIGHT_PACE,
                "Tight pace",
                "Right near the deadline. Adding 1 extra ${safeBlockMin}m block today creates breathing room."
            )
            else -> {
                val delayWholeDays = ceil(delayDays).toInt().coerceAtLeast(1)
                val firstRisk = atRisk.firstOrNull()?.task?.title
                val tip = if (firstRisk != null) {
                    "Pin \"$firstRisk\" to Focus Now — ~${formatBlocks(requiredBlocksPerDay)} blocks/day closes the gap."
                } else {
                    "Aim for ~${formatBlocks(requiredBlocksPerDay)} focus blocks/day or trim scope on lower-priority tasks."
                }
                Triple(
                    ForecastStatus.DELAY_RISK,
                    "Est. +${delayWholeDays}d delay",
                    tip
                )
            }
        }

        return SprintForecast(
            status = status,
            statusBadge = badge,
            completedMinutes = completedMinutes,
            totalMinutes = totalMinutes,
            remainingMinutes = remainingMinutes,
            remainingFocusBlocks = remainingBlocks,
            focusBlockMinutes = safeBlockMin,
            requiredBlocksPerDay = requiredBlocksPerDay,
            velocityMinutesPerDay = velocityMinPerDay,
            velocityTasksPerDay = velocityTasksPerDay,
            projectedFinishMs = projectedFinishMs,
            estimatedDelayDays = delayDays.coerceAtLeast(0.0),
            idealProgressFraction = idealProgress,
            actualProgressFraction = actualProgress,
            burnUpPoints = burnUpPoints,
            atRiskTasks = atRisk,
            calmSuggestion = suggestion
        )
    }

    private fun resolveSprintStartMs(
        project: Project,
        tasks: List<SprintTask>,
        nowMs: Long
    ): Long {
        val candidates = buildList {
            project.createdAt?.toDate()?.time?.let { add(it) }
            tasks.forEach { task -> task.createdAt?.toDate()?.time?.let { add(it) } }
        }.filter { it <= nowMs }
        return candidates.minOrNull() ?: (nowMs - DAY_MS.toLong())
    }

    private fun buildBurnUpTrajectory(
        tasks: List<SprintTask>,
        startMs: Long,
        nowMs: Long,
        totalMinutes: Int,
        currentActual: Float
    ): List<Float> {
        if (totalMinutes <= 0) return listOf(0f, 0f)
        val span = (nowMs - startMs).coerceAtLeast(1L)
        val steps = 5
        val points = mutableListOf(0f)
        for (i in 1 until steps) {
            val cutoff = startMs + (span * i) / steps
            val doneByCutoff = tasks
                .filter { it.taskStatus == TaskStatus.DONE }
                .filter {
                    val finishedAt = it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: nowMs
                    finishedAt <= cutoff
                }
                .sumOf { it.estimateMinutes.toInt().coerceAtLeast(5) }
            points.add((doneByCutoff.toFloat() / totalMinutes.toFloat()).coerceIn(0f, currentActual))
        }
        points.add(currentActual)
        return points
    }

    private fun findAtRiskTasks(openTasks: List<SprintTask>, nowMs: Long): List<AtRiskTaskItem> {
        val items = mutableListOf<AtRiskTaskItem>()
        openTasks.forEach { task ->
            when {
                task.dueDateMs in 1L until nowMs -> {
                    items.add(AtRiskTaskItem(task, "Past target date"))
                }
                task.milestone && task.dueDateMs > 0L && (task.dueDateMs - nowMs) <= 2 * DAY_MS -> {
                    items.add(AtRiskTaskItem(task, "Milestone due soon"))
                }
                task.estimateMinutes >= 60L && task.taskStatus == TaskStatus.TODO && task.substeps.isEmpty() -> {
                    items.add(AtRiskTaskItem(task, "Large step — try breaking down"))
                }
            }
        }
        return items.take(2)
    }

    private fun formatBlocks(value: Double): String {
        return if (value < 1.0) "1" else String.format("%.1f", value).removeSuffix(".0")
    }
}
