package com.blackfox.music.presentation.screens.mymusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.blackfox.music.data.download.DownloadProgress
import com.blackfox.music.data.local.LocalAccentTheme
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.components.TrackListItem
import com.blackfox.music.presentation.theme.*

@Composable
fun FavoritesDetailScreen(
    favoriteTracks: List<TrackEntity>,
    downloadProgressMap: Map<String, DownloadProgress>,
    onBackClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylistClick: (Track) -> Unit,
    onReorderFavorites: (List<String>) -> Unit = {},
    currentTrack: Track? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentTheme.current.primary
    var localTracks by remember { mutableStateOf(favoriteTracks) }
    var draggingTrackId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(favoriteTracks) {
        if (draggingTrackId == null) {
            localTracks = favoriteTracks
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
            // Шапка экрана Избранного
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Кнопка назад
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0x18FFFFFF))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Большая неоновая иконка
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(
                                Brush.radialGradient(
                                    listOf(accent.copy(alpha = 0.5f), Color(0x1AFFFFFF))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Любимое",
                            tint = accent,
                            modifier = Modifier.size(80.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Любимые треки",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${favoriteTracks.size} треков • Скачиваются автоматически",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Кнопки Слушать всё / Перемешать / Изменить порядок
                    if (favoriteTracks.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(0.92f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onPlayAll,
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
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

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Список треков
            if (favoriteTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Любимых треков пока нет",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Нажмите на сердечко на любом треке, чтобы добавить его сюда и сохранить на устройство",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(localTracks, key = { _, t -> t.id }) { index, entity ->
                    val domainTrack = Track(
                        id = entity.id,
                        title = entity.title,
                        artist = entity.artist,
                        album = entity.album,
                        duration = entity.duration,
                        thumbnailUrl = entity.thumbnailUrl,
                        isDownloaded = entity.isDownloaded,
                        localFilePath = entity.localFilePath
                    )
                    val progress = downloadProgressMap[entity.id]
                    val isDragging = entity.id == draggingTrackId

                    TrackListItem(
                        track = domainTrack,
                        onTrackClick = { onTrackClick(domainTrack) },
                        isFavorite = true,
                        onFavoriteToggle = { onToggleFavorite(domainTrack) },
                        onAddToPlaylistClick = { onAddToPlaylistClick(domainTrack) },
                        onDownloadClick = { /* скачивание */ },
                        downloadProgress = progress,
                        isDownloaded = entity.isDownloaded,
                        isActive = currentTrack?.id == entity.id,
                        isPlaying = isPlaying && currentTrack?.id == entity.id,
                        showReorderHandle = true,
                        reorderHandleModifier = Modifier.pointerInput(entity.id) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingTrackId = entity.id
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                    val itemHeightPx = 68.dp.toPx()
                                    val currentIdx = localTracks.indexOfFirst { it.id == entity.id }
                                    if (currentIdx != -1) {
                                        if (dragOffsetY > itemHeightPx && currentIdx < localTracks.lastIndex) {
                                            val nextIdx = currentIdx + 1
                                            val updated = localTracks.toMutableList().apply {
                                                add(nextIdx, removeAt(currentIdx))
                                            }
                                            localTracks = updated
                                            dragOffsetY -= itemHeightPx
                                        } else if (dragOffsetY < -itemHeightPx && currentIdx > 0) {
                                            val prevIdx = currentIdx - 1
                                            val updated = localTracks.toMutableList().apply {
                                                add(prevIdx, removeAt(currentIdx))
                                            }
                                            localTracks = updated
                                            dragOffsetY += itemHeightPx
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val finalOrder = localTracks.map { it.id }
                                    draggingTrackId = null
                                    dragOffsetY = 0f
                                    onReorderFavorites(finalOrder)
                                },
                                onDragCancel = {
                                    draggingTrackId = null
                                    dragOffsetY = 0f
                                }
                            )
                        },
                        modifier = Modifier
                            .zIndex(if (isDragging) 10f else 1f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetY else 0f
                                scaleX = if (isDragging) 1.03f else 1f
                                scaleY = if (isDragging) 1.03f else 1f
                                shadowElevation = if (isDragging) 16f else 0f
                            }
                    )
                }
            }
        }
    }
}