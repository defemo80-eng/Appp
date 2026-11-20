package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.data.local.VideoWithChannel
import com.example.data.model.QualityVariant
import com.example.data.model.VideoSourceType
import com.example.data.model.formatViewCount
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.DeiPlayVideoSurface
import com.example.ui.components.EmptyStateView
import com.example.ui.components.YouTubeEmbedPlayer
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun ShortsScreen(
    viewModel: MainViewModel,
    onOpenChannel: (String) -> Unit,
    onOpenPublishShort: () -> Unit
) {
    val context = LocalContext.current
    val shorts by viewModel.publicShorts.collectAsStateWithLifecycle()
    val fallbackVideos by viewModel.publicVideos.collectAsStateWithLifecycle()
    val feed = if (shorts.isNotEmpty()) shorts else fallbackVideos.take(10)

    if (feed.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeiPlayBlack)
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateView(
                icon = Icons.Filled.Bolt,
                title = "Sin Shorts disponibles aún",
                subtitle = "Publica tu primer Short vertical o sincroniza contenido mundial.",
                actionLabel = "Publicar Short",
                onAction = onOpenPublishShort
            )
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { feed.size })
    val shortsPlayer = remember {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    var isPaused by remember { mutableStateOf(false) }
    var activeCommentsVideo by remember { mutableStateOf<VideoWithChannel?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            shortsPlayer.release()
        }
    }

    LaunchedEffect(pagerState.currentPage, feed) {
        val current = feed.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        isPaused = false
        if (current.video.sourceType == VideoSourceType.YOUTUBE_EMBED.name) {
            shortsPlayer.pause()
            shortsPlayer.clearMediaItems()
        } else {
            shortsPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(current.video.primaryVideoUrl)))
            shortsPlayer.prepare()
            shortsPlayer.playWhenReady = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = feed[page]
            val isCurrent = page == pagerState.currentPage
            val isYouTubeShort = item.video.sourceType == VideoSourceType.YOUTUBE_EMBED.name
            val isLiked by viewModel.observeIsLiked(item.video.id).collectAsStateWithLifecycle(initialValue = false)
            val isSubscribed by remember(item.channel?.id) {
                item.channel?.let { viewModel.observeIsSubscribed(it.id) } ?: kotlinx.coroutines.flow.flowOf(false)
            }.collectAsStateWithLifecycle(initialValue = false)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = !isYouTubeShort) {
                        if (isCurrent && !isYouTubeShort) {
                            if (shortsPlayer.isPlaying) {
                                shortsPlayer.pause()
                                isPaused = true
                            } else {
                                shortsPlayer.play()
                                isPaused = false
                            }
                        }
                    }
            ) {
                if (isCurrent) {
                    if (isYouTubeShort) {
                        val ytId = item.video.id.removePrefix("yt_")
                        YouTubeEmbedPlayer(
                            videoId = ytId,
                            isShort = true,
                            showOpenExternalPill = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        DeiPlayVideoSurface(
                            player = shortsPlayer,
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.80f)
                                )
                            )
                        )
                )

                if (isPaused && isCurrent) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Reanudar Short",
                        tint = DeiPlayWhite.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(12.dp)
                    )
                }

                // Right interaction rail
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 92.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ShortActionButton(
                        icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        label = formatViewCount(item.video.likeCount),
                        tint = if (isLiked) DeiPlayRedBright else DeiPlayWhite,
                        onClick = { viewModel.toggleLike(item.video.id) }
                    )
                    ShortActionButton(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        label = formatViewCount(item.video.commentCount),
                        tint = DeiPlayWhite,
                        onClick = { activeCommentsVideo = item }
                    )
                    ShortActionButton(
                        icon = Icons.Outlined.Share,
                        label = "Compartir",
                        tint = DeiPlayWhite,
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, "Mira este Short en DeiPlay: ${item.video.title} ${item.video.primaryVideoUrl}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Compartir Short"))
                        }
                    )
                    if (item.video.downloadAllowed) {
                        ShortActionButton(
                            icon = Icons.Outlined.Download,
                            label = "Descargar",
                            tint = DeiPlayGold,
                            onClick = {
                                val variant = item.video.qualityVariants().firstOrNull()
                                    ?: QualityVariant("Original", item.video.primaryVideoUrl)
                                viewModel.startDownload(item, variant)
                            }
                        )
                    }
                }

                // Bottom metadata & channel bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.78f)
                        .padding(start = 14.dp, bottom = 92.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChannelAvatar(
                            avatarUrl = item.channel?.avatarUrl.orEmpty(),
                            name = item.channel?.name ?: item.video.sourceAttribution,
                            size = 38.dp,
                            modifier = Modifier.clickable { item.channel?.let { onOpenChannel(it.id) } }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = item.channel?.handle ?: item.video.sourceAttribution,
                            style = MaterialTheme.typography.titleSmall,
                            color = DeiPlayWhite,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { item.channel?.let { onOpenChannel(it.id) } }
                        )
                        if (item.channel != null) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSubscribed) DeiPlaySurfaceHigh else DeiPlayRed,
                                modifier = Modifier.clickable { viewModel.toggleSubscribe(item.channel) }
                            ) {
                                Text(
                                    text = if (isSubscribed) "Suscrito" else "Suscribirse",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DeiPlayWhite,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = item.video.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = DeiPlayWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${item.video.sourceAttribution} • ${item.video.language}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DeiPlayGold
                    )
                }
            }
        }

        // Top Shorts header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Bolt, null, tint = DeiPlayRedBright)
            Spacer(Modifier.width(6.dp))
            Text("DeiPlay Shorts", style = MaterialTheme.typography.titleLarge, color = DeiPlayWhite, modifier = Modifier.weight(1f))
            FilledTonalButton(
                onClick = onOpenPublishShort,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = DeiPlayRed)
            ) {
                Icon(Icons.Filled.Add, null, tint = DeiPlayWhite, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Publicar Short", color = DeiPlayWhite, style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    activeCommentsVideo?.let { target ->
        val comments by viewModel.observeComments(target.video.id).collectAsStateWithLifecycle(initialValue = emptyList())
        var input by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { activeCommentsVideo = null },
            containerColor = DeiPlaySurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text("Comentarios (${comments.size})", style = MaterialTheme.typography.titleMedium, color = DeiPlayWhite)
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(comments, key = { it.id }) { c ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(c.authorName, style = MaterialTheme.typography.labelSmall, color = DeiPlayGold)
                            Text(c.text, style = MaterialTheme.typography.bodyMedium, color = DeiPlayWhite)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Comentar este Short...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.postComment(target.video.id, input) {
                                if (it.isSuccess) input = ""
                            }
                        },
                        modifier = Modifier.background(DeiPlayRed, CircleShape)
                    ) {
                        Icon(Icons.Filled.Send, null, tint = DeiPlayWhite)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = tint, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = DeiPlayWhite)
    }
}
