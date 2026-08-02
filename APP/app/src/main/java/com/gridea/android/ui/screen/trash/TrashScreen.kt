package com.gridea.android.ui.screen.trash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.gridea.android.ui.theme.DangerColor
import com.gridea.android.ui.theme.LocalAccentColor
import com.gridea.android.ui.theme.LocalNoticeManager
import com.gridea.android.ui.theme.PinnedColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 回收站日期格式化器（文件级缓存，避免每次调用重复创建）
 * 所有调用均在主线程，SimpleDateFormat 非线程安全在此场景下无问题
 */
private val TRASH_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/**
 * 回收站 Tab 类型：文章 / 标签 / 菜单 / 友链
 */
private enum class TrashTab {
    POSTS,
    TAGS,
    MENUS,
    FRIEND_LINKS
}

/**
 * 回收站页面
 *
 * 展示已软删除的文章、标签、菜单和友链，支持恢复与彻底删除
 * 回收站内容保留 3 天，超过后自动清理
 * 支持批量管理（批量恢复、批量彻底删除）
 * 通过 TabRow 切换「文章」「标签」「菜单」「友链」四个视图，切换时退出选择模式
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val trashedPosts by viewModel.trashedPosts.collectAsState()
    val trashedTags by viewModel.trashedTags.collectAsState()
    val trashedMenus by viewModel.trashedMenus.collectAsState()
    val trashedFriendLinks by viewModel.trashedFriendLinks.collectAsState()
    val noticeManager = LocalNoticeManager.current

    // 当前 Tab 状态
    var currentTab by remember { mutableStateOf(TrashTab.POSTS) }

    // 批量选择状态（各 Tab 独立）：在 switchTab / LaunchedEffect 中清空
    var isPostSelectionMode by remember { mutableStateOf(false) }
    var selectedPostIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isTagSelectionMode by remember { mutableStateOf(false) }
    var selectedTagNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isMenuSelectionMode by remember { mutableStateOf(false) }
    var selectedMenuIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isFriendLinkSelectionMode by remember { mutableStateOf(false) }
    var selectedFriendLinkIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // HorizontalPager 状态：4 个页面（文章/标签/菜单/友链），支持左右手势滑动切换
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { TrashTab.entries.size })
    val scope = rememberCoroutineScope()

    // 点击切换栏 → 滚动 Pager 到对应页
    fun switchTab(tab: TrashTab) {
        if (tab != currentTab) {
            currentTab = tab
            isPostSelectionMode = false
            selectedPostIds = emptySet()
            isTagSelectionMode = false
            selectedTagNames = emptySet()
            isMenuSelectionMode = false
            selectedMenuIds = emptySet()
            isFriendLinkSelectionMode = false
            selectedFriendLinkIds = emptySet()
            val targetIndex = TrashTab.entries.indexOf(tab)
            if (targetIndex >= 0) {
                scope.launch { pagerState.animateScrollToPage(targetIndex) }
            }
        }
    }

    // 滑动 Pager → 同步 currentTab 与选择模式清空
    LaunchedEffect(pagerState.currentPage) {
        val newTab = TrashTab.entries.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        if (newTab != currentTab) {
            currentTab = newTab
            isPostSelectionMode = false
            selectedPostIds = emptySet()
            isTagSelectionMode = false
            selectedTagNames = emptySet()
            isMenuSelectionMode = false
            selectedMenuIds = emptySet()
            isFriendLinkSelectionMode = false
            selectedFriendLinkIds = emptySet()
        }
    }

    // 文章对话框状态
    var postToRestore by remember { mutableStateOf<TrashItem?>(null) }
    var postToDelete by remember { mutableStateOf<TrashItem?>(null) }
    var showBatchPostRestoreDialog by remember { mutableStateOf(false) }
    var showBatchPostDeleteDialog by remember { mutableStateOf(false) }

    // 标签对话框状态
    var tagToRestore by remember { mutableStateOf<TrashTagItem?>(null) }
    var tagToDelete by remember { mutableStateOf<TrashTagItem?>(null) }
    var showBatchTagRestoreDialog by remember { mutableStateOf(false) }
    var showBatchTagDeleteDialog by remember { mutableStateOf(false) }

    // 菜单对话框状态
    var menuToRestore by remember { mutableStateOf<TrashMenuItem?>(null) }
    var menuToDelete by remember { mutableStateOf<TrashMenuItem?>(null) }
    var showBatchMenuRestoreDialog by remember { mutableStateOf(false) }
    var showBatchMenuDeleteDialog by remember { mutableStateOf(false) }

    // 友链对话框状态
    var friendLinkToRestore by remember { mutableStateOf<TrashFriendLinkItem?>(null) }
    var friendLinkToDelete by remember { mutableStateOf<TrashFriendLinkItem?>(null) }
    var showBatchFriendLinkRestoreDialog by remember { mutableStateOf(false) }
    var showBatchFriendLinkDeleteDialog by remember { mutableStateOf(false) }

    val isSelectionMode = isPostSelectionMode || isTagSelectionMode ||
        isMenuSelectionMode || isFriendLinkSelectionMode

    // 选择模式下按返回键退出选择模式
    BackHandler(enabled = isSelectionMode) {
        isPostSelectionMode = false
        selectedPostIds = emptySet()
        isTagSelectionMode = false
        selectedTagNames = emptySet()
        isMenuSelectionMode = false
        selectedMenuIds = emptySet()
        isFriendLinkSelectionMode = false
        selectedFriendLinkIds = emptySet()
    }

    fun exitSelectionMode() {
        isPostSelectionMode = false
        selectedPostIds = emptySet()
        isTagSelectionMode = false
        selectedTagNames = emptySet()
        isMenuSelectionMode = false
        selectedMenuIds = emptySet()
        isFriendLinkSelectionMode = false
        selectedFriendLinkIds = emptySet()
    }

    Scaffold(
        topBar = {
            Column {
                Crossfade(
                    targetState = isSelectionMode,
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = FastOutSlowInEasing
                    ),
                    label = "trashTopBarTransition"
                ) { selectionMode ->
                    if (selectionMode) {
                        TopAppBar(
                            title = {
                                when (currentTab) {
                                    TrashTab.POSTS -> Text("已选 ${selectedPostIds.size} 篇")
                                    TrashTab.TAGS -> Text("已选 ${selectedTagNames.size} 个")
                                    TrashTab.MENUS -> Text("已选 ${selectedMenuIds.size} 个")
                                    TrashTab.FRIEND_LINKS -> Text("已选 ${selectedFriendLinkIds.size} 个")
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { exitSelectionMode() }) {
                                    Icon(Icons.Filled.Close, contentDescription = "退出选择")
                                }
                            },
                            actions = {
                                when (currentTab) {
                                    TrashTab.POSTS -> {
                                        val allFileNames = remember(trashedPosts) {
                                            trashedPosts.map { it.trashedPost.post.fileName }.toSet()
                                        }
                                        val isAllSelected = selectedPostIds.isNotEmpty() &&
                                            selectedPostIds == allFileNames
                                        IconButton(onClick = {
                                            selectedPostIds = if (isAllSelected) emptySet() else allFileNames
                                        }) {
                                            Icon(
                                                imageVector = Icons.Filled.LibraryAddCheck,
                                                contentDescription = if (isAllSelected) "取消全选" else "全选",
                                                tint = if (isAllSelected) LocalAccentColor.current
                                                       else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { showBatchPostRestoreDialog = true },
                                            enabled = selectedPostIds.isNotEmpty()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Restore,
                                                contentDescription = "批量恢复",
                                                tint = if (selectedPostIds.isNotEmpty())
                                                    LocalAccentColor.current
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { showBatchPostDeleteDialog = true },
                                            enabled = selectedPostIds.isNotEmpty()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteForever,
                                                contentDescription = "批量彻底删除",
                                                tint = if (selectedPostIds.isNotEmpty())
                                                    DangerColor
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    TrashTab.TAGS -> {
                                        val allTagNames = remember(trashedTags) {
                                            trashedTags.map { it.tag.name }.toSet()
                                        }
                                        val isAllSelected = selectedTagNames.isNotEmpty() &&
                                            selectedTagNames == allTagNames
                                        IconButton(onClick = {
                                            selectedTagNames = if (isAllSelected) emptySet() else allTagNames
                                        }) {
                                            Icon(
                                                imageVector = Icons.Filled.LibraryAddCheck,
                                                contentDescription = if (isAllSelected) "取消全选" else "全选",
                                                tint = if (isAllSelected) LocalAccentColor.current
                                                       else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { showBatchTagRestoreDialog = true },
                                            enabled = selectedTagNames.isNotEmpty()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Restore,
                                                contentDescription = "批量恢复",
                                                tint = if (selectedTagNames.isNotEmpty())
                                                    LocalAccentColor.current
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { showBatchTagDeleteDialog = true },
                                            enabled = selectedTagNames.isNotEmpty()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteForever,
                                                contentDescription = "批量彻底删除",
                                                tint = if (selectedTagNames.isNotEmpty())
                                                    DangerColor
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    TrashTab.MENUS -> {
                                        val allMenuIds = remember(trashedMenus) {
                                            trashedMenus.map { it.menu.id }.toSet()
                                        }
                                        val isAllSelected = selectedMenuIds.isNotEmpty() &&
                                            selectedMenuIds == allMenuIds
                                        IconButton(onClick = {
                                            selectedMenuIds = if (isAllSelected) emptySet() else allMenuIds
                                        }) {
                                            Icon(
                                                imageVector = Icons.Filled.LibraryAddCheck,
                                                contentDescription = if (isAllSelected) "取消全选" else "全选",
                                                tint = if (isAllSelected) LocalAccentColor.current
                                                       else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { showBatchMenuRestoreDialog = true },
                                            enabled = selectedMenuIds.isNotEmpty()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Restore,
                                                contentDescription = "批量恢复",
                                                tint = if (selectedMenuIds.isNotEmpty())
                                                    LocalAccentColor.current
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { showBatchMenuDeleteDialog = true },
                                            enabled = selectedMenuIds.isNotEmpty()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteForever,
                                                contentDescription = "批量彻底删除",
                                                tint = if (selectedMenuIds.isNotEmpty())
                                                    DangerColor
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    TrashTab.FRIEND_LINKS -> {
                                        val allFriendLinkIds = remember(trashedFriendLinks) {
                                            trashedFriendLinks.map { it.friendLink.id }.toSet()
                                        }
                                        val isAllSelected = selectedFriendLinkIds.isNotEmpty() &&
                                            selectedFriendLinkIds == allFriendLinkIds
                                        IconButton(onClick = {
                                            selectedFriendLinkIds = if (isAllSelected) emptySet() else allFriendLinkIds
                                        }) {
                                            Icon(
                                                imageVector = Icons.Filled.LibraryAddCheck,
                                                contentDescription = if (isAllSelected) "取消全选" else "全选",
                                                tint = if (isAllSelected) LocalAccentColor.current
                                                       else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { showBatchFriendLinkRestoreDialog = true },
                                            enabled = selectedFriendLinkIds.isNotEmpty()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Restore,
                                                contentDescription = "批量恢复",
                                                tint = if (selectedFriendLinkIds.isNotEmpty())
                                                    LocalAccentColor.current
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { showBatchFriendLinkDeleteDialog = true },
                                            enabled = selectedFriendLinkIds.isNotEmpty()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteForever,
                                                contentDescription = "批量彻底删除",
                                                tint = if (selectedFriendLinkIds.isNotEmpty())
                                                    DangerColor
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent
                            )
                        )
                    } else {
                        TopAppBar(
                            title = { Text("回收站") },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "返回"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent
                            )
                        )
                    }
                }

                // 圆角卡片样式 Tab（与部署/主题页风格一致）
                val accentColor = LocalAccentColor.current
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TrashTab.entries.forEachIndexed { index, tab ->
                            val selected = currentTab == tab
                            // 用 clip 让 ripple 被裁剪为圆角，匹配卡片形状
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { switchTab(tab) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) accentColor else Color.Transparent
                            ) {
                                Text(
                                    text = when (tab) {
                                        TrashTab.POSTS -> "文章"
                                        TrashTab.TAGS -> "标签"
                                        TrashTab.MENUS -> "菜单"
                                        TrashTab.FRIEND_LINKS -> "友链"
                                    },
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center,
                                    color = if (selected) Color.White
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        // 持久化四个 Tab 的滚动位置
        val postsListState = rememberLazyListState()
        val tagsListState = rememberLazyListState()
        val menusListState = rememberLazyListState()
        val friendLinksListState = rememberLazyListState()

        // HorizontalPager：左右手势滑动切换四个区，与 LazyColumn 垂直滚动方向正交不冲突
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val tab = TrashTab.entries[pageIndex]
            when (tab) {
                TrashTab.POSTS -> {
                    LazyColumn(
                        state = postsListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp)
                    ) {
                        if (trashedPosts.isEmpty()) {
                            item(key = "empty") {
                                TrashPostEmptyState()
                            }
                        } else {
                            items(
                                items = trashedPosts,
                                key = { it.trashedPost.post.fileName }
                            ) { item ->
                                val isSelected = item.trashedPost.post.fileName in selectedPostIds
                                TrashItemCard(
                                    item = item,
                                    onClick = {
                                        if (isPostSelectionMode) {
                                            selectedPostIds = if (isSelected) {
                                                selectedPostIds - item.trashedPost.post.fileName
                                            } else {
                                                selectedPostIds + item.trashedPost.post.fileName
                                            }
                                            if (selectedPostIds.isEmpty()) isPostSelectionMode = false
                                        }
                                    },
                                    onLongClick = {
                                        if (!isPostSelectionMode) {
                                            isPostSelectionMode = true
                                            selectedPostIds = setOf(item.trashedPost.post.fileName)
                                        } else {
                                            selectedPostIds = if (isSelected) {
                                                selectedPostIds - item.trashedPost.post.fileName
                                            } else {
                                                selectedPostIds + item.trashedPost.post.fileName
                                            }
                                            if (selectedPostIds.isEmpty()) isPostSelectionMode = false
                                        }
                                    },
                                    onRestore = { postToRestore = item },
                                    onPermanentDelete = { postToDelete = item },
                                    isSelectionMode = isPostSelectionMode,
                                    isSelected = isSelected,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .animateItem()
                                )
                            }
                        }
                    }
                }
                TrashTab.TAGS -> {
                    LazyColumn(
                        state = tagsListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp)
                    ) {
                        if (trashedTags.isEmpty()) {
                            item(key = "empty") {
                                TrashTagEmptyState()
                            }
                        } else {
                            items(
                                items = trashedTags,
                                key = { it.tag.name }
                            ) { item ->
                                val isSelected = item.tag.name in selectedTagNames
                                TrashTagCard(
                                    item = item,
                                    onClick = {
                                        if (isTagSelectionMode) {
                                            selectedTagNames = if (isSelected) {
                                                selectedTagNames - item.tag.name
                                            } else {
                                                selectedTagNames + item.tag.name
                                            }
                                            if (selectedTagNames.isEmpty()) isTagSelectionMode = false
                                        }
                                    },
                                    onLongClick = {
                                        if (!isTagSelectionMode) {
                                            isTagSelectionMode = true
                                            selectedTagNames = setOf(item.tag.name)
                                        } else {
                                            selectedTagNames = if (isSelected) {
                                                selectedTagNames - item.tag.name
                                            } else {
                                                selectedTagNames + item.tag.name
                                            }
                                            if (selectedTagNames.isEmpty()) isTagSelectionMode = false
                                        }
                                    },
                                    onRestore = { tagToRestore = item },
                                    onPermanentDelete = { tagToDelete = item },
                                    isSelectionMode = isTagSelectionMode,
                                    isSelected = isSelected,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .animateItem()
                                )
                            }
                        }
                    }
                }
                TrashTab.MENUS -> {
                    LazyColumn(
                        state = menusListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp)
                    ) {
                        if (trashedMenus.isEmpty()) {
                            item(key = "empty") {
                                TrashMenuEmptyState()
                            }
                        } else {
                            items(
                                items = trashedMenus,
                                key = { it.menu.id }
                            ) { item ->
                                val isSelected = item.menu.id in selectedMenuIds
                                TrashMenuCard(
                                    item = item,
                                    onClick = {
                                        if (isMenuSelectionMode) {
                                            selectedMenuIds = if (isSelected) {
                                                selectedMenuIds - item.menu.id
                                            } else {
                                                selectedMenuIds + item.menu.id
                                            }
                                            if (selectedMenuIds.isEmpty()) isMenuSelectionMode = false
                                        }
                                    },
                                    onLongClick = {
                                        if (!isMenuSelectionMode) {
                                            isMenuSelectionMode = true
                                            selectedMenuIds = setOf(item.menu.id)
                                        } else {
                                            selectedMenuIds = if (isSelected) {
                                                selectedMenuIds - item.menu.id
                                            } else {
                                                selectedMenuIds + item.menu.id
                                            }
                                            if (selectedMenuIds.isEmpty()) isMenuSelectionMode = false
                                        }
                                    },
                                    onRestore = { menuToRestore = item },
                                    onPermanentDelete = { menuToDelete = item },
                                    isSelectionMode = isMenuSelectionMode,
                                    isSelected = isSelected,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .animateItem()
                                )
                            }
                        }
                    }
                }
                TrashTab.FRIEND_LINKS -> {
                    LazyColumn(
                        state = friendLinksListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp)
                    ) {
                        if (trashedFriendLinks.isEmpty()) {
                            item(key = "empty") {
                                TrashFriendLinkEmptyState()
                            }
                        } else {
                            items(
                                items = trashedFriendLinks,
                                key = { it.friendLink.id }
                            ) { item ->
                                val isSelected = item.friendLink.id in selectedFriendLinkIds
                                TrashFriendLinkCard(
                                    item = item,
                                    onClick = {
                                        if (isFriendLinkSelectionMode) {
                                            selectedFriendLinkIds = if (isSelected) {
                                                selectedFriendLinkIds - item.friendLink.id
                                            } else {
                                                selectedFriendLinkIds + item.friendLink.id
                                            }
                                            if (selectedFriendLinkIds.isEmpty()) isFriendLinkSelectionMode = false
                                        }
                                    },
                                    onLongClick = {
                                        if (!isFriendLinkSelectionMode) {
                                            isFriendLinkSelectionMode = true
                                            selectedFriendLinkIds = setOf(item.friendLink.id)
                                        } else {
                                            selectedFriendLinkIds = if (isSelected) {
                                                selectedFriendLinkIds - item.friendLink.id
                                            } else {
                                                selectedFriendLinkIds + item.friendLink.id
                                            }
                                            if (selectedFriendLinkIds.isEmpty()) isFriendLinkSelectionMode = false
                                        }
                                    },
                                    onRestore = { friendLinkToRestore = item },
                                    onPermanentDelete = { friendLinkToDelete = item },
                                    isSelectionMode = isFriendLinkSelectionMode,
                                    isSelected = isSelected,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .animateItem()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== 文章对话框 =====

    // 单条恢复确认对话框
    postToRestore?.let { item ->
        AlertDialog(
            onDismissRequest = { postToRestore = null },
            title = { Text("恢复文章") },
            text = { Text("确认恢复「${item.trashedPost.post.data.title.ifEmpty { "未命名文章" }}」吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restorePost(item.trashedPost.post.fileName)
                        postToRestore = null
                        noticeManager.showNotice("已恢复")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAccentColor.current,
                        contentColor = Color.White
                    )
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { postToRestore = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 单条彻底删除确认对话框
    postToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { postToDelete = null },
            title = { Text("彻底删除") },
            text = { Text("彻底删除「${item.trashedPost.post.data.title.ifEmpty { "未命名文章" }}」后无法恢复，确认删除吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.permanentDeletePost(item.trashedPost.post.fileName)
                        postToDelete = null
                        noticeManager.showNotice("已彻底删除")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerColor,
                        contentColor = Color.White
                    )
                ) {
                    Text("彻底删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { postToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 批量恢复确认对话框
    if (showBatchPostRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showBatchPostRestoreDialog = false },
            title = { Text("批量恢复") },
            text = { Text("确认恢复选中的 ${selectedPostIds.size} 篇文章吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        val count = selectedPostIds.size
                        viewModel.restorePosts(selectedPostIds)
                        isPostSelectionMode = false
                        selectedPostIds = emptySet()
                        showBatchPostRestoreDialog = false
                        noticeManager.showNotice("已恢复 $count 篇文章")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAccentColor.current,
                        contentColor = Color.White
                    )
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchPostRestoreDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 批量彻底删除确认对话框
    if (showBatchPostDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchPostDeleteDialog = false },
            title = { Text("批量彻底删除") },
            text = { Text("将彻底删除选中的 ${selectedPostIds.size} 篇文章，此操作无法恢复，确认删除吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        val count = selectedPostIds.size
                        viewModel.permanentDeletePosts(selectedPostIds)
                        isPostSelectionMode = false
                        selectedPostIds = emptySet()
                        showBatchPostDeleteDialog = false
                        noticeManager.showNotice("已彻底删除 $count 篇文章")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerColor,
                        contentColor = Color.White
                    )
                ) {
                    Text("彻底删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchPostDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // ===== 标签对话框 =====

    // 单条恢复确认对话框
    tagToRestore?.let { item ->
        AlertDialog(
            onDismissRequest = { tagToRestore = null },
            title = { Text("恢复标签") },
            text = { Text("确认恢复标签「${item.tag.name}」吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreTag(item.tag.name)
                        tagToRestore = null
                        noticeManager.showNotice("已恢复")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAccentColor.current,
                        contentColor = Color.White
                    )
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { tagToRestore = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 单条彻底删除确认对话框
    tagToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            title = { Text("彻底删除") },
            text = { Text("彻底删除标签「${item.tag.name}」后无法恢复，确认删除吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.permanentDeleteTag(item.tag.name)
                        tagToDelete = null
                        noticeManager.showNotice("已彻底删除")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerColor,
                        contentColor = Color.White
                    )
                ) {
                    Text("彻底删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { tagToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 批量恢复确认对话框
    if (showBatchTagRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showBatchTagRestoreDialog = false },
            title = { Text("批量恢复") },
            text = { Text("确认恢复选中的 ${selectedTagNames.size} 个标签吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        val count = selectedTagNames.size
                        viewModel.restoreTags(selectedTagNames)
                        isTagSelectionMode = false
                        selectedTagNames = emptySet()
                        showBatchTagRestoreDialog = false
                        noticeManager.showNotice("已恢复 $count 个标签")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAccentColor.current,
                        contentColor = Color.White
                    )
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchTagRestoreDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 批量彻底删除确认对话框
    if (showBatchTagDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchTagDeleteDialog = false },
            title = { Text("批量彻底删除") },
            text = { Text("将彻底删除选中的 ${selectedTagNames.size} 个标签，此操作无法恢复，确认删除吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        val count = selectedTagNames.size
                        viewModel.permanentDeleteTags(selectedTagNames)
                        isTagSelectionMode = false
                        selectedTagNames = emptySet()
                        showBatchTagDeleteDialog = false
                        noticeManager.showNotice("已彻底删除 $count 个标签")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerColor,
                        contentColor = Color.White
                    )
                ) {
                    Text("彻底删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchTagDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // ===== 菜单对话框 =====

    // 单条恢复确认对话框
    menuToRestore?.let { item ->
        AlertDialog(
            onDismissRequest = { menuToRestore = null },
            title = { Text("恢复菜单") },
            text = { Text("确认恢复菜单「${item.menu.name}」吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreMenu(item.menu.id)
                        menuToRestore = null
                        noticeManager.showNotice("已恢复")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAccentColor.current,
                        contentColor = Color.White
                    )
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { menuToRestore = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 单条彻底删除确认对话框
    menuToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { menuToDelete = null },
            title = { Text("彻底删除") },
            text = { Text("彻底删除菜单「${item.menu.name}」后无法恢复，确认删除吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.permanentDeleteMenu(item.menu.id)
                        menuToDelete = null
                        noticeManager.showNotice("已彻底删除")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerColor,
                        contentColor = Color.White
                    )
                ) {
                    Text("彻底删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { menuToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 批量恢复确认对话框
    if (showBatchMenuRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showBatchMenuRestoreDialog = false },
            title = { Text("批量恢复") },
            text = { Text("确认恢复选中的 ${selectedMenuIds.size} 个菜单吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        val count = selectedMenuIds.size
                        viewModel.restoreMenus(selectedMenuIds)
                        isMenuSelectionMode = false
                        selectedMenuIds = emptySet()
                        showBatchMenuRestoreDialog = false
                        noticeManager.showNotice("已恢复 $count 个菜单")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAccentColor.current,
                        contentColor = Color.White
                    )
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchMenuRestoreDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 批量彻底删除确认对话框
    if (showBatchMenuDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchMenuDeleteDialog = false },
            title = { Text("批量彻底删除") },
            text = { Text("将彻底删除选中的 ${selectedMenuIds.size} 个菜单，此操作无法恢复，确认删除吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        val count = selectedMenuIds.size
                        viewModel.permanentDeleteMenus(selectedMenuIds)
                        isMenuSelectionMode = false
                        selectedMenuIds = emptySet()
                        showBatchMenuDeleteDialog = false
                        noticeManager.showNotice("已彻底删除 $count 个菜单")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerColor,
                        contentColor = Color.White
                    )
                ) {
                    Text("彻底删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchMenuDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // ===== 友链对话框 =====

    // 单条恢复确认对话框
    friendLinkToRestore?.let { item ->
        AlertDialog(
            onDismissRequest = { friendLinkToRestore = null },
            title = { Text("恢复友链") },
            text = { Text("确认恢复友链「${item.friendLink.name}」吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreFriendLink(item.friendLink.id)
                        friendLinkToRestore = null
                        noticeManager.showNotice("已恢复")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAccentColor.current,
                        contentColor = Color.White
                    )
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { friendLinkToRestore = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 单条彻底删除确认对话框
    friendLinkToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { friendLinkToDelete = null },
            title = { Text("彻底删除") },
            text = { Text("彻底删除友链「${item.friendLink.name}」后无法恢复，确认删除吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.permanentDeleteFriendLink(item.friendLink.id)
                        friendLinkToDelete = null
                        noticeManager.showNotice("已彻底删除")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerColor,
                        contentColor = Color.White
                    )
                ) {
                    Text("彻底删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { friendLinkToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 批量恢复确认对话框
    if (showBatchFriendLinkRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showBatchFriendLinkRestoreDialog = false },
            title = { Text("批量恢复") },
            text = { Text("确认恢复选中的 ${selectedFriendLinkIds.size} 个友链吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        val count = selectedFriendLinkIds.size
                        viewModel.restoreFriendLinks(selectedFriendLinkIds)
                        isFriendLinkSelectionMode = false
                        selectedFriendLinkIds = emptySet()
                        showBatchFriendLinkRestoreDialog = false
                        noticeManager.showNotice("已恢复 $count 个友链")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAccentColor.current,
                        contentColor = Color.White
                    )
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchFriendLinkRestoreDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 批量彻底删除确认对话框
    if (showBatchFriendLinkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchFriendLinkDeleteDialog = false },
            title = { Text("批量彻底删除") },
            text = { Text("将彻底删除选中的 ${selectedFriendLinkIds.size} 个友链，此操作无法恢复，确认删除吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        val count = selectedFriendLinkIds.size
                        viewModel.permanentDeleteFriendLinks(selectedFriendLinkIds)
                        isFriendLinkSelectionMode = false
                        selectedFriendLinkIds = emptySet()
                        showBatchFriendLinkDeleteDialog = false
                        noticeManager.showNotice("已彻底删除 $count 个友链")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerColor,
                        contentColor = Color.White
                    )
                ) {
                    Text("彻底删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchFriendLinkDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 文章回收站空状态：大图标 + 居中提示文案，呼吸动画
 */
