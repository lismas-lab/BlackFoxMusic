package com.blackfox.music.presentation.screens.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackfox.music.data.download.AudioDownloadManager
import com.blackfox.music.data.local.MusicDao
import com.blackfox.music.data.local.PlaylistEntity
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.data.network.InnerTubeRepository
import com.blackfox.music.data.repository.PlaylistRepository
import com.blackfox.music.domain.model.Album
import com.blackfox.music.domain.model.Track
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AlbumUiState(
    val isLoading: Boolean = true,
    val album: Album? = null,
    val error: String? = null
)

class AlbumViewModel(
    private val browseId: String,
    private val repository: InnerTubeRepository = InnerTubeRepository(),
    private val downloadManager: AudioDownloadManager,
    private val playlistRepository: PlaylistRepository,
    private val musicDao: MusicDao
) : ViewModel() {

    val uiState = MutableStateFlow(AlbumUiState())
    val downloadProgress = downloadManager.downloadProgressMap
    val playlists: StateFlow<List<PlaylistEntity>> = musicDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTrackIds: StateFlow<Set<String>> = musicDao.getFavoriteTrackIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val downloadedTrackIds: StateFlow<Set<String>> = musicDao.getDownloadedTrackIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        loadAlbumDetails()
    }

    fun loadAlbumDetails() {
        viewModelScope.launch {
            uiState.value = AlbumUiState(isLoading = true)
            try {
                val albumDetails = repository.getAlbumDetails(browseId)
                uiState.value = AlbumUiState(isLoading = false, album = albumDetails)
            } catch (e: Exception) {
                uiState.value = AlbumUiState(isLoading = false, error = e.localizedMessage ?: "Не удалось загрузить альбом")
            }
        }
    }

    fun downloadEntireAlbum() {
        val album = uiState.value.album ?: return
        downloadManager.downloadAlbum(album)
    }

    fun downloadSingleTrack(track: Track) {
        downloadManager.downloadTrack(track)
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            playlistRepository.toggleFavorite(track)
        }
    }

    fun getPlaylistIdsForTrack(trackId: String): Flow<List<Long>> {
        return playlistRepository.getPlaylistIdsForTrack(trackId)
    }

    fun toggleTrackInPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            playlistRepository.toggleTrackInPlaylist(playlistId, track)
        }
    }

    fun createPlaylistAndAddTrack(name: String, track: Track) {
        viewModelScope.launch {
            playlistRepository.createPlaylistAndAddTrack(name, track)
        }
    }
}
