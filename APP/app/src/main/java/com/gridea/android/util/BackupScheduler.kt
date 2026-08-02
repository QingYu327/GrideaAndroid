package com.gridea.android.util

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.gridea.android.data.repository.DataBackupRepository
import com.gridea.android.data.repository.SiteOutputRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动备份调度器
 *
 * 在应用启动时由 MainActivity 调用 [checkAndRunBackup]，按需执行：
 * - 每天备份一次全局配置和文章到 Documents/Gridea/backup（距上次备份 > 24 小时触发）
 * - 每 7 天清理一次旧备份，只保留最近 7 天的备份文件
 *
 * 备份时间与清理时间均通过 DataStore 持久化，应用重启后不会重复执行。
 */
@Singleton
class BackupScheduler @Inject constructor(
    private val dataBackupRepository: DataBackupRepository,
    private val siteOutputRepository: SiteOutputRepository,
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        private const val TAG = "BackupScheduler"

        /** 上次备份时间（毫秒） */
        private val KEY_LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")

        /** 上次清理时间（毫秒） */
        private val KEY_LAST_CLEAN_TIME = longPreferencesKey("last_clean_time")

        /** 备份间隔：24 小时 */
        private val BACKUP_INTERVAL_MS = TimeUnit.DAYS.toMillis(1)

        /** 清理间隔：7 天 */
        private val CLEAN_INTERVAL_MS = TimeUnit.DAYS.toMillis(7)

        /** 旧备份保留时长：7 天 */
        private val BACKUP_RETENTION_MS = TimeUnit.DAYS.toMillis(7)
    }

    /**
     * 检查并按需执行自动备份与旧备份清理。
     *
     * 应在后台协程中调用。无存储权限或备份失败时静默跳过，不抛出异常。
     */
    suspend fun checkAndRunBackup() = withContext(Dispatchers.IO) {
        if (!siteOutputRepository.hasPermission.value) {
            // 未获得存储权限，无法写入公共目录，跳过本次调度
            return@withContext
        }

        val now = System.currentTimeMillis()
        val prefs = dataStore.data.first()
        val lastBackup = prefs[KEY_LAST_BACKUP_TIME] ?: 0L
        val lastClean = prefs[KEY_LAST_CLEAN_TIME] ?: 0L

        // 每日备份
        if (now - lastBackup >= BACKUP_INTERVAL_MS) {
            try {
                val backupDir = siteOutputRepository.ensureBackupDir()
                val backupFile = dataBackupRepository.exportAutoBackup(backupDir)
                Log.i(TAG, "自动备份完成: ${backupFile.absolutePath}")
                dataStore.edit { it[KEY_LAST_BACKUP_TIME] = System.currentTimeMillis() }
            } catch (e: Exception) {
                Log.e(TAG, "自动备份失败", e)
            }
        }

        // 每 7 天清理一次旧备份
        if (now - lastClean >= CLEAN_INTERVAL_MS) {
            try {
                val removed = cleanOldBackups()
                Log.i(TAG, "清理旧备份完成，删除 $removed 个文件")
                dataStore.edit { it[KEY_LAST_CLEAN_TIME] = System.currentTimeMillis() }
            } catch (e: Exception) {
                Log.e(TAG, "清理旧备份失败", e)
            }
        }
    }

    /**
     * 清理超过保留期的旧备份文件。
     *
     * 仅删除 backup 目录下修改时间早于保留期的 .zip 文件。
     *
     * @return 删除的文件数
     */
    private fun cleanOldBackups(): Int {
        val dir = siteOutputRepository.backupDir
        if (!dir.exists()) return 0
        val cutoff = System.currentTimeMillis() - BACKUP_RETENTION_MS
        var removed = 0
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".zip") && file.lastModified() < cutoff) {
                if (file.delete()) removed++
            }
        }
        return removed
    }
}
