package com.gridea.android.ui.screen.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gridea.android.R
import com.gridea.android.data.model.PostVersion
import com.gridea.android.ui.theme.LocalAccentColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文章版本历史底部弹窗
 *
 * 展示当前文章的所有历史版本，支持恢复和删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHistorySheet(
    onDismiss: () -> Unit,
    onLoadVersions: (onResult: (List<PostVersion>) -> Unit) -> Unit,
    onRestore: (PostVersion) -> Unit,
    onDelete: (Long, () -> Unit) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var versions by remember { mutableStateOf<List<PostVersion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        onLoadVersions { result ->
            versions = result
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // 标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = LocalAccentColor.current
                )
                Text(
                    text = stringResource(R.string.editor_version_history),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (loading) {
                Text(
                    text = stringResource(R.string.editor_version_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else if (versions.isEmpty()) {
                Text(
                    text = stringResource(R.string.editor_version_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(versions, key = { it.id }) { version ->
                        VersionItem(
                            version = version,
                            onRestore = {
                                onRestore(version)
                                onDismiss()
                            },
                            onDelete = {
                                pendingDeleteId = version.id
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // 删除确认对话框
    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.editor_version_delete_title)) },
            text = { Text(stringResource(R.string.editor_version_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(id) {
                        versions = versions.filterNot { it.id == id }
                        pendingDeleteId = null
                    }
                }) {
                    Text(stringResource(R.string.confirm), color = com.gridea.android.ui.theme.DangerColor)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteId = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun VersionItem(
    version: PostVersion,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val timeStr = remember(version.savedAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(version.savedAt))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = version.title.ifEmpty { stringResource(R.string.editor_version_untitled) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (version.content.isNotEmpty()) {
                Text(
                    text = version.content.take(80),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRestore) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.editor_version_restore),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = com.gridea.android.ui.theme.DangerColor
                    )
                    Text(
                        text = stringResource(R.string.delete),
                        modifier = Modifier.padding(start = 4.dp),
                        color = com.gridea.android.ui.theme.DangerColor
                    )
                }
            }
        }
    }
}
