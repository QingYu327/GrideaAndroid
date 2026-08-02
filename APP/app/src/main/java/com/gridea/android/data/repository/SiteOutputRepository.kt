package com.gridea.android.data.repository

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 站点输出目录仓库
 *
 * 对应旧版 Gridea 0.9.3 中用户配置的 site output path
 *
 * 移动端使用公共 Documents/Gridea 目录作为输出位置：
 * - 通过 MANAGE_EXTERNAL_STORAGE 权限直接访问公共目录
 * - 使用 java.io.File API 读写（性能优于 SAF DocumentFile）
 * - 路径固定为 Documents/Gridea，应用重装后仍可访问
 * - 启动时扫描该目录可获取先前已生成的文件
 *
 * 目录结构（Documents/Gridea）：
 * - output/ — 存放渲染输出的静态网站文件（需要上传到仓库的文件）
 * - backup/ — 存放定时备份文件
 *
 * 渲染流程：
 * 1. SiteRenderer 先渲染到 cacheDir/gridea_build（应用内预览使用）
 * 2. 渲染完成后调用 copyToPublicOutput() 复制到 Documents/Gridea/output
 * 3. 用户可在系统文件管理器中直接查看输出结果
 */
@Singleton
class SiteOutputRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Gridea 根目录：内部存储/Documents/Gridea
     * 应用重装后该目录及其内容仍然保留。其下分 output 与 backup 两个子目录。
     */
    private val grideaBaseDir: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "Gridea"
        )

    /**
     * 公共输出目录：内部存储/Documents/Gridea/output
     * 存放渲染输出的静态网站文件（需要上传到仓库的文件）
     */
    val publicOutputDir: File
        get() = File(grideaBaseDir, "output")

    /**
     * 备份目录：内部存储/Documents/Gridea/backup
     * 存放定时备份文件（ZIP）
     */
    val backupDir: File
        get() = File(grideaBaseDir, "backup")

    /**
     * Markdown 导出目录：内部存储/Documents/Gridea/markdown
     * 存放批量导出的文章 Markdown 文件
     */
    val markdownDir: File
        get() = File(grideaBaseDir, "markdown")

    /**
     * 崩溃日志目录：内部存储/Documents/Gridea/log
     * 存放应用崩溃时的错误详情日志，每个崩溃一个独立文件
     */
    val logDir: File
        get() = File(grideaBaseDir, "log")

    /** 是否已获得所有文件访问权限（MANAGE_EXTERNAL_STORAGE） */
    private val _hasPermission = MutableStateFlow(checkPermissionInternal())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    /** 已扫描到的输出文件数量（null 表示未扫描） */
    private val _existingFileCount = MutableStateFlow<Int?>(null)
    val existingFileCount: StateFlow<Int?> = _existingFileCount.asStateFlow()

    /** 已扫描到的输出目录大小（字节，null 表示未扫描） */
    private val _existingTotalSize = MutableStateFlow<Long?>(null)
    val existingTotalSize: StateFlow<Long?> = _existingTotalSize.asStateFlow()

    /**
     * 检查是否已获得所有文件访问权限
     * Android 11+ 用 isExternalStorageManager，低版本用 WRITE_EXTERNAL_STORAGE 运行时权限
     */
    private fun checkPermissionInternal(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val writePerm = context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val readPerm = context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            writePerm == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                readPerm == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 刷新权限状态（用户从系统设置返回后调用）
     * 若已获得权限，自动创建 Documents/Gridea 下的 output 与 backup 子目录
     */
    fun refreshPermission() {
        _hasPermission.value = checkPermissionInternal()
        // 授权成功后立即创建 output/backup/markdown/log 子目录，无需用户手动创建
        if (_hasPermission.value) {
            ensureOutputDir()
            ensureBackupDir()
            ensureMarkdownDir()
            ensureLogDir()
        }
    }

    /**
     * 确保公共输出目录（Documents/Gridea/output）存在
     */
    fun ensureOutputDir(): File {
        val dir = publicOutputDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 确保备份目录（Documents/Gridea/backup）存在
     */
    fun ensureBackupDir(): File {
        val dir = backupDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 确保 Markdown 导出目录（Documents/Gridea/markdown）存在
     */
    fun ensureMarkdownDir(): File {
        val dir = markdownDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 确保崩溃日志目录（Documents/Gridea/log）存在
     */
    fun ensureLogDir(): File {
        val dir = logDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 获取日志目录的可读路径（用于显示给用户）
     */
    fun getLogDisplayPath(): String {
        return "内部存储/Documents/Gridea/log"
    }

    /**
     * 获取输出目录的可读路径（用于显示给用户）
     */
    fun getOutputDisplayPath(): String {
        return "内部存储/Documents/Gridea/output"
    }

    /**
     * 将渲染生成的文件复制到公共输出目录
     *
     * @param sourceDir 源目录（cacheDir/gridea_build）
     * @return 写入的文件数，失败抛异常
     */
    suspend fun copyToPublicOutput(sourceDir: File): Int = withContext(Dispatchers.IO) {
        if (!checkPermissionInternal()) {
            throw RuntimeException("未获得存储权限")
        }

        val destDir = ensureOutputDir()

        // 清空旧内容（仅清空 output 子目录内部，不删除目录本身，不影响 backup 子目录）
        destDir.listFiles()?.forEach { it.deleteRecursively() }

        var count = 0
        copyDirRecursively(sourceDir, destDir) { count++ }
        // 复制完成后刷新统计
        scanExistingFiles()
        count
    }

    /**
     * 递归复制目录
     */
    private fun copyDirRecursively(
        source: File,
        destDir: File,
        onFileCopied: () -> Unit
    ) {
        source.listFiles()?.forEach { file ->
            val destFile = File(destDir, file.name)
            if (file.isDirectory) {
                destFile.mkdirs()
                copyDirRecursively(file, destFile, onFileCopied)
            } else {
                file.copyTo(destFile, overwrite = true)
                onFileCopied()
            }
        }
    }

    /**
     * 扫描公共输出目录中的已有文件
     * 用于应用启动或用户手动触发，统计已有文件数量和总大小
     */
    suspend fun scanExistingFiles(): ScanResult = withContext(Dispatchers.IO) {
        val dir = publicOutputDir
        if (!dir.exists()) {
            _existingFileCount.value = 0
            _existingTotalSize.value = 0L
            return@withContext ScanResult(fileCount = 0, totalSize = 0L, hasIndex = false)
        }

        var count = 0
        var size = 0L
        var hasIndex = false
        walkFiles(dir) { file ->
            count++
            size += file.length()
            if (file.name == "index.html") hasIndex = true
        }
        _existingFileCount.value = count
        _existingTotalSize.value = size
        ScanResult(fileCount = count, totalSize = size, hasIndex = hasIndex)
    }

    /**
     * 递归遍历目录下所有文件
     */
    private fun walkFiles(dir: File, onFile: (File) -> Unit) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                walkFiles(file, onFile)
            } else {
                onFile(file)
            }
        }
    }

    /**
     * 清空公共输出目录
     */
    suspend fun clearPublicOutput(): Int = withContext(Dispatchers.IO) {
        val dir = publicOutputDir
        if (!dir.exists()) {
            _existingFileCount.value = 0
            _existingTotalSize.value = 0L
            return@withContext 0
        }
        var count = 0
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                walkFiles(file) { count++ }
                file.deleteRecursively()
            } else {
                count++
                file.delete()
            }
        }
        _existingFileCount.value = 0
        _existingTotalSize.value = 0L
        count
    }
}

/**
 * 扫描结果
 */
data class ScanResult(
    /** 文件总数 */
    val fileCount: Int,
    /** 总大小（字节） */
    val totalSize: Long,
    /** 是否包含 index.html（表示有可预览的站点） */
    val hasIndex: Boolean
)
