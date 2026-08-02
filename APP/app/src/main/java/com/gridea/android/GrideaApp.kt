package com.gridea.android

import android.app.Application
import com.gridea.android.data.repository.SiteOutputRepository
import com.gridea.android.data.repository.ThemePackRepository
import com.gridea.android.util.AppLogger
import com.gridea.android.util.CrashHandler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Gridea 应用入口
 *
 * 使用 @HiltAndroidApp 注解启用 Hilt 依赖注入
 * 替代旧版 Gridea 0.9.3 中 Electron 的 background.ts 入口
 *
 * 应用启动时自动扫描公共输出目录（Documents/Gridea）中的已有文件，
 * 用户重装应用后可立即看到先前的渲染结果，无需重新生成。
 */
@HiltAndroidApp
class GrideaApp : Application() {

    @Inject
    lateinit var siteOutputRepository: SiteOutputRepository

    @Inject
    lateinit var themePackRepository: ThemePackRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 初始化全局日志记录器（内存缓存 + 文件持久化 + 周期性批量写入）
        AppLogger.init(this)
        // 自动清理超过 7 天的过期日志
        AppLogger.cleanExpiredLogs()
        // 注册全局未捕获异常处理器，尽早拦截崩溃
        CrashHandler.install(this)
        // 启动时若已授权，确保输出目录存在并扫描已有文件
        // 已通过 appScope（IO 协程）异步执行，不阻塞主线程
        appScope.launch {
            // 首次启动时将内置主题从 assets 复制到 filesDir，使主题开箱即用
            themePackRepository.ensureBuiltinThemesInstalled()
            if (siteOutputRepository.hasPermission.value) {
                siteOutputRepository.ensureOutputDir()
            }
            siteOutputRepository.scanExistingFiles()
        }
    }
}
