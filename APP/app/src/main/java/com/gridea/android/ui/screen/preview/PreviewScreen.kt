package com.gridea.android.ui.screen.preview

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.gridea.android.R
import com.gridea.android.ui.theme.LocalAccentColor
import com.gridea.android.util.AppLogger
import java.io.File

/**
 * 站点预览页面
 *
 * 使用 WebView 加载本地构建产物（cacheDir/gridea_build/index.html），
 * 让用户在 App 内预览生成的静态博客站点。
 *
 * 关键修复点：
 * 1. **URL 改写**：WebView 在 file:// 协议下不会自动加载目录的 index.html，
 *    拦截 `post/abc123/`、`tag/xxx/`、`archives/`、`tags/`、`links.html` 这类
 *    路径，改为显式加载 `.../index.html`，避免空白页。
 * 2. **JS Bridge 桥接日志**：通过 `@JavascriptInterface` 把页面内的点击、
 *    控制台错误、资源加载失败、404 等行为回传到 AppLogger 写入全局日志，
 *    实现"在 APP 内就能定位预览网站问题"的需求，不再需要 logcat。
 * 3. **外链交给系统浏览器**：http://、https:// 链接用 Intent.ACTION_VIEW 打开。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val accentColor = LocalAccentColor.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // WebView 历史栈状态：用于控制返回/前进按钮的启用状态
    // onPageFinished 时同步刷新，按钮点击后用 post {} 异步刷新（goBack/goForward 是异步操作）
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    val indexFile = File(context.cacheDir, "gridea_build/index.html")
    val targetUrl = "file://${indexFile.absolutePath}"
    val buildRoot = indexFile.parentFile?.absolutePath ?: ""

    // WebView 调试开关：由设置-调试控制，开启后可通过 Chrome DevTools 远程调试
    val settingViewModel: com.gridea.android.ui.screen.setting.SettingViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()
    val webViewDebug by settingViewModel.webViewDebug.collectAsState()
    val isPreviewRendering by settingViewModel.isPreviewRendering.collectAsState()
    // 静态开关，进程级生效：监听设置变化即时应用
    LaunchedEffect(webViewDebug) {
        WebView.setWebContentsDebuggingEnabled(webViewDebug)
    }

    // 进入预览时以 isPreview=true 重新渲染，跳过评论系统 CDN（Gitalk/Valine 等），
    // 避免 file:// 协议下跨域加载 cdn.jsdelivr.net 等外部资源导致报错
    LaunchedEffect(Unit) {
        settingViewModel.renderForPreview()
    }
    // 预览渲染完成后加载/重新加载 WebView
    // 关键：forceRebuild=true 会先删除输出目录再重建，
    // 如果在渲染完成前 loadUrl，WebView 会遇到 ERR_FILE_NOT_FOUND。
    //
    // 竞态条件防护：LaunchedEffect(isPreviewRendering) 在初始组合时会立即执行一次，
    // 此时 isPreviewRendering=false（StateFlow 初始值），如果直接判断 !isPreviewRendering
    // 会提前 loadUrl。用 hasStartedRendering 标志确保只有 renderForPreview() 被调用后
    // （isPreviewRendering 从 true 变回 false）才执行加载。
    var hasInitiallyLoaded by remember { mutableStateOf(false) }
    var hasStartedRendering by remember { mutableStateOf(false) }
    LaunchedEffect(isPreviewRendering) {
        if (isPreviewRendering) {
            hasStartedRendering = true
        } else if (hasStartedRendering && webViewRef != null) {
            // 渲染完成后才加载
            webViewRef?.clearCache(false)
            if (!hasInitiallyLoaded) {
                webViewRef?.loadUrl(targetUrl)
                hasInitiallyLoaded = true
            } else {
                webViewRef?.reload()
            }
        }
    }

    // 拦截手势返回：优先在WebView历史中回退，无历史时退出预览
    BackHandler {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onBack()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preview_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = accentColor
                        )
                    }
                },
                actions = {
                    // 返回上一页按钮（WebView历史回退）
                    // enabled=false 时按钮变灰，避免无历史时点击无效造成困惑
                    IconButton(
                        onClick = {
                            webViewRef?.goBack()
                            // goBack 是异步操作，post 到下一帧再刷新状态确保 canGoBack 已更新
                            webViewRef?.post {
                                canGoBack = webViewRef?.canGoBack() == true
                                canGoForward = webViewRef?.canGoForward() == true
                            }
                        },
                        enabled = canGoBack
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回上一页",
                            tint = if (canGoBack) accentColor else accentColor.copy(alpha = 0.38f)
                        )
                    }
                    // 前进到下一页按钮（WebView历史前进）
                    IconButton(
                        onClick = {
                            webViewRef?.goForward()
                            webViewRef?.post {
                                canGoBack = webViewRef?.canGoBack() == true
                                canGoForward = webViewRef?.canGoForward() == true
                            }
                        },
                        enabled = canGoForward
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "前进到下一页",
                            tint = if (canGoForward) accentColor else accentColor.copy(alpha = 0.38f)
                        )
                    }
                    // 刷新按钮（原有）
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = accentColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // WebView 调试开关由上方 LaunchedEffect 按设置统一控制
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    // file:// 协议下 WebView 默认不允许跨源访问，加上避免资源被拒
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = true

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                            msg?.let {
                                AppLogger.d(
                                    "WebView.Console",
                                    "[${it.messageLevel()}] ${it.message()} (line ${it.lineNumber()})"
                                )
                            }
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {

                        /**
                         * URL 拦截：处理带尾斜杠的目录路径，外链交给系统浏览器
                         */
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false

                            // 外链：交给系统浏览器
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (e: Exception) {
                                    AppLogger.e("WebView", "打开外链失败: $url", e)
                                }
                                return true
                            }

                            // file:// 链接：去掉 query/hash 拿到纯路径
                            val purePath = url.substringAfter("file://")
                                .substringBefore('?')
                                .substringBefore('#')

                            // 去掉末尾斜杠后判断是否是已知目录索引（避免无限重写）
                            val withoutSlash = purePath.trimEnd('/')
                            val isDir = purePath.endsWith("/")

                            if (isDir && buildRoot.isNotEmpty()) {
                                // file:// 协议下 WebView 不会自动加载目录的 index.html，
                                // 必须手动追加 /index.html，否则打开就是空白页或目录列表
                                //
                                // 关键：URL 中的中文/特殊字符可能是编码形式（%E6%96%B0）也可能是原始形式（新），
                                // 磁盘上的目录名是原始中文。先 URL 解码得到磁盘路径，检查 index.html 是否存在，
                                // 再追加 /index.html 到**原始 URL**（保持编码一致性，避免 loadUrl 中文路径问题）
                                val decodedPath = try {
                                    java.net.URLDecoder.decode(withoutSlash, "UTF-8")
                                } catch (e: Exception) {
                                    withoutSlash
                                }
                                val indexExists = File(decodedPath).isDirectory &&
                                    File("$decodedPath/index.html").exists()
                                if (indexExists) {
                                    // 追加 index.html 到原始 URL（保持编码形式），而非解码路径
                                    val targetUrl = url.removeSuffix("/") + "/index.html"
                                    AppLogger.action(
                                        "WebView",
                                        "Nav",
                                        "${purePath.trimEnd('/').substringAfter(buildRoot)} → index.html"
                                    )
                                    // 关键：用 post {} 异步加载，避免同步 loadUrl 在
                                    // shouldOverrideUrlLoading 内调用导致历史项被替换而非追加，
                                    // 进而让返回上一页按钮失效。浏览器标准是 URL 变化追加到历史栈。
                                    view?.post { view.loadUrl(targetUrl) }
                                    return true
                                }
                            }

                            // 其他（带 .html 后缀的链接）让 WebView 自己处理
                            return false
                        }

                        /**
                         * 资源加载失败（如 404）：记录到日志
                         */
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            val failedUrl = request?.url?.toString() ?: "?"
                            val code = error?.errorCode ?: -1
                            val desc = error?.description?.toString() ?: ""
                            AppLogger.w(
                                "WebView",
                                "资源加载失败 [$code] $failedUrl${if (desc.isNotEmpty()) " - $desc" else ""}"
                            )
                        }

                        /**
                         * 页面开始加载
                         */
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            if (url != null) {
                                AppLogger.d("WebView", "PageStart: ${url.removePrefix("file://").substringAfter(buildRoot)}")
                            }
                        }

                        /**
                         * 页面加载完成
                         */
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url != null) {
                                AppLogger.i("WebView", "PageLoaded: ${url.removePrefix("file://").substringAfter(buildRoot)}")
                            }
                            // 同步 WebView 历史栈状态到 Compose state，
                            // 驱动返回/前进按钮的启用/禁用
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                            // 注入 JS Bridge 桥接：把页面内点击/错误统一桥接到 AppLogger
                            injectPreviewBridge(view)
                        }
                    }

                    // 注册 JS Bridge：preview_page 是 JS 端调用的对象名
                    addJavascriptInterface(PreviewJsBridge(buildRoot), "GrideaPreview")

                    // 不在此处 loadUrl：forceRebuild 会先删除输出目录，
                    // 渲染完成前加载会 ERR_FILE_NOT_FOUND。
                    // 由 LaunchedEffect(isPreviewRendering) 在渲染完成后首次加载。
                    webViewRef = this
                }
            }
        )
    }

    // 页面销毁时释放 WebView，避免内存泄漏
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                settings.javaScriptEnabled = false
                loadUrl("about:blank")
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
            webViewRef = null
        }
    }
}

