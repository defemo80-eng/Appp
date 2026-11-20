package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.DownloadEngine
import com.example.data.local.DeiPlayDatabase
import com.example.data.local.UserPreferences
import com.example.data.model.AccountRole
import com.example.data.model.AnalyticsPeriod
import com.example.data.model.VideoVisibility
import com.example.data.remote.WorldCatalogSync
import com.example.data.repository.DeiPlayRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeiPlayRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: DeiPlayDatabase
    private lateinit var repository: DeiPlayRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DeiPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefs = UserPreferences(context)
        val sync = WorldCatalogSync(db)
        val downloadEngine = DownloadEngine(context, db.downloadDao())
        repository = DeiPlayRepository(context, db, prefs, sync, downloadEngine)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun twoAccountEndToEndWorkflow_publishSubscribeLikeCommentAnalyticsAndAdminModeration() = runBlocking {
        repository.bootstrapPlatformDefaults()

        // 1. Register Account 1 (Founder -> ADMIN) and Account 2 (Viewer -> USER)
        val acc1 = repository.registerAccount("creator@deiplay.org", "CreatorOne", "secret123", "México").getOrThrow()
        assertEquals(AccountRole.ADMIN.name, acc1.role)

        val acc2 = repository.registerAccount("viewer@deiplay.org", "ViewerTwo", "secret456", "España").getOrThrow()
        assertEquals(AccountRole.USER.name, acc2.role)

        // 2. Account 1 creates a channel and publishes a local MP4 file
        val channel = repository.createChannel(
            ownerUserId = acc1.id,
            name = "DeiPlay Studio",
            handle = "@deiplaystudio",
            description = "Canal oficial de prueba",
            country = "México",
            avatarUri = null,
            bannerUri = null
        ).getOrThrow()

        val sampleFile = File(context.cacheDir, "sample_test_video.mp4").apply {
            writeBytes(ByteArray(1024) { 1 })
        }
        val video = repository.publishLocalVideo(
            ownerUserId = acc1.id,
            channelId = channel.id,
            videoUri = Uri.fromFile(sampleFile),
            thumbnailUri = null,
            title = "Documental Mundial",
            description = "Video publicado desde prueba real",
            category = "Educación",
            tagsCsv = "mundo,educacion",
            language = "Español",
            country = "México",
            isShort = false,
            visibility = VideoVisibility.PUBLIC,
            downloadAllowed = true,
            onProgress = {}
        ).getOrThrow()

        assertTrue(video.primaryVideoUrl.startsWith("file://"))

        // 3. Switch to Account 2: subscribe, like, comment, reply
        repository.loginAccount("viewer@deiplay.org", "secret456").getOrThrow()

        val subscribed = repository.toggleSubscription(acc2.id, channel)
        assertTrue(subscribed)
        val chAfterSub = repository.observeChannel(channel.id).first()!!
        assertEquals(1L, chAfterSub.subscriberCount)

        // Toggle like ON and verify counter = 1 (and toggling again unlikes cleanly, no duplication)
        assertTrue(repository.toggleLike(acc2.id, video.id))
        assertEquals(1L, repository.observeVideo(video.id).first()!!.video.likeCount)
        assertFalse(repository.toggleLike(acc2.id, video.id))
        assertEquals(0L, repository.observeVideo(video.id).first()!!.video.likeCount)
        assertTrue(repository.toggleLike(acc2.id, video.id))
        assertEquals(1L, repository.observeVideo(video.id).first()!!.video.likeCount)

        // Comment + record view
        repository.postComment(video.id, acc2, "Excelente documental!", null).getOrThrow()
        repository.recordView(acc2.id, video.id, pauseHistory = false)
        val updatedVideo = repository.observeVideo(video.id).first()!!.video
        assertEquals(1L, updatedVideo.commentCount)
        assertEquals(1L, updatedVideo.viewCount)

        // 4. Creator Analytics reflects exact real numbers
        val analytics = repository.computeCreatorAnalytics(acc1.id, AnalyticsPeriod.ALL_TIME)
        assertEquals(1L, analytics.totalViews)
        assertEquals(1L, analytics.totalSubscribers)
        assertEquals(1L, analytics.totalLikes)
        assertEquals(1L, analytics.totalComments)
        assertEquals(1, analytics.publishedVideos)

        // 5. Admin moderation removes & restores video and alters views, likes, and subscribers
        val removedRes = repository.adminRemoveVideo(acc1, updatedVideo, true)
        assertTrue(removedRes.isSuccess)
        assertTrue(repository.observeVideo(video.id).first()!!.video.isRemoved)
        assertTrue(repository.adminRemoveVideo(acc1, updatedVideo, false).isSuccess)

        assertTrue(repository.adminUpdateVideoMetrics(acc1, updatedVideo, newViews = 25000L, newLikes = 1850L).isSuccess)
        val alteredVideo = repository.observeVideo(video.id).first()!!.video
        assertEquals(25000L, alteredVideo.viewCount)
        assertEquals(1850L, alteredVideo.likeCount)

        assertTrue(repository.adminUpdateChannelSubscribers(acc1, chAfterSub, newSubscribers = 12400L).isSuccess)
        val alteredChannel = repository.observeChannel(channel.id).first()!!
        assertEquals(12400L, alteredChannel.subscriberCount)

        val logs = repository.auditLogsFlow.first()
        assertTrue(logs.any { it.action == "RETIRAR_VIDEO" })
        assertTrue(logs.any { it.action == "EDITAR_METRICAS_VIDEO" })
        assertTrue(logs.any { it.action == "EDITAR_SUSCRIPTORES" })

        // Non-admin cannot execute admin actions
        val deniedRes = repository.adminRemoveVideo(acc2, updatedVideo, false)
        assertTrue(deniedRes.isFailure)
        assertTrue(repository.adminUpdateVideoMetrics(acc2, updatedVideo, 999L, 999L).isFailure)
        assertTrue(repository.adminUpdateChannelSubscribers(acc2, chAfterSub, 999L).isFailure)

        // 6. Channel owner can edit name, handle, description, country, avatar and banner; non-owners cannot
        val sampleImg = File(context.cacheDir, "channel_banner.jpg").apply {
            writeBytes(ByteArray(128) { 2 })
        }
        val editedChannel = repository.updateChannel(
            channelId = channel.id,
            ownerUserId = acc1.id,
            name = "DeiPlay Global",
            handle = "@deiplayglobal",
            description = "Portada y nombre actualizados",
            country = "Colombia",
            avatarUri = Uri.fromFile(sampleImg),
            bannerUri = Uri.fromFile(sampleImg)
        ).getOrThrow()
        assertEquals("DeiPlay Global", editedChannel.name)
        assertEquals("@deiplayglobal", editedChannel.handle)
        assertEquals("Colombia", editedChannel.country)
        assertTrue(editedChannel.avatarUrl.isNotBlank())
        assertTrue(editedChannel.bannerUrl.isNotBlank())

        val unauthorizedEdit = repository.updateChannel(
            channelId = channel.id,
            ownerUserId = acc2.id,
            name = "Hack",
            handle = "@hack",
            description = "",
            country = "Global"
        )
        assertTrue(unauthorizedEdit.isFailure)
    }
}
