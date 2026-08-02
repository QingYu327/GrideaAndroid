package com.gridea.android.renderer

import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Markdown 转 HTML 转换器
 *
 * 对应旧版 Gridea 0.9.3 的 src/server/plugins/markdown.ts
 * 旧版使用 markdown-it + 13 个插件，移动端使用 commonmark-java + 后处理方式实现
 *
 * 支持的扩展语法（通过预处理/后处理实现，对齐旧版 markdown-it 插件）：
 * - GFM 表格 → 通过 TablesExtension 注册启用
 * - 删除线（~~text~~）→ 后处理正则替换为 `<del>text</del>`
 * - 任务列表（- [x] / - [ ]）→ 转为 HTML checkbox
 * - 高亮标记（==text==）→ `<mark>text</mark>`
 * - 上标（^text^）→ `<sup>text</sup>`
 * - 下标（~text~）→ `<sub>text</sub>`
 * - 代码块语言标记 → 添加 `language-xxx` class 供 Prism.js 高亮
 * - TOC 目录 → 提取 H1-H6 生成目录 HTML
 */
@Singleton
class MarkdownConverter @Inject constructor() {

    // 注册 commonmark 扩展：GFM 表格（删除线用正则后处理实现）
    private val extensions = listOf(TablesExtension.create())

    private val parser: Parser by lazy {
        Parser.builder()
            .extensions(extensions)
            .build()
    }

    private val htmlRenderer: HtmlRenderer by lazy {
        HtmlRenderer.builder()
            .extensions(extensions)
            .build()
    }

    /**
     * 将 Markdown 转换为 HTML 字符串
     *
     * 对应旧版 markdown.render(content)
     * 包含后处理：任务列表、高亮、上下标、代码语言标记
     *
     * @param markdown Markdown 原文
     * @return HTML 字符串
     */
    fun toHtml(markdown: String): String {
        if (markdown.isEmpty()) return ""
        val html = htmlRenderer.render(parser.parse(markdown))
        return postProcessHtml(html)
    }

    /**
     * 后处理 HTML，添加扩展语法支持
     */
    private fun postProcessHtml(html: String): String {
        var result = html

        // 1. 任务列表：将 <li>[ ] / [x] 文本</li> 转为 checkbox
        // commonmark 会把 - [ ] task 渲染为 <li>[ ] task</li> 或 <li><p>[ ] task</p></li>
        result = result.replace(
            Regex("""<li>(<p>)?\s*\[ \]\s*"""),
            "<li class=\"task-list-item\"><input type=\"checkbox\" disabled> $1"
        )
        result = result.replace(
            Regex("""<li>(<p>)?\s*\[x\]\s*""", RegexOption.IGNORE_CASE),
            "<li class=\"task-list-item\"><input type=\"checkbox\" checked disabled> $1"
        )

        // 2. 高亮标记 ==text== → <mark>text</mark>
        // 避免匹配 HTML 属性中的 ==，只匹配非 = 字符之间的内容
        result = result.replace(
            Regex("""(?<![=])==([^=\n]+)==(?![=])"""),
            "<mark>$1</mark>"
        )

        // 3. 上标 ^text^ → <sup>text</sup>
        // 只匹配单词字符，避免与幂运算等冲突
        result = result.replace(
            Regex("""\^([^\s^]+)\^"""),
            "<sup>$1</sup>"
        )

        // 4. 删除线 ~~text~~ → <del>text</del>
        // 必须在下标 ~text~ 之前处理，避免冲突
        result = result.replace(
            Regex("""~~([^~\n]+)~~"""),
            "<del>$1</del>"
        )

        // 5. 下标 ~text~ → <sub>text</sub>
        // 注意不与删除线 ~~text~~ 冲突（删除线已在上一步处理）
        result = result.replace(
            Regex("""(?<!~)~([^~\n]+)~(?!~)"""),
            "<sub>$1</sub>"
        )

        // 6. 代码块添加语言 class（commonmark 已生成 <code class="language-xxx">）
        // 确保 fenced code block 的语言 class 格式正确，供 Prism.js 高亮

        // 7. 图片优化：给所有 <img> 标签添加 loading="lazy" decoding="async" 属性
        // 关键修复文章详情页滑动卡顿：WebView 在 file:// 协议下同步解码图片会阻塞主线程，
        // 多张图片串行解码导致文章页加载 9-10 秒。
        // - loading="lazy"：延迟加载屏幕外的图片
        // - decoding="async"：异步解码，不阻塞主线程渲染
        // 与 renderPostCard 中 feature 图的优化属性保持一致
        // 正则匹配 <img ...> 标签，跳过已有 loading 或 decoding 属性的标签
        result = result.replace(
            Regex("""<img((?:(?!\s(?:loading|decoding)=)[^>])*)>"""),
            { match ->
                val attrs = match.groupValues[1]
                "<img$attrs loading=\"lazy\" decoding=\"async\">"
            }
        )

        // 8. 协议相对 URL（//domain.com/path）→ https://domain.com/path
        // 文章中的 iframe/embed/video 等使用 //player.bilibili.com/... 形式的外链，
        // 在 file:// 协议下会被解析为 file://player.bilibili.com/... 导致 ERR_INVALID_URL
        // 必须在渲染时统一改为 https://，由 WebView 自行决定是否交给系统浏览器
        result = result.replace(
            Regex("""((?:src|href|data-src)\s*=\s*["'])//"""),
            "$1https://"
        )

        return result
    }

