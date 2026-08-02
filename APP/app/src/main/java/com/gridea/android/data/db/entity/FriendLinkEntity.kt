package com.gridea.android.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 友情链接实体（Room 表）
 */
@Entity(tableName = "friend_links", indices = [Index("sortOrder")])
data class FriendLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val description: String = "",
    val avatar: String = "",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isTrashed: Boolean = false,
    val trashedAt: Long = 0L
)
