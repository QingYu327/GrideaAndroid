package com.gridea.android.deploy

import com.gridea.android.data.model.Setting
import com.gridea.android.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gitee 部署器
 *
 * 通过 Gitee API v5 Contents 接口逐文件上传静态站点。
 * 适用于配合 EdgeOne Pages 的 Git 仓库导入方案：
 *   APP 推送文件到 Gitee → EdgeOne 控制台检测到推送 → 自动触发部署
 *
 * 认证：access_token 作为 query 参数传递（Gitee API v5 规范）
 *
 * 部署策略：
 * 1. GET /repos/{owner}/{repo} 验证仓库可访问
 * 2. GET /repos/{owner}/{repo}/git/trees/{branch}?recursive=1 获取远程文件树
 * 3. POST /repos/{owner}/{repo}/contents/{path} 逐文件上传（Base64 编码）
 * 4. DELETE /repos/{owner}/{repo}/contents/{path} 删除远程多余文件
 */
@Singleton
class GiteeDeployer @Inject constructor() : Deployer {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://gitee.com/api/v5"

    /**
     * 清理仓库名：用户可能误填 "owner/repo" 格式，只保留最后一段 repo 名
     */
    private fun cleanRepoName(repo: String): String {
        return repo.trim().substringAfterLast("/")
    }

    /**
     * 从 Setting 中提取 Gitee 独立配置，返回一个使用 Gitee 专用字段的 Setting 副本。
     * Gitee 平台使用独立的 giteeUsername/giteeRepository/giteeBranch/giteeToken 字段，
     * 与 GitHub 的 username/repository/branch/token 隔离，避免切换平台时输入值串台。
     * 辅助方法统一读取 setting.username/repository/branch/token，
     * 因此这里将 Gitee 专用字段映射到通用字段上。
     */
    private fun Setting.toGiteeConfig(): Setting {
        return this.copy(
            username = giteeUsername,
            repository = cleanRepoName(giteeRepository),
            branch = giteeBranch,
            token = giteeToken
        )
    }

