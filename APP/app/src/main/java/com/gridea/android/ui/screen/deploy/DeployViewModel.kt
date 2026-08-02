package com.gridea.android.ui.screen.deploy

import androidx.lifecycle.ViewModel
import com.gridea.android.deploy.DeployProgress
import com.gridea.android.deploy.DeployResult
import com.gridea.android.deploy.DeployService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 部署状态 ViewModel（Activity 级）
 *
 * 用于在 GrideaApp 顶层观察 [DeployService] 的后台部署状态，
 * 将进度/结果同步到灵动岛通知系统。
 *
 * 注意：此 ViewModel 绑定到 Activity 的 ViewModelStore（通过 hiltViewModel() 在
 * GrideaApp 中获取），生命周期长于 NavBackStackEntry 级的 SettingViewModel，
 * 确保切页时部署状态观察不中断。
 */
@HiltViewModel
class DeployViewModel @Inject constructor(
    private val deployService: DeployService
) : ViewModel() {
    val isDeploying: StateFlow<Boolean> = deployService.isDeploying
    val deployProgress: StateFlow<DeployProgress?> = deployService.deployProgress
    val deployResult: StateFlow<DeployResult?> = deployService.deployResult

    fun clearDeployResult() = deployService.clearDeployResult()
}
