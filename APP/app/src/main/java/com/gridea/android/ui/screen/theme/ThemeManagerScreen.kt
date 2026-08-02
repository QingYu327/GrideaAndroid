package com.gridea.android.ui.screen.theme

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.gridea.android.R
import com.gridea.android.data.model.ThemeConfigItem
import com.gridea.android.data.model.ThemePack
import com.gridea.android.ui.theme.DangerColor
import com.gridea.android.ui.theme.LocalAccentColor
import com.gridea.android.ui.theme.LocalNoticeManager
import kotlinx.coroutines.delay

/**
 * 主题管理页面
 *
 * 展示所有主题包（内置 + 用户导入），支持主题切换、配置编辑、导入、删除
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeManagerScreen(
    onBack: () -> Unit,
    viewModel: ThemeManagerViewModel = hiltViewModel()
) {
    val themes by viewModel.themes.collectAsState()
    val activeThemeId by viewModel.activeThemeId.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val batchProgress by viewModel.batchImportProgress.collectAsState()

    val noticeManager = LocalNoticeManager.current
    val accentColor = LocalAccentColor.current

    val themeSwitchedText = stringResource(R.string.theme_switched)
    var batchMode by remember { mutableStateOf(false) }
    val batchProgressText = stringResource(R.string.theme_batch_import_progress, 0, 0)

    // 桥接导入结果到全局通知
    LaunchedEffect(importResult) {
        importResult?.let {
            noticeManager.showNotice(it)
            viewModel.clearImportResult()
        }
    }

    // 桥接批量导入进度到灵动岛
    LaunchedEffect(batchProgress) {
        batchProgress?.let { (current, total) ->
            noticeManager.showNotice(batchProgressText.format(current, total))
        }
    }

    // 多选文件导入 launcher
    val batchImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importThemes(uris)
        }
    }

    // 单选文件导入 launcher（保留给二级页面 TopAppBar）
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importTheme(it) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting_theme_manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    // 恢复内置主题按钮（批量管理模式下隐藏）
                    if (!batchMode) {
                        IconButton(onClick = { viewModel.restoreBuiltinThemes() }) {
                            Icon(
                                imageVector = Icons.Filled.Restore,
                                contentDescription = stringResource(R.string.theme_restore_builtin)
                            )
                        }
                        // 导入主题按钮
                        IconButton(onClick = { batchImportLauncher.launch("application/zip") }) {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = stringResource(R.string.theme_import)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        ThemeManagerContent(
            viewModel = viewModel,
            themes = themes,
            activeThemeId = activeThemeId,
            accentColor = accentColor,
            noticeManager = noticeManager,
            themeSwitchedText = themeSwitchedText,
            importLauncher = importLauncher,
            modifier = Modifier.padding(paddingValues),
            batchMode = batchMode,
            onBatchModeChange = { batchMode = it }
        )
    }
}

/**
 * 主题管理内容（不含 Scaffold/TopAppBar）
 *
 * 抽出为 internal 以便在「主题」页的「自定义主题」Tab 中平铺复用。
 * 包含主题卡片列表、配置 BottomSheet、删除确认对话框以及导入入口（顶部小工具栏）。
 *
 * @param importLauncher 由上层注册的文件选择 launcher；二级页面在 TopAppBar actions 触发，
 *                       Tab 平铺时通过内部工具栏触发
 * @param showImportAction 是否显示内置的导入工具按钮（Tab 平铺时为 true，二级页面已在 TopAppBar 提供故为 false）
 * @param batchMode 批量管理模式：开启后主题卡片显示复选框，底部出现批量操作栏
 * @param onBatchModeChange 批量模式开关回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeManagerContent(
    viewModel: ThemeManagerViewModel,
    themes: List<ThemePack>,
    activeThemeId: String,
    accentColor: Color,
    noticeManager: com.gridea.android.ui.theme.NoticeManager,
    themeSwitchedText: String,
    importLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Uri?>,
    modifier: Modifier = Modifier,
    showImportAction: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    batchMode: Boolean = false,
    onBatchModeChange: (Boolean) -> Unit = {}
) {
    // 配置编辑 BottomSheet 状态
    var configTheme by remember { mutableStateOf<ThemePack?>(null) }

    // 删除确认对话框状态
    var pendingDeleteTheme by remember { mutableStateOf<ThemePack?>(null) }

    // 批量选中的主题 ID 集合
    val selectedIds = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }

    // 批量删除确认对话框
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    // 批量模式下清空选中集合
    LaunchedEffect(batchMode) {
        if (!batchMode) selectedIds.clear()
    }

    // 选中/启用主题后滚动到该主题卡片；进入 Tab 时也定位到当前主题，避免每次从顶部开始
    LaunchedEffect(activeThemeId, themes) {
        if (!batchMode) {
            val index = themes.indexOfFirst { it.id == activeThemeId }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }
    }

    val selectedCount = selectedIds.values.count { it }

    // 可批量管理的主题（仅用户主题，内置主题不可选/不可删除）
    val selectableThemes = remember(themes) { themes.filter { !it.isBuiltin } }
    val allSelected = selectableThemes.isNotEmpty() && selectableThemes.all { selectedIds[it.id] == true }

    Column(modifier = modifier.fillMaxSize()) {
        // Tab 平铺模式下，提供内置导入按钮工具栏（圆角卡片风格）
        if (showImportAction) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { importLauncher.launch("*/*") },
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
        }

        // 批量模式下的信息栏：带动画过渡，避免生硬切换
        AnimatedVisibility(
            visible = batchMode,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.08f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：选中数 + 全选按钮
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.theme_batch_selected, selectedCount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        // 全选按钮：仅当选中数 > 0 时显示
                        if (selectedCount > 0) {
                            TextButton(
                                onClick = {
                                    if (allSelected) {
                                        // 全部已选 -> 取消全选
                                        selectedIds.clear()
                                    } else {
                                        // 选中所有用户主题
                                        selectableThemes.forEach { selectedIds[it.id] = true }
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SelectAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(
                                        if (allSelected) R.string.batch_deselect_all
                                        else R.string.batch_select_all
                                    ),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                    // 右侧：删除选中按钮
                    if (selectedCount > 0) {
                        TextButton(
                            onClick = { showBatchDeleteDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = DangerColor)
                        ) {
                            Text(stringResource(R.string.theme_batch_delete))
                        }
                    } else {
                        // 无选中时显示「退出」按钮
                        TextButton(
                            onClick = { onBatchModeChange(false) },
                            colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
                        ) {
                            Text(stringResource(R.string.batch_exit_selection))
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 空状态提示：主题列表为空时引导用户恢复内置主题或导入主题包
            if (themes.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.theme_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { viewModel.restoreBuiltinThemes() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.theme_restore_builtin))
                        }
                    }
                }
            }
            // 复合 key（isBuiltin + id）防御性保证唯一：即使数据层去重逻辑遗漏，
            // 内置主题与用户主题同名也不会触发 LazyColumn key 重复崩溃
            items(themes, key = { "${it.isBuiltin}_${it.id}" }) { theme ->
                ThemeCard(
                    theme = theme,
                    isActive = theme.id == activeThemeId,
                    accentColor = accentColor,
                    batchMode = batchMode,
                    isSelected = selectedIds[theme.id] == true,
                    onEnable = {
                        viewModel.enableTheme(theme.id)
                        noticeManager.showNotice(themeSwitchedText)
                    },
                    onConfig = { configTheme = theme },
                    onDelete = { pendingDeleteTheme = theme },
                    onSelect = { selected ->
                        if (selected) selectedIds[theme.id] = true
                        else selectedIds.remove(theme.id)
                    },
                    onLongPress = {
                        // 长按唤起批量管理模式（内置主题长按无效）
                        if (!theme.isBuiltin) {
                            onBatchModeChange(true)
                        }
                    }
                )
            }
            // 底部留白，避免被导航栏遮挡；批量模式多留一些给底部操作栏
            item {
                Spacer(modifier = Modifier.height(if (batchMode) 120.dp else 90.dp))
            }
        }
    }

    // 配置编辑 BottomSheet
    configTheme?.let { theme ->
        ThemeConfigSheet(
            theme = theme,
            accentColor = accentColor,
            onDismiss = { configTheme = null },
            onUpdateConfig = { key, value ->
                viewModel.updateConfig(theme.id, key, value)
            }
        )
    }

    // 删除确认对话框
    pendingDeleteTheme?.let { theme ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTheme = null },
            title = { Text(stringResource(R.string.theme_delete)) },
            text = { Text(stringResource(R.string.theme_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTheme(theme.id)
                    pendingDeleteTheme = null
                }) {
                    Text(stringResource(R.string.delete), color = DangerColor)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteTheme = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
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
            title = { Text(stringResource(R.string.theme_batch_delete)) },
            text = {
                Text(
                    stringResource(
                        R.string.theme_batch_delete_confirm,
                        selectedCount
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedIds.filter { it.value }.keys.toList()
                    viewModel.deleteThemes(ids)
                    selectedIds.clear()
                    showBatchDeleteDialog = false
                    onBatchModeChange(false)
                }) {
                    Text(stringResource(R.string.delete), color = DangerColor)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 主题卡片
 *
 * 展示预览图、名称、版本、作者、标签，以及启用/配置/删除按钮
 * 批量模式下：卡片左上角显示复选框，点击卡片切换选中状态
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThemeCard(
    theme: ThemePack,
    isActive: Boolean,
    accentColor: Color,
    batchMode: Boolean = false,
    isSelected: Boolean = false,
    onEnable: () -> Unit = {},
    onConfig: () -> Unit = {},
    onDelete: () -> Unit = {},
    onSelect: (Boolean) -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    // 内置主题在批量模式下不可选中
    val isSelectableInBatch = !theme.isBuiltin

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (batchMode && isSelectableInBatch) {
                    // 批量模式 + 可选主题：点击切换选中
                    Modifier.combinedClickable(
                        onClick = { onSelect(!isSelected) },
                        onLongClick = { onLongPress() }
                    )
                } else if (!batchMode) {
                    // 非批量模式：长按唤起批量管理
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { onLongPress() }
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected && batchMode) {
                accentColor.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surfaceVariant)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        border = if (isSelected && batchMode) {
            androidx.compose.foundation.BorderStroke(2.dp, accentColor)
        } else null
    ) {
        Column {
            // 预览图区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val imageModel = theme.previewImage?.replace("assets://", "file:///android_asset/")
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = theme.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = theme.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.theme_no_preview),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // 批量模式：左上角显示复选框（内置主题显示禁用态）
                if (batchMode) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopStart)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> accentColor
                                    !isSelectableInBatch -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                }
                            )
                            .border(
                                width = if (isSelected) 0.dp else 2.dp,
                                color = when {
                                    isSelected -> Color.Transparent
                                    !isSelectableInBatch -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    else -> accentColor
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        } else if (!isSelectableInBatch) {
                            // 内置主题显示锁定图标，提示不可选
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // 名称 + 版本 + 来源标签
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = theme.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "v${theme.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (theme.isBuiltin) stringResource(R.string.theme_builtin) else stringResource(R.string.theme_user),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (theme.isBuiltin) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = theme.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 标签
                if (theme.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        theme.tags.take(5).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = accentColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = tag,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor
                                )
                            }
                        }
                    }
                }

                // 批量模式下隐藏操作按钮，仅显示信息
                if (!batchMode) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // 操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 启用/已启用按钮
                        if (isActive) {
                            Button(
                                onClick = {},
                                enabled = false,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    disabledContainerColor = accentColor.copy(alpha = 0.6f),
                                    disabledContentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.theme_enabled))
                            }
                        } else {
                            Button(
                                onClick = onEnable,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.theme_enable))
                            }
                        }

                        // 配置按钮
                        OutlinedButton(onClick = onConfig) {
                            Text(stringResource(R.string.theme_config))
                        }

                        // 删除按钮（仅用户主题）
                        if (!theme.isBuiltin) {
                            TextButton(
                                onClick = onDelete,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = DangerColor
                                )
                            ) {
                                Text(stringResource(R.string.theme_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 主题配置编辑 BottomSheet
 *
 * 按 group 分组展示配置项，根据 type 渲染不同控件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeConfigSheet(
    theme: ThemePack,
    accentColor: Color,
    onDismiss: () -> Unit,
    onUpdateConfig: (String, Any) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    val groupedConfig = remember(theme.id) {
        theme.customConfig.groupBy { it.group.trim().ifEmpty { "其他配置" } }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = stringResource(R.string.theme_config),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = theme.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (theme.customConfig.isEmpty()) {
                Text(
                    text = "暂无配置项",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                groupedConfig.forEach { (group, items) ->
                    Text(
                        text = group,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    items.forEach { item ->
                        ConfigItemEditor(
                            item = item,
                            initialValue = theme.configValues[item.name] ?: item.value,
                            accentColor = accentColor,
                            onUpdate = { value -> onUpdateConfig(item.name, value) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * 单个配置项编辑器
 *
 * 根据 type 渲染不同控件，值变更后以 400ms 防抖调用 onUpdate
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConfigItemEditor(
    item: ThemeConfigItem,
    initialValue: Any,
    accentColor: Color,
    onUpdate: (Any) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // switch 类型在行内显示 label，其他类型在控件上方显示
        if (item.type != "switch") {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        when (item.type) {
            "switch" -> {
                val initialBool = when (initialValue) {
                    is Boolean -> initialValue
                    is String -> initialValue.toBooleanStrictOrNull()
                        ?: initialValue.equals("true", ignoreCase = true)
                    else -> false
                }
                var checked by remember(item.name) { mutableStateOf(initialBool) }
                DebouncedEffect(checked) { onUpdate(checked) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = checked,
                        onCheckedChange = { checked = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accentColor,
                            checkedBorderColor = accentColor
                        )
                    )
                }
            }

            "color" -> {
                var color by remember(item.name) { mutableStateOf(initialValue.toString()) }
                var showPicker by remember(item.name) { mutableStateOf(false) }
                DebouncedEffect(color) { onUpdate(color) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(parseColor(color))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable { showPicker = true }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = color,
                        onValueChange = { color = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                if (showPicker) {
                    ColorPickerDialog(
                        initialColor = color,
                        onConfirm = { color = it },
                        onDismiss = { showPicker = false }
                    )
                }
            }

            "select", "radio" -> {
                val initialStr = initialValue.toString()
                var selected by remember(item.name) { mutableStateOf(initialStr) }
                DebouncedEffect(selected) { onUpdate(selected) }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item.options?.forEach { option ->
                        FilterChip(
                            selected = selected == option.value,
                            onClick = { selected = option.value },
                            label = { Text(option.label) }
                        )
                    }
                }
            }

            "number" -> {
                var text by remember(item.name) { mutableStateOf(initialValue.toString()) }
                DebouncedEffect(text) { onUpdate(text) }

                OutlinedTextField(
                    value = text,
                    onValueChange = { newText -> text = newText.filter { it.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            "textarea" -> {
                var text by remember(item.name) { mutableStateOf(initialValue.toString()) }
                DebouncedEffect(text) { onUpdate(text) }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }

            "code" -> {
                // 代码编辑器：等宽字体 + 多行 + 语法高亮提示（通过 placeholder/language 提示）
                var text by remember(item.name) { mutableStateOf(initialValue.toString()) }
                DebouncedEffect(text) { onUpdate(text) }

                val languageLabel = item.language?.let { " [$it]" } ?: ""
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 12,
                    placeholder = {
                        Text(
                            text = item.placeholder ?: "// 在此填写$languageLabel 代码片段",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                )
            }

            "slider" -> {
                // 滑块控件：支持 min/max/step，value 为数值字符串
                val initialValueFloat = initialValue.toString().toFloatOrNull() ?: 0f
                val minVal = item.min?.toFloatOrNull() ?: 0f
                val maxVal = item.max?.toFloatOrNull() ?: 100f
                val stepVal = item.step?.toFloatOrNull() ?: 1f

                var sliderValue by remember(item.name) {
                    mutableStateOf(initialValueFloat.coerceIn(minVal, maxVal))
                }
                DebouncedEffect(sliderValue) { onUpdate(sliderValue.toString()) }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${minVal.toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = sliderValue.toString(),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${maxVal.toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = minVal..maxVal,
                        steps = if (stepVal > 0f) {
                            ((maxVal - minVal) / stepVal).toInt() - 1
                        } else 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            "multiselect" -> {
                // 多选 FilterChip：value 为逗号分隔字符串（如 "shadow,glass"）
                val initialSelected = initialValue.toString()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                var selected by remember(item.name) { mutableStateOf(initialSelected) }
                DebouncedEffect(selected) {
                    onUpdate(selected.joinToString(","))
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item.options?.forEach { option ->
                        FilterChip(
                            selected = selected.contains(option.value),
                            onClick = {
                                selected = if (selected.contains(option.value)) {
                                    selected - option.value
                                } else {
                                    selected + option.value
                                }
                            },
                            label = { Text(option.label) }
                        )
                    }
                }
            }

            else -> {
                // input / image / 未知类型统一用单行文本框
                var text by remember(item.name) { mutableStateOf(initialValue.toString()) }
                DebouncedEffect(text) { onUpdate(text) }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        // 提示说明
        item.note?.let { note ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 简易颜色选择对话框
 *
 * 8 个预设色板 + HEX 输入框
 */
@Composable
private fun ColorPickerDialog(
    initialColor: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var hex by remember { mutableStateOf(initialColor) }

    val presetColors = remember {
        listOf("#9C8FDA", "#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#EC4899", "#6366F1", "#6B7280")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择颜色") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(parseColor(color))
                                .border(
                                    width = if (hex.equals(color, ignoreCase = true)) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { hex = color }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = hex,
                    onValueChange = { hex = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("HEX") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hex) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 防抖 Effect
 *
 * 当 [key] 变化时跳过首次触发，延迟 [delayMs] 后执行 [onDebounce]
 * 连续变化会取消上一次的延时，实现防抖效果
 */
@Composable
private fun DebouncedEffect(
    key: Any?,
    delayMs: Long = 400L,
    onDebounce: () -> Unit
) {
    val firstTime = remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (firstTime.value) {
            firstTime.value = false
            return@LaunchedEffect
        }
        delay(delayMs)
        onDebounce()
    }
}

/**
 * 将 HEX 字符串解析为 Color
 */
private fun parseColor(hex: String): Color {
    return try {
        val cleanHex = hex.removePrefix("#")
        when (cleanHex.length) {
            6 -> Color(cleanHex.toLong(16) or 0xFF000000)
            8 -> Color(cleanHex.toLong(16))
            else -> Color.Gray
        }
    } catch (e: Exception) {
        Color.Gray
    }
}


