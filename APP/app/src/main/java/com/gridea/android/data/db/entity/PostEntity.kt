package com.gridea.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 文章实体（Room 表）
 *
 * 对应旧版 Gridea 0.9.3 的 src/server/posts.ts 中管理的文章数据
 * fileName 作为主键，对应 Markdown 文件名（不含扩展名）
 */
@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey
    val fileName: String,
    val title: String,
    val date: String,
    val content: String,
    val tags: String,                  // 标签以逗号分隔存储
    val published: Boolean,
    val hideInList: Boolean,
    val isTop: Boolean,
    val feature: String,
    val abstract: String,
    val writingTime: Long = 0L,        // 写作时长（毫秒）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "isTrashed") val isTrashed: Boolean = false,
    @ColumnInfo(name = "trashedAt") val trashedAt: Long = 0L
)
