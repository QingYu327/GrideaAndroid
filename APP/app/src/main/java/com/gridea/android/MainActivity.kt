package com.gridea.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.gridea.android.ui.GrideaApp
import com.gridea.android.ui.screen.setting.SettingViewModel
import com.gridea.android.ui.theme.GrideaAndroidTheme
import com.gridea.android.util.BackupScheduler
import com.gridea.android.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主 Activity
 *
 * 作为应用的唯一入口 Activity
 * 使用 Compose 构建界面，对应旧版 Electron 的主窗口
 *
 * 支持应用快捷方式：
 * - new_post: 快速新建文章
 *
 * 支持多语言切换（跟随系统/中文/英文）
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 快捷方式动作：null 表示正常启动 */
    private var shortcutAction: String? = null

    /** 当前语言模式，attachBaseContext 时读取用于首次应用 */
    private var currentLanguageMode: String = "system"

    /** 自动备份调度器，启动时按需执行每日备份与旧备份清理 */
    @Inject
    lateinit var backupScheduler: BackupScheduler

    override fun attachBaseContext(newBase: Context) {
        // 在 Activity 创建前同步读取已保存的语言设置
        currentLanguageMode = newBase.getSharedPreferences(
            "gridea_settings", Context.MODE_PRIVATE
        ).getString("language_mode", "system") ?: "system"

        // 读取 DataStore 需要异步，这里用 SharedPreferences 兜底
        // DataStore 的值会在 onCreate 中通过 Flow 同步过来
        val wrappedContext = LocaleHelper.wrap(newBase, currentLanguageMode)
        super.attachBaseContext(wrappedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动时使用 Splash 主题，ContentView 加载后切回正常主题
        setTheme(R.style.Theme_GrideaAndroid)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 读取快捷方式动作
        shortcutAction = intent?.getStringExtra("shortcut_action")

        // 启动时在后台协程中检查并按需执行自动备份（每日备份 + 每 7 天清理旧备份）
        lifecycleScope.launch(Dispatchers.IO) {
            backupScheduler.checkAndRunBackup()
        }

        setContent {
            val viewModel: SettingViewModel = hiltViewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            val languageMode by viewModel.languageMode.collectAsState()
            val fontSizeScale by viewModel.fontSizeScale.collectAsState()
            val dynamicColor by viewModel.dynamicColor.collectAsState()
            val appAccentColor by viewModel.appAccentColor.collectAsState()
            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            // 语言切换：由 SettingScreen 的重启按钮直接执行 finish()+startActivity()
            // 重启后 attachBaseContext() 会从 SharedPreferences 读取最新语言
            LaunchedEffect(languageMode) {
                // 仅同步当前语言模式记录，重启逻辑由设置页按钮直接处理
                currentLanguageMode = languageMode
            }

            // 快捷方式导航目标
            var pendingShortcut by remember { mutableStateOf(shortcutAction) }

            GrideaAndroidTheme(
                darkTheme = isDark,
                dynamicColor = dynamicColor,
                fontSizeScale = fontSizeScale,
                appAccentColor = appAccentColor
            ) {
                GrideaApp(
                    pendingShortcut = pendingShortcut,
                    onShortcutConsumed = { pendingShortcut = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 处理从快捷方式再次启动的情况
        shortcutAction = intent.getStringExtra("shortcut_action")
    }
}
