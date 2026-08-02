package com.gridea.android.ui.screen.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.gridea.android.R
import com.gridea.android.ui.component.ImagePickerSheet
import com.gridea.android.ui.component.MarkdownAction
import com.gridea.android.ui.component.MarkdownPreview
import com.gridea.android.ui.component.MarkdownToolbar
import com.gridea.android.ui.sharedFabElement
import com.gridea.android.ui.theme.LocalAccentColor
import com.gridea.android.util.MarkdownEditorHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 文章编辑器页面
 *
 * 对应旧版 Gridea 0.9.3 的 src/views/article/ArticleUpdate.vue
 * 以及 src/components/MonacoMarkdownEditor/Index.vue
 *
 * 功能：
 * - 标题、标签、正文编辑
 * - Markdown 工具栏（快捷插入语法）
 * - 编辑/预览双 Tab 切换
 * - 全屏编辑模式（隐藏元信息区，正文占满）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    fileName: String? = null,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val title by viewModel.title.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val content by viewModel.content.collectAsState()
    val date by viewModel.date.collectAsState()
    val published by viewModel.published.collectAsState()
    val hideInList by viewModel.hideInList.collectAsState()
    val isTop by viewModel.isTop.collectAsState()
    val wordCount by viewModel.wordCount.collectAsState()
    val wordCountGoal by viewModel.wordCountGoal.collectAsState()
    val congratulationMessage by viewModel.congratulationMessage.collectAsState()
    val writingTimeMs by viewModel.writingTimeMs.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val feature by viewModel.feature.collectAsState()
    val customUrl by viewModel.customUrl.collectAsState()

    // 全局灵动岛通知：保存成功后向用户反馈结果
    val noticeManager = com.gridea.android.ui.theme.LocalNoticeManager.current
    val scope = rememberCoroutineScope()

    // 编辑/预览 Tab 状态
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(stringResource(R.string.editor_tab_edit), stringResource(R.string.editor_tab_preview))

    // 正文 TextFieldValue（用于跟踪光标/选区位置）
    var contentFieldValue by remember {
        mutableStateOf(TextFieldValue(content))
    }

    // 全屏编辑模式
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    // 退出动画策略（区分两种入口）：
    // - FAB 新建（fileName == null，容器变换）：两步退出——内容先淡出 20ms 建空容器，再 popExit 收缩回 FAB
    // - 列表点击（fileName != null，缩放动画）：直接 popBackStack 一步退出，由 NavHost popExit 处理缩放淡出
    //   避免两步退出在缩放模式下造成额外延迟和白屏
    var isExiting by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (isExiting) 0f else 1f,
        animationSpec = tween(20, easing = FastOutSlowInEasing),
        label = "editorExitContentAlpha",
        finishedListener = {
            // 仅 FAB 新建模式需要两步退出：内容淡出完成后触发 NavHost popExit
            if (isExiting) onBack()
        }
    )

    // 返回处理：FAB 新建模式用两步退出，列表点击模式直接 pop
    fun triggerBack() {
        if (fileName == null) {
            // 容器变换模式：先淡出内容建空容器，再收缩
            isExiting = true
        } else {
            // 缩放模式：直接退出，由 NavHost popExitTransition 一步完成
            onBack()
        }
    }

    // 拦截系统返回键/手势返回：通过 ViewModel 判断是否需要保存
    // 全空（标题/标签/内容都为空）→ 不保存草稿，提示"内容为空，未保存"
    // 否则 → 保存后退出，提示"已保存"
    BackHandler {
        viewModel.savePost(
            onSaved = {
                noticeManager.showNotice("已保存")
                triggerBack()
            },
            onSkip = {
                noticeManager.showNotice("内容为空，未保存")
                triggerBack()
            }
        )
    }

    // 同步 ViewModel 内容变化到 TextFieldValue
    LaunchedEffect(content) {
        if (content != contentFieldValue.text) {
            contentFieldValue = TextFieldValue(
                text = content,
                selection = TextRange(content.length)
            )
        }
    }

    fun updateContent(newValue: TextFieldValue) {
        // 应用自动配对逻辑（输入配对符号时自动补全，或包裹选中文本）
        val paired = applyAutoPair(contentFieldValue, newValue)
        contentFieldValue = paired
        viewModel.onContentChange(paired.text)
    }

    // 字数达标通知：接入灵动岛通知栏（保留原文字内容）
    LaunchedEffect(congratulationMessage) {
        congratulationMessage?.let { msg ->
            noticeManager.showNotice(msg)
            viewModel.clearCongratulation()
        }
    }

    // 图片选择器显示状态
    var showImagePicker by remember { mutableStateOf(false) }
    // 封面图选择器显示状态（与正文插入图片的 ImagePickerSheet 区分用途）
    var showCoverPicker by remember { mutableStateOf(false) }
    // 日期选择器对话框
    var showDatePicker by remember { mutableStateOf(false) }
    // 版本历史弹窗显示状态
    var showVersionHistory by remember { mutableStateOf(false) }
    // 查找替换面板
    var showSearchPanel by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var searchMatchIndex by remember { mutableStateOf(0) }
    var searchMatchCount by remember { mutableStateOf(0) }
    var matchPositions by remember { mutableStateOf<List<Int>>(emptyList()) }
    // 保存选项弹窗：合并 URL/置顶/发布/隐藏 4 个选项
    var showSaveOptionsDialog by remember { mutableStateOf(false) }

    // 防抖计算匹配数：searchQuery 或 content 变化后延迟 220ms 重新计算
    // 避免每次按键都同步扫描全文（长文章会卡顿）
    LaunchedEffect(searchQuery, content) {
        if (searchQuery.isEmpty()) {
            searchMatchCount = 0
            searchMatchIndex = 0
            matchPositions = emptyList()
        } else {
            kotlinx.coroutines.delay(220)
            val positions = content.windowedMatches(searchQuery)
            matchPositions = positions
            searchMatchCount = positions.size
            if (searchMatchIndex >= positions.size) searchMatchIndex = 0
        }
    }

    // 切换匹配时，将光标移到当前匹配位置，TextField 自动滚动到该位置
    LaunchedEffect(searchMatchIndex, matchPositions) {
        if (matchPositions.isNotEmpty() && searchMatchIndex < matchPositions.size && searchQuery.isNotEmpty()) {
            val start = matchPositions[searchMatchIndex]
            val end = (start + searchQuery.length).coerceAtMost(content.length)
            contentFieldValue = contentFieldValue.copy(
                selection = TextRange(start, end)
            )
        }
    }

    // 加载已有文章
    LaunchedEffect(fileName) {
        if (fileName != null) {
            viewModel.loadPost(fileName)
        } else {
            viewModel.initNewPost()
        }
    }

    Scaffold(
        // 动画策略（两种入口区分）：
        // - 新建模式（fileName == null，从 FAB 进入）：挂 sharedElement 容器变换，
        //   与 HomeScreen FAB 共享 key="new_post_fab"，进入/退出都是容器变换过渡
        // - 编辑模式（fileName != null，从文章列表进入）：不挂 sharedElement，
        //   使用 NavHost 的 scale+fade 缩放动画过渡
        modifier = Modifier
            .fillMaxSize()
            .then(if (fileName == null) Modifier.sharedFabElement(key = "new_post_fab") else Modifier),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            // 全屏模式下隐藏顶部栏，平滑收起
            AnimatedVisibility(
                visible = !isFullscreen,
                enter = expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(220)),
                exit = shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(180))
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (fileName == null) {
                                stringResource(R.string.editor_new_post)
                            } else {
                                stringResource(R.string.editor_edit_post)
                            })
                            // 自动保存状态指示器
                            val status by viewModel.autoSaveStatus.collectAsState()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (status) {
                                    is AutoSaveStatus.Saving -> {
                                        // 保存中：旋转的进度指示器（16dp）
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            stringResource(R.string.editor_autosave_saving),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    is AutoSaveStatus.Pending -> {
                                        // 未保存：灰色文字
                                        Text(
                                            stringResource(R.string.editor_autosave_unsaved),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    else -> {}
                                }
                                // 已保存：绿色对勾 + 文字，2 秒后淡出
                                AnimatedVisibility(
                                    visible = status is AutoSaveStatus.Saved,
                                    enter = fadeIn(),
                                    exit = fadeOut(animationSpec = tween(500))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = Color(0xFF4CAF50)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            stringResource(R.string.editor_autosave_saved_label),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF4CAF50)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        // 查找替换（编辑模式下直接显示）
                        if (selectedTab == 0) {
                            IconButton(onClick = { showSearchPanel = !showSearchPanel }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.editor_search))
                            }
                        }
                        // 保存按钮（常用，单独放置）：点击后弹出发布选项弹窗
                        IconButton(onClick = { showSaveOptionsDialog = true }) {
                            Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.editor_save))
                        }
                        // 版本历史：放置在保存键右侧，便于直接访问
                        IconButton(onClick = { showVersionHistory = true }) {
                            Icon(Icons.Filled.History, contentDescription = stringResource(R.string.editor_version_history))
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }
        }
    ) { innerPadding ->
        // 退出动画：内容先淡出（graphicsLayer alpha），淡出完成后 NavHost popExit 收缩空容器
        // 用 Box 包裹 Column，使查找替换面板作为悬浮覆盖层不影响内容布局
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha }
        ) {
            // 编辑/预览 Tab（全屏模式下平滑收起）—— 圆角卡片风格分段切换
            // 不用 animateContentSize（与 AnimatedVisibility 的 expandVertically 叠加会抖动）
            AnimatedVisibility(
                visible = !isFullscreen,
                enter = expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(220)),
                exit = shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(180))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tabs.forEachIndexed { index, label ->
                            val isSelected = selectedTab == index
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedTab = index },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) LocalAccentColor.current
                                        else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) Color.White
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 编辑/预览内容切换：淡入淡出 + 轻微缩放，过渡更柔和
            // weight(1f)：确保 AnimatedContent 获得有界高度约束，
            // 避免 MarkdownPreview 的 verticalScroll 在动画期间收到无限高度约束导致崩溃
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    (fadeIn(tween(280)) +
                        scaleIn(initialScale = 0.96f, animationSpec = tween(280))) togetherWith
                        (fadeOut(tween(180)) +
                            scaleOut(targetScale = 1.02f, animationSpec = tween(180)))
                },
                label = "editPreviewTransition"
            ) { tab ->
                if (tab == 0) {
                    EditorContent(
                        title = title,
                        tags = tags,
                        date = date,
                        published = published,
                        hideInList = hideInList,
                        isTop = isTop,
                        contentFieldValue = contentFieldValue,
                        wordCount = wordCount,
                        wordCountGoal = wordCountGoal,
                        writingTimeMs = writingTimeMs,
                        availableTagNames = availableTags.map { it.name },
                        feature = feature,
                        customUrl = customUrl,
                        onTitleChange = viewModel::onTitleChange,
                        onTagsChange = viewModel::onTagsChange,
                        onPublishedChange = viewModel::onPublishedChange,
                        onHideInListChange = viewModel::onHideInListChange,
                        onIsTopChange = viewModel::onIsTopChange,
                        onDateClick = { showDatePicker = true },
                        onFeatureChange = viewModel::onFeatureChange,
                        onFeatureClear = viewModel::onFeatureClear,
                        onPickCover = { showCoverPicker = true },
                        onCustomUrlChange = viewModel::onCustomUrlChange,
                        onContentChange = { value ->
                            updateContent(value)
                        },
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = {
                            isFullscreen = !isFullscreen
                            // 进入全屏时切到编辑 Tab（在回调中执行，避免重组副作用）
                            if (isFullscreen) selectedTab = 0
                        },
                        onMarkdownAction = { action ->
                            if (action is MarkdownAction.Image) {
                                // 拦截图片动作，弹出图片选择器
                                showImagePicker = true
                            } else {
                                val selection = contentFieldValue.selection.start..contentFieldValue.selection.end
                                val (newText, newSelection) = applyMarkdownAction(
                                    contentFieldValue.text,
                                    selection,
                                    action
                                )
                                updateContent(TextFieldValue(
                                    text = newText,
                                    selection = TextRange(newSelection.first, newSelection.last)
                                ))
                            }
                        },
                        matchPositions = matchPositions,
                        searchQuery = searchQuery,
                        searchMatchIndex = searchMatchIndex
                    )
                } else {
                    MarkdownPreview(
                        markdown = content,
                        modifier = Modifier.fillMaxWidth(),
                        title = title.ifEmpty { null }
                    )
                }
            }
        }

            // 查找替换面板（悬浮底部覆盖层，不影响内容布局）
            AnimatedVisibility(
                visible = showSearchPanel && selectedTab == 0,
                enter = slideInVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(150)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            ) {
                SearchReplacePanel(
                    searchQuery = searchQuery,
                    replaceQuery = replaceQuery,
                    matchIndex = searchMatchIndex,
                    matchCount = searchMatchCount,
                    onSearchQueryChange = { query ->
                        searchQuery = query
                        // 匹配数由上面的 LaunchedEffect 防抖计算，无需在此同步扫描
                    },
                    onReplaceQueryChange = { replaceQuery = it },
                    onPrevious = {
                        if (searchMatchCount > 0) {
                            searchMatchIndex = (searchMatchIndex - 1 + searchMatchCount) % searchMatchCount
                        }
                    },
                    onNext = {
                        if (searchMatchCount > 0) {
                            searchMatchIndex = (searchMatchIndex + 1) % searchMatchCount
                        }
                    },
                    onReplace = {
                        val newText = content.replaceFirst(
                            searchQuery,
                            replaceQuery,
                            ignoreCase = false
                        )
                        updateContent(TextFieldValue(
                            text = newText,
                            selection = TextRange(newText.length)
                        ))
                        // content 变化后 LaunchedEffect 会自动重算匹配数
                    },
                    onReplaceAll = {
                        val newText = content.replace(searchQuery, replaceQuery)
                        updateContent(TextFieldValue(
                            text = newText,
                            selection = TextRange(newText.length)
                        ))
                        searchMatchIndex = 0
                        // content 变化后 LaunchedEffect 会自动重算匹配数
                    },
                    onClose = { showSearchPanel = false }
                )
            }
        }
    }

    // 图片选择器底部弹窗
    if (showImagePicker) {
        ImagePickerSheet(
            onImageSelected = { imageUrl ->
                showImagePicker = false
                // 在光标位置插入图片 Markdown 语法
                val cursor = contentFieldValue.selection.start
                val imageMd = "![]($imageUrl)"
                val newText = contentFieldValue.text.substring(0, cursor) +
                    imageMd +
                    contentFieldValue.text.substring(cursor)
                val newCursor = cursor + imageMd.length
                updateContent(TextFieldValue(
                    text = newText,
                    selection = TextRange(newCursor)
                ))
            },
            onDismiss = { showImagePicker = false }
        )
    }

    // 封面图选择器底部弹窗：与正文插入图片共用 ImagePickerSheet，
    // 仅回调处理不同——选中的图片 URL 直接写入 feature 字段
    if (showCoverPicker) {
        ImagePickerSheet(
            onImageSelected = { imageUrl ->
                showCoverPicker = false
                viewModel.onFeatureChange(imageUrl)
            },
            onDismiss = { showCoverPicker = false }
        )
    }

    // 日期选择器对话框：用户可修改文章创建/发布日期
    // PostData.date 格式为 "yyyy-MM-dd HH:mm:ss"，DatePicker 仅选择日期部分，
    // 选中后保留原时间部分（若原日期解析失败则补默认 00:00:00）
    if (showDatePicker) {
        val initialMillis = parseDateToMillis(date)
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.onDateChange(formatMillisToDate(millis, date))
                        }
                        showDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 版本历史弹窗
    if (showVersionHistory) {
        VersionHistorySheet(
            onDismiss = { showVersionHistory = false },
            onLoadVersions = { callback -> viewModel.loadVersions(callback) },
            onRestore = { version ->
                viewModel.restoreVersion(version)
                showVersionHistory = false
                noticeManager.showNotice("已恢复到该版本")
            },
            onDelete = { id, onDone ->
                viewModel.deleteVersion(id) {
                    onDone()
                    noticeManager.showNotice("已删除该版本")
                }
            }
        )
    }

    // 发布选项弹窗：合并文章 URL/置顶/发布/隐藏 4 个选项，点保存按钮触发
    if (showSaveOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showSaveOptionsDialog = false },
            title = { Text("发布选项") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = viewModel::onCustomUrlChange,
                        label = { Text(stringResource(R.string.editor_url_label)) },
                        placeholder = { Text(stringResource(R.string.editor_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedLabelColor = LocalAccentColor.current,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            cursorColor = LocalAccentColor.current
                        )
                    )
                    SwitchWithLabel(
                        label = stringResource(R.string.editor_publish),
                        checked = published,
                        onCheckedChange = viewModel::onPublishedChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SwitchWithLabel(
                        label = stringResource(R.string.editor_top),
                        checked = isTop,
                        onCheckedChange = viewModel::onIsTopChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SwitchWithLabel(
                        label = stringResource(R.string.editor_hide),
                        checked = hideInList,
                        onCheckedChange = viewModel::onHideInListChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSaveOptionsDialog = false
                    viewModel.savePost(
                        onSaved = {
                            noticeManager.showNotice("已保存")
                            onBack()
                        },
                        onSkip = {
                            noticeManager.showNotice("内容为空，未保存")
                            onBack()
                        }
                    )
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveOptionsDialog = false }) { Text("取消") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorContent(
    title: String,
    tags: String,
    date: String,
    published: Boolean,
    hideInList: Boolean,
    isTop: Boolean,
    contentFieldValue: TextFieldValue,
    wordCount: Int,
    wordCountGoal: Int,
    writingTimeMs: Long,
    availableTagNames: List<String>,
    feature: String,
    customUrl: String,
    onTitleChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onPublishedChange: (Boolean) -> Unit,
    onHideInListChange: (Boolean) -> Unit,
    onIsTopChange: (Boolean) -> Unit,
    onDateClick: () -> Unit,
    onFeatureChange: (String) -> Unit,
    onFeatureClear: () -> Unit,
    onPickCover: () -> Unit,
    onCustomUrlChange: (String) -> Unit,
    onContentChange: (TextFieldValue) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onMarkdownAction: (MarkdownAction) -> Unit,
    matchPositions: List<Int>,
    searchQuery: String,
    searchMatchIndex: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // 上方元信息区（全屏模式下平滑收起）
        // 不用 animateContentSize（与 AnimatedVisibility 的 expandVertically 叠加会抖动）
        AnimatedVisibility(
            visible = !isFullscreen,
            enter = expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(220)),
            exit = shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(180))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        label = { Text(stringResource(R.string.editor_title_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.titleMedium,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedLabelColor = LocalAccentColor.current,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            cursorColor = LocalAccentColor.current
                        )
                    )

                    // 标签输入框：带已有标签建议下拉
                    TagInputWithSuggestions(
                        tags = tags,
                        onTagsChange = onTagsChange,
                        availableTagNames = availableTagNames,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )

                    // 封面图：点击设置/更换，已设置时显示预览与移除按钮
                    CoverImageRow(
                        feature = feature,
                        onPickCover = onPickCover,
                        onFeatureClear = onFeatureClear,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )

                    // 日期：点击弹出日期选择器修改
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDateClick)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = stringResource(R.string.editor_date_label, date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }

        // 可滚动区域：工具栏 + 正文编辑区（小窗模式下可上滑露出；全屏下用 weight 填充）
        // 全屏模式下 TopAppBar 收起后，Scaffold 的 contentWindowInsets（默认含状态栏）会自动
        // 将状态栏高度补入 innerPadding.top，工具栏已停在状态栏下方边缘，无需额外平移
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // 工具栏（圆角卡片样式，固定在正文上方）
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                MarkdownToolbar(onAction = onMarkdownAction)
            }

            // 正文编辑区（圆角卡片样式）
            // 用 fillMaxSize() 让 TextField 填充 middle Column 的剩余空间
            // middle Column 有 weight(1f)，会随父布局中 AnimatedVisibility 的收起/展开平滑变化高度
            // 避免了 weight(1f) ↔ heightIn 离散切换导致的抖动
            TextField(
                value = contentFieldValue,
                onValueChange = onContentChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.editor_content_placeholder)) },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                textStyle = MaterialTheme.typography.bodyLarge,
                visualTransformation = if (searchQuery.isNotEmpty() && matchPositions.isNotEmpty()) {
                    SearchHighlightTransformation(matchPositions, searchQuery.length, searchMatchIndex)
                } else VisualTransformation.None
            )
        }

        // 底部状态栏：圆角卡片样式，全屏切换 + 写作时长 + 字数 + 字数目标进度
        val writingTimeText = formatWritingTime(writingTimeMs)
        // 字数目标进度（达到显示绿色，未达到跟随强调色）
        val goalProgress = if (wordCountGoal > 0) {
            (wordCount.toFloat() / wordCountGoal).coerceIn(0f, 1f)
        } else {
            0f
        }
        val goalReached = wordCountGoal > 0 && wordCount >= wordCountGoal
        val progressColor = if (goalReached) Color(0xFF4CAF50) else LocalAccentColor.current

        Card(
            modifier = Modifier
                .fillMaxWidth()
                // 添加系统导航栏 insets padding，避免底部状态栏被手势条/导航键遮挡
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 字数目标进度条
                if (wordCountGoal > 0) {
                    LinearProgressIndicator(
                        progress = { goalProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                }
                // 底部状态栏：左右分布，避免拥挤
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 左侧：全屏切换按钮（图标淡入淡出过渡）
                    IconButton(onClick = onToggleFullscreen) {
                        Crossfade(
                            targetState = isFullscreen,
                            animationSpec = tween(200),
                            label = "fullscreenIcon"
                        ) { fullscreen ->
                            Icon(
                                imageVector = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = if (fullscreen) "退出全屏" else "全屏编辑",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // 右侧：写作时长 + 字数统计 + 目标进度
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 写作时长
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = writingTimeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 3.dp)
                            )
                        }
                        // 分隔符
                        Text(
                            text = "|",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        // 字数统计
                        Text(
                            text = "${wordCount}字",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // 分隔符
                        Text(
                            text = "|",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        // 目标进度（仅设置了目标时显示）
                        if (wordCountGoal > 0) {
                            Text(
                                text = "${wordCount}/${wordCountGoal}",
                                style = MaterialTheme.typography.labelSmall,
                                color = progressColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 标签输入框 + 已有标签建议下拉
 *
 * 输入框聚焦时显示所有已有标签的下拉建议；
 * 输入文本时按"正在编辑的最后一个标签片段"进行过滤；
 * 点击建议项将其追加到当前标签列表（追加后补一个逗号，方便继续输入下一个）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagInputWithSuggestions(
    tags: String,
    onTagsChange: (String) -> Unit,
    availableTagNames: List<String>,
    modifier: Modifier = Modifier
) {
    // 输入框交互状态：用于判断聚焦
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // 下拉建议展开状态
    var showSuggestions by remember { mutableStateOf(false) }

    // 计算正在编辑的最后一个标签片段
    val currentEditingFragment: String = remember(tags) {
        // 取最后一个逗号之后的文本作为当前正在输入的标签片段
        val lastComma = tags.lastIndexOf(',')
        if (lastComma >= 0) tags.substring(lastComma + 1).trim() else tags.trim()
    }

    // 过滤建议列表：排除已输入的标签，按当前片段前缀匹配
    val existingTagsInInput: Set<String> = remember(tags) {
        tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
    val filteredSuggestions: List<String> = remember(availableTagNames, currentEditingFragment, existingTagsInInput) {
        availableTagNames
            .filter { name -> name !in existingTagsInInput }
            .filter { name ->
                if (currentEditingFragment.isEmpty()) {
                    true  // 空输入时显示全部
                } else {
                    name.contains(currentEditingFragment, ignoreCase = true)
                }
            }
            .take(8)  // 最多显示 8 条
    }

    // 聚焦时自动展开建议
    LaunchedEffect(isFocused) {
        showSuggestions = isFocused
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = tags,
            onValueChange = onTagsChange,
            label = { Text(stringResource(R.string.editor_tags_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.LocalOffer,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            interactionSource = interactionSource,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedLabelColor = LocalAccentColor.current,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                cursorColor = LocalAccentColor.current
            )
        )

        // 已有标签建议下拉
        DropdownMenu(
            expanded = showSuggestions && filteredSuggestions.isNotEmpty(),
            onDismissRequest = { showSuggestions = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            filteredSuggestions.forEach { tagName ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.LocalOffer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = LocalAccentColor.current
                            )
                            Text(
                                text = tagName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    },
                    onClick = {
                        // 将当前正在编辑的片段替换为选中的标签
                        val lastComma = tags.lastIndexOf(',')
                        val prefix = if (lastComma >= 0) tags.substring(0, lastComma + 1) else ""
                        val newTags = if (prefix.isEmpty()) {
                            tagName
                        } else {
                            // 已有逗号分隔时追加新标签
                            "$prefix $tagName"
                        }
                        onTagsChange("$newTags, ")
                        showSuggestions = true  // 保持聚焦状态
                    }
                )
            }
        }
    }
}

/**
 * 格式化写作时长（毫秒）为可读字符串
 * - 不足 1 分钟：显示 "X 秒"
 * - 1-60 分钟：显示 "X 分钟"
 * - 1 小时以上：显示 "X 小时 Y 分"
 */
private fun formatWritingTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours} 小时 ${minutes} 分"
        minutes > 0 -> "${minutes} 分钟"
        else -> "${totalSeconds.coerceAtLeast(0)} 秒"
    }
}

/**
 * 封面图行：未设置时显示"设置封面图"按钮，已设置时显示缩略图 + 更换/移除按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverImageRow(
    feature: String,
    onPickCover: () -> Unit,
    onFeatureClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalAccentColor.current
    if (feature.isEmpty()) {
        // 未设置封面图：仅显示一个"设置封面图"的圆角按钮
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onPickCover),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = accentColor
                )
                Text(
                    text = stringResource(R.string.editor_cover_add),
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentColor
                )
            }
        }
    } else {
        // 已设置封面图：左侧缩略图（16:9），右侧更换/移除按钮
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 缩略图（圆角，固定 64×36 的 16:9 尺寸）
            AsyncImage(
                model = feature,
                contentDescription = stringResource(R.string.editor_cover_label),
                modifier = Modifier
                    .size(width = 64.dp, height = 36.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            // 更换按钮（圆角卡片）
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onPickCover),
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(imageVector = Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp), tint = accentColor)
                    Text(text = stringResource(R.string.editor_cover_change), style = MaterialTheme.typography.labelMedium, color = accentColor)
                }
            }
            // 移除按钮（圆角卡片，警示色填充）
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onFeatureClear),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    Text(text = stringResource(R.string.editor_cover_remove), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * 将 "yyyy-MM-dd HH:mm:ss" 格式的日期字符串解析为 epoch 毫秒（取当天 00:00 UTC）。
 * 解析失败时返回当前时间，确保 DatePicker 初始值始终合法。
 */
private fun parseDateToMillis(dateStr: String): Long {
    return try {
        // 只取日期部分，避免时间部分影响 DatePicker 初始选中
        val dateOnly = dateStr.substringBefore(' ').trim()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        // 不设置时区转换，使用 UTC 以匹配 DatePicker 的 selectedDateMillis 语义
        val utc = TimeZone.getTimeZone("UTC")
        sdf.timeZone = utc
        sdf.parse(dateOnly)?.time ?: System.currentTimeMillis()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}

/**
 * 将 DatePicker 选中的 epoch 毫秒格式化为 "yyyy-MM-dd HH:mm:ss"，
 * 保留原日期字符串中的时间部分（若解析失败则使用 00:00:00）。
 */
private fun formatMillisToDate(millis: Long, originalDateStr: String): String {
    // 从原日期字符串提取时间部分 "HH:mm:ss"
    val timePart = try {
        val fullPart = originalDateStr.substringAfter(' ', "").trim()
        if (fullPart.matches(Regex("\\d{2}:\\d{2}:\\d{2}"))) fullPart else "00:00:00"
    } catch (_: Exception) {
        "00:00:00"
    }
    // 把 millis 当作 UTC 当天零点，转回本地日期
    val utc = TimeZone.getTimeZone("UTC")
    val dateSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = utc }
    val dateOnly = dateSdf.format(Date(millis))
    return "$dateOnly $timePart"
}

@Composable
private fun SwitchWithLabel(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = LocalAccentColor.current,
                checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.5f),
                checkedBorderColor = LocalAccentColor.current
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * 应用 Markdown 动作到文本
 */
private fun applyMarkdownAction(
    text: String,
    selection: IntRange,
    action: MarkdownAction
): Pair<String, IntRange> {
    return when (action) {
        MarkdownAction.Bold -> MarkdownEditorHelper.wrapSelection(text, selection, "**", "**", "加粗文本")
        MarkdownAction.Italic -> MarkdownEditorHelper.wrapSelection(text, selection, "*", "*", "斜体文本")
        MarkdownAction.Strikethrough -> MarkdownEditorHelper.wrapSelection(text, selection, "~~", "~~", "删除线文本")
        MarkdownAction.Mark -> MarkdownEditorHelper.wrapSelection(text, selection, "==", "==", "高亮文本")
        MarkdownAction.Superscript -> MarkdownEditorHelper.wrapSelection(text, selection, "^", "^", "上标")
        MarkdownAction.Subscript -> MarkdownEditorHelper.wrapSelection(text, selection, "~", "~", "下标")
        MarkdownAction.Heading -> MarkdownEditorHelper.insertLinePrefix(text, selection, "## ")
        MarkdownAction.Quote -> MarkdownEditorHelper.insertLinePrefix(text, selection, "> ")
        MarkdownAction.CodeInline -> MarkdownEditorHelper.wrapSelection(text, selection, "`", "`", "代码")
        MarkdownAction.CodeBlock -> MarkdownEditorHelper.insertCodeBlock(text, selection)
        MarkdownAction.UnorderedList -> MarkdownEditorHelper.toggleLinePrefix(text, selection, "- ")
        MarkdownAction.OrderedList -> MarkdownEditorHelper.toggleLinePrefix(text, selection, "1. ")
        MarkdownAction.TaskList -> MarkdownEditorHelper.insertTaskList(text, selection)
        MarkdownAction.Link -> MarkdownEditorHelper.insertLink(text, selection)
        MarkdownAction.Image -> MarkdownEditorHelper.insertImage(text, selection)
        MarkdownAction.Table -> MarkdownEditorHelper.insertTable(text, selection)
        MarkdownAction.HorizontalRule -> MarkdownEditorHelper.insertHorizontalRule(text, selection)
        is MarkdownAction.Template -> MarkdownEditorHelper.insertAtCursor(text, selection, action.content)
    }
}

/**
 * 自动配对逻辑
 *
 * 当用户输入配对符号（( [ { ` * ~ = ^）时：
 * - 若有选中文本，用配对符号包裹选中文本
 * - 若无选中文本，插入配对符号并将光标置于中间
 *
 * 仅在"新增单个字符"时触发，不影响删除、粘贴等操作
 *
 * @param oldValue 修改前的 TextFieldValue
 * @param newValue 修改后的 TextFieldValue
 * @return 处理后的 TextFieldValue（若无需配对则原样返回）
 */
private fun applyAutoPair(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
    // 配对符号映射
    val pairing = mapOf(
        '(' to ')',
        '[' to ']',
        '{' to '}',
        '`' to '`',
        '*' to '*',
        '~' to '~',
        '=' to '=',
        '^' to '^'
    )

    // 计算本次操作净增字符数 = 新文本长度 - (旧文本长度 - 被替换的选区长度)
    val oldSelectionLength = (oldValue.selection.end - oldValue.selection.start).coerceAtLeast(0)
    val added = newValue.text.length - (oldValue.text.length - oldSelectionLength)

    // 仅处理"净增 1 个字符且光标折叠"的情况（即普通键入）
    if (added != 1 || newValue.selection.start != newValue.selection.end) {
        return newValue
    }

    val cursor = newValue.selection.start
    if (cursor <= 0) return newValue

    val typedChar = newValue.text[cursor - 1]
    val closing = pairing[typedChar] ?: return newValue

    return if (oldSelectionLength > 0) {
        // 原有选中文本被替换为单个配对符 → 改为用配对符包裹原选中文本
        val selectedText = oldValue.text.substring(oldValue.selection.start, oldValue.selection.end)
        val before = oldValue.text.substring(0, oldValue.selection.start)
        val after = oldValue.text.substring(oldValue.selection.end)
        val newText = before + typedChar + selectedText + closing + after
        val newSelStart = before.length + 1
        val newSelEnd = newSelStart + selectedText.length
        TextFieldValue(
            text = newText,
            selection = TextRange(newSelStart, newSelEnd)
        )
    } else {
        // 无选区 → 插入配对符号，光标置于中间
        val before = newValue.text.substring(0, cursor - 1)
        val after = newValue.text.substring(cursor)
        val newText = before + typedChar + closing + after
        TextFieldValue(
            text = newText,
            selection = TextRange(cursor, cursor)
        )
    }
}

/**
 * 查找替换面板
 * 圆角卡片样式，输入框带明确标签，按钮分组排列
 */
@Composable
private fun SearchReplacePanel(
    searchQuery: String,
    replaceQuery: String,
    matchIndex: Int,
    matchCount: Int,
    onSearchQueryChange: (String) -> Unit,
    onReplaceQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 第一行：查找输入 + 计数 + 导航 + 关闭
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.editor_search)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedLabelColor = LocalAccentColor.current,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = LocalAccentColor.current
                    )
                )
                Text(
                    text = if (matchCount > 0) "${matchIndex + 1}/$matchCount" else "0/0",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 36.dp)
                )
                IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                }
                IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }
            // 第二行：替换输入 + 替换/全部替换按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceQueryChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.editor_replace)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedLabelColor = LocalAccentColor.current,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = LocalAccentColor.current
                    )
                )
                Button(onClick = onReplace, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current, contentColor = androidx.compose.ui.graphics.Color.White)) {
                    Text(stringResource(R.string.editor_replace))
                }
                Button(onClick = onReplaceAll, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = LocalAccentColor.current)) {
                    Text(stringResource(R.string.editor_replace_all))
                }
            }
        }
    }
}

