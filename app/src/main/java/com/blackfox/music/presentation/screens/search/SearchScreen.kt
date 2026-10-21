package com.blackfox.music.presentation.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackfox.music.domain.model.Album
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.components.AddToPlaylistBottomSheet
import com.blackfox.music.presentation.components.AlbumGridItem
import com.blackfox.music.presentation.components.GlassSearchBar
import com.blackfox.music.presentation.components.GlowingFoxLogo
import com.blackfox.music.data.download.DownloadProgress
import com.blackfox.music.presentation.components.TrackListItem
import com.blackfox.music.presentation.theme.*

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onTrackPlay: (Track) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    downloadProgressMap: Map<String, DownloadProgress> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val query by viewModel.query.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val favorites by viewModel.favoriteTrackIds.collectAsState()

    var selectedTrackForPlaylist by remember { mutableStateOf<Track?>(null) }

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
                    .padding(vertical = 12.dp)
            )

            // Поисковая строка в стиле Liquid Glass
            GlassSearchBar(
                query = query,
                onQueryChange = viewModel::onQueryChange
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                            .background(if (isSelected) FoxOrange else Color(0x12FFFFFF))
                            .clickable { viewModel.onFilterSelect(filter) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
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

            Spacer(modifier = Modifier.height(14.dp))

            // Индикатор загрузки
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = FoxOrange,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Результаты поиска
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Раздел Альбомов
                if (uiState.albums.isNotEmpty()) {
                    item {
                        Text(
                            text = "Альбомы",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
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
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }

                    items(uiState.tracks) { track ->
                        val isFav = favorites.contains(track.id)
                        val progress = downloadProgressMap[track.id]
                        TrackListItem(
                            track = track,
                            onTrackClick = { onTrackPlay(track) },
                            isFavorite = isFav,
                            onFavoriteToggle = { viewModel.toggleFavorite(track) },
                            onAddToPlaylistClick = { selectedTrackForPlaylist = track },
                            onDownloadClick = { onDownloadTrack(track) },
                            downloadProgress = progress
                        )
                    }
                }
            }
        }

        // Шторка быстрого добавления в плейлист
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
