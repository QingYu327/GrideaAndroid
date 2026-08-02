package com.gridea.android.deploy

import com.gridea.android.data.model.Setting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Netlify 部署器
 *
 * 对应旧版 Gridea 0.9.3 的 src/server/plugins/deploys/netlify.ts
 * 使用 Netlify REST API v1 实现 SHA1 增量上传
 *
 * 部署流程：
 * 1. GET sites/:site_id/ 验证站点
 * 2. 收集所有文件并计算 SHA1
 * 3. POST sites/:site_id/deploys 提交文件哈希清单
 * 4. 对 Netlify 缺失的文件逐个 PUT 上传
 */
@Singleton
class NetlifyDeployer @Inject constructor() : Deployer {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://api.netlify.com/api/v1"

    override suspend fun detect(setting: Setting): DetectResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/sites/${setting.netlifySiteId}")
                .header("Authorization", "Bearer ${setting.netlifyAccessToken}")
                .header("User-Agent", "Gridea-Android")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val siteName = json.optString("name", "")
                    DetectResult(success = true, message = "Netlify 站点连接成功：$siteName")
                } else {
                    DetectResult(success = false, message = "Netlify 认证失败：${response.code}")
                }
            }
        } catch (e: Exception) {
            DetectResult(success = false, message = "Netlify 连接失败：${e.message ?: "未知错误"}")
        }
    }

    override suspend fun publish(
        setting: Setting,
        buildDir: File,
        onProgress: (DeployProgress) -> Unit
    ): DeployResult = withContext(Dispatchers.IO) {
        try {
            // 1. 收集所有文件并计算 SHA1
            val fileHashes = mutableMapOf<String, String>()
            val allFiles = collectFiles(buildDir)
            for (file in allFiles) {
                val relativePath = "/" + file.relativeTo(buildDir).path.replace("\\", "/")
                val sha1 = calculateSha1(file)
                fileHashes[relativePath] = sha1
            }

            // 2. 提交文件哈希清单，创建 deploy
            val filesJson = JSONObject()
            for ((path, hash) in fileHashes) {
                filesJson.put(path, hash)
            }

            val requestBody = JSONObject()
                .put("files", filesJson)
                .toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/sites/${setting.netlifySiteId}/deploys")
                .header("Authorization", "Bearer ${setting.netlifyAccessToken}")
                .header("User-Agent", "Gridea-Android")
                .post(requestBody)
                .build()

            val deployId: String
            val requiredHashes: List<String>
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DeployResult(
                        success = false,
                        message = "创建 Netlify deploy 失败：${response.code}"
                    )
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                deployId = json.getString("id")
                val requiredArray = json.optJSONArray("required") ?: org.json.JSONArray()
                requiredHashes = (0 until requiredArray.length()).map { requiredArray.getString(it) }
            }

            // 3. 上传 Netlify 缺失的文件
            val requiredSet = requiredHashes.toSet()
            val filesToUpload = allFiles.filter { file ->
                val relativePath = "/" + file.relativeTo(buildDir).path.replace("\\", "/")
                fileHashes[relativePath] in requiredSet
            }

            for ((index, file) in filesToUpload.withIndex()) {
                val relativePath = "/" + file.relativeTo(buildDir).path.replace("\\", "/")
                uploadFile(setting, file, relativePath, deployId)

                onProgress(DeployProgress(
                    current = index + 1,
                    total = filesToUpload.size,
                    fileName = relativePath
                ))
            }

            DeployResult(
                success = true,
                message = "Netlify 部署成功",
                fileCount = filesToUpload.size
            )
        } catch (e: Exception) {
            DeployResult(
                success = false,
                message = "Netlify 部署失败：${e.message ?: "未知错误"}"
            )
        }
    }

    /**
     * 上传单个文件到 Netlify
     */
    private fun uploadFile(setting: Setting, file: File, filePath: String, deployId: String) {
        val fileBytes = file.readBytes()
        val encodedPath = java.net.URLEncoder.encode(filePath, "UTF-8").replace("+", "%20")

        val request = Request.Builder()
            .url("$baseUrl/deploys/$deployId/files$encodedPath")
            .header("Authorization", "Bearer ${setting.netlifyAccessToken}")
            .header("User-Agent", "Gridea-Android")
            .header("Content-Type", "application/octet-stream")
            .put(fileBytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("上传 $filePath 失败：${response.code}")
            }
        }
    }

    /**
     * 计算文件 SHA1 哈希
     */
    private fun calculateSha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
