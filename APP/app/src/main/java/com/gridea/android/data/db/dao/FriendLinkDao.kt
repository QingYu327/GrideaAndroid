package com.gridea.android.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gridea.android.data.db.entity.FriendLinkEntity
import kotlinx.coroutines.flow.Flow

/**
 * 友情链接数据访问对象
 */
@Dao
interface FriendLinkDao {

    @Query("SELECT * FROM friend_links WHERE isTrashed = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun getAll(): Flow<List<FriendLinkEntity>>

    @Query("SELECT * FROM friend_links WHERE isTrashed = 0 ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAllList(): List<FriendLinkEntity>

    @Query("SELECT * FROM friend_links WHERE id = :id AND isTrashed = 0")
    suspend fun getById(id: Long): FriendLinkEntity?

    @Insert
    suspend fun insert(entity: FriendLinkEntity): Long

    @Update
    suspend fun update(entity: FriendLinkEntity)

    @Delete
    suspend fun delete(entity: FriendLinkEntity)

    /** 软删除友链（移入回收站） */
    @Query("UPDATE friend_links SET isTrashed = 1, trashedAt = :trashedAt WHERE id = :id")
    suspend fun softDelete(id: Long, trashedAt: Long = System.currentTimeMillis())

    /** 恢复友链（从回收站恢复） */
    @Query("UPDATE friend_links SET isTrashed = 0, trashedAt = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    /** 彻底删除友链 */
    @Query("DELETE FROM friend_links WHERE id = :id")
    suspend fun permanentDelete(id: Long)

    /** 获取回收站中的友链 */
    @Query("SELECT * FROM friend_links WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun getTrashed(): Flow<List<FriendLinkEntity>>

    /** 清理超过指定时间戳的回收站友链 */
    @Query("DELETE FROM friend_links WHERE isTrashed = 1 AND trashedAt < :timestamp")
    suspend fun deleteExpiredTrash(timestamp: Long)

    @Query("SELECT COUNT(*) FROM friend_links WHERE isTrashed = 0")
    suspend fun count(): Int

    /** 删除所有非回收站友链（用于数据恢复覆盖） */
    @Query("DELETE FROM friend_links WHERE isTrashed = 0")
    suspend fun deleteAllNonTrashed()
}
