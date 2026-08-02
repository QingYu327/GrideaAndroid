package com.gridea.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gridea.android.data.model.ConfigOption
import com.gridea.android.data.model.ThemeAsset
import com.gridea.android.data.model.ThemeConfigItem
import com.gridea.android.data.model.ThemePack
import com.gridea.android.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主题包仓库
 *
 * 管理内置主题（assets/themes/）和用户导入主题（filesDir/themes/）
 * 持久化激活主题 ID 和各主题的配置值到 DataStore
 */
@Singleton
class ThemePackRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 激活主题 ID 的 StateFlow */
    private val _activeThemeId = MutableStateFlow(DEFAULT_THEME_ID)
    val activeThemeId: StateFlow<String> = _activeThemeId.asStateFlow()

    /** 主题列表缓存 */
    private var builtinThemesCache: List<ThemePack>? = null
    private var userThemesCache: List<ThemePack>? = null

    companion object {
        private val KEY_ACTIVE_THEME = stringPreferencesKey("active_theme_id")
        private val KEY_BUILTIN_INSTALLED = booleanPreferencesKey("builtin_themes_installed")
        private const val THEMES_ASSET_DIR = "themes"
        private const val THEMES_USER_DIR = "themes"
        // 内置默认主题 ID。首次启动时安装到 filesDir，开箱即用且可删除。
        private const val DEFAULT_THEME_ID = "magazine"
    }

    init {
        // 从 DataStore 加载激活主题 ID
        scope.launch {
            dataStore.data.collect { prefs ->
                _activeThemeId.value = prefs[KEY_ACTIVE_THEME] ?: DEFAULT_THEME_ID
            }
        }
    }

    /**
     * 获取内置主题列表（从 assets 加载，带缓存）
     *
     * 用于"恢复内置主题"功能：[ensureBuiltinThemesInstalled] 和 [restoreBuiltinTheme]
     * 都从此列表读取 assets 中的主题元数据。返回的 ThemePack 标记 isBuiltin=true、sourceDir=null。
     */
    fun getBuiltinThemes(): List<ThemePack> {
        builtinThemesCache?.let { return it }
        val themes = mutableListOf<ThemePack>()
        try {
            val assetFiles = context.assets.list(THEMES_ASSET_DIR) ?: emptyArray()
            for (dirName in assetFiles) {
                val theme = loadThemeFromAssets("$THEMES_ASSET_DIR/$dirName")
                if (theme != null) themes.add(theme)
            }
        } catch (_: Exception) {}
        builtinThemesCache = themes
        return themes
    }

    /**
     * 获取用户导入主题列表（从 filesDir/themes/ 加载）
     */
    fun getUserThemes(): List<ThemePack> {
        userThemesCache?.let { return it }
        val themes = mutableListOf<ThemePack>()
        val userThemesDir = File(context.filesDir, THEMES_USER_DIR)
        if (userThemesDir.exists()) {
            userThemesDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val theme = loadThemeFromDir(dir)
                    if (theme != null) themes.add(theme)
                }
            }
        }
        userThemesCache = themes
        return themes
    }

    /**
     * 获取所有主题（仅 filesDir 中的用户主题，不再用内置主题补齐）
     *
     * 内置主题在首次启动时已由 [ensureBuiltinThemesInstalled] 复制到 filesDir，
     * 因此只需返回 filesDir 中的主题即可。删除 filesDir 中的副本后，主题不再显示。
     *
     * 若 filesDir 为空（如用户删除了所有主题），返回空列表，
     * 由 [getActiveTheme] 负责回退安装内置主题。
     */
    fun getAllThemes(): List<ThemePack> {
        return getUserThemes()
    }

    /**
     * 获取当前激活主题
     *
     * 回退链：activeId → 已安装主题中第一个 → 安装内置主题后重新查找
     *
     * 当 filesDir 中没有任何主题（如用户删除了所有内置主题副本）时，
     * 会触发 [ensureBuiltinThemesInstalled] 重新安装内置主题，确保始终有可用主题。
     */
    suspend fun getActiveTheme(): ThemePack {
        val allThemes = getAllThemes()
        val activeId = _activeThemeId.value
        // 1. activeId 匹配
        allThemes.find { it.id == activeId }?.let { return it }
        // 2. 任意已安装主题
        allThemes.firstOrNull()?.let { return it }
        // 3. 无任何主题：确保内置主题已安装，然后重新加载
        ensureBuiltinThemesInstalled()
        userThemesCache = null
        val refreshed = getAllThemes()
        return refreshed.find { it.id == activeId }
            ?: refreshed.find { it.id == DEFAULT_THEME_ID }
            ?: refreshed.firstOrNull()
            ?: getBuiltinThemes().first()
    }

    /**
     * 首次启动时将内置主题从 assets 复制到 filesDir/themes/
     *
     * 通过 DataStore 的 `builtin_themes_installed` 标志保证只执行一次。
     * 复制后内置主题与用户导入主题统一存放在 filesDir/themes/，可被
     * SiteRenderer 正常加载模板（templates/ 子目录），且可被用户删除。
     */
    suspend fun ensureBuiltinThemesInstalled() {
        val prefs = dataStore.data.first()
        if (prefs[KEY_BUILTIN_INSTALLED] == true) {
            return
        }
        AppLogger.i("Theme", "首次启动：开始安装内置主题到 filesDir")
        val builtinThemes = getBuiltinThemes()
        for (theme in builtinThemes) {
            copyBuiltinThemeToFilesDir(theme.id, overwrite = false)
        }
        userThemesCache = null
        dataStore.edit { it[KEY_BUILTIN_INSTALLED] = true }
        AppLogger.action("Theme", "Install", "内置主题安装完成: ${builtinThemes.size}个")
    }

    /**
     * 从 assets 重新复制指定内置主题到 filesDir
     *
     * 用于"恢复内置主题"功能：当用户删除了某个内置主题的 filesDir 副本后，
     * 可调用此方法恢复。会覆盖 filesDir 中已有的同名主题目录。
     */
    suspend fun restoreBuiltinTheme(themeId: String) {
        val builtin = getBuiltinThemes().find { it.id == themeId }
        if (builtin == null) {
            AppLogger.w("Theme", "恢复内置主题失败: 未在 assets 中找到主题 $themeId")
            return
        }
        copyBuiltinThemeToFilesDir(themeId, overwrite = true)
        userThemesCache = null
        AppLogger.action("Theme", "Restore", "恢复内置主题: $themeId")
    }

    /**
     * 切换激活主题
     */
    suspend fun setActiveTheme(id: String) {
        _activeThemeId.value = id
        dataStore.edit { it[KEY_ACTIVE_THEME] = id }
        AppLogger.action("Theme", "Activate", "切换激活主题: $id")
    }

    /**
     * 获取所有用户主题的配置值快照（用于备份导出）
     *
     * 遍历 filesDir/themes/ 中的所有主题，将每个主题的 configValues（已合并默认值与
     * DataStore 中的已保存值）统一转换为字符串映射，便于 JSON 序列化。
     *
     * @return Map<themeId, Map<configKey, configValue>>，value 全部为字符串
     */
    fun getAllUserThemeConfigs(): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, Map<String, String>>()
        getUserThemes().forEach { themePack ->
            result[themePack.id] = themePack.configValues.mapValues { it.value.toString() }
        }
        return result
    }

    /**
     * 恢复主题配置值（用于备份导入）
     *
     * 将备份文件中的所有主题配置值逐条写回 DataStore。
     * 不会创建/删除主题包本身，仅恢复配置值；若对应主题未安装，写入的配置值会
     * 成为 DataStore 中的孤立 key（无害，待该主题导入后即可生效）。
     *
     * @param configs Map<themeId, Map<configKey, configValue>>
     */
    suspend fun restoreThemeConfigs(configs: Map<String, Map<String, String>>) {
        configs.forEach { (themeId, configMap) ->
            configMap.forEach { (key, value) ->
                updateConfigValue(themeId, key, value)
            }
        }
    }

    /**
     * 获取活动主题 ID（用于备份导出）
     *
     * 同步读取当前内存中的激活主题 ID（由 DataStore collect 同步维护）。
     */
    fun getActiveThemeIdSync(): String {
        return _activeThemeId.value
    }

    /**
     * 更新某主题的配置值
     * 持久化到 DataStore，key 格式: theme_config_{themeId}_{key}
     */
    suspend fun updateConfigValue(themeId: String, key: String, value: Any) {
        AppLogger.i("Theme", "更新配置: themeId=$themeId key=$key value=$value")
        val prefKey = stringPreferencesKey("theme_config_${themeId}_$key")
        dataStore.edit { prefs ->
            when (value) {
                is String -> prefs[prefKey] = value
                is Int -> prefs[prefKey] = value.toString()
                is Boolean -> prefs[prefKey] = value.toString()
                else -> prefs[prefKey] = value.toString()
            }
        }
        // 清除缓存让下次读取时重新合并配置值
        builtinThemesCache = null
        userThemesCache = null
    }

    /**
     * 同步获取配置值（从 DataStore 缓存中读取）
     *
     * DataStore preferences 只支持 String 存储，所以更新时 Boolean/Int 等都被
     * 转成字符串。读取时必须根据 [configItems] 的 `type` 字段还原真实类型，
     * 否则 switch 配置永远是 String "true"/"false"，渲染时用 `as? Boolean`
     * 取值会失败导致配置项不生效。
     *
     * 类型映射规则：
     * - `switch` → Boolean（"true"/"false" → true/false）
     * - `number`/`slider` → Double（按小数解析，失败回退原字符串）
     * - 其他 → String（透传）
     *
     * @param themeId 主题 ID
     * @param configItems 该主题的 customConfig 列表（用于推断每个 key 的目标类型），
     *                    传 null 时全部按 String 返回（向后兼容）
     */
    private fun getConfigValuesSync(themeId: String, configItems: List<ThemeConfigItem>? = null): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val prefs = runBlocking { dataStore.data.first() }
        val prefix = "theme_config_${themeId}_"
        // 构建 key → type 映射，方便 O(1) 查询
        val typeMap = configItems?.associate { it.name to it.type } ?: emptyMap()
        prefs.asMap().forEach { (key, value) ->
            val keyStr = key.name
            if (keyStr.startsWith(prefix)) {
                val configKey = keyStr.substring(prefix.length)
                val strVal = value.toString()
                // 根据 customConfig.type 还原真实类型，避免下游 as? Boolean 永远返回 null
                result[configKey] = when (typeMap[configKey]) {
                    "switch" -> strVal.equals("true", ignoreCase = true)
                    "number", "slider" -> strVal.toDoubleOrNull() ?: strVal
                    else -> strVal
                }
            }
        }
        return result
    }

    /**
     * 从 assets 加载主题
     */
    private fun loadThemeFromAssets(assetPath: String): ThemePack? {
        return try {
            val jsonStr = context.assets.open("$assetPath/theme.json").bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)

            val css = try {
                context.assets.open("$assetPath/custom.css").bufferedReader().use { it.readText() }
            } catch (_: Exception) { "" }

            val js = try {
                context.assets.open("$assetPath/custom.js").bufferedReader().use { it.readText() }
            } catch (_: Exception) { null }

            val themeId = json.optString("id", assetPath.substringAfterLast("/"))
            // 内置主题的 assets 资源仍在 APK assets 目录内，sourceDir=null
            // SiteRenderer 在复制内置主题 assets 时会从 context.assets 读取
            // 支持 preview.png 和 preview.jpg 两种格式
            val previewImage = resolveAssetPreviewImage(assetPath, themeId)
            parseThemePack(
                json, css, js, isBuiltin = true, themeId = themeId,
                previewImage = previewImage,
                sourceDir = null
            )
        } catch (e: Exception) {
            AppLogger.w("Theme", "加载内置主题失败: $assetPath", e)
            null
        }
    }

    /**
     * 解析内置主题 assets 目录中的预览图路径
     * 支持 preview.png / preview.jpg / preview.jpeg 三种格式，按优先级返回第一个存在的
     */
    private fun resolveAssetPreviewImage(assetPath: String, themeId: String): String? {
        val candidates = listOf("preview.png", "preview.jpg", "preview.jpeg")
        for (name in candidates) {
            try {
                context.assets.open("$assetPath/$name").use { return "assets://themes/$themeId/$name" }
            } catch (_: Exception) { /* 文件不存在，尝试下一个 */ }
        }
        return null
    }

    /**
     * 从文件目录加载主题
     */
    private fun loadThemeFromDir(dir: File): ThemePack? {
        return try {
            val jsonFile = File(dir, "theme.json")
            if (!jsonFile.exists()) return null
            val jsonStr = jsonFile.readText()
            val json = JSONObject(jsonStr)

            val css = try { File(dir, "custom.css").readText() } catch (_: Exception) { "" }
            val js = try { File(dir, "custom.js").readText() } catch (_: Exception) { null }

            val themeId = json.optString("id", dir.name)
            // 用户主题包内 preview 图片若存在,使用 file:// 协议加载
            // 支持 preview.png 和 preview.jpg 两种格式
            val previewFile = listOf("preview.png", "preview.jpg", "preview.jpeg")
                .map { File(dir, it) }
                .firstOrNull { it.exists() }
            val previewImage = if (previewFile != null) "file://${previewFile.absolutePath}" else null
            parseThemePack(
                json, css, js, isBuiltin = false, themeId = themeId,
                previewImage = previewImage,
                sourceDir = dir.absolutePath
            )
        } catch (e: Exception) {
            AppLogger.w("Theme", "加载用户主题失败: ${dir.name}", e)
            null
        }
    }

    /**
     * 将内置主题从 assets 复制到 filesDir/themes/{themeId}/
     *
     * 文件结构：
     * - theme.json, custom.css, custom.js, preview.* → 目标目录根
     * - *.peb → 目标目录/templates/ 子目录（SiteRenderer 从 sourceDir/templates/ 加载模板）
     *
     * @param themeId 主题 ID（对应 assets/themes/{themeId}/）
     * @param overwrite true 时覆盖已有目录（用于恢复）；false 时跳过已存在的目录
     */
    private fun copyBuiltinThemeToFilesDir(themeId: String, overwrite: Boolean) {
        val assetPath = "$THEMES_ASSET_DIR/$themeId"
        val targetDir = File(context.filesDir, "$THEMES_USER_DIR/$themeId")
        if (targetDir.exists() && !overwrite) {
            return
        }
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()
        try {
            // 复制 theme.json, custom.css, custom.js
            listOf("theme.json", "custom.css", "custom.js").forEach { fileName ->
                try {
                    context.assets.open("$assetPath/$fileName").use { input ->
                        File(targetDir, fileName).outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Exception) { /* 文件不存在，跳过 */ }
            }
            // 复制预览图（preview.jpg / preview.png / preview.jpeg，取第一个存在的）
            for (fileName in listOf("preview.jpg", "preview.png", "preview.jpeg")) {
                try {
                    context.assets.open("$assetPath/$fileName").use { input ->
                        File(targetDir, fileName).outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    break
                } catch (_: Exception) { /* 文件不存在，尝试下一个 */ }
            }
            // 创建 templates/ 子目录，复制所有 .peb 文件
            val templatesDir = File(targetDir, "templates")
            templatesDir.mkdirs()
            val pebFiles = context.assets.list(assetPath)?.filter { it.endsWith(".peb") } ?: emptyList()
            for (pebFile in pebFiles) {
                context.assets.open("$assetPath/$pebFile").use { input ->
                    File(templatesDir, pebFile).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            AppLogger.d("Theme", "内置主题已复制到 filesDir: $themeId (模板文件: ${pebFiles.size}个)")
        } catch (e: Exception) {
            AppLogger.w("Theme", "复制内置主题失败: $themeId", e)
        }
    }

    /**
     * 解析 JSON 为 ThemePack
     */
    private fun parseThemePack(
        json: JSONObject,
        css: String,
        js: String?,
        isBuiltin: Boolean,
        themeId: String,
        previewImage: String?,
        sourceDir: String?
    ): ThemePack {
        val customConfig = mutableListOf<ThemeConfigItem>()
        val configArray = json.optJSONArray("customConfig")
        if (configArray != null) {
            for (i in 0 until configArray.length()) {
                val item = configArray.getJSONObject(i)
                val options = item.optJSONArray("options")?.let { optsArr ->
                    (0 until optsArr.length()).map { j ->
                        val opt = optsArr.getJSONObject(j)
                        ConfigOption(
                            label = opt.optString("label"),
                            value = opt.optString("value")
                        )
                    }
                }
                customConfig.add(ThemeConfigItem(
                    name = item.optString("name"),
                    label = item.optString("label"),
                    group = item.optString("group", "默认").trim().ifEmpty { "默认" },
                    value = item.opt("value") ?: "",
                    type = item.optString("type", "input"),
                    note = item.optString("note").ifEmpty { null },
                    options = options,
                    min = item.optString("min").ifEmpty { null },
                    max = item.optString("max").ifEmpty { null },
                    step = item.optString("step").ifEmpty { null },
                    placeholder = item.optString("placeholder").ifEmpty { null },
                    language = item.optString("language").ifEmpty { null }
                ))
            }
        }

        // 解析 assets 资源数组（自定义字体/JS库/扩展CSS/图片等）
        // 兼容两种格式：
        // 1. 字符串数组：["assets/fonts/icon.woff", "assets/js/main.js"]
        // 2. 对象数组：[{"type":"font","src":"assets/fonts/icon.woff","defer":false}]
        val assets = mutableListOf<ThemeAsset>()
        json.optJSONArray("assets")?.let { assetsArr ->
            for (i in 0 until assetsArr.length()) {
                when (val elem = assetsArr.opt(i)) {
                    is String -> assets.add(ThemeAsset(
                        type = "file",
                        src = elem,
                        defer_ = false,
                        async_ = false
                    ))
                    is org.json.JSONObject -> assets.add(ThemeAsset(
                        type = elem.optString("type", "file"),
                        src = elem.optString("src"),
                        defer_ = elem.optBoolean("defer", false),
                        async_ = elem.optBoolean("async", false)
                    ))
                }
            }
        }

        // 合并默认值和已保存配置值
        val configValues = mutableMapOf<String, Any>()
        customConfig.forEach { item ->
            configValues[item.name] = item.value
        }
        // 同步读取已保存的配置值覆盖默认值
        // 关键：传入 customConfig，让 getConfigValuesSync 能根据 type 还原 Boolean/Number
        // 否则下游 as? Boolean 永远失败，switch 配置项（darkmode/motion_enable 等）永远不生效
        try {
            val savedValues = getConfigValuesSync(themeId, customConfig)
            configValues.putAll(savedValues)
        } catch (_: Exception) {}

        val tagsArray = json.optJSONArray("tags")
        val tags = if (tagsArray != null) {
            (0 until tagsArray.length()).map { tagsArray.optString(it) }
        } else {
            emptyList()
        }

        // 检测主题包是否自带 templates/ 目录（用户主题才支持）
        val hasTemplates = sourceDir != null && File(sourceDir, "templates").let { it.exists() && it.isDirectory }

        return ThemePack(
            id = themeId,
            name = json.optString("name", themeId),
            version = json.optString("version", "1.0.0"),
            author = json.optString("author", "Unknown"),
            description = json.optString("description", ""),
            previewImage = previewImage,
            tags = tags,
            isBuiltin = isBuiltin,
            customConfig = customConfig,
            css = css,
            js = js,
            configValues = configValues,
            assets = assets,
            sourceDir = sourceDir,
            hasTemplates = hasTemplates
        )
    }

    /**
     * 导入 .zip 主题包(ZIP 格式)
     *
     * 解压流程:
     * 1. 解压到临时目录,含路径穿越防护
     * 2. 递归查找 theme.json(支持 ZIP 根目录或一级子目录两种布局)
     * 3. 解析 theme.json 获取 id,移动到 filesDir/themes/{themeId}/
     * 4. 清除用户主题缓存,下次访问时重新扫描
     *
     * @param file .zip 主题包文件
     * @return Result<ThemePack> 成功返回主题包,失败返回异常
     */
    suspend fun importTheme(file: File): Result<ThemePack> {
        return try {
            AppLogger.i("Theme", "开始导入主题包: ${file.name}")
            val userThemesDir = File(context.filesDir, THEMES_USER_DIR)
            if (!userThemesDir.exists()) userThemesDir.mkdirs()

            // 先解压到临时目录，解析 theme.json 获取 id，再重命名
            val tempDir = File(userThemesDir, "_temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            ZipInputStream(file.inputStream()).use { zis ->
                var entry = zis.nextEntry
                val buffer = ByteArray(1024)
                while (entry != null) {
                    // 规范化路径：ZIP 标准使用正斜杠 /，但部分 Windows 工具（如 PowerShell
                    // Compress-Archive）会使用反斜杠 \。Android 是 Linux 内核，不识别 \ 为
                    // 路径分隔符，会导致子目录文件被创建为带 \ 的扁平文件名，目录结构丢失。
                    val safeEntryName = entry.name.replace('\\', '/')
                    // 路径穿越防护:确保解压路径不超出目标目录(加分隔符防止前缀同名目录绕过)
                    val targetFile = File(tempDir, safeEntryName)
                    val canonicalTarget = targetFile.canonicalPath
                    val canonicalBase = tempDir.canonicalPath
                    if (canonicalTarget != canonicalBase &&
                        !canonicalTarget.startsWith(canonicalBase + File.separator)) {
                        throw SecurityException("路径穿越攻击检测：${entry.name}")
                    }
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        targetFile.outputStream().use { output ->
                            while (true) {
                                val len = zis.read(buffer)
                                if (len <= 0) break
                                output.write(buffer, 0, len)
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            // 检查 ZIP 根目录是否有 theme.json，可能在子目录中
            var jsonFile = File(tempDir, "theme.json")
            var actualBaseDir = tempDir
            if (!jsonFile.exists()) {
                // 搜索子目录
                tempDir.walk().find { it.name == "theme.json" }?.let {
                    jsonFile = it
                    actualBaseDir = it.parentFile ?: tempDir
                }
            }

            if (!jsonFile.exists()) {
                tempDir.deleteRecursively()
                AppLogger.w("Theme", "导入失败：主题包缺少 theme.json")
                return Result.failure(IllegalArgumentException("主题包缺少 theme.json"))
            }

            val json = JSONObject(jsonFile.readText())
            val themeId = json.optString("id", actualBaseDir.name)

            // 移动临时目录到正式目录
            val finalDir = File(userThemesDir, themeId)
            if (finalDir.exists()) finalDir.deleteRecursively()
            actualBaseDir.copyRecursively(finalDir, overwrite = true)
            tempDir.deleteRecursively()

            // 加载并返回
            userThemesCache = null  // 清除缓存
            val theme = loadThemeFromDir(finalDir)
            if (theme != null) {
                AppLogger.action("Theme", "Import", "导入成功: id=${theme.id} name=${theme.name} 配置项=${theme.customConfig.size}个")
                Result.success(theme)
            } else {
                AppLogger.e("Theme", "导入失败：主题包解析失败 (loadThemeFromDir 返回 null)，路径=${finalDir.absolutePath}")
                Result.failure(IllegalArgumentException("主题包解析失败"))
            }
        } catch (e: Exception) {
            AppLogger.e("Theme", "导入异常: ${file.name}", e)
            Result.failure(e)
        }
    }

    /**
     * 删除用户主题
     *
     * 同时支持删除内置主题的 filesDir 副本（内置主题安装后存放在 filesDir/themes/）。
     * 删除 filesDir 副本后，主题不再在列表中显示；可通过 [restoreBuiltinTheme] 恢复。
     */
    suspend fun deleteUserTheme(id: String) {
        AppLogger.action("Theme", "Delete", "删除主题: $id")
        val themeDir = File(context.filesDir, "$THEMES_USER_DIR/$id")
        if (themeDir.exists()) {
            themeDir.deleteRecursively()
        }
        // 清理 DataStore 中的配置值
        dataStore.edit { prefs ->
            val prefix = "theme_config_${id}_"
            val keysToRemove = prefs.asMap().keys.filter { it.name.startsWith(prefix) }
            keysToRemove.forEach { prefs.remove(it) }
        }
        // 如果删除的是当前激活主题，切换到第一个可用主题
        if (_activeThemeId.value == id) {
            userThemesCache = null
            val remaining = getUserThemes()
            val newActiveId = remaining.firstOrNull()?.id ?: DEFAULT_THEME_ID
            setActiveTheme(newActiveId)
        }
        userThemesCache = null
    }
}