/**
 * 注入预览页 JS Bridge：桥接页面内点击/图片放大/代码复制/资源错误等到 AppLogger
 */
private fun injectPreviewBridge(webView: WebView?) {
    val script = """
        (function() {
            if (window.__grideaPreviewInjected) return;
            window.__grideaPreviewInjected = true;

            // 1. 拦截 a 标签点击，记录导航意图
            document.addEventListener('click', function(e) {
                try {
                    var a = e.target && e.target.closest ? e.target.closest('a') : null;
                    if (a && a.href) {
                        if (window.GrideaPreview && window.GrideaPreview.logClick) {
                            window.GrideaPreview.logClick(a.innerText || a.textContent || '', a.href);
                        }
                    }
                } catch (err) {
                    if (window.GrideaPreview && window.GrideaPreview.logError) {
                        window.GrideaPreview.logError('ClickHook: ' + (err && err.message || err));
                    }
                }
            }, true);

            // 2. 拦截 JS 运行时错误
            window.addEventListener('error', function(e) {
                if (window.GrideaPreview && window.GrideaPreview.logError) {
                    window.GrideaPreview.logError(
                        (e.message || 'Unknown') + ' @ ' + (e.filename || '?') + ':' + (e.lineno || 0)
                    );
                }
            });

            // 3. 拦截资源加载错误（404 等）
            window.addEventListener('error', function(e) {
                var t = e.target;
                if (t && (t.tagName === 'IMG' || t.tagName === 'SCRIPT' || t.tagName === 'LINK')) {
                    if (window.GrideaPreview && window.GrideaPreview.logResourceError) {
                        window.GrideaPreview.logResourceError(t.tagName, t.src || t.href || '?');
                    }
                }
            }, true);

            // 4. 标记页面已注入
            if (window.GrideaPreview && window.GrideaPreview.onReady) {
                window.GrideaPreview.onReady(document.title || location.pathname);
            }
        })();
    """.trimIndent()
    webView?.evaluateJavascript(script, null)
}

/**
 * 预览页 JS Bridge：把 JS 端事件桥接到 AppLogger
 *
 * 注意：方法名后缀如果是 JsBridge 的 Java 方法，必须用 @JavascriptInterface 标注
 */
class PreviewJsBridge(private val buildRoot: String) {

    @JavascriptInterface
    fun logClick(text: String, href: String) {
        val rel = href.removePrefix("file://").substringAfter(buildRoot)
        AppLogger.action("Preview", "Click", "[$text] → $rel")
    }

    @JavascriptInterface
    fun logError(msg: String) {
        AppLogger.e("Preview.JS", msg)
    }

    @JavascriptInterface
    fun logResourceError(tag: String, src: String) {
        AppLogger.w("Preview.Resource", "$tag 加载失败: $src")
    }

    @JavascriptInterface
    fun onReady(title: String) {
        AppLogger.d("Preview", "BridgeReady: $title")
    }
}
