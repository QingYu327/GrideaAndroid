package com.gridea.android.data.repository

import com.gridea.android.data.db.dao.MenuDao
import com.gridea.android.data.db.entity.MenuEntity
import com.gridea.android.data.model.Menu
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自定义菜单仓库
 *
 * 负责菜单项的 CRUD 与回收站操作，桥接 DAO 与上层 ViewModel
 * 删除采用软删除，移入回收站保留 3 天后自动清理
 */
@Singleton
class MenuRepository @Inject constructor(
    private val dao: MenuDao
) {

    fun getAll(): Flow<List<Menu>> = dao.getAll().map { list ->
        list.map { it.toModel() }
    }

    suspend fun getAllList(): List<Menu> = dao.getAllList().map { it.toModel() }

    suspend fun add(menu: Menu): Long {
        return dao.insert(menu.toEntity())
    }

    suspend fun update(menu: Menu) {
        dao.update(menu.toEntity())
    }

    /** 软删除菜单（移入回收站） */
    suspend fun delete(menu: Menu) {
        dao.softDelete(menu.id)
    }

    /** 获取回收站中的菜单 */
    fun getTrashed(): Flow<List<MenuEntity>> = dao.getTrashed()

    /** 恢复菜单 */
    suspend fun restore(id: Long) {
        dao.restore(id)
    }

    /** 批量恢复菜单 */
    suspend fun restoreAll(ids: Collection<Long>) {
        ids.forEach { dao.restore(it) }
    }

    /** 彻底删除菜单 */
    suspend fun permanentDelete(id: Long) {
        dao.permanentDelete(id)
    }

    /** 批量彻底删除菜单 */
    suspend fun permanentDeleteAll(ids: Collection<Long>) {
        ids.forEach { dao.permanentDelete(it) }
    }

    /** 清理超过 3 天的回收站菜单 */
    suspend fun cleanExpiredTrash() {
        val expireBefore = System.currentTimeMillis() - PostRepository.TRASH_RETENTION_MS
        dao.deleteExpiredTrash(expireBefore)
    }

    /**
     * 覆盖式恢复：删除所有非回收站菜单后批量插入（id 重置为 0 自动生成）
     */
    suspend fun replaceAll(menus: List<Menu>) {
        dao.deleteAllNonTrashed()
        menus.forEach { menu ->
            dao.insert(menu.copy(id = 0).toEntity())
        }
    }

    private fun MenuEntity.toModel() = Menu(
        id = id,
        name = name,
        openType = openType,
        linkType = linkType,
        linkValue = linkValue,
        sortOrder = sortOrder,
        createdAt = createdAt,
        isTrashed = isTrashed,
        trashedAt = trashedAt
    )

    private fun Menu.toEntity() = MenuEntity(
        id = id,
        name = name,
        openType = openType,
        linkType = linkType,
        linkValue = linkValue,
        sortOrder = sortOrder,
        createdAt = createdAt,
        isTrashed = isTrashed,
        trashedAt = trashedAt
    )
}
