package com.blackfox.music.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackfox.music.presentation.theme.*

@Composable
fun SettingsScreen(
    downloadedTracksCount: Int,
    storageSizeBytes: Long,
    onClearAllDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isClearDialogOpen by remember { mutableStateOf(false) }

    val formattedSize = remember(storageSizeBytes) {
        val mb = storageSizeBytes.toDouble() / (1024 * 1024)
        if (mb >= 1024) {
            String.format("%.2f ГБ", mb / 1024)
        } else {
            String.format("%.1f МБ", mb)
        }
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
            Text(
                text = "Настройки",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
            ) {
                // Карточка приложения
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(cornerRadius = 22.dp, backgroundColor = Color(0x16FFFFFF))
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(FoxOrange.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = FoxOrange,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(
                                    text = "Black Fox Music",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Версия 0.1.0 • Liquid Glass",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Раздел Хранилище и Оффлайн
                item {
                    SettingsSection(title = "ОФФЛАЙН И ХРАНИЛИЩЕ") {
                        SettingsItem(
                            icon = Icons.Default.CloudDownload,
                            title = "Автоскачивание музыки",
                            subtitle = "Любимые треки и плейлисты сохраняются автоматически",
                            trailing = {
                                Text(
                                    text = "Активно",
                                    color = FoxOrange,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        SettingsItem(
                            icon = Icons.Default.Folder,
                            title = "Занятое место",
                            subtitle = "$downloadedTracksCount треков на устройстве",
                            trailing = {
                                Text(
                                    text = formattedSize,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        SettingsItem(
                            icon = Icons.Default.DeleteOutline,
                            title = "Очистить скачанную музыку",
                            subtitle = "Удалить все сохраненные аудиофайлы",
                            iconTint = Color(0xFFFF5252),
                            onClick = { isClearDialogOpen = true }
                        )
                    }
                }

                // Раздел Воспроизведение
                item {
                    SettingsSection(title = "ВОСПРОИЗВЕДЕНИЕ") {
                        SettingsItem(
                            icon = Icons.Default.GraphicEq,
                            title = "Аудиодвижок",
                            subtitle = "AndroidX Media3 (ExoPlayer 1.5.1)",
                            trailing = {
                                Text("320 kbps", color = TextSecondary, fontSize = 13.sp)
                            }
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        SettingsItem(
                            icon = Icons.Default.PlayCircleOutline,
                            title = "Фоновый режим",
                            subtitle = "Воспроизведение при выключенном экране и в шторке",
                            trailing = {
                                Text("Включен", color = FoxOrange, fontSize = 13.sp)
                            }
                        )
                    }
                }

                // Раздел Внешний вид
                item {
                    SettingsSection(title = "ДИЗАЙН И СТИЛЬ") {
                        SettingsItem(
                            icon = Icons.Default.Palette,
                            title = "Тема интерфейса",
                            subtitle = "Liquid Glass & Obsidian Black (#08090C)"
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        SettingsItem(
                            icon = Icons.Default.ColorLens,
                            title = "Акцентный цвет",
                            subtitle = "Лисий оранжевый (Fox Orange #FF5E1E)",
                            trailing = {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(FoxOrange)
                                )
                            }
                        )
                    }
                }

                // Раздел О приложении
                item {
                    SettingsSection(title = "О ПРИЛОЖЕНИИ") {
                        SettingsItem(
                            icon = Icons.Default.Info,
                            title = "Источник каталога",
                            subtitle = "YouTube Music InnerTube API (без рекламы)"
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        SettingsItem(
                            icon = Icons.Default.Code,
                            title = "Лицензия",
                            subtitle = "MIT License • Black Fox Lab"
                        )
                    }
                }
            }
        }

        // Диалог подтверждения очистки кэша
        if (isClearDialogOpen) {
            AlertDialog(
                onDismissRequest = { isClearDialogOpen = false },
                containerColor = Color(0xF2151720),
                title = { Text("Очистить скачанную музыку?", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Все скачанные треки будут удалены из памяти устройства ($formattedSize). Сами плейлисты и список любимого сохранятся.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isClearDialogOpen = false
                            onClearAllDownloads()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Text("Очистить", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isClearDialogOpen = false }) {
                        Text("Отмена", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            color = FoxOrange,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(cornerRadius = 20.dp, backgroundColor = Color(0x12FFFFFF))
                .padding(vertical = 4.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = FoxOrange,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}
