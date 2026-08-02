package com.gridea.android.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.gridea.android.R
import com.gridea.android.data.model.Post
import com.gridea.android.ui.PressableFloatingActionButton
import com.gridea.android.ui.sharedFabElement
import com.gridea.android.ui.theme.LocalAccentColor

/**
 * 文章列表页
 *
 * 对应旧版 Gridea 0.9.3 的 src/views/article/Articles.vue
 * 支持按标题/内容/标签实时搜索
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onPostClick: (String) -> Unit,
    onNewPostClick: () -> Unit,
    onStatisticsClick: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val posts by viewModel.posts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val filterOption by viewModel.filterOption.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    // 首次加载状态：true 时文章列表区域显示骨架屏占位符
    val isLoading by viewModel.isLoading.collectAsState()

    // 归档模式：按月份将文章归档为文件夹卡片
    var isArchiveMode by rememberSaveable { mutableStateOf(false) }
    // 当前展开的月份（yyyy-MM），null 表示全部收起
    var expandedMonth by remember { mutableStateOf<String?>(null) }

    // 归档分组：按 yyyy-MM 将文章分组，月份降序
    // 直接对已过滤/搜索后的 posts 分组，分组结果自动跟随筛选状态
    val monthGroups = remember(posts) {
        posts.groupBy { it.data.date.take(7) }
            .map { (ym, ps) ->
                val parts = ym.split("-")
                ArchiveMonthGroup(
                    yearMonth = ym,
                    year = parts.getOrNull(0) ?: "",
                    month = parts.getOrNull(1) ?: "",
                    posts = ps
                )
            }
            .sortedByDescending { it.yearMonth }
    }

    // 列表滚动状态：滑动时立即隐藏 FAB，停止滑动后延缓 1s 再出现
    val listState = rememberLazyListState()
    var isFabVisible by remember { mutableStateOf(true) }
    LaunchedEffect(listState.isScrollInProgress) {
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
        label = "homeFabAlpha"
    )
    val fabScale by animateFloatAsState(
        targetValue = if (isFabVisible) 1f else 0.5f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "homeFabScale"
    )

    // 全局灵动岛通知：删除文章后向用户反馈结果
    val noticeManager = com.gridea.android.ui.theme.LocalNoticeManager.current

    var postToDelete by remember { mutableStateOf<Post?>(null) }
    // 用 rememberSaveable：切走再切回时保持搜索栏展开状态（如进入编辑页再返回）
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }

    // 批量选择模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPostIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    // 批量打标签弹窗状态
    var showBatchTagSheet by remember { mutableStateOf(false) }

    // 搜索栏延时收回：展开且搜索框为空时，5 秒无操作自动收回
    // 用户输入文字时不收回（保留搜索状态），用户随时可通过 Close 按钮主动收回
    LaunchedEffect(isSearchExpanded, searchQuery) {
        if (isSearchExpanded && searchQuery.isEmpty()) {
            kotlinx.coroutines.delay(5000)
            isSearchExpanded = false
        }
    }

    // 选择模式下按返回键退出选择模式（避免直接 popBackStack 跳出整个 Home 页）
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedPostIds = emptySet()
    }

    // 搜索状态下按返回键：优先清除搜索内容回到默认首页，而不是退出软件
    // 1. 搜索栏展开且有搜索内容 → 清除搜索内容并收回搜索栏
    // 2. 搜索栏展开但无搜索内容 → 收回搜索栏
    BackHandler(enabled = !isSelectionMode && isSearchExpanded) {
        if (searchQuery.isNotEmpty()) {
            viewModel.clearSearch()
        }
        isSearchExpanded = false
    }

    Scaffold(
        topBar = {
            // TopAppBar 切换加 Crossfade 过渡，避免长按进入选择模式时生硬
            Crossfade(
                targetState = isSelectionMode,
                animationSpec = tween(280),
                label = "topBarTransition"
            ) { selectionMode ->
                if (selectionMode) {
                // 选择模式 ActionMode 样式 TopAppBar
                TopAppBar(
                    title = { Text(stringResource(R.string.batch_selected_count, selectedPostIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedPostIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.batch_exit_selection))
                        }
                    },
                    actions = {
                        // 全选/取消全选按钮：再次点击可取消全选（toggle 行为）
                        // 已全选时图标变填充态 + 文字提示"取消全选"，未全选时为空心 + 文字提示"全选"
                        val allFileNames = remember(posts) { posts.map { it.fileName }.toSet() }
                        val isAllSelected = selectedPostIds.isNotEmpty() && selectedPostIds == allFileNames
                        IconButton(onClick = {
                            selectedPostIds = if (isAllSelected) emptySet() else allFileNames
                        }) {
                            Icon(
                                // 已全选时切换为带勾选效果的填充图标
                                imageVector = if (isAllSelected) Icons.Filled.LibraryAddCheck else Icons.Filled.LibraryAddCheck,
                                contentDescription = stringResource(
                                    if (isAllSelected) R.string.batch_deselect_all else R.string.batch_select_all
                                ),
                                // 已全选时图标使用主题强调色突出反馈
                                tint = if (isAllSelected) LocalAccentColor.current
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 批量导出按钮：将选中文章导出为 .md 文件到 exports 目录
                        IconButton(
                            onClick = {
                                viewModel.exportPostsToMarkdown(selectedPostIds) { success, _, message ->
                                    noticeManager.showNotice(message)
                                    if (success) {
                                        isSelectionMode = false
                                        selectedPostIds = emptySet()
                                    }
                                }
                            },
                            enabled = selectedPostIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "导出",
                                tint = if (selectedPostIds.isNotEmpty())
                                    LocalAccentColor.current
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 批量打标签按钮：弹出底部弹窗选择标签
                        IconButton(
                            onClick = { showBatchTagSheet = true },
                            enabled = selectedPostIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalOffer,
                                contentDescription = "打标签",
                                tint = if (selectedPostIds.isNotEmpty())
                                    LocalAccentColor.current
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 批量删除按钮（仅选中至少 1 篇时可用）
                        IconButton(
                            onClick = { showBatchDeleteDialog = true },
                            enabled = selectedPostIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.batch_delete),
                                tint = if (selectedPostIds.isNotEmpty())
                                    com.gridea.android.ui.theme.DangerColor
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        // 搜索框展开/收起：交叉淡入淡出 + 水平滑动，过渡流畅自然
                        AnimatedContent(
                            targetState = isSearchExpanded,
                            transitionSpec = {
                                if (targetState) {
                                    // 展开：搜索框从右滑入 + 淡入，标题向左滑出 + 淡出
                                    (slideInHorizontally(animationSpec = tween(280)) { it } + fadeIn(tween(280))) togetherWith
                                        (slideOutHorizontally(animationSpec = tween(200)) { -it } + fadeOut(tween(200)))
                                } else {
                                    // 收起：标题从左滑入 + 淡入，搜索框向右滑出 + 淡出
                                    (slideInHorizontally(animationSpec = tween(280)) { -it } + fadeIn(tween(280))) togetherWith
                                        (slideOutHorizontally(animationSpec = tween(200)) { it } + fadeOut(tween(200)))
                                }
                            },
                            label = "searchTransition"
                        ) { expanded ->
                            if (expanded) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = viewModel::updateSearchQuery,
                                    placeholder = {
                                        Text(
                                            text = stringResource(R.string.home_search_hint),
                                            // 提示语字号与输入框文字一致（16sp），视觉更协调
                                            style = TextStyle(
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Normal
                                            )
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Search, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            viewModel.clearSearch()
                                            isSearchExpanded = false
                                        }) {
                                            Icon(Icons.Filled.Close, contentDescription = null)
                                        }
                                    },
                                    singleLine = true,
                                    // 搜索框字体加大到 16sp + SemiBold 加粗，在 24dp 圆角胶囊搜索框内
                                    // 视觉上更突出，符合"在搜索框里不太突出"的优化诉求
                                    textStyle = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    ),
                                    // fillMaxWidth 让搜索框占满 title 区域宽度，居中显示
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Image(
                                        painter = painterResource(R.mipmap.ic_launcher),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(stringResource(R.string.home_title))
                                }
                            }
                        }
                    },
                    actions = {
                        // 非展开时显示搜索图标，带淡入淡出
                        AnimatedVisibility(
                            visible = !isSearchExpanded,
                            enter = fadeIn(animationSpec = tween(200)),
                            exit = fadeOut(animationSpec = tween(150))
                        ) {
                            IconButton(onClick = { isSearchExpanded = true }) {
                                Icon(Icons.Filled.Search, contentDescription = null)
                            }
                        }
                        // 回收站入口：搜索框展开时隐藏，保持与搜索图标一致的显隐节奏
                        AnimatedVisibility(
                            visible = !isSearchExpanded,
                            enter = fadeIn(animationSpec = tween(200)),
                            exit = fadeOut(animationSpec = tween(150))
                        ) {
                            IconButton(onClick = onNavigateToTrash) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = "回收站")
                            }
                        }
                    },
                    // 透明背景 + 无阴影，标题栏与内容区视觉融为一体
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
                }
            }
        },
        // 整体透明背景，由外层 GrideaApp 的 Box 提供统一背景色
        containerColor = Color.Transparent,
        // 选择模式下隐藏 FAB：AnimatedVisibility 平滑过渡，避免与批量删除操作冲突
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = scaleIn(initialScale = 0.6f, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = scaleOut(targetScale = 0.6f, animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(160))
            ) {
                PressableFloatingActionButton(
                    onClick = onNewPostClick,
                    // bottom = 160dp：补偿移除的 NavHost bottom padding（90dp）+ 原 70dp
                    // sharedFabElement：与 EditorScreen（新建模式）根容器做容器变换动画
                    // graphicsLayer alpha+scale：列表滑动时 FAB 淡出+缩小隐藏，停止时回显
                    modifier = Modifier
                        .padding(bottom = 160.dp)
                        .sharedFabElement(key = "new_post_fab")
                        .graphicsLayer {
                            alpha = fabAlpha
                            scaleX = fabScale
                            scaleY = fabScale
                        },
                    containerColor = LocalAccentColor.current,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.home_new_post))
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            // 底部 100dp 留白：让最后内容能滚动到悬浮导航栏上方完整显示，不被遮挡
            // 100dp = 10dp 底部间距 + 80dp NavigationBar 高度 + 10dp 额外留白
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp)
        ) {
            // 统计仪表盘（非搜索状态、非选择模式显示）
            if (!isSearching && !isSelectionMode) {
                item(key = "stats") {
                    StatsCard(stats, onStatisticsClick)
                }
            }

            // 排序与筛选行（选择模式下隐藏，避免干扰批量操作）
            if (!isSelectionMode) {
                item(key = "filters") {
                    // 2/3 占比左对齐：用 Box 包裹 Row，让按钮组在横向只占 2/3 宽度并左对齐显示
                    // 配合外周粗边框让按钮更醒目
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 排序按钮：圆角卡片风格，与全局圆角卡片 UI 一致
                            val sortLabel = when (sortOption) {
                                SortOption.DATE_DESC -> stringResource(R.string.home_sort_date_desc)
                                SortOption.DATE_ASC -> stringResource(R.string.home_sort_date_asc)
                                SortOption.TITLE -> stringResource(R.string.home_sort_title)
                            }
                            androidx.compose.material3.Surface(
                                onClick = {
                                    val next = when (sortOption) {
                                        SortOption.DATE_DESC -> SortOption.DATE_ASC
                                        SortOption.DATE_ASC -> SortOption.TITLE
                                        SortOption.TITLE -> SortOption.DATE_DESC
                                    }
                                    viewModel.updateSortOption(next)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = sortLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // 筛选按钮：圆角卡片风格，选中态用强调色填充
                            val filterLabel = when (filterOption) {
                                FilterOption.ALL -> stringResource(R.string.home_filter_all)
                                FilterOption.PUBLISHED -> stringResource(R.string.home_filter_published)
                                FilterOption.DRAFT -> stringResource(R.string.home_filter_draft)
                            }
                            val filterSelected = filterOption != FilterOption.ALL
                            androidx.compose.material3.Surface(
                                onClick = {
                                    val next = when (filterOption) {
                                        FilterOption.ALL -> FilterOption.PUBLISHED
                                        FilterOption.PUBLISHED -> FilterOption.DRAFT
                                        FilterOption.DRAFT -> FilterOption.ALL
                                    }
                                    viewModel.updateFilterOption(next)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (filterSelected) LocalAccentColor.current.copy(alpha = 0.12f)
                                       else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                contentColor = if (filterSelected) LocalAccentColor.current
                                              else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = filterLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // 归档按钮：圆角卡片风格，激活态用强调色填充
                            // 点击切换归档模式，相同月份文章合成文件夹卡片
                            androidx.compose.material3.Surface(
                                onClick = {
                                    isArchiveMode = !isArchiveMode
                                    expandedMonth = null
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isArchiveMode) LocalAccentColor.current.copy(alpha = 0.12f)
                                       else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                contentColor = if (isArchiveMode) LocalAccentColor.current
                                              else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Archive,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.home_archive),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 文章列表 / 骨架屏 / 空状态
            if (posts.isEmpty() && isLoading) {
                // 首次加载中：显示骨架屏占位卡片，避免空状态闪烁和动画卡顿
                items(5) { index ->
                    PostCardSkeleton()
                }
            } else if (posts.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        isEmpty = searchQuery.isEmpty(),
                        onCreatePost = onNewPostClick,
                        onClearSearch = {
                            viewModel.clearSearch()
                            isSearchExpanded = false
                        }
                    )
                }
            } else {
                if (isArchiveMode) {
                    // 归档模式：按月份合成文件夹卡片
                    // 流畅性策略：不逐条动画文章卡片收缩，而是直接切换数据源到分组结果
                    // 文件夹卡片数量远少于文章，入场时 scale+fade 缩放淡入即可营造"合成"视觉
                    items(
                        items = monthGroups,
                        key = { it.yearMonth }
                    ) { group ->
                        MonthFolderCard(
                            group = group,
                            isExpanded = expandedMonth == group.yearMonth,
                            onClick = {
                                expandedMonth = if (expandedMonth == group.yearMonth) null else group.yearMonth
                            },
                            onPostClick = onPostClick,
                            onDeletePost = { postToDelete = it }
                        )
                    }
                } else {
                    items(
                        items = posts,
                        key = { it.fileName }
                    ) { post ->
                        val isSelected = post.fileName in selectedPostIds
                        PostItem(
                            post = post,
                            onClick = {
                                if (isSelectionMode) {
                                    // 选择模式下点击切换选中状态
                                    selectedPostIds = if (isSelected) {
                                        selectedPostIds - post.fileName
                                    } else {
                                        selectedPostIds + post.fileName
                                    }
                                    // 取消所有选中后自动退出选择模式
                                    if (selectedPostIds.isEmpty()) isSelectionMode = false
                                } else {
                                    onPostClick(post.fileName)
                                }
                            },
                            onLongClick = {
                                // 长按进入选择模式并选中当前文章；已选择时长按切换选中
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedPostIds = setOf(post.fileName)
                                } else {
                                    selectedPostIds = if (isSelected) {
                                        selectedPostIds - post.fileName
                                    } else {
                                        selectedPostIds + post.fileName
                                    }
                                    if (selectedPostIds.isEmpty()) isSelectionMode = false
                                }
                            },
                            onDelete = { postToDelete = post },
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            // horizontal padding 16dp 保留初始边距
                            // 注意：不使用 animateItem()，因为它与 FlowRow 的 lookahead 机制冲突，
                            // 批量打标签后滚动会触发 "Placement happened before lookahead" 崩溃
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }

    // 单条删除确认对话框（移到回收站）
    postToDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { postToDelete = null },
            title = { Text("移到回收站") },
            text = { Text("文章将移到回收站，3 天后自动清理。可在回收站中恢复或彻底删除。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePost(post.fileName)
                        postToDelete = null
                        noticeManager.showNotice("已移到回收站")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.gridea.android.ui.theme.DangerColor,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text("移到回收站")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { postToDelete = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 批量删除确认对话框（移到回收站）
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("移到回收站") },
            text = { Text("将选中的 ${selectedPostIds.size} 篇文章移到回收站，3 天后自动清理。可在回收站中恢复或彻底删除。") },
            confirmButton = {
                Button(
                    onClick = {
                        val deletedCount = selectedPostIds.size
                        viewModel.deletePosts(selectedPostIds)
                        isSelectionMode = false
                        selectedPostIds = emptySet()
                        showBatchDeleteDialog = false
                        noticeManager.showNotice("已移到回收站 $deletedCount 篇文章")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.gridea.android.ui.theme.DangerColor,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text("移到回收站")
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

    // 批量打标签底部弹窗：展示已有标签（多选）+ 新建标签输入框
    if (showBatchTagSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // 已选标签（已有标签中勾选的 + 新建的都汇总到此集合）
        var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
        var newTagText by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { showBatchTagSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                // 标题栏：标题 + 选中文章数提示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "批量打标签",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "已选 ${selectedPostIds.size} 篇文章",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 已选标签摘要（实时反馈）
                if (selectedTags.isNotEmpty()) {
                    Text(
                        text = "待添加：${selectedTags.joinToString("、")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAccentColor.current,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // 已有标签列表（FlowRow + FilterChip 多选）
                if (allTags.isNotEmpty()) {
                    Text(
                        text = "已有标签",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allTags.forEach { tag ->
                            FilterChip(
                                selected = tag in selectedTags,
                                onClick = {
                                    selectedTags = if (tag in selectedTags) {
                                        selectedTags - tag
                                    } else {
                                        selectedTags + tag
                                    }
                                },
                                label = { Text(tag) }
                            )
                        }
                    }
                }

                // 新建标签输入框（支持逗号分隔多个标签）
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    label = { Text("新建标签（多个标签用逗号分隔）") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (newTagText.isNotBlank()) {
                            IconButton(onClick = {
                                // 将输入的标签加入已选集合并清空输入框
                                val parsed = newTagText.split(",").map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                selectedTags = selectedTags + parsed
                                newTagText = ""
                            }) {
                                Icon(Icons.Filled.Add, contentDescription = "添加")
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = LocalAccentColor.current,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = LocalAccentColor.current,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = LocalAccentColor.current
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )

                // 确定按钮：合并已选标签 + 输入框中的标签，调用批量打标签
                val canConfirm = selectedTags.isNotEmpty() || newTagText.trim().isNotEmpty()
                Button(
                    onClick = {
                        val tagsToAdd = selectedTags.toMutableList()
                        // 也把输入框中尚未点"添加"的内容一并解析加入
                        newTagText.split(",").map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { tagsToAdd.add(it) }
                        val dedupedTags = tagsToAdd.distinct()
                        viewModel.batchAddTags(selectedPostIds, dedupedTags) { success, _, message ->
                            noticeManager.showNotice(message)
                        }
                        showBatchTagSheet = false
                        isSelectionMode = false
                        selectedPostIds = emptySet()
                    },
                    enabled = canConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAccentColor.current,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text("确定")
                }
            }
        }
    }
}

/**
 * 文章卡片骨架屏占位符
 *
 * 首次加载期间显示，模拟真实文章卡片形状（圆角卡片 + 标题占位条 + 日期占位条），
 * 配合呼吸式 alpha 动画让用户感知"内容正在加载"。
 */
