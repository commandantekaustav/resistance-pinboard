package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.ProjectRepository
import com.example.data.github.GitHubIssueDto
import com.example.data.github.GitHubRepository
import com.example.data.github.GitHubWorkspaceData
import com.example.data.local.NetworkMonitor
import com.example.data.local.PreferencesRepository
import com.example.data.model.FileCategory
import com.example.data.model.LocalSettings
import com.example.data.model.MilestoneEmailDraft
import com.example.data.model.Project
import com.example.data.model.ProjectFile
import com.example.data.model.ProjectRole
import com.example.data.model.SprintTask
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.ThemeMode
import com.example.data.model.UiState
import com.example.data.model.WidgetConfig
import com.example.domain.SprintForecast
import com.example.domain.SprintForecastEngine
import com.example.notifications.MilestoneNotifier
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class FocusTimerState(
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val running: Boolean = false,
    val completedSessions: Int = 0
) {
    val progress: Float
        get() = if (totalSeconds <= 0) 0f else 1f - (remainingSeconds.toFloat() / totalSeconds.toFloat())
}

data class RoleCapabilities(
    val role: ProjectRole = ProjectRole.VIEWER,
    val canEditContent: Boolean = false,
    val canManageSettings: Boolean = false,
    val canManageRoles: Boolean = false,
    val canDeleteProject: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ClearSprintViewModel(
    val currentUserId: String,
    val currentUserName: String,
    val currentUserEmail: String,
    private val projectRepository: ProjectRepository,
    private val gitHubRepository: GitHubRepository,
    private val preferencesRepository: PreferencesRepository,
    networkMonitor: NetworkMonitor,
    private val onWidgetRefresh: () -> Unit,
    private val onMilestoneNotification: (String, String) -> Unit,
    private val onReminderSchedule: (Int, String) -> Unit
) : ViewModel() {

    val localSettings: StateFlow<LocalSettings> = preferencesRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LocalSettings())

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    private val _selectedProjectId = MutableStateFlow<String?>(null)

    val projectsState: StateFlow<UiState<List<Project>>> = projectRepository.observeProjects(currentUserId)
        .map<List<Project>, UiState<List<Project>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing projects", root)
            emit(UiState.Error(root.message ?: "Unable to sync projects right now."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val activeProject: StateFlow<Project?> = combine(
        projectsState,
        _selectedProjectId,
        localSettings
    ) { state, explicitId, prefs ->
        val list = (state as? UiState.Success)?.data ?: return@combine null
        list.firstOrNull { it.id == explicitId }
            ?: list.firstOrNull { it.id == prefs.lastActiveProjectId }
            ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val capabilities: StateFlow<RoleCapabilities> = activeProject.map { project ->
        if (project == null) {
            RoleCapabilities()
        } else {
            val role = project.roleFor(currentUserId)
            RoleCapabilities(
                role = role,
                canEditContent = role in setOf(ProjectRole.OWNER, ProjectRole.ADMIN, ProjectRole.EDITOR),
                canManageSettings = role in setOf(ProjectRole.OWNER, ProjectRole.ADMIN),
                canManageRoles = role in setOf(ProjectRole.OWNER, ProjectRole.ADMIN),
                canDeleteProject = role == ProjectRole.OWNER
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RoleCapabilities())

    val tasksState: StateFlow<UiState<List<SprintTask>>> = activeProject
        .flatMapLatest { project ->
            if (project == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                projectRepository.observeTasks(project.id, currentUserId)
                    .map<List<SprintTask>, UiState<List<SprintTask>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = unwrapFirestoreError(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        val root = firestoreError ?: error
                        Log.w(TAG, "Error observing tasks", root)
                        emit(UiState.Error(root.message ?: "Unable to sync sprint tasks."))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val filesState: StateFlow<UiState<List<ProjectFile>>> = activeProject
        .flatMapLatest { project ->
            if (project == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                projectRepository.observeProjectFiles(project.id, currentUserId)
                    .map<List<ProjectFile>, UiState<List<ProjectFile>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = unwrapFirestoreError(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        val root = firestoreError ?: error
                        Log.w(TAG, "Error observing files", root)
                        emit(UiState.Error(root.message ?: "Unable to sync project files."))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val sprintForecast: StateFlow<SprintForecast> = combine(
        activeProject,
        tasksState,
        localSettings
    ) { project, tState, prefs ->
        val tasks = (tState as? UiState.Success)?.data.orEmpty()
        SprintForecastEngine.compute(
            project = project,
            tasks = tasks,
            focusBlockMinutes = prefs.focusDurationMinutes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SprintForecast())

    private val _githubData = MutableStateFlow(GitHubWorkspaceData())
    val githubData: StateFlow<GitHubWorkspaceData> = _githubData.asStateFlow()

    private val _timerState = MutableStateFlow(FocusTimerState())
    val timerState: StateFlow<FocusTimerState> = _timerState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _pendingMilestoneEmail = MutableStateFlow<MilestoneEmailDraft?>(null)
    val pendingMilestoneEmail: StateFlow<MilestoneEmailDraft?> = _pendingMilestoneEmail.asStateFlow()

    private var timerJob: Job? = null
    private var lastSyncedRepoKey: String? = null

    init {
        viewModelScope.launch {
            projectRepository.ensureUserProfile()
        }

        // Sync widget snapshot + membership whenever active project or tasks change
        viewModelScope.launch {
            combine(activeProject, tasksState, filesState) { project, tState, fState ->
                Triple(
                    project,
                    (tState as? UiState.Success)?.data.orEmpty(),
                    (fState as? UiState.Success)?.data.orEmpty()
                )
            }.collect { (project, tasks, files) ->
                if (project != null) {
                    syncWidgetAndMembership(project, tasks, files)
                    val repoKey = "${project.githubOwner}/${project.githubRepo}"
                    if (project.hasGitHubRepo() && repoKey != lastSyncedRepoKey) {
                        lastSyncedRepoKey = repoKey
                        refreshGitHubRepo(project.githubOwner, project.githubRepo)
                    } else if (!project.hasGitHubRepo() && lastSyncedRepoKey != null) {
                        lastSyncedRepoKey = null
                        _githubData.value = GitHubWorkspaceData()
                    }
                }
            }
        }

        // Sync focus timer default when preference changes (only if timer isn't actively running)
        viewModelScope.launch {
            localSettings.collect { settings ->
                if (!_timerState.value.running) {
                    val targetSec = settings.focusDurationMinutes * 60
                    if (_timerState.value.totalSeconds != targetSec) {
                        _timerState.update {
                            it.copy(totalSeconds = targetSec, remainingSeconds = targetSec)
                        }
                    }
                }
            }
        }
    }

    private fun syncWidgetAndMembership(
        project: Project,
        tasks: List<SprintTask>,
        files: List<ProjectFile>
    ) {
        val doneCount = tasks.count { it.taskStatus == TaskStatus.DONE }
        val pct = if (tasks.isEmpty()) 0 else (doneCount * 100) / tasks.size
        val openTasks = tasks.filter { it.taskStatus != TaskStatus.DONE }
        val focusTask = openTasks.firstOrNull { it.focused } ?: openTasks.firstOrNull()
        val secondary = openTasks.filter { it.id != focusTask?.id }
            .take(2)
            .joinToString("\n") { "• ${it.title}" }
        val deadlineStr = formatDeadlineCountdown(project.sprintDeadlineMs)

        preferencesRepository.saveWidgetSnapshot(
            projectName = project.name,
            sprintLabel = project.sprintName,
            deadlineText = deadlineStr,
            focusTaskTitle = focusTask?.title ?: "All sprint tasks complete!",
            secondaryLines = secondary,
            progressPercent = pct
        )
        onWidgetRefresh()

        val role = project.roleFor(currentUserId)
        if (role == ProjectRole.OWNER || role == ProjectRole.ADMIN) {
            viewModelScope.launch {
                projectRepository.syncSubcollectionMemberUids(project, tasks, files)
            }
        }
    }

    fun selectProject(projectId: String) {
        _selectedProjectId.value = projectId
        viewModelScope.launch {
            preferencesRepository.setLastActiveProjectId(projectId)
        }
    }

    fun createProject(
        name: String,
        description: String,
        sprintName: String,
        daysUntilDeadline: Int,
        githubOwner: String,
        githubRepo: String,
        milestoneEmailsRaw: String
    ) {
        viewModelScope.launch {
            val deadlineMs = System.currentTimeMillis() + max(1, daysUntilDeadline) * 86_400_000L
            val emails = milestoneEmailsRaw.split(",", ";", "\n")
                .map { it.trim() }
                .filter { it.contains("@") }
            val result = projectRepository.createProject(
                name = name,
                description = description,
                sprintName = sprintName,
                sprintDeadlineMs = deadlineMs,
                githubOwner = githubOwner,
                githubRepo = githubRepo,
                milestoneEmails = emails
            )
            result.onSuccess { newId ->
                selectProject(newId)
                _statusBanner.value = "Project created and synced."
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Could not create project."
            }
        }
    }

    fun joinProjectByCode(code: String) {
        viewModelScope.launch {
            val result = projectRepository.joinProjectByInviteCode(code)
            result.onSuccess { joinedId ->
                selectProject(joinedId)
                _statusBanner.value = "Joined team workspace as Editor."
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Unable to join with that invite code."
            }
        }
    }

    fun updateActiveProjectSettings(
        name: String,
        description: String,
        sprintName: String,
        sprintDeadlineMs: Long,
        githubOwner: String,
        githubRepo: String,
        webhookUrl: String,
        milestoneEmailsRaw: String
    ) {
        val project = activeProject.value ?: return
        if (!capabilities.value.canManageSettings) {
            _statusBanner.value = "Only Owners and Admins can change workspace settings."
            return
        }
        viewModelScope.launch {
            val emails = milestoneEmailsRaw.split(",", ";", "\n")
                .map { it.trim() }
                .filter { it.contains("@") }
            val result = projectRepository.updateProjectSettings(
                projectId = project.id,
                name = name,
                description = description,
                sprintName = sprintName,
                sprintDeadlineMs = sprintDeadlineMs,
                githubOwner = githubOwner,
                githubRepo = githubRepo,
                webhookUrl = webhookUrl,
                milestoneEmails = emails
            )
            result.onSuccess {
                _statusBanner.value = "Workspace settings saved."
                if (githubOwner.isNotBlank() && githubRepo.isNotBlank()) {
                    lastSyncedRepoKey = "${githubOwner.trim()}/${githubRepo.trim()}"
                    refreshGitHubRepo(githubOwner, githubRepo)
                }
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Failed to update settings."
            }
        }
    }

    fun updateMemberRole(targetUid: String, role: ProjectRole) {
        val project = activeProject.value ?: return
        if (!capabilities.value.canManageRoles) {
            _statusBanner.value = "You don't have permission to change member roles."
            return
        }
        viewModelScope.launch {
            projectRepository.updateMemberRole(project, targetUid, role)
                .onSuccess { _statusBanner.value = "Updated role to ${role.label}." }
                .onFailure { _statusBanner.value = it.message ?: "Failed to update role." }
        }
    }

    fun removeMember(targetUid: String) {
        val project = activeProject.value ?: return
        if (!capabilities.value.canManageRoles) return
        viewModelScope.launch {
            projectRepository.removeMember(project, targetUid)
                .onSuccess { _statusBanner.value = "Teammate removed from workspace." }
                .onFailure { _statusBanner.value = it.message ?: "Failed to remove member." }
        }
    }

    // --- Tasks & Milestones ---
    fun createTask(
        title: String,
        description: String,
        priority: TaskPriority,
        assigneeUid: String,
        assigneeName: String,
        daysUntilDue: Int,
        estimateMinutes: Int,
        milestone: Boolean,
        focused: Boolean,
        substepsRaw: String,
        githubIssueNumber: Long = 0L
    ) {
        val project = activeProject.value ?: return
        if (!capabilities.value.canEditContent) {
            _statusBanner.value = "Viewer role cannot create tasks."
            return
        }
        viewModelScope.launch {
            val dueMs = if (daysUntilDue > 0) {
                System.currentTimeMillis() + daysUntilDue * 86_400_000L
            } else {
                project.sprintDeadlineMs
            }
            val substeps = substepsRaw.lines()
                .map { it.trim().removePrefix("-").removePrefix("•").trim() }
                .filter { it.isNotEmpty() }
                .take(10)
            projectRepository.createTask(
                project = project,
                title = title,
                description = description,
                priority = priority,
                assigneeUid = assigneeUid,
                assigneeName = assigneeName,
                dueDateMs = dueMs,
                estimateMinutes = estimateMinutes,
                milestone = milestone,
                focused = focused,
                substeps = substeps,
                githubIssueNumber = githubIssueNumber
            ).onSuccess {
                _statusBanner.value = if (milestone) "Milestone added to sprint." else "Task added."
            }.onFailure {
                _statusBanner.value = it.message ?: "Could not add task."
            }
        }
    }

    fun importGitHubIssueAsTask(issue: GitHubIssueDto) {
        val project = activeProject.value ?: return
        if (!capabilities.value.canEditContent) {
            _statusBanner.value = "Viewer role cannot import issues."
            return
        }
        createTask(
            title = "#${issue.number} ${issue.title}".take(120),
            description = (issue.body ?: "Imported from GitHub issue #${issue.number}\n${issue.htmlUrl}").take(900),
            priority = TaskPriority.MEDIUM,
            assigneeUid = currentUserId,
            assigneeName = currentUserName,
            daysUntilDue = 3,
            estimateMinutes = 25,
            milestone = false,
            focused = false,
            substepsRaw = "Review issue details\nImplement solution\nPush commit & close issue",
            githubIssueNumber = issue.number.toLong()
        )
    }

    fun updateTaskStatus(task: SprintTask, newStatus: TaskStatus) {
        val project = activeProject.value ?: return
        if (!capabilities.value.canEditContent) {
            _statusBanner.value = "Viewer role is read-only."
            return
        }
        viewModelScope.launch {
            val updates = mutableMapOf<String, Any>("status" to newStatus.name)
            if (newStatus == TaskStatus.DONE && task.focused) {
                updates["focused"] = false
            }
            projectRepository.updateTask(project.id, task.id, updates)
                .onSuccess {
                    if (newStatus == TaskStatus.DONE && task.milestone) {
                        triggerMilestoneEmailFlow(project, task.title)
                    }
                }
                .onFailure {
                    _statusBanner.value = it.message ?: "Unable to update task."
                }
        }
    }

    fun toggleSubstep(task: SprintTask, step: String) {
        val project = activeProject.value ?: return
        if (!capabilities.value.canEditContent) return
        val updated = task.completedSubsteps.toMutableList()
        if (step in updated) updated.remove(step) else updated.add(step)
        viewModelScope.launch {
            projectRepository.updateTask(
                project.id,
                task.id,
                mapOf("completedSubsteps" to updated.take(10))
            )
        }
    }

    fun pinFocusTask(task: SprintTask) {
        val project = activeProject.value ?: return
        if (!capabilities.value.canEditContent) return
        val currentTasks = (tasksState.value as? UiState.Success)?.data.orEmpty()
        viewModelScope.launch {
            currentTasks.filter { it.focused && it.id != task.id }.forEach { other ->
                projectRepository.updateTask(project.id, other.id, mapOf("focused" to false))
            }
            projectRepository.updateTask(
                project.id,
                task.id,
                mapOf(
                    "focused" to true,
                    "status" to if (task.taskStatus == TaskStatus.TODO) TaskStatus.IN_PROGRESS.name else task.status
                )
            ).onSuccess {
                _statusBanner.value = "Pinned \"${task.title}\" to Focus Now."
            }
        }
    }

    fun deleteTask(task: SprintTask) {
        val project = activeProject.value ?: return
        if (!capabilities.value.canEditContent) return
        viewModelScope.launch {
            projectRepository.deleteTask(project.id, task.id)
                .onSuccess { _statusBanner.value = "Task removed." }
                .onFailure { _statusBanner.value = it.message ?: "Unable to delete task." }
        }
    }

    // --- Project Files ---
    fun addProjectFile(
        name: String,
        path: String,
        category: FileCategory,
        url: String,
        notes: String,
        tagsRaw: String
    ) {
        val project = activeProject.value ?: return
        if (!capabilities.value.canEditContent) {
            _statusBanner.value = "Viewer role cannot add files."
            return
        }
        viewModelScope.launch {
            val tags = tagsRaw.split(",", " ").map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }
            projectRepository.createProjectFile(project, name, path, category, url, notes, tags)
                .onSuccess { _statusBanner.value = "File indexed for team search." }
                .onFailure { _statusBanner.value = it.message ?: "Unable to save file." }
        }
    }

    fun deleteProjectFile(file: ProjectFile) {
        val project = activeProject.value ?: return
        if (!capabilities.value.canEditContent) return
        viewModelScope.launch {
            projectRepository.deleteProjectFile(project.id, file.id)
                .onSuccess { _statusBanner.value = "File removed." }
                .onFailure { _statusBanner.value = it.message ?: "Unable to delete file." }
        }
    }

    // --- GitHub Live Sync ---
    fun refreshGitHubRepo(owner: String? = null, repo: String? = null) {
        val project = activeProject.value
        val targetOwner = owner ?: project?.githubOwner.orEmpty()
        val targetRepo = repo ?: project?.githubRepo.orEmpty()
        if (targetOwner.isBlank() || targetRepo.isBlank()) return

        viewModelScope.launch {
            _githubData.update { it.copy(isLoading = true, errorMessage = null) }
            val result = gitHubRepository.fetchRepoWorkspace(
                owner = targetOwner,
                repo = targetRepo,
                personalAccessToken = localSettings.value.githubToken
            )
            result.onSuccess { data ->
                _githubData.value = data
            }.onFailure { err ->
                _githubData.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Could not load GitHub repository."
                    )
                }
            }
        }
    }

    // --- Milestone Email Notification Flow ---
    private fun triggerMilestoneEmailFlow(project: Project, milestoneTitle: String) {
        val tasks = (tasksState.value as? UiState.Success)?.data.orEmpty()
        val doneCount = tasks.count { it.taskStatus == TaskStatus.DONE }
        val baseDraft = MilestoneNotifier.buildMilestoneDraft(
            project = project,
            milestoneTitle = milestoneTitle,
            completedCount = doneCount,
            totalCount = max(1, tasks.size),
            completedByName = currentUserName
        )
        onMilestoneNotification(project.name, milestoneTitle)

        viewModelScope.launch {
            if (project.webhookUrl.startsWith("https://")) {
                val (sent, note) = MilestoneNotifier.tryAutoDispatchWebhook(project.webhookUrl, baseDraft)
                _pendingMilestoneEmail.value = baseDraft.copy(
                    webhookDispatched = sent,
                    webhookStatusNote = note
                )
            } else {
                _pendingMilestoneEmail.value = baseDraft
            }
        }
    }

    fun triggerManualMilestoneDigest() {
        val project = activeProject.value ?: return
        triggerMilestoneEmailFlow(project, "${project.sprintName} Progress Check-In")
    }

    fun dismissMilestoneEmailSheet() {
        _pendingMilestoneEmail.value = null
    }

    // --- ADHD Focus Timer ---
    fun toggleTimer() {
        if (_timerState.value.running) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        _timerState.update { it.copy(running = true) }
        timerJob = viewModelScope.launch {
            while (_timerState.value.running && _timerState.value.remainingSeconds > 0) {
                delay(1000L)
                _timerState.update { state ->
                    val next = (state.remainingSeconds - 1).coerceAtLeast(0)
                    state.copy(remainingSeconds = next)
                }
            }
            if (_timerState.value.remainingSeconds == 0) {
                _timerState.update {
                    it.copy(
                        running = false,
                        remainingSeconds = it.totalSeconds,
                        completedSessions = it.completedSessions + 1
                    )
                }
                _statusBanner.value = "Focus block complete! Take a short 5-minute breather."
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        _timerState.update { it.copy(running = false) }
    }

    fun resetTimer(minutes: Int = localSettings.value.focusDurationMinutes) {
        timerJob?.cancel()
        val sec = minutes.coerceIn(5, 90) * 60
        _timerState.value = _timerState.value.copy(
            totalSeconds = sec,
            remainingSeconds = sec,
            running = false
        )
    }

    // --- Preferences, Dark Mode, ADHD Reminders & Widget ---
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setCalmMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setCalmMode(enabled)
            projectRepository.updateUserFocusSettings(
                focusMinutes = localSettings.value.focusDurationMinutes,
                reminderIntervalMinutes = localSettings.value.reminderIntervalMinutes,
                calmModeEnabled = enabled
            )
        }
    }

    fun setFocusDuration(minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.setFocusDuration(minutes)
            resetTimer(minutes)
            projectRepository.updateUserFocusSettings(
                focusMinutes = minutes,
                reminderIntervalMinutes = localSettings.value.reminderIntervalMinutes,
                calmModeEnabled = localSettings.value.calmModeEnabled
            )
        }
    }

    fun setReminderInterval(minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.setReminderInterval(minutes)
            val tasks = (tasksState.value as? UiState.Success)?.data.orEmpty()
            val focusTitle = tasks.firstOrNull { it.focused && it.taskStatus != TaskStatus.DONE }?.title
                ?: tasks.firstOrNull { it.taskStatus != TaskStatus.DONE }?.title
                ?: "your sprint goal"
            onReminderSchedule(minutes, focusTitle)
            projectRepository.updateUserFocusSettings(
                focusMinutes = localSettings.value.focusDurationMinutes,
                reminderIntervalMinutes = minutes,
                calmModeEnabled = localSettings.value.calmModeEnabled
            )
            _statusBanner.value = if (minutes == 0) {
                "Gentle reminders paused."
            } else {
                "Gentle focus reminders scheduled every $minutes min."
            }
        }
    }

    fun setGitHubToken(token: String) {
        viewModelScope.launch {
            preferencesRepository.setGitHubToken(token)
            _statusBanner.value = if (token.isBlank()) "Local GitHub token cleared." else "GitHub token saved on-device."
            refreshGitHubRepo()
        }
    }

    fun updateWidgetConfig(config: WidgetConfig) {
        viewModelScope.launch {
            preferencesRepository.updateWidgetConfig(config)
            onWidgetRefresh()
            _statusBanner.value = "Home screen widget updated."
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun formatDeadlineCountdown(deadlineMs: Long): String {
        if (deadlineMs <= 0L) return "No deadline"
        val diffMs = deadlineMs - System.currentTimeMillis()
        val days = (diffMs / 86_400_000L).toInt()
        return when {
            days < 0 -> "Due ${-days}d ago"
            days == 0 -> "Due today"
            days == 1 -> "1 day left"
            days <= 14 -> "$days days left"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(deadlineMs))
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "ClearSprintVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
