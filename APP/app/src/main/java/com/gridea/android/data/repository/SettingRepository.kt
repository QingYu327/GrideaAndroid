package com.gridea.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gridea.android.data.model.Account
import com.gridea.android.data.model.CommentSetting
import com.gridea.android.data.model.DisqusSetting
import com.gridea.android.data.model.GeneralSettings
import com.gridea.android.data.model.GiscusSetting
import com.gridea.android.data.model.GitalkSetting
import com.gridea.android.data.model.Setting
import com.gridea.android.data.model.Theme
import com.gridea.android.data.model.TwikooSetting
import com.gridea.android.data.model.ValineSetting
import com.gridea.android.data.model.WalineSetting
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设置仓库
 *
 * 对应旧版 Gridea 0.9.3 的 src/server/setting.ts 和 src/server/theme.ts
 * 使用 DataStore 持久化站点设置、主题配置、评论配置
 *
 * themeMode 和 languageMode 使用全局 StateFlow 共享，
 * 确保多个 ViewModel 实例（MainActivity 和 SettingScreen）状态同步，
 * 切换时立即生效，无 DataStore 异步延迟。
 *
 * 语言模式额外写入 SharedPreferences，供 Activity attachBaseContext 同步读取
 */
@Singleton
class SettingRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) {

    /** 应用级协程作用域，用于 init 中从 DataStore 加载初始值 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 主题模式全局 StateFlow（system/light/dark），所有 VM 共享 */
    private val _themeModeFlow = MutableStateFlow("system")
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    /** 语言模式全局 StateFlow（system/zh/en），所有 VM 共享 */
    private val _languageModeFlow = MutableStateFlow("system")
    val languageModeFlow: StateFlow<String> = _languageModeFlow.asStateFlow()

    /** 字体大小缩放全局 StateFlow（范围 0.85-1.3，默认 1.0），所有 VM 共享 */
    private val _fontSizeScaleFlow = MutableStateFlow(1.0f)
    val fontSizeScaleFlow: StateFlow<Float> = _fontSizeScaleFlow.asStateFlow()

    /** 动态取色（Material You，Android 12+）全局 StateFlow，所有 VM 共享 */
    private val _dynamicColorFlow = MutableStateFlow(false)
    val dynamicColorFlow: StateFlow<Boolean> = _dynamicColorFlow.asStateFlow()

    /** WebView 调试（Chrome DevTools 远程调试）全局 StateFlow，默认关闭 */
    private val _webViewDebugFlow = MutableStateFlow(false)
    val webViewDebugFlow: StateFlow<Boolean> = _webViewDebugFlow.asStateFlow()

    /** 详细日志（输出调试级日志）全局 StateFlow，默认关闭 */
    private val _verboseLogFlow = MutableStateFlow(false)
    val verboseLogFlow: StateFlow<Boolean> = _verboseLogFlow.asStateFlow()

    /**
     * 隐藏调试入口解锁状态。
     * - debug 版本：始终为 true，调试入口默认可见
     * - release 版本：默认 false，需在"关于"页面连续点击版本号 5 次解锁，
     *   解锁后持久化到 DataStore，下次启动仍生效；再次连续点击 5 次可重新锁定
     */
    private val _debugUnlockFlow = MutableStateFlow(false)
    val debugUnlockFlow: StateFlow<Boolean> = _debugUnlockFlow.asStateFlow()

    /**
     * APP 界面强调色（hex 字符串如 "#9C8FDA"，空串表示用默认淡紫色）
     * 仅在动态取色关闭时生效；动态取色开启时跟随系统 primary
     */
    private val _appAccentColorFlow = MutableStateFlow("")
    val appAccentColorFlow: StateFlow<String> = _appAccentColorFlow.asStateFlow()

    init {
        // 从 DataStore 加载初始值到全局 StateFlow
        scope.launch {
            dataStore.data.collect { prefs ->
                _themeModeFlow.value = prefs[KEY_THEME_MODE] ?: "system"
                _languageModeFlow.value = prefs[KEY_LANGUAGE_MODE] ?: "system"
                // 字体缩放与动态取色
                _fontSizeScaleFlow.value = prefs[KEY_FONT_SIZE_SCALE] ?: 1.0f
                _dynamicColorFlow.value = prefs[KEY_DYNAMIC_COLOR] ?: false
                _appAccentColorFlow.value = prefs[KEY_APP_ACCENT_COLOR] ?: ""
                // 调试开关
                _webViewDebugFlow.value = prefs[KEY_WEB_VIEW_DEBUG] ?: false
                _verboseLogFlow.value = prefs[KEY_VERBOSE_LOG] ?: false
                // 隐藏调试入口：debug 版本始终解锁，release 版本从 DataStore 读取
                _debugUnlockFlow.value = com.gridea.android.BuildConfig.DEBUG ||
                    prefs[KEY_DEBUG_UNLOCK] ?: false
                // 同步详细日志开关到 AppLogger
                AppLogger.setVerboseLogEnabled(_verboseLogFlow.value)
            }
        }
    }

