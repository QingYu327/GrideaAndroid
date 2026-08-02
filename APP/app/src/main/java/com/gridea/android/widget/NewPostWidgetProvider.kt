package com.gridea.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.gridea.android.MainActivity
import com.gridea.android.R

/**
 * 新建文章桌面小部件
 *
 * 点击后启动 MainActivity 并传递 shortcut_action="new_post"，
 * 与应用快捷方式共用同一入口逻辑（MainActivity 读取 extra 并跳转编辑器）。
 */
class NewPostWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_new_post)

            // 点击启动 MainActivity，携带 new_post 动作
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("shortcut_action", "new_post")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_NEW_POST,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_new_post_root, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    companion object {
        private const val REQUEST_NEW_POST = 1001
    }
}
