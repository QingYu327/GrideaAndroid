package com.gridea.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = LightOnPrimary,
    secondary = Secondary,
    onSecondary = LightOnSecondary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = DarkOnPrimary,
    secondary = SecondaryDark,
    onSecondary = DarkOnSecondary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant
)

/**
 * 字体大小缩放 CompositionLocal
 *
 * 通过 CompositionLocalProvider 向子树提供当前缩放比例（范围 0.85-1.3，默认 1.0）。
 * 组件可读取此值进行自定义缩放；全局文字大小主要通过缩放后的 Typography 影响。
 */
val LocalFontSizeScale = staticCompositionLocalOf { 1.0f }

/**
 * 强调色 CompositionLocal
 *
 * 平时（动态取色关闭）使用淡紫色 [AccentColor]；
 * 打开动态取色后跟随系统主题色，由 GrideaAndroidTheme 根据 dynamicColor 状态动态提供。
 */
val LocalAccentColor = staticCompositionLocalOf { AccentColor }

/** 默认强调色（淡紫色） */
val AccentColor = Color(0xFF9C8FDA)

@Composable
fun GrideaAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    fontSizeScale: Float = 1.0f,
    appAccentColor: String = "",
    content: @Composable () -> Unit
) {
    // 解析自定义强调色（合法时作为种子色派生完整 colorScheme，与动态取色走同一套注入路径）
    val customSeed: Color? = remember(appAccentColor) {
        if (appAccentColor.length == 7 && appAccentColor.startsWith("#")) {
            try {
                Color(appAccentColor.substring(1).toLong(16) or 0xFF000000)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        customSeed != null -> {
            // 用户自定义强调色 → 由种子色派生完整 ColorScheme
            // 让 141 处 MaterialTheme.colorScheme.* 与 LocalAccentColor 一样跟随用户取色
            if (darkTheme) accentDarkScheme(customSeed) else accentLightScheme(customSeed)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 强调色统一从 colorScheme.primary 取，保证动态取色与自定义色两条路径行为一致
    val accentColor = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> colorScheme.primary
        customSeed != null -> colorScheme.primary
        else -> AccentColor
    }

    // 根据缩放比例生成 Typography，影响全局文字大小
    val scaledTypography = scaledTypography(fontSizeScale)

    // 通过 CompositionLocalProvider 提供 LocalFontSizeScale 和 LocalAccentColor，供子树读取
    // 外层用 Surface 包裹提供统一背景色：让 Scaffold 的 containerColor=Transparent 时仍有背景显示，
    // 避免文章列表滑动到导航栏下方时因背景不同产生明显分界线
    CompositionLocalProvider(
        LocalFontSizeScale provides fontSizeScale,
        LocalAccentColor provides accentColor
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colorScheme.surface,
                contentColor = colorScheme.onSurface
            ) {
                content()
            }
        }
    }
}