    // ===== 部署设置（Setting）=====

    /**
     * 获取部署设置
     * 对应旧版 Setting.getSetting()
     */
    fun getSetting(): Flow<Setting> {
        return dataStore.data.map { prefs ->
            Setting(
                platform = prefs[KEY_PLATFORM] ?: "github",
                domain = prefs[KEY_DOMAIN] ?: "",
                repository = prefs[KEY_REPOSITORY] ?: "",
                branch = prefs[KEY_BRANCH] ?: "",
                username = prefs[KEY_USERNAME] ?: "",
                email = prefs[KEY_EMAIL] ?: "",
                token = prefs[KEY_TOKEN] ?: "",
                cname = prefs[KEY_CNAME] ?: "",
                port = prefs[KEY_PORT] ?: "22",
                server = prefs[KEY_SERVER] ?: "",
                sftpUsername = prefs[KEY_SFTP_USERNAME] ?: "",
                password = prefs[KEY_PASSWORD] ?: "",
                privateKey = prefs[KEY_PRIVATE_KEY] ?: "",
                remotePath = prefs[KEY_REMOTE_PATH] ?: "",
                netlifyAccessToken = prefs[KEY_NETLIFY_TOKEN] ?: "",
                netlifySiteId = prefs[KEY_NETLIFY_SITE_ID] ?: "",
                vercelAccessToken = prefs[KEY_VERCEL_TOKEN] ?: "",
                vercelProjectId = prefs[KEY_VERCEL_PROJECT_ID] ?: "",
                giteeRepository = prefs[KEY_GITEE_REPOSITORY] ?: "",
                giteeBranch = prefs[KEY_GITEE_BRANCH] ?: "",
                giteeUsername = prefs[KEY_GITEE_USERNAME] ?: "",
                giteeToken = prefs[KEY_GITEE_TOKEN] ?: ""
            )
        }
    }

    /**
     * 保存部署设置
     * 对应旧版 Setting.saveSetting()
     */
    suspend fun saveSetting(setting: Setting) {
        dataStore.edit { prefs ->
            prefs[KEY_PLATFORM] = setting.platform
            prefs[KEY_DOMAIN] = setting.domain
            prefs[KEY_REPOSITORY] = setting.repository
            prefs[KEY_BRANCH] = setting.branch
            prefs[KEY_USERNAME] = setting.username
            prefs[KEY_EMAIL] = setting.email
            prefs[KEY_TOKEN] = setting.token
            prefs[KEY_CNAME] = setting.cname
            prefs[KEY_PORT] = setting.port
            prefs[KEY_SERVER] = setting.server
            prefs[KEY_SFTP_USERNAME] = setting.sftpUsername
            prefs[KEY_PASSWORD] = setting.password
            prefs[KEY_PRIVATE_KEY] = setting.privateKey
            prefs[KEY_REMOTE_PATH] = setting.remotePath
            prefs[KEY_NETLIFY_TOKEN] = setting.netlifyAccessToken
            prefs[KEY_NETLIFY_SITE_ID] = setting.netlifySiteId
            prefs[KEY_VERCEL_TOKEN] = setting.vercelAccessToken
            prefs[KEY_VERCEL_PROJECT_ID] = setting.vercelProjectId
            prefs[KEY_GITEE_REPOSITORY] = setting.giteeRepository
            prefs[KEY_GITEE_BRANCH] = setting.giteeBranch
            prefs[KEY_GITEE_USERNAME] = setting.giteeUsername
            prefs[KEY_GITEE_TOKEN] = setting.giteeToken
        }
    }

    // ===== 主题/站点配置（Theme）=====

