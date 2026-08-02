package com.gridea.android.data.model

import kotlinx.serialization.Serializable

/**
 * 通用设置偏好（设置 - 通用）
 *
 * 用于数据备份 / 恢复，覆盖应用外观与编辑器偏好：
 * - 主题模式（system/light/dark）
 * - 语言模式（system/zh/en）
 * - 字体大小缩放（0.85-1.3）
 * - 动态取色（Material You）
 * - APP 界面强调色（hex 字符串，空串表示默认）
 * - 编辑器字数目标
 */
@Serializable
data class GeneralSettings(
    val themeMode: String = "system",
    val languageMode: String = "system",
    val fontSizeScale: Float = 1.0f,
    val dynamicColor: Boolean = false,
    val appAccentColor: String = "",
    val wordCountGoal: Int = 1000
)
