package com.blackfox.music.presentation.screens.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackfox.music.data.download.AudioDownloadManager
import com.blackfox.music.data.local.MusicDao
import com.blackfox.music.data.local.PlaylistEntity
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.data.network.InnerTubeRepository
import com.blackfox.music.data.repository.PlaylistRepository
import com.blackfox.music.domain.model.Artist
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

    val playlists: StateFlow<List<PlaylistEntity>> = musicDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTrackIds: StateFlow<Set<String>> = musicDao.getFavoriteTrackIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val downloadedTrackIds: StateFlow<Set<String>> = musicDao.getDownloadedTrackIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

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
                val list = repository.getArtistAllTracks(
                    allSongsBrowseId = a.allSongsBrowseId ?: "",
                    artistName = a.name,
                    artistId = a.channelId
                )
                allTracks.value = if (list.isNotEmpty()) list else a.topTracks
            } catch (e: Exception) {
                if (allTracks.value.isEmpty()) {
                    allTracks.value = a.topTracks
                }
            } finally {
                isAllTracksLoading.value = false
            }
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val isFav = favoriteTrackIds.value.contains(track.id)
            val newFav = !isFav
            val existing = musicDao.getTrackById(track.id)
            if (existing == null) {
                musicDao.insertTrack(
                    TrackEntity(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        duration = track.duration,
                        thumbnailUrl = track.thumbnailUrl,
                        isFavorite = newFav
                    )
                )
            } else {
                musicDao.updateFavorite(track.id, newFav)
            }
            if (newFav) {
                downloadManager.downloadTrack(track)
            }
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
}
