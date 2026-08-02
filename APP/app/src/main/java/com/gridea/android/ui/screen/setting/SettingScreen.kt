package com.gridea.android.ui.screen.setting

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import java.io.File
import java.util.Locale
import com.gridea.android.BuildConfig
import com.gridea.android.R
import com.gridea.android.data.model.CommentPlatform
import com.gridea.android.data.model.CommentSetting
import com.gridea.android.data.model.DeployPlatform
import com.gridea.android.ui.theme.DangerColor
import com.gridea.android.ui.theme.LocalAccentColor

/** 颜色 hex 输入校验正则（文件级缓存，避免每次按键都重新编译） */
private val HEX_COLOR_REGEX = Regex("^#[0-9a-f]{0,6}$")

/**
 * 设置分类标识
 */
object SettingSections {
    const val GENERAL = "general"
    const val EDITOR = "editor"
    const val DEPLOY = "deploy"
    const val COMMENT = "comment"
    const val ACCOUNT = "account"
    const val DATA = "data"
    const val ABOUT = "about"
    const val DEBUG = "debug"
    const val UPDATE_FEEDBACK = "update_feedback"
}

/** 设置页输入框统一的圆角形状 */
internal val SettingsTextFieldShape = RoundedCornerShape(12.dp)

/** 设置页输入框统一的颜色（无边框指示器，填充背景比页面背景偏深以区分边界） */
@Composable
internal fun settingsTextFieldColors() = androidx.compose.material3.TextFieldDefaults.colors(
    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedLabelColor = LocalAccentColor.current,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = LocalAccentColor.current
)

/**
 * 设置页面（分类列表）
 *
 * 对应旧版 Gridea 0.9.3 的 src/views/setting/Index.vue
 * 重构为分类列表（主-从模式），避免单页拥挤
 *
 * 支持顶部搜索：按标题或副标题关键词过滤分类项
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    onNavigateToSection: (String) -> Unit,
    onNavigateToLogManage: () -> Unit = {},
    viewModel: SettingViewModel = hiltViewModel()
) {
    // 全局搜索范围：仅覆盖设置 Tab 自身的功能入口
    // 部署/主题为独立主 Tab，入口已在底栏，不需要再在设置搜索中重复出现
    // 调试入口：debug 版本始终显示；release 版本需在"关于"页面连续点击版本号 5 次解锁后才显示
    val debugUnlocked by viewModel.debugUnlock.collectAsState()
    val allItems = buildList {
        // ===== 设置 Tab 相关 =====
        add(SettingCategoryData(
            icon = Icons.Filled.Settings,
            title = stringResource(R.string.setting_general),
            subtitle = stringResource(R.string.setting_general_subtitle),
            onClick = { onNavigateToSection(SettingSections.GENERAL) }
        ))
        add(SettingCategoryData(
            icon = Icons.Filled.AccountCircle,
            title = stringResource(R.string.setting_account),
            subtitle = stringResource(R.string.setting_account_subtitle),
            onClick = { onNavigateToSection(SettingSections.ACCOUNT) }
        ))
        add(SettingCategoryData(
            icon = Icons.Filled.Backup,
            title = stringResource(R.string.setting_data),
            subtitle = stringResource(R.string.setting_data_subtitle),
            onClick = { onNavigateToSection(SettingSections.DATA) }
        ))
        add(SettingCategoryData(
            icon = Icons.Filled.Assessment,
            title = stringResource(R.string.setting_log_manage),
            subtitle = stringResource(R.string.setting_log_manage_subtitle),
            onClick = onNavigateToLogManage
        ))
        // 调试入口仅解锁后显示
        if (debugUnlocked) {
            add(SettingCategoryData(
                icon = Icons.Filled.BugReport,
                title = stringResource(R.string.setting_debug),
                subtitle = stringResource(R.string.setting_debug_subtitle),
                onClick = { onNavigateToSection(SettingSections.DEBUG) }
            ))
        }
        add(SettingCategoryData(
            icon = Icons.Filled.SystemUpdate,
            title = stringResource(R.string.setting_update_feedback),
            subtitle = stringResource(R.string.setting_update_feedback_subtitle),
            onClick = { onNavigateToSection(SettingSections.UPDATE_FEEDBACK) }
        ))
        add(SettingCategoryData(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.setting_about),
            subtitle = stringResource(R.string.setting_about_subtitle),
            onClick = { onNavigateToSection(SettingSections.ABOUT) }
        ))
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.setting_title))
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            allItems.forEach { item ->
                SettingCategoryItem(
                    icon = item.icon,
                    title = item.title,
                    subtitle = item.subtitle,
                    onClick = item.onClick
                )
            }
        }
    }
}

/**
 * 设置分类项数据（用于搜索过滤）
 */
private data class SettingCategoryData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
private fun SettingCategoryItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = LocalAccentColor.current
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ===== 设置详情页 =====

