package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ChannelEntity
import com.example.data.local.VideoEntity
import com.example.data.local.VideoWithChannel
import com.example.data.model.VideoSourceType
import com.example.data.model.matchesWorldQuery
import com.example.data.remote.FeaturedYouTubeChannelSeed
import com.example.data.remote.YouTubeCatalogService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouTubeCatalogServiceTest {

    @Test
    fun matchesWorldQuery_handlesFamousCreatorTyposSpacesAndAccents() {
        assertTrue(matchesWorldQuery("mr beast", "MrBeast", "@MrBeast"))
        assertTrue(matchesWorldQuery("alejoigoa", "Alejo Igoa", "@AlejoIgoa"))
        assertTrue(matchesWorldQuery("fede vigevani", "Fede Vigevani", "@fedevigevani"))
        assertTrue(matchesWorldQuery("larin", "Alfredo Larin", "@alfredolarin"))
        assertTrue(matchesWorldQuery("karla abustillo", "Karla Bustillos", "@KarlaBustillos"))
        assertTrue(matchesWorldQuery("cachetes", "Rubén Tuesta (Cachetes)", "@Tuestaok", "Operación Cachetes"))
        assertFalse(matchesWorldQuery("nasa", "Fede Vigevani", "@fedevigevani"))
    }

    @Test
    fun parseYouTubeAtomFeed_extractsChannelAndVideosLegally() {
        // Ensures XML pull parser works cleanly in Android/Robolectric context
        ApplicationProvider.getApplicationContext<android.content.Context>()

        val sampleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015"
                  xmlns:media="http://search.yahoo.com/mrss/"
                  xmlns="http://www.w3.org/2005/Atom">
              <title>Fede Vigevani</title>
              <author><name>Fede Vigevani</name></author>
              <entry>
                <id>yt:video:abc123XYZ00</id>
                <yt:videoId>abc123XYZ00</yt:videoId>
                <title>Entramos a la casa abandonada</title>
                <published>2026-10-01T18:00:00+00:00</published>
                <author><name>Fede Vigevani</name></author>
                <media:group>
                  <media:description>Nuevo video mundial en DeiPlay</media:description>
                  <media:thumbnail url="https://i.ytimg.com/vi/abc123XYZ00/hqdefault.jpg" width="480" height="360"/>
                  <media:community>
                    <media:statistics views="8500000"/>
                  </media:community>
                </media:group>
              </entry>
            </feed>
        """.trimIndent()

        val service = YouTubeCatalogService()
        val seed = FeaturedYouTubeChannelSeed(
            channelId = "UCoQm-PeHC-cbJclKJYJ8LzA",
            fallbackName = "Fede Vigevani",
            handle = "@fedevigevani",
            aliases = "Fede Vigevani",
            country = "México",
            language = "Español",
            category = "Entretenimiento"
        )

        val res = service.parseYouTubeAtomFeed(sampleXml, "UCoQm-PeHC-cbJclKJYJ8LzA", seed)
        assertEquals(1, res.channels.size)
        assertEquals(1, res.videos.size)

        val ch = res.channels.first()
        assertEquals("ext_yt_UCoQm-PeHC-cbJclKJYJ8LzA", ch.id)
        assertEquals("YouTube", ch.sourceName)
        assertTrue(ch.isVerified)

        val vid = res.videos.first()
        assertEquals("yt_abc123XYZ00", vid.id)
        assertEquals(VideoSourceType.YOUTUBE_EMBED.name, vid.sourceType)
        assertFalse(vid.downloadAllowed)
        assertEquals(8_500_000L, vid.viewCount)
    }

    @Test
    fun parseYtInitialDataSearchJson_extractsChannelsAndVideos() {
        val sampleJson = """
            {
              "contents": {
                "twoColumnSearchResultsRenderer": {
                  "primaryContents": {
                    "sectionListRenderer": {
                      "contents": [
                        {
                          "itemSectionRenderer": {
                            "contents": [
                              {
                                "channelRenderer": {
                                  "channelId": "UCX6OQ3DkcsbYNE6H8uQQuVA",
                                  "title": { "simpleText": "MrBeast" },
                                  "subscriberCountText": { "simpleText": "@MrBeast" },
                                  "videoCountText": { "simpleText": "340 M de suscriptores" },
                                  "navigationEndpoint": {
                                    "browseEndpoint": { "canonicalBaseUrl": "/@MrBeast" }
                                  },
                                  "thumbnail": {
                                    "thumbnails": [{ "url": "//yt3.googleusercontent.com/mrbeast.jpg" }]
                                  },
                                  "ownerBadges": [{ "metadataBadgeRenderer": { "style": "BADGE_STYLE_TYPE_VERIFIED" } }]
                                }
                              },
                              {
                                "videoRenderer": {
                                  "videoId": "dQw4w9WgXcQ",
                                  "title": { "runs": [{ "text": "Sobreviví 100 días" }] },
                                  "lengthText": { "simpleText": "18:45" },
                                  "viewCountText": { "simpleText": "45 M de vistas" },
                                  "ownerText": {
                                    "runs": [{
                                      "text": "MrBeast",
                                      "navigationEndpoint": {
                                        "browseEndpoint": { "browseId": "UCX6OQ3DkcsbYNE6H8uQQuVA" }
                                      }
                                    }]
                                  },
                                  "thumbnail": {
                                    "thumbnails": [{ "url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg" }]
                                  }
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val service = YouTubeCatalogService()
        val parsed = service.parseYtInitialDataSearchJson(sampleJson)

        val mrBeast = parsed.channels.firstOrNull { it.id == "ext_yt_UCX6OQ3DkcsbYNE6H8uQQuVA" }
        assertNotNull(mrBeast)
        assertEquals("MrBeast", mrBeast!!.name)
        assertEquals("@MrBeast", mrBeast.handle)
        assertEquals(340_000_000L, mrBeast.subscriberCount)
        assertEquals("https://yt3.googleusercontent.com/mrbeast.jpg", mrBeast.avatarUrl)

        val vid = parsed.videos.firstOrNull { it.id == "yt_dQw4w9WgXcQ" }
        assertNotNull(vid)
        assertEquals(18L * 60 + 45L, vid!!.durationSeconds)
        assertEquals(45_000_000L, vid.viewCount)
        assertEquals(VideoSourceType.YOUTUBE_EMBED.name, vid.sourceType)
        assertFalse(vid.downloadAllowed)
    }
}
