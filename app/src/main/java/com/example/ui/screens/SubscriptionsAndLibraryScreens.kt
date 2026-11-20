package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.ChannelEntity
import com.example.data.local.DownloadEntity
import com.example.data.local.VideoEntity
import com.example.data.local.VideoWithChannel
import com.example.data.model.DownloadStatus
import com.example.data.model.VideoSourceType
import com.example.data.model.formatDuration
import com.example.data.model.formatRelativeDate
import com.example.data.model.formatViewCount
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.VideoCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import java.io.File

@Composable
fun SubscriptionsScreen(
    viewModel: MainViewModel,
    onWatchVideo: (VideoWithChannel) -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val subscriptions by viewModel.userSubscriptions.collectAsStateWithLifecycle()
    val channels by viewModel.activeChannels.collectAsStateWithLifecycle()
    val videos by viewModel.publicVideos.collectAsStateWithLifecycle()

    if (activeAccount == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeiPlayBlack)
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateView(
                icon = Icons.Outlined.Subscriptions,
                title = "Tus suscripciones",
                subtitle = "Inicia sesión para seguir canales de todo el mundo y recibir novedades.",
                actionLabel = "Iniciar sesión",
                onAction = { viewModel.openAuthModal() }
            )
        }
        return
    }

    val subIds = remember(subscriptions) { subscriptions.map { it.channelId }.toSet() }
    val subscribedChannels = remember(channels, subIds) { channels.filter { it.id in subIds } }
    var selectedChannelFilter by remember { mutableStateOf<String?>(null) }

    val feedVideos = remember(videos, subIds, selectedChannelFilter) {
        videos.filter {
            it.video.channelId in subIds &&
                (selectedChannelFilter == null || it.video.channelId == selectedChannelFilter)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeiPlayBlack)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            Text(
                text = "Suscripciones",
                style = MaterialTheme.typography.headlineMedium,
                color = DeiPlayWhite,
                modifier = Modifier.padding(14.dp)
            )
        }

        if (subscribedChannels.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Outlined.Subscriptions,
                    title = "Aún no sigues canales",
                    subtitle = "Explora el catálogo mundial y suscríbete a tus creadores favoritos."
                )
            }
        } else {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedChannelFilter == null,
                            onClick = { selectedChannelFilter = null },
                            label = { Text("Todos") }
                        )
                    }
                    items(subscribedChannels, key = { it.id }) { ch ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(74.dp)
                                .clickable {
                                    selectedChannelFilter = if (selectedChannelFilter == ch.id) null else ch.id
                                }
                        ) {
                            ChannelAvatar(avatarUrl = ch.avatarUrl, name = ch.name, size = 48.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = ch.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selectedChannelFilter == ch.id) DeiPlayGold else DeiPlayWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            items(feedVideos, key = { "subfeed_${it.video.id}" }) { item ->
                VideoCard(
                    item = item,
                    onClick = { onWatchVideo(item) },
                    onChannelClick = onOpenChannel,
                    onSaveWatchLater = { viewModel.toggleWatchLater(item.video.id, false) },
                    onShare = {},
                    onReport = {},
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }
    }
}

