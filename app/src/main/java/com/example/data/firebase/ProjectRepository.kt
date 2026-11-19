package com.example.data.firebase

import android.content.Context
import com.example.data.model.FileCategory
import com.example.data.model.Project
import com.example.data.model.ProjectFile
import com.example.data.model.ProjectRole
import com.example.data.model.SprintTask
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

class ProjectRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(FirebaseProvider.getFirestore(context))

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to access your workspace.")
    }

    private fun currentDisplayName(): String {
        val raw = auth.currentUser?.displayName?.trim().orEmpty()
        if (raw.isNotBlank()) return raw.take(60)
        val emailPrefix = auth.currentUser?.email?.substringBefore("@")?.trim().orEmpty()
        return if (emailPrefix.isNotBlank()) emailPrefix.take(60) else "Teammate"
    }

    // --- 1. Private User Profile ---
    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        if (userId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        focusMinutes: Int = 25,
        reminderIntervalMinutes: Int = 60,
        calmModeEnabled: Boolean = false
    ): Result<Unit> {
        var docPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid"
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to currentDisplayName(),
                    "focusMinutes" to focusMinutes.coerceIn(5, 90),
                    "reminderIntervalMinutes" to reminderIntervalMinutes.coerceIn(0, 240),
                    "calmModeEnabled" to calmModeEnabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                auth.currentUser?.email?.take(120)?.let { payload["email"] = it }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateUserFocusSettings(
        focusMinutes: Int,
        reminderIntervalMinutes: Int,
        calmModeEnabled: Boolean
    ): Result<Unit> {
        var docPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid"
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(
                    mapOf(
                        "focusMinutes" to focusMinutes.coerceIn(5, 90),
                        "reminderIntervalMinutes" to reminderIntervalMinutes.coerceIn(0, 240),
                        "calmModeEnabled" to calmModeEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ensureUserProfile(focusMinutes, reminderIntervalMinutes, calmModeEnabled).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- 2. Team Projects ---
    fun observeProjects(userId: String): Flow<List<Project>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "projects"
        emitAll(
            db.collection("projects")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Project::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun createProject(
        name: String,
        description: String,
        sprintName: String,
        sprintDeadlineMs: Long,
        githubOwner: String,
        githubRepo: String,
        milestoneEmails: List<String>
    ): Result<String> {
        var docPath = "projects"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Project name cannot be empty." }
            val cleanSprint = sprintName.trim().ifBlank { "Sprint 1" }.take(60)
            val cleanDesc = description.trim().take(400)
            val cleanOwner = githubOwner.trim().take(80)
            val cleanRepo = githubRepo.trim().take(100)
            val cleanEmails = milestoneEmails.map { it.trim().take(120) }.filter { it.contains("@") }.take(10)
            val inviteCode = generateInviteCode()
            val displayName = currentDisplayName()

            val projectRef = db.collection("projects").document()
            docPath = projectRef.path

            val projectPayload = mapOf(
                "ownerId" to uid,
                "name" to cleanName,
                "description" to cleanDesc,
                "sprintName" to cleanSprint,
                "sprintDeadlineMs" to sprintDeadlineMs.coerceAtLeast(0L),
                "githubOwner" to cleanOwner,
                "githubRepo" to cleanRepo,
                "inviteCode" to inviteCode,
                "webhookUrl" to "",
                "memberUids" to listOf(uid),
                "roles" to mapOf(uid to ProjectRole.OWNER.name),
                "memberNames" to mapOf(uid to displayName),
                "milestoneEmails" to cleanEmails,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            projectRef.set(projectPayload).await()

            val invitePath = "project_invites/$inviteCode"
            docPath = invitePath
            db.collection("project_invites").document(inviteCode).set(
                mapOf(
                    "projectId" to projectRef.id,
                    "ownerId" to uid,
                    "projectName" to cleanName,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(projectRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun joinProjectByInviteCode(rawCode: String): Result<String> {
        val code = rawCode.trim().uppercase()
        var docPath = "project_invites/$code"
        return try {
            val uid = requireUserId()
            require(code.matches(Regex("^[A-Z0-9]{6,12}$"))) {
                "Enter a valid 6-character team invite code."
            }
            val inviteDoc = db.collection("project_invites").document(code).get().await()
            if (!inviteDoc.exists()) {
                return Result.failure(IllegalArgumentException("No team project found for code $code."))
            }
            val projectId = inviteDoc.getString("projectId")
                ?: return Result.failure(IllegalStateException("Invite record is missing projectId."))

            docPath = "projects/$projectId"
            val displayName = currentDisplayName()
            db.collection("projects").document(projectId).update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "roles.$uid" to ProjectRole.EDITOR.name,
                    "memberNames.$uid" to displayName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(projectId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProjectSettings(
        projectId: String,
        name: String,
        description: String,
        sprintName: String,
        sprintDeadlineMs: Long,
        githubOwner: String,
        githubRepo: String,
        webhookUrl: String,
        milestoneEmails: List<String>
    ): Result<Unit> {
        val docPath = "projects/$projectId"
        return try {
            requireUserId()
            require(projectId.isNotBlank()) { "Project ID required" }
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Project name cannot be empty" }
            val cleanEmails = milestoneEmails.map { it.trim().take(120) }.filter { it.contains("@") }.take(10)

            db.collection("projects").document(projectId).update(
                mapOf(
                    "name" to cleanName,
                    "description" to description.trim().take(400),
                    "sprintName" to sprintName.trim().ifBlank { "Sprint 1" }.take(60),
                    "sprintDeadlineMs" to sprintDeadlineMs.coerceAtLeast(0L),
                    "githubOwner" to githubOwner.trim().take(80),
                    "githubRepo" to githubRepo.trim().take(100),
                    "webhookUrl" to webhookUrl.trim().take(300),
                    "milestoneEmails" to cleanEmails,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMemberRole(
        project: Project,
        targetUid: String,
        newRole: ProjectRole
    ): Result<Unit> {
        val docPath = "projects/${project.id}"
        return try {
            requireUserId()
            require(targetUid != project.ownerId) { "Project owner role cannot be changed." }
            require(newRole != ProjectRole.OWNER) { "Cannot assign secondary OWNER role." }
            val updatedRoles = project.roles.toMutableMap().apply {
                this[targetUid] = newRole.name
            }
            db.collection("projects").document(project.id).update(
                mapOf(
                    "roles" to updatedRoles,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun removeMember(project: Project, targetUid: String): Result<Unit> {
        val docPath = "projects/${project.id}"
        return try {
            requireUserId()
            require(targetUid != project.ownerId) { "Project owner cannot be removed." }
            val updatedUids = project.memberUids.filterNot { it == targetUid }
            val updatedRoles = project.roles.filterKeys { it != targetUid }
            val updatedNames = project.memberNames.filterKeys { it != targetUid }
            db.collection("projects").document(project.id).update(
                mapOf(
                    "memberUids" to updatedUids,
                    "roles" to updatedRoles,
                    "memberNames" to updatedNames,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- 3. Sprint Tasks & Milestones ---
    fun observeTasks(projectId: String, userId: String): Flow<List<SprintTask>> = flow {
        if (projectId.isBlank() || userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "projects/$projectId/tasks"
        emitAll(
            db.collection("projects").document(projectId).collection("tasks")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(SprintTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<SprintTask> { it.focused && it.taskStatus != TaskStatus.DONE }
                                .thenBy { it.taskStatus == TaskStatus.DONE }
                                .thenBy { if (it.dueDateMs > 0L) it.dueDateMs else Long.MAX_VALUE }
                                .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                        )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun createTask(
        project: Project,
        title: String,
        description: String,
        priority: TaskPriority,
        assigneeUid: String,
        assigneeName: String,
        dueDateMs: Long,
        estimateMinutes: Int,
        milestone: Boolean,
        focused: Boolean,
        substeps: List<String>,
        githubIssueNumber: Long = 0L
    ): Result<String> {
        var docPath = "projects/${project.id}/tasks"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(120)
            require(cleanTitle.isNotEmpty()) { "Task title is required." }
            val cleanSubsteps = substeps.map { it.trim().take(140) }.filter { it.isNotEmpty() }.take(10)
            val members = if (uid in project.memberUids) project.memberUids else (project.memberUids + uid).take(25)

            val taskRef = db.collection("projects").document(project.id).collection("tasks").document()
            docPath = taskRef.path
            val payload = mapOf(
                "projectId" to project.id,
                "creatorId" to uid,
                "title" to cleanTitle,
                "description" to description.trim().take(1000),
                "status" to TaskStatus.TODO.name,
                "priority" to priority.name,
                "assigneeUid" to assigneeUid.take(128),
                "assigneeName" to assigneeName.trim().take(60),
                "dueDateMs" to dueDateMs.coerceAtLeast(0L),
                "estimateMinutes" to estimateMinutes.coerceIn(5, 480),
                "milestone" to milestone,
                "focused" to focused,
                "substeps" to cleanSubsteps,
                "completedSubsteps" to emptyList<String>(),
                "githubIssueNumber" to githubIssueNumber.coerceAtLeast(0L),
                "memberUids" to members,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            taskRef.set(payload).await()
            Result.success(taskRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTask(
        projectId: String,
        taskId: String,
        updates: Map<String, Any>
    ): Result<Unit> {
        val docPath = "projects/$projectId/tasks/$taskId"
        return try {
            requireUserId()
            require(projectId.isNotBlank() && taskId.isNotBlank()) { "Valid project and task IDs required." }
            val payload = updates.toMutableMap().apply {
                this["updatedAt"] = FieldValue.serverTimestamp()
            }
            db.collection("projects").document(projectId)
                .collection("tasks").document(taskId)
                .update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(projectId: String, taskId: String): Result<Unit> {
        val docPath = "projects/$projectId/tasks/$taskId"
        return try {
            requireUserId()
            db.collection("projects").document(projectId)
                .collection("tasks").document(taskId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- 4. Searchable Project Files ---
    fun observeProjectFiles(projectId: String, userId: String): Flow<List<ProjectFile>> = flow {
        if (projectId.isBlank() || userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "projects/$projectId/files"
        emitAll(
            db.collection("projects").document(projectId).collection("files")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ProjectFile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun createProjectFile(
        project: Project,
        name: String,
        pathValue: String,
        category: FileCategory,
        url: String,
        notes: String,
        tags: List<String>
    ): Result<String> {
        var docPath = "projects/${project.id}/files"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(120)
            val cleanPath = pathValue.trim().ifBlank { cleanName }.take(240)
            require(cleanName.isNotEmpty()) { "File name is required." }
            val cleanTags = tags.map { it.trim().take(40) }.filter { it.isNotEmpty() }.take(8)
            val members = if (uid in project.memberUids) project.memberUids else (project.memberUids + uid).take(25)

            val fileRef = db.collection("projects").document(project.id).collection("files").document()
            docPath = fileRef.path
            fileRef.set(
                mapOf(
                    "projectId" to project.id,
                    "creatorId" to uid,
                    "name" to cleanName,
                    "path" to cleanPath,
                    "fileType" to category.name,
                    "url" to url.trim().take(300),
                    "notes" to notes.trim().take(600),
                    "tags" to cleanTags,
                    "memberUids" to members,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(fileRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProjectFile(projectId: String, fileId: String): Result<Unit> {
        val docPath = "projects/$projectId/files/$fileId"
        return try {
            requireUserId()
            db.collection("projects").document(projectId)
                .collection("files").document(fileId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- 5. Automatic Subcollection Membership Sync (for newly joined teammates) ---
    suspend fun syncSubcollectionMemberUids(
        project: Project,
        tasks: List<SprintTask>,
        files: List<ProjectFile>
    ) {
        val uid = auth.currentUser?.uid ?: return
        val role = project.roleFor(uid)
        if (role != ProjectRole.OWNER && role != ProjectRole.ADMIN) return
        val expectedSet = project.memberUids.toSet()

        val staleTasks = tasks.filter { it.memberUids.toSet() != expectedSet }
        val staleFiles = files.filter { it.memberUids.toSet() != expectedSet }
        if (staleTasks.isEmpty() && staleFiles.isEmpty()) return

        try {
            val batch = db.batch()
            staleTasks.take(100).forEach { task ->
                val ref = db.collection("projects").document(project.id)
                    .collection("tasks").document(task.id)
                batch.update(
                    ref,
                    mapOf(
                        "memberUids" to project.memberUids,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            staleFiles.take(100).forEach { file ->
                val ref = db.collection("projects").document(project.id)
                    .collection("files").document(file.id)
                batch.update(
                    ref,
                    mapOf(
                        "memberUids" to project.memberUids,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "projects/${project.id}/sync")
        }
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (1..6).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
}
