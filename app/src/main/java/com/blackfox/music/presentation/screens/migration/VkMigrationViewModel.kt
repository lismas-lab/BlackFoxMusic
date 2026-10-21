package com.blackfox.music.presentation.screens.migration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackfox.music.data.download.AudioDownloadManager
import com.blackfox.music.data.download.DownloadStatus
import com.blackfox.music.data.local.ArtistEntity
import com.blackfox.music.data.local.MusicDao
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.data.local.toDomain
import com.blackfox.music.data.network.InnerTubeRepository
import com.blackfox.music.data.network.VkPlaylist
import com.blackfox.music.data.network.VkRepository
import com.blackfox.music.data.network.VkTrack
import com.blackfox.music.data.repository.PlaylistRepository
import com.blackfox.music.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class MigrationUiState {
    object Idle : MigrationUiState()

    data class LoadingMetadata(val message: String) : MigrationUiState()

    data class MigratingTracks(
        val current: Int,
        val total: Int,
        val currentTrackName: String,
        val matchedCount: Int,
        val unmatchedCount: Int
    ) : MigrationUiState()

    data class DownloadingTracks(
        val current: Int,
        val total: Int,
        val currentTrackName: String
    ) : MigrationUiState()

    data class SubscribingArtists(
        val message: String
    ) : MigrationUiState()

    data class Completed(
        val totalVkTracks: Int,
        val successfullyMatched: Int,
        val playlistsCreated: Int,
        val downloadedCount: Int,
        val subscribedArtists: List<String>
    ) : MigrationUiState()

    data class Error(val message: String) : MigrationUiState()
}

