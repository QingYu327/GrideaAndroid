package com.gridea.android.ui.screen.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridea.android.R
import com.gridea.android.data.model.Post
import com.gridea.android.data.model.PostData
import com.gridea.android.data.model.PostVersion
import com.gridea.android.data.repository.ImageInfo
import com.gridea.android.data.repository.ImageRepository
import com.gridea.android.data.repository.PostRepository
import com.gridea.android.data.repository.PostVersionRepository
import com.gridea.android.data.repository.SettingRepository
import com.gridea.android.data.repository.TagRepository
import com.gridea.android.data.repository.TagWithCount
import com.gridea.android.util.AppLogger
import com.gridea.android.util.SlugUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 编辑器 ViewModel
 *
 * 对应旧版 Gridea 0.9.3 中 ArticleUpdate.vue 的数据逻辑
 * 含图片插入功能
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val postRepository: PostRepository,
    private val imageRepository: ImageRepository,
    private val postVersionRepository: PostVersionRepository,
    private val settingRepository: SettingRepository,
    private val tagRepository: TagRepository
) : ViewModel() {

    // ===== 图片相关 =====

    /** 图片库列表 */
    val images: StateFlow<List<ImageInfo>> =
        imageRepository.images

    /**
     * 所有已有标签（用于编辑器标签输入时的建议下拉）
     */
    val availableTags: StateFlow<List<TagWithCount>> = tagRepository.getAllTags()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 图片上传中 */
    private val _isImageUploading = MutableStateFlow(false)
    val isImageUploading: StateFlow<Boolean> = _isImageUploading.asStateFlow()

    /** 批量导入进度（current/total，未导入时为 null） */
    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    /**
     * 从 Uri 保存图片并回调
     */
    fun saveImageFromUri(uri: Uri, onSaved: (String?) -> Unit) {
        _isImageUploading.value = true
        viewModelScope.launch {
            try {
                val url = imageRepository.saveImageFromUri(uri)
                onSaved(url)
            } catch (e: Exception) {
                onSaved(null)
            } finally {
                _isImageUploading.value = false
            }
        }
    }

    /**
     * 从 Bitmap 保存图片（用于剪贴板粘贴图片）
     */
    fun saveImageFromBitmap(bitmap: Bitmap, onSaved: (String?) -> Unit) {
        _isImageUploading.value = true
        viewModelScope.launch {
            try {
                val url = imageRepository.saveImageFromBitmap(bitmap)
                onSaved(url)
            } catch (e: Exception) {
                onSaved(null)
            } finally {
                _isImageUploading.value = false
            }
        }
    }

    /**
     * 重命名图片并同步更新所有文章中的图片引用
     *
     * @param image 待重命名的图片
     * @param newName 新文件名（可不带扩展名）
     * @param onResult 回调：成功为 true，失败为 false
     */
    fun renameImage(image: ImageInfo, newName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val oldUrl = image.url
                val newUrl = imageRepository.renameImage(image, newName)
                if (newUrl == null) {
                    onResult(false)
                    return@launch
                }
                // 名称未变化时也视为成功
                if (newUrl != oldUrl) {
                    postRepository.updateImageReferences(oldUrl, newUrl)
                    // 若当前正在编辑的文章包含该图片，同步替换内容
                    if (_content.value.contains(oldUrl)) {
                        _content.value = _content.value.replace(oldUrl, newUrl)
                        scheduleAutoSave()
                    }
                }
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    /**
     * 批量导入图片
     *
     * @param uris 多张图片的 content Uri
     * @param onDone 完成回调（成功导入数量）
     */
    fun importImages(uris: List<Uri>, onDone: (Int) -> Unit) {
        if (uris.isEmpty()) {
            onDone(0)
            return
        }
        _importProgress.value = ImportProgress(0, uris.size)
        viewModelScope.launch {
            try {
                val success = imageRepository.importImages(uris) { current, total ->
                    _importProgress.value = ImportProgress(current, total)
                }
                onDone(success)
            } catch (e: Exception) {
                onDone(0)
            } finally {
                _importProgress.value = null
            }
        }
    }

    /**
     * 删除图片
     */
    fun deleteImage(url: String) {
        viewModelScope.launch {
            try {
                imageRepository.deleteImage(url)
            } catch (e: Exception) {
                // 删除失败不影响应用使用
            }
        }
    }

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    /** 实时字数统计（轻量计算，不经过 Markdown 解析） */
    val wordCount: StateFlow<Int> = _content.map { text ->
        text.replace(Regex("[\\s\\n\\r]"), "")
            .replace(Regex("[#*>`~\\[\\]()!\\-_|=+{}]"), "")
            .length
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 字数目标（DataStore 持久化，默认 1000） */
    val wordCountGoal: StateFlow<Int> = settingRepository.getWordCountGoal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1000)

    /** 字数目标达成祝贺消息（一次性消费，UI 显示后调用 clearCongratulation 清除） */
    private val _congratulationMessage = MutableStateFlow<String?>(null)
    val congratulationMessage: StateFlow<String?> = _congratulationMessage.asStateFlow()

    /** 标记是否已宣布达成目标（避免重复提醒） */
    private var goalAnnounced = false

    init {
        // 监听字数与目标，达到目标时发出祝贺消息
        viewModelScope.launch {
            combine(wordCount, wordCountGoal) { count, goal ->
                count to goal
            }.collect { (count, goal) ->
                if (goal > 0 && count >= goal && !goalAnnounced) {
                    goalAnnounced = true
                    _congratulationMessage.value = context.getString(R.string.editor_word_count_goal_reached, goal)
                } else if (count < goal) {
                    // 字数回落到目标以下，重置标记以便下次再次提醒
                    goalAnnounced = false
                }
            }
        }
        // 缓存文章 URL 格式设置，供 generateFileName 使用
        viewModelScope.launch {
            settingRepository.getTheme().collect { theme ->
                cachedPostUrlFormat = theme.postUrlFormat
            }
        }
    }

    /** 清除祝贺消息（UI 显示 Snackbar 后调用） */
    fun clearCongratulation() {
        _congratulationMessage.value = null
    }

    private val _tags = MutableStateFlow("")
    val tags: StateFlow<String> = _tags.asStateFlow()

    private val _date = MutableStateFlow(currentDate())
    val date: StateFlow<String> = _date.asStateFlow()

    /**
     * 文章封面图 URL（对应 front-matter 的 feature 字段）
     * 渲染时输出到 .post-feature 容器与 og:image meta
     */
    private val _feature = MutableStateFlow("")
    val feature: StateFlow<String> = _feature.asStateFlow()

    /**
     * 用户自定义 URL（对应 fileName）。
     *
     * - 新建文章：留空，保存时按标题 slugify 自动生成（短 URL）
     * - 编辑文章：从已保存的 fileName 加载，用户可手动修改
     * - 修改标题时不会自动覆盖已填写的 URL（与旧版 Gridea 的 fileNameChanged 行为一致）
     *
     * 显示给用户的"文章 URL"输入框绑定此字段，留空表示"按标题自动生成"。
     * 保存时调用 [resolveFileName] 决定最终的 fileName。
     */
    private val _customUrl = MutableStateFlow("")
    val customUrl: StateFlow<String> = _customUrl.asStateFlow()

    /**
     * 文章 URL 格式（缓存自设置）：控制自动生成 fileName 的策略。
     * - "SLUG"：用 SlugUtils.slugify 把标题转拼音 slug（如 "Gridea 使用" → "gridea-shi-yong"）
     * - "default"：用旧版格式 时间戳-标题（与旧版 Gridea 0.9.3 一致，URL 可能较长含中文）
     *
     * 用户在"文章 URL"输入框填写了内容时优先使用用户输入，此设置仅影响自动生成。
     */
    private var cachedPostUrlFormat: String = "SLUG"

    // ===== 写作时长计时 =====

    /** 累计写作时长（毫秒） */
    private val _writingTimeMs = MutableStateFlow(0L)
    val writingTimeMs: StateFlow<Long> = _writingTimeMs.asStateFlow()

    /** 编辑会话开始时间戳（首次内容变化时初始化） */
    private var sessionStartTime: Long = 0L

    /** 最近一次活动时间戳（用于判定是否仍在活跃写作） */
    private var lastActivityTime: Long = 0L

    /** 写作活跃判定阈值：超过此时间无活动则视为暂停 */
    private val ACTIVITY_TIMEOUT_MS = 60_000L  // 60 秒

    /** 版本快照保存间隔：两次版本保存至少间隔 2 分钟，避免版本爆炸 */
    private val VERSION_SAVE_INTERVAL_MS = 2 * 60 * 1000L  // 2 分钟

    /** 计时器协程任务 */
    private var writingTimerJob: Job? = null

    /** 当前文章已保存的写作时长（从文章加载，保存时累计） */
    private var loadedWritingTime: Long = 0L

    // 文章附加属性（对应旧版 front-matter 字段）
    private val _published = MutableStateFlow(false)
    val published: StateFlow<Boolean> = _published.asStateFlow()

    private val _hideInList = MutableStateFlow(false)
    val hideInList: StateFlow<Boolean> = _hideInList.asStateFlow()

    private val _isTop = MutableStateFlow(false)
    val isTop: StateFlow<Boolean> = _isTop.asStateFlow()

    private var currentFileName: String? = null

    /**
     * 上次成功保存到数据库的 fileName。
     *
     * 用于检测 URL 变更：当 [currentFileName] 与 [originalFileName] 不一致时，
     * 保存前需调用 [PostRepository.renamePost] 同步更新数据库主键，
     * 否则旧 fileName 记录会残留，导致出现两篇标题相同但 URL 不同的重复文章。
     *
     * - 加载已有文章：在 [loadPost] 中设为传入的 fileName
     * - 新建文章首次保存后：设为首次生成的 fileName
     * - 每次 URL 变更并成功保存后：更新为新的 fileName
     */
    private var originalFileName: String? = null
    private var isEditing: Boolean = false

    /** 自动保存状态提示 */
    private val _autoSaveStatus = MutableStateFlow<AutoSaveStatus>(AutoSaveStatus.Idle)
    val autoSaveStatus: StateFlow<AutoSaveStatus> = _autoSaveStatus.asStateFlow()

    /** 防抖定时器 */
    private var autoSaveJob: Job? = null

    /** 加载完成标记，加载中不触发自动保存 */
    private var loaded: Boolean = false

    /**
     * 上次版本快照保存时间（毫秒）。
     * 版本历史按节流策略：两次保存间隔至少 [VERSION_SAVE_INTERVAL_MS]（2 分钟），
     * 避免每次自动保存都生成新版本造成版本列表爆炸。
     */
    private var lastVersionSaveTime: Long = 0L

    /**
     * 加载已有文章
     * 对应旧版进入 ArticleUpdate 时读取指定文章的逻辑
     */
    fun loadPost(fileName: String) {
        if (isEditing) return
        isEditing = true
        currentFileName = fileName
        originalFileName = fileName

        viewModelScope.launch {
            try {
                postRepository.getPostByFileName(fileName)?.let { post ->
                    _title.value = post.data.title
                    _content.value = post.content
                    _tags.value = post.data.tags.joinToString(",")
                    _date.value = post.data.date
                    _published.value = post.data.published
                    _hideInList.value = post.data.hideInList
                    _isTop.value = post.data.isTop
                    _feature.value = post.data.feature
                    // 编辑已有文章时把当前 fileName 填入 URL 输入框，便于用户修改
                    _customUrl.value = post.fileName
                    loadedWritingTime = post.data.writingTime
                    _writingTimeMs.value = post.data.writingTime
                }
            } catch (e: Exception) {
                // 加载失败使用默认空值，不影响编辑
            } finally {
                loaded = true
            }
        }
    }

    /**
     * 新建文章模式
     */
    fun initNewPost() {
        if (!isEditing) {
            isEditing = true
            loaded = true
        }
    }

    fun onTitleChange(value: String) {
        _title.value = value
        markWritingActivity()
        scheduleAutoSave()
    }

    fun onContentChange(value: String) {
        _content.value = value
        markWritingActivity()
        scheduleAutoSave()
    }

    /**
     * 标记用户有写作活动，启动计时器（若未启动）
     */
    private fun markWritingActivity() {
        val now = System.currentTimeMillis()
        if (sessionStartTime == 0L) {
            sessionStartTime = now
            startWritingTimer()
        }
        lastActivityTime = now
    }

    /**
     * 启动写作时长计时器：每秒检查活跃状态并实时累计写作时长
     * 退出编辑页面（onCleared）时自动停止，时长已通过自动保存同步到统计柱状图
     */
    private fun startWritingTimer() {
        if (writingTimerJob?.isActive == true) return
        writingTimerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)  // 每秒采样一次，实现 UI 实时动态计数
                val now = System.currentTimeMillis()
                if (lastActivityTime > 0 && now - lastActivityTime < ACTIVITY_TIMEOUT_MS) {
                    // 用户仍在活跃写作，累计 1 秒
                    _writingTimeMs.value = _writingTimeMs.value + 1_000L
                }
            }
        }
    }

    /**
     * 停止计时器
     */
    private fun stopWritingTimer() {
        writingTimerJob?.cancel()
        writingTimerJob = null
    }

    fun onTagsChange(value: String) {
        _tags.value = value
        scheduleAutoSave()
    }

    fun onPublishedChange(value: Boolean) { _published.value = value }
    fun onHideInListChange(value: Boolean) { _hideInList.value = value }
    fun onIsTopChange(value: Boolean) { _isTop.value = value }

    /**
     * 修改文章日期（用户通过日期选择器手动修改）
     */
    fun onDateChange(value: String) {
        _date.value = value
        scheduleAutoSave()
    }

    /**
     * 设置封面图 URL（来自图片选择器）
     */
    fun onFeatureChange(value: String) {
        _feature.value = value
        scheduleAutoSave()
    }

    /**
     * 清除封面图
     */
    fun onFeatureClear() {
        _feature.value = ""
        scheduleAutoSave()
    }

    /**
     * 用户手动修改文章 URL（fileName）。
     * 输入会被实时净化为 URL-safe slug（小写字母/数字/连字符）。
     * 一旦用户手动填写，后续修改标题不再自动覆盖此值。
     */
    fun onCustomUrlChange(value: String) {
        // 净化输入：只保留 a-z 0-9 -，其余字符替换为 -
        val sanitized = SlugUtils.sanitize(value) ?: ""
        _customUrl.value = sanitized
        // 同步更新 currentFileName，确保保存时使用用户填写的 URL
        // （resolveFileName 会在 customUrl 非空时直接采用）
        if (sanitized.isNotEmpty()) {
            currentFileName = sanitized
        }
        scheduleAutoSave()
    }

    /**
     * 防抖自动保存：内容变化后 2 秒触发
     */
    private fun scheduleAutoSave() {
        if (!loaded) return
        if (_title.value.trim().isEmpty()) return
        autoSaveJob?.cancel()
        _autoSaveStatus.value = AutoSaveStatus.Pending
        autoSaveJob = viewModelScope.launch {
            delay(2000)
            performAutoSave()
        }
    }

    private suspend fun performAutoSave() {
        val titleValue = _title.value.trim()
        if (titleValue.isEmpty()) return
        // 进入"保存中"状态，UI 显示旋转指示器
        _autoSaveStatus.value = AutoSaveStatus.Saving
        try {
            val fileName = currentFileName ?: run {
                val generated = resolveFileName(titleValue)
                currentFileName = generated
                generated
            }
            val tagList = if (_tags.value.isBlank()) emptyList()
                          else _tags.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            val post = Post(
                content = _content.value,
                fileName = fileName,
                data = PostData(
                    title = titleValue,
                    date = _date.value,
                    published = _published.value,
                    hideInList = _hideInList.value,
                    tags = tagList,
                    feature = _feature.value,
                    isTop = _isTop.value,
                    writingTime = _writingTimeMs.value
                )
            )
            // URL 变更检测：若 fileName 与上次保存的不一致，先重命名数据库主键，
            // 避免旧记录残留导致重复文章（两篇标题相同但 URL 不同）
            val oldName = originalFileName
            if (oldName != null && oldName != fileName) {
                postRepository.renamePost(oldName, fileName)
                originalFileName = fileName
            }
            postRepository.savePost(post)
            // 新建文章首次保存后记录 originalFileName，后续 URL 变更才能触发重命名
            if (originalFileName == null) {
                originalFileName = fileName
            }
            // 创建版本快照：节流策略 - 两次版本保存至少间隔 2 分钟
            // 避免每次自动保存都生成新版本造成版本列表爆炸
            val now = System.currentTimeMillis()
            if (lastVersionSaveTime == 0L || now - lastVersionSaveTime >= VERSION_SAVE_INTERVAL_MS) {
                postVersionRepository.saveVersion(post)
                lastVersionSaveTime = now
            }
            _autoSaveStatus.value = AutoSaveStatus.Saved
            // 已保存状态保持 2 秒后淡出回到空闲
            delay(2000)
            if (_autoSaveStatus.value is AutoSaveStatus.Saved) {
                _autoSaveStatus.value = AutoSaveStatus.Idle
            }
        } catch (e: CancellationException) {
            // 协程被取消（如新自动保存替换旧任务、用户离开页面）是正常行为，
            // 不应作为错误上报，按 Kotlin 协程规范重新抛出
            throw e
        } catch (e: Exception) {
            _autoSaveStatus.value = AutoSaveStatus.Error(e.message ?: "保存失败")
            AppLogger.reportUserError("Editor", "文章保存失败", e)
        }
    }

    /**
     * ViewModel 销毁时立即保存（防止离开页面丢数据）
     */
    override fun onCleared() {
        autoSaveJob?.cancel()
        stopWritingTimer()
        // 退出编辑页面时同步触发一次保存
        // 用 NonCancellable 确保保存完成，即使用户已通过手势返回/点击 Tab 退出
        // 覆盖所有退出路径：系统返回键、手势返回、点击导航栏 Tab、Activity 销毁
        // 与 savePost 一致：标题/标签/内容全空时不保存草稿
        val titleEmpty = _title.value.trim().isEmpty()
        val tagsEmpty = _tags.value.trim().isEmpty()
        val contentEmpty = _content.value.trim().isEmpty()
        val allEmpty = titleEmpty && tagsEmpty && contentEmpty
        if (loaded && !allEmpty && !titleEmpty) {
            viewModelScope.launch(NonCancellable) { performAutoSave() }
        }
        super.onCleared()
    }

    /**
     * 保存文章
     * 对应旧版 Posts.savePostToFile()
     *
     * @param onSaved 完成回调（无论是否实际保存到磁盘都调用，避免调用方卡住）
     * @param onSkip 当内容全空（标题/标签/正文都为空）不保存时调用，调用方据此提示"内容为空，未保存"
     * @return 当实际调用保存协程时返回 true，立即跳过时返回 false
     */
    fun savePost(onSaved: () -> Unit, onSkip: () -> Unit = {}) {
        val titleValue = _title.value.trim()
        val tagsValue = _tags.value.trim()
        val contentValue = _content.value.trim()

        // 标题/标签/内容全部为空时不保存草稿，调用 onSkip 让 UI 提示用户
        if (titleValue.isEmpty() && tagsValue.isEmpty() && contentValue.isEmpty()) {
            onSkip()
            return
        }

        // 仅标题为空但其他字段非空：保存（防止丢失输入），但仍按原标题规则处理
        if (titleValue.isEmpty()) {
            onSaved()
            return
        }

        // 新建文章时生成 fileName 并记录到 currentFileName
        // 关键：必须更新 currentFileName，否则 onCleared → performAutoSave 会用新时间戳
        // 再生成一个不同的 fileName，导致出现两篇标题相同的重复文章
        val isNewPost = currentFileName == null
        val fileName = currentFileName ?: run {
            val generated = resolveFileName(titleValue)
            currentFileName = generated
            generated
        }
        val tagList = if (_tags.value.isBlank()) emptyList()
                      else _tags.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val post = Post(
            content = _content.value,
            fileName = fileName,
            data = PostData(
                title = titleValue,
                date = _date.value,
                published = _published.value,
                hideInList = _hideInList.value,
                tags = tagList,
                feature = _feature.value,
                isTop = _isTop.value,
                writingTime = _writingTimeMs.value
            )
        )

        viewModelScope.launch {
            try {
                // URL 变更检测：若 fileName 与上次保存的不一致，先重命名数据库主键，
                // 避免旧记录残留导致重复文章（两篇标题相同但 URL 不同）
                val oldName = originalFileName
                if (oldName != null && oldName != fileName) {
                    postRepository.renamePost(oldName, fileName)
                    originalFileName = fileName
                }
                postRepository.savePost(post)
                // 新建文章首次保存后记录 originalFileName，后续 URL 变更才能触发重命名
                if (originalFileName == null) {
                    originalFileName = fileName
                }
                if (isNewPost) {
                    AppLogger.action("Post", "Create", titleValue)
                } else {
                    AppLogger.action("Post", "Edit", titleValue)
                }
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _autoSaveStatus.value = AutoSaveStatus.Error(e.message ?: "保存失败")
                // 保存失败也要退出，避免用户卡在编辑页；onCleared 兜底会再尝试保存
                onSaved()
            }
        }
    }

    /**
     * 导出当前文章为 Markdown 文件到指定 Uri
     * 生成带 front-matter 的 .md 文件
     */
    fun exportToUri(uri: Uri, onResult: (success: Boolean, message: String) -> Unit) {
        val titleValue = _title.value.trim()
        if (titleValue.isEmpty()) {
            onResult(false, "标题为空，无法导出")
            return
        }
        viewModelScope.launch {
            try {
                val md = buildMarkdownString(titleValue)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(md.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("无法写入文件")
                onResult(true, "导出成功")
            } catch (e: Exception) {
                onResult(false, "导出失败：${e.message ?: "未知错误"}")
            }
        }
    }

    /**
     * 生成建议的文件名（用于 CreateDocument 默认名）
     */
    fun suggestFileName(): String {
        val titleValue = _title.value.trim()
        if (titleValue.isEmpty()) return "untitled.md"
        val safe = titleValue.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "-")
            .trim('-')
            .take(30)
        return "$safe.md"
    }

    /**
     * 构建 front-matter + content 的 Markdown 字符串
     */
    private fun buildMarkdownString(titleValue: String): String {
        val tagList = if (_tags.value.isBlank()) emptyList()
                      else _tags.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val tagsYaml = if (tagList.isEmpty()) {
            "tags: []"
        } else {
            tagList.joinToString("\n", prefix = "tags:\n") { "  - $it" }
        }
        return buildString {
            appendLine("---")
            appendLine("title: $titleValue")
            appendLine("date: ${_date.value}")
            appendLine("published: ${_published.value}")
            appendLine("hideInList: ${_hideInList.value}")
            appendLine(tagsYaml)
            appendLine("isTop: ${_isTop.value}")
            if (_feature.value.isNotEmpty()) {
                appendLine("feature: ${_feature.value}")
            }
            appendLine("---")
            appendLine()
            append(_content.value)
        }
    }

    /**
     * 由标题生成 fileName（URL 路径段）。
     *
     * 受 [cachedPostUrlFormat] 设置控制：
     * - "SLUG"（默认）：用 [SlugUtils.slugify] 把标题转为 URL-safe slug，如 "gridea 使用" → "gridea-shi-yong"
     *   slugify 失败时用 [SlugUtils.generateShortId] 生成 12 位短 ID 兜底。
     *   输出只含 ASCII 字符（a-z 0-9 -），从根本上避免 URL 编码问题。
     * - "SHORT_ID"：用 [SlugUtils.generateShortId] 生成随机短 ID（如 "a3f8b2c1d4e5"），
     *   与旧版 Gridea 的 shortid 一致，URL 简短但无语义。
     */
    private fun generateFileName(title: String): String {
        return when (cachedPostUrlFormat) {
            "SHORT_ID" -> SlugUtils.generateShortId()
            else -> SlugUtils.slugify(title)?.take(60) ?: SlugUtils.generateShortId()
        }
    }

    /**
     * 保存时决定最终的 fileName：
     * - 用户在 URL 输入框填写了内容 → 直接采用（已由 [onCustomUrlChange] 净化）
     * - 用户留空 → 按标题 slugify 自动生成
     *
     * 编辑已有文章时，_customUrl 在 loadPost 中被预填为当前 fileName，
     * 所以"留空"只发生在用户新建文章且未填写 URL 的场景。
     */
    private fun resolveFileName(titleValue: String): String {
        val custom = _customUrl.value.trim()
        return if (custom.isNotEmpty()) custom else generateFileName(titleValue)
    }

    private fun currentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    // ===== 文章版本历史 =====

    /**
     * 加载当前文章的版本历史列表
     */
    fun loadVersions(onResult: (List<PostVersion>) -> Unit) {
        val fileName = currentFileName ?: run {
            onResult(emptyList())
            return
        }
        viewModelScope.launch {
            try {
                val versions = postVersionRepository.getVersions(fileName)
                onResult(versions)
            } catch (e: Exception) {
                onResult(emptyList())
            }
        }
    }

    /**
     * 恢复到指定版本
     *
     * 将版本内容加载到编辑器，并立即保存（创建新的版本快照）
     */
    fun restoreVersion(version: PostVersion) {
        _title.value = version.title
        _content.value = version.content
        _tags.value = version.tags.joinToString(",")
        // 立即触发保存，创建新快照
        scheduleAutoSave()
    }

    /**
     * 删除指定版本
     */
    fun deleteVersion(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                postVersionRepository.deleteVersion(id)
            } catch (e: Exception) {
                // 删除失败仍通知 UI 刷新
            } finally {
                onDone()
            }
        }
    }
}

/**
 * 自动保存状态
 */
sealed class AutoSaveStatus {
    /** 空闲（已保存或未编辑） */
    object Idle : AutoSaveStatus()
    /** 等待保存（防抖中，内容已修改尚未保存） */
    object Pending : AutoSaveStatus()
    /** 保存中（正在写入文件） */
    object Saving : AutoSaveStatus()
    /** 已保存（2 秒后自动回到 Idle） */
    object Saved : AutoSaveStatus()
    /** 保存失败 */
    data class Error(val message: String) : AutoSaveStatus()
}

/**
 * 批量导入进度
 *
 * @param current 当前已处理数量
 * @param total 总数量
 */
data class ImportProgress(val current: Int, val total: Int)
