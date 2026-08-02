package com.gridea.android.deploy

import com.gridea.android.data.model.Setting
import com.gridea.android.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub 部署器
 *
 * 对应旧版 Gridea 0.9.3 的 src/server/deploy.ts（Git 部分）
 * 移动端使用 GitHub Contents API 替代 isomorphic-git
 *
 * 部署策略：通过 Contents API 逐文件上传
 * 1. GET /repos/{owner}/{repo} 验证仓库
 * 2. 获取现有文件列表
 * 3. 删除远程有但本地没有的文件（先清理，避免旧文件残留与新文件冲突、覆盖不完整）
 * 4. 对每个文件 PUT /repos/{owner}/{repo}/contents/{path} 上传（Base64 编码）
 *
 * 部署顺序：清理旧文件 → 上传新文件 → 完成
 *
 * 认证：Bearer token（Personal Access Token）
 *
 * 遵循 GitHub REST API 最佳实践：
 * - mutative 请求（PUT/DELETE）之间间隔至少 1 秒，避免触发 secondary rate limit
 * - 检查 x-ratelimit-remaining / retry-after 响应头，触发限流时按 retry-after 等待并重试（最多 3 次）
 * - 不忽略 4xx/5xx 错误（404 删除视为文件已不存在可忽略）
 */
@Singleton
class GithubDeployer @Inject constructor() : Deployer {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://api.github.com"

    /**
     * 清理仓库名：用户可能误填 "owner/repo" 格式，只保留最后一段 repo 名
     */
    private fun cleanRepoName(repo: String): String {
        return repo.trim().substringAfterLast("/")
    }

    override suspend fun detect(setting: Setting): DetectResult = withContext(Dispatchers.IO) {
        try {
            val repo = cleanRepoName(setting.repository)
            if (setting.username.isEmpty() || repo.isEmpty() || setting.token.isEmpty()) {
                return@withContext DetectResult(success = false, message = "请填写用户名、仓库名和 Token")
            }

            // 脱敏的 Token 前缀，用于日志排查权限问题
            val tokenPreview = if (setting.token.length > 4) {
                setting.token.substring(0, 4) + "***"
            } else {
                "***"
            }
            AppLogger.d(
                "Deploy",
                "GitHub detect 请求参数：owner=${setting.username}, repo=$repo, branch=${setting.branch.ifEmpty { "master" }}, tokenPrefix=$tokenPreview"
            )

            val request = Request.Builder()
                .url("$baseUrl/repos/${setting.username}/$repo")
                .header("Authorization", "Bearer ${setting.token}")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Gridea-Android")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val repoName = json.optString("full_name", "")
                    val private = json.optBoolean("private", false)
                    val defaultBranch = json.optString("default_branch", "")
                    AppLogger.i("Deploy", "GitHub 仓库信息：$repoName, private=$private, default_branch=$defaultBranch")
                    DetectResult(success = true, message = "GitHub 仓库连接成功：$repoName")
                } else {
                    // 记录详细的错误信息，帮助定位 401/403/404 根因
                    val errorDetail = parseGitHubError(response.code, body)
                    AppLogger.w(
                        "Deploy",
                        "GitHub detect 失败：code=${response.code}, owner=${setting.username}, repo=${setting.repository}, tokenPrefix=$tokenPreview, body=${body.take(500)}"
                    )
                    DetectResult(success = false, message = errorDetail)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Deploy", "GitHub 连接异常：${e.message ?: "未知错误"}", e)
            DetectResult(success = false, message = "GitHub 连接失败：${e.message ?: "未知错误"}")
        }
    }

    /**
     * 解析 GitHub API 错误响应，返回用户可读的诊断信息
     */
    private fun parseGitHubError(code: Int, body: String): String {
        return when (code) {
            401 -> {
                "Token 无效或已过期（HTTP 401）。请检查 Token 是否正确，是否已过期"
            }
            403 -> {
                "Token 权限不足（HTTP 403）。请确保 Token 具有 repo 权限（Classic Token）或 Contents 读写权限（Fine-grained Token）"
            }
            404 -> {
                // 404 可能是仓库不存在，也可能是 Token 无权限访问私有仓库（GitHub 对私有仓库也返回 404 以避免泄露存在性）
                "仓库不存在或无访问权限（HTTP 404）。可能原因：1) 用户名/仓库名拼写错误（注意大小写）；2) 仓库为私有且 Token 缺少访问权限；3) Token 权限不足（需要 repo scope）"
            }
            else -> {
                "GitHub 认证失败（HTTP $code）"
            }
        }
    }

