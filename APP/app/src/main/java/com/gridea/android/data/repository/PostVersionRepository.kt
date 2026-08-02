package com.gridea.android.data.repository

import com.gridea.android.data.db.dao.PostVersionDao
import com.gridea.android.data.db.entity.PostVersionEntity
import com.gridea.android.data.model.PostVersion
import com.gridea.android.data.model.Post
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 文章版本历史仓库
 *
 * 管理文章版本快照的创建、查询、删除。
 * 每次自动保存时调用 [saveVersion] 创建快照，
 * 每篇文章最多保留 [PostVersionEntity.MAX_VERSIONS_PER_POST] 个版本。
 */
@Singleton
class PostVersionRepository @Inject constructor(
    private val postVersionDao: PostVersionDao
) {

    /**
     * 保存文章版本快照
     *
     * 若该文章版本数已达上限，自动删除最旧的版本。
     *
     * @param post 当前文章状态
     */
    suspend fun saveVersion(post: Post) {
        val fileName = post.fileName

        // 检查版本数量，超出上限则删除最旧的
        val count = postVersionDao.getVersionCount(fileName)
        if (count >= PostVersionEntity.MAX_VERSIONS_PER_POST) {
            postVersionDao.getOldestVersion(fileName)?.let { oldest ->
                postVersionDao.deleteVersion(oldest.id)
            }
        }

        val version = PostVersionEntity(
            postFileName = fileName,
            title = post.data.title,
            content = post.content,
            tags = post.data.tags.joinToString(","),
            savedAt = System.currentTimeMillis()
        )
        postVersionDao.insertVersion(version)
    }

    /**
     * 获取某篇文章的所有版本（按时间倒序）
     */
    suspend fun getVersions(fileName: String): List<PostVersion> {
        return postVersionDao.getVersionsByFileName(fileName).map { it.toPostVersion() }
    }

    /**
     * 根据 ID 获取版本
     */
    suspend fun getVersionById(id: Long): PostVersion? {
        return postVersionDao.getVersionById(id)?.toPostVersion()
    }

    /**
     * 删除指定版本
     */
    suspend fun deleteVersion(id: Long) {
        postVersionDao.deleteVersion(id)
    }

    /**
     * 删除某篇文章的所有版本（文章被删除时调用）
     */
    suspend fun deleteVersionsByFileName(fileName: String) {
        postVersionDao.deleteVersionsByFileName(fileName)
    }

    // ===== 模型转换 =====

    private fun PostVersionEntity.toPostVersion(): PostVersion {
        return PostVersion(
            id = id,
            postFileName = postFileName,
            title = title,
            content = content,
            tags = if (tags.isEmpty()) emptyList() else tags.split(","),
            savedAt = savedAt
        )
    }
}