/**
 * 设置详情页
 * 根据 section 参数显示对应分类的内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingSectionScreen(
    section: String,
    onBack: () -> Unit,
    viewModel: SettingViewModel = hiltViewModel()
) {
    val title = when (section) {
        SettingSections.GENERAL -> stringResource(R.string.setting_general)
        SettingSections.DEPLOY -> stringResource(R.string.setting_deploy)
        SettingSections.COMMENT -> stringResource(R.string.setting_comment)
        SettingSections.ACCOUNT -> stringResource(R.string.setting_account)
        SettingSections.DATA -> stringResource(R.string.setting_data)
        SettingSections.ABOUT -> stringResource(R.string.setting_about)
        SettingSections.DEBUG -> stringResource(R.string.setting_debug)
        SettingSections.UPDATE_FEEDBACK -> stringResource(R.string.setting_update_feedback)
        else -> stringResource(R.string.setting_title)
    }

    // 监听 ViewModel 的保存提示消息：转发到全局灵动岛通知系统
    val noticeManager = com.gridea.android.ui.theme.LocalNoticeManager.current
    val savedMessage by viewModel.savedMessage.collectAsState()
    LaunchedEffect(savedMessage) {
        savedMessage?.let {
            noticeManager.showNotice(it)
            viewModel.clearMessage()
        }
    }

    // 一次性操作消息（清空输出目录、复用 OAuth Token）桥接到灵动岛
    val operationMessage by viewModel.operationMessage.collectAsState()
    LaunchedEffect(operationMessage) {
        operationMessage?.let {
            noticeManager.showNotice(it)
            viewModel.clearOperationMessage()
        }
    }

    // 登录/登出/更新账户信息消息桥接到灵动岛
    // refreshAccount 会先发"正在获取账户信息..."再发最终结果，连续 showNotice 会替换上一条
    val authMessage by viewModel.authMessage.collectAsState()
    LaunchedEffect(authMessage) {
        authMessage?.let {
            noticeManager.showNotice(it)
            viewModel.clearAuthMessage()
        }
    }

    // 备份/导入数据结果桥接到灵动岛
    val backupMessage by viewModel.backupMessage.collectAsState()
    LaunchedEffect(backupMessage) {
        backupMessage?.let { result ->
            val msg = when (result) {
                is com.gridea.android.ui.screen.setting.BackupResult.ExportSuccess -> "已导出 ${result.count} 篇文章"
                is com.gridea.android.ui.screen.setting.BackupResult.ImportSuccess -> "已导入 ${result.count} 篇文章"
                is com.gridea.android.ui.screen.setting.BackupResult.Fail -> "操作失败：${result.message}"
            }
            noticeManager.showNotice(msg)
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
    val deployResult by viewModel.deployResult.collectAsState()
    LaunchedEffect(deployResult) {
        deployResult?.let {
            val msg = if (it.success) "已发布：${it.fileCount} 个文件" else "发布失败：${it.message}"
            noticeManager.showNotice(msg)
        }
    }

    // 回滚结果消息桥接到灵动岛
    val rollbackMessage by viewModel.rollbackMessage.collectAsState()
    LaunchedEffect(rollbackMessage) {
        rollbackMessage?.let {
            noticeManager.showNotice(it)
            viewModel.clearRollbackMessage()
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (section) {
                SettingSections.GENERAL -> GeneralSection(viewModel)
                SettingSections.DEPLOY -> DeploySection(viewModel)
                SettingSections.COMMENT -> CommentSection(viewModel)
                SettingSections.ACCOUNT -> AccountSection(viewModel)
                SettingSections.DATA -> DataSection(viewModel)
                SettingSections.ABOUT -> AboutSection(viewModel)
                SettingSections.DEBUG -> if (viewModel.debugUnlock.collectAsState().value) {
                    DebugSection(viewModel)
                }
                SettingSections.UPDATE_FEEDBACK -> UpdateFeedbackSection(viewModel)
            }
        }
    }
}

// ===== 通用 Section =====

@Composable
private fun GeneralSection(viewModel: SettingViewModel) {
    val themeMode by viewModel.themeMode.collectAsState()
    val languageMode by viewModel.languageMode.collectAsState()
    val fontSizeScale by viewModel.fontSizeScale.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val appAccentColor by viewModel.appAccentColor.collectAsState()
    val wordCountGoal by viewModel.wordCountGoal.collectAsState()

    // 字体大小滑块本地缓冲：拖动时只更新本地 state，松手后才同步到 ViewModel
    // 避免拖动过程中高频 DataStore 写入导致卡顿
    var sliderValue by rememberSaveable { mutableStateOf(fontSizeScale) }
    LaunchedEffect(fontSizeScale) {
        // 外部更新时同步到本地（如初始化或恢复）
        if (kotlin.math.abs(sliderValue - fontSizeScale) > 0.01f) {
            sliderValue = fontSizeScale
        }
    }

    // 待切换的语言模式：非 null 时显示重启确认对话框
    var pendingLanguageMode by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // 1. 语言设置（首位）：圆角卡片按钮，横向铺满，文字居中，无线条框
    SettingGroupCard(title = stringResource(R.string.setting_language)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LanguageChip(stringResource(R.string.setting_language_system), "system", languageMode) {
                if (it != languageMode) pendingLanguageMode = it
            }
            LanguageChip(stringResource(R.string.setting_language_zh), "zh", languageMode) {
                if (it != languageMode) pendingLanguageMode = it
            }
            LanguageChip(stringResource(R.string.setting_language_en), "en", languageMode) {
                if (it != languageMode) pendingLanguageMode = it
            }
        }
    }

    // 2. 外观：深浅色切换同样改为圆角卡片按钮
    SettingGroupCard(title = stringResource(R.string.setting_section_appearance)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeModeChip(stringResource(R.string.setting_theme_system), "system", themeMode, viewModel::updateThemeMode)
            ThemeModeChip(stringResource(R.string.setting_theme_light), "light", themeMode, viewModel::updateThemeMode)
            ThemeModeChip(stringResource(R.string.setting_theme_dark), "dark", themeMode, viewModel::updateThemeMode)
        }
        // 动态取色开关仅 Android 12+ 显示
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setting_dynamic_color),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.setting_dynamic_color_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = dynamicColor,
                    onCheckedChange = viewModel::updateDynamicColor,
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = LocalAccentColor.current,
                        checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.5f),
                        checkedBorderColor = LocalAccentColor.current
                    )
                )
            }
            // APP 界面强调色：仅在动态取色关闭时显示（动态取色开启时跟随系统色）
            if (!dynamicColor) {
                AppAccentColorRow(
                    hexValue = appAccentColor,
                    onValueChange = viewModel::updateAppAccentColor
                )
            }
        }
    }

    // 3. 字体大小
    SettingGroupCard(title = stringResource(R.string.setting_section_font_size)) {
        Text(
            text = stringResource(R.string.setting_font_size_scale_format, (sliderValue * 100).toInt()),
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                // 松手后才同步到 ViewModel，避免拖动过程中高频 DataStore 写入
                viewModel.updateFontSizeScale(sliderValue)
            },
            valueRange = 0.85f..1.3f,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = LocalAccentColor.current,
                activeTrackColor = LocalAccentColor.current,
                activeTickColor = LocalAccentColor.current,
                inactiveTickColor = LocalAccentColor.current.copy(alpha = 0.4f)
            )
        )
        Text(
            text = stringResource(R.string.setting_font_size_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }

    // 4. 字数目标
    SettingGroupCard(title = stringResource(R.string.setting_word_count_goal)) {
        OutlinedTextField(
            value = wordCountGoal.toString(),
            onValueChange = { value ->
                // 仅接受正整数，防止非法输入
                value.toIntOrNull()?.let {
                    if (it > 0) viewModel.updateWordCountGoal(it)
                }
            },
            label = { Text(stringResource(R.string.setting_word_count_goal)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        Text(
            text = stringResource(R.string.setting_word_count_goal_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 语言切换重启确认对话框
    pendingLanguageMode?.let { mode ->
        AlertDialog(
            onDismissRequest = { pendingLanguageMode = null },
            title = { Text(stringResource(R.string.language_restart_title)) },
            text = { Text(stringResource(R.string.language_restart_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        // 同步保存语言设置（StateFlow + SharedPreferences commit 已同步完成）
                        viewModel.updateLanguageMode(mode)
                        pendingLanguageMode = null
                        // 直接重启 Activity，确保 attachBaseContext 重新读取语言设置
                        val intent = Intent(context, com.gridea.android.MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        (context as? Activity)?.finish()
                        @Suppress("DEPRECATION")
                        (context as? Activity)?.overridePendingTransition(
                            com.gridea.android.R.anim.fade_in,
                            com.gridea.android.R.anim.fade_out
                        )
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.language_restart_button))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingLanguageMode = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ===== 部署 Section =====

@Composable
internal fun DeploySection(
    viewModel: SettingViewModel,
    onNavigateToPreview: () -> Unit = {}
) {
    val setting by viewModel.setting.collectAsState()
    val isDeploying by viewModel.isDeploying.collectAsState()
    val isDetecting by viewModel.isDetecting.collectAsState()
    val isRendering by viewModel.isRendering.collectAsState()
    val deployProgress by viewModel.deployProgress.collectAsState()
    val deployResult by viewModel.deployResult.collectAsState()
    val detectResult by viewModel.detectResult.collectAsState()
    val accentColor = LocalAccentColor.current

    // 5 个平台横向滚动 + 点击定位：每个 Chip 用 onGloballyPositioned 记录位置，
    // 点击后 animateScrollTo 让该 Chip 居中显示
    val platformScrollState = rememberScrollState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val platformChipPositions = remember { mutableMapOf<String, Int>() }
    val platformList = listOf(
        Triple("GitHub", Icons.Filled.Code, DeployPlatform.GITHUB),
        Triple("Netlify", Icons.Filled.Cloud, DeployPlatform.NETLIFY),
        Triple("Vercel", Icons.Filled.Bolt, DeployPlatform.VERCEL),
        Triple("Gitee", Icons.Filled.Build, DeployPlatform.GITEE),
        Triple("SFTP", Icons.Filled.Dns, DeployPlatform.SFTP)
    )

    // ===== 1. 静态渲染（生成渲染 + 预览）=====
    SettingGroupCard(title = "静态渲染") {
        // 文字说明指引
        Text(
            text = "将文章和配置渲染为静态 HTML 文件，生成后可预览效果或发布到部署平台。" +
                "生成前会自动清除上一次的缓存，确保文件不会冲突。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 生成渲染（圆角卡片风格）
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !isRendering) { viewModel.renderSite() },
                shape = RoundedCornerShape(12.dp),
                color = if (!isRendering) accentColor
                    else accentColor.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    if (isRendering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AutoFixHigh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "生成渲染",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            // 预览（圆角卡片风格）
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !isRendering) { onNavigateToPreview() },
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.12f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Preview,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.preview),
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // ===== 2. 部署平台配置（平台选择 + 域名 + 检测连接 + 发布）=====
    SettingGroupCard(title = stringResource(R.string.setting_section_platform)) {
        // 5 个平台横向滑动切换（点击后自动定位）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(platformScrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            platformList.forEach { (label, icon, platform) ->
                PlatformChip(
                    label = label,
                    icon = icon,
                    value = platform,
                    currentValue = setting.platform,
                    onSelect = { selected ->
                        viewModel.updatePlatform(selected)
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(50)
                            val chipX = platformChipPositions[selected] ?: return@launch
                            val viewportWidth = platformScrollState.viewportSize
                            val targetScroll = (chipX - viewportWidth / 2).coerceAtLeast(0)
                            platformScrollState.animateScrollTo(targetScroll)
                        }
                    },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        platformChipPositions[platform] = coords.positionInParent().x.toInt()
                    }
                )
            }
        }
        OutlinedTextField(
            value = setting.domain,
            onValueChange = viewModel::updateDomain,
            label = { Text(stringResource(R.string.setting_site_domain)) },
            placeholder = { Text("https://example.com") },
            supportingText = { Text(stringResource(R.string.setting_site_domain_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        // 检测结果
        detectResult?.let { result ->
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (result.success) accentColor else DangerColor
            )
        }
        // 部署进度
        deployProgress?.let { progress ->
            Text(
                text = stringResource(
                    R.string.setting_uploading_format,
                    progress.current, progress.total, progress.fileName
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        // 检测连接 + 发布站点（圆角卡片风格，文字左侧带图标）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 检测连接
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !isDetecting) { viewModel.detectDeploy() },
                shape = RoundedCornerShape(12.dp),
                color = if (!isDetecting) accentColor.copy(alpha = 0.12f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    if (isDetecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = accentColor
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.NetworkCheck,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "检测连接",
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            // 发布站点
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !isDeploying) { viewModel.publishSite() },
                shape = RoundedCornerShape(12.dp),
                color = if (!isDeploying) accentColor
                    else accentColor.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    if (isDeploying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CloudUpload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "发布站点",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // ===== 3. 平台特定配置 =====
    when (setting.platform) {
        DeployPlatform.SFTP -> SftpConfig(setting, viewModel)
        DeployPlatform.NETLIFY -> NetlifyConfig(setting, viewModel)
        DeployPlatform.VERCEL -> VercelConfig(setting, viewModel)
        DeployPlatform.GITEE -> GiteeConfig(setting, viewModel)
        else -> GitConfig(setting, viewModel)
    }

    // ===== 4. 部署结果对话框 =====
    deployResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearDeployResult() },
            title = { Text(if (result.success) stringResource(R.string.setting_deploy_success) else stringResource(R.string.setting_deploy_fail)) },
            text = { Text(result.message) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearDeployResult() },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = accentColor)
                ) { Text(stringResource(R.string.confirm)) }
            }
        )
    }

    // ===== 5. 部署历史与回滚 =====
    DeployHistorySection(viewModel)
}

/**
 * 部署历史与回滚区域
 *
 * 展示最近的部署记录列表（最多 20 条），每条可展开查看详情（消息、URL、文件清单）。
 * 支持长按多选批量删除与一键清空。
 * 若存在上次成功部署记录，提供"回滚上次部署"入口；回滚为简化实现，仅在确认对话框中
 * 展示上次部署的文件清单并提示用户在对应平台管理页面手动删除。
 */
