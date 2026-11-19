package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.github.GitHubWorkspaceData
import com.example.data.model.FileCategory
import com.example.data.model.Project
import com.example.data.model.ProjectFile
import com.example.data.model.SprintTask
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.RoleCapabilities

private enum class SearchScope(val label: String) {
    ALL("All"),
    PROJECT_FILES("Project Files"),
    GITHUB_FILES("GitHub Repo"),
    TASKS("Tasks")
}

@Composable
fun SearchScreen(
    project: Project?,
    tasks: List<SprintTask>,
    projectFiles: List<ProjectFile>,
    githubData: GitHubWorkspaceData,
    capabilities: RoleCapabilities,
    onAddProjectFile: (String, String, FileCategory, String, String, String) -> Unit,
    onDeleteProjectFile: (ProjectFile) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(SearchScope.ALL) }
    var showAddFileDialog by remember { mutableStateOf(false) }

    if (project == null) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Select or create a project to search files, repo paths, and tasks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val normalized = query.trim().lowercase()

    val matchingFiles = remember(projectFiles, normalized, scope) {
        if (scope != SearchScope.ALL && scope != SearchScope.PROJECT_FILES) emptyList()
        else projectFiles.filter { file ->
            normalized.isEmpty() ||
                file.name.lowercase().contains(normalized) ||
                file.path.lowercase().contains(normalized) ||
                file.notes.lowercase().contains(normalized) ||
                file.tags.any { it.lowercase().contains(normalized) }
        }
    }

    val matchingGithubFiles = remember(githubData.files, normalized, scope) {
        if (scope != SearchScope.ALL && scope != SearchScope.GITHUB_FILES) emptyList()
        else githubData.files.filter { entry ->
            normalized.isEmpty() || entry.path.lowercase().contains(normalized)
        }.take(if (normalized.isEmpty()) 12 else 40)
    }

    val matchingTasks = remember(tasks, normalized, scope) {
        if (scope != SearchScope.ALL && scope != SearchScope.TASKS) emptyList()
        else tasks.filter { task ->
            normalized.isEmpty() ||
                task.title.lowercase().contains(normalized) ||
                task.description.lowercase().contains(normalized) ||
                task.assigneeName.lowercase().contains(normalized) ||
                task.substeps.any { it.lowercase().contains(normalized) }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Search Workspace",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        if (capabilities.canEditContent) {
                            FilledTonalButton(
                                onClick = { showAddFileDialog = true },
                                modifier = Modifier.testTag("add_project_file_button")
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Index File", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search files, GitHub paths, tasks, or tags…") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().testTag("global_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchScope.entries.forEach { item ->
                            FilterChip(
                                selected = scope == item,
                                onClick = { scope = item },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }

            if (matchingFiles.isNotEmpty()) {
                item {
                    Text(
                        text = "PROJECT FILES (${matchingFiles.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(matchingFiles, key = { "file_${it.id}" }) { file ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(enabled = file.url.startsWith("http")) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(file.url)))
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${file.category.label} · ${file.path}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (file.notes.isNotBlank()) {
                                    Text(
                                        text = file.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (file.url.startsWith("http")) {
                                Icon(
                                    imageVector = Icons.Outlined.OpenInNew,
                                    contentDescription = "Open external link",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (capabilities.canEditContent) {
                                IconButton(onClick = { onDeleteProjectFile(file) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete ${file.name}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (matchingGithubFiles.isNotEmpty()) {
                item {
                    Text(
                        text = "GITHUB REPOSITORY FILES (${matchingGithubFiles.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(matchingGithubFiles, key = { "gh_${it.path}" }) { entry ->
                    val branch = githubData.repo?.defaultBranch ?: "main"
                    val ghUrl = if (project.hasGitHubRepo()) {
                        "https://github.com/${project.githubOwner}/${project.githubRepo}/blob/$branch/${entry.path}"
                    } else ""
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(enabled = ghUrl.isNotEmpty()) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ghUrl)))
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.path.substringAfterLast('/'),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = entry.path,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.OpenInNew,
                                contentDescription = "View on GitHub",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (matchingTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "MATCHING TASKS (${matchingTasks.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(matchingTasks, key = { "task_${it.id}" }) { task ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.TaskAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${task.taskStatus.label} · ${task.estimateMinutes}m" +
                                        if (task.assigneeName.isNotBlank()) " · ${task.assigneeName}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (matchingFiles.isEmpty() && matchingGithubFiles.isEmpty() && matchingTasks.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("No matches found", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Try a different keyword, index a project file, or link your team's GitHub repo in Team.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showAddFileDialog) {
        AddProjectFileDialog(
            onDismiss = { showAddFileDialog = false },
            onAdd = onAddProjectFile
        )
    }
}

@Composable
private fun AddProjectFileDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, FileCategory, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(FileCategory.DOC) }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Index Project File", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    label = { Text("File or doc name") },
                    placeholder = { Text("Architecture.md") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("file_name_input")
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it.take(240) },
                    label = { Text("Path or folder") },
                    placeholder = { Text("docs/Architecture.md") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FileCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.take(300) },
                    label = { Text("URL (GitHub, Drive, Figma)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(600) },
                    label = { Text("Summary notes") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it.take(120) },
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("api, pitch, ui") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(name, path, category, url, notes, tags)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_file_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
