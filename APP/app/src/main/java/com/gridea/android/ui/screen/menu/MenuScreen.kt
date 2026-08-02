package com.gridea.android.ui.screen.menu

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gridea.android.R
import com.gridea.android.data.model.Menu
import com.gridea.android.data.model.Post
import com.gridea.android.ui.PressableFloatingActionButton
import com.gridea.android.ui.theme.LocalAccentColor

/**
 * 自定义菜单管理页面
 *
 * 功能：
 * - 菜单列表展示（名称、打开方式、链接类型、链接值）
 * - 添加 / 编辑菜单（弹窗表单，支持选择已有文章或自定义链接）
 * - 删除菜单（确认弹窗）
 * - 空状态提示
 *
 * 批量管理（与标签页 / 友链页统一模式）：
 * - 长按菜单卡片进入选择模式
 * - 选择模式下顶部悬浮圆角卡片操作栏（slideInVertically + fadeIn 进入）
 * - 支持全选 / 取消全选、批量删除（带确认弹窗）
 * - 选择模式下隐藏 FAB；列表清空后自动退出选择模式
 *
 * 嵌入模式（onBack==null）：参照标签页结构，去除自身 TopAppBar，
 * 用纯 LazyColumn + 简单 padding，避免与 PagesScreen 顶部圆角卡片切换栏出现间距叠加
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    onBack: (() -> Unit)? = null,
    onSelectionStateChange: ((com.gridea.android.ui.component.SelectionToolbarState?) -> Unit)? = null,
    viewModel: MenuViewModel = hiltViewModel()
) {
    val menus by viewModel.menus.collectAsState()
    val allPosts by viewModel.allPosts.collectAsState()

    // 桥接 ViewModel 一次性操作消息到全局灵动岛通知（添加/更新/删除菜单）
    val noticeManager = com.gridea.android.ui.theme.LocalNoticeManager.current
    val operationMessage by viewModel.operationMessage.collectAsState()
    androidx.compose.runtime.LaunchedEffect(operationMessage) {
        operationMessage?.let {
            noticeManager.showNotice(it)
            viewModel.clearOperationMessage()
        }
    }

    var editingMenu by remember { mutableStateOf<Menu?>(null) }
    var isAdding by remember { mutableStateOf(false) }
    var menuToDelete by remember { mutableStateOf<Menu?>(null) }

    // 批量选择模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedMenuIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
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
        label = "menuFabAlpha"
    )
    val fabScale by animateFloatAsState(
        targetValue = if (isFabVisible) 1f else 0.5f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "menuFabScale"
    )

    // 列表为空时自动退出选择模式
    LaunchedEffect(menus) {
        if (menus.isEmpty() && isSelectionMode) {
            isSelectionMode = false
            selectedMenuIds = emptySet()
        }
    }

    // 选择模式下按返回键退出选择模式
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedMenuIds = emptySet()
    }

    // 全选 / 取消全选所需的全集与状态
    val allMenuIds = remember(menus) { menus.map { it.id }.toSet() }
    val isAllSelected = selectedMenuIds.isNotEmpty() && selectedMenuIds == allMenuIds

    // 向父页面（PagesScreen）上报选择状态，让 TopAppBar 渲染全选/删除按钮
    LaunchedEffect(isSelectionMode, selectedMenuIds.size, isAllSelected) {
        onSelectionStateChange?.invoke(
            if (isSelectionMode) com.gridea.android.ui.component.SelectionToolbarState(
                selectedCount = selectedMenuIds.size,
                isAllSelected = isAllSelected,
                onToggleSelectAll = {
                    selectedMenuIds = if (isAllSelected) emptySet() else allMenuIds
                },
                onDelete = { showBatchDeleteDialog = true }
            ) else null
        )
    }

    val startCreate = {
        editingMenu = Menu()
        isAdding = true
    }

    // 嵌入模式：直接用 LazyColumn（与标签页 TagsCloudContent 保持一致）+ 悬浮选择栏 + FAB
    if (onBack == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            MenuContent(
                menus = menus,
                allPosts = allPosts,
                listState = listState,
                isSelectionMode = isSelectionMode,
                selectedIds = selectedMenuIds,
                onMenuClick = { menu ->
                    if (isSelectionMode) {
                        selectedMenuIds = if (menu.id in selectedMenuIds) {
                            selectedMenuIds - menu.id
                        } else {
                            selectedMenuIds + menu.id
                        }
                        if (selectedMenuIds.isEmpty()) isSelectionMode = false
                    } else {
                        editingMenu = menu
                        isAdding = false
                    }
                },
                onMenuLongClick = { menu ->
                    if (!isSelectionMode) {
                        isSelectionMode = true
                        selectedMenuIds = setOf(menu.id)
                    } else {
                        selectedMenuIds = if (menu.id in selectedMenuIds) {
                            selectedMenuIds - menu.id
                        } else {
                            selectedMenuIds + menu.id
                        }
                        if (selectedMenuIds.isEmpty()) isSelectionMode = false
                    }
                },
                onCreate = startCreate,
                onEdit = { menu ->
                    editingMenu = menu
                    isAdding = false
                },
                onDelete = { menuToDelete = it }
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
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.menu_add))
                }
            }
        }
    } else {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.menu_manage_title)) },
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
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.menu_add))
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                MenuContent(
                    menus = menus,
                    allPosts = allPosts,
                    modifier = Modifier.fillMaxSize(),
                    listState = listState,
                    isSelectionMode = isSelectionMode,
                    selectedIds = selectedMenuIds,
                    onMenuClick = { menu ->
                        if (isSelectionMode) {
                            selectedMenuIds = if (menu.id in selectedMenuIds) {
                                selectedMenuIds - menu.id
                            } else {
                                selectedMenuIds + menu.id
                            }
                            if (selectedMenuIds.isEmpty()) isSelectionMode = false
                        } else {
                            editingMenu = menu
                            isAdding = false
                        }
                    },
                    onMenuLongClick = { menu ->
                        if (!isSelectionMode) {
                            isSelectionMode = true
                            selectedMenuIds = setOf(menu.id)
                        } else {
                            selectedMenuIds = if (menu.id in selectedMenuIds) {
                                selectedMenuIds - menu.id
                            } else {
                                selectedMenuIds + menu.id
                            }
                            if (selectedMenuIds.isEmpty()) isSelectionMode = false
                        }
                    },
                    onCreate = startCreate,
                    onEdit = { menu ->
                        editingMenu = menu
                        isAdding = false
                    },
                    onDelete = { menuToDelete = it }
                )
            }
        }
    }

    // 添加 / 编辑对话框
    editingMenu?.let { menu ->
        MenuEditDialog(
            menu = menu,
            isAdding = isAdding,
            allPosts = allPosts,
            onDismiss = { editingMenu = null },
            onConfirm = { name, openType, linkType, linkValue ->
                if (isAdding) {
                    viewModel.addMenu(name, openType, linkType, linkValue)
                } else {
                    viewModel.updateMenu(
                        menu.copy(
                            name = name,
                            openType = openType,
                            linkType = linkType,
                            linkValue = linkValue
                        )
                    )
                }
                editingMenu = null
            }
        )
    }

    // 删除确认对话框
    menuToDelete?.let { menu ->
        AlertDialog(
            onDismissRequest = { menuToDelete = null },
            title = { Text(stringResource(R.string.menu_delete)) },
            text = { Text(stringResource(R.string.menu_delete_confirm, menu.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMenu(menu)
                    menuToDelete = null
                }) {
                    Text(stringResource(R.string.delete), color = com.gridea.android.ui.theme.DangerColor)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { menuToDelete = null },
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
            title = { Text(stringResource(R.string.batch_delete_menus_title)) },
            text = { Text(stringResource(R.string.batch_delete_menus_message, selectedMenuIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = menus.filter { it.id in selectedMenuIds }
                    val deletedCount = toDelete.size
                    viewModel.deleteMenus(toDelete)
                    isSelectionMode = false
                    selectedMenuIds = emptySet()
                    showBatchDeleteDialog = false
                    noticeManager.showNotice("已删除 $deletedCount 个菜单")
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
 * 菜单列表内容（参照标签页 TagsCloudContent 的简洁结构）
 *
 * 嵌入式使用：直接 LazyColumn + 16dp 周边 padding + 8dp 卡片间距
 * 独立使用：通过 modifier 接收 Scaffold 的 innerPadding
 *
 * 选择模式下：卡片支持长按进入多选、点击切换选中状态、隐藏单条编辑/删除按钮。
 */
