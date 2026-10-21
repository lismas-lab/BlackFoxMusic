package com.blackfox.music.presentation.screens.mymusic

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blackfox.music.data.download.DownloadProgress
import com.blackfox.music.data.local.LocalAccentTheme
import com.blackfox.music.data.local.PlaylistWithTracks
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.data.local.toDomain
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.components.TrackListItem
import com.blackfox.music.presentation.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MyMusicScreen(
    userPlaylists: List<PlaylistWithTracks>,
    favoriteTracks: List<TrackEntity>,
    allLibraryTracks: List<TrackEntity> = emptyList(),
    uploadedTracksCount: Int = 0,
    downloadedTracksCount: Int = 0,
    followedArtistsCount: Int = 0,
    downloadProgressMap: Map<String, DownloadProgress> = emptyMap(),
    onOpenFavorites: () -> Unit,
    onPlayFavorites: () -> Unit,
    onOpenDownloaded: () -> Unit = {},
    onPlayDownloaded: () -> Unit = {},
    onOpenUploaded: () -> Unit = {},
    onPlayUploaded: () -> Unit = {},
    onOpenFollowedArtists: () -> Unit = {},
    onOpenPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onUpdatePlaylistCover: (Long, Uri?) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit = { _, _ -> },
    onToggleFavorite: (Track) -> Unit = {},
    onAddToPlaylist: (Track) -> Unit = {},
    onDownloadTrack: (Track) -> Unit = {},
    currentTrack: Track? = null,
    isPlaying: Boolean = false,
    onOpenMigration: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val accentColor = LocalAccentTheme.current.primary

    var searchQuery by remember { mutableStateOf("") }
    var isCreateDialogOpen by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<PlaylistWithTracks?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistWithTracks?>(null) }

    // Лаунчер для выбора кастомной обложки из галереи
    var targetPlaylistForCover by remember { mutableStateOf<Long?>(null) }
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        targetPlaylistForCover?.let { plId ->
            onUpdatePlaylistCover(plId, uri)
        }
        targetPlaylistForCover = null
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
            // Верхняя панель: Заголовок и кнопка создания
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Моя музыка",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Кнопка миграции из VK
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .liquidGlass(cornerRadius = 14.dp, backgroundColor = Color(0x22FFFFFF))
                            .clickable { onOpenMigration() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Миграция из VK",
                                tint = accentColor,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "Импорт VK",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Кнопка нового плейлиста
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .liquidGlass(cornerRadius = 14.dp, backgroundColor = Color(0x22FFFFFF))
                            .clickable { isCreateDialogOpen = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Новый плейлист",
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Плейлист",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Поисковая строка по трекам своей медиатеки
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .height(48.dp)
                    .liquidGlass(cornerRadius = 16.dp, backgroundColor = Color(0x16FFFFFF))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (searchQuery.isNotEmpty()) accentColor else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Поиск по своей медиатеке...",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(accentColor),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Очистить",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Если пользователь выполняет поиск по медиатеке
            if (searchQuery.isNotBlank()) {
                val filteredTracks = remember(searchQuery, allLibraryTracks) {
                    val q = searchQuery.trim().lowercase()
                    allLibraryTracks.filter { track ->
                        track.title.lowercase().contains(q) ||
                                track.artist.lowercase().contains(q) ||
                                (track.album?.lowercase()?.contains(q) == true)
                    }
                }
                val favoriteIds = remember(favoriteTracks) { favoriteTracks.map { it.id }.toSet() }

                if (filteredTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Ничего не найдено в медиатеке",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "По запросу «$searchQuery» треков не найдено",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            Text(
                                text = "Найдено треков: ${filteredTracks.size}",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                            )
                        }
                        val domainQueue = filteredTracks.map { it.toDomain() }
                        items(domainQueue, key = { it.id }) { track ->
                            TrackListItem(
                                track = track,
                                onTrackClick = { onPlayTrack(track, domainQueue) },
                                isFavorite = favoriteIds.contains(track.id),
                                onFavoriteToggle = { onToggleFavorite(track) },
                                onAddToPlaylistClick = { onAddToPlaylist(track) },
                                onDownloadClick = { onDownloadTrack(track) },
                                downloadProgress = downloadProgressMap[track.id],
                                isActive = currentTrack?.id == track.id,
                                isPlaying = isPlaying && currentTrack?.id == track.id
                            )
                        }
                    }
                }
            } else {
                // Обычный режим: Квадратная сетка 2x2 плашек + Плейлисты
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
                ) {
                    // 1. Любимые треки (Квадратная плашка 1x1)
                    item {
                        QuickLibraryTile(
                            title = "Любимые",
                            subtitle = "${favoriteTracks.size} треков",
                            icon = Icons.Default.Favorite,
                            accentColor = accentColor,
                            onClick = onOpenFavorites,
                            onPlay = onPlayFavorites,
                            canPlay = favoriteTracks.isNotEmpty()
                        )
                    }

                    // 2. Скачанные треки (Квадратная плашка 1x1)
                    item {
                        QuickLibraryTile(
                            title = "Скачанные",
                            subtitle = "$downloadedTracksCount треков",
                            icon = Icons.Default.DownloadDone,
                            accentColor = accentColor,
                            onClick = onOpenDownloaded,
                            onPlay = onPlayDownloaded,
                            canPlay = downloadedTracksCount > 0
                        )
                    }

                    // 3. Загруженные треки (Квадратная плашка 1x1)
                    item {
                        QuickLibraryTile(
                            title = "Загруженные",
                            subtitle = "$uploadedTracksCount треков",
                            icon = Icons.Default.FolderOpen,
                            accentColor = accentColor,
                            onClick = onOpenUploaded,
                            onPlay = onPlayUploaded,
                            canPlay = uploadedTracksCount > 0
                        )
                    }

                    // 4. Мои артисты (Квадратная плашка 1x1)
                    item {
                        QuickLibraryTile(
                            title = "Мои артисты",
                            subtitle = "$followedArtistsCount подписок",
                            icon = Icons.Default.Person,
                            accentColor = accentColor,
                            onClick = onOpenFollowedArtists,
                            onPlay = null,
                            canPlay = false
                        )
                    }

                    // Заголовок секции "Плейлисты"
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            text = "Плейлисты (${userPlaylists.size})",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }

                    // Если плейлистов пока нет
                    if (userPlaylists.isEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlass(cornerRadius = 18.dp, backgroundColor = Color(0x0EFFFFFF))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Плейлистов пока нет",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Создайте плейлист, чтобы собирать треки по настроению и жанрам",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Карточки плейлистов
                    items(userPlaylists, key = { it.playlist.playlistId }) { playlistWithTracks ->
                        val pl = playlistWithTracks.playlist
                        val tracks = playlistWithTracks.tracks

                        val coverModel = remember(pl.customCoverUri, tracks) {
                            when {
                                !pl.customCoverUri.isNullOrBlank() -> File(pl.customCoverUri)
                                tracks.isNotEmpty() -> tracks.first().thumbnailUrl.ifBlank { null }
                                else -> null
                            }
                        }

                        var isMenuExpanded by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlass(cornerRadius = 18.dp, backgroundColor = Color(0x12FFFFFF))
                                .clickable { onOpenPlaylist(pl.playlistId) }
                                .padding(10.dp)
                        ) {
                            Column {
                                // Квадратная обложка
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0x1AFFFFFF)),
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
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }

                                    // Кнопка меню "..." поверх обложки в правом верхнем углу
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { isMenuExpanded = true },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.5f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Действия",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
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
                                                    Icon(Icons.Default.Image, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                                },
                                                onClick = {
                                                    isMenuExpanded = false
                                                    targetPlaylistForCover = pl.playlistId
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
                                                        onUpdatePlaylistCover(pl.playlistId, null)
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
                                                    playlistToRename = playlistWithTracks
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
                                                    playlistToDelete = playlistWithTracks
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Название плейлиста
                                Text(
                                    text = pl.name,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Количество треков
                                Text(
                                    text = "${tracks.size} треков",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // ДИАЛОГ: СОЗДАНИЕ ПЛЕЙЛИСТА
        if (isCreateDialogOpen) {
            PlaylistInputDialog(
                title = "Новый плейлист",
                placeholder = "Название плейлиста...",
                initialText = "",
                confirmButtonText = "Создать",
                accentColor = accentColor,
                onDismiss = { isCreateDialogOpen = false },
                onConfirm = { name ->
                    if (name.isNotBlank()) {
                        onCreatePlaylist(name.trim())
                    }
                    isCreateDialogOpen = false
                }
            )
        }

        // ДИАЛОГ: ПЕРЕИМЕНОВАНИЕ ПЛЕЙЛИСТА
        playlistToRename?.let { target ->
            PlaylistInputDialog(
                title = "Переименовать",
                placeholder = "Новое название...",
                initialText = target.playlist.name,
                confirmButtonText = "Сохранить",
                accentColor = accentColor,
                onDismiss = { playlistToRename = null },
                onConfirm = { newName ->
                    if (newName.isNotBlank()) {
                        onRenamePlaylist(target.playlist.playlistId, newName.trim())
                    }
                    playlistToRename = null
                }
            )
        }

        // ДИАЛОГ: ПОДТВЕРЖДЕНИЕ УДАЛЕНИЯ ПЛЕЙЛИСТА
        playlistToDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { playlistToDelete = null },
                containerColor = Color(0xF2151720),
                title = {
                    Text(
                        text = "Удалить «${target.playlist.name}»?",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Плейлист будет удален. Треки из этого плейлиста, не используемые в других плейлистах или в «Любимом», будут удалены из памяти устройства.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeletePlaylist(target.playlist.playlistId)
                            playlistToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Text("Удалить", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { playlistToDelete = null }) {
                        Text("Отмена", color = TextSecondary)
                    }
                }
            )
        }
    }
}

/**
 * Компактная квадратная плашка для медиатеки (2х2 на сетке)
 */
@Composable
private fun QuickLibraryTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null,
    canPlay: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp)
            .liquidGlass(
                cornerRadius = 20.dp,
                backgroundColor = Color(0x18FFFFFF),
                borderBrush = Brush.linearGradient(
                    listOf(accentColor.copy(alpha = 0.45f), Color.Transparent)
                )
            )
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(accentColor.copy(alpha = 0.35f), Color(0x14FFFFFF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (onPlay != null && canPlay) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                            .clickable { onPlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Воспроизвести всё",
                            tint = ObsidianBlack,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Диалог ввода названия плейлиста в стиле Liquid Glass
 */
@Composable
fun PlaylistInputDialog(
    title: String,
    placeholder: String,
    initialText: String,
    confirmButtonText: String,
    accentColor: Color = LocalAccentTheme.current.primary,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF2151720),
        title = {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .liquidGlass(cornerRadius = 14.dp, backgroundColor = Color(0x1AFFFFFF))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(accentColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(confirmButtonText, color = ObsidianBlack, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = TextSecondary)
            }
        }
    )
}