@Composable
private fun DeployHistorySection(viewModel: SettingViewModel) {
    val history by viewModel.deployHistory.collectAsState()
    val isRollingBack by viewModel.isRollingBack.collectAsState()
    val accentColor = LocalAccentColor.current

    // 清空历史确认弹窗
    var showClearHistoryConfirm by rememberSaveable { mutableStateOf(false) }
    // 选中的回滚版本（点击"回滚到此版本"后设置，触发确认弹窗）
    var rollbackTarget by remember { mutableStateOf<com.gridea.android.data.model.DeployRecord?>(null) }

    // ===== 批量选择状态 =====
    // 选中记录的 ID 集合；非空时进入选择模式
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    // 批量删除确认弹窗
    var showBatchDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    // 历史列表为空时退出选择模式，避免悬浮选中态
    LaunchedEffect(history.isEmpty()) {
        if (history.isEmpty()) selectedIds = emptySet()
    }

    // 历史收纳：默认仅显示最新 5 条，展开后显示全部
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    val COLLAPSED_HISTORY_COUNT = 5
    val displayHistory = if (historyExpanded || isSelectionMode) history
                        else history.take(COLLAPSED_HISTORY_COUNT)

    SettingGroupCard(title = "部署历史") {
        if (history.isEmpty()) {
            Text(
                text = "暂无部署历史记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // 选择模式下的顶部操作栏：全选/取消全选 + 选中计数 + 批量删除 + 退出
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(180))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.08f).compositeOver(MaterialTheme.colorScheme.surface))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val allSelected = selectedIds.size == history.size
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedIds = if (allSelected) emptySet()
                                    else history.map { it.id }.toSet()
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.12f)
                            .compositeOver(MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (allSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (allSelected) "取消全选" else "全选",
                                style = MaterialTheme.typography.labelMedium,
                                color = accentColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Text(
                        text = "已选 ${selectedIds.size}/${history.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    // 批量删除按钮（警示色）
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showBatchDeleteConfirm = true },
                        shape = RoundedCornerShape(8.dp),
                        color = DangerColor.copy(alpha = 0.12f)
                            .compositeOver(MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                tint = DangerColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "删除",
                                style = MaterialTheme.typography.labelMedium,
                                color = DangerColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    IconButton(
                        onClick = { selectedIds = emptySet() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "退出选择",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            displayHistory.forEach { record ->
                DeployHistoryItem(
                    record = record,
                    isSelectionMode = isSelectionMode,
                    isSelected = record.id in selectedIds,
                    onSelectionToggle = {
                        selectedIds = if (record.id in selectedIds) {
                            selectedIds - record.id
                        } else {
                            selectedIds + record.id
                        }
                    },
                    onLongClick = {
                        if (record.id !in selectedIds) {
                            selectedIds = selectedIds + record.id
                        }
                    },
                    onRollback = if (record.success && !isSelectionMode) {
                        { rollbackTarget = record }
                    } else null
                )
            }

            // 展开/收起按钮：超过 5 条时显示
            if (history.size > COLLAPSED_HISTORY_COUNT && !isSelectionMode) {
                androidx.compose.material3.TextButton(
                    onClick = { historyExpanded = !historyExpanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (historyExpanded) Icons.Filled.KeyboardArrowUp
                            else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (historyExpanded) "收起历史"
                               else "展开全部历史（${history.size - COLLAPSED_HISTORY_COUNT} 条）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 底部按钮区：一键清空（非选择模式下显示）
        if (history.isNotEmpty() && !isSelectionMode) {
            OutlinedButton(
                onClick = { showClearHistoryConfirm = true },
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = DangerColor
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("一键清空") }
        }
    }

    // 一键清空确认弹窗
    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("清空部署历史") },
            text = { Text("确定要清空所有部署历史记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearDeployHistory()
                        showClearHistoryConfirm = false
                    }
                ) {
                    Text(
                        "清空",
                        color = DangerColor
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearHistoryConfirm = false },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // 批量删除确认弹窗
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("批量删除部署历史") },
            text = { Text("确定要删除选中的 ${selectedIds.size} 条部署历史记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDeployRecords(selectedIds)
                        selectedIds = emptySet()
                        showBatchDeleteConfirm = false
                    }
                ) {
                    Text("删除", color = DangerColor)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchDeleteConfirm = false },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // 回滚确认弹窗：展示选中版本的文件清单和提示
    rollbackTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { rollbackTarget = null },
            title = { Text("回滚到选定版本") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dateFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
                    Text(
                        text = "回滚功能仅支持 Git 类平台（GitHub/Gitee）。如需回滚，请在对应平台管理页面手动删除以下文件：",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "部署时间：${dateFormat.format(java.util.Date(target.timestamp))}\n" +
                            "部署平台：${target.platform}（${target.fileCount} 个文件）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 文件清单（限制高度，可滚动）
                    val manifest = target.fileManifest
                    if (manifest.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            manifest.forEach { path ->
                                Text(
                                    text = path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.rollbackLastDeploy()
                        rollbackTarget = null
                    },
                    enabled = !isRollingBack,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    if (isRollingBack) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("我已知晓")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { rollbackTarget = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/**
 * 单条部署历史记录项
 *
 * 默认折叠，点击展开显示消息、URL（可点击跳转）和文件清单。
 * 成功记录在展开时显示"回滚到此版本"按钮。
 *
 * 选择模式下：
 * - 长按任意记录进入选择模式并选中该条
 * - 点击记录切换选中态（不再展开）
 * - 卡片显示选中指示器（圆点/勾选）和强调色边框
 * - 隐藏展开图标与回滚按钮
 *
 * @param isSelectionMode 是否处于批量选择模式
 * @param isSelected 当前记录是否被选中
 * @param onSelectionToggle 点击切换选中态回调（选择模式下触发）
 * @param onLongClick 长按回调（进入选择模式）
 * @param onRollback 回滚回调，仅成功记录且非选择模式传入（失败记录为 null 不显示按钮）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeployHistoryItem(
    record: com.gridea.android.data.model.DeployRecord,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onSelectionToggle: () -> Unit,
    onLongClick: () -> Unit,
    onRollback: (() -> Unit)? = null
) {
    var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val dateFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val accentColor = LocalAccentColor.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onSelectionToggle()
                    else expanded = !expanded
                },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) accentColor.copy(alpha = 0.08f)
                .compositeOver(MaterialTheme.colorScheme.surfaceVariant)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, accentColor) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 72.dp)
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 第一行：选择指示器（选择模式） + 时间 + 平台 + 状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 选择模式下显示选中指示器
                if (isSelectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle
                            else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) accentColor
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = dateFormat.format(java.util.Date(record.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = record.platform,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (record.success) "成功" else "失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (record.success) accentColor
                        else DangerColor,
                    fontWeight = FontWeight.Bold
                )
            }
            // 第二行：文件数 + 展开切换 + 回滚按钮（仅成功记录且非选择模式）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isSelectionMode) { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "文件数：${record.fileCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    // 选择模式下隐藏展开图标，避免与选中态语义冲突
                    if (!isSelectionMode) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.Close
                                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 回滚到此版本按钮（仅成功记录 + 非选择模式显示）
                if (onRollback != null) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onRollback),
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.12f)
                            .compositeOver(MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Backup,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "回滚到此版本",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            // 展开内容：消息、URL、文件清单
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(200))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "消息：${record.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    record.url?.takeIf { it.isNotBlank() }?.let { url ->
                        Text(
                            text = "访问地址：$url",
                            style = MaterialTheme.typography.bodySmall,
                            color = accentColor,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(url)
                                        )
                                    )
                                }
                            }
                        )
                    }
                    if (record.fileManifest.isNotEmpty()) {
                        Text(
                            text = "文件清单（${record.fileManifest.size}）：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            record.fileManifest.forEach { path ->
                                Text(
                                    text = path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== 评论 Section =====

@Composable
internal fun CommentSection(viewModel: SettingViewModel) {
    val commentSetting by viewModel.commentSetting.collectAsState()

    SettingGroupCard(title = stringResource(R.string.setting_comment)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.setting_show_comment), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(
                checked = commentSetting.showComment,
                onCheckedChange = viewModel::updateShowComment,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = LocalAccentColor.current,
                    checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.5f),
                    checkedBorderColor = LocalAccentColor.current
                )
            )
        }
        // 评论平台选择器：Gitalk / Giscus / Disqus / Valine / Twikoo / Waline（3+3 排列）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlatformChip("Gitalk", Icons.AutoMirrored.Filled.Comment, CommentPlatform.GITALK, commentSetting.commentPlatform, viewModel::updateCommentPlatform, Modifier.weight(1f))
            PlatformChip("Giscus", Icons.AutoMirrored.Filled.Comment, CommentPlatform.GISCUS, commentSetting.commentPlatform, viewModel::updateCommentPlatform, Modifier.weight(1f))
            PlatformChip("Disqus", Icons.AutoMirrored.Filled.Comment, CommentPlatform.DISQUS, commentSetting.commentPlatform, viewModel::updateCommentPlatform, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlatformChip("Valine", Icons.AutoMirrored.Filled.Comment, CommentPlatform.VALINE, commentSetting.commentPlatform, viewModel::updateCommentPlatform, Modifier.weight(1f))
            PlatformChip("Twikoo", Icons.AutoMirrored.Filled.Comment, CommentPlatform.TWIKOO, commentSetting.commentPlatform, viewModel::updateCommentPlatform, Modifier.weight(1f))
            PlatformChip("Waline", Icons.AutoMirrored.Filled.Comment, CommentPlatform.WALINE, commentSetting.commentPlatform, viewModel::updateCommentPlatform, Modifier.weight(1f))
        }
    }

    // 根据选择的平台显示对应的配置表单
    when (commentSetting.commentPlatform) {
        CommentPlatform.GITALK -> GitalkConfigForm(commentSetting, viewModel)
        CommentPlatform.GISCUS -> GiscusConfigForm(commentSetting, viewModel)
        CommentPlatform.DISQUS -> DisqusConfigForm(commentSetting, viewModel)
        CommentPlatform.VALINE -> ValineConfigForm(commentSetting, viewModel)
        CommentPlatform.TWIKOO -> TwikooConfigForm(commentSetting, viewModel)
        CommentPlatform.WALINE -> WalineConfigForm(commentSetting, viewModel)
    }
}

@Composable
private fun GitalkConfigForm(commentSetting: CommentSetting, viewModel: SettingViewModel) {
    SettingGroupCard(title = stringResource(R.string.setting_gitalk_config)) {
        OutlinedTextField(
            value = commentSetting.gitalkSetting.clientId,
            onValueChange = viewModel::updateGitalkClientId,
            label = { Text(stringResource(R.string.setting_gitalk_client_id)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = commentSetting.gitalkSetting.clientSecret,
            onValueChange = viewModel::updateGitalkClientSecret,
            label = { Text(stringResource(R.string.setting_gitalk_client_secret)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = commentSetting.gitalkSetting.repository,
            onValueChange = viewModel::updateGitalkRepo,
            label = { Text(stringResource(R.string.setting_gitalk_repo)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = commentSetting.gitalkSetting.owner,
            onValueChange = viewModel::updateGitalkOwner,
            label = { Text(stringResource(R.string.setting_gitalk_owner)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
    }
}

@Composable
private fun GiscusConfigForm(commentSetting: CommentSetting, viewModel: SettingViewModel) {
    SettingGroupCard(title = stringResource(R.string.setting_giscus_config)) {
        OutlinedTextField(
            value = commentSetting.giscusSetting.repo,
            onValueChange = viewModel::updateGiscusRepo,
            label = { Text(stringResource(R.string.setting_giscus_repo)) },
            placeholder = { Text("owner/repo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = commentSetting.giscusSetting.repoId,
            onValueChange = viewModel::updateGiscusRepoId,
            label = { Text(stringResource(R.string.setting_giscus_repo_id)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = commentSetting.giscusSetting.category,
            onValueChange = viewModel::updateGiscusCategory,
            label = { Text(stringResource(R.string.setting_giscus_category)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = commentSetting.giscusSetting.categoryId,
            onValueChange = viewModel::updateGiscusCategoryId,
            label = { Text(stringResource(R.string.setting_giscus_category_id)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = commentSetting.giscusSetting.mapping,
            onValueChange = viewModel::updateGiscusMapping,
            label = { Text(stringResource(R.string.setting_giscus_mapping)) },
            placeholder = { Text("pathname") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = commentSetting.giscusSetting.theme,
            onValueChange = viewModel::updateGiscusTheme,
            label = { Text(stringResource(R.string.setting_giscus_theme)) },
            placeholder = { Text("light") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
    }
}

@Composable
private fun DisqusConfigForm(commentSetting: CommentSetting, viewModel: SettingViewModel) {
    SettingGroupCard(title = stringResource(R.string.setting_disqus_config)) {
        OutlinedTextField(
            value = commentSetting.disqusSetting.shortname,
            onValueChange = viewModel::updateDisqusShortname,
            label = { Text(stringResource(R.string.setting_disqus_shortname)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = commentSetting.disqusSetting.apikey,
            onValueChange = viewModel::updateDisqusApikey,
            label = { Text(stringResource(R.string.setting_disqus_apikey)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = commentSetting.disqusSetting.api,
            onValueChange = viewModel::updateDisqusApi,
            label = { Text(stringResource(R.string.setting_disqus_api)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
    }
}

@Composable
private fun ValineConfigForm(commentSetting: CommentSetting, viewModel: SettingViewModel) {
    SettingGroupCard(title = stringResource(R.string.setting_valine_config)) {
        OutlinedTextField(
            value = commentSetting.valineSetting.appId,
            onValueChange = viewModel::updateValineAppId,
            label = { Text(stringResource(R.string.setting_valine_app_id)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = commentSetting.valineSetting.appKey,
            onValueChange = viewModel::updateValineAppKey,
            label = { Text(stringResource(R.string.setting_valine_app_key)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
    }
}

@Composable
private fun TwikooConfigForm(commentSetting: CommentSetting, viewModel: SettingViewModel) {
    SettingGroupCard(title = stringResource(R.string.setting_twikoo_config)) {
        OutlinedTextField(
            value = commentSetting.twikooSetting.envId,
            onValueChange = viewModel::updateTwikooEnvId,
            label = { Text(stringResource(R.string.setting_twikoo_env_id)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
    }
}

@Composable
private fun WalineConfigForm(commentSetting: CommentSetting, viewModel: SettingViewModel) {
    SettingGroupCard(title = stringResource(R.string.setting_waline_config)) {
        OutlinedTextField(
            value = commentSetting.walineSetting.serverURL,
            onValueChange = viewModel::updateWalineServerURL,
            label = { Text(stringResource(R.string.setting_waline_server_url)) },
            placeholder = { Text("https://your-waline.vercel.app") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
    }
}

// ===== 账户 Section =====

@Composable
private fun AccountSection(viewModel: SettingViewModel) {
    val account by viewModel.account.collectAsState()
    val oauthClientId by viewModel.oauthClientId.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val deviceCode by viewModel.deviceCode.collectAsState()
    val context = LocalContext.current
    // 退出登录确认弹窗状态
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (account.isLoggedIn) {
        SettingGroupCard(title = stringResource(R.string.setting_current_account)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (account.avatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = account.avatarUrl,
                        contentDescription = "头像",
                        modifier = Modifier.size(56.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    // 兼容旧数据：JSON null 被存为字符串 "null"，此时回退到 login
                    val displayName = account.name
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?: account.login
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@${account.login}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            if (account.htmlUrl.isNotEmpty()) {
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(account.htmlUrl)
                                )
                            )
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = LocalAccentColor.current
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.setting_view_github)) }
            }

            // 更新账户信息：圆角卡片风格，放在退出登录上方
            // GitHub /user 端点在登录时拉取一次后不会自动更新，用户新建/删除仓库后需手动更新
            // 文字居中 + 高度与退出登录按钮（Button 默认 36dp）对齐
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !isLoggingIn) { viewModel.refreshAccount() },
                shape = RoundedCornerShape(12.dp),
                color = LocalAccentColor.current.copy(alpha = 0.12f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = LocalAccentColor.current,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "更新账户信息",
                        color = LocalAccentColor.current,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 退出登录：浅红色警示色圆角按钮，放在更新账户信息下方
            Button(
                onClick = { showLogoutDialog = true },
                enabled = !isLoggingIn,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = com.gridea.android.ui.theme.DangerColor.copy(alpha = 0.12f),
                    contentColor = com.gridea.android.ui.theme.DangerColor
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.setting_logout)) }
        }

        // GitHub 账户详情分组：统计数据 + 个人信息（按需展示，空值不展示）
        SettingGroupCard(title = "GitHub 账户详情") {
            // 统计数据三列：仓库数 / 粉丝数 / 关注数
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = account.totalRepos.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = LocalAccentColor.current
                    )
                    Text(
                        text = "仓库",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = account.followers.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = LocalAccentColor.current
                    )
                    Text(
                        text = "粉丝",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = account.following.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = LocalAccentColor.current
                    )
                    Text(
                        text = "关注",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 个人信息字段（按需展示，空值或 "null" 字符串不展示）
            fun isNotBlankValue(s: String) = s.isNotBlank() && s != "null"

            if (isNotBlankValue(account.bio)) {
                AccountInfoRow(label = "简介", value = account.bio)
            }
            if (isNotBlankValue(account.company)) {
                AccountInfoRow(label = "公司", value = account.company)
            }
            if (isNotBlankValue(account.location)) {
                AccountInfoRow(label = "所在地", value = account.location)
            }
            if (isNotBlankValue(account.email)) {
                AccountInfoRow(label = "邮箱", value = account.email)
            }
            if (isNotBlankValue(account.blog)) {
                AccountInfoRow(label = "网站", value = account.blog)
            }
            if (isNotBlankValue(account.createdAt)) {
                // ISO 8601 格式：2011-01-25T18:44:36Z，截取日期部分展示
                val createdDate = account.createdAt.substringBefore('T').ifBlank { account.createdAt }
                AccountInfoRow(label = "注册时间", value = createdDate)
            }
        }
    } else {
        SettingGroupCard(title = stringResource(R.string.setting_oauth_config)) {
            Text(
                text = stringResource(R.string.setting_oauth_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            OutlinedTextField(
                value = oauthClientId,
                onValueChange = viewModel::updateOAuthClientId,
                label = { Text(stringResource(R.string.setting_oauth_client_id)) },
                placeholder = { Text(stringResource(R.string.setting_oauth_client_id_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = SettingsTextFieldShape,
                colors = settingsTextFieldColors()
            )

            if (isLoggingIn && deviceCode != null) {
                Text(
                    text = stringResource(R.string.setting_auth_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
                val dc = deviceCode!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = dc.userCode,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = dc.verificationUri,
                            style = MaterialTheme.typography.bodyLarge,
                            color = LocalAccentColor.current,
                            modifier = Modifier.clickable {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(dc.verificationUri)
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.setting_waiting_browser), style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = { viewModel.cancelLogin() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = LocalAccentColor.current
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.cancel)) }
            } else {
                Button(
                    onClick = { viewModel.startLogin() },
                    enabled = !isLoggingIn && oauthClientId.isNotBlank(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Text("  ${stringResource(R.string.setting_requesting)}")
                    } else {
                        Text(stringResource(R.string.setting_login_github))
                    }
                }
                if (oauthClientId.isBlank()) {
                    Text(
                        text = stringResource(R.string.setting_need_client_id),
                        style = MaterialTheme.typography.bodySmall,
                        color = com.gridea.android.ui.theme.DangerColor
                    )
                }
            }
        }
    }

    // 退出登录确认弹窗：防止误触，明确告知用户后果
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("退出登录后将清除本地保存的 GitHub 账户信息（Token、用户资料等），需要重新登录才能再次使用部署功能。确定要退出吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logout()
                        showLogoutDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = com.gridea.android.ui.theme.DangerColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("确认退出") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = LocalAccentColor.current
                    )
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/**
 * 账户信息行：左侧标签 + 右侧值，用于展示 GitHub 账户的 bio/company/location 等字段
 */
@Composable
private fun AccountInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp).weight(1f, fill = false)
        )
    }
}

// ===== 数据 Section =====

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DataSection(
    viewModel: SettingViewModel
) {
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val backupMessage by viewModel.backupMessage.collectAsState()
    val hasPermission by viewModel.hasStoragePermission.collectAsState()
    val existingFileCount by viewModel.existingFileCount.collectAsState()
    val existingTotalSize by viewModel.existingTotalSize.collectAsState()

    // 清空目录操作的确认弹窗
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.setting_clear_output_confirm_title)) },
            text = { Text(stringResource(R.string.setting_clear_output_confirm_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearOutputFiles()
                        showClearConfirm = false
                    }
                ) {
                    Text(
                        stringResource(R.string.setting_clear_output),
                        color = com.gridea.android.ui.theme.DangerColor
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirm = false },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) viewModel.exportData(uri) }

    // 使用 GetContent("*/*") 替代 OpenDocument，兼容小米/澎湃OS文件选择器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.importData(uri) }

    // 用于从系统权限设置页返回时刷新权限状态
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { viewModel.onPermissionResult() }

    SettingGroupCard(title = stringResource(R.string.setting_section_output)) {
        // 显示固定输出路径
        Text(
            text = stringResource(R.string.setting_current_path, viewModel.outputDisplayPath),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )

        // 权限状态与授权按钮
        if (!hasPermission) {
            Text(
                text = stringResource(R.string.setting_output_no_permission),
                style = MaterialTheme.typography.bodySmall,
                color = com.gridea.android.ui.theme.DangerColor,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                    } else {
                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:${context.packageName}"))
                    }
                    permissionLauncher.launch(intent)
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.setting_grant_permission)) }
        } else {
            // 已授权，显示扫描结果
            existingFileCount?.let { count ->
                val sizeText = formatFileSize(existingTotalSize ?: 0L)
                Text(
                    text = stringResource(R.string.setting_existing_files, count, sizeText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.scanOutputFiles() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current, contentColor = Color.White),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.setting_scan_files)) }
                if (existingFileCount != null && existingFileCount!! > 0) {
                    Button(
                        onClick = { showClearConfirm = true },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = com.gridea.android.ui.theme.DangerColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.setting_clear_output)) }
                }
            }
        }
    }

    // ===== 数据备份（仿站点输出目录格式 + 批量管理）=====
    val scannedBackups by viewModel.scannedBackups.collectAsState()
    val isScanningBackups by viewModel.isScanningBackups.collectAsState()
    var pendingImportPath by remember { mutableStateOf<String?>(null) }

    // 批量选择状态
    var selectedBackupPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isBackupSelectionMode = selectedBackupPaths.isNotEmpty()
    var showBatchDeleteBackupsConfirm by rememberSaveable { mutableStateOf(false) }
    var showClearAllBackupsConfirm by rememberSaveable { mutableStateOf(false) }
    // 列表为空时退出选择模式
    LaunchedEffect(scannedBackups.isEmpty()) {
        if (scannedBackups.isEmpty()) selectedBackupPaths = emptySet()
    }

    val backupTotalSize = scannedBackups.sumOf { it.size }

    SettingGroupCard(title = stringResource(R.string.setting_section_backup)) {
        // 显示固定备份目录路径
        Text(
            text = stringResource(R.string.setting_current_path, viewModel.backupDisplayPath),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )

        // 已扫描文件统计
        if (scannedBackups.isNotEmpty()) {
            Text(
                text = stringResource(R.string.setting_existing_files, scannedBackups.size, formatFileSize(backupTotalSize)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 导出/导入按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val fileName = "gridea-backup-${System.currentTimeMillis()}.zip"
                    exportLauncher.launch(fileName)
                },
                enabled = !isBackingUp,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current, contentColor = Color.White),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isBackingUp) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.setting_export_data))
                }
            }
            Button(
                onClick = { importLauncher.launch("*/*") },
                enabled = !isBackingUp,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = LocalAccentColor.current
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.setting_import_data)) }
        }

        // 选择模式下的顶部操作栏：全选/取消全选 + 选中计数 + 批量删除 + 退出
        AnimatedVisibility(
            visible = isBackupSelectionMode,
            enter = slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(220)),
            exit = slideOutVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(180))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LocalAccentColor.current.copy(alpha = 0.08f).compositeOver(MaterialTheme.colorScheme.surface))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allBackupSelected = selectedBackupPaths.size == scannedBackups.size
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            selectedBackupPaths = if (allBackupSelected) emptySet()
                                else scannedBackups.map { it.absolutePath }.toSet()
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = LocalAccentColor.current.copy(alpha = 0.12f)
                        .compositeOver(MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (allBackupSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                            contentDescription = null,
                            tint = LocalAccentColor.current,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (allBackupSelected) "取消全选" else "全选",
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalAccentColor.current,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Text(
                    text = "已选 ${selectedBackupPaths.size}/${scannedBackups.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showBatchDeleteBackupsConfirm = true },
                    shape = RoundedCornerShape(8.dp),
                    color = DangerColor.copy(alpha = 0.12f)
                        .compositeOver(MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = DangerColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "删除",
                            style = MaterialTheme.typography.labelMedium,
                            color = DangerColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                IconButton(
                    onClick = { selectedBackupPaths = emptySet() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "退出选择",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 扫描备份按钮（非选择模式下显示）
        if (!isBackupSelectionMode) {
            Button(
                onClick = { viewModel.scanBackups() },
                enabled = !isScanningBackups,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = LocalAccentColor.current
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isScanningBackups) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("扫描备份")
                }
            }
        }

        // 扫描到的备份列表（长按进入选择模式，点击导入或切换选中）
        if (scannedBackups.isNotEmpty() && !isBackupSelectionMode) {
            Text(
                text = "共扫描到 ${scannedBackups.size} 个备份文件（点击导入，长按多选）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        scannedBackups.forEach { backup ->
            val isSelected = backup.absolutePath in selectedBackupPaths
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .combinedClickable(
                        onClick = {
                            if (isBackupSelectionMode) {
                                selectedBackupPaths = if (backup.absolutePath in selectedBackupPaths) {
                                    selectedBackupPaths - backup.absolutePath
                                } else {
                                    selectedBackupPaths + backup.absolutePath
                                }
                            } else {
                                pendingImportPath = backup.absolutePath
                            }
                        },
                        onLongClick = {
                            if (backup.absolutePath !in selectedBackupPaths) {
                                selectedBackupPaths = selectedBackupPaths + backup.absolutePath
                            }
                        }
                    ),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) LocalAccentColor.current.copy(alpha = 0.08f)
                        .compositeOver(MaterialTheme.colorScheme.surfaceVariant)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, LocalAccentColor.current) else null,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isBackupSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle
                                else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) LocalAccentColor.current
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = LocalAccentColor.current
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = backup.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatFileSize(backup.size)} · ${
                                java.text.SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm", java.util.Locale.getDefault()
                                ).format(java.util.Date(backup.lastModified))
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 底部一键清空（非选择模式 + 有文件时显示）
        if (scannedBackups.isNotEmpty() && !isBackupSelectionMode) {
            OutlinedButton(
                onClick = { showClearAllBackupsConfirm = true },
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = DangerColor
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("一键清空") }
        }

        // 导入确认对话框
        pendingImportPath?.let { path ->
            AlertDialog(
                onDismissRequest = { pendingImportPath = null },
                title = { Text("导入备份") },
                text = {
                    Text(
                        text = "确定要导入此备份吗？\n${java.io.File(path).name}\n\n导入后会覆盖同名的文章和设置，请谨慎操作。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.importFromBackupFile(path)
                            pendingImportPath = null
                        },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = DangerColor
                        )
                    ) { Text("确定导入") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { pendingImportPath = null },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = LocalAccentColor.current
                        )
                    ) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        // 批量删除确认弹窗
        if (showBatchDeleteBackupsConfirm) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteBackupsConfirm = false },
                title = { Text("批量删除备份文件") },
                text = { Text("确定要删除选中的 ${selectedBackupPaths.size} 个备份文件吗？此操作不可撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteBackups(selectedBackupPaths)
                            selectedBackupPaths = emptySet()
                            showBatchDeleteBackupsConfirm = false
                        }
                    ) { Text("删除", color = DangerColor) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showBatchDeleteBackupsConfirm = false },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                    ) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        // 一键清空确认弹窗
        if (showClearAllBackupsConfirm) {
            AlertDialog(
                onDismissRequest = { showClearAllBackupsConfirm = false },
                title = { Text("清空所有备份文件") },
                text = { Text("确定要清空所有备份文件吗？此操作不可撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllBackups()
                            showClearAllBackupsConfirm = false
                        }
                    ) { Text("清空", color = DangerColor) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearAllBackupsConfirm = false },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                    ) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        backupMessage?.let { result ->
            val (text, isSuccess) = when (result) {
                is BackupResult.ExportSuccess ->
                    stringResource(R.string.setting_export_success, result.count) to true
                is BackupResult.ImportSuccess ->
                    stringResource(R.string.setting_import_success, result.count) to true
                is BackupResult.Fail ->
                    stringResource(R.string.setting_backup_fail, result.message) to false
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSuccess) LocalAccentColor.current
                    else DangerColor,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

// ===== 关于 Section =====

@Composable
private fun AboutSection(viewModel: SettingViewModel) {
    val context = LocalContext.current
    // 连续点击"软件版本"行 5 次解锁/锁定隐藏调试入口（仿 Android 开发者选项）
    // debug 版本始终解锁，点击时提示无需开启
    var versionClickCount by remember { mutableStateOf(0) }
    val noticeManager = com.gridea.android.ui.theme.LocalNoticeManager.current
    val debugUnlocked by viewModel.debugUnlock.collectAsState()

    // 顶部软件图标：居中圆角裁剪，使用高清 PNG 图标
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_about_logo),
            contentDescription = stringResource(R.string.about_app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(108.dp)
                .clip(RoundedCornerShape(24.dp))
        )
    }

    SettingGroupCard(title = stringResource(R.string.about_app_name)) {
        Text(
            text = stringResource(R.string.about_app_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SettingGroupCard(title = stringResource(R.string.about_version_info)) {
        AboutRow(
            label = stringResource(R.string.about_version_name),
            value = BuildConfig.VERSION_NAME
        )
        AboutRow(
            label = stringResource(R.string.about_version_code),
            value = BuildConfig.VERSION_CODE.toString()
        )
        // 发行版本行：动态显示 Debug/Release，连续点击 5 次解锁/锁定调试入口
        // 仿 Android 开发者选项交互，点击背景为圆角与软件设计风格一致
        // debug 版本点击时提示"无需开启此功能"，release 版本切换后通过灵动岛通知反馈
        AboutRow(
            label = stringResource(R.string.about_software_version),
            value = if (BuildConfig.DEBUG) "Debug" else "Release",
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    if (BuildConfig.DEBUG) {
                        noticeManager.showNotice(context.getString(R.string.debug_unlock_not_needed))
                        return@clickable
                    }
                    versionClickCount++
                    if (versionClickCount >= 5) {
                        versionClickCount = 0
                        viewModel.toggleDebugUnlock()
                        // 注意：debugUnlocked 是切换前的状态，取反得到切换后的状态
                        val msg = if (!debugUnlocked) {
                            context.getString(R.string.debug_unlock_enabled)
                        } else {
                            context.getString(R.string.debug_unlock_disabled)
                        }
                        noticeManager.showNotice(msg)
                    }
                }
        )
    }

    // 开发者分组卡片：每个开发者独立圆角卡片，仅含头像和名称（简化展示）
    SettingGroupCard(title = stringResource(R.string.about_developers)) {
        DeveloperRow(
            avatarRes = R.drawable.dev_xiuhong,
            name = "xiuhong"
        )
        DeveloperRow(
            avatarRes = R.drawable.dev_ai_avatar,
            name = "Trae"
        )
    }

    SettingGroupCard(title = stringResource(R.string.about_tech_stack)) {
        Text(
            text = stringResource(R.string.about_tech_stack_content),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SettingGroupCard(title = stringResource(R.string.about_open_source)) {
        Text(
            text = stringResource(R.string.about_open_source_content),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // 本项目仓库地址（超链接）：点击跳转浏览器打开
        Text(
            text = "github.com/QingYu327/GrideaAndroid",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAccentColor.current,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/QingYu327/GrideaAndroid"))
                    context.startActivity(intent)
                }
        )
        // 原项目仓库地址（超链接）：点击跳转浏览器打开
        Text(
            text = "github.com/getgridea/gridea",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAccentColor.current,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/getgridea/gridea"))
                    context.startActivity(intent)
                }
        )
    }

    SettingGroupCard(title = stringResource(R.string.about_acknowledgement)) {
        Text(
            text = stringResource(R.string.about_acknowledgement_content),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 开源许可分组：列出本项目使用的主要开源项目及其许可，点击跳转对应开源链接
    SettingGroupCard(title = stringResource(R.string.about_open_source_licenses)) {
        OpenSourceLicenses.forEach { license ->
            LicenseRow(license = license)
        }
    }
}

// ===== 调试 Section =====

/**
 * 调试开关页面
 *
 * 提供运行时调试开关，用于监测软件运行：
 * - WebView 调试：允许 Chrome DevTools 远程调试（chrome://inspect）
 * - 详细日志：输出调试级日志到日志管理
 *
 * 所有开关默认关闭，需要手动开启。
 */
@Composable
private fun DebugSection(viewModel: SettingViewModel) {
    val webViewDebug by viewModel.webViewDebug.collectAsState()
    val verboseLog by viewModel.verboseLog.collectAsState()

    SettingGroupCard(title = stringResource(R.string.setting_debug)) {
        // WebView 调试开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.setting_debug_webview),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.setting_debug_webview_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = webViewDebug,
                onCheckedChange = viewModel::updateWebViewDebug,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = LocalAccentColor.current,
                    checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.5f),
                    checkedBorderColor = LocalAccentColor.current
                )
            )
        }

        // 详细日志开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.setting_debug_verbose_log),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.setting_debug_verbose_log_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = verboseLog,
                onCheckedChange = viewModel::updateVerboseLog,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = LocalAccentColor.current,
                    checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.5f),
                    checkedBorderColor = LocalAccentColor.current
                )
            )
        }
    }
}

