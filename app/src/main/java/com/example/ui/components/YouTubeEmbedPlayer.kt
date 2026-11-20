package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    isShort: Boolean = false,
    showOpenExternalPill: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var lastLoadedId by remember { mutableStateOf("") }

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
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    webViewRef = this
                }
            },
            update = { webView ->
                if (lastLoadedId != videoId && videoId.isNotBlank()) {
                    lastLoadedId = videoId
                    val html = buildYouTubeEmbedHtml(videoId, isShort)
                    webView.loadDataWithBaseURL(
                        "https://www.youtube.com",
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

private fun buildYouTubeEmbedHtml(videoId: String, isShort: Boolean): String {
    val safeId = videoId.replace(Regex("[^a-zA-Z0-9_-]"), "")
    val loopParam = if (isShort) "&loop=1&playlist=$safeId" else ""
    return """
        <!DOCTYPE html>
        <html>
        <head>
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
            src="https://www.youtube.com/embed/$safeId?autoplay=1&playsinline=1&rel=0&modestbranding=1$loopParam"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            allowfullscreen>
          </iframe>
        </body>
        </html>
    """.trimIndent()
}