    override suspend fun detect(setting: Setting): DetectResult = withContext(Dispatchers.IO) {
        try {
            val cfg = setting.toGiteeConfig()
            if (cfg.username.isEmpty() || cfg.repository.isEmpty() || cfg.token.isEmpty()) {
                return@withContext DetectResult(success = false, message = "请填写用户名、仓库名和 Token")
            }

            val tokenPreview = if (cfg.token.length > 4) {
                cfg.token.substring(0, 4) + "***"
            } else {
                "***"
            }
            AppLogger.d(
                "Deploy",
                "Gitee detect 请求参数：owner=${cfg.username}, repo=${cfg.repository}, branch=${cfg.branch.ifEmpty { "master" }}, tokenPrefix=$tokenPreview"
            )

            val request = Request.Builder()
                .url("$baseUrl/repos/${cfg.username}/${cfg.repository}?access_token=${cfg.token}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val repoName = json.optString("full_name", "")
                    val private = json.optBoolean("private", false)
                    val defaultBranch = json.optString("default_branch", "")
                    AppLogger.i("Deploy", "Gitee 仓库信息：$repoName, private=$private, default_branch=$defaultBranch")
                    DetectResult(success = true, message = "Gitee 仓库连接成功：$repoName")
                } else {
                    // 404 时额外验证 token 有效性并对比用户名，给出更精确的诊断
                    val errorDetail = if (response.code == 404) {
                        diagnoseGitee404(cfg, body)
                    } else {
                        parseGiteeError(response.code, body)
                    }
                    AppLogger.w(
                        "Deploy",
                        "Gitee detect 失败：code=${response.code}, owner=${cfg.username}, repo=${cfg.repository}, tokenPrefix=$tokenPreview, body=${body.take(500)}"
                    )
                    DetectResult(success = false, message = errorDetail)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Deploy", "Gitee 连接异常：${e.message ?: "未知错误"}", e)
            DetectResult(success = false, message = "Gitee 连接失败：${e.message ?: "未知错误"}")
        }
    }

    override suspend fun publish(
        setting: Setting,
        buildDir: File,
        onProgress: (DeployProgress) -> Unit
    ): DeployResult = withContext(Dispatchers.IO) {
        try {
            val cfg = setting.toGiteeConfig()
            val branch = cfg.branch.ifEmpty { "master" }
            val allFiles = collectFiles(buildDir)

            AppLogger.i("Deploy", "Gitee 开始上传：共 ${allFiles.size} 个文件，目标分支 $branch")

            // 获取远程现有文件列表（用于检测需要删除的文件和更新已有文件）
            val remoteFiles = getRemoteFiles(cfg, branch)
            AppLogger.d("Deploy", "Gitee 远程文件数：${remoteFiles.size}")

            // 上传/更新文件
            var uploadedCount = 0
            for ((index, file) in allFiles.withIndex()) {
                val relativePath = file.relativeTo(buildDir).path.replace("\\", "/")
                val fileSha = remoteFiles[relativePath]

                // 大文件检查：Gitee 单文件限制 100MB，超过应使用 Git LFS
                val fileSizeMb = file.length() / (1024.0 * 1024.0)
                if (fileSizeMb > 100) {
                    AppLogger.w("Deploy", "Gitee 跳过大文件（>100MB）：$relativePath (${String.format("%.1f", fileSizeMb)}MB)，建议使用 Git LFS")
                    continue
                }

                uploadFile(cfg, file, relativePath, branch, fileSha)
                uploadedCount++

                onProgress(DeployProgress(
                    current = index + 1,
                    total = allFiles.size,
                    fileName = relativePath
                ))

                // mutative 请求之间间隔 1 秒，避免触发 Gitee API 频率限制
                if (index < allFiles.size - 1) {
                    delay(1000)
                }
            }

            // 删除远程多余文件
            val localPaths = allFiles.map { it.relativeTo(buildDir).path.replace("\\", "/") }.toSet()
            val toDelete = remoteFiles.keys.filter { it !in localPaths }
            AppLogger.d("Deploy", "Gitee 待删除远程文件数：${toDelete.size}")
            for ((index, path) in toDelete.withIndex()) {
                deleteFile(cfg, path, remoteFiles[path] ?: "", branch)
                // 删除请求之间也间隔 1 秒
                if (index < toDelete.size - 1) {
                    delay(1000)
                }
            }

            DeployResult(
                success = true,
                message = "Gitee 部署成功",
                fileCount = uploadedCount,
                url = "https://gitee.com/${cfg.username}/${cfg.repository}/raw/${branch}/"
            )
        } catch (e: Exception) {
            AppLogger.e("Deploy", "Gitee 部署失败：${e.message ?: "未知错误"}", e)
            DeployResult(
                success = false,
                message = "Gitee 部署失败：${e.message ?: "未知错误"}"
            )
        }
    }

    /**
     * 解析 Gitee API 错误响应，返回用户可读的诊断信息
     */
    private fun parseGiteeError(code: Int, body: String): String {
        return when (code) {
            401 -> {
                "Token 无效或已过期（HTTP 401）。请检查个人访问令牌是否正确"
            }
            403 -> {
                "Token 权限不足（HTTP 403）。请确保 Token 具有 projects 权限"
            }
            404 -> {
                "仓库不存在或无访问权限（HTTP 404）。可能原因：1) 用户名/仓库名拼写错误；2) 仓库为私有且 Token 缺少访问权限；3) Token 权限不足"
            }
            else -> {
                "Gitee 认证失败（HTTP $code）"
            }
        }
    }

    /**
     * 404 时的增强诊断：调用 /api/v5/user 验证 token 有效性并对比用户名
     *
     * Gitee 对不存在的仓库和权限不足都返回 404 "Not Found Project"，
     * 需要额外调用 user 接口来区分以下情况：
     * 1. Token 无效 → /user 也返回 401
     * 2. Token 有效但用户名不匹配 → 用户名填错了
     * 3. Token 有效且用户名匹配 → 仓库名错误或 Token 缺少 projects 权限
     */
    private fun diagnoseGitee404(cfg: Setting, originalBody: String): String {
        return try {
            val userRequest = Request.Builder()
                .url("$baseUrl/user?access_token=${cfg.token}")
                .get()
                .build()

            client.newCall(userRequest).execute().use { response ->
                val userBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    // /user 接口也失败，说明 token 本身无效
                    AppLogger.w("Deploy", "Gitee token 验证失败：code=${response.code}, body=${userBody.take(300)}")
                    return "Token 无效或权限不足（HTTP ${response.code}）。请检查个人访问令牌是否正确，并确保已勾选 projects 权限"
                }

                val userJson = JSONObject(userBody)
                val tokenOwner = userJson.optString("login", "")
                AppLogger.d("Deploy", "Gitee token 验证成功：token 对应用户=$tokenOwner, 输入的 owner=${cfg.username}")

                if (tokenOwner.isNotEmpty() && tokenOwner != cfg.username) {
                    // 用户名不匹配
                    "用户名不匹配（HTTP 404）。Token 对应的 Gitee 用户名为「$tokenOwner」，但你填写的用户名是「${cfg.username}」。请检查用户名是否正确（注意大小写）"
                } else {
                    // 用户名匹配，说明是仓库名错误或权限不足
                    "仓库不存在或 Token 缺少权限（HTTP 404）。Token 验证通过（用户：$tokenOwner），但无法访问仓库 ${cfg.username}/${cfg.repository}。请检查：1) 仓库名是否正确；2) Token 是否勾选了 projects 权限；3) 仓库是否为私有"
                }
            }
        } catch (e: Exception) {
            AppLogger.w("Deploy", "Gitee 404 诊断异常：${e.message}")
            parseGiteeError(404, originalBody)
        }
    }

