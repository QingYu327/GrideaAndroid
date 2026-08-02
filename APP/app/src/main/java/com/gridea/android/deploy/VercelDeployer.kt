package com.gridea.android.deploy

import com.gridea.android.data.model.Setting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vercel 部署器
 *
 * 替代旧版 Gridea 中的 Gitee/Coding 部署（Gitee Pages 已停止服务）
 * 使用 Vercel REST API v13 实现部署
 *
 * 部署流程：
 * 1. GET /v2/user 验证 token
 * 2. 对每个文件 POST /v2/files 上传（SHA 去重）
 * 3. POST /v13/deployments 创建部署（引用已上传的文件）
 *
 * 认证：Bearer token
 */
@Singleton
class VercelDeployer @Inject constructor() : Deployer {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://api.vercel.com"

    override suspend fun detect(setting: Setting): DetectResult = withContext(Dispatchers.IO) {
        try {
            if (setting.vercelAccessToken.isEmpty()) {
                return@withContext DetectResult(success = false, message = "请填写 Vercel Access Token")
            }

            val request = Request.Builder()
                .url("$baseUrl/v2/user")
                .header("Authorization", "Bearer ${setting.vercelAccessToken}")
                .header("User-Agent", "Gridea-Android")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body).optJSONObject("user")
                    val username = json?.optString("username", "") ?: ""
                    DetectResult(success = true, message = "Vercel 认证成功：$username")
                } else {
                    DetectResult(success = false, message = "Vercel 认证失败：${response.code}")
                }
            }
        } catch (e: Exception) {
            DetectResult(success = false, message = "Vercel 连接失败：${e.message ?: "未知错误"}")
        }
    }

    override suspend fun publish(
        setting: Setting,
        buildDir: File,
        onProgress: (DeployProgress) -> Unit
    ): DeployResult = withContext(Dispatchers.IO) {
        try {
            val allFiles = collectFiles(buildDir)

            // 1. 上传所有文件（Vercel 会按 SHA 去重）
            val fileRefs = JSONArray()
            for ((index, file) in allFiles.withIndex()) {
                val relativePath = file.relativeTo(buildDir).path.replace("\\", "/")
                val sha = uploadFile(setting, file)

                val fileRef = JSONObject()
                    .put("file", relativePath)
                    .put("sha", sha)
                    .put("size", file.length())
                fileRefs.put(fileRef)

                onProgress(DeployProgress(
                    current = index + 1,
                    total = allFiles.size,
                    fileName = relativePath
                ))
            }

            // 2. 创建部署
            val requestBody = JSONObject()
                .put("name", setting.vercelProjectId)
                .put("files", fileRefs)
                .put("target", "production")
                .put("project", setting.vercelProjectId)
                .toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/v13/deployments?forceNew=1")
                .header("Authorization", "Bearer ${setting.vercelAccessToken}")
                .header("User-Agent", "Gridea-Android")
                .post(requestBody)
                .build()

            var deployUrl: String? = null
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    return@withContext DeployResult(
                        success = false,
                        message = "创建 Vercel 部署失败：${response.code} - $errorBody"
                    )
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val url = json.optString("url", "")
                if (url.isNotEmpty()) {
                    deployUrl = "https://$url"
                }
            }

            DeployResult(
                success = true,
                message = "Vercel 部署成功",
                fileCount = allFiles.size,
                url = deployUrl
            )
        } catch (e: Exception) {
            DeployResult(
                success = false,
                message = "Vercel 部署失败：${e.message ?: "未知错误"}"
            )
        }
    }

    /**
     * 上传单个文件到 Vercel
     * POST /v2/files，返回文件 SHA
     */
    private fun uploadFile(setting: Setting, file: File): String {
        val fileBytes = file.readBytes()
        val sha = calculateSha1(fileBytes)

        val request = Request.Builder()
            .url("$baseUrl/v2/files")
            .header("Authorization", "Bearer ${setting.vercelAccessToken}")
            .header("User-Agent", "Gridea-Android")
            .header("Content-Type", "application/octet-stream")
            .header("x-vercel-digest", sha)
            .post(fileBytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 409) {
                throw RuntimeException("上传文件 ${file.name} 失败：${response.code}")
            }
        }

        return sha
    }

    /**
     * 计算 SHA1 哈希
     */
    private fun calculateSha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /**
     * 递归收集所有文件
     */
    private fun collectFiles(dir: File): List<File> {
        val files = mutableListOf<File>()
        dir.walkTopDown().forEach { file ->
            if (file.isFile && !file.name.startsWith(".")) {
                files.add(file)
            }
        }
        return files
    }
}
