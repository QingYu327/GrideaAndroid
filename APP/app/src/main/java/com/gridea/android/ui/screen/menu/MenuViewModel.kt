package com.gridea.android.ui.screen.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridea.android.data.model.Menu
import com.gridea.android.data.model.Post
import com.gridea.android.data.repository.MenuRepository
import com.gridea.android.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 自定义菜单管理 ViewModel
 *
 * 暴露菜单列表 StateFlow 以及增删改操作，同时提供可选文章列表（用于菜单项链接到已有文章）
 */
@HiltViewModel
class MenuViewModel @Inject constructor(
    private val repository: MenuRepository,
    private val postRepository: PostRepository
) : ViewModel() {

    val menus: StateFlow<List<Menu>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 所有文章列表（供菜单选择文章时使用）
     * 一次性获取，避免频繁查询
     */
    val allPosts: StateFlow<List<Post>> = postRepository.getAllPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 一次性操作结果消息：用于桥接到全局灵动岛通知 */
    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    fun clearOperationMessage() { _operationMessage.value = null }

    fun addMenu(
        name: String,
        openType: String,
        linkType: String,
        linkValue: String
    ) {
        viewModelScope.launch {
            val order = (menus.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
            repository.add(
                Menu(
                    name = name,
                    openType = openType,
                    linkType = linkType,
                    linkValue = linkValue,
                    sortOrder = order
                )
            )
            _operationMessage.value = "已添加菜单"
        }
    }

    fun updateMenu(menu: Menu) {
        viewModelScope.launch {
            repository.update(menu)
            _operationMessage.value = "已更新菜单"
        }
    }

    fun deleteMenu(menu: Menu) {
        viewModelScope.launch {
            repository.delete(menu)
            _operationMessage.value = "已删除菜单"
        }
    }

    /**
     * 批量删除多个菜单
     * 用于菜单列表多选模式下的批量删除操作
     */
    fun deleteMenus(menus: Collection<Menu>) {
        if (menus.isEmpty()) return
        viewModelScope.launch {
            menus.forEach { repository.delete(it) }
            _operationMessage.value = "已删除 ${menus.size} 个菜单"
        }
    }
}