/**
 * 统计字符串中匹配子串的数量（重叠不计）
 */
private fun String.windowedMatches(query: String): List<Int> {
    if (query.isEmpty()) return emptyList()
    val result = mutableListOf<Int>()
    var index = indexOf(query)
    while (index >= 0) {
        result.add(index)
        index = indexOf(query, index + query.length)
    }
    return result
}

/**
 * 查找高亮视觉变换：在 TextField 中高亮所有匹配项
 * 当前匹配项使用更深的背景色突出显示
 *
 * @param matchPositions 匹配项起始位置列表
 * @param matchLength 匹配文本长度
 * @param currentIndex 当前选中的匹配项索引（0-based）
 */
private class SearchHighlightTransformation(
    private val matchPositions: List<Int>,
    private val matchLength: Int,
    private val currentIndex: Int
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (matchPositions.isEmpty() || matchLength <= 0) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val builder = AnnotatedString.Builder(text)
        matchPositions.forEachIndexed { index, start ->
            val end = (start + matchLength).coerceAtMost(text.length)
            if (start < text.length && end > start) {
                if (index == currentIndex) {
                    builder.addStyle(
                        SpanStyle(
                            background = Color(0x50FFC107),
                            fontStyle = FontStyle.Italic
                        ),
                        start, end
                    )
                } else {
                    builder.addStyle(
                        SpanStyle(
                            background = Color(0x28FFEB3B)
                        ),
                        start, end
                    )
                }
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
