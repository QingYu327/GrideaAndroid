package com.gridea.android.ui.screen.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridea.android.data.model.Post
import com.gridea.android.data.repository.PostRepository
import com.gridea.android.data.repository.TagRepository
import com.gridea.android.data.repository.TagWithCount
import com.gridea.android.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 标签管理 ViewModel
 *
 * 对应旧版 Gridea 0.9.3 的 src/views/tags/Index.vue 数据逻辑
 */
@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagRepository: TagRepository,
    private val postRepository: PostRepository
) : ViewModel() {

    /**
     * 所有标签（含文章计数）
     */
    val tags: StateFlow<List<TagWithCount>> = tagRepository.getAllTags()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * 当前选中标签下的文章列表
     */
    private val _selectedTagPosts = MutableStateFlow<List<Post>>(emptyList())
    val selectedTagPosts: StateFlow<List<Post>> = _selectedTagPosts.asStateFlow()

    /**
     * 当前选中的标签名
     */
    private val _selectedTagName = MutableStateFlow<String?>(null)
    val selectedTagName: StateFlow<String?> = _selectedTagName.asStateFlow()

    /**
     * 加载某标签下的文章
     * 对应旧版 renderer.ts 中 renderTagDetail() 的筛选逻辑
     */
    fun loadPostsByTag(tagName: String) {
        _selectedTagName.value = tagName
        viewModelScope.launch {
            postRepository.getPostsByTag(tagName).collect { posts ->
                _selectedTagPosts.value = posts
            }
        }
    }

    /**
     * 清除选中状态
     *
     * 仅清空选中标签名，保留文章列表数据。
     * 原因：AnimatedContent 退出过渡期间 TagDetailContent 仍会短暂渲染，
     * 若此时 selectedTagPosts 已被清空，会闪现"该标签下暂无文章"空状态文字。
     * 保留旧数据可让退出动画平滑完成，下次 loadPostsByTag 会覆盖旧值。
     */
    fun clearSelection() {
        _selectedTagName.value = null
    }

    /**
     * 新建标签
     * @param name 标签名
     * @param onResult 回调：true 表示创建成功，false 表示标签已存在
     */
    fun createTag(name: String, onResult: (Boolean) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            try {
                tagRepository.createTag(trimmed)
                AppLogger.action("Tag", "Create", trimmed)
                onResult(true)
            } catch (_: Exception) {
                onResult(false)
            }
        }
    }

    /**
     * 删除标签
     * - 从标签表中删除
     * - 从所有文章的 tags 字段中移除该标签引用（解除关联）
     * @param tagName 标签名
     * @param onResult 回调：受影响的文章数量
     */
    fun deleteTag(tagName: String, onResult: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val affected = tagRepository.deleteTag(tagName)
            AppLogger.action("Tag", "Delete", tagName)
            onResult(affected)
        }
    }

    /**
     * 批量删除多个标签
     * 用于标签云多选模式下的批量删除操作
     */
    fun deleteTags(tagNames: Collection<String>, onResult: (Int) -> Unit = {}) {
        if (tagNames.isEmpty()) return
        viewModelScope.launch {
            var totalAffected = 0
            tagNames.forEach { name ->
                totalAffected += tagRepository.deleteTag(name)
            }
            AppLogger.action("Tag", "DeleteBatch", tagNames.joinToString(","))
            onResult(totalAffected)
        }
    }
}
