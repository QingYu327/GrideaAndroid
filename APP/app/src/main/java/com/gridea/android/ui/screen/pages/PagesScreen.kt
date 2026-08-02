package com.gridea.android.ui.screen.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gridea.android.R
import com.gridea.android.ui.component.SelectionToolbarState
import com.gridea.android.ui.screen.friendlink.FriendLinkScreen
import com.gridea.android.ui.screen.menu.MenuScreen
import com.gridea.android.ui.screen.setting.RoundedTabbedPager
import com.gridea.android.ui.screen.tags.TagsScreen
import com.gridea.android.ui.theme.DangerColor
import com.gridea.android.ui.theme.LocalAccentColor

/**
 * 页面 Tab：整合标签、菜单、友链三大管理页面
 *
 * 顶部 TopAppBar 标题"页面"用于限位，同时承载批量管理操作按钮：
 * 全选 + 删除按钮平时隐藏，长按触发批量管理后出现在标题右侧区域。
 *
 * 嵌入模式下三个子页面都不显示 TopAppBar，统一由本页 TopAppBar 作为容器顶栏。
 * 子页面通过 onSelectionStateChange 回调上报选择状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesScreen(
    onPostClick: (String) -> Unit
) {
    val accentColor = LocalAccentColor.current
    val tabTitles = listOf("标签", "菜单", "友链")

    // 当前页面索引
    var currentPage by remember { mutableStateOf(0) }

    // 各子页面的选择状态
    var tagSelectionState by remember { mutableStateOf<SelectionToolbarState?>(null) }
    var menuSelectionState by remember { mutableStateOf<SelectionToolbarState?>(null) }
    var linkSelectionState by remember { mutableStateOf<SelectionToolbarState?>(null) }

    // 离开页面 Tab 再返回时重置到默认子页（标签）
    // 监听 ON_START：每次页面重新变为活跃时递增 resetTrigger，触发 RoundedTabbedPager 滚回第 0 页
    var resetTrigger by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                resetTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentSelection = when (currentPage) {
        0 -> tagSelectionState
        1 -> menuSelectionState
        2 -> linkSelectionState
        else -> null
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_tags)) },
                actions = {
                    // 批量管理操作按钮：选择模式时出现在标题右侧
                    AnimatedVisibility(
                        visible = currentSelection != null,
                        enter = fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.8f, animationSpec = tween(180, easing = FastOutSlowInEasing)),
                        exit = fadeOut(tween(140)) +
                            scaleOut(targetScale = 0.8f, animationSpec = tween(140, easing = FastOutSlowInEasing))
                    ) {
                        Row {
                            // 全选/取消全选
                            IconButton(onClick = { currentSelection?.onToggleSelectAll?.invoke() }) {
                                Icon(
                                    imageVector = Icons.Filled.LibraryAddCheck,
                                    contentDescription = stringResource(
                                        if (currentSelection?.isAllSelected == true)
                                            R.string.batch_deselect_all
                                        else R.string.batch_select_all
                                    ),
                                    tint = if (currentSelection?.isAllSelected == true) accentColor
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // 批量删除
                            IconButton(
                                onClick = { currentSelection?.onDelete?.invoke() },
                                enabled = currentSelection != null && currentSelection.selectedCount > 0
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.batch_delete),
                                    tint = if (currentSelection != null && currentSelection.selectedCount > 0)
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
        }
    ) { innerPadding ->
        RoundedTabbedPager(
            tabTitles = tabTitles,
            accentColor = accentColor,
            resetTrigger = resetTrigger,
            onPageChange = { currentPage = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> TagsScreen(
                    onPostClick = onPostClick,
                    embedded = true,
                    onSelectionStateChange = { tagSelectionState = it }
                )
                1 -> MenuScreen(
                    onSelectionStateChange = { menuSelectionState = it }
                )
                2 -> FriendLinkScreen(
                    onSelectionStateChange = { linkSelectionState = it }
                )
            }
        }
    }
}
