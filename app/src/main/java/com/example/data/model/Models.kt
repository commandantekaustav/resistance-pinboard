package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ProjectRole(val label: String, val description: String) {
    OWNER("Owner", "Full workspace, RBAC, and deletion control"),
    ADMIN("Admin", "Manage settings, GitHub sync, roles, and tasks"),
    EDITOR("Editor", "Create and update tasks, milestones, and files"),
    VIEWER("Viewer", "Read-only access to sprint board and files");

    companion object {
        fun fromRaw(raw: String?): ProjectRole =
            entries.firstOrNull { it.name == raw } ?: VIEWER
    }
}

enum class TaskStatus(val label: String) {
    TODO("To Do"),
    IN_PROGRESS("Doing"),
    DONE("Done");

    companion object {
        fun fromRaw(raw: String?): TaskStatus =
            entries.firstOrNull { it.name == raw } ?: TODO
    }
}

enum class TaskPriority(val label: String) {
    LOW("Calm"),
    MEDIUM("Normal"),
    HIGH("Priority");

    companion object {
        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class FileCategory(val label: String) {
    CODE("Code"),
    DOC("Doc"),
    DESIGN("Design"),
    SPEC("Spec"),
    LINK("Link");

    companion object {
        fun fromRaw(raw: String?): FileCategory =
            entries.firstOrNull { it.name == raw } ?: DOC
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val focusMinutes: Long = 25L,
    val reminderIntervalMinutes: Long = 60L,
    val calmModeEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Project(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val description: String = "",
    val sprintName: String = "Sprint 1",
    val sprintDeadlineMs: Long = 0L,
    val githubOwner: String = "",
    val githubRepo: String = "",
    val inviteCode: String = "",
    val webhookUrl: String = "",
    val memberUids: List<String> = emptyList(),
    val roles: Map<String, String> = emptyMap(),
    val memberNames: Map<String, String> = emptyMap(),
    val milestoneEmails: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleFor(uid: String): ProjectRole = ProjectRole.fromRaw(roles[uid])
    fun hasGitHubRepo(): Boolean = githubOwner.isNotBlank() && githubRepo.isNotBlank()
}

@Keep
@IgnoreExtraProperties
data class SprintTask(
    @DocumentId val id: String = "",
    val projectId: String = "",
    val creatorId: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = TaskStatus.TODO.name,
    val priority: String = TaskPriority.MEDIUM.name,
    val assigneeUid: String = "",
    val assigneeName: String = "",
    val dueDateMs: Long = 0L,
    val estimateMinutes: Long = 25L,
    val milestone: Boolean = false,
    val focused: Boolean = false,
    val substeps: List<String> = emptyList(),
    val completedSubsteps: List<String> = emptyList(),
    val githubIssueNumber: Long = 0L,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val taskStatus: TaskStatus get() = TaskStatus.fromRaw(status)
    val taskPriority: TaskPriority get() = TaskPriority.fromRaw(priority)
}

@Keep
@IgnoreExtraProperties
data class ProjectFile(
    @DocumentId val id: String = "",
    val projectId: String = "",
    val creatorId: String = "",
    val name: String = "",
    val path: String = "",
    val fileType: String = FileCategory.DOC.name,
    val url: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val category: FileCategory get() = FileCategory.fromRaw(fileType)
}

data class MilestoneEmailDraft(
    val projectName: String,
    val sprintName: String,
    val milestoneTitle: String,
    val completionPercent: Int,
    val recipients: List<String>,
    val subject: String,
    val body: String,
    val webhookDispatched: Boolean = false,
    val webhookStatusNote: String? = null
)
