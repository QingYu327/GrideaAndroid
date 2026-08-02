package com.gridea.android.util

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全局错误事件总线
 *
 * 串联"软件内部错误 → 灵动岛通知提醒 → 用户查看日志反馈"的完整流程：
 * 1. ViewModel / Repository 的 catch 块调用 [report] 发送错误
 * 2. [AppLogger.reportUserError] 便捷方法同时写日志 + 发送错误事件
 * 3. GrideaAppContent 订阅 [events]，通过 NoticeManager 显示红色错误通知
 * 4. 通知文案包含错误信息 + "请查看日志管理进行反馈"的引导
 *
 * 设计要点：
 * - 基于 SharedFlow，无需绑定生命周期，全局可写
 * - 使用 DROP_OLDEST 策略：避免短时间内大量错误堆积阻塞
 * - replay=0：新订阅者不会收到历史错误，只收到订阅后的新错误
 */
object ErrorBus {

    private val _events = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** 错误事件流，供 GrideaAppContent 收集并显示灵动岛通知 */
    val events: SharedFlow<String> = _events.asSharedFlow()

    /**
     * 发送一个错误事件到全局通知总线
     *
     * @param message 用户可见的错误消息（已简明描述，不含堆栈）
     */
    fun report(message: String) {
        _events.tryEmit(message)
    }
}
