package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.VideoWithChannel
import com.example.data.model.VideoSourceType
import com.example.data.model.formatDuration
import com.example.data.model.formatRelativeDate
import com.example.data.model.formatViewCount
import com.example.ui.theme.*
import java.io.File

/**
 * Official DeiPlay Logo.
 * Uses the exact original PNG untouched, preserving aspect ratio (1:1) with ContentScale.Fit.
 */
@Composable
fun DeiPlayOfficialLogo(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.deiplay_logo_official),
        contentDescription = stringResource(id = R.string.cd_deiplay_logo),
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
    )
}

@Composable
fun ChannelAvatar(
    avatarUrl: String,
    name: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val modelData: Any? = when {
        avatarUrl.isBlank() -> null
        avatarUrl.startsWith("/") -> File(avatarUrl)
        else -> avatarUrl
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(DeiPlayRed, DeiPlayRedDeep))
            ),
        contentAlignment = Alignment.Center
    ) {
        if (modelData != null) {
            AsyncImage(
                model = modelData,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = name.trim().firstOrNull()?.uppercase() ?: "D",
                style = MaterialTheme.typography.titleMedium,
                color = DeiPlayWhite,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SourceAttributionBadge(
    sourceAttribution: String,
    isExternal: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (isExternal) DeiPlaySurfaceHigh else DeiPlayRedDeep.copy(alpha = 0.6f),
        border = BorderStroke(
            0.75.dp,
            if (isExternal) DeiPlayInfo.copy(alpha = 0.5f) else DeiPlayGold.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isExternal) Icons.Outlined.Public else Icons.Filled.Verified,
                contentDescription = null,
                tint = if (isExternal) DeiPlayInfo else DeiPlayGold,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = sourceAttribution,
                style = MaterialTheme.typography.labelSmall,
                color = DeiPlayWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun VideoCard(
    item: VideoWithChannel,
    onClick: () -> Unit,
    onChannelClick: (String) -> Unit,
    onSaveWatchLater: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val video = item.video
    val channel = item.channel
    var menuExpanded by remember { mutableStateOf(false) }

    val thumbModel: Any? = when {
        video.thumbnailUrl.isBlank() -> null
        video.thumbnailUrl.startsWith("/") -> File(video.thumbnailUrl)
        else -> video.thumbnailUrl
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("video_card_${video.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(DeiPlaySurfaceVariant)
        ) {
            if (thumbModel != null) {
                AsyncImage(
                    model = thumbModel,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircleOutline,
                        contentDescription = null,
                        tint = DeiPlayTextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Bottom subtle gradient for legibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))
                    )
            )

            // Source & country pill top-left
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SourceAttributionBadge(
                    sourceAttribution = video.sourceAttribution,
                    isExternal = video.sourceType != VideoSourceType.DEIPLAY_USER.name
                )
            }

            // Duration pill bottom-right
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.85f)
            ) {
                Text(
                    text = formatDuration(video.durationSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = DeiPlayWhite,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            if (video.downloadAllowed) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = DeiPlayBlack.copy(alpha = 0.78f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DownloadForOffline,
                            contentDescription = "Descarga autorizada",
                            tint = DeiPlayGold,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Descargable",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeiPlayGold
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 14.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            ChannelAvatar(
                avatarUrl = channel?.avatarUrl.orEmpty(),
                name = channel?.name ?: video.sourceAttribution,
                size = 38.dp,
                modifier = Modifier
                    .clickable { channel?.let { onChannelClick(it.id) } }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = DeiPlayWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel?.name ?: video.sourceAttribution,
                        style = MaterialTheme.typography.bodySmall,
                        color = DeiPlayTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (channel?.isVerified == true) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Canal verificado",
                            tint = DeiPlayGold,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    text = "${formatViewCount(video.viewCount)} vistas • ${formatRelativeDate(video.createdAt)} • ${video.country}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DeiPlayTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Opciones del video",
                        tint = DeiPlayTextSecondary
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = DeiPlaySurfaceHigh
                ) {
                    DropdownMenuItem(
                        text = { Text("Ver más tarde", color = DeiPlayWhite) },
                        leadingIcon = { Icon(Icons.Outlined.WatchLater, null, tint = DeiPlayWhite) },
                        onClick = {
                            menuExpanded = false
                            onSaveWatchLater()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir", color = DeiPlayWhite) },
                        leadingIcon = { Icon(Icons.Outlined.Share, null, tint = DeiPlayWhite) },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                    channel?.let { ch ->
                        DropdownMenuItem(
                            text = { Text("Ir al canal", color = DeiPlayWhite) },
                            leadingIcon = { Icon(Icons.Outlined.AccountCircle, null, tint = DeiPlayWhite) },
                            onClick = {
                                menuExpanded = false
                                onChannelClick(ch.id)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Reportar contenido", color = DeiPlayRedBright) },
                        leadingIcon = { Icon(Icons.Outlined.Flag, null, tint = DeiPlayRedBright) },
                        onClick = {
                            menuExpanded = false
                            onReport()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = DeiPlaySurfaceVariant,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = DeiPlayGold,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = DeiPlayWhite
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = DeiPlayTextSecondary
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed)
            ) {
                Text(actionLabel, color = DeiPlayWhite)
            }
        }
    }
}
