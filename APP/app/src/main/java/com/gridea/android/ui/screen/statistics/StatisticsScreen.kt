package com.gridea.android.ui.screen.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gridea.android.R
import com.gridea.android.ui.theme.LocalAccentColor
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 写作统计仪表盘页面
 *
 * 展示写作数据统计，包括：
 * - 总字数 / 本周 / 本月字数
 * - 连续写作天数
 * - 发布 / 草稿 / 标签数 / 总写作时长
 * - 最近 7 天每日字数柱状图（Canvas 手绘）
 * - 最近 7 天每日写作时长柱状图（Canvas 手绘）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val stats by viewModel.statistics.collectAsState()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.statistics_title),
                        fontFamily = FontFamily.Serif
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                // 底部 90dp 留白：让最底部的写作时长柱状图能完整滚动到悬浮导航栏上方，避免遮挡
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 写作日历热力图（置顶展示）
            WritingCalendarSection(stats.writingCalendar)

            // 字数统计卡片
            WordCountSection(stats)

            // 连续写作 + 文章/标签数 + 写作时长
            OverviewSection(stats)

            // 最近 7 天字数柱状图
            BarChartSection(stats.last7DaysWordCount)

            // 最近 7 天写作时长柱状图
            WritingTimeBarChartSection(stats.last7DaysWritingTime)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ===== 字数统计卡片 =====

@Composable
private fun WordCountSection(stats: WritingStatistics) {
    StatisticsGroupCard(
        title = stringResource(R.string.statistics_section_word_count),
        icon = Icons.Filled.Edit
    ) {
        // 总字数（大号显示）
        BigStatItem(
            value = formatNumber(stats.totalWordCount),
            label = stringResource(R.string.statistics_total_words)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallStatItem(
                value = formatNumber(stats.weeklyWordCount),
                label = stringResource(R.string.statistics_weekly_words),
                modifier = Modifier.weight(1f)
            )
            SmallStatItem(
                value = formatNumber(stats.monthlyWordCount),
                label = stringResource(R.string.statistics_monthly_words),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ===== 概览卡片（连续天数 + 文章/标签数 + 写作时长）=====

@Composable
private fun OverviewSection(stats: WritingStatistics) {
    StatisticsGroupCard(
        title = stringResource(R.string.statistics_section_overview),
        icon = Icons.Filled.AutoAwesome
    ) {
        // 连续写作天数
        BigStatItem(
            value = "${stats.streak}",
            label = stringResource(R.string.statistics_streak_days)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallStatItem(
                value = "${stats.publishedCount}",
                label = stringResource(R.string.statistics_published_count),
                modifier = Modifier.weight(1f)
            )
            SmallStatItem(
                value = "${stats.draftCount}",
                label = stringResource(R.string.statistics_draft_count),
                modifier = Modifier.weight(1f)
            )
            SmallStatItem(
                value = "${stats.totalTagCount}",
                label = stringResource(R.string.statistics_tag_count),
                modifier = Modifier.weight(1f)
            )
        }
        // 总写作时长（用 Tertiary 色调区别于连续天数）
        BigStatItem(
            value = formatWritingTime(stats.totalWritingTimeMinutes),
            label = stringResource(R.string.statistics_total_writing_time)
        )
    }
}

// ===== 柱状图卡片 =====

@Composable
private fun BarChartSection(data: List<DailyWordCount>) {
    StatisticsGroupCard(
        title = stringResource(R.string.statistics_section_last_7_days),
        icon = Icons.Filled.BarChart
    ) {
        if (data.isEmpty()) {
            Text(
                text = stringResource(R.string.statistics_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontFamily = FontFamily.Serif
            )
        } else {
            DailyWordBarChart(data)
        }
    }
}

// ===== 写作时长柱状图卡片 =====

@Composable
private fun WritingTimeBarChartSection(data: List<DailyWritingTime>) {
    StatisticsGroupCard(
        title = stringResource(R.string.statistics_section_last_7_days_time),
        icon = Icons.Filled.Schedule
    ) {
        if (data.isEmpty()) {
            Text(
                text = stringResource(R.string.statistics_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontFamily = FontFamily.Serif
            )
        } else {
            DailyWritingTimeBarChart(data)
        }
    }
}

// ===== Canvas 柱状图 =====

/**
 * 用 Compose Canvas 手绘的每日字数柱状图
 * 不引入第三方图表库
 */
@Composable
private fun DailyWordBarChart(data: List<DailyWordCount>) {
    val primaryColor = LocalAccentColor.current
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline

    // 缓存 Paint 对象，避免每帧每个柱子都重新创建（仅主题色变化时重建）
    val valuePaint = remember(onSurfaceColor) {
        android.graphics.Paint().apply {
            color = onSurfaceColor.toArgb()
            textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.SERIF
        }
    }
    val labelPaint = remember(outlineColor) {
        android.graphics.Paint().apply {
            color = outlineColor.toArgb()
            textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.SERIF
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val maxCount = data.maxOfOrNull { it.wordCount } ?: 0
            val barCount = data.size
            if (barCount == 0 || maxCount == 0) return@Canvas

            val labelHeight = 36f // 底部标签预留高度
            val valuePadding = 16f // 柱顶数值预留高度
            val maxBarHeight = size.height - labelHeight - valuePadding
            val slotWidth = size.width / barCount
            val barWidth = slotWidth * 0.55f

            data.forEachIndexed { index, item ->
                val barHeight = (item.wordCount.toFloat() / maxCount) * maxBarHeight
                val barX = index * slotWidth + (slotWidth - barWidth) / 2f
                val barY = size.height - labelHeight - barHeight

                // 绘制柱子（圆角矩形）
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.85f),
                    topLeft = Offset(barX, barY),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(8f, 8f)
                )

                // 在柱顶绘制数值（仅非零柱）
                if (item.wordCount > 0) {
                    val valueText = "${item.wordCount}"
                    // 用原生 Canvas 绘制文字（Paint 已在 Composable 作用域缓存）
                    drawContext.canvas.nativeCanvas.apply {
                        val textX = barX + barWidth / 2f
                        val textY = barY - 6f
                        drawText(valueText, textX, textY, valuePaint)
                    }
                }

                // 在底部绘制星期标签
                val dayLabel = formatDayLabel(item.date)
                drawContext.canvas.nativeCanvas.apply {
                    val labelX = index * slotWidth + slotWidth / 2f
                    val labelY = size.height - 8f
                    drawText(dayLabel, labelX, labelY, labelPaint)
                }
            }
        }
        // 提示文字
        Text(
            text = stringResource(R.string.statistics_bar_chart_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp),
            fontFamily = FontFamily.Serif
        )
    }
}

// ===== 通用组件 =====

/**
 * 统计分组卡片（与设置页风格一致）
 */
@Composable
private fun StatisticsGroupCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = LocalAccentColor.current
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = LocalAccentColor.current,
            modifier = Modifier.padding(start = 6.dp),
            fontFamily = FontFamily.Serif
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/**
 * 大号统计项（用于突出展示总字数、连续天数等）
 */
@Composable
private fun BigStatItem(value: String, label: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = LocalAccentColor.current,
            fontFamily = FontFamily.Serif
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 2.dp),
            fontFamily = FontFamily.Serif
        )
    }
}

/**
 * 小号统计项（用于并排展示多个指标）
 */
@Composable
private fun SmallStatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 2.dp),
            fontFamily = FontFamily.Serif
        )
    }
}

