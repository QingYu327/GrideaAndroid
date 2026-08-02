package com.gridea.android.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
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
 * 图片仓库
 *
 * 对应旧版 Gridea 0.9.3 的 src/server/posts.ts 中图片上传逻辑
 *
 * 存储策略：
 * - 文章图片存于 filesDir/post-images/ 目录（应用私有，无需权限）
 * - 命名：时间戳 + 原扩展名（避免冲突）
 * - Markdown 引用：file://绝对路径（编辑态），渲染时替换为 domain/post-images/
 *
 * 与旧版的差异：
 * - 旧版用 appDir/post-images/（用户文档目录），移动端用 filesDir/post-images/（应用沙箱）
 * - 移动端新增图片库页面，弥补旧版无图片管理的缺陷
 */
@Singleton
class ImageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** 图片最大边长（超过此尺寸将压缩） */
        private const val MAX_IMAGE_DIMENSION = 1920
        /** WEBP 压缩质量 */
        private const val WEBP_QUALITY = 85
    }

    /** 图片存储目录 */
    private val imageDir = File(context.filesDir, "post-images").apply { mkdirs() }

    /** 图片列表（文件名排序，最新的在后） */
    private val _images = MutableStateFlow<List<ImageInfo>>(emptyList())
    val images: StateFlow<List<ImageInfo>> = _images.asStateFlow()

    init {
        refreshImages()
    }

    /**
     * 刷新图片列表
     */
    fun refreshImages() {
        _images.value = imageDir.listFiles { f -> f.isFile }
            ?.sortedBy { it.lastModified() }
            ?.map { it.toImageInfo() }
            ?: emptyList()
    }

    /**
     * 保存图片 Uri 到 post-images 目录
     * 对应旧版 uploadImages()
     *
     * @param uri 图片的 content Uri（来自相册/文件选择器）
     * @return 保存后的本地文件路径（用于 Markdown 引用）
     */
    suspend fun saveImageFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        val savedFile = saveImageFromUriInternal(uri) ?: return@withContext null
        refreshImages()
        "file://${savedFile.absolutePath}"
    }

    /**
     * 保存图片 Uri 的内部实现（不刷新列表，供批量导入复用）
     *
     * 流程：
     * 1. GIF 直接复制保留原格式（压缩会破坏动画）
     * 2. 其他图片先写入临时文件，再调用 compressImage 压缩为 WEBP
     * 3. 最终保存压缩后的文件到 post-images 目录
     *
     * @return 保存后的文件对象，失败返回 null
     */
    private suspend fun saveImageFromUriInternal(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val mimeType = context.contentResolver.getType(uri) ?: "image/png"

            // GIF 跳过压缩，保留原格式（压缩会破坏动画）
            if (mimeType.equals("image/gif", ignoreCase = true)) {
                val gifName = "${System.currentTimeMillis()}.gif"
                val gifFile = File(imageDir, gifName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    gifFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
                return@withContext gifFile
            }

            // 推断原始扩展名（压缩未带来收益时使用）
            val originalExtension = when {
                mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                mimeType.contains("png") -> "png"
                mimeType.contains("webp") -> "webp"
                else -> "png"
            }

            // 先把原始 URI 内容写到临时文件
            val tempFile = File(context.cacheDir, "img_temp_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null

            // 调用 compressImage 压缩，返回压缩后的文件（WEBP 或原始临时文件）
            val compressedFile = compressImage(tempFile)

            // 根据压缩结果确定最终扩展名：WEBP 文件用 webp，否则保留原始扩展名
            val finalExtension = if (compressedFile.extension.equals("webp", ignoreCase = true)) {
                "webp"
            } else {
                originalExtension
            }
            val destFile = File(imageDir, "${System.currentTimeMillis()}.$finalExtension")

            // 复制到 post-images 目录，并清理临时文件
            compressedFile.inputStream().use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            compressedFile.delete()

            destFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 重命名图片
     * 保持原扩展名不变，仅修改文件名主体
     *
     * @param image 待重命名的图片信息
     * @param newName 用户输入的新文件名（可带或不带扩展名）
     * @return 新的图片 URL（file://...），失败返回 null
     */
    suspend fun renameImage(image: ImageInfo, newName: String): String? = withContext(Dispatchers.IO) {
        try {
            val oldFile = File(image.path)
            if (!oldFile.exists()) return@withContext null

            // 提取原文件扩展名（保留原扩展名）
            val oldExtension = oldFile.extension
            // 处理用户输入：去除可能的扩展名、空格和路径分隔符
            val sanitized = newName.trim()
                .replace(Regex("[/\\\\:*?\"<>|]"), "")
                .trim()
            if (sanitized.isEmpty()) return@withContext null

            // 去除用户可能输入的扩展名，再补上原扩展名
            val nameWithoutExt = if (sanitized.endsWith(".$oldExtension", ignoreCase = true)) {
                sanitized.dropLast(oldExtension.length + 1)
            } else {
                val dotIndex = sanitized.lastIndexOf('.')
                if (dotIndex > 0) sanitized.substring(0, dotIndex) else sanitized
            }

            val newFileName = if (oldExtension.isEmpty()) nameWithoutExt else "$nameWithoutExt.$oldExtension"
            val newFile = File(imageDir, newFileName)

            // 名称未变化直接返回原 URL
            if (newFile == oldFile) return@withContext image.url

            // 目标已存在则失败
            if (newFile.exists()) return@withContext null

            val renamed = oldFile.renameTo(newFile)
            if (!renamed) return@withContext null

            refreshImages()
            "file://${newFile.absolutePath}"
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 批量导入图片
     *
     * @param uris 多个图片 content Uri
     * @param onProgress 进度回调（current 当前已处理，total 总数）
     * @return 成功导入的数量
     */
    suspend fun importImages(
        uris: List<Uri>,
        onProgress: (current: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        var success = 0
        val total = uris.size
        uris.forEachIndexed { index, uri ->
            try {
                val saved = saveImageFromUriInternal(uri)
                if (saved != null) success++
            } catch (e: Exception) {
                // 单张失败不影响其他图片导入
            }
            onProgress(index + 1, total)
        }
        refreshImages()
        success
    }

    /**
     * 从 Bitmap 保存图片（用于剪贴板图片保存）
     *
     * 直接压缩为 WEBP 格式保存，并对超过 MAX_IMAGE_DIMENSION 的尺寸进行缩放
     *
     * @param bitmap 位图数据
     * @return 保存后的本地文件 URL（file://...），失败返回 null
     */
    suspend fun saveImageFromBitmap(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            // 如果尺寸超过限制，用 Matrix 缩放到 1920px 以内
            val targetBitmap = if (maxOf(bitmap.width, bitmap.height) > MAX_IMAGE_DIMENSION) {
                val scale = MAX_IMAGE_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
                val matrix = Matrix().apply { postScale(scale, scale) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            val fileName = "${System.currentTimeMillis()}.webp"
            val destFile = File(imageDir, fileName)
            destFile.outputStream().use { out ->
                targetBitmap.compress(Bitmap.CompressFormat.WEBP, WEBP_QUALITY, out)
            }
            refreshImages()
            "file://${destFile.absolutePath}"
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 压缩图片文件
     *
     * 流程：
     * 1. 使用 inJustDecodeBounds 获取原始尺寸
     * 2. 计算采样率 inSampleSize（2 的幂次）降低解码内存
     * 3. 解码为 Bitmap，若尺寸仍超过 MAX_IMAGE_DIMENSION 则用 Matrix 缩放
     * 4. 保存为 WEBP 格式（质量 85），替换原始临时文件
     * 5. 若压缩后文件比原始文件大，则保留原始文件
     *
     * @param file 原始临时文件
     * @return 压缩后的文件（WEBP），或在压缩无收益时返回原始文件
     */
    private fun compressImage(file: File): File {
        try {
            // 先解码边界信息获取原始尺寸
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

            // 无法解码（非图片或损坏），返回原文件
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return file
            }

            val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)

            // 计算采样率（2 的幂次）：保证采样后尺寸仍大于目标尺寸，便于后续 Matrix 精确缩放
            var sampleSize = 1
            while (maxDim / sampleSize > MAX_IMAGE_DIMENSION * 2) {
                sampleSize *= 2
            }

            // 解码为 Bitmap
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return file

            // 如果尺寸仍超过 MAX_IMAGE_DIMENSION，用 Matrix 缩放
            val targetBitmap = if (maxOf(bitmap.width, bitmap.height) > MAX_IMAGE_DIMENSION) {
                val scale = MAX_IMAGE_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
                val matrix = Matrix().apply { postScale(scale, scale) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            // 保存为 WEBP 格式，命名为 ${时间戳}.webp
            val webpFile = File(file.parentFile, "${System.currentTimeMillis()}.webp")
            try {
                webpFile.outputStream().use { out ->
                    targetBitmap.compress(Bitmap.CompressFormat.WEBP, WEBP_QUALITY, out)
                }
            } catch (e: Exception) {
                webpFile.delete()
                return file
            }

            // 压缩后文件大小若比原始文件还大，则保留原始文件
            if (webpFile.length() >= file.length()) {
                webpFile.delete()
                return file
            }

            // 删除原始临时文件，返回压缩后的 WEBP 文件
            file.delete()
            return webpFile
        } catch (e: Exception) {
            return file
        }
    }

    /**
     * 删除图片
     */
    suspend fun deleteImage(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val path = filePath.removePrefix("file://")
            val file = File(path)
            val deleted = file.delete()
            if (deleted) refreshImages()
            deleted
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取图片目录的绝对路径（用于渲染时复制到输出目录）
     */
    fun getImageDir(): File = imageDir

    /**
     * 将 Markdown 中的 file:// 本地图片路径替换为 web 可访问路径
     * 对应旧版 content-helper.ts 的 changeImageUrlLocalToDomain()
     *
     * @param domain 站点域名。为空时使用相对路径 post-images（兼容 file:// 预览和 <base> 标签）
     */
    fun replaceLocalImageUrls(content: String, domain: String): String {
        val localPrefix = "file://${imageDir.absolutePath}"
        val domainPrefix = if (domain.isEmpty()) {
            "post-images"
        } else {
            "${domain.trimEnd('/')}/post-images"
        }
        return content.replace(localPrefix, domainPrefix)
    }

    /**
     * 将 web 图片路径替换回 file:// 本地路径（编辑态）
     * 对应旧版 content-helper.ts 的 changeImageUrlDomainToLocal()
     *
     * @param domain 站点域名。为空时尝试匹配相对路径 post-images
     */
    fun replaceDomainImageUrls(content: String, domain: String): String {
        val localPrefix = "file://${imageDir.absolutePath}"
        val domainPrefix = if (domain.isEmpty()) {
            "post-images"
        } else {
            "${domain.trimEnd('/')}/post-images"
        }
        return content.replace(domainPrefix, localPrefix)
    }

    private fun File.toImageInfo() = ImageInfo(
        name = name,
        path = absolutePath,
        url = "file://$absolutePath",
        size = length(),
        lastModified = lastModified()
    )
}

/**
 * 图片信息
 */
data class ImageInfo(
    val name: String,
    val path: String,
    val url: String,
    val size: Long,
    val lastModified: Long
)
