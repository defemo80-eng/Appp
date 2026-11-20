package com.example.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.local.VideoWithChannel
import com.example.data.model.DownloadStatus
import com.example.data.model.QualityVariant
import com.example.data.model.VideoSourceType
import com.example.data.repository.DeiPlayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlayerUiState(
    val currentVideo: VideoWithChannel? = null,
    val relatedQueue: List<VideoWithChannel> = emptyList(),
    val isMinimized: Boolean = false,
    val isFullscreen: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val volume: Float = 1f,
    val subtitlesEnabled: Boolean = false,
    val autoPlayNext: Boolean = true,
    val availableQualities: List<QualityVariant> = emptyList(),
    val selectedQuality: QualityVariant? = null,
    val playingOfflineFile: Boolean = false,
    val videoWidth: Int = 16,
    val videoHeight: Int = 9,
    val isYouTubeEmbed: Boolean = false,
    val youTubeVideoId: String? = null,
    val errorMessage: String? = null
)

class DeiPlayPlayerController(
    context: Context,
    private val repository: DeiPlayRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _uiState.update {
                    it.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    )
                }
                if (playbackState == Player.STATE_ENDED && _uiState.value.autoPlayNext) {
                    playNextInQueue()
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    _uiState.update {
                        it.copy(videoWidth = videoSize.width, videoHeight = videoSize.height)
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _uiState.update {
                    it.copy(
                        errorMessage = error.localizedMessage ?: "Error al reproducir el video",
                        isBuffering = false
                    )
                }
            }
        })

        // Position polling loop for smooth scrubber & Watch History updates
        scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying || exoPlayer.playbackState == Player.STATE_BUFFERING) {
                    _uiState.update {
                        it.copy(
                            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                            durationMs = exoPlayer.duration.coerceAtLeast(0L),
                            bufferedMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                        )
                    }
                }
                delay(400L)
            }
        }
    }

    fun playVideo(
        item: VideoWithChannel,
        queue: List<VideoWithChannel> = emptyList(),
        preferredQualityLabel: String = "Auto",
        autoPlayNext: Boolean = true,
        minimized: Boolean = false
    ) {
        scope.launch {
            val video = item.video
            if (video.sourceType == VideoSourceType.YOUTUBE_EMBED.name) {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                val ytId = extractYouTubeVideoId(video.id, video.primaryVideoUrl)
                _uiState.update {
                    it.copy(
                        currentVideo = item,
                        relatedQueue = queue.filter { q -> q.video.id != video.id },
                        isMinimized = minimized,
                        isFullscreen = false,
                        isPlaying = true,
                        isBuffering = false,
                        autoPlayNext = autoPlayNext,
                        availableQualities = emptyList(),
                        selectedQuality = null,
                        playingOfflineFile = false,
                        isYouTubeEmbed = true,
                        youTubeVideoId = ytId,
                        errorMessage = null,
                        positionMs = 0L,
                        durationMs = (video.durationSeconds * 1000L).coerceAtLeast(0L)
                    )
                }
                val settings = repository.settingsFlow.firstOrNull()
                repository.recordView(
                    userId = settings?.activeUserId,
                    videoId = video.id,
                    pauseHistory = settings?.pauseWatchHistory == true
                )
                return@launch
            }

            val download = repository.observeDownload(video.id).firstOrNull()
            val offlineReady = download?.status == DownloadStatus.COMPLETED.name &&
                File(download.localFilePath).exists()

            val variants = video.qualityVariants().ifEmpty {
                listOf(QualityVariant(label = "Original", url = video.primaryVideoUrl))
            }
            val selected = if (offlineReady && download != null) {
                QualityVariant(
                    label = "${download.qualityLabel} (Offline)",
                    url = Uri.fromFile(File(download.localFilePath)).toString()
                )
            } else {
                variants.firstOrNull { it.label == preferredQualityLabel }
                    ?: variants.firstOrNull { it.label == "720p" }
                    ?: variants.first()
            }

            _uiState.update {
                it.copy(
                    currentVideo = item,
                    relatedQueue = queue.filter { q -> q.video.id != video.id },
                    isMinimized = minimized,
                    isFullscreen = false,
                    autoPlayNext = autoPlayNext,
                    availableQualities = variants,
                    selectedQuality = selected,
                    playingOfflineFile = offlineReady,
                    isYouTubeEmbed = false,
                    youTubeVideoId = null,
                    errorMessage = null,
                    positionMs = 0L
                )
            }

            prepareMediaItem(item, selected.url, startPositionMs = 0L)
            val settings = repository.settingsFlow.firstOrNull()
            repository.recordView(
                userId = settings?.activeUserId,
                videoId = video.id,
                pauseHistory = settings?.pauseWatchHistory == true
            )
        }
    }

    private fun extractYouTubeVideoId(videoId: String, primaryUrl: String): String {
        if (videoId.startsWith("yt_") && videoId.length > 3) {
            return videoId.removePrefix("yt_")
        }
        val uri = runCatching { Uri.parse(primaryUrl) }.getOrNull()
        val vParam = uri?.getQueryParameter("v")
        if (!vParam.isNullOrBlank()) return vParam
        val lastSeg = uri?.lastPathSegment.orEmpty()
        return lastSeg.ifBlank { videoId.removePrefix("yt_") }
    }

    @OptIn(UnstableApi::class)
    private fun prepareMediaItem(
        item: VideoWithChannel,
        url: String,
        startPositionMs: Long
    ) {
        val subtitleConfigs = item.video.subtitleTracks().map { track ->
            val mime = if (track.url.lowercase().endsWith(".vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
                .setMimeType(mime)
                .setLanguage(track.language)
                .setLabel(track.label)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setSubtitleConfigurations(subtitleConfigs)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !_uiState.value.subtitlesEnabled)
            .build()
        exoPlayer.prepare()
        if (startPositionMs > 0L) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.playWhenReady = true
    }

    fun selectQuality(variant: QualityVariant) {
        val current = _uiState.value.currentVideo ?: return
        val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
        _uiState.update {
            it.copy(selectedQuality = variant, playingOfflineFile = false, errorMessage = null)
        }
        prepareMediaItem(current, variant.url, currentPos)
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L)))
        _uiState.update { it.copy(positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)) }
    }

    fun seekBy(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
        seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        exoPlayer.volume = clamped
        _uiState.update { it.copy(volume = clamped) }
    }

    fun toggleSubtitles() {
        val next = !_uiState.value.subtitlesEnabled
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !next)
            .build()
        _uiState.update { it.copy(subtitlesEnabled = next) }
    }

    fun toggleAutoPlayNext() {
        _uiState.update { it.copy(autoPlayNext = !it.autoPlayNext) }
    }

    fun playNextInQueue() {
        val queue = _uiState.value.relatedQueue
        val next = queue.firstOrNull() ?: return
        playVideo(
            item = next,
            queue = queue.drop(1),
            preferredQualityLabel = _uiState.value.selectedQuality?.label ?: "Auto",
            autoPlayNext = _uiState.value.autoPlayNext,
            minimized = _uiState.value.isMinimized
        )
    }

    fun minimizePlayer() {
        _uiState.update { it.copy(isMinimized = true, isFullscreen = false) }
    }

    fun expandPlayer() {
        _uiState.update { it.copy(isMinimized = false) }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.update { it.copy(isFullscreen = fullscreen, isMinimized = false) }
    }

    fun stopAndClose() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _uiState.value = PlayerUiState()
    }

    fun pausePlayback() {
        exoPlayer.pause()
    }
}