/**
 * 用 Compose Canvas 手绘的每日写作时长柱状图（单位：分钟）
 * 风格与字数柱状图一致，使用 Tertiary 色调区分
 */
@Composable
private fun DailyWritingTimeBarChart(data: List<DailyWritingTime>) {
    val barColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline

    // 缓存 Paint 对象，避免每帧每个柱子都重新创建（仅主题色变化时重建）
    val valuePaint = remember(onSurfaceColor) {
        android.graphics.Paint().apply {
            color = onSurfaceColor.toArgb()
            textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.SERIF
        }
    }
    val labelPaint = remember(outlineColor) {
        android.graphics.Paint().apply {
            color = outlineColor.toArgb()
            textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.SERIF
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val maxMinutes = data.maxOfOrNull { it.writingTimeMinutes } ?: 0f
            val barCount = data.size
            if (barCount == 0 || maxMinutes == 0f) return@Canvas

            val labelHeight = 36f
            val valuePadding = 16f
            val maxBarHeight = size.height - labelHeight - valuePadding
            val slotWidth = size.width / barCount
            val barWidth = slotWidth * 0.55f

            data.forEachIndexed { index, item ->
                val barHeight = (item.writingTimeMinutes / maxMinutes) * maxBarHeight
                val barX = index * slotWidth + (slotWidth - barWidth) / 2f
                val barY = size.height - labelHeight - barHeight

                // 绘制柱子（圆角矩形）
                drawRoundRect(
                    color = barColor.copy(alpha = 0.85f),
                    topLeft = Offset(barX, barY),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(8f, 8f)
                )

                // 柱顶数值（仅非零柱，显示分钟数，保留1位小数）
                if (item.writingTimeMinutes > 0f) {
                    // 超过1分钟：保留1位小数；未超过1分钟：保留1位小数（如 0.5）
                    val valueText = String.format("%.1f", item.writingTimeMinutes)
                    drawContext.canvas.nativeCanvas.apply {
                        val textX = barX + barWidth / 2f
                        val textY = barY - 6f
                        drawText(valueText, textX, textY, valuePaint)
                    }
                }

                // 底部星期标签
                val dayLabel = formatDayLabel(item.date)
                drawContext.canvas.nativeCanvas.apply {
                    val labelX = index * slotWidth + slotWidth / 2f
                    val labelY = size.height - 8f
                    drawText(dayLabel, labelX, labelY, labelPaint)
                }
            }
        }
        // 提示文字
        Text(
            text = stringResource(R.string.statistics_bar_chart_time_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp),
            fontFamily = FontFamily.Serif
        )
    }
}

