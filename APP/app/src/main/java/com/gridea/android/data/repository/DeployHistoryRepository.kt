package com.gridea.android.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gridea.android.data.model.DeployRecord
import com.gridea.android.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 部署历史仓库
 *
 * 使用 DataStore 持久化部署历史记录（JSON 序列化存储为单个字符串）。
 * 最多保留最近 [MAX_HISTORY] 条记录，超过时自动丢弃最旧的。
 *
 * 存储结构：
 * - KEY_DEPLOY_HISTORY_JSON：部署历史记录列表（JSONArray → 字符串）
 * - KEY_LAST_FILE_MANIFEST：上次部署的文件清单（JSONArray → 字符串，用于回滚对比）
 */
@Singleton
class DeployHistoryRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        /** 历史记录最大保留条数 */
        private const val MAX_HISTORY = 20

        private val KEY_DEPLOY_HISTORY_JSON = stringPreferencesKey("deploy_history_json")
        private val KEY_LAST_FILE_MANIFEST = stringPreferencesKey("deploy_last_file_manifest")
    }

    /**
     * 获取历史记录流（按时间倒序，最新在前）
     */
    fun getHistory(): Flow<List<DeployRecord>> {
        return dataStore.data.map { prefs ->
            parseHistory(prefs[KEY_DEPLOY_HISTORY_JSON])
        }
    }

    /**
     * 保存一条部署记录。
     *
     * 新记录插入到列表头部，超过 [MAX_HISTORY] 条时丢弃尾部最旧的。
     * 如果记录为成功部署，同时更新"上次部署文件清单"用于回滚参考。
     */
    suspend fun saveRecord(record: DeployRecord) {
        dataStore.edit { prefs ->
            val current = parseHistory(prefs[KEY_DEPLOY_HISTORY_JSON]).toMutableList()
            current.add(0, record)
            // 超出上限时丢弃最旧的
            while (current.size > MAX_HISTORY) {
                current.removeAt(current.lastIndex)
            }
            prefs[KEY_DEPLOY_HISTORY_JSON] = serializeHistory(current)

            // 成功部署时更新"上次部署文件清单"
            if (record.success && record.fileManifest.isNotEmpty()) {
                prefs[KEY_LAST_FILE_MANIFEST] = serializeManifest(record.fileManifest)
            }
        }
        AppLogger.i("DeployHistory", "已保存部署记录：${record.platform} success=${record.success}")
    }

    /**
     * 清空所有历史记录（不影响上次文件清单，便于回滚参考）
     */
    suspend fun clearHistory() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_DEPLOY_HISTORY_JSON)
        }
        AppLogger.i("DeployHistory", "已清空部署历史")
    }

    /**
     * 按 ID 批量删除历史记录。
     *
     * 通过 id 集合过滤当前列表，保留未在集合中的记录。
     * 若所有记录均被删除，等价于清空（不影响上次文件清单）。
     *
     * @param ids 要删除的记录 ID 集合
     */
    suspend fun deleteRecordsByIds(ids: Set<Long>) {
        if (ids.isEmpty()) return
        dataStore.edit { prefs ->
            val current = parseHistory(prefs[KEY_DEPLOY_HISTORY_JSON])
            val remaining = current.filterNot { it.id in ids }
            if (remaining.isEmpty()) {
                prefs.remove(KEY_DEPLOY_HISTORY_JSON)
            } else {
                prefs[KEY_DEPLOY_HISTORY_JSON] = serializeHistory(remaining)
            }
        }
        AppLogger.i("DeployHistory", "已批量删除 ${ids.size} 条部署历史")
    }

    /**
     * 获取上次成功部署记录（按时间倒序第一条 success=true 的记录）
     */
    suspend fun getLastSuccessRecord(): DeployRecord? {
        return getHistory().first().firstOrNull { it.success }
    }

    /**
     * 单独保存当前部署的文件清单（用于回滚对比，与 [saveRecord] 中自动保存等价）
     */
    suspend fun saveFileManifest(manifest: List<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_FILE_MANIFEST] = serializeManifest(manifest)
        }
    }

    /**
     * 获取上次部署的文件清单
     */
    suspend fun getLastFileManifest(): List<String> {
        return parseManifest(
            dataStore.data.first()[KEY_LAST_FILE_MANIFEST]
        )
    }

    // ===== JSON 序列化辅助 =====

    private fun parseHistory(json: String?): List<DeployRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DeployRecord(
                    id = obj.optLong("id", 0L),
                    timestamp = obj.optLong("timestamp", 0L),
                    platform = obj.optString("platform", ""),
                    success = obj.optBoolean("success", false),
                    fileCount = obj.optInt("fileCount", 0),
                    message = obj.optString("message", ""),
                    url = obj.optString("url", "").ifEmpty { null },
                    fileManifest = parseManifest(obj.optString("fileManifest", ""))
                )
            }
        }.getOrElse { e ->
            AppLogger.e("DeployHistory", "解析部署历史失败：${e.message}", e)
            emptyList()
        }
    }

    private fun serializeHistory(records: List<DeployRecord>): String {
        val arr = JSONArray()
        records.forEach { r ->
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("timestamp", r.timestamp)
            obj.put("platform", r.platform)
            obj.put("success", r.success)
            obj.put("fileCount", r.fileCount)
            obj.put("message", r.message)
            r.url?.let { obj.put("url", it) }
            obj.put("fileManifest", JSONArray(r.fileManifest))
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseManifest(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i -> arr.getString(i) }
        }.getOrElse { emptyList() }
    }

    private fun serializeManifest(manifest: List<String>): String {
        return JSONArray(manifest).toString()
    }
}
