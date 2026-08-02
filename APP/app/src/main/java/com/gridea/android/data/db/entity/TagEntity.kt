package com.gridea.android.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 标签实体（Room 表）
 *
 * 对应旧版 Gridea 0.9.3 的 src/server/tags.ts 中管理的标签数据
 */
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey
    val name: String,
    val slug: String,
    val usedCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isTrashed: Boolean = false,
    val trashedAt: Long = 0L
)
