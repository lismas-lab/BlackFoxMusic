package com.blackfox.music.data.download

import android.content.Context
import android.os.Environment
import com.blackfox.music.data.local.MusicDao
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.data.network.AudioStreamResolver
import com.blackfox.music.domain.model.Album
import com.blackfox.music.domain.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit

class AudioDownloadManager(
    private val context: Context,
    private val musicDao: MusicDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    private val _downloadProgressMap = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, DownloadProgress>> = _downloadProgressMap.asStateFlow()

    // Последовательная очередь закачек для исключения конфликтов потоков и таймаутов
    private val downloadChannel = Channel<Track>(Channel.UNLIMITED)
    private val activeOrQueuedTrackIds = Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile private var currentDownloadingTrackId: String? = null
    @Volatile private var currentDownloadingJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val musicDirectory: File by lazy {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "BlackFox")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    val coversDirectory: File by lazy {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Covers")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    init {
        // Фоновый рабочий воркер, последовательно берущий треки из очереди
        scope.launch {
            for (track in downloadChannel) {
                // Если задача была отменена пока стояла в очереди
                if (!activeOrQueuedTrackIds.contains(track.id)) {
                    continue
                }

                currentDownloadingTrackId = track.id
                val job = launch {
                    processTrackDownload(track)
                }
                currentDownloadingJob = job
                job.join()
                currentDownloadingJob = null
                currentDownloadingTrackId = null
                activeOrQueuedTrackIds.remove(track.id)
            }
        }
    }

    /**
     * Поставить трек в очередь на скачивание
     */
    fun downloadTrack(track: Track) {
        if (activeOrQueuedTrackIds.contains(track.id)) return

        scope.launch {
            val existing = musicDao.getTrackById(track.id)
            if (existing?.isDownloaded == true && existing.localFilePath != null && File(existing.localFilePath).exists()) {
                updateProgress(track.id, DownloadProgress(track.id, 100, 0, 0, DownloadStatus.COMPLETED))
                return@launch
            }

            activeOrQueuedTrackIds.add(track.id)
            updateProgress(
                track.id,
                DownloadProgress(track.id, 0, 0, 0, DownloadStatus.DOWNLOADING, "В очереди...")
            )
            downloadChannel.send(track)
        }
    }

    private suspend fun processTrackDownload(track: Track) {
        val tempFile = File(musicDirectory, "${track.id}.tmp")
        try {
            updateProgress(track.id, DownloadProgress(track.id, 5, 0, 0, DownloadStatus.DOWNLOADING))

            // 1. Получаем прямую ссылку на аудиопоток (с повторной попыткой при необходимости)
            var directStreamUrl: String? = null
            var attempts = 0
            while (directStreamUrl.isNullOrBlank() && attempts < 2) {
                attempts++
                directStreamUrl = AudioStreamResolver.resolveAudioStreamUrl(track.id) { prepPercent ->
                    updateProgress(track.id, DownloadProgress(track.id, prepPercent, 0, 0, DownloadStatus.DOWNLOADING))
                }
                if (directStreamUrl.isNullOrBlank() && attempts < 2) {
                    delay(1500)
                }
            }

            if (directStreamUrl.isNullOrBlank()) {
                updateProgress(track.id, DownloadProgress(track.id, 0, 0, 0, DownloadStatus.FAILED, "Не удалось получить аудиопоток"))
                return
            }

            // 2. Убеждаемся, что трек есть в локальной базе Room
            val existing = musicDao.getTrackById(track.id)
            val isFav = existing?.isFavorite ?: false
            if (existing == null) {
                musicDao.insertTrack(
                    TrackEntity(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        duration = track.duration,
                        thumbnailUrl = track.thumbnailUrl,
                        isDownloaded = false,
                        isFavorite = isFav
                    )
                )
            }

            // 3. Скачиваем аудиофайл во временный файл
            val ext = if (directStreamUrl.contains(".m4a") || directStreamUrl.contains("audio/mp4")) "m4a" else "mp3"
            val finalFile = File(musicDirectory, "${track.id}.$ext")

            if (tempFile.exists()) tempFile.delete()

            val totalBytes = downloadFileWithProgress(directStreamUrl, tempFile) { percent, downloaded, total ->
                updateProgress(
                    track.id,
                    DownloadProgress(track.id, percent, downloaded, total, DownloadStatus.DOWNLOADING)
                )
            }

            // Атомарно переименовываем в финальный файл
            if (tempFile.exists() && tempFile.length() > 0) {
                if (finalFile.exists()) finalFile.delete()
                tempFile.renameTo(finalFile)
            } else {
                throw IOException("Файл скачался пустым")
            }

            // 4. Скачиваем обложку для оффлайна
            if (track.thumbnailUrl.isNotBlank()) {
                try {
                    val coverFile = File(coversDirectory, "${track.id}_cover.jpg")
                    downloadDirectFile(track.thumbnailUrl, coverFile)
                } catch (e: Exception) {
                    // Обложка не должна блокировать скачивание
                }
            }

            // 5. Записываем в базу Room
            musicDao.updateDownloadStatus(
                trackId = track.id,
                isDownloaded = true,
                filePath = finalFile.absolutePath,
                downloadedAt = System.currentTimeMillis()
            )

            updateProgress(
                track.id,
                DownloadProgress(track.id, 100, totalBytes, totalBytes, DownloadStatus.COMPLETED)
            )

        } catch (e: CancellationException) {
            if (tempFile.exists()) tempFile.delete()
            updateProgress(track.id, DownloadProgress(track.id, 0, 0, 0, DownloadStatus.FAILED, "Отменено"))
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            updateProgress(track.id, DownloadProgress(track.id, 0, 0, 0, DownloadStatus.FAILED, e.localizedMessage))
        }
    }

    /**
     * Скачать альбом целиком
     */
    fun downloadAlbum(album: Album) {
        album.tracks.forEach { track ->
            downloadTrack(track)
        }
    }

    suspend fun deleteDownloadedTrack(trackId: String) = withContext(Dispatchers.IO) {
        activeOrQueuedTrackIds.remove(trackId)
        if (currentDownloadingTrackId == trackId) {
            currentDownloadingJob?.cancel()
        }

        val tmp = File(musicDirectory, "$trackId.tmp")
        if (tmp.exists()) tmp.delete()

        val mp3 = File(musicDirectory, "$trackId.mp3")
        if (mp3.exists()) mp3.delete()

        val m4a = File(musicDirectory, "$trackId.m4a")
        if (m4a.exists()) m4a.delete()

        val coverFile = File(coversDirectory, "${trackId}_cover.jpg")
        if (coverFile.exists()) coverFile.delete()

        musicDao.updateDownloadStatus(
            trackId = trackId,
            isDownloaded = false,
            filePath = null,
            downloadedAt = null
        )

        _downloadProgressMap.value = _downloadProgressMap.value - trackId
    }

    fun getDownloadedStorageSize(): Long {
        var size = 0L
        musicDirectory.listFiles()?.forEach { size += it.length() }
        coversDirectory.listFiles()?.forEach { size += it.length() }
        return size
    }

    suspend fun clearAllDownloaded() = withContext(Dispatchers.IO) {
        currentDownloadingJob?.cancel()
        currentDownloadingJob = null
        activeOrQueuedTrackIds.clear()

        musicDirectory.listFiles()?.forEach { it.delete() }
        coversDirectory.listFiles()?.forEach { it.delete() }
        musicDao.resetAllDownloadedStatus()
        _downloadProgressMap.value = emptyMap()
    }

    private suspend fun downloadFileWithProgress(
        urlStr: String,
        destination: File,
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlStr)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Ошибка загрузки: HTTP ${response.code}")
        }

        val body = response.body ?: throw IOException("Пустой ответ от сервера")
        val totalLength = body.contentLength()
        var downloadedLength = 0L

        body.byteStream().use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                var lastPercent = 25

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    ensureActive()
                    output.write(buffer, 0, bytesRead)
                    downloadedLength += bytesRead

                    if (totalLength > 0) {
                        val currentPercent = (25 + ((downloadedLength * 75) / totalLength)).toInt().coerceIn(25, 99)
                        if (currentPercent != lastPercent) {
                            lastPercent = currentPercent
                            onProgress(currentPercent, downloadedLength, totalLength)
                        }
                    } else {
                        val estimatedTotal = 5_000_000L
                        val currentPercent = (25 + ((downloadedLength * 75) / estimatedTotal)).toInt().coerceIn(25, 98)
                        if (currentPercent != lastPercent) {
                            lastPercent = currentPercent
                            onProgress(currentPercent, downloadedLength, downloadedLength)
                        }
                    }
                }
            }
        }
        downloadedLength
    }

    private suspend fun downloadDirectFile(urlStr: String, destination: File) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlStr)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val response = httpClient.newCall(request).execute()
        if (response.isSuccessful) {
            response.body?.byteStream()?.use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun updateProgress(trackId: String, progress: DownloadProgress) {
        _downloadProgressMap.value = _downloadProgressMap.value + (trackId to progress)
    }
}
