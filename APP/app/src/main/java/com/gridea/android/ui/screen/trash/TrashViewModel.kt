package com.gridea.android.ui.screen.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridea.android.data.db.entity.FriendLinkEntity
import com.gridea.android.data.db.entity.MenuEntity
import com.gridea.android.data.db.entity.TagEntity
import com.gridea.android.data.model.TrashedPost
import com.gridea.android.data.repository.FriendLinkRepository
import com.gridea.android.data.repository.MenuRepository
import com.gridea.android.data.repository.PostRepository
import com.gridea.android.data.repository.TagRepository
import com.gridea.android.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 回收站文章 UI 数据：包含文章信息与剩余保留天数
 */
data class TrashItem(
    val trashedPost: TrashedPost,
    val remainingDays: Int
)

/**
 * 回收站标签 UI 数据：包含标签信息与剩余保留天数
 */
data class TrashTagItem(
    val tag: TagEntity,
    val remainingDays: Int
)

/**
 * 回收站菜单 UI 数据：包含菜单信息与剩余保留天数
 */
data class TrashMenuItem(
    val menu: MenuEntity,
    val remainingDays: Int
)

/**
 * 回收站友链 UI 数据：包含友链信息与剩余保留天数
 */
data class TrashFriendLinkItem(
    val friendLink: FriendLinkEntity,
    val remainingDays: Int
)

/**
 * 回收站 ViewModel
 *
 * 管理已软删除文章、标签、菜单、友链的列表展示、恢复与彻底删除操作
 * 回收站内容保留 3 天，超过后自动清理
 * 四类内容分页面管理，避免混淆
 */
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val tagRepository: TagRepository,
    private val menuRepository: MenuRepository,
    private val friendLinkRepository: FriendLinkRepository
) : ViewModel() {

    private val dayMs = 24 * 60 * 60 * 1000L

    private fun calcRemainingDays(trashedAt: Long): Int {
        val now = System.currentTimeMillis()
        val elapsedDays = ((now - trashedAt) / dayMs).toInt()
        return (TRASH_RETENTION_DAYS - elapsedDays).coerceAtLeast(0)
    }

    /** 回收站文章列表（含剩余保留天数） */
    val trashedPosts: StateFlow<List<TrashItem>> = postRepository.getTrashedPosts()
        .map { list -> list.map { TrashItem(it, calcRemainingDays(it.trashedAt)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 回收站标签列表（含剩余保留天数） */
    val trashedTags: StateFlow<List<TrashTagItem>> = tagRepository.getTrashedTags()
        .map { list -> list.map { TrashTagItem(it, calcRemainingDays(it.trashedAt)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 回收站菜单列表（含剩余保留天数） */
    val trashedMenus: StateFlow<List<TrashMenuItem>> = menuRepository.getTrashed()
        .map { list -> list.map { TrashMenuItem(it, calcRemainingDays(it.trashedAt)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 回收站友链列表（含剩余保留天数） */
    val trashedFriendLinks: StateFlow<List<TrashFriendLinkItem>> = friendLinkRepository.getTrashed()
        .map { list -> list.map { TrashFriendLinkItem(it, calcRemainingDays(it.trashedAt)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ===== 文章操作 =====

    fun restorePost(fileName: String) {
        viewModelScope.launch {
            postRepository.restorePost(fileName)
            AppLogger.action("Post", "Restore", fileName)
        }
    }

    fun restorePosts(fileNames: Collection<String>) {
        if (fileNames.isEmpty()) return
        viewModelScope.launch {
            postRepository.restorePosts(fileNames)
            fileNames.forEach { AppLogger.action("Post", "Restore", it) }
        }
    }

    fun permanentDeletePost(fileName: String) {
        viewModelScope.launch {
            postRepository.permanentDeletePost(fileName)
            AppLogger.action("Post", "PermanentDelete", fileName)
        }
    }

    fun permanentDeletePosts(fileNames: Collection<String>) {
        if (fileNames.isEmpty()) return
        viewModelScope.launch {
            postRepository.permanentDeletePosts(fileNames)
            fileNames.forEach { AppLogger.action("Post", "PermanentDelete", it) }
        }
    }

    // ===== 标签操作 =====

    fun restoreTag(name: String) {
        viewModelScope.launch {
            tagRepository.restoreTag(name)
            AppLogger.action("Tag", "Restore", name)
        }
    }

    fun restoreTags(names: Collection<String>) {
        if (names.isEmpty()) return
        viewModelScope.launch {
            tagRepository.restoreTags(names)
            names.forEach { AppLogger.action("Tag", "Restore", it) }
        }
    }

    fun permanentDeleteTag(name: String) {
        viewModelScope.launch {
            tagRepository.permanentDeleteTag(name)
            AppLogger.action("Tag", "PermanentDelete", name)
        }
    }

    fun permanentDeleteTags(names: Collection<String>) {
        if (names.isEmpty()) return
        viewModelScope.launch {
            tagRepository.permanentDeleteTags(names)
            names.forEach { AppLogger.action("Tag", "PermanentDelete", it) }
        }
    }

    // ===== 菜单操作 =====

    fun restoreMenu(id: Long) {
        viewModelScope.launch {
            menuRepository.restore(id)
            AppLogger.action("Menu", "Restore", id.toString())
        }
    }

    fun restoreMenus(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            menuRepository.restoreAll(ids)
            ids.forEach { AppLogger.action("Menu", "Restore", it.toString()) }
        }
    }

    fun permanentDeleteMenu(id: Long) {
        viewModelScope.launch {
            menuRepository.permanentDelete(id)
            AppLogger.action("Menu", "PermanentDelete", id.toString())
        }
    }

    fun permanentDeleteMenus(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            menuRepository.permanentDeleteAll(ids)
            ids.forEach { AppLogger.action("Menu", "PermanentDelete", it.toString()) }
        }
    }

    // ===== 友链操作 =====

    fun restoreFriendLink(id: Long) {
        viewModelScope.launch {
            friendLinkRepository.restore(id)
            AppLogger.action("FriendLink", "Restore", id.toString())
        }
    }

    fun restoreFriendLinks(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            friendLinkRepository.restoreAll(ids)
            ids.forEach { AppLogger.action("FriendLink", "Restore", it.toString()) }
        }
    }

    fun permanentDeleteFriendLink(id: Long) {
        viewModelScope.launch {
            friendLinkRepository.permanentDelete(id)
            AppLogger.action("FriendLink", "PermanentDelete", id.toString())
        }
    }

    fun permanentDeleteFriendLinks(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            friendLinkRepository.permanentDeleteAll(ids)
            ids.forEach { AppLogger.action("FriendLink", "PermanentDelete", it.toString()) }
        }
    }

    companion object {
        /** 回收站保留天数 */
        private const val TRASH_RETENTION_DAYS = 3
    }
}
