package com.gridea.android.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gridea.android.data.db.entity.MenuEntity
import kotlinx.coroutines.flow.Flow

/**
 * 自定义菜单数据访问对象
 */
@Dao
interface MenuDao {

    @Query("SELECT * FROM menus WHERE isTrashed = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun getAll(): Flow<List<MenuEntity>>

    @Query("SELECT * FROM menus WHERE isTrashed = 0 ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAllList(): List<MenuEntity>

    @Query("SELECT * FROM menus WHERE id = :id AND isTrashed = 0")
    suspend fun getById(id: Long): MenuEntity?

    @Insert
    suspend fun insert(entity: MenuEntity): Long

    @Update
    suspend fun update(entity: MenuEntity)

    @Delete
    suspend fun delete(entity: MenuEntity)

    /** 软删除菜单（移入回收站） */
    @Query("UPDATE menus SET isTrashed = 1, trashedAt = :trashedAt WHERE id = :id")
    suspend fun softDelete(id: Long, trashedAt: Long = System.currentTimeMillis())

    /** 恢复菜单（从回收站恢复） */
    @Query("UPDATE menus SET isTrashed = 0, trashedAt = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    /** 彻底删除菜单 */
    @Query("DELETE FROM menus WHERE id = :id")
    suspend fun permanentDelete(id: Long)

    /** 获取回收站中的菜单 */
    @Query("SELECT * FROM menus WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun getTrashed(): Flow<List<MenuEntity>>

    /** 清理超过指定时间戳的回收站菜单 */
    @Query("DELETE FROM menus WHERE isTrashed = 1 AND trashedAt < :timestamp")
    suspend fun deleteExpiredTrash(timestamp: Long)

    @Query("SELECT COUNT(*) FROM menus WHERE isTrashed = 0")
    suspend fun count(): Int

    /** 删除所有非回收站菜单（用于数据恢复覆盖） */
    @Query("DELETE FROM menus WHERE isTrashed = 0")
    suspend fun deleteAllNonTrashed()
}