@Composable
private fun MenuContent(
    menus: List<Menu>,
    allPosts: List<Post>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    isSelectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onMenuClick: (Menu) -> Unit = {},
    onMenuLongClick: (Menu) -> Unit = {},
    onCreate: () -> Unit,
    onEdit: (Menu) -> Unit,
    onDelete: (Menu) -> Unit
) {
    if (menus.isEmpty()) {
        // 空状态：与 HomeScreen / TagsScreen 一致的呼吸动画 + 大图标 + 强调色按钮
        val infiniteTransition = rememberInfiniteTransition(label = "menuEmptyPulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "menuPulseAlpha"
        )
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = LocalAccentColor.current.copy(alpha = pulseAlpha),
                modifier = Modifier.size(120.dp)
            )
            Text(
                text = stringResource(R.string.menu_empty_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                text = stringResource(R.string.menu_empty_subtitle),
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
                    text = stringResource(R.string.menu_add),
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
            // 底部 90dp 留白：避开悬浮导航栏，让最后内容能完整显示
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)
        ) {
            items(menus, key = { it.id }) { menu ->
                MenuCard(
                    menu = menu,
                    allPosts = allPosts,
                    onClick = { onMenuClick(menu) },
                    onLongClick = { onMenuLongClick(menu) },
                    onEdit = { onEdit(menu) },
                    onDelete = { onDelete(menu) },
                    isSelectionMode = isSelectionMode,
                    isSelected = menu.id in selectedIds
                )
            }
        }
    }
}

