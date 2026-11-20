package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.player.DeiPlayPlayerController
import com.example.player.PlayerUiState
import com.example.ui.theme.*
import kotlin.math.roundToInt

/**
 * Draggable, resizable in-app floating video window that renders live Media3 video playback.
 */
@Composable
fun FloatingMiniPlayer(
    controller: DeiPlayPlayerController,
    state: PlayerUiState,
    onExpandToFullWatch: (String) -> Unit,
    onEnterSystemPip: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val video = state.currentVideo?.video ?: return
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var expandedSize by remember { mutableStateOf(false) }

    val widthDp = if (expandedSize) 280.dp else 210.dp

    Surface(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(widthDp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .testTag("floating_mini_player"),
        shape = RoundedCornerShape(14.dp),
        color = DeiPlaySurface,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, DeiPlayRed.copy(alpha = 0.7f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (video.isShort) 9f / 14f else 16f / 9f)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .clickable {
                        controller.expandPlayer()
                        onExpandToFullWatch(video.id)
                    }
            ) {
                if (state.isYouTubeEmbed && !state.youTubeVideoId.isNullOrBlank()) {
                    YouTubeEmbedPlayer(
                        videoId = state.youTubeVideoId,
                        isShort = video.isShort,
                        showOpenExternalPill = false,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    DeiPlayVideoSurface(
                        player = controller.exoPlayer,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = DeiPlayWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            controller.expandPlayer()
                            onExpandToFullWatch(video.id)
                        }
                )
                if (onEnterSystemPip != null) {
                    IconButton(
                        onClick = onEnterSystemPip,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("floating_pip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PictureInPictureAlt,
                            contentDescription = "Ventana flotante fuera de la app",
                            tint = DeiPlayWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { expandedSize = !expandedSize },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AspectRatio,
                        contentDescription = "Cambiar tamaño de ventana flotante",
                        tint = DeiPlayGold,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (!state.isYouTubeEmbed) {
                    IconButton(
                        onClick = { controller.togglePlayPause() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Reproducir o pausar",
                            tint = DeiPlayWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { controller.stopAndClose() },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("floating_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cerrar reproductor flotante",
                        tint = DeiPlayRedBright,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
