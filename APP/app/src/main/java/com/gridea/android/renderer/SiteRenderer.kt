package com.gridea.android.renderer

import android.content.Context
import com.gridea.android.data.model.Post
import com.gridea.android.data.model.Setting
import com.gridea.android.data.model.Theme
import com.gridea.android.data.model.CommentSetting
import com.gridea.android.data.model.FriendLink
import com.gridea.android.data.model.Menu
import com.gridea.android.data.repository.FriendLinkRepository
import com.gridea.android.data.repository.ImageRepository
import com.gridea.android.data.repository.MenuRepository
import com.gridea.android.data.repository.SettingRepository
import com.gridea.android.data.repository.TagRepository
import com.gridea.android.data.repository.PostRepository
import com.gridea.android.util.AppLogger
import com.gridea.android.util.SlugUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 站点渲染器
 *
 * 对应旧版 Gridea 0.9.3 的 src/server/renderer.ts
 * 负责将文章、标签等数据渲染为静态 HTML 站点
 *
 * 渲染流程：
 * 1. 清空输出目录
 * 2. 准备渲染数据（Markdown → HTML）
 * 3. 生成 CSS
 * 4. 渲染首页、归档页、标签页、文章详情页、标签详情页
 * 5. 生成 RSS Feed
 */
@Singleton
class SiteRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val markdownConverter: MarkdownConverter,
    private val settingRepository: SettingRepository,
    private val postRepository: PostRepository,
    private val tagRepository: TagRepository,
    private val imageRepository: ImageRepository,
    private val friendLinkRepository: FriendLinkRepository,
    private val menuRepository: MenuRepository,
    private val themePackRepository: com.gridea.android.data.repository.ThemePackRepository
) {

    /**
     * 构建状态序列化用的 Json 实例。
     * ignoreUnknownKeys：字段演进时旧状态文件不致解析失败（回退全量重建）。
     * encodeDefaults：确保缓存的渲染产物字段被完整写入。
     */
    private val buildStateJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private const val BUILD_STATE_FILE_NAME = ".build_state.json"
    }

    /**
     * 渲染整个站点
     *
     * 对应旧版 renderer.renderAll()
     *
     * 支持增量构建（仅非预览模式）：
     * - 预览模式（isPreview=true）：始终全量重建，不读写构建状态
     * - 非预览模式：读取 .build_state.json，对比配置 hash 与文章 content hash，
     *   只对变更/新增文章执行 Markdown→HTML 转换与详情页渲染；
     *   首页/归档/标签/RSS/sitemap 等列表页始终重新渲染。
     * - 构建状态缺失或损坏、配置变更、输出目录不存在时回退到全量重建。
     *
     * **线程**：所有渲染工作（Markdown 转换、模板解析、文件 IO）均在 [Dispatchers.IO] 上执行，
     * 避免阻塞主线程导致 ANR。ViewModel 通过 viewModelScope 启动协程，主线程只负责
     * 调度，不会卡顿 UI。
     *
     * @param isPreview 是否为预览模式。预览模式下文章详情页不注入评论系统 CDN（Gitalk 等），
     *                  避免同步脚本阻塞 WebView 主线程导致加载耗时暴涨。默认 false（导出时）。
     * @param forceRebuild 是否强制全量重建。true 时清除前一次的生成缓存和构建状态，
     *                     防止文件冲突出错。默认 false。
     * @return 渲染结果（输出路径、文章数等）
     */
    suspend fun renderAll(isPreview: Boolean = false, forceRebuild: Boolean = false): RenderResult = withContext(Dispatchers.IO) {
        doRenderAll(isPreview, forceRebuild)
    }

    /**
     * 实际渲染逻辑（必须运行在 [Dispatchers.IO] 上）
     *
     * 由 [renderAll] 在 IO 调度器中调用，避免主线程被耗时渲染阻塞。
     */
    private suspend fun doRenderAll(isPreview: Boolean, forceRebuild: Boolean): RenderResult {
        val renderStartTime = System.currentTimeMillis()
        AppLogger.i("Renderer", "渲染开始: isPreview=$isPreview, forceRebuild=$forceRebuild")

        // 1. 加载配置
        val theme = settingRepository.getTheme().first()
        val setting = settingRepository.getSetting().first()
        val commentSetting = settingRepository.getCommentSetting().first()
        AppLogger.d("Renderer", "配置加载完成: 站点名=${theme.siteName}, 域名=${setting.domain}")

        // 2. 获取输出目录
        val outputDir = getOutputDir()

        // 提前加载菜单和友链数据，用于计算配置 hash
        // 修改菜单/友链后需触发全量重建，否则增量构建下未变更文章的详情页菜单不会更新
        val friendLinks = friendLinkRepository.getAllList()
        val customMenus = menuRepository.getAllList()

        // 提前加载激活主题：主题配置（scheme/darkmode/sidebar 等）变更时
        // 也需触发全量重建，否则未变更文章的详情页会保留旧主题配置
        val activeThemePack = themePackRepository.getActiveTheme()
        AppLogger.d("Renderer", "主题加载: id=${activeThemePack.id}, name=${activeThemePack.name}, " +
                "sourceDir=${activeThemePack.sourceDir}, hasTemplates=${activeThemePack.hasTemplates}")

        // 3. 计算当前配置 hash，并尝试读取上次构建状态
        //    预览模式不读取状态，直接走全量重建
        val currentConfigHash = computeConfigHash(theme, setting, commentSetting, friendLinks, customMenus, activeThemePack.configValues)
        val prevState = if (!isPreview && !forceRebuild) loadBuildState(outputDir) else null
        val canIncremental = !isPreview && !forceRebuild &&
                prevState != null &&
                prevState.configHash == currentConfigHash &&
                outputDir.exists()
        AppLogger.d("Renderer", "构建策略: configHash=$currentConfigHash, 增量构建=$canIncremental, " +
                "输出目录=${outputDir.absolutePath}")

        // 4. 处理输出目录
        //    全量重建：清空并重建输出目录
        //    增量构建：保留输出目录，复用未变更文章的详情页 HTML
        if (!canIncremental) {
            if (outputDir.exists()) {
                outputDir.deleteRecursively()
            }
            outputDir.mkdirs()
            // 生成 .nojekyll 文件：禁用 GitHub Pages 的 Jekyll 处理
            // Gridea 输出的是纯静态 HTML，无需 Jekyll 构建；缺少此文件会导致
            // GitHub Pages 尝试用 Jekyll 构建并报错（如找不到 docs 目录）
            File(outputDir, ".nojekyll").writeText("")
        } else {
            // 增量构建时也确保 .nojekyll 存在（可能被用户手动删除）
            val noJekyll = File(outputDir, ".nojekyll")
            if (!noJekyll.exists()) noJekyll.writeText("")
        }

        // 5. 获取所有已发布文章
        val allPosts = postRepository.getAllPostsSync()
        val publishedPosts = allPosts.filter { it.data.published }
        AppLogger.d("Renderer", "文章加载: 总数=${allPosts.size}, 已发布=${publishedPosts.size}")

        // 6. 准备文章渲染数据
        //    增量构建：对未变更文章复用缓存的渲染产物（contentHtml/abstractHtml/stats/toc），
        //              跳过最耗时的 Markdown→HTML 转换；变更/新增文章执行完整转换。
        //    全量重建：所有文章执行完整 Markdown 转换。
        val prepareResult = preparePostRenderData(
            publishedPosts, theme, setting,
            prevState = prevState,
            incremental = canIncremental
        )
        val postRenderDataList = prepareResult.postRenderDataList
        val changedFileNames = prepareResult.changedFileNames
        val newStatePosts = prepareResult.newStatePosts.toMutableMap()
        AppLogger.i("Renderer", "文章数据准备完成: ${postRenderDataList.size}篇, " +
                "变更文章=${changedFileNames.size}篇" +
                (if (canIncremental && changedFileNames.isNotEmpty()) " (${changedFileNames.joinToString(", ")})" else ""))

        // 7. 增量清理：删除已移除文章（上次构建存在、本次不存在）的输出目录
        if (canIncremental) {
            val postPath = theme.postPath.ifEmpty { "post" }
            prevState.posts.keys.forEach { oldFileName ->
                if (publishedPosts.none { it.fileName == oldFileName }) {
                    deletePostOutput(outputDir, postPath, oldFileName)
                }
            }
        }

        // 8. 准备标签数据
        val tagsData = prepareTagsData(postRenderDataList, theme)
        AppLogger.d("Renderer", "标签数据准备: ${tagsData.size}个标签")

        // 9. 准备菜单数据（自动生成：标准链接 + 已发布页面 + 友链入口 + 用户自定义菜单）
        // friendLinks/customMenus 已在步骤 2 提前加载（用于 configHash 计算）
        val menusData = prepareMenusData(postRenderDataList, theme, setting, friendLinks, customMenus)
        AppLogger.d("Renderer", "菜单数据准备: ${menusData.size}项 (友链${friendLinks.size}个, 自定义菜单${customMenus.size}个)")

        // 10. 构建站点数据
        // themePack.configValues 传给 SiteRenderData.themePackConfig，
        // 让 buildPage 注入 <html data-card="..."> 等控制主题 CSS 变体的属性
        // 复用步骤 2.5 提前加载的主题（避免重复 IO）
        val themePack = activeThemePack
        val siteData = SiteRenderData(
            siteName = theme.siteName.ifEmpty { "My Blog" },
            siteDescription = theme.siteDescription,
            footerInfo = theme.footerInfo,
            domain = setting.domain,
            siteAuthor = theme.siteAuthor,
            siteFavicon = theme.siteFavicon,
            siteAvatar = theme.siteAvatar,
            posts = postRenderDataList,
            tags = tagsData,
            themeConfig = theme,
            commentSetting = commentSetting,
            menus = menusData,
            // themePack.configValues 是 Map<String, Any>，转换为 Map<String, String> 供模板引擎使用
            themePackConfig = themePack.configValues.mapValues { it.value.toString() },
            themeAssets = themePack.assets
        )

        // 11. 生成 CSS（使用主题包 CSS，替换 {{变量}} 占位符）
        //     与桌面端一致，仅输出 styles/main.css，不再在根目录额外写 styles.css
        val stylesDir = File(outputDir, "styles")
        stylesDir.mkdirs()
        val cssText = processThemeCss(themePack)
        File(stylesDir, "main.css").writeText(cssText)
        AppLogger.d("Renderer", "CSS生成: ${cssText.length}字符 → styles/main.css")

        // 12. 写入主题 JS（custom.js）
        //     customJs 是注入到 IIFE 内部的纯 JS 代码片段，不接受 <script> 标签。
        //     用户若粘贴了完整 <script>...</script> 标签，自动剥离提取内部 JS。
        //     同时用 try-catch 包裹用户代码，防止单个错误导致整个主题脚本崩溃。
        //     始终生成 custom.js（即使主题无 JS），避免模板引用 <script src="scripts/custom.js">
        //     时出现 ERR_FILE_NOT_FOUND
        val scriptsDir = File(outputDir, "scripts")
        scriptsDir.mkdirs()
        val jsContent = themePack.js
        val customJs = if (jsContent.isNullOrEmpty()) {
            "/* 主题未提供 custom.js */"
        } else {
            processThemeJs(jsContent, themePack)
        }
        File(scriptsDir, "custom.js").writeText(customJs)
        AppLogger.d("Renderer", "JS生成: ${customJs.length}字符 → scripts/custom.js")

        // 12.1 复制主题包声明的附加资源（assets）到输出目录
        //      css/font/image/file 类型仅复制文件；js 类型复制文件后还会在 HTML 注入 <script>
        //      资源 src 路径相对主题根目录，复制时保留相对路径结构（如 fonts/Mona.woff2）
        copyThemeAssets(themePack, outputDir)
        AppLogger.d("Renderer", "主题资源复制: ${themePack.assets.size}项")

        // 12.2 创建 Pebble 模板引擎（从主题包的 templates 目录加载）
        //      主题包 sourceDir 指向 filesDir/themes/{id}/，templates 子目录存放 .peb 文件
        //      兼容旧主题：若 templates/ 不存在，回退到 sourceDir 根目录（.peb 直接在根目录）
        val templatesDir = themePack.sourceDir?.let { sourcePath ->
            val subDir = File(sourcePath, "templates")
            if (subDir.exists() && subDir.isDirectory) subDir else File(sourcePath)
        }
        val templateEngine = if (templatesDir != null && templatesDir.exists() &&
            templatesDir.listFiles { f -> f.extension.equals("peb", ignoreCase = true) }?.isNotEmpty() == true) {
            val engine = PebbleTemplateEngine.create(templatesDir)
            if (engine != null) {
                AppLogger.i("Renderer", "模板引擎创建成功: templatesDir=${templatesDir.absolutePath}")
            } else {
                AppLogger.w("Renderer", "模板引擎创建返回null: templatesDir=${templatesDir.absolutePath}")
            }
            engine
        } else {
            AppLogger.w("Renderer",
                "模板引擎未创建: sourceDir=${themePack.sourceDir}, " +
                "templatesDir=${templatesDir?.absolutePath}, " +
                "hasTemplates=${themePack.hasTemplates}")
            null
        }

        // 13. 渲染所有页面
        //     列表页（首页/归档/标签/标签详情/404/友链）始终重新渲染；
        //     文章详情页在增量模式下只渲染变更文章（changedFileNames），全量模式下渲染全部。
        AppLogger.i("Renderer", "页面渲染开始: 模板引擎=${if (templateEngine != null) "已启用" else "未启用(回退默认)"}")
        renderAllPages(
            outputDir, siteData, postRenderDataList, tagsData, friendLinks, theme, isPreview,
            changedFileNames = if (canIncremental) changedFileNames else null,
            templateEngine = templateEngine,
            commentSetting = commentSetting,
            setting = setting
        )

        // 14. 复制图片资源到输出目录（对应旧版 copyFiles()）
        copyImageAssets(outputDir)
        AppLogger.d("Renderer", "图片资源复制完成")

        // 15. 生成 RSS Feed
        buildFeed(outputDir, siteData, theme)
        AppLogger.d("Renderer", "RSS Feed 生成完成 → atom.xml")

        // 16. 生成 sitemap.xml（SEO 站点地图）
        buildSitemap(outputDir, siteData, theme)
        AppLogger.d("Renderer", "Sitemap 生成完成 → sitemap.xml")

        // 17. 生成 robots.txt（搜索引擎抓取规则）
        buildRobots(outputDir, siteData)
        AppLogger.d("Renderer", "robots.txt 生成完成")

        // 18. 生成 CNAME 文件（GitHub Pages 自定义域名，cname 非空时才生成）
        buildCname(outputDir, setting)

        // 19. 保存构建状态（仅非预览模式），供下次增量构建比对
        if (!isPreview) {
            val newState = BuildState(
                lastBuildTime = System.currentTimeMillis(),
                configHash = currentConfigHash,
                posts = newStatePosts
            )
            saveBuildState(outputDir, newState)
            AppLogger.d("Renderer", "构建状态已保存")
        }

        val renderDuration = System.currentTimeMillis() - renderStartTime
        AppLogger.i("Renderer", "渲染完成: ${postRenderDataList.size}篇文章, ${tagsData.size}个标签, " +
                "耗时${renderDuration}ms, 模式=${if (isPreview) "预览" else if (canIncremental) "增量" else "全量"}")

        return RenderResult(
            outputDir = outputDir.absolutePath,
            postCount = postRenderDataList.size,
            tagCount = tagsData.size
        )
    }   // doRenderAll 结束（运行在 Dispatchers.IO）

    /**
     * 渲染所有页面
     *
     * @param changedFileNames 增量构建时需重新渲染详情页的文章 fileName 集合；
     *                         为 null 表示全量构建，渲染所有文章详情页。
     */
    private fun renderAllPages(
        outputDir: File,
        siteData: SiteRenderData,
        posts: List<PostRenderData>,
        tags: List<TagRenderData>,
        friendLinks: List<FriendLink>,
        theme: Theme,
        isPreview: Boolean,
        changedFileNames: Set<String>? = null,
        templateEngine: PebbleTemplateEngine?,
        commentSetting: CommentSetting?,
        setting: Setting
    ) {
        AppLogger.d("Renderer", "→ 渲染首页(分页)")
        renderIndex(outputDir, siteData, posts, theme, templateEngine)
        AppLogger.d("Renderer", "→ 渲染归档页")
        renderArchives(outputDir, siteData, posts, theme, templateEngine)
        AppLogger.d("Renderer", "→ 渲染标签总览页")
        renderTagsPage(outputDir, siteData, tags, theme, templateEngine)
        val postCount = if (changedFileNames != null) changedFileNames.size else posts.size
        AppLogger.d("Renderer", "→ 渲染文章详情页: ${postCount}篇" +
                (if (changedFileNames != null && changedFileNames.isNotEmpty()) " (增量: ${changedFileNames.joinToString(", ")})" else ""))
        renderPostDetails(outputDir, siteData, posts, theme, isPreview, changedFileNames, templateEngine, commentSetting, setting)
        AppLogger.d("Renderer", "→ 渲染标签详情页: ${tags.size}个")
        renderTagDetails(outputDir, siteData, tags, posts, theme, templateEngine)
        if (friendLinks.isNotEmpty()) {
            AppLogger.d("Renderer", "→ 渲染友链页: ${friendLinks.size}个")
            val linksHtml = if (templateEngine != null) {
                val context = buildBaseContext(siteData, theme, "./", "友链 · ${siteData.siteName}", siteData.themePackConfig).toMutableMap()
                context["friendLinks"] = friendLinks
                templateEngine.render("friends", context)
            } else ""
            writeHtmlWithLog(File(outputDir, "links.html"), linksHtml)
        }
        // 404 错误页：在所有页面渲染之后生成
        AppLogger.d("Renderer", "→ 渲染404页")
        render404Page(outputDir, siteData, theme, templateEngine)
    }

    /**
     * 准备文章渲染数据
     * 对应旧版 formatDataForRender()
     *
     * 链接使用相对路径（如 post/{fileName}/），通过 HTML <base> 标签解析，
     * 兼容 file:// 预览和 HTTP 部署。
     *
     * 增量构建支持：当 incremental=true 且 prevState 中存在该文章且 content hash 一致时，
     * 复用上次构建缓存的渲染产物（contentHtml/abstractHtml/description/stats/toc），
     * 跳过最耗时的 Markdown→HTML 转换。变更/新增文章及全量构建时执行完整转换。
     *
     * @param prevState 上次构建状态，用于比对 content hash 并复用缓存渲染产物
     * @param incremental 是否启用增量构建
     * @return PostPrepareResult 包含渲染数据列表、需重新渲染详情页的 fileName 集合、新的构建状态文章映射
     */
    private suspend fun preparePostRenderData(
        posts: List<Post>,
        theme: Theme,
        setting: Setting,
        prevState: BuildState? = null,
        incremental: Boolean = false
    ): PostPrepareResult {
        val postPath = theme.postPath.ifEmpty { "post" }
        val tagPath = theme.tagPath.ifEmpty { "tag" }

        // 按日期倒序排序，置顶文章在前
        val sorted = posts.sortedWith(
            compareByDescending<Post> { it.data.isTop }
                .thenByDescending { it.data.date }
        )

        val postRenderDataList = mutableListOf<PostRenderData>()
        val changedFileNames = mutableSetOf<String>()
        val newStatePosts = mutableMapOf<String, PostBuildInfo>()

        sorted.forEach { post ->
            val contentHash = computeContentHash(post.content)
            // 增量构建时查找上次构建信息；全量构建时 prevInfo 始终为 null
            val prevInfo = if (incremental) prevState?.posts?.get(post.fileName) else null
            val isUnchanged = prevInfo != null && prevInfo.contentHash == contentHash

            // 标签数据从元数据生成（开销小，始终计算）
            val tags = post.data.tags.map { tagName ->
                TagRenderData(
                    name = tagName,
                    slug = tagName,
                    link = "$tagPath/$tagName/",
                    count = 0
                )
            }

            // 封面图 URL 替换（开销小，始终执行，确保 feature 图片路径正确）
            val feature = if (post.data.feature.isNotEmpty()) {
                imageRepository.replaceLocalImageUrls(post.data.feature, "")
            } else ""

            val title = post.data.title.ifEmpty { "未命名" }
            // 链接使用原始 fileName，与磁盘目录名一致，避免编码/解码不匹配导致 WebView 加载失败
            // （短 URL 功能启用后新文章的 fileName 为 ASCII slug，不受影响）
            val link = "$postPath/${post.fileName}/"

            if (isUnchanged) {
                // 未变更文章：复用缓存的渲染产物，跳过 Markdown→HTML 转换
                postRenderDataList.add(PostRenderData(
                    fileName = post.fileName,
                    title = title,
                    content = prevInfo.contentHtml,
                    abstract = prevInfo.abstractHtml,
                    description = prevInfo.description,
                    date = post.data.date,
                    tags = tags,
                    feature = feature,
                    link = link,
                    hideInList = post.data.hideInList,
                    isTop = post.data.isTop,
                    stats = PostStats(prevInfo.statsWords, prevInfo.statsMinutes, prevInfo.statsTime),
                    toc = prevInfo.tocHtml
                ))
                // 构建状态保持不变（content hash 与上次一致）
                newStatePosts[post.fileName] = prevInfo
            } else {
                // 变更/新增文章或全量构建：执行完整 Markdown→HTML 转换
                changedFileNames.add(post.fileName)

                // 编辑态 file:// 路径 → 渲染态 post-images/ 相对路径（domain 为空，配合 <base> 标签解析）
                val renderedContent = imageRepository.replaceLocalImageUrls(post.content, "")
                val contentHtml = markdownConverter.toHtml(renderedContent)
                val abstractMd = markdownConverter.extractAbstract(renderedContent)
                val abstractHtml = markdownConverter.toHtml(abstractMd)
                val description = markdownConverter.toPlainText(contentHtml)
                val stats = markdownConverter.calculateStats(renderedContent)
                val tocHtml = markdownConverter.extractToc(renderedContent)

                postRenderDataList.add(PostRenderData(
                    fileName = post.fileName,
                    title = title,
                    content = contentHtml,
                    abstract = abstractHtml,
                    description = description,
                    date = post.data.date,
                    tags = tags,
                    feature = feature,
                    link = link,
                    hideInList = post.data.hideInList,
                    isTop = post.data.isTop,
                    stats = stats,
                    toc = tocHtml
                ))

                // 缓存渲染产物到构建状态，供下次增量构建复用
                newStatePosts[post.fileName] = PostBuildInfo(
                    contentHash = contentHash,
                    updatedAt = System.currentTimeMillis(),
                    contentHtml = contentHtml,
                    abstractHtml = abstractHtml,
                    description = description,
                    statsWords = stats.words,
                    statsMinutes = stats.minutes,
                    statsTime = stats.time,
                    tocHtml = tocHtml
                )
            }
        }

        return PostPrepareResult(postRenderDataList, changedFileNames, newStatePosts)
    }

    /**
     * 准备标签渲染数据
     * 对应旧版 tagsData 聚合逻辑
     *
     * 标签 URL 受 theme.tagUrlFormat 控制：
     * - "SLUG"：用 SlugUtils.slugify 把中文标签名转拼音（如 "你好世界" → "ni-hao-shi-jie"），
     *           slugify 失败（纯符号/生僻字）回退到原名，保证链接可用
     * - 其他（"default"）：直接用原始标签名作为 URL 路径段
     *
     * 标签的 name（显示名）始终保留原始中文，仅 slug 和 link 受影响。
     */
    private suspend fun prepareTagsData(
        posts: List<PostRenderData>,
        theme: Theme
    ): List<TagRenderData> {
        val tagPath = theme.tagPath.ifEmpty { "tag" }
        val useSlug = theme.tagUrlFormat == "SLUG"

        // 从文章中聚合标签，统计每个标签的文章数
        val tagCountMap = mutableMapOf<String, Int>()
        val visiblePosts = posts.filter { !it.hideInList }

        visiblePosts.forEach { post ->
            post.tags.forEach { tag ->
                tagCountMap[tag.name] = (tagCountMap[tag.name] ?: 0) + 1
            }
        }

        return tagCountMap.entries.map { (name, count) ->
            val slug = if (useSlug) (SlugUtils.slugify(name) ?: name) else name
            TagRenderData(
                name = name,
                slug = slug,
                link = "$tagPath/$slug/",
                count = count
            )
        }.sortedByDescending { it.count }
    }

    /**
     * 准备菜单数据
     * 对应旧版 menus.ts + menuLinks 自动生成逻辑
     *
     * 自动生成菜单项：
     * - 首页、归档、标签（标准导航）
     * - 友链（存在友链数据时才加入）
     *
     * 隐藏文章不再自动加入菜单（用户标记为隐藏即视为不在前端展示）
     *
     * 链接使用相对路径，通过 <base> 标签解析
     */
    private fun prepareMenusData(
        posts: List<PostRenderData>,
        theme: Theme,
        setting: Setting,
        friendLinks: List<FriendLink>,
        customMenus: List<Menu>
    ): List<MenuRenderData> {
        val archivesPath = theme.archivesPath.ifEmpty { "archives" }
        val postPath = theme.postPath.ifEmpty { "post" }

        val menus = mutableListOf<MenuRenderData>()
        menus.add(MenuRenderData(name = "首页", link = "./"))
        menus.add(MenuRenderData(name = "归档", link = "$archivesPath/"))
        menus.add(MenuRenderData(name = "标签", link = "tags/"))

        // 注意：曾经会把 hideInList 文章作为自定义页面加入导航菜单，
        // 但这与"隐藏"语义冲突（用户标记隐藏后仍出现在前端导航中是严重错误），
        // 已移除该逻辑。隐藏文章仍会生成详情页（可通过直链访问），但不会出现在任何导航中

        // 存在友链时加入导航
        if (friendLinks.isNotEmpty()) {
            menus.add(MenuRenderData(name = "友链", link = "links.html"))
        }

        // 用户自定义菜单项
        customMenus.forEach { menu ->
            val link = if (menu.linkType == "article") {
                // 文章类型：生成文章详情页链接（与磁盘目录名一致）
                "$postPath/${menu.linkValue}/"
            } else {
                // URL 类型：直接使用用户填写的链接
                menu.linkValue
            }
            menus.add(
                MenuRenderData(
                    name = menu.name,
                    link = link,
                    openType = menu.openType
                )
            )
        }

        return menus
    }

    /**
     * 构建模板通用上下文（所有页面共享的变量）
     */
    private fun buildBaseContext(
        site: SiteRenderData,
        theme: Theme,
        baseUrl: String,
        title: String,
        themePackConfig: Map<String, String>
    ): Map<String, Any> {
        val config = themePackConfig

        // 构建 html 标签的 data-* 属性
        val dataAttrs = buildString {
            config["card_style"]?.let { append(" data-card=\"$it\"") }
            config["content_width"]?.let { append(" data-width=\"$it\"") }
            config["dark_mode"]?.let {
                val themeMode = if (it == "true") "dark" else "light"
                append(" data-theme=\"$themeMode\"")
            }
            // 其他主题特有的 data 属性
            config["columns"]?.let { append(" data-columns=\"$it\"") }
            config["drop_cap"]?.let { append(" data-drop-cap=\"$it\"") }
            config["card_blur"]?.let { append(" data-blur=\"$it\"") }
            config["cursor_blink"]?.let { append(" data-cursor=\"$it\"") }
            config["column_count"]?.let { append(" data-cols=\"$it\"") }
            config["sidebar_width"]?.let { append(" data-sidebar-width=\"$it\"") }
            config["show_social"]?.let { append(" data-social=\"$it\"") }
            config["scanline"]?.let { append(" data-scanline=\"$it\"") }
            config["bg_tone"]?.let { append(" data-tone=\"$it\"") }
            config["vertical_title"]?.let { append(" data-vertical=\"$it\"") }
        }

        // 构建 CSS 资源列表（type=css 的 assets）
        val cssAssets = site.themeAssets.filter { it.type == "css" }
        // 构建 JS 资源列表（type=js 的 assets）
        val jsAssets = site.themeAssets.filter { it.type == "js" }

        return mutableMapOf(
            "site" to mapOf(
                "siteName" to site.siteName,
                "siteDescription" to site.siteDescription,
                "siteAuthor" to site.siteAuthor,
                "siteFavicon" to site.siteFavicon,
                "siteAvatar" to site.siteAvatar,
                "footerInfo" to site.footerInfo,
                "domain" to site.domain,
                "menus" to site.menus
            ),
            "baseUrl" to baseUrl,
            "title" to title,
            "htmlDataAttrs" to dataAttrs,
            "themeVarsStyle" to "", // 主题变量样式，暂留空
            "cssAssets" to cssAssets,
            "jsAssets" to jsAssets,
            "extraScripts" to "",
            "themePackConfig" to config,
            "showHero" to (config["show_hero"] == "true")
        )
    }

    /**
     * 渲染首页
     * 对应旧版 renderPostList('')
     */
    private fun renderIndex(
        outputDir: File,
        site: SiteRenderData,
        posts: List<PostRenderData>,
        theme: Theme,
        templateEngine: PebbleTemplateEngine?
    ) {
        val pageSize = theme.postPageSize.coerceAtLeast(1)
        val visiblePosts = posts.filter { !it.hideInList }
        val totalPages = (visiblePosts.size + pageSize - 1) / pageSize

        // 第一页（输出到根 index.html）
        // baseUrl="./"：页面在根目录，相对链接 post/xxx/ 从根解析
        val firstPagePosts = visiblePosts.take(pageSize)
        val firstHtml = if (templateEngine != null) {
            val context = buildBaseContext(site, theme, "./", site.siteName, site.themePackConfig).toMutableMap()
            context["posts"] = firstPagePosts
            context["pagination"] = Pagination(
                prev = "",
                next = if (totalPages > 1) "page/2/" else "",
                current = 1,
                total = totalPages
            )
            templateEngine.render("index", context)
        } else ""
        File(outputDir, "index.html").let { f -> writeHtmlWithLog(f, firstHtml) }

        // 后续页（输出到 page/N/index.html）
        // baseUrl="../../"：页面在 page/N/ 两层子目录，<base href> 需回退两级到根目录，
        // 才能正确解析 styles/main.css、scripts/custom.js、post/xxx/ 等根目录相对路径。
        // 与 post/xxx/index.html（同样两层深）的 baseUrl="../../" 保持一致。
        for (page in 2..totalPages) {
            val start = (page - 1) * pageSize
            val pagePosts = visiblePosts.subList(start, minOf(start + pageSize, visiblePosts.size))
            val pageDir = File(File(outputDir, "page"), page.toString())
            pageDir.mkdirs()
            val html = if (templateEngine != null) {
                val context = buildBaseContext(site, theme, "../../", site.siteName, site.themePackConfig).toMutableMap()
                context["posts"] = pagePosts
                // 重要：<base href="../../"> 已将基准 URL 设为根目录，
                // prev/next 必须使用相对根目录的路径，不能再带 ../../ 前缀，
                // 否则浏览器/WebView 会二次回退导致路径错误（file:// 下尤为明显）。
                context["pagination"] = Pagination(
                    prev = if (page > 2) "page/${page - 1}/" else "./",
                    next = if (page < totalPages) "page/${page + 1}/" else "",
                    current = page,
                    total = totalPages
                )
                templateEngine.render("index", context)
            } else ""
            writeHtmlWithLog(File(pageDir, "index.html"), html)
        }
    }

    /**
     * 渲染归档页
     * 对应旧版 renderPostList(archivesPath)
     */
    private fun renderArchives(
        outputDir: File,
        site: SiteRenderData,
        posts: List<PostRenderData>,
        theme: Theme,
        templateEngine: PebbleTemplateEngine?
    ) {
        val archivesPath = theme.archivesPath.ifEmpty { "archives" }
        val archivesDir = File(outputDir, archivesPath)
        archivesDir.mkdirs()

        val visiblePosts = posts.filter { !it.hideInList }

        // 按年份分组
        val archivesByYear = visiblePosts.groupBy { post ->
            post.date.substringBefore("-").ifEmpty { "未知" }
        }.map { (year, yearPosts) ->
            mapOf("year" to year, "posts" to yearPosts)
        }

        val html = if (templateEngine != null) {
            val context = buildBaseContext(site, theme, "../", "归档 · ${site.siteName}", site.themePackConfig).toMutableMap()
            context["archivesByYear"] = archivesByYear
            templateEngine.render("archives", context)
        } else ""
        writeHtmlWithLog(File(archivesDir, "index.html"), html)
    }

    /**
     * 渲染标签总览页
     * 对应旧版 renderTags()
     */
    private fun renderTagsPage(
        outputDir: File,
        site: SiteRenderData,
        tags: List<TagRenderData>,
        theme: Theme,
        templateEngine: PebbleTemplateEngine?
    ) {
        val tagsDir = File(outputDir, "tags")
        tagsDir.mkdirs()

        val html = if (templateEngine != null) {
            val context = buildBaseContext(site, theme, "../", "标签 · ${site.siteName}", site.themePackConfig).toMutableMap()
            context["tags"] = tags
            templateEngine.render("tags", context)
        } else ""
        writeHtmlWithLog(File(tagsDir, "index.html"), html)
    }

    /**
     * 渲染文章详情页
     * 对应旧版 renderPostDetail()
     *
     * @param isPreview 是否为预览模式（控制评论系统 CDN 是否注入）
     * @param changedFileNames 增量构建时需重新渲染的文章 fileName 集合；
     *                         为 null 表示全量构建，渲染所有文章；
     *                         非空时仅渲染集合内的文章，未变更文章的详情页保留上次构建产物。
     */
    private fun renderPostDetails(
        outputDir: File,
        site: SiteRenderData,
        posts: List<PostRenderData>,
        theme: Theme,
        isPreview: Boolean = false,
        changedFileNames: Set<String>? = null,
        templateEngine: PebbleTemplateEngine?,
        commentSetting: CommentSetting?,
        setting: Setting
    ) {
        val postPath = theme.postPath.ifEmpty { "post" }
        val postsDir = File(outputDir, postPath)

        // 获取可见文章列表（用于计算上一篇/下一篇）
        val visiblePosts = posts.filter { !it.hideInList }

        posts.forEachIndexed { index, post ->
            // 增量构建：跳过未变更文章，其详情页 HTML 已在上次构建中生成并保留
            if (changedFileNames != null && post.fileName !in changedFileNames) {
                return@forEachIndexed
            }

            val postDir = File(postsDir, post.fileName)
            postDir.mkdirs()

            // 计算上一篇/下一篇
            val visibleIndex = visiblePosts.indexOf(post)
            val prevPost = if (visibleIndex > 0) visiblePosts[visibleIndex - 1] else null
            val nextPost = if (visibleIndex < visiblePosts.size - 1) visiblePosts[visibleIndex + 1] else null

            val postWithNav = post.copy(
                prevPost = prevPost,
                nextPost = nextPost
            )

            val html = if (templateEngine != null) {
                val context = buildBaseContext(site, theme, "../../", post.title, site.themePackConfig).toMutableMap()
                context["post"] = postWithNav
                // 评论区 HTML
                context["commentHtml"] = CommentRenderer.renderCommentHtml(
                    commentSetting, postWithNav, setting.domain, isPreview
                )
                // OG 标签和 JSON-LD（简单实现）
                context["ogTags"] = ""
                context["jsonLd"] = ""
                templateEngine.render("post", context)
            } else ""
            writeHtmlWithLog(File(postDir, "index.html"), html)
        }
    }

    /**
     * 渲染标签详情页
     * 对应旧版 renderTagDetail()
     */
    private fun renderTagDetails(
        outputDir: File,
        site: SiteRenderData,
        tags: List<TagRenderData>,
        posts: List<PostRenderData>,
        theme: Theme,
        templateEngine: PebbleTemplateEngine?
    ) {
        val tagPath = theme.tagPath.ifEmpty { "tag" }
        val tagsDir = File(outputDir, tagPath)

        tags.forEach { tag ->
            val tagDir = File(tagsDir, tag.slug)
            tagDir.mkdirs()

            // 筛选该标签下的文章
            val tagPosts = posts.filter { post ->
                post.tags.any { it.name == tag.name }
            }

            val html = if (templateEngine != null) {
                val context = buildBaseContext(site, theme, "../../", "${tag.name} · ${site.siteName}", site.themePackConfig).toMutableMap()
                context["tag"] = tag
                context["posts"] = tagPosts
                context["pagination"] = Pagination(current = 1, total = 1)
                templateEngine.render("tag", context)
            } else ""
            writeHtmlWithLog(File(tagDir, "index.html"), html)
        }
    }

    /**
     * 渲染 404 错误页
     * 生成 404.html，当访问不存在的页面时由部署平台/服务器展示
     */
    private fun render404Page(outputDir: File, site: SiteRenderData, theme: Theme, templateEngine: PebbleTemplateEngine?) {
        val html = if (templateEngine != null) {
            val context = buildBaseContext(site, theme, "./", "404 · ${site.siteName}", site.themePackConfig).toMutableMap()
            templateEngine.render("404", context)
        } else ""
        writeHtmlWithLog(File(outputDir, "404.html"), html)
    }

    /**
     * 生成 RSS Feed
     * 对应旧版 buildFeed()
     * 简化实现：手写 Atom 1.0 XML
     */
    private fun buildFeed(
        outputDir: File,
        site: SiteRenderData,
        theme: Theme
    ) {
        val feedCount = theme.feedCount.coerceAtLeast(1)
        val feedPosts = site.posts
            .filter { !it.hideInList }
            .take(feedCount)

        val feedXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<feed xmlns="http://www.w3.org/2005/Atom">""")
            append("<title>${escapeXml(site.siteName)}</title>")
            append("<subtitle>${escapeXml(site.siteDescription)}</subtitle>")
            append("<id>${escapeXml(site.domain)}/</id>")
            append("""<link href="${escapeXml(site.domain)}/"/>""")
            append("<updated>${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())}</updated>")

            feedPosts.forEach { post ->
                // RSS 需要绝对 URL，将相对链接拼接 domain
                val postAbsoluteLink = if (site.domain.isNotEmpty()) {
                    "${site.domain.trimEnd('/')}/${post.link}"
                } else {
                    post.link
                }
                append("<entry>")
                append("<title>${escapeXml(post.title)}</title>")
                append("<id>${escapeXml(postAbsoluteLink)}</id>")
                append("""<link href="${escapeXml(postAbsoluteLink)}"/>""")
                append("<updated>${post.date}T00:00:00Z</updated>")
                append("<summary>${escapeXml(post.description)}</summary>")
                if (theme.feedFullText) {
                    append("""<content type="html">${escapeXml(post.content)}</content>""")
                }
                append("</entry>")
            }

            append("</feed>")
        }

        File(outputDir, "atom.xml").writeText(feedXml)
    }

    /**
     * 生成 sitemap.xml
     * SEO 站点地图：遍历所有已发布文章及主要页面生成 URL 条目
     * 包含首页、归档页、标签总览页、标签详情页、文章详情页、自定义页面
     * 当 domain 为空时跳过生成（无法拼接绝对 URL）
     */
    private fun buildSitemap(
        outputDir: File,
        site: SiteRenderData,
        theme: Theme
    ) {
        val domain = site.domain.trimEnd('/')
        // domain 为空时无法生成绝对 URL，跳过 sitemap 生成
        if (domain.isEmpty()) return

        val archivesPath = theme.archivesPath.ifEmpty { "archives" }

        // 拼接绝对 URL
        fun absoluteUrl(path: String): String = "$domain/${path.trimStart('/')}"

        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")

            // 首页
            append("<url>")
            append("<loc>${escapeXml(domain)}/</loc>")
            append("<changefreq>daily</changefreq>")
            append("<priority>1.0</priority>")
            append("</url>")

            // 归档页
            append("<url>")
            append("<loc>${escapeXml(absoluteUrl("$archivesPath/"))}</loc>")
            append("<changefreq>weekly</changefreq>")
            append("<priority>0.6</priority>")
            append("</url>")

            // 标签总览页
            append("<url>")
            append("<loc>${escapeXml(absoluteUrl("tags/"))}</loc>")
            append("<changefreq>weekly</changefreq>")
            append("<priority>0.6</priority>")
            append("</url>")

            // 标签详情页
            site.tags.forEach { tag ->
                append("<url>")
                append("<loc>${escapeXml(absoluteUrl(tag.link))}</loc>")
                append("<changefreq>weekly</changefreq>")
                append("<priority>0.5</priority>")
                append("</url>")
            }

            // 文章详情页 + 自定义页面（hideInList 的文章作为自定义页面）
            site.posts.forEach { post ->
                append("<url>")
                append("<loc>${escapeXml(absoluteUrl(post.link))}</loc>")
                if (post.date.isNotEmpty()) {
                    append("<lastmod>${escapeXml(post.date)}</lastmod>")
                }
                append("<changefreq>monthly</changefreq>")
                append("<priority>0.8</priority>")
                append("</url>")
            }

            append("</urlset>")
        }

        File(outputDir, "sitemap.xml").writeText(xml)
    }

    /**
     * 生成 robots.txt
     * 允许所有搜索引擎爬虫抓取全站，并声明 sitemap 地址
     */
    private fun buildRobots(outputDir: File, site: SiteRenderData) {
        val domain = site.domain.trimEnd('/')
        val content = buildString {
            append("User-agent: *\n")
            append("Allow: /\n")
            // domain 非空时声明 sitemap 地址
            if (domain.isNotEmpty()) {
                append("Sitemap: $domain/sitemap.xml\n")
            }
        }
        File(outputDir, "robots.txt").writeText(content)
    }

    /**
     * 生成 CNAME 文件（GitHub Pages 自定义域名）
     * 当 setting.cname 配置非空时写入输出目录根目录
     * 文件内容为自定义域名，GitHub Pages 会据此启用自定义域名访问
     */
    private fun buildCname(outputDir: File, setting: Setting) {
        val cname = setting.cname.trim()
        if (cname.isEmpty()) return
        File(outputDir, "CNAME").writeText(cname)
    }

    /**
     * 获取输出目录
     * 移动端使用应用缓存目录
     */
    private fun getOutputDir(): File {
        return File(context.cacheDir, "gridea_build")
    }

    /**
     * 复制主题包声明的附加资源（assets）到输出目录。
     *
     * 用户主题：sourceDir 指向 filesDir/themes/{id}/，直接从文件系统复制
     * 内置主题：sourceDir 为 null，从 context.assets 读取（路径 themes/{id}/{src}）
     *
     * 复制时保留 src 相对路径结构（如 fonts/Mona.woff2 → outputDir/fonts/Mona.woff2）。
     * 字体/图片等二进制文件用二进制流复制，避免 charset 转换破坏内容。
     */
    private fun copyThemeAssets(
        themePack: com.gridea.android.data.model.ThemePack,
        outputDir: File
    ) {
        if (themePack.assets.isEmpty()) return
        val themeId = themePack.id
        val sourceDirPath = themePack.sourceDir

        themePack.assets.forEach { asset ->
            if (asset.src.isBlank()) return@forEach
            val destFile = File(outputDir, asset.src)
            destFile.parentFile?.mkdirs()

            try {
                if (sourceDirPath != null) {
                    // 用户主题：从文件系统复制
                    val srcFile = File(sourceDirPath, asset.src)
                    if (srcFile.exists()) {
                        srcFile.copyTo(destFile, overwrite = true)
                    }
                } else {
                    // 内置主题：从 context.assets 读取
                    val assetPath = "themes/$themeId/${asset.src}"
                    context.assets.open(assetPath).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (_: Exception) {
                // 资源缺失不影响渲染，仅跳过（可在日志中记录）
            }
        }
    }

    /**
     * 根据菜单名称返回对应的 FontAwesome 图标类名。
     * 用于导航菜单项的图标显示。
     */
    private fun getMenuIcon(name: String): String {
        return when (name.lowercase()) {
            "home", "首页", "首页" -> "fa fa-home"
            "archives", "归档", "归档" -> "fa fa-archive"
            "tags", "标签", "标签" -> "fa fa-tags"
            "categories", "分类", "分类" -> "fa fa-th"
            "about", "关于", "关于" -> "fa fa-user"
            "links", "友链", "友链" -> "fa fa-link"
            "search", "搜索", "搜索" -> "fa fa-search"
            else -> "fa fa-chevron-right"
        }
    }

    /**
     * 将 JSONObject 递归转换为 Map<String, Any>。
     * 嵌套 JSONObject → Map，JSONArray → List，其他类型原样保留。
     */
    private fun jsonToMap(json: org.json.JSONObject): MutableMap<String, Any> {
        val map = mutableMapOf<String, Any>()
        for (key in json.keys()) {
            val value = json.get(key)
            when (value) {
                is org.json.JSONObject -> map[key] = jsonToMap(value)
                is org.json.JSONArray -> map[key] = jsonToList(value)
                org.json.JSONObject.NULL -> map[key] = ""
                else -> map[key] = value
            }
        }
        return map
    }

    /**
     * 将 JSONArray 转换为 List<Any>。
     */
    private fun jsonToList(json: org.json.JSONArray): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until json.length()) {
            val value = json.get(i)
            when (value) {
                is org.json.JSONObject -> list.add(jsonToMap(value))
                is org.json.JSONArray -> list.add(jsonToList(value))
                org.json.JSONObject.NULL -> list.add("")
                else -> list.add(value)
            }
        }
        return list
    }

    /**
     * 将配置覆盖值应用到嵌套 Map 的指定路径。
     *
     * @param configMap 配置 Map（会被修改）
     * @param configKey 配置键名（仅用于日志）
     * @param value 覆盖值，null 时不覆盖
     * @param path 嵌套路径，如 listOf("sidebar", "position")
     */
    @Suppress("UNCHECKED_CAST")
    private fun applyConfigOverride(
        configMap: MutableMap<String, Any>,
        configKey: String,
        value: Any?,
        path: List<String>
    ) {
        if (value == null) return
        // 跳过空字符串覆盖（用户未配置时保留 _config.json 默认值）
        if (value is String && value.isBlank()) return

        var current: MutableMap<String, Any> = configMap
        for (i in 0 until path.size - 1) {
            val segment = path[i]
            val next = current[segment]
            if (next is MutableMap<*, *>) {
                current = next as MutableMap<String, Any>
            } else {
                // 路径不存在，创建中间 Map
                val newMap = mutableMapOf<String, Any>()
                current[segment] = newMap
                current = newMap
            }
        }
        current[path.last()] = value
    }

    /**
     * 从配置值中提取 Boolean。
     *
     * 由于 DataStore 持久化时所有值都被转 String，重新读取时可能拿到 "true"/"false" 字符串；
     * 主题 JSON 的默认值是真正的 Boolean。统一在此处兼容：
     * - true/Boolean true → true
     * - "true"/"TRUE" → true
     * - 其他 → false
     *
     * null/空字符串/非 Boolean 字符串统一视为 false（用户未配置时的安全默认值）。
     */
    private fun extractBoolean(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            is Number -> value.toInt() != 0
            else -> false
        }
    }

    /**
     * 处理主题 CSS：将 {{变量名}} 占位符替换为用户配置值
     *
     * 支持三类占位符：
     * 1. 简单替换：{{key}} → 配置值
     * 2. 布尔条件块：{{#key}}...{{/key}}（值为 true 时保留内容）
     *    反向条件：{{^key}}...{{/key}}（值不为 true 时保留内容）
     * 3. 值匹配条件块：{{#key_value}}...{{/key_value}}（配置值等于 value 时保留内容）
     *
     * 如果主题包 CSS 为空（异常情况），回退到内置默认 CSS
     */
    private fun processThemeCss(themePack: com.gridea.android.data.model.ThemePack): String {
        if (themePack.css.isBlank()) {
            // 回退到内置默认 CSS
            val theme = settingRepository.getTheme()
            val themeValue = kotlinx.coroutines.runBlocking { theme.first() }
            return DefaultCssGenerator.getDefaultCss(themeValue)
        }

        var css = themePack.css
        val configValues = themePack.configValues

        // 1. 处理条件块 {{#block}}...{{/block}} 和 {{^block}}...{{/block}}
        //    block 名可能是配置键名（布尔条件）或 key_value 形式（值匹配条件）
        //    使用非贪婪匹配，避免跨块错误捕获
        val blockPattern = Regex("\\{\\{([#^])([a-zA-Z0-9_]+)\\}\\}(.*?)\\{\\{/\\2\\}\\}", RegexOption.DOT_MATCHES_ALL)
        css = blockPattern.replace(css) { match ->
            val isInverted = match.groupValues[1] == "^"
            val blockName = match.groupValues[2]
            val content = match.groupValues[3]

            val shouldInclude = evaluateCondition(blockName, configValues)
            val result = if (isInverted) !shouldInclude else shouldInclude
            if (result) content else ""
        }

        // 2. 替换所有 {{变量名}} 占位符为配置值
        for ((key, value) in configValues) {
            // 字体键值是枚举（system/serif/mono/sans），需要转成 CSS font-family 真实堆栈
            // 否则 CSS 中 `var(--font-family)` 会变成无效字符串，导致整个页面布局异常（白屏）
            val replaced = if (key == "fontFamily") {
                resolveFontStack(value.toString())
            } else {
                value.toString()
            }
            css = css.replace("{{$key}}", replaced)
        }
        // 3. 替换可能遗留的未配置占位符为空字符串
        //    注意：Android ICU 正则引擎对未配对的 } 报错，必须转义为 \\}
        css = css.replace(Regex("\\{\\{[^}]+\\}\\}"), "")

        // 4. 追加 scheme CSS（布局方案样式）
        //    custom.css 是 base CSS（normalize/typography 等），
        //    scheme CSS（main.muse.css 等）包含各方案的布局样式，需追加到 main.css。
        //    scheme 值来自 themePack.configValues["scheme"]，默认 "Muse"。
        val scheme = (configValues["scheme"] as? String)?.takeIf { it.isNotBlank() } ?: "Muse"
        val schemeCssPath = "assets/styles/main.${scheme.lowercase()}.css"
        val schemeCss = readThemeAsset(themePack, schemeCssPath)
        if (schemeCss.isNotEmpty()) {
            css = css + "\n\n/* Scheme: $scheme */\n" + schemeCss
        }

        return css
    }

    /**
     * 读取主题资源文件内容。
     * 内置主题从 assets 读取，用户主题从 sourceDir 读取。
     */
    private fun readThemeAsset(themePack: com.gridea.android.data.model.ThemePack, relativePath: String): String {
        return try {
            if (themePack.sourceDir != null) {
                val file = File(themePack.sourceDir, relativePath)
                if (file.exists()) file.readText() else ""
            } else {
                val assetPath = "themes/${themePack.id}/$relativePath"
                context.assets.open(assetPath).bufferedReader().use { it.readText() }
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 评估条件块是否应保留内容
     *
     * 支持两种模式：
     * - 布尔条件：blockName 直接匹配配置键名，值为 "true"/true 时返回 true
     * - 值匹配条件：blockName 形如 key_value，当配置键 key 的值等于 value 时返回 true
     *   （按最后一个下划线拆分，优先尝试完整键名匹配）
     */
    private fun evaluateCondition(
        blockName: String,
        configValues: Map<String, Any>
    ): Boolean {
        // 先尝试作为布尔条件（blockName 直接是配置键名）
        if (configValues.containsKey(blockName)) {
            val v = configValues[blockName]
            return v is Boolean && v || v?.toString()?.equals("true", ignoreCase = true) == true
        }
        // 尝试值匹配条件：按最后一个下划线拆分为 key 和 value
        val lastUnderscore = blockName.lastIndexOf('_')
        if (lastUnderscore > 0) {
            val key = blockName.substring(0, lastUnderscore)
            val value = blockName.substring(lastUnderscore + 1)
            if (configValues.containsKey(key)) {
                return configValues[key]?.toString() == value
            }
        }
        return false
    }

    /**
     * 处理主题 JS（custom.js）。
     *
     * 1. 替换 {{customJs}} 占位符为用户在主题配置中填写的自定义 JS 代码。
     * 2. 自动剥离用户误粘贴的 `<script>` 标签：
     *    - `<script src="..."></script>` → 转为 document.write 加载（同步阻塞，确保库在后续代码前加载）
     *    - `<script type="text/javascript">code</script>` → 提取内部纯 JS 代码
     *    - 裸 `<script>code</script>` → 提取内部纯 JS 代码
     *    这样即使用户把完整的 HTML script 标签粘贴到 customJs 配置项，
     *    也不会导致 JS 语法错误（`<` 在 JS 中是非法起始 token）使整个 custom.js 崩溃。
     * 3. 用 try-catch 包裹用户代码，防止单个运行时错误（如 L2Dwidget 未定义）
     *    导致整个主题 IIFE 中断，后续 init() 等关键函数无法执行。
     */
    private fun processThemeJs(jsTemplate: String, themePack: com.gridea.android.data.model.ThemePack): String {
        var js = jsTemplate

        // 1. 替换 {{customJs}} 占位符
        val customJsValue = themePack.configValues["customJs"]?.toString() ?: ""

        // 2. 预处理用户 customJs：剥离 <script> 标签
        val sanitizedCustomJs = if (customJsValue.contains("<script")) {
            sanitizeScriptTags(customJsValue)
        } else {
            customJsValue
        }

        // 3. 用 try-catch 包裹用户代码，隔离错误
        val wrappedCustomJs = if (sanitizedCustomJs.isNotBlank()) {
            """
            /* === 用户自定义 JS（错误隔离） === */
            try {
                $sanitizedCustomJs
            } catch(e) {
                if (window.console) console.warn('[customJs]', e && e.message || e);
            }
            """.trimIndent()
        } else ""

        js = js.replace("{{customJs}}", wrappedCustomJs)

        // 4. 替换其他配置值占位符 {{key}} → 配置值
        //    主题 JS 可通过 {{configKey}} 读取用户配置（如皮肤选择、暗色模式开关等）
        for ((key, value) in themePack.configValues) {
            if (key == "customJs") continue // 已在上方处理
            js = js.replace("{{$key}}", value.toString())
        }

        // 5. 替换可能遗留的未配置占位符为空字符串
        js = js.replace(Regex("\\{\\{[^}]+\\}\\}"), "")
        return js
    }

    /**
     * 剥离 <script> 标签，提取纯 JS 代码或转为 document.write 加载。
     *
     * - `<script src="url"></script>` → `document.write('<script src="url"><\/script>');`
     *   同步写入确保库在后续代码执行前加载完成（与 HTML 中 <script> 行为一致）
     * - `<script ...>code</script>` → 直接提取 code
     */
    private fun sanitizeScriptTags(input: String): String {
        val result = StringBuilder()
        var remaining = input

        val scriptRegex = Regex("""<script\b([^>]*)>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
        while (remaining.isNotEmpty()) {
            val match = scriptRegex.find(remaining)
            if (match == null) {
                // 剩余部分无 <script> 标签，作为纯 JS 代码追加
                result.append(remaining)
                break
            }
            // 追加 <script> 标签前的纯文本
            if (match.range.first > 0) {
                result.append(remaining.substring(0, match.range.first))
            }
            val attrs = match.groupValues[1].trim()
            val innerCode = match.groupValues[2].trim()
            val srcMatch = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)
            if (srcMatch != null) {
                // <script src="..."> → 转为 document.write 同步加载
                val src = srcMatch.groupValues[1]
                result.append("\ndocument.write('<script src=\"$src\"><\\/script>');\n")
            } else if (innerCode.isNotEmpty()) {
                // <script>code</script> → 提取纯 JS 代码
                result.append("\n$innerCode\n")
            }
            remaining = remaining.substring(match.range.last + 1)
        }
        return result.toString().trim()
    }

    /**
     * 将主题配置中的 fontFamily 枚举值映射为 CSS font-family 字体堆栈。
     *
     * CSS 中 `--font-family` 必须用真实可用的字体名/堆栈（如 "Helvetica, Arial, serif"），
     * 主题配置中存的是枚举 key（system/serif/mono/sans），不能直接塞到 CSS 里。
     */
    private fun resolveFontStack(value: String): String {
        return when (value) {
            "serif" -> "Georgia, 'Times New Roman', 'Source Han Serif SC', 'Noto Serif CJK SC', serif"
            "mono" -> "'Courier New', Consolas, Monaco, 'Source Han Mono SC', monospace"
            "sans" -> "'Helvetica Neue', Helvetica, Arial, 'Source Han Sans SC', 'Noto Sans CJK SC', sans-serif"
            "system", "" -> "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"
            else -> value  // 用户可能直接填了字体堆栈，透传
        }
    }

    /**
     * 写入 HTML 文件
     *
     * file:// 协议下 WebView 不会处理 <base href="/"> 绝对根路径，必须保留原始
     * 相对路径 base href（./、../、../../），由 WebView 基于当前 URL 解析。
     * 带尾斜杠的目录链接（如 post/abc/）的 index.html 自动加载由 PreviewScreen
     * 的 WebViewClient 在 shouldOverrideUrlLoading 中手动改写，避免空白页。
     */
    private fun writeHtmlWithLog(file: File, html: String, isPreview: Boolean = false) {
        file.parentFile?.mkdirs()
        // 重要：file:// 协议下 WebView 不会处理 <base href="/"> 这种绝对根路径，
        // 必须保持各页面原本的相对路径 base href（./ ../ ../../），
        // 让 WebView 基于当前页面 URL 自动解析相对链接。
        // 后续在 PreviewScreen 的 WebViewClient 中拦截带尾斜杠的目录路径，
        // 手动改写为 .../index.html，避免 WebView 不自动加载目录索引导致空白。
        file.writeText(html)
    }

    /**
     * 复制图片资源到输出目录
     * 对应旧版 renderer.ts 的 copyFiles() 中的 post-images / images 复制
     *
     * 与桌面端一致的输出结构：
     * - post-images/：文章内引用的图片（从 imageRepository 源目录复制）
     * - images/：站点身份图片（avatar、favicon 等，从 filesDir/images 复制）
     *
     * 两者均为相对路径根目录下的子目录，HTML 中以 /post-images/xxx.png 引用
     */
    private fun copyImageAssets(outputDir: File) {
        // 1. 文章图片 → post-images/
        val sourceImageDir = imageRepository.getImageDir()
        if (sourceImageDir.exists() && sourceImageDir.isDirectory) {
            val destImageDir = File(outputDir, "post-images")
            destImageDir.mkdirs()
            sourceImageDir.listFiles { f -> f.isFile }?.forEach { file ->
                file.copyTo(File(destImageDir, file.name), overwrite = true)
            }
        }
        // 2. 站点身份图片（avatar 等）→ images/
        //    桌面端将 Gridea/images/ 整体复制到 output/images/
        val siteImagesDir = File(context.filesDir, "images")
        if (siteImagesDir.exists() && siteImagesDir.isDirectory) {
            val destSiteImagesDir = File(outputDir, "images")
            siteImagesDir.copyRecursively(destSiteImagesDir, overwrite = true)
        }
    }

    /**
     * XML 转义
     */
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    // ==================== 增量构建辅助方法 ====================

    /**
     * 计算内容的 MD5 hash，用于检测文章内容是否变化。
     * 返回 32 位小写十六进制字符串。
     */
    private fun computeContentHash(content: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(content.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 计算站点配置 hash（theme + setting + commentSetting 的序列化 hash）。
     * 配置变化时触发全量重建，因为配置影响所有页面的渲染。
     */
    private fun computeConfigHash(
        theme: Theme,
        setting: Setting,
        commentSetting: CommentSetting,
        friendLinks: List<FriendLink>,
        customMenus: List<Menu>,
        themeConfigValues: Map<String, Any> = emptyMap()
    ): String {
        // 关键：主题配置（scheme/darkmode/sidebar 等）变更时必须触发全量重建，
        // 否则增量构建下未变更文章的详情页会保留旧主题配置
        // 注意：themeConfigValues 是 Map<String, Any>，kotlinx.serialization 无法序列化 Any，
        // 改用手动拼接 key=value 字符串参与 hash 计算
        val themeConfigString = themeConfigValues.toSortedMap().entries
            .joinToString(",") { "${it.key}=${it.value}" }
        val configString = buildStateJson.encodeToString(theme) +
                buildStateJson.encodeToString(setting) +
                buildStateJson.encodeToString(commentSetting) +
                buildStateJson.encodeToString(friendLinks) +
                buildStateJson.encodeToString(customMenus) +
                themeConfigString
        return computeContentHash(configString)
    }

    /**
     * 读取构建状态文件。
     * 文件不存在或解析失败（损坏/格式不兼容）时返回 null，调用方据此回退到全量重建。
     */
    private fun loadBuildState(outputDir: File): BuildState? {
        return try {
            val stateFile = File(outputDir, BUILD_STATE_FILE_NAME)
            if (!stateFile.exists()) return null
            val content = stateFile.readText()
            buildStateJson.decodeFromString<BuildState>(content)
        } catch (e: Exception) {
            // 状态文件损坏或读取失败：回退到全量重建
            null
        }
    }

    /**
     * 保存构建状态文件到输出目录。
     * 保存失败不影响本次构建结果，下次构建会因状态缺失而回退到全量重建。
     */
    private fun saveBuildState(outputDir: File, state: BuildState) {
        try {
            val stateFile = File(outputDir, BUILD_STATE_FILE_NAME)
            stateFile.writeText(buildStateJson.encodeToString(state))
        } catch (e: Exception) {
            // 保存失败：忽略，下次构建回退全量重建
        }
    }

    /**
     * 删除指定文章的输出目录（增量构建时清理已删除文章的残留文件）。
     */
    private fun deletePostOutput(outputDir: File, postPath: String, fileName: String) {
        val postDir = File(File(outputDir, postPath), fileName)
        if (postDir.exists()) {
            postDir.deleteRecursively()
        }
    }

    /**
     * 单篇文章的构建状态信息。
     * 除 contentHash/updatedAt 外，还缓存 Markdown 渲染产物，
     * 使未变更文章在增量构建时完全跳过 Markdown→HTML 转换，
     * 同时列表页（首页摘要等）仍能正确展示。
     */
    @Serializable
    private data class PostBuildInfo(
        val contentHash: String,
        val updatedAt: Long,
        val contentHtml: String = "",
        val abstractHtml: String = "",
        val description: String = "",
        val statsWords: Int = 0,
        val statsMinutes: Int = 0,
        val statsTime: Long = 0L,
        val tocHtml: String = ""
    )

    /**
     * 构建状态：记录上次构建的配置 hash 与每篇文章的渲染状态。
     * 序列化为 .build_state.json 存放在输出目录中。
     */
    @Serializable
    private data class BuildState(
        val lastBuildTime: Long,
        val configHash: String,
        val posts: Map<String, PostBuildInfo>
    )

    /**
     * preparePostRenderData 的返回结果。
     */
    private data class PostPrepareResult(
        val postRenderDataList: List<PostRenderData>,
        val changedFileNames: Set<String>,
        val newStatePosts: Map<String, PostBuildInfo>
    )
}

/**
 * 渲染结果
 */
data class RenderResult(
    val outputDir: String,
    val postCount: Int,
    val tagCount: Int
)
