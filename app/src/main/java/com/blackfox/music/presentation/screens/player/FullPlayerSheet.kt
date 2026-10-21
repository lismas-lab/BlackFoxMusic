package com.blackfox.music.presentation.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlaylistAdd
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
import com.blackfox.music.data.network.InnerTubeRepository
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.theme.*

/**
 * Полноэкранный плеер в стиле Liquid Glass с текстами песен и переходом к артисту
 */
@Composable
fun FullPlayerSheet(
    track: Track,
    isPlaying: Boolean,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isFavorite: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onFavoriteToggle: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onCloseClick: () -> Unit,
    onArtistClick: (artistName: String, artistId: String?) -> Unit = { _, _ -> },
    repository: InnerTubeRepository = remember { InnerTubeRepository() },
    modifier: Modifier = Modifier
) {
    var isLyricsMode by remember { mutableStateOf(false) }
    var lyricsPair by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isLyricsLoading by remember { mutableStateOf(false) }
    var lyricsLoadedTrackId by remember { mutableStateOf<String?>(null) }

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

    val progressFraction = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
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
                            .background(if (!isLyricsMode) FoxOrange else Color.Transparent)
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
                            .background(if (isLyricsMode) FoxOrange else Color.Transparent)
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
                        imageVector = Icons.Outlined.PlaylistAdd,
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
                                CircularProgressIndicator(color = FoxOrange, modifier = Modifier.size(36.dp))
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
                            color = FoxOrange,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "К артисту",
                            tint = FoxOrange,
                            modifier = Modifier.size(16.dp)
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
                        tint = if (isFavorite) FoxOrange else TextSecondary,
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
                    onSeekTo((userSeekFraction * totalDurationMs).toLong())
                    isUserSeeking = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = FoxOrange,
                    activeTrackColor = FoxOrange,
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
                    text = formatTime(totalDurationMs),
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
                // Скачать для оффлайна
                IconButton(onClick = onDownloadClick) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Скачать",
                        tint = TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
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
                            ambientColor = FoxOrangeGlow,
                            spotColor = FoxOrange
                        )
                        .clip(CircleShape)
                        .background(FoxOrange)
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
                        tint = if (isLyricsMode) FoxOrange else TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
