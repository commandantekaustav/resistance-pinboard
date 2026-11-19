package com.example

import com.example.data.model.Project
import com.example.data.model.SprintTask
import com.example.data.model.TaskStatus
import com.example.domain.ForecastStatus
import com.example.domain.SprintForecastEngine
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SprintForecastEngineTest {

    private val dayMs = 86_400_000L

    @Test
    fun emptyTasksReturnsNoData() {
        val now = 1_760_000_000_000L
        val project = Project(id = "p1", name = "Buildathon", sprintDeadlineMs = now + 5 * dayMs)
        val forecast = SprintForecastEngine.compute(project, emptyList(), focusBlockMinutes = 25, nowMs = now)
        assertEquals(ForecastStatus.NO_DATA, forecast.status)
    }

    @Test
    fun fastVelocityForecastsOnTrack() {
        val now = 1_760_000_000_000L
        val start = Timestamp(Date(now - 2 * dayMs))
        val project = Project(
            id = "p1",
            name = "Buildathon",
            sprintDeadlineMs = now + 5 * dayMs,
            memberUids = listOf("u1", "u2"),
            createdAt = start
        )
        val tasks = listOf(
            SprintTask(id = "t1", title = "Done 1", status = TaskStatus.DONE.name, estimateMinutes = 120, createdAt = start, updatedAt = Timestamp(Date(now - dayMs))),
            SprintTask(id = "t2", title = "Done 2", status = TaskStatus.DONE.name, estimateMinutes = 120, createdAt = start, updatedAt = Timestamp(Date(now - dayMs / 2))),
            SprintTask(id = "t3", title = "Remaining", status = TaskStatus.TODO.name, estimateMinutes = 60, dueDateMs = now + 3 * dayMs, createdAt = start)
        )

        val forecast = SprintForecastEngine.compute(project, tasks, focusBlockMinutes = 25, nowMs = now)
        assertEquals(ForecastStatus.ON_TRACK, forecast.status)
        assertEquals(0.0, forecast.estimatedDelayDays, 0.01)
        assertEquals(3, forecast.remainingFocusBlocks) // ceil(60 / 25) = 3
        assertNotNull(forecast.projectedFinishMs)
    }

    @Test
    fun slowVelocityAndHeavyBacklogTriggersDelayRiskAndWatchlist() {
        val now = 1_760_000_000_000L
        val start = Timestamp(Date(now - 4 * dayMs))
        val project = Project(
            id = "p1",
            name = "Buildathon",
            sprintDeadlineMs = now + 2 * dayMs,
            memberUids = listOf("u1"),
            createdAt = start
        )
        val tasks = listOf(
            SprintTask(id = "t1", title = "Done small", status = TaskStatus.DONE.name, estimateMinutes = 60, createdAt = start, updatedAt = Timestamp(Date(now - dayMs))),
            SprintTask(id = "t2", title = "Overdue API", status = TaskStatus.TODO.name, estimateMinutes = 120, dueDateMs = now - dayMs, milestone = true, createdAt = start),
            SprintTask(id = "t3", title = "Big unsplit task", status = TaskStatus.TODO.name, estimateMinutes = 120, dueDateMs = now + dayMs, createdAt = start)
        )

        val forecast = SprintForecastEngine.compute(project, tasks, focusBlockMinutes = 25, nowMs = now)
        assertEquals(ForecastStatus.DELAY_RISK, forecast.status)
        assertTrue(forecast.estimatedDelayDays > 1.0)
        assertEquals(2, forecast.atRiskTasks.size)
        assertEquals("Past target date", forecast.atRiskTasks.first().reason)
    }

    @Test
    fun allDoneReturnsCompleted() {
        val now = 1_760_000_000_000L
        val project = Project(id = "p1", name = "Buildathon", sprintDeadlineMs = now + 2 * dayMs)
        val tasks = listOf(
            SprintTask(id = "t1", title = "Ship", status = TaskStatus.DONE.name, estimateMinutes = 45)
        )
        val forecast = SprintForecastEngine.compute(project, tasks, focusBlockMinutes = 25, nowMs = now)
        assertEquals(ForecastStatus.COMPLETED, forecast.status)
        assertEquals(0, forecast.remainingFocusBlocks)
        assertEquals(1f, forecast.actualProgressFraction, 0.001f)
    }
}
