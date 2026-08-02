package com.gridea.android.data.repository

import com.gridea.android.data.db.dao.FriendLinkDao
import com.gridea.android.data.db.entity.FriendLinkEntity
import com.gridea.android.data.model.FriendLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 友情链接仓库
 *
 * 负责友链的 CRUD 与回收站操作，桥接 DAO 与上层 ViewModel
 * 删除采用软删除，移入回收站保留 3 天后自动清理
 */
@Singleton
class FriendLinkRepository @Inject constructor(
    private val dao: FriendLinkDao
) {

    fun getAll(): Flow<List<FriendLink>> = dao.getAll().map { list ->
        list.map { it.toModel() }
    }

    suspend fun getAllList(): List<FriendLink> = dao.getAllList().map { it.toModel() }

    suspend fun add(link: FriendLink): Long {
        return dao.insert(link.toEntity())
    }

    suspend fun update(link: FriendLink) {
        dao.update(link.toEntity())
    }

    /** 软删除友链（移入回收站） */
    suspend fun delete(link: FriendLink) {
        dao.softDelete(link.id)
    }

    /** 获取回收站中的友链 */
    fun getTrashed(): Flow<List<FriendLinkEntity>> = dao.getTrashed()

    /** 恢复友链 */
    suspend fun restore(id: Long) {
        dao.restore(id)
    }

    /** 批量恢复友链 */
    suspend fun restoreAll(ids: Collection<Long>) {
        ids.forEach { dao.restore(it) }
    }

    /** 彻底删除友链 */
    suspend fun permanentDelete(id: Long) {
        dao.permanentDelete(id)
    }

    /** 批量彻底删除友链 */
    suspend fun permanentDeleteAll(ids: Collection<Long>) {
        ids.forEach { dao.permanentDelete(it) }
    }

    /** 清理超过 3 天的回收站友链 */
    suspend fun cleanExpiredTrash() {
        val expireBefore = System.currentTimeMillis() - PostRepository.TRASH_RETENTION_MS
        dao.deleteExpiredTrash(expireBefore)
    }

    /**
     * 覆盖式恢复：删除所有非回收站友链后批量插入（id 重置为 0 自动生成）
     */
    suspend fun replaceAll(links: List<FriendLink>) {
        dao.deleteAllNonTrashed()
        links.forEach { link ->
            dao.insert(link.copy(id = 0).toEntity())
        }
    }

    private fun FriendLinkEntity.toModel() = FriendLink(
        id = id,
        name = name,
        url = url,
        description = description,
        avatar = avatar,
        sortOrder = sortOrder,
        createdAt = createdAt,
        isTrashed = isTrashed,
        trashedAt = trashedAt
    )

    private fun FriendLink.toEntity() = FriendLinkEntity(
        id = id,
        name = name,
        url = url,
        description = description,
        avatar = avatar,
        sortOrder = sortOrder,
        createdAt = createdAt,
        isTrashed = isTrashed,
        trashedAt = trashedAt
    )
}
