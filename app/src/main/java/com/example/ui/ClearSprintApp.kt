package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.firebase.FirebaseProvider
import com.example.data.firebase.ProjectRepository
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.signOutUser
import com.example.data.github.GitHubRepository
import com.example.data.local.NetworkMonitor
import com.example.data.local.PreferencesRepository
import com.example.data.model.LocalSettings
import com.example.data.model.TaskStatus
import com.example.data.model.UiState
import com.example.notifications.AdhdReminderScheduler
import com.example.notifications.MilestoneNotifier
import com.example.ui.components.CreateProjectDialog
import com.example.ui.components.EditProjectSettingsDialog
import com.example.ui.components.JoinProjectDialog
import com.example.ui.components.MilestoneEmailDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CreateTaskDialog
import com.example.ui.screens.FocusDashboardScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.TeamHubScreen
import com.example.ui.theme.ClearSprintTheme
import com.example.viewmodel.ClearSprintViewModel
import com.example.widget.ClearSprintWidgetProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

enum class AppDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    FOCUS("focus", R.string.nav_focus, Icons.Filled.CenterFocusStrong, Icons.Outlined.CenterFocusStrong),
    TASKS("tasks", R.string.nav_tasks, Icons.Filled.CheckCircle, Icons.Outlined.CheckCircleOutline),
    SEARCH("search", R.string.nav_search, Icons.Filled.Search, Icons.Outlined.Search),
    TEAM("team", R.string.nav_team, Icons.Filled.Groups, Icons.Outlined.Groups)
}

