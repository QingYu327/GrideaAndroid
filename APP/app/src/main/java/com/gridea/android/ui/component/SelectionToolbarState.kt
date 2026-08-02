package com.gridea.android.ui.component

/**
 * 批量选择工具栏状态
 *
 * 用于子页面向父页面（PagesScreen）上报选择模式状态，
 * 让父页面的 TopAppBar 渲染全选/删除按钮。
 *
 * @param selectedCount 已选条目数
 * @param isAllSelected 是否已全选
 * @param onToggleSelectAll 切换全选/取消全选
 * @param onDelete 触发批量删除（调用方负责弹出确认对话框）
 */
data class SelectionToolbarState(
    val selectedCount: Int,
    val isAllSelected: Boolean,
    val onToggleSelectAll: () -> Unit,
    val onDelete: () -> Unit
)
