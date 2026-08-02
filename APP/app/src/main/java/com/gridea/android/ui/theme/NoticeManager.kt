package com.gridea.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * 通知类型：决定灵动岛通知的图标与配色
 *
 * - [Success]：默认类型，主题色 + CheckCircle 图标
 * - [Error]：错误类型，红色 + Warning 图标，用于提示用户软件内部错误并引导查看日志反馈
 */
enum class NoticeType {
    Success,
    Error
}

/**
 * 通知数据（含唯一 id，用于区分连续相同消息）
 *
 * @param persistent 是否常驻（不自动消失）。用于部署进度等需要持续显示的场景。
 *                   常驻通知不会被 [rememberNoticeManager] 的自动 delay 清除，
 *                   需手动调用 [NoticeManager.clear] 或显示新通知替换。
 * @param type 通知类型，决定图标与配色（默认 Success）
 */
data class NoticeData(
    val text: String,
    val id: Long,
    val persistent: Boolean = false,
    val type: NoticeType = NoticeType.Success
)

/**
 * 全局灵动岛通知管理器
 *
 * 提供全局通知能力：任何页面都可调用 [showNotice] 显示顶部灵动岛风格悬浮通知
 *
 * 设计要点：
 * - 每次调用 [showNotice] 都生成新的 id（即使文本相同），确保 LaunchedEffect 能重置倒计时
 * - 通知内容变化时 visible 保持 true，AnimatedVisibility 不会重新播进入动画，文字直接更新
 *   实现"强制刷新换新通知"的效果，无需等待上一次动画完成
 * - 支持常驻通知（persistent=true）：用于部署进度等持续场景，不自动消失
 * - 支持 [NoticeType.Error]：错误通知使用红色配色，用于软件内部错误提醒
 *
 * 使用方式：
 * 1. 在 GrideaApp 顶层用 [rememberNoticeManager] 创建实例
 * 2. 用 CompositionLocalProvider 提供 LocalNoticeManager
 * 3. 任何子组件通过 LocalNoticeManager.current.showNotice("消息") 触发通知
 */
class NoticeManager {
    /** 当前通知（含唯一 id），null 表示无通知 */
    private val _notice = mutableStateOf<NoticeData?>(null)
    val notice: State<NoticeData?> = _notice

    /** 自增计数器，每次 showNotice 递增，生成唯一 id */
    private var counter = 0L

    /**
     * 显示通知：每次调用生成新 id，即使文本相同也会重置倒计时
     * 若当前已有通知显示，直接替换文本（不重新播进入动画）
     *
     * @param persistent 是否常驻（不自动消失），默认 false
     * @param type 通知类型，决定图标与配色（默认 Success）
     */
    fun showNotice(text: String, persistent: Boolean = false, type: NoticeType = NoticeType.Success) {
        _notice.value = NoticeData(text, ++counter, persistent, type)
    }

    /** 清除通知（触发退出动画） */
    fun clear() {
        _notice.value = null
    }
}

/** CompositionLocal：让任何子组件都能访问全局通知管理器 */
val LocalNoticeManager = androidx.compose.runtime.staticCompositionLocalOf<NoticeManager> {
    error("LocalNoticeManager not provided")
}

/**
 * 在 Composable 中记住 NoticeManager 实例，并自动延时清除
 *
 * 用 notice.id 作为 LaunchedEffect 的 key：每次 showNotice 都生成新 id，
 * 即使文本相同也会重新触发 effect 重置倒计时，避免连续相同消息时通知提前消失
 *
 * 常驻通知（persistent=true）不会自动清除，需手动调用 clear()。
 */
@Composable
fun rememberNoticeManager(): NoticeManager {
    val manager = remember { NoticeManager() }
    val notice = manager.notice.value
    LaunchedEffect(notice?.id) {
        if (notice != null && !notice.persistent) {
            delay(2200)
            manager.clear()
        }
    }
    return manager
}
