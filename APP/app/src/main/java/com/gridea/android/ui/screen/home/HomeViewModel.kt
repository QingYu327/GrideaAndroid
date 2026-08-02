package com.gridea.android.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridea.android.data.model.Post
import com.gridea.android.data.repository.FriendLinkRepository
import com.gridea.android.data.repository.MenuRepository
import com.gridea.android.data.repository.PostRepository
import com.gridea.android.data.repository.SiteOutputRepository
import com.gridea.android.data.repository.TagRepository
import com.gridea.android.util.AppLogger
import com.gridea.android.util.MarkdownUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 文章统计
 */
data class PostStats(
    val total: Int,
    val published: Int,
    val draft: Int,
    val tagCount: Int,
    val continuousDays: Int = 0
)

/**
 * 排序方式
 */
enum class SortOption(val label: String) {
    DATE_DESC("日期降序"),
    DATE_ASC("日期升序"),
    TITLE("标题排序")
}

/**
 * 筛选方式
 */
enum class FilterOption(val label: String) {
    ALL("全部"),
    PUBLISHED("已发布"),
    DRAFT("草稿")
}

/**
 * 文章列表 ViewModel
 *
 * 对应旧版 Gridea 0.9.3 中 Articles.vue 的数据逻辑
 * 支持按标题/内容/标签实时搜索、排序、筛选
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val tagRepository: TagRepository,
    private val siteOutputRepository: SiteOutputRepository,
    private val menuRepository: MenuRepository,
    private val friendLinkRepository: FriendLinkRepository
) : ViewModel() {

    /** 搜索关键词（空字符串表示不搜索） */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * 首次加载状态：true 时 UI 显示骨架屏占位符，避免文章列表动画卡顿
     * 在 posts flow 首次发射（combine 完成搜索+筛选+排序）后置为 false
     */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 是否在搜索状态 */
    val isSearching: StateFlow<Boolean> = _searchQuery
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 排序方式 */
    private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    /** 筛选方式 */
    private val _filterOption = MutableStateFlow(FilterOption.ALL)
    val filterOption: StateFlow<FilterOption> = _filterOption.asStateFlow()

    /**
     * 文章列表：支持搜索、筛选、排序
     */
    val posts: StateFlow<List<Post>> = combine(
        postRepository.getAllPosts(),
        _searchQuery,
        combine(_sortOption, _filterOption) { sort, filter -> sort to filter }
    ) { allPosts, query, (sort, filter) ->
        // 0. 文章列表显示全部文章（含隐藏文章），隐藏文章通过标记区分以便管理
        val articlesOnly = allPosts
        // 1. 搜索过滤
        val searched = if (query.isBlank()) {
            articlesOnly
        } else {
            val lowerQuery = query.lowercase().trim()
            articlesOnly.filter { post ->
                post.data.title.lowercase().contains(lowerQuery) ||
                    post.content.lowercase().contains(lowerQuery) ||
                    post.data.tags.any { it.lowercase().contains(lowerQuery) }
            }
        }
        // 2. 发布状态筛选
        val filtered = when (filter) {
            FilterOption.ALL -> searched
            FilterOption.PUBLISHED -> searched.filter { it.data.published }
            FilterOption.DRAFT -> searched.filter { !it.data.published }
        }
        // 3. 排序（置顶文章始终在前）
        when (sort) {
            SortOption.DATE_DESC -> filtered.sortedWith(
                compareByDescending<Post> { it.data.isTop }.thenByDescending { it.data.date }
            )
            SortOption.DATE_ASC -> filtered.sortedWith(
                compareByDescending<Post> { it.data.isTop }.thenBy { it.data.date }
            )
            SortOption.TITLE -> filtered.sortedWith(
                compareByDescending<Post> { it.data.isTop }.thenBy { it.data.title }
            )
        }
    }.onEach {
        // combine 首次发射（完成搜索+筛选+排序）后关闭骨架屏
        _isLoading.value = false
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * 文章统计仪表盘数据
     *
     * continuousDays：连续写作天数，从今天向前回溯，遇到有文章的日期就 +1，
     * 遇到没有文章的日期就停止。文章日期取自 Post.data.date（yyyy-MM-dd 格式）。
     */
    val stats: StateFlow<PostStats> = combine(
        postRepository.getAllPosts(),
        tagRepository.getAllTags()
    ) { allPosts, tags ->
        val articles = allPosts.filter { !it.data.hideInList }
        val dateSet = articles.map { it.data.date.take(10) }.toSet()
        PostStats(
            total = articles.size,
            published = articles.count { it.data.published },
            draft = articles.count { !it.data.published },
            tagCount = tags.size,
            continuousDays = calculateContinuousDays(dateSet)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PostStats(0, 0, 0, 0, 0)
    )

    /**
     * 所有标签名列表（供批量打标签弹窗展示）
     */
    val allTags: StateFlow<List<String>> = tagRepository.getAllTags()
        .map { tags -> tags.map { it.name } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * 更新搜索关键词
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * 清除搜索
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun updateSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun updateFilterOption(option: FilterOption) {
        _filterOption.value = option
    }

    /**
     * 移入回收站（软删除）
     * 对应旧版 Posts.deletePost()
     */
    fun deletePost(fileName: String) {
        viewModelScope.launch {
            postRepository.deletePost(fileName)
            AppLogger.action("Post", "Trash", fileName)
        }
    }

    /**
     * 批量移入回收站（软删除）
     * 用于列表多选模式下的批量删除操作
     */
    fun deletePosts(fileNames: Collection<String>) {
        if (fileNames.isEmpty()) return
        viewModelScope.launch {
            fileNames.forEach { fileName ->
                postRepository.deletePost(fileName)
                AppLogger.action("Post", "Trash", fileName)
            }
        }
    }

    /**
     * 批量导出文章为 Markdown 文件
     *
     * 将选中文章导出到 Documents/Gridea/output/exports/ 目录
     * 每篇文章导出为一个 {fileName}.md 文件，内容包含 front-matter（title, date, tags 等）+ 正文
     * 导出前会检查存储权限（MANAGE_EXTERNAL_STORAGE）
     *
     * @param fileNames 待导出文章的 fileName 集合
     * @param onResult 回调：success 是否至少有一篇成功、count 成功篇数、message 提示文案
     */
    fun exportPostsToMarkdown(
        fileNames: Collection<String>,
        onResult: (success: Boolean, count: Int, message: String) -> Unit
    ) {
        if (fileNames.isEmpty()) {
            onResult(false, 0, "未选择文章")
            return
        }
        if (!siteOutputRepository.hasPermission.value) {
            onResult(false, 0, "未获得存储权限，请先在设置中授权")
            return
        }
        viewModelScope.launch {
            var successCount = 0
            var failedCount = 0
            withContext(Dispatchers.IO) {
                try {
                    // 导出目录：Documents/Gridea/markdown/
                    val exportsDir = siteOutputRepository.markdownDir
                    if (!exportsDir.exists()) exportsDir.mkdirs()

                    fileNames.forEach { fileName ->
                        try {
                            val post = postRepository.getPostByFileName(fileName) ?: return@forEach
                            val md = MarkdownUtils.buildMarkdownContent(
                                title = post.data.title,
                                date = post.data.date,
                                tags = post.data.tags,
                                published = post.data.published,
                                hideInList = post.data.hideInList,
                                feature = post.data.feature,
                                isTop = post.data.isTop,
                                content = post.content
                            )
                            val file = File(exportsDir, "$fileName.md")
                            file.writeText(md, Charsets.UTF_8)
                            successCount++
                            AppLogger.action("Post", "Export", fileName)
                        } catch (e: Exception) {
                            failedCount++
                            AppLogger.reportUserError("Export", "导出失败：$fileName", e)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.reportUserError("Export", "批量导出失败", e)
                }
            }
            val message = when {
                failedCount == 0 && successCount > 0 -> "已导出 $successCount 篇文章到 markdown 目录"
                successCount == 0 -> "导出失败"
                else -> "已导出 $successCount 篇，$failedCount 篇失败"
            }
            onResult(successCount > 0, successCount, message)
        }
    }

    /**
     * 批量为选中文章追加标签
     *
     * 遍历选中文章，给每篇文章追加新标签（去重，不替换已有标签）
     * 完成后通过 [onResult] 回调通知 UI 显示灵动岛通知
     *
     * @param fileNames 待打标签文章的 fileName 集合
     * @param tags 要追加的标签列表
     * @param onResult 回调：success 是否至少有一篇成功、count 成功篇数、message 提示文案
     */
    fun batchAddTags(
        fileNames: Collection<String>,
        tags: List<String>,
        onResult: (success: Boolean, count: Int, message: String) -> Unit
    ) {
        if (fileNames.isEmpty() || tags.isEmpty()) {
            onResult(false, 0, "未选择文章或标签")
            return
        }
        // 去除空白与重复标签
        val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanTags.isEmpty()) {
            onResult(false, 0, "标签为空")
            return
        }
        viewModelScope.launch {
            var successCount = 0
            fileNames.forEach { fileName ->
                try {
                    val post = postRepository.getPostByFileName(fileName) ?: return@forEach
                    // 追加新标签：保留已有标签，仅添加不存在的标签（忽略大小写去重）
                    val existingLower = post.data.tags.map { it.lowercase() }.toMutableSet()
                    val mergedTags = post.data.tags.toMutableList()
                    cleanTags.forEach { tag ->
                        if (tag.lowercase() !in existingLower) {
                            mergedTags.add(tag)
                            existingLower.add(tag.lowercase())
                        }
                    }
                    if (mergedTags.size != post.data.tags.size) {
                        val updatedPost = post.copy(
                            data = post.data.copy(tags = mergedTags)
                        )
                        postRepository.savePost(updatedPost)
                        successCount++
                        AppLogger.action("Post", "BatchTag", fileName)
                    }
                } catch (e: Exception) {
                    AppLogger.reportUserError("BatchTag", "打标签失败：$fileName", e)
                }
            }
            val message = if (successCount > 0) {
                "已为 $successCount 篇文章添加标签"
            } else {
                "添加标签失败"
            }
            onResult(successCount > 0, successCount, message)
        }
    }

    /**
     * 清理过期回收站内容（超过 3 天自动物理删除）
     * 涵盖文章、标签、菜单、友链四类
     * 在 ViewModel 初始化时调用一次
     */
    fun cleanExpiredTrash() {
        viewModelScope.launch {
            postRepository.cleanExpiredTrash()
            tagRepository.cleanExpiredTrash()
            menuRepository.cleanExpiredTrash()
            friendLinkRepository.cleanExpiredTrash()
        }
    }

    init {
        cleanExpiredTrash()
    }

    /**
     * 计算连续写作天数：从今天向前回溯，遇到有文章的日期就 +1，遇到无文章日期停止
     *
     * @param dateSet 文章日期集合（yyyy-MM-dd 格式）
     * @return 连续写作天数（今天有文章则至少为 1，今天和昨天都没有则为 0）
     */
    private fun calculateContinuousDays(dateSet: Set<String>): Int {
        if (dateSet.isEmpty()) return 0
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val calendar = java.util.Calendar.getInstance()
        var streak = 0
        // 从今天开始向前回溯
        while (true) {
            val dateKey = dateFormat.format(calendar.time)
            if (dateKey in dateSet) {
                streak++
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
    }
}
