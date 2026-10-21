package com.blackfox.music.presentation.screens.mymusic

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.blackfox.music.data.local.PlaylistEntity
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.components.TrackListItem
import com.blackfox.music.presentation.theme.*
import java.io.File

@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistEntity,
    tracks: List<TrackEntity>,
    favoriteTrackIds: Set<String>,
    downloadProgressMap: Map<String, DownloadProgress>,
    onBackClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onUpdateCover: (Uri?) -> Unit,
    onRenamePlaylist: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onMoveTrack: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pl = playlist
    var isReorderMode by remember { mutableStateOf(false) }

    BackHandler(enabled = isReorderMode) {
        isReorderMode = false
    }

    val coverModel = remember(pl.customCoverUri, tracks) {
        when {
            !pl.customCoverUri.isNullOrBlank() -> File(pl.customCoverUri)
            tracks.isNotEmpty() -> tracks.first().thumbnailUrl.ifBlank { null }
            else -> null
        }
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onUpdateCover(uri)
        }
    }

    var isMenuExpanded by remember { mutableStateOf(false) }
    var isRenameDialogOpen by remember { mutableStateOf(false) }
    var isDeleteDialogOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Шапка плейлиста
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Панель навигации (Назад и Меню)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = TextPrimary
                            )
                        }

                        Box {
                            IconButton(onClick = { isMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Меню",
                                    tint = TextPrimary
                                )
                            }

                            DropdownMenu(
                                expanded = isMenuExpanded,
                                onDismissRequest = { isMenuExpanded = false },
                                modifier = Modifier.background(Color(0xF0181A22))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Сменить обложку", color = TextPrimary, fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Image, contentDescription = null, tint = FoxOrange, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        isMenuExpanded = false
                                        coverPickerLauncher.launch("image/*")
                                    }
                                )

                                if (!pl.customCoverUri.isNullOrBlank()) {
                                    DropdownMenuItem(
                                        text = { Text("Сбросить обложку", color = TextSecondary, fontSize = 13.sp) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                        },
                                        onClick = {
                                            isMenuExpanded = false
                                            onUpdateCover(null)
                                        }
                                    )
                                }

                                if (tracks.size > 1) {
                                    DropdownMenuItem(
                                        text = { Text("Порядок треков", color = TextPrimary, fontSize = 13.sp) },
                                        leadingIcon = {
                                            Icon(Icons.Default.SwapVert, contentDescription = null, tint = FoxOrange, modifier = Modifier.size(18.dp))
                                        },
                                        onClick = {
                                            isMenuExpanded = false
                                            isReorderMode = true
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("Переименовать", color = TextPrimary, fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        isMenuExpanded = false
                                        isRenameDialogOpen = true
                                    }
                                )

                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                DropdownMenuItem(
                                    text = { Text("Удалить плейлист", color = Color(0xFFFF5252), fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        isMenuExpanded = false
                                        isDeleteDialogOpen = true
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Большая обложка плейлиста с кнопкой смены поверх
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0x18FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverModel != null) {
                            AsyncImage(
                                model = coverModel,
                                contentDescription = pl.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(60.dp)
                            )
                        }

                        // Иконка камеры для быстрой смены обложки
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { coverPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Сменить обложку",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Название
                    Text(
                        text = pl.name,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Плейлист • ${tracks.size} треков",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Кнопки управления (Слушать всё / Перемешать / Изменить порядок)
                    if (tracks.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(0.92f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onPlayAll,
                                colors = ButtonDefaults.buttonColors(containerColor = FoxOrange),
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

                            if (tracks.size > 1) {
                                IconButton(
                                    onClick = { isReorderMode = !isReorderMode },
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isReorderMode) FoxOrange.copy(alpha = 0.25f) else Color(0x1AFFFFFF))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapVert,
                                        contentDescription = "Изменить порядок",
                                        tint = if (isReorderMode) FoxOrange else TextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        if (isReorderMode) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .liquidGlass(cornerRadius = 14.dp, backgroundColor = FoxOrange.copy(alpha = 0.12f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SwapVert, contentDescription = null, tint = FoxOrange, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Перемещение треков", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                TextButton(
                                    onClick = { isReorderMode = false },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("Готово", color = FoxOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Список треков
            if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Плейлист пуст",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Нажмите + на любом треке на Главной, чтобы добавить его сюда",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, entity ->
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
                    val isFav = favoriteTrackIds.contains(entity.id)
                    val progress = downloadProgressMap[entity.id]

                    TrackListItem(
                        track = domainTrack,
                        onTrackClick = { onTrackClick(domainTrack) },
                        isFavorite = isFav,
                        onFavoriteToggle = { onToggleFavorite(domainTrack) },
                        onAddToPlaylistClick = { onRemoveTrack(entity.id) },
                        onDownloadClick = { /* скачивание */ },
                        downloadProgress = progress,
                        isDownloaded = entity.isDownloaded,
                        showReorderHandle = isReorderMode,
                        onMoveUp = if (index > 0) { { onMoveTrack(index, index - 1) } } else null,
                        onMoveDown = if (index < tracks.lastIndex) { { onMoveTrack(index, index + 1) } } else null
                    )
                }
            }
        }

        // ДИАЛОГИ
        if (isRenameDialogOpen) {
            PlaylistInputDialog(
                title = "Переименовать",
                placeholder = "Новое название...",
                initialText = pl.name,
                confirmButtonText = "Сохранить",
                onDismiss = { isRenameDialogOpen = false },
                onConfirm = { newName ->
                    if (newName.isNotBlank()) {
                        onRenamePlaylist(newName.trim())
                    }
                    isRenameDialogOpen = false
                }
            )
        }

        if (isDeleteDialogOpen) {
            AlertDialog(
                onDismissRequest = { isDeleteDialogOpen = false },
                containerColor = Color(0xF2151720),
                title = { Text("Удалить «${pl.name}»?", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Плейлист будет удален. Треки, не входящие в другие плейлисты или «Любимое», будут удалены из памяти.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isDeleteDialogOpen = false
                            onDeletePlaylist()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Text("Удалить", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isDeleteDialogOpen = false }) {
                        Text("Отмена", color = TextSecondary)
                    }
                }
            )
        }
    }
}
