package com.blackfox.music.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackfox.music.data.local.PlaylistEntity
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.theme.*

/**
 * Шторка быстрого добавления в плейлисты в стиле Liquid Glass
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistBottomSheet(
    track: Track,
    playlists: List<PlaylistEntity>,
    containingPlaylistIds: Set<Long>,
    onDismiss: () -> Unit,
    onToggleTrackInPlaylist: (playlistId: Long) -> Unit,
    onCreatePlaylistAndAdd: (name: String) -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }
    val userPlaylists = remember(playlists) { playlists.filter { !it.isFavoriteSystem } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF20F1117), // Матовое стекло
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
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
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Добавить в плейлист",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onDismiss) {
                    Text("Готово", color = FoxOrange, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${track.title} • ${track.artist}",
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Поле создания нового плейлиста "на лету"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .liquidGlass(cornerRadius = 16.dp, backgroundColor = Color(0x1AFFFFFF))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (newPlaylistName.isEmpty()) {
                        Text(
                            text = "+ Создать новый плейлист...",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
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

                if (newPlaylistName.isNotBlank()) {
                    IconButton(
                        onClick = {
                            onCreatePlaylistAndAdd(newPlaylistName.trim())
                            newPlaylistName = ""
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Создать",
                            tint = FoxOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Список существующих плейлистов
            if (userPlaylists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "У вас пока нет плейлистов.\nВведите имя выше и нажмите +, чтобы создать!",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(userPlaylists, key = { it.playlistId }) { pl ->
                        val contains = containingPlaylistIds.contains(pl.playlistId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlass(
                                    cornerRadius = 14.dp,
                                    backgroundColor = if (contains) Color(0x28FFFFFF) else Color(0x0EFFFFFF),
                                    borderColor = if (contains) FoxOrange.copy(alpha = 0.5f) else GlassBorder
                                )
                                .clickable { onToggleTrackInPlaylist(pl.playlistId) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = pl.name,
                                color = if (contains) FoxOrange else TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (contains) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )

                            // Чекбокс индикатор
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (contains) FoxOrange else Color.Transparent)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (contains) FoxOrange else Color.White.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(7.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (contains) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Добавлено",
                                        tint = ObsidianBlack,
                                        modifier = Modifier.size(16.dp)
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
