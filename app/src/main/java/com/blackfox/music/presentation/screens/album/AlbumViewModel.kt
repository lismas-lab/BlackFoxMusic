package com.blackfox.music.presentation.screens.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackfox.music.data.download.AudioDownloadManager
import com.blackfox.music.data.local.MusicDao
import com.blackfox.music.data.local.PlaylistEntity
import com.blackfox.music.data.local.PlaylistTrackCrossRef
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.data.network.InnerTubeRepository
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
    private val musicDao: MusicDao
) : ViewModel() {

    val uiState = MutableStateFlow(AlbumUiState())
    val downloadProgress = downloadManager.downloadProgressMap
    val playlists: StateFlow<List<PlaylistEntity>> = musicDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTrackIds: StateFlow<Set<String>> = musicDao.getFavoriteTrackIds()
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
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            musicDao.insertTrack(
                TrackEntity(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    duration = track.duration,
                    thumbnailUrl = track.thumbnailUrl
                )
            )
            musicDao.addTrackToPlaylist(
                PlaylistTrackCrossRef(playlistId = playlistId, trackId = track.id)
            )
        }
    }
}
