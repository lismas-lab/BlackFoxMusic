package com.blackfox.music.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackfox.music.data.download.AudioDownloadManager
import com.blackfox.music.data.local.MusicDao
import com.blackfox.music.data.local.PlaylistEntity
import com.blackfox.music.data.local.TrackEntity
import com.blackfox.music.data.network.InnerTubeRepository
import com.blackfox.music.data.repository.PlaylistRepository
import com.blackfox.music.domain.model.Album
import com.blackfox.music.domain.model.Artist
import com.blackfox.music.domain.model.Track
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SearchFilter(val label: String) {
    ALL("Все"),
    SONGS("Песни"),
    ARTISTS("Артисты"),
    ALBUMS("Альбомы")
}

data class SearchUiState(
    val isLoading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val error: String? = null
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repository: InnerTubeRepository = InnerTubeRepository(),
    private val musicDao: MusicDao,
    private val downloadManager: AudioDownloadManager,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    val query = MutableStateFlow("")
    val selectedFilter = MutableStateFlow(SearchFilter.ALL)
    val uiState = MutableStateFlow(SearchUiState())
    val suggestions = MutableStateFlow<List<String>>(emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = musicDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Реактивный список ID избранных треков
    val favoriteTrackIds: StateFlow<Set<String>> = musicDao.getFavoriteTrackIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Реактивный список ID скачанных треков (для глобальной синхронизации галочек)
    val downloadedTrackIds: StateFlow<Set<String>> = musicDao.getDownloadedTrackIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        query.debounce(350)
            .filter { it.isNotBlank() }
            .distinctUntilChanged()
            .onEach { performSearch(it, selectedFilter.value) }
            .launchIn(viewModelScope)

        query.debounce(150)
            .onEach { q ->
                if (q.trim().length >= 2) {
                    val list = repository.getSearchSuggestions(q.trim())
                    suggestions.value = list
                } else {
                    suggestions.value = emptyList()
                }
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
        if (newQuery.isBlank()) {
            uiState.value = SearchUiState()
            suggestions.value = emptyList()
        }
    }

    fun onSuggestionSelect(suggestion: String) {
        query.value = suggestion
        suggestions.value = emptyList()
        performSearch(suggestion, selectedFilter.value)
    }

    fun clearSuggestions() {
        suggestions.value = emptyList()
    }

    fun onFilterSelect(filter: SearchFilter) {
        selectedFilter.value = filter
        if (query.value.isNotBlank()) {
            performSearch(query.value, filter)
        }
    }

    private fun performSearch(searchQuery: String, filter: SearchFilter) {
        viewModelScope.launch {
            uiState.value = uiState.value.copy(isLoading = true, error = null)
            try {
                when (filter) {
                    SearchFilter.ALL -> {
                        val songs = repository.searchTracks(searchQuery, limit = 10)
                        val artists = repository.searchArtists(searchQuery, limit = 6)
                        val albums = repository.searchAlbums(searchQuery, limit = 8)
                        uiState.value = SearchUiState(
                            isLoading = false,
                            tracks = songs,
                            artists = artists,
                            albums = albums
                        )
                    }
                    SearchFilter.SONGS -> {
                        val songs = repository.searchTracks(searchQuery, limit = 25)
                        uiState.value = SearchUiState(
                            isLoading = false,
                            tracks = songs,
                            artists = emptyList(),
                            albums = emptyList()
                        )
                    }
                    SearchFilter.ARTISTS -> {
                        val artists = repository.searchArtists(searchQuery, limit = 25)
                        uiState.value = SearchUiState(
                            isLoading = false,
                            tracks = emptyList(),
                            artists = artists,
                            albums = emptyList()
                        )
                    }
                    SearchFilter.ALBUMS -> {
                        val albums = repository.searchAlbums(searchQuery, limit = 20)
                        uiState.value = SearchUiState(
                            isLoading = false,
                            tracks = emptyList(),
                            artists = emptyList(),
                            albums = albums
                        )
                    }
                }
            } catch (e: Exception) {
                uiState.value = SearchUiState(
                    isLoading = false,
                    error = e.localizedMessage ?: "Ошибка поиска"
                )
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
                // Автоматическое скачивание при добавлении в Любимое
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
            playlistRepository.toggleTrackInPlaylist(playlistId, track)
        }
    }

    fun createPlaylistAndAddTrack(name: String, track: Track) {
        viewModelScope.launch {
            playlistRepository.createPlaylistAndAddTrack(name, track)
        }
    }
}
