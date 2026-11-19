package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.github.GitHubIssueDto
import com.example.data.github.GitHubWorkspaceData
import com.example.data.model.LocalSettings
import com.example.data.model.Project
import com.example.data.model.ProjectRole
import com.example.data.model.SprintTask
import com.example.data.model.TaskStatus
import com.example.data.model.ThemeMode
import com.example.data.model.WidgetConfig
import com.example.ui.components.FocusAndWidgetPanel
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.RoleCapabilities

private enum class HubSection(val label: String) {
    WORKSPACE("Team"),
    GITHUB("GitHub"),
    FOCUS_WIDGET("Focus & Widget")
}

@Composable
fun TeamHubScreen(
    projects: List<Project>,
    activeProject: Project?,
    tasks: List<SprintTask>,
    capabilities: RoleCapabilities,
    githubData: GitHubWorkspaceData,
    settings: LocalSettings,
    currentUserName: String,
    currentUserEmail: String,
    formatDeadline: (Long) -> String,
    onSelectProject: (String) -> Unit,
    onCreateProjectClick: () -> Unit,
    onJoinProjectClick: () -> Unit,
    onEditSettingsClick: () -> Unit,
    onUpdateMemberRole: (String, ProjectRole) -> Unit,
    onRemoveMember: (String) -> Unit,
    onRefreshGitHub: () -> Unit,
    onImportIssue: (GitHubIssueDto) -> Unit,
    onSaveGitHubToken: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onFocusDurationChange: (Int) -> Unit,
    onReminderIntervalChange: (Int) -> Unit,
    onUpdateWidgetConfig: (WidgetConfig) -> Unit,
    onSendMilestoneDigest: () -> Unit,
    onShowMessage: (String) -> Unit,
    onSignOutClick: () -> Unit
) {
    var section by remember { mutableStateOf(HubSection.WORKSPACE) }

    val doneCount = tasks.count { it.taskStatus == TaskStatus.DONE }
    val progressPct = if (tasks.isEmpty()) 0 else (doneCount * 100) / tasks.size
    val openTasks = tasks.filter { it.taskStatus != TaskStatus.DONE }
    val focusTaskTitle = openTasks.firstOrNull { it.focused }?.title
        ?: openTasks.firstOrNull()?.title
        ?: "All sprint steps complete"

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Team & Controls", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                text = "$currentUserName · $currentUserEmail",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onSignOutClick,
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = "Sign out"
                            )
                        }
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        HubSection.entries.forEachIndexed { idx, item ->
                            SegmentedButton(
                                selected = section == item,
                                onClick = { section = item },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = idx,
                                    count = HubSection.entries.size
                                ),
                                modifier = Modifier.testTag("hub_tab_${item.name.lowercase()}")
                            ) {
                                Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            when (section) {
                HubSection.WORKSPACE -> {
                    item {
                        WorkspaceAndRbacSection(
                            projects = projects,
                            activeProject = activeProject,
                            capabilities = capabilities,
                            onSelectProject = onSelectProject,
                            onCreateProjectClick = onCreateProjectClick,
                            onJoinProjectClick = onJoinProjectClick,
                            onEditSettingsClick = onEditSettingsClick,
                            onUpdateMemberRole = onUpdateMemberRole,
                            onRemoveMember = onRemoveMember,
                            onShowMessage = onShowMessage
                        )
                    }
                }
                HubSection.GITHUB -> {
                    item {
                        GitHubIntegrationSection(
                            project = activeProject,
                            githubData = githubData,
                            canEditContent = capabilities.canEditContent,
                            canManageSettings = capabilities.canManageSettings,
                            savedToken = settings.githubToken,
                            onEditSettingsClick = onEditSettingsClick,
                            onRefreshGitHub = onRefreshGitHub,
                            onImportIssue = onImportIssue,
                            onSaveToken = onSaveGitHubToken
                        )
                    }
                }
                HubSection.FOCUS_WIDGET -> {
                    item {
                        FocusAndWidgetPanel(
                            project = activeProject,
                            settings = settings,
                            focusTaskTitle = focusTaskTitle,
                            sprintProgressPct = progressPct,
                            deadlineLabel = activeProject?.let { formatDeadline(it.sprintDeadlineMs) } ?: "No deadline",
                            onThemeModeChange = onThemeModeChange,
                            onFocusDurationChange = onFocusDurationChange,
                            onReminderIntervalChange = onReminderIntervalChange,
                            onUpdateWidgetConfig = onUpdateWidgetConfig,
                            onSendMilestoneDigest = onSendMilestoneDigest,
                            onShowMessage = onShowMessage
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun WorkspaceAndRbacSection(
    projects: List<Project>,
    activeProject: Project?,
    capabilities: RoleCapabilities,
    onSelectProject: (String) -> Unit,
    onCreateProjectClick: () -> Unit,
    onJoinProjectClick: () -> Unit,
    onEditSettingsClick: () -> Unit,
    onUpdateMemberRole: (String, ProjectRole) -> Unit,
    onRemoveMember: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Project switcher + Create/Join
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Workspaces", style = MaterialTheme.typography.titleMedium)
                    if (activeProject != null && capabilities.canManageSettings) {
                        IconButton(
                            onClick = onEditSettingsClick,
                            modifier = Modifier.testTag("edit_project_settings_button")
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Workspace settings")
                        }
                    }
                }

                if (projects.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        projects.forEach { proj ->
                            val selected = proj.id == activeProject?.id
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onSelectProject(proj.id) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = proj.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Text(
                                            text = "${proj.sprintName} · ${proj.memberUids.size} members",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (selected) {
                                        Text(
                                            text = "Active",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onCreateProjectClick,
                        modifier = Modifier.weight(1f).testTag("create_project_button")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Project")
                    }
                    OutlinedButton(
                        onClick = onJoinProjectClick,
                        modifier = Modifier.weight(1f).testTag("join_project_button")
                    ) {
                        Icon(Icons.Outlined.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Join Code")
                    }
                }
            }
        }

        // Team Invite Code + RBAC Member Access Control
        if (activeProject != null) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Role-Based Access Control", style = MaterialTheme.typography.titleMedium)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    clipboard.setText(AnnotatedString(activeProject.inviteCode))
                                    onShowMessage("Invite code ${activeProject.inviteCode} copied!")
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = activeProject.inviteCode,
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy invite code", modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Text(
                        text = "Share the invite code above so Buildathon teammates join as Editors. Owners and Admins can adjust permissions below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    activeProject.memberUids.forEach { memberUid ->
                        val memberName = activeProject.memberNames[memberUid] ?: "Teammate"
                        val role = activeProject.roleFor(memberUid)
                        val isOwner = memberUid == activeProject.ownerId
                        var menuOpen by remember(memberUid) { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = memberName.take(2).uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = memberName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = role.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box {
                                FilterChip(
                                    selected = isOwner,
                                    onClick = { if (capabilities.canManageRoles && !isOwner) menuOpen = true },
                                    label = { Text(role.label) }
                                )
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false }
                                ) {
                                    listOf(ProjectRole.ADMIN, ProjectRole.EDITOR, ProjectRole.VIEWER).forEach { r ->
                                        DropdownMenuItem(
                                            text = { Text("${r.label} — ${r.description}") },
                                            onClick = {
                                                menuOpen = false
                                                onUpdateMemberRole(memberUid, r)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Remove member", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menuOpen = false
                                            onRemoveMember(memberUid)
                                        }
                                    )
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
private fun GitHubIntegrationSection(
    project: Project?,
    githubData: GitHubWorkspaceData,
    canEditContent: Boolean,
    canManageSettings: Boolean,
    savedToken: String,
    onEditSettingsClick: () -> Unit,
    onRefreshGitHub: () -> Unit,
    onImportIssue: (GitHubIssueDto) -> Unit,
    onSaveToken: (String) -> Unit
) {
    val context = LocalContext.current
    var tokenInput by remember(savedToken) { mutableStateOf(savedToken) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("GitHub Repository", style = MaterialTheme.typography.titleMedium)
                    }
                    if (project != null && project.hasGitHubRepo()) {
                        IconButton(onClick = onRefreshGitHub, modifier = Modifier.testTag("refresh_github_button")) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh GitHub")
                        }
                    }
                }

                if (project == null || !project.hasGitHubRepo()) {
                    Text(
                        text = "Connect a public or private GitHub repository to track live commits, open issues, and search repository files for free.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (project != null && canManageSettings) {
                        FilledTonalButton(onClick = onEditSettingsClick) {
                            Text("Connect GitHub Repository")
                        }
                    }
                } else {
                    Text(
                        text = "${project.githubOwner}/${project.githubRepo}",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (githubData.isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Syncing repository…", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    githubData.errorMessage?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    githubData.repo?.let { repo ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Branch: ${repo.defaultBranch}", style = MaterialTheme.typography.labelSmall)
                            Text("Open Issues: ${repo.openIssues}", style = MaterialTheme.typography.labelSmall)
                            repo.language?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        }
                        if (!repo.description.isNullOrBlank()) {
                            Text(
                                text = repo.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repo.htmlUrl)))
                                }
                            }
                        ) {
                            Text("Open on GitHub")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Recent Commits
        if (githubData.commits.isNotEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Recent Commits", style = MaterialTheme.typography.titleMedium)
                    githubData.commits.take(6).forEach { item ->
                        val firstLine = item.commit.message.lineSequence().firstOrNull().orEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.htmlUrl)))
                                    }
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.sha.take(7),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = firstLine,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.commit.author?.name ?: "Contributor",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Open Issues -> One-tap Import to Sprint Board
        if (githubData.issues.isNotEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Open Issues & PRs", style = MaterialTheme.typography.titleMedium)
                    githubData.issues.take(8).forEach { issue ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "#${issue.number} ${issue.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (issue.pullRequest != null) "Pull Request" else "Issue",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (canEditContent) {
                                FilledTonalButton(onClick = { onImportIssue(issue) }) {
                                    Text("Import")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Optional local GitHub PAT (stored strictly on-device in DataStore)
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Optional GitHub Token (On-Device Only)", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Public repos work with zero setup. Add a personal token locally only for private repos or higher API limits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    placeholder = { Text("ghp_… (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onSaveToken(tokenInput) }) {
                        Text("Save Token")
                    }
                    if (savedToken.isNotBlank()) {
                        TextButton(onClick = {
                            tokenInput = ""
                            onSaveToken("")
                        }) {
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }
}