    /**
     * 提取文章目录（TOC）
     *
     * 解析 Markdown 中的 H1-H6 标题，生成目录 HTML
     * 对应旧版 markdown-it-toc-and-anchor 的 toc 功能
     *
     * @param markdown Markdown 原文
     * @return 目录 HTML（无标题时返回空字符串）
     */
    fun extractToc(markdown: String): String {
        if (markdown.isEmpty()) return ""

        val headings = mutableListOf<TocItem>()
        val headingRegex = Regex("""^(#{1,6})\s+(.+)$""", RegexOption.MULTILINE)
        headingRegex.findAll(markdown).forEach { match ->
            val level = match.groupValues[1].length
            val text = match.groupValues[2].trim()
            // 跳过 HTML 标签内的内容
            if (!text.startsWith("<")) {
                val id = generateHeadingId(text)
                headings.add(TocItem(level, text, id))
            }
        }

        if (headings.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("""<div class="toc">""")
        sb.append("""<div class="toc-title">目录</div>""")
        sb.append("""<ul class="toc-list">""")

        var prevLevel = 0
        for (item in headings) {
            if (prevLevel == 0) {
                sb.append("""<li class="toc-level-${item.level}"><a href="#${item.id}">${escapeHtml(item.text)}</a></li>""")
                prevLevel = item.level
            } else if (item.level > prevLevel) {
                // 层级加深，嵌套 ul
                sb.append("""<ul>""")
                sb.append("""<li class="toc-level-${item.level}"><a href="#${item.id}">${escapeHtml(item.text)}</a></li>""")
                prevLevel = item.level
            } else if (item.level < prevLevel) {
                // 层级变浅，关闭 ul
                sb.append("""</ul>""")
                sb.append("""<li class="toc-level-${item.level}"><a href="#${item.id}">${escapeHtml(item.text)}</a></li>""")
                prevLevel = item.level
            } else {
                sb.append("""<li class="toc-level-${item.level}"><a href="#${item.id}">${escapeHtml(item.text)}</a></li>""")
            }
        }
        // 关闭未关闭的 ul
        if (prevLevel > 0) {
            sb.append("""</ul>""")
        }
        sb.append("""</ul>""")
        sb.append("""</div>""")

        return sb.toString()
    }

    /**
     * 为标题生成 id（用于 TOC 锚点）
     */
    private fun generateHeadingId(text: String): String {
        return text.lowercase()
            .replace(Regex("""[^\w\u4e00-\u9fa5\s-]"""), "") // 保留字母数字中文空格连字符
            .replace(Regex("""\s+"""), "-")
            .takeIf { it.isNotEmpty() } ?: "heading"
    }

    /**
     * HTML 转义
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    /**
     * 提取文章摘要
     *
     * 对应旧版 posts.ts 中使用 <!--more--> 分割摘要的逻辑
     * 返回摘要部分的 Markdown，调用方再自行转 HTML
     *
     * @param content 文章完整内容
     * @return 摘要 Markdown 文本
     */
    fun extractAbstract(content: String): String {
        val moreRegex = Regex("""\n\s*<!--\s*more\s*-->\s*\n""", RegexOption.IGNORE_CASE)
        val match = moreRegex.find(content)
        val raw = if (match != null) {
            content.substring(0, match.range.first)
        } else {
            // 无 more 标记时，取前 200 字符作为摘要
            if (content.length > 200) content.substring(0, 200) + "..." else content
        }
        // 移除图片、链接URL等会在卡片中渲染为大块内容的 Markdown 元素
        // 图片 ![alt](url) → 移除（避免卡片被图片撑高）
        // 保留链接文字部分，去掉URL
        return raw
            .replace(Regex("""!\[[^\]]*\]\([^)]*\)"""), "")
            .replace(Regex("""\[([^\]]*)\]\([^)]*\)"""), "$1")
            .trim()
    }

    /**
     * 去除 HTML 标签，提取纯文本
     * 用于生成文章描述（对应旧版 description 字段）
     *
     * @param html HTML 字符串
     * @param maxLength 最大长度
     * @return 纯文本
     */
    fun toPlainText(html: String, maxLength: Int = 120): String {
        val plainText = html.replace(Regex("<[^>]+>"), "").trim()
        return if (plainText.length > maxLength) {
            plainText.substring(0, maxLength) + "..."
        } else {
            plainText
        }
    }

    /**
     * 计算字数和阅读时间
     * 对应旧版 wordCount / timeCalc 辅助函数
     *
     * @param content 文章内容（Markdown 或纯文本）
     * @return 字数和阅读时间（分钟）
     */
    fun calculateStats(content: String): PostStats {
        val plainText = toPlainText(toHtml(content), Int.MAX_VALUE)
        val wordCount = plainText.replace(Regex("\\s+"), "").length
        // 中文阅读速度约 300 字/分钟
        val readingTime = (wordCount / 300.0).toInt().coerceAtLeast(1)
        return PostStats(
            words = wordCount,
            minutes = readingTime,
            time = readingTime * 60 * 1000L
        )
    }
}

/**
 * 文章统计信息
 * 对应旧版 IPostRenderData.stats
 */
data class PostStats(
    val words: Int,
    val minutes: Int,
    val time: Long
)

/**
 * TOC 目录项
 */
private data class TocItem(
    val level: Int,
    val text: String,
    val id: String
)
