package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CommentEntity
import com.example.data.local.VideoWithChannel
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@Composable
fun WatchScreen(
    videoId: String,
    viewModel: MainViewModel,
    inSystemPipMode: Boolean,
    onEnterSystemPip: () -> Unit,
    onMinimizeToFloating: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val observedItem by viewModel.observeVideo(videoId).collectAsStateWithLifecycle(initialValue = null)
    val allVideos by viewModel.publicVideos.collectAsStateWithLifecycle()
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val isLiked by viewModel.observeIsLiked(videoId).collectAsStateWithLifecycle(initialValue = false)
    val isInWatchLater by viewModel.observeIsInWatchLater(videoId).collectAsStateWithLifecycle(initialValue = false)
    val comments by viewModel.observeComments(videoId).collectAsStateWithLifecycle(initialValue = emptyList())
    val download by viewModel.observeDownload(videoId).collectAsStateWithLifecycle(initialValue = null)
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()

    val item = playerState.currentVideo?.takeIf { it.video.id == videoId } ?: observedItem
    val channel = item?.channel
    val isSubscribed by remember(channel?.id) {
        if (channel != null) viewModel.observeIsSubscribed(channel.id) else kotlinx.coroutines.flow.flowOf(false)
    }.collectAsStateWithLifecycle(initialValue = false)

    LaunchedEffect(videoId, observedItem) {
        val loaded = observedItem ?: return@LaunchedEffect
        if (playerState.currentVideo?.video?.id != videoId) {
            val related = allVideos.filter { it.video.id != videoId }
            viewModel.playVideo(loaded, related)
        }
    }

    // Handle orientation changes on fullscreen toggle
    LaunchedEffect(playerState.isFullscreen) {
        activity?.requestedOrientation = if (playerState.isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler {
        if (playerState.isFullscreen) {
            viewModel.playerController.setFullscreen(false)
        } else {
            onMinimizeToFloating()
            onBack()
        }
    }

    var descriptionExpanded by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<CommentEntity?>(null) }
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    if (item == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeiPlayBlack),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = DeiPlayRed)
        }
        return
    }

    val video = item.video
    val relatedVideos = remember(allVideos, videoId) {
        allVideos.filter { it.video.id != videoId }.take(15)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeiPlayBlack)
            .let { if (!playerState.isFullscreen && !inSystemPipMode) it.statusBarsPadding() else it }
    ) {
        DailyTubePlayerBox(
            controller = viewModel.playerController,
            state = playerState,
            inSystemPipMode = inSystemPipMode,
            onMinimize = {
                onMinimizeToFloating()
                onBack()
            },
            onEnterSystemPip = onEnterSystemPip,
            onToggleFullscreen = {
                viewModel.playerController.setFullscreen(!playerState.isFullscreen)
            },
            modifier = if (playerState.isFullscreen || inSystemPipMode) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            }
        )

        if (playerState.isFullscreen || inSystemPipMode) return@Column

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Title, views, source attribution
            item {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourceAttributionBadge(
                            sourceAttribution = video.sourceAttribution,
                            isExternal = video.sourceType != VideoSourceType.DEIPLAY_USER.name
                        )
                        Text(
                            text = video.licenseName,
                            style = MaterialTheme.typography.labelSmall,
                            color = DeiPlayGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = DeiPlayWhite
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${formatViewCount(video.viewCount)} vistas • ${formatRelativeDate(video.createdAt)} • ${video.category} • ${video.language} (${video.country})",
                        style = MaterialTheme.typography.bodySmall,
                        color = DeiPlayTextSecondary
                    )
                }
            }

            // Action Buttons Bar
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        ActionPillButton(
                            icon = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            label = formatViewCount(video.likeCount),
                            active = isLiked,
                            onClick = { viewModel.toggleLike(video.id) },
                            testTag = "watch_like_button"
                        )
                    }
                    item {
                        ActionPillButton(
                            icon = Icons.Outlined.Share,
                            label = "Compartir",
                            active = false,
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, "Mira '${video.title}' en DeiPlay: ${video.primaryVideoUrl}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Compartir video"))
                            }
                        )
                    }
                    item {
                        ActionPillButton(
                            icon = if (isInWatchLater) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            label = if (isInWatchLater) "Guardado" else "Ver después",
                            active = isInWatchLater,
                            onClick = { viewModel.toggleWatchLater(video.id, isInWatchLater) }
                        )
                    }
                    item {
                        ActionPillButton(
                            icon = Icons.Outlined.PlaylistAdd,
                            label = "Lista",
                            active = false,
                            onClick = {
                                if (activeAccount == null) viewModel.openAuthModal()
                                else showPlaylistDialog = true
                            }
                        )
                    }
                    if (video.downloadAllowed) {
                        item {
                            val dl = download
                            val dlStatus = dl?.status
                            val dlLabel = when (dlStatus) {
                                DownloadStatus.DOWNLOADING.name -> {
                                    val pct = if ((dl?.totalBytes ?: 0L) > 0L) {
                                        ((dl!!.bytesDownloaded * 100) / dl.totalBytes).toInt()
                                    } else 0
                                    "Descargando $pct%"
                                }
                                DownloadStatus.PAUSED.name -> "Pausado (Reanudar)"
                                DownloadStatus.COMPLETED.name -> "Descargado"
                                else -> "Descargar"
                            }
                            ActionPillButton(
                                icon = when (dlStatus) {
                                    DownloadStatus.COMPLETED.name -> Icons.Filled.DownloadDone
                                    DownloadStatus.DOWNLOADING.name -> Icons.Filled.Pause
                                    else -> Icons.Outlined.Download
                                },
                                label = dlLabel,
                                active = dlStatus == DownloadStatus.COMPLETED.name,
                                onClick = {
                                    when (dlStatus) {
                                        DownloadStatus.DOWNLOADING.name -> viewModel.pauseDownload(video.id)
                                        DownloadStatus.PAUSED.name, DownloadStatus.FAILED.name -> {
                                            val variant = playerState.availableQualities.firstOrNull {
                                                it.label == download?.qualityLabel
                                            } ?: playerState.availableQualities.first()
                                            viewModel.startDownload(item, variant)
                                        }
                                        DownloadStatus.COMPLETED.name -> viewModel.emitMessage("Disponible en la sección Descargas para ver sin conexión")
                                        else -> showDownloadQualityDialog = true
                                    }
                                },
                                testTag = "watch_download_button"
                            )
                        }
                    }
                    item {
                        ActionPillButton(
                            icon = Icons.Outlined.Flag,
                            label = "Reportar",
                            active = false,
                            onClick = { showReportDialog = true }
                        )
                    }
                }
            }

            // Channel card
            if (channel != null) {
                item {
                    Surface(
                        color = DeiPlaySurfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .clickable { onOpenChannel(channel.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChannelAvatar(avatarUrl = channel.avatarUrl, name = channel.name, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(channel.name, style = MaterialTheme.typography.titleMedium, color = DeiPlayWhite)
                                    if (channel.isVerified) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Filled.Verified, null, tint = DeiPlayGold, modifier = Modifier.size(15.dp))
                                    }
                                }
                                Text(
                                    text = "${channel.handle} • ${formatViewCount(channel.subscriberCount)} suscriptores",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DeiPlayTextSecondary
                                )
                            }
                            Button(
                                onClick = { viewModel.toggleSubscribe(channel) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSubscribed) DeiPlaySurfaceHigh else DeiPlayRed
                                ),
                                modifier = Modifier.testTag("watch_subscribe_button")
                            ) {
                                Text(
                                    text = if (isSubscribed) "Suscrito" else "Suscribirse",
                                    color = DeiPlayWhite
                                )
                            }
                        }
                    }
                }
            }

            // Description expandable
            item {
                Surface(
                    color = DeiPlaySurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .clickable { descriptionExpanded = !descriptionExpanded }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = video.description.ifBlank { "Sin descripción adicional." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = DeiPlayTextSecondary,
                            maxLines = if (descriptionExpanded) 50 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (video.tagsCsv.isNotBlank() && descriptionExpanded) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Etiquetas: ${video.tagsCsv}",
                                style = MaterialTheme.typography.labelSmall,
                                color = DeiPlayGold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (descriptionExpanded) "Mostrar menos" else "Ver descripción completa",
                            style = MaterialTheme.typography.labelMedium,
                            color = DeiPlayGold
                        )
                    }
                }
            }

            // Comments section
            item {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Comentarios (${comments.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = DeiPlayWhite
                    )
                    if (replyingTo != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Text(
                                text = "Respondiendo a ${replyingTo!!.authorName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = DeiPlayGold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { replyingTo = null }) {
                                Text("Cancelar", color = DeiPlayRedBright)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            placeholder = { Text("Agrega un comentario...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("watch_comment_input")
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                viewModel.postComment(video.id, commentText, replyingTo?.id) { res ->
                                    if (res.isSuccess) {
                                        commentText = ""
                                        replyingTo = null
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(DeiPlayRed, RoundedCornerShape(10.dp))
                                .testTag("watch_comment_send_button")
                        ) {
                            Icon(Icons.Filled.Send, "Publicar comentario", tint = DeiPlayWhite)
                        }
                    }
                }
            }

            val topComments = comments.filter { it.parentCommentId == null }
            items(topComments, key = { it.id }) { comment ->
                val replies = comments.filter { it.parentCommentId == comment.id }.reversed()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    CommentRow(
                        comment = comment,
                        onReply = { replyingTo = comment },
                        onReport = {
                            viewModel.submitReport("COMMENT", comment.id, comment.text.take(40), "Comentario reportado")
                        }
                    )
                    replies.forEach { reply ->
                        Box(modifier = Modifier.padding(start = 36.dp, top = 6.dp)) {
                            CommentRow(
                                comment = reply,
                                onReply = { replyingTo = comment },
                                onReport = {
                                    viewModel.submitReport("COMMENT", reply.id, reply.text.take(40), "Respuesta reportada")
                                }
                            )
                        }
                    }
                }
            }

            // Related Videos
            item {
                SectionHeader(
                    icon = Icons.Outlined.SmartDisplay,
                    iconTint = DeiPlayGold,
                    title = "Videos relacionados",
                    actionLabel = null,
                    onAction = null
                )
            }

            items(relatedVideos, key = { "rel_${it.video.id}" }) { rel ->
                VideoCard(
                    item = rel,
                    onClick = {
                        viewModel.playVideo(rel, allVideos.filter { it.video.id != rel.video.id })
                    },
                    onChannelClick = onOpenChannel,
                    onSaveWatchLater = { viewModel.toggleWatchLater(rel.video.id, false) },
                    onShare = { },
                    onReport = { },
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }
    }

    if (showDownloadQualityDialog) {
        val qualities = playerState.availableQualities.ifEmpty {
            listOf(QualityVariant("Original", video.primaryVideoUrl))
        }
        AlertDialog(
            onDismissRequest = { showDownloadQualityDialog = false },
            containerColor = DeiPlaySurface,
            title = { Text("Descargar video autorizado", color = DeiPlayWhite) },
            text = {
                Column {
                    Text(
                        text = "Licencia: ${video.licenseName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DeiPlayGold
                    )
                    Spacer(Modifier.height(8.dp))
                    qualities.forEach { variant ->
                        val sizeStr = if (variant.sizeBytes > 0) " • ${Formatter.formatFileSize(context, variant.sizeBytes)}" else ""
                        ListItem(
                            headlineContent = { Text("${variant.label}$sizeStr", color = DeiPlayWhite) },
                            leadingContent = { Icon(Icons.Outlined.Download, null, tint = DeiPlayRed) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.clickable {
                                showDownloadQualityDialog = false
                                viewModel.startDownload(item, variant)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadQualityDialog = false }) { Text("Cancelar", color = DeiPlayTextSecondary) }
            }
        )
    }

    if (showPlaylistDialog) {
        var newListName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            containerColor = DeiPlaySurface,
            title = { Text("Guardar en lista de reproducción", color = DeiPlayWhite) },
            text = {
                Column {
                    userPlaylists.forEach { pl ->
                        ListItem(
                            headlineContent = { Text(pl.title, color = DeiPlayWhite) },
                            leadingContent = { Icon(Icons.Outlined.PlaylistPlay, null, tint = DeiPlayGold) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.clickable {
                                viewModel.addVideoToPlaylist(pl.id, video.id)
                                showPlaylistDialog = false
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newListName,
                            onValueChange = { newListName = it },
                            label = { Text("Nueva lista") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                if (newListName.isNotBlank()) {
                                    viewModel.createPlaylist(newListName, "", true)
                                    newListName = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed)
                        ) { Text("Crear", color = DeiPlayWhite) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) { Text("Cerrar", color = DeiPlayTextSecondary) }
            }
        )
    }

    if (showReportDialog) {
        ReportContentDialog(
            title = video.title,
            onConfirm = { reason ->
                viewModel.submitReport("VIDEO", video.id, video.title, reason)
                showReportDialog = false
            },
            onDismiss = { showReportDialog = false }
        )
    }
}

@Composable
private fun ActionPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (active) DeiPlayRedDeep else DeiPlaySurfaceVariant,
        border = BorderStroke(0.75.dp, if (active) DeiPlayRed else DeiPlayOutline),
        modifier = Modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = if (active) DeiPlayGold else DeiPlayWhite, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = DeiPlayWhite)
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentEntity,
    onReply: () -> Unit,
    onReport: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        ChannelAvatar(avatarUrl = comment.authorAvatar, name = comment.authorName, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${comment.authorName} • ${formatRelativeDate(comment.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = DeiPlayTextMuted
            )
            Spacer(Modifier.height(2.dp))
            Text(comment.text, style = MaterialTheme.typography.bodyMedium, color = DeiPlayWhite)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Responder",
                    style = MaterialTheme.typography.labelSmall,
                    color = DeiPlayGold,
                    modifier = Modifier
                        .clickable(onClick = onReply)
                        .padding(vertical = 4.dp)
                )
                Text(
                    text = "Reportar",
                    style = MaterialTheme.typography.labelSmall,
                    color = DeiPlayTextMuted,
                    modifier = Modifier
                        .clickable(onClick = onReport)
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}