@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onWatchVideo: (VideoWithChannel) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPublish: () -> Unit,
    onOpenCreatorStudio: () -> Unit
) {
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val allVideos by viewModel.publicVideos.collectAsStateWithLifecycle()
    val userVideos by viewModel.userVideos.collectAsStateWithLifecycle()
    val userChannels by viewModel.userChannels.collectAsStateWithLifecycle()
    val history by viewModel.watchHistory.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedVideoIds.collectAsStateWithLifecycle()
    val watchLaterIds by viewModel.watchLaterIds.collectAsStateWithLifecycle()
    val playlists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    var activeSection by remember { mutableStateOf("overview") }
    var showCreateChannel by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }

    val videoMap = remember(allVideos, userVideos) {
        (allVideos + userVideos).associateBy { it.video.id }
    }
    val historyVideos = remember(history, videoMap) { history.mapNotNull { videoMap[it.videoId] } }
    val likedVideos = remember(likedIds, videoMap) { likedIds.mapNotNull { videoMap[it] } }
    val watchLaterVideos = remember(watchLaterIds, videoMap) { watchLaterIds.mapNotNull { videoMap[it] } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeiPlayBlack)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Biblioteca", style = MaterialTheme.typography.headlineMedium, color = DeiPlayWhite, modifier = Modifier.weight(1f))
                if (activeAccount != null) {
                    TextButton(onClick = onOpenCreatorStudio) {
                        Icon(Icons.Outlined.Analytics, null, tint = DeiPlayGold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Creadores", color = DeiPlayGold)
                    }
                }
            }
        }

        if (activeAccount == null) {
            item {
                Surface(
                    color = DeiPlaySurfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tu cuenta DeiPlay", style = MaterialTheme.typography.titleMedium, color = DeiPlayWhite)
                            Text("Accede para guardar historial, listas, likes y canales.", style = MaterialTheme.typography.bodySmall, color = DeiPlayTextSecondary)
                        }
                        Button(onClick = { viewModel.openAuthModal() }, colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed)) {
                            Text("Entrar", color = DeiPlayWhite)
                        }
                    }
                }
            }
        }

        // Section selector chips
        item {
            val sections = listOf(
                "overview" to "Resumen",
                "history" to "Historial (${historyVideos.size})",
                "liked" to "Me gusta (${likedVideos.size})",
                "later" to "Ver más tarde (${watchLaterVideos.size})",
                "my_videos" to "Mis videos (${userVideos.count { !it.video.isShort }})",
                "my_shorts" to "Mis Shorts (${userVideos.count { it.video.isShort }})",
                "playlists" to "Listas (${playlists.size})"
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sections) { (key, label) ->
                    FilterChip(
                        selected = activeSection == key,
                        onClick = { activeSection = key },
                        label = { Text(label) }
                    )
                }
            }
        }

        // Downloads shortcut card always accessible
        item {
            Surface(
                color = DeiPlaySurfaceVariant,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.75.dp, DeiPlayGold.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDownloads)
                    .testTag("library_downloads_card")
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DownloadForOffline, null, tint = DeiPlayGold, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Descargas sin conexión", style = MaterialTheme.typography.titleMedium, color = DeiPlayWhite)
                        Text(
                            text = "${downloads.count { it.status == DownloadStatus.COMPLETED.name }} descargados • Reproducción offline autorizada",
                            style = MaterialTheme.typography.bodySmall,
                            color = DeiPlayTextSecondary
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = DeiPlayTextSecondary)
                }
            }
        }

        when (activeSection) {
            "overview" -> {
                if (activeAccount != null) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Mis canales (${userChannels.size})", style = MaterialTheme.typography.titleLarge, color = DeiPlayWhite, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showCreateChannel = true }) {
                                Text("+ Nuevo canal", color = DeiPlayGold)
                            }
                        }
                    }
                    if (userChannels.isEmpty()) {
                        item {
                            OutlinedButton(onClick = { showCreateChannel = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Crear mi primer canal en DeiPlay", color = DeiPlayWhite)
                            }
                        }
                    } else {
                        items(userChannels, key = { "mych_${it.id}" }) { ch ->
                            ListItem(
                                leadingContent = { ChannelAvatar(ch.avatarUrl, ch.name, 44.dp) },
                                headlineContent = { Text(ch.name, color = DeiPlayWhite) },
                                supportingContent = { Text("${ch.handle} • ${formatViewCount(ch.subscriberCount)} suscriptores", color = DeiPlayTextSecondary) },
                                trailingContent = { Icon(Icons.Filled.ChevronRight, null, tint = DeiPlayTextMuted) },
                                colors = ListItemDefaults.colors(containerColor = DeiPlaySurface),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenChannel(ch.id) }
                            )
                        }
                    }
                }

                if (historyVideos.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Historial reciente", style = MaterialTheme.typography.titleLarge, color = DeiPlayWhite, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.clearWatchHistory() }) {
                                Text("Limpiar", color = DeiPlayRedBright)
                            }
                        }
                    }
                    items(historyVideos.take(6), key = { "hist_${it.video.id}" }) { item ->
                        VideoCard(
                            item = item,
                            onClick = { onWatchVideo(item) },
                            onChannelClick = onOpenChannel,
                            onSaveWatchLater = { viewModel.toggleWatchLater(item.video.id, false) },
                            onShare = {},
                            onReport = {}
                        )
                    }
                }
            }
            "history" -> {
                items(historyVideos, key = { "h_${it.video.id}" }) { item ->
                    VideoCard(item, { onWatchVideo(item) }, onOpenChannel, {}, {}, {})
                }
            }
            "liked" -> {
                items(likedVideos, key = { "l_${it.video.id}" }) { item ->
                    VideoCard(item, { onWatchVideo(item) }, onOpenChannel, {}, {}, {})
                }
            }
            "later" -> {
                items(watchLaterVideos, key = { "wl_${it.video.id}" }) { item ->
                    VideoCard(item, { onWatchVideo(item) }, onOpenChannel, {}, {}, {})
                }
            }
            "my_videos" -> {
                val myVids = userVideos.filter { !it.video.isShort }
                if (myVids.isEmpty()) {
                    item {
                        EmptyStateView(Icons.Outlined.VideoCall, "Sin videos propios", "Publica tu primer video desde la pestaña Publicar.", "Publicar", onOpenPublish)
                    }
                } else {
                    items(myVids, key = { "mv_${it.video.id}" }) { item ->
                        VideoCard(item, { onWatchVideo(item) }, onOpenChannel, {}, {}, {})
                    }
                }
            }
            "my_shorts" -> {
                val myShorts = userVideos.filter { it.video.isShort }
                if (myShorts.isEmpty()) {
                    item {
                        EmptyStateView(Icons.Filled.Bolt, "Sin Shorts propios", "Publica un Short vertical.", "Publicar Short", onOpenPublish)
                    }
                } else {
                    items(myShorts, key = { "ms_${it.video.id}" }) { item ->
                        VideoCard(item, { onWatchVideo(item) }, onOpenChannel, {}, {}, {})
                    }
                }
            }
            "playlists" -> {
                item {
                    Button(
                        onClick = { showCreatePlaylist = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ Crear nueva lista de reproducción", color = DeiPlayWhite)
                    }
                }
                items(playlists, key = { it.id }) { pl ->
                    val plVideoIds by viewModel.observePlaylistVideoIds(pl.id).collectAsStateWithLifecycle(initialValue = emptyList())
                    Surface(
                        color = DeiPlaySurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(pl.title, style = MaterialTheme.typography.titleMedium, color = DeiPlayWhite)
                            Text("${plVideoIds.size} videos • ${if (pl.isPublic) "Pública" else "Privada"}", style = MaterialTheme.typography.bodySmall, color = DeiPlayGold)
                            plVideoIds.mapNotNull { videoMap[it] }.forEach { v ->
                                TextButton(onClick = { onWatchVideo(v) }) {
                                    Text("▶ ${v.video.title}", color = DeiPlayWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateChannel) {
        CreateChannelDialog(
            defaultCountry = activeAccount?.country ?: "Global",
            onConfirm = { name, handle, desc, country, avatar, banner ->
                viewModel.createChannel(name, handle, desc, country, avatar, banner) { res ->
                    if (res.isSuccess) {
                        showCreateChannel = false
                        viewModel.emitMessage("Canal creado correctamente")
                    }
                }
            },
            onDismiss = { showCreateChannel = false }
        )
    }

    if (showCreatePlaylist) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var isPublic by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            containerColor = DeiPlaySurface,
            title = { Text("Nueva lista de reproducción", color = DeiPlayWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título") })
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Descripción") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Lista pública", color = DeiPlayWhite, modifier = Modifier.weight(1f))
                        Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createPlaylist(title, desc, isPublic)
                        showCreatePlaylist = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed)
                ) { Text("Crear", color = DeiPlayWhite) }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancelar", color = DeiPlayTextSecondary) }
            }
        )
    }
}

@Composable
fun DownloadsScreen(
    viewModel: MainViewModel,
    onPlayOffline: (VideoWithChannel) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val allVideos by viewModel.publicVideos.collectAsStateWithLifecycle()
    val videoMap = remember(allVideos) { allVideos.associateBy { it.video.id } }

    val totalBytes = remember(downloads) { viewModel.getDownloadedStorageBytes() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeiPlayBlack)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = DeiPlayWhite)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("DESCARGAS", style = MaterialTheme.typography.titleLarge, color = DeiPlayWhite)
                Text(
                    text = "Espacio ocupado: ${Formatter.formatFileSize(context, totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DeiPlayGold
                )
            }
        }

        if (downloads.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.DownloadForOffline,
                title = "Sin descargas todavía",
                subtitle = "Los videos con licencia abierta o descarga autorizada por el creador mostrarán el botón Descargar."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloads, key = { it.videoId }) { dl ->
                    DownloadItemCard(
                        dl = dl,
                        onPlayOffline = {
                            val localFile = File(dl.localFilePath)
                            if (localFile.exists()) {
                                val item = videoMap[dl.videoId] ?: VideoWithChannel(
                                    video = VideoEntity(
                                        id = dl.videoId,
                                        channelId = "offline",
                                        ownerUserId = "offline",
                                        title = dl.title,
                                        description = "Reproducción sin conexión desde Descargas de DeiPlay.",
                                        thumbnailUrl = dl.thumbnailUrl,
                                        primaryVideoUrl = Uri.fromFile(localFile).toString(),
                                        durationSeconds = dl.durationSeconds,
                                        isShort = dl.isShort,
                                        sourceType = VideoSourceType.DEIPLAY_USER.name,
                                        sourceAttribution = dl.channelName,
                                        licenseName = dl.licenseName,
                                        downloadAllowed = true
                                    ),
                                    channel = null
                                )
                                onPlayOffline(item)
                            } else {
                                viewModel.emitMessage("El archivo local ya no existe")
                            }
                        },
                        onPause = { viewModel.pauseDownload(dl.videoId) },
                        onResume = {
                            val item = videoMap[dl.videoId] ?: VideoWithChannel(
                                video = VideoEntity(
                                    id = dl.videoId,
                                    channelId = "offline",
                                    ownerUserId = "offline",
                                    title = dl.title,
                                    description = "",
                                    thumbnailUrl = dl.thumbnailUrl,
                                    primaryVideoUrl = dl.remoteUrl,
                                    downloadAllowed = true
                                ),
                                channel = null
                            )
                            viewModel.startDownload(
                                item,
                                com.example.data.model.QualityVariant(dl.qualityLabel, dl.remoteUrl)
                            )
                        },
                        onDelete = { viewModel.deleteDownload(dl.videoId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    dl: DownloadEntity,
    onPlayOffline: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val status = runCatching { DownloadStatus.valueOf(dl.status) }.getOrDefault(DownloadStatus.FAILED)
    val progress = if (dl.totalBytes > 0) (dl.bytesDownloaded.toFloat() / dl.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        color = DeiPlaySurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = status == DownloadStatus.COMPLETED, onClick = onPlayOffline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = if (dl.thumbnailUrl.startsWith("/")) File(dl.thumbnailUrl) else dl.thumbnailUrl,
                    contentDescription = dl.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(108.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DeiPlayBlack)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(dl.title, style = MaterialTheme.typography.titleSmall, color = DeiPlayWhite, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${dl.channelName} • ${dl.qualityLabel}", style = MaterialTheme.typography.bodySmall, color = DeiPlayTextSecondary)
                    Text(
                        text = when (status) {
                            DownloadStatus.COMPLETED -> "Listo sin conexión (${Formatter.formatFileSize(context, dl.bytesDownloaded)})"
                            DownloadStatus.DOWNLOADING -> "Descargando ${(progress * 100).toInt()}%"
                            DownloadStatus.PAUSED -> "Pausado (${(progress * 100).toInt()}%)"
                            DownloadStatus.FAILED -> "Error — toca reanudar"
                            DownloadStatus.QUEUED -> "En cola..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status == DownloadStatus.COMPLETED) DeiPlaySuccess else DeiPlayGold
                    )
                }
            }
            if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PAUSED) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, color = DeiPlayRed, modifier = Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                when (status) {
                    DownloadStatus.DOWNLOADING -> TextButton(onClick = onPause) { Text("Pausar", color = DeiPlayGold) }
                    DownloadStatus.PAUSED, DownloadStatus.FAILED -> TextButton(onClick = onResume) { Text("Reanudar", color = DeiPlayGold) }
                    DownloadStatus.COMPLETED -> TextButton(onClick = onPlayOffline) { Text("Reproducir offline", color = DeiPlayWhite) }
                    else -> {}
                }
                TextButton(onClick = onDelete) { Text("Eliminar", color = DeiPlayRedBright) }
            }
        }
    }
}