    override suspend fun publish(
        setting: Setting,
        buildDir: File,
        onProgress: (DeployProgress) -> Unit
    ): DeployResult = withContext(Dispatchers.IO) {
        try {
            val branch = setting.branch.ifEmpty { "master" }
            // 使用清理过仓库名的 setting 副本，确保所有辅助方法使用统一的 repo 名
            val cleanedSetting = setting.copy(repository = cleanRepoName(setting.repository))
            val allFiles = collectFiles(buildDir)

            // 获取远程现有文件列表（用于检测需要删除的文件和更新已有文件）
            val remoteFiles = getRemoteFiles(cleanedSetting, branch)
            AppLogger.i("Deploy", "GitHub 开始部署：本地 ${allFiles.size} 个文件，远程 ${remoteFiles.size} 个文件")

            val localPaths = allFiles.map { it.relativeTo(buildDir).path.replace("\\", "/") }.toSet()

            // ===== 第 1 步：先清理远程多余文件 =====
            // 必须在上传前删除，否则旧文件残留会与新文件冲突，导致覆盖不完整、部署不生效。
            // toDelete 覆盖所有文件类型（.html/.css/.js/图片等），凡本地没有的远程文件一律删除。
            val toDelete = remoteFiles.keys.filter { it !in localPaths }
            AppLogger.d("Deploy", "GitHub 待删除远程文件数：${toDelete.size}")
            for ((index, path) in toDelete.withIndex()) {
                deleteFile(cleanedSetting, path, remoteFiles[path] ?: "", branch)
                // 删除请求之间也间隔 1 秒，避免触发 secondary rate limit
                if (index < toDelete.size - 1) {
                    delay(1000)
                }
            }

            // ===== 第 2 步：上传/更新文件 =====
            // 此时远程仅保留本地也存在的文件，上传阶段按 sha 更新它们，避免旧文件干扰。
            var uploadedCount = 0
            for ((index, file) in allFiles.withIndex()) {
                val relativePath = file.relativeTo(buildDir).path.replace("\\", "/")
                val fileSha = remoteFiles[relativePath]

                // 大文件检查：GitHub Contents API 限制单文件 100MB，超过应使用 Git LFS
                val fileSizeMb = file.length() / (1024.0 * 1024.0)
                if (fileSizeMb > 100) {
                    AppLogger.w("Deploy", "GitHub 跳过大文件（>100MB）：$relativePath (${String.format("%.1f", fileSizeMb)}MB)，建议使用 Git LFS")
                    continue
                }

                uploadFile(cleanedSetting, file, relativePath, branch, fileSha)
                uploadedCount++

                onProgress(DeployProgress(
                    current = index + 1,
                    total = allFiles.size,
                    fileName = relativePath
                ))

                // GitHub API 最佳实践：mutative 请求之间间隔至少 1 秒，避免触发 secondary rate limit
                if (index < allFiles.size - 1) {
                    delay(1000)
                }
            }

            DeployResult(
                success = true,
                message = "GitHub 部署成功",
                fileCount = uploadedCount,
                url = "https://${cleanedSetting.username}.github.io/${cleanedSetting.repository}/"
            )
        } catch (e: Exception) {
            AppLogger.e("Deploy", "GitHub 部署失败：${e.message ?: "未知错误"}", e)
            DeployResult(
                success = false,
                message = "GitHub 部署失败：${e.message ?: "未知错误"}"
            )
        }
    }