    /**
     * 获取远程仓库指定分支的文件列表（path → sha）
     * 使用 git trees API 递归获取，比逐目录遍历更高效
     */
    private fun getRemoteFiles(setting: Setting, branch: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            // 先获取分支引用对应的 commit sha
            val branchUrl = "$baseUrl/repos/${setting.username}/${setting.repository}/branches/${branch}?access_token=${setting.token}"
            val branchRequest = Request.Builder().url(branchUrl).get().build()
            client.newCall(branchRequest).execute().use { response ->
                if (!response.isSuccessful) return result
                val body = response.body?.string() ?: return result
                val json = JSONObject(body)
                val commitSha = json.optJSONObject("commit")?.optString("sha", "") ?: return result

                // 递归获取文件树
                val treeUrl = "$baseUrl/repos/${setting.username}/${setting.repository}/git/trees/${commitSha}?recursive=1&access_token=${setting.token}"
                val treeRequest = Request.Builder().url(treeUrl).get().build()
                client.newCall(treeRequest).execute().use { treeResponse ->
                    if (!treeResponse.isSuccessful) return result
                    val treeBody = treeResponse.body?.string() ?: return result
                    val treeJson = JSONObject(treeBody)
                    val tree = treeJson.optJSONArray("tree") ?: return result
                    for (i in 0 until tree.length()) {
                        val item = tree.getJSONObject(i)
                        val type = item.optString("type", "")
                        val path = item.optString("path", "")
                        val sha = item.optString("sha", "")
                        if (type == "blob" && path.isNotEmpty()) {
                            result[path] = sha
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 远程为空或仓库新创建，忽略
            AppLogger.d("Deploy", "Gitee 获取远程文件列表异常（可能是空仓库）：${e.message}")
        }
        return result
    }

    /**
     * 上传/更新单个文件
     *
     * Gitee API v5 规范：
     * - 创建文件：POST /repos/{owner}/{repo}/contents/{path}
     * - 更新文件：PUT /repos/{owner}/{repo}/contents/{path}，必须携带 sha
     *
     * access_token 必须作为 query 参数传递（放在 form body 中会导致更新接口返回 422）
     *
     * 冲突处理（规则 6：推送被拒绝先拉取合并，不暴力强推）：
     * 如果返回 409 Conflict（sha 不匹配），重新获取文件最新 sha 后重试一次。
     */
    private fun uploadFile(setting: Setting, file: File, path: String, branch: String, existingSha: String?) {
        val content = Base64.getEncoder().encodeToString(file.readBytes())
        val encodedPath = java.net.URLEncoder.encode(path, "UTF-8").replace("+", "%20")
        val url = "$baseUrl/repos/${setting.username}/${setting.repository}/contents/$encodedPath?access_token=${setting.token}"

        // 最多重试 2 次：首次 + 1 次 409 重试
        var currentSha = existingSha
        for (attempt in 0..1) {
            val formBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("content", content)
                .addFormDataPart("message", "Update $path via Gridea")
                .addFormDataPart("branch", branch)
            // 更新已存在文件时必须传 sha，否则 Gitee 会返回 422 "sha can't be blank"
            val method: String
            if (currentSha != null) {
                formBuilder.addFormDataPart("sha", currentSha)
                method = "PUT"
            } else {
                method = "POST"
            }

            val requestBuilder = Request.Builder().url(url)
            val body = formBuilder.build()
            when (method) {
                "PUT" -> requestBuilder.put(body)
                else -> requestBuilder.post(body)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) return

                val errBody = response.body?.string()?.take(500) ?: ""
                // 409 Conflict：sha 不匹配，远程文件已被修改，重新获取 sha 后重试
                if (response.code == 409 && attempt == 0) {
                    AppLogger.w("Deploy", "Gitee 上传 $path 遇到冲突（409），重新拉取 sha 后重试")
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
        val url = "$baseUrl/repos/${setting.username}/${setting.repository}/contents/$encodedPath?access_token=${setting.token}&ref=$branch"
        val request = Request.Builder()
            .url(url)
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

    /**
     * 删除远程文件
     * Gitee 使用 DELETE 方法，access_token 作为 query 参数
     */
    private fun deleteFile(setting: Setting, path: String, sha: String, branch: String) {
        val formBody = "sha=${sha}&message=Delete $path via Gridea&branch=${branch}"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val encodedPath = java.net.URLEncoder.encode(path, "UTF-8").replace("+", "%20")
        val url = "$baseUrl/repos/${setting.username}/${setting.repository}/contents/$encodedPath?access_token=${setting.token}"
        val request = Request.Builder()
            .url(url)
            .delete(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            // 404 表示文件已被删除，可忽略；其他错误记录日志但不阻断部署
            if (!response.isSuccessful && response.code != 404) {
                val errBody = response.body?.string()?.take(300) ?: ""
                AppLogger.w("Deploy", "Gitee 删除 $path 失败：${response.code} - $errBody")
            }
        }
    }

    /**
     * 递归收集所有文件
     *
     * 过滤规则（等效 .gitignore）：
     * - .git/ 目录及其下所有文件
     * - IDE 配置（.idea/、.vscode/）
     * - 系统文件（.DS_Store、Thumbs.db、desktop.ini）
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
        // 排除隐藏文件（以 . 开头）
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
