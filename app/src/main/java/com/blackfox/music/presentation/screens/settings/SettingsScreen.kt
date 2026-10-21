package com.blackfox.music.presentation.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackfox.music.data.local.*
import com.blackfox.music.data.repository.PlaylistRepository
import com.blackfox.music.presentation.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    downloadedTracksCount: Int,
    storageSizeBytes: Long,
    appSettings: AppSettings,
    favoriteTracks: List<TrackEntity>,
    userPlaylists: List<PlaylistWithTracks>,
    musicDao: MusicDao,
    playlistRepository: PlaylistRepository,
    onClearAllDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentAccent by appSettings.currentAccent.collectAsState()
    val streamQuality by appSettings.streamQuality.collectAsState()
    val downloadQuality by appSettings.downloadQuality.collectAsState()

    var isClearDialogOpen by remember { mutableStateOf(false) }
    var isStreamQualityDialogOpen by remember { mutableStateOf(false) }
    var isDownloadQualityDialogOpen by remember { mutableStateOf(false) }
    var isVkMigrationDialogOpen by remember { mutableStateOf(false) }
    var importResultDialog by remember { mutableStateOf<ImportResult?>(null) }

    val formattedSize = remember(storageSizeBytes) {
        val mb = storageSizeBytes.toDouble() / (1024 * 1024)
        if (mb >= 1024) {
            String.format("%.2f ГБ", mb / 1024)
        } else {
            String.format("%.1f МБ", mb)
        }
    }

    // Экспорт медиатеки в JSON
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val ok = MediaLibraryBackupManager.exportLibrary(
                    context = context,
                    destinationUri = uri,
                    favorites = favoriteTracks,
                    playlists = userPlaylists
                )
                if (ok) {
                    Toast.makeText(context, "Медиатека успешно экспортирована!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Не удалось экспортировать медиатеку", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Импорт медиатеки из JSON
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val result = MediaLibraryBackupManager.importLibrary(
                    context = context,
                    sourceUri = uri,
                    musicDao = musicDao,
                    playlistRepository = playlistRepository
                )
                importResultDialog = result
            }
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
                // Карточка приложения (без надписи Liquid Glass)
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
                                    .background(currentAccent.color.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = currentAccent.color,
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
                                    text = "Версия 0.1.2",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Раздел: ДИЗАЙН И АКЦЕНТНЫЙ ЦВЕТ
                item {
                    SettingsSection(title = "ДИЗАЙН И АКЦЕНТНЫЙ ЦВЕТ", accentColor = currentAccent.color) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Палитра неоновых акцентов",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Выберите цвет интерфейса, кнопок и волн",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(AccentTheme.entries) { theme ->
                                    val isSelected = currentAccent == theme
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isSelected) theme.color.copy(alpha = 0.15f) else Color(0x0EFFFFFF))
                                            .border(
                                                1.5.dp,
                                                if (isSelected) theme.color else Color.Transparent,
                                                RoundedCornerShape(14.dp)
                                            )
                                            .clickable { appSettings.setAccent(theme) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(theme.color),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = if (theme == AccentTheme.MONOCHROME) ObsidianBlack else Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = theme.emoji,
                                                    fontSize = 16.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = theme.title.substringBefore(" /"),
                                            color = if (isSelected) theme.color else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Раздел: АУДИО (Потоковое воспроизведение и загрузка)
                item {
                    SettingsSection(title = "АУДИО", accentColor = currentAccent.color) {
                        SettingsItem(
                            icon = Icons.Default.CellTower,
                            title = "Потоковое воспроизведение",
                            subtitle = when (streamQuality) {
                                "low" -> "Экономия (128 kbps — быстрая загрузка)"
                                "high" -> "Высокое (320 kbps)"
                                else -> "Стандарт (192 kbps)"
                            },
                            iconTint = currentAccent.color,
                            trailing = {
                                Text(
                                    text = when (streamQuality) {
                                        "low" -> "128 kbps"
                                        "high" -> "320 kbps"
                                        else -> "192 kbps"
                                    },
                                    color = currentAccent.color,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            onClick = { isStreamQualityDialogOpen = true }
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        SettingsItem(
                            icon = Icons.Default.FileDownload,
                            title = "Загрузка треков",
                            subtitle = when (downloadQuality) {
                                "256" -> "Высокое (256 kbps)"
                                "192" -> "Стандартное (192 kbps)"
                                else -> "Максимальное (320 kbps, по умолчанию)"
                            },
                            iconTint = currentAccent.color,
                            trailing = {
                                Text(
                                    text = "$downloadQuality kbps",
                                    color = currentAccent.color,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            onClick = { isDownloadQualityDialogOpen = true }
                        )
                    }
                }

                // Раздел: ИМПОРТ / ЭКСПОРТ МЕДИАТЕКИ
                item {
                    SettingsSection(title = "ИМПОРТ / ЭКСПОРТ МЕДИАТЕКИ", accentColor = currentAccent.color) {
                        SettingsItem(
                            icon = Icons.Default.CloudUpload,
                            title = "Экспорт медиатеки",
                            subtitle = "Сохранение всех плейлистов и списка «Любимых» треков в .json",
                            iconTint = currentAccent.color,
                            onClick = {
                                exportLauncher.launch("blackfox_library_backup.json")
                            }
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        SettingsItem(
                            icon = Icons.Default.CloudDownload,
                            title = "Импорт медиатеки",
                            subtitle = "Восстановление коллекции в 1 клик из .json файла",
                            iconTint = currentAccent.color,
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "*/*"))
                            }
                        )
                    }
                }

                // Раздел: МИГРАЦИЯ
                item {
                    SettingsSection(title = "МИГРАЦИЯ ИЗ ДРУГИХ СЕРВИСОВ", accentColor = currentAccent.color) {
                        SettingsItem(
                            icon = Icons.Default.Sync,
                            title = "Миграция из VK Music",
                            subtitle = "Перенос плейлистов и аудиозаписей из ВКонтакте",
                            iconTint = currentAccent.color,
                            trailing = {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(currentAccent.color.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Скоро",
                                        color = currentAccent.color,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            onClick = { isVkMigrationDialogOpen = true }
                        )
                    }
                }

                // Раздел: ОФФЛАЙН И ХРАНИЛИЩЕ
                item {
                    SettingsSection(title = "ОФФЛАЙН И ХРАНИЛИЩЕ", accentColor = currentAccent.color) {
                        SettingsItem(
                            icon = Icons.Default.CloudDownload,
                            title = "Автоскачивание музыки",
                            subtitle = "Любимые треки и плейлисты сохраняются автоматически",
                            iconTint = currentAccent.color,
                            trailing = {
                                Text(
                                    text = "Активно",
                                    color = currentAccent.color,
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
                            iconTint = currentAccent.color,
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
                            subtitle = "Удалить все сохраненные аудиофайлы с устройства",
                            iconTint = Color(0xFFFF5252),
                            onClick = { isClearDialogOpen = true }
                        )
                    }
                }
            }
        }

        // ДИАЛОГИ ВЫБОРА

        // 1. Диалог качества потока
        if (isStreamQualityDialogOpen) {
            AlertDialog(
                onDismissRequest = { isStreamQualityDialogOpen = false },
                containerColor = Color(0xF2151720),
                title = { Text("Потоковое воспроизведение", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QualityOption(
                            title = "Экономия (128 kbps)",
                            subtitle = "Быстрый старт треков и минимальный расход трафика",
                            isSelected = streamQuality == "low",
                            accentColor = currentAccent.color,
                            onSelect = {
                                appSettings.setStreamQuality("low")
                                isStreamQualityDialogOpen = false
                            }
                        )
                        QualityOption(
                            title = "Стандарт (192 kbps)",
                            subtitle = "Оптимальный баланс скорости и качества звука",
                            isSelected = streamQuality == "standard",
                            accentColor = currentAccent.color,
                            onSelect = {
                                appSettings.setStreamQuality("standard")
                                isStreamQualityDialogOpen = false
                            }
                        )
                        QualityOption(
                            title = "Высокое (320 kbps)",
                            subtitle = "Максимальная детализация звучания в потоке",
                            isSelected = streamQuality == "high",
                            accentColor = currentAccent.color,
                            onSelect = {
                                appSettings.setStreamQuality("high")
                                isStreamQualityDialogOpen = false
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isStreamQualityDialogOpen = false }) {
                        Text("Закрыть", color = currentAccent.color)
                    }
                }
            )
        }

        // 2. Диалог качества загрузки
        if (isDownloadQualityDialogOpen) {
            AlertDialog(
                onDismissRequest = { isDownloadQualityDialogOpen = false },
                containerColor = Color(0xF2151720),
                title = { Text("Качество загрузки треков", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QualityOption(
                            title = "Максимальное (320 kbps)",
                            subtitle = "Наивысший битрейт для оффлайн прослушивания",
                            isSelected = downloadQuality == "320",
                            accentColor = currentAccent.color,
                            onSelect = {
                                appSettings.setDownloadQuality("320")
                                isDownloadQualityDialogOpen = false
                            }
                        )
                        QualityOption(
                            title = "Высокое (256 kbps)",
                            subtitle = "Отличное качество и меньший размер файлов",
                            isSelected = downloadQuality == "256",
                            accentColor = currentAccent.color,
                            onSelect = {
                                appSettings.setDownloadQuality("256")
                                isDownloadQualityDialogOpen = false
                            }
                        )
                        QualityOption(
                            title = "Стандартное (192 kbps)",
                            subtitle = "Экономия дискового пространства устройства",
                            isSelected = downloadQuality == "192",
                            accentColor = currentAccent.color,
                            onSelect = {
                                appSettings.setDownloadQuality("192")
                                isDownloadQualityDialogOpen = false
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isDownloadQualityDialogOpen = false }) {
                        Text("Закрыть", color = currentAccent.color)
                    }
                }
            )
        }

        // 3. Диалог миграции из VK Music
        if (isVkMigrationDialogOpen) {
            AlertDialog(
                onDismissRequest = { isVkMigrationDialogOpen = false },
                containerColor = Color(0xF2151720),
                icon = {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = currentAccent.color, modifier = Modifier.size(36.dp))
                },
                title = { Text("Миграция из VK Music", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Функция находится в разработке. В следующем релизе появится импорт всех плейлистов и аудиозаписей напрямую через ссылку на профиль или VK ID.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { isVkMigrationDialogOpen = false },
                        colors = ButtonDefaults.buttonColors(containerColor = currentAccent.color)
                    ) {
                        Text("Понятно", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // 4. Диалог результатов импорта
        importResultDialog?.let { res ->
            AlertDialog(
                onDismissRequest = { importResultDialog = null },
                containerColor = Color(0xF2151720),
                title = {
                    Text(
                        text = if (res.success) "Медиатека восстановлена" else "Ошибка импорта",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    if (res.success) {
                        Text(
                            text = "Успешно импортировано:\n• Любимых треков: ${res.favoritesCount}\n• Создано плейлистов: ${res.playlistsCount}",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    } else {
                        Text(
                            text = "Не удалось прочитать файл: ${res.errorMessage ?: "Неверный формат резервной копии"}",
                            color = Color(0xFFFF5252),
                            fontSize = 13.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { importResultDialog = null },
                        colors = ButtonDefaults.buttonColors(containerColor = currentAccent.color)
                    ) {
                        Text("Отлично", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // 5. Диалог подтверждения очистки кэша
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
private fun QualityOption(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    accentColor: Color,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Color(0x0EFFFFFF))
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isSelected) accentColor else TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    accentColor: Color = LocalAccentTheme.current.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            color = accentColor,
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
    iconTint: Color = LocalAccentTheme.current.primary,
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
