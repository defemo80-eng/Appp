package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    fun observeAccountById(id: String): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getByEmail(email: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getByUsername(username: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE isModerated = 0 ORDER BY subscriberCount DESC")
    fun observeActiveChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY createdAt DESC")
    fun observeAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE ownerUserId = :userId ORDER BY createdAt DESC")
    fun observeUserChannels(userId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :channelId LIMIT 1")
    fun observeChannel(channelId: String): Flow<ChannelEntity?>

    @Query("SELECT * FROM channels WHERE id = :channelId LIMIT 1")
    suspend fun getById(channelId: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE LOWER(handle) = LOWER(:handle) LIMIT 1")
    suspend fun getByHandle(handle: String): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(channel: ChannelEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET subscriberCount = MAX(0, subscriberCount + :delta) WHERE id = :channelId")
    suspend fun adjustSubscriberCount(channelId: String, delta: Long)

    @Query("UPDATE channels SET subscriberCount = MAX(0, :subscriberCount) WHERE id = :channelId")
    suspend fun setSubscriberCount(channelId: String, subscriberCount: Long)

    @Query("UPDATE channels SET isModerated = :moderated WHERE id = :channelId")
    suspend fun setModerated(channelId: String, moderated: Boolean)
}

@Dao
interface VideoDao {
    @Transaction
    @Query("SELECT * FROM videos WHERE isRemoved = 0 AND visibility = 'PUBLIC' ORDER BY createdAt DESC")
    fun observePublicVideos(): Flow<List<VideoWithChannel>>

    @Transaction
    @Query("SELECT * FROM videos WHERE isRemoved = 0 AND visibility = 'PUBLIC' AND isShort = 1 ORDER BY viewCount DESC, createdAt DESC")
    fun observePublicShorts(): Flow<List<VideoWithChannel>>

    @Transaction
    @Query("SELECT * FROM videos WHERE isRemoved = 0 AND channelId = :channelId ORDER BY createdAt DESC")
    fun observeChannelVideos(channelId: String): Flow<List<VideoWithChannel>>

    @Transaction
    @Query("SELECT * FROM videos WHERE ownerUserId = :userId AND isRemoved = 0 ORDER BY createdAt DESC")
    fun observeUserVideos(userId: String): Flow<List<VideoWithChannel>>

    @Transaction
    @Query("SELECT * FROM videos WHERE id = :videoId LIMIT 1")
    fun observeVideo(videoId: String): Flow<VideoWithChannel?>

    @Transaction
    @Query("SELECT * FROM videos ORDER BY createdAt DESC")
    fun observeAllVideosAdmin(): Flow<List<VideoWithChannel>>

    @Query("SELECT * FROM videos WHERE id = :videoId LIMIT 1")
    suspend fun getById(videoId: String): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(video: VideoEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(videos: List<VideoEntity>)

    @Query("UPDATE videos SET viewCount = viewCount + 1 WHERE id = :videoId")
    suspend fun incrementViews(videoId: String)

    @Query("UPDATE videos SET likeCount = MAX(0, likeCount + :delta) WHERE id = :videoId")
    suspend fun adjustLikes(videoId: String, delta: Long)

    @Query("UPDATE videos SET commentCount = MAX(0, commentCount + :delta) WHERE id = :videoId")
    suspend fun adjustComments(videoId: String, delta: Long)

    @Query("UPDATE videos SET viewCount = MAX(0, :viewCount), likeCount = MAX(0, :likeCount) WHERE id = :videoId")
    suspend fun setMetrics(videoId: String, viewCount: Long, likeCount: Long)

    @Query("UPDATE videos SET isRemoved = :removed WHERE id = :videoId")
    suspend fun setRemoved(videoId: String, removed: Boolean)

    @Query("DELETE FROM videos WHERE id = :videoId")
    suspend fun deleteById(videoId: String)
}

@Dao
interface SocialDao {
    // Subscriptions
    @Query("SELECT * FROM subscriptions WHERE subscriberUserId = :userId")
    fun observeSubscriptions(userId: String): Flow<List<SubscriptionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE subscriberUserId = :userId AND channelId = :channelId)")
    fun observeIsSubscribed(userId: String, channelId: String): Flow<Boolean>

    @Query("SELECT * FROM subscriptions WHERE subscriberUserId = :userId AND channelId = :channelId LIMIT 1")
    suspend fun getSubscription(userId: String, channelId: String): SubscriptionEntity?

    @Query("SELECT subscriberUserId FROM subscriptions WHERE channelId = :channelId AND notificationsEnabled = 1")
    suspend fun getNotifiableSubscribers(channelId: String): List<String>

    @Query("SELECT COUNT(*) FROM subscriptions WHERE channelId IN (:channelIds) AND createdAt >= :sinceMs")
    suspend fun countNewSubscribersSince(channelIds: List<String>, sinceMs: Long): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubscription(sub: SubscriptionEntity): Long

    @Query("DELETE FROM subscriptions WHERE subscriberUserId = :userId AND channelId = :channelId")
    suspend fun deleteSubscription(userId: String, channelId: String): Int

    @Query("UPDATE subscriptions SET notificationsEnabled = :enabled WHERE subscriberUserId = :userId AND channelId = :channelId")
    suspend fun updateSubscriptionBell(userId: String, channelId: String, enabled: Boolean)

    // Likes
    @Query("SELECT EXISTS(SELECT 1 FROM video_likes WHERE userId = :userId AND videoId = :videoId)")
    fun observeIsLiked(userId: String, videoId: String): Flow<Boolean>

    @Query("SELECT videoId FROM video_likes WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeLikedVideoIds(userId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLike(like: LikeEntity): Long

    @Query("DELETE FROM video_likes WHERE userId = :userId AND videoId = :videoId")
    suspend fun deleteLike(userId: String, videoId: String): Int

    // Comments
    @Query("SELECT * FROM comments WHERE videoId = :videoId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeComments(videoId: String): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments WHERE id = :commentId LIMIT 1")
    suspend fun getCommentById(commentId: String): CommentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Query("UPDATE comments SET isDeleted = 1 WHERE id = :commentId")
    suspend fun softDeleteComment(commentId: String)

    // History & Watch Later
    @Query("SELECT * FROM watch_history WHERE userId = :userId ORDER BY watchedAt DESC")
    fun observeWatchHistory(userId: String): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(item: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE userId = :userId")
    suspend fun clearHistory(userId: String)

    @Query("SELECT videoId FROM watch_later WHERE userId = :userId ORDER BY addedAt DESC")
    fun observeWatchLaterIds(userId: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM watch_later WHERE userId = :userId AND videoId = :videoId)")
    fun observeIsInWatchLater(userId: String, videoId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchLater(item: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE userId = :userId AND videoId = :videoId")
    suspend fun deleteWatchLater(userId: String, videoId: String)

    // Playlists
    @Query("SELECT * FROM playlists WHERE ownerUserId = :userId ORDER BY createdAt DESC")
    fun observePlaylists(userId: String): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE isPublic = 1 ORDER BY createdAt DESC")
    fun observePublicPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT videoId FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    fun observePlaylistVideoIds(playlistId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addVideoToPlaylist(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeVideoFromPlaylist(playlistId: String, videoId: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE videoId = :videoId LIMIT 1")
    fun observeDownload(videoId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE videoId = :videoId LIMIT 1")
    suspend fun getById(videoId: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE videoId = :videoId")
    suspend fun delete(videoId: String)
}

@Dao
interface PlatformDao {
    // Notifications
    @Query("SELECT * FROM notifications WHERE targetUserId = :userId OR targetUserId = 'ALL' ORDER BY createdAt DESC")
    fun observeNotifications(userId: String): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationRead(id: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE targetUserId = :userId OR targetUserId = 'ALL'")
    suspend fun markAllRead(userId: String)

    // Search history
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 15")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchQuery(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // Categories
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategoriesIgnore(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE name = :name")
    suspend fun deleteCategory(name: String)

    // Reports & Admin
    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    fun observeReports(): Flow<List<ReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ReportEntity)

    @Query("UPDATE reports SET status = :status WHERE id = :id")
    suspend fun updateReportStatus(id: String, status: String)

    @Query("SELECT * FROM platform_notices WHERE id = 'main_notice' LIMIT 1")
    fun observePlatformNotice(): Flow<PlatformNoticeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlatformNotice(notice: PlatformNoticeEntity)

    @Query("SELECT * FROM admin_audit_logs ORDER BY createdAt DESC LIMIT 100")
    fun observeAuditLogs(): Flow<List<AdminAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AdminAuditLogEntity)
}
