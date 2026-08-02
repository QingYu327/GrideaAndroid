package com.gridea.android.renderer

import com.gridea.android.util.AppLogger
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.error.PebbleException
import io.pebbletemplates.pebble.extension.AbstractExtension
import io.pebbletemplates.pebble.extension.Filter
import io.pebbletemplates.pebble.loader.FileLoader
import io.pebbletemplates.pebble.template.EvaluationContext
import io.pebbletemplates.pebble.template.PebbleTemplate
import java.io.File
import java.io.StringWriter

/**
 * Pebble 模板引擎封装
 *
 * 从主题包的 templates 目录加载 .peb 模板并渲染。
 * 每次构建站点时创建新实例（因为主题可能切换）。
 */
class PebbleTemplateEngine private constructor(
    private val engine: PebbleEngine
) {
    companion object {
        /**
         * 从主题模板目录创建引擎实例
         * @param templatesDir 主题包的 templates 目录（如 filesDir/themes/{id}/templates）
         * @return 引擎实例，若目录不存在返回 null
         */
        fun create(templatesDir: File): PebbleTemplateEngine? {
            if (!templatesDir.exists() || !templatesDir.isDirectory) return null

            // Pebble 4.x 的 FileLoader 构造函数要求传入 prefix（模板目录绝对路径）
            val loader = FileLoader(templatesDir.absolutePath)
            loader.suffix = ".peb"

            val engine = PebbleEngine.Builder()
                .loader(loader)
                .cacheActive(false) // 模板量少，不缓存，避免主题切换后旧缓存残留
                .autoEscaping(true) // 自动 HTML 转义（模板用 | raw 输出可信 HTML）
                .newLineTrimming(false) // 保留模板换行，避免 HTML 结构错乱
                .extension(HttpsUpgradeExtension())
                .build()

            return PebbleTemplateEngine(engine)
        }
    }

    /**
     * 渲染指定模板
     * @param templateName 模板名（不带 .peb 后缀），如 "index", "post", "archives"
     * @param context 模板变量 Map
     * @return 渲染后的 HTML 字符串
     */
    fun render(templateName: String, context: Map<String, Any>): String {
        return try {
            val template: PebbleTemplate = engine.getTemplate(templateName)
            val writer = StringWriter()
            template.evaluate(writer, context)
            val result = writer.toString()
            AppLogger.d("PebbleEngine", "渲染成功: $templateName (${result.length} 字符)")
            result
        } catch (e: PebbleException) {
            AppLogger.e("PebbleEngine", "渲染失败 ($templateName): ${e.message}", e)
            // 模板语法错误等：返回错误提示 HTML，避免页面空白
            "<!-- Pebble 渲染错误 ($templateName): ${e.message} -->"
        } catch (e: Exception) {
            AppLogger.e("PebbleEngine", "渲染异常 ($templateName): ${e.message}", e)
            "<!-- 渲染异常 ($templateName): ${e.message} -->"
        }
    }
}

/**
 * 注册自定义过滤器的扩展
 * - https_upgrade: http:// → https://
 * - striptags: 去除 HTML 标签（Pebble 4.x 移除了内置的 striptags，需自行实现）
 */
private class HttpsUpgradeExtension : AbstractExtension() {
    override fun getFilters(): Map<String, Filter> {
        return mapOf(
            "https_upgrade" to HttpsUpgradeFilter(),
            "striptags" to StripTagsFilter()
        )
    }
}

/**
 * striptags 过滤器：去除字符串中的 HTML 标签
 */
private class StripTagsFilter : Filter {
    override fun getArgumentNames(): List<String>? = null

    override fun apply(
        input: Any?,
        args: Map<String, Any>?,
        template: PebbleTemplate,
        context: EvaluationContext,
        lineNumber: Int
    ): Any? {
        if (input == null) return null
        return input.toString().replace(Regex("<[^>]*>"), "")
    }
}

/**
 * https_upgrade 过滤器
 * 将 http:// URL 升级为 https://，其他情况原样返回
 */
private class HttpsUpgradeFilter : Filter {
    override fun getArgumentNames(): List<String>? = null

    override fun apply(
        input: Any?,
        args: Map<String, Any>?,
        template: PebbleTemplate,
        context: EvaluationContext,
        lineNumber: Int
    ): Any? {
        if (input == null) return null
        val url = input.toString()
        return if (url.startsWith("http://")) {
            "https://" + url.substring(7)
        } else {
            url
        }
    }
}
