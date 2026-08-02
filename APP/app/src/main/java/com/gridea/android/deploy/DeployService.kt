package com.gridea.android.deploy

import com.gridea.android.data.repository.SettingRepository
import com.gridea.android.data.repository.SiteOutputRepository
import com.gridea.android.renderer.SiteRenderer
import com.gridea.android.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 部署服务（Application 级单例）
 *
 * 将部署逻辑从 ViewModel 迁移到 Application 作用域，解决切换页面导致部署中断的问题。
 *
 * 核心设计：
 * - 持有独立的 [SupervisorJob] + [Dispatchers.IO] 协程作用域，不绑定任何 ViewModel 生命周期
 * - 部署状态通过 [StateFlow] 暴露，任何页面的 UI 都可观察
 * - 部署进度通过 [onProgress] 回调实时更新 [deployProgress] 状态流
 * - 部署完成后自动重置状态，UI 通过观察 [deployResult] 获取结果
 *
 * 使用方式：
 * - ViewModel 注入 DeployService，调用 [publish] 启动部署
 * - UI 观察 [isDeploying] / [deployProgress] / [deployResult] 状态流
 */
@Singleton
class DeployService @Inject constructor(
    private val siteRenderer: SiteRenderer,
    private val deployManager: DeployManager,
    private val settingRepository: SettingRepository,
    private val siteOutputRepository: SiteOutputRepository
) {
    /** 独立协程作用域：SupervisorJob 确保子协程失败不会取消整个作用域 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 部署中状态：切页不重置，全局可见 */
    private val _isDeploying = MutableStateFlow(false)
    val isDeploying: StateFlow<Boolean> = _isDeploying.asStateFlow()

    /** 部署进度：实时更新当前/总数/文件名 */
    private val _deployProgress = MutableStateFlow<DeployProgress?>(null)
    val deployProgress: StateFlow<DeployProgress?> = _deployProgress.asStateFlow()

    /** 部署结果：完成后写入，UI 显示后可调 [clearDeployResult] 清空 */
    private val _deployResult = MutableStateFlow<DeployResult?>(null)
    val deployResult: StateFlow<DeployResult?> = _deployResult.asStateFlow()

    /**
     * 发布站点（后台运行，切页不中断）
     *
     * 流程：读取最新配置 → 渲染静态站点 → 上传到部署平台 → 写入历史记录
     * 进度通过 [deployProgress] 实时暴露，结果通过 [deployResult] 暴露
     *
     * @param onProgress 可选的进度回调（除 StateFlow 外的额外通知渠道，如灵动岛）
     * @param onComplete 可选的完成回调（成功/失败均触发）
     */
    fun publish(
        onProgress: ((DeployProgress) -> Unit)? = null,
        onComplete: ((DeployResult) -> Unit)? = null
    ) {
        if (_isDeploying.value) return
        AppLogger.i("Deploy", "DeployService 开始发布站点（后台运行）")
        _isDeploying.value = true
        _deployResult.value = null
        _deployProgress.value = null

        serviceScope.launch {
            try {
                // 读取最新配置，确保部署用的是用户当前设置
                val setting = settingRepository.getSetting().first()
                // 1. 渲染静态站点
                val renderResult = siteRenderer.renderAll()
                AppLogger.i("Deploy", "渲染完成，开始上传")
                val buildDir = File(renderResult.outputDir)

                // 2. 部署到远程平台
                val result = deployManager.publish(setting, buildDir) { progress ->
                    _deployProgress.value = progress
                    onProgress?.invoke(progress)
                }

                _deployResult.value = result
                onComplete?.invoke(result)
            } catch (e: Exception) {
                AppLogger.reportUserError("Deploy", "部署失败", e)
                val failResult = DeployResult(
                    success = false,
                    message = "部署失败：${e.message ?: "未知错误"}"
                )
                _deployResult.value = failResult
                onComplete?.invoke(failResult)
            } finally {
                _isDeploying.value = false
                _deployProgress.value = null
            }
        }
    }

    /** 清空部署结果（UI 显示后调用） */
    fun clearDeployResult() {
        _deployResult.value = null
    }

    /** 清空部署进度（部署结束后清理） */
    fun clearDeployProgress() {
        _deployProgress.value = null
    }

    /**
     * 一键部署（后台运行，切页不中断）
     *
     * 合并原"生成静态站点 → 检测连接 → 发布站点"三步为一个流程：
     * 1. 检查存储权限（未授权不允许渲染输出到内置目录）
     * 2. 渲染站点并复制到公共目录 → 通过 [onRenderComplete] 回调通知生成结果
     * 3. 检测连接 → 通过 [onDetectComplete] 回调通知检测结果；失败则中止
     * 4. 部署到远程平台 → 通过 [onProgress] / [onComplete] 回调通知进度和结果
     *
     * @param onRenderComplete 渲染完成回调，参数为生成结果消息文本
     * @param onDetectComplete 连通性检测完成回调
     * @param onProgress 部署进度回调
     * @param onComplete 部署最终结果回调（成功/失败均触发）
     */
    fun oneClickDeploy(
        onRenderComplete: ((String) -> Unit)? = null,
        onDetectComplete: ((DetectResult) -> Unit)? = null,
        onProgress: ((DeployProgress) -> Unit)? = null,
        onComplete: ((DeployResult) -> Unit)? = null
    ) {
        if (_isDeploying.value) return

        // 1. 检查存储权限：必须取得储存权限才能渲染输出
        if (!siteOutputRepository.hasPermission.value) {
            onComplete?.invoke(
                DeployResult(success = false, message = "请先授权存储权限后再一键部署")
            )
            return
        }

        AppLogger.i("Deploy", "一键部署开始（生成 → 检测 → 部署）")
        _isDeploying.value = true
        _deployResult.value = null
        _deployProgress.value = null

        serviceScope.launch {
            try {
                val setting = settingRepository.getSetting().first()

                // 2. 渲染静态站点
                val renderResult = siteRenderer.renderAll()
                AppLogger.i("Deploy", "一键部署：渲染完成 ${renderResult.postCount} 篇文章")
                val sourceDir = File(renderResult.outputDir)
                val fileCount = siteOutputRepository.copyToPublicOutput(sourceDir)
                val renderMsg = "生成成功！${renderResult.postCount} 篇文章，${renderResult.tagCount} 个标签\n" +
                    "已输出到公共目录（$fileCount 个文件）\n${siteOutputRepository.getOutputDisplayPath()}"
                onRenderComplete?.invoke(renderMsg)

                // 3. 检测连接
                val detectRes = deployManager.detect(setting)
                onDetectComplete?.invoke(detectRes)
                if (!detectRes.success) {
                    val failResult = DeployResult(
                        success = false,
                        message = "连通失败：${detectRes.message}，已停止部署"
                    )
                    _deployResult.value = failResult
                    onComplete?.invoke(failResult)
                    return@launch
                }
                AppLogger.i("Deploy", "一键部署：连通正常，开始上传")

                // 4. 部署到远程平台
                val result = deployManager.publish(setting, sourceDir) { progress ->
                    _deployProgress.value = progress
                    onProgress?.invoke(progress)
                }
                _deployResult.value = result
                onComplete?.invoke(result)
            } catch (e: Exception) {
                AppLogger.reportUserError("Deploy", "一键部署失败", e)
                val failResult = DeployResult(
                    success = false,
                    message = "一键部署失败：${e.message ?: "未知错误"}"
                )
                _deployResult.value = failResult
                onComplete?.invoke(failResult)
            } finally {
                _isDeploying.value = false
                _deployProgress.value = null
            }
        }
    }
}
