package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AdhdReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskTitle = intent.getStringExtra(AdhdReminderScheduler.EXTRA_TASK_TITLE)
            ?: "your next sprint step"
        val body = intent.getStringExtra(AdhdReminderScheduler.EXTRA_PROMPT_BODY)
        AdhdReminderScheduler.postFocusNudgeNow(context, taskTitle, body)
    }
}