/**
 * 菜单卡片
 *
 * 选择模式下：combinedClickable 支持长按进入多选；选中态用强调色高亮；
 * 左侧图标切换为复选标记；右侧编辑/删除按钮隐藏。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuCard(
    menu: Menu,
    allPosts: List<Post>,
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
        label = "menuCardColor"
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
            // 选择模式下显示复选标记，否则显示菜单类型图标：Crossfade 平滑切换
            Crossfade(
                targetState = isSelectionMode,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                label = "menuIconTransition"
            ) { selectionMode ->
                if (selectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle
                                      else Icons.Filled.Circle,
                        contentDescription = null,
                        tint = if (isSelected) accentColor
                               else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(28.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (menu.openType == "External") Icons.AutoMirrored.Filled.OpenInNew
                                      else Icons.AutoMirrored.Filled.MenuOpen,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = menu.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.size(4.dp))
                // 链接类型 + 打开方式标签
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (menu.linkType == "article") Icons.AutoMirrored.Filled.Article
                                      else Icons.Filled.Link,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = if (menu.linkType == "article") {
                            allPosts.find { it.fileName == menu.linkValue }?.data?.title
                                ?: menu.linkValue
                        } else {
                            menu.linkValue
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = if (menu.openType == "External") stringResource(R.string.menu_open_external)
                           else stringResource(R.string.menu_open_internal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
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
 * 添加 / 编辑菜单对话框
 *
 * 支持选择链接类型：
 * - 自定义链接（手动输入 URL）
 * - 选择已有文章（从文章列表中选择，点击后跳转到对应文章）
 */
