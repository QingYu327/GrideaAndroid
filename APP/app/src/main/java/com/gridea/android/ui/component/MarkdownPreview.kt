package com.gridea.android.ui.component

import android.content.Context
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import coil.disk.DiskCache
import io.noties.markwon.Markwon
import io.noties.markwon.SpanFactory
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.simple.ext.SimpleExtPlugin

/**
 * Markdown 预览组件
 *
 * 对应旧版 Gridea 0.9.3 中 Monaco 编辑器的预览功能
 * 使用 Markwon 渲染 Markdown 为原生 TextView 富文本
 *
 * 支持的 Markdown 扩展（对齐旧版 markdown-it 插件）：
 * - 标题显示（文章标题独立于 Markdown 内容）
 * - 图片渲染（CoilImagesPlugin，支持 file:// 本地路径和网络图片）
 * - HTML 标签（HtmlPlugin）
 * - GFM 表格（TablePlugin）
 * - 任务列表（TaskListPlugin，- [x] / - [ ]）
 * - 高亮标记（SimpleExtPlugin，==text==）
 * - 上标下标（SimpleExtPlugin，^text^ / ~text~）
 * - 自动链接（LinkifyPlugin）
 *
 * 注：代码语法高亮在渲染端（生成静态站点）通过 CSS class + Prism.js 实现，
 * 预览端用 TextView 等宽字体 + 背景色显示代码块
 */
@Composable
fun MarkdownPreview(
    markdown: String,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    val context = LocalContext.current
    val markwon = remember { createMarkwon(context) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // 文章标题（独立于 Markdown 内容显示）
        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        AndroidView(
            factory = { ctx ->
                TextView(ctx).apply {
                    movementMethod = LinkMovementMethod.getInstance()
                    textSize = 15f
                    setLineSpacing(4f, 1.2f)
                }
            },
            update = { textView ->
                markwon.setMarkdown(textView, markdown)
            }
        )
    }
}

/**
 * 创建 Markwon 实例
 *
 * 启用的插件：
 * - CoilImagesPlugin：图片加载（基于 Coil，支持 file:// 本地路径和网络图片）
 * - ImagesPlugin：图片基础支持
 * - HtmlPlugin：HTML 标签支持
 * - TablePlugin：GFM 表格
 * - TaskListPlugin：任务列表（- [x] / - [ ]）
 * - SimpleExtPlugin：高亮（==text==）、上标（^text^）、下标（~text~）
 * - LinkifyPlugin：自动链接识别
 */
private fun createMarkwon(context: Context): Markwon {
    // 配置 Coil 图片加载器，启用磁盘缓存
    val imageLoader = ImageLoader.Builder(context)
        .diskCache(
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("markwon_cache"))
                .maxSizeBytes(50L * 1024 * 1024) // 50MB
                .build()
        )
        .build()

    return Markwon.builder(context)
        .usePlugin(CoilImagesPlugin.create(context, imageLoader))
        .usePlugin(ImagesPlugin.create())
        .usePlugin(HtmlPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(
            SimpleExtPlugin.create()
                .addExtension(2, '=', SpanFactory { _, _ -> BackgroundColorSpan(0xFFFFFF00.toInt()) })  // ==高亮== 黄色背景
                .addExtension(1, '^', SpanFactory { _, _ -> SuperscriptSpan() })  // ^上标^
                .addExtension(1, '~', SpanFactory { _, _ -> SubscriptSpan() })    // ~下标~
        )
        .usePlugin(LinkifyPlugin.create())
        .build()
}
