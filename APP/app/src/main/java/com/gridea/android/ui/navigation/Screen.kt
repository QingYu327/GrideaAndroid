package com.gridea.android.ui.navigation

/**
 * 路由定义
 *
 * 对应旧版 Gridea 0.9.3 的 src/router.ts
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Editor : Screen("editor")
    object Tags : Screen("tags")
    object TagDetail : Screen("tag_detail")
    object Setting : Screen("setting")
    object SettingSection : Screen("setting_section")
    object SiteInfo : Screen("site_info")
    object Preview : Screen("preview")
    object LogManager : Screen("log_manage")
    object Statistics : Screen("statistics")
    object FriendLinks : Screen("friend_links")
    object Menus : Screen("menus")
    object Trash : Screen("trash")
    object Deploy : Screen("deploy")
    object ThemeHub : Screen("theme_hub")
}
