package com.example.data.remote

import android.util.Log
import android.util.Xml
import com.example.BuildConfig
import com.example.data.local.ChannelEntity
import com.example.data.local.VideoEntity
import com.example.data.model.VideoSourceType
import com.example.data.model.VideoVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class YouTubeSyncResult(
    val channels: List<ChannelEntity>,
    val videos: List<VideoEntity>
)

data class FeaturedYouTubeChannelSeed(
    val channelId: String,
    val fallbackName: String,
    val handle: String,
    val aliases: String,
    val country: String,
    val language: String,
    val category: String
)

class YouTubeCatalogService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val featuredGlobalSeeds = listOf(
        FeaturedYouTubeChannelSeed(
            channelId = "UCX6OQ3DkcsbYNE6H8uQQuVA",
            fallbackName = "MrBeast",
            handle = "@MrBeast",
            aliases = "MrBeast, Mr Beast, Jimmy Donaldson, retos, entretenimiento",
            country = "EE.UU.",
            language = "English",
            category = "Entretenimiento"
        ),
        FeaturedYouTubeChannelSeed(
            channelId = "UCoQm-PeHC-cbJclKJYJ8LzA",
            fallbackName = "Fede Vigevani",
            handle = "@fedevigevani",
            aliases = "Fede Vigevani, Fede, misterios, payasos, vecinos",
            country = "México",
            language = "Español",
            category = "Entretenimiento"
        ),
        FeaturedYouTubeChannelSeed(
            channelId = "UCZs0WwC0Dn_noiQE2BHSTKg",
            fallbackName = "Alejo Igoa",
            handle = "@AlejoIgoa",
            aliases = "Alejo Igoa, AlejoIgoa, retos, entretenimiento",
            country = "Argentina",
            language = "Español",
            category = "Entretenimiento"
        ),
        FeaturedYouTubeChannelSeed(
            channelId = "UCd5ApCORQsMOZZz5E9oVeFA",
            fallbackName = "Alfredo Larin",
            handle = "@alfredolarin",
            aliases = "Alfredo Larin, Larin, comedia, shorts, humor",
            country = "El Salvador",
            language = "Español",
            category = "Entretenimiento"
        ),
        FeaturedYouTubeChannelSeed(
            channelId = "UCyI6QRxXArFsfWlMdvoG2hw",
            fallbackName = "Karla Bustillos",
            handle = "@KarlaBustillos",
            aliases = "Karla Bustillos, Karla Bustillo, Karla Abustillo, vlogs, retos",
            country = "México",
            language = "Español",
            category = "Entretenimiento"
        ),
        FeaturedYouTubeChannelSeed(
            channelId = "UC8id8PPmTd6aqfpUnQzUaxw",
            fallbackName = "Rubén Tuesta (Cachetes)",
            handle = "@Tuestaok",
            aliases = "Cachetes, Rubén Tuesta, Ruben Tuesta, Operación Cachetes, Cachetes La Serie, comedia",
            country = "Argentina",
            language = "Español",
            category = "Entretenimiento"
        ),
        FeaturedYouTubeChannelSeed(
            channelId = "UCXazgXDIYyWH-yXLAkcrFxw",
            fallbackName = "elrubiusOMG",
            handle = "@elrubiusOMG",
            aliases = "ElRubius, Rubius, videojuegos, gaming",
            country = "España",
            language = "Español",
            category = "Videojuegos"
        ),
        FeaturedYouTubeChannelSeed(
            channelId = "UCqJ5zFEED1hWs0KNQCQuYdQ",
            fallbackName = "Mikecrack",
            handle = "@Mikecrack",
            aliases = "Mikecrack, Mike, videojuegos, música, animación",
            country = "España",
            language = "Español",
            category = "Videojuegos"
        ),
        FeaturedYouTubeChannelSeed(
            channelId = "UC2bYhAHyaqfVGqmWGuSZmqg",
            fallbackName = "Enaldinho",
            handle = "@enaldinho",
            aliases = "Enaldinho, Brasil, entretenimiento, retos",
            country = "Brasil",
            language = "Português",
            category = "Entretenimiento"
        ),
        FeaturedYouTubeChannelSeed(
            channelId = "UCsXVk37bltHxD1rDPwtNM8Q",
            fallbackName = "Kurzgesagt",
            handle = "@kurzgesagt",
            aliases = "Kurzgesagt, ciencia, educación, universo",
            country = "Global",
            language = "English",
            category = "Educación"
        ),
        FeaturedYouTubeChannelSeed(
            channelId = "UCY1kMZp36IQSyNx_9h4mpCg",
            fallbackName = "Mark Rober",
            handle = "@MarkRober",
            aliases = "Mark Rober, ciencia, ingeniería, tecnología",
            country = "EE.UU.",
            language = "English",
            category = "Tecnología"
        ),
        FeaturedYouTubeChannelSeed(
            channelId = "UCRijo3ddMTht_IHyNSNXpNQ",
            fallbackName = "Dude Perfect",
            handle = "@dudeperfect",
            aliases = "Dude Perfect, deportes, trucos",
            country = "EE.UU.",
            language = "English",
            category = "Deportes"
        ),
        FeaturedYouTubeChannelSeed(
            channelId = "UCLA_DiR1FfKNvjuUpBHmylQ",
            fallbackName = "NASA",
            handle = "@NASA",
            aliases = "NASA, espacio, ciencia, educación",
            country = "EE.UU.",
            language = "English",
            category = "Tecnología"
        )
    )

    suspend fun syncFeaturedGlobalChannels(): YouTubeSyncResult = withContext(Dispatchers.IO) {
        val allChannels = mutableListOf<ChannelEntity>()
        val allVideos = mutableListOf<VideoEntity>()

        coroutineScope {
            val apiKey = validYouTubeApiKey()
            if (apiKey != null) {
                val statsMap = fetchChannelsStatsViaDataApi(
                    apiKey = apiKey,
                    channelIds = featuredGlobalSeeds.map { it.channelId }
                )
                featuredGlobalSeeds.map { seed ->
                    async {
                        val rssRes = fetchChannelFeed(seed.channelId, seed)
                        val apiCh = statsMap[seed.channelId]
                        val mergedCh = if (apiCh != null) {
                            apiCh.copy(
                                name = apiCh.name.ifBlank { seed.fallbackName },
                                description = listOf(apiCh.description, seed.aliases)
                                    .filter { it.isNotBlank() }
                                    .joinToString("\n"),
                                country = apiCh.country.ifBlank { seed.country }
                            )
                        } else {
                            rssRes.channels.firstOrNull()
                        }
                        listOfNotNull(mergedCh) to rssRes.videos
                    }
                }.awaitAll().forEach { (chs, vids) ->
                    allChannels.addAll(chs)
                    allVideos.addAll(vids)
                }
            } else {
                featuredGlobalSeeds.map { seed ->
                    async {
                        val rssRes = fetchChannelFeed(seed.channelId, seed)
                        val enrichedCh = rssRes.channels.firstOrNull()?.let { ch ->
                            enrichChannelMetadataFromWeb(seed.channelId, ch)
                        }
                        listOfNotNull(enrichedCh) to rssRes.videos
                    }
                }.awaitAll().forEach { (chs, vids) ->
                    allChannels.addAll(chs)
                    allVideos.addAll(vids)
                }
            }

            // Also fetch YouTube's global trending feed so trending world creators appear immediately
            val trendingDeferred = async {
                runCatching {
                    val html = fetchWebHtml("https://www.youtube.com/feed/trending")
                    html?.let { extractYtInitialDataJson(it) }?.let { parseYtInitialDataSearchJson(it) }
                }.getOrNull()
            }
            trendingDeferred.await()?.let { tr ->
                allChannels.addAll(tr.channels)
                allVideos.addAll(tr.videos)
            }
        }

        YouTubeSyncResult(
            channels = mergeChannelsPreferRicher(allChannels),
            videos = allVideos.distinctBy { it.id }
        )
    }

    suspend fun searchWorldYouTube(rawQuery: String): YouTubeSyncResult = withContext(Dispatchers.IO) {
        val query = rawQuery.trim()
        if (query.isBlank()) return@withContext YouTubeSyncResult(emptyList(), emptyList())

        val collectedChannels = mutableListOf<ChannelEntity>()
        val collectedVideos = mutableListOf<VideoEntity>()

        val apiKey = validYouTubeApiKey()
        if (apiKey != null) {
            runCatching {
                searchViaDataApi(apiKey, query)
            }.getOrNull()?.let { res ->
                collectedChannels.addAll(res.channels)
                collectedVideos.addAll(res.videos)
            }
        }

        // Always complement or run via YouTube's live public search endpoint so ALL YouTube
        // channels in the world appear even without an API key.
        if (collectedVideos.isEmpty() || collectedChannels.isEmpty()) {
            runCatching {
                searchViaYouTubePublicWeb(query)
            }.getOrNull()?.let { webRes ->
                collectedChannels.addAll(webRes.channels)
                collectedVideos.addAll(webRes.videos)
            }
        }

        // Enrich top discovered channels with their official YouTube Atom/RSS feed
        val topUcIds = collectedChannels
            .map { it.id.removePrefix("ext_yt_") }
            .filter { it.startsWith("UC") }
            .distinct()
            .take(3)

        if (topUcIds.isNotEmpty()) {
            coroutineScope {
                topUcIds.map { ucId ->
                    async { fetchChannelFeed(ucId, null) }
                }.awaitAll().forEach { rssRes ->
                    collectedVideos.addAll(rssRes.videos)
                    rssRes.channels.forEach { rssCh ->
                        if (collectedChannels.none { it.id == rssCh.id }) {
                            collectedChannels.add(rssCh)
                        }
                    }
                }
            }
        }

        YouTubeSyncResult(
            channels = mergeChannelsPreferRicher(collectedChannels),
            videos = collectedVideos.distinctBy { it.id }
        )
    }

    suspend fun syncChannelById(rawChannelId: String): YouTubeSyncResult = withContext(Dispatchers.IO) {
        val ucId = rawChannelId.removePrefix("ext_yt_")
        if (!ucId.startsWith("UC")) return@withContext YouTubeSyncResult(emptyList(), emptyList())

        val seed = featuredGlobalSeeds.firstOrNull { it.channelId == ucId }
        val rssRes = fetchChannelFeed(ucId, seed)
        val shortsRes = runCatching {
            val shortsHtml = fetchWebHtml("https://www.youtube.com/channel/$ucId/shorts")
            shortsHtml?.let { extractYtInitialDataJson(it) }?.let { parseYtInitialDataSearchJson(it) }
        }.getOrNull()

        val apiKey = validYouTubeApiKey()
        val enrichedChannel = if (apiKey != null) {
            fetchChannelsStatsViaDataApi(apiKey, listOf(ucId))[ucId] ?: rssRes.channels.firstOrNull()
        } else {
            rssRes.channels.firstOrNull()?.let { enrichChannelMetadataFromWeb(ucId, it) }
        }

        val combinedVideos = (rssRes.videos + shortsRes?.videos.orEmpty())
            .map { vid ->
                if (vid.channelId == "ext_yt_shorts") vid.copy(channelId = "ext_yt_$ucId") else vid
            }
            .distinctBy { it.id }

        YouTubeSyncResult(
            channels = listOfNotNull(enrichedChannel),
            videos = combinedVideos
        )
    }

    private fun validYouTubeApiKey(): String? {
        val key = runCatching { BuildConfig.YOUTUBE_API_KEY }.getOrDefault("")
        return key.takeIf { it.isNotBlank() && it != "MY_YOUTUBE_API_KEY" }
    }

    private fun fetchChannelFeed(
        ucChannelId: String,
        seed: FeaturedYouTubeChannelSeed?
    ): YouTubeSyncResult {
        val url = "https://www.youtube.com/feeds/videos.xml?channel_id=${URLEncoder.encode(ucChannelId, "UTF-8")}"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
            .build()

        val xmlText = runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return YouTubeSyncResult(emptyList(), emptyList())
                resp.body?.string()
            }
        }.getOrNull() ?: return YouTubeSyncResult(emptyList(), emptyList())

        return parseYouTubeAtomFeed(xmlText, ucChannelId, seed)
    }

    private fun enrichChannelMetadataFromWeb(ucId: String, baseChannel: ChannelEntity): ChannelEntity {
        val req = Request.Builder()
            .url("https://www.youtube.com/channel/$ucId")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36")
            .header("Accept-Language", "es-419,es;q=0.9,en;q=0.8")
            .build()
        val html = runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull() ?: return baseChannel

        val ogImage = Regex("""<meta\s+property="og:image"\s+content="([^"]+)"""")
            .find(html)?.groupValues?.getOrNull(1).orEmpty()
        val ogDesc = Regex("""<meta\s+property="og:description"\s+content="([^"]+)"""")
            .find(html)?.groupValues?.getOrNull(1).orEmpty()

        var subCount = baseChannel.subscriberCount
        var handle = baseChannel.handle
        var bannerUrl = baseChannel.bannerUrl

        extractYtInitialDataJson(html)?.let { jsonStr ->
            runCatching {
                val root = JSONObject(jsonStr)
                val metaRenderer = findFirstJsonObjectByKey(root, "channelMetadataRenderer")
                val vanityUrl = metaRenderer?.optString("vanityChannelUrl").orEmpty()
                if (vanityUrl.contains("/@")) {
                    handle = "@" + vanityUrl.substringAfter("/@").substringBefore("/")
                }
                val c4Header = findFirstJsonObjectByKey(root, "c4TabbedHeaderRenderer")
                val subText = c4Header?.optJSONObject("subscriberCountText")?.let { extractRunsOrSimpleText(it) }
                    ?: findFirstSubscriberCountInHeader(root)
                parseCompactCount(subText)?.let { parsed -> subCount = parsed }

                val bannerThumbs = c4Header?.optJSONObject("banner")?.optJSONArray("thumbnails")
                if (bannerThumbs != null && bannerThumbs.length() > 0) {
                    bannerUrl = bannerThumbs.optJSONObject(bannerThumbs.length() - 1)?.optString("url").orEmpty()
                }
            }
        }

        return baseChannel.copy(
            handle = handle,
            avatarUrl = ogImage.ifBlank { baseChannel.avatarUrl },
            bannerUrl = bannerUrl.ifBlank { ogImage.ifBlank { baseChannel.bannerUrl } },
            description = listOf(ogDesc, baseChannel.description)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("\n"),
            subscriberCount = subCount
        )
    }

    private fun searchViaYouTubePublicWeb(query: String): YouTubeSyncResult {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val channels = mutableListOf<ChannelEntity>()
        val videos = mutableListOf<VideoEntity>()

        // 1. General YouTube search (returns both channelRenderer + videoRenderer + shorts)
        val mainHtml = fetchWebHtml("https://www.youtube.com/results?search_query=$encoded")
        if (mainHtml != null) {
            extractYtInitialDataJson(mainHtml)?.let { json ->
                val parsed = parseYtInitialDataSearchJson(json)
                channels.addAll(parsed.channels)
                videos.addAll(parsed.videos)
            }
        }

        // 2. Explicit Channel-filtered YouTube search (sp=EgIQAg%3D%3D) to guarantee famous
        // channels show up cleanly in the Canales & Creadores section!
        val channelHtml = fetchWebHtml("https://www.youtube.com/results?search_query=$encoded&sp=EgIQAg%253D%253D")
        if (channelHtml != null) {
            extractYtInitialDataJson(channelHtml)?.let { json ->
                val parsed = parseYtInitialDataSearchJson(json)
                channels.addAll(parsed.channels)
            }
        }

        return YouTubeSyncResult(
            channels = mergeChannelsPreferRicher(channels),
            videos = videos.distinctBy { it.id }
        )
    }

    private fun fetchWebHtml(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
            .header("Accept-Language", "es-419,es;q=0.9,en;q=0.8")
            .build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    private fun searchViaDataApi(apiKey: String, query: String): YouTubeSyncResult {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video,channel&maxResults=20&q=$encoded&key=$apiKey"
        val body = fetchWebHtml(searchUrl) ?: return YouTubeSyncResult(emptyList(), emptyList())
        val root = JSONObject(body)
        val items = root.optJSONArray("items") ?: JSONArray()

        val channelIds = mutableSetOf<String>()
        val videoIds = mutableListOf<String>()
        val fallbackChannels = mutableListOf<ChannelEntity>()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val idObj = item.optJSONObject("id") ?: continue
            val kind = idObj.optString("kind")
            val snippet = item.optJSONObject("snippet") ?: continue

            when (kind) {
                "youtube#channel" -> {
                    val chId = idObj.optString("channelId")
                    if (chId.isNotBlank()) {
                        channelIds.add(chId)
                        fallbackChannels.add(
                            ChannelEntity(
                                id = "ext_yt_$chId",
                                ownerUserId = "external_youtube",
                                name = snippet.optString("title"),
                                handle = "@${snippet.optString("title").lowercase().replace(Regex("[^a-z0-9]"), "")}",
                                avatarUrl = bestThumbnailUrl(snippet.optJSONObject("thumbnails")),
                                description = snippet.optString("description"),
                                country = "Global",
                                isVerified = true,
                                isExternal = true,
                                sourceName = "YouTube"
                            )
                        )
                    }
                }
                "youtube#video" -> {
                    val vId = idObj.optString("videoId")
                    val chId = snippet.optString("channelId")
                    if (vId.isNotBlank()) videoIds.add(vId)
                    if (chId.isNotBlank()) channelIds.add(chId)
                }
            }
        }

        val chMap = if (channelIds.isNotEmpty()) {
            fetchChannelsStatsViaDataApi(apiKey, channelIds.toList())
        } else emptyMap()

        val channels = (chMap.values + fallbackChannels).let { mergeChannelsPreferRicher(it.toList()) }
        val videos = if (videoIds.isNotEmpty()) {
            fetchVideosStatsViaDataApi(apiKey, videoIds, chMap)
        } else emptyList()

        return YouTubeSyncResult(channels = channels, videos = videos)
    }

    private fun fetchChannelsStatsViaDataApi(apiKey: String, channelIds: List<String>): Map<String, ChannelEntity> {
        val result = mutableMapOf<String, ChannelEntity>()
        channelIds.chunked(25).forEach { chunk ->
            val joined = URLEncoder.encode(chunk.joinToString(","), "UTF-8")
            val url = "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics,brandingSettings&id=$joined&key=$apiKey"
            val body = fetchWebHtml(url) ?: return@forEach
            val items = JSONObject(body).optJSONArray("items") ?: return@forEach
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val snippet = obj.optJSONObject("snippet") ?: continue
                val stats = obj.optJSONObject("statistics")
                val branding = obj.optJSONObject("brandingSettings")?.optJSONObject("image")
                val title = snippet.optString("title")
                val customUrl = snippet.optString("customUrl").takeIf { it.isNotBlank() }
                    ?: "@${title.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { id.take(12) }}"
                val avatar = bestThumbnailUrl(snippet.optJSONObject("thumbnails"))
                val banner = branding?.optString("bannerExternalUrl").orEmpty().ifBlank { avatar }
                val subCount = stats?.optString("subscriberCount")?.toLongOrNull() ?: 0L
                val countryCode = snippet.optString("country")

                result[id] = ChannelEntity(
                    id = "ext_yt_$id",
                    ownerUserId = "external_youtube",
                    name = title,
                    handle = if (customUrl.startsWith("@")) customUrl else "@$customUrl",
                    avatarUrl = avatar,
                    bannerUrl = banner,
                    description = snippet.optString("description"),
                    country = mapIsoCountry(countryCode),
                    subscriberCount = subCount,
                    isVerified = true,
                    isModerated = false,
                    isExternal = true,
                    sourceName = "YouTube"
                )
            }
        }
        return result
    }

    private fun fetchVideosStatsViaDataApi(
        apiKey: String,
        videoIds: List<String>,
        channelMap: Map<String, ChannelEntity>
    ): List<VideoEntity> {
        val videos = mutableListOf<VideoEntity>()
        videoIds.chunked(25).forEach { chunk ->
            val joined = URLEncoder.encode(chunk.joinToString(","), "UTF-8")
            val url = "https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails,statistics&id=$joined&key=$apiKey"
            val body = fetchWebHtml(url) ?: return@forEach
            val items = JSONObject(body).optJSONArray("items") ?: return@forEach
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val vidId = obj.optString("id")
                val snippet = obj.optJSONObject("snippet") ?: continue
                val contentDetails = obj.optJSONObject("contentDetails")
                val stats = obj.optJSONObject("statistics")

                val chId = snippet.optString("channelId")
                val chTitle = snippet.optString("channelTitle")
                val durationSec = parseIso8601Duration(contentDetails?.optString("duration").orEmpty())
                val title = snippet.optString("title")
                val isShort = durationSec in 1L..62L || title.contains("#shorts", ignoreCase = true)
                val country = channelMap[chId]?.country ?: "Global"
                val langCode = snippet.optString("defaultAudioLanguage").ifBlank { snippet.optString("defaultLanguage") }

                videos.add(
                    VideoEntity(
                        id = "yt_$vidId",
                        channelId = "ext_yt_$chId",
                        ownerUserId = "external_youtube",
                        title = title,
                        description = snippet.optString("description"),
                        thumbnailUrl = bestThumbnailUrl(snippet.optJSONObject("thumbnails"))
                            .ifBlank { "https://i.ytimg.com/vi/$vidId/hqdefault.jpg" },
                        primaryVideoUrl = "https://www.youtube.com/watch?v=$vidId",
                        durationSeconds = durationSec,
                        category = mapYouTubeCategoryId(snippet.optString("categoryId")),
                        tagsCsv = jsonArrayToCsv(snippet.optJSONArray("tags")),
                        language = mapIsoLanguage(langCode, country),
                        country = country,
                        isShort = isShort,
                        visibility = VideoVisibility.PUBLIC.name,
                        sourceType = VideoSourceType.YOUTUBE_EMBED.name,
                        sourceAttribution = "YouTube • $chTitle",
                        licenseName = "YouTube • Reproductor oficial integrado",
                        downloadAllowed = false,
                        viewCount = stats?.optString("viewCount")?.toLongOrNull() ?: 0L,
                        likeCount = stats?.optString("likeCount")?.toLongOrNull() ?: 0L,
                        commentCount = stats?.optString("commentCount")?.toLongOrNull() ?: 0L,
                        createdAt = parseIso8601Date(snippet.optString("publishedAt"))
                    )
                )
            }
        }
        return videos
    }

    internal fun parseYouTubeAtomFeed(
        xmlText: String,
        ucChannelId: String,
        seed: FeaturedYouTubeChannelSeed?
    ): YouTubeSyncResult {
        var channelTitle = seed?.fallbackName ?: "Canal de YouTube"
        val videos = mutableListOf<VideoEntity>()

        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlText))

            var insideEntry = false
            var currentVideoId = ""
            var currentTitle = ""
            var currentPublished = 0L
            var currentDesc = ""
            var currentThumb = ""
            var currentViews = 0L
            var currentAuthor = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name.orEmpty()
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tag) {
                            "entry" -> {
                                insideEntry = true
                                currentVideoId = ""
                                currentTitle = ""
                                currentPublished = System.currentTimeMillis()
                                currentDesc = ""
                                currentThumb = ""
                                currentViews = 0L
                                currentAuthor = ""
                            }
                            "title" -> {
                                val text = safeNextText(parser)
                                if (insideEntry) currentTitle = text
                                else if (text.isNotBlank()) channelTitle = text
                            }
                            "name" -> {
                                val text = safeNextText(parser)
                                if (insideEntry) currentAuthor = text
                                else if (text.isNotBlank()) channelTitle = text
                            }
                            "yt:videoId" -> {
                                if (insideEntry) currentVideoId = safeNextText(parser).trim()
                            }
                            "published" -> {
                                if (insideEntry) currentPublished = parseIso8601Date(safeNextText(parser))
                            }
                            "media:description" -> {
                                if (insideEntry) currentDesc = safeNextText(parser).trim()
                            }
                            "media:thumbnail" -> {
                                if (insideEntry) {
                                    currentThumb = parser.getAttributeValue(null, "url").orEmpty()
                                }
                            }
                            "media:statistics" -> {
                                if (insideEntry) {
                                    currentViews = parser.getAttributeValue(null, "views")?.toLongOrNull() ?: 0L
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag == "entry" && insideEntry) {
                            if (currentVideoId.isNotBlank() && currentTitle.isNotBlank()) {
                                val finalAuthor = currentAuthor.ifBlank { channelTitle }
                                val isShort = currentTitle.contains("#short", ignoreCase = true) ||
                                    currentDesc.contains("#short", ignoreCase = true)
                                videos.add(
                                    VideoEntity(
                                        id = "yt_$currentVideoId",
                                        channelId = "ext_yt_$ucChannelId",
                                        ownerUserId = "external_youtube",
                                        title = currentTitle,
                                        description = currentDesc.ifBlank { "Video oficial de $finalAuthor en YouTube." },
                                        thumbnailUrl = currentThumb.ifBlank { "https://i.ytimg.com/vi/$currentVideoId/hqdefault.jpg" },
                                        primaryVideoUrl = "https://www.youtube.com/watch?v=$currentVideoId",
                                        durationSeconds = if (isShort) 45L else 0L,
                                        category = seed?.category ?: "Entretenimiento",
                                        tagsCsv = listOfNotNull(seed?.aliases, finalAuthor).joinToString(","),
                                        language = seed?.language ?: "Global",
                                        country = seed?.country ?: "Global",
                                        isShort = isShort,
                                        visibility = VideoVisibility.PUBLIC.name,
                                        sourceType = VideoSourceType.YOUTUBE_EMBED.name,
                                        sourceAttribution = "YouTube • $finalAuthor",
                                        licenseName = "YouTube • Reproductor oficial integrado",
                                        downloadAllowed = false,
                                        viewCount = currentViews,
                                        likeCount = (currentViews / 22L),
                                        commentCount = 0L,
                                        createdAt = currentPublished
                                    )
                                )
                            }
                            insideEntry = false
                        }
                    }
                }
                eventType = parser.next()
            }
        }.onFailure {
            Log.e("YouTubeCatalogService", "Failed parsing Atom feed for $ucChannelId", it)
        }

        val firstThumb = videos.firstOrNull()?.thumbnailUrl.orEmpty()
        val channel = ChannelEntity(
            id = "ext_yt_$ucChannelId",
            ownerUserId = "external_youtube",
            name = channelTitle,
            handle = seed?.handle ?: "@${channelTitle.lowercase().replace(Regex("[^a-z0-9]"), "").take(18)}",
            avatarUrl = firstThumb,
            bannerUrl = firstThumb,
            description = seed?.aliases ?: "Canal oficial de YouTube ($channelTitle)",
            country = seed?.country ?: "Global",
            subscriberCount = videos.maxOfOrNull { it.viewCount / 10L }?.coerceAtLeast(1000L) ?: 0L,
            isVerified = true,
            isModerated = false,
            isExternal = true,
            sourceName = "YouTube"
        )

        return YouTubeSyncResult(channels = listOf(channel), videos = videos)
    }

    internal fun parseYtInitialDataSearchJson(jsonStr: String): YouTubeSyncResult {
        val channels = mutableListOf<ChannelEntity>()
        val videos = mutableListOf<VideoEntity>()

        runCatching {
            val root = JSONObject(jsonStr)
            val channelNodes = mutableListOf<JSONObject>()
            val videoNodes = mutableListOf<JSONObject>()
            val reelNodes = mutableListOf<JSONObject>()

            collectRenderers(root, channelNodes, videoNodes, reelNodes)

            for (node in channelNodes) {
                val chId = node.optString("channelId")
                if (!chId.startsWith("UC")) continue
                val title = extractRunsOrSimpleText(node.optJSONObject("title")).ifBlank { chId }
                val handleText = extractRunsOrSimpleText(node.optJSONObject("subscriberCountText"))
                val videoCountText = extractRunsOrSimpleText(node.optJSONObject("videoCountText"))
                val canonical = node.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("canonicalBaseUrl")
                    .orEmpty()

                val handle = when {
                    canonical.startsWith("/@") -> canonical.removePrefix("/")
                    handleText.startsWith("@") -> handleText.trim()
                    else -> "@${title.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "yt" }}_${chId.takeLast(4).lowercase()}"
                }

                // On YouTube's newer channelRenderer layout, subscriberCountText sometimes holds
                // the "@handle" while videoCountText holds "340 M de suscriptores"!
                val subCount = parseCompactCount(videoCountText)
                    ?: parseCompactCount(handleText)
                    ?: 0L

                val avatarUrl = extractBestRendererThumbnail(node.optJSONObject("thumbnail"))
                val desc = extractRunsOrSimpleText(node.optJSONObject("descriptionSnippet"))
                val isVerified = (node.optJSONArray("ownerBadges")?.length() ?: 0) > 0

                channels.add(
                    ChannelEntity(
                        id = "ext_yt_$chId",
                        ownerUserId = "external_youtube",
                        name = title,
                        handle = handle,
                        avatarUrl = avatarUrl,
                        bannerUrl = avatarUrl,
                        description = desc.ifBlank { "Canal oficial de YouTube ($title)" },
                        country = "Global",
                        subscriberCount = subCount,
                        isVerified = isVerified || subCount >= 100_000L,
                        isModerated = false,
                        isExternal = true,
                        sourceName = "YouTube"
                    )
                )
            }

            for (node in videoNodes) {
                val vidId = node.optString("videoId")
                if (vidId.isBlank()) continue
                val title = extractRunsOrSimpleText(node.optJSONObject("title")).ifBlank { continue }

                val ownerRun = node.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)
                    ?: node.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)
                val ownerName = ownerRun?.optString("text").orEmpty().ifBlank { "YouTube" }
                val ownerBrowseId = ownerRun?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    .orEmpty()

                val channelId = if (ownerBrowseId.startsWith("UC")) {
                    "ext_yt_$ownerBrowseId"
                } else {
                    "ext_yt_name_${ownerName.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { vidId }}"
                }

                val channelAvatar = node.optJSONObject("channelThumbnailSupportedRenderers")
                    ?.optJSONObject("channelThumbnailWithLinkRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.let { extractBestRendererThumbnail(it) }
                    .orEmpty()

                if (channels.none { it.id == channelId }) {
                    channels.add(
                        ChannelEntity(
                            id = channelId,
                            ownerUserId = "external_youtube",
                            name = ownerName,
                            handle = "@${ownerName.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "yt" }}_${channelId.takeLast(4).lowercase()}",
                            avatarUrl = channelAvatar,
                            bannerUrl = channelAvatar,
                            description = "Canal oficial de YouTube ($ownerName)",
                            country = "Global",
                            subscriberCount = 0L,
                            isVerified = (node.optJSONArray("ownerBadges")?.length() ?: 0) > 0,
                            isExternal = true,
                            sourceName = "YouTube"
                        )
                    )
                }

                val durationText = extractRunsOrSimpleText(node.optJSONObject("lengthText"))
                val durationSec = parseDisplayDuration(durationText)
                val viewText = extractRunsOrSimpleText(node.optJSONObject("viewCountText"))
                val views = parseCompactCount(viewText) ?: 0L
                val thumbUrl = extractBestRendererThumbnail(node.optJSONObject("thumbnail"))
                    .ifBlank { "https://i.ytimg.com/vi/$vidId/hqdefault.jpg" }
                val desc = extractRunsOrSimpleText(node.optJSONObject("descriptionSnippet"))
                val isShort = durationSec in 1L..62L ||
                    title.contains("#short", ignoreCase = true) ||
                    node.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("reelWatchEndpoint") != null

                videos.add(
                    VideoEntity(
                        id = "yt_$vidId",
                        channelId = channelId,
                        ownerUserId = "external_youtube",
                        title = title,
                        description = desc.ifBlank { "Video oficial de $ownerName en YouTube." },
                        thumbnailUrl = thumbUrl,
                        primaryVideoUrl = "https://www.youtube.com/watch?v=$vidId",
                        durationSeconds = durationSec,
                        category = "Entretenimiento",
                        tagsCsv = ownerName,
                        language = "Global",
                        country = "Global",
                        isShort = isShort,
                        visibility = VideoVisibility.PUBLIC.name,
                        sourceType = VideoSourceType.YOUTUBE_EMBED.name,
                        sourceAttribution = "YouTube • $ownerName",
                        licenseName = "YouTube • Reproductor oficial integrado",
                        downloadAllowed = false,
                        viewCount = views,
                        likeCount = views / 25L,
                        commentCount = 0L
                    )
                )
            }

            for (reel in reelNodes) {
                val vidId = reel.optString("videoId")
                    .ifBlank {
                        reel.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("reelWatchEndpoint")
                            ?.optString("videoId")
                            .orEmpty()
                    }
                if (vidId.isBlank()) continue
                val title = extractRunsOrSimpleText(reel.optJSONObject("headline")).ifBlank { "YouTube Short" }
                val views = parseCompactCount(extractRunsOrSimpleText(reel.optJSONObject("viewCountText"))) ?: 0L
                val thumb = extractBestRendererThumbnail(reel.optJSONObject("thumbnail"))
                    .ifBlank { "https://i.ytimg.com/vi/$vidId/hqdefault.jpg" }

                val fallbackChId = channels.firstOrNull()?.id ?: "ext_yt_shorts"
                videos.add(
                    VideoEntity(
                        id = "yt_$vidId",
                        channelId = fallbackChId,
                        ownerUserId = "external_youtube",
                        title = title,
                        description = "Short oficial en YouTube.",
                        thumbnailUrl = thumb,
                        primaryVideoUrl = "https://www.youtube.com/shorts/$vidId",
                        durationSeconds = 45L,
                        category = "Entretenimiento",
                        tagsCsv = "shorts",
                        language = "Global",
                        country = "Global",
                        isShort = true,
                        visibility = VideoVisibility.PUBLIC.name,
                        sourceType = VideoSourceType.YOUTUBE_EMBED.name,
                        sourceAttribution = "YouTube Shorts",
                        licenseName = "YouTube • Reproductor oficial integrado",
                        downloadAllowed = false,
                        viewCount = views,
                        likeCount = views / 20L
                    )
                )
            }
        }.onFailure {
            Log.e("YouTubeCatalogService", "Failed parsing ytInitialData", it)
        }

        return YouTubeSyncResult(
            channels = mergeChannelsPreferRicher(channels),
            videos = videos.distinctBy { it.id }
        )
    }

    private fun collectRenderers(
        node: Any?,
        channelOut: MutableList<JSONObject>,
        videoOut: MutableList<JSONObject>,
        reelOut: MutableList<JSONObject>
    ) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("channelRenderer")?.let { channelOut.add(it) }
                node.optJSONObject("videoRenderer")?.let { videoOut.add(it) }
                node.optJSONObject("reelItemRenderer")?.let { reelOut.add(it) }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k != "channelRenderer" && k != "videoRenderer" && k != "reelItemRenderer") {
                        collectRenderers(node.opt(k), channelOut, videoOut, reelOut)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectRenderers(node.opt(i), channelOut, videoOut, reelOut)
                }
            }
        }
    }

    private fun findFirstJsonObjectByKey(node: Any?, targetKey: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(targetKey)?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val found = findFirstJsonObjectByKey(node.opt(keys.next()), targetKey)
                    if (found != null) return found
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val found = findFirstJsonObjectByKey(node.opt(i), targetKey)
                    if (found != null) return found
                }
            }
        }
        return null
    }

    private fun findFirstSubscriberCountInHeader(root: JSONObject): String {
        val pageHeader = findFirstJsonObjectByKey(root, "pageHeaderViewModel") ?: return ""
        val textBlob = pageHeader.toString()
        val match = Regex(""""content"\s*:\s*"([^"]*(?:suscriptores|subscribers)[^"]*)"""", RegexOption.IGNORE_CASE)
            .find(textBlob)
        return match?.groupValues?.getOrNull(1).orEmpty()
    }

    internal fun extractYtInitialDataJson(html: String): String? {
        val markers = listOf(
            "var ytInitialData = ",
            "window[\"ytInitialData\"] = ",
            "ytInitialData = "
        )
        for (marker in markers) {
            val idx = html.indexOf(marker)
            if (idx < 0) continue
            val start = html.indexOf('{', idx + marker.length)
            if (start < 0) continue

            var depth = 0
            var inString = false
            var escaped = false
            for (i in start until html.length) {
                val c = html[i]
                if (escaped) {
                    escaped = false
                    continue
                }
                if (c == '\\' && inString) {
                    escaped = true
                    continue
                }
                if (c == '"') {
                    inString = !inString
                    continue
                }
                if (!inString) {
                    if (c == '{') depth++
                    else if (c == '}') {
                        depth--
                        if (depth == 0) {
                            return html.substring(start, i + 1)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun mergeChannelsPreferRicher(list: List<ChannelEntity>): List<ChannelEntity> {
        return list.groupBy { it.id }.values.map { group ->
            group.maxByOrNull { ch ->
                var score = 0
                if (ch.subscriberCount > 0L) score += 4
                if (ch.avatarUrl.isNotBlank()) score += 2
                if (ch.description.isNotBlank()) score += 1
                if (ch.country != "Global") score += 1
                score
            } ?: group.first()
        }
    }

    private fun extractRunsOrSimpleText(obj: JSONObject?): String {
        if (obj == null) return ""
        val simple = obj.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString().trim()
    }

    private fun extractBestRendererThumbnail(thumbObj: JSONObject?): String {
        val arr = thumbObj?.optJSONArray("thumbnails") ?: return ""
        if (arr.length() == 0) return ""
        val raw = arr.optJSONObject(arr.length() - 1)?.optString("url").orEmpty()
        return when {
            raw.startsWith("//") -> "https:$raw"
            else -> raw
        }
    }

    private fun bestThumbnailUrl(thumbnails: JSONObject?): String {
        if (thumbnails == null) return ""
        for (key in listOf("maxres", "standard", "high", "medium", "default")) {
            val u = thumbnails.optJSONObject(key)?.optString("url").orEmpty()
            if (u.isNotBlank()) return u
        }
        return ""
    }

    internal fun parseCompactCount(rawText: String?): Long? {
        if (rawText.isNullOrBlank()) return null
        val clean = rawText.lowercase()
            .replace("\u00A0", " ")
            .replace("de suscriptores", "")
            .replace("suscriptores", "")
            .replace("subscribers", "")
            .replace("subscriber", "")
            .replace("de vistas", "")
            .replace("visualizaciones", "")
            .replace("vistas", "")
            .replace("views", "")
            .replace("view", "")
            .trim()

        val match = Regex("""(\d+(?:[.,]\d+)?)\s*([kmb]|mil|millones|m)?""").find(clean) ?: return null
        val numToken = match.groupValues[1]
        val suffix = match.groupValues[2]

        val number = if (suffix.isEmpty()) {
            numToken.replace(".", "").replace(",", "").toDoubleOrNull() ?: return null
        } else {
            numToken.replace(",", ".").toDoubleOrNull() ?: return null
        }

        val multiplier = when (suffix) {
            "k", "mil" -> 1_000.0
            "m", "millones" -> 1_000_000.0
            "b" -> 1_000_000_000.0
            else -> 1.0
        }
        return (number * multiplier).toLong().coerceAtLeast(0L)
    }

    private fun parseDisplayDuration(raw: String): Long {
        if (raw.isBlank()) return 0L
        val parts = raw.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0L
        }
    }

    private fun parseIso8601Duration(iso: String): Long {
        if (iso.isBlank()) return 0L
        val regex = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""")
        val m = regex.find(iso) ?: return 0L
        val h = m.groupValues[1].toLongOrNull() ?: 0L
        val min = m.groupValues[2].toLongOrNull() ?: 0L
        val s = m.groupValues[3].toLongOrNull() ?: 0L
        return h * 3600 + min * 60 + s
    }

    private fun parseIso8601Date(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        )
        for (pattern in formats) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(raw)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return System.currentTimeMillis()
    }

    private fun safeNextText(parser: XmlPullParser): String {
        return runCatching { parser.nextText() }.getOrDefault("")
    }

    private fun jsonArrayToCsv(arr: JSONArray?): String {
        if (arr == null) return ""
        return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            .take(8)
            .joinToString(",")
    }

    private fun mapYouTubeCategoryId(catId: String): String = when (catId) {
        "10" -> "Música"
        "20" -> "Videojuegos"
        "17" -> "Deportes"
        "25" -> "Noticias"
        "27" -> "Educación"
        "28" -> "Tecnología"
        "1", "30", "43" -> "Películas"
        else -> "Entretenimiento"
    }

    private fun mapIsoCountry(code: String): String = when (code.uppercase()) {
        "MX" -> "México"
        "AR" -> "Argentina"
        "ES" -> "España"
        "CO" -> "Colombia"
        "SV" -> "El Salvador"
        "US" -> "EE.UU."
        "BR" -> "Brasil"
        "JP" -> "Japón"
        "FR" -> "Francia"
        else -> if (code.isBlank()) "Global" else code.uppercase()
    }

    private fun mapIsoLanguage(code: String, fallbackCountry: String): String {
        val lower = code.lowercase()
        return when {
            lower.startsWith("es") -> "Español"
            lower.startsWith("en") -> "English"
            lower.startsWith("pt") -> "Português"
            lower.startsWith("fr") -> "Français"
            lower.startsWith("ja") -> "日本語"
            fallbackCountry in listOf("México", "Argentina", "España", "Colombia", "El Salvador") -> "Español"
            fallbackCountry == "Brasil" -> "Português"
            fallbackCountry == "EE.UU." -> "English"
            else -> "Global"
        }
    }
}
