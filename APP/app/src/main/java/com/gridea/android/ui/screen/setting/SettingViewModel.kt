package com.gridea.android.ui.screen.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridea.android.data.model.Account
import com.gridea.android.data.model.CommentSetting
import com.gridea.android.data.model.DeployRecord
import com.gridea.android.data.model.Setting
import com.gridea.android.data.model.Theme
import com.gridea.android.data.repository.AuthRepository
import com.gridea.android.data.repository.BackupFileInfo
import com.gridea.android.data.repository.DataBackupRepository
import com.gridea.android.data.repository.DeviceCodeResponse
import com.gridea.android.data.repository.SettingRepository
import com.gridea.android.data.repository.SiteOutputRepository
import com.gridea.android.deploy.DeployManager
import com.gridea.android.deploy.DeployProgress
import com.gridea.android.deploy.DeployResult
import com.gridea.android.deploy.DeployService
import com.gridea.android.deploy.DetectResult
import com.gridea.android.renderer.SiteRenderer
import com.gridea.android.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 设置 ViewModel
 *
 * 对应旧版 Gridea 0.9.3 的 src/views/setting/Index.vue 数据逻辑
 * 含站点渲染、部署检测、部署发布、账户登录
 */
@HiltViewModel
class SettingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingRepository: SettingRepository,
    private val siteRenderer: SiteRenderer,
    private val deployManager: DeployManager,
    private val deployService: DeployService,
    private val dataBackupRepository: DataBackupRepository,
    private val siteOutputRepository: SiteOutputRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    // 站点信息（Theme 配置）— 本地 MutableStateFlow 同步更新，避免输入光标跳动
    private val _theme = MutableStateFlow(Theme())
    val theme: StateFlow<Theme> = _theme.asStateFlow()

    // 部署配置
    private val _setting = MutableStateFlow(Setting())
    val setting: StateFlow<Setting> = _setting.asStateFlow()

    // 评论配置
    private val _commentSetting = MutableStateFlow(CommentSetting())
    val commentSetting: StateFlow<CommentSetting> = _commentSetting.asStateFlow()

    // 配置加载完成标志：数据从 DataStore 首次加载完成后置 true
    // 用于在 TextField 渲染前等待数据就绪，避免 label 先空后有触发上移动画
    private val _isSettingLoaded = MutableStateFlow(false)
    val isSettingLoaded: StateFlow<Boolean> = _isSettingLoaded.asStateFlow()

    private val _isThemeLoaded = MutableStateFlow(false)
    val isThemeLoaded: StateFlow<Boolean> = _isThemeLoaded.asStateFlow()

    private val _isCommentLoaded = MutableStateFlow(false)
    val isCommentLoaded: StateFlow<Boolean> = _isCommentLoaded.asStateFlow()

    // OAuth App Client ID — 本地同步更新，避免输入光标跳动（必须在 init 块前声明）
    private val _oauthClientId = MutableStateFlow("")
    val oauthClientId: StateFlow<String> = _oauthClientId.asStateFlow()

    // 字数目标 — 本地同步更新，避免输入光标跳动
    private val _wordCountGoal = MutableStateFlow(1000)
    val wordCountGoal: StateFlow<Int> = _wordCountGoal.asStateFlow()

    init {
        // 从 DataStore 加载初始值
        // collect 操作添加 try-catch，避免 ViewModel 销毁或读取异常时崩溃
        viewModelScope.launch {
            try {
                settingRepository.getTheme().collect {
                    _theme.value = it
                    _isThemeLoaded.value = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 忽略 collect 过程中的异常，避免崩溃
            }
        }
        viewModelScope.launch {
            try {
                settingRepository.getSetting().collect {
                    _setting.value = it
                    _isSettingLoaded.value = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 忽略 collect 过程中的异常，避免崩溃
            }
        }
        viewModelScope.launch {
            try {
                settingRepository.getCommentSetting().collect {
                    _commentSetting.value = it
                    _isCommentLoaded.value = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 忽略 collect 过程中的异常，避免崩溃
            }
        }
        viewModelScope.launch {
            try {
                settingRepository.getOAuthClientId().collect { _oauthClientId.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 忽略 collect 过程中的异常，避免崩溃
            }
        }
        viewModelScope.launch {
            try {
                settingRepository.getWordCountGoal().collect { _wordCountGoal.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 忽略 collect 过程中的异常，避免崩溃
            }
        }
        // themeMode / languageMode 直接使用 repository 的全局 StateFlow，无需本地 collect
    }

    // 保存状态提示
    private val _savedMessage = MutableStateFlow<String?>(null)
    val savedMessage: StateFlow<String?> = _savedMessage.asStateFlow()

    // 保存提示防抖 Job：用户停止输入 400ms 后显示"已保存"，避免连续输入时频繁弹提示
    // 原 1s 太慢（用户反馈要等 2-3 秒），缩短至 400ms 让反馈更及时
    private var savedMessageJob: kotlinx.coroutines.Job? = null

    /**
     * 防抖显示"已保存"提示：每次调用取消上一次的延时，重新计时 400ms
     * 这样连续输入（onValueChange 高频触发）只在停顿后提示一次
     */
    private fun showSavedMessageDebounced() {
        savedMessageJob?.cancel()
        savedMessageJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            _savedMessage.value = "已保存"
        }
    }

    // 渲染状态
    private val _isRendering = MutableStateFlow(false)
    val isRendering: StateFlow<Boolean> = _isRendering.asStateFlow()

    // 渲染结果
    private val _renderResult = MutableStateFlow<String?>(null)
    val renderResult: StateFlow<String?> = _renderResult.asStateFlow()

    // ===== 站点信息 =====

    fun updateSiteName(value: String) { updateTheme { it.copy(siteName = value) } }
    fun updateSiteDescription(value: String) { updateTheme { it.copy(siteDescription = value) } }
    fun updateFooterInfo(value: String) { updateTheme { it.copy(footerInfo = value) } }
    fun updateSiteAuthor(value: String) { updateTheme { it.copy(siteAuthor = value) } }
    fun updateSiteFavicon(value: String) { updateTheme { it.copy(siteFavicon = value) } }
    fun updateSiteAvatar(value: String) { updateTheme { it.copy(siteAvatar = value) } }
    fun updatePostPageSize(value: Int) { updateTheme { it.copy(postPageSize = value) } }
    fun updateShowFeatureImage(value: Boolean) { updateTheme { it.copy(showFeatureImage = value) } }
    fun updateDateFormat(value: String) { updateTheme { it.copy(dateFormat = value) } }
    fun updatePostUrlFormat(value: String) { updateTheme { it.copy(postUrlFormat = value) } }
    fun updateTagUrlFormat(value: String) { updateTheme { it.copy(tagUrlFormat = value) } }
    fun updateFeedCount(value: Int) { updateTheme { it.copy(feedCount = value) } }
    fun updateFeedFullText(value: Boolean) { updateTheme { it.copy(feedFullText = value) } }
    fun updateArchivesPath(value: String) { updateTheme { it.copy(archivesPath = value) } }
    fun updatePostPath(value: String) { updateTheme { it.copy(postPath = value) } }
    fun updateTagPath(value: String) { updateTheme { it.copy(tagPath = value) } }
    fun updatePrimaryColor(value: String) { updateTheme { it.copy(primaryColor = value) } }
    fun updateTextColor(value: String) { updateTheme { it.copy(textColor = value) } }
    fun updateBackgroundColor(value: String) { updateTheme { it.copy(backgroundColor = value) } }
    fun updateFontFamily(value: String) { updateTheme { it.copy(fontFamily = value) } }
    fun updateContentWidth(value: Int) { updateTheme { it.copy(contentWidth = value) } }
    fun updateBorderRadius(value: Int) { updateTheme { it.copy(borderRadius = value) } }

    // ===== 部署配置 =====

    fun updatePlatform(value: String) { updateSetting { it.copy(platform = value) } }
    fun updateDomain(value: String) { updateSetting { it.copy(domain = value) } }
    fun updateRepository(value: String) { updateSetting { it.copy(repository = value) } }
    fun updateBranch(value: String) { updateSetting { it.copy(branch = value) } }
    fun updateUsername(value: String) { updateSetting { it.copy(username = value) } }
    fun updateSftpUsername(value: String) { updateSetting { it.copy(sftpUsername = value) } }
    fun updateEmail(value: String) { updateSetting { it.copy(email = value) } }
    fun updateToken(value: String) { updateSetting { it.copy(token = value) } }
    fun updateCname(value: String) { updateSetting { it.copy(cname = value) } }
    fun updatePort(value: String) { updateSetting { it.copy(port = value) } }
    fun updateServer(value: String) { updateSetting { it.copy(server = value) } }
    fun updatePassword(value: String) { updateSetting { it.copy(password = value) } }
    fun updateRemotePath(value: String) { updateSetting { it.copy(remotePath = value) } }
    fun updateNetlifySiteId(value: String) { updateSetting { it.copy(netlifySiteId = value) } }
    fun updateNetlifyToken(value: String) { updateSetting { it.copy(netlifyAccessToken = value) } }
    fun updateVercelAccessToken(value: String) { updateSetting { it.copy(vercelAccessToken = value) } }
    fun updateVercelProjectId(value: String) { updateSetting { it.copy(vercelProjectId = value) } }
    // Gitee 独立配置
    fun updateGiteeRepository(value: String) { updateSetting { it.copy(giteeRepository = value) } }
    fun updateGiteeBranch(value: String) { updateSetting { it.copy(giteeBranch = value) } }
    fun updateGiteeUsername(value: String) { updateSetting { it.copy(giteeUsername = value) } }
    fun updateGiteeToken(value: String) { updateSetting { it.copy(giteeToken = value) } }

    /**
     * 从已登录的 GitHub OAuth 账户获取 token，自动填入部署配置
     */
    fun useOAuthTokenForDeploy() {
        viewModelScope.launch {
            try {
                val acc = settingRepository.getAccount().first()
                if (acc.accessToken.isNotEmpty()) {
                    updateSetting {
                        it.copy(
                            token = acc.accessToken,
                            username = if (it.username.isEmpty()) acc.login else it.username
                        )
                    }
                    _operationMessage.value = "已复用 OAuth Token"
                } else {
                    _operationMessage.value = "未找到已登录的账户"
                }
            } catch (e: Exception) {
                _operationMessage.value = "复用失败：${e.message ?: "未知错误"}"
            }
        }
    }

    // ===== 评论配置 =====

    fun updateCommentPlatform(value: String) {
        updateCommentSetting { it.copy(commentPlatform = value) }
    }

    fun updateShowComment(value: Boolean) {
        updateCommentSetting { it.copy(showComment = value) }
    }

    fun updateGitalkClientId(value: String) {
        updateCommentSetting { it.copy(gitalkSetting = it.gitalkSetting.copy(clientId = value)) }
    }

    fun updateGitalkClientSecret(value: String) {
        updateCommentSetting { it.copy(gitalkSetting = it.gitalkSetting.copy(clientSecret = value)) }
    }

    fun updateGitalkRepo(value: String) {
        updateCommentSetting { it.copy(gitalkSetting = it.gitalkSetting.copy(repository = value)) }
    }

    fun updateGitalkOwner(value: String) {
        updateCommentSetting { it.copy(gitalkSetting = it.gitalkSetting.copy(owner = value)) }
    }

    fun updateDisqusShortname(value: String) {
        updateCommentSetting { it.copy(disqusSetting = it.disqusSetting.copy(shortname = value)) }
    }

    fun updateDisqusApikey(value: String) {
        updateCommentSetting { it.copy(disqusSetting = it.disqusSetting.copy(apikey = value)) }
    }

    fun updateDisqusApi(value: String) {
        updateCommentSetting { it.copy(disqusSetting = it.disqusSetting.copy(api = value)) }
    }

    fun updateGiscusRepo(value: String) {
        updateCommentSetting { it.copy(giscusSetting = it.giscusSetting.copy(repo = value)) }
    }

    fun updateGiscusRepoId(value: String) {
        updateCommentSetting { it.copy(giscusSetting = it.giscusSetting.copy(repoId = value)) }
    }

    fun updateGiscusCategory(value: String) {
        updateCommentSetting { it.copy(giscusSetting = it.giscusSetting.copy(category = value)) }
    }

    fun updateGiscusCategoryId(value: String) {
        updateCommentSetting { it.copy(giscusSetting = it.giscusSetting.copy(categoryId = value)) }
    }

    fun updateGiscusMapping(value: String) {
        updateCommentSetting { it.copy(giscusSetting = it.giscusSetting.copy(mapping = value)) }
    }

    fun updateGiscusTheme(value: String) {
        updateCommentSetting { it.copy(giscusSetting = it.giscusSetting.copy(theme = value)) }
    }

    fun updateValineAppId(value: String) {
        updateCommentSetting { it.copy(valineSetting = it.valineSetting.copy(appId = value)) }
    }

    fun updateValineAppKey(value: String) {
        updateCommentSetting { it.copy(valineSetting = it.valineSetting.copy(appKey = value)) }
    }

    fun updateTwikooEnvId(value: String) {
        updateCommentSetting { it.copy(twikooSetting = it.twikooSetting.copy(envId = value)) }
    }

    fun updateWalineServerURL(value: String) {
        updateCommentSetting { it.copy(walineSetting = it.walineSetting.copy(serverURL = value)) }
    }

    // ===== 内部更新方法 =====

    private fun updateTheme(transform: (Theme) -> Theme) {
        // 同步更新本地状态，避免输入光标跳动
        _theme.value = transform(_theme.value)
        // 异步持久化到 DataStore
        viewModelScope.launch {
            try {
                settingRepository.saveTheme(_theme.value)
                showSavedMessageDebounced()
            } catch (e: Exception) {
                // 持久化失败不影响 UI 操作
            }
        }
    }

    private fun updateSetting(transform: (Setting) -> Setting) {
        _setting.value = transform(_setting.value)
        viewModelScope.launch {
            try {
                settingRepository.saveSetting(_setting.value)
                showSavedMessageDebounced()
            } catch (e: Exception) {
                // 持久化失败不影响 UI 操作
            }
        }
    }

    private fun updateCommentSetting(transform: (CommentSetting) -> CommentSetting) {
        _commentSetting.value = transform(_commentSetting.value)
        viewModelScope.launch {
            try {
                settingRepository.saveCommentSetting(_commentSetting.value)
                showSavedMessageDebounced()
            } catch (e: Exception) {
                // 持久化失败不影响 UI 操作
            }
        }
    }

    private fun showSavedMessage() {
        _savedMessage.value = "已保存"
    }

    fun clearMessage() {
        _savedMessage.value = null
    }

    // ===== 站点渲染 =====

    /**
     * 生成静态站点（含自定义输出目录支持）
     * 对应旧版 renderer.renderAll()
     * 实现在文件末尾（含自定义输出目录复制逻辑）
     */

    fun clearRenderResult() {
        _renderResult.value = null
    }

    // ===== 部署功能 =====

    /** 部署检测中 */
    private val _isDetecting = MutableStateFlow(false)
    val isDetecting: StateFlow<Boolean> = _isDetecting.asStateFlow()

    /** 部署中（代理 DeployService，后台运行切页不中断） */
    val isDeploying: StateFlow<Boolean> = deployService.isDeploying

    /** 部署进度（代理 DeployService，实时更新） */
    val deployProgress: StateFlow<DeployProgress?> = deployService.deployProgress

    /** 部署结果（代理 DeployService，完成后写入） */
    val deployResult: StateFlow<DeployResult?> = deployService.deployResult

    /** 检测结果 */
    private val _detectResult = MutableStateFlow<DetectResult?>(null)
    val detectResult: StateFlow<DetectResult?> = _detectResult.asStateFlow()

    /** 部署历史记录（按时间倒序，最新在前） */
    val deployHistory: StateFlow<List<DeployRecord>> = deployManager.getDeployHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 上次成功部署记录（用于回滚入口显示），无成功记录时为 null */
    val lastSuccessRecord: StateFlow<DeployRecord?> = deployManager.getDeployHistory()
        .map { list -> list.firstOrNull { it.success } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 回滚中状态 */
    private val _isRollingBack = MutableStateFlow(false)
    val isRollingBack: StateFlow<Boolean> = _isRollingBack.asStateFlow()

    /** 回滚结果消息（一次性，UI 显示后清空） */
    private val _rollbackMessage = MutableStateFlow<String?>(null)
    val rollbackMessage: StateFlow<String?> = _rollbackMessage.asStateFlow()

    /**
     * 连通性检测
     * 对应旧版 remote-detect 事件
     */
    fun detectDeploy() {
        if (_isDetecting.value) return
        _isDetecting.value = true
        _detectResult.value = null
        _hasNotifiedDetectResult = false

        viewModelScope.launch {
            try {
                _detectResult.value = deployManager.detect(setting.value)
            } catch (e: Exception) {
                _detectResult.value = DetectResult(
                    success = false,
                    message = "检测失败：${e.message ?: "未知错误"}"
                )
            } finally {
                _isDetecting.value = false
            }
        }
    }

    // 连通结果是否已通知过：切换页面再返回时不重复弹出通知
    private var _hasNotifiedDetectResult = false
    fun shouldNotifyDetectResult(): Boolean {
        val current = _detectResult.value
        return current != null && !_hasNotifiedDetectResult
    }
    fun markDetectResultNotified() {
        _hasNotifiedDetectResult = true
    }

    /**
     * 发布站点
     * 对应旧版 site-publish 事件
     *
     * 委托给 [DeployService] 在 Application 级协程中运行，切页不中断。
     * 进度通过 [deployProgress] StateFlow 实时暴露，由 GrideaApp 层观察并更新灵动岛通知。
     * 结果通过 [deployResult] StateFlow 暴露，UI 显示后调 [clearDeployResult] 清空。
     */
    fun publishSite() {
        deployService.publish()
    }

    /**
     * 一键部署
     *
     * 合并原"生成静态站点 → 检测连接 → 发布站点"三步为一个流程。
     * 灵动岛通知顺序：
     * 1. 生成结果（引用原生成站点通知内容，通过 renderResult 暴露）
     * 2. 检测结果（通过 detectResult 暴露）
     * 3. 部署进度与结果（通过 deployProgress / deployResult 暴露）
     *
     * 通过 DeployService 在 Application 级协程中运行，切页不中断。
     */
    fun oneClickDeploy() {
        deployService.oneClickDeploy(
            onRenderComplete = { msg -> _renderResult.value = msg },
            onDetectComplete = { res ->
                _detectResult.value = res
                _hasNotifiedDetectResult = false
            }
        )
    }

    fun clearDeployResult() {
        deployService.clearDeployResult()
    }

    fun clearDetectResult() {
        _detectResult.value = null
    }

    /**
     * 清空部署历史记录
     */
    fun clearDeployHistory() {
        viewModelScope.launch {
            try {
                deployManager.clearDeployHistory()
                _operationMessage.value = "已清空部署历史"
            } catch (e: Exception) {
                _operationMessage.value = "清空历史失败：${e.message ?: "未知错误"}"
            }
        }
    }

    /**
     * 按 ID 批量删除部署历史记录
     *
     * @param ids 要删除的记录 ID 集合
     */
    fun deleteDeployRecords(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                deployManager.deleteDeployRecords(ids)
                _operationMessage.value = "已删除 ${ids.size} 条部署历史"
            } catch (e: Exception) {
                _operationMessage.value = "删除失败：${e.message ?: "未知错误"}"
            }
        }
    }

    /**
     * 回滚上次部署（简化实现）。
     *
     * 当前实现不会真正删除远程文件，而是返回上次成功部署的文件清单和提示信息，
     * 由用户在对应平台管理页面手动处理。结果消息通过 [rollbackMessage] 暴露。
     */
    fun rollbackLastDeploy() {
        if (_isRollingBack.value) return
        _isRollingBack.value = true
        _rollbackMessage.value = null
        viewModelScope.launch {
            try {
                val result = deployManager.rollbackLastDeploy(setting.value) { /* 暂无进度 */ }
                _rollbackMessage.value = result.message
            } catch (e: Exception) {
                _rollbackMessage.value = "回滚失败：${e.message ?: "未知错误"}"
            } finally {
                _isRollingBack.value = false
            }
        }
    }

    fun clearRollbackMessage() {
        _rollbackMessage.value = null
    }

    // ===== 数据备份 =====

    /** 备份中状态 */
    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()

    /** 备份结果消息 */
    private val _backupMessage = MutableStateFlow<BackupResult?>(null)
    val backupMessage: StateFlow<BackupResult?> = _backupMessage.asStateFlow()

    /**
     * 导出到指定 Uri
     */
    fun exportData(uri: android.net.Uri) {
        if (_isBackingUp.value) return
        _isBackingUp.value = true
        _backupMessage.value = null

        viewModelScope.launch {
            try {
                val count = dataBackupRepository.exportToUri(uri)
                AppLogger.action("Backup", "Export", "$count 篇文章")
                _backupMessage.value = BackupResult.ExportSuccess(count)
            } catch (e: Exception) {
                _backupMessage.value = BackupResult.Fail(e.message ?: "")
            } finally {
                _isBackingUp.value = false
            }
        }
    }

    /**
     * 从指定 Uri 导入
     */
    fun importData(uri: android.net.Uri) {
        if (_isBackingUp.value) return
        _isBackingUp.value = true
        _backupMessage.value = null

        viewModelScope.launch {
            try {
                val count = dataBackupRepository.importFromUri(uri)
                AppLogger.action("Backup", "Import", "$count 篇文章")
                _backupMessage.value = BackupResult.ImportSuccess(count)
            } catch (e: Exception) {
                _backupMessage.value = BackupResult.Fail(e.message ?: "")
            } finally {
                _isBackingUp.value = false
            }
        }
    }

    /** 扫描到的备份文件列表 */
    private val _scannedBackups = MutableStateFlow<List<BackupFileInfo>>(emptyList())
    val scannedBackups: StateFlow<List<BackupFileInfo>> = _scannedBackups.asStateFlow()

    /** 是否正在扫描备份 */
    private val _isScanningBackups = MutableStateFlow(false)
    val isScanningBackups: StateFlow<Boolean> = _isScanningBackups.asStateFlow()

    /**
     * 扫描 backup 目录下已有的 Gridea 备份文件
     */
    fun scanBackups() {
        viewModelScope.launch {
            _isScanningBackups.value = true
            try {
                _scannedBackups.value = dataBackupRepository.scanBackups()
            } catch (e: Exception) {
                _scannedBackups.value = emptyList()
            } finally {
                _isScanningBackups.value = false
            }
        }
    }

    /** 备份目录可读路径 */
    val backupDisplayPath: String
        get() = dataBackupRepository.getBackupDisplayPath()

    /**
     * 从扫描到的备份文件导入（通过绝对路径转换为 file:// Uri 后复用 importData）
     */
    fun importFromBackupFile(absolutePath: String) {
        val uri = android.net.Uri.fromFile(java.io.File(absolutePath))
        importData(uri)
    }

    /**
     * 批量删除选中的备份文件，删除后自动刷新扫描列表
     */
    fun deleteBackups(absolutePaths: Set<String>) {
        if (absolutePaths.isEmpty()) return
        viewModelScope.launch {
            try {
                val deleted = dataBackupRepository.deleteBackups(absolutePaths)
                _scannedBackups.value = dataBackupRepository.scanBackups()
                _operationMessage.value = if (deleted > 0) "已删除 $deleted 个备份文件" else "没有文件被删除"
            } catch (e: Exception) {
                _operationMessage.value = "删除备份失败：${e.message ?: "未知错误"}"
            }
        }
    }

    /**
     * 一键清空所有备份文件，清空后自动刷新扫描列表
     */
    fun clearAllBackups() {
        viewModelScope.launch {
            try {
                val deleted = dataBackupRepository.clearAllBackups()
                _scannedBackups.value = dataBackupRepository.scanBackups()
                _operationMessage.value = if (deleted > 0) "已清空 $deleted 个备份文件" else "备份目录为空"
            } catch (e: Exception) {
                _operationMessage.value = "清空备份失败：${e.message ?: "未知错误"}"
            }
        }
    }

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    // ===== 公共输出目录（Documents/Gridea）=====

    /** 是否已获得所有文件访问权限 */
    val hasStoragePermission: StateFlow<Boolean> = siteOutputRepository.hasPermission

    /** 输出目录可读路径 */
    val outputDisplayPath: String
        get() = siteOutputRepository.getOutputDisplayPath()

    /** 日志目录（Documents/Gridea/log），供反馈日志收集使用 */
    val logDir: java.io.File
        get() = siteOutputRepository.logDir

    /** 日志目录可读路径 */
    val logDisplayPath: String
        get() = siteOutputRepository.getLogDisplayPath()

    /**
     * 收集反馈日志：打包 log 目录文件 + 设备信息 + DataStore 配置快照（脱敏）为 zip
     * 写入 Documents/Gridea/log/，返回 zip 文件路径
     */
    suspend fun collectFeedbackLogs(context: Context): Result<String> {
        return com.gridea.android.util.FeedbackCollector.collect(
            context = context,
            settingRepository = settingRepository,
            logDir = siteOutputRepository.logDir
        )
    }

    /**
     * 清空 log 目录下的所有日志文件
     * 删除 Documents/Gridea/log/ 下的全部文件，返回删除的文件数量
     */
    suspend fun clearFeedbackLogs(): Result<Int> {
        return try {
            val logDir = siteOutputRepository.logDir
            if (!logDir.exists()) {
                Result.success(0)
            } else {
                val files = logDir.listFiles()?.toList() ?: emptyList()
                var count = 0
                files.forEach { f ->
                    if (f.isFile && f.delete()) count++
                }
                Result.success(count)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 已扫描到的输出文件数量（null 表示未扫描） */
    val existingFileCount: StateFlow<Int?> = siteOutputRepository.existingFileCount

    /** 已扫描到的输出目录大小（字节，null 表示未扫描） */
    val existingTotalSize: StateFlow<Long?> = siteOutputRepository.existingTotalSize

    /**
     * 用户从系统权限设置返回后，刷新权限状态并扫描已有文件
     */
    fun onPermissionResult() {
        siteOutputRepository.refreshPermission()
        if (siteOutputRepository.hasPermission.value) {
            scanOutputFiles()
        }
    }

    /**
     * 扫描公共输出目录中的已有文件
     */
    fun scanOutputFiles() {
        viewModelScope.launch {
            try {
                siteOutputRepository.scanExistingFiles()
            } catch (e: Exception) {
                // 扫描失败不影响应用使用
            }
        }
    }

    /**
     * 清空公共输出目录
     */
    fun clearOutputFiles() {
        viewModelScope.launch {
            try {
                val cleared = siteOutputRepository.clearPublicOutput()
                _operationMessage.value = if (cleared > 0) "已清空输出目录（$cleared 个文件）" else "输出目录为空"
            } catch (e: Exception) {
                _operationMessage.value = "清空失败：${e.message ?: "未知错误"}"
            }
        }
    }

    /** 一次性操作消息（如清空输出目录、复用 OAuth Token），用于桥接到全局灵动岛通知 */
    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    fun clearOperationMessage() { _operationMessage.value = null }

    /**
     * 渲染站点
     * 渲染到 cacheDir 后，若已授权则复制到公共 Documents/Gridea 目录
     */
    fun renderSite() {
        if (_isRendering.value) return
        // 必须取得存储权限才能渲染输出，不允许在未取得储存权限就渲染静态网站文件输出到软件内置目录
        if (!siteOutputRepository.hasPermission.value) {
            _operationMessage.value = "请先授权存储权限后再生成站点"
            return
        }
        AppLogger.i("Renderer", "开始渲染站点")
        _isRendering.value = true
        _renderResult.value = null

        viewModelScope.launch {
            try {
                val result = siteRenderer.renderAll(forceRebuild = true)
                AppLogger.i("Renderer", "渲染完成：${result.postCount} 篇文章，${result.tagCount} 个标签")
                val sourceDir = java.io.File(result.outputDir)
                // 已获得存储权限，复制到公共 Documents/Gridea 目录
                val fileCount = siteOutputRepository.copyToPublicOutput(sourceDir)
                _renderResult.value = "生成成功！${result.postCount} 篇文章，${result.tagCount} 个标签\n" +
                    "已输出到公共目录（$fileCount 个文件）\n${siteOutputRepository.getOutputDisplayPath()}"
            } catch (e: Exception) {
                AppLogger.e("Renderer", "渲染失败", e)
                _renderResult.value = "生成失败：${e.message ?: "未知错误"}"
            } finally {
                _isRendering.value = false
            }
        }
    }

    /** 预览渲染是否在进行中 */
    private val _isPreviewRendering = MutableStateFlow(false)
    val isPreviewRendering: StateFlow<Boolean> = _isPreviewRendering.asStateFlow()

    /**
     * 预览渲染：以 isPreview=true 渲染到 cacheDir/gridea_build，不复制到公共目录。
     *
     * 与 [renderSite] 的区别：
     * - isPreview=true → 文章详情页不注入评论系统 CDN（Gitalk/Valine 等），避免 file:// 下跨域报错
     * - 不需要存储权限（输出到 cacheDir）
     * - 不复制到公共 Documents 目录
     */
    fun renderForPreview() {
        if (_isPreviewRendering.value) return
        _isPreviewRendering.value = true
        viewModelScope.launch {
            try {
                AppLogger.i("Renderer", "开始预览渲染")
                siteRenderer.renderAll(isPreview = true, forceRebuild = true)
                AppLogger.i("Renderer", "预览渲染完成")
            } catch (e: Exception) {
                AppLogger.e("Renderer", "预览渲染失败", e)
            } finally {
                _isPreviewRendering.value = false
            }
        }
    }

    // ===== GitHub 账户（Device Flow）=====

    /** 已登录账户（未登录时 accessToken 为空） */
    val account: StateFlow<Account> = settingRepository.getAccount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Account())

    /** 登录中状态 */
    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    /** 当前设备码（登录中显示给用户） */
    private val _deviceCode = MutableStateFlow<DeviceCodeResponse?>(null)
    val deviceCode: StateFlow<DeviceCodeResponse?> = _deviceCode.asStateFlow()

    /** 登录/登出消息 */
    private val _authMessage = MutableStateFlow<String?>(null)
    val authMessage: StateFlow<String?> = _authMessage.asStateFlow()

    fun updateOAuthClientId(value: String) {
        _oauthClientId.value = value
        viewModelScope.launch {
            try {
                settingRepository.saveOAuthClientId(value)
                showSavedMessageDebounced()
            } catch (e: Exception) {
                // 持久化失败不影响 UI 操作
            }
        }
    }

    // ===== 编辑器设置 =====

    /**
     * 更新字数目标
     * 同步更新本地状态，异步持久化到 DataStore
     */
    fun updateWordCountGoal(value: Int) {
        _wordCountGoal.value = value
        viewModelScope.launch {
            try {
                settingRepository.saveWordCountGoal(value)
                showSavedMessageDebounced()
            } catch (e: Exception) {
                // 持久化失败不影响 UI 操作
            }
        }
    }

    /**
     * 开始 Device Flow 登录
     * 会在 deviceCode StateFlow 中更新用户码供 UI 展示，并自动轮询直到成功或超时
     */
    fun startLogin() {
        if (_isLoggingIn.value) return
        val clientId = oauthClientId.value
        if (clientId.isBlank()) {
            _authMessage.value = "请先填写 OAuth App Client ID"
            return
        }

        _isLoggingIn.value = true
        _deviceCode.value = null
        _authMessage.value = null

        viewModelScope.launch {
            try {
                val account = authRepository.loginWithDeviceFlow(clientId) { resp ->
                    _deviceCode.value = resp
                }
                settingRepository.saveAccount(account)
                _authMessage.value = "登录成功：${account.login}"
            } catch (e: Exception) {
                _authMessage.value = "登录失败：${e.message ?: "未知错误"}"
            } finally {
                _isLoggingIn.value = false
                _deviceCode.value = null
            }
        }
    }

    /** 取消登录（仅清除 UI 状态，正在进行的网络轮询无法中断） */
    fun cancelLogin() {
        _isLoggingIn.value = false
        _deviceCode.value = null
        _authMessage.value = "已取消登录"
    }

    /** 登出 */
    fun logout() {
        viewModelScope.launch {
            try {
                settingRepository.clearAccount()
                _authMessage.value = "已登出"
            } catch (e: Exception) {
                _authMessage.value = "登出失败：${e.message ?: "未知错误"}"
            }
        }
    }

    /**
     * 刷新当前登录账户的 GitHub 信息
     *
     * 用于解决：用户首次登录后 GitHub 上的统计信息（如仓库数）发生变化，
     * 但本地已保存的 Account 不会自动更新，导致显示过期数据。
     * 调用 GitHub /user API 拉取最新数据并覆盖本地账户。
     *
     * 通知流程（通过 authMessage 桥接到灵动岛）：
     * 1. "正在获取账户信息..." — 开始请求时展示
     * 2. "账户信息已刷新" / "刷新失败：xxx" — 最终结果
     */
    fun refreshAccount() {
        val current = account.value
        if (!current.isLoggedIn) {
            _authMessage.value = "未登录，无法刷新"
            return
        }
        _authMessage.value = "正在获取账户信息..."
        viewModelScope.launch {
            try {
                val updated = authRepository.fetchUserInfo(current.accessToken)
                settingRepository.saveAccount(updated)
                _authMessage.value = "账户信息已刷新"
            } catch (e: Exception) {
                _authMessage.value = "刷新失败：${e.message ?: "未知错误"}"
            }
        }
    }

    fun clearAuthMessage() {
        _authMessage.value = null
    }

    // ===== 应用外观 =====

    /**
     * 主题模式：system / light / dark
     * 直接引用 repository 的全局 StateFlow，确保所有 ViewModel 实例状态同步
     */
    val themeMode: StateFlow<String> = settingRepository.themeModeFlow

    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            try {
                settingRepository.saveThemeMode(mode)
                showSavedMessageDebounced()
            } catch (e: Exception) {
                // 持久化失败不影响 UI 操作
            }
        }
    }

    /**
     * 语言模式：system / zh / en
     * 直接引用 repository 的全局 StateFlow，确保所有 ViewModel 实例状态同步
     */
    val languageMode: StateFlow<String> = settingRepository.languageModeFlow

    fun updateLanguageMode(mode: String) {
        // 同步更新 StateFlow + commit SharedPreferences，确保重启后能读到最新语言
        settingRepository.applyLanguageModeSync(mode)
        // 异步写入 DataStore 持久化
        viewModelScope.launch {
            try {
                settingRepository.saveLanguageMode(mode)
                // 语言切换会触发重启，不显示"已保存"提示
            } catch (e: Exception) {
                // 持久化失败不影响 UI 操作
            }
        }
    }

    /**
     * 字体大小缩放（范围 0.85-1.3，默认 1.0）
     * 直接引用 repository 的全局 StateFlow，确保所有 ViewModel 实例状态同步
     */
    val fontSizeScale: StateFlow<Float> = settingRepository.fontSizeScaleFlow

    fun updateFontSizeScale(scale: Float) {
        viewModelScope.launch {
            try {
                settingRepository.saveFontSizeScale(scale)
                showSavedMessageDebounced()
            } catch (e: Exception) {
                // 持久化失败不影响 UI 操作
            }
        }
    }

    /**
     * 动态取色（Material You，Android 12+）
     * 直接引用 repository 的全局 StateFlow，确保所有 ViewModel 实例状态同步
     */
    val dynamicColor: StateFlow<Boolean> = settingRepository.dynamicColorFlow

    fun updateDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingRepository.saveDynamicColor(enabled)
                showSavedMessageDebounced()
            } catch (e: Exception) {
                // 持久化失败不影响 UI 操作
            }
        }
    }

    /**
     * APP 界面强调色（hex 字符串，空串表示用默认淡紫色）
     * 仅在动态取色关闭时生效
     */
    val appAccentColor: StateFlow<String> = settingRepository.appAccentColorFlow

    fun updateAppAccentColor(hex: String) {
        viewModelScope.launch {
            try {
                settingRepository.saveAppAccentColor(hex)
                showSavedMessageDebounced()
            } catch (e: Exception) {
                // 持久化失败不影响 UI 操作
            }
        }
    }

    // ===== 调试开关 =====

    /** WebView 调试（Chrome DevTools 远程调试） */
    val webViewDebug: StateFlow<Boolean> = settingRepository.webViewDebugFlow

    fun updateWebViewDebug(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingRepository.saveWebViewDebug(enabled)
                showSavedMessageDebounced()
            } catch (e: Exception) {
                // 持久化失败不影响 UI 操作
            }
        }
    }

    /** 详细日志（输出调试级日志） */
    val verboseLog: StateFlow<Boolean> = settingRepository.verboseLogFlow

    fun updateVerboseLog(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingRepository.saveVerboseLog(enabled)
                showSavedMessageDebounced()
            } catch (e: Exception) {
                // 持久化失败不影响 UI 操作
            }
        }
    }

    // ===== 隐藏调试入口 =====

    /** 调试入口解锁状态：debug 版本始终 true，release 版本需连续点击版本号解锁 */
    val debugUnlock: StateFlow<Boolean> = settingRepository.debugUnlockFlow

    /**
     * 切换调试入口解锁状态（连续点击版本号 5 次后调用）
     */
    fun toggleDebugUnlock() {
        viewModelScope.launch {
            settingRepository.toggleDebugUnlock()
        }
    }
}

/**
 * 备份/导入结果（UI 层根据类型本地化显示）
 */
sealed class BackupResult {
    data class ExportSuccess(val count: Int) : BackupResult()
    data class ImportSuccess(val count: Int) : BackupResult()
    data class Fail(val message: String) : BackupResult()
}
