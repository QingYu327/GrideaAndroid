package com.gridea.android.ui.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.gridea.android.R
import com.gridea.android.data.repository.ImageInfo
import com.gridea.android.ui.screen.editor.EditorViewModel
import com.gridea.android.ui.screen.editor.ImportProgress
import com.gridea.android.ui.theme.LocalAccentColor
import kotlinx.coroutines.launch

/**
 * 图片选择器底部弹窗
 *
 * 提供两种插入图片的方式：
 * 1. 从相册选择（用 Activity Result API）
 * 2. 从图片库选择（已上传的图片）
 *
 * 对应旧版 Gridea 0.9.3 ArticleUpdate.vue 的 insertImage + fileChangeHandler
 *
 * 扩展功能：
 * - 长按图片弹出菜单（重命名/删除）
 * - 批量导入多张图片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePickerSheet(
    onImageSelected: (imageUrl: String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val images by viewModel.images.collectAsState()
    val isUploading by viewModel.isImageUploading.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()

    // 待删除的图片（确认对话框）
    var imageToDelete by remember { mutableStateOf<ImageInfo?>(null) }
    // 待重命名的图片（输入对话框）
    var imageToRename by remember { mutableStateOf<ImageInfo?>(null) }
    // 导入/操作结果提示（自动消失）
    var resultMessage by remember { mutableStateOf<ResultMessage?>(null) }

    // 全局灵动岛通知：图片删除/重命名/导入结果反馈
    val noticeManager = com.gridea.android.ui.theme.LocalNoticeManager.current

    // 相册选择 Launcher（单张）
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.saveImageFromUri(uri) { savedUrl ->
                if (savedUrl != null) {
                    noticeManager.showNotice("已添加图片")
                    onImageSelected(savedUrl)
                }
            }
        }
    }

    // 批量导入 Launcher（多张）
    val batchImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris) { success ->
                noticeManager.showNotice("已导入 $success 张图片")
                resultMessage = if (success == uris.size) {
                    ResultMessage.ImportDone(success)
                } else {
                    ResultMessage.ImportPartial(uris.size, success)
                }
            }
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
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.image_insert_title),
                    style = MaterialTheme.typography.titleMedium
                )
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            // 从相册选择按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { galleryLauncher.launch("image/*") }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.image_select_from_album))
            }

            // 批量导入按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { batchImportLauncher.launch("image/*") }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.image_import_batch))
            }

            // 导入进度条
            importProgress?.let { progress ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.image_import_progress,
                            progress.current,
                            progress.total
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = {
                            if (progress.total == 0) 0f
                            else progress.current.toFloat() / progress.total.toFloat()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }

            // 导入/操作结果提示
            resultMessage?.let { msg ->
                val (text, isError) = formatResultMessage(msg)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) com.gridea.android.ui.theme.DangerColor
                           else LocalAccentColor.current,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            // 图片库标题
            if (images.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.image_library_count, images.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.image_long_press_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // 图片网格
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(images, key = { it.url }) { image ->
                        ImageGridItem(
                            image = image,
                            onClick = {
                                scope.launch { sheetState.hide() }
                                    .invokeOnCompletion { onImageSelected(image.url) }
                            },
                            onRename = { imageToRename = image },
                            onDelete = { imageToDelete = image }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.image_library_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }

    // 删除确认对话框
    imageToDelete?.let { image ->
        AlertDialog(
            onDismissRequest = { imageToDelete = null },
            title = { Text(stringResource(R.string.image_delete_title)) },
            text = { Text(stringResource(R.string.image_delete_message, image.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteImage(image.url)
                    imageToDelete = null
                    noticeManager.showNotice("已删除图片")
                }) {
                    Text(stringResource(R.string.delete), color = com.gridea.android.ui.theme.DangerColor)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { imageToDelete = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 重命名对话框
    imageToRename?.let { image ->
        RenameImageDialog(
            image = image,
            onDismiss = { imageToRename = null },
            onConfirm = { newName ->
                viewModel.renameImage(image, newName) { success ->
                    if (success) {
                        noticeManager.showNotice("已重命名图片")
                    } else {
                        resultMessage = ResultMessage.RenameFailed
                    }
                }
                imageToRename = null
            }
        )
    }

    // 结果提示自动清除
    resultMessage?.let {
        androidx.compose.runtime.LaunchedEffect(it) {
            kotlinx.coroutines.delay(2500)
            resultMessage = null
        }
    }
}

/**
 * 操作结果消息类型
 */
private sealed class ResultMessage {
    /** 重命名失败 */
    object RenameFailed : ResultMessage()
    /** 导入完成 */
    data class ImportDone(val count: Int) : ResultMessage()
    /** 部分导入成功 */
    data class ImportPartial(val total: Int, val success: Int) : ResultMessage()
}

/**
 * 格式化结果消息为显示文本
 */
@Composable
private fun formatResultMessage(msg: ResultMessage): Pair<String, Boolean> {
    return when (msg) {
        is ResultMessage.RenameFailed ->
            stringResource(R.string.image_rename_failed) to true
        is ResultMessage.ImportDone ->
            stringResource(R.string.image_import_done, msg.count) to false
        is ResultMessage.ImportPartial ->
            stringResource(R.string.image_import_partial, msg.total, msg.success) to false
    }
}

/**
 * 重命名图片对话框
 */
@Composable
private fun RenameImageDialog(
    image: ImageInfo,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // 默认填入当前文件名（去除扩展名）
    val defaultName = remember(image.name) {
        val dotIndex = image.name.lastIndexOf('.')
        if (dotIndex > 0) image.name.substring(0, dotIndex) else image.name
    }
    var newName by remember { mutableStateOf(defaultName) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.image_rename_title)) },
        text = {
            Column {
                Text(
                    text = image.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = {
                        newName = it
                        isError = false
                    },
                    label = { Text(stringResource(R.string.image_rename_hint)) },
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(stringResource(R.string.image_rename_empty))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedIndicatorColor = LocalAccentColor.current,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedLabelColor = LocalAccentColor.current,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = LocalAccentColor.current
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newName.isBlank()) {
                        isError = true
                    } else {
                        onConfirm(newName)
                    }
                },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalAccentColor.current)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 图片网格单元
 *
 * @param onClick 单击：插入图片
 * @param onRename 长按菜单：重命名
 * @param onDelete 长按菜单：删除
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageGridItem(
    image: ImageInfo,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    // 长按弹出菜单显示状态
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.05f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
    ) {
        AsyncImage(
            model = image.url,
            contentDescription = image.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 长按弹出菜单（重命名/删除）
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.image_rename)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    showMenu = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = com.gridea.android.ui.theme.DangerColor
                    )
                },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }

        // 右上角快捷删除按钮（保留原交互）
        IconButton(
            onClick = onDelete,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = Color.White,
                modifier = Modifier.background(
                    Color.Black.copy(alpha = 0.4f),
                    RoundedCornerShape(50)
                )
            )
        }
    }
}