// 开发者行：独立圆角卡片，头像（48dp 圆形）+ 名称（简化展示，移除角色描述）
@Composable
private fun DeveloperRow(avatarRes: Int, name: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 48dp 圆形头像
            Image(
                painter = painterResource(avatarRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 开源许可条目数据
 *
 * @param name 项目名称
 * @param license 许可协议（如 "Apache 2.0"）
 * @param author 作者/组织
 * @param url 项目主页（点击跳转浏览器）
 */
private data class OpenSourceLicense(
    val name: String,
    val license: String,
    val author: String,
    val url: String
)

/** 本项目使用的主要开源项目及其许可 */
private val OpenSourceLicenses = listOf(
    OpenSourceLicense("Kotlin", "Apache 2.0", "JetBrains", "https://github.com/JetBrains/kotlin"),
    OpenSourceLicense("Jetpack Compose", "Apache 2.0", "Google", "https://developer.android.com/jetpack/compose"),
    OpenSourceLicense("Pebble Templates", "MIT", "Pebble Templates", "https://github.com/PebbleTemplates/pebble"),
    OpenSourceLicense("Markwon", "Apache 2.0", "Dimitry Ivanov", "https://github.com/noties/markwon"),
    OpenSourceLicense("commonmark-java", "BSD 2-Clause", "Robin Schneider", "https://github.com/commonmark/commonmark-java"),
    OpenSourceLicense("Coil", "Apache 2.0", "Coil Contributors", "https://github.com/coil-kt/coil"),
    OpenSourceLicense("OkHttp", "Apache 2.0", "Square", "https://github.com/square/okhttp"),
    OpenSourceLicense("Room", "Apache 2.0", "Google", "https://developer.android.com/jetpack/androidx/releases/room"),
    OpenSourceLicense("Hilt", "Apache 2.0", "Google", "https://dagger.dev/hilt/"),
    OpenSourceLicense("DataStore", "Apache 2.0", "Google", "https://developer.android.com/topic/libraries/architecture/datastore")
)

/**
 * 开源许可行：显示 "项目名 (协议) - 作者"，点击跳转浏览器打开项目主页
 */
@Composable
private fun LicenseRow(license: OpenSourceLicense) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(license.url))
                context.startActivity(intent)
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${license.name} (${license.license}) - ${license.author}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = LocalAccentColor.current
        )
    }
}

