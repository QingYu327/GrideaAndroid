package com.gridea.android.util

/**
 * Markdown 编辑辅助类
 *
 * 对应旧版 Gridea 0.9.3 中 src/components/MonacoMarkdownEditor/Index.vue
 * 里对 Monaco 编辑器执行的文本插入/包裹操作
 *
 * 由于 Compose TextField 没有"光标位置"的直接 API，
 * 我们用 TextFieldValue 保存 selection 范围，由调用方传入
 */
object MarkdownEditorHelper {

    /**
     * 在光标处插入文本（如插入图片语法）
     * @param text 原始全文
     * @param selection 当前选区 [start, end]
     * @param insert 待插入文本
     * @return Pair(新文本, 新光标位置)
     */
    fun insertAtCursor(
        text: String,
        selection: IntRange,
        insert: String
    ): Pair<String, IntRange> {
        val before = text.substring(0, selection.first)
        val after = text.substring(selection.last)
        val newText = before + insert + after
        val newCursor = before.length + insert.length
        return Pair(newText, newCursor..newCursor)
    }

    /**
     * 用前后缀包裹当前选中文本（如加粗、斜体、行内代码）
     * @param text 原始全文
     * @param selection 当前选区 [start, end]
     * @param prefix 前缀，如 "**"
     * @param suffix 后缀，如 "**"
     * @param placeholder 选区为空时使用的占位文本
     * @return Pair(新文本, 新选区)
     */
    fun wrapSelection(
        text: String,
        selection: IntRange,
        prefix: String,
        suffix: String,
        placeholder: String = "文本"
    ): Pair<String, IntRange> {
        val before = text.substring(0, selection.first)
        val selected = if (selection.isEmpty()) placeholder else text.substring(selection.first, selection.last)
        val after = text.substring(selection.last)
        val newText = before + prefix + selected + suffix + after

        // 新选区选中被包裹的内容
        val newStart = before.length + prefix.length
        val newEnd = newStart + selected.length
        return Pair(newText, newStart..newEnd)
    }

    /**
     * 在当前行行首插入前缀（如标题 #、引用 >、列表 -）
     * 若有多行选区，则每行都插入
     * @param text 原始全文
     * @param selection 当前选区 [start, end]
     * @param linePrefix 行首前缀，如 "# "、"> "、"- "
     * @return Pair(新文本, 新选区)
     */
    fun insertLinePrefix(
        text: String,
        selection: IntRange,
        linePrefix: String
    ): Pair<String, IntRange> {
        // 找到选区起始所在行的行首
        val lineStart = findLineStart(text, selection.first)
        val lineEnd = findLineEnd(text, selection.last)

        val before = text.substring(0, lineStart)
        val block = text.substring(lineStart, lineEnd)
        val after = text.substring(lineEnd)

        // 对块内每一行添加前缀
        val newBlock = block.split("\n").joinToString("\n") { line ->
            if (line.startsWith(linePrefix)) line else linePrefix + line
        }

        val newText = before + newBlock + after
        // 选中新块
        return Pair(newText, before.length..(before.length + newBlock.length))
    }

    /**
     * 切换当前行行首前缀（若已存在则移除，否则添加）
     * 用于列表项、任务列表等开关型语法
     */
    fun toggleLinePrefix(
        text: String,
        selection: IntRange,
        linePrefix: String
    ): Pair<String, IntRange> {
        val lineStart = findLineStart(text, selection.first)
        val lineEnd = findLineEnd(text, selection.last)

        val before = text.substring(0, lineStart)
        val block = text.substring(lineStart, lineEnd)
        val after = text.substring(lineEnd)

        val newBlock = block.split("\n").joinToString("\n") { line ->
            if (line.startsWith(linePrefix)) {
                line.substring(linePrefix.length)
            } else {
                linePrefix + line
            }
        }

        val newText = before + newBlock + after
        return Pair(newText, before.length..(before.length + newBlock.length))
    }

    /**
     * 插入链接语法 [文本](url)
     */
    fun insertLink(
        text: String,
        selection: IntRange,
        url: String = "https://"
    ): Pair<String, IntRange> {
        val selected = if (selection.isEmpty()) "链接文本" else text.substring(selection.first, selection.last)
        val insert = "[$selected]($url)"
        return insertAtCursor(text, selection, insert)
    }

    /**
     * 插入图片语法 ![alt](url)
     */
    fun insertImage(
        text: String,
        selection: IntRange,
        url: String = "https://",
        alt: String = "图片描述"
    ): Pair<String, IntRange> {
        val insert = "![$alt]($url)"
        return insertAtCursor(text, selection, insert)
    }

    /**
     * 插入代码块语法 ```...```
     */
    fun insertCodeBlock(
        text: String,
        selection: IntRange,
        language: String = ""
    ): Pair<String, IntRange> {
        val before = text.substring(0, selection.first)
        val selected = if (selection.isEmpty()) "// 代码" else text.substring(selection.first, selection.last)
        val after = text.substring(selection.last)
        val insert = "```$language\n$selected\n```"
        val newText = before + insert + after
        // 光标定位到代码块内首行末
        val cursorPos = before.length + "```$language\n".length + selected.length
        return Pair(newText, cursorPos..cursorPos)
    }

    /**
     * 插入表格语法（2 行 2 列基础表格）
     */
    fun insertTable(text: String, selection: IntRange): Pair<String, IntRange> {
        val insert = "\n| 列1 | 列2 |\n| --- | --- |\n| 内容1 | 内容2 |\n"
        return insertAtCursor(text, selection, insert)
    }

    /**
     * 插入任务列表项（"- [ ] "）
     */
    fun insertTaskList(text: String, selection: IntRange): Pair<String, IntRange> {
        return toggleLinePrefix(text, selection, "- [ ] ")
    }

    /**
     * 插入分割线
     */
    fun insertHorizontalRule(text: String, selection: IntRange): Pair<String, IntRange> {
        val insert = "\n\n---\n\n"
        return insertAtCursor(text, selection, insert)
    }

    // ===== 内部工具方法 =====

    private fun findLineStart(text: String, position: Int): Int {
        var i = position
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    private fun findLineEnd(text: String, position: Int): Int {
        var i = position
        while (i < text.length && text[i] != '\n') i++
        return i
    }

    private fun IntRange.isEmpty(): Boolean = first == last
}
