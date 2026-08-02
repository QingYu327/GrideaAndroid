package com.gridea.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gridea.android.data.db.dao.FriendLinkDao
import com.gridea.android.data.db.dao.MenuDao
import com.gridea.android.data.db.dao.PostDao
import com.gridea.android.data.db.dao.PostVersionDao
import com.gridea.android.data.db.dao.TagDao
import com.gridea.android.data.db.entity.FriendLinkEntity
import com.gridea.android.data.db.entity.MenuEntity
import com.gridea.android.data.db.entity.PostEntity
import com.gridea.android.data.db.entity.PostVersionEntity
import com.gridea.android.data.db.entity.TagEntity

/**
 * Gridea 应用数据库
 *
 * 替代旧版 Gridea 0.9.3 中使用的 lowdb（基于 JSON 文件存储）
 * 使用 Room 提供更高效的本地数据持久化能力
 */
@Database(
    entities = [PostEntity::class, TagEntity::class, PostVersionEntity::class, FriendLinkEntity::class, MenuEntity::class],
    version = 8,
    exportSchema = false
)
abstract class GrideaDatabase : RoomDatabase() {

    abstract fun postDao(): PostDao

    abstract fun tagDao(): TagDao

    abstract fun postVersionDao(): PostVersionDao

    abstract fun friendLinkDao(): FriendLinkDao

    abstract fun menuDao(): MenuDao

    companion object {
        const val DATABASE_NAME = "gridea.db"
    }
}
