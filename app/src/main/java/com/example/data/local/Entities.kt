package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.data.model.AccountRole
import com.example.data.model.DownloadStatus
import com.example.data.model.NotificationType
import com.example.data.model.QualityVariant
import com.example.data.model.ReportStatus
import com.example.data.model.SubtitleTrack
import com.example.data.model.VideoSourceType
import com.example.data.model.VideoVisibility
import kotlinx.serialization.json.Json

private val jsonCodec = Json { ignoreUnknownKeys = true }

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["email"], unique = true),
        Index(value = ["username"], unique = true)
    ]
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val email: String,
    val username: String,
    val passwordHash: String,
    val salt: String,
    val avatarUri: String = "",
    val bio: String = "",
    val country: String = "Global",
    val role: String = AccountRole.USER.name,
    val isSuspended: Boolean = false,
    val emailVerified: Boolean = false,
    val verificationCode: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["handle"], unique = true),
        Index(value = ["ownerUserId"])
    ]
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val ownerUserId: String,
    val name: String,
    val handle: String,
    val avatarUrl: String = "",
    val bannerUrl: String = "",
    val description: String = "",
    val country: String = "Global",
    val subscriberCount: Long = 0L,
    val isVerified: Boolean = false,
    val isModerated: Boolean = false,
    val isExternal: Boolean = false,
    val sourceName: String = "DeiPlay",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "videos",
    indices = [
        Index(value = ["channelId"]),
        Index(value = ["category"]),
        Index(value = ["isShort"]),
        Index(value = ["visibility"])
    ]
)
data class VideoEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val ownerUserId: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val primaryVideoUrl: String,
    val qualitiesJson: String = "[]",
    val subtitlesJson: String = "[]",
    val durationSeconds: Long = 0L,
    val category: String = "Entretenimiento",
    val tagsCsv: String = "",
    val language: String = "Español",
    val country: String = "Global",
    val isShort: Boolean = false,
    val visibility: String = VideoVisibility.PUBLIC.name,
    val sourceType: String = VideoSourceType.DEIPLAY_USER.name,
    val sourceAttribution: String = "DeiPlay",
    val licenseName: String = "Licencia de creador DeiPlay",
    val downloadAllowed: Boolean = true,
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
    val isRemoved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun qualityVariants(): List<QualityVariant> = runCatching {
        jsonCodec.decodeFromString<List<QualityVariant>>(qualitiesJson)
    }.getOrDefault(emptyList())

    fun subtitleTracks(): List<SubtitleTrack> = runCatching {
        jsonCodec.decodeFromString<List<SubtitleTrack>>(subtitlesJson)
    }.getOrDefault(emptyList())
}

data class VideoWithChannel(
    @Embedded val video: VideoEntity,
    @Relation(
        parentColumn = "channelId",
        entityColumn = "id"
    )
    val channel: ChannelEntity?
)

@Entity(
    tableName = "subscriptions",
    primaryKeys = ["subscriberUserId", "channelId"],
    indices = [Index(value = ["channelId"])]
)
data class SubscriptionEntity(
    val subscriberUserId: String,
    val channelId: String,
    val notificationsEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "video_likes",
    primaryKeys = ["userId", "videoId"],
    indices = [Index(value = ["videoId"])]
)
data class LikeEntity(
    val userId: String,
    val videoId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "comments",
    indices = [Index(value = ["videoId"]), Index(value = ["parentCommentId"])]
)
data class CommentEntity(
    @PrimaryKey val id: String,
    val videoId: String,
    val authorUserId: String,
    val authorName: String,
    val authorAvatar: String = "",
    val parentCommentId: String? = null,
    val text: String,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "watch_history",
    primaryKeys = ["userId", "videoId"],
    indices = [Index(value = ["watchedAt"])]
)
data class WatchHistoryEntity(
    val userId: String,
    val videoId: String,
    val watchedAt: Long = System.currentTimeMillis(),
    val lastPositionMs: Long = 0L
)

@Entity(
    tableName = "watch_later",
    primaryKeys = ["userId", "videoId"]
)
data class WatchLaterEntity(
    val userId: String,
    val videoId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists", indices = [Index(value = ["ownerUserId"])])
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val ownerUserId: String,
    val title: String,
    val description: String = "",
    val isPublic: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "videoId"]
)
data class PlaylistItemEntity(
    val playlistId: String,
    val videoId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val qualityLabel: String,
    val remoteUrl: String,
    val localFilePath: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val status: String = DownloadStatus.QUEUED.name,
    val licenseName: String = "",
    val durationSeconds: Long = 0L,
    val isShort: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications", indices = [Index(value = ["targetUserId"])])
data class NotificationEntity(
    @PrimaryKey val id: String,
    val targetUserId: String,
    val type: String = NotificationType.NEW_VIDEO.name,
    val title: String,
    val message: String,
    val videoId: String? = null,
    val channelId: String? = null,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reports", indices = [Index(value = ["status"])])
data class ReportEntity(
    @PrimaryKey val id: String,
    val reporterUserId: String,
    val targetType: String, // VIDEO, COMMENT, CHANNEL
    val targetId: String,
    val targetTitle: String,
    val reason: String,
    val status: String = ReportStatus.PENDING.name,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val isActive: Boolean = true,
    val sortOrder: Int = 0
)

@Entity(tableName = "platform_notices")
data class PlatformNoticeEntity(
    @PrimaryKey val id: String = "main_notice",
    val title: String,
    val message: String,
    val rulesText: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "admin_audit_logs")
data class AdminAuditLogEntity(
    @PrimaryKey val id: String,
    val adminUserId: String,
    val adminUsername: String,
    val action: String,
    val targetSummary: String,
    val createdAt: Long = System.currentTimeMillis()
)
