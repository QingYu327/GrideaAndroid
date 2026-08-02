package com.gridea.android.ui.screen.friendlink

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.gridea.android.R
import com.gridea.android.data.model.FriendLink
import com.gridea.android.ui.PressableFloatingActionButton
import com.gridea.android.ui.theme.LocalAccentColor

/**
 * 友情链接管理页面
 *
 * 功能：
 * - 友链列表展示（头像、名称、URL、描述）
 * - 添加 / 编辑友链（弹窗表单）
 * - 删除友链（确认弹窗）
 * - 空状态提示
 *
 * 批量管理（与标签页 / 菜单页统一模式）：
 * - 长按友链卡片进入选择模式
 * - 选择模式下顶部悬浮圆角卡片操作栏（slideInVertically + fadeIn 进入）
 * - 支持全选 / 取消全选、批量删除（带确认弹窗）
 * - 选择模式下隐藏 FAB；列表清空后自动退出选择模式
 *
 * 嵌入模式（onBack==null）：参照标签页结构，去除自身 TopAppBar，
 * 用纯 LazyColumn + 简单 padding，避免与 PagesScreen 顶部圆角卡片切换栏出现间距叠加
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendLinkScreen(
    onBack: (() -> Unit)? = null,
    onSelectionStateChange: ((com.gridea.android.ui.component.SelectionToolbarState?) -> Unit)? = null,
    viewModel: FriendLinkViewModel = hiltViewModel()
) {
    val friendLinks by viewModel.friendLinks.collectAsState()

    // 桥接 ViewModel 一次性操作消息到全局灵动岛通知（添加/更新/删除友链）
    val noticeManager = com.gridea.android.ui.theme.LocalNoticeManager.current
    val operationMessage by viewModel.operationMessage.collectAsState()
    androidx.compose.runtime.LaunchedEffect(operationMessage) {
        operationMessage?.let {
            noticeManager.showNotice(it)
            viewModel.clearOperationMessage()
        }
    }

    // 编辑/添加对话框状态：null 表示关闭，非 null 表示打开
    var editingLink by remember { mutableStateOf<FriendLink?>(null) }
    // 是否为新增模式（决定提交时调用 add 还是 update）
    var isAdding by remember { mutableStateOf(false) }
    // 删除确认对话框状态
    var linkToDelete by remember { mutableStateOf<FriendLink?>(null) }

    // 批量选择模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedLinkIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    // 列表滚动状态：滑动时立即隐藏 FAB，停止滑动后延缓 1s 再出现
    val listState = rememberLazyListState()
    var isFabVisible by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isFabVisible = false
        } else {
            kotlinx.coroutines.delay(1000)
            isFabVisible = true
        }
    }
    val fabAlpha by animateFloatAsState(
        targetValue = if (isFabVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "friendLinkFabAlpha"
    )
    val fabScale by animateFloatAsState(
        targetValue = if (isFabVisible) 1f else 0.5f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "friendLinkFabScale"
    )

    // 列表为空时自动退出选择模式
    LaunchedEffect(friendLinks) {
        if (friendLinks.isEmpty() && isSelectionMode) {
            isSelectionMode = false
            selectedLinkIds = emptySet()
        }
    }

    // 选择模式下按返回键退出选择模式
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedLinkIds = emptySet()
    }

    // 全选 / 取消全选所需的全集与状态
    val allLinkIds = remember(friendLinks) { friendLinks.map { it.id }.toSet() }
    val isAllSelected = selectedLinkIds.isNotEmpty() && selectedLinkIds == allLinkIds

    // 向父页面（PagesScreen）上报选择状态，让 TopAppBar 渲染全选/删除按钮
    LaunchedEffect(isSelectionMode, selectedLinkIds.size, isAllSelected) {
        onSelectionStateChange?.invoke(
            if (isSelectionMode) com.gridea.android.ui.component.SelectionToolbarState(
                selectedCount = selectedLinkIds.size,
                isAllSelected = isAllSelected,
                onToggleSelectAll = {
                    selectedLinkIds = if (isAllSelected) emptySet() else allLinkIds
                },
                onDelete = { showBatchDeleteDialog = true }
            ) else null
        )
    }

    val startCreate = {
        editingLink = FriendLink()
        isAdding = true
    }

    // 嵌入模式：直接用 LazyColumn + 悬浮选择栏 + FAB
    if (onBack == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            FriendLinkContent(
                friendLinks = friendLinks,
                listState = listState,
                isSelectionMode = isSelectionMode,
                selectedIds = selectedLinkIds,
                onLinkClick = { link ->
                    if (isSelectionMode) {
                        selectedLinkIds = if (link.id in selectedLinkIds) {
                            selectedLinkIds - link.id
                        } else {
                            selectedLinkIds + link.id
                        }
                        if (selectedLinkIds.isEmpty()) isSelectionMode = false
                    } else {
                        editingLink = link
                        isAdding = false
                    }
                },
                onLinkLongClick = { link ->
                    if (!isSelectionMode) {
                        isSelectionMode = true
                        selectedLinkIds = setOf(link.id)
                    } else {
                        selectedLinkIds = if (link.id in selectedLinkIds) {
                            selectedLinkIds - link.id
                        } else {
                            selectedLinkIds + link.id
                        }
                        if (selectedLinkIds.isEmpty()) isSelectionMode = false
                    }
                },
                onCreate = startCreate,
                onEdit = { link ->
                    editingLink = link
                    isAdding = false
                },
                onDelete = { linkToDelete = it }
            )

            // 选择模式下隐藏 FAB
            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = scaleIn(initialScale = 0.6f, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = scaleOut(targetScale = 0.6f, animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                PressableFloatingActionButton(
                    onClick = startCreate,
                    // bottom = 176dp：与标签页 Scaffold FAB slot 同一高度（160 + 16dp Scaffold 默认 margin）
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
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.friend_link_add))
                }
            }
        }
    } else {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.friend_links_title)) },
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
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !isSelectionMode,
                    enter = scaleIn(initialScale = 0.6f, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                    exit = scaleOut(targetScale = 0.6f, animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(160))
                ) {
                    PressableFloatingActionButton(
                        onClick = startCreate,
                        modifier = Modifier
                            .padding(bottom = 0.dp)
                            .graphicsLayer {
                                alpha = fabAlpha
                                scaleX = fabScale
                                scaleY = fabScale
                            },
                        containerColor = LocalAccentColor.current,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.friend_link_add))
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                FriendLinkContent(
                    friendLinks = friendLinks,
                    modifier = Modifier.fillMaxSize(),
                    listState = listState,
                    isSelectionMode = isSelectionMode,
                    selectedIds = selectedLinkIds,
                    onLinkClick = { link ->
                        if (isSelectionMode) {
                            selectedLinkIds = if (link.id in selectedLinkIds) {
                                selectedLinkIds - link.id
                            } else {
                                selectedLinkIds + link.id
                            }
                            if (selectedLinkIds.isEmpty()) isSelectionMode = false
                        } else {
                            editingLink = link
                            isAdding = false
                        }
                    },
                    onLinkLongClick = { link ->
                        if (!isSelectionMode) {
                            isSelectionMode = true
                            selectedLinkIds = setOf(link.id)
                        } else {
                            selectedLinkIds = if (link.id in selectedLinkIds) {
                                selectedLinkIds - link.id
                            } else {
                                selectedLinkIds + link.id
                            }
                            if (selectedLinkIds.isEmpty()) isSelectionMode = false
                        }
                    },
                    onCreate = startCreate,
                    onEdit = { link ->
                        editingLink = link
                        isAdding = false
                    },
                    onDelete = { linkToDelete = it }
                )
            }
        }
    }

    // 添加 / 编辑对话框
    editingLink?.let { link ->
        FriendLinkEditDialog(
            link = link,
            isAdding = isAdding,
            onDismiss = { editingLink = null },
            onConfirm = { name, url, description, avatar ->
                if (isAdding) {
                    viewModel.addFriendLink(name, url, description, avatar)
                } else {
                    viewModel.updateFriendLink(
                        link.copy(name = name, url = url, description = description, avatar = avatar)
                    )
                }
                editingLink = null
            }
        )
    }

    // 删除确认对话框
    linkToDelete?.let { link ->
        AlertDialog(
            onDismissRequest = { linkToDelete = null },
            title = { Text(stringResource(R.string.friend_link_delete)) },
            text = { Text(stringResource(R.string.friend_link_delete_confirm, link.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFriendLink(link)
                    linkToDelete = null
                }) {
                    Text(stringResource(R.string.delete), color = com.gridea.android.ui.theme.DangerColor)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { linkToDelete = null },
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
            title = { Text(stringResource(R.string.batch_delete_friend_links_title)) },
            text = { Text(stringResource(R.string.batch_delete_friend_links_message, selectedLinkIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = friendLinks.filter { it.id in selectedLinkIds }
                    val deletedCount = toDelete.size
                    viewModel.deleteFriendLinks(toDelete)
                    isSelectionMode = false
                    selectedLinkIds = emptySet()
                    showBatchDeleteDialog = false
                    noticeManager.showNotice("已删除 $deletedCount 个友链")
                }) {
                    Text(stringResource(R.string.delete), color = com.gridea.android.ui.theme.DangerColor)
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
}

/**
 * 友链列表内容（参照标签页 TagsCloudContent 的简洁结构）
 *
 * 选择模式下：卡片支持长按进入多选、点击切换选中状态、隐藏单条编辑/删除按钮。
 */
