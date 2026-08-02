package com.gridea.android.ui.screen.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridea.android.data.model.LogEntry
import com.gridea.android.data.model.LogLevel
import com.gridea.android.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 日志排序维度
 */
enum class LogSortField {
    /** 按时间戳排序 */
    TIME,
    /** 按重要性程度排序（important 优先，再按 level 权重） */
    IMPORTANCE
}

/**
 * 日志排序方向
 */
enum class LogSortOrder {
    /** 正序（升序） */
    ASC,
    /** 倒序（降序） */
    DESC
}

/**
 * 日志管理页面 ViewModel
 *
 * 从 [AppLogger] 读取日志（内存缓存 + 文件），支持按级别、分类、关键词筛选。
 * 导出与清空操作也委托给 AppLogger 执行。
 */
@HiltViewModel
class LogManagerViewModel @Inject constructor() : ViewModel() {

    /** 当前筛选级别（null = 全部） */
    private val _levelFilter = MutableStateFlow<LogLevel?>(null)
    val levelFilter: StateFlow<LogLevel?> = _levelFilter.asStateFlow()

    /** 当前筛选分类（null / 空串 = 全部） */
    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    /** 当前搜索关键词 */
    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    /** 是否仅显示存在警告和错误的日志（level=WARN 或 ERROR） */
    private val _showWarningsErrorsOnly = MutableStateFlow(false)
    val showWarningsErrorsOnly: StateFlow<Boolean> = _showWarningsErrorsOnly.asStateFlow()

    /** 排序维度（默认按时间） */
    private val _sortField = MutableStateFlow(LogSortField.TIME)
    val sortField: StateFlow<LogSortField> = _sortField.asStateFlow()

    /** 排序方向（默认倒序，最新在上） */
    private val _sortOrder = MutableStateFlow(LogSortOrder.DESC)
    val sortOrder: StateFlow<LogSortOrder> = _sortOrder.asStateFlow()

    /** 当前日志列表（已筛选） */
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    /** 加载状态：true 时 UI 显示骨架屏占位符，避免卡顿感 */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 所有出现过的分类集合（供筛选下拉使用） */
    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    /** 导出结果文件（供 UI 拷贝到用户选定位置后清空） */
    private val _exportFile = MutableStateFlow<File?>(null)
    val exportFile: StateFlow<File?> = _exportFile.asStateFlow()

    init {
        refresh()
    }

