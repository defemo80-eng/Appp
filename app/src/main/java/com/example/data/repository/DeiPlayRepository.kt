package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.download.DownloadEngine
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.WorldCatalogSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class DeiPlayRepository(
    private val context: Context,
    private val db: DeiPlayDatabase,
    private val preferences: UserPreferences,
    private val worldCatalogSync: WorldCatalogSync,
    private val downloadEngine: DownloadEngine
) {
    private val jsonCodec = Json { ignoreUnknownKeys = true }

    private val accountDao = db.accountDao()
    private val channelDao = db.channelDao()
    private val videoDao = db.videoDao()
    private val socialDao = db.socialDao()
    private val downloadDao = db.downloadDao()
    private val platformDao = db.platformDao()

    val settingsFlow: Flow<AppSettings> = preferences.settingsFlow

    val activeAccountFlow: Flow<AccountEntity?> = settingsFlow
        .map { it.activeUserId }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(null) else accountDao.observeAccountById(uid)
        }

    val allAccountsFlow: Flow<List<AccountEntity>> = accountDao.observeAllAccounts()
    val publicVideosFlow: Flow<List<VideoWithChannel>> = videoDao.observePublicVideos()
    val publicShortsFlow: Flow<List<VideoWithChannel>> = videoDao.observePublicShorts()
    val activeChannelsFlow: Flow<List<ChannelEntity>> = channelDao.observeActiveChannels()
    val allChannelsFlow: Flow<List<ChannelEntity>> = channelDao.observeAllChannels()
    val allVideosAdminFlow: Flow<List<VideoWithChannel>> = videoDao.observeAllVideosAdmin()
    val downloadsFlow: Flow<List<DownloadEntity>> = downloadDao.observeDownloads()
    val categoriesFlow: Flow<List<CategoryEntity>> = platformDao.observeCategories()
    val searchHistoryFlow: Flow<List<SearchHistoryEntity>> = platformDao.observeSearchHistory()
    val reportsFlow: Flow<List<ReportEntity>> = platformDao.observeReports()
    val auditLogsFlow: Flow<List<AdminAuditLogEntity>> = platformDao.observeAuditLogs()
    val platformNoticeFlow: Flow<PlatformNoticeEntity?> = platformDao.observePlatformNotice()
    val publicPlaylistsFlow: Flow<List<PlaylistEntity>> = socialDao.observePublicPlaylists()

    fun observeNotifications(userId: String): Flow<List<NotificationEntity>> =
        platformDao.observeNotifications(userId)

    fun observeUserChannels(userId: String): Flow<List<ChannelEntity>> =
        channelDao.observeUserChannels(userId)

    fun observeUserVideos(userId: String): Flow<List<VideoWithChannel>> =
        videoDao.observeUserVideos(userId)

    fun observeChannel(channelId: String): Flow<ChannelEntity?> =
        channelDao.observeChannel(channelId)

    fun observeChannelVideos(channelId: String): Flow<List<VideoWithChannel>> =
        videoDao.observeChannelVideos(channelId)

    fun observeVideo(videoId: String): Flow<VideoWithChannel?> =
        videoDao.observeVideo(videoId)

    fun observeComments(videoId: String): Flow<List<CommentEntity>> =
        socialDao.observeComments(videoId)

    fun observeIsLiked(userId: String, videoId: String): Flow<Boolean> =
        socialDao.observeIsLiked(userId, videoId)

    fun observeIsSubscribed(userId: String, channelId: String): Flow<Boolean> =
        socialDao.observeIsSubscribed(userId, channelId)

    fun observeIsInWatchLater(userId: String, videoId: String): Flow<Boolean> =
        socialDao.observeIsInWatchLater(userId, videoId)

    fun observeDownload(videoId: String): Flow<DownloadEntity?> =
        downloadDao.observeDownload(videoId)

    fun observeSubscriptions(userId: String): Flow<List<SubscriptionEntity>> =
        socialDao.observeSubscriptions(userId)

    fun observeWatchHistory(userId: String): Flow<List<WatchHistoryEntity>> =
        socialDao.observeWatchHistory(userId)

    fun observeLikedVideoIds(userId: String): Flow<List<String>> =
        socialDao.observeLikedVideoIds(userId)

    fun observeWatchLaterIds(userId: String): Flow<List<String>> =
        socialDao.observeWatchLaterIds(userId)

    fun observeUserPlaylists(userId: String): Flow<List<PlaylistEntity>> =
        socialDao.observePlaylists(userId)

    fun observePlaylistVideoIds(playlistId: String): Flow<List<String>> =
        socialDao.observePlaylistVideoIds(playlistId)

    suspend fun bootstrapPlatformDefaults() = withContext(Dispatchers.IO) {
        val defaultCategories = listOf(
            "Todos", "Música", "Videojuegos", "Educación", "Tecnología",
            "Deportes", "Noticias", "Películas", "Entretenimiento", "Recientes"
        ).mapIndexed { index, name ->
            CategoryEntity(name = name, isActive = true, sortOrder = index)
        }
        platformDao.insertCategoriesIgnore(defaultCategories)

        if (platformDao.observePlatformNotice().firstOrNull() == null) {
            platformDao.upsertPlatformNotice(
                PlatformNoticeEntity(
                    id = "main_notice",
                    title = "Bienvenido a DeiPlay",
                    message = "Plataforma mundial sin anuncios. Disfruta contenido abierto internacional y crea tu propio canal.",
                    rulesText = "1. Respeta los derechos de autor.\n2. Sin spam ni suplantación.\n3. Contenido apto para la comunidad global.\n4. Las descargas respetan las licencias de cada creador."
                )
            )
        }
    }

    suspend fun syncWorldCatalog(query: String? = null): Result<Int> =
        worldCatalogSync.syncWorldCatalog(query)

    suspend fun syncExternalChannel(channelId: String): Result<Int> =
        worldCatalogSync.syncChannel(channelId)

    // --- Authentication & Accounts ---

    suspend fun registerAccount(
        email: String,
        username: String,
        password: String,
        country: String,
        role: AccountRole = AccountRole.USER
    ): Result<AccountEntity> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val cleanUsername = username.trim()
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return@withContext Result.failure(IllegalArgumentException("Correo electrónico inválido"))
        }
        if (cleanUsername.length < 3) {
            return@withContext Result.failure(IllegalArgumentException("El nombre de usuario debe tener al menos 3 caracteres"))
        }
        if (password.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("La contraseña debe tener al menos 6 caracteres"))
        }
        if (accountDao.getByEmail(cleanEmail) != null) {
            return@withContext Result.failure(IllegalArgumentException("Este correo ya está registrado"))
        }
        if (accountDao.getByUsername(cleanUsername) != null) {
            return@withContext Result.failure(IllegalArgumentException("Este nombre de usuario ya está en uso"))
        }

        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        val verificationCode = (100000..999999).random().toString()
        val isFirstAccount = accountDao.count() == 0
        val effectiveRole = if (isFirstAccount) AccountRole.ADMIN else role

        val account = AccountEntity(
            id = "usr_${UUID.randomUUID()}",
            email = cleanEmail,
            username = cleanUsername,
            passwordHash = hash,
            salt = salt,
            country = country.ifBlank { "Global" },
            role = effectiveRole.name,
            emailVerified = false,
            verificationCode = verificationCode
        )
        accountDao.insert(account)
        preferences.setActiveUserId(account.id)

        platformDao.insertNotification(
            NotificationEntity(
                id = "notif_${UUID.randomUUID()}",
                targetUserId = account.id,
                type = NotificationType.ADMIN_NOTICE.name,
                title = "Cuenta DeiPlay creada",
                message = if (effectiveRole == AccountRole.ADMIN) {
                    "Tu cuenta tiene privilegios de Administración por ser la cuenta fundadora. Código de verificación: $verificationCode"
                } else {
                    "¡Bienvenido $cleanUsername! Código de verificación de correo: $verificationCode"
                }
            )
        )
        Result.success(account)
    }

    suspend fun loginAccount(emailOrUser: String, password: String): Result<AccountEntity> = withContext(Dispatchers.IO) {
        val key = emailOrUser.trim()
        val account = accountDao.getByEmail(key) ?: accountDao.getByUsername(key)
            ?: return@withContext Result.failure(IllegalArgumentException("Cuenta no encontrada"))

        if (account.isSuspended) {
            return@withContext Result.failure(IllegalStateException("Esta cuenta ha sido suspendida por moderación"))
        }
        val computed = hashPassword(password, account.salt)
        if (computed != account.passwordHash) {
            return@withContext Result.failure(IllegalArgumentException("Contraseña incorrecta"))
        }
        preferences.setActiveUserId(account.id)
        Result.success(account)
    }

    suspend fun switchAccount(accountId: String): Result<AccountEntity> = withContext(Dispatchers.IO) {
        val account = accountDao.getById(accountId)
            ?: return@withContext Result.failure(IllegalArgumentException("Cuenta no encontrada"))
        if (account.isSuspended) {
            return@withContext Result.failure(IllegalStateException("Esta cuenta está suspendida"))
        }
        preferences.setActiveUserId(account.id)
        Result.success(account)
    }

    suspend fun signOut() {
        preferences.setActiveUserId(null)
    }

    suspend fun verifyEmailCode(userId: String, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        val account = accountDao.getById(userId) ?: return@withContext Result.failure(IllegalStateException("Usuario no encontrado"))
        if (code.trim() != account.verificationCode) {
            return@withContext Result.failure(IllegalArgumentException("Código de verificación incorrecto"))
        }
        accountDao.update(account.copy(emailVerified = true))
        Result.success(Unit)
    }

    suspend fun requestPasswordReset(email: String): Result<String> = withContext(Dispatchers.IO) {
        val account = accountDao.getByEmail(email.trim())
            ?: return@withContext Result.failure(IllegalArgumentException("No existe ninguna cuenta con ese correo"))
        val code = (100000..999999).random().toString()
        accountDao.update(account.copy(verificationCode = code))
        Result.success(code)
    }

    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (newPassword.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Mínimo 6 caracteres"))
        }
        val account = accountDao.getByEmail(email.trim())
            ?: return@withContext Result.failure(IllegalArgumentException("Cuenta no encontrada"))
        if (account.verificationCode != code.trim()) {
            return@withContext Result.failure(IllegalArgumentException("Código incorrecto"))
        }
        val salt = generateSalt()
        val hash = hashPassword(newPassword, salt)
        accountDao.update(account.copy(passwordHash = hash, salt = salt, emailVerified = true))
        Result.success(Unit)
    }

    suspend fun changePassword(userId: String, currentPass: String, newPass: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (newPass.length < 6) return@withContext Result.failure(IllegalArgumentException("Mínimo 6 caracteres"))
        val account = accountDao.getById(userId) ?: return@withContext Result.failure(IllegalStateException("Cuenta no encontrada"))
        if (hashPassword(currentPass, account.salt) != account.passwordHash) {
            return@withContext Result.failure(IllegalArgumentException("La contraseña actual no coincide"))
        }
        val salt = generateSalt()
        accountDao.update(account.copy(passwordHash = hashPassword(newPass, salt), salt = salt))
        Result.success(Unit)
    }

    suspend fun updateProfile(
        userId: String,
        username: String,
        bio: String,
        country: String,
        avatarUri: Uri?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val account = accountDao.getById(userId) ?: return@withContext Result.failure(IllegalStateException("Cuenta no encontrada"))
        val cleanName = username.trim()
        if (cleanName.length < 3) return@withContext Result.failure(IllegalArgumentException("Nombre mínimo 3 caracteres"))
        val existing = accountDao.getByUsername(cleanName)
        if (existing != null && existing.id != userId) {
            return@withContext Result.failure(IllegalArgumentException("Ese nombre de usuario ya existe"))
        }
        val storedAvatar = if (avatarUri != null) {
            copyImageToInternalStorage(avatarUri, "avatar_${userId}_${System.currentTimeMillis()}.jpg") ?: account.avatarUri
        } else account.avatarUri

        accountDao.update(
            account.copy(
                username = cleanName,
                bio = bio.trim(),
                country = country.trim().ifBlank { "Global" },
                avatarUri = storedAvatar
            )
        )
        Result.success(Unit)
    }

    suspend fun deleteAccount(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        accountDao.deleteById(userId)
        preferences.setActiveUserId(null)
        Result.success(Unit)
    }

    // --- Channels ---

    suspend fun createChannel(
        ownerUserId: String,
        name: String,
        handle: String,
        description: String,
        country: String,
        avatarUri: Uri?,
        bannerUri: Uri?
    ): Result<ChannelEntity> = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        val cleanHandle = "@" + handle.trim().removePrefix("@").lowercase().replace(Regex("[^a-z0-9._]"), "")
        if (cleanName.length < 2) return@withContext Result.failure(IllegalArgumentException("Nombre de canal muy corto"))
        if (cleanHandle.length < 4) return@withContext Result.failure(IllegalArgumentException("Usuario único inválido"))
        if (channelDao.getByHandle(cleanHandle) != null) {
            return@withContext Result.failure(IllegalArgumentException("Ese @handle ya está ocupado"))
        }
        val channelId = "ch_${UUID.randomUUID()}"
        val avatarPath = avatarUri?.let { copyImageToInternalStorage(it, "ch_avatar_$channelId.jpg") }.orEmpty()
        val bannerPath = bannerUri?.let { copyImageToInternalStorage(it, "ch_banner_$channelId.jpg") }.orEmpty()

        val channel = ChannelEntity(
            id = channelId,
            ownerUserId = ownerUserId,
            name = cleanName,
            handle = cleanHandle,
            avatarUrl = avatarPath,
            bannerUrl = bannerPath,
            description = description.trim(),
            country = country.trim().ifBlank { "Global" },
            subscriberCount = 0L,
            isVerified = false,
            isExternal = false,
            sourceName = "DeiPlay"
        )
        channelDao.upsert(channel)
        Result.success(channel)
    }

    // --- Video & Short Publishing ---

    suspend fun publishLocalVideo(
        ownerUserId: String,
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
        onProgress: (Float) -> Unit
    ): Result<VideoEntity> = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        if (cleanTitle.length < 2) {
            return@withContext Result.failure(IllegalArgumentException("Agrega un título válido"))
        }
        val channel = channelDao.getById(channelId)
            ?: return@withContext Result.failure(IllegalArgumentException("Selecciona o crea un canal primero"))
        if (channel.ownerUserId != ownerUserId) {
            return@withContext Result.failure(IllegalAccessException("No eres dueño de este canal"))
        }

        // Validate file size (max 500MB for local device safety)
        val fileSize = queryUriSize(videoUri)
        if (fileSize > 500L * 1024L * 1024L) {
            return@withContext Result.failure(IllegalArgumentException("El archivo supera el límite de 500 MB"))
        }

        val videoId = "vid_${UUID.randomUUID()}"
        val mediaDir = File(context.filesDir, "user_media/videos").apply { mkdirs() }
        val destFile = File(mediaDir, "$videoId.mp4")

        val inputStream = context.contentResolver.openInputStream(videoUri)
            ?: return@withContext Result.failure(IllegalStateException("No se pudo leer el archivo seleccionado"))

        inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(32 * 1024)
                var copied = 0L
                val total = fileSize.coerceAtLeast(1L)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    copied += read
                    onProgress((copied.toFloat() / total.toFloat()).coerceIn(0f, 0.95f))
                }
            }
        }

        if (!destFile.exists() || destFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("El video no se guardó correctamente"))
        }

        // Extract real duration, width, height, and frame thumbnail
        val retriever = MediaMetadataRetriever()
        var durationSec = 0L
        var width = 0
        var height = 0
        var autoThumbPath = ""
        try {
            retriever.setDataSource(destFile.absolutePath)
            durationSec = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (thumbnailUri == null) {
                val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                if (frame != null) {
                    val thumbFile = File(File(context.filesDir, "user_media/thumbs").apply { mkdirs() }, "$videoId.jpg")
                    FileOutputStream(thumbFile).use { out ->
                        frame.compress(Bitmap.CompressFormat.JPEG, 88, out)
                    }
                    autoThumbPath = thumbFile.absolutePath
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }

        val customThumbPath = thumbnailUri?.let {
            copyImageToInternalStorage(it, "thumb_$videoId.jpg")
        } ?: autoThumbPath

        val resolutionHeight = minOf(width, height).takeIf { it > 0 } ?: height
        val qualityLabel = when {
            resolutionHeight >= 4320 -> "8K"
            resolutionHeight >= 2160 -> "4K"
            resolutionHeight >= 1440 -> "1440p"
            resolutionHeight >= 1080 -> "1080p"
            resolutionHeight >= 720 -> "720p"
            resolutionHeight >= 480 -> "480p"
            resolutionHeight >= 360 -> "360p"
            resolutionHeight >= 240 -> "240p"
            resolutionHeight > 0 -> "${resolutionHeight}p"
            else -> "Original"
        }

        val fileUriStr = Uri.fromFile(destFile).toString()
        val variants = listOf(
            QualityVariant(
                label = qualityLabel,
                url = fileUriStr,
                width = width,
                height = height,
                sizeBytes = destFile.length()
            )
        )

        val videoEntity = VideoEntity(
            id = videoId,
            channelId = channel.id,
            ownerUserId = ownerUserId,
            title = cleanTitle,
            description = description.trim(),
            thumbnailUrl = customThumbPath,
            primaryVideoUrl = fileUriStr,
            qualitiesJson = jsonCodec.encodeToString(variants),
            durationSeconds = durationSec.coerceAtLeast(1L),
            category = category,
            tagsCsv = tagsCsv.trim(),
            language = language,
            country = country,
            isShort = isShort,
            visibility = visibility.name,
            sourceType = VideoSourceType.DEIPLAY_USER.name,
            sourceAttribution = "DeiPlay (${channel.name})",
            licenseName = if (downloadAllowed) "Descarga autorizada por el creador" else "Streaming exclusivo en DeiPlay",
            downloadAllowed = downloadAllowed
        )
        videoDao.upsert(videoEntity)
        onProgress(1f)

        // Notify subscribers if public
        if (visibility == VideoVisibility.PUBLIC) {
            val subscriberIds = socialDao.getNotifiableSubscribers(channel.id)
            for (subUid in subscriberIds) {
                if (subUid != ownerUserId) {
                    platformDao.insertNotification(
                        NotificationEntity(
                            id = "notif_${UUID.randomUUID()}",
                            targetUserId = subUid,
                            type = if (isShort) NotificationType.NEW_SHORT.name else NotificationType.NEW_VIDEO.name,
                            title = "${channel.name} publicó un ${if (isShort) "Short" else "video"}",
                            message = cleanTitle,
                            videoId = videoEntity.id,
                            channelId = channel.id
                        )
                    )
                }
            }
        }
        Result.success(videoEntity)
    }

    suspend fun updateVideoMetadata(
        videoId: String,
        ownerUserId: String,
        title: String,
        description: String,
        category: String,
        visibility: VideoVisibility,
        downloadAllowed: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val video = videoDao.getById(videoId) ?: return@withContext Result.failure(IllegalArgumentException("Video no encontrado"))
        if (video.ownerUserId != ownerUserId) return@withContext Result.failure(IllegalAccessException("No autorizado"))
        videoDao.upsert(
            video.copy(
                title = title.trim().ifBlank { video.title },
                description = description.trim(),
                category = category,
                visibility = visibility.name,
                downloadAllowed = downloadAllowed
            )
        )
        Result.success(Unit)
    }

    suspend fun deleteUserVideo(videoId: String, ownerUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val video = videoDao.getById(videoId) ?: return@withContext Result.failure(IllegalArgumentException("Video no encontrado"))
        if (video.ownerUserId != ownerUserId) return@withContext Result.failure(IllegalAccessException("No autorizado"))
        if (video.primaryVideoUrl.startsWith("file://")) {
            runCatching { File(Uri.parse(video.primaryVideoUrl).path ?: "").delete() }
        }
        videoDao.deleteById(videoId)
        Result.success(Unit)
    }

    // --- Social Actions ---

    suspend fun recordView(userId: String?, videoId: String, pauseHistory: Boolean, positionMs: Long = 0L) =
        withContext(Dispatchers.IO) {
            videoDao.incrementViews(videoId)
            if (!userId.isNullOrBlank() && !pauseHistory) {
                socialDao.upsertHistory(
                    WatchHistoryEntity(
                        userId = userId,
                        videoId = videoId,
                        watchedAt = System.currentTimeMillis(),
                        lastPositionMs = positionMs
                    )
                )
            }
        }

    suspend fun updateWatchPosition(userId: String?, videoId: String, pauseHistory: Boolean, positionMs: Long) =
        withContext(Dispatchers.IO) {
            if (!userId.isNullOrBlank() && !pauseHistory) {
                socialDao.upsertHistory(
                    WatchHistoryEntity(
                        userId = userId,
                        videoId = videoId,
                        watchedAt = System.currentTimeMillis(),
                        lastPositionMs = positionMs
                    )
                )
            }
        }

    suspend fun clearWatchHistory(userId: String) = withContext(Dispatchers.IO) {
        socialDao.clearHistory(userId)
    }

    suspend fun toggleLike(userId: String, videoId: String): Boolean = withContext(Dispatchers.IO) {
        val deleted = socialDao.deleteLike(userId, videoId)
        if (deleted > 0) {
            videoDao.adjustLikes(videoId, -1L)
            false
        } else {
            val inserted = socialDao.insertLike(LikeEntity(userId = userId, videoId = videoId))
            if (inserted != -1L) {
                videoDao.adjustLikes(videoId, 1L)
            }
            true
        }
    }

    suspend fun toggleSubscription(subscriberUserId: String, channel: ChannelEntity): Boolean = withContext(Dispatchers.IO) {
        val deleted = socialDao.deleteSubscription(subscriberUserId, channel.id)
        if (deleted > 0) {
            channelDao.adjustSubscriberCount(channel.id, -1L)
            false
        } else {
            val inserted = socialDao.insertSubscription(
                SubscriptionEntity(subscriberUserId = subscriberUserId, channelId = channel.id)
            )
            if (inserted != -1L) {
                channelDao.adjustSubscriberCount(channel.id, 1L)
                if (!channel.isExternal && channel.ownerUserId != subscriberUserId) {
                    val subscriber = accountDao.getById(subscriberUserId)
                    platformDao.insertNotification(
                        NotificationEntity(
                            id = "notif_${UUID.randomUUID()}",
                            targetUserId = channel.ownerUserId,
                            type = NotificationType.NEW_SUBSCRIBER.name,
                            title = "Nuevo suscriptor",
                            message = "${subscriber?.username ?: "Un usuario"} se suscribió a ${channel.name}",
                            channelId = channel.id
                        )
                    )
                }
            }
            true
        }
    }

    suspend fun toggleSubscriptionBell(subscriberUserId: String, channelId: String) = withContext(Dispatchers.IO) {
        val sub = socialDao.getSubscription(subscriberUserId, channelId) ?: return@withContext
        socialDao.updateSubscriptionBell(subscriberUserId, channelId, !sub.notificationsEnabled)
    }

    suspend fun postComment(
        videoId: String,
        author: AccountEntity,
        text: String,
        parentCommentId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isBlank()) return@withContext Result.failure(IllegalArgumentException("Escribe un comentario"))
        val comment = CommentEntity(
            id = "cmt_${UUID.randomUUID()}",
            videoId = videoId,
            authorUserId = author.id,
            authorName = author.username,
            authorAvatar = author.avatarUri,
            parentCommentId = parentCommentId,
            text = clean
        )
        socialDao.insertComment(comment)
        videoDao.adjustComments(videoId, 1L)

        val video = videoDao.getById(videoId)
        if (video != null && video.ownerUserId != author.id && video.sourceType == VideoSourceType.DEIPLAY_USER.name) {
            platformDao.insertNotification(
                NotificationEntity(
                    id = "notif_${UUID.randomUUID()}",
                    targetUserId = video.ownerUserId,
                    type = if (parentCommentId == null) NotificationType.COMMENT.name else NotificationType.REPLY.name,
                    title = if (parentCommentId == null) "Nuevo comentario en tu video" else "Nueva respuesta",
                    message = "${author.username}: ${clean.take(80)}",
                    videoId = videoId
                )
            )
        }
        Result.success(Unit)
    }

    suspend fun toggleWatchLater(userId: String, videoId: String, currentlySaved: Boolean) = withContext(Dispatchers.IO) {
        if (currentlySaved) socialDao.deleteWatchLater(userId, videoId)
        else socialDao.insertWatchLater(WatchLaterEntity(userId = userId, videoId = videoId))
    }

    suspend fun createPlaylist(ownerUserId: String, title: String, description: String, isPublic: Boolean) =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext
            socialDao.insertPlaylist(
                PlaylistEntity(
                    id = "pl_${UUID.randomUUID()}",
                    ownerUserId = ownerUserId,
                    title = title.trim(),
                    description = description.trim(),
                    isPublic = isPublic
                )
            )
        }

    suspend fun addVideoToPlaylist(playlistId: String, videoId: String) = withContext(Dispatchers.IO) {
        socialDao.addVideoToPlaylist(PlaylistItemEntity(playlistId = playlistId, videoId = videoId))
    }

    suspend fun submitReport(
        reporterUserId: String,
        targetType: String,
        targetId: String,
        targetTitle: String,
        reason: String
    ) = withContext(Dispatchers.IO) {
        platformDao.insertReport(
            ReportEntity(
                id = "rep_${UUID.randomUUID()}",
                reporterUserId = reporterUserId,
                targetType = targetType,
                targetId = targetId,
                targetTitle = targetTitle,
                reason = reason.trim().ifBlank { "Infracción de normas" }
            )
        )
    }

    // --- Downloads ---

    fun startOrResumeDownload(item: VideoWithChannel, qualityLabel: String, url: String) {
        downloadEngine.startOrResumeDownload(item, qualityLabel, url)
    }

    suspend fun pauseDownload(videoId: String) = downloadEngine.pauseDownload(videoId)

    suspend fun cancelOrDeleteDownload(videoId: String) = downloadEngine.cancelAndDeleteDownload(videoId)

    fun getDownloadedStorageBytes(): Long = downloadEngine.calculateTotalStorageBytes()

    // --- Search & Notifications ---

    suspend fun saveSearchQuery(query: String) = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isNotBlank()) {
            platformDao.upsertSearchQuery(SearchHistoryEntity(query = clean))
        }
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        platformDao.clearSearchHistory()
    }

    suspend fun markNotificationRead(id: String) = withContext(Dispatchers.IO) {
        platformDao.markNotificationRead(id)
    }

    suspend fun markAllNotificationsRead(userId: String) = withContext(Dispatchers.IO) {
        platformDao.markAllRead(userId)
    }

    // --- Creator Analytics (100% Real) ---

    suspend fun computeCreatorAnalytics(userId: String, period: AnalyticsPeriod): CreatorAnalytics =
        withContext(Dispatchers.IO) {
            val allUserVideos = videoDao.observeUserVideos(userId).first()
            val userChannels = channelDao.observeUserChannels(userId).first()
            val sinceMs = period.days?.let { System.currentTimeMillis() - it * 86_400_000L } ?: 0L

            val filteredVideos = if (period.days != null) {
                allUserVideos.filter { it.video.createdAt >= sinceMs }
            } else {
                allUserVideos
            }

            val totalSubscribers = userChannels.sumOf { it.subscriberCount }
            val channelIds = userChannels.map { it.id }
            val newSubs = if (channelIds.isNotEmpty()) {
                socialDao.countNewSubscribersSince(channelIds, sinceMs)
            } else 0L

            CreatorAnalytics(
                totalViews = filteredVideos.sumOf { it.video.viewCount },
                totalSubscribers = totalSubscribers,
                newSubscribersInPeriod = newSubs,
                totalLikes = filteredVideos.sumOf { it.video.likeCount },
                totalComments = filteredVideos.sumOf { it.video.commentCount },
                publishedVideos = filteredVideos.count { !it.video.isShort },
                publishedShorts = filteredVideos.count { it.video.isShort },
                topVideos = filteredVideos.sortedByDescending { it.video.viewCount }.map {
                    VideoPerformanceItem(
                        videoId = it.video.id,
                        title = it.video.title,
                        thumbnailUrl = it.video.thumbnailUrl,
                        isShort = it.video.isShort,
                        views = it.video.viewCount,
                        likes = it.video.likeCount,
                        comments = it.video.commentCount,
                        createdAt = it.video.createdAt
                    )
                }
            )
        }

    // --- Admin Console Actions (Role-Gated + Audited) ---

    suspend fun adminSetUserSuspended(admin: AccountEntity, targetUserId: String, suspend: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (admin.role != AccountRole.ADMIN.name) return@withContext Result.failure(IllegalAccessException("Solo administradores"))
            val target = accountDao.getById(targetUserId) ?: return@withContext Result.failure(IllegalArgumentException("Usuario no encontrado"))
            if (target.id == admin.id) return@withContext Result.failure(IllegalArgumentException("No puedes suspender tu propia cuenta"))
            accountDao.update(target.copy(isSuspended = suspend))
            logAdminAction(admin, if (suspend) "SUSPENDER_USUARIO" else "REACTIVAR_USUARIO", "${target.username} (${target.email})")
            Result.success(Unit)
        }

    suspend fun adminToggleChannelModeration(admin: AccountEntity, channel: ChannelEntity, moderated: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (admin.role != AccountRole.ADMIN.name) return@withContext Result.failure(IllegalAccessException("Solo administradores"))
            channelDao.setModerated(channel.id, moderated)
            logAdminAction(admin, if (moderated) "MODERAR_CANAL" else "RESTAURAR_CANAL", "${channel.name} (${channel.handle})")
            Result.success(Unit)
        }

    suspend fun adminUpdateChannelSubscribers(admin: AccountEntity, channel: ChannelEntity, newSubscribers: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (admin.role != AccountRole.ADMIN.name) return@withContext Result.failure(IllegalAccessException("Solo administradores"))
            val safeValue = newSubscribers.coerceAtLeast(0L)
            channelDao.setSubscriberCount(channel.id, safeValue)
            logAdminAction(admin, "EDITAR_SUSCRIPTORES", "${channel.name}: ${channel.subscriberCount} -> $safeValue")
            Result.success(Unit)
        }

    suspend fun adminRemoveVideo(admin: AccountEntity, video: VideoEntity, remove: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (admin.role != AccountRole.ADMIN.name) return@withContext Result.failure(IllegalAccessException("Solo administradores"))
            videoDao.setRemoved(video.id, remove)
            logAdminAction(admin, if (remove) "RETIRAR_VIDEO" else "RESTAURAR_VIDEO", video.title)
            Result.success(Unit)
        }

    suspend fun adminUpdateVideoMetrics(admin: AccountEntity, video: VideoEntity, newViews: Long, newLikes: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (admin.role != AccountRole.ADMIN.name) return@withContext Result.failure(IllegalAccessException("Solo administradores"))
            val safeViews = newViews.coerceAtLeast(0L)
            val safeLikes = newLikes.coerceAtLeast(0L)
            videoDao.setMetrics(video.id, safeViews, safeLikes)
            logAdminAction(admin, "EDITAR_METRICAS_VIDEO", "${video.title}: vistas=$safeViews, likes=$safeLikes")
            Result.success(Unit)
        }

    suspend fun adminResolveReport(admin: AccountEntity, report: ReportEntity, removeTarget: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (admin.role != AccountRole.ADMIN.name) return@withContext Result.failure(IllegalAccessException("Solo administradores"))
            if (removeTarget) {
                when (report.targetType) {
                    "VIDEO" -> videoDao.setRemoved(report.targetId, true)
                    "COMMENT" -> socialDao.softDeleteComment(report.targetId)
                    "CHANNEL" -> channelDao.setModerated(report.targetId, true)
                }
                platformDao.updateReportStatus(report.id, ReportStatus.REMOVED.name)
                logAdminAction(admin, "REPORTE_RETIRAR_${report.targetType}", report.targetTitle)
            } else {
                platformDao.updateReportStatus(report.id, ReportStatus.DISMISSED.name)
                logAdminAction(admin, "REPORTE_DESESTIMAR", report.targetTitle)
            }
            Result.success(Unit)
        }

    suspend fun adminAddCategory(admin: AccountEntity, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (admin.role != AccountRole.ADMIN.name) return@withContext Result.failure(IllegalAccessException("Solo administradores"))
        val clean = name.trim()
        if (clean.isBlank()) return@withContext Result.failure(IllegalArgumentException("Nombre vacío"))
        platformDao.upsertCategory(CategoryEntity(name = clean, isActive = true, sortOrder = 50))
        logAdminAction(admin, "AGREGAR_CATEGORIA", clean)
        Result.success(Unit)
    }

    suspend fun adminDeleteCategory(admin: AccountEntity, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (admin.role != AccountRole.ADMIN.name) return@withContext Result.failure(IllegalAccessException("Solo administradores"))
        if (name == "Todos") return@withContext Result.failure(IllegalArgumentException("No se puede eliminar 'Todos'"))
        platformDao.deleteCategory(name)
        logAdminAction(admin, "ELIMINAR_CATEGORIA", name)
        Result.success(Unit)
    }

    suspend fun adminUpdateNoticeAndRules(admin: AccountEntity, title: String, message: String, rules: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (admin.role != AccountRole.ADMIN.name) return@withContext Result.failure(IllegalAccessException("Solo administradores"))
            platformDao.upsertPlatformNotice(
                PlatformNoticeEntity(
                    id = "main_notice",
                    title = title.trim(),
                    message = message.trim(),
                    rulesText = rules.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            platformDao.insertNotification(
                NotificationEntity(
                    id = "notif_${UUID.randomUUID()}",
                    targetUserId = "ALL",
                    type = NotificationType.ADMIN_NOTICE.name,
                    title = title.trim(),
                    message = message.trim()
                )
            )
            logAdminAction(admin, "ACTUALIZAR_AVISO_Y_NORMAS", title.trim())
            Result.success(Unit)
        }

    private suspend fun logAdminAction(admin: AccountEntity, action: String, targetSummary: String) {
        platformDao.insertAuditLog(
            AdminAuditLogEntity(
                id = "audit_${UUID.randomUUID()}",
                adminUserId = admin.id,
                adminUsername = admin.username,
                action = action,
                targetSummary = targetSummary
            )
        )
    }

    // --- Utilities ---

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        preferences.updateSettings(transform)
    }

    private fun copyImageToInternalStorage(uri: Uri, filename: String): String? {
        return runCatching {
            val dir = File(context.filesDir, "user_media/images").apply { mkdirs() }
            val dest = File(dir, filename)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            dest.absolutePath
        }.getOrNull()
    }

    private fun queryUriSize(uri: Uri): Long {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getLong(idx) else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$password".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
