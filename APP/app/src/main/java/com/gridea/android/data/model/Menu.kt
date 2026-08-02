package com.gridea.android.data.model

import kotlinx.serialization.Serializable

/**
 * 自定义菜单项数据模型
 *
 * @param openType 打开方式：INTERNAL（内部跳转）/ EXTERNAL（外部跳转，新窗口打开）
 * @param linkType 链接类型：URL（自定义链接）/ ARTICLE（选择已有文章）
 * @param linkValue 链接值：linkType=URL 时为 URL 字符串，linkType=ARTICLE 时为文章 fileName
 */
@Serializable
data class Menu(
    val id: Long = 0,
    val name: String = "",
    val openType: String = "Internal",   // Internal / External
    val linkType: String = "url",         // url / article
    val linkValue: String = "",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isTrashed: Boolean = false,
    val trashedAt: Long = 0L
)
