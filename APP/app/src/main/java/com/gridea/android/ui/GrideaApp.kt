package com.gridea.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.gridea.android.R
import com.gridea.android.ui.navigation.Screen
import com.gridea.android.ui.screen.onboarding.OnboardingScreen
import com.gridea.android.ui.screen.onboarding.OnboardingViewModel
import com.gridea.android.ui.screen.editor.EditorScreen
import com.gridea.android.ui.screen.home.HomeScreen
import com.gridea.android.ui.screen.setting.DeployScreen
import com.gridea.android.ui.screen.setting.SettingScreen
import com.gridea.android.ui.screen.setting.SettingSectionScreen
import com.gridea.android.ui.screen.setting.ThemeHubScreen
import com.gridea.android.ui.screen.statistics.StatisticsScreen
import com.gridea.android.ui.screen.pages.PagesScreen
import com.gridea.android.ui.screen.tags.TagsScreen
import com.gridea.android.ui.screen.trash.TrashScreen
import com.gridea.android.ui.theme.LocalAccentColor
import com.gridea.android.ui.theme.LocalNoticeManager
import com.gridea.android.ui.theme.rememberNoticeManager

/**
 * Gridea 应用入口 Composable
 *
 * 启动时先通过 DataStore 读取引导完成状态：
 * - null（加载中）：显示简单 loading
 * - false（未完成引导）：全屏显示引导页，完成后写入 onboarding_completed = true
 * - true（已完成引导）：进入正常导航主界面
 */
@Composable
fun GrideaApp(
    pendingShortcut: String? = null,
    onShortcutConsumed: () -> Unit = {}
) {
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsState()

    when (onboardingCompleted) {
        // DataStore 读取中：显示 loading + 预热 Compose 运行时
        null -> {
            // 预热阶段：在 loading 期间运行关键动画代码，触发 ART 预编译
            // Compose 动画首次执行时处于解释模式（JIT 未编译），造成卡顿
            // 预先运行一遍让 ART 收集 profile 数据并编译热点代码
            LaunchedEffect(Unit) {
                // 预热动画核心：运行 tween / spring 让 Animatable、TweenSpec、SpringSpec 等类被加载和编译
                val warmup = Animatable(0f)
                warmup.animateTo(1f, animationSpec = tween(16, easing = FastOutSlowInEasing))
                warmup.animateTo(0f, animationSpec = spring(
                    dampingRatio = Spring.DampingRatioHighBouncy,
                    stiffness = Spring.StiffnessHigh
                ))
                warmup.animateTo(1f, animationSpec = tween(16, easing = FastOutSlowInEasing))
                // 等待 2 帧让 Choreographer 和 Compose runtime 完成初始化
                withFrameNanos { }
                withFrameNanos { }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LocalAccentColor.current)
            }
        }
        // 未完成引导：显示引导页，完成后持久化状态并自动切换到主界面
        false -> {
            OnboardingScreen(onComplete = { onboardingViewModel.completeOnboarding() })
        }
        // 已完成引导：进入主界面
        true -> {
            GrideaAppContent(
                pendingShortcut = pendingShortcut,
                onShortcutConsumed = onShortcutConsumed
            )
        }
    }
}

/**
 * 主 Tab 路由集合（文件级常量，避免每次重组都重建 Set）
 * 用于判断当前是否在主 Tab，决定导航栏可见性与手势切换权限
 */
private val MAIN_TAB_ROUTES = setOf(
    Screen.Home.route, Screen.Tags.route, Screen.Deploy.route,
    Screen.ThemeHub.route, Screen.Setting.route
)