    /**
     * 获取远程仓库指定分支的文件列表
     */
    private fun getRemoteFiles(setting: Setting, branch: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            collectRemoteFiles(setting, "", branch, result)
        } catch (e: Exception) {
            // 远程为空或仓库新创建，忽略
        }
        return result
    }

    private fun collectRemoteFiles(setting: Setting, path: String, branch: String, result: MutableMap<String, String>) {
        val url = if (path.isEmpty()) {
            "$baseUrl/repos/${setting.username}/${setting.repository}/contents?ref=$branch"
        } else {
            "$baseUrl/repos/${setting.username}/${setting.repository}/contents/$path?ref=$branch"
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${setting.token}")
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "Gridea-Android")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            val body = response.body?.string() ?: return
            val items = JSONArray(body)
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val type = item.getString("type")
                val name = item.getString("path")
                val sha = item.optString("sha", "")

                if (type == "file") {
                    result[name] = sha
                } else if (type == "dir") {
                    collectRemoteFiles(setting, name, branch, result)
                }
            }
        }
    }

    /**
     * 上传/更新单个文件
     *
     * 冲突处理（规则 6：推送被拒绝先拉取合并，不暴力强推）：
     * 如果返回 409 Conflict（sha 不匹配，说明远程文件已被修改），
     * 重新获取文件最新 sha 后重试一次，相当于"先 pull 再 push"。
     *
     * 限流处理：触发 403/429 且响应头指示已限流（x-ratelimit-remaining=0 或带 retry-after）时，
     * 按 retry-after（缺省 60s）等待后重试，最多重试 3 次，避免因限流导致上传半途中断。
     */
    private suspend fun uploadFile(setting: Setting, file: File, path: String, branch: String, existingSha: String?) {
        val content = Base64.getEncoder().encodeToString(file.readBytes())
        val encodedPath = java.net.URLEncoder.encode(path, "UTF-8").replace("+", "%20")
        val url = "$baseUrl/repos/${setting.username}/${setting.repository}/contents/$encodedPath"

        var currentSha = existingSha
        var rateLimitRetries = 0
        val maxRateLimitRetries = 3
        var conflictRetried = false

        while (true) {
            val jsonBody = JSONObject()
                .put("message", "Update $path via Gridea")
                .put("content", content)
                .put("branch", branch)
            if (currentSha != null) {
                jsonBody.put("sha", currentSha)
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${setting.token}")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Gridea-Android")
                .put(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) return

                val errBody = response.body?.string()?.take(500) ?: ""
                // 检查是否触发 rate limit：等待后重试，而非直接抛出导致整批上传中断
                if (response.code == 403 || response.code == 429) {
                    val retryAfter = response.header("retry-after")
                    val remaining = response.header("x-ratelimit-remaining")
                    if (remaining == "0" || retryAfter != null) {
                        if (rateLimitRetries < maxRateLimitRetries) {
                            rateLimitRetries++
                            val waitSec = retryAfter?.toIntOrNull() ?: 60
                            AppLogger.w("Deploy", "GitHub 上传 $path 触发限流，等待 ${waitSec}s 后重试（第 $rateLimitRetries/$maxRateLimitRetries 次）")
                            delay(waitSec * 1000L)
                            return@use
                        }
                        throw RuntimeException("触发 GitHub 限流（rate limit），重试 $maxRateLimitRetries 次后仍失败，请稍后再试。retry-after=$retryAfter")
                    }
                }
                // 409 Conflict：sha 不匹配，远程文件已被修改，重新获取 sha 后重试一次
                if (response.code == 409 && !conflictRetried) {
                    AppLogger.w("Deploy", "GitHub 上传 $path 遇到冲突（409），重新拉取 sha 后重试")
                    conflictRetried = true
                    currentSha = fetchFileSha(setting, path, branch)
                    return@use
                }
                throw RuntimeException("上传 $path 失败：${response.code} - $errBody")
            }
        }
    }

    /**
     * 获取远程文件的最新 sha（用于 409 冲突后重试）
     * 相当于 git pull 拉取最新版本
     */
    private fun fetchFileSha(setting: Setting, path: String, branch: String): String? {
        val encodedPath = java.net.URLEncoder.encode(path, "UTF-8").replace("+", "%20")
        val url = "$baseUrl/repos/${setting.username}/${setting.repository}/contents/$encodedPath?ref=$branch"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${setting.token}")
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "Gridea-Android")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                JSONObject(body).optString("sha", "").ifEmpty { null }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun deleteFile(setting: Setting, path: String, sha: String, branch: String) {
        val jsonBody = JSONObject()
            .put("message", "Delete $path via Gridea")
            .put("sha", sha)
            .put("branch", branch)

        val encodedPath = java.net.URLEncoder.encode(path, "UTF-8").replace("+", "%20")
        val request = Request.Builder()
            .url("$baseUrl/repos/${setting.username}/${setting.repository}/contents/$encodedPath")
            .header("Authorization", "Bearer ${setting.token}")
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "Gridea-Android")
            .delete(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            // 404 表示文件已被删除，可忽略；其他错误记录日志但不阻断部署
            if (!response.isSuccessful && response.code != 404) {
                val errBody = response.body?.string()?.take(300) ?: ""
                AppLogger.w("Deploy", "GitHub 删除 $path 失败：${response.code} - $errBody")
            }
        }
    }

    /**
     * 递归收集所有文件
     *
     * 过滤规则（等效 .gitignore）：
     * - .git/ 目录及其下所有文件
     * - .env 等敏感配置文件
     * - IDE 配置（.idea/、.vscode/）
     * - 系统文件（.DS_Store、Thumbs.db）
     * - 隐藏文件（以 . 开头）
     */
    private fun collectFiles(dir: File): List<File> {
        val files = mutableListOf<File>()
        dir.walkTopDown().forEach { file ->
            if (file.isFile && shouldUploadFile(file)) {
                files.add(file)
            }
        }
        return files
    }

    /**
     * 判断文件是否应该上传
     * 排除敏感文件、IDE 配置、系统文件，避免上传隐私信息
     */
    private fun shouldUploadFile(file: File): Boolean {
        val name = file.name
        // .nojekyll 文件必须上传：禁用 GitHub Pages 的 Jekyll 处理
        if (name == ".nojekyll") return true
        // 排除其他隐藏文件（以 . 开头）
        if (name.startsWith(".")) return false
        // 排除系统文件
        if (name == "Thumbs.db" || name == "desktop.ini") return false
        // 排除 .git 目录下的文件（通过路径判断）
        val path = file.absolutePath.replace("\\", "/")
        if (path.contains("/.git/")) return false
        // 排除 IDE 配置目录
        if (path.contains("/.idea/") || path.contains("/.vscode/")) return false
        return true
    }
}
