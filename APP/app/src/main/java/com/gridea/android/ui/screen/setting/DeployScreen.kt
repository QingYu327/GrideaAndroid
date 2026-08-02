package com.gridea.android.ui.screen.setting

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gridea.android.R
import com.gridea.android.ui.theme.LocalAccentColor
import com.gridea.android.ui.theme.LocalNoticeManager
import kotlinx.coroutines.launch

/**
 * 部署页面（底部导航主 Tab）
 *
 * 用圆角卡片样式 Tab 分为两个子页面：
 * - 基础配置：站点生成 + 部署平台（先生成站点，再配置平台并发布）
 * - 评论配置：评论系统配置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeployScreen(
    onNavigateToPreview: () -> Unit = {},
    viewModel: SettingViewModel = hiltViewModel()
) {
    val noticeManager = LocalNoticeManager.current
    val accentColor = LocalAccentColor.current

    // 保存提示消息桥接到灵动岛
    val savedMessage by viewModel.savedMessage.collectAsState()
    LaunchedEffect(savedMessage) {
        savedMessage?.let {
            noticeManager.showNotice(it)
            viewModel.clearMessage()
        }
    }

    // 一次性操作消息（清空输出目录、复用 OAuth Token 等）桥接到灵动岛
    val operationMessage by viewModel.operationMessage.collectAsState()
    LaunchedEffect(operationMessage) {
        operationMessage?.let {
            noticeManager.showNotice(it)
            viewModel.clearOperationMessage()
        }
    }

    // 渲染站点结果桥接到灵动岛
    val renderResult by viewModel.renderResult.collectAsState()
    LaunchedEffect(renderResult) {
        renderResult?.let {
            if (it.startsWith("生成失败")) {
                noticeManager.showNotice(it)
            } else {
                noticeManager.showNotice("已生成站点")
            }
        }
    }

    // 部署连通性检测/发布结果桥接到灵动岛
    val detectResult by viewModel.detectResult.collectAsState()
    LaunchedEffect(detectResult) {
        detectResult?.let {
            if (viewModel.shouldNotifyDetectResult()) {
                val msg = if (it.success) "连通正常" else "连通失败：${it.message}"
                noticeManager.showNotice(msg)
                viewModel.markDetectResultNotified()
            }
        }
    }
    // 部署结果通知已迁移到 GrideaApp 层统一处理（DeployService 后台运行 + 灵动岛进度通知）
    // 此处仅收集 deployResult 用于 UI 状态展示（如按钮文案）
    val deployResult by viewModel.deployResult.collectAsState()

    // 回滚结果消息桥接到灵动岛
    val rollbackMessage by viewModel.rollbackMessage.collectAsState()
    LaunchedEffect(rollbackMessage) {
        rollbackMessage?.let {
            noticeManager.showNotice(it)
            viewModel.clearRollbackMessage()
        }
    }

    val tabTitles = listOf("基础配置", "评论配置")

    // 配置加载状态：数据未就绪前不渲染 TextField，避免 label 先空后有触发上移动画
    val isSettingLoaded by viewModel.isSettingLoaded.collectAsState()
    val isCommentLoaded by viewModel.isCommentLoaded.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            val setting by viewModel.setting.collectAsState()
            TopAppBar(
                title = { Text(stringResource(R.string.nav_deploy)) },
                actions = {
                    // 访问站点按钮（圆角卡片风格，右上角）
                    val domain = setting.domain.trim()
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = domain.isNotEmpty()) {
                                val url = if (domain.startsWith("http://") || domain.startsWith("https://")) {
                                    domain
                                } else {
                                    "https://$domain"
                                }
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(url)
                                        )
                                    )
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (domain.isNotEmpty()) accentColor.copy(alpha = 0.12f)
                            .compositeOver(MaterialTheme.colorScheme.surface)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Language,
                                contentDescription = null,
                                tint = if (domain.isNotEmpty()) accentColor
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "访问站点",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (domain.isNotEmpty()) accentColor
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        RoundedTabbedPager(
            tabTitles = tabTitles,
            accentColor = accentColor,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> {
                    // 基础配置：站点生成 + 部署平台
                    // 数据未就绪前显示加载占位符，避免 TextField label 先空后有触发上移动画
                    if (isSettingLoaded) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 90.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            DeploySection(viewModel, onNavigateToPreview)
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                1 -> {
                    // 评论配置
                    if (isCommentLoaded) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 90.dp)
                        ) {
                            CommentSection(viewModel)
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 圆角卡片样式 Tab + HorizontalPager 组合组件
 *
 * - Tab 用 SegmentedControl 风格的圆角卡片，比 TabRow 更精致
 * - HorizontalPager 外层 Box 用 PointerEventPass.Initial 拦截边界出界方向手势，
 *   让 NavHost 的 swipe-to-navigate 能在边界时正常触发，避免手势冲突
 *
 * @param tabTitles Tab 标题列表
 * @param accentColor 强调色（选中 Tab 的填充色）
 * @param modifier 修饰符
 * @param initialPage 初始页索引
 * @param content 每页内容，参数为页索引
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RoundedTabbedPager(
    tabTitles: List<String>,
    accentColor: Color,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    resetTrigger: Int = 0,
    onPageChange: ((Int) -> Unit)? = null,
    content: @Composable (Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()
    val pageCount = tabTitles.size

    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
        onPageChange?.invoke(pagerState.currentPage)
    }

    // resetTrigger 变化时滚动回初始页：用于离开页面 Tab 再返回时重置到默认子页
    androidx.compose.runtime.LaunchedEffect(resetTrigger) {
        if (pagerState.currentPage != initialPage) {
            pagerState.scrollToPage(initialPage)
        }
    }

    Column(modifier = modifier) {
        // 圆角卡片样式 Tab（SegmentedControl 风格）
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
                tabTitles.forEachIndexed { index, title ->
                    val selected = pagerState.currentPage == index
                    // 用 clip 让 ripple 被裁剪为圆角，匹配卡片形状（避免正方形 ripple）
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) accentColor else Color.Transparent
                    ) {
                        Text(
                            text = title,
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

        // HorizontalPager：移除边界手势拦截（pointerInput 拦截会导致滑动卡顿）
        // 恢复为简单的 HorizontalPager，边界处 Tab 切换手势冲突为已知限制
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                content(page)
            }
        }
    }
}
