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
import com.blackfox.music.data.network.InnerTubeRepository
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.components.AddToPlaylistBottomSheet
import com.blackfox.music.presentation.components.FloatingMiniPlayer
import com.blackfox.music.presentation.screens.album.AlbumScreen
import com.blackfox.music.presentation.screens.album.AlbumViewModel
import com.blackfox.music.presentation.screens.home.HomeScreen
import com.blackfox.music.presentation.screens.mymusic.FavoritesDetailScreen
import com.blackfox.music.presentation.screens.mymusic.MyMusicScreen
import com.blackfox.music.presentation.screens.mymusic.PlaylistDetailScreen
import com.blackfox.music.presentation.screens.player.FullPlayerSheet
import com.blackfox.music.presentation.screens.search.SearchViewModel
import com.blackfox.music.presentation.screens.settings.SettingsScreen
import com.blackfox.music.presentation.theme.*
import com.blackfox.music.service.AudioPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        playerManager = app.playerManager

        setContent {
            BlackFoxTheme {
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
                var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
                var isFavoritesOpen by remember { mutableStateOf(false) }
                var isFullPlayerVisible by remember { mutableStateOf(false) }
                var trackForPlaylistSheet by remember { mutableStateOf<Track?>(null) }

                // Жест "Назад" и системная кнопка "Назад"
                BackHandler(
                    enabled = isFullPlayerVisible || selectedAlbumBrowseId != null ||
                            selectedPlaylistId != null || isFavoritesOpen || currentTab != MainTab.HOME
                ) {
                    when {
                        isFullPlayerVisible -> isFullPlayerVisible = false
                        selectedAlbumBrowseId != null -> selectedAlbumBrowseId = null
                        selectedPlaylistId != null -> selectedPlaylistId = null
                        isFavoritesOpen -> isFavoritesOpen = false
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
                val favoriteTrackIds by musicDao.getFavoriteTrackIds().collectAsState(initial = emptyList())
                val downloadedCount by musicDao.getDownloadedCount().collectAsState(initial = 0)
                val favoriteSet = remember(favoriteTrackIds) { favoriteTrackIds.toSet() }

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

                // Функция добавления/удаления из Любимого с автоматическим скачиванием
                fun toggleFavorite(track: Track) {
                    coroutineScope.launch {
                        val isFav = favoriteSet.contains(track.id)
                        val newFav = !isFav
                        val existing = musicDao.getTrackById(track.id)
                        if (existing == null) {
                            musicDao.insertTrack(
                                TrackEntity(
                                    id = track.id,
                                    title = track.title,
                                    artist = track.artist,
                                    album = track.album,
                                    duration = track.duration,
                                    thumbnailUrl = track.thumbnailUrl,
                                    isFavorite = newFav
                                )
                            )
                        } else {
                            musicDao.updateFavorite(track.id, newFav)
                        }
                        if (newFav) {
                            // Автоматически скачиваем трек при добавлении в любимое
                            downloadManager.downloadTrack(track)
                        }
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
                                    onMoveTrack = { fromIdx, toIdx ->
                                        coroutineScope.launch {
                                            playlistRepository.moveTrack(currentPlaylist.playlistId, fromIdx, toIdx)
                                        }
                                    }
                                )
                            } else {
                                selectedPlaylistId = null
                            }
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
                                onAddToPlaylistClick = { track -> trackForPlaylistSheet = track }
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
                                        onTrackPlay = { track, queue ->
                                            playTrackInQueue(track, queue)
                                        },
                                        onPlayFavorites = { playFavorites(shuffle = false) },
                                        onAlbumClick = { album ->
                                            selectedAlbumBrowseId = album.browseId
                                        },
                                        onNavigateToMyMusic = { currentTab = MainTab.MY_MUSIC },
                                        onNavigateToFavorites = { isFavoritesOpen = true },
                                        downloadProgressMap = downloadProgressMap
                                    )
                                }

                                MainTab.MY_MUSIC -> {
                                    MyMusicScreen(
                                        userPlaylists = userPlaylists,
                                        favoriteTracks = favoriteTracks,
                                        onOpenFavorites = { isFavoritesOpen = true },
                                        onPlayFavorites = { playFavorites(shuffle = false) },
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
                                        }
                                    )
                                }

                                MainTab.SETTINGS -> {
                                    SettingsScreen(
                                        downloadedTracksCount = downloadedCount,
                                        storageSizeBytes = storageSizeBytes,
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

                        // Стеклянная нижняя навигация
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .floatingGlass(cornerRadius = 24.dp)
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MainTab.entries.forEach { tab ->
                                val isSelected = currentTab == tab &&
                                        selectedAlbumBrowseId == null &&
                                        selectedPlaylistId == null &&
                                        !isFavoritesOpen

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedAlbumBrowseId = null
                                            selectedPlaylistId = null
                                            isFavoritesOpen = false
                                            currentTab = tab
                                        }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) FoxOrange else TextMuted,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = tab.title,
                                        color = if (isSelected) FoxOrange else TextMuted,
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
                                onCloseClick = { isFullPlayerVisible = false }
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
