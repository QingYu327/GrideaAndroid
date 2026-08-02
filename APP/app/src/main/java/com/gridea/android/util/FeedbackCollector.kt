package com.gridea.android.util

import android.content.Context
import android.os.Build
import com.gridea.android.BuildConfig
import com.gridea.android.data.repository.SettingRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 反馈日志收集器
 *
 * 将以下内容打包为 zip 写入 Documents/Gridea/log/：
 * - log 目录下所有日志文件（崩溃日志 + 运行日志）
 * - 设备信息（机型、系统版本、APP 版本等）
 * - DataStore 配置快照（脱敏后，移除 token/密钥等敏感字段）
 *
 * 脱敏策略：所有包含 token/secret/password/key 的字段值替换为 ***，保护用户隐私
 */
object FeedbackCollector {

    /**
     * 收集日志并打包为 zip
     *
     * 流程：
     * 1. 自动导出日志管理系统收集的操作日志到 log 目录（无需用户手动导出）
     * 2. 打包 log 目录下所有日志文件（含崩溃日志 + 刚导出的操作日志）+ 设备信息 + 配置快照为 zip
     *
     * @param context 应用上下文
     * @param settingRepository 设置仓库（用于读取配置快照）
     * @param logDir 日志目录（Documents/Gridea/log）
     * @return 结果：Success(zipPath) 或 Failure(message)
     */
    suspend fun collect(
        context: Context,
        settingRepository: SettingRepository,
        logDir: File
    ): Result<String> {
        return try {
            AppLogger.i("Feedback", "开始收集反馈日志")

            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            // 步骤1：自动导出日志管理系统收集的操作日志到 log 目录
            // 日志管理页面的操作日志原本需要用户手动导出，这里自动化完成
            try {
                AppLogger.exportLogsToDirectory(logDir)
                AppLogger.d("Feedback", "操作日志已自动导出到 log 目录")
            } catch (e: Exception) {
                AppLogger.w("Feedback", "导出操作日志失败：${e.message}", e)
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                .format(Date())
            val zipFile = File(logDir, "feedback_$timestamp.zip")

            ZipOutputStream(zipFile.outputStream()).use { zos ->
                // 1. 写入设备信息
                writeDeviceInfo(zos)

                // 2. 写入 DataStore 配置快照（脱敏）
                writeConfigSnapshot(zos, settingRepository)

                // 3. 写入 log 目录下所有日志文件（含崩溃日志 + 刚导出的操作日志）
                writeLogFiles(zos, logDir)
            }

            AppLogger.i("Feedback", "反馈日志收集完成：${zipFile.name}")
            Result.success(zipFile.absolutePath)
        } catch (e: Exception) {
            AppLogger.e("Feedback", "反馈日志收集失败：${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 写入设备信息文件
     */
    private fun writeDeviceInfo(zos: ZipOutputStream) {
        val entry = ZipEntry("device_info.txt")
        zos.putNextEntry(entry)
        val info = buildString {
            appendLine("===== Device Info =====")
            appendLine("APP Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Build: ${Build.DISPLAY}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("Collect Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        }
        zos.write(info.toByteArray())
        zos.closeEntry()
    }

    /**
     * 写入 DataStore 配置快照（脱敏后）
     *
     * 读取 SettingRepository 中的配置 StateFlow 当前值，
     * 序列化为文本并脱敏（token/secret/password/key 字段值替换为 ***）
     */
    private suspend fun writeConfigSnapshot(zos: ZipOutputStream, settingRepository: SettingRepository) {
        val entry = ZipEntry("config_snapshot.txt")
        zos.putNextEntry(entry)
        val snapshot = buildString {
            appendLine("===== Config Snapshot (Sanitized) =====")
            appendLine("Theme: ${settingRepository.getTheme().first().themeName}")
            appendLine("Language: ${settingRepository.languageModeFlow.value}")
            appendLine("Theme Mode: ${settingRepository.themeModeFlow.value}")
            appendLine("Font Scale: ${settingRepository.fontSizeScaleFlow.value}")
            appendLine("Dynamic Color: ${settingRepository.dynamicColorFlow.value}")
            appendLine("Accent Color: ${settingRepository.appAccentColorFlow.value}")
            appendLine("Word Count Goal: ${settingRepository.getWordCountGoal().first()}")
            appendLine("WebView Debug: ${settingRepository.webViewDebugFlow.value}")
            appendLine("Verbose Log: ${settingRepository.verboseLogFlow.value}")

            // 部署配置（脱敏：token 替换为 ***）
            val setting = settingRepository.getSetting().first()
            appendLine("Deploy Platform: ${setting.platform}")
            appendLine("GitHub Repo: ${setting.repository}")
            appendLine("GitHub Username: ${setting.username}")
            appendLine("GitHub Token: ***")  // 脱敏
            appendLine("Gitee Repo: ${setting.giteeRepository}")
            appendLine("Gitee Username: ${setting.giteeUsername}")
            appendLine("Gitee Token: ***")  // 脱敏
            appendLine("Domain: ${setting.domain}")

            // 账户信息（脱敏）
            val account = settingRepository.getAccount().first()
            appendLine("Account Login: ${account.login.ifEmpty { "N/A" }}")
            appendLine("Account Name: ${account.name.ifEmpty { "N/A" }}")
        }
        zos.write(snapshot.toByteArray())
        zos.closeEntry()
    }

    /**
     * 写入 log 目录下所有日志文件
     */
    private fun writeLogFiles(zos: ZipOutputStream, logDir: File) {
        logDir.listFiles { file ->
            file.isFile && (file.extension == "txt" || file.extension == "log")
        }?.forEach { logFile ->
            try {
                val entry = ZipEntry("logs/${logFile.name}")
                zos.putNextEntry(entry)
                zos.write(logFile.readBytes())
                zos.closeEntry()
            } catch (_: Exception) {
                // 单个文件读取失败跳过，不影响整体打包
            }
        }
    }
}
