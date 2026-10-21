package com.blackfox.music.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackfox.music.BlackFoxApp
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.data.local.toDomain
import com.blackfox.music.data.network.InnerTubeRepository
import com.blackfox.music.domain.model.Artist
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.components.AddToPlaylistBottomSheet
import com.blackfox.music.presentation.components.FloatingMiniPlayer
import com.blackfox.music.presentation.screens.album.AlbumScreen
import com.blackfox.music.presentation.screens.album.AlbumViewModel
import com.blackfox.music.presentation.screens.artist.ArtistScreen
import com.blackfox.music.presentation.screens.artist.ArtistViewModel
import com.blackfox.music.presentation.screens.home.HomeScreen
import com.blackfox.music.presentation.screens.mymusic.DownloadedTracksScreen
import com.blackfox.music.presentation.screens.mymusic.FavoritesDetailScreen
import com.blackfox.music.presentation.screens.mymusic.FollowedArtistsScreen
import com.blackfox.music.presentation.screens.mymusic.MyMusicScreen
import com.blackfox.music.presentation.screens.mymusic.PlaylistDetailScreen
import com.blackfox.music.presentation.screens.mymusic.UploadedTracksScreen
import com.blackfox.music.presentation.screens.player.FullPlayerSheet
import com.blackfox.music.presentation.screens.search.SearchViewModel
import com.blackfox.music.presentation.screens.settings.SettingsScreen
import com.blackfox.music.presentation.theme.*
import com.blackfox.music.service.AudioPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab(val title: String, val icon: ImageVector) {
    HOME("Главная", Icons.Default.Home),
    MY_MUSIC("Моя музыка", Icons.Default.LibraryMusic),
    SETTINGS("Настройки", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private lateinit var playerManager: AudioPlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as BlackFoxApp
        val musicDao = app.database.musicDao()
        val repository = InnerTubeRepository()
        val downloadManager = app.downloadManager
        val playlistRepository = app.playlistRepository
        val appSettings = app.appSettings
        playerManager = app.playerManager

        setContent {
            val currentAccentTheme by appSettings.currentAccent.collectAsState()
            BlackFoxTheme(accentTheme = currentAccentTheme) {
                val coroutineScope = rememberCoroutineScope()

                // Запрос разрешения на показ уведомлений (Android 13+) для фонового плеера и шторки
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* разрешение обработано */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val check = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                        if (check != PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                // Навигационные состояния
                var currentTab by remember { mutableStateOf(MainTab.HOME) }
                var selectedAlbumBrowseId by remember { mutableStateOf<String?>(null) }
                var selectedArtistId by remember { mutableStateOf<String?>(null) }
                var selectedArtistName by remember { mutableStateOf<String?>(null) }
                var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
                var isFavoritesOpen by remember { mutableStateOf(false) }
                var isDownloadedOpen by remember { mutableStateOf(false) }
                var isUploadedOpen by remember { mutableStateOf(false) }
                var isFollowedArtistsOpen by remember { mutableStateOf(false) }
                var isFullPlayerVisible by remember { mutableStateOf(false) }
                var trackForPlaylistSheet by remember { mutableStateOf<Track?>(null) }

                // Жест "Назад" и системная кнопка "Назад"
                BackHandler(
                    enabled = isFullPlayerVisible || selectedAlbumBrowseId != null ||
                            selectedArtistId != null || selectedArtistName != null ||
                            selectedPlaylistId != null || isFavoritesOpen || isDownloadedOpen || isUploadedOpen ||
                            isFollowedArtistsOpen || currentTab != MainTab.HOME
                ) {
                    when {
                        isFullPlayerVisible -> isFullPlayerVisible = false
                        selectedAlbumBrowseId != null -> selectedAlbumBrowseId = null
                        selectedArtistId != null || selectedArtistName != null -> {
                            selectedArtistId = null
                            selectedArtistName = null
                        }
                        selectedPlaylistId != null -> selectedPlaylistId = null
                        isFavoritesOpen -> isFavoritesOpen = false
                        isDownloadedOpen -> isDownloadedOpen = false
                        isUploadedOpen -> isUploadedOpen = false
                        isFollowedArtistsOpen -> isFollowedArtistsOpen = false
                        currentTab != MainTab.HOME -> currentTab = MainTab.HOME
                    }
                }

                // Состояние аудиоплеера
                val currentTrack by playerManager.currentTrack.collectAsState()
                val isPlaying by playerManager.isPlaying.collectAsState()
                val currentPositionMs by playerManager.currentPositionMs.collectAsState()
                val totalDurationMs by playerManager.totalDurationMs.collectAsState()

                // Прогресс скачиваний треков
                val downloadProgressMap by downloadManager.downloadProgressMap.collectAsState()

                // Данные из базы Room
                val userPlaylists by playlistRepository.getAllUserPlaylists().collectAsState(initial = emptyList())
                val favoriteTracks by musicDao.getFavoriteTracks().collectAsState(initial = emptyList())
                val downloadedTracks by musicDao.getDownloadedTracks().collectAsState(initial = emptyList())
                val favoriteTrackIds by musicDao.getFavoriteTrackIds().collectAsState(initial = emptyList())
                val userUploadedTracks by musicDao.getUserUploadedTracks().collectAsState(initial = emptyList())
                val downloadedCount by musicDao.getDownloadedCount().collectAsState(initial = 0)
                val followedArtists by musicDao.getAllFollowedArtists().collectAsState(initial = emptyList())
                val favoriteSet = remember(favoriteTrackIds) { favoriteTrackIds.toSet() }

                // Полная коллекция сохраненных треков (из плейлистов, любимого и загруженных)
                val allSavedTracks = remember(userPlaylists, favoriteTracks, userUploadedTracks) {
                    val set = mutableSetOf<String>()
                    val result = mutableListOf<TrackEntity>()
                    for (t in favoriteTracks) {
                        if (set.add(t.id)) result.add(t)
                    }
                    for (t in userUploadedTracks) {
                        if (set.add(t.id)) result.add(t)
                    }
                    for (pl in userPlaylists) {
                        for (t in pl.tracks) {
                            if (set.add(t.id)) result.add(t)
                        }
                    }
                    result
                }

                // Случайный микс из сохраненных треков, фиксированный на время сессии приложения
                var sessionSavedRandomChain by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }
                var hasInitializedSavedRandom by remember { mutableStateOf(false) }
                LaunchedEffect(allSavedTracks) {
                    if (allSavedTracks.isNotEmpty() && !hasInitializedSavedRandom) {
                        sessionSavedRandomChain = allSavedTracks.shuffled()
                        hasInitializedSavedRandom = true
                    }
                }

                // Новинки артистов, на которых подписан пользователь (реальные свежие релизы из API)
                var newReleasesFromFollowedArtists by remember { mutableStateOf<List<Track>>(emptyList()) }
                LaunchedEffect(followedArtists) {
                    if (followedArtists.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            val releases = mutableListOf<Track>()
                            val sampleArtists = followedArtists.take(6)
                            for (artist in sampleArtists) {
                                try {
                                    val newTracks = repository.getArtistNewReleasesLastMonth(artist.channelId, artist.name)
                                    releases.addAll(newTracks.take(3))
                                } catch (_: Exception) {}
                            }
                            newReleasesFromFollowedArtists = releases.distinctBy { it.id }
                        }
                    } else {
                        newReleasesFromFollowedArtists = emptyList()
                    }
                }

                var storageSizeBytes by remember { mutableStateOf(0L) }
                LaunchedEffect(downloadProgressMap, downloadedCount) {
                    coroutineScope.launch(Dispatchers.IO) {
                        storageSizeBytes = downloadManager.getDownloadedStorageSize()
                    }
                }

                val searchViewModel = viewModel {
                    SearchViewModel(
                        repository = repository,
                        musicDao = musicDao,
                        downloadManager = downloadManager,
                        playlistRepository = playlistRepository
                    )
                }

                // Функция добавления/удаления из Любимого с автоматическим скачиванием и удалением файлов
                fun toggleFavorite(track: Track) {
                    coroutineScope.launch {
                        playlistRepository.toggleFavorite(track)
                    }
                }

                // Сохранение порядка избранного после перетаскивания в транзакции
                fun reorderFavorites(orderedIds: List<String>) {
                    coroutineScope.launch {
                        playlistRepository.updateFavoritesOrder(orderedIds)
                    }
                }

                // Запуск бесконечного радиопотока Fox Flow (Моя волна)
                fun startFoxFlow() {
                    coroutineScope.launch {
                        val favIds = favoriteSet
                        val userLibraryDomain = (favoriteTracks.map { entity ->
                            Track(
                                id = entity.id,
                                title = entity.title,
                                artist = entity.artist,
                                album = entity.album,
                                duration = entity.duration,
                                thumbnailUrl = entity.thumbnailUrl,
                                isDownloaded = entity.isDownloaded,
                                localFilePath = entity.localFilePath
                            )
                        } + allSavedTracks.map { entity ->
                            Track(
                                id = entity.id,
                                title = entity.title,
                                artist = entity.artist,
                                album = entity.album,
                                duration = entity.duration,
                                thumbnailUrl = entity.thumbnailUrl,
                                isDownloaded = entity.isDownloaded,
                                localFilePath = entity.localFilePath
                            )
                        }).distinctBy { it.id }

                        val flowQueue = withContext(Dispatchers.IO) {
                            val queueResult = mutableListOf<Track>()

                            if (userLibraryDomain.isNotEmpty()) {
                                // До 4 разнообразных семян от разных исполнителей
                                val seeds = userLibraryDomain.shuffled()
                                    .distinctBy { it.artist.lowercase().trim() }
                                    .take(4)

                                val deferreds = seeds.map { seed ->
                                    async {
                                        try {
                                            repository.getRadioQueue(seed.id)
                                        } catch (e: Exception) {
                                            emptyList()
                                        }
                                    }
                                }
                                val queues = deferreds.awaitAll()
                                val seedIds = seeds.map { it.id }.toSet()

                                var maxLen = 0
                                queues.forEach { if (it.size > maxLen) maxLen = it.size }

                                for (idx in 0 until maxLen) {
                                    for (q in queues) {
                                        if (idx < q.size) {
                                            val candidate = q[idx]
                                            if (!seedIds.contains(candidate.id) && !queueResult.any { it.id == candidate.id }) {
                                                queueResult.add(candidate)
                                            }
                                        }
                                    }
                                }
                            }

                            if (queueResult.isEmpty()) {
                                val fallbackList = repository.searchTracks("popular music mix", limit = 30)
                                queueResult.addAll(fallbackList)
                            }

                            // Не запускать песню из любимых первой!
                            val firstNonFavIndex = queueResult.indexOfFirst { !favIds.contains(it.id) }
                            if (firstNonFavIndex > 0) {
                                val nonFav = queueResult.removeAt(firstNonFavIndex)
                                queueResult.add(0, nonFav)
                            }
                            queueResult
                        }

                        if (flowQueue.isNotEmpty()) {
                            playerManager.setQueueAndPlay(flowQueue, 0)
                        }
                    }
                }

                // Воспроизведение случайного микса из сохраненных треков пользователя
                fun playSavedRandom() {
                    val chain = if (sessionSavedRandomChain.isNotEmpty()) sessionSavedRandomChain else allSavedTracks.shuffled()
                    if (chain.isNotEmpty()) {
                        val domainList = chain.map { it.toDomain() }
                        playerManager.setQueueAndPlay(domainList, 0)
                    }
                }

                // Воспроизведение загруженных пользователем файлов
                fun playUploaded(shuffle: Boolean = false) {
                    val domainList = userUploadedTracks.map { entity ->
                        Track(
                            id = entity.id,
                            title = entity.title,
                            artist = entity.artist,
                            album = entity.album,
                            duration = entity.duration,
                            thumbnailUrl = entity.thumbnailUrl,
                            isDownloaded = true,
                            localFilePath = entity.localFilePath,
                            isUserUploaded = true
                        )
                    }
                    if (domainList.isNotEmpty()) {
                        val queue = if (shuffle) domainList.shuffled() else domainList
                        playerManager.setQueueAndPlay(queue, 0)
                    }
                }

                // Вспомогательная функция запуска воспроизведения любимых треков
                fun playFavorites(shuffle: Boolean = false) {
                    val domainTracks = favoriteTracks.map { entity ->
                        Track(
                            id = entity.id,
                            title = entity.title,
                            artist = entity.artist,
                            album = entity.album,
                            duration = entity.duration,
                            thumbnailUrl = entity.thumbnailUrl,
                            isDownloaded = entity.isDownloaded,
                            localFilePath = entity.localFilePath
                        )
                    }
                    if (domainTracks.isNotEmpty()) {
                        val queue = if (shuffle) domainTracks.shuffled() else domainTracks
                        playerManager.setQueueAndPlay(queue, 0)
                    }
                }

                // Гарантированный запуск трека в очереди с точным поиском по ID
                fun playTrackInQueue(track: Track, queue: List<Track>) {
                    val idx = queue.indexOfFirst { it.id == track.id }
                    if (idx != -1) {
                        playerManager.setQueueAndPlay(queue, idx)
                    } else {
                        val newQueue = listOf(track) + queue
                        playerManager.setQueueAndPlay(newQueue, 0)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBlack)
                ) {
                    // Фоновый веб-компонент для гарантированного fallback онлайн-воспроизведения
                    AndroidView(
                        factory = { ctx -> playerManager.webViewEngine.getOrCreateWebView(ctx) },
                        modifier = Modifier
                            .size(1.dp)
                            .alpha(0.001f)
                    )

                    // ОСНОВНОЙ КОНТЕНТ
                    when {
                        // Экран Альбома
                        selectedAlbumBrowseId != null -> {
                            val albumViewModel = remember(selectedAlbumBrowseId) {
                                AlbumViewModel(
                                    browseId = selectedAlbumBrowseId!!,
                                    repository = repository,
                                    downloadManager = downloadManager,
                                    playlistRepository = playlistRepository,
                                    musicDao = musicDao
                                )
                            }

                            AlbumScreen(
                                viewModel = albumViewModel,
                                onBackClick = { selectedAlbumBrowseId = null },
                                onPlayTrack = { track ->
                                    val albumTracks = albumViewModel.uiState.value.album?.tracks ?: listOf(track)
                                    playTrackInQueue(track, albumTracks)
                                },
                                downloadProgressMap = downloadProgressMap
                            )
                        }

                        // Экран Артиста
                        selectedArtistId != null || selectedArtistName != null -> {
                            val artistViewModel = remember(selectedArtistId, selectedArtistName) {
                                ArtistViewModel(
                                    channelId = selectedArtistId ?: "",
                                    initialName = selectedArtistName ?: "",
                                    repository = repository,
                                    downloadManager = downloadManager,
                                    playlistRepository = playlistRepository,
                                    musicDao = musicDao
                                )
                            }

                            ArtistScreen(
                                viewModel = artistViewModel,
                                onBackClick = {
                                    selectedArtistId = null
                                    selectedArtistName = null
                                },
                                onPlayTrack = { track, queue ->
                                    playTrackInQueue(track, queue)
                                },
                                onAlbumClick = { album ->
                                    selectedAlbumBrowseId = album.browseId
                                },
                                onArtistClick = { artist ->
                                    selectedArtistId = artist.channelId
                                    selectedArtistName = artist.name
                                },
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                downloadProgressMap = downloadProgressMap
                            )
                        }

                        // Экран детального просмотра Плейлиста
                        selectedPlaylistId != null -> {
                            val currentPlWithTracks = userPlaylists.find { it.playlist.playlistId == selectedPlaylistId }
                            if (currentPlWithTracks != null) {
                                val currentPlaylist = currentPlWithTracks.playlist
                                val orderedTracks by playlistRepository.getTracksForPlaylist(currentPlaylist.playlistId).collectAsState(initial = emptyList())
                                val activeTracks = if (orderedTracks.isNotEmpty()) orderedTracks else currentPlWithTracks.tracks

                                PlaylistDetailScreen(
                                    playlist = currentPlaylist,
                                    tracks = activeTracks,
                                    favoriteTrackIds = favoriteSet,
                                    downloadProgressMap = downloadProgressMap,
                                    onBackClick = { selectedPlaylistId = null },
                                    onTrackClick = { track ->
                                        val domainList = activeTracks.map { entity ->
                                            Track(
                                                id = entity.id,
                                                title = entity.title,
                                                artist = entity.artist,
                                                album = entity.album,
                                                duration = entity.duration,
                                                thumbnailUrl = entity.thumbnailUrl,
                                                isDownloaded = entity.isDownloaded,
                                                localFilePath = entity.localFilePath
                                            )
                                        }
                                        playTrackInQueue(track, domainList)
                                    },
                                    onPlayAll = {
                                        val domainList = activeTracks.map { entity ->
                                            Track(
                                                id = entity.id,
                                                title = entity.title,
                                                artist = entity.artist,
                                                album = entity.album,
                                                duration = entity.duration,
                                                thumbnailUrl = entity.thumbnailUrl,
                                                isDownloaded = entity.isDownloaded,
                                                localFilePath = entity.localFilePath
                                            )
                                        }
                                        if (domainList.isNotEmpty()) {
                                            playerManager.setQueueAndPlay(domainList, 0)
                                        }
                                    },
                                    onShuffle = {
                                        val domainList = activeTracks.map { entity ->
                                            Track(
                                                id = entity.id,
                                                title = entity.title,
                                                artist = entity.artist,
                                                album = entity.album,
                                                duration = entity.duration,
                                                thumbnailUrl = entity.thumbnailUrl,
                                                isDownloaded = entity.isDownloaded,
                                                localFilePath = entity.localFilePath
                                            )
                                        }.shuffled()
                                        if (domainList.isNotEmpty()) {
                                            playerManager.setQueueAndPlay(domainList, 0)
                                        }
                                    },
                                    onToggleFavorite = { track -> toggleFavorite(track) },
                                    onRemoveTrack = { trackId ->
                                        coroutineScope.launch {
                                            playlistRepository.removeTrackFromPlaylist(currentPlaylist.playlistId, trackId)
                                        }
                                    },
                                    onUpdateCover = { uri: Uri? ->
                                        coroutineScope.launch {
                                            playlistRepository.updatePlaylistCover(currentPlaylist.playlistId, uri)
                                        }
                                    },
                                    onRenamePlaylist = { newName ->
                                        coroutineScope.launch {
                                            playlistRepository.renamePlaylist(currentPlaylist.playlistId, newName)
                                        }
                                    },
                                    onDeletePlaylist = {
                                        coroutineScope.launch {
                                            playlistRepository.deletePlaylist(currentPlaylist.playlistId)
                                            selectedPlaylistId = null
                                        }
                                    },
                                    onReorderTracks = { orderedIds ->
                                        coroutineScope.launch {
                                            playlistRepository.updatePlaylistOrder(currentPlaylist.playlistId, orderedIds)
                                        }
                                    },
                                    currentTrack = currentTrack,
                                    isPlaying = isPlaying
                                )
                            } else {
                                selectedPlaylistId = null
                            }
                        }

                        // Экран Скачанных треков (Оффлайн)
                        isDownloadedOpen -> {
                            DownloadedTracksScreen(
                                downloadedTracks = downloadedTracks,
                                storageSizeBytes = storageSizeBytes,
                                downloadProgressMap = downloadProgressMap,
                                favoriteTrackIds = favoriteSet,
                                onBackClick = { isDownloadedOpen = false },
                                onTrackClick = { track ->
                                    val domainList = downloadedTracks.map { it.toDomain() }
                                    playTrackInQueue(track, domainList)
                                },
                                onPlayAll = {
                                    if (downloadedTracks.isNotEmpty()) {
                                        val domainList = downloadedTracks.map { it.toDomain() }
                                        playTrackInQueue(domainList.first(), domainList)
                                    }
                                },
                                onShuffle = {
                                    if (downloadedTracks.isNotEmpty()) {
                                        val domainList = downloadedTracks.map { it.toDomain() }.shuffled()
                                        playTrackInQueue(domainList.first(), domainList)
                                    }
                                },
                                onToggleFavorite = { track -> toggleFavorite(track) },
                                onAddToPlaylistClick = { track -> trackForPlaylistSheet = track },
                                onDeleteTrack = { trackId ->
                                    coroutineScope.launch {
                                        downloadManager.deleteDownloadedTrack(trackId)
                                        val inAnyPlaylist = musicDao.countPlaylistsContainingTrack(trackId, excludePlaylistId = -1) > 0
                                        val trackEntity = musicDao.getTrackById(trackId)
                                        val isFav = trackEntity?.isFavorite == true
                                        val isUploaded = trackEntity?.isUserUploaded == true
                                        if (!inAnyPlaylist && !isFav && !isUploaded) {
                                            musicDao.deleteTrack(trackId)
                                        }
                                    }
                                },
                                currentTrack = currentTrack,
                                isPlaying = isPlaying
                            )
                        }

                        // Экран детального просмотра Любимого
                        isFavoritesOpen -> {
                            FavoritesDetailScreen(
                                favoriteTracks = favoriteTracks,
                                downloadProgressMap = downloadProgressMap,
                                onBackClick = { isFavoritesOpen = false },
                                onTrackClick = { track ->
                                    val domainList = favoriteTracks.map { entity ->
                                        Track(
                                            id = entity.id,
                                            title = entity.title,
                                            artist = entity.artist,
                                            album = entity.album,
                                            duration = entity.duration,
                                            thumbnailUrl = entity.thumbnailUrl,
                                            isDownloaded = entity.isDownloaded,
                                            localFilePath = entity.localFilePath
                                        )
                                    }
                                    playTrackInQueue(track, domainList)
                                },
                                onPlayAll = { playFavorites(shuffle = false) },
                                onShuffle = { playFavorites(shuffle = true) },
                                onToggleFavorite = { track -> toggleFavorite(track) },
                                onAddToPlaylistClick = { track -> trackForPlaylistSheet = track },
                                onReorderFavorites = { orderedIds -> reorderFavorites(orderedIds) },
                                currentTrack = currentTrack,
                                isPlaying = isPlaying
                            )
                        }

                        // Экран Загруженных треков (Собственные файлы пользователя)
                        isUploadedOpen -> {
                            UploadedTracksScreen(
                                uploadedTracks = userUploadedTracks,
                                favoriteTrackIds = favoriteSet,
                                musicDao = musicDao,
                                playlistRepository = playlistRepository,
                                onBackClick = { isUploadedOpen = false },
                                onTrackClick = { track, queue ->
                                    playTrackInQueue(track, queue)
                                },
                                onPlayAll = { playUploaded(shuffle = false) },
                                onShuffle = { playUploaded(shuffle = true) },
                                onToggleFavorite = { track -> toggleFavorite(track) },
                                currentTrack = currentTrack,
                                isPlaying = isPlaying
                            )
                        }

                        // Экран артистов, на которых подписан пользователь
                        isFollowedArtistsOpen -> {
                            FollowedArtistsScreen(
                                followedArtists = followedArtists,
                                onBackClick = { isFollowedArtistsOpen = false },
                                onArtistClick = { artist ->
                                    isFollowedArtistsOpen = false
                                    selectedArtistId = artist.channelId
                                    selectedArtistName = artist.name
                                },
                                onUnfollowArtist = { channelId ->
                                    coroutineScope.launch {
                                        musicDao.unfollowArtist(channelId)
                                    }
                                }
                            )
                        }

                        // Вкладки нижнего меню
                        else -> {
                            when (currentTab) {
                                MainTab.HOME -> {
                                    HomeScreen(
                                        viewModel = searchViewModel,
                                        favoriteTracks = favoriteTracks,
                                        userPlaylistsCount = userPlaylists.size,
                                        allSavedTracks = allSavedTracks,
                                        randomSavedPreviewTracks = sessionSavedRandomChain,
                                        newReleasesFromFollowedArtists = newReleasesFromFollowedArtists,
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        onTrackPlay = { track, queue ->
                                            playTrackInQueue(track, queue)
                                        },
                                        onPlayFavorites = { playFavorites(shuffle = false) },
                                        onAlbumClick = { album ->
                                            selectedAlbumBrowseId = album.browseId
                                        },
                                        onArtistClick = { artist ->
                                            selectedArtistId = artist.channelId
                                            selectedArtistName = artist.name
                                        },
                                        onArtistNameClick = { name, id ->
                                            selectedArtistId = id
                                            selectedArtistName = name
                                        },
                                        onStartFoxFlow = { startFoxFlow() },
                                        onPlaySavedRandom = { playSavedRandom() },
                                        onNavigateToMyMusic = { currentTab = MainTab.MY_MUSIC },
                                        onNavigateToFavorites = { isFavoritesOpen = true },
                                        downloadProgressMap = downloadProgressMap
                                    )
                                }

                                MainTab.MY_MUSIC -> {
                                    MyMusicScreen(
                                        userPlaylists = userPlaylists,
                                        favoriteTracks = favoriteTracks,
                                        allLibraryTracks = allSavedTracks,
                                        uploadedTracksCount = userUploadedTracks.size,
                                        downloadedTracksCount = downloadedCount,
                                        followedArtistsCount = followedArtists.size,
                                        downloadProgressMap = downloadProgressMap,
                                        onOpenFavorites = { isFavoritesOpen = true },
                                        onPlayFavorites = { playFavorites(shuffle = false) },
                                        onOpenDownloaded = { isDownloadedOpen = true },
                                        onPlayDownloaded = {
                                            if (downloadedTracks.isNotEmpty()) {
                                                val domainList = downloadedTracks.map { it.toDomain() }
                                                playTrackInQueue(domainList.first(), domainList)
                                            }
                                        },
                                        onOpenUploaded = { isUploadedOpen = true },
                                        onPlayUploaded = { playUploaded(shuffle = false) },
                                        onOpenFollowedArtists = { isFollowedArtistsOpen = true },
                                        onOpenPlaylist = { plId -> selectedPlaylistId = plId },
                                        onCreatePlaylist = { name ->
                                            coroutineScope.launch {
                                                playlistRepository.createPlaylist(name)
                                            }
                                        },
                                        onRenamePlaylist = { plId, newName ->
                                            coroutineScope.launch {
                                                playlistRepository.renamePlaylist(plId, newName)
                                            }
                                        },
                                        onDeletePlaylist = { plId ->
                                            coroutineScope.launch {
                                                playlistRepository.deletePlaylist(plId)
                                            }
                                        },
                                        onUpdatePlaylistCover = { plId, uri ->
                                            coroutineScope.launch {
                                                playlistRepository.updatePlaylistCover(plId, uri)
                                            }
                                        },
                                        onPlayTrack = { track, queue ->
                                            playTrackInQueue(track, queue)
                                        },
                                        onToggleFavorite = { track -> toggleFavorite(track) },
                                        onAddToPlaylist = { track -> trackForPlaylistSheet = track },
                                        onDownloadTrack = { downloadManager.downloadTrack(it) },
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying
                                    )
                                }

                                MainTab.SETTINGS -> {
                                    SettingsScreen(
                                        downloadedTracksCount = downloadedCount,
                                        storageSizeBytes = storageSizeBytes,
                                        appSettings = appSettings,
                                        favoriteTracks = favoriteTracks,
                                        userPlaylists = userPlaylists,
                                        musicDao = musicDao,
                                        playlistRepository = playlistRepository,
                                        onClearAllDownloads = {
                                            coroutineScope.launch {
                                                downloadManager.clearAllDownloaded()
                                                storageSizeBytes = 0L
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Плавающий мини-плеер и нижняя навигация
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    ) {
                        // Мини-плеер (если выбран трек)
                        currentTrack?.let { track ->
                            val progress = if (totalDurationMs > 0) currentPositionMs.toFloat() / totalDurationMs else 0f
                            val isFav = favoriteSet.contains(track.id)

                            FloatingMiniPlayer(
                                currentTrack = track,
                                isPlaying = isPlaying,
                                progressFraction = progress,
                                onPlayPauseClick = { playerManager.togglePlayPause() },
                                onPlayerClick = { isFullPlayerVisible = true },
                                isFavorite = isFav,
                                onFavoriteToggle = { toggleFavorite(track) }
                            )
                        }

                        // Стеклянная нижняя навигация (островная)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .height(62.dp)
                                .floatingGlass(cornerRadius = 28.dp)
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MainTab.entries.forEach { tab ->
                                val isSelected = currentTab == tab &&
                                        selectedAlbumBrowseId == null &&
                                        selectedArtistId == null &&
                                        selectedArtistName == null &&
                                        selectedPlaylistId == null &&
                                        !isFavoritesOpen &&
                                        !isDownloadedOpen &&
                                        !isUploadedOpen &&
                                        !isFollowedArtistsOpen

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (tab == MainTab.HOME && isSelected) {
                                                searchViewModel.onQueryChange("")
                                            }
                                            selectedAlbumBrowseId = null
                                            selectedArtistId = null
                                            selectedArtistName = null
                                            selectedPlaylistId = null
                                            isFavoritesOpen = false
                                            isDownloadedOpen = false
                                            isUploadedOpen = false
                                            isFollowedArtistsOpen = false
                                            currentTab = tab
                                        }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) currentAccentTheme.color else TextMuted,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = tab.title,
                                        color = if (isSelected) currentAccentTheme.color else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Полноэкранный плеер
                    AnimatedVisibility(
                        visible = isFullPlayerVisible && currentTrack != null,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        currentTrack?.let { track ->
                            val isFav = favoriteSet.contains(track.id)

                            FullPlayerSheet(
                                track = track,
                                isPlaying = isPlaying,
                                currentPositionMs = currentPositionMs,
                                totalDurationMs = totalDurationMs,
                                isFavorite = isFav,
                                onPlayPauseClick = { playerManager.togglePlayPause() },
                                onPreviousClick = { playerManager.playPrevious() },
                                onNextClick = { playerManager.playNext(loop = true) },
                                onSeekTo = { newPos -> playerManager.seekTo(newPos) },
                                onFavoriteToggle = { toggleFavorite(track) },
                                onDownloadClick = { downloadManager.downloadTrack(track) },
                                onAddToPlaylistClick = { trackForPlaylistSheet = track },
                                onSwitchToTrack = { uncensoredTrack ->
                                    val queue = playerManager.currentQueue.value
                                    val idx = queue.indexOfFirst { it.id == track.id }
                                    if (idx != -1) {
                                        val updatedQueue = queue.toMutableList()
                                        updatedQueue[idx] = uncensoredTrack
                                        playerManager.setQueueAndPlay(updatedQueue, idx)
                                    } else {
                                        playerManager.setQueueAndPlay(listOf(uncensoredTrack) + queue, 0)
                                    }
                                },
                                onCloseClick = { isFullPlayerVisible = false },
                                onArtistClick = { name, id ->
                                    isFullPlayerVisible = false
                                    selectedAlbumBrowseId = null
                                    selectedArtistId = id
                                    selectedArtistName = name
                                },
                                onAlbumClick = { albumTitle, albumId ->
                                    isFullPlayerVisible = false
                                    if (!albumId.isNullOrBlank()) {
                                        selectedAlbumBrowseId = albumId
                                    } else {
                                        coroutineScope.launch {
                                            try {
                                                val query = if (albumTitle.isNotBlank() && !albumTitle.equals("Single", ignoreCase = true) && !albumTitle.equals("Сингл", ignoreCase = true)) {
                                                    "${track.artist} $albumTitle"
                                                } else {
                                                    "${track.artist} ${track.title}"
                                                }
                                                val albums = repository.searchAlbums(query, limit = 1)
                                                if (albums.isNotEmpty()) {
                                                    selectedAlbumBrowseId = albums.first().browseId
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                },
                                repository = repository
                            )
                        }
                    }

                    // Общая шторка добавления трека в плейлист (вызывается из FullPlayerSheet, Favorites, etc.)
                    trackForPlaylistSheet?.let { track ->
                        val containingPlaylistIds by musicDao.getPlaylistIdsForTrack(track.id).collectAsState(initial = emptyList())
                        val allRawPlaylists by musicDao.getAllPlaylists().collectAsState(initial = emptyList())
                        AddToPlaylistBottomSheet(
                            track = track,
                            playlists = allRawPlaylists,
                            containingPlaylistIds = remember(containingPlaylistIds) { containingPlaylistIds.toSet() },
                            onDismiss = { trackForPlaylistSheet = null },
                            onToggleTrackInPlaylist = { plId ->
                                coroutineScope.launch {
                                    playlistRepository.toggleTrackInPlaylist(plId, track)
                                }
                            },
                            onCreatePlaylistAndAdd = { name ->
                                coroutineScope.launch {
                                    playlistRepository.createPlaylistAndAddTrack(name, track)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
