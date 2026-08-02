package com.gridea.android.renderer

import com.gridea.android.data.model.CommentSetting
import com.gridea.android.data.model.Setting
import com.gridea.android.data.model.Theme
import com.gridea.android.data.repository.TagWithCount

/**
 * 渲染数据模型
 *
 * 对应旧版 Gridea 0.9.3 的 src/server/interfaces/renderer.ts
 * 封装传递给模板的各类数据
 */

/**
 * 文章渲染数据
 * 对应旧版 IPostRenderData
 */
data class PostRenderData(
    val fileName: String,
    val title: String,
    val content: String,          // 已渲染的 HTML
    val abstract: String,         // 已渲染的 HTML 摘要
    val description: String,      // 纯文本描述
    val date: String,
    val tags: List<TagRenderData>,
    val feature: String = "",     // 封面图 URL
    val link: String,             // 文章链接
    val hideInList: Boolean = false,
    val isTop: Boolean = false,
    val stats: PostStats,
    val toc: String = "",         // 文章目录 HTML（无标题时为空）
    val prevPost: PostRenderData? = null,
    val nextPost: PostRenderData? = null
)

/**
 * 标签渲染数据
 * 对应旧版 ITagRenderData
 */
data class TagRenderData(
    val name: String,
    val slug: String,
    val link: String,
    val count: Int = 0
)

/**
 * 菜单渲染数据
 * 对应旧版 IMenu
 */
data class MenuRenderData(
    val name: String,
    val link: String,
    val openType: String = "Internal"  // Internal 或 External
)

/**
 * 站点渲染数据
 * 对应旧版 siteData
 */
data class SiteRenderData(
    val siteName: String,
    val siteDescription: String,
    val footerInfo: String,
    val domain: String,
    val siteAuthor: String = "",
    val siteFavicon: String = "",
    val siteAvatar: String = "",
    val posts: List<PostRenderData>,
    val tags: List<TagRenderData>,
    val themeConfig: Theme,
    val commentSetting: CommentSetting? = null,
    val menus: List<MenuRenderData> = emptyList(),
    /**
     * 主题包（ThemePack）的用户配置值。
     * SiteRenderer 会从这里取出 cardStyle / showHero / showToc 等
     * 开关型配置，写入 <html data-...> 属性，
     * 供主题 CSS 通过 :root[data-card="shadow"] 等选择器分支样式。
     */
    val themePackConfig: Map<String, String> = emptyMap(),
    /**
     * 主题包声明的附加资源（assets）。
     * - type=css: 在 <head> 中 styles/main.css 之前注入 <link>
     * - type=js:  在 <body> 末尾 scripts/custom.js 之前注入 <script>
     * - 其他类型不注入 HTML（仅 SiteRenderer 复制文件到输出目录）
     */
    val themeAssets: List<com.gridea.android.data.model.ThemeAsset> = emptyList()
)

/**
 * 页面渲染数据
 *
 * 对应 Hexo 的 page 变量，描述当前页面的类型和元信息。
 * 由 SiteRenderer 根据页面类型（首页/文章/归档/标签等）构建后注入 context。
 *
 * @param type 页面类型：home/post/page/archive/category/tag
 * @param lang 页面语言代码，默认 "zh-CN"
 * @param title 页面标题
 * @param toc 目录配置
 * @param comments 是否启用评论
 * @param date 页面日期（文章详情页为文章日期）
 * @param categories 分类列表
 * @param photos 图片列表（用于图廊等）
 */
data class PageRenderData(
    val type: String = "home",
    val lang: String = "zh-CN",
    val title: String = "",
    val toc: TocConfig = TocConfig(),
    val comments: Boolean = false,
    val date: String = "",
    val categories: List<String> = emptyList(),
    val photos: List<String> = emptyList()
)

/**
 * 目录配置
 *
 * @param enable 是否启用目录
 * @param number 是否自动编号
 * @param max_depth 最大深度
 */
data class TocConfig(
    val enable: Boolean = false,
    val number: Boolean = false,
    val max_depth: Int = 3
)

/**
 * 分页信息
 * 对应旧版 pagination
 */
data class Pagination(
    val prev: String = "",
    val next: String = "",
    val current: Int = 1,
    val total: Int = 1
)