class VkMigrationViewModel(
    private val vkRepository: VkRepository,
    private val innerTubeRepository: InnerTubeRepository,
    private val playlistRepository: PlaylistRepository,
    private val musicDao: MusicDao,
    private val downloadManager: AudioDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<MigrationUiState>(MigrationUiState.Idle)
    val uiState: StateFlow<MigrationUiState> = _uiState.asStateFlow()

    private var shouldSkipDownloading = false

    fun reset() {
        _uiState.value = MigrationUiState.Idle
        shouldSkipDownloading = false
    }

    fun skipDownloading() {
        shouldSkipDownloading = true
    }

    fun startMigration(
        rawVkInput: String,
        isOpenConfirmed: Boolean,
        isDownloadRequested: Boolean
    ) {
        if (!isOpenConfirmed) {
            _uiState.value = MigrationUiState.Error("Подтвердите, что ваш профиль и аудиозаписи в VK открыты для всех")
            return
        }

        val cleaned = rawVkInput.trim()
        if (cleaned.isBlank()) {
            _uiState.value = MigrationUiState.Error("Введите VK ID или имя пользователя")
            return
        }

        viewModelScope.launch {
            try {
                // 1. Определение числового user_id
                _uiState.value = MigrationUiState.LoadingMetadata("Определение пользователя VK...")
                val userId = vkRepository.resolveUserId(cleaned).getOrElse { err ->
                    _uiState.value = MigrationUiState.Error(err.message ?: "Не удалось определить ID пользователя")
                    return@launch
                }

                // 2. Получение списка избранных аудиозаписей
                _uiState.value = MigrationUiState.LoadingMetadata("Получение аудиозаписей из VK...")
                val favoriteVkTracks = vkRepository.fetchFavoriteAudios(userId).getOrElse { err ->
                    _uiState.value = MigrationUiState.Error(err.message ?: "Не удалось загрузить аудиозаписи. Проверьте открытость аудиозаписей в профиле VK.")
                    return@launch
                }

                // 3. Получение пользовательских плейлистов
                _uiState.value = MigrationUiState.LoadingMetadata("Проверка плейлистов VK...")
                val vkPlaylists = vkRepository.fetchPlaylistsWithTracks(userId).getOrDefault(emptyList())

                // Считаем общее число уникальных треков для переноса
                val allVkTracksMap = mutableMapOf<Long, VkTrack>()
                favoriteVkTracks.forEach { allVkTracksMap[it.id] = it }
                vkPlaylists.forEach { pl -> pl.tracks.forEach { allVkTracksMap[it.id] = it } }

                val totalTracksToMigrate = allVkTracksMap.size
                if (totalTracksToMigrate == 0) {
                    _uiState.value = MigrationUiState.Error("В вашем профиле VK не найдено ни одного трека. Убедитесь, что аудиозаписи открыты.")
                    return@launch
                }

                // 4. Поиск и сопоставление треков через InnerTube и сохранение в базу
                val matchedTracksList = mutableListOf<Track>()
                var currentProcessed = 0
                var matchedCount = 0
                var unmatchedCount = 0

                val vkIdToMatchedTrack = mutableMapOf<Long, Track>()

                // Миграция каждого уникального трека
                for (vkTrack in allVkTracksMap.values) {
                    currentProcessed++
                    val trackLabel = "${vkTrack.artist} — ${vkTrack.title}"
                    _uiState.value = MigrationUiState.MigratingTracks(
                        current = currentProcessed,
                        total = totalTracksToMigrate,
                        currentTrackName = trackLabel,
                        matchedCount = matchedCount,
                        unmatchedCount = unmatchedCount
                    )

                    // Небольшая задержка, чтобы не спамить API InnerTube
                    delay(40)

                    val matchedTrack = matchVkTrack(vkTrack)
                    if (matchedTrack != null) {
                        matchedCount++
                        vkIdToMatchedTrack[vkTrack.id] = matchedTrack
                        matchedTracksList.add(matchedTrack)
                    } else {
                        unmatchedCount++
                    }
                }

                // 5. Занесение треков в Любимое
                val minFavPos = musicDao.getMinFavoriteOrder() ?: 0
                var favOrderOffset = 1
                for (favTrack in favoriteVkTracks) {
                    val matched = vkIdToMatchedTrack[favTrack.id] ?: continue
                    val targetPos = minFavPos - favOrderOffset++
                    musicDao.insertTrack(
                        TrackEntity(
                            id = matched.id,
                            title = matched.title,
                            artist = matched.artist,
                            album = matched.album,
                            albumId = matched.albumId,
                            duration = matched.duration,
                            durationSeconds = matched.durationSeconds,
                            thumbnailUrl = matched.thumbnailUrl,
                            isFavorite = true,
                            favoriteOrderPosition = targetPos
                        )
                    )
                }

                // 6. Создание плейлистов и наполнение их треками
                var playlistsCreatedCount = 0
                for (vkPl in vkPlaylists) {
                    if (vkPl.tracks.isEmpty()) continue
                    val createdId = playlistRepository.createPlaylist(vkPl.title)
                    playlistsCreatedCount++

                    for (t in vkPl.tracks) {
                        val matched = vkIdToMatchedTrack[t.id] ?: continue
                        // Сохраняем в таблицу tracks и связываем с плейлистом
                        musicDao.insertTrack(
                            TrackEntity(
                                id = matched.id,
                                title = matched.title,
                                artist = matched.artist,
                                album = matched.album,
                                albumId = matched.albumId,
                                duration = matched.duration,
                                durationSeconds = matched.durationSeconds,
                                thumbnailUrl = matched.thumbnailUrl,
                                isFavorite = false
                            )
                        )
                        playlistRepository.addTrackToPlaylist(createdId, matched)
                    }
                }

                // 7. Офлайн-скачивание треков (если запрошено пользователем)
                var successfullyDownloadedCount = 0
                val uniqueMatchedTracks = matchedTracksList.distinctBy { it.id }

                if (isDownloadRequested && uniqueMatchedTracks.isNotEmpty()) {
                    val totalToDownload = uniqueMatchedTracks.size
                    _uiState.value = MigrationUiState.DownloadingTracks(
                        current = 0,
                        total = totalToDownload,
                        currentTrackName = "Подготовка загрузки..."
                    )

                    // Добавляем треки в очередь AudioDownloadManager
                    uniqueMatchedTracks.forEach { track ->
                        downloadManager.downloadTrack(track)
                    }

                    // Мониторим прогресс скачивания до завершения или пропуска
                    val targetIds = uniqueMatchedTracks.map { it.id }.toSet()
                    while (!shouldSkipDownloading) {
                        val progressMap = downloadManager.downloadProgressMap.value
                        val downloadedInThisSession = targetIds.count { id ->
                            val prog = progressMap[id]
                            prog?.status == DownloadStatus.COMPLETED
                        }
                        successfullyDownloadedCount = downloadedInThisSession

                        val currentlyActive = progressMap.entries.firstOrNull { it.key in targetIds && it.value.status == DownloadStatus.DOWNLOADING }
                        val activeTrackTitle = currentlyActive?.let { active ->
                            uniqueMatchedTracks.find { it.id == active.key }?.let { "${it.artist} — ${it.title}" }
                        } ?: if (downloadedInThisSession < totalToDownload) "Загрузка треков..." else "Завершено"

                        _uiState.value = MigrationUiState.DownloadingTracks(
                            current = downloadedInThisSession,
                            total = totalToDownload,
                            currentTrackName = activeTrackTitle
                        )

                        if (downloadedInThisSession >= totalToDownload) {
                            break
                        }
                        delay(600)
                    }
                }

                // 8. Автоподписка на артистов, у которых более 6 треков
                _uiState.value = MigrationUiState.SubscribingArtists("Поиск и подписка на любимых артистов...")
                val subscribedArtistNames = autoSubscribeToTopArtists(uniqueMatchedTracks)

                // 9. Завершено!
                _uiState.value = MigrationUiState.Completed(
                    totalVkTracks = totalTracksToMigrate,
                    successfullyMatched = matchedCount,
                    playlistsCreated = playlistsCreatedCount,
                    downloadedCount = successfullyDownloadedCount,
                    subscribedArtists = subscribedArtistNames
                )

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = MigrationUiState.Error(e.message ?: "Произошла непредвиденная ошибка при миграции")
            }
        }
    }

    /**
     * Сопоставляет трек VK с каталогом YouTube Music
     */
    private suspend fun matchVkTrack(vkTrack: VkTrack): Track? = withContext(Dispatchers.IO) {
        // Проверка в локальной базе данных
        val existingEntity = musicDao.findTrackByTitleAndArtist(vkTrack.title, vkTrack.artist)
        if (existingEntity != null) {
            return@withContext existingEntity.toDomain()
        }

        // Поиск через InnerTube
        val searchCandidates = mutableListOf<Track>()

        // Попытка 1: с альбомом (если есть)
        if (vkTrack.album.isNotBlank()) {
            val qWithAlbum = "${vkTrack.artist} ${vkTrack.title} ${vkTrack.album}"
            val res = runCatching { innerTubeRepository.searchTracks(qWithAlbum, limit = 6) }.getOrDefault(emptyList())
            searchCandidates.addAll(res)
        }

        // Попытка 2: только исполнитель и название трека
        if (searchCandidates.isEmpty()) {
            val qSimple = "${vkTrack.artist} ${vkTrack.title}"
            val res = runCatching { innerTubeRepository.searchTracks(qSimple, limit = 8) }.getOrDefault(emptyList())
            searchCandidates.addAll(res)
        }

        if (searchCandidates.isEmpty()) {
            return@withContext null
        }

        // Оценка кандидатов и выбор лучшего
        var bestCandidate: Track? = null
        var bestScore = -1

        for (candidate in searchCandidates) {
            val score = scoreCandidateMatch(candidate, vkTrack)
            if (score > bestScore) {
                bestScore = score
                bestCandidate = candidate
            }
        }

        // Порог релевантности
        if (bestScore >= 35) {
            bestCandidate
        } else {
            // Если совпали хотя бы имя артиста или название
            val first = searchCandidates.firstOrNull()
            if (first != null && (first.artist.contains(vkTrack.artist, ignoreCase = true) || vkTrack.artist.contains(first.artist, ignoreCase = true))) {
                first
            } else null
        }
    }

    private fun scoreCandidateMatch(candidate: Track, vkTrack: VkTrack): Int {
        var score = 0
        val candArtist = candidate.artist.lowercase().trim()
        val candTitle = candidate.title.lowercase().trim()
        val targetArtist = vkTrack.artist.lowercase().trim()
        val targetTitle = vkTrack.title.lowercase().trim()

        if (candArtist == targetArtist) {
            score += 50
        } else if (candArtist.contains(targetArtist) || targetArtist.contains(candArtist)) {
            score += 30
        }

        val cleanCandTitle = candTitle.replace(Regex("[()\\[\\]{}.,!?-]"), "").trim()
        val cleanTargetTitle = targetTitle.replace(Regex("[()\\[\\]{}.,!?-]"), "").trim()

        if (cleanCandTitle == cleanTargetTitle) {
            score += 50
        } else if (cleanCandTitle.contains(cleanTargetTitle) || cleanTargetTitle.contains(cleanCandTitle)) {
            score += 30
        }

        if (vkTrack.album.isNotBlank() && candidate.album != null) {
            if (candidate.album.equals(vkTrack.album, ignoreCase = true)) {
                score += 25
            } else if (candidate.album.contains(vkTrack.album, ignoreCase = true) || vkTrack.album.contains(candidate.album, ignoreCase = true)) {
                score += 15
            }
        }

        if (vkTrack.durationSeconds > 0 && candidate.durationSeconds > 0) {
            val diff = kotlin.math.abs(candidate.durationSeconds - vkTrack.durationSeconds)
            if (diff <= 5) score += 20
            else if (diff <= 15) score += 10
            else if (diff > 120) score -= 25
        }

        return score
    }

    /**
     * Поиск исполнителей с более чем 6 треками и оформление подписки
     */
    private suspend fun autoSubscribeToTopArtists(tracks: List<Track>): List<String> = withContext(Dispatchers.IO) {
        val subscribedNames = mutableListOf<String>()

        // Считаем треки по артистам
        val artistCounts = mutableMapOf<String, Int>()
        for (track in tracks) {
            val name = track.artist.trim()
            if (name.isNotBlank()) {
                artistCounts[name] = (artistCounts[name] ?: 0) + 1
            }
        }

        // Фильтруем: более 6 треков (> 6)
        val qualifyingArtists = artistCounts.filter { it.value > 6 }

        for ((artistName, count) in qualifyingArtists) {
            try {
                // Проверяем, не подписан ли уже
                if (musicDao.isArtistFollowedByName(artistName)) {
                    subscribedNames.add(artistName)
                    continue
                }

                // Ищем артиста через InnerTube
                val searchRes = runCatching { innerTubeRepository.searchArtists(artistName, limit = 5) }.getOrDefault(emptyList())
                val matchedArtist = searchRes.firstOrNull { it.name.equals(artistName, ignoreCase = true) }
                    ?: searchRes.firstOrNull()

                if (matchedArtist != null) {
                    musicDao.followArtist(
                        ArtistEntity(
                            channelId = matchedArtist.channelId,
                            name = matchedArtist.name,
                            thumbnailUrl = matchedArtist.avatarUrl,
                            subscribers = matchedArtist.subscribers,
                            followedAt = System.currentTimeMillis()
                        )
                    )
                    subscribedNames.add(matchedArtist.name)
                } else {
                    // Создаем базовую подписку по имени
                    val syntheticId = "artist_${artistName.hashCode()}"
                    musicDao.followArtist(
                        ArtistEntity(
                            channelId = syntheticId,
                            name = artistName,
                            thumbnailUrl = "",
                            subscribers = "",
                            followedAt = System.currentTimeMillis()
                        )
                    )
                    subscribedNames.add(artistName)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        subscribedNames
    }
}
