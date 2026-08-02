package com.gridea.android.util

/**
 * Markdown 工具类
 *
 * 对应旧版 Gridea 0.9.3 的 src/helpers/content-helper.ts 和 src/server/posts.ts 中的部分逻辑
 */
object MarkdownUtils {

    /**
     * 提取文章摘要
     *
     * 对应旧版 posts.ts 中的逻辑：
     * const moreReg = /\n\s*<!--\s*more\s*-->\s*\n/i
     * 如果匹配到 <!-- more --> 标记，则取其前的内容作为摘要
     */
    fun extractAbstract(content: String): String {
        val moreReg = Regex("\\n\\s*<!--\\s*more\\s*-->\\s*\\n", RegexOption.IGNORE_CASE)
        val match = moreReg.find(content)
        return if (match != null) {
            content.substring(0, match.range.first)
        } else {
            // 未找到标记则取前 200 字符作为摘要
            if (content.length > 200) content.substring(0, 200) + "..." else content
        }
    }

    /**
     * 格式化 YAML 字符串
     *
     * 对应旧版 helpers/utils.ts 中的 formatYamlString
     * 处理标题中的单引号，避免 YAML 解析错误
     */
    fun formatYamlString(value: String): String {
        return value.replace("'", "''")
    }

    /**
     * 构建 Markdown 文件内容（含 front-matter）
     *
     * 对应旧版 posts.ts 中 savePostToFile() 方法构建 mdStr 的逻辑
     */
    fun buildMarkdownContent(
        title: String,
        date: String,
        tags: List<String>,
        published: Boolean,
        hideInList: Boolean,
        feature: String,
        isTop: Boolean,
        content: String
    ): String {
        val formattedTitle = formatYamlString(title)
        return """---
title: '$formattedTitle'
date: $date
tags: [${tags.joinToString(",")}]
published: $published
hideInList: $hideInList
feature: $feature
isTop: $isTop
---
$content"""
    }

    /**
     * 解析 Markdown 文件的 front-matter
     *
     * 对应旧版使用 gray-matter 库解析的功能
     * 返回 Pair(frontMatterMap, contentBody)
     */
    fun parseFrontMatter(markdown: String): Pair<Map<String, String>, String> {
        val frontMatterRegex = Regex("""^---\s*\n(.*?)\n---\s*\n(.*)$""", RegexOption.DOT_MATCHES_ALL)
        val match = frontMatterRegex.find(markdown)

        if (match == null) {
            return Pair(emptyMap(), markdown)
        }

        val frontMatterText = match.groupValues[1]
        val content = match.groupValues[2]

        val frontMatter = mutableMapOf<String, String>()
        frontMatterText.lines().forEach { line ->
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                var value = line.substring(colonIndex + 1).trim()
                // 去除首尾单引号
                if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length - 1).replace("''", "'")
                }
                frontMatter[key] = value
            }
        }

        return Pair(frontMatter, content)
    }
}
