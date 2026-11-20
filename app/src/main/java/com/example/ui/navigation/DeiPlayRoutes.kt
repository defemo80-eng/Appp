package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object ShortsRoute

@Serializable
data class PublishRoute(val defaultShort: Boolean = false)

@Serializable
data object SubscriptionsRoute

@Serializable
data object LibraryRoute

@Serializable
data object SearchRoute

@Serializable
data class WatchRoute(val videoId: String)

@Serializable
data class ChannelRoute(val channelId: String)

@Serializable
data object DownloadsRoute

@Serializable
data object NotificationsRoute

@Serializable
data object CreatorStudioRoute

@Serializable
data object AdminPanelRoute

@Serializable
data object SettingsRoute
