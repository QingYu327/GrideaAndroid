package com.gridea.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gridea.android.data.db.entity.PostVersionEntity

/**
 * 文章版本历史数据访问对象
 */
@Dao
interface PostVersionDao {

    /**
     * 插入版本快照
     */
    @Insert
    suspend fun insertVersion(version: PostVersionEntity): Long

    /**
     * 获取某篇文章的所有版本（按时间倒序）
     */
    @Query("SELECT * FROM post_versions WHERE postFileName = :fileName ORDER BY savedAt DESC")
    suspend fun getVersionsByFileName(fileName: String): List<PostVersionEntity>

    /**
     * 获取某篇文章的版本数量
     */
    @Query("SELECT COUNT(*) FROM post_versions WHERE postFileName = :fileName")
    suspend fun getVersionCount(fileName: String): Int

    /**
     * 获取某篇文章最旧的版本（用于超出上限时删除）
     */
    @Query("SELECT * FROM post_versions WHERE postFileName = :fileName ORDER BY savedAt ASC LIMIT 1")
    suspend fun getOldestVersion(fileName: String): PostVersionEntity?

    /**
     * 根据 ID 获取版本
     */
    @Query("SELECT * FROM post_versions WHERE id = :id")
    suspend fun getVersionById(id: Long): PostVersionEntity?

    /**
     * 根据 ID 删除版本
     */
    @Query("DELETE FROM post_versions WHERE id = :id")
    suspend fun deleteVersion(id: Long)

    /**
     * 删除某篇文章的所有版本（文章被删除时调用）
     */
    @Query("DELETE FROM post_versions WHERE postFileName = :fileName")
    suspend fun deleteVersionsByFileName(fileName: String)

    /**
     * 重命名版本历史中的 postFileName 引用（文章 URL 变更时同步迁移版本历史）
     */
    @Query("UPDATE post_versions SET postFileName = :newFileName WHERE postFileName = :oldFileName")
    suspend fun renameFileName(oldFileName: String, newFileName: String)
}
