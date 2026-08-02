package com.gridea.android.data.repository

import android.content.Context
import android.net.Uri
import com.gridea.android.data.model.CommentSetting
import com.gridea.android.data.model.FriendLink
import com.gridea.android.data.model.GeneralSettings
import com.gridea.android.data.model.Menu
import com.gridea.android.data.model.Post
import com.gridea.android.data.model.Setting
import com.gridea.android.data.model.Tag
import com.gridea.android.data.model.Theme
import com.gridea.android.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据备份仓库
 *
 * 对应旧版 Gridea 0.9.3 中隐含的数据持久化（posts.json + posts 目录下的 md 文件）
 * 移动端改为统一的 ZIP 备份包，包含文章、设置、图片，用 SAF 让用户选择保存/读取位置
 *
 * 备份格式（ZIP）：
 * - backup.json — 文章 + 设置数据
 * - images/ — 文章图片目录
 */
@Singleton
class DataBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val postRepository: PostRepository,
    private val settingRepository: SettingRepository,
    private val imageRepository: ImageRepository,
    private val siteOutputRepository: SiteOutputRepository,
    private val tagRepository: TagRepository,
    private val menuRepository: MenuRepository,
    private val friendLinkRepository: FriendLinkRepository,
    private val themePackRepository: ThemePackRepository
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 扫描 backup 目录下已有的 Gridea 备份文件（.zip）
     *
     * 自动备份和手动备份的内容结构一致，都可被扫描到并导入。
     * 返回结果按最后修改时间倒序排列（最新的在最前面）。
     */
    suspend fun scanBackups(): List<BackupFileInfo> = withContext(Dispatchers.IO) {
        val dir = siteOutputRepository.ensureBackupDir()
        dir.listFiles { file -> file.isFile && file.name.endsWith(".zip", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                BackupFileInfo(
                    fileName = file.name,
                    absolutePath = file.absolutePath,
                    lastModified = file.lastModified(),
                    size = file.length()
                )
            } ?: emptyList()
    }

    /**
     * 获取备份目录的可读路径（用于显示给用户）
     */
    fun getBackupDisplayPath(): String {
        return "内部存储/Documents/Gridea/backup"
    }

    /**
     * 批量删除备份文件
     *
     * @param absolutePaths 待删除备份文件的绝对路径集合
     * @return 实际删除的文件数
     */
    suspend fun deleteBackups(absolutePaths: Set<String>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        absolutePaths.forEach { path ->
            val file = File(path)
            if (file.exists() && file.isFile && file.delete()) {
                deleted++
            }
        }
        deleted
    }

    /**
     * 清空所有备份文件
     *
     * @return 实际删除的文件数
     */
    suspend fun clearAllBackups(): Int = withContext(Dispatchers.IO) {
        val dir = siteOutputRepository.backupDir
        if (!dir.exists()) return@withContext 0
        var deleted = 0
        dir.listFiles { file -> file.isFile && file.name.endsWith(".zip", ignoreCase = true) }
            ?.forEach { file ->
                if (file.delete()) deleted++
            }
        deleted
    }

    /**
     * 导出全部数据（文章 + 设置 + 图片）到指定 Uri（ZIP 格式）
     *
     * @param destUri SAF 返回的目标文件 Uri
     * @return 导出的文章数，失败抛异常
     */
    suspend fun exportToUri(destUri: Uri): Int = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(destUri)
            ?: throw RuntimeException("无法写入文件")
        output.use {
            writeBackupZip(it)
        }
    }

    /**
     * 自动备份：将备份数据直接写入指定目录的 ZIP 文件（不通过 SAF）
     *
     * 供 BackupScheduler 定时调用，复用 [writeBackupZip] 的打包逻辑。
     * 文件名格式：backup_yyyy-MM-dd_HH-mm-ss.zip
     *
     * @param destDir 目标目录（若不存在会自动创建）
     * @return 生成的备份文件
     */
    suspend fun exportAutoBackup(destDir: File): File = withContext(Dispatchers.IO) {
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            .format(Date())
        val backupFile = File(destDir, "backup_$timestamp.zip")
        backupFile.outputStream().use { output ->
            writeBackupZip(output)
        }
        backupFile
    }

    /**
     * 将备份数据写入 ZIP 输出流（共享打包逻辑）
     *
     * ZIP 结构：
     * - backup.json — 文章 + 设置数据
     * - images/{name} — 文章图片
     *
     * @param outputStream 目标输出流（由本方法负责关闭）
     * @return 备份的文章数
     */
    private suspend fun writeBackupZip(outputStream: OutputStream): Int {
        val posts = postRepository.getAllPostsSync()
        val theme = settingRepository.getTheme().first()
        val setting = settingRepository.getSetting().first()
        val commentSetting = settingRepository.getCommentSetting().first()
        val generalSettings = settingRepository.getGeneralSettings()
        val tags = tagRepository.getAllList()
        val menus = menuRepository.getAllList()
        val friendLinks = friendLinkRepository.getAllList()

        // 主题包配置：活动主题 ID + 所有用户主题的 configValues 快照
        val activeThemeId = themePackRepository.getActiveThemeIdSync()
        val themeConfigs = themePackRepository.getAllUserThemeConfigs()
        val themePackBackup = ThemePackBackup(
            activeThemeId = activeThemeId,
            themeConfigs = themeConfigs
        )

        val backup = BackupData(
            version = 4,
            exportTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date()),
            posts = posts,
            theme = theme,
            setting = setting,
            commentSetting = commentSetting,
            generalSettings = generalSettings,
            tags = tags,
            menus = menus,
            friendLinks = friendLinks,
            themePackConfigs = themePackBackup
        )
        val jsonStr = json.encodeToString(BackupData.serializer(), backup)

        ZipOutputStream(outputStream).use { zip ->
            // 写入 backup.json
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(jsonStr.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 写入图片目录
            val imageDir = imageRepository.getImageDir()
            imageDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    zip.putNextEntry(ZipEntry("images/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }

        return posts.size
    }

    /**
     * 从指定 Uri 导入数据（支持 ZIP 和纯 JSON 两种格式）
     *
     * @param srcUri SAF 返回的源文件 Uri
     * @param onConflict 冲突处理策略（同名文章）
     * @return 导入的文章数，失败抛异常
     */
    suspend fun importFromUri(
        srcUri: Uri,
        onConflict: ConflictStrategy = ConflictStrategy.SKIP
    ): Int = withContext(Dispatchers.IO) {
        val rawStream = context.contentResolver.openInputStream(srcUri)
            ?: throw RuntimeException("无法读取文件")
        // 用 BufferedInputStream 支持 mark/reset
        val bufferedStream = java.io.BufferedInputStream(rawStream)

        var importedCount = 0

        // 检测文件类型：ZIP 还是纯 JSON
        val isZip = isZipFile(bufferedStream)
        if (isZip) {
            ZipInputStream(bufferedStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "backup.json" -> {
                            val jsonStr = zip.bufferedReader(Charsets.UTF_8).readText()
                            importedCount = importFromJson(jsonStr, onConflict)
                        }
                        entry.name.startsWith("images/") -> {
                            val fileName = entry.name.removePrefix("images/")
                            if (fileName.isNotEmpty()) {
                                val imageDir = imageRepository.getImageDir()
                                val destFile = File(imageDir, fileName)
                                destFile.outputStream().use { out -> zip.copyTo(out) }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } else {
            // 纯 JSON 格式（兼容旧版备份）
            val jsonStr = bufferedStream.bufferedReader(Charsets.UTF_8).readText()
            importedCount = importFromJson(jsonStr, onConflict)
        }
        bufferedStream.close()
        rawStream.close()

        // 刷新图片列表
        imageRepository.refreshImages()

        importedCount
    }

    /**
     * 从 JSON 字符串导入数据
     */
    private suspend fun importFromJson(
        jsonStr: String,
        onConflict: ConflictStrategy
    ): Int {
        val backup = json.decodeFromString(BackupData.serializer(), jsonStr)

        // 导入文章
        var importedCount = 0
        for (post in backup.posts) {
            val existing = postRepository.getPostByFileName(post.fileName)
            if (existing != null) {
                when (onConflict) {
                    ConflictStrategy.SKIP -> continue
                    ConflictStrategy.OVERWRITE -> {
                        postRepository.savePost(post)
                        importedCount++
                    }
                    ConflictStrategy.DUPLICATE -> {
                        val newPost = post.copy(
                            fileName = "${post.fileName}-imported-${System.currentTimeMillis()}"
                        )
                        postRepository.savePost(newPost)
                        importedCount++
                    }
                }
            } else {
                postRepository.savePost(post)
                importedCount++
            }
        }

        // 导入设置（v2 格式）
        if (backup.version >= 2) {
            backup.theme?.let { settingRepository.saveTheme(it) }
            backup.setting?.let { settingRepository.saveSetting(it) }
            backup.commentSetting?.let { settingRepository.saveCommentSetting(it) }
        }

        // 导入标签 / 菜单 / 友链 / 通用偏好（v3 格式）
        if (backup.version >= 3) {
            // 标签：按 name 主键覆盖（REPLACE 策略）
            if (backup.tags.isNotEmpty()) {
                tagRepository.saveTags(backup.tags)
            }
            // 菜单：覆盖式恢复（先清空非回收站，再插入）
            backup.menus?.let { menuRepository.replaceAll(it) }
            // 友链：覆盖式恢复（先清空非回收站，再插入）
            backup.friendLinks?.let { friendLinkRepository.replaceAll(it) }
            // 通用偏好：覆盖式恢复
            backup.generalSettings?.let { settingRepository.saveGeneralSettings(it) }
        }

        // 导入主题包自定义配置（v4 格式）
        if (backup.version >= 4) {
            backup.themePackConfigs?.let { themePackBackup ->
                AppLogger.i(
                    "Backup",
                    "开始恢复主题包配置: activeThemeId=${themePackBackup.activeThemeId}, " +
                        "主题数=${themePackBackup.themeConfigs.size}"
                )
                if (themePackBackup.themeConfigs.isNotEmpty()) {
                    themePackRepository.restoreThemeConfigs(themePackBackup.themeConfigs)
                    val totalConfigCount = themePackBackup.themeConfigs.values.sumOf { it.size }
                    AppLogger.action(
                        "Backup", "RestoreThemeConfigs",
                        "已恢复 $totalConfigCount 条配置（${themePackBackup.themeConfigs.size} 个主题）"
                    )
                }
                if (themePackBackup.activeThemeId.isNotEmpty()) {
                    themePackRepository.setActiveTheme(themePackBackup.activeThemeId)
                    AppLogger.i(
                        "Backup",
                        "已恢复活动主题 ID: ${themePackBackup.activeThemeId}"
                    )
                }
            } ?: AppLogger.i("Backup", "v4 备份文件中无 themePackConfigs 字段，跳过主题配置恢复")
        }

        return importedCount
    }

    /**
     * 检测输入流是否为 ZIP 文件（读取前 2 字节魔数 PK）
     */
    private fun isZipFile(stream: java.io.BufferedInputStream): Boolean {
        return try {
            val header = ByteArray(2)
            stream.mark(2)
            val read = stream.read(header)
            stream.reset()
            // ZIP 魔数：PK
            read == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * 备份文件数据结构
 * version 1: 仅文章
 * version 2: 文章 + 设置（theme/setting/commentSetting）
 * version 3: 文章 + 设置 + 标签 + 菜单 + 友链 + 通用偏好
 * version 4: 在 v3 基础上增加 ThemePack 用户自定义配置（configValues）和活动主题 ID
 */
@Serializable
data class BackupData(
    val version: Int = 1,
    val exportTime: String = "",
    val posts: List<Post> = emptyList(),
    val theme: Theme? = null,
    val setting: Setting? = null,
    val commentSetting: CommentSetting? = null,
    val generalSettings: GeneralSettings? = null,
    val tags: List<Tag> = emptyList(),
    val menus: List<Menu>? = null,
    val friendLinks: List<FriendLink>? = null,
    val themePackConfigs: ThemePackBackup? = null
)

/**
 * 主题包备份结构
 *
 * - [activeThemeId]：当前激活主题 ID（用于恢复时切换活动主题）
 * - [themeConfigs]：所有用户主题的配置值快照，外层 key 为 themeId，
 *   内层 key 为配置项 name（value 全部为字符串，导入时由 ThemePackRepository
 *   统一写回 DataStore，读取时再按 type 还原）
 */
@Serializable
data class ThemePackBackup(
    val activeThemeId: String = "",
    val themeConfigs: Map<String, Map<String, String>> = emptyMap()
)

/**
 * 导入冲突处理策略
 */
enum class ConflictStrategy {
    /** 跳过同名文章 */
    SKIP,
    /** 覆盖同名文章 */
    OVERWRITE,
    /** 创建副本（重命名） */
    DUPLICATE
}

/**
 * 扫描到的备份文件信息
 */
data class BackupFileInfo(
    val fileName: String,
    val absolutePath: String,
    val lastModified: Long,
    val size: Long
)