@Composable
fun ChannelScreen(
    channelId: String,
    viewModel: MainViewModel,
    onWatchVideo: (VideoWithChannel) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val channel by viewModel.observeChannel(channelId).collectAsStateWithLifecycle(initialValue = null)
    val channelVideos by viewModel.observeChannelVideos(channelId).collectAsStateWithLifecycle(initialValue = emptyList())
    val isSubscribed by viewModel.observeIsSubscribed(channelId).collectAsStateWithLifecycle(initialValue = false)

    LaunchedEffect(channelId) {
        viewModel.syncChannelIfExternal(channelId)
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Videos", "Shorts", "Información")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeiPlayBlack)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = DeiPlayWhite)
            }
            Text(channel?.name ?: "Canal", style = MaterialTheme.typography.titleLarge, color = DeiPlayWhite, modifier = Modifier.weight(1f))
            channel?.let { ch ->
                IconButton(onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, "Sigue el canal ${ch.name} (${ch.handle}) en DeiPlay: deiplay://channel/${ch.id}")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Compartir canal"))
                }) {
                    Icon(Icons.Outlined.Share, "Compartir canal", tint = DeiPlayWhite)
                }
            }
        }

        val ch = channel
        if (ch == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DeiPlayRed)
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
            item {
                // Channel Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(DeiPlaySurfaceHigh)
                ) {
                    if (ch.bannerUrl.isNotBlank()) {
                        AsyncImage(
                            model = if (ch.bannerUrl.startsWith("/")) File(ch.bannerUrl) else ch.bannerUrl,
                            contentDescription = "Portada del canal",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Channel Header Info
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChannelAvatar(avatarUrl = ch.avatarUrl, name = ch.name, size = 64.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ch.name, style = MaterialTheme.typography.headlineSmall, color = DeiPlayWhite)
                                if (ch.isVerified) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Filled.Verified, null, tint = DeiPlayGold, modifier = Modifier.size(18.dp))
                                }
                            }
                            Text("${ch.handle} • ${ch.country}", style = MaterialTheme.typography.bodySmall, color = DeiPlayTextSecondary)
                            Text(
                                text = "${formatViewCount(ch.subscriberCount)} suscriptores • ${channelVideos.size} publicaciones",
                                style = MaterialTheme.typography.labelMedium,
                                color = DeiPlayGold
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.toggleSubscribe(ch) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSubscribed) DeiPlaySurfaceHigh else DeiPlayRed
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isSubscribed) "Suscrito" else "Suscribirse", color = DeiPlayWhite)
                        }
                        if (isSubscribed) {
                            OutlinedButton(onClick = { viewModel.toggleSubscriptionBell(ch.id) }) {
                                Icon(Icons.Outlined.NotificationsActive, "Notificaciones del canal", tint = DeiPlayGold)
                            }
                        }
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DeiPlayBlack,
                    contentColor = DeiPlayRed
                ) {
                    tabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(label, color = if (selectedTab == idx) DeiPlayWhite else DeiPlayTextMuted) }
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    val vids = channelVideos.filter { !it.video.isShort }
                    items(vids, key = { "chv_${it.video.id}" }) { item ->
                        VideoCard(item, { onWatchVideo(item) }, {}, {}, {}, {}, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
                1 -> {
                    val shorts = channelVideos.filter { it.video.isShort }
                    items(shorts, key = { "chs_${it.video.id}" }) { item ->
                        VideoCard(item, { onWatchVideo(item) }, {}, {}, {}, {}, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
                2 -> {
                    item {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Descripción", style = MaterialTheme.typography.titleMedium, color = DeiPlayGold)
                            Text(ch.description.ifBlank { "Sin descripción" }, style = MaterialTheme.typography.bodyMedium, color = DeiPlayWhite)
                            Text("Origen: ${ch.sourceName}", style = MaterialTheme.typography.bodySmall, color = DeiPlayTextSecondary)
                            Text("País: ${ch.country}", style = MaterialTheme.typography.bodySmall, color = DeiPlayTextSecondary)
                            Text("Creado: ${formatRelativeDate(ch.createdAt)}", style = MaterialTheme.typography.bodySmall, color = DeiPlayTextSecondary)
                            Text("Página del canal: deiplay://channel/${ch.handle}", style = MaterialTheme.typography.bodySmall, color = DeiPlayInfo)
                        }
                    }
                }
            }
        }
    }
}
