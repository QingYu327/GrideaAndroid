package com.gridea.android.data.model

import kotlinx.serialization.Serializable

/**
 * 站点设置
 *
 * 对应旧版 Gridea 0.9.3 的 src/interfaces/setting.ts 中的 ISetting
 * 包含部署相关的所有配置
 */
@Serializable
data class Setting(
    val platform: String = DeployPlatform.GITHUB,
    val domain: String = "",
    // GitHub 配置
    val repository: String = "",
    val branch: String = "",
    val username: String = "",
    val email: String = "",
    val token: String = "",
    val cname: String = "",
    // SFTP 配置（独立字段，与 GitHub 隔离，避免切换平台时输入值串台）
    val port: String = "",
    val server: String = "",
    val sftpUsername: String = "",
    val password: String = "",
    val privateKey: String = "",
    val remotePath: String = "",
    // Netlify 配置
    val netlifyAccessToken: String = "",
    val netlifySiteId: String = "",
    // Vercel 配置
    val vercelAccessToken: String = "",
    val vercelProjectId: String = "",
    // Gitee 配置（独立字段，与 GitHub 隔离，避免切换平台时输入值串台）
    val giteeRepository: String = "",
    val giteeBranch: String = "",
    val giteeUsername: String = "",
    val giteeToken: String = ""
)

/**
 * 部署平台枚举
 */
object DeployPlatform {
    const val GITHUB = "github"
    const val SFTP = "sftp"
    const val NETLIFY = "netlify"
    const val VERCEL = "vercel"
    const val GITEE = "gitee"
}
