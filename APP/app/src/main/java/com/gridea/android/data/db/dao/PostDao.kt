package com.gridea.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gridea.android.data.db.entity.PostEntity
import kotlinx.coroutines.flow.Flow

/**
 * 文章数据访问对象
 *
 * 对应旧版 Gridea 0.9.3 的 src/server/posts.ts 中的数据操作方法
 */
@Dao
interface PostDao {

    /**
     * 获取所有文章（按日期倒序，排除回收站文章）
     * 对应旧版 Posts.list() 方法
     */
    @Query("SELECT * FROM posts WHERE isTrashed = 0 ORDER BY date DESC")
    fun getAllPosts(): Flow<List<PostEntity>>

    /**
     * 获取已发布的文章（排除回收站文章）
     */
    @Query("SELECT * FROM posts WHERE published = 1 AND isTrashed = 0 ORDER BY date DESC")
    fun getPublishedPosts(): Flow<List<PostEntity>>

    /**
     * 根据文件名获取单篇文章
     * 对应旧版读取单个 Markdown 文件的逻辑
     */
    @Query("SELECT * FROM posts WHERE fileName = :fileName LIMIT 1")
    suspend fun getPostByFileName(fileName: String): PostEntity?

    /**
     * 插入或替换文章
     * 对应旧版 Posts.savePostToFile() 方法
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: PostEntity)

    /**
     * 更新文章
     */
    @Update
    suspend fun updatePost(post: PostEntity)

    /**
     * 移入回收站（软删除）
     * 对应旧版 Posts.deletePost() 方法，现改为软删除
     */
    @Query("UPDATE posts SET isTrashed = 1, trashedAt = :currentTime WHERE fileName = :fileName")
    suspend fun deletePost(fileName: String, currentTime: Long)

    /**
     * 从回收站恢复文章
     */
    @Query("UPDATE posts SET isTrashed = 0, trashedAt = 0 WHERE fileName = :fileName")
    suspend fun restorePost(fileName: String)

    /**
     * 彻底删除文章（物理删除）
     */
    @Query("DELETE FROM posts WHERE fileName = :fileName")
    suspend fun permanentDeletePost(fileName: String)

    /**
     * 重命名文章的 fileName（主键）
     * 用于用户修改文章 URL 时同步更新数据库主键，避免旧记录残留导致重复文章
     */
    @Query("UPDATE posts SET fileName = :newFileName WHERE fileName = :oldFileName")
    suspend fun renameFileName(oldFileName: String, newFileName: String)

    /**
     * 获取回收站文章（按移入时间倒序）
     */
    @Query("SELECT * FROM posts WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun getTrashedPosts(): Flow<List<PostEntity>>

    /**
     * 清理超过指定时间戳的回收站文章
     */
    @Query("DELETE FROM posts WHERE isTrashed = 1 AND trashedAt < :currentTime")
    suspend fun deleteExpiredTrash(currentTime: Long)

    /**
     * 获取文章总数（排除回收站文章）
     */
    @Query("SELECT COUNT(*) FROM posts WHERE isTrashed = 0")
    suspend fun getPostCount(): Int

    /**
     * 搜索文章（按标题或内容，排除回收站文章）
     */
    @Query("SELECT * FROM posts WHERE isTrashed = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY date DESC")
    fun searchPosts(query: String): Flow<List<PostEntity>>

    /**
     * 根据标签名查询文章（排除回收站文章）
     * tags 字段以逗号分隔存储，使用 LIKE 匹配
     * 对应旧版 renderer.ts 中 renderTagDetail() 的文章筛选逻辑
     */
    @Query("SELECT * FROM posts WHERE isTrashed = 0 AND tags LIKE '%' || :tagName || '%' ORDER BY date DESC")
    fun getPostsByTag(tagName: String): Flow<List<PostEntity>>
}
