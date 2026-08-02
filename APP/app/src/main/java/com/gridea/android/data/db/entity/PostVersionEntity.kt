package com.gridea.android.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 文章版本历史实体（Room 表）
 *
 * 每次自动保存时创建一份内容快照，用于回滚到历史版本。
 * 每篇文章最多保留 [MAX_VERSIONS_PER_POST] 个版本，超出后删除最旧的。
 */
@Entity(
    tableName = "post_versions",
    indices = [Index("postFileName")]
)
data class PostVersionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val postFileName: String,
    val title: String,
    val content: String,
    val tags: String,
    val savedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** 每篇文章最多保留的版本数 */
        const val MAX_VERSIONS_PER_POST = 20
    }
}
