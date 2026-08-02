package com.gridea.android.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 自定义菜单项实体（Room 表）
 *
 * 对应旧版 Gridea 0.9.3 的 menus.json
 * 用户可自定义导航菜单项，支持内部跳转/外部跳转，链接可为自定义 URL 或已有文章
 */
@Entity(tableName = "menus", indices = [Index("sortOrder")])
data class MenuEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,               // 菜单显示名称
    val openType: String,           // 打开方式："Internal"（内部跳转）或 "External"（外部跳转）
    val linkType: String,           // 链接类型："url"（自定义链接）或 "article"（选择已有文章）
    val linkValue: String,          // 链接值：URL 字符串 或 文章 fileName
    val sortOrder: Int = 0,         // 排序序号
    val createdAt: Long = System.currentTimeMillis(),
    val isTrashed: Boolean = false,
    val trashedAt: Long = 0L
)
