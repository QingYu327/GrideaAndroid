package com.gridea.android.ui.screen.friendlink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridea.android.data.model.FriendLink
import com.gridea.android.data.repository.FriendLinkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 友情链接管理 ViewModel
 *
 * 暴露友链列表 StateFlow 以及增删改操作
 */
@HiltViewModel
class FriendLinkViewModel @Inject constructor(
    private val repository: FriendLinkRepository
) : ViewModel() {

    val friendLinks: StateFlow<List<FriendLink>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 一次性操作结果消息：用于桥接到全局灵动岛通知 */
    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    fun clearOperationMessage() { _operationMessage.value = null }

    fun addFriendLink(name: String, url: String, description: String, avatar: String) {
        viewModelScope.launch {
            repository.add(FriendLink(name = name, url = url, description = description, avatar = avatar))
            _operationMessage.value = "已添加友链"
        }
    }

    fun updateFriendLink(link: FriendLink) {
        viewModelScope.launch {
            repository.update(link)
            _operationMessage.value = "已更新友链"
        }
    }

    fun deleteFriendLink(link: FriendLink) {
        viewModelScope.launch {
            repository.delete(link)
            _operationMessage.value = "已删除友链"
        }
    }

    /**
     * 批量删除多个友链
     * 用于友链列表多选模式下的批量删除操作
     */
    fun deleteFriendLinks(links: Collection<FriendLink>) {
        if (links.isEmpty()) return
        viewModelScope.launch {
            links.forEach { repository.delete(it) }
            _operationMessage.value = "已删除 ${links.size} 个友链"
        }
    }
}
