package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object AdhdReminderScheduler {
    const val CHANNEL_FOCUS = "clearsprint_focus_reminders"
    const val CHANNEL_MILESTONE = "clearsprint_milestones"
    const val EXTRA_TASK_TITLE = "extra_task_title"
    const val EXTRA_PROMPT_BODY = "extra_prompt_body"
    private const val REMINDER_REQUEST_CODE = 4201
    private const val NOTIFICATION_ID_FOCUS = 9001
    private const val NOTIFICATION_ID_MILESTONE = 9002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val focusChannel = NotificationChannel(
                CHANNEL_FOCUS,
                "Gentle Focus Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Calm, encouraging ADHD-friendly focus check-ins"
            }
            val milestoneChannel = NotificationChannel(
                CHANNEL_MILESTONE,
                "Project Milestones",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when Buildathon milestones are reached"
            }
            manager.createNotificationChannel(focusChannel)
            manager.createNotificationChannel(milestoneChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun scheduleGentleCheckIn(
        context: Context,
        intervalMinutes: Int,
        nextTaskTitle: String
    ) {
        ensureChannels(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AdhdReminderReceiver::class.java).apply {
            putExtra(EXTRA_TASK_TITLE, nextTaskTitle.ifBlank { "your next sprint step" })
            putExtra(
                EXTRA_PROMPT_BODY,
                "Just 5 minutes on \"${nextTaskTitle.ifBlank { "one small step" }}\" builds momentum. You've got this."
            )
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        if (intervalMinutes <= 0) return

        // Play-Policy compliant inexact repeating reminder (no SCHEDULE_EXACT_ALARM needed)
        val intervalMs = intervalMinutes * 60_000L
        val triggerAt = System.currentTimeMillis() + intervalMs
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            intervalMs,
            pendingIntent
        )
    }

    fun postFocusNudgeNow(context: Context, taskTitle: String, body: String? = null) {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = body ?: "Take one calm breath and spend 5 minutes on \"$taskTitle\". Small progress counts."
        val notification = NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Gentle Focus Check-In")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_FOCUS, notification)
        }
    }

    fun postMilestoneReachedNotification(context: Context, projectName: String, milestoneTitle: String) {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            102,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = "Milestone completed in $projectName: $milestoneTitle. Tap to send the team update."
        val notification = NotificationCompat.Builder(context, CHANNEL_MILESTONE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Milestone Reached")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_MILESTONE, notification)
        }
    }
}
