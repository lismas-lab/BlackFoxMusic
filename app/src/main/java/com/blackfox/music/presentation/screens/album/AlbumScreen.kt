package com.blackfox.music.presentation.screens.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blackfox.music.data.download.DownloadProgress
import com.blackfox.music.data.download.DownloadStatus
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.components.AddToPlaylistBottomSheet
import com.blackfox.music.presentation.theme.*

@Composable
fun AlbumScreen(
    viewModel: AlbumViewModel,
    onBackClick: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    downloadProgressMap: Map<String, DownloadProgress> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val favorites by viewModel.favoriteTrackIds.collectAsState()
    var selectedTrackForPlaylist by remember { mutableStateOf<Track?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = FoxOrange)
            }
        } else if (uiState.album != null) {
            val album = uiState.album!!

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 110.dp)
            ) {
                // Шапка альбома
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Кнопка назад
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = TextPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Большая обложка 1080p в стекле
                        AsyncImage(
                            model = album.coverUrl,
                            contentDescription = album.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .liquidGlass(cornerRadius = 26.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = album.title,
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${album.artist} • ${album.year} • ${album.trackCount} треков",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Кнопки управления (Play All, Shuffle, Download All)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Кнопка Слушать Всё (Play All)
                            Button(
                                onClick = {
                                    if (album.tracks.isNotEmpty()) onPlayTrack(album.tracks.first())
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = FoxOrange),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(46.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ObsidianBlack)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Слушать", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Скачать весь альбом
                            IconButton(
                                onClick = { viewModel.downloadEntireAlbum() },
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x18FFFFFF))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Скачать всё",
                                    tint = FoxOrange
                                )
                            }
                        }
                    }
                }

                // Список треков альбома
                itemsIndexed(album.tracks) { index, track ->
                    val isFav = favorites.contains(track.id)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayTrack(track) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Порядковый номер
                        Text(
                            text = String.format("%02d", index + 1),
                            color = TextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(28.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (track.duration.isNotBlank()) {
                                Text(
                                    text = track.duration,
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Скачать с индикатором прогресса
                        val dlProgress = downloadProgressMap[track.id]
                        val isDl = dlProgress?.status == DownloadStatus.DOWNLOADING
                        val isDone = track.isDownloaded || dlProgress?.status == DownloadStatus.COMPLETED

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(
                                    enabled = !isDl,
                                    onClick = { viewModel.downloadSingleTrack(track) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDl) {
                                val percent = dlProgress?.progressPercent ?: 0
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        progress = { if (percent > 0) percent / 100f else 0.15f },
                                        color = FoxOrange,
                                        trackColor = ObsidianCard,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    if (percent > 0) {
                                        Text(
                                            text = "$percent%",
                                            color = FoxOrange,
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else if (isDone) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Скачано",
                                    tint = FoxOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Скачать",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Лайк
                        IconButton(
                            onClick = { viewModel.toggleFavorite(track) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Избранное",
                                tint = if (isFav) FoxOrange else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // В плейлист
                        IconButton(
                            onClick = { selectedTrackForPlaylist = track },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlaylistAdd,
                                contentDescription = "В плейлист",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Шторка быстрого добавления в плейлист
        selectedTrackForPlaylist?.let { track ->
            AddToPlaylistBottomSheet(
                track = track,
                playlists = playlists,
                onDismiss = { selectedTrackForPlaylist = null },
                onToggleTrackInPlaylist = { plId ->
                    viewModel.addTrackToPlaylist(plId, track)
                },
                onCreatePlaylistAndAdd = { name -> },
                isPlaylistContainsTrack = { false }
            )
        }
    }
}
