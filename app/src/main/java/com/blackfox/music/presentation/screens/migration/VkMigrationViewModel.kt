package com.blackfox.music.presentation.screens.migration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackfox.music.data.download.AudioDownloadManager
import com.blackfox.music.data.download.DownloadStatus
import com.blackfox.music.data.local.ArtistEntity
import com.blackfox.music.data.local.MusicDao
import com.blackfox.music.data.local.PlaylistTrackCrossRef
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.data.local.toDomain
import com.blackfox.music.data.network.InnerTubeRepository
import com.blackfox.music.data.network.VkPlaylist
import com.blackfox.music.data.network.VkRepository
import com.blackfox.music.data.network.VkTrack
import com.blackfox.music.data.repository.PlaylistRepository
import com.blackfox.music.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MigrationSearchFilter(val label: String) {
    ALL("Все"),
    SONGS("Песни"),
    VIDEOS("Видео (без цензуры)")
}

/**
 * Элемент для экрана сопоставления треков
 */
data class MigrationReviewItem(
    val vkTrack: VkTrack,
    val matchedTrack: Track?,
    val isFavorite: Boolean,
    val targetPlaylists: List<String>
)

sealed interface MigrationUiState {
    data object Idle : MigrationUiState

    data class LoadingMetadata(val message: String) : MigrationUiState

    // Экран подтверждения перед запуском сопоставления
    data class ConfirmMigration(
        val rawVkInput: String,
        val userId: Long,
        val totalUniqueTracks: Int,
        val favoritesCount: Int,
        val playlists: List<VkPlaylist>,
        val isDownloadRequested: Boolean,
        val isClearTracksRequested: Boolean
    ) : MigrationUiState

    // Круговой индикатор при автоматическом сопоставлении
    data class MatchingTracks(
        val current: Int,
        val total: Int,
        val currentTrackName: String,
        val progressPercent: Float,
        val matchedCount: Int,
        val unmatchedCount: Int
    ) : MigrationUiState

    // Интерактивная таблица сопоставления
    data class ReviewMatching(
        val items: List<MigrationReviewItem>,
        val isDownloadRequested: Boolean,
        val isClearTracksRequested: Boolean
    ) : MigrationUiState

    data class SavingToDatabase(val message: String) : MigrationUiState

    data class SubscribingArtists(val message: String) : MigrationUiState

    // Круговой индикатор при скачивании
    data class DownloadingTracks(
        val current: Int,
        val total: Int,
        val currentTrackName: String,
        val progressPercent: Float
    ) : MigrationUiState

    data class Completed(
        val totalProcessed: Int,
        val matchedTracks: Int,
        val unmatchedTracks: Int,
        val downloadedTracks: Int,
        val playlistsCreated: Int,
        val artistsSubscribed: List<String>
    ) : MigrationUiState

    data class Error(val message: String) : MigrationUiState
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

    private var cachedFavoriteVkTracks: List<VkTrack> = emptyList()
    private var cachedVkPlaylists: List<VkPlaylist> = emptyList()
    private var shouldSkipDownloading = false

    fun reset() {
        _uiState.value = MigrationUiState.Idle
        cachedFavoriteVkTracks = emptyList()
        cachedVkPlaylists = emptyList()
        shouldSkipDownloading = false
    }

    fun skipDownloading() {
        shouldSkipDownloading = true
    }

