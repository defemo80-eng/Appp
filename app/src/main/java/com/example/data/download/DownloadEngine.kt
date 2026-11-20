package com.example.data.download

import android.content.Context
import android.util.Log
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.VideoWithChannel
import com.example.data.model.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadEngine(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val downloadsDir: File by lazy {
        File(context.filesDir, "downloads").apply { mkdirs() }
    }

    fun startOrResumeDownload(
        item: VideoWithChannel,
        qualityLabel: String,
        sourceUrl: String
    ) {
        val video = item.video
        if (!video.downloadAllowed) return
        val videoId = video.id
        activeJobs[videoId]?.cancel()

        val targetFile = File(downloadsDir, "${videoId}_${qualityLabel}.mp4")

        val job = scope.launch {
            val existing = downloadDao.getById(videoId)
            val alreadyDownloaded = if (targetFile.exists()) targetFile.length() else 0L
            val initialEntity = (existing ?: DownloadEntity(
                videoId = videoId,
                title = video.title,
                channelName = item.channel?.name ?: video.sourceAttribution,
                thumbnailUrl = video.thumbnailUrl,
                qualityLabel = qualityLabel,
                remoteUrl = sourceUrl,
                localFilePath = targetFile.absolutePath,
                licenseName = video.licenseName,
                durationSeconds = video.durationSeconds,
                isShort = video.isShort
            )).copy(
                qualityLabel = qualityLabel,
                remoteUrl = sourceUrl,
                localFilePath = targetFile.absolutePath,
                bytesDownloaded = alreadyDownloaded,
                status = DownloadStatus.DOWNLOADING.name,
                updatedAt = System.currentTimeMillis()
            )
            downloadDao.upsert(initialEntity)

            try {
                if (sourceUrl.startsWith("file://") || sourceUrl.startsWith("/")) {
                    val srcPath = sourceUrl.removePrefix("file://")
                    val srcFile = File(srcPath)
                    if (!srcFile.exists()) throw IllegalStateException("Archivo local no encontrado")
                    val total = srcFile.length().coerceAtLeast(1L)
                    FileInputStream(srcFile).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buf = ByteArray(16 * 1024)
                            var copied = 0L
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                copied += read
                            }
                            downloadDao.upsert(
                                initialEntity.copy(
                                    bytesDownloaded = copied,
                                    totalBytes = total,
                                    status = DownloadStatus.COMPLETED.name,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    return@launch
                }

                val requestBuilder = Request.Builder().url(sourceUrl)
                if (alreadyDownloaded > 0L) {
                    requestBuilder.header("Range", "bytes=$alreadyDownloaded-")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("Respuesta vacía")
                    val isResumed = response.code == 206 && alreadyDownloaded > 0L
                    val contentLength = body.contentLength()
                    val totalBytes = if (isResumed && contentLength > 0) {
                        alreadyDownloaded + contentLength
                    } else if (contentLength > 0) {
                        contentLength
                    } else {
                        existing?.totalBytes ?: 0L
                    }

                    var currentBytes = if (isResumed) alreadyDownloaded else 0L
                    FileOutputStream(targetFile, isResumed).use { fos ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(16 * 1024)
                            var bytesRead: Int
                            var lastDbUpdate = System.currentTimeMillis()
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                fos.write(buffer, 0, bytesRead)
                                currentBytes += bytesRead
                                val now = System.currentTimeMillis()
                                if (now - lastDbUpdate > 300L) {
                                    lastDbUpdate = now
                                    downloadDao.upsert(
                                        initialEntity.copy(
                                            bytesDownloaded = currentBytes,
                                            totalBytes = totalBytes.coerceAtLeast(currentBytes),
                                            status = DownloadStatus.DOWNLOADING.name,
                                            updatedAt = now
                                        )
                                    )
                                }
                            }
                        }
                    }

                    downloadDao.upsert(
                        initialEntity.copy(
                            bytesDownloaded = targetFile.length(),
                            totalBytes = targetFile.length(),
                            status = DownloadStatus.COMPLETED.name,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DownloadEngine", "Download error for $videoId", e)
                downloadDao.upsert(
                    initialEntity.copy(
                        bytesDownloaded = if (targetFile.exists()) targetFile.length() else 0L,
                        status = DownloadStatus.FAILED.name,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } finally {
                activeJobs.remove(videoId)
            }
        }

        activeJobs[videoId] = job
    }

    suspend fun pauseDownload(videoId: String) {
        activeJobs[videoId]?.cancel()
        activeJobs.remove(videoId)
        val current = downloadDao.getById(videoId) ?: return
        val file = File(current.localFilePath)
        downloadDao.upsert(
            current.copy(
                bytesDownloaded = if (file.exists()) file.length() else current.bytesDownloaded,
                status = DownloadStatus.PAUSED.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun cancelAndDeleteDownload(videoId: String) {
        activeJobs[videoId]?.cancel()
        activeJobs.remove(videoId)
        val current = downloadDao.getById(videoId)
        if (current != null) {
            runCatching { File(current.localFilePath).delete() }
            downloadDao.delete(videoId)
        }
    }

    fun calculateTotalStorageBytes(): Long {
        return downloadsDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
