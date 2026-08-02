package com.gridea.android.data.model

import kotlinx.serialization.Serializable

/**
 * 文章元数据
 *
 * 对应旧版 Gridea 0.9.3 的 src/interfaces/post.ts 中的 IPostData
 * 存储于 Markdown 文件的 front-matter 中
 */
@Serializable
data class PostData(
    val title: String = "",
    val date: String = "",
    val published: Boolean = false,
    val hideInList: Boolean = false,
    val tags: List<String> = emptyList(),
    val feature: String = "",
    val isTop: Boolean = false,
    val writingTime: Long = 0L
)

/**
 * 文章完整结构
 *
 * 对应旧版 Gridea 0.9.3 的 src/interfaces/post.ts 中的 IPost
 */
@Serializable
data class Post(
    val content: String = "",
    val data: PostData = PostData(),
    val fileName: String = ""
)

/**
 * 回收站文章：包含文章领域模型与移入回收站的时间戳
 * 用于计算剩余保留天数
 */
data class TrashedPost(
    val post: Post,
    val trashedAt: Long
)