@Composable
private fun AboutRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// ===== 更新与反馈 =====

/**
 * 一键收集日志的圆角卡片按钮（共享组件）
 *
 * 点击后自动导出日志管理系统的操作日志到 log 目录，
 * 再将 log 目录下所有日志文件 + 设备信息 + DataStore 配置快照（脱敏）打包为 zip。
 *
 * 供 DebugSection 和 UpdateFeedbackSection 复用，确保两处入口行为一致。
 */
@Composable
private fun LogCollectCard(viewModel: SettingViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noticeManager = com.gridea.android.ui.theme.LocalNoticeManager.current
    var isCollecting by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isCollecting) {
                if (isCollecting) return@clickable
                isCollecting = true
                scope.launch {
                    try {
                        val result = viewModel.collectFeedbackLogs(context)
                        result.fold(
                            onSuccess = { path ->
                                noticeManager.showNotice(
                                    context.getString(R.string.feedback_collect_success, File(path).name)
                                )
                            },
                            onFailure = { e ->
                                noticeManager.showNotice(
                                    context.getString(R.string.feedback_collect_failed, e.message ?: "未知错误")
                                )
                            }
                        )
                    } finally {
                        isCollecting = false
                    }
                }
            },
        shape = RoundedCornerShape(12.dp),
        color = LocalAccentColor.current.copy(alpha = 0.12f)
            .compositeOver(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Archive,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = LocalAccentColor.current
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isCollecting) stringResource(R.string.feedback_collecting)
                       else stringResource(R.string.feedback_collect_logs),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = LocalAccentColor.current
            )
        }
    }
}