@Composable
private fun PostCardSkeleton() {
    val transition = rememberInfiniteTransition(label = "postSkeletonBreath")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "postSkeletonAlpha"
    )
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.18f)
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题占位条
            Surface(shape = RoundedCornerShape(4.dp), color = placeholderColor) {
                Spacer(modifier = Modifier.fillMaxWidth(0.75f).height(16.dp))
            }
            // 日期占位条
            Surface(shape = RoundedCornerShape(4.dp), color = placeholderColor) {
                Spacer(modifier = Modifier.fillMaxWidth(0.4f).height(10.dp))
            }
        }
    }
}

/**
 * 空状态：大图标（120dp）+ 居中提示文案，图标用主题色淡化处理并附带柔和呼吸动画
 *
 * 无文章时显示"新建文章"按钮，搜索无结果时显示"清除搜索"按钮。
 *
 * @param isEmpty true 表示无文章，false 表示搜索无结果
 * @param onCreatePost 无文章时点击"新建文章"的回调
 * @param onClearSearch 搜索无结果时点击"清除搜索"的回调
 */
@Composable
private fun EmptyState(
    isEmpty: Boolean,
    onCreatePost: () -> Unit = {},
    onClearSearch: () -> Unit = {}
) {
    // 柔和的呼吸动画：透明度在 0.35f 与 0.65f 之间往返
    val infiniteTransition = rememberInfiniteTransition(label = "emptyStatePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isEmpty) Icons.AutoMirrored.Filled.Article else Icons.Filled.Search,
            contentDescription = null,
            tint = LocalAccentColor.current.copy(alpha = pulseAlpha),
            modifier = Modifier.size(120.dp)
        )
        Text(
            text = if (isEmpty) stringResource(R.string.home_empty_title) else stringResource(R.string.home_no_match_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = if (isEmpty) stringResource(R.string.home_empty_subtitle) else stringResource(R.string.home_no_match_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        // 无文章时显示"新建文章"按钮（强调色填充）
        if (isEmpty) {
            Button(
                onClick = onCreatePost,
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
                    text = "新建文章",
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        } else {
            // 搜索无结果时显示"清除搜索"提示按钮（文本按钮，强调色文字）
            TextButton(
                onClick = onClearSearch,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = LocalAccentColor.current
                ),
                modifier = Modifier.padding(top = 20.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "清除搜索",
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

/**
 * 文章卡片：16dp 圆角 + 微妙阴影，置顶文章加浅绿色调背景，置顶/隐藏状态用徽章标识
 * 出现时有淡入动画（[fadeInAlpha]）。
 * 长按进入批量选择模式，选择模式下点击切换选中状态。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PostItem(
    post: Post,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .graphicsLayer { alpha = fadeInAlpha.value },
        shape = RoundedCornerShape(16.dp),
        // 卡片背景：默认填充强调色浅色（alpha 0.08 让任何颜色都变浅，跟随主题变换）
        // 选中状态用更深的强调色（0.12），置顶状态用浅绿色调
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> LocalAccentColor.current.copy(alpha = 0.16f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
                post.data.isTop -> com.gridea.android.ui.theme.PinnedColor.copy(alpha = 0.10f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
                else -> LocalAccentColor.current.copy(alpha = 0.08f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
            }
        ),
        // 移除阴影，靠浅色背景与周边区分
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
                // 标题（加粗）+ 选中复选标记（选择模式）+ 状态徽章
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 选择模式下显示复选标记
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
                        text = post.data.title.ifEmpty { stringResource(R.string.home_unnamed_post) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 置顶标记：浅绿色徽章（与隐藏文章标记同理）
                    if (post.data.isTop) {
                        StatusBadge(stringResource(R.string.home_pinned), com.gridea.android.ui.theme.PinnedColor)
                    }
                    // 隐藏文章标记（加粗红色徽章，方便管理）
                    if (post.data.hideInList) {
                        StatusBadge(stringResource(R.string.home_hidden_badge), com.gridea.android.ui.theme.DangerColor)
                    }
                    if (post.data.published) {
                        StatusBadge(stringResource(R.string.home_published_badge), LocalAccentColor.current)
                    } else {
                        StatusBadge(stringResource(R.string.home_draft_badge), MaterialTheme.colorScheme.outline)
                    }
                }

                // 日期：Icon + Text 组合
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = post.data.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // 摘要预览（从 content 提取纯文本前 100 字，用次要颜色）
                val summary = remember(post.content) { extractSummary(post.content) }
                if (summary.isNotEmpty()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                // 标签：Chip 样式（小圆角 + 半透明主题色背景）
                if (post.data.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        post.data.tags.forEach { tag ->
                            TagChip(tag)
                        }
                    }
                }
            }

            // 右侧：特色图缩略图 + 删除按钮（选择模式下隐藏单个删除按钮）
            if (post.data.feature.isNotEmpty()) {
                AsyncImage(
                    model = post.data.feature,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = 16.dp, end = 4.dp)
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            if (!isSelectionMode) {
                // 删除按钮：fillMaxHeight 让按钮随卡片内容高度动态垂直居中
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 4.dp)
                ) {
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
 * 标签 Chip：圆角药丸样式，半透明强调色背景，内含 LocalOffer 图标 + 文字
 */
@Composable
private fun TagChip(text: String) {
    val accentColor = LocalAccentColor.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = accentColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.LocalOffer,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// extractSummary 使用的正则（文件级缓存，避免每次调用都重新编译 6 个 Regex）
private val CODE_BLOCK_REGEX = Regex("```[\\s\\S]*?```")
private val IMAGE_REGEX = Regex("!\\[[^\\]]*\\]\\([^)]*\\)")
private val LINK_REGEX = Regex("\\[([^\\]]*)\\]\\([^)]*\\)")
private val HEADING_REGEX = Regex("^#{1,6}\\s+", RegexOption.MULTILINE)
private val MD_SYMBOL_REGEX = Regex("[*>`#~-]")
private val WHITESPACE_REGEX = Regex("\\s+")

/**
 * 从 Markdown 内容提取纯文本摘要
 * 去除 front-matter、标题符号、图片、链接、代码块等
 */
private fun extractSummary(content: String): String {
    var text = content
    // 去除 front-matter（--- 包裹的部分）
    if (text.startsWith("---")) {
        val end = text.indexOf("\n---", 3)
        if (end >= 0) text = text.substring(end + 4)
    }
    return text
        .replace(CODE_BLOCK_REGEX, "")   // 代码块
        .replace(IMAGE_REGEX, "") // 图片
        .replace(LINK_REGEX, "$1") // 链接保留文字
        .replace(HEADING_REGEX, "") // 标题符号
        .replace(MD_SYMBOL_REGEX, " ") // markdown 符号
        .replace(WHITESPACE_REGEX, " ")
        .trim()
        .take(100)
}

@Composable
private fun StatusBadge(
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
 * 文章统计仪表盘卡片
 * 使用柔和的 surfaceVariant 背景，与页面整体视觉统一
 */
@Composable
private fun StatsCard(stats: PostStats, onStatisticsClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("${stats.published}", stringResource(R.string.home_stat_published))
                StatItem("${stats.draft}", stringResource(R.string.home_stat_draft))
                StatItem("${stats.tagCount}", stringResource(R.string.home_stat_tags))
                StatItem("${stats.continuousDays}", stringResource(R.string.home_stat_continuous_days))
            }
            // 查看详细统计按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onStatisticsClick)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.statistics_view_detail),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    val accentColor = LocalAccentColor.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 数字使用强调色渐变 + 大号粗体
        Text(
            text = value,
            style = TextStyle(
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.onSurface,
                        accentColor
                    )
                )
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 归档月份分组数据
 */
private data class ArchiveMonthGroup(
    val yearMonth: String,
    val year: String,
    val month: String,
    val posts: List<Post>
)

/**
 * 月份文件夹卡片
 *
 * 归档模式下展示：左侧年份+月份，右侧文章总数 + 展开箭头。
 * 点击展开后内嵌显示该月文章列表（复用 PostItem 保持视觉一致）。
 *
 * 流畅性策略（应对大量文章）：
 * - 不对每条文章卡片逐条做收缩动画，而是直接切换数据源到分组结果；
 * - 文件夹卡片数量远少于文章（每月一条），入场仅用一个 Animatable 同时驱动
 *   alpha(0→1) 与 scale(0.9→1)，渲染开销极低；
 * - LazyColumn 自身虚拟化，展开月份内的文章按需组合，避免一次性渲染全部。
 */
@Composable
private fun MonthFolderCard(
    group: ArchiveMonthGroup,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onPostClick: (String) -> Unit,
    onDeletePost: (Post) -> Unit
) {
    val accentColor = LocalAccentColor.current
    // 入场动画：alpha 0→1 + scale 0.9→1，营造"文件夹合成"的视觉
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                alpha = entrance.value
                scaleX = 0.9f + 0.1f * entrance.value
                scaleY = 0.9f + 0.1f * entrance.value
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.10f)
                .compositeOver(MaterialTheme.colorScheme.surface)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // 文件夹头：左侧年月，右侧文章总数 + 展开箭头
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Archive,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "${group.year}年${group.month}月",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                )
                // 文章总数
                Text(
                    text = "${group.posts.size} 篇",
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = if (isExpanded) 90f else 0f }
                )
            }
            // 展开内容：该月文章列表（复用 PostItem 保持与首页列表视觉一致）
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    group.posts.forEach { post ->
                        PostItem(
                            post = post,
                            onClick = { onPostClick(post.fileName) },
                            onLongClick = {},
                            onDelete = { onDeletePost(post) },
                            isSelectionMode = false,
                            isSelected = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
