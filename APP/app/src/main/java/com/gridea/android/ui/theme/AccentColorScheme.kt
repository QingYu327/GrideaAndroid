package com.gridea.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 基于种子色派生完整的 Material3 ColorScheme。
 *
 * 用于"自定义 APP 强调色"模式：用户通过调色盘选取一个颜色后，
 * 由该色派生 primary / primaryContainer / secondary / tertiary / outline 等所有色调字段，
 * 让整个 colorScheme 与动态取色（dynamicLightColorScheme）走同一套注入路径，
 * 从而保证两种模式的覆盖范围一致。
 *
 * 派生规则（参考 Material3 Tonal Palette 语义，使用 HSV 转换简化实现）：
 * - primary：保持种子色相和饱和度，调整亮度到对比度合适档位
 * - onPrimary：白色（浅色模式）/ 黑色（深色模式）
 * - primaryContainer：seed 浅化（浅色）/ 深化（深色）
 * - onPrimaryContainer：seed 深化（浅色）/ 浅化（深色）
 * - secondary：seed 降低饱和度
 * - tertiary：seed 色相旋转 +60°（互补色调）
 * - surfaceVariant / onSurfaceVariant / outline：加微弱种子色调，保持中性观感
 * - background / surface / onBackground / onSurface：保留原 LightColorScheme/DarkColorScheme 值，
 *   避免切换强调色时背景大幅跳变
 */
private object AccentPalette {
    /** Color → [h, s, v] */
    private fun toHSV(color: Color): FloatArray {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        return hsv
    }

    /** 按色相/饱和度/亮度直接生成 Color */
    private fun hsv(h: Float, s: Float, v: Float): Color =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v)))

    /** 主色：保持种子色相与饱和度，目标亮度 */
    fun primary(seed: Color, value: Float): Color {
        val (h, s, _) = toHSV(seed)
        return hsv(h, s, value)
    }

    /** 容器色：色相不变，饱和度按系数衰减，目标亮度 */
    fun container(seed: Color, value: Float, satFactor: Float): Color {
        val (h, s, _) = toHSV(seed)
        return hsv(h, s * satFactor, value)
    }

    /** 容器上的文字色：色相不变，饱和度按系数衰减，目标亮度 */
    fun onContainer(seed: Color, value: Float, satFactor: Float): Color {
        val (h, s, _) = toHSV(seed)
        return hsv(h, s * satFactor, value)
    }

    /** 次要色：饱和度大幅衰减 */
    fun secondary(seed: Color, value: Float, satFactor: Float): Color {
        val (h, s, _) = toHSV(seed)
        return hsv(h, s * satFactor, value)
    }

    /** 第三色：色相旋转 +60°，保持饱和度 */
    fun tertiary(seed: Color, value: Float): Color {
        val (h, s, _) = toHSV(seed)
        return hsv((h + 60f) % 360f, s, value)
    }

    /** 第三色容器：色相旋转 +60°，饱和度衰减 */
    fun tertiaryContainer(seed: Color, value: Float, satFactor: Float): Color {
        val (h, s, _) = toHSV(seed)
        return hsv((h + 60f) % 360f, s * satFactor, value)
    }

    /** 微弱色调的中性色（用于 surfaceVariant/outline 等） */
    fun neutral(seed: Color, value: Float, satFactor: Float): Color {
        val (h, s, _) = toHSV(seed)
        return hsv(h, s * satFactor, value)
    }
}

/**
 * 基于种子色生成浅色 ColorScheme。
 *
 * 背景与表面保留原 LightBackground / LightSurface，避免切换强调色时整体观感突变。
 */
fun accentLightScheme(seed: Color): ColorScheme = lightColorScheme(
    primary = AccentPalette.primary(seed, 0.62f),
    onPrimary = Color.White,
    primaryContainer = AccentPalette.container(seed, 0.93f, 0.50f),
    onPrimaryContainer = AccentPalette.onContainer(seed, 0.18f, 0.80f),
    secondary = AccentPalette.secondary(seed, 0.55f, 0.45f),
    onSecondary = Color.White,
    secondaryContainer = AccentPalette.container(seed, 0.92f, 0.30f),
    onSecondaryContainer = AccentPalette.onContainer(seed, 0.22f, 0.50f),
    tertiary = AccentPalette.tertiary(seed, 0.60f),
    onTertiary = Color.White,
    tertiaryContainer = AccentPalette.tertiaryContainer(seed, 0.92f, 0.40f),
    onTertiaryContainer = AccentPalette.tertiaryContainer(seed, 0.18f, 0.50f),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = AccentPalette.neutral(seed, 0.94f, 0.10f),
    onSurfaceVariant = AccentPalette.neutral(seed, 0.32f, 0.35f),
    outline = AccentPalette.neutral(seed, 0.55f, 0.25f)
)

/**
 * 基于种子色生成深色 ColorScheme。
 *
 * 背景与表面保留原 DarkBackground / DarkSurface，避免切换强调色时整体观感突变。
 */
fun accentDarkScheme(seed: Color): ColorScheme = darkColorScheme(
    primary = AccentPalette.primary(seed, 0.78f),
    onPrimary = Color.Black,
    primaryContainer = AccentPalette.container(seed, 0.30f, 0.60f),
    onPrimaryContainer = AccentPalette.onContainer(seed, 0.92f, 0.50f),
    secondary = AccentPalette.secondary(seed, 0.75f, 0.45f),
    onSecondary = Color.Black,
    secondaryContainer = AccentPalette.container(seed, 0.32f, 0.30f),
    onSecondaryContainer = AccentPalette.onContainer(seed, 0.90f, 0.40f),
    tertiary = AccentPalette.tertiary(seed, 0.75f),
    onTertiary = Color.Black,
    tertiaryContainer = AccentPalette.tertiaryContainer(seed, 0.32f, 0.40f),
    onTertiaryContainer = AccentPalette.tertiaryContainer(seed, 0.90f, 0.50f),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = AccentPalette.neutral(seed, 0.22f, 0.15f),
    onSurfaceVariant = AccentPalette.neutral(seed, 0.82f, 0.30f),
    outline = AccentPalette.neutral(seed, 0.62f, 0.25f)
)
