package com.gridea.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gridea.android.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/**
 * 标签数据访问对象
 *
 * 对应旧版 Gridea 0.9.3 的 src/server/tags.ts 中的数据操作方法
 */
@Dao
interface TagDao {

    /**
     * 获取所有标签（排除回收站中的）
     * 对应旧版 Tags.list() 方法
     */
    @Query("SELECT * FROM tags WHERE isTrashed = 0 ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    /**
     * 同步获取所有标签（排除回收站中的），用于数据备份
     */
    @Query("SELECT * FROM tags WHERE isTrashed = 0 ORDER BY name ASC")
    suspend fun getAllList(): List<TagEntity>

    /**
     * 获取已使用的标签（排除回收站中的）
     * 对应旧版 ITag.used = true 的过滤逻辑
     */
    @Query("SELECT * FROM tags WHERE usedCount > 0 AND isTrashed = 0 ORDER BY name ASC")
    fun getUsedTags(): Flow<List<TagEntity>>

    /**
     * 根据名称获取标签（排除回收站中的）
     */
    @Query("SELECT * FROM tags WHERE name = :name AND isTrashed = 0 LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    /**
     * 插入或替换标签
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity)

    /**
     * 批量插入标签
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TagEntity>)

    /**
     * 软删除标签（移入回收站）
     */
    @Query("UPDATE tags SET isTrashed = 1, trashedAt = :trashedAt WHERE name = :name")
    suspend fun deleteTag(name: String, trashedAt: Long = System.currentTimeMillis())

    /**
     * 恢复标签（从回收站恢复）
     */
    @Query("UPDATE tags SET isTrashed = 0, trashedAt = 0 WHERE name = :name")
    suspend fun restoreTag(name: String)

    /**
     * 彻底删除标签
     */
    @Query("DELETE FROM tags WHERE name = :name")
    suspend fun permanentDeleteTag(name: String)

    /**
     * 获取回收站中的标签
     */
    @Query("SELECT * FROM tags WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun getTrashedTags(): Flow<List<TagEntity>>

    /**
     * 清理超过指定时间戳的回收站标签
     */
    @Query("DELETE FROM tags WHERE isTrashed = 1 AND trashedAt < :timestamp")
    suspend fun deleteExpiredTrash(timestamp: Long)

    /**
     * 更新标签使用计数
     */
    @Query("UPDATE tags SET usedCount = :count WHERE name = :name")
    suspend fun updateTagUsedCount(name: String, count: Int)
}
