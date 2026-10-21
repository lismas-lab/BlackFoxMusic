package com.blackfox.music.data.repository

import android.content.Context
import android.net.Uri
import com.blackfox.music.data.download.AudioDownloadManager
import com.blackfox.music.data.local.*
import com.blackfox.music.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PlaylistRepository(
    private val context: Context,
    private val musicDao: MusicDao,
    private val downloadManager: AudioDownloadManager
) {

    fun getAllUserPlaylists(): Flow<List<PlaylistWithTracks>> =
        musicDao.getAllUserPlaylistsWithTracks()

    fun getPlaylist(playlistId: Long): Flow<PlaylistWithTracks?> =
        musicDao.getPlaylistWithTracks(playlistId)

    fun getTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>> =
        musicDao.getTracksForPlaylist(playlistId)

    suspend fun moveTrack(playlistId: Long, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        val currentIds = musicDao.getTrackIdsInPlaylistOrdered(playlistId).toMutableList()
        if (fromIndex in currentIds.indices && toIndex in currentIds.indices && fromIndex != toIndex) {
            val moved = currentIds.removeAt(fromIndex)
            currentIds.add(toIndex, moved)
            musicDao.updatePlaylistTracksOrder(playlistId, currentIds)
        }
    }

    fun getPlaylistIdsForTrack(trackId: String): Flow<List<Long>> =
        musicDao.getPlaylistIdsForTrack(trackId)

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        musicDao.insertPlaylist(
            PlaylistEntity(name = name.trim(), isFavoriteSystem = false)
        )
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        musicDao.updatePlaylistName(playlistId, newName.trim())
    }

    suspend fun updatePlaylistCover(playlistId: Long, uri: Uri?) = withContext(Dispatchers.IO) {
        if (uri == null) {
            musicDao.updatePlaylistCover(playlistId, null)
            return@withContext
        }
        val path = saveCustomCover(playlistId, uri)
        if (path != null) {
            musicDao.updatePlaylistCover(playlistId, path)
        }
    }

    suspend fun addTrackToPlaylist(playlistId: Long, track: Track) = withContext(Dispatchers.IO) {
        val existing = musicDao.getTrackById(track.id)
        if (existing == null) {
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
        }
        val minPos = musicDao.getMinOrderPosition(playlistId) ?: 0
        val targetPos = minPos - 1
        musicDao.addTrackToPlaylist(
            PlaylistTrackCrossRef(playlistId = playlistId, trackId = track.id, orderPosition = targetPos)
        )
        // Автоматически скачиваем трек при добавлении в плейлист
        downloadManager.downloadTrack(track)
    }

    /**
     * Переключить наличие трека в плейлисте: если есть -> удалить, если нет -> добавить и скачать.
     * Возвращает true, если трек был добавлен, false - если удален.
     */
    suspend fun toggleTrackInPlaylist(playlistId: Long, track: Track): Boolean = withContext(Dispatchers.IO) {
        val isPresent = musicDao.isTrackInPlaylistDirect(playlistId, track.id)
        if (isPresent) {
            removeTrackFromPlaylist(playlistId, track.id)
            false
        } else {
            addTrackToPlaylist(playlistId, track)
            true
        }
    }

    suspend fun createPlaylistAndAddTrack(name: String, track: Track): Long = withContext(Dispatchers.IO) {
        val newId = createPlaylist(name)
        addTrackToPlaylist(newId, track)
        newId
    }

    suspend fun updatePlaylistOrder(playlistId: Long, trackIds: List<String>) = withContext(Dispatchers.IO) {
        musicDao.updatePlaylistTracksOrder(playlistId, trackIds)
    }

    suspend fun updateFavoritesOrder(trackIds: List<String>) = withContext(Dispatchers.IO) {
        musicDao.updateFavoritesOrder(trackIds)
    }

    suspend fun toggleFavorite(track: Track): Boolean = withContext(Dispatchers.IO) {
        val existing = musicDao.getTrackById(track.id)
        val newFav = !(existing?.isFavorite ?: false)
        if (newFav) {
            val minPos = musicDao.getMinFavoriteOrder() ?: 0
            val targetPos = minPos - 1
            if (existing == null) {
                musicDao.insertTrack(
                    TrackEntity(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        duration = track.duration,
                        thumbnailUrl = track.thumbnailUrl,
                        isFavorite = true,
                        favoriteOrderPosition = targetPos
                    )
                )
            } else {
                musicDao.updateFavorite(track.id, true)
                musicDao.updateFavoriteOrder(track.id, targetPos)
            }
            downloadManager.downloadTrack(track)
        } else {
            musicDao.updateFavorite(track.id, false)
            // Если трека больше нет ни в одном плейлисте и он не загружен пользователем -> удаляем с устройства
            val inAnyPlaylist = musicDao.countPlaylistsContainingTrack(track.id, excludePlaylistId = -1) > 0
            val isUploaded = existing?.isUserUploaded == true
            if (!inAnyPlaylist && !isUploaded) {
                downloadManager.deleteDownloadedTrack(track.id)
                musicDao.deleteTrack(track.id)
            }
        }
        newFav
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) = withContext(Dispatchers.IO) {
        musicDao.removeTrack(playlistId, trackId)

        // Проверяем: входит ли трек в другие плейлисты или в избранное
        val inOtherPlaylists = musicDao.countPlaylistsContainingTrack(trackId, excludePlaylistId = playlistId) > 0
        val trackEntity = musicDao.getTrackById(trackId)
        val isFavorite = trackEntity?.isFavorite == true
        val isUploaded = trackEntity?.isUserUploaded == true

        if (!inOtherPlaylists && !isFavorite && !isUploaded) {
            // Трек не используется в других плейлистах и не в избранном -> очищаем файлы и запись
            downloadManager.deleteDownloadedTrack(trackId)
            musicDao.deleteTrack(trackId)
        }
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        // 1. Получаем список всех треков плейлиста перед удалением
        val trackIds = musicDao.getTrackIdsInPlaylist(playlistId)

        // 2. Удаляем плейлист (каскадно удалятся связи)
        musicDao.deletePlaylistById(playlistId)

        // 3. Для каждого трека: если он не пересекается с другими плейлистами и не в любимом - удаляем из памяти
        for (trackId in trackIds) {
            val inOtherPlaylists = musicDao.countPlaylistsContainingTrack(trackId, excludePlaylistId = playlistId) > 0
            val trackEntity = musicDao.getTrackById(trackId)
            val isFavorite = trackEntity?.isFavorite == true
            val isUploaded = trackEntity?.isUserUploaded == true

            if (!inOtherPlaylists && !isFavorite && !isUploaded) {
                downloadManager.deleteDownloadedTrack(trackId)
                musicDao.deleteTrack(trackId)
            }
        }
    }

    private fun saveCustomCover(playlistId: Long, uri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "playlist_covers")
            if (!dir.exists()) dir.mkdirs()
            val targetFile = File(dir, "playlist_${playlistId}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