@Composable
fun ClearSprintRoot(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val prefsRepo = remember(context) { PreferencesRepository(context.applicationContext) }
    val localSettings by prefsRepo.settingsFlow.collectAsStateWithLifecycle(initialValue = LocalSettings())
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    ClearSprintTheme(themeMode = localSettings.themeMode) {
        val user = currentUser
        if (user == null) {
            AuthScreen(onAuthSuccess = { /* authStateFlow automatically transitions */ })
        } else {
            val uid = user.uid
            val displayName = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "Teammate"
            val email = user.email.orEmpty()

            key(uid) {
                AuthenticatedWorkspace(
                    currentUserId = uid,
                    currentUserName = displayName,
                    currentUserEmail = email
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedWorkspace(
    currentUserId: String,
    currentUserName: String,
    currentUserEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: ClearSprintViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val db = FirebaseProvider.getFirestore(app)
                ClearSprintViewModel(
                    currentUserId = currentUserId,
                    currentUserName = currentUserName,
                    currentUserEmail = currentUserEmail,
                    projectRepository = ProjectRepository(db),
                    gitHubRepository = GitHubRepository(),
                    preferencesRepository = PreferencesRepository(app),
                    networkMonitor = NetworkMonitor(app),
                    onWidgetRefresh = { ClearSprintWidgetProvider.refreshAllWidgets(app) },
                    onMilestoneNotification = { projName, milestoneTitle ->
                        AdhdReminderScheduler.postMilestoneReachedNotification(app, projName, milestoneTitle)
                    },
                    onReminderSchedule = { mins, taskTitle ->
                        AdhdReminderScheduler.scheduleGentleCheckIn(app, mins, taskTitle)
                    }
                )
            }
        }
    )

    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val filesState by viewModel.filesState.collectAsStateWithLifecycle()
    val githubData by viewModel.githubData.collectAsStateWithLifecycle()
    val localSettings by viewModel.localSettings.collectAsStateWithLifecycle()
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val pendingMilestoneEmail by viewModel.pendingMilestoneEmail.collectAsStateWithLifecycle()

    var currentDestination by remember { mutableStateOf(AppDestination.FOCUS) }
    var showCreateProject by remember { mutableStateOf(false) }
    var showJoinProject by remember { mutableStateOf(false) }
    var showEditSettings by remember { mutableStateOf(false) }
    var showCreateTask by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    // Back button from secondary tabs returns cleanly to Focus home screen
    BackHandler(enabled = currentDestination != AppDestination.FOCUS) {
        currentDestination = AppDestination.FOCUS
    }

    val projects = (projectsState as? UiState.Success)?.data.orEmpty()
    val tasks = (tasksState as? UiState.Success)?.data.orEmpty()
    val files = (filesState as? UiState.Success)?.data.orEmpty()
    val streamError = (projectsState as? UiState.Error)?.message
        ?: (tasksState as? UiState.Error)?.message
        ?: (filesState as? UiState.Error)?.message

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        AppDestination.entries.forEach { dest ->
                            val selected = currentDestination == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentDestination = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (activeProject != null && capabilities.canEditContent &&
                    (currentDestination == AppDestination.FOCUS || currentDestination == AppDestination.TASKS)
                ) {
                    FloatingActionButton(
                        onClick = { showCreateTask = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("quick_add_task_fab")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add sprint task")
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight().statusBarsPadding()) {
                        AppDestination.entries.forEach { dest ->
                            val selected = currentDestination == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentDestination = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Calm subtle Offline Mode banner
                    AnimatedVisibility(visible = !isOnline) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.offline_banner),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Surface any Firestore stream error calmly without crashing
                    if (streamError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = streamError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (currentDestination) {
                            AppDestination.FOCUS -> FocusDashboardScreen(
                                project = activeProject,
                                tasks = tasks,
                                capabilities = capabilities,
                                calmModeEnabled = localSettings.calmModeEnabled,
                                timerState = timerState,
                                formatDeadline = viewModel::formatDeadlineCountdown,
                                onToggleCalmMode = viewModel::setCalmMode,
                                onToggleTimer = viewModel::toggleTimer,
                                onResetTimer = { viewModel.resetTimer() },
                                onCompleteTask = { task -> viewModel.updateTaskStatus(task, TaskStatus.DONE) },
                                onToggleSubstep = viewModel::toggleSubstep,
                                onPinFocusTask = viewModel::pinFocusTask,
                                onCreateProjectClick = { showCreateProject = true },
                                onJoinProjectClick = { showJoinProject = true },
                                onQuickAddTaskClick = { showCreateTask = true }
                            )

                            AppDestination.TASKS -> TasksScreen(
                                project = activeProject,
                                tasks = tasks,
                                capabilities = capabilities,
                                formatDeadline = viewModel::formatDeadlineCountdown,
                                onUpdateStatus = viewModel::updateTaskStatus,
                                onToggleSubstep = viewModel::toggleSubstep,
                                onPinFocus = viewModel::pinFocusTask,
                                onDeleteTask = viewModel::deleteTask
                            )

                            AppDestination.SEARCH -> SearchScreen(
                                project = activeProject,
                                tasks = tasks,
                                projectFiles = files,
                                githubData = githubData,
                                capabilities = capabilities,
                                onAddProjectFile = viewModel::addProjectFile,
                                onDeleteProjectFile = viewModel::deleteProjectFile
                            )

                            AppDestination.TEAM -> TeamHubScreen(
                                projects = projects,
                                activeProject = activeProject,
                                tasks = tasks,
                                capabilities = capabilities,
                                githubData = githubData,
                                settings = localSettings,
                                currentUserName = currentUserName,
                                currentUserEmail = currentUserEmail,
                                formatDeadline = viewModel::formatDeadlineCountdown,
                                onSelectProject = viewModel::selectProject,
                                onCreateProjectClick = { showCreateProject = true },
                                onJoinProjectClick = { showJoinProject = true },
                                onEditSettingsClick = { showEditSettings = true },
                                onUpdateMemberRole = viewModel::updateMemberRole,
                                onRemoveMember = viewModel::removeMember,
                                onRefreshGitHub = { viewModel.refreshGitHubRepo() },
                                onImportIssue = viewModel::importGitHubIssueAsTask,
                                onSaveGitHubToken = viewModel::setGitHubToken,
                                onThemeModeChange = viewModel::setThemeMode,
                                onFocusDurationChange = viewModel::setFocusDuration,
                                onReminderIntervalChange = viewModel::setReminderInterval,
                                onUpdateWidgetConfig = viewModel::updateWidgetConfig,
                                onSendMilestoneDigest = viewModel::triggerManualMilestoneDigest,
                                onShowMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                },
                                onSignOutClick = {
                                    signOutUser(
                                        credentialManager = credentialManager,
                                        scope = scope
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs & Milestone Email Automation Sheet
    if (showCreateProject) {
        CreateProjectDialog(
            onDismiss = { showCreateProject = false },
            onCreate = viewModel::createProject
        )
    }
    if (showJoinProject) {
        JoinProjectDialog(
            onDismiss = { showJoinProject = false },
            onJoin = viewModel::joinProjectByCode
        )
    }
    if (showEditSettings && activeProject != null) {
        EditProjectSettingsDialog(
            project = activeProject!!,
            onDismiss = { showEditSettings = false },
            onSave = viewModel::updateActiveProjectSettings
        )
    }
    if (showCreateTask && activeProject != null) {
        CreateTaskDialog(
            project = activeProject!!,
            currentUserId = currentUserId,
            currentUserName = currentUserName,
            onDismiss = { showCreateTask = false },
            onCreate = { title, desc, priority, uid, name, days, est, milestone, focused, substeps ->
                viewModel.createTask(
                    title = title,
                    description = desc,
                    priority = priority,
                    assigneeUid = uid,
                    assigneeName = name,
                    daysUntilDue = days,
                    estimateMinutes = est,
                    milestone = milestone,
                    focused = focused,
                    substepsRaw = substeps
                )
            }
        )
    }
    pendingMilestoneEmail?.let { draft ->
        MilestoneEmailDialog(
            draft = draft,
            onSendViaEmailApp = { MilestoneNotifier.launchEmailComposer(context, draft) },
            onDismiss = viewModel::dismissMilestoneEmailSheet
        )
    }
}
