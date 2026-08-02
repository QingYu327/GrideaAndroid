package com.gridea.android.data.model

/**
 * 主题包数据模型
 *
 * 对应 theme.json 的完整解析结果，包含主题元数据、配置项声明、CSS/JS 内容、附加资源列表
 */
data class ThemePack(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val previewImage: String?,
    val tags: List<String>,
    val isBuiltin: Boolean,
    val customConfig: List<ThemeConfigItem>,
    val css: String,
    val js: String?,
    val configValues: Map<String, Any>,
    /**
     * 主题包声明的附加资源列表（来自 theme.json 的 assets 数组）。
     *
     * 渲染时由 SiteRenderer 复制到输出目录，并在 HTML 中注入对应
     * `<link>` / `<script>` 标签。
     */
    val assets: List<ThemeAsset> = emptyList(),
    /**
     * 主题包所在目录的绝对路径（用户主题为 filesDir/themes/{id}，内置主题为 null）。
     *
     * 用于 SiteRenderer 复制 assets 资源文件到输出目录。内置主题的 assets
     * 通过 assets:// 协议访问，需要单独处理，故保留 null。
     */
    val sourceDir: String? = null,
    // 主题包是否自带 templates/*.peb 模板目录。true 时优先从该目录加载模板。
    // 由 ThemePackRepository 加载时检测 sourceDir/templates 是否存在。内置主题（sourceDir=null）不支持。
    val hasTemplates: Boolean = false
)

/**
 * 主题配置项声明
 *
 * 对应 theme.json 中 customConfig 数组的每一项
 */
data class ThemeConfigItem(
    val name: String,           // 配置键名，对应 {{name}} 占位符
    val label: String,          // 显示标签
    val group: String,          // 分组名
    val value: Any,             // 默认值
    val type: String,           // input/textarea/select/switch/color/radio/number/slider/code/multiselect/image
    val note: String? = null,   // 提示说明
    val options: List<ConfigOption>? = null,  // 选项列表（select/radio/multiselect 时）
    val min: String? = null,    // number/slider 类型的最小值
    val max: String? = null,    // number/slider 类型的最大值
    val step: String? = null,   // slider 类型的步长
    val placeholder: String? = null,  // input/textarea/code 类型的占位提示
    val language: String? = null  // code 类型的语法高亮语言（css/javascript/json）
)

/**
 * 配置选项
 */
data class ConfigOption(
    val label: String,
    val value: String
)

/**
 * 主题资源声明
 *
 * 对应 theme.json 中 assets 数组的每一项。SiteRenderer 渲染时：
 * - 所有类型：把 src 文件从主题目录复制到输出目录（保留相对路径）
 * - css 类型：在 <head> 中注入 <link rel="stylesheet">，位于 styles/main.css 之前
 * - js 类型：在 <body> 末尾注入 <script>，位于 scripts/custom.js 之前
 * - font/image/file 类型：仅复制文件，不注入 HTML，供 CSS @font-face / url() / fetch 引用
 */
data class ThemeAsset(
    val type: String,           // css / js / font / image / file
    val src: String,            // 主题包内相对路径，如 "fonts/Mona.woff2"
    val defer_: Boolean = false, // 仅 js 类型：注入 <script defer>
    val async_: Boolean = false  // 仅 js 类型：注入 <script async>
)

