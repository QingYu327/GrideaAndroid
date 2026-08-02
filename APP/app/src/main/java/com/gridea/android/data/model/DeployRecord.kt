package com.gridea.android.data.model

/**
 * 部署历史记录
 *
 * 记录每次部署的关键信息，用于历史查看和回滚参考。
 * 文件清单只保存相对路径（相对于 buildDir 的路径），不保存文件内容。
 *
 * @param id 时间戳作为 ID
 * @param timestamp 部署时间
 * @param platform 平台名（GitHub/Gitee/SFTP/Netlify/Vercel）
 * @param success 是否成功
 * @param fileCount 文件数
 * @param message 结果消息
 * @param url 访问地址
 * @param fileManifest 文件清单（相对路径列表，用于回滚参考）
 */
data class DeployRecord(
    val id: Long,
    val timestamp: Long,
    val platform: String,
    val success: Boolean,
    val fileCount: Int,
    val message: String,
    val url: String? = null,
    val fileManifest: List<String> = emptyList()
)
