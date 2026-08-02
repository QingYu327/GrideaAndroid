package com.gridea.android.deploy

import com.gridea.android.data.model.Setting
import java.io.File

/**
 * 部署器接口
 *
 * 对应旧版 Gridea 0.9.3 的部署类（Deploy / SftpDeploy / NetlifyApi）
 * 所有平台的部署器均实现此接口
 *
 * Setting 作为方法参数传入（而非构造注入），因为用户会动态修改配置
 */
interface Deployer {

    /**
     * 连通性检测
     * 对应旧版 remoteDetect()
     */
    suspend fun detect(setting: Setting): DetectResult

    /**
     * 发布静态站点
     * 对应旧版 publish()
     *
     * @param setting 当前部署配置
     * @param buildDir 渲染输出目录（cacheDir/gridea_build）
     * @param onProgress 进度回调
     */
    suspend fun publish(
        setting: Setting,
        buildDir: File,
        onProgress: (DeployProgress) -> Unit = {}
    ): DeployResult
}

/**
 * 连通性检测结果
 */
data class DetectResult(
    val success: Boolean,
    val message: String
)

/**
 * 部署结果
 */
data class DeployResult(
    val success: Boolean,
    val message: String,
    val url: String? = null,
    val fileCount: Int = 0
)

/**
 * 部署进度
 */
data class DeployProgress(
    val current: Int,
    val total: Int,
    val fileName: String
)
