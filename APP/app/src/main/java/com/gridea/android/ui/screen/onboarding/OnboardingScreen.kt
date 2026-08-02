package com.gridea.android.ui.screen.onboarding


import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gridea.android.ui.theme.LocalAccentColor
import kotlinx.coroutines.launch

/**
 * 单页引导数据
 */
private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String
)

private fun onboardingPages(): List<OnboardingPage> = listOf(
    OnboardingPage(
        icon = Icons.Filled.AutoAwesome,
        title = "欢迎使用 Gridea",
        description = "Gridea 是一个安卓端的博客写作与发布工具，让你随时随地记录想法、撰写文章并发布到自己的站点。"
    ),
    OnboardingPage(
        icon = Icons.Filled.EditNote,
        title = "沉浸式写作",
        description = "支持 Markdown 编辑、实时预览与自动保存，专注内容创作，再也不用担心灵感丢失。"
    ),
    OnboardingPage(
        icon = Icons.Filled.Web,
        title = "一键生成站点",
        description = "内置多套主题模板，一键生成静态网站，自由定制属于你的个性化博客。"
    ),
    OnboardingPage(
        icon = Icons.Filled.CloudUpload,
        title = "多平台部署",
        description = "支持 GitHub、Gitee、SFTP、Netlify、Vercel 等多平台部署，让你的博客快速上线。"
    )
)

/**
 * 首次启动引导页
 *
 * 使用 HorizontalPager 左右滑动多页引导，底部含页面指示器与操作按钮，
 * 最后一页显示「开始使用」。全屏显示，不包含底部导航栏。
 *
 * @param onComplete 引导完成（点击「跳过」或「开始使用」）回调，
 * 由调用方负责持久化完成状态并切换到主界面。
 */
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pages = remember { onboardingPages() }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val accentColor = LocalAccentColor.current

    val isLastPage = pagerState.currentPage == pages.size - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部：跳过按钮（最后一页隐藏）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            if (!isLastPage) {
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "跳过",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // 中部：多页引导内容，占据剩余空间
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { pageIndex ->
            OnboardingPageContent(pages[pageIndex])
        }

        // 底部：页面指示器 + 操作按钮
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 页面指示器：小圆点，当前页放大并用强调色
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    val selected = pagerState.currentPage == index
                    val dotSize by animateDpAsState(
                        targetValue = if (selected) 10.dp else 8.dp,
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "dotSize"
                    )
                    val dotColor = if (selected) {
                        accentColor
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(dotSize)
                            .background(dotColor, CircleShape)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // 操作按钮：最后一页「开始使用」，其余页「下一步」
            Button(
                onClick = {
                    if (isLastPage) {
                        onComplete()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(
                    text = if (isLastPage) "开始使用" else "下一步",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 单页引导内容：大图标 + 标题 + 描述
 */
@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    val accentColor = LocalAccentColor.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 大图标：圆形半透明强调色背景 + 居中图标
        Surface(
            shape = CircleShape,
            color = accentColor.copy(alpha = 0.12f),
            modifier = Modifier.size(140.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(72.dp)
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            lineHeight = 26.sp
        )
    }
}
