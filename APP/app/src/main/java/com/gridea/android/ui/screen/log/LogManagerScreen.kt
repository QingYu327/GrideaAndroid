package com.gridea.android.ui.screen.log

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gridea.android.R
import com.gridea.android.data.model.LogEntry
import com.gridea.android.data.model.LogLevel
import com.gridea.android.ui.theme.DangerColor
import com.gridea.android.ui.theme.LocalAccentColor
import com.gridea.android.ui.theme.LocalNoticeManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志管理页面
 *
 * 功能：
 * - 按级别（全部/调试/信息/警告/错误/操作）筛选日志
 * - 按分类筛选（从已有日志动态提取）
 * - 关键词搜索日志消息
 * - 点击单条日志展开/收起堆栈信息
 * - 导出全部日志为 .txt 文件（通过系统文件选择器）
 * - 一键清空所有日志（带确认对话框）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogManagerScreen(
    onBack: () -> Unit,
    viewModel: LogManagerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val accentColor = LocalAccentColor.current
    val noticeManager = LocalNoticeManager.current

    val logs by viewModel.logs.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val levelFilter by viewModel.levelFilter.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val keyword by viewModel.keyword.collectAsState()
    val exportFile by viewModel.exportFile.collectAsState()
    val showWarningsErrorsOnly by viewModel.showWarningsErrorsOnly.collectAsState()
    val sortField by viewModel.sortField.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    // 加载状态：true 时显示骨架屏占位符，避免日志过多时主线程渲染卡顿感
    val isLoading by viewModel.isLoading.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }
    var expandedLogId by remember { mutableStateOf<Long?>(null) }

    // 日志列表滚动状态：用于"返回顶部"FAB
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 是否显示返回顶部按钮：列表已向下滚动（非第一项）且不在加载/空状态
    val showBackToTop by remember {
        derivedStateOf {
            !isLoading && logs.isNotEmpty() &&
                listState.firstVisibleItemIndex > 0
        }
    }
    // FAB 显示/隐藏动画（与文章页 FAB 一致的淡出 + 缩小风格）
    val fabAlpha by animateFloatAsState(
        targetValue = if (showBackToTop) 1f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "logFabAlpha"
    )
    val fabScale by animateFloatAsState(
        targetValue = if (showBackToTop) 1f else 0.5f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "logFabScale"
    )

    // 导出文件选择器：用户选择保存位置后，将导出的临时文件内容拷贝过去
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val src = exportFile
        if (uri != null && src != null && src.exists()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    src.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                noticeManager.showNotice(context.getString(R.string.log_export_success))
            } catch (e: Exception) {
                noticeManager.showNotice(context.getString(R.string.log_export_failed))
            }
        }
        viewModel.consumeExportFile()
    }

    // 监听导出文件就绪：触发系统文件选择器
    androidx.compose.runtime.LaunchedEffect(exportFile) {
        if (exportFile != null) {
            val fileName = "gridea_log_${System.currentTimeMillis()}.txt"
            exportLauncher.launch(fileName)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = accentColor
                        )
                    }
                },
                actions = {
                    // 导出按钮
                    IconButton(onClick = { viewModel.exportLogs() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(R.string.log_export),
                            tint = accentColor
                        )
                    }
                    // 清空按钮
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.log_clear),
                            tint = accentColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ===== 筛选栏 =====
            LogFilterBar(
                levelFilter = levelFilter,
                categoryFilter = categoryFilter,
                categories = categories,
                keyword = keyword,
                showWarningsErrorsOnly = showWarningsErrorsOnly,
                sortField = sortField,
                sortOrder = sortOrder,
                onLevelChange = viewModel::setLevelFilter,
                onCategoryChange = viewModel::setCategoryFilter,
                onKeywordChange = viewModel::setKeyword,
                onShowWarningsErrorsOnlyChange = viewModel::setShowWarningsErrorsOnly,
                onSortFieldChange = viewModel::setSortField,
                onSortOrderChange = viewModel::setSortOrder
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ===== 日志列表 =====
            // 加载期间显示骨架屏占位符，避免日志过多时主线程一次性渲染大量 item 造成卡顿
            // isLoading=false 后直接展示真实列表或空状态（不使用 Crossfade，避免过渡期
            // 骨架屏与列表双重组合导致掉帧）
            if (isLoading) {
                LogListSkeleton()
            } else if (logs.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.log_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = logs,
                        key = { it.id }
                    ) { entry ->
                        LogItemCard(
                            entry = entry,
                            isExpanded = expandedLogId == entry.id,
                            onClick = {
                                expandedLogId = if (expandedLogId == entry.id) null else entry.id
                            }
                        )
                    }
                }
            }
        }
    }
        // 返回顶部悬浮按钮：列表向下滚动后出现，点击平滑滚动到顶部
        if (showBackToTop || fabAlpha > 0f) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                containerColor = accentColor,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp)
                    .graphicsLayer {
                        alpha = fabAlpha
                        scaleX = fabScale
                        scaleY = fabScale
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = stringResource(R.string.log_back_to_top)
                )
            }
        }
    }

    // 清空确认对话框：通过下拉菜单选择清除范围
    if (showClearDialog) {
        ClearLogsDialog(
            onDismiss = { showClearDialog = false },
            onClearAll = {
                viewModel.clearLogs()
                showClearDialog = false
                noticeManager.showNotice("已清除全部日志")
            },
            onClearOlderThan = { days ->
                viewModel.clearLogsOlderThanDays(days)
                showClearDialog = false
                noticeManager.showNotice("已清除 ${days} 天以外的日志")
            }
        )
    }
}