@Composable
private fun TrashPostEmptyState() {
    val infiniteTransition = rememberInfiniteTransition(label = "trashPostEmptyPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trashPostPulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Article,
            contentDescription = null,
            tint = LocalAccentColor.current.copy(alpha = pulseAlpha),
            modifier = Modifier.size(120.dp)
        )
        Text(
            text = "回收站为空",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = "删除的文章将暂存在此，3 天后自动清理",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * 标签回收站空状态：大图标 + 居中提示文案，呼吸动画
 */
@Composable
private fun TrashTagEmptyState() {
    val infiniteTransition = rememberInfiniteTransition(label = "trashTagEmptyPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trashTagPulseAlpha"
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
            text = "回收站中没有标签",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = "删除的标签将暂存在此，3 天后自动清理",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * 菜单回收站空状态：大图标 + 居中提示文案，呼吸动画
 */
@Composable
private fun TrashMenuEmptyState() {
    val infiniteTransition = rememberInfiniteTransition(label = "trashMenuEmptyPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trashMenuPulseAlpha"
    )

    Column(
        modifier = Modifier
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
            text = "回收站中没有菜单",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = "删除的菜单将暂存在此，3 天后自动清理",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * 友链回收站空状态：大图标 + 居中提示文案，呼吸动画
 */
@Composable
private fun TrashFriendLinkEmptyState() {
    val infiniteTransition = rememberInfiniteTransition(label = "trashFriendLinkEmptyPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trashFriendLinkPulseAlpha"
    )

    Column(
        modifier = Modifier
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
            text = "回收站中没有友链",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = "删除的友链将暂存在此，3 天后自动清理",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * 回收站文章卡片：显示标题、删除时间、剩余天数，支持恢复与彻底删除
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashItemCard(
    item: TrashItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 卡片首次出现时的淡入动画
    val fadeInAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fadeInAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300)
        )
    }

    val post = item.trashedPost.post
    val remainingDays = item.remainingDays

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .graphicsLayer { alpha = fadeInAlpha.value },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> LocalAccentColor.current.copy(alpha = 0.16f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
                else -> LocalAccentColor.current.copy(alpha = 0.08f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top
        ) {
            // 内容区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                // 标题 + 复选标记 + 剩余天数徽章
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle
                                          else Icons.Filled.Circle,
                            contentDescription = null,
                            tint = if (isSelected) LocalAccentColor.current
                                   else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = post.data.title.ifEmpty { "未命名文章" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 剩余天数徽章
                    val remainText = if (remainingDays > 0) "剩余 $remainingDays 天" else "今日清理"
                    val remainColor = if (remainingDays <= 1) DangerColor else PinnedColor
                    RemainingDaysBadge(remainText, remainColor)
                }

                // 删除时间
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formatTrashDate(item.trashedPost.trashedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            // 右侧操作按钮（选择模式下隐藏）
            if (!isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onRestore) {
                        Icon(
                            imageVector = Icons.Filled.Restore,
                            contentDescription = "恢复",
                            tint = LocalAccentColor.current
                        )
                    }
                    IconButton(onClick = onPermanentDelete) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = "彻底删除",
                            tint = DangerColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * 回收站标签卡片：显示名称、删除时间、剩余天数，支持恢复与彻底删除
 * 卡片样式与文章卡片保持一致
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashTagCard(
    item: TrashTagItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 卡片首次出现时的淡入动画
    val fadeInAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fadeInAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300)
        )
    }

    val tag = item.tag
    val remainingDays = item.remainingDays

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .graphicsLayer { alpha = fadeInAlpha.value },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> LocalAccentColor.current.copy(alpha = 0.16f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
                else -> LocalAccentColor.current.copy(alpha = 0.08f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top
        ) {
            // 内容区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                // 名称 + 复选标记 + 剩余天数徽章
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle
                                          else Icons.Filled.Circle,
                            contentDescription = null,
                            tint = if (isSelected) LocalAccentColor.current
                                   else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 剩余天数徽章
                    val remainText = if (remainingDays > 0) "剩余 $remainingDays 天" else "今日清理"
                    val remainColor = if (remainingDays <= 1) DangerColor else PinnedColor
                    RemainingDaysBadge(remainText, remainColor)
                }

                // 删除时间
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formatTrashDate(tag.trashedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            // 右侧操作按钮（选择模式下隐藏）
            if (!isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onRestore) {
                        Icon(
                            imageVector = Icons.Filled.Restore,
                            contentDescription = "恢复",
                            tint = LocalAccentColor.current
                        )
                    }
                    IconButton(onClick = onPermanentDelete) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = "彻底删除",
                            tint = DangerColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * 回收站菜单卡片：显示名称、打开方式/链接类型/链接值、删除时间、剩余天数，支持恢复与彻底删除
 * 卡片样式与标签卡片保持一致
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashMenuCard(
    item: TrashMenuItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 卡片首次出现时的淡入动画
    val fadeInAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fadeInAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300)
        )
    }

    val menu = item.menu
    val remainingDays = item.remainingDays

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .graphicsLayer { alpha = fadeInAlpha.value },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> LocalAccentColor.current.copy(alpha = 0.16f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
                else -> LocalAccentColor.current.copy(alpha = 0.08f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top
        ) {
            // 内容区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                // 名称 + 复选标记 + 剩余天数徽章
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle
                                          else Icons.Filled.Circle,
                            contentDescription = null,
                            tint = if (isSelected) LocalAccentColor.current
                                   else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = menu.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 剩余天数徽章
                    val remainText = if (remainingDays > 0) "剩余 $remainingDays 天" else "今日清理"
                    val remainColor = if (remainingDays <= 1) DangerColor else PinnedColor
                    RemainingDaysBadge(remainText, remainColor)
                }

                // 副标题：打开方式 · 链接类型 · 链接值
                Text(
                    text = "${menu.openType} · ${menu.linkType} · ${menu.linkValue}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 删除时间
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formatTrashDate(menu.trashedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            // 右侧操作按钮（选择模式下隐藏）
            if (!isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onRestore) {
                        Icon(
                            imageVector = Icons.Filled.Restore,
                            contentDescription = "恢复",
                            tint = LocalAccentColor.current
                        )
                    }
                    IconButton(onClick = onPermanentDelete) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = "彻底删除",
                            tint = DangerColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * 回收站友链卡片：显示名称、链接、删除时间、剩余天数，支持恢复与彻底删除
 * 卡片样式与标签卡片保持一致
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashFriendLinkCard(
    item: TrashFriendLinkItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 卡片首次出现时的淡入动画
    val fadeInAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fadeInAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300)
        )
    }

    val friendLink = item.friendLink
    val remainingDays = item.remainingDays

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .graphicsLayer { alpha = fadeInAlpha.value },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> LocalAccentColor.current.copy(alpha = 0.16f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
                else -> LocalAccentColor.current.copy(alpha = 0.08f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top
        ) {
            // 内容区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                // 名称 + 复选标记 + 剩余天数徽章
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle
                                          else Icons.Filled.Circle,
                            contentDescription = null,
                            tint = if (isSelected) LocalAccentColor.current
                                   else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = friendLink.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 剩余天数徽章
                    val remainText = if (remainingDays > 0) "剩余 $remainingDays 天" else "今日清理"
                    val remainColor = if (remainingDays <= 1) DangerColor else PinnedColor
                    RemainingDaysBadge(remainText, remainColor)
                }

                // 副标题：链接 URL
                Text(
                    text = friendLink.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 删除时间
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formatTrashDate(friendLink.trashedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            // 右侧操作按钮（选择模式下隐藏）
            if (!isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onRestore) {
                        Icon(
                            imageVector = Icons.Filled.Restore,
                            contentDescription = "恢复",
                            tint = LocalAccentColor.current
                        )
                    }
                    IconButton(onClick = onPermanentDelete) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = "彻底删除",
                            tint = DangerColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * 剩余天数徽章
 */
@Composable
private fun RemainingDaysBadge(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.12f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 格式化回收站时间戳为可读字符串
 */
private fun formatTrashDate(timestamp: Long): String {
    return TRASH_DATE_FORMAT.format(Date(timestamp))
}
