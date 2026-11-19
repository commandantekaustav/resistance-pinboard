package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.LocalSettings
import com.example.data.model.ThemeMode
import com.example.data.model.WidgetConfig
import com.example.data.model.WidgetStyle
import com.example.data.model.WidgetTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "clearsprint_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CALM_MODE = booleanPreferencesKey("calm_mode")
        val FOCUS_DURATION = intPreferencesKey("focus_duration_minutes")
        val REMINDER_INTERVAL = intPreferencesKey("reminder_interval_minutes")
        val GITHUB_TOKEN = stringPreferencesKey("github_pat")
        val LAST_PROJECT_ID = stringPreferencesKey("last_project_id")
        val WIDGET_STYLE = stringPreferencesKey("widget_style")
        val WIDGET_THEME = stringPreferencesKey("widget_theme")
        val WIDGET_SHOW_PROGRESS = booleanPreferencesKey("widget_show_progress")
        val WIDGET_SHOW_DEADLINE = booleanPreferencesKey("widget_show_deadline")
    }

    val settingsFlow: Flow<LocalSettings> = context.dataStore.data.map { prefs ->
        LocalSettings(
            themeMode = ThemeMode.fromRaw(prefs[Keys.THEME_MODE]),
            calmModeEnabled = prefs[Keys.CALM_MODE] ?: false,
            focusDurationMinutes = (prefs[Keys.FOCUS_DURATION] ?: 25).coerceIn(5, 90),
            reminderIntervalMinutes = (prefs[Keys.REMINDER_INTERVAL] ?: 60).coerceIn(0, 240),
            githubToken = prefs[Keys.GITHUB_TOKEN] ?: "",
            lastActiveProjectId = prefs[Keys.LAST_PROJECT_ID] ?: "",
            widgetConfig = WidgetConfig(
                style = WidgetStyle.fromRaw(prefs[Keys.WIDGET_STYLE]),
                theme = WidgetTheme.fromRaw(prefs[Keys.WIDGET_THEME]),
                showProgressBar = prefs[Keys.WIDGET_SHOW_PROGRESS] ?: true,
                showDeadline = prefs[Keys.WIDGET_SHOW_DEADLINE] ?: true
            )
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setCalmMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CALM_MODE] = enabled }
    }

    suspend fun setFocusDuration(minutes: Int) {
        context.dataStore.edit { it[Keys.FOCUS_DURATION] = minutes.coerceIn(5, 90) }
    }

    suspend fun setReminderInterval(minutes: Int) {
        context.dataStore.edit { it[Keys.REMINDER_INTERVAL] = minutes.coerceIn(0, 240) }
    }

    suspend fun setGitHubToken(token: String) {
        context.dataStore.edit { it[Keys.GITHUB_TOKEN] = token.trim() }
    }

    suspend fun setLastActiveProjectId(projectId: String) {
        context.dataStore.edit { it[Keys.LAST_PROJECT_ID] = projectId }
    }

    suspend fun updateWidgetConfig(config: WidgetConfig) {
        context.dataStore.edit {
            it[Keys.WIDGET_STYLE] = config.style.name
            it[Keys.WIDGET_THEME] = config.theme.name
            it[Keys.WIDGET_SHOW_PROGRESS] = config.showProgressBar
            it[Keys.WIDGET_SHOW_DEADLINE] = config.showDeadline
        }
        // Mirror lightweight config into SharedPreferences so AppWidgetProvider reads synchronously
        val sp = context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_STYLE, config.style.name)
            .putString(KEY_THEME, config.theme.name)
            .putBoolean(KEY_SHOW_PROGRESS, config.showProgressBar)
            .putBoolean(KEY_SHOW_DEADLINE, config.showDeadline)
            .apply()
    }

    fun saveWidgetSnapshot(
        projectName: String,
        sprintLabel: String,
        deadlineText: String,
        focusTaskTitle: String,
        secondaryLines: String,
        progressPercent: Int
    ) {
        val sp = context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_PROJECT_NAME, projectName)
            .putString(KEY_SPRINT_LABEL, sprintLabel)
            .putString(KEY_DEADLINE_TEXT, deadlineText)
            .putString(KEY_FOCUS_TITLE, focusTaskTitle)
            .putString(KEY_SECONDARY_LINES, secondaryLines)
            .putInt(KEY_PROGRESS_PERCENT, progressPercent.coerceIn(0, 100))
            .apply()
    }

    companion object {
        const val WIDGET_PREFS_NAME = "clearsprint_widget_state"
        const val KEY_STYLE = "style"
        const val KEY_THEME = "theme"
        const val KEY_SHOW_PROGRESS = "show_progress"
        const val KEY_SHOW_DEADLINE = "show_deadline"
        const val KEY_PROJECT_NAME = "project_name"
        const val KEY_SPRINT_LABEL = "sprint_label"
        const val KEY_DEADLINE_TEXT = "deadline_text"
        const val KEY_FOCUS_TITLE = "focus_title"
        const val KEY_SECONDARY_LINES = "secondary_lines"
        const val KEY_PROGRESS_PERCENT = "progress_percent"
    }
}
