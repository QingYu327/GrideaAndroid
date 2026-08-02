package com.gridea.android.util

import com.gridea.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 应用更新检查器（GitHub Release 方案）
 *
 * 通过 GitHub Releases API 检查最新版本：
 * 1. 请求 https://api.github.com/repos/{owner}/{repo}/releases/latest
 * 2. 解析 tag_name（版本号）和 body（更新说明）
 * 3. 找到 apk 资源（assets 中 .apk 后缀文件）的 download_url
 * 4. 与 BuildConfig.VERSION_NAME 比对，判断是否有新版本
 *
 * 版本号比对策略：去除 "v" 前缀后按字符串比较（如 "1.2.3" vs "1.2.4"）
 */
object UpdateChecker {

    /**
     * GitHub 仓库全路径，格式 owner/repo
     */
    private const val REPO_FULL_PATH = "QingYu327/GrideaAndroid"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 更新检查结果
     */
    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String,       // 最新版本号（如 "1.2.3"）
        val releaseNotes: String,        // 更新说明
        val apkDownloadUrl: String?,     // APK 下载链接（null 表示 Release 未附带 APK）
        val apkSize: Long,               // APK 文件大小（字节，0 表示未知）
        val htmlUrl: String              // Release 页面链接（备用浏览器打开）
    )

    /**
     * 检查更新
     *
     * @return UpdateInfo 或抛出 Exception（网络错误等）
     */
    suspend fun check(): UpdateInfo = withContext(Dispatchers.IO) {
        AppLogger.i("Update", "开始检查更新：$REPO_FULL_PATH")
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO_FULL_PATH/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Gridea-Android/${BuildConfig.VERSION_NAME}")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 404 通常是仓库地址错误或尚未发布 Release，给用户清晰提示而非抛异常打栈
                    if (response.code == 404) {
                        AppLogger.w("Update", "未找到 Release（404），请确认仓库 $REPO_FULL_PATH 已发布 Release")
                        return@withContext UpdateInfo(
                            hasUpdate = false,
                            latestVersion = BuildConfig.VERSION_NAME,
                            releaseNotes = "尚未发布任何版本（仓库 $REPO_FULL_PATH 没有 Release）",
                            apkDownloadUrl = null,
                            apkSize = 0L,
                            htmlUrl = "https://github.com/$REPO_FULL_PATH/releases"
                        )
                    }
                    AppLogger.w("Update", "检查更新失败：HTTP ${response.code}")
                    throw Exception("HTTP ${response.code}")
                }

                val body = response.body?.string()
                    ?: throw Exception("Empty response body")

                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "").removePrefix("v")
                val releaseBody = json.optString("body", "无更新说明")
                val htmlUrl = json.optString("html_url", "")

                // 查找 APK 资源
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                var apkSize = 0L
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i)
                        val name = asset?.optString("name", "") ?: ""
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url").ifEmpty { null }
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                val hasUpdate = compareVersions(tagName, BuildConfig.VERSION_NAME) > 0
                AppLogger.i("Update", "检查更新完成：latest=$tagName, current=${BuildConfig.VERSION_NAME}, hasUpdate=$hasUpdate, hasApk=${apkUrl != null}")

                UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = tagName,
                    releaseNotes = releaseBody,
                    apkDownloadUrl = apkUrl,
                    apkSize = apkSize,
                    htmlUrl = htmlUrl
                )
            }
        } catch (e: Exception) {
            AppLogger.e("Update", "检查更新异常：${e.message}", e)
            throw e
        }
    }

    /**
     * 版本号比较
     *
     * @param v1 版本号1（如 "1.2.3"）
     * @param v2 版本号2（如 "1.2.4"）
     * @return 正数表示 v1 > v2，负数表示 v1 < v2，0 表示相等
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}
