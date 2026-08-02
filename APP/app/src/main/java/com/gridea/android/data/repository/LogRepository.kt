package com.gridea.android.data.repository

import android.content.Context
import com.gridea.android.data.model.LogEntry
import com.gridea.android.data.model.LogLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日志文件仓库
 *
 * 负责日志的文件持久化：JSONL 格式写入、文件轮转、读取与导出。
 *
 * - 日志目录：[context.filesDir]/log/
 * - 当前日志文件：app_log.jsonl
 * - 轮转旧文件：app_log_old.jsonl
 * - 单文件上限 2MB，超过后当前文件重命名为旧文件并创建新文件
 *
 * 每个 LogEntry 序列化为一行 JSON（JSONL 格式）：
 * {"id":1,"timestamp":1690000000000,"level":"INFO","category":"Renderer","tag":"Renderer","message":"...","stackTrace":null}
 *
 * 同时被 AppLogger（object，手动实例化）和 Hilt（@Inject）使用。
 */
@Singleton
class LogRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** 日志目录：filesDir/log */
    private val logDir = File(context.filesDir, LOG_DIR_NAME)

    /** 当前日志文件 */
    val currentLogFile = File(logDir, CURRENT_LOG_NAME)

    /** 轮转后的旧日志文件 */
    val oldLogFile = File(logDir, OLD_LOG_NAME)

    /**
     * 将一条日志以 JSONL 格式追加写入当前日志文件。
     * 写入前检查文件大小，超过 [MAX_FILE_SIZE] 时执行轮转。
     */
    fun writeLog(entry: LogEntry) {
        try {
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            // 文件轮转：当前文件超过 2MB 时，重命名为旧文件
            if (currentLogFile.exists() && currentLogFile.length() >= MAX_FILE_SIZE) {
                if (oldLogFile.exists()) {
                    oldLogFile.delete()
                }
                currentLogFile.renameTo(oldLogFile)
            }

            // 追加一行 JSON
            val json = entryToJson(entry)
            currentLogFile.appendText("$json\n")
        } catch (e: Exception) {
            // 日志写入失败不应影响应用运行
        }
    }

    /**
     * 读取所有日志文件中的条目（旧文件 + 当前文件），按时间戳升序排列。
     */
    fun readAllLogs(): List<LogEntry> {
        val entries = mutableListOf<LogEntry>()

        // 先读旧文件，再读当前文件
        for (file in listOf(oldLogFile, currentLogFile)) {
            if (!file.exists()) continue
            try {
                file.useLines { lines ->
                    for (line in lines) {
                        if (line.isBlank()) continue
                        jsonToEntry(line)?.let { entries.add(it) }
                    }
                }
            } catch (e: Exception) {
                // 单个文件读取失败时继续读取其他文件
            }
        }

        return entries.sortedBy { it.timestamp }
    }

    /**
     * 删除当前日志文件与旧日志文件。
     */
    fun clearFiles() {
        try {
            currentLogFile.delete()
        } catch (e: Exception) {
        }
        try {
            oldLogFile.delete()
        } catch (e: Exception) {
        }
    }

    /**
     * 删除早于指定时间戳的日志条目，保留较新的日志。
     *
     * 读取两个日志文件中的所有条目，过滤掉 timestamp < [cutoffTimestamp] 的条目，
     * 将剩余条目重新写入当前日志文件（旧文件删除）。
     *
     * @param cutoffTimestamp 截断时间戳（毫秒），早于此时间的日志将被删除
     * @return 被删除的条目数
     */
    fun clearLogsOlderThan(cutoffTimestamp: Long): Int {
        if (!logDir.exists()) return 0

        val remaining = mutableListOf<LogEntry>()
        var deletedCount = 0

        for (file in listOf(oldLogFile, currentLogFile)) {
            if (!file.exists()) continue
            try {
                file.useLines { lines ->
                    for (line in lines) {
                        if (line.isBlank()) continue
                        val entry = jsonToEntry(line)
                        if (entry != null) {
                            if (entry.timestamp < cutoffTimestamp) {
                                deletedCount++
                            } else {
                                remaining.add(entry)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // 单个文件读取失败时继续处理
            }
        }

        // 删除旧文件，将保留的日志写入当前文件
        try {
            oldLogFile.delete()
        } catch (e: Exception) {
        }

        try {
            if (remaining.isEmpty()) {
                currentLogFile.delete()
            } else {
                // 覆盖写入保留的日志
                currentLogFile.bufferedWriter().use { writer ->
                    remaining.forEach { entry ->
                        writer.write(entryToJson(entry))
                        writer.newLine()
                    }
                }
            }
        } catch (e: Exception) {
        }

        return deletedCount
    }

    /**
     * 自动清理：删除超过 7 天的日志。
     * @return 被删除的条目数
     */
    fun cleanExpiredLogs(): Int {
        val cutoff = System.currentTimeMillis() - LOG_RETENTION_MS
        return clearLogsOlderThan(cutoff)
    }

    /**
     * 将所有日志合并导出为格式化文本文件。
     *
     * 输出路径：cacheDir/gridea_log_{timestamp}.txt
     *
     * @return 导出的文件
     */
    fun exportToText(): File {
        return exportToDirectory(context.cacheDir)
    }

    /**
     * 将所有日志合并导出为格式化文本文件到指定目录。
     *
     * 用于反馈日志收集场景：直接导出到 Documents/Gridea/log 目录，
     * 无需用户手动通过系统文件选择器选择保存位置。
     *
     * @param targetDir 目标目录（若不存在会自动创建）
     * @return 导出的文件
     */
    fun exportToDirectory(targetDir: File): File {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val entries = readAllLogs()
        val exportFile = File(targetDir, "gridea_log_${System.currentTimeMillis()}.txt")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val sb = StringBuilder()

        for (entry in entries) {
            sb.append("[${sdf.format(Date(entry.timestamp))}]")
            sb.append(" [${entry.level.name}]")
            sb.append(" [${entry.category}]")
            sb.append(" ${entry.tag}: ${entry.message}")
            if (!entry.stackTrace.isNullOrEmpty()) {
                sb.append("\n").append(entry.stackTrace)
            }
            sb.append("\n")
        }

        exportFile.writeText(sb.toString())
        return exportFile
    }

    // ===== JSON 序列化 / 反序列化 =====

    /**
     * 将 LogEntry 序列化为 JSON 字符串。
     * 使用 org.json.JSONObject 避免 LogEntry 类耦合序列化注解。
     */
    private fun entryToJson(entry: LogEntry): String {
        val json = JSONObject()
        json.put("id", entry.id)
        json.put("timestamp", entry.timestamp)
        json.put("level", entry.level.name)
        json.put("category", entry.category)
        json.put("tag", entry.tag)
        json.put("message", entry.message)
        // JSONObject.NULL 保证 stackTrace 为 null 时输出 "stackTrace":null
        json.put("stackTrace", entry.stackTrace ?: JSONObject.NULL)
        json.put("important", entry.important)
        return json.toString()
    }

    /**
     * 从 JSON 字符串反序列化为 LogEntry，解析失败返回 null。
     */
    private fun jsonToEntry(line: String): LogEntry? {
        return try {
            val json = JSONObject(line)
            LogEntry(
                id = json.getLong("id"),
                timestamp = json.getLong("timestamp"),
                level = LogLevel.fromString(json.getString("level")),
                category = json.getString("category"),
                tag = json.getString("tag"),
                message = json.getString("message"),
                stackTrace = if (json.isNull("stackTrace")) null else json.getString("stackTrace"),
                // 兼容旧日志文件（没有 important 字段）：默认为 false
                important = if (json.has("important") && !json.isNull("important")) json.getBoolean("important") else false
            )
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** 日志目录名 */
        const val LOG_DIR_NAME = "log"

        /** 当前日志文件名 */
        const val CURRENT_LOG_NAME = "app_log.jsonl"

        /** 轮转旧日志文件名 */
        const val OLD_LOG_NAME = "app_log_old.jsonl"

        /** 单文件最大字节数：2MB */
        const val MAX_FILE_SIZE = 2L * 1024 * 1024

        /** 日志保留时长：7 天（毫秒） */
        const val LOG_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}
