package com.blackfox.music.presentation.screens.mymusic

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackfox.music.data.local.MusicDao
import com.blackfox.music.data.local.PlaylistEntity
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.data.local.UserUploadedAudioImporter
import com.blackfox.music.data.repository.PlaylistRepository
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.components.AddToPlaylistBottomSheet
import com.blackfox.music.presentation.components.TrackListItem
import com.blackfox.music.presentation.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UploadedTracksScreen(
    uploadedTracks: List<TrackEntity>,
    favoriteTrackIds: Set<String>,
    musicDao: MusicDao,
    playlistRepository: PlaylistRepository,
    onBackClick: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    currentTrack: Track? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }
    var trackToDelete by remember { mutableStateOf<TrackEntity?>(null) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                isImporting = true
                try {
                    UserUploadedAudioImporter.importAudioUris(context, uris, musicDao)
                } finally {
                    isImporting = false
                }
            }
        }
    }

    val domainTracks = remember(uploadedTracks) {
        uploadedTracks.map { entity ->
            Track(
                id = entity.id,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                duration = entity.duration,
                thumbnailUrl = entity.thumbnailUrl,
                isDownloaded = true,
                localFilePath = entity.localFilePath,
                isUserUploaded = true
            )
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
                    // Кнопка назад и кнопка загрузки
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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

                        // Кнопка Добавить аудио
                        Button(
                            onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = FoxOrange),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = ObsidianBlack,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Загрузить", color = ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Большая неоновая иконка папки аудио
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(
                                Brush.radialGradient(
                                    listOf(FoxOrange.copy(alpha = 0.4f), Color(0x18FFFFFF))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Загруженные",
                            tint = FoxOrange,
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Загруженные треки",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${uploadedTracks.size} треков • Ваши локальные файлы",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    if (isImporting) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .liquidGlass(cornerRadius = 14.dp, backgroundColor = Color(0x18FFFFFF))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            CircularProgressIndicator(color = FoxOrange, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Импорт аудиозаписей...", color = TextPrimary, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Кнопки Слушать всё / Перемешать
                    if (uploadedTracks.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(0.88f)
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
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Список треков
            if (uploadedTracks.isEmpty() && !isImporting) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Загруженных аудиозаписей пока нет",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Нажмите «Загрузить» вверху, чтобы добавить свои аудиофайлы (.mp3, .flac, .wav, .m4a)",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            } else {
                items(domainTracks, key = { it.id }) { track ->
                    val isFav = favoriteTrackIds.contains(track.id)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            TrackListItem(
                                track = track,
                                onTrackClick = { onTrackClick(track, domainTracks) },
                                isFavorite = isFav,
                                onFavoriteToggle = { onToggleFavorite(track) },
                                onAddToPlaylistClick = { trackForPlaylist = track },
                                onDownloadClick = { /* уже локальный */ },
                                isDownloaded = true,
                                isActive = currentTrack?.id == track.id,
                                isPlaying = isPlaying && currentTrack?.id == track.id
                            )
                        }

                        // Кнопка удаления файла
                        IconButton(
                            onClick = {
                                val entity = uploadedTracks.find { it.id == track.id }
                                if (entity != null) trackToDelete = entity
                            },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Удалить",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Шторка быстрого добавления загруженного трека в пользовательские плейлисты
        trackForPlaylist?.let { track ->
            val containingPlaylistIds by playlistRepository.getPlaylistIdsForTrack(track.id).collectAsState(initial = emptyList())
            val allPlaylists by musicDao.getAllPlaylists().collectAsState(initial = emptyList())

            AddToPlaylistBottomSheet(
                track = track,
                playlists = allPlaylists,
                containingPlaylistIds = remember(containingPlaylistIds) { containingPlaylistIds.toSet() },
                onDismiss = { trackForPlaylist = null },
                onToggleTrackInPlaylist = { plId ->
                    coroutineScope.launch {
                        playlistRepository.toggleTrackInPlaylist(plId, track)
                    }
                },
                onCreatePlaylistAndAdd = { name ->
                    coroutineScope.launch {
                        playlistRepository.createPlaylistAndAddTrack(name, track)
                    }
                }
            )
        }

        // Диалог подтверждения удаления трека
        trackToDelete?.let { track ->
            AlertDialog(
                onDismissRequest = { trackToDelete = null },
                title = { Text("Удалить аудиозапись?", color = TextPrimary) },
                text = { Text("Трек «${track.title}» будет удален из памяти устройства.", color = TextSecondary) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                track.localFilePath?.let { File(it).delete() }
                                track.thumbnailUrl.takeIf { it.isNotBlank() }?.let { File(it).delete() }
                                musicDao.deleteTrack(track.id)
                                trackToDelete = null
                            }
                        }
                    ) {
                        Text("Удалить", color = FoxOrange, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { trackToDelete = null }) {
                        Text("Отмена", color = TextSecondary)
                    }
                },
                containerColor = ObsidianCard
            )
        }
    }
}