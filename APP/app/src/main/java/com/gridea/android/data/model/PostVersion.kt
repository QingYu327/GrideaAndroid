package com.gridea.android.data.model

import kotlinx.serialization.Serializable

/**
 * 文章版本快照（领域模型）
 *
 * 保存某次自动保存时的文章内容快照，用于回滚
 */
@Serializable
data class PostVersion(
    val id: Long = 0,
    val postFileName: String,
    val title: String,
    val content: String,
    val tags: List<String>,
    val savedAt: Long
)
