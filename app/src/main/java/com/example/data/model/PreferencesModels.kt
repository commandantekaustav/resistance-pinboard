package com.example.data.model

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light");

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class WidgetStyle(val label: String, val subtitle: String) {
    ZEN_FOCUS("Zen Focus", "Single next step + zero clutter"),
    SPRINT_SNAPSHOT("Sprint Snapshot", "Progress bar + top 3 tasks"),
    DEADLINES("Deadlines", "Sprint countdown + key milestones");

    companion object {
        fun fromRaw(raw: String?): WidgetStyle =
            entries.firstOrNull { it.name == raw } ?: ZEN_FOCUS
    }
}

enum class WidgetTheme(val label: String) {
    DARK("Calm Obsidian"),
    LIGHT("Warm Paper"),
    SAGE("Deep Sage");

    companion object {
        fun fromRaw(raw: String?): WidgetTheme =
            entries.firstOrNull { it.name == raw } ?: DARK
    }
}

data class WidgetConfig(
    val style: WidgetStyle = WidgetStyle.ZEN_FOCUS,
    val theme: WidgetTheme = WidgetTheme.DARK,
    val showProgressBar: Boolean = true,
    val showDeadline: Boolean = true
)

data class LocalSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val calmModeEnabled: Boolean = false,
    val focusDurationMinutes: Int = 25,
    val reminderIntervalMinutes: Int = 60,
    val githubToken: String = "",
    val lastActiveProjectId: String = "",
    val widgetConfig: WidgetConfig = WidgetConfig()
)
