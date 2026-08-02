package com.gridea.android.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局未捕获异常处理器
 *
 * 实现线程未捕获异常的统一处理：
 * - 将崩溃堆栈写入 Documents/Gridea/log 目录下，每次崩溃生成独立文件（crash_yyyy-MM-dd_HH-mm-ss.txt）
 * - 在主线程弹出友好的 Toast 提示
 * - 交由默认异常处理器处理或直接退出进程，避免应用无响应卡死
 *
 * 使用方式：在 Application.onCreate 中调用 [install]
 */
class CrashHandler private constructor(
    private val context: Context
) : Thread.UncaughtExceptionHandler {

    // 系统默认的异常处理器，崩溃日志记录后交由其完成退出流程
    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    companion object {
        @Volatile
        private var instance: CrashHandler? = null

        /**
         * 注册全局异常处理器，应在 Application.onCreate 中调用
         */
        fun install(context: Context) {
            if (instance != null) return
            instance = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(instance)
        }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        // 0. 记录到全局日志系统（AppLogger），便于后续在应用内日志管理页查看
        try {
            AppLogger.e("System", "未捕获异常: ${e.javaClass.name}: ${e.message}", e)
        } catch (_: Throwable) {
            // AppLogger 未初始化或写入失败时不再抛出，避免掩盖原始崩溃
        }

        // 1. 将崩溃堆栈写入文件
        writeCrashLogToFile(t, e)

        // 2. 在主线程弹出友好提示
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "应用遇到问题，即将退出", Toast.LENGTH_LONG).show()
        }

        // 3. 短暂等待 Toast 显示，再交由默认处理器或退出进程
        try {
            Thread.sleep(2000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        // 优先交由系统默认处理器（会弹出系统崩溃对话框并退出）
        defaultHandler?.uncaughtException(t, e) ?: android.os.Process.killProcess(
            android.os.Process.myPid()
        )
    }

    /**
     * 将崩溃信息写入 Documents/Gridea/log 目录下的独立文件
     *
     * 文件名格式：crash_yyyy-MM-dd_HH-mm-ss.txt
     * 每次崩溃生成独立文件，便于在日志管理页批量查看与清理。
     * 优先使用公共 Documents 目录（需 MANAGE_EXTERNAL_STORAGE 权限），
     * 未授权时回退到应用内部存储 filesDir/log 保证崩溃一定能被记录。
     */
    private fun writeCrashLogToFile(t: Thread, e: Throwable) {
        try {
            val logDir = resolveCrashLogDir()
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                .format(Date())
            val logFile = File(logDir, "crash_$timestamp.txt")

            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            val log = buildString {
                append("========== Crash Log ==========")
                append("\n时间: $time")
                append("\n线程: ${t.name}")
                append("\n设备: ${Build.MANUFACTURER} ${Build.MODEL}")
                append("\n系统版本: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                append("\n异常: ${e.javaClass.name}: ${e.message}")
                append("\n堆栈:\n$stackTrace")
                // 完整 cause 链
                var cause: Throwable? = e.cause
                while (cause != null) {
                    append("\nCaused by: ${cause.javaClass.name}: ${cause.message}\n")
                    val csw = StringWriter()
                    cause.printStackTrace(PrintWriter(csw))
                    append(csw.toString())
                    cause = cause.cause
                }
                append("\n\n")
            }

            logFile.writeText(log)
        } catch (_: Throwable) {
            // 记录日志本身失败时不再抛出，避免掩盖原始崩溃
        }
    }

    /**
     * 解析崩溃日志目录：
     * - 已获得 MANAGE_EXTERNAL_STORAGE 权限时使用 Documents/Gridea/log
     * - 未授权时回退到应用内部存储 filesDir/log 保证崩溃仍能被记录
     */
    private fun resolveCrashLogDir(): File {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return if (hasPermission) {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "Gridea/log"
            )
        } else {
            File(context.filesDir, "log")
        }
    }
}