/**
 * 清除日志对话框
 *
 * 用圆角卡片列表展示清除范围选项，点击即选中并高亮，
 * 配合底部"确定"按钮执行清除操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearLogsDialog(
    onDismiss: () -> Unit,
    onClearAll: () -> Unit,
    onClearOlderThan: (Int) -> Unit
) {
    // 清除范围选项：清除全部 + 各时间范围
    val options = remember {
        listOf(
            ClearOption("清除全部日志", isAll = true, days = 0, isDanger = true),
            ClearOption("清除 3 天以外的日志", isAll = false, days = 3, isDanger = false),
            ClearOption("清除 5 天以外的日志", isAll = false, days = 5, isDanger = false),
            ClearOption("清除 7 天以外的日志", isAll = false, days = 7, isDanger = false)
        )
    }
    var selectedOption by remember { mutableStateOf<ClearOption?>(null) }
    val accentColor = LocalAccentColor.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清除日志", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    text = "请选择清除范围",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                // 下拉选择框（圆角卡片风格）
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selectedOption != null) accentColor.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = selectedOption?.label ?: "请选择...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selectedOption != null) {
                                    if (selectedOption!!.isDanger) DangerColor
                                    else MaterialTheme.colorScheme.onSurface
                                } else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontWeight = if (selectedOption != null) FontWeight.SemiBold
                                    else FontWeight.Normal
                            )
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = expanded
                            )
                        }
                    }
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    ) {
                        options.forEach { option ->
                            val isSelected = selectedOption == option
                            val optionColor = if (option.isDanger) DangerColor else accentColor
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (option.isDanger) DangerColor
                                            else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.SemiBold
                                            else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    selectedOption = option
                                    expanded = false
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 14.dp, vertical = 8.dp
                                )
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(optionColor.copy(alpha = 0.3f))
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val option = selectedOption
                    if (option != null) {
                        if (option.isAll) onClearAll() else onClearOlderThan(option.days)
                    }
                },
                enabled = selectedOption != null
            ) {
                Text("确定", color = if (selectedOption != null) DangerColor
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = accentColor)
            }
        }
    )
}

/**
 * 清除选项数据
 */
private data class ClearOption(
    val label: String,
    val isAll: Boolean,
    val days: Int,
    val isDanger: Boolean
)

/**
 * 日志筛选栏：级别 FilterChip 横向滚动 + 分类筛选 + 排序按钮 + 搜索框
 */
@Composable
private fun LogFilterBar(
    levelFilter: LogLevel?,
    categoryFilter: String?,
    categories: List<String>,
    keyword: String,
    showWarningsErrorsOnly: Boolean,
    sortField: LogSortField,
    sortOrder: LogSortOrder,
    onLevelChange: (LogLevel?) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onKeywordChange: (String) -> Unit,
    onShowWarningsErrorsOnlyChange: (Boolean) -> Unit,
    onSortFieldChange: (LogSortField) -> Unit,
    onSortOrderChange: (LogSortOrder) -> Unit
) {
    val levels = remember {
        listOf<Pair<LogLevel?, Int>>(
            null to R.string.log_level_all,
            LogLevel.DEBUG to R.string.log_level_debug,
            LogLevel.INFO to R.string.log_level_info,
            LogLevel.WARN to R.string.log_level_warn,
            LogLevel.ERROR to R.string.log_level_error,
            LogLevel.ACTION to R.string.log_level_action
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 级别筛选 Chip 行（横向滚动）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            levels.forEach { (level, labelRes) ->
                FilterChip(
                    selected = levelFilter == level,
                    onClick = { onLevelChange(level) },
                    label = { Text(stringResource(labelRes), fontSize = 12.sp) }
                )
            }
        }

        // 分类筛选 Chip 行（横向滚动）
        if (categories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = categoryFilter.isNullOrBlank(),
                    onClick = { onCategoryChange(null) },
                    label = { Text(stringResource(R.string.log_category_all), fontSize = 12.sp) }
                )
                categories.forEach { cat ->
                    FilterChip(
                        selected = categoryFilter == cat,
                        onClick = { onCategoryChange(cat) },
                        label = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }
        }

        // 仅显示存在警告和错误的日志 Chip + 排序按钮（同一行，Chip 占左侧，排序按钮占右侧）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = showWarningsErrorsOnly,
                onClick = { onShowWarningsErrorsOnlyChange(!showWarningsErrorsOnly) },
                label = { Text("仅显示警告和错误", fontSize = 12.sp) }
            )
            SortButton(
                sortField = sortField,
                sortOrder = sortOrder,
                onSortFieldChange = onSortFieldChange,
                onSortOrderChange = onSortOrderChange
            )
        }

        // 搜索框
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.log_search_hint), fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 排序按钮：溢出菜单风格（DropdownMenu）
 * 点击 Sort 图标弹出菜单，选择排序维度和方向
 */
