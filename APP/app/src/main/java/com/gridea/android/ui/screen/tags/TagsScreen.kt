package com.gridea.android.ui.screen.tags

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import com.gridea.android.R
import com.gridea.android.data.model.Post
import com.gridea.android.data.repository.TagWithCount
import com.gridea.android.ui.PressableFloatingActionButton
import com.gridea.android.ui.theme.LocalAccentColor

/**
 * 标签管理页面
 *
 * 对应旧版 Gridea 0.9.3 的 src/views/tags/Index.vue
 *
 * 功能：
 * - 标签云展示（带文章计数）
 * - 点击标签查看该标签下的文章列表
 * - 新建标签（供编辑器引用）
 * - 删除标签（已使用的标签会自动从相关文章中移除引用）
 *
 * 批量管理（与菜单页 / 友链页统一模式）：
 * - 长按标签卡片进入选择模式
 * - 选择模式下顶部悬浮圆角卡片操作栏（slideInVertically + fadeIn 进入）
 * - 支持全选 / 取消全选、批量删除（带确认弹窗）
 * - 选择模式下隐藏 FAB；列表清空后自动退出选择模式
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onPostClick: (String) -> Unit,
    embedded: Boolean = false,
    onSelectionStateChange: ((com.gridea.android.ui.component.SelectionToolbarState?) -> Unit)? = null,
    viewModel: TagsViewModel = hiltViewModel()
) {
    val tags by viewModel.tags.collectAsState()
    val selectedTagName by viewModel.selectedTagName.collectAsState()
    val selectedTagPosts by viewModel.selectedTagPosts.collectAsState()

    // 全局灵动岛通知：删除标签后向用户反馈结果
    val noticeManager = com.gridea.android.ui.theme.LocalNoticeManager.current

    // 删除确认对话框状态：保存待删除标签及其文章计数
    var tagToDelete by remember { mutableStateOf<TagWithCount?>(null) }
    // 新建标签对话框状态
    var showCreateDialog by remember { mutableStateOf(false) }

    // 批量选择模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTagNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    // 列表滚动状态：任一列表滑动时立即隐藏 FAB，两个都停止后延缓 1s 再出现
    // 标签云和标签详情各有独立的 listState
    val cloudListState = rememberLazyListState()
    val detailListState = rememberLazyListState()
    var isFabVisible by remember { mutableStateOf(true) }
    LaunchedEffect(cloudListState.isScrollInProgress, detailListState.isScrollInProgress) {
        if (cloudListState.isScrollInProgress || detailListState.isScrollInProgress) {
            isFabVisible = false
        } else {
            kotlinx.coroutines.delay(1000)
            isFabVisible = true
        }
    }
    val fabAlpha by animateFloatAsState(
        targetValue = if (isFabVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "tagsFabAlpha"
    )
    val fabScale by animateFloatAsState(
        targetValue = if (isFabVisible) 1f else 0.5f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "tagsFabScale"
    )

    // 切走再切回时重置为标签云初始视图：监听 ON_START（从其他 Tab 切回或从编辑器返回均触发）
    // 用户确认：从编辑器返回时直接回到标签云，不再尝试保持标签详情视图
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.clearSelection()
                isSelectionMode = false
                selectedTagNames = emptySet()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 列表为空时自动退出选择模式（避免选中不存在的条目）
    LaunchedEffect(tags) {
        if (tags.isEmpty() && isSelectionMode) {
            isSelectionMode = false
            selectedTagNames = emptySet()
        }
    }

    // 标签详情视图下拦截系统返回/手势返回：回到标签云而非直接 popBackStack 跳出整个 Tags 页
    // 选择模式下拦截返回：退出选择模式
    BackHandler(enabled = selectedTagName != null || isSelectionMode) {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedTagNames = emptySet()
        } else {
            viewModel.clearSelection()
        }
    }

    // 全选 / 取消全选所需的全集与状态
    val allTagNames = remember(tags) { tags.map { it.name }.toSet() }
    val isAllSelected = selectedTagNames.isNotEmpty() && selectedTagNames == allTagNames

    // 向父页面（PagesScreen）上报选择状态，让 TopAppBar 渲染全选/删除按钮
    LaunchedEffect(isSelectionMode, selectedTagNames.size, isAllSelected) {
        onSelectionStateChange?.invoke(
            if (isSelectionMode) com.gridea.android.ui.component.SelectionToolbarState(
                selectedCount = selectedTagNames.size,
                isAllSelected = isAllSelected,
                onToggleSelectAll = {
                    selectedTagNames = if (isAllSelected) emptySet() else allTagNames
                },
                onDelete = { showBatchDeleteDialog = true }
            ) else null
        )
    }

    // 嵌入模式：直接用 Box（避免 Scaffold 的 contentWindowInsets 造成顶部空白间距）
    if (embedded) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (tags.isEmpty()) {
                // 空状态
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalOffer,
                        contentDescription = null,
                        tint = LocalAccentColor.current.copy(alpha = 0.4f),
                        modifier = Modifier.size(120.dp)
                    )
                    Text(
                        text = stringResource(R.string.tags_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                    Text(
                        text = stringResource(R.string.tags_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LocalAccentColor.current,
                            contentColor = androidx.compose.ui.graphics.Color.White
                        ),
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("新建标签", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            } else {
                AnimatedContent(
                    targetState = selectedTagName,
                    transitionSpec = {
                        (fadeIn(tween(280)) +
                            scaleIn(initialScale = 0.96f, animationSpec = tween(280))) togetherWith
                            (fadeOut(tween(180)) +
                                scaleOut(targetScale = 1.02f, animationSpec = tween(180)))
                    },
                    label = "tagCloudDetailTransition"
                ) { name ->
                    if (name != null) {
                        TagDetailContent(
                            posts = selectedTagPosts,
                            onPostClick = { fileName ->
                                // 导航到编辑器前先清除标签选中状态，
                                // 这样返回时已经是标签云视图，不会触发 AnimatedContent 过渡闪烁
                                viewModel.clearSelection()
                                onPostClick(fileName)
                            },
                            listState = detailListState,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        TagsCloudContent(
                            tags = tags,
                            listState = cloudListState,
                            onTagClick = { tagName ->
                                if (isSelectionMode) {
                                    selectedTagNames = if (tagName in selectedTagNames) {
                                        selectedTagNames - tagName
                                    } else {
                                        selectedTagNames + tagName
                                    }
                                    if (selectedTagNames.isEmpty()) isSelectionMode = false
                                } else {
                                    viewModel.loadPostsByTag(tagName)
                                }
                            },
                            onTagLongClick = { tagName ->
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedTagNames = setOf(tagName)
                                } else {
                                    selectedTagNames = if (tagName in selectedTagNames) {
                                        selectedTagNames - tagName
                                    } else {
                                        selectedTagNames + tagName
                                    }
                                    if (selectedTagNames.isEmpty()) isSelectionMode = false
                                }
                            },
                            onTagDelete = { tag -> tagToDelete = tag },
                            isSelectionMode = isSelectionMode,
                            selectedTagNames = selectedTagNames,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // 选择模式下隐藏 FAB
            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = scaleIn(initialScale = 0.6f, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = scaleOut(targetScale = 0.6f, animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                PressableFloatingActionButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .padding(end = 16.dp, bottom = 176.dp)
                        .graphicsLayer {
                            alpha = fabAlpha
                            scaleX = fabScale
                            scaleY = fabScale
                        },
                    containerColor = LocalAccentColor.current,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tags_new))
                }
            }
        }
    } else {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (selectedTagName != null) "标签: $selectedTagName" else stringResource(R.string.tags_title))
                },
                navigationIcon = {
                    if (selectedTagName != null) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        },
        floatingActionButton = {
            // 选择模式下隐藏 FAB：AnimatedVisibility 平滑过渡
            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = scaleIn(initialScale = 0.6f, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = scaleOut(targetScale = 0.6f, animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(160))
            ) {
                PressableFloatingActionButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .padding(bottom = 160.dp)
                        .graphicsLayer {
                            alpha = fabAlpha
                            scaleX = fabScale
                            scaleY = fabScale
                        },
                    containerColor = LocalAccentColor.current,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tags_new))
                }
            }
        }
    ) { innerPadding ->
        // Box 容器：内容
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (tags.isEmpty()) {
                // 空状态：与 HomeScreen 一致的呼吸动画 + 大图标
                val infiniteTransition = rememberInfiniteTransition(label = "tagEmptyPulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "tagPulseAlpha"
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalOffer,
                        contentDescription = null,
                        tint = LocalAccentColor.current.copy(alpha = pulseAlpha),
                        modifier = Modifier.size(120.dp)
                    )
                    Text(
                        text = stringResource(R.string.tags_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                    Text(
                        text = stringResource(R.string.tags_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    // 新建标签按钮（强调色填充）
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LocalAccentColor.current,
                            contentColor = androidx.compose.ui.graphics.Color.White
                        ),
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "新建标签",
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            } else {
                // 标签云 ↔ 标签详情切换：缩放 + 淡入淡出过渡，与其他页面切换风格一致
                AnimatedContent(
                    targetState = selectedTagName,
                    transitionSpec = {
                        (fadeIn(tween(280)) +
                            scaleIn(initialScale = 0.96f, animationSpec = tween(280))) togetherWith
                            (fadeOut(tween(180)) +
                                scaleOut(targetScale = 1.02f, animationSpec = tween(180)))
                    },
                    label = "tagCloudDetailTransition"
                ) { name ->
                    if (name != null) {
                        // 标签详情：展示该标签下的文章列表
                        TagDetailContent(
                            posts = selectedTagPosts,
                            onPostClick = onPostClick,
                            listState = detailListState,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // 标签云
                        TagsCloudContent(
                            tags = tags,
                            listState = cloudListState,
                            onTagClick = { tagName ->
                                if (isSelectionMode) {
                                    selectedTagNames = if (tagName in selectedTagNames) {
                                        selectedTagNames - tagName
                                    } else {
                                        selectedTagNames + tagName
                                    }
                                    if (selectedTagNames.isEmpty()) isSelectionMode = false
                                } else {
                                    viewModel.loadPostsByTag(tagName)
                                }
                            },
                            onTagLongClick = { tagName ->
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedTagNames = setOf(tagName)
                                } else {
                                    selectedTagNames = if (tagName in selectedTagNames) {
                                        selectedTagNames - tagName
                                    } else {
                                        selectedTagNames + tagName
                                    }
                                    if (selectedTagNames.isEmpty()) isSelectionMode = false
                                }
                            },
                            onTagDelete = { tag -> tagToDelete = tag },
                            isSelectionMode = isSelectionMode,
                            selectedTagNames = selectedTagNames,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
    }

    // 删除确认对话框（区分已使用 / 未使用）
    tagToDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            title = { Text(stringResource(R.string.tags_delete_title)) },
            text = {
                Text(
                    if (tag.postCount > 0) stringResource(
                        R.string.tags_delete_used_message,
                        tag.name,
                        tag.postCount
                    ) else stringResource(R.string.tags_delete_unused_message, tag.name)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTag(tag.name)
                        tagToDelete = null
                        noticeManager.showNotice("已移入回收站")
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = com.gridea.android.ui.theme.DangerColor, contentColor = androidx.compose.ui.graphics.Color.White)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { tagToDelete = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 批量删除确认对话框
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text(stringResource(R.string.batch_delete_tags_title)) },
            text = { Text(stringResource(R.string.batch_delete_tags_message, selectedTagNames.size)) },
            confirmButton = {
                Button(
                    onClick = {
                        val deletedCount = selectedTagNames.size
                        viewModel.deleteTags(selectedTagNames)
                        isSelectionMode = false
                        selectedTagNames = emptySet()
                        showBatchDeleteDialog = false
                        noticeManager.showNotice("已删除 $deletedCount 个标签")
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = com.gridea.android.ui.theme.DangerColor, contentColor = androidx.compose.ui.graphics.Color.White)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchDeleteDialog = false },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 新建标签对话框
    if (showCreateDialog) {
        CreateTagDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createTag(name) { success ->
                    if (success) {
                        showCreateDialog = false
                    }
                }
            },
            existingNames = tags.map { it.name }
        )
    }
}

/**
 * 新建标签对话框
 */
