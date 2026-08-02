package com.gridea.android.util

import android.content.Context
import android.util.Log
import com.gridea.android.data.model.LogEntry
import com.gridea.android.data.model.LogLevel
import com.gridea.android.data.repository.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Collections
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicLong

/**
 * 应用全局日志记录器（单例 object）
 *
 * 提供全局日志 API，支持多种日志级别与用户操作记录。
 * 日志同时写入内存缓存（最近 500 条）和文件（JSONL 格式）。
 *
 * 写入策略：
 * - important=true 的日志（ERROR、ACTION 或显式标记）立即写入文件
 * - DEBUG / INFO / WARN 级别（important=false）批量写入（每 5 秒或累积 50 条刷新一次）
 * - 文件超过 2MB 时自动轮转（由 LogRepository 处理）
 *
 * 文件 I/O 在 Dispatchers.IO 协程中执行，不阻塞调用线程。
 *
 * 需在 Application.onCreate 中调用 [init] 完成初始化。
 */
object AppLogger {

    private const val TAG = "AppLogger"

    /** 内存缓存最大条数 */
    private const val MAX_CACHE_SIZE = 500

    /** 批量写入阈值（条数） */
    private const val BATCH_THRESHOLD = 50

    /** 批量刷新间隔（毫秒） */
    private const val FLUSH_INTERVAL_MS = 5_000L

    /** 文件日志仓库，负责实际的文件读写与轮转 */
    private var repository: LogRepository? = null

    /** 自增 ID 计数器，从 1 开始 */
    private val idCounter = AtomicLong(1L)

    /** 内存缓存，线程安全的 LinkedList，保留最近 500 条日志 */
    private val memoryCache = Collections.synchronizedList(LinkedList<LogEntry>())

    /** 待批量写入文件的日志缓冲 */
    private val pendingLogs = mutableListOf<LogEntry>()

    /** pendingLogs 的同步锁 */
    private val pendingLock = Any()

    /** 日志写入协程作用域 */
    private var scope: CoroutineScope? = null

    /** 周期性刷新任务 */
    private var flushJob: Job? = null

    /**
     * 详细日志开关（默认关闭）。
     * 关闭时 DEBUG 级别日志将被丢弃，不写入内存缓存和文件；
     * 开启后 DEBUG 日志照常记录。由设置-调试控制。
     */
    @Volatile
    private var verboseLogEnabled: Boolean = false

    /**
     * 设置详细日志开关。
     * 由 SettingRepository 在开关变化时同步调用。
     */
    fun setVerboseLogEnabled(enabled: Boolean) {
        verboseLogEnabled = enabled
    }