/**
 * 一键清空日志的圆角卡片按钮
 *
 * 浅红色警示色填充，点击后删除 log 目录下所有日志文件。
 * 用于日志数量较多时避免手动去文件管理器删除。
 */
@Composable
private fun LogClearCard(viewModel: SettingViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noticeManager = com.gridea.android.ui.theme.LocalNoticeManager.current
    var isClearing by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    // 清空日志确认弹窗，防止误删
    if (showConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("清空日志") },
            text = { Text("此操作将删除 log 目录下的所有日志文件，且不可恢复。确定继续吗？") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showConfirmDialog = false
                        isClearing = true
                        scope.launch {
                            try {
                                val result = viewModel.clearFeedbackLogs()
                                result.fold(
                                    onSuccess = { count ->
                                        val msg = if (count == 0) {
                                            context.getString(R.string.feedback_clear_empty)
                                        } else {
                                            context.getString(R.string.feedback_clear_success, count)
                                        }
                                        noticeManager.showNotice(msg)
                                    },
                                    onFailure = { e ->
                                        noticeManager.showNotice(
                                            context.getString(R.string.feedback_clear_failed, e.message ?: "未知错误")
                                        )
                                    }
                                )
                            } finally {
                                isClearing = false
                            }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("确定清空") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showConfirmDialog = false }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isClearing) {
                if (isClearing) return@clickable
                showConfirmDialog = true
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            .compositeOver(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isClearing) stringResource(R.string.feedback_clearing)
                       else stringResource(R.string.feedback_clear_logs),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 可复制的反馈信息行
 *
 * 左侧显示标签 + 值，右侧"复制"按钮，点击将值复制到系统剪贴板并通知。
 * 用于邮件反馈区块中的邮箱地址和邮件主题建议。
 */
@Composable
private fun CopyableInfoRow(label: String, value: String) {
    val context = LocalContext.current
    val noticeManager = com.gridea.android.ui.theme.LocalNoticeManager.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
                    noticeManager.showNotice(context.getString(R.string.feedback_copied))
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp,
                    vertical = 0.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = LocalAccentColor.current
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.feedback_copy),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalAccentColor.current
                )
            }
        }
    }
}

/**
 * 更新与反馈页面
 *
 * 包含两个区块：
 * - 检查更新：通过 GitHub Releases API 检查最新版本，支持下载 APK 并调起安装
 * - 问题反馈：一键收集日志（log 目录文件 + 设备信息 + DataStore 配置快照脱敏）打包为 zip
 */
