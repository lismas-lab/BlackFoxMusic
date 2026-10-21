package com.blackfox.music.presentation.screens.migration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackfox.music.data.local.LocalAccentTheme
import com.blackfox.music.presentation.theme.*

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
                IconButton(onClick = onBackClick) {
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
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
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
                            onStartClick = {
                                viewModel.startMigration(
                                    rawVkInput = rawVkInput,
                                    isOpenConfirmed = isOpenConfirmed,
                                    isDownloadRequested = isDownloadRequested
                                )
                            },
                            accentColor = accentColor
                        )
                    }

                    is MigrationUiState.LoadingMetadata -> {
                        LoadingMetadataContent(
                            message = state.message,
                            accentColor = accentColor
                        )
                    }

                    is MigrationUiState.MigratingTracks -> {
                        MigratingTracksContent(
                            state = state,
                            accentColor = accentColor
                        )
                    }

                    is MigrationUiState.DownloadingTracks -> {
                        DownloadingTracksContent(
                            state = state,
                            onSkipClick = { viewModel.skipDownloading() },
                            accentColor = accentColor
                        )
                    }

                    is MigrationUiState.SubscribingArtists -> {
                        LoadingMetadataContent(
                            message = state.message,
                            accentColor = accentColor
                        )
                    }

                    is MigrationUiState.Completed -> {
                        CompletedMigrationContent(
                            state = state,
                            onFinish = onFinish,
                            accentColor = accentColor
                        )
                    }

                    is MigrationUiState.Error -> {
                        ErrorMigrationContent(
                            message = state.message,
                            onRetryClick = { viewModel.reset() },
                            accentColor = accentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleMigrationContent(
    rawVkInput: String,
    onRawVkInputChange: (String) -> Unit,
    isOpenConfirmed: Boolean,
    onIsOpenConfirmedChange: (Boolean) -> Unit,
    isDownloadRequested: Boolean,
    onIsDownloadRequestedChange: (Boolean) -> Unit,
    onStartClick: () -> Unit,
    accentColor: Color
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Иконка и заголовок
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
            text = "Black Fox автоматически найдет все ваши аудиозаписи, плейлисты и любимые треки в каталоге высокого качества.",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Поле ввода VK ID / ссылки
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

        Spacer(modifier = Modifier.height(8.dp))

        // Чекбокс загрузки в память
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
                    text = "Треки сохранятся на устройство для прослушивания без интернета",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        // Кнопка запуска
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
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Начать миграцию",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LoadingMetadataContent(
    message: String,
    accentColor: Color
) {
    Column(
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
private fun MigratingTracksContent(
    state: MigrationUiState.MigratingTracks,
    accentColor: Color
) {
    val progress = if (state.total > 0) state.current.toFloat() / state.total.toFloat() else 0f
    val percentage = (progress * 100).toInt()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Перенос и сопоставление треков",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Круговой индикатор прогресса
        Box(
            modifier = Modifier.size(170.dp),
            contentAlignment = Alignment.Center
        ) {
            // Фоновое кольцо
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = Color(0x1AFFFFFF),
                strokeWidth = 12.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round
            )
            // Активное кольцо
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = accentColor,
                strokeWidth = 12.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round
            )

            // Текст внутри индикатора: СколькоПеренесено / СколькоВсего
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

        // Карточка текущего обрабатываемого трека
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
                    text = "Поиск и сопоставление:",
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

        // Статистика находок
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
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
                            .background(Color(0xFFE57373))
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

@Composable
private fun DownloadingTracksContent(
    state: MigrationUiState.DownloadingTracks,
    onSkipClick: () -> Unit,
    accentColor: Color
) {
    val progress = if (state.total > 0) state.current.toFloat() / state.total.toFloat() else 0f
    val percentage = (progress * 100).toInt()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Загрузка треков в память",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Круговой индикатор прогресса скачивания: СколькоСкачано / СколькоНужноСкачать
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

        // Карточка текущей загрузки
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

        // Кнопка пропуска ожидания
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
    accentColor: Color
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
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

        // Карточки итогов
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
                value = "${state.successfullyMatched} из ${state.totalVkTracks}",
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

            if (state.downloadedCount > 0) {
                SummaryItem(
                    icon = Icons.Default.DownloadDone,
                    title = "Сохранено в память (офлайн)",
                    value = "${state.downloadedCount} треков",
                    accentColor = accentColor
                )
            }

            if (state.subscribedArtists.isNotEmpty()) {
                Column {
                    SummaryItem(
                        icon = Icons.Default.PersonAdd,
                        title = "Подписки на артистов (> 6 треков)",
                        value = "${state.subscribedArtists.size}",
                        accentColor = accentColor
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.subscribedArtists.forEach { artistName ->
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
    accentColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
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
