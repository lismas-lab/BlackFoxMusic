package com.blackfox.music.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.blackfox.music.data.network.AudioStreamResolver
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File

class AudioPlayerManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {

    private enum class PlaybackEngine {
        NONE, EXOPLAYER, WEBVIEW
    }

    private var activeEngine = PlaybackEngine.NONE

    // 1. Центральный движок ExoPlayer с аудифокусом, Wakelock и фоновым воспроизведением
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    // 2. Fallback движок YouTube IFrame WebView
    val webViewEngine = YouTubeAudioWebView(context)

    private var mediaSession: MediaSession? = null

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(0L)
    val totalDurationMs: StateFlow<Long> = _totalDurationMs.asStateFlow()

    private val _isLoadingAudio = MutableStateFlow(false)
    val isLoadingAudio: StateFlow<Boolean> = _isLoadingAudio.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Track>>(emptyList())
    val currentQueue: StateFlow<List<Track>> = _currentQueue.asStateFlow()

    private var currentQueueIndex: Int = -1
    private var exoProgressJob: Job? = null
    private var streamResolveJob: Job? = null

    init {
        // Слушатель событий ExoPlayer
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                if (activeEngine == PlaybackEngine.EXOPLAYER) {
                    _isPlaying.value = playing
                    if (playing) startExoProgressTicker() else stopExoProgressTicker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (activeEngine == PlaybackEngine.EXOPLAYER) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            _isLoadingAudio.value = false
                            val duration = player.duration
                            if (duration > 0) _totalDurationMs.value = duration
                        }
                        Player.STATE_BUFFERING -> _isLoadingAudio.value = true
                        Player.STATE_ENDED -> {
                            _isPlaying.value = false
                            _currentPositionMs.value = 0L
                            stopExoProgressTicker()
                            playNext()
                        }
                    }
                }
            }
        })

        // Слушатели событий WebView движка
        scope.launch {
            webViewEngine.isPlaying.collect { playing ->
                if (activeEngine == PlaybackEngine.WEBVIEW) {
                    _isPlaying.value = playing
                }
            }
        }
        scope.launch {
            webViewEngine.currentPositionMs.collect { pos ->
                if (activeEngine == PlaybackEngine.WEBVIEW) {
                    _currentPositionMs.value = pos
                }
            }
        }
        scope.launch {
            webViewEngine.durationMs.collect { dur ->
                if (activeEngine == PlaybackEngine.WEBVIEW && dur > 0) {
                    _totalDurationMs.value = dur
                }
            }
        }
        scope.launch {
            webViewEngine.isLoading.collect { loading ->
                if (activeEngine == PlaybackEngine.WEBVIEW) {
                    _isLoadingAudio.value = loading
                }
            }
        }
    }

    fun getOrCreateMediaSession(serviceContext: Context): MediaSession {
        if (mediaSession == null) {
            val sessionActivityPendingIntent = PendingIntent.getActivity(
                serviceContext,
                0,
                Intent(serviceContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            mediaSession = MediaSession.Builder(serviceContext, player)
                .setSessionActivity(sessionActivityPendingIntent)
                .build()
        }
        return mediaSession!!
    }

    private fun startPlaybackService() {
        try {
            val intent = Intent(context, PlaybackService::class.java)
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setQueueAndPlay(tracks: List<Track>, startIndex: Int) {
        _currentQueue.value = tracks
        currentQueueIndex = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        if (currentQueueIndex in tracks.indices) {
            playTrack(tracks[currentQueueIndex])
        }
    }

    fun playNext() {
        val queue = _currentQueue.value
        val nextIdx = currentQueueIndex + 1
        if (nextIdx in queue.indices) {
            currentQueueIndex = nextIdx
            playTrack(queue[nextIdx])
        }
    }

    fun playPrevious() {
        if (_currentPositionMs.value > 3000L) {
            seekTo(0)
            return
        }
        val queue = _currentQueue.value
        val prevIdx = currentQueueIndex - 1
        if (prevIdx in queue.indices) {
            currentQueueIndex = prevIdx
            playTrack(queue[prevIdx])
        } else {
            seekTo(0)
        }
    }

    fun playTrack(track: Track) {
        streamResolveJob?.cancel()
        _currentTrack.value = track
        _currentPositionMs.value = 0L

        val initialDurationMs = parseDurationStringToMs(track.duration)
        if (initialDurationMs > 0) {
            _totalDurationMs.value = initialDurationMs
        }
        _isLoadingAudio.value = true

        // 1. Проверяем наличие скачанного файла на устройстве
        val musicDirectory = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "BlackFox")
        val candidateMp3 = File(musicDirectory, "${track.id}.mp3")
        val candidateM4a = File(musicDirectory, "${track.id}.m4a")

        val localFile = when {
            !track.localFilePath.isNullOrBlank() && File(track.localFilePath).exists() -> File(track.localFilePath)
            candidateMp3.exists() -> candidateMp3
            candidateM4a.exists() -> candidateM4a
            else -> null
        }

        if (localFile != null) {
            activeEngine = PlaybackEngine.EXOPLAYER
            webViewEngine.pauseTrack()
            startPlaybackService()

            val mediaItem = buildMediaItem(track, Uri.fromFile(localFile))
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            loadArtwork(track)
            return
        }

        // 2. Онлайн воспроизведение через прямой поток (ExoPlayer) с фоновым сервисом
        streamResolveJob = scope.launch(Dispatchers.IO) {
            val directStreamUrl = AudioStreamResolver.resolveAudioStreamUrl(track.id)
            withContext(Dispatchers.Main) {
                if (!directStreamUrl.isNullOrBlank()) {
                    activeEngine = PlaybackEngine.EXOPLAYER
                    webViewEngine.pauseTrack()
                    startPlaybackService()

                    val mediaItem = buildMediaItem(track, Uri.parse(directStreamUrl))
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                    loadArtwork(track)
                } else {
                    // Fallback на WebView
                    activeEngine = PlaybackEngine.WEBVIEW
                    player.stop()
                    webViewEngine.playTrack(track.id)
                    _isLoadingAudio.value = false
                }
            }
        }
    }

    private fun buildMediaItem(track: Track, uri: Uri): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album?.ifBlank { "Black Fox Music" } ?: "Black Fox Music")
            .setArtworkUri(if (track.thumbnailUrl.isNotBlank()) Uri.parse(track.thumbnailUrl) else null)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun loadArtwork(track: Track) {
        if (track.thumbnailUrl.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val req = ImageRequest.Builder(context)
                    .data(track.thumbnailUrl)
                    .size(512, 512)
                    .build()
                val result = (context.imageLoader.execute(req) as? SuccessResult)?.drawable
                val bitmap = (result as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    val artworkBytes = stream.toByteArray()

                    withContext(Dispatchers.Main) {
                        val currentItem = player.currentMediaItem
                        if (currentItem != null && currentItem.mediaId == track.id) {
                            val updatedMetadata = currentItem.mediaMetadata.buildUpon()
                                .setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                .build()
                            val updatedItem = currentItem.buildUpon()
                                .setMediaMetadata(updatedMetadata)
                                .build()
                            player.replaceMediaItem(player.currentMediaItemIndex, updatedItem)
                        }
                    }
                }
            } catch (e: Exception) {
                // Игнорируем ошибку обложки
            }
        }
    }

    fun togglePlayPause() {
        when (activeEngine) {
            PlaybackEngine.EXOPLAYER -> {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    if (player.playbackState == Player.STATE_IDLE) {
                        _currentTrack.value?.let { playTrack(it) }
                    } else {
                        player.play()
                    }
                }
            }
            PlaybackEngine.WEBVIEW -> {
                if (webViewEngine.isPlaying.value) {
                    webViewEngine.pauseTrack()
                } else {
                    webViewEngine.resumeTrack()
                }
            }
            PlaybackEngine.NONE -> {
                _currentTrack.value?.let { playTrack(it) }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs
        when (activeEngine) {
            PlaybackEngine.EXOPLAYER -> player.seekTo(positionMs)
            PlaybackEngine.WEBVIEW -> webViewEngine.seekTo(positionMs)
            PlaybackEngine.NONE -> {}
        }
    }

    private fun startExoProgressTicker() {
        stopExoProgressTicker()
        exoProgressJob = scope.launch {
            while (isActive) {
                _currentPositionMs.value = player.currentPosition
                val dur = player.duration
                if (dur > 0) _totalDurationMs.value = dur
                delay(300)
            }
        }
    }

    private fun stopExoProgressTicker() {
        exoProgressJob?.cancel()
        exoProgressJob = null
    }

    private fun parseDurationStringToMs(durationStr: String): Long {
        if (durationStr.isBlank()) return 0L
        return try {
            val parts = durationStr.split(":").mapNotNull { it.trim().toLongOrNull() }
            when (parts.size) {
                1 -> parts[0] * 1000L
                2 -> (parts[0] * 60L + parts[1]) * 1000L
                3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    fun release() {
        streamResolveJob?.cancel()
        stopExoProgressTicker()
        player.release()
        webViewEngine.release()
        mediaSession?.release()
        mediaSession = null
    }
}
