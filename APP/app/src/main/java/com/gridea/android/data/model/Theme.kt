package com.gridea.android.data.model

import kotlinx.serialization.Serializable

/**
 * 主题配置
 *
 * 对应旧版 Gridea 0.9.3 的 src/interfaces/theme.ts 中的 ITheme
 */
@Serializable
data class Theme(
    val themeName: String = "",
    val postPageSize: Int = 10,
    val archivesPageSize: Int = 10,
    val siteName: String = "",
    val siteDescription: String = "",
    val footerInfo: String = "",
    // 站点作者信息（渲染到 <meta name="author"> 与页眉作者区块）
    val siteAuthor: String = "",
    // 网页图标 URL（渲染到 <link rel="icon">，空则不输出）
    val siteFavicon: String = "",
    // 站点头像 URL（渲染到页眉 <img class="site-avatar">，空则不输出）
    val siteAvatar: String = "",
    val showFeatureImage: Boolean = true,
    val postUrlFormat: String = "SLUG",
    val tagUrlFormat: String = "SLUG",
    // 旧版 Gridea 使用 SHORT_ID：shortid.generate() 生成 8-10 位随机字符（如 "5C7WYjXZg"）
    val dateFormat: String = "YYYY-MM-DD",
    val feedFullText: Boolean = true,
    val feedCount: Int = 10,
    val archivesPath: String = "archives",
    val postPath: String = "post",
    val tagPath: String = "tag",
    // 主题样式自定义
    val primaryColor: String = "#42b983",
    val textColor: String = "#2c3e50",
    val backgroundColor: String = "#ffffff",
    val fontFamily: String = "system",
    val contentWidth: Int = 800,
    val borderRadius: Int = 8
)
