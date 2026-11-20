package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        ChannelEntity::class,
        VideoEntity::class,
        SubscriptionEntity::class,
        LikeEntity::class,
        CommentEntity::class,
        WatchHistoryEntity::class,
        WatchLaterEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        DownloadEntity::class,
        NotificationEntity::class,
        ReportEntity::class,
        SearchHistoryEntity::class,
        CategoryEntity::class,
        PlatformNoticeEntity::class,
        AdminAuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DeiPlayDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun channelDao(): ChannelDao
    abstract fun videoDao(): VideoDao
    abstract fun socialDao(): SocialDao
    abstract fun downloadDao(): DownloadDao
    abstract fun platformDao(): PlatformDao

    companion object {
        @Volatile
        private var INSTANCE: DeiPlayDatabase? = null

        fun getInstance(context: Context): DeiPlayDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DeiPlayDatabase::class.java,
                    "deiplay_platform.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
