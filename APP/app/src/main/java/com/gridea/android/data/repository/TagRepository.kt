package com.gridea.android.data.repository

import com.gridea.android.data.db.dao.PostDao
import com.gridea.android.data.db.dao.TagDao
import com.gridea.android.data.db.entity.TagEntity
import com.gridea.android.data.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 标签仓库
 *
 * 对应旧版 Gridea 0.9.3 的 src/server/tags.ts
 * 负责标签的 CRUD 以及从文章中聚合标签
 */
@Singleton
class TagRepository @Inject constructor(
    private val tagDao: TagDao,
    private val postDao: PostDao,
    private val postRepository: PostRepository
) {

    /**
     * 获取所有标签（含文章计数）
     * 对应旧版 Tags.list()
     */
    fun getAllTags(): Flow<List<TagWithCount>> {
        return combine(tagDao.getAllTags(), postDao.getAllPosts()) { tagEntities, postEntities ->
            // 从所有文章中聚合标签名 → 使用次数
            val tagCountMap = mutableMapOf<String, Int>()
            postEntities.forEach { post ->
                if (post.tags.isNotEmpty()) {
                    post.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tagName ->
                        tagCountMap[tagName] = (tagCountMap[tagName] ?: 0) + 1
                    }
                }
            }

            // 合并：数据库中已有的标签 + 文章中出现但数据库未记录的标签
            val existingNames = tagEntities.map { it.name }.toMutableSet()
            val result = mutableListOf<TagWithCount>()

            // 已有标签，更新计数
            tagEntities.forEach { entity ->
                val count = tagCountMap[entity.name] ?: 0
                result.add(
                    TagWithCount(
                        name = entity.name,
                        slug = entity.slug,
                        postCount = count,
                        used = count > 0
                    )
                )
            }

            // 文章中出现但标签表未记录的（自动补充）
            tagCountMap.keys.filter { it !in existingNames }.forEach { tagName ->
                result.add(
                    TagWithCount(
                        name = tagName,
                        slug = tagName,
                        postCount = tagCountMap[tagName] ?: 0,
                        used = true
                    )
                )
            }

            result.sortedByDescending { it.postCount }
        }
    }

    /**
     * 保存标签
     * 对应旧版 Tags.saveTag()
     */
    suspend fun saveTag(tag: Tag) {
        tagDao.insertTag(tag.toEntity())
    }

    /**
     * 同步获取所有标签（排除回收站），用于数据备份
     */
    suspend fun getAllList(): List<Tag> = tagDao.getAllList().map { entity ->
        Tag(
            name = entity.name,
            used = entity.usedCount > 0,
            slug = entity.slug
        )
    }

    /**
     * 批量保存标签（用于数据恢复，REPLACE 策略按 name 主键覆盖）
     */
    suspend fun saveTags(tags: List<Tag>) {
        tagDao.insertTags(tags.map { it.toEntity() })
    }

    /**
     * 新建标签（若已存在则忽略）
     */
    suspend fun createTag(name: String) {
        val existing = tagDao.getTagByName(name)
        if (existing == null) {
            tagDao.insertTag(TagEntity(
                name = name,
                slug = name,
                usedCount = 0
            ))
        }
    }

    /**
     * 删除标签（软删除，移入回收站）
     * - 从标签表中标记为已删除
     * - 从所有文章的 tags 字段中移除该标签引用（解除关联）
     * @return 受影响的文章数量
     */
    suspend fun deleteTag(name: String): Int {
        tagDao.deleteTag(name)
        return postRepository.removeTagFromAllPosts(name)
    }

    /**
     * 获取回收站中的标签
     */
    fun getTrashedTags(): Flow<List<TagEntity>> {
        return tagDao.getTrashedTags()
    }

    /**
     * 恢复标签（从回收站恢复）
     */
    suspend fun restoreTag(name: String) {
        tagDao.restoreTag(name)
    }

    /**
     * 批量恢复标签
     */
    suspend fun restoreTags(names: Collection<String>) {
        names.forEach { tagDao.restoreTag(it) }
    }

    /**
     * 彻底删除标签
     */
    suspend fun permanentDeleteTag(name: String) {
        tagDao.permanentDeleteTag(name)
    }

    /**
     * 批量彻底删除标签
     */
    suspend fun permanentDeleteTags(names: Collection<String>) {
        names.forEach { tagDao.permanentDeleteTag(it) }
    }

    /**
     * 清理超过 3 天的回收站标签
     */
    suspend fun cleanExpiredTrash() {
        val expireBefore = System.currentTimeMillis() - PostRepository.TRASH_RETENTION_MS
        tagDao.deleteExpiredTrash(expireBefore)
    }

    // ===== 模型转换方法 =====

    private fun Tag.toEntity(): TagEntity {
        return TagEntity(
            name = name,
            slug = slug ?: name,
            usedCount = if (used) 1 else 0
        )
    }
}

/**
 * 带文章计数的标签
 * 对应旧版 ISiteTagsData（含 count 字段）
 */
data class TagWithCount(
    val name: String,
    val slug: String,
    val postCount: Int,
    val used: Boolean
)