    /**
     * 从 AppLogger 重新加载日志并应用当前筛选条件。
     * 文件读取在 IO 线程，分类提取与排序过滤在 Default 线程执行，避免主线程卡顿。
     * 加载期间 [isLoading] 为 true，UI 显示骨架屏占位符。
     */
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val all = withContext(Dispatchers.IO) {
                AppLogger.getLogs(null, null, null)
            }
            // 分类提取与筛选排序都在 Default 线程完成，减少主线程负担与线程切换次数
            val (filtered, cats) = withContext(Dispatchers.Default) {
                val cats = all.map { it.category }.distinct().sorted()
                applyFilterPure(all) to cats
            }
            _categories.value = cats
            _logs.value = filtered
            _isLoading.value = false
        }
    }

    /**
     * 设置级别筛选并刷新。
     */
    fun setLevelFilter(level: LogLevel?) {
        _levelFilter.value = level
        refresh()
    }

    /**
     * 设置分类筛选并刷新。
     */
    fun setCategoryFilter(category: String?) {
        _categoryFilter.value = category
        refresh()
    }

    /**
     * 设置搜索关键词并刷新。
     */
    fun setKeyword(text: String) {
        _keyword.value = text
        refresh()
    }

    /**
     * 设置是否仅显示存在警告和错误的日志并刷新。
     */
    fun setShowWarningsErrorsOnly(value: Boolean) {
        _showWarningsErrorsOnly.value = value
        refresh()
    }

    /**
     * 设置排序维度并刷新。
     */
    fun setSortField(field: LogSortField) {
        _sortField.value = field
        refresh()
    }

    /**
     * 设置排序方向并刷新。
     */
    fun setSortOrder(order: LogSortOrder) {
        _sortOrder.value = order
        refresh()
    }

    /**
     * 切换排序方向（正序 ↔ 倒序），便捷方法。
     */
    fun toggleSortOrder() {
        _sortOrder.value = if (_sortOrder.value == LogSortOrder.ASC) LogSortOrder.DESC else LogSortOrder.ASC
        refresh()
    }

    /**
     * 清空全部日志。
     */
    fun clearLogs() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                AppLogger.clearLogs()
            }
            _logs.value = emptyList()
            _categories.value = emptyList()
        }
    }

    /**
     * 清除超过指定天数的日志（保留近 N 天）。
     *
     * @param days 保留天数（3/5/7），删除该天数之前的日志
     */
    fun clearLogsOlderThanDays(days: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                AppLogger.clearLogsOlderThanDays(days)
            }
            refresh()
        }
    }

    /**
     * 导出全部日志为文本文件，结果通过 [exportFile] 暴露。
     */
    fun exportLogs() {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                AppLogger.exportLogs()
            }
            _exportFile.value = file
        }
    }

    /**
     * 清除导出文件状态（UI 拷贝完成后调用）。
     */
    fun consumeExportFile() {
        _exportFile.value = null
    }

    /**
     * 对全量日志应用当前筛选条件与排序（纯函数版本，可在 Default 线程执行）。
     *
     * 排序规则：
     * - TIME：按时间戳排序
     * - IMPORTANCE：按重要性程度排序（important 优先，再按 level 权重：ACTION=5 > ERROR=4 > WARN=3 > INFO=2 > DEBUG=1）
     * - 方向：ASC 正序 / DESC 倒序
     */
    private fun applyFilterPure(all: List<LogEntry>): List<LogEntry> {
        val level = _levelFilter.value
        val category = _categoryFilter.value
        val kw = _keyword.value.trim()
        val warningsErrorsOnly = _showWarningsErrorsOnly.value
        val sortField = _sortField.value
        val sortOrder = _sortOrder.value

        val filtered = all.filter { entry ->
            (level == null || entry.level == level) &&
                (category.isNullOrBlank() || entry.category == category) &&
                (kw.isEmpty() || entry.message.contains(kw, ignoreCase = true)) &&
                (!warningsErrorsOnly || entry.level == LogLevel.WARN || entry.level == LogLevel.ERROR)
        }

        // level 权重映射：重要程度 ACTION > ERROR > WARN > INFO > DEBUG
        val levelWeight: (LogLevel) -> Int = { lvl ->
            when (lvl) {
                LogLevel.ACTION -> 5
                LogLevel.ERROR -> 4
                LogLevel.WARN -> 3
                LogLevel.INFO -> 2
                LogLevel.DEBUG -> 1
            }
        }
        // 重要性分数：important 加 1000，保证重要日志总排在普通日志前
        val importanceScore: (LogEntry) -> Int = { e ->
            (if (e.important) 1000 else 0) + levelWeight(e.level)
        }

        return when (sortField) {
            LogSortField.TIME -> {
                if (sortOrder == LogSortOrder.DESC) filtered.sortedByDescending { it.timestamp }
                else filtered.sortedBy { it.timestamp }
            }
            LogSortField.IMPORTANCE -> {
                // 重要性相同的情况下，按时间戳次级排序
                val comparator = compareByDescending<LogEntry> { importanceScore(it) }
                    .thenByDescending { it.timestamp }
                val baseSorted = filtered.sortedWith(comparator)
                if (sortOrder == LogSortOrder.DESC) baseSorted else baseSorted.reversed()
            }
        }
    }

    /**
     * 对全量日志应用当前筛选条件与排序（保留旧 API，内部委托给 [applyFilterPure]）。
     */
    private fun applyFilter(all: List<LogEntry>) {
        _logs.value = applyFilterPure(all)
    }
}
