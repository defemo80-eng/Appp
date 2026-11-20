package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.DeiPlayBlack
import com.example.ui.theme.DeiPlayGold
import com.example.ui.theme.DeiPlayWhite
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    isShort: Boolean = false,
    showOpenExternalPill: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appOrigin = remember(context.packageName) { buildYouTubeAppOrigin(context.packageName) }

    Box(
        modifier = modifier.background(Color.Black)
    ) {
        var webViewRef by remember { mutableStateOf<WebView?>(null) }

        DisposableEffect(Unit) {
            onDispose {
                webViewRef?.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    destroy()
                }
                webViewRef = null
            }
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)

                    runCatching {
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        javaScriptCanOpenWindowsAutomatically = true
                        val ua = userAgentString.orEmpty()
                        if (ua.contains("; wv")) {
                            userAgentString = ua.replace("; wv", "")
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun getDefaultVideoPoster(): Bitmap {
                            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val targetUri = request?.url ?: return false
                            val host = targetUri.host.orEmpty().lowercase()
                            if (host.contains("youtube.com") || host.contains("youtu.be")) {
                                val path = targetUri.path.orEmpty()
                                if (path.startsWith("/watch") || path.startsWith("/shorts")) {
                                    runCatching {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, targetUri))
                                    }
                                    return true
                                }
                            }
                            return false
                        }
                    }
                    webViewRef = this
                }
            },
            update = { webView ->
                val expectedKey = "$videoId|$isShort|$appOrigin"
                if (webView.tag != expectedKey && videoId.isNotBlank()) {
                    webView.tag = expectedKey
                    val html = buildYouTubeEmbedHtml(videoId, isShort, appOrigin)
                    webView.loadDataWithBaseURL(
                        "$appOrigin/",
                        html,
                        "text/html",
                        "utf-8",
                        null
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showOpenExternalPill && videoId.isNotBlank()) {
            Surface(
                color = DeiPlayBlack.copy(alpha = 0.72f),
                shape = RoundedCornerShape(16.dp),
                onClick = {
                    val url = if (isShort) {
                        "https://www.youtube.com/shorts/$videoId"
                    } else {
                        "https://www.youtube.com/watch?v=$videoId"
                    }
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Abrir en YouTube",
                        tint = DeiPlayGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "YouTube",
                        style = MaterialTheme.typography.labelSmall,
                        color = DeiPlayWhite
                    )
                }
            }
        }
    }
}

internal fun buildYouTubeAppOrigin(packageName: String): String {
    val cleanPkg = packageName.lowercase().replace(Regex("[^a-z0-9._-]"), "").ifBlank { "com.deiplay.app" }
    return "https://$cleanPkg"
}

internal fun buildYouTubeEmbedHtml(
    videoId: String,
    isShort: Boolean,
    appOrigin: String
): String {
    val safeId = videoId.replace(Regex("[^a-zA-Z0-9_-]"), "")
    val encodedOrigin = URLEncoder.encode(appOrigin, "UTF-8")
    val loopParam = if (isShort) "&loop=1&playlist=$safeId" else ""
    val embedSrc =
        "https://www.youtube.com/embed/$safeId?autoplay=1&playsinline=1&rel=0&modestbranding=1&iv_load_policy=3&enablejsapi=1&origin=$encodedOrigin&widget_referrer=$encodedOrigin$loopParam"

    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8"/>
          <meta name="referrer" content="strict-origin-when-cross-origin"/>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
          <style>
            html, body {
              margin: 0;
              padding: 0;
              width: 100%;
              height: 100%;
              background-color: #000000;
              overflow: hidden;
            }
            iframe {
              position: absolute;
              top: 0;
              left: 0;
              width: 100%;
              height: 100%;
              border: 0;
            }
          </style>
        </head>
        <body>
          <iframe
            src="$embedSrc"
            referrerpolicy="strict-origin-when-cross-origin"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            allowfullscreen>
          </iframe>
        </body>
        </html>
    """.trimIndent()
}
