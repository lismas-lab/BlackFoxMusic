package com.blackfox.music.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // --- ТРЕКИ ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 ORDER BY downloadedAt DESC")
    fun getDownloadedTracks(): Flow<List<TrackEntity>>

    @Query("UPDATE tracks SET isDownloaded = :isDownloaded, localFilePath = :filePath, downloadedAt = :downloadedAt WHERE id = :trackId")
    suspend fun updateDownloadStatus(trackId: String, isDownloaded: Boolean, filePath: String?, downloadedAt: Long?)

    // --- ИЗБРАННОЕ ---
    @Query("SELECT id FROM tracks WHERE isFavorite = 1")
    fun getFavoriteTrackIds(): Flow<List<String>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY rowid DESC")
    fun getFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun updateFavorite(trackId: String, isFavorite: Boolean)

    // --- ПЛЕЙЛИСТЫ ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY isFavoriteSystem DESC, createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE isFavoriteSystem = 1 LIMIT 1")
    suspend fun getFavoritesPlaylist(): PlaylistEntity?

    @Transaction
    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    fun getPlaylistWithTracks(playlistId: Long): Flow<PlaylistWithTracks?>

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    // --- СВЯЗЬ ТРЕКОВ И ПЛЕЙЛИСТОВ ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrackToPlaylist(crossRef: PlaylistTrackCrossRef)

    @Delete
    suspend fun removeTrackFromPlaylist(crossRef: PlaylistTrackCrossRef)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId)")
    fun isTrackInPlaylist(playlistId: Long, trackId: String): Flow<Boolean>

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: String)
}
