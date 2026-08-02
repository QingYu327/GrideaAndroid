package com.gridea.android.widget

import com.gridea.android.data.repository.PostRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 小部件依赖注入入口
 *
 * AppWidgetProvider 是 BroadcastReceiver，无法用 @AndroidEntryPoint。
 * 通过 EntryPointAccessors 从 Application 获取 Repository。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun postRepository(): PostRepository
}