@Composable
private fun MenuEditDialog(
    menu: Menu,
    isAdding: Boolean,
    allPosts: List<Post>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, openType: String, linkType: String, linkValue: String) -> Unit
) {
    var name by remember { mutableStateOf(menu.name) }
    var openType by remember { mutableStateOf(menu.openType) }
    var linkType by remember { mutableStateOf(menu.linkType) }
    var linkValue by remember { mutableStateOf(menu.linkValue) }
    var showArticlePicker by remember { mutableStateOf(false) }

    val nameError = name.isBlank()
    val linkError = linkValue.isBlank()
    val canSubmit = !nameError && !linkError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isAdding) stringResource(R.string.menu_add)
                else stringResource(R.string.menu_edit)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.menu_name)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.menu_name_required)) }
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

                // 打开方式选择
                Text(
                    text = stringResource(R.string.menu_open_type),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = openType == "Internal",
                        onClick = { openType = "Internal" },
                        label = { Text(stringResource(R.string.menu_open_internal)) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = LocalAccentColor.current.copy(alpha = 0.2f), selectedLabelColor = LocalAccentColor.current, selectedLeadingIconColor = LocalAccentColor.current)
                    )
                    FilterChip(
                        selected = openType == "External",
                        onClick = { openType = "External" },
                        label = { Text(stringResource(R.string.menu_open_external)) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = LocalAccentColor.current.copy(alpha = 0.2f), selectedLabelColor = LocalAccentColor.current, selectedLeadingIconColor = LocalAccentColor.current)
                    )
                }

                // 链接类型选择
                Text(
                    text = stringResource(R.string.menu_link_type),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = linkType == "url",
                        onClick = { linkType = "url"; linkValue = "" },
                        label = { Text(stringResource(R.string.menu_link_url)) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = LocalAccentColor.current.copy(alpha = 0.2f), selectedLabelColor = LocalAccentColor.current, selectedLeadingIconColor = LocalAccentColor.current)
                    )
                    FilterChip(
                        selected = linkType == "article",
                        onClick = {
                            linkType = "article"
                            linkValue = ""
                            showArticlePicker = true
                        },
                        label = { Text(stringResource(R.string.menu_link_article)) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = LocalAccentColor.current.copy(alpha = 0.2f), selectedLabelColor = LocalAccentColor.current, selectedLeadingIconColor = LocalAccentColor.current)
                    )
                }

                // 链接值输入或文章选择
                if (linkType == "url") {
                    OutlinedTextField(
                        value = linkValue,
                        onValueChange = { linkValue = it },
                        label = { Text(stringResource(R.string.menu_link_value_url)) },
                        placeholder = { Text(stringResource(R.string.menu_link_url_hint)) },
                        isError = linkError,
                        supportingText = if (linkError) {
                            { Text(stringResource(R.string.menu_link_required)) }
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
                } else {
                    // 文章选择：显示当前选中的文章标题，点击切换
                    val selectedPost = allPosts.find { it.fileName == linkValue }
                    OutlinedTextField(
                        value = selectedPost?.data?.title ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.menu_link_value_article)) },
                        placeholder = { Text(stringResource(R.string.menu_link_article_hint)) },
                        isError = linkError,
                        supportingText = if (linkError) {
                            { Text(stringResource(R.string.menu_link_required)) }
                        } else null,
                        trailingIcon = {
                            IconButton(onClick = { showArticlePicker = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = stringResource(R.string.menu_select_article)
                                )
                            }
                        },
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name.trim(), openType, linkType, linkValue.trim())
                },
                enabled = canSubmit,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
            ) {
                Text(stringResource(R.string.menu_save))
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

    // 文章选择对话框
    if (showArticlePicker) {
        ArticlePickerDialog(
            posts = allPosts,
            selectedFileName = linkValue,
            onDismiss = { showArticlePicker = false },
            onSelect = { post ->
                linkValue = post.fileName
                showArticlePicker = false
            }
        )
    }
}

/**
 * 文章选择对话框
 *
 * 从所有文章列表中选择一篇，作为菜单项的跳转目标
 * 用自定义 Dialog + AnimatedVisibility 缩放动画，避免 AlertDialog 默认滑动动画与 LazyColumn 滚动冲突造成的抖动
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticlePickerDialog(
    posts: List<Post>,
    selectedFileName: String,
    onDismiss: () -> Unit,
    onSelect: (Post) -> Unit
) {
    // 用自定义 Dialog 替代 AlertDialog：可控缩放进入动画
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
            enter = androidx.compose.animation.scaleIn(
                initialScale = 0.85f,
                animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + androidx.compose.animation.fadeIn(tween(180)),
            exit = androidx.compose.animation.scaleOut(
                targetScale = 0.85f,
                animationSpec = androidx.compose.animation.core.tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + androidx.compose.animation.fadeOut(tween(160))
        ) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = stringResource(R.string.menu_select_article),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    if (posts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.menu_no_articles),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(posts, key = { it.fileName }) { post ->
                                val isSelected = post.fileName == selectedFileName
                                Card(
                                    onClick = { onSelect(post) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected)
                                            LocalAccentColor.current.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Article,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (isSelected) LocalAccentColor.current
                                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = post.data.title.ifEmpty { post.fileName },
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = post.data.date,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            }
        }
    }
}
