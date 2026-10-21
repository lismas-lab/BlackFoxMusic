package com.blackfox.music.presentation.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blackfox.music.data.download.DownloadProgress
import com.blackfox.music.domain.model.Album
import com.blackfox.music.domain.model.Artist
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.components.AddToPlaylistBottomSheet
import com.blackfox.music.presentation.components.AlbumListItem
import com.blackfox.music.presentation.components.ArtistGridItem
import com.blackfox.music.presentation.components.ArtistListItem
import com.blackfox.music.presentation.components.TrackListItem
import com.blackfox.music.presentation.theme.*

enum class ArtistTab(val title: String) {
    OVERVIEW("Обзор"),
    ALL_SONGS("Все песни"),
    SIMILAR("Похожие")
}

@Composable
fun ArtistScreen(
    viewModel: ArtistViewModel,
    onBackClick: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit = {},
    downloadProgressMap: Map<String, DownloadProgress> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val favorites by viewModel.favoriteTrackIds.collectAsState()
    val downloadedTrackIds by viewModel.downloadedTrackIds.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val allTracks by viewModel.allTracks.collectAsState()
    val isAllTracksLoading by viewModel.isAllTracksLoading.collectAsState()

    var selectedTab by remember { mutableStateOf(ArtistTab.OVERVIEW) }
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
        ) {
            // Верхняя навигационная панель
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = uiState.artist?.name ?: viewModel.initialName.ifBlank { "Артист" },
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = FoxOrange)
                }
            } else if (uiState.error != null && uiState.artist == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: "Ошибка загрузки артиста",
                        color = TextSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val artist = uiState.artist ?: return@Column

                // Переключатель вкладок: Обзор | Все песни | Похожие
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArtistTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        val countSuffix = when (tab) {
                            ArtistTab.ALL_SONGS -> if (allTracks.isNotEmpty()) " (${allTracks.size})" else ""
                            ArtistTab.SIMILAR -> if (artist.similarArtists.isNotEmpty()) " (${artist.similarArtists.size})" else ""
                            else -> ""
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) FoxOrange.copy(alpha = 0.2f) else Color(0x14FFFFFF))
                                .clickable {
                                    selectedTab = tab
                                    if (tab == ArtistTab.ALL_SONGS && allTracks.isEmpty()) {
                                        viewModel.loadAllTracks()
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${tab.title}$countSuffix",
                                color = if (isSelected) FoxOrange else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                when (selectedTab) {
                    // ВКЛАДКА 1: ОБЗОР
                    ArtistTab.OVERVIEW -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Шапка артиста с большим аватаром, именем и кнопкой воспроизведения
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(CircleShape)
                                            .background(ObsidianCard),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (artist.avatarUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = artist.avatarUrl,
                                                contentDescription = artist.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = artist.name,
                                                tint = FoxOrange,
                                                modifier = Modifier.size(60.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = artist.name,
                                        color = TextPrimary,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )

                                    if (artist.subscribers.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${artist.subscribers} подписчиков",
                                            color = TextSecondary,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    val primaryPlayList = if (allTracks.isNotEmpty()) allTracks else artist.topTracks
                                    if (primaryPlayList.isNotEmpty()) {
                                        Button(
                                            onClick = { onPlayTrack(primaryPlayList[0], primaryPlayList) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = FoxOrange,
                                                contentColor = ObsidianBlack
                                            ),
                                            shape = RoundedCornerShape(24.dp),
                                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = ObsidianBlack,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Слушать всё",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Раздел: Популярные треки + переход ко всем песням
                            if (artist.topTracks.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Популярные треки",
                                            color = TextPrimary,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    selectedTab = ArtistTab.ALL_SONGS
                                                    if (allTracks.isEmpty()) viewModel.loadAllTracks()
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Все песни",
                                                color = FoxOrange,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = FoxOrange,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                items(artist.topTracks.take(5), key = { it.id }) { track ->
                                    val isFav = favorites.contains(track.id)
                                    val progress = downloadProgressMap[track.id]
                                    val isDownloaded = downloadedTrackIds.contains(track.id) || track.isDownloaded

                                    TrackListItem(
                                        track = track,
                                        onTrackClick = { onPlayTrack(track, artist.topTracks) },
                                        isFavorite = isFav,
                                        onFavoriteToggle = { viewModel.toggleFavorite(track) },
                                        onAddToPlaylistClick = { selectedTrackForPlaylist = track },
                                        onDownloadClick = { viewModel.downloadTrack(track) },
                                        downloadProgress = progress,
                                        isDownloaded = isDownloaded
                                    )
                                }
                            }

                            // Раздел: Альбомы и синглы
                            if (artist.albums.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Альбомы и синглы",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                                    )
                                }

                                items(artist.albums, key = { it.browseId }) { album ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                        AlbumListItem(
                                            album = album,
                                            onAlbumClick = { onAlbumClick(album) }
                                        )
                                    }
                                }
                            }

                            // Раздел: Похожие артисты
                            if (artist.similarArtists.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Похожие артисты",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                                    )

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                                    ) {
                                        items(artist.similarArtists, key = { it.channelId }) { sim ->
                                            ArtistGridItem(
                                                artist = sim,
                                                onArtistClick = { onArtistClick(sim) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ВКЛАДКА 2: ВСЕ ПЕСНИ АРТИСТА
                    ArtistTab.ALL_SONGS -> {
                        val activeTracks = if (allTracks.isNotEmpty()) allTracks else artist.topTracks

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Все треки артиста",
                                            color = TextPrimary,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${activeTracks.size} треков",
                                            color = TextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }

                                    if (activeTracks.isNotEmpty()) {
                                        Button(
                                            onClick = { onPlayTrack(activeTracks[0], activeTracks) },
                                            colors = ButtonDefaults.buttonColors(containerColor = FoxOrange),
                                            shape = RoundedCornerShape(16.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = ObsidianBlack,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Слушать все", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            if (isAllTracksLoading && allTracks.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = FoxOrange)
                                    }
                                }
                            } else {
                                items(activeTracks, key = { it.id }) { track ->
                                    val isFav = favorites.contains(track.id)
                                    val progress = downloadProgressMap[track.id]
                                    val isDownloaded = downloadedTrackIds.contains(track.id) || track.isDownloaded

                                    TrackListItem(
                                        track = track,
                                        onTrackClick = { onPlayTrack(track, activeTracks) },
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

                    // ВКЛАДКА 3: ПОХОЖИЕ АРТИСТЫ
                    ArtistTab.SIMILAR -> {
                        if (artist.similarArtists.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Похожие артисты не найдены",
                                    color = TextSecondary,
                                    fontSize = 15.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 120.dp)
                            ) {
                                item {
                                    Text(
                                        text = "Похожие исполнители",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                    )
                                }

                                items(artist.similarArtists, key = { it.channelId }) { sim ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                        ArtistListItem(
                                            artist = sim,
                                            onArtistClick = { onArtistClick(sim) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Шторка добавления в плейлист
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