package com.gridea.android.data.model

import kotlinx.serialization.Serializable

/**
 * 标签
 *
 * 对应旧版 Gridea 0.9.3 的 src/interfaces/tag.ts 中的 ITag
 */
@Serializable
data class Tag(
    val name: String = "",
    val used: Boolean = false,
    val slug: String? = null
)