    /**
     * 获取主题配置（含站点基础信息）
     * 对应旧版 Theme.getThemeConfig()
     */
    fun getTheme(): Flow<Theme> {
        return dataStore.data.map { prefs ->
            Theme(
                themeName = prefs[KEY_THEME_NAME] ?: "",
                siteName = prefs[KEY_SITE_NAME] ?: "",
                siteDescription = prefs[KEY_SITE_DESC] ?: "",
                footerInfo = prefs[KEY_FOOTER_INFO] ?: "Powered by Gridea",
                siteAuthor = prefs[KEY_SITE_AUTHOR] ?: "",
                siteFavicon = prefs[KEY_SITE_FAVICON] ?: "",
                siteAvatar = prefs[KEY_SITE_AVATAR] ?: "",
                postPageSize = prefs[KEY_POST_PAGE_SIZE] ?: 10,
                archivesPageSize = prefs[KEY_ARCHIVES_PAGE_SIZE] ?: 10,
                showFeatureImage = prefs[KEY_SHOW_FEATURE_IMAGE] ?: true,
                postUrlFormat = prefs[KEY_POST_URL_FORMAT] ?: "SLUG",
                tagUrlFormat = prefs[KEY_TAG_URL_FORMAT] ?: "SLUG",
                dateFormat = prefs[KEY_DATE_FORMAT] ?: "YYYY-MM-DD",
                feedFullText = prefs[KEY_FEED_FULL_TEXT] ?: true,
                feedCount = prefs[KEY_FEED_COUNT] ?: 10,
                archivesPath = prefs[KEY_ARCHIVES_PATH] ?: "archives",
                postPath = prefs[KEY_POST_PATH] ?: "post",
                tagPath = prefs[KEY_TAG_PATH] ?: "tag",
                // 主题样式自定义
                primaryColor = prefs[KEY_PRIMARY_COLOR] ?: "#42b983",
                textColor = prefs[KEY_TEXT_COLOR] ?: "#2c3e50",
                backgroundColor = prefs[KEY_BG_COLOR] ?: "#ffffff",
                fontFamily = prefs[KEY_FONT_FAMILY] ?: "system",
                contentWidth = prefs[KEY_CONTENT_WIDTH] ?: 800,
                borderRadius = prefs[KEY_BORDER_RADIUS] ?: 8
            )
        }
    }

