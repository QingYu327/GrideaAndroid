package com.gridea.android.data.model

import kotlinx.serialization.Serializable

/**
 * 友情链接（友链）数据模型
 */
@Serializable
data class FriendLink(
    val id: Long = 0,
    val name: String = "",
    val url: String = "",
    val description: String = "",
    val avatar: String = "",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isTrashed: Boolean = false,
    val trashedAt: Long = 0L
)