    /**
     * Шаг 1: Загрузка метаданных из VK и открытие окна подтверждения
     */
    fun prepareMigration(
        rawVkInput: String,
        isOpenConfirmed: Boolean,
        isDownloadRequested: Boolean,
        isClearTracksRequested: Boolean
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
                _uiState.value = MigrationUiState.LoadingMetadata("Определение пользователя VK...")
                val userId = vkRepository.resolveUserId(cleaned).getOrElse { err ->
                    _uiState.value = MigrationUiState.Error(err.message ?: "Не удалось определить ID пользователя VK")
                    return@launch
                }

                _uiState.value = MigrationUiState.LoadingMetadata("Получение аудиозаписей из VK...")
                val favoriteVkTracks = vkRepository.fetchFavoriteAudios(userId).getOrElse { err ->
                    _uiState.value = MigrationUiState.Error(err.message ?: "Не удалось загрузить аудиозаписи. Проверьте открытость аудиозаписей в профиле VK.")
                    return@launch
                }

                _uiState.value = MigrationUiState.LoadingMetadata("Поиск плейлистов VK...")
                val vkPlaylists = vkRepository.fetchPlaylistsWithTracks(userId).getOrDefault(emptyList())

                cachedFavoriteVkTracks = favoriteVkTracks
                cachedVkPlaylists = vkPlaylists

                val uniqueVkTracksMap = mutableMapOf<Long, VkTrack>()
                favoriteVkTracks.forEach { uniqueVkTracksMap[it.id] = it }
                vkPlaylists.forEach { pl -> pl.tracks.forEach { uniqueVkTracksMap[it.id] = it } }

                if (uniqueVkTracksMap.isEmpty()) {
                    _uiState.value = MigrationUiState.Error("В вашем профиле VK не найдено ни одного трека. Убедитесь, что аудиозаписи открыты.")
                    return@launch
                }

                _uiState.value = MigrationUiState.ConfirmMigration(
                    rawVkInput = rawVkInput,
                    userId = userId,
                    totalUniqueTracks = uniqueVkTracksMap.size,
                    favoritesCount = favoriteVkTracks.size,
                    playlists = vkPlaylists,
                    isDownloadRequested = isDownloadRequested,
                    isClearTracksRequested = isClearTracksRequested
                )

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = MigrationUiState.Error(e.message ?: "Ошибка при обращении к серверам VK")
            }
        }
    }

    /**
     * Шаг 2: Запуск автоматического поиска и сопоставления треков
     */
    fun startMatchingAfterConfirmation(
        isDownloadRequested: Boolean,
        isClearTracksRequested: Boolean
    ) {
        viewModelScope.launch {
            val favTracks = cachedFavoriteVkTracks
            val playlists = cachedVkPlaylists

            val uniqueMap = linkedMapOf<Long, VkTrack>()
            favTracks.forEach { uniqueMap[it.id] = it }
            playlists.forEach { pl -> pl.tracks.forEach { uniqueMap[it.id] = it } }

            val total = uniqueMap.size
            var current = 0
            var matchedCount = 0
            var unmatchedCount = 0

            val reviewItems = mutableListOf<MigrationReviewItem>()

            for (vkTrack in uniqueMap.values) {
                current++
                val trackLabel = "${vkTrack.artist} — ${vkTrack.title}"
                val progressPercent = current.toFloat() / total.toFloat()

                _uiState.value = MigrationUiState.MatchingTracks(
                    current = current,
                    total = total,
                    currentTrackName = trackLabel,
                    progressPercent = progressPercent,
                    matchedCount = matchedCount,
                    unmatchedCount = unmatchedCount
                )

                delay(30)

                val matched = matchVkTrack(vkTrack)
                if (matched != null) {
                    matchedCount++
                } else {
                    unmatchedCount++
                }

                val inFav = favTracks.any { it.id == vkTrack.id }
                val targetPls = playlists.filter { pl -> pl.tracks.any { it.id == vkTrack.id } }.map { it.title }

                reviewItems.add(
                    MigrationReviewItem(
                        vkTrack = vkTrack,
                        matchedTrack = matched,
                        isFavorite = inFav,
                        targetPlaylists = targetPls
                    )
                )
            }

            // Переход к экрану проверки сопоставления
            _uiState.value = MigrationUiState.ReviewMatching(
                items = reviewItems,
                isDownloadRequested = isDownloadRequested,
                isClearTracksRequested = isClearTracksRequested
            )
        }
    }

    /**
     * Изменение сопоставления трека вручную из таблицы сопоставления
     */
    fun updateMatchedTrack(vkTrackId: Long, newMatchedTrack: Track?) {
        val current = _uiState.value
        if (current is MigrationUiState.ReviewMatching) {
            val updated = current.items.map { item ->
                if (item.vkTrack.id == vkTrackId) {
                    item.copy(matchedTrack = newMatchedTrack)
                } else {
                    item
                }
            }
            _uiState.value = current.copy(items = updated)
        }
    }

    /**
     * Ручной поиск трека для замены или сопоставления ненайденного.
     * Поддерживает как официальные треки из каталога музыки, так и видеоклипы / версии без цензуры.
     */
    suspend fun searchReplacement(
        query: String,
        filter: MigrationSearchFilter = MigrationSearchFilter.ALL
    ): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = query.trim()
        when (filter) {
            MigrationSearchFilter.ALL -> {
                coroutineScope {
                    val songsDef = async {
                        runCatching { innerTubeRepository.searchTracks(q, limit = 15) }.getOrDefault(emptyList())
                    }
                    val videosDef = async {
                        runCatching { innerTubeRepository.searchVideos(q, limit = 15) }.getOrDefault(emptyList())
                    }
                    val songs = songsDef.await()
                    val videos = videosDef.await()

                    val seenIds = mutableSetOf<String>()
                    val combined = mutableListOf<Track>()
                    for (s in songs) {
                        if (seenIds.add(s.id)) combined.add(s)
                    }
                    for (v in videos) {
                        val videoTrack = if (v.album != "Видео / Без цензуры") v.copy(album = "Видео / Без цензуры") else v
                        if (seenIds.add(videoTrack.id)) combined.add(videoTrack)
                    }
                    combined
                }
            }
            MigrationSearchFilter.SONGS -> {
                runCatching { innerTubeRepository.searchTracks(q, limit = 25) }.getOrDefault(emptyList())
            }
            MigrationSearchFilter.VIDEOS -> {
                runCatching { innerTubeRepository.searchVideos(q, limit = 25) }.getOrDefault(emptyList()).map {
                    if (it.album != "Видео / Без цензуры") it.copy(album = "Видео / Без цензуры") else it
                }
            }
        }
    }

    /**
     * Шаг 3: Сохранение подтвержденных сопоставленных треков в базу и запуск скачивания
     */
    fun applyMigration(
        items: List<MigrationReviewItem>,
        isDownloadRequested: Boolean,
        isClearTracksRequested: Boolean
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = MigrationUiState.SavingToDatabase("Сохранение треков в медиатеку...")

                // 1. Очистка старых треков (если была выбрана настройка)
                // ВАЖНО: загруженные пользователем треки (isUserUploaded == true) НЕ удаляются!
                if (isClearTracksRequested) {
                    playlistRepository.clearAllExceptUserUploaded()
                }

                // 2. Подготовка сопоставленных треков
                val matchedItems = items.filter { it.matchedTrack != null }
                val vkIdToMatched = matchedItems.associate { it.vkTrack.id to it.matchedTrack!! }

                // 3. Сохранение треков в "Любимое"
                // В VK треки упорядочены от самых новых к старым (индекс 0 - самый свежий трек).
                // Чтобы новые треки отображались сверху (ORDER BY favoriteOrderPosition ASC),
                // самый свежий трек (индекс 0) должен получить наименьший targetPos.
                val favItems = matchedItems.filter { it.isFavorite }
                val totalFav = favItems.size
                val minFavPos = musicDao.getMinFavoriteOrder() ?: 0

                favItems.forEachIndexed { index, item ->
                    val matched = item.matchedTrack ?: return@forEachIndexed
                    val targetPos = minFavPos - (totalFav - index)
                    val existing = musicDao.getTrackById(matched.id)
                    val entity = if (existing != null) {
                        existing.copy(
                            isFavorite = true,
                            favoriteOrderPosition = targetPos
                        )
                    } else {
                        TrackEntity(
                            id = matched.id,
                            title = matched.title,
                            artist = if (matched.artist.isNotBlank() && matched.artist != "Unknown") matched.artist else item.vkTrack.artist,
                            album = matched.album,
                            albumId = matched.albumId,
                            duration = matched.duration,
                            durationSeconds = matched.durationSeconds,
                            thumbnailUrl = matched.thumbnailUrl,
                            isFavorite = true,
                            favoriteOrderPosition = targetPos
                        )
                    }
                    musicDao.insertTrack(entity)
                }

                // 4. Создание пользовательских плейлистов и привязка треков
                val playlistTitleToId = mutableMapOf<String, Long>()
                var playlistsCreatedCount = 0

                for (vkPl in cachedVkPlaylists) {
                    if (vkPl.tracks.isEmpty()) continue

                    // Ищем или создаем плейлист
                    val createdId = playlistRepository.createPlaylist(vkPl.title)
                    playlistTitleToId[vkPl.title] = createdId
                    playlistsCreatedCount++

                    var plPos = 0
                    for (t in vkPl.tracks) {
                        val matched = vkIdToMatched[t.id] ?: continue

                        // Добавляем запись о треке в Room (если ещё нет)
                        val existing = musicDao.getTrackById(matched.id)
                        if (existing == null) {
                            musicDao.insertTrack(
                                TrackEntity(
                                    id = matched.id,
                                    title = matched.title,
                                    artist = if (matched.artist.isNotBlank() && matched.artist != "Unknown") matched.artist else t.artist,
                                    album = matched.album,
                                    albumId = matched.albumId,
                                    duration = matched.duration,
                                    durationSeconds = matched.durationSeconds,
                                    thumbnailUrl = matched.thumbnailUrl,
                                    isFavorite = false
                                )
                            )
                        }

                        // Связываем трек с плейлистом напрямую
                        musicDao.addTrackToPlaylist(
                            PlaylistTrackCrossRef(
                                playlistId = createdId,
                                trackId = matched.id,
                                orderPosition = plPos++
                            )
                        )
                    }
                }

                // 5. Офлайн-скачивание треков (если запрошено пользователем)
                val uniqueMatchedTracks = matchedItems.map { it.matchedTrack!! }.distinctBy { it.id }
                var downloadedCount = 0

                if (isDownloadRequested && uniqueMatchedTracks.isNotEmpty()) {
                    val totalToDownload = uniqueMatchedTracks.size
                    _uiState.value = MigrationUiState.DownloadingTracks(
                        current = 0,
                        total = totalToDownload,
                        currentTrackName = "Подготовка к скачиванию...",
                        progressPercent = 0f
                    )

                    uniqueMatchedTracks.forEach { track ->
                        downloadManager.downloadTrack(track)
                    }

                    val targetIds = uniqueMatchedTracks.map { it.id }.toSet()
                    while (!shouldSkipDownloading) {
                        val progressMap = downloadManager.downloadProgressMap.value
                        val completedCount = targetIds.count { id ->
                            progressMap[id]?.status == DownloadStatus.COMPLETED
                        }
                        downloadedCount = completedCount

                        val activeEntry = progressMap.entries.firstOrNull { it.key in targetIds && it.value.status == DownloadStatus.DOWNLOADING }
                        val activeTrackTitle = activeEntry?.let { entry ->
                            uniqueMatchedTracks.find { it.id == entry.key }?.let { "${it.artist} — ${it.title}" }
                        } ?: if (completedCount < totalToDownload) "Скачивание треков..." else "Скачивание завершено"

                        val progressPct = if (totalToDownload > 0) completedCount.toFloat() / totalToDownload.toFloat() else 1f

                        _uiState.value = MigrationUiState.DownloadingTracks(
                            current = completedCount,
                            total = totalToDownload,
                            currentTrackName = activeTrackTitle,
                            progressPercent = progressPct
                        )

                        if (completedCount >= totalToDownload) {
                            break
                        }
                        delay(500)
                    }
                }

                // 6. Подписка на артистов, у которых более 6 треков
                _uiState.value = MigrationUiState.SubscribingArtists("Поиск и подписка на любимых артистов...")
                val subscribedArtistNames = autoSubscribeToTopArtists(uniqueMatchedTracks)

                // 7. Завершено!
                _uiState.value = MigrationUiState.Completed(
                    totalProcessed = items.size,
                    matchedTracks = matchedItems.size,
                    unmatchedTracks = items.size - matchedItems.size,
                    downloadedTracks = downloadedCount,
                    playlistsCreated = playlistsCreatedCount,
                    artistsSubscribed = subscribedArtistNames
                )

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = MigrationUiState.Error(e.message ?: "Произошла ошибка при сохранении миграции")
            }
        }
    }

    /**
     * Интеллектуальный поиск трека VK в каталоге InnerTube (Песни + Видео)
     */
    private suspend fun matchVkTrack(vkTrack: VkTrack): Track? = withContext(Dispatchers.IO) {
        val existingEntity = musicDao.findTrackByTitleAndArtist(vkTrack.title, vkTrack.artist)
        if (existingEntity != null) {
            return@withContext existingEntity.toDomain()
        }

        val candidates = mutableListOf<Track>()

        // 1. Полнотекстовый поиск по артисту и названию среди официальных треков
        val qStandard = "${vkTrack.artist} ${vkTrack.title}"
        val res1 = runCatching { innerTubeRepository.searchTracks(qStandard, limit = 8) }.getOrDefault(emptyList())
        candidates.addAll(res1)

        val cleanTitle = vkTrack.title
            .replace(Regex("(?i)\\((?:remaster(?:ed)?|feat\\.?|live|club|edit|remix|acoustic|radio).*?\\)"), "")
            .replace(Regex("(?i)\\[(?:remaster(?:ed)?|feat\\.?|live|club|edit|remix|acoustic|radio).*?\\]"), "")
            .trim()
        val hasCleanTitle = cleanTitle != vkTrack.title && cleanTitle.isNotBlank()

        // 2. Если результатов мало или нет совпадений - пробуем очищенную версию названия (без скобок и пометок)
        if (candidates.isEmpty() || candidates.all { scoreCandidateMatch(it, vkTrack) < 35 }) {
            if (hasCleanTitle) {
                val qClean = "${vkTrack.artist} $cleanTitle"
                val res2 = runCatching { innerTubeRepository.searchTracks(qClean, limit = 6) }.getOrDefault(emptyList())
                candidates.addAll(res2)
            }
        }

        // 3. Если указан альбом - пробуем поиск с альбомом
        if (candidates.isEmpty() && vkTrack.album.isNotBlank()) {
            val qAlbum = "${vkTrack.artist} ${vkTrack.title} ${vkTrack.album}"
            val res3 = runCatching { innerTubeRepository.searchTracks(qAlbum, limit = 6) }.getOrDefault(emptyList())
            candidates.addAll(res3)
        }

        // 4. Поиск по видеоклипам / видео (если среди официальных треков не нашлось надежного совпадения)
        if (candidates.isEmpty() || candidates.all { scoreCandidateMatch(it, vkTrack) < 35 }) {
            val resVideo = runCatching { innerTubeRepository.searchVideos(qStandard, limit = 8) }.getOrDefault(emptyList())
            candidates.addAll(resVideo)

            if ((candidates.isEmpty() || candidates.all { scoreCandidateMatch(it, vkTrack) < 35 }) && hasCleanTitle) {
                val qClean = "${vkTrack.artist} $cleanTitle"
                val resVideoClean = runCatching { innerTubeRepository.searchVideos(qClean, limit = 6) }.getOrDefault(emptyList())
                candidates.addAll(resVideoClean)
            }
        }

        if (candidates.isEmpty()) {
            return@withContext null
        }

        // Оценка кандидатов
        var bestCandidate: Track? = null
        var bestScore = -1

        for (candidate in candidates) {
            val score = scoreCandidateMatch(candidate, vkTrack)
            if (score > bestScore) {
                bestScore = score
                bestCandidate = candidate
            }
        }

        if (bestScore >= 35) {
            bestCandidate
        } else {
            val first = candidates.firstOrNull()
            if (first != null && (first.artist.contains(vkTrack.artist, ignoreCase = true) ||
                        vkTrack.artist.contains(first.artist, ignoreCase = true) ||
                        first.title.contains(vkTrack.artist, ignoreCase = true))) {
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
        } else if (candTitle.contains(targetArtist)) {
            // В видеоклипах имя артиста часто находится в самом названии видео ("Артист - Трек")
            score += 25
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
            else if (diff > 120) score -= 30
        }

        return score
    }

    private suspend fun autoSubscribeToTopArtists(tracks: List<Track>): List<String> = withContext(Dispatchers.IO) {
        val subscribedNames = mutableListOf<String>()
        val artistCounts = mutableMapOf<String, Int>()
        for (track in tracks) {
            val name = track.artist.trim()
            if (name.isNotBlank()) {
                artistCounts[name] = (artistCounts[name] ?: 0) + 1
            }
        }

        val qualifyingArtists = artistCounts.filter { it.value > 6 }

        for ((artistName, _) in qualifyingArtists) {
            try {
                if (musicDao.isArtistFollowedByName(artistName)) {
                    subscribedNames.add(artistName)
                    continue
                }

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
