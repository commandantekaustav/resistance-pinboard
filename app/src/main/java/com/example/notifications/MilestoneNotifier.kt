package com.example.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.model.MilestoneEmailDraft
import com.example.data.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object MilestoneNotifier {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    const val FREE_APPS_SCRIPT_TEMPLATE = """function doPost(e) {
  var data = JSON.parse(e.postData.contents);
  if (data.recipients && data.recipients.length > 0) {
    MailApp.sendEmail({
      to: data.recipients.join(","),
      subject: data.subject,
      body: data.body
    });
  }
  return ContentService.createTextOutput("OK");
}"""

    fun buildMilestoneDraft(
        project: Project,
        milestoneTitle: String,
        completedCount: Int,
        totalCount: Int,
        completedByName: String
    ): MilestoneEmailDraft {
        val pct = if (totalCount > 0) ((completedCount * 100) / totalCount) else 100
        val deadlineStr = if (project.sprintDeadlineMs > 0L) {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(project.sprintDeadlineMs))
        } else {
            "Ongoing"
        }
        val repoLine = if (project.hasGitHubRepo()) {
            "GitHub Repo: https://github.com/${project.githubOwner}/${project.githubRepo}\n"
        } else {
            ""
        }
        val subject = "[ClearSprint] Milestone Reached: $milestoneTitle (${project.sprintName} - $pct%)"
        val body = buildString {
            appendLine("Team Milestone Update — ${project.name}")
            appendLine("----------------------------------------")
            appendLine("Milestone Completed: $milestoneTitle")
            appendLine("Completed By: $completedByName")
            appendLine("Sprint: ${project.sprintName} ($pct% complete — $completedCount/$totalCount tasks)")
            appendLine("Sprint Deadline: $deadlineStr")
            if (repoLine.isNotEmpty()) append(repoLine)
            appendLine()
            appendLine("Sent via ClearSprint (Zero-Cost Buildathon Workspace)")
        }
        return MilestoneEmailDraft(
            projectName = project.name,
            sprintName = project.sprintName,
            milestoneTitle = milestoneTitle,
            completionPercent = pct,
            recipients = project.milestoneEmails,
            subject = subject,
            body = body
        )
    }

    /**
     * If the project has a free Google Apps Script / Formspree webhook URL configured,
     * automatically POSTs the milestone email JSON in the background with zero subscription cost.
     */
    suspend fun tryAutoDispatchWebhook(
        webhookUrl: String,
        draft: MilestoneEmailDraft
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val url = webhookUrl.trim()
        if (!url.startsWith("https://")) {
            return@withContext Pair(false, null)
        }
        try {
            val json = JSONObject().apply {
                put("projectName", draft.projectName)
                put("sprintName", draft.sprintName)
                put("milestoneTitle", draft.milestoneTitle)
                put("completionPercent", draft.completionPercent)
                put("recipients", JSONArray(draft.recipients))
                put("subject", draft.subject)
                put("body", draft.body)
            }
            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Pair(true, "Automated email webhook sent (${response.code})")
                } else {
                    Pair(false, "Webhook returned HTTP ${response.code}; use email compose fallback")
                }
            }
        } catch (e: Exception) {
            Pair(false, "Webhook offline (${e.localizedMessage ?: "error"}); ready to send via email app")
        }
    }

    /**
     * Zero-permission standard mailto: Intent for instant free email delivery via the device's email client.
     */
    fun launchEmailComposer(context: Context, draft: MilestoneEmailDraft): Boolean {
        val recipientsStr = draft.recipients.joinToString(",")
        val uri = Uri.parse(
            "mailto:${Uri.encode(recipientsStr)}" +
                "?subject=${Uri.encode(draft.subject)}" +
                "&body=${Uri.encode(draft.body)}"
        )
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, "Send milestone email").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        }.getOrDefault(false)
    }
}
