package com.gridea.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.gridea.android.MainActivity
import com.gridea.android.R
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 文章统计桌面小部件
 *
 * 显示文章总数、已发布、草稿、标签数四项统计。
 * 点击小部件打开应用主页。
 *
 * 数据获取：通过 EntryPointAccessors 从 Hilt 获取 PostRepository，
 * 用 goAsync() 避免阻塞 BroadcastReceiver（onReceive 有 10 秒限制）。
 */
class StatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return

        val pendingResult = goAsync()
        // 局部作用域：协程完成后 finish() 释放 BroadcastReceiver
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    WidgetEntryPoint::class.java
                )
                val posts = entryPoint.postRepository().getAllPostsSync()

                val total = posts.size
                val published = posts.count { it.data.published }
                val draft = posts.count { !it.data.published }
                val tagCount = posts
                    .flatMap { it.data.tags }
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .size

                appWidgetIds.forEach { widgetId ->
                    val views = RemoteViews(context.packageName, R.layout.widget_stats).apply {
                        setTextViewText(R.id.widget_stat_total_value, total.toString())
                        setTextViewText(R.id.widget_stat_published_value, published.toString())
                        setTextViewText(R.id.widget_stat_draft_value, draft.toString())
                        setTextViewText(R.id.widget_stat_tags_value, tagCount.toString())
                    }

                    // 点击打开主页
                    val intent = Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        REQUEST_OPEN_APP,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    views.setOnClickPendingIntent(R.id.widget_stats_root, pendingIntent)

                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } catch (_: Exception) {
                // 查询失败时静默处理，下次更新会重试
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_OPEN_APP = 2001
    }
}
