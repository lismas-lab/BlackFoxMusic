package com.blackfox.music.presentation

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Search
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
import com.blackfox.music.presentation.components.FloatingMiniPlayer
import com.blackfox.music.presentation.screens.album.AlbumScreen
import com.blackfox.music.presentation.screens.album.AlbumViewModel
import com.blackfox.music.presentation.screens.downloads.DownloadsScreen
import com.blackfox.music.presentation.screens.player.FullPlayerSheet
import com.blackfox.music.presentation.screens.search.SearchScreen
import com.blackfox.music.presentation.screens.search.SearchViewModel
import com.blackfox.music.presentation.theme.*
import com.blackfox.music.service.AudioPlayerManager
import kotlinx.coroutines.launch

enum class MainTab(val title: String, val icon: ImageVector) {
    SEARCH("Поиск", Icons.Default.Search),
    DOWNLOADS("Оффлайн", Icons.Default.OfflinePin),
    FAVORITES("Избранное", Icons.Default.Favorite)
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
        playerManager = app.playerManager

        setContent {
            BlackFoxTheme {
                val coroutineScope = rememberCoroutineScope()

                // Запрос разрешения на показ уведомлений (Android 13+) для шторки и экрана блокировки
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

                // Навигация
                var currentTab by remember { mutableStateOf(MainTab.SEARCH) }
                var selectedAlbumBrowseId by remember { mutableStateOf<String?>(null) }
                var isFullPlayerVisible by remember { mutableStateOf(false) }

                // Жест "Назад" и системная кнопка "Назад"
                BackHandler(enabled = isFullPlayerVisible || selectedAlbumBrowseId != null || currentTab != MainTab.SEARCH) {
                    when {
                        isFullPlayerVisible -> isFullPlayerVisible = false
                        selectedAlbumBrowseId != null -> selectedAlbumBrowseId = null
                        currentTab != MainTab.SEARCH -> currentTab = MainTab.SEARCH
                    }
                }

                // Состояние реального аудиоплеера
                val currentTrack by playerManager.currentTrack.collectAsState()
                val isPlaying by playerManager.isPlaying.collectAsState()
                val currentPositionMs by playerManager.currentPositionMs.collectAsState()
                val totalDurationMs by playerManager.totalDurationMs.collectAsState()
                val isLoadingAudio by playerManager.isLoadingAudio.collectAsState()

                // Прогресс скачиваний треков
                val downloadProgressMap by downloadManager.downloadProgressMap.collectAsState()

                // Данные из базы Room
                val downloadedTracks by musicDao.getDownloadedTracks().collectAsState(initial = emptyList())
                val favoriteTracks by musicDao.getFavoriteTracks().collectAsState(initial = emptyList())
                val favoriteTrackIds by musicDao.getFavoriteTrackIds().collectAsState(initial = emptyList())
                val favoriteSet = remember(favoriteTrackIds) { favoriteTrackIds.toSet() }

                val searchViewModel = viewModel {
                    SearchViewModel(repository = repository, musicDao = musicDao)
                }

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

                    // Основной контент
                    if (selectedAlbumBrowseId != null) {
                        // Экран Альбома
                        val albumViewModel = remember(selectedAlbumBrowseId) {
                            AlbumViewModel(
                                browseId = selectedAlbumBrowseId!!,
                                repository = repository,
                                downloadManager = downloadManager,
                                musicDao = musicDao
                            )
                        }

                        AlbumScreen(
                            viewModel = albumViewModel,
                            onBackClick = { selectedAlbumBrowseId = null },
                            onPlayTrack = { track ->
                                val albumTracks = albumViewModel.uiState.value.album?.tracks ?: listOf(track)
                                val idx = albumTracks.indexOf(track).coerceAtLeast(0)
                                playerManager.setQueueAndPlay(albumTracks, idx)
                            },
                            downloadProgressMap = downloadProgressMap
                        )
                    } else {
                        // Вкладки нижнего меню
                        when (currentTab) {
                            MainTab.SEARCH -> {
                                SearchScreen(
                                    viewModel = searchViewModel,
                                    onTrackPlay = { track ->
                                        val tracks = searchViewModel.uiState.value.tracks
                                        val idx = tracks.indexOf(track).coerceAtLeast(0)
                                        playerManager.setQueueAndPlay(tracks, idx)
                                    },
                                    onAlbumClick = { album ->
                                        selectedAlbumBrowseId = album.browseId
                                    },
                                    onDownloadTrack = { track ->
                                        downloadManager.downloadTrack(track)
                                    },
                                    downloadProgressMap = downloadProgressMap
                                )
                            }
                            MainTab.DOWNLOADS -> {
                                val offlineTracks = remember(downloadedTracks) {
                                    downloadedTracks.map { entity ->
                                        Track(
                                            id = entity.id,
                                            title = entity.title,
                                            artist = entity.artist,
                                            album = entity.album,
                                            duration = entity.duration,
                                            thumbnailUrl = entity.thumbnailUrl,
                                            isDownloaded = true,
                                            localFilePath = entity.localFilePath
                                        )
                                    }
                                }

                                DownloadsScreen(
                                    downloadedTracks = downloadedTracks,
                                    onTrackClick = { track ->
                                        val idx = offlineTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                        playerManager.setQueueAndPlay(offlineTracks, idx)
                                    },
                                    onDeleteTrack = { trackId ->
                                        coroutineScope.launch {
                                            downloadManager.deleteDownloadedTrack(trackId)
                                        }
                                    }
                                )
                            }
                            MainTab.FAVORITES -> {
                                val favTracks = remember(favoriteTracks) {
                                    favoriteTracks.map { entity ->
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
                                }

                                DownloadsScreen(
                                    downloadedTracks = favoriteTracks,
                                    onTrackClick = { track ->
                                        val idx = favTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                        playerManager.setQueueAndPlay(favTracks, idx)
                                    },
                                    onDeleteTrack = { trackId ->
                                        coroutineScope.launch {
                                            musicDao.updateFavorite(trackId, false)
                                        }
                                    }
                                )
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
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MainTab.entries.forEach { tab ->
                                val isSelected = currentTab == tab && selectedAlbumBrowseId == null
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedAlbumBrowseId = null
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
                                        fontSize = 11.sp
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
                                onNextClick = { playerManager.playNext() },
                                onSeekTo = { newPos -> playerManager.seekTo(newPos) },
                                onFavoriteToggle = { toggleFavorite(track) },
                                onDownloadClick = { downloadManager.downloadTrack(track) },
                                onAddToPlaylistClick = { /* Добавить в плейлист */ },
                                onCloseClick = { isFullPlayerVisible = false }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
