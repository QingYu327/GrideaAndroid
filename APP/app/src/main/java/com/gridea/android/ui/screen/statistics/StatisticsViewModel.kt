package com.gridea.android.ui.screen.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridea.android.data.model.Post
import com.gridea.android.data.repository.PostRepository
import com.gridea.android.data.repository.TagRepository
import com.gridea.android.data.repository.TagWithCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/**
 * 每日字数统计项
 */
data class DailyWordCount(
    val date: String,       // yyyy-MM-dd
    val wordCount: Int
)

/**
 * 每日写作时长统计项
 */
data class DailyWritingTime(
    val date: String,               // yyyy-MM-dd
    val writingTimeMinutes: Float   // 写作时长（分钟，保留小数以精确到秒）
)

/**
 * 标签文章计数项
 */
data class TagPostCount(
    val name: String,
    val postCount: Int
)

/**
 * 写作日历单日数据
 * 用于 GitHub 风格热力图，记录某天已发布的文章数
 */
data class CalendarDay(
    val date: String,       // yyyy-MM-dd
    val dayOfMonth: Int,    // 几号（1-31）
    val postCount: Int      // 当天已发布文章数（用于颜色分级）
)

/**
 * 热力图的一周列（GitHub 风格：每列代表一周）
 * 7 个格子按周一到周日排列，null 表示范围外或未来日期
 */
data class CalendarWeekColumn(
    val days: List<CalendarDay?>  // 长度固定为 7，索引 0=周一 ... 6=周日
)

/**
 * 月份标签（用于热力图顶部）
 * 标记该月份在周列列表中首次出现的列索引
 */
data class MonthLabel(
    val title: String,         // 月份标题（如 "7月"）
    val columnIndex: Int       // 在 weeks 列表中的列索引
)

/**
 * 写作日历数据
 * GitHub 风格热力图：近 6 个月的每日发布活动，按周列横向排列
 */
data class WritingCalendar(
    val weeks: List<CalendarWeekColumn>,  // 按时间从早到晚排列的周列
    val monthLabels: List<MonthLabel>,    // 月份标签（仅显示有数据的月份）
    val maxPostCount: Int                 // 最大单日文章数（用于颜色分级）
)

/**
 * 写作统计完整数据
 */
data class WritingStatistics(
    val totalWordCount: Int,
    val weeklyWordCount: Int,
    val monthlyWordCount: Int,
    val streak: Int,
    val publishedCount: Int,
    val draftCount: Int,
    val totalTagCount: Int,
    val totalWritingTimeMinutes: Float,
    val last7DaysWordCount: List<DailyWordCount>,
    val last7DaysWritingTime: List<DailyWritingTime>,
    val topTags: List<TagPostCount>,
    val writingCalendar: WritingCalendar
)

