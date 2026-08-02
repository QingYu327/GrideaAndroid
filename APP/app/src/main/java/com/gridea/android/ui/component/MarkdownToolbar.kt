package com.gridea.android.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Subscript
import androidx.compose.material.icons.filled.Superscript
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gridea.android.R

/**
 * Markdown 工具栏动作类型
 */
sealed class MarkdownAction {
    object Bold : MarkdownAction()
    object Italic : MarkdownAction()
    object Strikethrough : MarkdownAction()
    object Mark : MarkdownAction()           // ==高亮==
    object Superscript : MarkdownAction()    // ^上标^
    object Subscript : MarkdownAction()      // ~下标~
    object Heading : MarkdownAction()
    object Quote : MarkdownAction()
    object CodeInline : MarkdownAction()
    object CodeBlock : MarkdownAction()
    object UnorderedList : MarkdownAction()
    object OrderedList : MarkdownAction()
    object TaskList : MarkdownAction()       // - [ ] 任务列表
    object Link : MarkdownAction()
    object Image : MarkdownAction()
    object Table : MarkdownAction()
    object HorizontalRule : MarkdownAction() // --- 分割线

    /**
     * 代码片段模板：携带待插入的模板文本
     */
    data class Template(val content: String) : MarkdownAction()
}

/**
 * 常用 Markdown 代码片段模板集合
 */
object MarkdownTemplates {

    /** 代码块模板（带语言标识） */
    fun codeBlock(language: String): String = "```$language\n\n```"

    /** 3×3 表格（3 列 × 3 行，含表头） */
    val table3x3: String =
        "\n| 列1 | 列2 | 列3 |\n| --- | --- | --- |\n| 内容 | 内容 | 内容 |\n| 内容 | 内容 | 内容 |\n"

    /** 4×4 表格（4 列 × 4 行，含表头） */
    val table4x4: String =
        "\n| 列1 | 列2 | 列3 | 列4 |\n| --- | --- | --- | --- |\n" +
            "| 内容 | 内容 | 内容 | 内容 |\n| 内容 | 内容 | 内容 | 内容 |\n| 内容 | 内容 | 内容 | 内容 |\n"

    /** 引用块 */
    val quote: String = "> 引用文本"

    /** 任务列表（3 项） */
    val taskList: String = "- [ ] 任务一\n- [ ] 任务二\n- [ ] 任务三"

    /** 数学公式块 */
    val math: String = "\n$$\n\n$$\n"

    /** 警告框（GitHub 风格 admonition） */
    val warning: String = "> [!WARNING]\n> 警告内容"
}

/**
 * Markdown 编辑工具栏
 *
 * 对应旧版 Gridea 0.9.3 中 MonacoMarkdownEditor 上方的工具按钮
 * 提供常用 Markdown 语法的快捷插入
 */
@Composable
fun MarkdownToolbar(
    onAction: (MarkdownAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            ToolbarItem(Icons.Filled.FormatBold, "加粗") { onAction(MarkdownAction.Bold) }
            ToolbarItem(Icons.Filled.FormatItalic, "斜体") { onAction(MarkdownAction.Italic) }
            ToolbarItem(Icons.Filled.FormatStrikethrough, "删除线") { onAction(MarkdownAction.Strikethrough) }
            ToolbarItem(Icons.Filled.TextFields, "高亮") { onAction(MarkdownAction.Mark) }
            ToolbarItem(Icons.Filled.Superscript, "上标") { onAction(MarkdownAction.Superscript) }
            ToolbarItem(Icons.Filled.Subscript, "下标") { onAction(MarkdownAction.Subscript) }
            ToolbarItem(Icons.Filled.Title, "标题") { onAction(MarkdownAction.Heading) }
            ToolbarItem(Icons.Filled.FormatQuote, "引用") { onAction(MarkdownAction.Quote) }
            ToolbarItem(Icons.Filled.Code, "行内代码") { onAction(MarkdownAction.CodeInline) }
            ToolbarItem(Icons.Filled.Functions, "代码块") { onAction(MarkdownAction.CodeBlock) }
            ToolbarItem(Icons.AutoMirrored.Filled.FormatListBulleted, "无序列表") { onAction(MarkdownAction.UnorderedList) }
            ToolbarItem(Icons.Filled.FormatListNumbered, "有序列表") { onAction(MarkdownAction.OrderedList) }
            ToolbarItem(Icons.Filled.Checklist, "任务列表") { onAction(MarkdownAction.TaskList) }
            ToolbarItem(Icons.Filled.Link, "链接") { onAction(MarkdownAction.Link) }
            ToolbarItem(Icons.Filled.Image, "图片") { onAction(MarkdownAction.Image) }
            ToolbarItem(Icons.Filled.TableChart, "表格") { onAction(MarkdownAction.Table) }
            ToolbarItem(Icons.Filled.HorizontalRule, "分割线") { onAction(MarkdownAction.HorizontalRule) }

            // 模板按钮：弹出常用代码片段模板
            TemplateButton(onAction = onAction)
        }
    }
}

/**
 * 模板按钮：点击弹出 DropdownMenu，展示常用 Markdown 模板
 *
 * 其中"代码块"项会展开二级菜单，供选择语言（kotlin/javascript/python/sql/bash）
 */
@Composable
private fun TemplateButton(onAction: (MarkdownAction) -> Unit) {
    // 主模板菜单显示状态
    var showTemplateMenu by remember { mutableStateOf(false) }
    // 代码块语言选择子菜单显示状态
    var showCodeLangMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { showTemplateMenu = true },
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Code,
                contentDescription = stringResource(R.string.editor_template),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 主模板菜单
        DropdownMenu(
            expanded = showTemplateMenu,
            onDismissRequest = { showTemplateMenu = false }
        ) {
            // 代码块：展开语言选择
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_template_code_block)) },
                onClick = {
                    showTemplateMenu = false
                    showCodeLangMenu = true
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_template_table_3x3)) },
                onClick = {
                    showTemplateMenu = false
                    onAction(MarkdownAction.Template(MarkdownTemplates.table3x3))
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_template_table_4x4)) },
                onClick = {
                    showTemplateMenu = false
                    onAction(MarkdownAction.Template(MarkdownTemplates.table4x4))
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_template_quote)) },
                onClick = {
                    showTemplateMenu = false
                    onAction(MarkdownAction.Template(MarkdownTemplates.quote))
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_template_task_list)) },
                onClick = {
                    showTemplateMenu = false
                    onAction(MarkdownAction.Template(MarkdownTemplates.taskList))
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_template_math)) },
                onClick = {
                    showTemplateMenu = false
                    onAction(MarkdownAction.Template(MarkdownTemplates.math))
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_template_warning)) },
                onClick = {
                    showTemplateMenu = false
                    onAction(MarkdownAction.Template(MarkdownTemplates.warning))
                }
            )
        }

        // 代码块语言选择子菜单
        DropdownMenu(
            expanded = showCodeLangMenu,
            onDismissRequest = { showCodeLangMenu = false }
        ) {
            listOf("kotlin", "javascript", "python", "sql", "bash").forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang) },
                    onClick = {
                        showCodeLangMenu = false
                        onAction(MarkdownAction.Template(MarkdownTemplates.codeBlock(lang)))
                    }
                )
            }
        }
    }
}

@Composable
private fun ToolbarItem(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
