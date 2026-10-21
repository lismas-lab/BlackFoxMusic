package com.blackfox.music.presentation.screens.mymusic

import com.blackfox.music.data.local.LocalAccentTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackfox.music.data.download.DownloadProgress
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.components.TrackListItem
import com.blackfox.music.presentation.theme.*

@Composable
fun DownloadedTracksScreen(
    downloadedTracks: List<TrackEntity>,
    storageSizeBytes: Long,
    downloadProgressMap: Map<String, DownloadProgress>,
    favoriteTrackIds: Set<String>,
    onBackClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylistClick: (Track) -> Unit,
    onDeleteTrack: (String) -> Unit,
    currentTrack: Track? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    var trackToDelete by remember { mutableStateOf<TrackEntity?>(null) }
    val accentTheme = LocalAccentTheme.current

    val formattedSize = remember(storageSizeBytes) {
        val mb = storageSizeBytes.toDouble() / (1024 * 1024)
        if (mb >= 1024) {
            String.format("%.2f ГБ", mb / 1024)
        } else {
            String.format("%.1f МБ", mb)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Шапка экрана
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Верхняя навигация
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x1AFFFFFF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Назад",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "Скачанные треки",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Большая карточка со статусом хранилища
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(cornerRadius = 24.dp, backgroundColor = Color(0x18FFFFFF))
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(accentTheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DownloadDone,
                                    contentDescription = null,
                                    tint = accentTheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Доступно оффлайн",
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${downloadedTracks.size} треков • $formattedSize занято",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Кнопки управления воспроизведением
                    if (downloadedTracks.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onPlayAll,
                                colors = ButtonDefaults.buttonColors(containerColor = accentTheme.primary),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = ObsidianBlack,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Слушать", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = onShuffle,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x1AFFFFFF)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Микс", color = TextPrimary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Список треков
            if (downloadedTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Нет скачанных треков",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Нажмите стрелочку скачивания на любом треке или добавьте его в Любимое, чтобы слушать музыку без интернета",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            } else {
                items(downloadedTracks, key = { it.id }) { entity ->
                    val domainTrack = Track(
                        id = entity.id,
                        title = entity.title,
                        artist = entity.artist,
                        album = entity.album,
                        duration = entity.duration,
                        thumbnailUrl = entity.thumbnailUrl,
                        isDownloaded = true,
                        localFilePath = entity.localFilePath
                    )
                    val isFav = favoriteTrackIds.contains(entity.id)
                    val progress = downloadProgressMap[entity.id]

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            TrackListItem(
                                track = domainTrack,
                                onTrackClick = { onTrackClick(domainTrack) },
                                isFavorite = isFav,
                                onFavoriteToggle = { onToggleFavorite(domainTrack) },
                                onAddToPlaylistClick = { onAddToPlaylistClick(domainTrack) },
                                onDownloadClick = { /* уже скачан */ },
                                downloadProgress = progress,
                                isDownloaded = true,
                                isActive = currentTrack?.id == entity.id,
                                isPlaying = isPlaying && currentTrack?.id == entity.id
                            )
                        }

                        // Кнопка удаления файла с устройства
                        IconButton(
                            onClick = { trackToDelete = entity },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Удалить с устройства",
                                tint = TextSecondary.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Диалог подтверждения удаления трека
        trackToDelete?.let { track ->
            AlertDialog(
                onDismissRequest = { trackToDelete = null },
                containerColor = Color(0xFF1E1E22),
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "Удалить из памяти?",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Файл «${track.title}» будет удален с устройства. Вы сможете скачать его снова в любое время.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val id = track.id
                            trackToDelete = null
                            onDeleteTrack(id)
                        }
                    ) {
                        Text("Удалить", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { trackToDelete = null }) {
                        Text("Отмена", color = TextPrimary)
                    }
                }
            )
        }
    }
}