    /**
     * 保存主题配置
     * 对应旧版 Theme.saveThemeConfig()
     */
    suspend fun saveTheme(theme: Theme) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_NAME] = theme.themeName
            prefs[KEY_SITE_NAME] = theme.siteName
            prefs[KEY_SITE_DESC] = theme.siteDescription
            prefs[KEY_FOOTER_INFO] = theme.footerInfo
            prefs[KEY_SITE_AUTHOR] = theme.siteAuthor
            prefs[KEY_SITE_FAVICON] = theme.siteFavicon
            prefs[KEY_SITE_AVATAR] = theme.siteAvatar
            prefs[KEY_POST_PAGE_SIZE] = theme.postPageSize
            prefs[KEY_ARCHIVES_PAGE_SIZE] = theme.archivesPageSize
            prefs[KEY_SHOW_FEATURE_IMAGE] = theme.showFeatureImage
            prefs[KEY_POST_URL_FORMAT] = theme.postUrlFormat
            prefs[KEY_TAG_URL_FORMAT] = theme.tagUrlFormat
            prefs[KEY_DATE_FORMAT] = theme.dateFormat
            prefs[KEY_FEED_FULL_TEXT] = theme.feedFullText
            prefs[KEY_FEED_COUNT] = theme.feedCount
            prefs[KEY_ARCHIVES_PATH] = theme.archivesPath
            prefs[KEY_POST_PATH] = theme.postPath
            prefs[KEY_TAG_PATH] = theme.tagPath
            // 主题样式自定义
            prefs[KEY_PRIMARY_COLOR] = theme.primaryColor
            prefs[KEY_TEXT_COLOR] = theme.textColor
            prefs[KEY_BG_COLOR] = theme.backgroundColor
            prefs[KEY_FONT_FAMILY] = theme.fontFamily
            prefs[KEY_CONTENT_WIDTH] = theme.contentWidth
            prefs[KEY_BORDER_RADIUS] = theme.borderRadius
        }
    }

    // ===== 评论设置 =====

    /**
     * 获取评论设置
     * 对应旧版 Setting.getCommentSetting()
     */
    fun getCommentSetting(): Flow<CommentSetting> {
        return dataStore.data.map { prefs ->
            CommentSetting(
                commentPlatform = prefs[KEY_COMMENT_PLATFORM] ?: "gitalk",
                showComment = prefs[KEY_SHOW_COMMENT] ?: false,
                gitalkSetting = GitalkSetting(
                    clientId = prefs[KEY_GITALK_CLIENT_ID] ?: "",
                    clientSecret = prefs[KEY_GITALK_CLIENT_SECRET] ?: "",
                    repository = prefs[KEY_GITALK_REPO] ?: "",
                    owner = prefs[KEY_GITALK_OWNER] ?: ""
                ),
                giscusSetting = GiscusSetting(
                    repo = prefs[KEY_GISCUS_REPO] ?: "",
                    repoId = prefs[KEY_GISCUS_REPO_ID] ?: "",
                    category = prefs[KEY_GISCUS_CATEGORY] ?: "",
                    categoryId = prefs[KEY_GISCUS_CATEGORY_ID] ?: "",
                    mapping = prefs[KEY_GISCUS_MAPPING] ?: "pathname",
                    theme = prefs[KEY_GISCUS_THEME] ?: "light"
                ),
                disqusSetting = DisqusSetting(
                    api = prefs[KEY_DISQUS_API] ?: "https://disqus.skk.moe/disqus/",
                    apikey = prefs[KEY_DISQUS_APIKEY] ?: "",
                    shortname = prefs[KEY_DISQUS_SHORTNAME] ?: ""
                ),
                valineSetting = ValineSetting(
                    appId = prefs[KEY_VALINE_APP_ID] ?: "",
                    appKey = prefs[KEY_VALINE_APP_KEY] ?: ""
                ),
                twikooSetting = TwikooSetting(
                    envId = prefs[KEY_TWIKOO_ENV_ID] ?: ""
                ),
                walineSetting = WalineSetting(
                    serverURL = prefs[KEY_WALINE_SERVER_URL] ?: ""
                )
            )
        }
    }

    /**
     * 保存评论设置
     * 对应旧版 Setting.saveCommentSetting()
     */
    suspend fun saveCommentSetting(setting: CommentSetting) {
        dataStore.edit { prefs ->
            prefs[KEY_COMMENT_PLATFORM] = setting.commentPlatform
            prefs[KEY_SHOW_COMMENT] = setting.showComment
            prefs[KEY_GITALK_CLIENT_ID] = setting.gitalkSetting.clientId
            prefs[KEY_GITALK_CLIENT_SECRET] = setting.gitalkSetting.clientSecret
            prefs[KEY_GITALK_REPO] = setting.gitalkSetting.repository
            prefs[KEY_GITALK_OWNER] = setting.gitalkSetting.owner
            prefs[KEY_GISCUS_REPO] = setting.giscusSetting.repo
            prefs[KEY_GISCUS_REPO_ID] = setting.giscusSetting.repoId
            prefs[KEY_GISCUS_CATEGORY] = setting.giscusSetting.category
            prefs[KEY_GISCUS_CATEGORY_ID] = setting.giscusSetting.categoryId
            prefs[KEY_GISCUS_MAPPING] = setting.giscusSetting.mapping
            prefs[KEY_GISCUS_THEME] = setting.giscusSetting.theme
            prefs[KEY_DISQUS_API] = setting.disqusSetting.api
            prefs[KEY_DISQUS_APIKEY] = setting.disqusSetting.apikey
            prefs[KEY_DISQUS_SHORTNAME] = setting.disqusSetting.shortname
            prefs[KEY_VALINE_APP_ID] = setting.valineSetting.appId
            prefs[KEY_VALINE_APP_KEY] = setting.valineSetting.appKey
            prefs[KEY_TWIKOO_ENV_ID] = setting.twikooSetting.envId
            prefs[KEY_WALINE_SERVER_URL] = setting.walineSetting.serverURL
        }
    }

    // ===== 账户（GitHub OAuth Device Flow）=====

    /**
     * 获取已登录的 GitHub 账户
     * 未登录时返回空 Account（accessToken 为空）
     */
    fun getAccount(): Flow<Account> {
        return dataStore.data.map { prefs ->
            Account(
                accessToken = prefs[KEY_GH_ACCESS_TOKEN] ?: "",
                login = prefs[KEY_GH_LOGIN] ?: "",
                name = prefs[KEY_GH_NAME] ?: "",
                avatarUrl = prefs[KEY_GH_AVATAR] ?: "",
                htmlUrl = prefs[KEY_GH_HTML_URL] ?: "",
                bio = prefs[KEY_GH_BIO] ?: "",
                company = prefs[KEY_GH_COMPANY] ?: "",
                blog = prefs[KEY_GH_BLOG] ?: "",
                location = prefs[KEY_GH_LOCATION] ?: "",
                email = prefs[KEY_GH_EMAIL] ?: "",
                publicRepos = prefs[KEY_GH_PUBLIC_REPOS] ?: 0,
                followers = prefs[KEY_GH_FOLLOWERS] ?: 0,
                following = prefs[KEY_GH_FOLLOWING] ?: 0,
                createdAt = prefs[KEY_GH_CREATED_AT] ?: ""
            )
        }
    }

    /**
     * 获取 OAuth App Client ID（用户在 GitHub 创建 OAuth App 后填入）
     */
    fun getOAuthClientId(): Flow<String> {
        return dataStore.data.map { prefs ->
            prefs[KEY_OAUTH_CLIENT_ID] ?: ""
        }
    }

    suspend fun saveOAuthClientId(clientId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_OAUTH_CLIENT_ID] = clientId
        }
    }

    /**
     * 保存登录成功的账户信息
     */
    suspend fun saveAccount(account: Account) {
        dataStore.edit { prefs ->
            prefs[KEY_GH_ACCESS_TOKEN] = account.accessToken
            prefs[KEY_GH_LOGIN] = account.login
            prefs[KEY_GH_NAME] = account.name
            prefs[KEY_GH_AVATAR] = account.avatarUrl
            prefs[KEY_GH_HTML_URL] = account.htmlUrl
            prefs[KEY_GH_BIO] = account.bio
            prefs[KEY_GH_COMPANY] = account.company
            prefs[KEY_GH_BLOG] = account.blog
            prefs[KEY_GH_LOCATION] = account.location
            prefs[KEY_GH_EMAIL] = account.email
            prefs[KEY_GH_PUBLIC_REPOS] = account.publicRepos
            prefs[KEY_GH_FOLLOWERS] = account.followers
            prefs[KEY_GH_FOLLOWING] = account.following
            prefs[KEY_GH_CREATED_AT] = account.createdAt
        }
    }

    /**
     * 登出：清除账户信息（不清除 Client ID）
     */
    suspend fun clearAccount() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_GH_ACCESS_TOKEN)
            prefs.remove(KEY_GH_LOGIN)
            prefs.remove(KEY_GH_NAME)
            prefs.remove(KEY_GH_AVATAR)
            prefs.remove(KEY_GH_HTML_URL)
            prefs.remove(KEY_GH_BIO)
            prefs.remove(KEY_GH_COMPANY)
            prefs.remove(KEY_GH_BLOG)
            prefs.remove(KEY_GH_LOCATION)
            prefs.remove(KEY_GH_EMAIL)
            prefs.remove(KEY_GH_PUBLIC_REPOS)
            prefs.remove(KEY_GH_FOLLOWERS)
            prefs.remove(KEY_GH_FOLLOWING)
            prefs.remove(KEY_GH_CREATED_AT)
        }
    }

    // ===== 应用外观 =====

    /**
     * 获取主题模式：system（跟随系统）、light（浅色）、dark（深色）
     * 返回全局 StateFlow，所有 ViewModel 共享同一实例，切换立即生效
     */
    fun getThemeMode(): StateFlow<String> = themeModeFlow

    /**
     * 保存主题模式
     * 同步更新 StateFlow（UI 立即响应），异步写入 DataStore（持久化）
     */
    suspend fun saveThemeMode(mode: String) {
        _themeModeFlow.value = mode
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    /**
     * 获取语言设置：system（跟随系统）、zh（中文）、en（英文）
     * 返回全局 StateFlow，所有 ViewModel 共享同一实例，切换立即生效
     */
    fun getLanguageMode(): StateFlow<String> = languageModeFlow

    /**
     * 保存语言模式
     * 同步更新 StateFlow + 同步写入 SharedPreferences（供 attachBaseContext 读取）+ 异步写入 DataStore
     */
    suspend fun saveLanguageMode(mode: String) {
        applyLanguageModeSync(mode)
        dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE_MODE] = mode
        }
    }

    /**
     * 同步应用语言模式（非 suspend）：
     * 立即更新 StateFlow + 同步 commit SharedPreferences，确保重启后 attachBaseContext 能读到最新值
     * DataStore 异步持久化由 [saveLanguageMode] 或调用方自行处理
     */
    fun applyLanguageModeSync(mode: String) {
        _languageModeFlow.value = mode
        context.getSharedPreferences("gridea_settings", Context.MODE_PRIVATE)
            .edit().putString("language_mode", mode).commit()
    }

    /**
     * 获取字体大小缩放（范围 0.85-1.3，默认 1.0）
     * 返回全局 StateFlow，所有 ViewModel 共享同一实例，切换立即生效
     */
    fun getFontSizeScale(): StateFlow<Float> = fontSizeScaleFlow

    /**
     * 保存字体大小缩放
     * 同步更新 StateFlow（UI 立即响应），异步写入 DataStore（持久化）
     */
    suspend fun saveFontSizeScale(scale: Float) {
        // 钳制到合法范围，避免越界
        val clamped = scale.coerceIn(FONT_SIZE_SCALE_MIN, FONT_SIZE_SCALE_MAX)
        _fontSizeScaleFlow.value = clamped
        dataStore.edit { prefs ->
            prefs[KEY_FONT_SIZE_SCALE] = clamped
        }
    }

    /**
     * 获取动态取色（Material You，Android 12+）
     * 返回全局 StateFlow，所有 ViewModel 共享同一实例，切换立即生效
     */
    fun getDynamicColor(): StateFlow<Boolean> = dynamicColorFlow

    /**
     * 保存动态取色开关
     * 同步更新 StateFlow（UI 立即响应），异步写入 DataStore（持久化）
     */
    suspend fun saveDynamicColor(enabled: Boolean) {
        _dynamicColorFlow.value = enabled
        dataStore.edit { prefs ->
            prefs[KEY_DYNAMIC_COLOR] = enabled
        }
    }

    /**
     * 获取 APP 界面强调色（hex 字符串，空串表示用默认色）
     */
    fun getAppAccentColor(): StateFlow<String> = appAccentColorFlow

    /**
     * 保存 APP 界面强调色
     * @param hex 形如 "#9C8FDA" 的字符串，空串表示恢复默认
     */
    suspend fun saveAppAccentColor(hex: String) {
        _appAccentColorFlow.value = hex
        dataStore.edit { prefs ->
            prefs[KEY_APP_ACCENT_COLOR] = hex
        }
    }

    // ===== 调试开关 =====

    /**
     * 获取 WebView 调试开关（Chrome DevTools 远程调试）
     * 返回全局 StateFlow，所有 ViewModel 共享同一实例
     */
    fun getWebViewDebug(): StateFlow<Boolean> = webViewDebugFlow

    /**
     * 保存 WebView 调试开关
     * 同步更新 StateFlow（立即生效），异步写入 DataStore（持久化）
     */
    suspend fun saveWebViewDebug(enabled: Boolean) {
        _webViewDebugFlow.value = enabled
        dataStore.edit { prefs ->
            prefs[KEY_WEB_VIEW_DEBUG] = enabled
        }
    }

    /**
     * 获取详细日志开关
     * 返回全局 StateFlow，所有 ViewModel 共享同一实例
     */
    fun getVerboseLog(): StateFlow<Boolean> = verboseLogFlow

    /**
     * 保存详细日志开关
     * 同步更新 StateFlow（立即生效），异步写入 DataStore（持久化）
     */
    suspend fun saveVerboseLog(enabled: Boolean) {
        _verboseLogFlow.value = enabled
        AppLogger.setVerboseLogEnabled(enabled)
        dataStore.edit { prefs ->
            prefs[KEY_VERBOSE_LOG] = enabled
        }
    }

    // ===== 隐藏调试入口 =====

    /**
     * 获取调试入口解锁状态。
     * debug 版本始终为 true；release 版本需连续点击版本号 5 次解锁。
     */
    fun getDebugUnlock(): StateFlow<Boolean> = debugUnlockFlow

    /**
     * 切换调试入口解锁状态（连续点击版本号 5 次后调用）。
     * debug 版本无需切换（始终解锁），此方法仅对 release 版本生效。
     */
    suspend fun toggleDebugUnlock() {
        if (com.gridea.android.BuildConfig.DEBUG) return
        val newValue = !_debugUnlockFlow.value
        _debugUnlockFlow.value = newValue
        dataStore.edit { prefs ->
            prefs[KEY_DEBUG_UNLOCK] = newValue
        }
    }

    // ===== 编辑器设置 =====

    /**
     * 获取字数目标（默认 1000）
     */
    fun getWordCountGoal(): Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_WORD_COUNT_GOAL] ?: 1000
    }

    /**
     * 保存字数目标
     */
    suspend fun saveWordCountGoal(goal: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_WORD_COUNT_GOAL] = goal
        }
    }

    // ===== 通用设置偏好（备份 / 恢复）=====

    /**
     * 获取通用设置偏好快照（用于数据备份）
     * 主题模式 / 语言模式 / 字体缩放 / 动态取色 / 强调色 从全局 StateFlow 读取，
     * 字数目标从 DataStore Flow 读取
     */
    suspend fun getGeneralSettings(): GeneralSettings = GeneralSettings(
        themeMode = _themeModeFlow.value,
        languageMode = _languageModeFlow.value,
        fontSizeScale = _fontSizeScaleFlow.value,
        dynamicColor = _dynamicColorFlow.value,
        appAccentColor = _appAccentColorFlow.value,
        wordCountGoal = getWordCountGoal().first()
    )

    /**
     * 恢复通用设置偏好（用于数据恢复）
     * 逐项保存，触发各自的 StateFlow 同步更新与 DataStore 持久化
     */
    suspend fun saveGeneralSettings(settings: GeneralSettings) {
        saveThemeMode(settings.themeMode)
        saveLanguageMode(settings.languageMode)
        saveFontSizeScale(settings.fontSizeScale)
        saveDynamicColor(settings.dynamicColor)
        saveAppAccentColor(settings.appAccentColor)
        saveWordCountGoal(settings.wordCountGoal)
    }

    // ===== DataStore Keys =====

    companion object {
        // 字体缩放范围（0.85 - 1.3）
        const val FONT_SIZE_SCALE_MIN = 0.85f
        const val FONT_SIZE_SCALE_MAX = 1.3f

        // 部署设置
        private val KEY_PLATFORM = stringPreferencesKey("platform")
        // 应用外观
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_LANGUAGE_MODE = stringPreferencesKey("language_mode")
        // 字体大小缩放（Float，0.85-1.3）
        private val KEY_FONT_SIZE_SCALE = floatPreferencesKey("font_size_scale")
        // 动态取色（Material You，Android 12+）
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        // APP 界面强调色（hex 字符串，空串表示用默认淡紫色）
        private val KEY_APP_ACCENT_COLOR = stringPreferencesKey("app_accent_color")
        // 编辑器字数目标
        private val KEY_WORD_COUNT_GOAL = intPreferencesKey("word_count_goal")
        // 调试开关
        private val KEY_WEB_VIEW_DEBUG = booleanPreferencesKey("web_view_debug")
        private val KEY_VERBOSE_LOG = booleanPreferencesKey("verbose_log")
        private val KEY_DEBUG_UNLOCK = booleanPreferencesKey("debug_unlock")
        private val KEY_DOMAIN = stringPreferencesKey("domain")
        private val KEY_REPOSITORY = stringPreferencesKey("repository")
        private val KEY_BRANCH = stringPreferencesKey("branch")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_EMAIL = stringPreferencesKey("email")
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_CNAME = stringPreferencesKey("cname")
        private val KEY_PORT = stringPreferencesKey("port")
        private val KEY_SERVER = stringPreferencesKey("server")
        private val KEY_SFTP_USERNAME = stringPreferencesKey("sftp_username")
        private val KEY_PASSWORD = stringPreferencesKey("password")
        private val KEY_PRIVATE_KEY = stringPreferencesKey("private_key")
        private val KEY_REMOTE_PATH = stringPreferencesKey("remote_path")
        private val KEY_NETLIFY_TOKEN = stringPreferencesKey("netlify_token")
        private val KEY_NETLIFY_SITE_ID = stringPreferencesKey("netlify_site_id")
        private val KEY_VERCEL_TOKEN = stringPreferencesKey("vercel_token")
        private val KEY_VERCEL_PROJECT_ID = stringPreferencesKey("vercel_project_id")
        // Gitee 独立配置键
        private val KEY_GITEE_REPOSITORY = stringPreferencesKey("gitee_repository")
        private val KEY_GITEE_BRANCH = stringPreferencesKey("gitee_branch")
        private val KEY_GITEE_USERNAME = stringPreferencesKey("gitee_username")
        private val KEY_GITEE_TOKEN = stringPreferencesKey("gitee_token")

        // 主题/站点配置
        private val KEY_THEME_NAME = stringPreferencesKey("theme_name")
        private val KEY_SITE_NAME = stringPreferencesKey("site_name")
        private val KEY_SITE_DESC = stringPreferencesKey("site_desc")
        private val KEY_FOOTER_INFO = stringPreferencesKey("footer_info")
        private val KEY_SITE_AUTHOR = stringPreferencesKey("site_author")
        private val KEY_SITE_FAVICON = stringPreferencesKey("site_favicon")
        private val KEY_SITE_AVATAR = stringPreferencesKey("site_avatar")
        private val KEY_POST_PAGE_SIZE = intPreferencesKey("post_page_size")
        private val KEY_ARCHIVES_PAGE_SIZE = intPreferencesKey("archives_page_size")
        private val KEY_SHOW_FEATURE_IMAGE = booleanPreferencesKey("show_feature_image")
        private val KEY_POST_URL_FORMAT = stringPreferencesKey("post_url_format")
        private val KEY_TAG_URL_FORMAT = stringPreferencesKey("tag_url_format")
        private val KEY_DATE_FORMAT = stringPreferencesKey("date_format")
        private val KEY_FEED_FULL_TEXT = booleanPreferencesKey("feed_full_text")
        private val KEY_FEED_COUNT = intPreferencesKey("feed_count")
        private val KEY_ARCHIVES_PATH = stringPreferencesKey("archives_path")
        private val KEY_POST_PATH = stringPreferencesKey("post_path")
        private val KEY_TAG_PATH = stringPreferencesKey("tag_path")

        // 主题样式自定义
        private val KEY_PRIMARY_COLOR = stringPreferencesKey("primary_color")
        private val KEY_TEXT_COLOR = stringPreferencesKey("text_color")
        private val KEY_BG_COLOR = stringPreferencesKey("bg_color")
        private val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        private val KEY_CONTENT_WIDTH = intPreferencesKey("content_width")
        private val KEY_BORDER_RADIUS = intPreferencesKey("border_radius")

        // 评论设置
        private val KEY_COMMENT_PLATFORM = stringPreferencesKey("comment_platform")
        private val KEY_SHOW_COMMENT = booleanPreferencesKey("show_comment")
        private val KEY_GITALK_CLIENT_ID = stringPreferencesKey("gitalk_client_id")
        private val KEY_GITALK_CLIENT_SECRET = stringPreferencesKey("gitalk_client_secret")
        private val KEY_GITALK_REPO = stringPreferencesKey("gitalk_repo")
        private val KEY_GITALK_OWNER = stringPreferencesKey("gitalk_owner")
        private val KEY_GISCUS_REPO = stringPreferencesKey("giscus_repo")
        private val KEY_GISCUS_REPO_ID = stringPreferencesKey("giscus_repo_id")
        private val KEY_GISCUS_CATEGORY = stringPreferencesKey("giscus_category")
        private val KEY_GISCUS_CATEGORY_ID = stringPreferencesKey("giscus_category_id")
        private val KEY_GISCUS_MAPPING = stringPreferencesKey("giscus_mapping")
        private val KEY_GISCUS_THEME = stringPreferencesKey("giscus_theme")
        private val KEY_DISQUS_API = stringPreferencesKey("disqus_api")
        private val KEY_DISQUS_APIKEY = stringPreferencesKey("disqus_apikey")
        private val KEY_DISQUS_SHORTNAME = stringPreferencesKey("disqus_shortname")
        private val KEY_VALINE_APP_ID = stringPreferencesKey("valine_app_id")
        private val KEY_VALINE_APP_KEY = stringPreferencesKey("valine_app_key")
        private val KEY_TWIKOO_ENV_ID = stringPreferencesKey("twikoo_env_id")
        private val KEY_WALINE_SERVER_URL = stringPreferencesKey("waline_server_url")

        // GitHub OAuth Device Flow
        private val KEY_OAUTH_CLIENT_ID = stringPreferencesKey("oauth_client_id")
        private val KEY_GH_ACCESS_TOKEN = stringPreferencesKey("gh_access_token")
        private val KEY_GH_LOGIN = stringPreferencesKey("gh_login")
        private val KEY_GH_NAME = stringPreferencesKey("gh_name")
        private val KEY_GH_AVATAR = stringPreferencesKey("gh_avatar")
        private val KEY_GH_HTML_URL = stringPreferencesKey("gh_html_url")
        // 账户扩展字段（修复：此前 fetchUserInfo 能拿到但持久化时被丢弃，导致仓库数/粉丝等显示为 0）
        private val KEY_GH_BIO = stringPreferencesKey("gh_bio")
        private val KEY_GH_COMPANY = stringPreferencesKey("gh_company")
        private val KEY_GH_BLOG = stringPreferencesKey("gh_blog")
        private val KEY_GH_LOCATION = stringPreferencesKey("gh_location")
        private val KEY_GH_EMAIL = stringPreferencesKey("gh_email")
        private val KEY_GH_PUBLIC_REPOS = intPreferencesKey("gh_public_repos")
        private val KEY_GH_FOLLOWERS = intPreferencesKey("gh_followers")
        private val KEY_GH_FOLLOWING = intPreferencesKey("gh_following")
        private val KEY_GH_CREATED_AT = stringPreferencesKey("gh_created_at")
    }
}
