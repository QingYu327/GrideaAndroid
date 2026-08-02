package com.gridea.android.ui.screen.theme

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridea.android.R
import com.gridea.android.data.model.ThemePack
import com.gridea.android.data.repository.ThemePackRepository
import com.gridea.android.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 主题管理 ViewModel
 *
 * 管理主题列表、主题切换、主题导入/删除、主题配置更新
 */
@HiltViewModel
class ThemeManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val themePackRepository: ThemePackRepository
) : ViewModel() {

    private val _themes = MutableStateFlow<List<ThemePack>>(emptyList())
    val themes: StateFlow<List<ThemePack>> = _themes.asStateFlow()

    val activeThemeId: StateFlow<String> = themePackRepository.activeThemeId

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult.asStateFlow()

    private val _batchImportProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val batchImportProgress: StateFlow<Pair<Int, Int>?> = _batchImportProgress.asStateFlow()

    init {
        refresh()
    }

    /**
     * 重新从仓库加载主题列表
     */
    fun refresh() {
        _themes.value = themePackRepository.getAllThemes()
    }

    /**
     * 启用指定主题
     */
    fun enableTheme(id: String) {
        viewModelScope.launch {
            try {
                themePackRepository.setActiveTheme(id)
                refresh()
            } catch (e: Exception) {
                AppLogger.e("Theme", "切换主题失败: $id", e)
                _importResult.value = context.getString(R.string.theme_import_failed, "切换主题失败: ${e.message}")
            }
        }
    }

    /**
     * 从 Uri 导入主题包(.zip)
     * 将 URI 内容复制到临时文件后调用仓库导入
     */
    fun importTheme(uri: Uri) {
        viewModelScope.launch {
            try {
                val tempFile = File.createTempFile("theme_import", ".zip", context.cacheDir)
                val copied = try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                        true
                    } ?: false
                } catch (e: Exception) {
                    AppLogger.w("Theme", "复制导入文件失败: ${uri}", e)
                    false
                }

                if (!copied) {
                    AppLogger.w("Theme", "无法读取导入文件: $uri")
                    _importResult.value = context.getString(R.string.theme_import_failed, "无法读取文件")
                    tempFile.delete()
                    return@launch
                }

                val result = themePackRepository.importTheme(tempFile)
                tempFile.delete()

                if (result.isSuccess) {
                    val theme = result.getOrNull()
                    AppLogger.action("Theme", "Import", "单次导入成功: ${theme?.id ?: "?"}")
                    _importResult.value = context.getString(R.string.theme_import_success)
                    refresh()
                } else {
                    val msg = result.exceptionOrNull()?.message ?: "未知错误"
                    AppLogger.e("Theme", "单次导入失败: $msg", result.exceptionOrNull())
                    _importResult.value = context.getString(R.string.theme_import_failed, msg)
                }
            } catch (e: Exception) {
                AppLogger.e("Theme", "单次导入异常: ${e.message}", e)
                _importResult.value = context.getString(R.string.theme_import_failed, e.message ?: "未知错误")
            }
        }
    }

    /**
     * 删除用户主题
     */
    fun deleteTheme(id: String) {
        viewModelScope.launch {
            try {
                themePackRepository.deleteUserTheme(id)
                refresh()
            } catch (e: Exception) {
                AppLogger.e("Theme", "删除主题失败: $id", e)
                _importResult.value = context.getString(R.string.theme_import_failed, "删除主题失败: ${e.message}")
            }
        }
    }

    /**
     * 批量导入主题包(.zip)
     * 逐个导入，统计成功/失败数量，并报告进度
     */
    fun importThemes(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var successCount = 0
            var failCount = 0
            val total = uris.size
            _batchImportProgress.value = 0 to total
            AppLogger.i("Theme", "开始批量导入: 共 $total 个主题包")

            uris.forEachIndexed { index, uri ->
                _batchImportProgress.value = (index + 1) to total
                try {
                    val tempFile = File.createTempFile("theme_batch_$index", ".zip", context.cacheDir)
                    val copied = try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                            true
                        } ?: false
                    } catch (e: Exception) {
                        AppLogger.w("Theme", "批量导入[${index + 1}/$total] 复制文件失败: $uri", e)
                        false
                    }

                    if (!copied) {
                        failCount++
                        tempFile.delete()
                        return@forEachIndexed
                    }

                    val result = themePackRepository.importTheme(tempFile)
                    tempFile.delete()

                    if (result.isSuccess) {
                        successCount++
                        AppLogger.i("Theme", "批量导入[${index + 1}/$total] 成功: ${result.getOrNull()?.id}")
                    } else {
                        failCount++
                        AppLogger.w("Theme", "批量导入[${index + 1}/$total] 失败: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    failCount++
                    AppLogger.e("Theme", "批量导入[${index + 1}/$total] 异常: ${e.message}", e)
                }
            }

            _batchImportProgress.value = null
            _importResult.value = context.getString(R.string.theme_batch_import_result, successCount, failCount)
            AppLogger.action("Theme", "BatchImport", "批量导入完成: 成功 $successCount 失败 $failCount 总计 $total")
            refresh()
        }
    }

    /**
     * 批量删除用户主题
     */
    fun deleteThemes(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            var failCount = 0
            AppLogger.i("Theme", "开始批量删除: 共 ${ids.size} 个主题")
            ids.forEach { id ->
                try {
                    themePackRepository.deleteUserTheme(id)
                } catch (e: Exception) {
                    failCount++
                    AppLogger.e("Theme", "批量删除失败: $id", e)
                }
            }
            refresh()
            AppLogger.action("Theme", "BatchDelete", "批量删除完成: 成功 ${ids.size - failCount} 失败 $failCount")
            if (failCount > 0) {
                _importResult.value = context.getString(R.string.theme_import_failed, "$failCount 个主题删除失败")
            }
        }
    }

    /**
     * 清除批量导入进度
     */
    fun clearBatchImportProgress() {
        _batchImportProgress.value = null
    }

    /**
     * 恢复缺失的内置主题
     *
     * 对比 assets 中的内置主题与 filesDir 中已安装的主题，仅恢复缺失的。
     * 已存在的主题（含用户自定义配置）不会被覆盖。
     */
    fun restoreBuiltinThemes() {
        viewModelScope.launch {
            try {
                val builtinThemes = themePackRepository.getBuiltinThemes()
                val existingIds = themePackRepository.getAllThemes().map { it.id }.toHashSet()
                val missing = builtinThemes.filter { it.id !in existingIds }
                if (missing.isEmpty()) {
                    _importResult.value = context.getString(R.string.theme_restore_builtin_none)
                    return@launch
                }
                for (theme in missing) {
                    themePackRepository.restoreBuiltinTheme(theme.id)
                }
                _importResult.value = context.getString(
                    R.string.theme_restore_builtin_success, missing.size
                )
                AppLogger.action("Theme", "RestoreBuiltin", "恢复内置主题: ${missing.size}个")
                refresh()
            } catch (e: Exception) {
                AppLogger.e("Theme", "恢复内置主题失败", e)
                _importResult.value = context.getString(
                    R.string.theme_restore_builtin_failed, e.message ?: "未知错误"
                )
            }
        }
    }

    /**
     * 更新主题配置值
     */
    fun updateConfig(themeId: String, key: String, value: Any) {
        viewModelScope.launch {
            try {
                themePackRepository.updateConfigValue(themeId, key, value)
                refresh()
            } catch (e: Exception) {
                AppLogger.e("Theme", "更新配置失败: themeId=$themeId key=$key", e)
                _importResult.value = context.getString(R.string.theme_import_failed, "更新配置失败: ${e.message}")
            }
        }
    }

    /**
     * 清除导入结果消息
     */
    fun clearImportResult() {
        _importResult.value = null
    }
}