/**
 * 写作统计 ViewModel
 *
 * 从 PostRepository 和 TagRepository 获取数据，计算写作统计指标：
 * - 总字数 / 本周字数 / 本月字数
 * - 连续写作天数（streak）
 * - 发布文章数 / 草稿数 / 总标签数
 * - 最近 7 天每日字数 / 每日写作时长
 * - 热门标签 TOP 5
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val tagRepository: TagRepository
) : ViewModel() {

    /**
     * 写作统计数据：合并文章列表与标签列表后计算
     */
    val statistics: StateFlow<WritingStatistics> = combine(
        postRepository.getAllPosts(),
        tagRepository.getAllTags()
    ) { allPosts, tags ->
        computeStatistics(allPosts, tags)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WritingStatistics(
            totalWordCount = 0,
            weeklyWordCount = 0,
            monthlyWordCount = 0,
            streak = 0,
            publishedCount = 0,
            draftCount = 0,
            totalTagCount = 0,
            totalWritingTimeMinutes = 0f,
            last7DaysWordCount = emptyList(),
            last7DaysWritingTime = emptyList(),
            topTags = emptyList(),
            writingCalendar = WritingCalendar(emptyList(), emptyList(), 0)
        )
    )

    /**
     * 核心统计计算
     */
    private fun computeStatistics(posts: List<Post>, tags: List<TagWithCount>): WritingStatistics {
        // 排除自定义页面（hideInList 的文章归"页面"管理）
        val articles = posts.filter { !it.data.hideInList }

        val dateWordCountMap = mutableMapOf<String, Int>()
        val dateWritingTimeMap = mutableMapOf<String, Long>()
        val datePostCountMap = mutableMapOf<String, Int>()
        var totalWordCount = 0
        var totalWritingTimeMs = 0L

        articles.forEach { post ->
            val words = countWords(post.content)
            totalWordCount += words
            totalWritingTimeMs += post.data.writingTime
            val dateKey = extractDateKey(post.data.date)
            if (dateKey != null) {
                dateWordCountMap[dateKey] = (dateWordCountMap[dateKey] ?: 0) + words
                dateWritingTimeMap[dateKey] = (dateWritingTimeMap[dateKey] ?: 0L) + post.data.writingTime
                datePostCountMap[dateKey] = (datePostCountMap[dateKey] ?: 0) + 1
            }
        }

        val weeklyWordCount = sumWordCountInRange(articles, RangeType.WEEK)
        val monthlyWordCount = sumWordCountInRange(articles, RangeType.MONTH)
        val streak = calculateStreak(dateWordCountMap.keys)
        val last7Days = buildLast7Days(dateWordCountMap)
        val last7DaysTime = buildLast7DaysWritingTime(dateWritingTimeMap)
        val writingCalendar = buildWritingCalendar(datePostCountMap)

        // 热门标签 TOP 5（按文章数排序）
        val topTags = tags
            .filter { it.postCount > 0 }
            .sortedByDescending { it.postCount }
            .take(5)
            .map { TagPostCount(name = it.name, postCount = it.postCount) }

        return WritingStatistics(
            totalWordCount = totalWordCount,
            weeklyWordCount = weeklyWordCount,
            monthlyWordCount = monthlyWordCount,
            streak = streak,
            publishedCount = articles.count { it.data.published },
            draftCount = articles.count { !it.data.published },
            totalTagCount = tags.size,
            totalWritingTimeMinutes = totalWritingTimeMs / 60_000f,
            last7DaysWordCount = last7Days,
            last7DaysWritingTime = last7DaysTime,
            topTags = topTags,
            writingCalendar = writingCalendar
        )
    }

    /**
     * 字数统计（与编辑器字数统计逻辑一致：去除空白与 Markdown 符号）
     */
    private fun countWords(content: String): Int {
        return content
            .replace(Regex("[\\s\\n\\r]"), "")
            .replace(Regex("[#*>`~\\[\\]()!\\-_|=+{}]"), "")
            .length
    }

    /**
     * 从日期字符串中提取 yyyy-MM-dd 部分
     * 日期格式为 yyyy-MM-dd HH:mm:ss，取前 10 位
     */
    private fun extractDateKey(dateStr: String): String? {
        if (dateStr.length < 10) return null
        return dateStr.substring(0, 10)
    }

    /**
     * 连续写作天数：从今天往前追溯，连续有文章的天数
     */
    private fun calculateStreak(datesWithPosts: Set<String>): Int {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        var streak = 0
        // 从今天开始往前追溯
        while (true) {
            val dateKey = dateFormat.format(calendar.time)
            if (dateKey in datesWithPosts) {
                streak++
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
    }

    /**
     * 构建最近 7 天的每日字数列表（含今天，按日期升序）
     */
    private fun buildLast7Days(dateWordCountMap: Map<String, Int>): List<DailyWordCount> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val result = mutableListOf<DailyWordCount>()
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateKey = dateFormat.format(cal.time)
            result.add(
                DailyWordCount(
                    date = dateKey,
                    wordCount = dateWordCountMap[dateKey] ?: 0
                )
            )
        }
        return result
    }

    /**
     * 构建最近 7 天的每日写作时长列表（含今天，按日期升序，单位：分钟）
     */
    private fun buildLast7DaysWritingTime(dateWritingTimeMap: Map<String, Long>): List<DailyWritingTime> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val result = mutableListOf<DailyWritingTime>()
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateKey = dateFormat.format(cal.time)
            val ms = dateWritingTimeMap[dateKey] ?: 0L
            result.add(
                DailyWritingTime(
                    date = dateKey,
                    writingTimeMinutes = ms / 60_000f
                )
            )
        }
        return result
    }

    /**
     * 构建写作日历数据（近 3 个月，GitHub 风格热力图）
     *
     * 以 GitHub 仓库推送频率热力图样式展示近 3 个月的每日发布活动：
     * - 横向排列的周列（每周一列），每列 7 个格子（周一到周日）
     * - 时间范围：当前月份往前推 2 个月（共 3 个月），起点对齐到所在周的周一
     * - 终点对齐到今天所在周的周一，未来日期格子留空（postCount = 0）
     * - 颜色深浅基于当天已发布文章数（postCount），而非字数
     *
     * @param datePostCountMap 日期 → 已发布文章数映射（按 Post.data.date 聚合）
     * @return 写作日历数据（周列 + 月份标签 + 最大单日文章数）
     */
    private fun buildWritingCalendar(
        datePostCountMap: Map<String, Int>
    ): WritingCalendar {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val monthLabelFormat = SimpleDateFormat("M月", Locale.getDefault())

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayDateKey = dateFormat.format(today.time)

        // 起点：当前月份往前推 2 个月的 1 号（共 3 个月）
        val startCal = (today.clone() as Calendar).apply {
            add(Calendar.MONTH, -2)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        // 回退到本周一（GitHub 风格以周一为列起始）
        val firstDow = startCal.get(Calendar.DAY_OF_WEEK)
        val offsetToMonday = if (firstDow == Calendar.SUNDAY) -6 else (Calendar.MONDAY - firstDow)
        startCal.add(Calendar.DAY_OF_MONTH, offsetToMonday)

        // 终点：今天所在周的周一（最后一列可能包含未来日期，留空处理）
        val endWeekMonday = (today.clone() as Calendar).apply {
            val fd = get(Calendar.DAY_OF_WEEK)
            val off = if (fd == Calendar.SUNDAY) -6 else (Calendar.MONDAY - fd)
            add(Calendar.DAY_OF_MONTH, off)
        }

        val weeks = mutableListOf<CalendarWeekColumn>()
        val monthLabels = mutableListOf<MonthLabel>()
        var maxPostCount = 0
        var lastSeenMonth = -1
        var columnIndex = 0

        val cursor = (startCal.clone() as Calendar)
        // 按周遍历，直到超过今天所在周
        while (!cursor.after(endWeekMonday)) {
            // 月份标签：本周一所属月份与上周不同时新增一个标签
            val mondayMonth = cursor.get(Calendar.MONTH)
            if (mondayMonth != lastSeenMonth) {
                monthLabels.add(
                    MonthLabel(
                        title = monthLabelFormat.format(cursor.time),
                        columnIndex = columnIndex
                    )
                )
                lastSeenMonth = mondayMonth
            }

            val weekDays: MutableList<CalendarDay?> = MutableList(7) { null }
            for (dayIdx in 0..6) {
                val dateKey = dateFormat.format(cursor.time)
                // 未来日期不记录发布数据（字符串比较 yyyy-MM-dd 等价于日期比较）
                val isFuture = dateKey > todayDateKey
                val postCount = if (isFuture) 0 else (datePostCountMap[dateKey] ?: 0)
                if (postCount > maxPostCount) maxPostCount = postCount
                weekDays[dayIdx] = CalendarDay(
                    date = dateKey,
                    dayOfMonth = cursor.get(Calendar.DAY_OF_MONTH),
                    postCount = postCount
                )
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }
            weeks.add(CalendarWeekColumn(days = weekDays))
            columnIndex++
        }

        return WritingCalendar(
            weeks = weeks,
            monthLabels = monthLabels,
            maxPostCount = maxPostCount
        )
    }

    /**
     * 范围类型：本周 / 本月
     */
    private enum class RangeType { WEEK, MONTH }

    /**
     * 统计指定范围内的文章字数总和
     * - WEEK：本周（从本周第一天到当前时刻）
     * - MONTH：本月（从本月 1 号到当前时刻）
     */
    private fun sumWordCountInRange(posts: List<Post>, range: RangeType): Int {
        val now = Calendar.getInstance()
        val start = Calendar.getInstance()
        when (range) {
            RangeType.WEEK -> {
                // 本周第一天（跟随系统 locale 设置）
                start.set(Calendar.DAY_OF_WEEK, start.firstDayOfWeek)
            }
            RangeType.MONTH -> {
                start.set(Calendar.DAY_OF_MONTH, 1)
            }
        }
        start.set(Calendar.HOUR_OF_DAY, 0)
        start.set(Calendar.MINUTE, 0)
        start.set(Calendar.SECOND, 0)
        start.set(Calendar.MILLISECOND, 0)

        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return posts.sumOf { post ->
            val date = try { parser.parse(post.data.date) } catch (_: Exception) { null }
            if (date != null) {
                val cal = Calendar.getInstance().apply { time = date }
                if (!cal.before(start) && !cal.after(now)) {
                    countWords(post.content)
                } else 0
            } else 0
        }
    }
}