@Composable
private fun UpdateFeedbackSection(viewModel: SettingViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noticeManager = com.gridea.android.ui.theme.LocalNoticeManager.current

    // ===== 更新检查状态 =====
    var isChecking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.gridea.android.util.UpdateChecker.UpdateInfo?>(null) }
    var checkError by remember { mutableStateOf<String?>(null) }

    // ===== 下载状态 =====
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadedApkPath by remember { mutableStateOf<String?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    // 检查更新
    fun checkUpdate() {
        if (isChecking) return
        isChecking = true
        checkError = null
        updateInfo = null
        scope.launch {
            try {
                val info = com.gridea.android.util.UpdateChecker.check()
                updateInfo = info
            } catch (e: Exception) {
                checkError = e.message ?: "未知错误"
            } finally {
                isChecking = false
            }
        }
    }

    // 下载 APK
    fun downloadApk(url: String) {
        if (isDownloading) return
        isDownloading = true
        downloadProgress = 0
        downloadError = null
        scope.launch {
            try {
                val path = com.gridea.android.util.ApkDownloader.download(
                    url = url,
                    targetDir = File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS
                        ),
                        "Gridea"
                    ).apply { if (!exists()) mkdirs() }
                ) { progress ->
                    downloadProgress = progress
                }
                downloadedApkPath = path
            } catch (e: Exception) {
                downloadError = e.message ?: "未知错误"
                com.gridea.android.util.AppLogger.e("Update", "APK 下载失败：${e.message}", e)
            } finally {
                isDownloading = false
            }
        }
    }

    // 安装 APK
    fun installApk(apkPath: String) {
        try {
            val file = File(apkPath)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            com.gridea.android.util.AppLogger.i("Update", "调起 APK 安装器：${file.name}")
        } catch (e: Exception) {
            noticeManager.showNotice("安装失败：${e.message}", type = com.gridea.android.ui.theme.NoticeType.Error)
            com.gridea.android.util.AppLogger.e("Update", "调起安装器失败：${e.message}", e)
        }
    }

    // ===== 检查更新区块 =====
    SettingGroupCard(title = stringResource(R.string.update_check)) {
        // 当前版本
        AboutRow(
            label = "当前版本",
            value = com.gridea.android.BuildConfig.VERSION_NAME
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 检查更新按钮（圆角卡片风格）
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !isChecking) { checkUpdate() },
            shape = RoundedCornerShape(12.dp),
            color = LocalAccentColor.current.copy(alpha = 0.12f)
                .compositeOver(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = LocalAccentColor.current
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isChecking) stringResource(R.string.update_checking)
                           else stringResource(R.string.update_check),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = LocalAccentColor.current
                )
            }
        }

        // 检查结果
        updateInfo?.let { info ->
            Spacer(modifier = Modifier.height(12.dp))
            if (info.hasUpdate) {
                Text(
                    text = stringResource(R.string.update_available, info.latestVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalAccentColor.current,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.update_release_notes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = info.releaseNotes,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 下载/安装按钮
                if (info.apkDownloadUrl != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    when {
                        isDownloading -> {
                            // 下载进度
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(R.string.update_downloading, downloadProgress),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LocalAccentColor.current
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { downloadProgress / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = LocalAccentColor.current,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                        downloadedApkPath != null -> {
                            // 下载完成，点击安装
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { downloadedApkPath?.let { installApk(it) } },
                                shape = RoundedCornerShape(12.dp),
                                color = LocalAccentColor.current.copy(alpha = 0.12f)
                                    .compositeOver(MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = LocalAccentColor.current
                                    )
                                    Text(
                                        text = stringResource(R.string.update_download_complete),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = LocalAccentColor.current
                                    )
                                }
                            }
                        }
                        else -> {
                            // 下载按钮
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { downloadApk(info.apkDownloadUrl) },
                                shape = RoundedCornerShape(12.dp),
                                color = LocalAccentColor.current.copy(alpha = 0.12f)
                                    .compositeOver(MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = LocalAccentColor.current
                                    )
                                    Text(
                                        text = stringResource(R.string.update_download),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = LocalAccentColor.current
                                    )
                                }
                            }
                        }
                    }
                    downloadError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.update_download_failed, err),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    // 无 APK 资源，提供浏览器打开 Release 页面
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl))
                                context.startActivity(intent)
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = LocalAccentColor.current
                            )
                            Text(
                                text = "在浏览器中查看",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalAccentColor.current
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.update_latest),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        checkError?.let { err ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.update_check_failed, err),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // ===== 问题反馈区块 =====
    SettingGroupCard(title = stringResource(R.string.feedback_section)) {
        Text(
            text = stringResource(R.string.feedback_collect_logs_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 日志目录路径展示
        Text(
            text = viewModel.logDisplayPath,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 一键收集日志按钮（共享组件，与调试入口行为一致）
        LogCollectCard(viewModel = viewModel)

        Spacer(modifier = Modifier.height(8.dp))

        // 一键清空日志按钮（浅红色警示色）
        LogClearCard(viewModel = viewModel)
    }

    Spacer(modifier = Modifier.height(16.dp))

    // ===== 邮件反馈区块 =====
    // 提示用户将收集的日志 zip 作为附件发送到开发者邮箱
    SettingGroupCard(title = stringResource(R.string.feedback_email_section)) {
        Text(
            text = stringResource(R.string.feedback_email_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CopyableInfoRow(
            label = stringResource(R.string.feedback_email_address_label),
            value = stringResource(R.string.feedback_email_address)
        )
        CopyableInfoRow(
            label = stringResource(R.string.feedback_email_subject_label),
            value = stringResource(R.string.feedback_email_subject)
        )
        Text(
            text = stringResource(R.string.feedback_email_attach_hint),
            style = MaterialTheme.typography.bodySmall,
            color = LocalAccentColor.current
        )
    }
}

// ===== 部署配置子组件 =====

@Composable
private fun GitConfig(
    setting: com.gridea.android.data.model.Setting,
    viewModel: SettingViewModel
) {
    val account by viewModel.account.collectAsState()
    SettingGroupCard(title = stringResource(R.string.setting_git_config)) {
        OutlinedTextField(
            value = setting.repository,
            onValueChange = viewModel::updateRepository,
            label = { Text(stringResource(R.string.setting_repo_name)) },
            placeholder = { Text("octocat/blog") },
            supportingText = { Text(stringResource(R.string.setting_repo_name_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = setting.branch,
            onValueChange = viewModel::updateBranch,
            label = { Text(stringResource(R.string.setting_branch)) },
            placeholder = { Text("master") },
            supportingText = { Text(stringResource(R.string.setting_branch_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = setting.username,
            onValueChange = viewModel::updateUsername,
            label = { Text(stringResource(R.string.setting_username)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = setting.email,
            onValueChange = viewModel::updateEmail,
            label = { Text(stringResource(R.string.setting_email)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = setting.token,
            onValueChange = viewModel::updateToken,
            label = { Text(stringResource(R.string.setting_token)) },
            supportingText = { Text(stringResource(R.string.setting_token_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        // OAuth Token 复用按钮：已登录时显示
        if (account.accessToken.isNotEmpty()) {
            Button(
                onClick = { viewModel.useOAuthTokenForDeploy() },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = LocalAccentColor.current
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.setting_use_oauth_token), fontSize = 13.sp)
            }
        } else {
            Text(
                text = stringResource(R.string.setting_oauth_not_logged_in),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        OutlinedTextField(
            value = setting.cname,
            onValueChange = viewModel::updateCname,
            label = { Text(stringResource(R.string.setting_cname)) },
            placeholder = { Text("mydomain.com") },
            supportingText = { Text(stringResource(R.string.setting_cname_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
    }
}

/**
 * Gitee 配置表单
 * 使用独立的 gitee* 字段，与 GitHub 配置完全隔离，切换平台时输入值不会串台
 */
@Composable
private fun GiteeConfig(
    setting: com.gridea.android.data.model.Setting,
    viewModel: SettingViewModel
) {
    SettingGroupCard(title = stringResource(R.string.setting_gitee_config)) {
        OutlinedTextField(
            value = setting.giteeRepository,
            onValueChange = viewModel::updateGiteeRepository,
            label = { Text(stringResource(R.string.setting_gitee_repo_name)) },
            placeholder = { Text("blog") },
            supportingText = { Text(stringResource(R.string.setting_gitee_repo_name_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = setting.giteeBranch,
            onValueChange = viewModel::updateGiteeBranch,
            label = { Text(stringResource(R.string.setting_gitee_branch)) },
            placeholder = { Text("master") },
            supportingText = { Text(stringResource(R.string.setting_gitee_branch_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = setting.giteeUsername,
            onValueChange = viewModel::updateGiteeUsername,
            label = { Text(stringResource(R.string.setting_gitee_username)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = setting.giteeToken,
            onValueChange = viewModel::updateGiteeToken,
            label = { Text(stringResource(R.string.setting_gitee_token)) },
            supportingText = { Text(stringResource(R.string.setting_gitee_token_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
    }
}

@Composable
private fun SftpConfig(
    setting: com.gridea.android.data.model.Setting,
    viewModel: SettingViewModel
) {
    SettingGroupCard(title = stringResource(R.string.setting_sftp_config)) {
        OutlinedTextField(
            value = setting.server,
            onValueChange = viewModel::updateServer,
            label = { Text(stringResource(R.string.setting_server)) },
            supportingText = { Text(stringResource(R.string.setting_server_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = setting.port,
            onValueChange = viewModel::updatePort,
            label = { Text(stringResource(R.string.setting_port)) },
            placeholder = { Text("22") },
            supportingText = { Text(stringResource(R.string.setting_port_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = setting.sftpUsername,
            onValueChange = viewModel::updateSftpUsername,
            label = { Text(stringResource(R.string.setting_sftp_username)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = setting.password,
            onValueChange = viewModel::updatePassword,
            label = { Text(stringResource(R.string.setting_password)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = setting.remotePath,
            onValueChange = viewModel::updateRemotePath,
            label = { Text(stringResource(R.string.setting_remote_path)) },
            placeholder = { Text("/var/www/blog") },
            supportingText = { Text(stringResource(R.string.setting_remote_path_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
    }
}

@Composable
private fun NetlifyConfig(
    setting: com.gridea.android.data.model.Setting,
    viewModel: SettingViewModel
) {
    SettingGroupCard(title = stringResource(R.string.setting_netlify_config)) {
        OutlinedTextField(
            value = setting.netlifySiteId,
            onValueChange = viewModel::updateNetlifySiteId,
            label = { Text(stringResource(R.string.setting_netlify_site_id)) },
            supportingText = { Text(stringResource(R.string.setting_netlify_site_id_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = setting.netlifyAccessToken,
            onValueChange = viewModel::updateNetlifyToken,
            label = { Text(stringResource(R.string.setting_netlify_token)) },
            supportingText = { Text(stringResource(R.string.setting_netlify_token_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
    }
}

@Composable
private fun VercelConfig(
    setting: com.gridea.android.data.model.Setting,
    viewModel: SettingViewModel
) {
    SettingGroupCard(title = stringResource(R.string.setting_vercel_config)) {
        OutlinedTextField(
            value = setting.vercelProjectId,
            onValueChange = viewModel::updateVercelProjectId,
            label = { Text(stringResource(R.string.setting_vercel_project_id)) },
            supportingText = { Text(stringResource(R.string.setting_vercel_project_id_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
        OutlinedTextField(
            value = setting.vercelAccessToken,
            onValueChange = viewModel::updateVercelAccessToken,
            label = { Text(stringResource(R.string.setting_vercel_token)) },
            supportingText = { Text(stringResource(R.string.setting_vercel_token_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
    }
}

// ===== 通用组件 =====

/**
 * 设置分组卡片：用 Card 包裹一组设置项，提供视觉分组边界，避免扁平生硬
 * 标题与卡片包裹在同一 Column 中，作为单一分组参与父级间距控制
 */
@Composable
internal fun SettingGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = LocalAccentColor.current,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

/**
 * 无标题分组卡片（用于按钮组等）
 */
@Composable
internal fun SettingGroupCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
internal fun ColorPickerDialog(
    initialColor: Color,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Color → HSV
    val initialArgb = initialColor.toArgb()
    val initialHsv = FloatArray(3)
    android.graphics.Color.colorToHSV(initialArgb, initialHsv)

    var hue by rememberSaveable { mutableStateOf(initialHsv[0]) }
    var saturation by rememberSaveable { mutableStateOf(initialHsv[1]) }
    var valueField by rememberSaveable { mutableStateOf(initialHsv[2]) }
    var hexInput by rememberSaveable { mutableStateOf(colorToHex(initialColor)) }

    // 当前 HSV 对应颜色
    val currentColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, valueField)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择颜色") },
        text = {
            Column {
                // SV 面板（饱和度×明度）
                val svModifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(RoundedCornerShape(12.dp))
                Box(svModifier) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    if (w > 0 && h > 0) {
                                        saturation = (offset.x / w).coerceIn(0f, 1f)
                                        valueField = (1f - offset.y / h).coerceIn(0f, 1f)
                                        // 内联计算颜色，避免捕获过期的 currentColor
                                        hexInput = colorToHex(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, valueField))))
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    if (w > 0 && h > 0) {
                                        saturation = (change.position.x / w).coerceIn(0f, 1f)
                                        valueField = (1f - change.position.y / h).coerceIn(0f, 1f)
                                        hexInput = colorToHex(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, valueField))))
                                    }
                                }
                            }
                    ) {
                        val w = size.width
                        val h = size.height
                        // 横向：白 → 纯色（当前色相，S=1, V=1）
                        val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.White, hueColor),
                                startX = 0f,
                                endX = w
                            )
                        )
                        // 纵向：透明 → 黑
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = h
                            )
                        )
                        // 圆点指示器
                        val indicatorX = saturation * w
                        val indicatorY = (1f - valueField) * h
                        drawCircle(
                            color = Color.White,
                            radius = 10f,
                            center = Offset(indicatorX, indicatorY),
                            style = Stroke(width = 3f)
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 10f,
                            center = Offset(indicatorX, indicatorY),
                            style = Stroke(width = 1f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 色相滑块
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val w = size.width.toFloat()
                                    if (w > 0) {
                                        hue = (offset.x / w * 360f).coerceIn(0f, 360f)
                                        hexInput = colorToHex(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, valueField))))
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val w = size.width.toFloat()
                                    if (w > 0) {
                                        hue = (change.position.x / w * 360f).coerceIn(0f, 360f)
                                        hexInput = colorToHex(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, valueField))))
                                    }
                                }
                            }
                    ) {
                        val w = size.width
                        val h = size.height
                        // 彩虹渐变（0° 红 → 120° 绿 → 240° 蓝 → 360° 红）
                        val rainbowColors = (0..360 step 60).map { deg ->
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(deg.toFloat(), 1f, 1f)))
                        }
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = rainbowColors,
                                startX = 0f,
                                endX = w
                            )
                        )
                        // 指示器
                        val indicatorX = (hue / 360f) * w
                        drawCircle(
                            color = Color.White,
                            radius = h / 2 - 2f,
                            center = Offset(indicatorX, h / 2),
                            style = Stroke(width = 2f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 预览 + 十六进制输入
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(currentColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { input ->
                            // 只允许合法的十六进制输入
                            val cleaned = input.trim().let {
                                if (it.startsWith("#")) it else "#$it"
                            }.lowercase()
                            if (cleaned.matches(HEX_COLOR_REGEX)) {
                                hexInput = cleaned
                                if (cleaned.length == 7) {
                                    try {
                                        val rgb = cleaned.substring(1).toLong(16)
                                        val r = ((rgb shr 16) and 0xFF) / 255f
                                        val g = ((rgb shr 8) and 0xFF) / 255f
                                        val b = (rgb and 0xFF) / 255f
                                        val hsv = FloatArray(3)
                                        android.graphics.Color.RGBToHSV(
                                            (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), hsv
                                        )
                                        hue = hsv[0]
                                        saturation = hsv[1]
                                        valueField = hsv[2]
                                    } catch (_: Exception) { }
                                }
                            }
                        },
                        label = { Text("颜色代码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = settingsTextFieldColors()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(hexInput) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = LocalAccentColor.current,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
            ) { Text("取消") }
        }
    )
}

/** Color → #rrggbb */
private fun colorToHex(color: Color): String {
    val argb = color.toArgb()
    return String.format("#%06x", argb and 0xFFFFFF)
}

/**
 * APP 界面强调色设置行
 *
 * 点击色块弹出调色盘，选色后整个 APP 的按钮/开关/选中态等强调色立即变化。
 * 空字符串 = 用默认淡紫色（#9C8FDA）；非空 = 用户自定义色。
 * 提供"恢复默认"按钮一键回到默认色。
 */
@Composable
private fun AppAccentColorRow(
    hexValue: String,
    onValueChange: (String) -> Unit
) {
    val defaultColor = com.gridea.android.ui.theme.AccentColor
    val isCustom = hexValue.length == 7 && hexValue.startsWith("#")
    val previewColor = if (isCustom) {
        try {
            Color(hexValue.substring(1).toLong(16) or 0xFF000000)
        } catch (_: Exception) {
            defaultColor
        }
    } else {
        defaultColor
    }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.setting_app_accent_color),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = if (isCustom) hexValue else stringResource(R.string.setting_app_accent_color_default),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 自定义色时显示"恢复默认"按钮
        if (isCustom) {
            TextButton(
                onClick = { onValueChange("") },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.setting_app_accent_color_reset),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAccentColor.current
                )
            }
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(previewColor)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable { showPicker = true }
        )
    }

    if (showPicker) {
        ColorPickerDialog(
            initialColor = previewColor,
            onConfirm = { hex ->
                onValueChange(hex)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
internal fun PlatformChip(
    label: String,
    icon: ImageVector,
    value: String,
    currentValue: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = currentValue == value,
        onClick = { onSelect(value) },
        label = {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(label, maxLines = 1, fontSize = 12.sp)
            }
        },
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = LocalAccentColor.current.copy(alpha = 0.2f), selectedLabelColor = LocalAccentColor.current, selectedLeadingIconColor = LocalAccentColor.current)
    )
}

@Composable
private fun RowScope.ThemeModeChip(
    label: String,
    value: String,
    currentValue: String,
    onSelect: (String) -> Unit
) {
    val isSelected = currentValue == value
    val accentColor = LocalAccentColor.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val containerColor = if (isSelected) {
        accentColor.copy(alpha = 0.16f).compositeOver(surfaceColor)
    } else {
        surfaceColor
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable { onSelect(value) }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RowScope.LanguageChip(
    label: String,
    value: String,
    currentValue: String,
    onSelect: (String) -> Unit
) {
    val isSelected = currentValue == value
    val accentColor = LocalAccentColor.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val containerColor = if (isSelected) {
        accentColor.copy(alpha = 0.16f).compositeOver(surfaceColor)
    } else {
        surfaceColor
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable { onSelect(value) }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 格式化文件大小为可读字符串
 */
private fun formatFileSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes < kb -> "$bytes B"
        bytes < mb -> String.format("%.1f KB", bytes / kb)
        bytes < gb -> String.format("%.1f MB", bytes / mb)
        else -> String.format("%.2f GB", bytes / gb)
    }
}

// ===== 站点信息页面 =====

/**
 * 站点信息独立页面
 *
 * 集中管理所有站点级配置项：基本信息、站点身份、URL 与路径、内容展示、主题外观
 * 替代原 `GeneralSection` 中"基本信息"和"文章设置"两块内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteInfoScreen(
    onBack: () -> Unit,
    viewModel: SettingViewModel = hiltViewModel()
) {
    val theme by viewModel.theme.collectAsState()

    // 监听 ViewModel 的保存提示消息：转发到全局灵动岛通知系统
    val noticeManager = com.gridea.android.ui.theme.LocalNoticeManager.current
    val savedMessage by viewModel.savedMessage.collectAsState()
    LaunchedEffect(savedMessage) {
        savedMessage?.let {
            noticeManager.showNotice(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting_site_info_title)) },
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
        }
    ) { innerPadding ->
        SiteInfoContent(
            viewModel = viewModel,
            theme = theme,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

/**
 * 站点信息内容（不含 Scaffold/TopAppBar）
 *
 * 抽出为 internal 以便在「主题」页的「基础配置」Tab 中平铺复用，
 * 同时仍被独立的 SiteInfoScreen 二级页面调用。
 */
@Composable
internal fun SiteInfoContent(
    viewModel: SettingViewModel,
    theme: com.gridea.android.data.model.Theme,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            // ===== 基本信息 =====
            SettingGroupCard(title = stringResource(R.string.setting_section_basic_info)) {
                OutlinedTextField(
                    value = theme.siteName,
                    onValueChange = viewModel::updateSiteName,
                    label = { Text(stringResource(R.string.setting_site_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = SettingsTextFieldShape,
                    colors = settingsTextFieldColors()
                )
                OutlinedTextField(
                    value = theme.siteDescription,
                    onValueChange = viewModel::updateSiteDescription,
                    label = { Text(stringResource(R.string.setting_site_description)) },
                    supportingText = { Text(stringResource(R.string.setting_html_supported)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = SettingsTextFieldShape,
                    colors = settingsTextFieldColors()
                )
                OutlinedTextField(
                    value = theme.footerInfo,
                    onValueChange = viewModel::updateFooterInfo,
                    label = { Text(stringResource(R.string.setting_footer_info)) },
                    supportingText = { Text(stringResource(R.string.setting_html_supported)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = SettingsTextFieldShape,
                    colors = settingsTextFieldColors()
                )
            }

            // ===== 站点身份 =====
            SettingGroupCard(title = stringResource(R.string.setting_section_site_identity)) {
                OutlinedTextField(
                    value = theme.siteAuthor,
                    onValueChange = viewModel::updateSiteAuthor,
                    label = { Text(stringResource(R.string.setting_site_author)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = SettingsTextFieldShape,
                    colors = settingsTextFieldColors()
                )
                OutlinedTextField(
                    value = theme.siteAvatar,
                    onValueChange = viewModel::updateSiteAvatar,
                    label = { Text(stringResource(R.string.setting_site_avatar)) },
                    supportingText = { Text(stringResource(R.string.setting_site_avatar_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = SettingsTextFieldShape,
                    colors = settingsTextFieldColors()
                )
                OutlinedTextField(
                    value = theme.siteFavicon,
                    onValueChange = viewModel::updateSiteFavicon,
                    label = { Text(stringResource(R.string.setting_site_favicon)) },
                    supportingText = { Text(stringResource(R.string.setting_site_favicon_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = SettingsTextFieldShape,
                    colors = settingsTextFieldColors()
                )
            }

            // ===== URL 与路径 =====
            // 对应桌面端 Gridea BasicSetting.vue 的 URL 与路径分组
            // postUrlFormat/tagUrlFormat：SLUG（标题转拼音）或 SHORT_ID（随机短ID）
            // postPath/tagPath/archivesPath：URL 子路径前缀，影响目录结构
            SettingGroupCard(title = stringResource(R.string.setting_section_url_path)) {
                Text(
                    text = stringResource(R.string.setting_url_path_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 文章 URL 格式：SLUG 或 SHORT_ID
                Text(
                    text = stringResource(R.string.setting_post_url_format),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlatformChip(
                        stringResource(R.string.setting_url_format_slug),
                        Icons.Filled.Link,
                        "SLUG",
                        theme.postUrlFormat,
                        viewModel::updatePostUrlFormat,
                        Modifier.weight(1f)
                    )
                    PlatformChip(
                        stringResource(R.string.setting_url_format_short_id),
                        Icons.Filled.Link,
                        "SHORT_ID",
                        theme.postUrlFormat,
                        viewModel::updatePostUrlFormat,
                        Modifier.weight(1f)
                    )
                }
                // 标签 URL 格式：SLUG 或 SHORT_ID
                Text(
                    text = stringResource(R.string.setting_tag_url_format),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlatformChip(
                        stringResource(R.string.setting_url_format_slug),
                        Icons.Filled.Link,
                        "SLUG",
                        theme.tagUrlFormat,
                        viewModel::updateTagUrlFormat,
                        Modifier.weight(1f)
                    )
                    PlatformChip(
                        stringResource(R.string.setting_url_format_short_id),
                        Icons.Filled.Link,
                        "SHORT_ID",
                        theme.tagUrlFormat,
                        viewModel::updateTagUrlFormat,
                        Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = theme.postPath,
                    onValueChange = viewModel::updatePostPath,
                    label = { Text(stringResource(R.string.setting_site_post_path)) },
                    supportingText = { Text(stringResource(R.string.setting_site_post_path_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = SettingsTextFieldShape,
                    colors = settingsTextFieldColors()
                )
                OutlinedTextField(
                    value = theme.tagPath,
                    onValueChange = viewModel::updateTagPath,
                    label = { Text(stringResource(R.string.setting_site_tag_path)) },
                    supportingText = { Text(stringResource(R.string.setting_site_tag_path_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = SettingsTextFieldShape,
                    colors = settingsTextFieldColors()
                )
                OutlinedTextField(
                    value = theme.archivesPath,
                    onValueChange = viewModel::updateArchivesPath,
                    label = { Text(stringResource(R.string.setting_site_archives_path)) },
                    supportingText = { Text(stringResource(R.string.setting_site_archives_path_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = SettingsTextFieldShape,
                    colors = settingsTextFieldColors()
                )
            }

            // ===== 内容展示 =====
            SettingGroupCard(title = stringResource(R.string.setting_section_content_display)) {
                OutlinedTextField(
                    value = theme.postPageSize.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { viewModel.updatePostPageSize(it) }
                    },
                    label = { Text(stringResource(R.string.setting_post_page_size)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = SettingsTextFieldShape,
                    colors = settingsTextFieldColors()
                )
                OutlinedTextField(
                    value = theme.dateFormat,
                    onValueChange = viewModel::updateDateFormat,
                    label = { Text(stringResource(R.string.setting_date_format)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = SettingsTextFieldShape,
                    colors = settingsTextFieldColors()
                )
                OutlinedTextField(
                    value = theme.feedCount.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { viewModel.updateFeedCount(it) }
                    },
                    label = { Text(stringResource(R.string.setting_site_feed_count)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = SettingsTextFieldShape,
                    colors = settingsTextFieldColors()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.setting_site_feed_full_text),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = theme.feedFullText,
                        onCheckedChange = viewModel::updateFeedFullText,
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = LocalAccentColor.current,
                            checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.5f),
                            checkedBorderColor = LocalAccentColor.current
                        )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.setting_show_feature_image),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = theme.showFeatureImage,
                        onCheckedChange = viewModel::updateShowFeatureImage,
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = LocalAccentColor.current,
                            checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.5f),
                            checkedBorderColor = LocalAccentColor.current
                        )
                    )
                }
            }

            // 主题外观选项已迁移至「自定义主题」页面，此处不再展示
            // 域名占位提示已移除
    }
}

/**
 * 主题外观颜色行：左侧文字、右侧色块（点击调色盘）+ hex 文本输入
 * 空值时显示"未设置（使用默认）"占位
 */
@Composable
internal fun ThemeColorRow(
    label: String,
    hexValue: String,
    onValueChange: (String) -> Unit
) {
    val isSet = hexValue.isNotEmpty()
    // 预览色：解析失败时回退到透明（占位）
    val previewColor = remember(hexValue) {
        if (hexValue.isEmpty()) Color.Transparent
        else try {
            // 接受 #xxxxxx 或 xxxxxx 形式
            val hex = if (hexValue.startsWith("#")) hexValue.substring(1) else hexValue
            if (hex.length == 6) Color(hex.toLong(16) or 0xFF000000)
            else Color.Transparent
        } catch (_: Exception) {
            Color.Transparent
        }
    }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            // 色块：空值时显示斜纹占位边框
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(previewColor)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showPicker = true }
            )
        }
        OutlinedTextField(
            value = hexValue,
            onValueChange = onValueChange,
            label = { Text("#hex") },
            placeholder = {
                Text(
                    text = stringResource(R.string.setting_site_color_unset),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SettingsTextFieldShape,
            colors = settingsTextFieldColors()
        )
    }

    if (showPicker) {
        // 用当前色（无则用默认主题色）打开调色盘
        val initialColor = if (isSet) previewColor else LocalAccentColor.current
        ColorPickerDialog(
            initialColor = initialColor,
            onConfirm = { hex -> onValueChange(hex); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}
