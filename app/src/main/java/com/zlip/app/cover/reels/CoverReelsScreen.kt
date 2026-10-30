package com.zlip.app.cover.reels

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zlip.app.R
import com.zlip.app.data.CoverSettings
import com.zlip.app.data.MediaFit

private const val REELS_URL = "https://www.instagram.com/reels/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CoverReelsScreen(
    settings: CoverSettings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fitMode by remember { mutableStateOf(settings.reelsDefaultFit) }
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }

    fun injectFitCss(wv: WebView, fit: MediaFit) {
        val cssMode = if (fit == MediaFit.FIT) "contain" else "cover"
        val js = """
            (function() {
                var mode = '$cssMode';
                var style = document.getElementById('zlip-reels-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'zlip-reels-style';
                    document.head.appendChild(style);
                }
                style.innerHTML = 'header, nav, [role="navigation"], footer, [role="banner"], ._a9-z, ._a9-- { display: none !important; } ' +
                    'video { object-fit: ' + mode + ' !important; width: 100vw !important; height: 100vh !important; max-height: 100vh !important; background: black !important; } ' +
                    'body, html { background: black !important; overflow-x: hidden !important; }';
                var videos = document.querySelectorAll('video');
                for (var i = 0; i < videos.length; i++) {
                    videos[i].style.objectFit = mode;
                }
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    LaunchedEffect(fitMode) {
        webViewRef?.let { wv -> injectFitCss(wv, fitMode) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    this.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        // Clean user-agent so mobile Instagram displays cleanly
                        userAgentString = userAgentString.replace("; wv", "")
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            view?.let { wv ->
                                injectFitCss(wv, fitMode)
                                // Keep new Reels video elements formatted as user scrolls
                                val intervalJs = """
                                    if (!window.zlipIntervalStarted) {
                                        window.zlipIntervalStarted = true;
                                        setInterval(function() {
                                            var style = document.getElementById('zlip-reels-style');
                                            var mode = style ? (style.innerHTML.indexOf('contain') !== -1 ? 'contain' : 'cover') : 'contain';
                                            var vids = document.querySelectorAll('video');
                                            for (var i = 0; i < vids.length; i++) {
                                                if (vids[i].style.objectFit !== mode) {
                                                    vids[i].style.objectFit = mode;
                                                }
                                            }
                                        }, 1000);
                                    }
                                """.trimIndent()
                                wv.evaluateJavascript(intervalJs, null)
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return false // Stay in webview
                        }
                    }
                    loadUrl(REELS_URL)
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top gradient scrim for control buttons
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(72.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))),
        )

        // Control buttons bar
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.reels_title),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Fit / Fill button
                    val isFit = fitMode == MediaFit.FIT
                    ReelsControlButton(
                        icon = if (isFit) Icons.Filled.ZoomOutMap else Icons.Filled.FitScreen,
                        description = stringResource(if (isFit) R.string.reels_fill_desc else R.string.reels_fit_desc),
                        onClick = {
                            fitMode = if (fitMode == MediaFit.FIT) MediaFit.FILL else MediaFit.FIT
                        },
                    )

                    // Zoom in
                    ReelsControlButton(
                        icon = Icons.Filled.ZoomIn,
                        description = stringResource(R.string.media_zoom_in),
                        onClick = { webViewRef?.zoomIn() },
                    )

                    // Zoom out
                    ReelsControlButton(
                        icon = Icons.Filled.ZoomOut,
                        description = stringResource(R.string.media_zoom_out),
                        onClick = { webViewRef?.zoomOut() },
                    )

                    // Refresh
                    ReelsControlButton(
                        icon = Icons.Filled.Refresh,
                        description = "Reload",
                        onClick = { webViewRef?.reload() },
                    )

                    // Close
                    ReelsControlButton(
                        icon = Icons.Filled.Close,
                        description = stringResource(R.string.cover_close),
                        onClick = onClose,
                    )
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                )
            }
        }
    }
}

@Composable
private fun ReelsControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(34.dp)
            .background(Color.Black.copy(alpha = 0.65f), CircleShape),
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}
