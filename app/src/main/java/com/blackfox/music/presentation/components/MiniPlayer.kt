package com.blackfox.music.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blackfox.music.data.local.LocalAccentTheme
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.theme.*

/**
 * Плавающий мини-плеер в стиле Liquid Glass
 */
@Composable
fun FloatingMiniPlayer(
    currentTrack: Track,
    isPlaying: Boolean,
    progressFraction: Float, // от 0.0 до 1.0
    onPlayPauseClick: () -> Unit,
    onPlayerClick: () -> Unit,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalAccentTheme.current.primary
    val heartColor by animateColorAsState(
        targetValue = if (isFavorite) accentColor else TextMuted,
        label = "HeartColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .height(68.dp)
            .floatingGlass(cornerRadius = 22.dp)
            .clickable(onClick = onPlayerClick)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Круглая или скругленная обложка трека
                AsyncImage(
                    model = currentTrack.thumbnailUrl,
                    contentDescription = currentTrack.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianCard)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Название трека и артист
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentTrack.artist,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Кнопка Play / Pause с лисьим свечением
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = accentColor.copy(alpha = 0.35f), spotColor = accentColor)
                        .clip(CircleShape)
                        .background(accentColor)
                        .clickable(onClick = onPlayPauseClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Играть",
                        tint = ObsidianBlack,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Кнопка Избранное (Сердечко)
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Избранное",
                        tint = heartColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Тонкая полоска прогресса трека на нижней грани
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progressFraction.coerceIn(0f, 1f))
                        .background(accentColor)
                )
            }
        }
    }
}
