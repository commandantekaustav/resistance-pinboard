package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.PreferencesRepository
import com.example.data.model.WidgetStyle
import com.example.data.model.WidgetTheme

class ClearSprintWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun refreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ClearSprintWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            for (id in ids) {
                updateWidget(context, appWidgetManager, id)
            }
        }

        fun requestPinWidget(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val provider = ComponentName(context, ClearSprintWidgetProvider::class.java)
                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    return appWidgetManager.requestPinAppWidget(provider, null, null)
                }
            }
            return false
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val sp = context.getSharedPreferences(
                PreferencesRepository.WIDGET_PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val style = WidgetStyle.fromRaw(sp.getString(PreferencesRepository.KEY_STYLE, null))
            val theme = WidgetTheme.fromRaw(sp.getString(PreferencesRepository.KEY_THEME, null))
            val showProgress = sp.getBoolean(PreferencesRepository.KEY_SHOW_PROGRESS, true)
            val showDeadline = sp.getBoolean(PreferencesRepository.KEY_SHOW_DEADLINE, true)

            val projectName = sp.getString(PreferencesRepository.KEY_PROJECT_NAME, "ClearSprint") ?: "ClearSprint"
            val deadlineText = sp.getString(PreferencesRepository.KEY_DEADLINE_TEXT, "Ready to focus") ?: "Ready to focus"
            val focusTitle = sp.getString(
                PreferencesRepository.KEY_FOCUS_TITLE,
                "Tap to pick your next focus task"
            ) ?: "Tap to pick your next focus task"
            val secondaryLines = sp.getString(PreferencesRepository.KEY_SECONDARY_LINES, "") ?: ""
            val progressPct = sp.getInt(PreferencesRepository.KEY_PROGRESS_PERCENT, 0)

            val views = RemoteViews(context.packageName, R.layout.widget_clearsprint)

            // Apply theme colors & drawable background
            val bgRes = when (theme) {
                WidgetTheme.DARK -> R.drawable.widget_bg_dark
                WidgetTheme.LIGHT -> R.drawable.widget_bg_light
                WidgetTheme.SAGE -> R.drawable.widget_bg_sage
            }
            val titleColor = when (theme) {
                WidgetTheme.DARK -> Color.parseColor("#5ED4C2")
                WidgetTheme.LIGHT -> Color.parseColor("#1A7368")
                WidgetTheme.SAGE -> Color.parseColor("#8CF0E0")
            }
            val primaryTextColor = when (theme) {
                WidgetTheme.DARK -> Color.parseColor("#F3F5F7")
                WidgetTheme.LIGHT -> Color.parseColor("#1B1F23")
                WidgetTheme.SAGE -> Color.parseColor("#F4FBF9")
            }
            val secondaryTextColor = when (theme) {
                WidgetTheme.DARK -> Color.parseColor("#9BA8B4")
                WidgetTheme.LIGHT -> Color.parseColor("#5A636C")
                WidgetTheme.SAGE -> Color.parseColor("#A7CBC5")
            }
            val deadlineColor = when (theme) {
                WidgetTheme.LIGHT -> Color.parseColor("#B57227")
                else -> Color.parseColor("#F0B96B")
            }

            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
            views.setTextColor(R.id.widget_project_title, titleColor)
            views.setTextColor(R.id.widget_focus_heading, primaryTextColor)
            views.setTextColor(R.id.widget_secondary_tasks, secondaryTextColor)
            views.setTextColor(R.id.widget_deadline_badge, deadlineColor)
            views.setTextColor(R.id.widget_progress_text, secondaryTextColor)

            views.setTextViewText(R.id.widget_project_title, projectName)
            views.setTextViewText(R.id.widget_deadline_badge, deadlineText)
            views.setViewVisibility(
                R.id.widget_deadline_badge,
                if (showDeadline) View.VISIBLE else View.GONE
            )

            // Apply selected widget density style
            when (style) {
                WidgetStyle.ZEN_FOCUS -> {
                    views.setTextViewText(R.id.widget_focus_heading, focusTitle)
                    views.setViewVisibility(R.id.widget_secondary_tasks, View.GONE)
                }
                WidgetStyle.SPRINT_SNAPSHOT -> {
                    views.setTextViewText(R.id.widget_focus_heading, focusTitle)
                    views.setTextViewText(R.id.widget_secondary_tasks, secondaryLines)
                    views.setViewVisibility(
                        R.id.widget_secondary_tasks,
                        if (secondaryLines.isNotBlank()) View.VISIBLE else View.GONE
                    )
                }
                WidgetStyle.DEADLINES -> {
                    views.setTextViewText(R.id.widget_focus_heading, "Next Milestone: $deadlineText")
                    views.setTextViewText(R.id.widget_secondary_tasks, focusTitle)
                    views.setViewVisibility(R.id.widget_secondary_tasks, View.VISIBLE)
                }
            }

            views.setViewVisibility(
                R.id.widget_progress_row,
                if (showProgress) View.VISIBLE else View.GONE
            )
            views.setProgressBar(R.id.widget_progress_bar, 100, progressPct, false)
            views.setTextViewText(R.id.widget_progress_text, "$progressPct%")

            val launchIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
