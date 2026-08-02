package com.gridea.android.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * APK 下载器
 *
 * 从指定 URL 下载 APK 到目标目录，支持进度回调
 * 文件名从 URL 中提取，若失败则用 gridea_update.apk
 */
object ApkDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * 下载文件
     *
     * @param url 下载链接
     * @param targetDir 目标目录
     * @param onProgress 进度回调（0-100）
     * @return 下载完成的文件路径
     */
    suspend fun download(
        url: String,
        targetDir: File,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        // 从 URL 提取文件名
        val fileName = url.substringAfterLast("/").substringBefore("?")
            .let { if (it.endsWith(".apk")) it else "gridea_update.apk" }
        val targetFile = File(targetDir, fileName)

        AppLogger.i("Update", "开始下载 APK：$fileName → ${targetDir.absolutePath}")

        val request = Request.Builder().url(url).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("Update", "APK 下载失败：HTTP ${response.code}")
                    throw Exception("HTTP ${response.code}")
                }

                val body = response.body ?: throw Exception("Empty response body")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = (downloadedBytes * 100 / totalBytes).toInt()
                                onProgress(progress.coerceIn(0, 100))
                            }
                        }
                    }
                }
            }

            AppLogger.i("Update", "APK 下载完成：${targetFile.absolutePath} (${targetFile.length() / 1024}KB)")
            targetFile.absolutePath
        } catch (e: Exception) {
            AppLogger.e("Update", "APK 下载异常：${e.message}", e)
            throw e
        }
    }
}
