package com.example.data.github

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GitHubRepoDto(
    @Json(name = "full_name") val fullName: String = "",
    val description: String? = null,
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "stargazers_count") val stars: Int = 0,
    @Json(name = "open_issues_count") val openIssues: Int = 0,
    val language: String? = null,
    @Json(name = "pushed_at") val pushedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubCommitItemDto(
    val sha: String = "",
    @Json(name = "html_url") val htmlUrl: String = "",
    val commit: GitHubCommitDetailDto = GitHubCommitDetailDto()
)

@JsonClass(generateAdapter = true)
data class GitHubCommitDetailDto(
    val message: String = "",
    val author: GitHubCommitAuthorDto? = null
)

@JsonClass(generateAdapter = true)
data class GitHubCommitAuthorDto(
    val name: String = "",
    val date: String = ""
)

@JsonClass(generateAdapter = true)
data class GitHubIssueDto(
    val number: Int = 0,
    val title: String = "",
    val state: String = "open",
    @Json(name = "html_url") val htmlUrl: String = "",
    val body: String? = null,
    @Json(name = "pull_request") val pullRequest: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class GitHubTreeResponseDto(
    val tree: List<GitHubTreeEntryDto> = emptyList(),
    val truncated: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GitHubTreeEntryDto(
    val path: String = "",
    val type: String = "",
    val size: Long? = null
)

data class GitHubWorkspaceData(
    val repo: GitHubRepoDto? = null,
    val commits: List<GitHubCommitItemDto> = emptyList(),
    val issues: List<GitHubIssueDto> = emptyList(),
    val files: List<GitHubTreeEntryDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

interface GitHubApiService {
    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") authHeader: String? = null
    ): GitHubRepoDto

    @GET("repos/{owner}/{repo}/commits")
    suspend fun getCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 12,
        @Header("Authorization") authHeader: String? = null
    ): List<GitHubCommitItemDto>

    @GET("repos/{owner}/{repo}/issues")
    suspend fun getOpenIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 20,
        @Header("Authorization") authHeader: String? = null
    ): List<GitHubIssueDto>

    @GET("repos/{owner}/{repo}/git/trees/{branch}")
    suspend fun getRepoTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Query("recursive") recursive: Int = 1,
        @Header("Authorization") authHeader: String? = null
    ): GitHubTreeResponseDto
}

class GitHubRepository {
    private val service: GitHubApiService by lazy {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "ClearSprint-Android")
                    .build()
                chain.proceed(req)
            }
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubApiService::class.java)
    }

    suspend fun fetchRepoWorkspace(
        owner: String,
        repo: String,
        personalAccessToken: String = ""
    ): Result<GitHubWorkspaceData> {
        return try {
            val cleanOwner = owner.trim()
            val cleanRepo = repo.trim()
            require(cleanOwner.isNotEmpty() && cleanRepo.isNotEmpty()) {
                "Enter a GitHub owner and repository name."
            }
            val authHeader = personalAccessToken.trim().takeIf { it.isNotEmpty() }?.let { "Bearer $it" }
            val repoInfo = service.getRepository(cleanOwner, cleanRepo, authHeader)
            val commits = runCatching {
                service.getCommits(cleanOwner, cleanRepo, 12, authHeader)
            }.getOrDefault(emptyList())
            val issues = runCatching {
                service.getOpenIssues(cleanOwner, cleanRepo, "open", 20, authHeader)
            }.getOrDefault(emptyList())
            val files = runCatching {
                service.getRepoTree(cleanOwner, cleanRepo, repoInfo.defaultBranch, 1, authHeader)
                    .tree
                    .filter { it.type == "blob" }
                    .take(300)
            }.getOrDefault(emptyList())

            Result.success(
                GitHubWorkspaceData(
                    repo = repoInfo,
                    commits = commits,
                    issues = issues,
                    files = files,
                    isLoading = false,
                    errorMessage = null
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
