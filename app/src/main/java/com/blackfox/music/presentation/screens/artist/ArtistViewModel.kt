package com.blackfox.music.presentation.screens.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackfox.music.data.download.AudioDownloadManager
import com.blackfox.music.data.local.ArtistEntity
import com.blackfox.music.data.local.MusicDao
import com.blackfox.music.data.local.PlaylistEntity
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.data.network.InnerTubeRepository
import com.blackfox.music.data.repository.PlaylistRepository
import com.blackfox.music.domain.model.Artist
import com.blackfox.music.domain.model.ArtistTracksPage
import com.blackfox.music.domain.model.Track
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ArtistUiState(
    val isLoading: Boolean = false,
    val artist: Artist? = null,
    val error: String? = null
)

class ArtistViewModel(
    val channelId: String,
    val initialName: String,
    private val repository: InnerTubeRepository = InnerTubeRepository(),
    private val downloadManager: AudioDownloadManager,
    private val playlistRepository: PlaylistRepository,
    private val musicDao: MusicDao
) : ViewModel() {

    val uiState = MutableStateFlow(ArtistUiState(isLoading = true))
    val allTracks = MutableStateFlow<List<Track>>(emptyList())
    val isAllTracksLoading = MutableStateFlow(false)
    val allTracksContinuationToken = MutableStateFlow<String?>(null)
    val totalSongsCountString = MutableStateFlow<String?>(null)
    val isLoadingMoreTracks = MutableStateFlow(false)
    val hasMoreTracks = MutableStateFlow(true)

    val playlists: StateFlow<List<PlaylistEntity>> = musicDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTrackIds: StateFlow<Set<String>> = musicDao.getFavoriteTrackIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val downloadedTrackIds: StateFlow<Set<String>> = musicDao.getDownloadedTrackIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val isFollowed: StateFlow<Boolean> = uiState
        .flatMapLatest { state ->
            val cid = state.artist?.channelId ?: channelId
            if (cid.isNotBlank()) musicDao.isArtistFollowed(cid) else flowOf(false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        loadArtist()
    }

    private fun loadArtist() {
        viewModelScope.launch {
            uiState.value = ArtistUiState(isLoading = true)
            try {
                var targetId = channelId
                if (targetId.isBlank() || !targetId.startsWith("UC")) {
                    val searchResult = repository.searchArtists(initialName, limit = 5)
                    val matched = searchResult.firstOrNull { it.name.equals(initialName, ignoreCase = true) }
                        ?: searchResult.firstOrNull()
                    if (matched != null) {
                        targetId = matched.channelId
                    }
                }

                if (targetId.isNotBlank()) {
                    val artist = repository.getArtistDetails(targetId)
                    uiState.value = ArtistUiState(isLoading = false, artist = artist)
                    // Фоновая предзагрузка всех песен артиста
                    loadAllTracks(artist)
                } else {
                    uiState.value = ArtistUiState(isLoading = false, error = "Информация об артисте не найдена")
                }
            } catch (e: Exception) {
                uiState.value = ArtistUiState(
                    isLoading = false,
                    error = e.localizedMessage ?: "Не удалось загрузить данные артиста"
                )
            }
        }
    }

    fun loadAllTracks(currentArtist: Artist? = uiState.value.artist) {
        val a = currentArtist ?: uiState.value.artist ?: return
        if (allTracks.value.isNotEmpty() || isAllTracksLoading.value) return

        viewModelScope.launch {
            isAllTracksLoading.value = true
            try {
                val page = repository.getArtistAllTracks(
                    allSongsBrowseId = a.allSongsBrowseId ?: "",
                    allSongsParams = a.allSongsParams,
                    artistName = a.name,
                    artistId = a.channelId
                )
                allTracks.value = if (page.tracks.isNotEmpty()) page.tracks else a.topTracks
                allTracksContinuationToken.value = page.continuationToken
                totalSongsCountString.value = page.totalCountString
                hasMoreTracks.value = !page.continuationToken.isNullOrBlank() || page.tracks.size >= 15
            } catch (e: Exception) {
                if (allTracks.value.isEmpty()) {
                    allTracks.value = a.topTracks
                }
                hasMoreTracks.value = false
            } finally {
                isAllTracksLoading.value = false
            }
        }
    }

    fun loadMoreTracks() {
        val a = uiState.value.artist ?: return
        if (isLoadingMoreTracks.value || !hasMoreTracks.value) return

        val token = allTracksContinuationToken.value

        viewModelScope.launch {
            isLoadingMoreTracks.value = true
            try {
                var addedCount = 0
                if (!token.isNullOrBlank()) {
                    val page = repository.getArtistTracksContinuation(
                        continuationToken = token,
                        artistName = a.name,
                        artistId = a.channelId
                    )
                    if (page.tracks.isNotEmpty()) {
                        val existingIds = allTracks.value.map { it.id }.toSet()
                        val newTracks = page.tracks.filter { !existingIds.contains(it.id) }
                        addedCount = newTracks.size
                        allTracks.value = allTracks.value + newTracks
                    }
                    allTracksContinuationToken.value = page.continuationToken
                    if (page.continuationToken.isNullOrBlank() && addedCount == 0) {
                        // Попытка найти дополнительные треки через расширенный поиск
                        val searchResults = repository.searchTracks(a.name, limit = allTracks.value.size + 50)
                        val existingIds = allTracks.value.map { it.id }.toSet()
                        val newTracks = searchResults.filter { !existingIds.contains(it.id) }
                        if (newTracks.isNotEmpty()) {
                            allTracks.value = allTracks.value + newTracks
                        } else {
                            hasMoreTracks.value = false
                        }
                    }
                } else {
                    // Токен отсутствует, загружаем следующую порцию через расширенный поиск артиста
                    val searchResults = repository.searchTracks(a.name, limit = allTracks.value.size + 50)
                    val existingIds = allTracks.value.map { it.id }.toSet()
                    val newTracks = searchResults.filter { !existingIds.contains(it.id) }
                    if (newTracks.isNotEmpty()) {
                        allTracks.value = allTracks.value + newTracks
                    } else {
                        hasMoreTracks.value = false
                    }
                }
            } catch (e: Exception) {
                hasMoreTracks.value = false
            } finally {
                isLoadingMoreTracks.value = false
            }
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            playlistRepository.toggleFavorite(track)
        }
    }

    fun downloadTrack(track: Track) {
        downloadManager.downloadTrack(track)
    }

    fun getPlaylistIdsForTrack(trackId: String): Flow<List<Long>> {
        return playlistRepository.getPlaylistIdsForTrack(trackId)
    }

    fun toggleTrackInPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            playlistRepository.toggleTrackInPlaylist(
                playlistId = playlistId,
                track = track
            )
        }
    }

    fun createPlaylistAndAddTrack(name: String, track: Track) {
        viewModelScope.launch {
            playlistRepository.createPlaylistAndAddTrack(name, track)
        }
    }

    fun toggleFollowArtist() {
        val a = uiState.value.artist ?: return
        val cid = a.channelId
        if (cid.isBlank()) return

        viewModelScope.launch {
            if (isFollowed.value) {
                musicDao.unfollowArtist(cid)
            } else {
                musicDao.followArtist(
                    ArtistEntity(
                        channelId = cid,
                        name = a.name,
                        thumbnailUrl = a.avatarUrl,
                        subscribers = a.subscribers
                    )
                )
            }
        }
    }
}