@Composable
private fun SortButton(
    sortField: LogSortField,
    sortOrder: LogSortOrder,
    onSortFieldChange: (LogSortField) -> Unit,
    onSortOrderChange: (LogSortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val accentColor = LocalAccentColor.current

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "排序",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // 排序维度分组标题
            Text(
                text = "排序维度",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            DropdownMenuItem(
                text = { Text("时间") },
                onClick = {
                    onSortFieldChange(LogSortField.TIME)
                    expanded = false
                },
                leadingIcon = {
                    if (sortField == LogSortField.TIME) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("重要性程度") },
                onClick = {
                    onSortFieldChange(LogSortField.IMPORTANCE)
                    expanded = false
                },
                leadingIcon = {
                    if (sortField == LogSortField.IMPORTANCE) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 排序方向分组标题
            Text(
                text = "排序方向",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            DropdownMenuItem(
                text = { Text("倒序（新→旧）") },
                onClick = {
                    onSortOrderChange(LogSortOrder.DESC)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp),
                        tint = if (sortOrder == LogSortOrder.DESC) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (sortOrder == LogSortOrder.DESC) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("正序（旧→新）") },
                onClick = {
                    onSortOrderChange(LogSortOrder.ASC)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp),
                        tint = if (sortOrder == LogSortOrder.ASC) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (sortOrder == LogSortOrder.ASC) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                }
            )
        }
    }
}

/**
 * 日志列表骨架屏占位符
 *
 * 加载期间显示 8 个固定高度的灰色圆角卡片，配合呼吸式 alpha 动画，
 * 让用户感知"内容正在加载"而非"应用卡死"。日志较多时避免主线程一次性渲染大量 item。
 */
@Composable
private fun LogListSkeleton() {
    // 呼吸式 alpha 动画：0.4 ↔ 0.8 循环
    val transition = rememberInfiniteTransition(label = "skeleton_breath")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )

    // 8 个占位卡片，模拟真实日志卡片形状（RoundedCornerShape(10.dp)）
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false
    ) {
        items(8) { index ->
            SkeletonLogCard(alpha = alpha)
        }
    }
}

/**
 * 单个骨架屏日志卡片
 *
 * 模拟真实日志卡片的布局：第一行（彩色小标签 + 时间占位条），第二行（消息占位条）。
 */
@Composable
private fun SkeletonLogCard(alpha: Float) {
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.18f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 第一行：标签 + 时间占位
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 级别标签占位（小圆角方块）
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = placeholderColor
                ) {
                    Spacer(modifier = Modifier.size(width = 28.dp, height = 12.dp))
                }
                // 时间占位条
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = placeholderColor
                ) {
                    Spacer(modifier = Modifier.size(width = 110.dp, height = 10.dp))
                }
            }
            // 第二行：消息文本占位条（更长）
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = placeholderColor
            ) {
                Spacer(modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(12.dp))
            }
        }
    }
}

/**
 * 单条日志卡片
 *
 * 显示时间、级别彩色标签、分类、消息；点击展开堆栈信息。
 */
@Composable
private fun LogItemCard(
    entry: LogEntry,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault()) }
    val levelColor = remember(entry.level) { levelColor(entry.level) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 第一行：时间 + 级别标签 + 分类
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 级别彩色标签
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = levelColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = entry.level.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        color = levelColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // 分类
                Text(
                    text = entry.category,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
                // 时间
                Text(
                    text = sdf.format(Date(entry.timestamp)),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            // 第二行：消息
            Text(
                text = entry.message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )
            // 堆栈信息（展开时显示）
            AnimatedVisibility(
                visible = isExpanded && !entry.stackTrace.isNullOrEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.log_stack_trace),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = entry.stackTrace ?: "",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

/**
 * 日志级别对应的颜色
 */
private fun levelColor(level: LogLevel): Color {
    return when (level) {
        LogLevel.DEBUG -> Color(0xFF6B7280)   // 灰
        LogLevel.INFO -> Color(0xFF2563EB)    // 蓝
        LogLevel.WARN -> Color(0xFFD97706)    // 橙
        LogLevel.ERROR -> Color(0xFFDC2626)   // 红
        LogLevel.ACTION -> Color(0xFF7C3AED)  // 紫
    }
}
