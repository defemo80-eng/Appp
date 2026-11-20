package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "deiplay_prefs")

data class AppSettings(
    val activeUserId: String? = null,
    val appLanguage: String = "Español",
    val oledBlackTheme: Boolean = false,
    val defaultQuality: String = "Auto",
    val autoPlayNext: Boolean = true,
    val pauseWatchHistory: Boolean = false,
    val notifyNewVideos: Boolean = true,
    val notifyComments: Boolean = true,
    val notifySubscribers: Boolean = true,
    val notifyAdmin: Boolean = true
)

class UserPreferences(private val context: Context) {
    private object Keys {
        val ACTIVE_USER_ID = stringPreferencesKey("active_user_id")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val OLED_BLACK = booleanPreferencesKey("oled_black")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val PAUSE_HISTORY = booleanPreferencesKey("pause_history")
        val NOTIFY_NEW_VIDEOS = booleanPreferencesKey("notify_new_videos")
        val NOTIFY_COMMENTS = booleanPreferencesKey("notify_comments")
        val NOTIFY_SUBSCRIBERS = booleanPreferencesKey("notify_subscribers")
        val NOTIFY_ADMIN = booleanPreferencesKey("notify_admin")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            activeUserId = prefs[Keys.ACTIVE_USER_ID],
            appLanguage = prefs[Keys.APP_LANGUAGE] ?: "Español",
            oledBlackTheme = prefs[Keys.OLED_BLACK] ?: false,
            defaultQuality = prefs[Keys.DEFAULT_QUALITY] ?: "Auto",
            autoPlayNext = prefs[Keys.AUTO_PLAY_NEXT] ?: true,
            pauseWatchHistory = prefs[Keys.PAUSE_HISTORY] ?: false,
            notifyNewVideos = prefs[Keys.NOTIFY_NEW_VIDEOS] ?: true,
            notifyComments = prefs[Keys.NOTIFY_COMMENTS] ?: true,
            notifySubscribers = prefs[Keys.NOTIFY_SUBSCRIBERS] ?: true,
            notifyAdmin = prefs[Keys.NOTIFY_ADMIN] ?: true
        )
    }

    suspend fun setActiveUserId(userId: String?) {
        context.dataStore.edit { prefs ->
            if (userId == null) prefs.remove(Keys.ACTIVE_USER_ID)
            else prefs[Keys.ACTIVE_USER_ID] = userId
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                activeUserId = prefs[Keys.ACTIVE_USER_ID],
                appLanguage = prefs[Keys.APP_LANGUAGE] ?: "Español",
                oledBlackTheme = prefs[Keys.OLED_BLACK] ?: false,
                defaultQuality = prefs[Keys.DEFAULT_QUALITY] ?: "Auto",
                autoPlayNext = prefs[Keys.AUTO_PLAY_NEXT] ?: true,
                pauseWatchHistory = prefs[Keys.PAUSE_HISTORY] ?: false,
                notifyNewVideos = prefs[Keys.NOTIFY_NEW_VIDEOS] ?: true,
                notifyComments = prefs[Keys.NOTIFY_COMMENTS] ?: true,
                notifySubscribers = prefs[Keys.NOTIFY_SUBSCRIBERS] ?: true,
                notifyAdmin = prefs[Keys.NOTIFY_ADMIN] ?: true
            )
            val updated = transform(current)
            prefs[Keys.APP_LANGUAGE] = updated.appLanguage
            prefs[Keys.OLED_BLACK] = updated.oledBlackTheme
            prefs[Keys.DEFAULT_QUALITY] = updated.defaultQuality
            prefs[Keys.AUTO_PLAY_NEXT] = updated.autoPlayNext
            prefs[Keys.PAUSE_HISTORY] = updated.pauseWatchHistory
            prefs[Keys.NOTIFY_NEW_VIDEOS] = updated.notifyNewVideos
            prefs[Keys.NOTIFY_COMMENTS] = updated.notifyComments
            prefs[Keys.NOTIFY_SUBSCRIBERS] = updated.notifySubscribers
            prefs[Keys.NOTIFY_ADMIN] = updated.notifyAdmin
        }
    }
}
