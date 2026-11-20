package com.example.data.remote

import android.util.Log
import com.example.data.local.ChannelEntity
import com.example.data.local.DeiPlayDatabase
import com.example.data.local.VideoEntity
import com.example.data.model.QualityVariant
import com.example.data.model.SubtitleTrack
import com.example.data.model.VideoSourceType
import com.example.data.model.VideoVisibility
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WorldCatalogSync(
    private val db: DeiPlayDatabase,
    private val youTubeCatalogService: YouTubeCatalogService = YouTubeCatalogService()
) {

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(FlexibleStringListAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val api: ArchiveOrgApi = Retrofit.Builder()
        .baseUrl("https://archive.org/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ArchiveOrgApi::class.java)

    private val jsonCodec = Json { ignoreUnknownKeys = true }

    private data class WorldQuerySeed(
        val query: String,
        val defaultCategory: String,
        val defaultLanguage: String,
        val defaultCountry: String
    )

    private val worldSeeds = listOf(
        WorldQuerySeed(
            query = "mediatype:movies AND format:\"MPEG4\" AND (collection:opensource_movies OR collection:blenderfoundation) AND licenseurl:*creativecommons*",
            defaultCategory = "Películas",
            defaultLanguage = "English",
            defaultCountry = "Global"
        ),
        WorldQuerySeed(
            query = "mediatype:movies AND format:\"MPEG4\" AND (language:spa OR language:Spanish) AND (licenseurl:*creativecommons* OR collection:opensource_movies)",
            defaultCategory = "Educación",
            defaultLanguage = "Español",
            defaultCountry = "España"
        ),
        WorldQuerySeed(
            query = "mediatype:movies AND format:\"MPEG4\" AND (subject:music OR subject:concert) AND (licenseurl:*creativecommons* OR collection:opensource_movies)",
            defaultCategory = "Música",
            defaultLanguage = "Global",
            defaultCountry = "EE.UU."
        ),
        WorldQuerySeed(
            query = "mediatype:movies AND format:\"MPEG4\" AND (subject:science OR subject:technology OR subject:nasa OR subject:space) AND (licenseurl:*creativecommons* OR collection:nasa)",
            defaultCategory = "Tecnología",
            defaultLanguage = "English",
            defaultCountry = "EE.UU."
        ),
        WorldQuerySeed(
            query = "mediatype:movies AND format:\"MPEG4\" AND (subject:sport OR subject:gaming OR subject:nature) AND licenseurl:*creativecommons*",
            defaultCategory = "Entretenimiento",
            defaultLanguage = "Global",
            defaultCountry = "Global"
        )
    )

    suspend fun syncWorldCatalog(customSearch: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val fetchedVideos = mutableListOf<VideoEntity>()
            val fetchedChannels = mutableListOf<ChannelEntity>()

            coroutineScope {
                val ytDeferred = async {
                    runCatching {
                        if (!customSearch.isNullOrBlank()) {
                            youTubeCatalogService.searchWorldYouTube(customSearch)
                        } else {
                            youTubeCatalogService.syncFeaturedGlobalChannels()
                        }
                    }.getOrNull()
                }

                val iaDeferred = async {
                    val iaVideos = mutableListOf<VideoEntity>()
                    val iaChannels = mutableListOf<ChannelEntity>()
                    val queries = if (!customSearch.isNullOrBlank()) {
                        listOf(
                            WorldQuerySeed(
                                query = "mediatype:movies AND format:\"MPEG4\" AND ($customSearch)",
                                defaultCategory = "Entretenimiento",
                                defaultLanguage = "Global",
                                defaultCountry = "Global"
                            )
                        )
                    } else {
                        worldSeeds
                    }
                    for (seed in queries) {
                        val searchRes = runCatching {
                            api.searchVideos(
                                query = seed.query,
                                rows = if (customSearch != null) 8 else 5
                            )
                        }.getOrNull() ?: continue

                        val docs = searchRes.response?.docs.orEmpty()
                        val resolved = docs.map { doc ->
                            async { resolveArchiveDoc(doc, seed) }
                        }.awaitAll().filterNotNull()

                        resolved.forEach { (video, channel) ->
                            iaChannels.add(channel)
                            iaVideos.add(video)
                        }
                    }
                    iaChannels to iaVideos
                }

                ytDeferred.await()?.let { ytRes ->
                    fetchedChannels.addAll(ytRes.channels)
                    fetchedVideos.addAll(ytRes.videos)
                }
                val (iaCh, iaVids) = iaDeferred.await()
                fetchedChannels.addAll(iaCh)
                fetchedVideos.addAll(iaVids)
            }

            if (fetchedChannels.isNotEmpty()) {
                db.channelDao().insertAllIgnore(fetchedChannels.distinctBy { it.id })
            }
            if (fetchedVideos.isNotEmpty()) {
                db.videoDao().insertAllIgnore(fetchedVideos.distinctBy { it.id })
            }
            Result.success(fetchedVideos.size + fetchedChannels.size)
        } catch (e: Exception) {
            Log.e("WorldCatalogSync", "Failed to sync world catalog", e)
            Result.failure(e)
        }
    }

    suspend fun syncChannel(channelId: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val ytRes = youTubeCatalogService.syncChannelById(channelId)
            if (ytRes.channels.isNotEmpty()) {
                db.channelDao().insertAllIgnore(ytRes.channels.distinctBy { it.id })
            }
            if (ytRes.videos.isNotEmpty()) {
                db.videoDao().insertAllIgnore(ytRes.videos.distinctBy { it.id })
            }
            Result.success(ytRes.videos.size)
        } catch (e: Exception) {
            Log.e("WorldCatalogSync", "Failed to sync channel $channelId", e)
            Result.failure(e)
        }
    }

    private suspend fun resolveArchiveDoc(
        doc: ArchiveDoc,
        seed: WorldQuerySeed
    ): Pair<VideoEntity, ChannelEntity>? {
        val id = doc.identifier.trim()
        if (id.isBlank()) return null

        val meta = runCatching { api.getMetadata(id) }.getOrNull() ?: return null
        val mp4Files = meta.files.filter { file ->
            val nameLower = file.name.lowercase()
            (nameLower.endsWith(".mp4") || nameLower.endsWith(".m4v")) &&
                !nameLower.contains("_thumb") &&
                !nameLower.contains(".gif")
        }
        if (mp4Files.isEmpty()) return null

        val qualities = mp4Files.mapNotNull { file ->
            val encodedName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
            val url = "https://archive.org/download/$id/$encodedName"
            val h = file.height?.toIntOrNull() ?: 0
            val w = file.width?.toIntOrNull() ?: 0
            val size = file.size?.toLongOrNull() ?: 0L
            val label = when {
                h >= 4320 -> "8K"
                h >= 2160 -> "4K"
                h >= 1440 -> "1440p"
                h >= 1080 -> "1080p"
                h >= 720 -> "720p"
                h >= 480 -> "480p"
                h >= 360 -> "360p"
                h >= 240 -> "240p"
                h > 0 -> "${h}p"
                else -> "MP4"
            }
            QualityVariant(
                label = label,
                url = url,
                width = w,
                height = h,
                sizeBytes = size
            )
        }.distinctBy { it.label }.sortedByDescending { it.height }

        val primaryUrl = qualities.firstOrNull()?.url ?: return null
        val durationSec = mp4Files.firstNotNullOfOrNull { parseLengthSeconds(it.length) } ?: 120L

        val subtitles = meta.files.filter { file ->
            val lower = file.name.lowercase()
            lower.endsWith(".vtt") || lower.endsWith(".srt")
        }.map { file ->
            val encodedName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
            SubtitleTrack(
                label = file.name.substringAfterLast("/").substringBeforeLast("."),
                language = doc.language.firstOrNull() ?: "en",
                url = "https://archive.org/download/$id/$encodedName"
            )
        }

        val rawTitle = doc.title.firstOrNull()?.takeIf { it.isNotBlank() } ?: id
        val rawDesc = doc.description.joinToString("\n").replace(Regex("<[^>]*>"), "").trim()
        val creatorName = doc.creator.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Archivo Mundial"
        val channelHandle = "@" + creatorName.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(18)
            .ifBlank { "archive_$id".take(18) }
        val channelId = "ext_ch_${channelHandle.removePrefix("@")}"

        val rawLang = doc.language.firstOrNull()?.lowercase().orEmpty()
        val mappedLang = when {
            rawLang.contains("spa") || rawLang.contains("es") -> "Español"
            rawLang.contains("por") || rawLang.contains("pt") -> "Português"
            rawLang.contains("fre") || rawLang.contains("fra") || rawLang.contains("fr") -> "Français"
            rawLang.contains("jpn") || rawLang.contains("ja") -> "日本語"
            rawLang.contains("deu") || rawLang.contains("ger") -> "Deutsch"
            rawLang.contains("eng") || rawLang.contains("en") -> "English"
            else -> seed.defaultLanguage
        }

        val subjects = doc.subject.joinToString(", ").lowercase()
        val mappedCategory = when {
            subjects.contains("music") || subjects.contains("música") || subjects.contains("concert") -> "Música"
            subjects.contains("game") || subjects.contains("videojuego") -> "Videojuegos"
            subjects.contains("sport") || subjects.contains("deporte") -> "Deportes"
            subjects.contains("news") || subjects.contains("noticia") -> "Noticias"
            subjects.contains("science") || subjects.contains("tech") || subjects.contains("nasa") -> "Tecnología"
            subjects.contains("edu") || subjects.contains("documentary") -> "Educación"
            subjects.contains("animation") || subjects.contains("film") || subjects.contains("movie") -> "Películas"
            else -> seed.defaultCategory
        }

        val licenseUrl = doc.licenseUrl.firstOrNull().orEmpty()
        val licenseName = when {
            licenseUrl.contains("publicdomain") || licenseUrl.contains("zero") -> "Dominio Público (CC0)"
            licenseUrl.contains("by-sa") -> "Creative Commons BY-SA"
            licenseUrl.contains("by-nc") -> "Creative Commons BY-NC"
            licenseUrl.contains("creativecommons") -> "Creative Commons"
            else -> "Licencia abierta Internet Archive"
        }
        val downloadAllowed = licenseUrl.contains("creativecommons") ||
            licenseUrl.contains("publicdomain") ||
            doc.collection.any { it.contains("opensource") || it.contains("nasa") || it.contains("blender") }

        val firstThumbFile = meta.files.firstOrNull {
            val l = it.name.lowercase()
            l.endsWith(".jpg") || l.endsWith(".png")
        }
        val thumbUrl = if (firstThumbFile != null) {
            val encoded = URLEncoder.encode(firstThumbFile.name, "UTF-8").replace("+", "%20")
            "https://archive.org/download/$id/$encoded"
        } else {
            "https://archive.org/services/img/$id"
        }

        val isShort = durationSec in 5..65

        val channel = ChannelEntity(
            id = channelId,
            ownerUserId = "external_archive",
            name = creatorName.take(40),
            handle = channelHandle,
            avatarUrl = thumbUrl,
            bannerUrl = thumbUrl,
            description = "Canal de catálogo público verificado procedente de Internet Archive.",
            country = seed.defaultCountry,
            subscriberCount = (doc.downloads / 40L).coerceAtLeast(12L),
            isVerified = true,
            isModerated = false,
            isExternal = true,
            sourceName = "Internet Archive"
        )

        val video = VideoEntity(
            id = "ia_$id",
            channelId = channelId,
            ownerUserId = "external_archive",
            title = rawTitle.take(120),
            description = rawDesc.ifBlank { "Contenido abierto internacional disponible legalmente desde Internet Archive." },
            thumbnailUrl = thumbUrl,
            primaryVideoUrl = primaryUrl,
            qualitiesJson = jsonCodec.encodeToString(qualities),
            subtitlesJson = jsonCodec.encodeToString(subtitles),
            durationSeconds = durationSec,
            category = mappedCategory,
            tagsCsv = doc.subject.take(6).joinToString(","),
            language = mappedLang,
            country = seed.defaultCountry,
            isShort = isShort,
            visibility = VideoVisibility.PUBLIC.name,
            sourceType = VideoSourceType.INTERNET_ARCHIVE.name,
            sourceAttribution = "Internet Archive",
            licenseName = licenseName,
            downloadAllowed = downloadAllowed,
            viewCount = doc.downloads.coerceAtLeast(1L),
            likeCount = (doc.downloads / 25L).coerceAtLeast(1L),
            commentCount = 0L,
            isRemoved = false
        )
        return video to channel
    }

    private fun parseLengthSeconds(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        raw.toDoubleOrNull()?.let { return it.toLong().coerceAtLeast(1L) }
        val parts = raw.split(":").mapNotNull { it.trim().toDoubleOrNull()?.toLong() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> null
        }
    }
}