// ===== 写作日历（GitHub 风格热力图）=====

/**
 * 写作日历区块
 *
 * 以 GitHub 仓库推送频率热力图样式展示近 6 个月的每日发布活动，
 * 每天格子颜色深浅代表当天已发布文章数（postCount），即使只发布 1 篇也能清晰辨认。
 */
@Composable
private fun WritingCalendarSection(calendar: WritingCalendar) {
    StatisticsGroupCard(
        title = stringResource(R.string.statistics_writing_calendar),
        icon = Icons.Filled.CalendarMonth
    ) {
        if (calendar.weeks.isEmpty()) {
            Text(
                text = stringResource(R.string.statistics_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontFamily = FontFamily.Serif
            )
        } else {
            WritingCalendarGrid(calendar)
        }
    }
}

/**
 * 写作日历热力图网格（GitHub 风格）
 *
 * 布局：
 * - 顶部：月份标签（按周列对齐，仅显示有数据的月份）
 * - 左侧：星期标签（一、三、五）
 * - 主体：横向滚动的周列，每列 7 个小格子（周一到周日）
 * - 格子颜色深浅代表当天已发布文章数（5 档分级，1 篇即用浅色确保可见）
 * - 格子点击：Toast 显示当天文章数
 * - 底部：图例（少 → 多）
 *
 * 数据刷新：上层 statistics StateFlow 通过 combine(postRepository.getAllPosts(), tagRepository.getAllTags())
 * 自动刷新，当文章发布/保存时 Room 自动推送新数据，热力图随之更新。
 */
@Composable
private fun WritingCalendarGrid(calendar: WritingCalendar) {
    val accentColor = LocalAccentColor.current
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val context = LocalContext.current

    // 尺寸参数（GitHub 风格：小格子 + 圆角 + 紧凑间距）
    val cellSize = 13.dp
    val cellGap = 3.dp
    val cornerRadius = 3.dp
    val labelWidth = 18.dp
    val monthLabelHeight = 16.dp

    val listState = rememberLazyListState()
    // 首次加载数据后滚动到最右侧，让最近的写作活动优先可见
    LaunchedEffect(calendar.weeks.size) {
        if (calendar.weeks.isNotEmpty()) {
            listState.scrollBy(10_000f)
        }
    }

    // 月份标签仅展示最近 3 个月：取 monthLabels 最后 3 个，避免顶部标签过于拥挤
    val visibleMonthLabels = remember(calendar.monthLabels) {
        calendar.monthLabels.takeLast(3)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧星期标签列（与右侧周列共用相同的垂直间距与高度，保证行对齐）
            Column(
                modifier = Modifier.width(labelWidth + cellGap),
                verticalArrangement = Arrangement.spacedBy(cellGap)
            ) {
                // 顶部占位，与月份标签高度对齐
                Box(modifier = Modifier.height(monthLabelHeight))
                // 7 行，仅一、三、五显示文字
                val dayLabels = listOf("一", "", "三", "", "五", "", "")
                dayLabels.forEach { label ->
                    Box(
                        modifier = Modifier.size(width = labelWidth, height = cellSize),
                        contentAlignment = Alignment.Center
                    ) {
                        if (label.isNotEmpty()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = outlineColor.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Serif
                            )
                        }
                    }
                }
            }

            // 右侧：横向滚动的周列
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(cellGap)
            ) {
                itemsIndexed(calendar.weeks) { index, week ->
                    Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                        // 月份标签（仅在该列对应月份首次出现时显示，且仅展示最近 3 个月）
                        val monthLabel = visibleMonthLabels.find { it.columnIndex == index }
                        Box(
                            modifier = Modifier.height(monthLabelHeight),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (monthLabel != null) {
                                Text(
                                    text = monthLabel.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = outlineColor.copy(alpha = 0.8f),
                                    fontFamily = FontFamily.Serif
                                )
                            }
                        }
                        // 7 个格子（周一到周日）
                        week.days.forEach { day ->
                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .clip(RoundedCornerShape(cornerRadius))
                                    .background(
                                        getDayBackgroundColor(day, accentColor, surfaceVariant)
                                    )
                                    .clickable {
                                        if (day != null) {
                                            val msg = if (day.postCount > 0) {
                                                "${day.date}：${day.postCount} 篇文章"
                                            } else {
                                                "${day.date}：无文章发布"
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 图例（少 → 多）
        CalendarLegend()
    }
}

/**
 * 根据当天已发布文章数计算格子背景色（5 档分级）
 *
 * - 0 篇：极浅灰（surfaceVariant alpha 0.15）
 * - 1 篇：浅色（accent alpha 0.25，确保清晰可见）
 * - 2-3 篇：accent alpha 0.5
 * - 4-6 篇：accent alpha 0.75
 * - 7+ 篇：满色（accent alpha 1.0）
 */
private fun getDayBackgroundColor(
    day: CalendarDay?,
    accentColor: Color,
    surfaceVariant: Color
): Color {
    if (day == null || day.postCount <= 0) {
        return surfaceVariant.copy(alpha = 0.15f)
    }
    return when (day.postCount) {
        1 -> accentColor.copy(alpha = 0.25f)
        in 2..3 -> accentColor.copy(alpha = 0.5f)
        in 4..6 -> accentColor.copy(alpha = 0.75f)
        else -> accentColor.copy(alpha = 1.0f)
    }
}

/**
 * 根据当天已发布文章数计算文字颜色（高文章数时白色，低文章数时深色）
 * 热力图格子较小不显示日期文字，保留以备复用并保持与背景色分级逻辑一致
 */
private fun getDayTextColor(postCount: Int): Color {
    return if (postCount >= 4) {
        Color.White
    } else {
        Color.Black.copy(alpha = 0.6f)
    }
}

/**
 * 日历图例（颜色深浅说明：少 → 多）
 * 5 个色块对应 5 档分级，与 [getDayBackgroundColor] 完全一致
 */
@Composable
private fun CalendarLegend() {
    val accentColor = LocalAccentColor.current
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.statistics_calendar_less),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontFamily = FontFamily.Serif
        )
        Spacer(modifier = Modifier.width(4.dp))
        // 5 个色块：0 篇 / 1 篇 / 2-3 篇 / 4-6 篇 / 7+ 篇
        val colors = listOf(
            surfaceVariant.copy(alpha = 0.2f),
            accentColor.copy(alpha = 0.45f),
            accentColor.copy(alpha = 0.65f),
            accentColor.copy(alpha = 0.85f),
            accentColor.copy(alpha = 1.0f)
        )
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .size(11.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.statistics_calendar_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontFamily = FontFamily.Serif
        )
    }
}

// ===== 工具函数 =====

/**
 * 格式化数字（千分位）
 */
private fun formatNumber(value: Int): String {
    return String.format("%,d", value)
}

/**
 * 格式化写作时长（分钟，Float 精确到秒）为可读字符串
 * - 0：显示 "0 分钟"
 * - 不足 1 分钟：显示 "X 秒"
 * - 不足 60 分钟：显示 "X 分 Y 秒"
 * - 1 小时以上：显示 "X 小时 Y 分 Z 秒"
 */
private fun formatWritingTime(minutes: Float): String {
    if (minutes <= 0f) return "0 分钟"
    val totalSeconds = (minutes * 60).toInt()
    val hours = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return when {
        hours > 0 -> "${hours} 小时 ${mins} 分 ${secs} 秒"
        mins > 0 -> "${mins} 分 ${secs} 秒"
        else -> "${secs} 秒"
    }
}

// 日期解析与星期符号（文件级缓存，避免每次绘制柱状图都重复创建）
private val DATE_PARSER = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private val WEEKDAY_SYMBOLS = java.text.DateFormatSymbols(Locale.getDefault()).shortWeekdays

/**
 * 将 yyyy-MM-dd 日期格式化为星期标签（如 周一、Tue）
 */
private fun formatDayLabel(dateStr: String): String {
    return try {
        val date = DATE_PARSER.parse(dateStr) ?: return ""
        val dayOfWeek = Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_WEEK)
        WEEKDAY_SYMBOLS[dayOfWeek]
    } catch (_: Exception) {
        ""
    }
}
