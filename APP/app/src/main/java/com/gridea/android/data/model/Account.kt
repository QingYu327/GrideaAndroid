package com.gridea.android.data.model

import kotlinx.serialization.Serializable

/**
 * GitHub 账户信息
 *
 * 登录成功后保存的账户数据，对应旧版 Gridea 0.9.3 中通过 GitHub OAuth 获取的用户信息
 * 移动端使用 Device Flow（无需 Client Secret）
 *
 * 字段对应 GitHub `/user` API 返回内容（scope = "repo user"）：
 * - login: 用户名
 * - name: 显示名称（用户未设置时 GitHub 返回 null，此处回退为 login）
 * - avatar_url: 头像 URL
 * - html_url: GitHub 主页地址
 * - bio: 个人简介
 * - company: 公司
 * - blog: 个人网站
 * - location: 所在地
 * - email: 邮箱（用户未公开邮箱时为空）
 * - public_repos: 公开仓库数
 * - total_private_repos: 私有仓库数（需要 repo scope）
 * - followers: 粉丝数
 * - following: 关注数
 * - created_at: 账号注册时间（ISO 8601 字符串）
 */
@Serializable
data class Account(
    val accessToken: String = "",
    val login: String = "",
    val name: String = "",
    val avatarUrl: String = "",
    val htmlUrl: String = "",
    val bio: String = "",
    val company: String = "",
    val blog: String = "",
    val location: String = "",
    val email: String = "",
    val publicRepos: Int = 0,
    val totalPrivateRepos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    val createdAt: String = ""
) {
    val isLoggedIn: Boolean get() = accessToken.isNotEmpty()

    /** 仓库总数（公开 + 私有） */
    val totalRepos: Int get() = publicRepos + totalPrivateRepos
}
