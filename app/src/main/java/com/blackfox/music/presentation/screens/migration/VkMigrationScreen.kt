package com.blackfox.music.presentation.screens.migration

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.blackfox.music.data.local.LocalAccentTheme
import com.blackfox.music.data.network.VkPlaylist
import com.blackfox.music.domain.model.Track
import com.blackfox.music.presentation.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VkMigrationScreen(
    viewModel: VkMigrationViewModel,
    onBackClick: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val accentColor = LocalAccentTheme.current.primary

    var rawVkInput by remember { mutableStateOf("") }
    var isOpenConfirmed by remember { mutableStateOf(false) }
    var isDownloadRequested by remember { mutableStateOf(false) }
    var isClearTracksRequested by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (uiState !is MigrationUiState.Idle && uiState !is MigrationUiState.Completed && uiState !is MigrationUiState.Error) {
            viewModel.reset()
        } else {
            onBackClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Верхняя панель навигации
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (uiState !is MigrationUiState.Idle && uiState !is MigrationUiState.Completed && uiState !is MigrationUiState.Error) {
                        viewModel.reset()
                    } else {
                        onBackClick()
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "Миграция из VK",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Основное содержимое в зависимости от состояния
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (val state = uiState) {
                    is MigrationUiState.Idle -> {
                        IdleMigrationContent(
                            rawVkInput = rawVkInput,
                            onRawVkInputChange = { rawVkInput = it },
                            isOpenConfirmed = isOpenConfirmed,
                            onIsOpenConfirmedChange = { isOpenConfirmed = it },
                            isDownloadRequested = isDownloadRequested,
                            onIsDownloadRequestedChange = { isDownloadRequested = it },
                            isClearTracksRequested = isClearTracksRequested,
                            onIsClearTracksRequestedChange = { isClearTracksRequested = it },
                            onStartClick = {
                                viewModel.prepareMigration(
                                    rawVkInput = rawVkInput,
                                    isOpenConfirmed = isOpenConfirmed,
                                    isDownloadRequested = isDownloadRequested,
                                    isClearTracksRequested = isClearTracksRequested
                                )
                            },
                            accentColor = accentColor,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    is MigrationUiState.LoadingMetadata -> {
                        LoadingMetadataContent(
                            message = state.message,
                            accentColor = accentColor,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    is MigrationUiState.ConfirmMigration -> {
                        ConfirmMigrationContent(
                            state = state,
                            onConfirm = {
                                viewModel.startMatchingAfterConfirmation(
                                    isDownloadRequested = state.isDownloadRequested,
                                    isClearTracksRequested = state.isClearTracksRequested
                                )
                            },
                            onCancel = { viewModel.reset() },
                            accentColor = accentColor,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    is MigrationUiState.MatchingTracks -> {
                        MatchingTracksContent(
                            state = state,
                            accentColor = accentColor,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    is MigrationUiState.ReviewMatching -> {
                        ReviewMatchingContent(
                            state = state,
                            onApply = { items ->
                                viewModel.applyMigration(
                                    items = items,
                                    isDownloadRequested = state.isDownloadRequested,
                                    isClearTracksRequested = state.isClearTracksRequested
                                )
                            },
                            onCancel = { viewModel.reset() },
                            onUpdateTrack = { vkId, track -> viewModel.updateMatchedTrack(vkId, track) },
                            onSearchReplacement = { q, filter -> viewModel.searchReplacement(q, filter) },
                            accentColor = accentColor
                        )
                    }

                    is MigrationUiState.SavingToDatabase -> {
                        LoadingMetadataContent(
                            message = state.message,
                            accentColor = accentColor,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    is MigrationUiState.SubscribingArtists -> {
                        LoadingMetadataContent(
                            message = state.message,
                            accentColor = accentColor,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    is MigrationUiState.DownloadingTracks -> {
                        DownloadingTracksContent(
                            state = state,
                            onSkipClick = { viewModel.skipDownloading() },
                            accentColor = accentColor,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    is MigrationUiState.Completed -> {
                        CompletedMigrationContent(
                            state = state,
                            onFinish = onFinish,
                            accentColor = accentColor,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    is MigrationUiState.Error -> {
                        ErrorMigrationContent(
                            message = state.message,
                            onRetryClick = { viewModel.reset() },
                            accentColor = accentColor,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Стартовый экран: Ввод VK ID, опции очистки, скачивания и подтверждение открытости
 */
@Composable
private fun IdleMigrationContent(
    rawVkInput: String,
    onRawVkInputChange: (String) -> Unit,
    isOpenConfirmed: Boolean,
    onIsOpenConfirmedChange: (Boolean) -> Unit,
    isDownloadRequested: Boolean,
    onIsDownloadRequestedChange: (Boolean) -> Unit,
    isClearTracksRequested: Boolean,
    onIsClearTracksRequestedChange: (Boolean) -> Unit,
    onStartClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Перенос медиатеки VK",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Black Fox перенесет все ваши аудиозаписи, плейлисты и избранное из открытого профиля VK.",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Поле ввода VK ID
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x12FFFFFF))
                .padding(16.dp)
        ) {
            Text(
                text = "Ваш VK ID или ссылка на профиль",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x18FFFFFF))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (rawVkInput.isEmpty()) {
                    Text(
                        text = "Например: 0lesnoyilyushka7 или id193678534",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
                BasicTextField(
                    value = rawVkInput,
                    onValueChange = onRawVkInputChange,
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

        Spacer(modifier = Modifier.height(16.dp))

        // Чекбокс подтверждения открытости
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onIsOpenConfirmedChange(!isOpenConfirmed) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isOpenConfirmed,
                onCheckedChange = onIsOpenConfirmedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = accentColor,
                    checkmarkColor = ObsidianBlack,
                    uncheckedColor = TextSecondary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Подтверждаю, что мой профиль и список аудиозаписей в VK открыты для всех",
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Чекбокс: Очистить все текущие треки перед миграцией
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onIsClearTracksRequestedChange(!isClearTracksRequested) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isClearTracksRequested,
                onCheckedChange = onIsClearTracksRequestedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFFEF5350),
                    checkmarkColor = ObsidianBlack,
                    uncheckedColor = TextSecondary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Очистить все текущие треки перед миграцией",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Удалит существующую медиатеку и скачанные треки, кроме загруженных вручную вами (файлы с вашего устройства не удалятся)",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Чекбокс: Скачать перенесенные треки в память (офлайн)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onIsDownloadRequestedChange(!isDownloadRequested) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isDownloadRequested,
                onCheckedChange = onIsDownloadRequestedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = accentColor,
                    checkmarkColor = ObsidianBlack,
                    uncheckedColor = TextSecondary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Скачать перенесенные треки в память (офлайн)",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "После миграции треки будут автоматически сохранены на устройство",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Информационная плашка про подписку на артистов (> 6 треков)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(accentColor.copy(alpha = 0.08f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Исполнители, у которых перенесено более 6 треков, будут автоматически добавлены в раздел «Мои артисты».",
                color = TextPrimary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onStartClick,
            enabled = rawVkInput.isNotBlank() && isOpenConfirmed,
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = ObsidianBlack,
                disabledContainerColor = Color(0x22FFFFFF),
                disabledContentColor = TextMuted
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Получить список треков",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Экран подтверждения: вывод статистики найденных в VK треков и плейлистов
 */
@Composable
private fun ConfirmMigrationContent(
    state: MigrationUiState.ConfirmMigration,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Checklist,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Найдена музыка в VK",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Пользователь: id${state.userId} (${state.rawVkInput})",
            color = TextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Сводка
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x14FFFFFF))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🎵 Всего уникальных треков", color = TextSecondary, fontSize = 14.sp)
                Text(text = "${state.totalUniqueTracks}", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = Color(0x12FFFFFF))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "⭐ В разделе «Любимое»", color = TextSecondary, fontSize = 14.sp)
                Text(text = "${state.favoritesCount}", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = Color(0x12FFFFFF))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "📁 Пользовательских плейлистов", color = TextSecondary, fontSize = 14.sp)
                Text(text = "${state.playlists.size}", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            // Список найденных плейлистов
            if (state.playlists.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.playlists.forEach { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x10FFFFFF))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• ${pl.title}",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${pl.tracks.size} треков",
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Уведомление об очистке, если выбрано
        if (state.isClearTracksRequested) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEF5350).copy(alpha = 0.12f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Текущая медиатека будет очищена перед сохранением (файлы загруженные вами с устройства не пострадают).",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (state.isDownloadRequested) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.08f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "После сопоставления все найденные треки будут загружены в память устройства.",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопки Подтвердить / Отмена
        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = ObsidianBlack
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Подтвердить миграцию",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TextSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(text = "Отмена", fontSize = 14.sp)
        }
    }
}

/**
 * Экран фонового сопоставления треков с круговым индикатором
 */
@Composable
private fun MatchingTracksContent(
    state: MigrationUiState.MatchingTracks,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val progress = state.progressPercent.coerceIn(0f, 1f)
    val percentage = (progress * 100).toInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Сопоставление треков",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Круговой индикатор прогресса: СколькоПеренесено / СколькоВсего
        Box(
            modifier = Modifier.size(170.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = Color(0x1AFFFFFF),
                strokeWidth = 12.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = accentColor,
                strokeWidth = 12.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${state.current} / ${state.total}",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$percentage%",
                    color = accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Карточка текущего трека
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x14FFFFFF))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Поиск в каталоге:",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = state.currentTrackName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF66BB6A))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Найдено: ${state.matchedCount}",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (state.unmatchedCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF5350))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Не найдено: ${state.unmatchedCount}",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Таблица сопоставления треков: фильтры, поиск, ручная замена и сопоставление
 */
@Composable
private fun ReviewMatchingContent(
    state: MigrationUiState.ReviewMatching,
    onApply: (List<MigrationReviewItem>) -> Unit,
    onCancel: () -> Unit,
    onUpdateTrack: (Long, Track?) -> Unit,
    onSearchReplacement: suspend (String, MigrationSearchFilter) -> List<Track>,
    accentColor: Color
) {
    var selectedFilter by remember { mutableStateOf(ReviewFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var trackBeingEdited by remember { mutableStateOf<MigrationReviewItem?>(null) }

    val matchedCount = remember(state.items) { state.items.count { it.matchedTrack != null } }
    val unmatchedCount = remember(state.items) { state.items.count { it.matchedTrack == null } }

    val filteredItems = remember(state.items, selectedFilter, searchQuery) {
        state.items.filter { item ->
            val matchesFilter = when (selectedFilter) {
                ReviewFilter.ALL -> true
                ReviewFilter.MATCHED -> item.matchedTrack != null
                ReviewFilter.UNMATCHED -> item.matchedTrack == null
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.lowercase().trim()
                item.vkTrack.title.lowercase().contains(q) ||
                        item.vkTrack.artist.lowercase().contains(q) ||
                        item.matchedTrack?.title?.lowercase()?.contains(q) == true ||
                        item.matchedTrack?.artist?.lowercase()?.contains(q) == true
            }
            matchesFilter && matchesSearch
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Заголовок и фильтры
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Таблица сопоставления",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Сопоставлено: $matchedCount из ${state.items.size} • Не найдено: $unmatchedCount",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Чипы фильтров
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == ReviewFilter.ALL,
                    onClick = { selectedFilter = ReviewFilter.ALL },
                    label = { Text("Все (${state.items.size})", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor,
                        selectedLabelColor = ObsidianBlack,
                        containerColor = Color(0x14FFFFFF),
                        labelColor = TextSecondary
                    )
                )

                FilterChip(
                    selected = selectedFilter == ReviewFilter.MATCHED,
                    onClick = { selectedFilter = ReviewFilter.MATCHED },
                    label = { Text("Найденные ($matchedCount)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF66BB6A),
                        selectedLabelColor = ObsidianBlack,
                        containerColor = Color(0x14FFFFFF),
                        labelColor = TextSecondary
                    )
                )

                FilterChip(
                    selected = selectedFilter == ReviewFilter.UNMATCHED,
                    onClick = { selectedFilter = ReviewFilter.UNMATCHED },
                    label = { Text("Не найденные ($unmatchedCount)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFEF5350),
                        selectedLabelColor = TextPrimary,
                        containerColor = Color(0x14FFFFFF),
                        labelColor = TextSecondary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Поле поиска по списку
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x16FFFFFF))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Фильтр по названию или исполнителю...",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(accentColor),
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
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
        }

        // Список элементов
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(filteredItems, key = { it.vkTrack.id }) { item ->
                ReviewTrackRow(
                    item = item,
                    accentColor = accentColor,
                    onEditClick = { trackBeingEdited = item }
                )
            }
        }

        // Нижняя панель действий
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF141414),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    modifier = Modifier.weight(0.35f)
                ) {
                    Text(text = "Отмена", fontSize = 13.sp)
                }

                Button(
                    onClick = { onApply(state.items) },
                    enabled = matchedCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = ObsidianBlack
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(0.65f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Применить ($matchedCount)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Модальное окно ручного поиска трека
    trackBeingEdited?.let { item ->
        ManualSearchDialog(
            item = item,
            onDismiss = { trackBeingEdited = null },
            onSelectTrack = { selectedTrack ->
                onUpdateTrack(item.vkTrack.id, selectedTrack)
                trackBeingEdited = null
            },
            onClearMatch = {
                onUpdateTrack(item.vkTrack.id, null)
                trackBeingEdited = null
            },
            onSearch = onSearchReplacement,
            accentColor = accentColor
        )
    }
}

private enum class ReviewFilter {
    ALL, MATCHED, UNMATCHED
}

/**
 * Строка таблицы сопоставления для одного трека
 */
@Composable
private fun ReviewTrackRow(
    item: MigrationReviewItem,
    accentColor: Color,
    onEditClick: () -> Unit
) {
    val isMatched = item.matchedTrack != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isMatched) Color(0x14FFFFFF) else Color(0x1CEF5350))
            .border(
                width = 1.dp,
                color = if (isMatched) Color(0x18FFFFFF) else Color(0x40EF5350),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        // Исходный трек VK
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x22FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "VK", color = Color(0xFF4C75A3), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.vkTrack.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.vkTrack.artist + if (item.vkTrack.album.isNotBlank()) " • ${item.vkTrack.album}" else "",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.vkTrack.durationSeconds > 0) {
                val min = item.vkTrack.durationSeconds / 60
                val sec = item.vkTrack.durationSeconds % 60
                Text(
                    text = String.format("%d:%02d", min, sec),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Бейджи назначения: Любимое, Плейлисты
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isFavorite) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x25FFD54F))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "⭐ Любимое",
                        color = Color(0xFFFFD54F),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            item.targetPlaylists.forEach { plTitle ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x2281D4FA))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "📁 $plTitle",
                        color = Color(0xFF81D4FA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0x10FFFFFF))

        // Результат сопоставления
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isMatched) {
                val matched = item.matchedTrack!!
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF66BB6A),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = matched.title,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (matched.album == "Видео / Без цензуры") {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x33FF5252))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "ВИДЕО",
                                        color = Color(0xFFFF5252),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${matched.artist} • ${matched.duration}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onEditClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Заменить", fontSize = 11.sp)
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Трек не найден в каталоге",
                        color = Color(0xFFEF5350),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onEditClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = ObsidianBlack
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Найти вручную", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Диалоговое окно ручного поиска трека
 */
@Composable
private fun ManualSearchDialog(
    item: MigrationReviewItem,
    onDismiss: () -> Unit,
    onSelectTrack: (Track) -> Unit,
    onClearMatch: () -> Unit,
    onSearch: suspend (String, MigrationSearchFilter) -> List<Track>,
    accentColor: Color
) {
    var searchQuery by remember { mutableStateOf("${item.vkTrack.artist} ${item.vkTrack.title}") }
    var selectedFilter by remember { mutableStateOf(MigrationSearchFilter.ALL) }
    var searchResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun triggerSearch(q: String, f: MigrationSearchFilter) {
        scope.launch {
            isSearching = true
            searchResults = onSearch(q, f)
            isSearching = false
        }
    }

    // Автоматический первичный поиск при открытии
    LaunchedEffect(Unit) {
        triggerSearch(searchQuery, selectedFilter)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Заголовок
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Поиск трека",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Исходный: ${item.vkTrack.artist} — ${item.vkTrack.title}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть", tint = TextSecondary)
                    }
                }

                // Поле поиска с кнопкой
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x18FFFFFF))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(accentColor),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { triggerSearch(searchQuery, selectedFilter) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Искать", tint = accentColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Фильтры поиска: Все / Песни / Видео (без цензуры)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    MigrationSearchFilter.entries.forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) accentColor else Color(0x18FFFFFF))
                                .clickable {
                                    if (selectedFilter != filter) {
                                        selectedFilter = filter
                                        triggerSearch(searchQuery, filter)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = filter.label,
                                color = if (isSelected) ObsidianBlack else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Кнопка очистки сопоставления
                if (item.matchedTrack != null) {
                    TextButton(
                        onClick = onClearMatch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Убрать сопоставление (пропустить этот трек)", color = Color(0xFFEF5350), fontSize = 12.sp)
                    }
                }

                // Список результатов
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(color = accentColor, modifier = Modifier.size(36.dp))
                    } else if (searchResults.isEmpty()) {
                        Text(
                            text = "Ничего не найдено. Попробуйте изменить поисковый запрос.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(searchResults, key = { it.id }) { track ->
                                val isVideo = track.album == "Видео / Без цензуры"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectTrack(track) }
                                        .background(Color(0x12FFFFFF))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(42.dp)) {
                                        AsyncImage(
                                            model = track.thumbnailUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                        if (isVideo) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xCC000000)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Videocam,
                                                    contentDescription = "Видео",
                                                    tint = Color(0xFFFF5252),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = track.title,
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isVideo) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0x33FF5252))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "ВИДЕО",
                                                        color = Color(0xFFFF5252),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${track.artist} • ${track.duration}",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Выбрать",
                                        tint = accentColor,
                                        modifier = Modifier.size(20.dp)
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

@Composable
private fun LoadingMetadataContent(
    message: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = accentColor,
            strokeWidth = 4.dp,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = message,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Пожалуйста, подождите, это займет несколько секунд...",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DownloadingTracksContent(
    state: MigrationUiState.DownloadingTracks,
    onSkipClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val progress = state.progressPercent.coerceIn(0f, 1f)
    val percentage = (progress * 100).toInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Загрузка треков в память",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier.size(170.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = Color(0x1AFFFFFF),
                strokeWidth = 12.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = accentColor,
                strokeWidth = 12.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${state.current} / ${state.total}",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$percentage%",
                    color = accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x14FFFFFF))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Загрузка аудиопотока:",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = state.currentTrackName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        OutlinedButton(
            onClick = onSkipClick,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TextSecondary
            )
        ) {
            Text(text = "Завершить и скачивать в фоне", fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompletedMigrationContent(
    state: MigrationUiState.Completed,
    onFinish: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Миграция завершена!",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Ваша музыка успешно добавлена в медиатеку",
            color = TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x12FFFFFF))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SummaryItem(
                icon = Icons.Default.Favorite,
                title = "Перенесено треков",
                value = "${state.matchedTracks} из ${state.totalProcessed}",
                accentColor = accentColor
            )

            if (state.playlistsCreated > 0) {
                SummaryItem(
                    icon = Icons.Default.QueueMusic,
                    title = "Создано плейлистов",
                    value = "${state.playlistsCreated}",
                    accentColor = accentColor
                )
            }

            if (state.downloadedTracks > 0) {
                SummaryItem(
                    icon = Icons.Default.DownloadDone,
                    title = "Сохранено в память (офлайн)",
                    value = "${state.downloadedTracks} треков",
                    accentColor = accentColor
                )
            }

            if (state.artistsSubscribed.isNotEmpty()) {
                Column {
                    SummaryItem(
                        icon = Icons.Default.PersonAdd,
                        title = "Подписки на артистов (> 6 треков)",
                        value = "${state.artistsSubscribed.size}",
                        accentColor = accentColor
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.artistsSubscribed.forEach { artistName ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x18FFFFFF))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = artistName,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onFinish,
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = ObsidianBlack
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(
                text = "Перейти в медиатеку",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SummaryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ErrorMigrationContent(
    message: String,
    onRetryClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Color(0xFFE57373).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFE57373),
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Ошибка миграции",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = message,
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onRetryClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = ObsidianBlack
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(text = "Попробовать снова", fontWeight = FontWeight.SemiBold)
        }
    }
}