    /**
     * 初始化日志记录器，应在 Application.onCreate 中调用。
     *
     * 创建 LogRepository 实例（手动构造，仅需 Context），
     * 从已有日志恢复 ID 计数器，并启动周期性批量刷新协程。
     */
    fun init(context: Context) {
        if (repository != null) return

        repository = LogRepository(context.applicationContext)

        // 从已有日志文件恢复 ID 计数器，避免重启后 ID 冲突
        val maxId = try {
            repository?.readAllLogs()?.maxOfOrNull { it.id } ?: 0L
        } catch (e: Exception) {
            0L
        }
        idCounter.set(maxId + 1)

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        flushJob = scope!!.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flushPending()
            }
        }
    }

    /**
     * 记录 DEBUG 级别日志（批量写入）。
     *
     * @param important 显式标记重要性；null 表示按级别自动判断（DEBUG 默认 false）。
     *                  设为 true 时该日志将立即写入文件，并可在"仅显示重要日志"筛选中被保留。
     */
    fun d(tag: String, message: String, important: Boolean? = null) {
        // 详细日志关闭时跳过 DEBUG 级别（显式 important=true 例外，强制记录）
        if (!verboseLogEnabled && important != true) return
        log(LogLevel.DEBUG, tag, tag, message, null, batch = true, importantOverride = important)
    }

    /**
     * 记录 INFO 级别日志（批量写入）。
     *
     * @param important 显式标记重要性；null 表示按级别自动判断（INFO 默认 false）。
     *                  设为 true 时该日志将立即写入文件，并可在"仅显示重要日志"筛选中被保留。
     */
    fun i(tag: String, message: String, important: Boolean? = null) {
        log(LogLevel.INFO, tag, tag, message, null, batch = true, importantOverride = important)
    }

    /**
     * 记录 WARN 级别日志（批量写入）。
     *
     * @param throwable 可选异常对象，其堆栈会记录到 stackTrace 字段
     * @param important 显式标记重要性；null 表示按级别自动判断（WARN 默认 false）。
     *                  设为 true 时该日志将立即写入文件，并可在"仅显示重要日志"筛选中被保留。
     */
    fun w(tag: String, message: String, throwable: Throwable? = null, important: Boolean? = null) {
        val stackTrace = throwable?.let { Log.getStackTraceString(it) }?.takeIf { it.isNotEmpty() }
        log(LogLevel.WARN, tag, tag, message, stackTrace, batch = true, importantOverride = important)
    }

    /**
     * 记录 ERROR 级别日志（立即写入）。
     *
     * @param throwable 可选异常对象，其堆栈会记录到 stackTrace 字段
     * @param important 显式标记重要性；null 表示按级别自动判断（ERROR 默认 true）。
     */
    fun e(tag: String, message: String, throwable: Throwable? = null, important: Boolean? = null) {
        val stackTrace = throwable?.let { Log.getStackTraceString(it) }?.takeIf { it.isNotEmpty() }
        log(LogLevel.ERROR, tag, tag, message, stackTrace, batch = false, importantOverride = important)
    }

    /**
     * 记录用户可见错误：写日志 + 发送到 [ErrorBus] 触发灵动岛红色错误通知。
     *
     * 串联完整的错误反馈流程：
     * 1. catch 块调用本方法 → ERROR 日志立即写入文件
     * 2. ErrorBus 派发错误事件 → GrideaAppContent 显示红色灵动岛通知
     * 3. 通知文案引导用户"请查看日志管理进行反馈"
     *
     * @param userMessage 用户可见的错误简述（如"保存失败：磁盘空间不足"）
     * @param throwable 可选异常对象，堆栈写入日志文件但不在通知中展示
     */
    fun reportUserError(tag: String, userMessage: String, throwable: Throwable? = null) {
        e(tag, userMessage, throwable)
        ErrorBus.report(userMessage)
    }

    /**
     * 记录用户操作日志（ACTION 级别，立即写入）。
     *
     * @param category 操作类别（如 "Post"、"Tag"），映射到 LogEntry.category 和 tag
     * @param action 操作名称（如 "Create"、"Delete"）
     * @param detail 操作详情
     * @param important 显式标记重要性；null 表示按级别自动判断（ACTION 默认 true）。
     */
    fun action(category: String, action: String, detail: String, important: Boolean? = null) {
        log(LogLevel.ACTION, category, category, "$action: $detail", null, batch = false, importantOverride = important)
    }

    /**
     * 内部日志记录核心方法。
     *
     * 每条日志先写入内存缓存，再根据是否重要决定是批量缓冲还是立即写入文件。
     *
     * important 字段的确定规则：
     * - 若 [importantOverride] 非 null，使用该显式值
     * - 否则按级别自动判断：ERROR 和 ACTION 为 true，DEBUG / INFO / WARN 为 false
     *
     * 写入文件的时机：
     * - important=true 的日志立即写入文件（不等待批量刷新）
     * - important=false 的日志加入待写入缓冲，达到阈值或定时刷新时写入
     *
     * @param level 日志级别
     * @param category 日志类别
     * @param tag 日志标签
     * @param message 日志消息
     * @param stackTrace 堆栈信息（可为 null）
     * @param batch true 表示允许批量写入文件，false 表示立即写入文件
     * @param importantOverride 显式覆盖 important 字段；null 表示按级别自动判断
     */
    private fun log(
        level: LogLevel,
        category: String,
        tag: String,
        message: String,
        stackTrace: String?,
        batch: Boolean,
        importantOverride: Boolean? = null
    ) {
        // important 字段：显式覆盖优先，否则按级别自动判断（ERROR / ACTION 为重要）
        val important = importantOverride ?: (level == LogLevel.ERROR || level == LogLevel.ACTION)

        val entry = LogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            level = level,
            category = category,
            tag = tag,
            message = message,
            stackTrace = stackTrace,
            important = important
        )

        // 写入内存缓存（线程安全，保留最近 MAX_CACHE_SIZE 条）
        synchronized(memoryCache) {
            memoryCache.add(entry)
            while (memoryCache.size > MAX_CACHE_SIZE) {
                memoryCache.removeAt(0)
            }
        }

        if (batch && !important) {
            // 非重要日志：加入待写入缓冲，达到阈值时立即刷新
            val shouldFlush = synchronized(pendingLock) {
                pendingLogs.add(entry)
                pendingLogs.size >= BATCH_THRESHOLD
            }
            if (shouldFlush) {
                scope?.launch { flushPending() }
            }
        } else {
            // 重要日志（important=true）或显式要求立即写入：立即写入文件
            scope?.launch {
                try {
                    repository?.writeLog(entry)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write log entry", e)
                }
            }
        }
    }

    /**
     * 将待写入缓冲中的日志批量刷新到文件。
     *
     * 从 pendingLogs 中取出全部条目并清空缓冲，逐条调用 LogRepository.writeLog。
     * 在周期性刷新任务和阈值触发的协程中均可能调用，通过 pendingLock 保证线程安全。
     */
    private fun flushPending() {
        val toWrite = synchronized(pendingLock) {
            if (pendingLogs.isEmpty()) return
            val copy = pendingLogs.toList()
            pendingLogs.clear()
            copy
        }

        for (entry in toWrite) {
            try {
                repository?.writeLog(entry)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to flush log entry", e)
            }
        }
    }

    /**
     * 获取日志列表，支持按级别、类别和关键词过滤。
     *
     * 合并内存缓存与文件中的日志，按 ID 去重（刷新后的条目可能同时存在于缓存和文件），
     * 按时间戳升序排列。
     *
     * @param level 日志级别过滤（null 表示不过滤）
     * @param category 类别过滤（null 表示不过滤）
     * @param keyword 消息关键词过滤（null 表示不过滤，不区分大小写）
     * @return 过滤后的日志列表
     */
    fun getLogs(level: LogLevel?, category: String?, keyword: String?): List<LogEntry> {
        val fromMemory = synchronized(memoryCache) { memoryCache.toList() }

        val fromFile = try {
            repository?.readAllLogs() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // 合并去重（按 ID）：内存缓存中的日志在批量刷新后可能与文件中重复
        val all = (fromFile + fromMemory).distinctBy { it.id }

        return all.filter { entry ->
            (level == null || entry.level == level) &&
            (category == null || entry.category == category) &&
            (keyword == null || entry.message.contains(keyword, ignoreCase = true))
        }.sortedBy { it.timestamp }
    }

    /**
     * 清除所有日志：清空内存缓存、待写入缓冲，并删除日志文件。
     */
    fun clearLogs() {
        synchronized(memoryCache) { memoryCache.clear() }
        synchronized(pendingLock) { pendingLogs.clear() }
        try {
            repository?.clearFiles()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear log files", e)
        }
    }

    /**
     * 清除早于指定天数的日志（保留近 N 天的日志）。
     *
     * 从文件中删除超过 [days] 天的日志条目，同时清空内存缓存中过期的条目。
     *
     * @param days 保留天数（如 3 表示保留近 3 天，删除 3 天以前的）
     * @return 被删除的条目数
     */
    fun clearLogsOlderThanDays(days: Int): Int {
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        // 清空内存缓存中过期的条目
        synchronized(memoryCache) {
            memoryCache.removeAll { it.timestamp < cutoff }
        }
        synchronized(pendingLock) {
            pendingLogs.removeAll { it.timestamp < cutoff }
        }
        // 从文件中删除过期条目
        return try {
            repository?.clearLogsOlderThan(cutoff) ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear old log files", e)
            0
        }
    }

    /**
     * 自动清理：删除超过 7 天的日志。
     * 应在应用启动时调用。
     */
    fun cleanExpiredLogs() {
        try {
            repository?.cleanExpiredLogs()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean expired logs", e)
        }
    }

    /**
     * 导出所有日志为文本文件。
     *
     * 合并所有日志文件内容，格式化为可读文本，保存到 cacheDir 下
     * 文件名格式：gridea_log_{timestamp}.txt
     *
     * @return 导出的文件；若未初始化则返回空临时文件
     */
    fun exportLogs(): File {
        val repo = repository
        if (repo != null) {
            return try {
                repo.exportToText()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export logs", e)
                File.createTempFile("gridea_log", ".txt")
            }
        }
        // 未初始化时返回空临时文件
        return File.createTempFile("gridea_log", ".txt")
    }

    /**
     * 导出所有日志为文本文件到指定目录。
     *
     * 用于反馈日志收集：直接导出到 Documents/Gridea/log 目录，
     * 自动化完成日志导出，无需用户手动操作系统文件选择器。
     *
     * @param targetDir 目标目录
     * @return 导出的文件；若未初始化则返回空临时文件
     */
    fun exportLogsToDirectory(targetDir: File): File {
        val repo = repository
        if (repo != null) {
            return try {
                repo.exportToDirectory(targetDir)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export logs to directory", e)
                File.createTempFile("gridea_log", ".txt")
            }
        }
        return File.createTempFile("gridea_log", ".txt")
    }
}
