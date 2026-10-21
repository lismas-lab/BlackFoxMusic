package com.blackfox.music.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blackfox.music.data.download.DownloadProgress
import com.blackfox.music.data.download.DownloadStatus
import com.blackfox.music.domain.model.Album
import com.blackfox.music.domain.model.Artist
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.theme.*

/**
 * Стеклянная поисковая строка в стиле Liquid Glass с поддержкой подсказок
 */
@Composable
fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Поиск песен, альбомов, артистов...",
    suggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit = {},
    onSearchSubmitted: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    var isSuggestionsDismissed by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .liquidGlass(cornerRadius = 22.dp, backgroundColor = Color(0x18FFFFFF))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Поиск",
                    tint = if (query.isNotBlank()) FoxOrange else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = TextMuted,
                            fontSize = 15.sp
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            isSuggestionsDismissed = false
                            onQueryChange(it)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                isSuggestionsDismissed = true
                                focusManager.clearFocus()
                                onSearchSubmitted?.invoke()
                            }
                        ),
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(FoxOrange),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            isSuggestionsDismissed = true
                            onQueryChange("")
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Очистить",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Всплывающее меню поисковых подсказок API (исчезает при подтверждении ввода или клике)
        if (!isSuggestionsDismissed && suggestions.isNotEmpty() && query.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(cornerRadius = 18.dp, backgroundColor = Color(0x351C1D22))
                    .padding(vertical = 4.dp)
            ) {
                suggestions.take(5).forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                isSuggestionsDismissed = true
                                focusManager.clearFocus()
                                onSuggestionClick(suggestion)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = FoxOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = suggestion,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.NorthWest,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Карточка трека в списке
 */
@Composable
fun TrackListItem(
    track: Track,
    onTrackClick: () -> Unit,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onDownloadClick: () -> Unit,
    downloadProgress: DownloadProgress? = null,
    isDownloaded: Boolean = track.isDownloaded,
    onArtistClick: ((artistName: String, artistId: String?) -> Unit)? = null,
    showReorderHandle: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val heartColor by animateColorAsState(
        targetValue = if (isFavorite) FoxOrange else TextMuted,
        label = "HeartColor"
    )

    val isDownloading = downloadProgress?.status == DownloadStatus.DOWNLOADING
    val isCompleted = isDownloaded || track.isDownloaded || downloadProgress?.status == DownloadStatus.COMPLETED

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Кнопка-манипулятор с тремя полосками слева от обложки для изменения порядка
        if (showReorderHandle) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Перетащить",
                tint = FoxOrange.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(26.dp)
                    .padding(end = 6.dp)
            )
        }

        // Кликабельная зона информации о треке (обложка + название + артист)
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onTrackClick
                )
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Квадратная обложка трека
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianCard)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Название, Артист, Длительность
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "${track.artist}${if (track.duration.isNotBlank()) " • ${track.duration}" else ""}",
                    color = if (onArtistClick != null) FoxOrange.copy(alpha = 0.9f) else TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onArtistClick != null) {
                        Modifier.clickable { onArtistClick(track.artist, track.artistId) }
                    } else Modifier
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Кнопки действий: при изменении порядка показываются стрелки перемещения, иначе действия
        if (showReorderHandle && (onMoveUp != null || onMoveDown != null)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = { onMoveUp?.invoke() },
                    enabled = onMoveUp != null,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Вверх",
                        tint = if (onMoveUp != null) FoxOrange else TextMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = { onMoveDown?.invoke() },
                    enabled = onMoveDown != null,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Вниз",
                        tint = if (onMoveDown != null) FoxOrange else TextMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Скачать для оффлайна с круговым индикатором прогресса
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = !isDownloading,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = 19.dp),
                            onClick = onDownloadClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDownloading) {
                        val percent = downloadProgress?.progressPercent ?: 0
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { if (percent > 0) percent / 100f else 0.15f },
                                color = FoxOrange,
                                trackColor = ObsidianCard,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(26.dp)
                            )
                            if (percent > 0) {
                                Text(
                                    text = "$percent%",
                                    color = FoxOrange,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Загрузка",
                                    tint = FoxOrange,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    } else if (isCompleted) {
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
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 1-Click Лайк (Избранное)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = 19.dp),
                            onClick = onFavoriteToggle
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "В избранное",
                        tint = heartColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Кнопка быстрого добавления в плейлист
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = 19.dp),
                            onClick = onAddToPlaylistClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                        contentDescription = "В плейлист",
                        tint = TextSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

/**
 * Стеклянная карточка альбома в сетке
 */
@Composable
fun AlbumGridItem(
    album: Album,
    onAlbumClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(160.dp)
            .liquidGlass(cornerRadius = 18.dp)
            .clickable(onClick = onAlbumClick)
            .padding(10.dp)
    ) {
        // Обложка высокого разрешения
        AsyncImage(
            model = album.coverUrl,
            contentDescription = album.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianCard)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = album.title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "${album.artist}${if (album.year.isNotBlank()) " • ${album.year}" else ""}",
            color = TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Круглая карточка артиста для горизонтальной ленты в разделе "Все"
 */
@Composable
fun ArtistGridItem(
    artist: Artist,
    onArtistClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(105.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onArtistClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(ObsidianCard),
            contentAlignment = Alignment.Center
        ) {
            if (artist.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = artist.avatarUrl,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = artist.name,
                    tint = FoxOrange,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = if (artist.subscribers.isNotBlank()) artist.subscribers else "Исполнитель",
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Карточка артиста для вертикального списка при фильтре "Артисты"
 */
@Composable
fun ArtistListItem(
    artist: Artist,
    onArtistClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(cornerRadius = 16.dp, backgroundColor = Color(0x12FFFFFF))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onArtistClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ObsidianCard),
            contentAlignment = Alignment.Center
        ) {
            if (artist.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = artist.avatarUrl,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = artist.name,
                    tint = FoxOrange,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = if (artist.subscribers.isNotBlank()) "${artist.subscribers} • Исполнитель" else "Исполнитель",
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Перейти к артисту",
            tint = TextMuted,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Карточка альбома для вертикального списка при фильтре "Альбомы"
 */
@Composable
fun AlbumListItem(
    album: Album,
    onAlbumClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(cornerRadius = 16.dp, backgroundColor = Color(0x12FFFFFF))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onAlbumClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = album.coverUrl,
            contentDescription = album.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianCard)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${album.artist}${if (album.year.isNotBlank()) " • ${album.year}" else ""}${if (album.type.isNotBlank()) " • ${album.type}" else ""}",
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Открыть альбом",
            tint = TextMuted,
            modifier = Modifier.size(24.dp)
        )
    }
}
