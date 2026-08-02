package com.gridea.android.data.repository

import com.gridea.android.data.db.dao.PostDao
import com.gridea.android.data.db.entity.PostEntity
import com.gridea.android.data.model.Post
import com.gridea.android.data.model.PostData
import com.gridea.android.data.model.TrashedPost
import com.gridea.android.util.MarkdownUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 文章仓库
 *
 * 对应旧版 Gridea 0.9.3 的 src/server/posts.ts
 * 负责文章的 CRUD 操作以及领域模型与数据库实体的转换
 */
@Singleton
class PostRepository @Inject constructor(
    private val postDao: PostDao,
    private val postVersionDao: com.gridea.android.data.db.dao.PostVersionDao
) {

    /**
     * 获取所有文章
     * 对应旧版 Posts.list()
     */
    fun getAllPosts(): Flow<List<Post>> {
        return postDao.getAllPosts().map { entities ->
            entities.map { it.toPost() }
        }
    }

    /**
     * 获取已发布文章
     */
    fun getPublishedPosts(): Flow<List<Post>> {
        return postDao.getPublishedPosts().map { entities ->
            entities.map { it.toPost() }
        }
    }

    /**
     * 根据文件名获取文章
     */
    suspend fun getPostByFileName(fileName: String): Post? {
        return postDao.getPostByFileName(fileName)?.toPost()
    }

    /**
     * 保存文章
     * 对应旧版 Posts.savePostToFile()
     */
    suspend fun savePost(post: Post) {
        val entity = post.toEntity()
        postDao.insertPost(entity)
    }

    /**
     * 移入回收站（软删除）
     * 对应旧版 Posts.deletePost()
     * 仅标记为已删除，不删除版本历史，3 天后由清理逻辑自动物理删除
     */
    suspend fun deletePost(fileName: String) {
        postDao.deletePost(fileName, System.currentTimeMillis())
    }

    /**
     * 获取回收站文章列表
     */
    fun getTrashedPosts(): Flow<List<TrashedPost>> {
        return postDao.getTrashedPosts().map { entities ->
            entities.map { TrashedPost(it.toPost(), it.trashedAt) }
        }
    }

    /**
     * 从回收站恢复单篇文章
     */
    suspend fun restorePost(fileName: String) {
        postDao.restorePost(fileName)
    }

    /**
     * 彻底删除单篇文章（物理删除，同时删除版本历史）
     */
    suspend fun permanentDeletePost(fileName: String) {
        postDao.permanentDeletePost(fileName)
        postVersionDao.deleteVersionsByFileName(fileName)
    }

    /**
     * 重命名文章的 fileName（主键）
     *
     * 用于用户修改文章 URL 时同步更新数据库主键，避免旧记录残留导致重复文章。
     * 同时迁移版本历史中的 postFileName 引用，保留历史版本。
     *
     * @param oldFileName 原始 fileName（加载时的 URL）
     * @param newFileName 新 fileName（用户修改后的 URL）
     */
    suspend fun renamePost(oldFileName: String, newFileName: String) {
        postDao.renameFileName(oldFileName, newFileName)
        postVersionDao.renameFileName(oldFileName, newFileName)
    }

    /**
     * 批量彻底删除文章
     */
    suspend fun permanentDeletePosts(fileNames: Collection<String>) {
        fileNames.forEach { fileName ->
            postDao.permanentDeletePost(fileName)
            postVersionDao.deleteVersionsByFileName(fileName)
        }
    }

    /**
     * 批量从回收站恢复文章
     */
    suspend fun restorePosts(fileNames: Collection<String>) {
        fileNames.forEach { fileName ->
            postDao.restorePost(fileName)
        }
    }

    /**
     * 清理超过 3 天的回收站文章
     */
    suspend fun cleanExpiredTrash() {
        val expireBefore = System.currentTimeMillis() - TRASH_RETENTION_MS
        postDao.deleteExpiredTrash(expireBefore)
    }

    companion object {
        /** 回收站保留时长：3 天（毫秒） */
        const val TRASH_RETENTION_MS = 3 * 24 * 60 * 60 * 1000L
    }

    /**
     * 搜索文章
     */
    fun searchPosts(query: String): Flow<List<Post>> {
        return postDao.searchPosts(query).map { entities ->
            entities.map { it.toPost() }
        }
    }

    /**
     * 获取文章总数
     */
    suspend fun getPostCount(): Int = postDao.getPostCount()

    /**
     * 一次性获取所有文章（非 Flow，用于渲染器）
     */
    suspend fun getAllPostsSync(): List<Post> {
        return postDao.getAllPosts().first().map { it.toPost() }
    }

    /**
     * 更新所有文章中对图片 URL 的引用（用于图片重命名后同步内容）
     *
     * @param oldUrl 旧图片 URL（file://...）
     * @param newUrl 新图片 URL（file://...）
     * @return 受影响的文章数量
     */
    suspend fun updateImageReferences(oldUrl: String, newUrl: String): Int {
        val entities = postDao.getAllPosts().first()
        var affected = 0
        entities.forEach { entity ->
            if (entity.content.contains(oldUrl)) {
                val newContent = entity.content.replace(oldUrl, newUrl)
                postDao.updatePost(
                    entity.copy(
                        content = newContent,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                affected++
            }
        }
        return affected
    }

    /**
     * 根据标签名获取文章
     * 对应旧版 renderer.ts 中 renderTagDetail() 的文章筛选
     */
    fun getPostsByTag(tagName: String): Flow<List<Post>> {
        return postDao.getPostsByTag(tagName).map { entities ->
            entities.map { it.toPost() }.filter { post ->
                // 精确匹配标签名（避免 LIKE 匹配到子串）
                post.data.tags.any { it.equals(tagName, ignoreCase = true) }
            }
        }
    }

    /**
     * 从所有文章中移除指定标签引用（用于标签删除时同步清理）
     * @return 受影响的文章数量
     */
    suspend fun removeTagFromAllPosts(tagName: String): Int {
        val entities = postDao.getAllPosts().first()
        var affected = 0
        entities.forEach { entity ->
            val currentTags = if (entity.tags.isEmpty()) emptyList()
                              else entity.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (currentTags.any { it.equals(tagName, ignoreCase = true) }) {
                val newTags = currentTags.filter { !it.equals(tagName, ignoreCase = true) }
                postDao.updatePost(
                    entity.copy(
                        tags = newTags.joinToString(","),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                affected++
            }
        }
        return affected
    }

    // ===== 模型转换方法 =====

    /**
     * 数据库实体转领域模型
     */
    private fun PostEntity.toPost(): Post {
        return Post(
            content = content,
            fileName = fileName,
            data = PostData(
                title = title,
                date = date,
                published = published,
                hideInList = hideInList,
                tags = if (tags.isEmpty()) emptyList() else tags.split(","),
                feature = feature,
                isTop = isTop,
                writingTime = writingTime
            )
        )
    }

    /**
     * 领域模型转数据库实体
     */
    private fun Post.toEntity(): PostEntity {
        // 从 content 中提取摘要（对应旧版 moreReg 正则匹配逻辑）
        val abstract = MarkdownUtils.extractAbstract(content)

        return PostEntity(
            fileName = fileName,
            title = data.title,
            date = data.date,
            content = content,
            tags = data.tags.joinToString(","),
            published = data.published,
            hideInList = data.hideInList,
            isTop = data.isTop,
            feature = data.feature,
            abstract = abstract,
            writingTime = data.writingTime
        )
    }
}
