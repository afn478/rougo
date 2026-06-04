package com.selxo.rougo

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.util.Size
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeBrowserScreen(
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    fun updateNavigationState(view: WebView?) {
        canGoBack = view?.canGoBack() == true
    }

    fun openVideoIfPlayable(rawUrl: String?): Boolean {
        val videoUrl = rawUrl?.let { playableYoutubeUrl(it) } ?: return false
        stopYoutubeWebViewPlayback(webView)
        onOpenVideo(videoUrl)
        return true
    }

    fun installYoutubeBridge(view: WebView?) {
        view?.evaluateJavascript(youtubeBrowserInterceptScript(), null)
    }

    BackHandler {
        val view = webView
        if (view?.canGoBack() == true) {
            view.goBack()
            updateNavigationState(view)
        } else {
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.webChromeClient = null
            webView?.webViewClient = WebViewClient()
            webView?.removeJavascriptInterface("RougoYoutube")
            webView?.destroy()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YouTube", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        val view = webView
                        if (view?.canGoBack() == true) {
                            view.goBack()
                            updateNavigationState(view)
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.loadUrl("https://m.youtube.com/") }) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                    IconButton(onClick = {
                        val view = webView ?: return@IconButton
                        if (isLoading) view.stopLoading() else view.reload()
                    }) {
                        Icon(
                            if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                            contentDescription = if (isLoading) "Stop" else "Refresh"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(context).apply {
                        webView = this
                        setBackgroundColor(android.graphics.Color.BLACK)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadsImagesAutomatically = true
                            mediaPlaybackRequiresUserGesture = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            setSupportMultipleWindows(false)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            settings.safeBrowsingEnabled = true
                        }
                        addJavascriptInterface(
                            YoutubeBrowserBridge { url -> openVideoIfPlayable(url) },
                            "RougoYoutube"
                        )

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                if (request?.isForMainFrame == false) return false
                                return openVideoIfPlayable(request?.url?.toString())
                            }

                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                return openVideoIfPlayable(url)
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                if (openVideoIfPlayable(url)) {
                                    view?.stopLoading()
                                    return
                                }
                                installYoutubeBridge(view)
                                updateNavigationState(view)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                if (openVideoIfPlayable(url)) {
                                    view?.stopLoading()
                                    return
                                }
                                isLoading = true
                                updateNavigationState(view)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                CookieManager.getInstance().flush()
                                installYoutubeBridge(view)
                                updateNavigationState(view)
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                isLoading = newProgress < 100
                                if (newProgress >= 25) installYoutubeBridge(view)
                                if (newProgress >= 100) openVideoIfPlayable(view?.url)
                                updateNavigationState(view)
                            }
                        }
                        loadUrl("https://m.youtube.com/")
                    }
                }
            )

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
