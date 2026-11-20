package com.example.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.QualityVariant
import com.example.data.model.formatDuration
import com.example.player.DeiPlayPlayerController
import com.example.player.PlayerUiState
import com.example.ui.theme.*

@OptIn(UnstableApi::class)
@Composable
fun DeiPlayVideoSurface(
    player: Player,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
            view.resizeMode = resizeMode
        },
        modifier = modifier
    )
}

@Composable
fun DailyTubePlayerBox(
    controller: DeiPlayPlayerController,
    state: PlayerUiState,
    inSystemPipMode: Boolean,
    onMinimize: () -> Unit,
    onEnterSystemPip: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.isYouTubeEmbed && !state.youTubeVideoId.isNullOrBlank()) {
        Box(
            modifier = modifier.background(Color.Black)
        ) {
            YouTubeEmbedPlayer(
                videoId = state.youTubeVideoId,
                isShort = state.currentVideo?.video?.isShort == true,
                showOpenExternalPill = !inSystemPipMode,
                modifier = Modifier.fillMaxSize()
            )
            if (!inSystemPipMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .testTag("player_minimize_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Minimizar reproductor flotante",
                            tint = DeiPlayWhite
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = onEnterSystemPip,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .testTag("player_pip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PictureInPictureAlt,
                            contentDescription = "Pantalla flotante PiP",
                            tint = DeiPlayWhite
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .testTag("player_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = if (state.isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = "Pantalla completa",
                            tint = DeiPlayWhite
                        )
                    }
                }
            }
        }
        return
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showVolumeSlider by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controlsVisible = !controlsVisible }
    ) {
        DeiPlayVideoSurface(
            player = controller.exoPlayer,
            modifier = Modifier.fillMaxSize()
        )

        if (state.isBuffering && !inSystemPipMode) {
            CircularProgressIndicator(
                color = DeiPlayRed,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (state.errorMessage != null && !inSystemPipMode) {
            Surface(
                color = DeiPlayBlack.copy(alpha = 0.86f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = DeiPlayRedBright)
                    Spacer(Modifier.height(6.dp))
                    Text(state.errorMessage, color = DeiPlayWhite, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { state.currentVideo?.let { controller.playVideo(it, state.relatedQueue) } },
                        colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed)
                    ) {
                        Text("Reintentar", color = DeiPlayWhite)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !inSystemPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.68f),
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.80f)
                            )
                        )
                    )
                    .padding(10.dp)
            ) {
                // Top Player Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.testTag("player_minimize_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Minimizar reproductor flotante",
                            tint = DeiPlayWhite
                        )
                    }
                    if (state.playingOfflineFile) {
                        Surface(
                            color = DeiPlayGold.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "OFFLINE",
                                color = DeiPlayGold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))

                    // Autoplay toggle
                    IconButton(onClick = { controller.toggleAutoPlayNext() }) {
                        Icon(
                            imageVector = if (state.autoPlayNext) Icons.Filled.PlaylistPlay else Icons.Outlined.PlaylistRemove,
                            contentDescription = "Reproducción automática",
                            tint = if (state.autoPlayNext) DeiPlayGold else DeiPlayTextSecondary
                        )
                    }

                    // Subtitles toggle (only active if tracks exist or lets user toggle CC)
                    val hasSubtitles = state.currentVideo?.video?.subtitleTracks()?.isNotEmpty() == true
                    IconButton(
                        onClick = { controller.toggleSubtitles() },
                        enabled = hasSubtitles
                    ) {
                        Icon(
                            imageVector = if (state.subtitlesEnabled) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaptionOff,
                            contentDescription = "Subtítulos",
                            tint = when {
                                !hasSubtitles -> DeiPlayTextMuted.copy(alpha = 0.4f)
                                state.subtitlesEnabled -> DeiPlayGold
                                else -> DeiPlayWhite
                            }
                        )
                    }

                    // Speed selector
                    TextButton(onClick = { showSpeedSheet = true }) {
                        Text(
                            text = "${state.playbackSpeed}x",
                            color = DeiPlayWhite,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    // Quality selector (shows ONLY real available qualities)
                    TextButton(
                        onClick = { showQualitySheet = true },
                        modifier = Modifier.testTag("player_quality_button")
                    ) {
                        Text(
                            text = state.selectedQuality?.label ?: "Auto",
                            color = DeiPlayGold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    // System PiP button
                    IconButton(
                        onClick = onEnterSystemPip,
                        modifier = Modifier.testTag("player_pip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PictureInPictureAlt,
                            contentDescription = "Pantalla flotante PiP",
                            tint = DeiPlayWhite
                        )
                    }
                }

                // Center Play / Rewind / Forward / Next controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    IconButton(
                        onClick = { controller.seekBy(-10_000L) },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(Icons.Filled.Replay10, "Retroceder 10s", tint = DeiPlayWhite)
                    }

                    IconButton(
                        onClick = { controller.togglePlayPause() },
                        modifier = Modifier
                            .size(62.dp)
                            .background(DeiPlayRed, CircleShape)
                            .testTag("player_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                            tint = DeiPlayWhite,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(
                        onClick = { controller.seekBy(10_000L) },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(Icons.Filled.Forward10, "Adelantar 10s", tint = DeiPlayWhite)
                    }

                    if (state.relatedQueue.isNotEmpty()) {
                        IconButton(
                            onClick = { controller.playNextInQueue() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        ) {
                            Icon(Icons.Filled.SkipNext, "Siguiente video", tint = DeiPlayWhite)
                        }
                    }
                }

                // Bottom scrub bar, timestamps, volume & fullscreen
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    if (showVolumeSlider) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .background(DeiPlaySurfaceHigh.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.VolumeUp, null, tint = DeiPlayGold, modifier = Modifier.size(18.dp))
                            Slider(
                                value = state.volume,
                                onValueChange = { controller.setVolume(it) },
                                colors = SliderDefaults.colors(thumbColor = DeiPlayGold, activeTrackColor = DeiPlayGold),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    val totalMs = state.durationMs.coerceAtLeast(1L)
                    Slider(
                        value = (state.positionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { frac -> controller.seekTo((frac * totalMs).toLong()) },
                        colors = SliderDefaults.colors(
                            thumbColor = DeiPlayRed,
                            activeTrackColor = DeiPlayRed,
                            inactiveTrackColor = DeiPlayWhite.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatDuration(state.positionMs / 1000)} / ${formatDuration(state.durationMs / 1000)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = DeiPlayWhite
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showVolumeSlider = !showVolumeSlider }) {
                            Icon(
                                imageVector = if (state.volume == 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                contentDescription = "Volumen",
                                tint = DeiPlayWhite
                            )
                        }
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.testTag("player_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = if (state.isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = "Pantalla completa",
                                tint = DeiPlayWhite
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQualitySheet) {
        QualitySelectionDialog(
            variants = state.availableQualities,
            selected = state.selectedQuality,
            onSelect = {
                controller.selectQuality(it)
                showQualitySheet = false
            },
            onDismiss = { showQualitySheet = false }
        )
    }

    if (showSpeedSheet) {
        SpeedSelectionDialog(
            currentSpeed = state.playbackSpeed,
            onSelect = {
                controller.setPlaybackSpeed(it)
                showSpeedSheet = false
            },
            onDismiss = { showSpeedSheet = false }
        )
    }
}

@Composable
fun QualitySelectionDialog(
    variants: List<QualityVariant>,
    selected: QualityVariant?,
    onSelect: (QualityVariant) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeiPlaySurface,
        title = { Text("Calidades disponibles", color = DeiPlayWhite) },
        text = {
            Column {
                variants.forEach { variant ->
                    val isSelected = variant.label == selected?.label
                    ListItem(
                        headlineContent = {
                            Text(
                                text = variant.label,
                                color = if (isSelected) DeiPlayGold else DeiPlayWhite
                            )
                        },
                        supportingContent = if (variant.width > 0 && variant.height > 0) {
                            { Text("${variant.width}x${variant.height}", color = DeiPlayTextMuted) }
                        } else null,
                        trailingContent = {
                            if (isSelected) Icon(Icons.Filled.Check, null, tint = DeiPlayGold)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onSelect(variant) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = DeiPlayRed) }
        }
    )
}

@Composable
fun SpeedSelectionDialog(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeiPlaySurface,
        title = { Text("Velocidad de reproducción", color = DeiPlayWhite) },
        text = {
            Column {
                speeds.forEach { speed ->
                    val isSelected = speed == currentSpeed
                    ListItem(
                        headlineContent = {
                            Text(
                                text = if (speed == 1.0f) "Normal (1.0x)" else "${speed}x",
                                color = if (isSelected) DeiPlayGold else DeiPlayWhite
                            )
                        },
                        trailingContent = {
                            if (isSelected) Icon(Icons.Filled.Check, null, tint = DeiPlayGold)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onSelect(speed) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = DeiPlayRed) }
        }
    )
}
