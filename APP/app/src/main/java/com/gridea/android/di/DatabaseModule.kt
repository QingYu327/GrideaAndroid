package com.gridea.android.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gridea.android.data.db.GrideaDatabase
import com.gridea.android.data.db.dao.FriendLinkDao
import com.gridea.android.data.db.dao.MenuDao
import com.gridea.android.data.db.dao.PostDao
import com.gridea.android.data.db.dao.PostVersionDao
import com.gridea.android.data.db.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库依赖注入模块
 *
 * 提供 GrideaDatabase、PostDao、TagDao、PostVersionDao 的单例实例
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * v1 → v2 迁移：新增 post_versions 表（文章版本历史）
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS post_versions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    postFileName TEXT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    tags TEXT NOT NULL,
                    savedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_post_versions_postFileName ON post_versions(postFileName)"
            )
        }
    }

    /**
     * v2 → v3 迁移：新增 friend_links 表（友情链接）
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `friend_links` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `url` TEXT NOT NULL,
                    `description` TEXT NOT NULL DEFAULT '',
                    `avatar` TEXT NOT NULL DEFAULT '',
                    `sortOrder` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_friend_links_sort_order` ON `friend_links` (`sortOrder`)"
            )
        }
    }

    /**
     * v3 → v4 迁移：posts 表新增 writingTime 字段（写作时长，毫秒）
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE posts ADD COLUMN writingTime INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * v4 → v5 迁移：新增 menus 表（自定义菜单）
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `menus` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `openType` TEXT NOT NULL,
                    `linkType` TEXT NOT NULL,
                    `linkValue` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_menus_sortOrder` ON `menus` (`sortOrder`)"
            )
        }
    }

    /**
     * v5 → v6 迁移：posts 表新增软删除字段 isTrashed / trashedAt（回收站功能）
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE posts ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE posts ADD COLUMN trashedAt INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * v6 → v7 迁移：tags 表新增软删除字段 isTrashed / trashedAt（标签回收站功能）
     */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE tags ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE tags ADD COLUMN trashedAt INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * v7 → v8 迁移：menus 和 friend_links 表新增软删除字段 isTrashed / trashedAt（菜单和友链回收站功能）
     */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE menus ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE menus ADD COLUMN trashedAt INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE friend_links ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE friend_links ADD COLUMN trashedAt INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GrideaDatabase {
        return Room.databaseBuilder(
            context,
            GrideaDatabase::class.java,
            GrideaDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun providePostDao(database: GrideaDatabase): PostDao {
        return database.postDao()
    }

    @Provides
    fun provideTagDao(database: GrideaDatabase): TagDao {
        return database.tagDao()
    }

    @Provides
    fun providePostVersionDao(database: GrideaDatabase): PostVersionDao {
        return database.postVersionDao()
    }

    @Provides
    fun provideFriendLinkDao(database: GrideaDatabase): FriendLinkDao {
        return database.friendLinkDao()
    }

    @Provides
    fun provideMenuDao(database: GrideaDatabase): MenuDao {
        return database.menuDao()
    }
}