@Composable
private fun FriendLinkContent(
    friendLinks: List<FriendLink>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    isSelectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onLinkClick: (FriendLink) -> Unit = {},
    onLinkLongClick: (FriendLink) -> Unit = {},
    onCreate: () -> Unit,
    onEdit: (FriendLink) -> Unit,
    onDelete: (FriendLink) -> Unit
) {
    if (friendLinks.isEmpty()) {
        // 空状态：与 HomeScreen / TagsScreen 一致的呼吸动画 + 大图标 + 强调色按钮
        val infiniteTransition = rememberInfiniteTransition(label = "friendLinkEmptyPulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "friendLinkPulseAlpha"
        )
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Link,
                contentDescription = null,
                tint = LocalAccentColor.current.copy(alpha = pulseAlpha),
                modifier = Modifier.size(120.dp)
            )
            Text(
                text = stringResource(R.string.friend_link_empty_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                text = stringResource(R.string.friend_link_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            androidx.compose.material3.Button(
                onClick = onCreate,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
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
                    text = stringResource(R.string.friend_link_add),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // 底部 90dp 留白：避开悬浮导航栏
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)
        ) {
            items(friendLinks, key = { it.id }) { link ->
                FriendLinkCard(
                    link = link,
                    onClick = { onLinkClick(link) },
                    onLongClick = { onLinkLongClick(link) },
                    onEdit = { onEdit(link) },
                    onDelete = { onDelete(link) },
                    isSelectionMode = isSelectionMode,
                    isSelected = link.id in selectedIds
                )
            }
        }
    }
}

/**
 * 友链卡片
 *
 * 选择模式下：combinedClickable 支持长按进入多选；选中态用强调色高亮；
 * 左侧头像切换为复选标记；右侧编辑/删除按钮隐藏。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FriendLinkCard(
    link: FriendLink,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false
) {
    val accentColor = LocalAccentColor.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            accentColor.copy(alpha = 0.16f).compositeOver(surfaceColor)
        } else {
            accentColor.copy(alpha = 0.08f).compositeOver(surfaceColor)
        },
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "friendLinkCardColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 选择模式下显示复选标记，否则显示头像/默认图标：Crossfade 平滑切换
            Crossfade(
                targetState = isSelectionMode,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                label = "friendLinkIconTransition"
            ) { selectionMode ->
                if (selectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle
                                      else Icons.Filled.Circle,
                        contentDescription = null,
                        tint = if (isSelected) accentColor
                               else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    if (link.avatar.isNotEmpty()) {
                        AsyncImage(
                            model = link.avatar,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = link.name.ifEmpty { link.url },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (link.url.isNotEmpty()) {
                    Text(
                        text = link.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (link.description.isNotEmpty()) {
                    Text(
                        text = link.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // 选择模式下隐藏单条编辑/删除按钮：AnimatedVisibility 平滑过渡
            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = scaleIn(initialScale = 0.6f, animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeIn(tween(150)),
                exit = scaleOut(targetScale = 0.6f, animationSpec = tween(150, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
            ) {
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.edit),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
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
}

/**
 * 添加 / 编辑友链对话框
 */
@Composable
private fun FriendLinkEditDialog(
    link: FriendLink,
    isAdding: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, description: String, avatar: String) -> Unit
) {
    var name by remember { mutableStateOf(link.name) }
    var url by remember { mutableStateOf(link.url) }
    var description by remember { mutableStateOf(link.description) }
    var avatar by remember { mutableStateOf(link.avatar) }

    // 简单校验：名称和 URL 必填
    val nameError = name.isBlank()
    val urlError = url.isBlank()
    val canSubmit = !nameError && !urlError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isAdding) stringResource(R.string.friend_link_add)
                else stringResource(R.string.friend_link_edit)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.friend_link_name)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.friend_link_name_required)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedIndicatorColor = LocalAccentColor.current,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedLabelColor = LocalAccentColor.current,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = LocalAccentColor.current
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.friend_link_url)) },
                    placeholder = { Text(stringResource(R.string.friend_link_url_hint)) },
                    isError = urlError,
                    supportingText = if (urlError) {
                        { Text(stringResource(R.string.friend_link_url_required)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedIndicatorColor = LocalAccentColor.current,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedLabelColor = LocalAccentColor.current,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = LocalAccentColor.current
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.friend_link_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedIndicatorColor = LocalAccentColor.current,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedLabelColor = LocalAccentColor.current,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = LocalAccentColor.current
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = avatar,
                    onValueChange = { avatar = it },
                    label = { Text(stringResource(R.string.friend_link_avatar)) },
                    placeholder = { Text(stringResource(R.string.friend_link_avatar_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedIndicatorColor = LocalAccentColor.current,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedLabelColor = LocalAccentColor.current,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = LocalAccentColor.current
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), url.trim(), description.trim(), avatar.trim()) },
                enabled = canSubmit,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
            ) {
                Text(stringResource(R.string.friend_link_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
            ) {
                Text(stringResource(R.string.friend_link_cancel))
            }
        }
    )
}
