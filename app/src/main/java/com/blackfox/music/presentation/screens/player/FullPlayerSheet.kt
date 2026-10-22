package com.blackfox.music.presentation.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blackfox.music.data.download.DownloadProgress
import com.blackfox.music.data.download.DownloadStatus
import com.blackfox.music.data.local.LocalAccentTheme
import com.blackfox.music.data.network.InnerTubeRepository
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Полноэкранный плеер в стиле Liquid Glass с текстами песен и переходом к артисту
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    track: Track,
    isPlaying: Boolean,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isFavorite: Boolean,
    isDownloaded: Boolean = false,
    downloadProgress: DownloadProgress? = null,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onFavoriteToggle: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onCloseClick: () -> Unit,
    onArtistClick: (artistName: String, artistId: String?) -> Unit = { _, _ -> },
    onAlbumClick: (albumTitle: String, albumId: String?) -> Unit = { _, _ -> },
    onSwitchToTrack: (Track) -> Unit = {},
    repository: InnerTubeRepository = remember { InnerTubeRepository() },
    modifier: Modifier = Modifier
) {
    val accentColor = LocalAccentTheme.current.primary
    var isLyricsMode by remember { mutableStateOf(false) }
    var lyricsPair by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isLyricsLoading by remember { mutableStateOf(false) }
    var lyricsLoadedTrackId by remember { mutableStateOf<String?>(null) }

    var isUncensoredSheetVisible by remember { mutableStateOf(false) }
    var uncensoredVideos by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isUncensoredLoading by remember { mutableStateOf(false) }

    var resolvedAlbumId by remember(track.id) { mutableStateOf(track.albumId) }
    var resolvedAlbumTitle by remember(track.id) { mutableStateOf(track.album) }
    var isResolvingAlbum by remember(track.id) { mutableStateOf(false) }

    LaunchedEffect(isUncensoredSheetVisible, track.id) {
        if (isUncensoredSheetVisible) {
            isUncensoredLoading = true
            try {
                val query = "${track.artist} ${track.title}"
                val results = repository.searchVideos(query, limit = 10)
                uncensoredVideos = results
            } catch (_: Exception) {
                uncensoredVideos = emptyList()
            } finally {
                isUncensoredLoading = false
            }
        }
    }

    LaunchedEffect(track.id) {
        resolvedAlbumId = track.albumId
        resolvedAlbumTitle = track.album
        if (resolvedAlbumId == null) {
            val query = if (!track.album.isNullOrBlank()) {
                "${track.artist} ${track.album}"
            } else if (track.artist.isNotBlank() && track.title.isNotBlank()) {
                "${track.artist} ${track.title}"
            } else null

            if (query != null) {
                isResolvingAlbum = true
                try {
                    val albums = repository.searchAlbums(query, limit = 1)
                    if (albums.isNotEmpty()) {
                        val first = albums.first()
                        resolvedAlbumId = first.browseId
                        if (resolvedAlbumTitle.isNullOrBlank()) {
                            resolvedAlbumTitle = first.title
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    isResolvingAlbum = false
                }
            }
        }
    }

    LaunchedEffect(track.id, isLyricsMode) {
        if (isLyricsMode && lyricsLoadedTrackId != track.id) {
            isLyricsLoading = true
            lyricsPair = null
            try {
                lyricsPair = repository.getLyrics(track.id)
                lyricsLoadedTrackId = track.id
            } catch (e: Exception) {
                lyricsPair = null
            } finally {
                isLyricsLoading = false
            }
        }
    }

    val effectiveTotalDurationMs = remember(totalDurationMs, track.duration, track.durationSeconds) {
        when {
            totalDurationMs > 0L -> totalDurationMs
            track.durationSeconds > 0L -> track.durationSeconds * 1000L
            track.duration.isNotBlank() -> {
                try {
                    val parts = track.duration.split(":").map { it.trim().toInt() }
                    when (parts.size) {
                        2 -> ((parts[0] * 60) + parts[1]) * 1000L
                        3 -> ((parts[0] * 3600) + (parts[1] * 60) + parts[2]) * 1000L
                        else -> 0L
                    }
                } catch (_: Exception) {
                    0L
                }
            }
            else -> 0L
        }
    }

    val progressFraction = if (effectiveTotalDurationMs > 0) {
        (currentPositionMs.toFloat() / effectiveTotalDurationMs).coerceIn(0f, 1f)
    } else 0f

    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%d:%02d", min, sec)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Верхняя панель (Свернуть плеер, переключатель ТРЕК / ТЕКСТ, В плейлист)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCloseClick) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Свернуть",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Переключатель ТРЕК / ТЕКСТ
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x18FFFFFF))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (!isLyricsMode) accentColor else Color.Transparent)
                            .clickable { isLyricsMode = false }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "ТРЕК",
                            color = if (!isLyricsMode) ObsidianBlack else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isLyricsMode) accentColor else Color.Transparent)
                            .clickable { isLyricsMode = true }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "ТЕКСТ",
                            color = if (isLyricsMode) ObsidianBlack else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                IconButton(onClick = onAddToPlaylistClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                        contentDescription = "В плейлист",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Центральная область: Обложка альбома или Текст песни
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                if (!isLyricsMode) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(32.dp))
                            .liquidGlass(cornerRadius = 32.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(32.dp))
                            .liquidGlass(cornerRadius = 32.dp, backgroundColor = Color(0x221C1D22))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLyricsLoading) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = accentColor, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Загрузка текста песни...", color = TextSecondary, fontSize = 13.sp)
                            }
                        } else if (lyricsPair != null) {
                            val (lyricsContent, lyricsSource) = lyricsPair!!
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = lyricsContent,
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 28.sp
                                )
                                if (lyricsSource.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = lyricsSource,
                                        color = TextMuted,
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Lyrics,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Текст для этого трека пока не найден",
                                    color = TextSecondary,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Название трека и кликабельный артист
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onCloseClick()
                                onArtistClick(track.artist, track.artistId)
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = track.artist,
                            color = accentColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "К артисту",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Кликабельный альбом (переход к альбому из карточки трека)
                    val albumToShow = resolvedAlbumTitle?.takeIf { it.isNotBlank() } ?: track.album?.takeIf { it.isNotBlank() }
                    if (albumToShow != null || resolvedAlbumId != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onCloseClick()
                                    onAlbumClick(albumToShow ?: "", resolvedAlbumId)
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = "К альбому",
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = albumToShow ?: "Альбом",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Кнопка поиска версии без цензуры (Видеоклип / Официальное видео)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x18FFFFFF))
                            .clickable { isUncensoredSheetVisible = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Без цензуры (Видео)",
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Версия без цензуры (Видео)",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Кнопка Лайк
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Избранное",
                        tint = if (isFavorite) accentColor else TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Полоса перемотки (Seek bar) с плавной прокруткой
            var isUserSeeking by remember { mutableStateOf(false) }
            var userSeekFraction by remember { mutableStateOf(0f) }

            val displayFraction = if (isUserSeeking) userSeekFraction else progressFraction

            Slider(
                value = displayFraction,
                onValueChange = { frac ->
                    isUserSeeking = true
                    userSeekFraction = frac
                },
                onValueChangeFinished = {
                    if (effectiveTotalDurationMs > 0L) {
                        onSeekTo((userSeekFraction * effectiveTotalDurationMs).toLong())
                    }
                    isUserSeeking = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Тайминги (Прошло / Осталось)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPositionMs),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = formatTime(if (totalDurationMs > 0) totalDurationMs else effectiveTotalDurationMs),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Кнопки управления воспроизведением
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Скачать для оффлайна с круговым индикатором прогресса и статусом
                val isDownloading = downloadProgress?.status == DownloadStatus.DOWNLOADING
                val isCompleted = isDownloaded || track.isDownloaded || downloadProgress?.status == DownloadStatus.COMPLETED

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = !isDownloading && !isCompleted,
                            onClick = onDownloadClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDownloading) {
                        val percent = downloadProgress?.progressPercent ?: 0
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { if (percent > 0) percent / 100f else 0.15f },
                                color = accentColor,
                                trackColor = ObsidianCard,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(32.dp)
                            )
                            if (percent > 0) {
                                Text(
                                    text = "$percent%",
                                    color = accentColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Загрузка",
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Скачано",
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Скачать",
                            tint = TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Предыдущий трек
                IconButton(onClick = onPreviousClick) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Назад",
                        tint = TextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Большая центральная кнопка Play / Pause с неоновым свечением
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            ambientColor = accentColor.copy(alpha = 0.4f),
                            spotColor = accentColor
                        )
                        .clip(CircleShape)
                        .background(accentColor)
                        .clickable(onClick = onPlayPauseClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Играть",
                        tint = ObsidianBlack,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Следующий трек
                IconButton(onClick = onNextClick) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Вперед",
                        tint = TextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Переключение Текста песни
                IconButton(onClick = { isLyricsMode = !isLyricsMode }) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = "Текст песни",
                        tint = if (isLyricsMode) accentColor else TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Шторка выбора версии без цензуры (Видеоклипы / Официальные премьеры)
        if (isUncensoredSheetVisible) {
            ModalBottomSheet(
                onDismissRequest = { isUncensoredSheetVisible = false },
                containerColor = Color(0xF20F1117),
                scrimColor = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                        .navigationBarsPadding()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Версии без цензуры (Видео)",
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Официальные клипы и премьеры с чистым звуком",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isUncensoredLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = accentColor, modifier = Modifier.size(36.dp))
                        }
                    } else if (uncensoredVideos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Видеоверсий для этого трека не найдено",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uncensoredVideos, key = { it.id }) { video ->
                                val isCurrent = video.id == track.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isCurrent) accentColor.copy(alpha = 0.15f) else Color(0x12FFFFFF))
                                        .clickable {
                                            isUncensoredSheetVisible = false
                                            onSwitchToTrack(video)
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Обложка видео
                                    Box(
                                        modifier = Modifier
                                            .size(width = 72.dp, height = 48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ObsidianCard)
                                    ) {
                                        AsyncImage(
                                            model = video.thumbnailUrl,
                                            contentDescription = video.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (video.duration.isNotBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .background(Color.Black.copy(alpha = 0.75f))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = video.duration,
                                                    color = Color.White,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = video.title,
                                            color = if (isCurrent) accentColor else TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = video.artist,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    IconButton(
                                        onClick = {
                                            isUncensoredSheetVisible = false
                                            onSwitchToTrack(video)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Включить эту версию",
                                            tint = accentColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
