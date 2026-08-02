package com.gridea.android.data.model

import kotlinx.serialization.Serializable

/**
 * 评论设置
 *
 * 对应旧版 Gridea 0.9.3 的 src/interfaces/setting.ts 中的 ICommentSetting
 * 支持的评论平台：Gitalk、Giscus、Disqus、Valine、Twikoo、Waline
 */
@Serializable
data class CommentSetting(
    val commentPlatform: String = CommentPlatform.GITALK,
    val showComment: Boolean = false,
    val gitalkSetting: GitalkSetting = GitalkSetting(),
    val giscusSetting: GiscusSetting = GiscusSetting(),
    val disqusSetting: DisqusSetting = DisqusSetting(),
    val valineSetting: ValineSetting = ValineSetting(),
    val twikooSetting: TwikooSetting = TwikooSetting(),
    val walineSetting: WalineSetting = WalineSetting()
)

/**
 * Gitalk 评论配置（基于 GitHub Issue）
 */
@Serializable
data class GitalkSetting(
    val clientId: String = "",
    val clientSecret: String = "",
    val repository: String = "",
    val owner: String = ""
)

/**
 * Giscus 评论配置（基于 GitHub Discussions）
 */
@Serializable
data class GiscusSetting(
    val repo: String = "",
    val repoId: String = "",
    val category: String = "",
    val categoryId: String = "",
    val mapping: String = "pathname",
    val theme: String = "light"
)

/**
 * DisqusJS 评论配置
 */
@Serializable
data class DisqusSetting(
    val api: String = "https://disqus.skk.moe/disqus/",
    val apikey: String = "",
    val shortname: String = ""
)

/**
 * Valine 评论配置（基于 LeanCloud）
 */
@Serializable
data class ValineSetting(
    val appId: String = "",
    val appKey: String = ""
)

/**
 * Twikoo 评论配置（基于腾讯云开发）
 */
@Serializable
data class TwikooSetting(
    val envId: String = ""
)

/**
 * Waline 评论配置（基于 LeanCloud/MongoDB，Valine 的增强版）
 */
@Serializable
data class WalineSetting(
    val serverURL: String = ""
)

object CommentPlatform {
    const val GITALK = "gitalk"
    const val GISCUS = "giscus"
    const val DISQUS = "disqus"
    const val VALINE = "valine"
    const val TWIKOO = "twikoo"
    const val WALINE = "waline"
}
