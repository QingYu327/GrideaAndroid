package com.gridea.android.ui.theme

import androidx.compose.ui.graphics.Color

// Gridea 品牌色板
// Primary 统一使用淡紫色，与 AccentColor 一致，确保所有默认使用 primary 的组件（如 TextButton）自动跟随
val Primary = Color(0xFF9C8FDA)
val PrimaryDark = Color(0xFF9C8FDA)
val Secondary = Color(0xFF10B981)
val SecondaryDark = Color(0xFF34D399)

/** 危险操作色（浅红色），用于删除按钮、删除图标等警醒性操作 */
val DangerColor = Color(0xFFE57373)

/** 置顶标记色（浅绿色），用于文章卡片"置顶"徽章，与隐藏文章红色徽章同理 */
val PinnedColor = Color(0xFF34D399)

// 浅色主题
val LightBackground = Color(0xFFF9FAFB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F3F7)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF1F2937)
val LightOnSurface = Color(0xFF1F2937)
val LightOnSurfaceVariant = Color(0xFF4B5563)

// 深色主题
val DarkBackground = Color(0xFF0F172A)
val DarkSurface = Color(0xFF1E293B)
val DarkSurfaceVariant = Color(0xFF2A3A55)
val DarkOnPrimary = Color(0xFF0B1220)
val DarkOnSecondary = Color(0xFF002117)
val DarkOnBackground = Color(0xFFF9FAFB)
val DarkOnSurface = Color(0xFFF9FAFB)
val DarkOnSurfaceVariant = Color(0xFFCBD5E1)
