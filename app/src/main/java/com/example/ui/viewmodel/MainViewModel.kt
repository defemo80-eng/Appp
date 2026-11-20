package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.repository.DeiPlayRepository
import com.example.player.DeiPlayPlayerController
import com.example.player.PlayerUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: DeiPlayRepository,
    val playerController: DeiPlayPlayerController
) : ViewModel() {

    val playerState: StateFlow<PlayerUiState> = playerController.uiState

    val settings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val activeAccount: StateFlow<AccountEntity?> = repository.activeAccountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allAccounts: StateFlow<List<AccountEntity>> = repository.allAccountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val publicVideos: StateFlow<List<VideoWithChannel>> = repository.publicVideosFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val publicShorts: StateFlow<List<VideoWithChannel>> = repository.publicShortsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeChannels: StateFlow<List<ChannelEntity>> = repository.activeChannelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<DownloadEntity>> = repository.downloadsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = repository.searchHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val platformNotice: StateFlow<PlatformNoticeEntity?> = repository.platformNoticeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val publicPlaylists: StateFlow<List<PlaylistEntity>> = repository.publicPlaylistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // User-scoped reactive flows
    val userChannels: StateFlow<List<ChannelEntity>> = activeAccount
        .flatMapLatest { acc -> if (acc == null) flowOf(emptyList()) else repository.observeUserChannels(acc.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userVideos: StateFlow<List<VideoWithChannel>> = activeAccount
        .flatMapLatest { acc -> if (acc == null) flowOf(emptyList()) else repository.observeUserVideos(acc.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userSubscriptions: StateFlow<List<SubscriptionEntity>> = activeAccount
        .flatMapLatest { acc -> if (acc == null) flowOf(emptyList()) else repository.observeSubscriptions(acc.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchHistory: StateFlow<List<WatchHistoryEntity>> = activeAccount
        .flatMapLatest { acc -> if (acc == null) flowOf(emptyList()) else repository.observeWatchHistory(acc.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedVideoIds: StateFlow<List<String>> = activeAccount
        .flatMapLatest { acc -> if (acc == null) flowOf(emptyList()) else repository.observeLikedVideoIds(acc.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchLaterIds: StateFlow<List<String>> = activeAccount
        .flatMapLatest { acc -> if (acc == null) flowOf(emptyList()) else repository.observeWatchLaterIds(acc.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPlaylists: StateFlow<List<PlaylistEntity>> = activeAccount
        .flatMapLatest { acc -> if (acc == null) flowOf(emptyList()) else repository.observeUserPlaylists(acc.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationEntity>> = activeAccount
        .flatMapLatest { acc -> repository.observeNotifications(acc?.id ?: "ALL") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationCount: StateFlow<Int> = notifications
        .map { list -> list.count { !it.isRead } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Admin flows
    val reports: StateFlow<List<ReportEntity>> = repository.reportsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AdminAuditLogEntity>> = repository.auditLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVideosAdmin: StateFlow<List<VideoWithChannel>> = repository.allVideosAdminFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allChannelsAdmin: StateFlow<List<ChannelEntity>> = repository.allChannelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Home & Search filters
    private val _homeCategory = MutableStateFlow("Todos")
    val homeCategory: StateFlow<String> = _homeCategory.asStateFlow()

    private val _homeCountry = MutableStateFlow("Todos")
    val homeCountry: StateFlow<String> = _homeCountry.asStateFlow()

    private val _homeLanguage = MutableStateFlow("Todos")
    val homeLanguage: StateFlow<String> = _homeLanguage.asStateFlow()

    private val _searchFilter = MutableStateFlow(SearchFilter())
    val searchFilter: StateFlow<SearchFilter> = _searchFilter.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _showAuthModal = MutableStateFlow(false)
    val showAuthModal: StateFlow<Boolean> = _showAuthModal.asStateFlow()

    val filteredHomeVideos: StateFlow<List<VideoWithChannel>> = combine(
        publicVideos, _homeCategory, _homeCountry, _homeLanguage
    ) { videos, cat, country, lang ->
        videos.filter { item ->
            val v = item.video
            val catOk = cat == "Todos" || (cat == "Recientes") || v.category.equals(cat, ignoreCase = true)
            val countryOk = country == "Todos" || v.country.equals(country, ignoreCase = true)
            val langOk = lang == "Todos" || v.language.equals(lang, ignoreCase = true)
            catOk && countryOk && langOk
        }.let { list ->
            if (cat == "Recientes") list.sortedByDescending { it.video.createdAt } else list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResultsVideos: StateFlow<List<VideoWithChannel>> = combine(
        publicVideos, _searchFilter
    ) { videos, filter ->
        val q = filter.query.trim().lowercase()
        val now = System.currentTimeMillis()
        videos.filter { item ->
            val v = item.video
            val ch = item.channel
            val qOk = matchesWorldQuery(
                q,
                v.title,
                v.description,
                v.tagsCsv,
                v.category,
                ch?.name.orEmpty(),
                ch?.handle.orEmpty(),
                ch?.description.orEmpty()
            )
            val countryOk = filter.country == "Todos" || v.country.equals(filter.country, ignoreCase = true)
            val langOk = filter.language == "Todos" || v.language.equals(filter.language, ignoreCase = true)
            val catOk = filter.category == "Todos" || v.category.equals(filter.category, ignoreCase = true)
            val durOk = when (filter.durationFilter) {
                DurationFilter.ALL -> true
                DurationFilter.SHORT_UNDER_4M -> v.durationSeconds < 240
                DurationFilter.MEDIUM_4_TO_20M -> v.durationSeconds in 240..1200
                DurationFilter.LONG_OVER_20M -> v.durationSeconds > 1200
            }
            val dateOk = when (filter.dateFilter) {
                DateFilter.ALL -> true
                DateFilter.LAST_24H -> now - v.createdAt <= 86_400_000L
                DateFilter.LAST_7D -> now - v.createdAt <= 7L * 86_400_000L
                DateFilter.LAST_30D -> now - v.createdAt <= 30L * 86_400_000L
            }
            qOk && countryOk && langOk && catOk && durOk && dateOk
        }.let { list ->
            when (filter.sort) {
                SearchSort.POPULARITY -> list.sortedByDescending { it.video.viewCount }
                SearchSort.RECENT -> list.sortedByDescending { it.video.createdAt }
                SearchSort.RELEVANCE -> list.sortedByDescending { item ->
                    val titleMatch = if (q.isNotBlank() && item.video.title.lowercase().contains(q)) 1_000_000L else 0L
                    titleMatch + item.video.viewCount
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.bootstrapPlatformDefaults()
            refreshWorldCatalog()
        }
    }

    fun emitMessage(msg: String) {
        _snackbarEvents.tryEmit(msg)
    }

    fun openAuthModal() {
        _showAuthModal.value = true
    }

    fun closeAuthModal() {
        _showAuthModal.value = false
    }

    fun refreshWorldCatalog(customQuery: String? = null) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            val res = repository.syncWorldCatalog(customQuery)
            _isSyncing.value = false
            res.onFailure { err ->
                _syncError.value = err.localizedMessage ?: "No se pudo conectar con el catálogo mundial"
            }
        }
    }

    fun setHomeCategory(category: String) {
        _homeCategory.value = category
    }

    fun setHomeCountry(country: String) {
        _homeCountry.value = country
    }

    fun setHomeLanguage(language: String) {
        _homeLanguage.value = language
    }

    fun updateSearchFilter(transform: (SearchFilter) -> SearchFilter) {
        _searchFilter.update(transform)
    }

    private var lastSyncedSearchQuery: String = ""

    fun executeWorldSearch(query: String, saveHistory: Boolean = true) {
        val clean = query.trim()
        _searchFilter.update { it.copy(query = clean) }
        if (clean.isNotBlank()) {
            viewModelScope.launch {
                if (saveHistory) {
                    repository.saveSearchQuery(clean)
                }
                if (!clean.equals(lastSyncedSearchQuery, ignoreCase = true) || saveHistory) {
                    lastSyncedSearchQuery = clean
                    refreshWorldCatalog(clean)
                }
            }
        }
    }

    fun syncChannelIfExternal(channelId: String) {
        if (channelId.startsWith("ext_yt_")) {
            viewModelScope.launch {
                repository.syncExternalChannel(channelId)
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { repository.clearSearchHistory() }
    }

    // Playback helpers
    fun playVideo(item: VideoWithChannel, queue: List<VideoWithChannel> = publicVideos.value) {
        val s = settings.value
        playerController.playVideo(
            item = item,
            queue = queue,
            preferredQualityLabel = s.defaultQuality,
            autoPlayNext = s.autoPlayNext,
            minimized = false
        )
    }

    // Account actions
    fun register(
        email: String,
        username: String,
        password: String,
        country: String,
        onResult: (Result<AccountEntity>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.registerAccount(email, username, password, country)
            if (res.isSuccess) _showAuthModal.value = false
            onResult(res)
        }
    }

    fun login(emailOrUser: String, password: String, onResult: (Result<AccountEntity>) -> Unit) {
        viewModelScope.launch {
            val res = repository.loginAccount(emailOrUser, password)
            if (res.isSuccess) _showAuthModal.value = false
            onResult(res)
        }
    }

    fun switchAccount(accountId: String) {
        viewModelScope.launch {
            val res = repository.switchAccount(accountId)
            res.onSuccess { emitMessage("Sesión cambiada a ${it.username}") }
                .onFailure { emitMessage(it.localizedMessage ?: "Error al cambiar de cuenta") }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            emitMessage("Sesión cerrada")
        }
    }

    fun verifyEmail(code: String, onResult: (Result<Unit>) -> Unit) {
        val uid = activeAccount.value?.id ?: return
        viewModelScope.launch { onResult(repository.verifyEmailCode(uid, code)) }
    }

    fun requestPasswordReset(email: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch { onResult(repository.requestPasswordReset(email)) }
    }

    fun confirmPasswordReset(email: String, code: String, newPass: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onResult(repository.confirmPasswordReset(email, code, newPass)) }
    }

    fun changePassword(currentPass: String, newPass: String, onResult: (Result<Unit>) -> Unit) {
        val uid = activeAccount.value?.id ?: return
        viewModelScope.launch { onResult(repository.changePassword(uid, currentPass, newPass)) }
    }

    fun updateProfile(username: String, bio: String, country: String, avatarUri: Uri?, onResult: (Result<Unit>) -> Unit) {
        val uid = activeAccount.value?.id ?: return
        viewModelScope.launch { onResult(repository.updateProfile(uid, username, bio, country, avatarUri)) }
    }

    fun deleteActiveAccount() {
        val uid = activeAccount.value?.id ?: return
        viewModelScope.launch {
            repository.deleteAccount(uid)
            emitMessage("Cuenta eliminada")
        }
    }

    // Channel & Publish actions
    fun createChannel(
        name: String,
        handle: String,
        description: String,
        country: String,
        avatarUri: Uri?,
        bannerUri: Uri?,
        onResult: (Result<ChannelEntity>) -> Unit
    ) {
        val uid = activeAccount.value?.id
        if (uid == null) {
            _showAuthModal.value = true
            onResult(Result.failure(IllegalStateException("Inicia sesión primero")))
            return
        }
        viewModelScope.launch {
            onResult(repository.createChannel(uid, name, handle, description, country, avatarUri, bannerUri))
        }
    }

    fun publishVideo(
        channelId: String,
        videoUri: Uri,
        thumbnailUri: Uri?,
        title: String,
        description: String,
        category: String,
        tagsCsv: String,
        language: String,
        country: String,
        isShort: Boolean,
        visibility: VideoVisibility,
        downloadAllowed: Boolean,
        onProgress: (Float) -> Unit,
        onResult: (Result<VideoEntity>) -> Unit
    ) {
        val uid = activeAccount.value?.id ?: return
        viewModelScope.launch {
            onResult(
                repository.publishLocalVideo(
                    ownerUserId = uid,
                    channelId = channelId,
                    videoUri = videoUri,
                    thumbnailUri = thumbnailUri,
                    title = title,
                    description = description,
                    category = category,
                    tagsCsv = tagsCsv,
                    language = language,
                    country = country,
                    isShort = isShort,
                    visibility = visibility,
                    downloadAllowed = downloadAllowed,
                    onProgress = onProgress
                )
            )
        }
    }

    fun updateVideoMetadata(
        videoId: String,
        title: String,
        description: String,
        category: String,
        visibility: VideoVisibility,
        downloadAllowed: Boolean
    ) {
        val uid = activeAccount.value?.id ?: return
        viewModelScope.launch {
            repository.updateVideoMetadata(videoId, uid, title, description, category, visibility, downloadAllowed)
                .onSuccess { emitMessage("Publicación actualizada") }
                .onFailure { emitMessage(it.localizedMessage ?: "Error al actualizar") }
        }
    }

    fun deleteUserVideo(videoId: String) {
        val uid = activeAccount.value?.id ?: return
        viewModelScope.launch {
            repository.deleteUserVideo(videoId, uid)
                .onSuccess { emitMessage("Publicación eliminada") }
                .onFailure { emitMessage(it.localizedMessage ?: "Error al eliminar") }
        }
    }

    // Social & Watch Actions
    fun toggleLike(videoId: String) {
        val uid = activeAccount.value?.id
        if (uid == null) {
            _showAuthModal.value = true
            return
        }
        viewModelScope.launch { repository.toggleLike(uid, videoId) }
    }

    fun toggleSubscribe(channel: ChannelEntity) {
        val uid = activeAccount.value?.id
        if (uid == null) {
            _showAuthModal.value = true
            return
        }
        viewModelScope.launch {
            val subscribed = repository.toggleSubscription(uid, channel)
            emitMessage(if (subscribed) "Suscrito a ${channel.name}" else "Suscripción cancelada")
        }
    }

    fun toggleSubscriptionBell(channelId: String) {
        val uid = activeAccount.value?.id ?: return
        viewModelScope.launch { repository.toggleSubscriptionBell(uid, channelId) }
    }

    fun postComment(videoId: String, text: String, parentCommentId: String? = null, onDone: (Result<Unit>) -> Unit = {}) {
        val account = activeAccount.value
        if (account == null) {
            _showAuthModal.value = true
            return
        }
        viewModelScope.launch {
            val res = repository.postComment(videoId, account, text, parentCommentId)
            onDone(res)
        }
    }

    fun toggleWatchLater(videoId: String, currentlySaved: Boolean) {
        val uid = activeAccount.value?.id
        if (uid == null) {
            _showAuthModal.value = true
            return
        }
        viewModelScope.launch {
            repository.toggleWatchLater(uid, videoId, currentlySaved)
            emitMessage(if (currentlySaved) "Quitado de Ver más tarde" else "Guardado en Ver más tarde")
        }
    }

    fun createPlaylist(title: String, description: String, isPublic: Boolean) {
        val uid = activeAccount.value?.id ?: return
        viewModelScope.launch {
            repository.createPlaylist(uid, title, description, isPublic)
            emitMessage("Lista '$title' creada")
        }
    }

    fun addVideoToPlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            repository.addVideoToPlaylist(playlistId, videoId)
            emitMessage("Agregado a la lista de reproducción")
        }
    }

    fun submitReport(targetType: String, targetId: String, targetTitle: String, reason: String) {
        val uid = activeAccount.value?.id
        if (uid == null) {
            _showAuthModal.value = true
            return
        }
        viewModelScope.launch {
            repository.submitReport(uid, targetType, targetId, targetTitle, reason)
            emitMessage("Reporte enviado al equipo de moderación")
        }
    }

    // Downloads
    fun startDownload(item: VideoWithChannel, quality: QualityVariant) {
        if (!item.video.downloadAllowed) {
            emitMessage("Este video no tiene permiso de descarga del creador/licencia")
            return
        }
        repository.startOrResumeDownload(item, quality.label, quality.url)
        emitMessage("Descarga iniciada (${quality.label})")
    }

    fun pauseDownload(videoId: String) {
        viewModelScope.launch { repository.pauseDownload(videoId) }
    }

    fun deleteDownload(videoId: String) {
        viewModelScope.launch {
            repository.cancelOrDeleteDownload(videoId)
            emitMessage("Descarga eliminada")
        }
    }

    fun getDownloadedStorageBytes(): Long = repository.getDownloadedStorageBytes()

    // Observers for screens
    fun observeVideo(videoId: String) = repository.observeVideo(videoId)
    fun observeChannel(channelId: String) = repository.observeChannel(channelId)
    fun observeChannelVideos(channelId: String) = repository.observeChannelVideos(channelId)
    fun observeComments(videoId: String) = repository.observeComments(videoId)
    fun observeDownload(videoId: String) = repository.observeDownload(videoId)
    fun observePlaylistVideoIds(playlistId: String) = repository.observePlaylistVideoIds(playlistId)

    fun observeIsLiked(videoId: String): Flow<Boolean> = activeAccount.flatMapLatest { acc ->
        if (acc == null) flowOf(false) else repository.observeIsLiked(acc.id, videoId)
    }

    fun observeIsSubscribed(channelId: String): Flow<Boolean> = activeAccount.flatMapLatest { acc ->
        if (acc == null) flowOf(false) else repository.observeIsSubscribed(acc.id, channelId)
    }

    fun observeIsInWatchLater(videoId: String): Flow<Boolean> = activeAccount.flatMapLatest { acc ->
        if (acc == null) flowOf(false) else repository.observeIsInWatchLater(acc.id, videoId)
    }

    // Notifications & History
    fun markNotificationRead(id: String) {
        viewModelScope.launch { repository.markNotificationRead(id) }
    }

    fun markAllNotificationsRead() {
        val uid = activeAccount.value?.id ?: return
        viewModelScope.launch { repository.markAllNotificationsRead(uid) }
    }

    fun clearWatchHistory() {
        val uid = activeAccount.value?.id ?: return
        viewModelScope.launch {
            repository.clearWatchHistory(uid)
            emitMessage("Historial borrado")
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }

    // Creator Analytics
    suspend fun loadCreatorAnalytics(period: AnalyticsPeriod): CreatorAnalytics {
        val uid = activeAccount.value?.id ?: return CreatorAnalytics()
        return repository.computeCreatorAnalytics(uid, period)
    }

    // Admin actions
    fun adminSetUserSuspended(targetUserId: String, suspend: Boolean) {
        val admin = activeAccount.value ?: return
        viewModelScope.launch {
            repository.adminSetUserSuspended(admin, targetUserId, suspend)
                .onSuccess { emitMessage(if (suspend) "Cuenta suspendida" else "Cuenta reactivada") }
                .onFailure { emitMessage(it.localizedMessage ?: "Error") }
        }
    }

    fun adminToggleChannelModeration(channel: ChannelEntity, moderated: Boolean) {
        val admin = activeAccount.value ?: return
        viewModelScope.launch {
            repository.adminToggleChannelModeration(admin, channel, moderated)
                .onSuccess { emitMessage(if (moderated) "Canal moderado" else "Canal restaurado") }
        }
    }

    fun adminUpdateChannelSubscribers(channel: ChannelEntity, newSubscribers: Long) {
        val admin = activeAccount.value ?: return
        viewModelScope.launch {
            repository.adminUpdateChannelSubscribers(admin, channel, newSubscribers)
                .onSuccess { emitMessage("Suscriptores actualizados a $newSubscribers") }
                .onFailure { emitMessage(it.localizedMessage ?: "Error") }
        }
    }

    fun adminRemoveVideo(video: VideoEntity, remove: Boolean) {
        val admin = activeAccount.value ?: return
        viewModelScope.launch {
            repository.adminRemoveVideo(admin, video, remove)
                .onSuccess { emitMessage(if (remove) "Video retirado" else "Video restaurado") }
        }
    }

    fun adminUpdateVideoMetrics(video: VideoEntity, newViews: Long, newLikes: Long) {
        val admin = activeAccount.value ?: return
        viewModelScope.launch {
            repository.adminUpdateVideoMetrics(admin, video, newViews, newLikes)
                .onSuccess { emitMessage("Vistas ($newViews) y likes ($newLikes) actualizados") }
                .onFailure { emitMessage(it.localizedMessage ?: "Error") }
        }
    }

    fun adminResolveReport(report: ReportEntity, removeTarget: Boolean) {
        val admin = activeAccount.value ?: return
        viewModelScope.launch {
            repository.adminResolveReport(admin, report, removeTarget)
                .onSuccess { emitMessage(if (removeTarget) "Contenido retirado" else "Reporte desestimado") }
        }
    }

    fun adminAddCategory(name: String) {
        val admin = activeAccount.value ?: return
        viewModelScope.launch {
            repository.adminAddCategory(admin, name)
                .onSuccess { emitMessage("Categoría '$name' agregada") }
        }
    }

    fun adminDeleteCategory(name: String) {
        val admin = activeAccount.value ?: return
        viewModelScope.launch {
            repository.adminDeleteCategory(admin, name)
                .onSuccess { emitMessage("Categoría eliminada") }
        }
    }

    fun adminUpdateNoticeAndRules(title: String, message: String, rules: String) {
        val admin = activeAccount.value ?: return
        viewModelScope.launch {
            repository.adminUpdateNoticeAndRules(admin, title, message, rules)
                .onSuccess { emitMessage("Avisos y normas actualizados") }
        }
    }

    class Factory(
        private val repository: DeiPlayRepository,
        private val playerController: DeiPlayPlayerController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, playerController) as T
        }
    }
}
