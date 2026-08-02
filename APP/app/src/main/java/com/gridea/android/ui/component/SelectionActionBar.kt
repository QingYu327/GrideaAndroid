package com.gridea.android.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gridea.android.R
import com.gridea.android.ui.theme.DangerColor
import com.gridea.android.ui.theme.LocalAccentColor

/**
 * 批量选择模式统一操作栏（紧凑右上角样式）
 *
 * 仿照文章批量管理：多选图标和删除放置在页面顶部右上角处，平时隐藏，
 * 长按触发批量管理后出现。
 *
 * - 圆角卡片风格（紧凑型，非全宽）
 * - 进入：slideInVertically + fadeIn + scaleIn 从顶部滑入
 * - 退出：slideOutVertically + fadeOut + scaleOut 向顶部滑出
 * - 显示已选计数 + 全选切换 + 批量删除
 * - 由各页面在外层 Box 中按 TopEnd 对齐悬浮显示
 *
 * @param visible 是否显示（选择模式开启）
 * @param selectedCount 已选条目数
 * @param isAllSelected 是否已全选（控制全选图标着色与切换语义）
 * @param onToggleSelectAll 切换全选 / 取消全选
 * @param onDelete 触发批量删除（调用方负责弹出确认对话框）
 * @param modifier 修饰符
 */
@Composable
fun SelectionActionBar(
    visible: Boolean,
    selectedCount: Int,
    isAllSelected: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(220, easing = FastOutSlowInEasing)) +
            scaleIn(initialScale = 0.8f, animationSpec = tween(220, easing = FastOutSlowInEasing)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(160)) +
            scaleOut(targetScale = 0.8f, animationSpec = tween(180, easing = FastOutSlowInEasing)),
        modifier = modifier
    ) {
        val accentColor = LocalAccentColor.current
        val surfaceColor = MaterialTheme.colorScheme.surface
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = accentColor.copy(alpha = 0.12f).compositeOver(surfaceColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 已选计数
                Text(
                    text = stringResource(R.string.batch_selected_count, selectedCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                )
                // 全选 / 取消全选
                IconButton(onClick = onToggleSelectAll) {
                    Icon(
                        imageVector = Icons.Filled.LibraryAddCheck,
                        contentDescription = stringResource(
                            if (isAllSelected) R.string.batch_deselect_all
                            else R.string.batch_select_all
                        ),
                        tint = if (isAllSelected) accentColor
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 批量删除
                IconButton(
                    onClick = onDelete,
                    enabled = selectedCount > 0
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.batch_delete),
                        tint = if (selectedCount > 0) DangerColor
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
