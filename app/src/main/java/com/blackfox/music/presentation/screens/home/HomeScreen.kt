package com.blackfox.music.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackfox.music.data.download.DownloadProgress
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.domain.model.Album
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.components.*
import com.blackfox.music.presentation.screens.search.SearchFilter
import com.blackfox.music.presentation.screens.search.SearchViewModel
import com.blackfox.music.presentation.theme.*

@Composable
fun HomeScreen(
    viewModel: SearchViewModel,
    favoriteTracks: List<TrackEntity>,
    userPlaylistsCount: Int,
    onTrackPlay: (track: Track, queue: List<Track>) -> Unit,
    onPlayFavorites: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onNavigateToMyMusic: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    downloadProgressMap: Map<String, DownloadProgress> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val query by viewModel.query.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val favorites by viewModel.favoriteTrackIds.collectAsState()
    val downloadedTrackIds by viewModel.downloadedTrackIds.collectAsState()

    var selectedTrackForPlaylist by remember { mutableStateOf<Track?>(null) }

    val quickGenres = remember {
        listOf("Рок", "Электроника", "Phonk", "Хип-хоп", "Lo-Fi", "Synthwave", "Поп", "Метал")
    }

    // Преобразуем любимые треки в доменные модели для мгновенного воспроизведения из очереди
    val favoriteDomainTracks = remember(favoriteTracks, downloadedTrackIds) {
        favoriteTracks.map { entity ->
            Track(
                id = entity.id,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                duration = entity.duration,
                thumbnailUrl = entity.thumbnailUrl,
                isDownloaded = downloadedTrackIds.contains(entity.id) || entity.isDownloaded,
                localFilePath = entity.localFilePath
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Логотип и брендинг Black Fox со светящейся эмблемой
            GlowingFoxLogo(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            )

            // Поисковая строка в стиле Liquid Glass
            GlassSearchBar(
                query = query,
                onQueryChange = viewModel::onQueryChange
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Чипы фильтрации (Все, Песни, Альбомы)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SearchFilter.entries.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) FoxOrange else Color(0x14FFFFFF))
                            .clickable { viewModel.onFilterSelect(filter) }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = filter.label,
                            color = if (isSelected) ObsidianBlack else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Индикатор загрузки поиска
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = FoxOrange,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Основной скроллируемый контент
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 110.dp)
            ) {
                // ЕСЛИ ПОИСК ПУСТОЙ -> ПОКАЗЫВАЕМ БЛОКИ ГЛАВНОГО ЭКРАНА
                if (query.isBlank()) {
                    item {
                        // Виджеты быстрого доступа (Любимое + Моя музыка)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Карточка Любимое
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .liquidGlass(
                                        cornerRadius = 20.dp,
                                        backgroundColor = Color(0x18FFFFFF),
                                        borderBrush = Brush.linearGradient(
                                            listOf(FoxOrange.copy(alpha = 0.5f), Color.Transparent)
                                        )
                                    )
                                    .clickable { onNavigateToFavorites() }
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(FoxOrange.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = FoxOrange,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        if (favoriteTracks.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(FoxOrange)
                                                    .clickable { onPlayFavorites() },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Слушать",
                                                    tint = ObsidianBlack,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = "Любимые",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${favoriteTracks.size} треков",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // Карточка Моя музыка
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .liquidGlass(cornerRadius = 20.dp, backgroundColor = Color(0x14FFFFFF))
                                    .clickable { onNavigateToMyMusic() }
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LibraryMusic,
                                            contentDescription = null,
                                            tint = TextPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = "Моя музыка",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$userPlaylistsCount плейлистов",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // Быстрый выбор настроения / жанра
                    item {
                        Column {
                            Text(
                                text = "Жанры и настроения",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(quickGenres) { genre ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .liquidGlass(cornerRadius = 12.dp, backgroundColor = Color(0x10FFFFFF))
                                            .clickable { viewModel.onQueryChange(genre) }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = genre,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Недавно добавленные любимые треки
                    if (favoriteDomainTracks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Недавнее из любимого",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        items(favoriteDomainTracks.take(6), key = { it.id }) { track ->
                            val progress = downloadProgressMap[track.id]
                            val isDownloaded = downloadedTrackIds.contains(track.id) || track.isDownloaded

                            TrackListItem(
                                track = track,
                                onTrackClick = { onTrackPlay(track, favoriteDomainTracks) },
                                isFavorite = true,
                                onFavoriteToggle = { viewModel.toggleFavorite(track) },
                                onAddToPlaylistClick = { selectedTrackForPlaylist = track },
                                onDownloadClick = { viewModel.downloadTrack(track) },
                                downloadProgress = progress,
                                isDownloaded = isDownloaded
                            )
                        }
                    }
                }

                // ЕСЛИ ПОИСК АКТИВЕН -> ВЫДАЧА РЕЗУЛЬТАТОВ
                if (query.isNotBlank()) {
                    // Раздел Альбомов
                    if (uiState.albums.isNotEmpty()) {
                        item {
                            Text(
                                text = "Альбомы",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(uiState.albums) { album ->
                                    AlbumGridItem(
                                        album = album,
                                        onAlbumClick = { onAlbumClick(album) }
                                    )
                                }
                            }
                        }
                    }

                    // Раздел Песен (Треков)
                    if (uiState.tracks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Песни",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }

                        items(uiState.tracks, key = { it.id }) { track ->
                            val isFav = favorites.contains(track.id)
                            val progress = downloadProgressMap[track.id]
                            val isDownloaded = downloadedTrackIds.contains(track.id) || track.isDownloaded

                            TrackListItem(
                                track = track,
                                onTrackClick = { onTrackPlay(track, uiState.tracks) },
                                isFavorite = isFav,
                                onFavoriteToggle = { viewModel.toggleFavorite(track) },
                                onAddToPlaylistClick = { selectedTrackForPlaylist = track },
                                onDownloadClick = { viewModel.downloadTrack(track) },
                                downloadProgress = progress,
                                isDownloaded = isDownloaded
                            )
                        }
                    }
                }
            }
        }

        // Шторка быстрого добавления в плейлист с РЕАКТИВНЫМ состоянием привязки
        selectedTrackForPlaylist?.let { track ->
            val containingPlaylistIds by viewModel.getPlaylistIdsForTrack(track.id).collectAsState(initial = emptyList())

            AddToPlaylistBottomSheet(
                track = track,
                playlists = playlists,
                containingPlaylistIds = remember(containingPlaylistIds) { containingPlaylistIds.toSet() },
                onDismiss = { selectedTrackForPlaylist = null },
                onToggleTrackInPlaylist = { plId ->
                    viewModel.toggleTrackInPlaylist(plId, track)
                },
                onCreatePlaylistAndAdd = { name ->
                    viewModel.createPlaylistAndAddTrack(name, track)
                }
            )
        }
    }
}
