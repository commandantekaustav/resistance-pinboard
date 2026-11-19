package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.SprintTask
import com.example.domain.ForecastStatus
import com.example.domain.SprintForecast
import com.example.ui.theme.MonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SprintForecastCard(
    forecast: SprintForecast,
    canEdit: Boolean,
    onPinFocusTask: (SprintTask) -> Unit
) {
    if (forecast.status == ForecastStatus.NO_DATA) return

    var detailsExpanded by remember { mutableStateOf(false) }

    val badgeContainerColor = when (forecast.status) {
        ForecastStatus.ON_TRACK, ForecastStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
        ForecastStatus.WARMING_UP -> MaterialTheme.colorScheme.surfaceContainerHigh
        ForecastStatus.TIGHT_PACE, ForecastStatus.DELAY_RISK -> MaterialTheme.colorScheme.secondaryContainer
        ForecastStatus.NO_DATA -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val badgeContentColor = when (forecast.status) {
        ForecastStatus.ON_TRACK, ForecastStatus.COMPLETED -> MaterialTheme.colorScheme.onPrimaryContainer
        ForecastStatus.WARMING_UP -> MaterialTheme.colorScheme.onSurfaceVariant
        ForecastStatus.TIGHT_PACE, ForecastStatus.DELAY_RISK -> MaterialTheme.colorScheme.onSecondaryContainer
        ForecastStatus.NO_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val projectedDateText = remember(forecast.projectedFinishMs, forecast.status) {
        when {
            forecast.status == ForecastStatus.COMPLETED -> "Done"
            forecast.projectedFinishMs == null -> "—"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(forecast.projectedFinishMs))
        }
    }

    val requiredPaceText = remember(forecast.requiredBlocksPerDay, forecast.status) {
        if (forecast.status == ForecastStatus.COMPLETED) "0/day"
        else {
            val formatted = if (forecast.requiredBlocksPerDay < 1.0) "1"
            else String.format("%.1f", forecast.requiredBlocksPerDay).removeSuffix(".0")
            "$formatted blocks/day"
        }
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sprint_forecast_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header row: Forecast title + Calm status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Insights,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Pace & Forecast",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeContainerColor,
                    modifier = Modifier.testTag("forecast_status_badge")
                ) {
                    Text(
                        text = forecast.statusBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeContentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Minimalist 3-stat strip (high scannability, zero clutter)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ForecastMetricTile(
                    label = "EST. FINISH",
                    value = projectedDateText,
                    modifier = Modifier.weight(1f)
                )
                ForecastMetricTile(
                    label = "FOCUS BUDGET",
                    value = "${forecast.remainingFocusBlocks} × ${forecast.focusBlockMinutes}m",
                    modifier = Modifier.weight(1f)
                )
                ForecastMetricTile(
                    label = "DAILY PACE",
                    value = requiredPaceText,
                    modifier = Modifier.weight(1f)
                )
            }

            // Quiet Burn-Up Sparkline (Ideal pace vs. Actual team momentum)
            PaceSparkline(
                burnUpPoints = forecast.burnUpPoints,
                idealProgress = forecast.idealProgressFraction,
                isBehind = forecast.status == ForecastStatus.DELAY_RISK || forecast.status == ForecastStatus.TIGHT_PACE
            )

            // Calm, encouraging ADHD suggestion
            Text(
                text = forecast.calmSuggestion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Expandable details & At-Risk Watchlist (kept tucked away by default to reduce visual load)
            if (forecast.atRiskTasks.isNotEmpty() || forecast.velocityMinutesPerDay > 0.0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { detailsExpanded = !detailsExpanded }
                        .padding(vertical = 2.dp)
                        .testTag("forecast_details_toggle"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val label = if (forecast.atRiskTasks.isNotEmpty()) {
                        "Watchlist (${forecast.atRiskTasks.size}) & Velocity"
                    } else {
                        "Velocity details"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (detailsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (detailsExpanded) "Hide forecast details" else "Show forecast details",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(visible = detailsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val velHours = forecast.velocityMinutesPerDay / 60.0
                        val remHours = forecast.remainingMinutes / 60.0
                        Text(
                            text = String.format(
                                Locale.getDefault(),
                                "Current velocity: %.1fh/day (%.1f tasks/day) · Remaining effort: %.1fh",
                                velHours,
                                forecast.velocityTasksPerDay,
                                remHours
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        forecast.atRiskTasks.forEach { item ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.task.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.reason} · ${item.task.estimateMinutes}m",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    if (canEdit) {
                                        TextButton(onClick = { onPinFocusTask(item.task) }) {
                                            Icon(
                                                imageVector = Icons.Outlined.CenterFocusStrong,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Focus")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PaceSparkline(
    burnUpPoints: List<Float>,
    idealProgress: Float,
    isBehind: Boolean
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val idealColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val actualColor = if (isBehind) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            val padY = 6.dp.toPx()
            val w = size.width
            val h = size.height - (padY * 2)

            // Subtle baseline
            drawLine(
                color = gridColor,
                start = Offset(0f, size.height - padY),
                end = Offset(w, size.height - padY),
                strokeWidth = 1.dp.toPx()
            )

            // Ideal pace dashed diagonal (from 0% at start to idealProgress at current sprint point)
            val idealEndY = padY + h * (1f - idealProgress.coerceIn(0f, 1f))
            drawLine(
                color = idealColor,
                start = Offset(0f, size.height - padY),
                end = Offset(w, idealEndY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            )

            // Actual burn-up curve
            val safePoints = if (burnUpPoints.size >= 2) burnUpPoints else listOf(0f, 0f)
            val path = Path()
            val stepX = w / (safePoints.size - 1).coerceAtLeast(1)
            safePoints.forEachIndexed { idx, value ->
                val x = idx * stepX
                val y = padY + h * (1f - value.coerceIn(0f, 1f))
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = actualColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // End dot
            val lastY = padY + h * (1f - safePoints.last().coerceIn(0f, 1f))
            drawCircle(
                color = actualColor,
                radius = 4.dp.toPx(),
                center = Offset(w, lastY)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Ideal pace (dashed) vs. actual momentum",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Target ${(idealProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
