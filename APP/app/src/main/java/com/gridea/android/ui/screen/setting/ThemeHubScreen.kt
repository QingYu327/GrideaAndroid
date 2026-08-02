package com.gridea.android.ui.screen.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gridea.android.R
import com.gridea.android.ui.screen.theme.ThemeManagerContent
import com.gridea.android.ui.screen.theme.ThemeManagerViewModel
import com.gridea.android.ui.theme.LocalAccentColor
import com.gridea.android.ui.theme.LocalNoticeManager

/**
 * 主题页面(底部导航主 Tab)
 *
 * 用圆角卡片样式 Tab 分为两个子页面:
 * - 基础配置:站点信息的全部配置项(基本信息、站点身份、URL与路径、内容展示)
 * - 自定义主题:博客主题管理(主题包列表、切换、配置编辑、导入、删除)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeHubScreen() {
    val accentColor = LocalAccentColor.current
    val noticeManager = LocalNoticeManager.current

    // 站点信息用 SettingViewModel
    val settingViewModel: SettingViewModel = hiltViewModel()
    val theme by settingViewModel.theme.collectAsState()
    // 桥接站点信息的保存提示到灵动岛
    val savedMessage by settingViewModel.savedMessage.collectAsState()
    LaunchedEffect(savedMessage) {
        savedMessage?.let {
            noticeManager.showNotice(it)
            settingViewModel.clearMessage()
        }
    }

    // 自定义主题用 ThemeManagerViewModel
    val themeViewModel: ThemeManagerViewModel = hiltViewModel()
    val themes by themeViewModel.themes.collectAsState()
    val activeThemeId by themeViewModel.activeThemeId.collectAsState()
    val importResult by themeViewModel.importResult.collectAsState()
    val batchProgress by themeViewModel.batchImportProgress.collectAsState()
    val themeSwitchedText = stringResource(R.string.theme_switched)
    // 批量管理模式状态：在选择模式时，主题卡片显示复选框
    var batchMode by remember { mutableStateOf(false) }
    // 当前 Tab 页索引：0=基础配置, 1=自定义主题。"导入主题"按钮仅在自定义主题页显示
    var currentPage by remember { mutableStateOf(0) }
    // 批量导入进度文案
    val batchProgressText = stringResource(R.string.theme_batch_import_progress, 0, 0)
    // 桥接主题导入结果到灵动岛
    LaunchedEffect(importResult) {
        importResult?.let {
            noticeManager.showNotice(it)
            themeViewModel.clearImportResult()
        }
    }
    // 桥接批量导入进度到灵动岛
    LaunchedEffect(batchProgress) {
        batchProgress?.let { (current, total) ->
            noticeManager.showNotice(batchProgressText.format(current, total))
        }
    }
    // 多选文件导入 launcher：使用 OpenMultipleDocuments（ACTION_OPEN_DOCUMENT）
    // 比 GetMultipleContents（ACTION_GET_CONTENT）更可靠，避免部分设备结果投递 NPE
    val batchImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            themeViewModel.importThemes(uris)
        }
    }
    // 单选文件导入 launcher：保留单文件导入的快捷入口
    val singleImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { themeViewModel.importTheme(it) }
    }
    // 统一导入入口：实际使用批量 launcher
    val importLauncher = singleImportLauncher

    val tabTitles = listOf("基础配置", "自定义主题")

    // 自定义主题 Tab 的列表滚动状态：提升到此处以便在 Tab 切换时保持滚动位置
    val themeListState = rememberLazyListState()

    // 站点信息加载状态：数据未就绪前不渲染 TextField，避免 label 先空后有触发上移动画
    val isThemeLoaded by settingViewModel.isThemeLoaded.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_theme)) },
                actions = {
                    // 导入主题按钮：仅在「自定义主题」Tab 显示，基础配置 Tab 隐藏
                    // 通过 AnimatedVisibility 实现切换 Tab 时的淡入淡出过渡
                    AnimatedVisibility(
                        visible = currentPage == 1 && !batchMode,
                        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
                        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 2 })
                    ) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { batchImportLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
                                .padding(end = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = accentColor.copy(alpha = 0.12f)
                                .compositeOver(MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FileDownload,
                                    contentDescription = stringResource(R.string.theme_import),
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.theme_import),
                                    color = accentColor,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
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
                .padding(innerPadding),
            onPageChange = { currentPage = it }
        ) { page ->
            when (page) {
                0 -> {
                    // 基础配置：站点信息全部内容平铺
                    // 数据未就绪前显示加载占位符，避免 TextField label 先空后有触发上移动画
                    if (isThemeLoaded) {
                        SiteInfoContent(
                            viewModel = settingViewModel,
                            theme = theme
                        )
                    } else {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                1 -> {
                    // 自定义主题：博客主题管理全部内容平铺
                    ThemeManagerContent(
                        viewModel = themeViewModel,
                        themes = themes,
                        activeThemeId = activeThemeId,
                        accentColor = accentColor,
                        noticeManager = noticeManager,
                        themeSwitchedText = themeSwitchedText,
                        importLauncher = importLauncher,
                        showImportAction = false,
                        listState = themeListState,
                        batchMode = batchMode,
                        onBatchModeChange = { batchMode = it }
                    )
                }
            }
        }
    }
}
