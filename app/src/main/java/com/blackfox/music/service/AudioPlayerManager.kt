package com.blackfox.music.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.blackfox.music.data.local.AppSettings
import com.blackfox.music.data.local.MusicDao
import com.blackfox.music.data.network.AudioStreamResolver
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioPlayerManager(
    private val context: Context,
    private val musicDao: MusicDao? = null,
    private val appSettings: AppSettings? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {

    constructor(context: Context, appSettings: AppSettings?) : this(context, null, appSettings)

    internal enum class PlaybackEngine {
        NONE, EXOPLAYER, WEBVIEW
    }

    internal var activeEngine = PlaybackEngine.NONE

    private var isPlayingLocalFile: Boolean = false
    private var currentLocalFilePath: String? = null

    // 1. Центральный движок ExoPlayer с аудиофокусом, устойчивыми таймаутами для VPN и Wakelock
    @OptIn(UnstableApi::class)
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
        .setConnectTimeoutMs(20_000)
        .setReadTimeoutMs(25_000)
        .setAllowCrossProtocolRedirects(true)

    @OptIn(UnstableApi::class)
    private val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(DefaultDataSource.Factory(context, httpDataSourceFactory))

    @OptIn(UnstableApi::class)
    private val fastLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 10_000,
            /* maxBufferMs = */ 40_000,
            /* bufferForPlaybackMs = */ 500, // 500 мс буфера (~8-10 КБ) для моментального старта воспроизведения
            /* bufferForPlaybackAfterRebufferMs = */ 1_500
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .setBackBuffer(20_000, true) // Сохраняем 20 сек обратного буфера для плавной перемотки назад
        .build()

    @OptIn(UnstableApi::class)
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(fastLoadControl)
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
    private var forwardingPlayer: BlackFoxForwardingPlayer? = null

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
                            preResolveNextTrack()
                        }
                        Player.STATE_BUFFERING -> _isLoadingAudio.value = true
                        Player.STATE_ENDED -> {
                            val duration = player.duration
                            val currentPos = player.currentPosition
                            val currentTrackValue = _currentTrack.value

                            if (currentPos < 2000L) {
                                // Воспроизведение завершилось практически сразу на старте (0-2 сек).
                                // Это признак поврежденного локального файла или моментального обрыва потока!
                                // Ни в коем случае НЕ вызываем playNext() — иначе трек просто скипнется!
                                if (isPlayingLocalFile && currentTrackValue != null) {
                                    handleCorruptLocalFileAndFallback(currentTrackValue)
                                } else if (currentTrackValue != null) {
                                    // Онлайн-поток не смог запуститься — пробуем бесшовный fallback на WebView
                                    activeEngine = PlaybackEngine.WEBVIEW
                                    player.stop()
                                    player.clearMediaItems()
                                    webViewEngine.playTrack(currentTrackValue.id, startPositionMs = 0L)
                                } else {
                                    _isPlaying.value = false
                                    stopExoProgressTicker()
                                }
                            } else if (duration > 0 && currentPos < duration - 2500L) {
                                // Воспроизведение неожиданно сорвалось далеко от конца трека (срыв Range при перемотке)
                                val savedPos = _currentPositionMs.value
                                activeEngine = PlaybackEngine.WEBVIEW
                                player.stop()
                                player.clearMediaItems()
                                currentTrackValue?.let { tr ->
                                    webViewEngine.playTrack(tr.id, startPositionMs = savedPos)
                                }
                            } else {
                                // Трек успешно доиграл до конца — переходим к следующему
                                _isPlaying.value = false
                                _currentPositionMs.value = 0L
                                stopExoProgressTicker()
                                playNext()
                            }
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Если ExoPlayer выдал ошибку на локальном файле — очищаем поврежденный файл и играем онлайн
                if (activeEngine == PlaybackEngine.EXOPLAYER) {
                    val currentTrackValue = _currentTrack.value
                    if (isPlayingLocalFile && currentTrackValue != null) {
                        handleCorruptLocalFileAndFallback(currentTrackValue)
                    } else if (currentTrackValue != null) {
                        val savedPositionMs = _currentPositionMs.value
                        activeEngine = PlaybackEngine.WEBVIEW
                        player.stop()
                        player.clearMediaItems()
                        webViewEngine.playTrack(currentTrackValue.id, startPositionMs = savedPositionMs)
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

            val fp = BlackFoxForwardingPlayer(player, this)
            forwardingPlayer = fp

            mediaSession = MediaSession.Builder(serviceContext, fp)
                .setSessionActivity(sessionActivityPendingIntent)
                .setCallback(object : MediaSession.Callback {
                    override fun onConnect(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo
                    ): MediaSession.ConnectionResult {
                        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().build()
                        val playerCommands = session.player.availableCommands
                        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            .setAvailableSessionCommands(sessionCommands)
                            .setAvailablePlayerCommands(playerCommands)
                            .build()
                    }
                })
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
        if (tracks.isEmpty()) return
        _currentQueue.value = tracks
        currentQueueIndex = startIndex.coerceIn(0, tracks.lastIndex)
        playTrack(tracks[currentQueueIndex])
    }

    fun playNext(loop: Boolean = false) {
        val queue = _currentQueue.value
        if (queue.isEmpty()) return
        val nextIdx = currentQueueIndex + 1
        if (nextIdx in queue.indices) {
            currentQueueIndex = nextIdx
            playTrack(queue[nextIdx])
        } else if (loop && queue.isNotEmpty()) {
            currentQueueIndex = 0
            playTrack(queue[0])
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
        } else if (queue.isNotEmpty()) {
            currentQueueIndex = queue.lastIndex
            playTrack(queue[currentQueueIndex])
        } else {
            seekTo(0)
        }
    }

    fun playTrack(track: Track) {
        streamResolveJob?.cancel()

        // ВАЖНО: Мгновенно останавливаем предыдущий играющий трек, чтобы звук сразу прекратился!
        player.stop()
        player.clearMediaItems()
        webViewEngine.stopTrack()

        _currentTrack.value = track
        _currentPositionMs.value = 0L

        val q = _currentQueue.value
        val foundIdx = q.indexOfFirst { it.id == track.id }
        if (foundIdx != -1) {
            currentQueueIndex = foundIdx
        } else if (q.isEmpty()) {
            _currentQueue.value = listOf(track)
            currentQueueIndex = 0
        }

        val initialDurationMs = parseDurationStringToMs(track.duration)
        if (initialDurationMs > 0) {
            _totalDurationMs.value = initialDurationMs
        }
        _isLoadingAudio.value = true

        // 1. Проверяем наличие полностью скачанного файла на устройстве
        val musicDirectory = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "BlackFox")
        val candidateM4a = File(musicDirectory, "${track.id}.m4a")
        val candidateMp3 = File(musicDirectory, "${track.id}.mp3")
        val candidateOpus = File(musicDirectory, "${track.id}.opus")
        val candidateWebm = File(musicDirectory, "${track.id}.webm")

        val localFile = when {
            !track.localFilePath.isNullOrBlank() && File(track.localFilePath).exists() && File(track.localFilePath).length() > 50_000L -> File(track.localFilePath)
            candidateM4a.exists() && candidateM4a.length() > 50_000L -> candidateM4a
            candidateMp3.exists() && candidateMp3.length() > 50_000L -> candidateMp3
            candidateOpus.exists() && candidateOpus.length() > 50_000L -> candidateOpus
            candidateWebm.exists() && candidateWebm.length() > 50_000L -> candidateWebm
            else -> null
        }

        if (localFile != null) {
            isPlayingLocalFile = true
            currentLocalFilePath = localFile.absolutePath
            activeEngine = PlaybackEngine.EXOPLAYER
            startPlaybackService()

            val mediaItem = buildMediaItem(track, Uri.fromFile(localFile))
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            return
        }

        // 2. Онлайн воспроизведение через прямой поток (ExoPlayer) с фоновым сервисом
        playTrackOnline(track)
    }

    private fun playTrackOnline(track: Track) {
        isPlayingLocalFile = false
        currentLocalFilePath = null
        val streamQuality = appSettings?.getStreamQuality() ?: "standard"
        streamResolveJob = scope.launch(Dispatchers.IO) {
            val directStreamUrl = AudioStreamResolver.resolveAudioStreamUrl(
                videoId = track.id,
                forDownload = false,
                quality = streamQuality
            )
            withContext(Dispatchers.Main) {
                // Убеждаемся, что за время загрузки ссылки пользователь не переключил на другой трек
                if (_currentTrack.value?.id != track.id) {
                    return@withContext
                }

                if (!directStreamUrl.isNullOrBlank()) {
                    activeEngine = PlaybackEngine.EXOPLAYER
                    startPlaybackService()

                    val mediaItem = buildMediaItem(track, Uri.parse(directStreamUrl))
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
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

    private fun handleCorruptLocalFileAndFallback(track: Track) {
        isPlayingLocalFile = false
        val pathToDelete = currentLocalFilePath ?: track.localFilePath
        currentLocalFilePath = null

        scope.launch(Dispatchers.IO) {
            // Удаляем поврежденный файл с диска
            if (!pathToDelete.isNullOrBlank()) {
                try {
                    val file = File(pathToDelete)
                    if (file.exists()) file.delete()
                } catch (ignored: Exception) {}
            }

            // Удаляем возможные другие поврежденные кандидаты этого трека (< 100 КБ)
            try {
                val musicDirectory = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "BlackFox")
                File(musicDirectory, "${track.id}.m4a").takeIf { it.exists() && it.length() < 100_000L }?.delete()
                File(musicDirectory, "${track.id}.mp3").takeIf { it.exists() && it.length() < 100_000L }?.delete()
                File(musicDirectory, "${track.id}.opus").takeIf { it.exists() && it.length() < 100_000L }?.delete()
                File(musicDirectory, "${track.id}.webm").takeIf { it.exists() && it.length() < 100_000L }?.delete()
            } catch (ignored: Exception) {}

            // Сбрасываем статус скачивания в базе Room
            try {
                musicDao?.updateDownloadStatus(
                    trackId = track.id,
                    isDownloaded = false,
                    filePath = null,
                    downloadedAt = null
                )
            } catch (ignored: Exception) {}
        }

        // Запускаем воспроизведение трека онлайн, чтобы он заиграл и не скипнулся
        playTrackOnline(track)
    }

    private fun preResolveNextTrack() {
        val queue = _currentQueue.value
        val nextIndex = currentQueueIndex + 1
        if (nextIndex in queue.indices) {
            val nextTrack = queue[nextIndex]
            scope.launch(Dispatchers.IO) {
                val musicDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "BlackFox")
                if (File(musicDir, "${nextTrack.id}.m4a").exists() ||
                    File(musicDir, "${nextTrack.id}.mp3").exists() ||
                    File(musicDir, "${nextTrack.id}.opus").exists() ||
                    File(musicDir, "${nextTrack.id}.webm").exists()
                ) {
                    return@launch
                }
                // Фоновое упреждающее разрешение ссылки следующего трека с учетом настроек качества
                val streamQuality = appSettings?.getStreamQuality() ?: "standard"
                AudioStreamResolver.resolveAudioStreamUrl(
                    videoId = nextTrack.id,
                    forDownload = false,
                    quality = streamQuality
                )
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
            PlaybackEngine.EXOPLAYER -> {
                player.seekTo(positionMs)
                if (!player.isPlaying && player.playbackState != Player.STATE_ENDED) {
                    player.play()
                }
            }
            PlaybackEngine.WEBVIEW -> webViewEngine.seekTo(positionMs)
            PlaybackEngine.NONE -> Unit
        }
    }

    private fun startExoProgressTicker() {
        stopExoProgressTicker()
        exoProgressJob = scope.launch {
            while (isActive && activeEngine == PlaybackEngine.EXOPLAYER) {
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
            val parts = durationStr.split(":").map { it.trim().toInt() }
            when (parts.size) {
                2 -> ((parts[0] * 60) + parts[1]) * 1000L
                3 -> ((parts[0] * 3600) + (parts[1] * 60) + parts[2]) * 1000L
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    fun release() {
        stopExoProgressTicker()
        streamResolveJob?.cancel()
        player.release()
        webViewEngine.release()
        mediaSession?.release()
        mediaSession = null
        forwardingPlayer = null
    }
}

@OptIn(UnstableApi::class)
private class BlackFoxForwardingPlayer(
    player: Player,
    private val playerManager: AudioPlayerManager
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_STOP)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_STOP -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun hasNextMediaItem(): Boolean = true
    override fun hasPreviousMediaItem(): Boolean = true
    override fun isCurrentMediaItemSeekable(): Boolean = true
    override fun isCurrentMediaItemDynamic(): Boolean = false

    override fun seekToNext() {
        playerManager.playNext(loop = true)
    }

    override fun seekToNextMediaItem() {
        playerManager.playNext(loop = true)
    }

    override fun seekToPrevious() {
        playerManager.playPrevious()
    }

    override fun seekToPreviousMediaItem() {
        playerManager.playPrevious()
    }

    override fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    override fun play() {
        when (playerManager.activeEngine) {
            AudioPlayerManager.PlaybackEngine.EXOPLAYER -> {
                if (playerManager.player.playbackState == Player.STATE_IDLE) {
                    playerManager.currentTrack.value?.let { playerManager.playTrack(it) }
                } else {
                    super.play()
                }
            }
            AudioPlayerManager.PlaybackEngine.WEBVIEW -> playerManager.webViewEngine.resumeTrack()
            AudioPlayerManager.PlaybackEngine.NONE -> {
                playerManager.currentTrack.value?.let { playerManager.playTrack(it) }
            }
        }
    }

    override fun pause() {
        when (playerManager.activeEngine) {
            AudioPlayerManager.PlaybackEngine.EXOPLAYER -> super.pause()
            AudioPlayerManager.PlaybackEngine.WEBVIEW -> playerManager.webViewEngine.pauseTrack()
            AudioPlayerManager.PlaybackEngine.NONE -> Unit
        }
    }

    override fun isPlaying(): Boolean {
        return if (playerManager.activeEngine == AudioPlayerManager.PlaybackEngine.WEBVIEW) {
            playerManager.webViewEngine.isPlaying.value
        } else {
            super.isPlaying()
        }
    }

    override fun getCurrentPosition(): Long {
        return if (playerManager.activeEngine == AudioPlayerManager.PlaybackEngine.WEBVIEW) {
            playerManager.currentPositionMs.value
        } else {
            super.getCurrentPosition()
        }
    }

    override fun getDuration(): Long {
        return if (playerManager.activeEngine == AudioPlayerManager.PlaybackEngine.WEBVIEW) {
            playerManager.totalDurationMs.value
        } else {
            super.getDuration()
        }
    }
}
