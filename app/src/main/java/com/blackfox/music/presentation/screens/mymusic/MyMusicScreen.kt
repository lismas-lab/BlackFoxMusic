package com.blackfox.music.presentation.screens.mymusic

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blackfox.music.data.local.PlaylistWithTracks
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.presentation.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MyMusicScreen(
    userPlaylists: List<PlaylistWithTracks>,
    favoriteTracks: List<TrackEntity>,
    onOpenFavorites: () -> Unit,
    onPlayFavorites: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onUpdatePlaylistCover: (Long, Uri?) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
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

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .liquidGlass(cornerRadius = 14.dp, backgroundColor = Color(0x22FFFFFF))
                        .clickable { isCreateDialogOpen = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Новый плейлист",
                            tint = FoxOrange,
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

            // Сетка с закрепленной карточкой "Любимое" и пользовательскими плейлистами
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
            ) {
                // Закрепленная карточка "Любимые треки" на всю ширину (2 колонки)
                item(span = { GridItemSpan(2) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(
                                cornerRadius = 22.dp,
                                backgroundColor = Color(0x1AFFFFFF),
                                borderBrush = Brush.linearGradient(
                                    listOf(FoxOrange.copy(alpha = 0.6f), Color.Transparent)
                                )
                            )
                            .clickable { onOpenFavorites() }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Огненное сердце в неоновом круге
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.radialGradient(
                                            listOf(FoxOrange.copy(alpha = 0.4f), Color(0x1AFFFFFF))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Любимые",
                                    tint = FoxOrange,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Любимые треки",
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${favoriteTracks.size} треков • Оффлайн",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }

                            if (favoriteTracks.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(FoxOrange)
                                        .clickable { onPlayFavorites() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Воспроизвести всё",
                                        tint = ObsidianBlack,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
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

                    // Обложка: своя кастомная -> либо обложка первой песни -> либо заглушка
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
                                                Icon(Icons.Default.Image, contentDescription = null, tint = FoxOrange, modifier = Modifier.size(18.dp))
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

        // ДИАЛОГ: СОЗДАНИЕ ПЛЕЙЛИСТА
        if (isCreateDialogOpen) {
            PlaylistInputDialog(
                title = "Новый плейлист",
                placeholder = "Название плейлиста...",
                initialText = "",
                confirmButtonText = "Создать",
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
 * Диалог ввода названия плейлиста в стиле Liquid Glass
 */
@Composable
fun PlaylistInputDialog(
    title: String,
    placeholder: String,
    initialText: String,
    confirmButtonText: String,
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
                        cursorBrush = SolidColor(FoxOrange),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FoxOrange)
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