@Composable
private fun CreateTagDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    existingNames: List<String>
) {
    var tagName by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // 在 Composable 作用域中预提取字符串，onClick 回调里不能直接调用 stringResource
    val emptyMsg = stringResource(R.string.tags_create_empty)
    val existsMsg = stringResource(R.string.tags_create_exists)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_create_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = {
                        tagName = it
                        errorMsg = null
                    },
                    label = { Text(stringResource(R.string.tags_create_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    isError = errorMsg != null,
                    supportingText = {
                        errorMsg?.let { msg -> Text(msg, color = com.gridea.android.ui.theme.DangerColor) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedIndicatorColor = LocalAccentColor.current,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedLabelColor = LocalAccentColor.current,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = LocalAccentColor.current
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val trimmed = tagName.trim()
                when {
                    trimmed.isEmpty() -> errorMsg = emptyMsg
                    existingNames.any { it.equals(trimmed, ignoreCase = true) } ->
                        errorMsg = existsMsg
                    else -> onCreate(trimmed)
                }
            }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current, contentColor = androidx.compose.ui.graphics.Color.White)) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 标签云内容
 *
 * 选择模式下：标签卡片支持长按进入多选、点击切换选中状态、隐藏单条删除按钮。
 */
@Composable
private fun TagsCloudContent(
    tags: List<TagWithCount>,
    onTagClick: (String) -> Unit,
    onTagLongClick: (String) -> Unit,
    onTagDelete: (TagWithCount) -> Unit,
    isSelectionMode: Boolean = false,
    selectedTagNames: Set<String> = emptySet(),
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        // 底部 90dp 留白：让最后内容能滚动到悬浮导航栏上方完整显示，不被遮挡
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)
    ) {
        items(tags, key = { it.name }) { tag ->
            TagCard(
                tag = tag,
                onClick = { onTagClick(tag.name) },
                onLongClick = { onTagLongClick(tag.name) },
                onDelete = { onTagDelete(tag) },
                isSelectionMode = isSelectionMode,
                isSelected = tag.name in selectedTagNames
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagCard(
    tag: TagWithCount,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false
) {
    // 卡片背景色：animateColorAsState 平滑过渡，避免选中/取消选中时颜色硬切换
    val accentColor = LocalAccentColor.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            accentColor.copy(alpha = 0.16f).compositeOver(surfaceColor)
        } else {
            accentColor.copy(alpha = 0.08f).compositeOver(surfaceColor)
        },
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "tagCardColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        // 移除阴影，靠浅色背景与周边区分
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 选择模式下显示复选标记，否则显示标签图标：Crossfade 平滑切换
            Crossfade(
                targetState = isSelectionMode,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                label = "tagIconTransition"
            ) { selectionMode ->
                if (selectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle
                                      else Icons.Filled.Circle,
                        contentDescription = null,
                        tint = if (isSelected) LocalAccentColor.current
                               else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.LocalOffer,
                        contentDescription = null,
                        tint = if (tag.used) LocalAccentColor.current
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (tag.used) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = if (tag.postCount > 0) stringResource(R.string.tags_post_count_format, tag.postCount)
                           else "未使用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // 选择模式下隐藏单条删除按钮：AnimatedVisibility 平滑过渡
            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = scaleIn(initialScale = 0.6f, animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeIn(tween(150)),
                exit = scaleOut(targetScale = 0.6f, animationSpec = tween(150, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = com.gridea.android.ui.theme.DangerColor
                    )
                }
            }
        }
    }
}

/**
 * 标签详情：文章列表
 */
@Composable
private fun TagDetailContent(
    posts: List<Post>,
    onPostClick: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    if (posts.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.LocalOffer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(96.dp)
            )
            Text(
                text = "还没有文章使用这个标签",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "为文章添加此标签后，会显示在这里",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // 底部 90dp 留白：让最后内容能滚动到悬浮导航栏上方完整显示，不被遮挡
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)
        ) {
            items(posts, key = { it.fileName }) { post ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPostClick(post.fileName) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text(
                            text = post.data.title.ifEmpty { "未命名文章" },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = post.data.date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
