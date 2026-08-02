package com.gridea.android.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 语言/区域工具
 *
 * 支持 system（跟随系统）、zh（中文）、en（英文）三种模式
 * 使用 ContextWrapper 方式实现，兼容所有 API 级别（min SDK 24）
 */
object LocaleHelper {

    /**
     * 根据语言模式创建 Locale
     * @param mode "system" / "zh" / "en"
     */
    fun getLocale(mode: String): Locale? = when (mode) {
        "zh" -> Locale.CHINESE
        "en" -> Locale.ENGLISH
        else -> null  // system -> 使用系统默认
    }

    /**
     * 将语言模式应用到 Context，返回包装后的 Context
     */
    fun wrap(context: Context, mode: String): Context {
        val locale = getLocale(mode) ?: return context
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        // 避免某些机型上仅有 Locale.US 时的回退问题
        config.setLocales(android.os.LocaleList(locale))
        return context.createConfigurationContext(config)
    }

    /**
     * 持久化应用语言到系统（API 24+ 通过 updateConfiguration）
     * 在 Activity recreate 前调用
     */
    fun applyLanguage(context: Context, mode: String) {
        val locale = getLocale(mode) ?: return
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(android.os.LocaleList(locale))
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
