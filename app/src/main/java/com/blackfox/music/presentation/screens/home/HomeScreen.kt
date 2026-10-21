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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blackfox.music.data.download.DownloadProgress
import com.blackfox.music.data.local.LocalAccentTheme
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.domain.model.Album
import com.blackfox.music.domain.model.Artist
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
    allSavedTracks: List<TrackEntity> = emptyList(),
    randomSavedPreviewTracks: List<TrackEntity> = emptyList(),
    newReleasesFromFollowedArtists: List<Track> = emptyList(),
    currentTrack: Track? = null,
    isPlaying: Boolean = false,
    onTrackPlay: (track: Track, queue: List<Track>) -> Unit,
    onPlayFavorites: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onArtistNameClick: (String, String?) -> Unit,
    onStartFoxFlow: () -> Unit = {},
    onPlaySavedRandom: () -> Unit = {},
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
    val suggestions by viewModel.suggestions.collectAsState()
    val accentColor = LocalAccentTheme.current.primary

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

            // Поисковая строка в стиле Liquid Glass с подсказками
            GlassSearchBar(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                suggestions = suggestions,
                onSuggestionClick = { viewModel.onSuggestionSelect(it) }
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
                            .background(if (isSelected) accentColor else Color(0x14FFFFFF))
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
                        color = accentColor,
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
                    // Баннер «Fox Flow» (Моя волна)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlass(
                                    cornerRadius = 24.dp,
                                    backgroundColor = Color(0x18FFFFFF),
                                    borderBrush = Brush.linearGradient(
                                        listOf(accentColor.copy(alpha = 0.85f), accentColor.copy(alpha = 0.15f))
                                    )
                                )
                                .clickable { onStartFoxFlow() }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            Brush.radialGradient(
                                                listOf(accentColor, accentColor.copy(alpha = 0.35f))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "Fox Flow",
                                        tint = ObsidianBlack,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Fox Flow",
                                            color = TextPrimary,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(accentColor.copy(alpha = 0.25f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Моя волна",
                                                color = accentColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Умный бесконечный поток на основе любимой музыки",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                        .clickable { onStartFoxFlow() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Запустить",
                                        tint = ObsidianBlack,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

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
                                            listOf(accentColor.copy(alpha = 0.5f), Color.Transparent)
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
                                                .background(accentColor.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        if (favoriteTracks.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(accentColor)
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

                    // Раздел "Новинки от ваших артистов"
                    if (newReleasesFromFollowedArtists.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(accentColor.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NewReleases,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Новинки ваших артистов",
                                                color = TextPrimary,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Свежие треки за последний месяц",
                                                color = TextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    items(newReleasesFromFollowedArtists, key = { it.id }) { track ->
                                        Box(
                                            modifier = Modifier
                                                .width(150.dp)
                                                .liquidGlass(cornerRadius = 18.dp, backgroundColor = Color(0x16FFFFFF))
                                                .clickable { onTrackPlay(track, newReleasesFromFollowedArtists) }
                                                .padding(10.dp)
                                        ) {
                                            Column {
                                                Box(
                                                    modifier = Modifier
                                                        .size(130.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(ObsidianCard)
                                                ) {
                                                    AsyncImage(
                                                        model = track.thumbnailUrl,
                                                        contentDescription = track.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(6.dp)
                                                            .align(Alignment.TopStart)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(accentColor)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "NEW",
                                                            color = ObsidianBlack,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(6.dp)
                                                            .align(Alignment.BottomEnd)
                                                            .size(32.dp)
                                                            .clip(CircleShape)
                                                            .background(accentColor)
                                                            .clickable { onTrackPlay(track, newReleasesFromFollowedArtists) },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = "Слушать",
                                                            tint = ObsidianBlack,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                Text(
                                                    text = track.title,
                                                    color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = track.artist,
                                                    color = TextSecondary,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Баннер "Случайное из сохраненного" (отображает 3 первых трека из цепочки)
                    if (allSavedTracks.isNotEmpty()) {
                        item {
                            val firstThree = if (randomSavedPreviewTracks.isNotEmpty()) randomSavedPreviewTracks.take(3) else allSavedTracks.take(3)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlass(cornerRadius = 22.dp, backgroundColor = Color(0x16FFFFFF))
                                    .clickable { onPlaySavedRandom() }
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                .clip(CircleShape)
                                                .background(accentColor.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Shuffle,
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Случайное из сохраненного",
                                                    color = TextPrimary,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${allSavedTracks.size} треков в вашей коллекции",
                                                    color = TextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(accentColor)
                                                .clickable { onPlaySavedRandom() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Слушать микс",
                                                tint = ObsidianBlack,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // 3 первых трека из цепочки с мини-обложками и названиями
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        firstThree.forEach { track ->
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0x14FFFFFF))
                                                    .padding(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AsyncImage(
                                                    model = track.thumbnailUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(ObsidianCard)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = track.title,
                                                        color = TextPrimary,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = track.artist,
                                                        color = TextSecondary,
                                                        fontSize = 10.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
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
                                isDownloaded = isDownloaded,
                                isActive = currentTrack?.id == track.id,
                                isPlaying = isPlaying && currentTrack?.id == track.id,
                                onArtistClick = { artistName, artistId ->
                                    onArtistNameClick(artistName, artistId)
                                }
                            )
                        }
                    }
                }

                // ЕСЛИ ПОИСК АКТИВЕН -> ВЫДАЧА РЕЗУЛЬТАТОВ
                if (query.isNotBlank()) {
                    when (selectedFilter) {
                        SearchFilter.ALL -> {
                            // 1. Артисты сверху над альбомами и песнями
                            if (uiState.artists.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Артисты",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        items(uiState.artists, key = { it.channelId }) { artist ->
                                            ArtistGridItem(
                                                artist = artist,
                                                onArtistClick = { onArtistClick(artist) }
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. Альбомы
                            if (uiState.albums.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Альбомы",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        items(uiState.albums, key = { it.browseId }) { album ->
                                            AlbumGridItem(
                                                album = album,
                                                onAlbumClick = { onAlbumClick(album) }
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. Песни (Треки)
                            if (uiState.tracks.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Песни",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
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
                                        isDownloaded = isDownloaded,
                                        isActive = currentTrack?.id == track.id,
                                        isPlaying = isPlaying && currentTrack?.id == track.id,
                                        onArtistClick = { artistName, artistId ->
                                            onArtistNameClick(artistName, artistId)
                                        }
                                    )
                                }
                            }
                        }

                        SearchFilter.ARTISTS -> {
                            // Вертикальный список артистов при фильтре "Артисты"
                            if (uiState.artists.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Артисты",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }

                                items(uiState.artists, key = { it.channelId }) { artist ->
                                    ArtistListItem(
                                        artist = artist,
                                        onArtistClick = { onArtistClick(artist) }
                                    )
                                }
                            }
                        }

                        SearchFilter.ALBUMS -> {
                            // Вертикальный список альбомов при фильтре "Альбомы"
                            if (uiState.albums.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Альбомы",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }

                                items(uiState.albums, key = { it.browseId }) { album ->
                                    AlbumListItem(
                                        album = album,
                                        onAlbumClick = { onAlbumClick(album) }
                                    )
                                }
                            }
                        }

                        SearchFilter.SONGS -> {
                            // Вертикальный список песен при фильтре "Песни"
                            if (uiState.tracks.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Песни",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 6.dp)
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
                                        isDownloaded = isDownloaded,
                                        isActive = currentTrack?.id == track.id,
                                        isPlaying = isPlaying && currentTrack?.id == track.id,
                                        onArtistClick = { artistName, artistId ->
                                            onArtistNameClick(artistName, artistId)
                                        }
                                    )
                                }
                            }
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
