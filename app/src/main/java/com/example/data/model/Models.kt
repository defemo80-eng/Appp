package com.example.data.model

import kotlinx.serialization.Serializable

enum class AccountRole {
    USER,
    ADMIN
}

enum class VideoVisibility(val label: String) {
    PUBLIC("Público"),
    UNLISTED("No listado"),
    PRIVATE("Privado")
}

enum class VideoSourceType(val displayLabel: String) {
    DEIPLAY_USER("Creador DeiPlay"),
    YOUTUBE_EMBED("YouTube (Reproductor oficial)"),
    INTERNET_ARCHIVE("Internet Archive (Licencia abierta)")
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

enum class ReportStatus(val label: String) {
    PENDING("Pendiente"),
    REVIEWED("Revisado"),
    REMOVED("Retirado"),
    DISMISSED("Desestimado")
}

enum class NotificationType {
    NEW_VIDEO,
    NEW_SHORT,
    NEW_SUBSCRIBER,
    COMMENT,
    REPLY,
    ADMIN_NOTICE
}

enum class SearchSort(val label: String) {
    RELEVANCE("Relevancia"),
    POPULARITY("Popularidad"),
    RECENT("Más recientes")
}

enum class DurationFilter(val label: String) {
    ALL("Cualquier duración"),
    SHORT_UNDER_4M("Cortos (< 4 min)"),
    MEDIUM_4_TO_20M("Medios (4-20 min)"),
    LONG_OVER_20M("Largos (> 20 min)")
}

enum class DateFilter(val label: String) {
    ALL("Cualquier fecha"),
    LAST_24H("Últimas 24h"),
    LAST_7D("Última semana"),
    LAST_30D("Último mes")
}

enum class AnalyticsPeriod(val label: String, val days: Int?) {
    DAYS_7("Últimos 7 días", 7),
    DAYS_30("Últimos 30 días", 30),
    ALL_TIME("Todo el tiempo", null)
}

@Serializable
data class QualityVariant(
    val label: String,
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L
)

@Serializable
data class SubtitleTrack(
    val label: String,
    val language: String,
    val url: String
)

data class SearchFilter(
    val query: String = "",
    val country: String = "Todos",
    val language: String = "Todos",
    val category: String = "Todos",
    val durationFilter: DurationFilter = DurationFilter.ALL,
    val dateFilter: DateFilter = DateFilter.ALL,
    val sort: SearchSort = SearchSort.RELEVANCE
)

data class CreatorAnalytics(
    val totalViews: Long = 0L,
    val totalSubscribers: Long = 0L,
    val newSubscribersInPeriod: Long = 0L,
    val totalLikes: Long = 0L,
    val totalComments: Long = 0L,
    val publishedVideos: Int = 0,
    val publishedShorts: Int = 0,
    val topVideos: List<VideoPerformanceItem> = emptyList()
)

data class VideoPerformanceItem(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val isShort: Boolean,
    val views: Long,
    val likes: Long,
    val comments: Long,
    val createdAt: Long
)

data class AdminPlatformStats(
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val suspendedUsers: Int = 0,
    val totalChannels: Int = 0,
    val totalVideos: Int = 0,
    val totalShorts: Int = 0,
    val totalViews: Long = 0L,
    val pendingReports: Int = 0
)

fun formatViewCount(views: Long): String = when {
    views >= 1_000_000 -> String.format("%.1f M", views / 1_000_000.0)
    views >= 1_000 -> String.format("%.1f K", views / 1_000.0)
    else -> views.toString()
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0:00"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}

fun formatRelativeDate(timestampMs: Long): String {
    val diff = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 2 -> "Ahora mismo"
        minutes < 60 -> "Hace $minutes min"
        hours < 24 -> "Hace $hours h"
        days < 7 -> "Hace $days d"
        days < 30 -> "Hace ${days / 7} sem"
        days < 365 -> "Hace ${days / 30} m"
        else -> "Hace ${days / 365} a"
    }
}

fun normalizeSearchText(input: String): String {
    val decomposed = java.text.Normalizer.normalize(input.lowercase(), java.text.Normalizer.Form.NFD)
    return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").trim()
}

fun matchesWorldQuery(query: String, vararg fields: String): Boolean {
    val qNorm = normalizeSearchText(query)
    if (qNorm.isBlank()) return true
    val qCollapsed = qNorm.replace(Regex("[^a-z0-9]"), "")
    val tokens = qNorm.split(Regex("\\s+")).filter { it.isNotBlank() }

    val joined = fields.joinToString(" ") { normalizeSearchText(it) }
    val joinedCollapsed = joined.replace(Regex("[^a-z0-9]"), "")

    if (joined.contains(qNorm) || (qCollapsed.length >= 3 && joinedCollapsed.contains(qCollapsed))) {
        return true
    }
    if (tokens.isEmpty()) return false
    val candidateWords = joined.split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }

    return tokens.all { token ->
        val cleanToken = token.replace(Regex("[^a-z0-9]"), "")
        if (cleanToken.isBlank()) return@all true
        if (joined.contains(token) || joinedCollapsed.contains(cleanToken)) return@all true
        if (cleanToken.length >= 4) {
            candidateWords.any { word ->
                kotlin.math.abs(word.length - cleanToken.length) <= 2 &&
                    levenshteinDistance(word, cleanToken) <= 2
            }
        } else {
            false
        }
    }
}

private fun levenshteinDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = dp[0]
        dp[0] = i
        for (j in 1..b.length) {
            val temp = dp[j]
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
            prev = temp
        }
    }
    return dp[b.length]
}