/**
 * Gridea 应用主界面 Composable
 *
 * 对应旧版 Gridea 0.9.3 的 src/App.vue
 * 包含底部导航和页面路由
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun GrideaAppContent(
    pendingShortcut: String? = null,
    onShortcutConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // 全局灵动岛通知管理器：任何子页面都可通过 LocalNoticeManager.current.showNotice() 触发通知
    val noticeManager = rememberNoticeManager()
    val noticeData by noticeManager.notice
    val noticeText = noticeData?.text

    // 部署服务观察者：监听后台部署进度和结果，通过灵动岛通知实时反馈
    // 部署在 Application 级协程运行（DeployService），切页不中断
    // 此 ViewModel 绑定到 Activity 生命周期，确保切页时观察不中断
    val deployViewModel: com.gridea.android.ui.screen.deploy.DeployViewModel = hiltViewModel()
    val isDeploying by deployViewModel.isDeploying.collectAsState()
    val deployProgress by deployViewModel.deployProgress.collectAsState()
    val deployResult by deployViewModel.deployResult.collectAsState()

    // 部署进度通知：常驻显示当前上传进度（如"上传中 5/20 - index.html"）
    LaunchedEffect(isDeploying, deployProgress) {
        if (isDeploying && deployProgress != null) {
            val p = deployProgress!!
            noticeManager.showNotice("上传中 ${p.current}/${p.total} - ${p.fileName}", persistent = true)
        }
    }

    // 部署完成通知：显示成功/失败结果，2.2 秒后自动消失
    LaunchedEffect(deployResult) {
        deployResult?.let { result ->
            if (result.success) {
                val urlPart = result.url?.let { "，访问：$it" } ?: ""
                noticeManager.showNotice("部署成功！已上传 ${result.fileCount} 个文件$urlPart")
            } else {
                noticeManager.showNotice(result.message)
            }
            deployViewModel.clearDeployResult()
        }
    }

    // 全局错误事件订阅：软件内部错误 → 红色灵动岛通知 → 引导用户查看日志反馈
    // 串联"错误捕获 → 智能提醒 → 日志查看 → 问题反馈"完整流程
    LaunchedEffect(Unit) {
        com.gridea.android.util.ErrorBus.events.collect { message ->
            noticeManager.showNotice(
                text = "$message，请查看日志管理进行反馈",
                type = com.gridea.android.ui.theme.NoticeType.Error
            )
        }
    }

    // 处理应用快捷方式导航
    LaunchedEffect(pendingShortcut) {
        when (pendingShortcut) {
            "new_post" -> {
                navController.navigate(Screen.Editor.route)
                onShortcutConsumed()
            }
        }
    }

    CompositionLocalProvider(LocalNoticeManager provides noticeManager) {
    // SharedTransitionLayout 包裹整个界面：提供容器变换动画作用域
    // 通过 LocalSharedTransitionScope 注入给子 Composable，配合各 composable 的
    // LocalNavAnimatedVisibilityScope 实现 FAB → 编辑页的容器变换动画
    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
    // 悬浮导航栏：用 Box 叠加让内容延伸到导航栏下方，导航栏真正悬浮
    // 这样滑动文章列表时可以滑到导航栏下方，没有明显分界线
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            // 容器背景透明：让外层 Surface 的背景色透过显示
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0)
        ) { innerPadding ->
        // 当前是否在主 Tab（Home/Tags/Deploy/ThemeHub/Setting）。仅在主 Tab 才允许手势切换，
        // 进入编辑/设置详情等子页面时禁用，避免与子页面内部手势冲突
        val currentRoute = currentDestination?.route
        val isOnMainTab = currentRoute in setOf(
            Screen.Home.route, Screen.Tags.route, Screen.Deploy.route,
            Screen.ThemeHub.route, Screen.Setting.route
        )

        // 手势滑动切换 Tab：累积横向滑动距离，超过阈值后触发一次切换
        val density = LocalDensity.current
        val swipeThresholdPx = with(density) { 80.dp.toPx() }   // 触发切换的滑动阈值
        val accumulatedDrag = remember { mutableFloatStateOf(0f) }

        // 导航锁：防止快速滑动连续触发 navigate 导致 NavController 状态异常崩溃
        // 触发切换后锁定 350ms，等导航动画完成再放开
        var isNavigating by remember { mutableStateOf(false) }
        LaunchedEffect(isNavigating) {
            if (isNavigating) {
                kotlinx.coroutines.delay(350)
                isNavigating = false
            }
        }

        // 用 rememberUpdatedState 让 pointerInput lambda 始终读到最新的 route/isOnMainTab
        // 避免 pointerInput key 随 currentRoute 变化而重建（重建会中断进行中的手势）
        val currentRouteUpdated = rememberUpdatedState(currentRoute)
        val isOnMainTabUpdated = rememberUpdatedState(isOnMainTab)

        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            // 不应用 bottom padding，让内容能延伸到悬浮导航栏下方
            // 导航栏圆角内 surface 不透明遮挡内容，圆角外透明能透过看见下方滚动内容，真悬浮视觉
            // 各页面 LazyColumn 自行用 contentPadding bottom 处理最后内容显示，避免被导航栏遮挡
            modifier = Modifier
                .padding(
                    start = innerPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                )
                .pointerInput(Unit) {
                    // key=Unit：pointerInput 只启动一次，不随 route 变化重建
                    // 避免导航发生时手势被中断导致的崩溃
                    detectHorizontalDragGestures(
                        onDragStart = { accumulatedDrag.floatValue = 0f },
                        onDragEnd = { accumulatedDrag.floatValue = 0f },
                        onDragCancel = { accumulatedDrag.floatValue = 0f }
                    ) { _, dragAmount ->
                        // 每次拖动事件读取最新状态
                        if (!isOnMainTabUpdated.value || isNavigating) return@detectHorizontalDragGestures
                        accumulatedDrag.floatValue += dragAmount
                        val total = accumulatedDrag.floatValue
                        val route = currentRouteUpdated.value
                        when {
                            // 向左滑动距离超过阈值（向右切到下一个 Tab）
                            total <= -swipeThresholdPx -> {
                                val currentIndex = bottomNavItems.indexOfFirst { it.route == route }
                                if (currentIndex in 0..(bottomNavItems.size - 2)) {
                                    val target = bottomNavItems[currentIndex + 1]
                                    // 设置/部署/主题页不恢复状态：切走再切回时始终回到入口默认状态
                                    val shouldResetState = target.route == Screen.Setting.route ||
                                        target.route == Screen.Deploy.route ||
                                        target.route == Screen.ThemeHub.route
                                    isNavigating = true
                                    try {
                                        navController.navigate(target.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = !shouldResetState
                                        }
                                    } catch (_: Exception) {
                                        // 导航过程中状态不一致时忽略，避免崩溃
                                        isNavigating = false
                                    }
                                }
                                accumulatedDrag.floatValue = 0f
                            }
                            // 向右滑动距离超过阈值（向左切到上一个 Tab）
                            total >= swipeThresholdPx -> {
                                val currentIndex = bottomNavItems.indexOfFirst { it.route == route }
                                if (currentIndex in 1..(bottomNavItems.size - 1)) {
                                    val target = bottomNavItems[currentIndex - 1]
                                    // 设置/部署/主题页不恢复状态：切走再切回时始终回到入口默认状态
                                    val shouldResetState = target.route == Screen.Setting.route ||
                                        target.route == Screen.Deploy.route ||
                                        target.route == Screen.ThemeHub.route
                                    isNavigating = true
                                    try {
                                        navController.navigate(target.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = !shouldResetState
                                        }
                                    } catch (_: Exception) {
                                        isNavigating = false
                                    }
                                }
                                accumulatedDrag.floatValue = 0f
                            }
                        }
                    }
                },
            // 底部 Tab 之间：scale(0.92f) + fade 组合
            // 通过减小 initialScale（0.85f → 0.92f）和缩短时长（280ms → 220ms）让动画更轻快流畅，
            // 避免大缩放+长时长在配置项较多页面造成的卡顿感
            enterTransition = {
                scaleIn(animationSpec = tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f) +
                    fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
            },
            exitTransition = {
                scaleOut(animationSpec = tween(180, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                    fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
            },
            popEnterTransition = {
                scaleIn(animationSpec = tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f) +
                    fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
            },
            popExitTransition = {
                scaleOut(animationSpec = tween(180, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                    fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
            }
        ) {
            composable(
                route = Screen.Home.route,
                // 文章列表：scale(0.92f) + fade，与主 Tab 切换过渡保持一致
                enterTransition = {
                    scaleIn(animationSpec = tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f) +
                        fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    scaleOut(animationSpec = tween(180, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                },
                // popEnter（从编辑页返回首页）：200ms + initialScale 0.94f
                // 列表点击退出已改为一步退出（直接 onBack），不再有 EditorScreen 内部两步淡出，
                // 不会出现双方都透明的白屏空档，可安全使用规范时长让过渡更柔和
                popEnterTransition = {
                    scaleIn(animationSpec = tween(200, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    scaleOut(animationSpec = tween(180, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                }
            ) {
                // 注入 AnimatedVisibilityScope，供 HomeScreen 内 FAB 的 sharedElement 使用
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    HomeScreen(
                        onPostClick = { fileName ->
                            navController.navigate("${Screen.Editor.route}?fileName=$fileName")
                        },
                        onNewPostClick = {
                            navController.navigate(Screen.Editor.route)
                        },
                        onStatisticsClick = {
                            navController.navigate(Screen.Statistics.route)
                        },
                        onNavigateToTrash = {
                            navController.navigate(Screen.Trash.route)
                        }
                    )
                }
            }
            composable(
                route = "${Screen.Editor.route}?fileName={fileName}",
                arguments = listOf(
                    navArgument("fileName") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
                // 编辑页：scale(0.94f) + fade 组合
                // 进入保持柔和过渡，退出为两步动画：
                //   1. 内容淡出 20ms（在 EditorScreen 内通过 graphicsLayer alpha 完成，创建空容器）
                //   2. 空容器收缩 30ms（此处 popExitTransition），总耗时 50ms
                enterTransition = {
                    scaleIn(animationSpec = tween(240, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    scaleOut(animationSpec = tween(50, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(50, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    scaleIn(animationSpec = tween(200, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
                },
                // popExit（列表点击返回）：180ms + targetScale 0.96f
                // 列表点击模式直接 onBack() 一步退出，与首页 popEnter(200ms) 时长接近，过渡更协调
                popExitTransition = {
                    scaleOut(animationSpec = tween(180, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                }
            ) { backStackEntry ->
                val fileName = backStackEntry.arguments?.getString("fileName")
                // 注入 AnimatedVisibilityScope，供 EditorScreen 根容器的 sharedElement 使用
                // 仅 fileName==null（从 FAB 新建）时 EditorScreen 才会挂 sharedElement modifier
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    EditorScreen(
                        fileName = fileName,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                route = Screen.Tags.route,
                // 页面 Tab：scale(0.92f) + fade，与主 Tab 切换过渡保持一致
                enterTransition = {
                    scaleIn(animationSpec = tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f) +
                        fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    scaleOut(animationSpec = tween(180, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    scaleIn(animationSpec = tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f) +
                        fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    scaleOut(animationSpec = tween(180, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                }
            ) {
                PagesScreen(
                    onPostClick = { fileName ->
                        navController.navigate("${Screen.Editor.route}?fileName=$fileName")
                    }
                )
            }
            composable(
                route = Screen.Setting.route,
                // 设置页（主 Tab）：scale(0.92f) + fade
                enterTransition = {
                    scaleIn(animationSpec = tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f) +
                        fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    scaleOut(animationSpec = tween(180, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    scaleIn(animationSpec = tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f) +
                        fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    scaleOut(animationSpec = tween(180, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                }
            ) {
                SettingScreen(
                    onNavigateToSection = { section ->
                        navController.navigate("${Screen.SettingSection.route}?section=$section")
                    },
                    onNavigateToLogManage = {
                        navController.navigate(Screen.LogManager.route)
                    }
                )
            }
            // 部署页（主 Tab）：scale(0.92f) + fade
            composable(
                route = Screen.Deploy.route,
                enterTransition = {
                    scaleIn(animationSpec = tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f) +
                        fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    scaleOut(animationSpec = tween(180, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    scaleIn(animationSpec = tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f) +
                        fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    scaleOut(animationSpec = tween(180, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                }
            ) {
                DeployScreen(
                    onNavigateToPreview = { navController.navigate(Screen.Preview.route) }
                )
            }
            // 主题页（主 Tab）：scale(0.92f) + fade
            composable(
                route = Screen.ThemeHub.route,
                enterTransition = {
                    scaleIn(animationSpec = tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f) +
                        fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    scaleOut(animationSpec = tween(180, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    scaleIn(animationSpec = tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f) +
                        fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    scaleOut(animationSpec = tween(180, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                }
            ) {
                ThemeHubScreen()
            }
            composable(
                route = Screen.SiteInfo.route,
                // 站点信息页：scale(0.94f) + fade 组合，与 Editor/SettingSection 等二级页面风格一致
                enterTransition = {
                    scaleIn(animationSpec = tween(240, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    scaleOut(animationSpec = tween(160, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    scaleIn(animationSpec = tween(200, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    scaleOut(animationSpec = tween(160, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
                }
            ) {
                com.gridea.android.ui.screen.setting.SiteInfoScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.Preview.route,
                enterTransition = {
                    scaleIn(animationSpec = tween(240, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    scaleOut(animationSpec = tween(160, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    scaleIn(animationSpec = tween(200, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    scaleOut(animationSpec = tween(160, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
                }
            ) {
                com.gridea.android.ui.screen.preview.PreviewScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.LogManager.route,
                enterTransition = {
                    scaleIn(animationSpec = tween(240, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    scaleOut(animationSpec = tween(160, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    scaleIn(animationSpec = tween(200, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    scaleOut(animationSpec = tween(160, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
                }
            ) {
                com.gridea.android.ui.screen.log.LogManagerScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "${Screen.SettingSection.route}?section={section}",
                arguments = listOf(
                    navArgument("section") {
                        type = NavType.StringType
                        defaultValue = "general"
                    }
                ),
                // 设置详情页：scale(0.94f) + fade 组合
                // 减小 initialScale 让缩放感更柔和，缩短时长提升响应感
                enterTransition = {
                    scaleIn(animationSpec = tween(240, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    scaleOut(animationSpec = tween(160, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    scaleIn(animationSpec = tween(200, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    scaleOut(animationSpec = tween(160, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
                }
            ) { backStackEntry ->
                val section = backStackEntry.arguments?.getString("section") ?: "general"
                SettingSectionScreen(
                    section = section,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.Statistics.route,
                enterTransition = {
                    scaleIn(animationSpec = tween(240, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    scaleOut(animationSpec = tween(160, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    scaleIn(animationSpec = tween(200, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    scaleOut(animationSpec = tween(160, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
                }
            ) {
                StatisticsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.Trash.route,
                // 回收站页：scale(0.94f) + fade，与二级页面风格一致
                enterTransition = {
                    scaleIn(animationSpec = tween(240, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    scaleOut(animationSpec = tween(160, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    scaleIn(animationSpec = tween(200, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                        fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    scaleOut(animationSpec = tween(160, easing = FastOutSlowInEasing), targetScale = 0.96f) +
                        fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
                }
            ) {
                TrashScreen(onBack = { navController.popBackStack() })
            }
        }
        }

        // 动态测量导航栏圆角卡片的位置：精确计算"圆角中心点"到屏幕底部的距离
        // 用 onGloballyPositioned 拿到 Surface 的全局位置和尺寸，不依赖硬编码 NavigationBar 高度
        // 覆盖部件高度 = (圆角中心点 Y 坐标) 到 (屏幕底部 Y 坐标) 的距离
        // 圆角中心点位于卡片底边上方 28dp 处（圆角半径），即中心点 Y = 卡片底边 Y - 28dp
        var navBarCenterToBottomPx by remember { mutableStateOf(0) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        // 圆角半径 20dp（从 28dp 减小，让导航栏更紧凑现代）
        val cornerRadiusPx = with(density) { 20.dp.toPx() }

        // 导航栏可见性：仅在主 Tab（Home/Tags/Deploy/ThemeHub/Setting）显示
        // 进入二级页面（编辑/统计/友链/菜单/设置详情）时自动隐藏，回到主页再出现
        // 避免二级页面底部内容被导航栏遮挡
        val isNavBarVisible = currentDestination?.route?.let { route ->
            route in MAIN_TAB_ROUTES
        } ?: true

        // 底部不透明背景条：覆盖屏幕底部区域 + 导航栏 28dp 圆角区域外的两侧
        // 独立放在 AnimatedVisibility 外的 Box 中，避免重蹈第十四轮"两个子项垂直排列导致导航栏被挤到顶部"的坑
        // 高度由 onGloballyPositioned 动态测量得出 = 圆角中心点到屏幕底部的距离
        // 用 AnimatedVisibility 跟随导航栏可见性，离开主 Tab 时同步淡出
        AnimatedVisibility(
            visible = isNavBarVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(with(density) { navBarCenterToBottomPx.toDp() })
                    .background(MaterialTheme.colorScheme.surface)
            )
        }

        // 悬浮导航栏：放在 Box 最上层，与内容叠加
        // 圆角内显示不透明 surface 背景，圆角外完全透明让内容可见
        // 进入预览页/文章编辑页时自动隐藏（slideIn/slideOut + fade 平滑过渡），让内容占满屏幕
        // 注意：必须用 slideInVertically/slideOutVertically 而非 expandVertically/shrinkVertically
        // 后者会改变 AnimatedVisibility 容器的尺寸和位置，导致导航栏被推到屏幕顶部
        androidx.compose.animation.AnimatedVisibility(
            // AnimatedVisibility 自身定位到 Box 底部，避免默认靠顶布局把导航栏推到屏幕顶部
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = isNavBarVisible,
            enter = slideInVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(160))
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 导航栏左右/底部间距全部为 0dp，让导航栏顶到屏幕边缘
                    .padding(start = 0.dp, end = 0.dp, bottom = 0.dp)
                    // 动态测量卡片位置：positionInRoot 拿到 Surface 在父 Box（屏幕）中的 Y 坐标
                    // 父 Box 是 fillMaxSize 占据整屏，所以 父高度 - (SurfaceY + SurfaceHeight) = 卡片底边到屏幕底部距离
                    // 圆角中心点 = 卡片底边下方 28dp（圆角半径），所以中心到底部距离 = 上述 + 28dp
                    .onGloballyPositioned { coords ->
                        val rootHeight: Int = coords.parentLayoutCoordinates?.size?.height ?: 0
                        val surfaceY: Int = coords.positionInRoot().y.toInt()
                        val surfaceHeight: Int = coords.size.height
                        val surfaceBottomToScreenBottom: Int = (rootHeight - surfaceY - surfaceHeight).coerceAtLeast(0)
                        navBarCenterToBottomPx = surfaceBottomToScreenBottom + cornerRadiusPx.toInt()
                    },
                // 椭圆状大圆角（20dp），整体浮起感（从 28dp 减小）
                shape = RoundedCornerShape(20.dp),
                // 不透明背景：恢复上一次对话的导航栏样式
                color = MaterialTheme.colorScheme.surface,
                // 微弱阴影（2dp 让卡片自然浮起）
                shadowElevation = 2.dp
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0)
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                // 当前已在该 Tab 根路由（如设置入口页）时，点击不再触发导航，
                                // 避免连续点击造成页面抽动；仅在子页面（如设置详情页）时才触发返回根路由
                                val isOnTabRoot = currentDestination?.route == item.route
                                if (isOnTabRoot) return@NavigationBarItem

                                // 设置/部署/主题页不恢复状态：切走再切回时始终回到入口默认状态
                                val shouldResetState = item.route == Screen.Setting.route ||
                                    item.route == Screen.Deploy.route ||
                                    item.route == Screen.ThemeHub.route
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = !shouldResetState
                                }
                            },
                            icon = {
                                Icon(imageVector = item.icon, contentDescription = stringResource(item.label))
                            },
                            label = { Text(stringResource(item.label)) }
                        )
                    }
                }
            }
        }

        // 全局灵动岛风格悬浮通知：椭圆卡片 + 图标 + 文字
        // 动画时长缩短（进入 280ms / 退出 fadeOut 200ms + 收缩 380ms），整体更轻快
        // 通知内容变化时 visible 保持 true，文字直接更新（不重新播进入动画），实现强制刷新
        // 显示 2.2 秒后自动消失
        // Error 类型使用红色配色 + Warning 图标，Success 类型使用主题色 + CheckCircle
        AnimatedVisibility(
            visible = noticeData != null,
            enter = fadeIn(tween(280, easing = FastOutSlowInEasing)) + slideInVertically(
                initialOffsetY = { -it / 2 },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(
                animationSpec = tween(200, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))
            ) + slideOutVertically(
                targetOffsetY = { -it / 2 },
                animationSpec = tween(380, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))
            ) + shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(380, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp)
        ) {
            val isError = noticeData?.type == com.gridea.android.ui.theme.NoticeType.Error
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                contentColor = if (isError) MaterialTheme.colorScheme.onError
                               else MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 4.dp,
                tonalElevation = 2.dp,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Filled.Warning
                                      else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    // 用 AnimatedContent 平滑切换文字，避免新旧消息硬替换
                    androidx.compose.animation.AnimatedContent(
                        targetState = noticeText ?: "",
                        transitionSpec = {
                            (fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 4 }) togetherWith
                                (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 })
                        },
                        label = "noticeTextSwitch"
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
    // 闭合 Box（已在上方）
    // 闭合 CompositionLocalProvider(LocalSharedTransitionScope)
    }
    // 闭合 SharedTransitionLayout
    }
    // 闭合 CompositionLocalProvider(LocalNoticeManager)
    }
    // 闭合 GrideaAppContent 函数
}

data class BottomNavItem(
    val route: String,
    val label: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        route = Screen.Home.route,
        label = R.string.nav_articles,
        icon = Icons.AutoMirrored.Filled.Article
    ),
    BottomNavItem(
        route = Screen.Tags.route,
        label = R.string.nav_tags,
        icon = Icons.Filled.LocalOffer
    ),
    BottomNavItem(
        route = Screen.Deploy.route,
        label = R.string.nav_deploy,
        icon = Icons.Filled.CloudUpload
    ),
    BottomNavItem(
        route = Screen.ThemeHub.route,
        label = R.string.nav_theme,
        icon = Icons.Filled.Palette
    ),
    BottomNavItem(
        route = Screen.Setting.route,
        label = R.string.nav_setting,
        icon = Icons.Filled.Settings
    )
)

/**
 * 容器变换动画（Shared Element Transition）所需的两个作用域
 *
 * 通过 CompositionLocal 向子 Composable 注入：
 * - [LocalSharedTransitionScope]：来自 [SharedTransitionLayout]，提供 sharedElement modifier
 * - [LocalNavAnimatedVisibilityScope]：来自 NavHost 各 composable 的 receiver，决定动画时机
 *
 * 任一为 null 时（如预览、独立测试场景），sharedElement modifier 不生效，安全降级为普通渲染。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 给 Modifier 附加一个 sharedElement（容器变换）。
 *
 * 用法：`Modifier.sharedFabElement(key = "new_post_fab")`
 *
 * - 若 [LocalSharedTransitionScope] 或 [LocalNavAnimatedVisibilityScope] 缺失（不在 NavHost 内或未包裹
 *   SharedTransitionLayout），返回原 Modifier，不抛异常。
 * - 用于：HomeScreen 的 FAB ↔ EditorScreen（新建模式）根容器的容器变换动画。
 *
 * @param key 共享元素的唯一标识，两端必须一致
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedFabElement(key: String): Modifier {
    val sts = LocalSharedTransitionScope.current ?: return this
    val avs = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sts) {
        this@sharedFabElement.sharedElement(
            rememberSharedContentState(key = key),
            animatedVisibilityScope = avs
        )
    }
}

/**
 * 带按压反馈的 FloatingActionButton
 *
 * 点击瞬间缩放至 0.85（50ms），松手回弹至 1.0（150ms，带轻微过冲），
 * 给用户明确的"点中了"触感，掩盖界面加载的微小延迟。
 *
 * 用法与 [FloatingActionButton] 一致，只是多了按压缩放反馈。
 */
@Composable
fun PressableFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(isPressed) {
        if (isPressed) {
            // 按下：快速缩至 0.85（50ms）
            scale.animateTo(0.85f, animationSpec = tween(durationMillis = 50, easing = FastOutSlowInEasing))
        } else {
            // 松手：回弹至 1.0（150ms，带过冲）
            scale.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
        containerColor = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource
    ) {
        content()
    }
}
