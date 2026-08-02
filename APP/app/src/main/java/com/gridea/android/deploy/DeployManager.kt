package com.gridea.android.deploy

import com.gridea.android.data.model.DeployPlatform
import com.gridea.android.data.model.DeployRecord
import com.gridea.android.data.model.Setting
import com.gridea.android.data.repository.DeployHistoryRepository
import com.gridea.android.util.AppLogger
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 部署管理器
 *
 * 对应旧版 Gridea 0.9.3 的 src/server/events/deploy.ts 中的路由逻辑
 * 根据 setting.platform 选择对应的部署器
 *
 * 同时负责：
 * - 部署成功后保存历史记录（含文件清单，用于回滚参考）
 * - 暴露部署历史流和清空接口
 * - 提供简化的回滚接口（仅返回上次成功部署清单与提示信息，实际删除需用户在平台管理页手动操作）
 */
@Singleton
class DeployManager @Inject constructor(
    private val sftpDeployer: SftpDeployer,
    private val netlifyDeployer: NetlifyDeployer,
    private val githubDeployer: GithubDeployer,
    private val vercelDeployer: VercelDeployer,
    private val giteeDeployer: GiteeDeployer,
    private val deployHistoryRepository: DeployHistoryRepository
) {

    /**
     * 根据平台获取部署器
     */
    fun getDeployer(platform: String): Deployer {
        return when (platform) {
            DeployPlatform.GITHUB -> githubDeployer
            DeployPlatform.SFTP -> sftpDeployer
            DeployPlatform.NETLIFY -> netlifyDeployer
            DeployPlatform.VERCEL -> vercelDeployer
            DeployPlatform.GITEE -> giteeDeployer
            else -> githubDeployer
        }
    }

    /**
     * 获取平台显示名
     */
    private fun platformDisplayName(platform: String): String = when (platform) {
        DeployPlatform.GITHUB -> "GitHub"
        DeployPlatform.SFTP -> "SFTP"
        DeployPlatform.NETLIFY -> "Netlify"
        DeployPlatform.VERCEL -> "Vercel"
        DeployPlatform.GITEE -> "Gitee"
        else -> platform
    }

    /**
     * 连通性检测
     */
    suspend fun detect(setting: Setting): DetectResult {
        val platformName = platformDisplayName(setting.platform)
        AppLogger.i("Deploy", "开始连接测试：$platformName")
        return try {
            val result = getDeployer(setting.platform).detect(setting)
            if (result.success) {
                AppLogger.i("Deploy", "$platformName 连接测试成功：${result.message}")
            } else {
                AppLogger.w("Deploy", "$platformName 连接测试失败：${result.message}")
            }
            result
        } catch (e: Exception) {
            AppLogger.e("Deploy", "$platformName 连接测试异常：${e.message ?: "未知错误"}", e)
            DetectResult(success = false, message = "连接测试异常：${e.message ?: "未知错误"}")
        }
    }

    /**
     * 发布静态站点
     *
     * 部署完成后（无论成功或失败）均会保存一条历史记录；
     * 成功记录中会带上文件清单（相对 buildDir 的路径），用于回滚参考。
     */
    suspend fun publish(
        setting: Setting,
        buildDir: java.io.File,
        onProgress: (DeployProgress) -> Unit = {}
    ): DeployResult {
        val platformName = platformDisplayName(setting.platform)
        AppLogger.action("Deploy", "开始部署", "平台：$platformName，构建目录：${buildDir.absolutePath}")
        val now = System.currentTimeMillis()
        return try {
            val result = getDeployer(setting.platform).publish(setting, buildDir) { progress ->
                AppLogger.d("Deploy", "$platformName 部署进度：${progress.current}/${progress.total} - ${progress.fileName}")
                onProgress(progress)
            }
            if (result.success) {
                AppLogger.action("Deploy", "部署成功", "$platformName - ${result.message}")
            } else {
                AppLogger.e("Deploy", "$platformName 部署失败：${result.message}")
            }
            // 保存部署历史记录
            val manifest = if (result.success) collectFileManifest(buildDir) else emptyList()
            val record = DeployRecord(
                id = now,
                timestamp = now,
                platform = platformName,
                success = result.success,
                fileCount = result.fileCount,
                message = result.message,
                url = result.url,
                fileManifest = manifest
            )
            runCatching {
                deployHistoryRepository.saveRecord(record)
            }.onFailure { e ->
                AppLogger.e("Deploy", "保存部署历史失败：${e.message}", e)
            }
            result
        } catch (e: Exception) {
            AppLogger.e("Deploy", "$platformName 部署异常：${e.message ?: "未知错误"}", e)
            // 异常也保存一条失败记录
            val record = DeployRecord(
                id = now,
                timestamp = now,
                platform = platformName,
                success = false,
                fileCount = 0,
                message = "部署异常：${e.message ?: "未知错误"}",
                url = null,
                fileManifest = emptyList()
            )
            runCatching {
                deployHistoryRepository.saveRecord(record)
            }.onFailure { ex ->
                AppLogger.e("Deploy", "保存部署历史（异常）失败：${ex.message}", ex)
            }
            DeployResult(success = false, message = "部署异常：${e.message ?: "未知错误"}")
        }
    }

    /**
     * 暴露部署历史记录流（按时间倒序，最新在前）
     */
    fun getDeployHistory(): Flow<List<DeployRecord>> {
        return deployHistoryRepository.getHistory()
    }

    /**
     * 清空部署历史
     */
    suspend fun clearDeployHistory() {
        deployHistoryRepository.clearHistory()
    }

    /**
     * 按 ID 批量删除部署历史记录
     *
     * @param ids 要删除的记录 ID 集合
     */
    suspend fun deleteDeployRecords(ids: Set<Long>) {
        deployHistoryRepository.deleteRecordsByIds(ids)
    }

    /**
     * 回滚上次部署（简化实现）。
     *
     * 由于 Deployer 接口未提供统一的删除方法，且不同平台对回滚的支持差异较大
     * （Git 类平台可通过 API 删除文件；SFTP 可删除远程文件；Netlify/Vercel 没有简单回滚方式），
     * 当前实现仅返回上次成功部署的文件清单和提示信息，由用户在对应平台管理页面手动处理。
     *
     * @return 包含提示信息和文件数的 DeployResult（success=false 表示未真正执行回滚）
     */
    suspend fun rollbackLastDeploy(
        setting: Setting,
        onProgress: (DeployProgress) -> Unit = {}
    ): DeployResult {
        val last = deployHistoryRepository.getLastSuccessRecord()
            ?: return DeployResult(
                success = false,
                message = "没有可回滚的成功部署记录"
            )
        val platformName = platformDisplayName(setting.platform)
        val manifest = deployHistoryRepository.getLastFileManifest()
        val displayPlatform = last.platform.ifBlank { platformName }
        return DeployResult(
            success = false,
            message = "回滚功能仅支持 Git 类平台（GitHub/Gitee）。上次部署平台：$displayPlatform，" +
                "共 ${manifest.size} 个文件。如需回滚，请在对应平台管理页面手动删除以下文件。",
            fileCount = manifest.size
        )
    }

    /**
     * 遍历 buildDir 收集所有文件的相对路径（相对于 buildDir），用于历史记录与回滚参考。
     * 仅保留文件，跳过空目录。
     */
    private fun collectFileManifest(buildDir: File): List<String> {
        if (!buildDir.exists() || !buildDir.isDirectory) return emptyList()
        val result = mutableListOf<String>()
        buildDir.walkTopDown().forEach { f ->
            if (f.isFile) {
                val rel = f.relativeTo(buildDir).path.replace(File.separatorChar, '/')
                if (rel.isNotEmpty()) result.add(rel)
            }
        }
        return result
    }
}
