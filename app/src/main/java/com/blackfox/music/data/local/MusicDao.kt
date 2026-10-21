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

    @Query("SELECT id FROM tracks WHERE isDownloaded = 1")
    fun getDownloadedTrackIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM tracks WHERE isDownloaded = 1")
    fun getDownloadedCount(): Flow<Int>

    @Query("UPDATE tracks SET isDownloaded = :isDownloaded, localFilePath = :filePath, downloadedAt = :downloadedAt WHERE id = :trackId")
    suspend fun updateDownloadStatus(trackId: String, isDownloaded: Boolean, filePath: String?, downloadedAt: Long?)

    @Query("UPDATE tracks SET isDownloaded = 0, localFilePath = NULL, downloadedAt = NULL")
    suspend fun resetAllDownloadedStatus()

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrack(trackId: String)

    // --- ИЗБРАННОЕ ---
    @Query("SELECT id FROM tracks WHERE isFavorite = 1")
    fun getFavoriteTrackIds(): Flow<List<String>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY favoriteOrderPosition ASC, rowid DESC")
    fun getFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE isFavorite = 1")
    fun getFavoritesCount(): Flow<Int>

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun updateFavorite(trackId: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET favoriteOrderPosition = :order WHERE id = :trackId")
    suspend fun updateFavoriteOrder(trackId: String, order: Int)

    @Transaction
    suspend fun updateFavoritesOrder(trackIdsInOrder: List<String>) {
        trackIdsInOrder.forEachIndexed { index, trackId ->
            updateFavoriteOrder(trackId, index)
        }
    }

    @Query("SELECT MAX(favoriteOrderPosition) FROM tracks WHERE isFavorite = 1")
    suspend fun getMaxFavoriteOrder(): Int?

    @Query("SELECT MIN(favoriteOrderPosition) FROM tracks WHERE isFavorite = 1")
    suspend fun getMinFavoriteOrder(): Int?

    // --- ЗАГРУЖЕННЫЕ ПОЛЬЗОВАТЕЛЕМ ТРЕКИ ---
    @Query("SELECT * FROM tracks WHERE isUserUploaded = 1 ORDER BY rowid DESC")
    fun getUserUploadedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE isUserUploaded = 1")
    fun getUserUploadedCount(): Flow<Int>

    // --- ПЛЕЙЛИСТЫ ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY isFavoriteSystem DESC, createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE isFavoriteSystem = 0 ORDER BY createdAt DESC")
    fun getAllUserPlaylistsWithTracks(): Flow<List<PlaylistWithTracks>>

    @Query("SELECT * FROM playlists WHERE isFavoriteSystem = 1 LIMIT 1")
    suspend fun getFavoritesPlaylist(): PlaylistEntity?

    @Transaction
    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    fun getPlaylistWithTracks(playlistId: Long): Flow<PlaylistWithTracks?>

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Query("UPDATE playlists SET name = :name WHERE playlistId = :playlistId")
    suspend fun updatePlaylistName(playlistId: Long, name: String)

    @Query("UPDATE playlists SET customCoverUri = :coverUri WHERE playlistId = :playlistId")
    suspend fun updatePlaylistCover(playlistId: Long, coverUri: String?)

    // --- СВЯЗЬ ТРЕКОВ И ПЛЕЙЛИСТОВ ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrackToPlaylist(crossRef: PlaylistTrackCrossRef)

    @Delete
    suspend fun removeTrackFromPlaylist(crossRef: PlaylistTrackCrossRef)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId)")
    fun isTrackInPlaylist(playlistId: Long, trackId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId)")
    suspend fun isTrackInPlaylistDirect(playlistId: Long, trackId: String): Boolean

    @Query("SELECT playlistId FROM playlist_track_cross_ref WHERE trackId = :trackId")
    fun getPlaylistIdsForTrack(trackId: String): Flow<List<Long>>

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: String)

    @Query("SELECT trackId FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    suspend fun getTrackIdsInPlaylist(playlistId: Long): List<String>

    @Query("""
        SELECT t.* FROM tracks t 
        INNER JOIN playlist_track_cross_ref r ON t.id = r.trackId 
        WHERE r.playlistId = :playlistId 
        ORDER BY r.orderPosition ASC, r.addedAt ASC
    """)
    fun getTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT trackId FROM playlist_track_cross_ref WHERE playlistId = :playlistId ORDER BY orderPosition ASC, addedAt ASC")
    suspend fun getTrackIdsInPlaylistOrdered(playlistId: Long): List<String>

    @Query("SELECT COALESCE(MAX(orderPosition), -1) + 1 FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    suspend fun getNextOrderPosition(playlistId: Long): Int

    @Query("SELECT MIN(orderPosition) FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    suspend fun getMinOrderPosition(playlistId: Long): Int?

    @Query("UPDATE playlist_track_cross_ref SET orderPosition = :orderPosition WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updateTrackOrder(playlistId: Long, trackId: String, orderPosition: Int)

    @Transaction
    suspend fun updatePlaylistTracksOrder(playlistId: Long, trackIdsInOrder: List<String>) {
        trackIdsInOrder.forEachIndexed { index, trackId ->
            updateTrackOrder(playlistId, trackId, index)
        }
    }

    @Query("SELECT COUNT(*) FROM playlist_track_cross_ref WHERE trackId = :trackId AND playlistId != :excludePlaylistId")
    suspend fun countPlaylistsContainingTrack(trackId: String, excludePlaylistId: Long): Int

    // --- ПОДПИСКИ НА АРТИСТОВ ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun followArtist(artist: ArtistEntity)

    @Query("DELETE FROM followed_artists WHERE channelId = :channelId")
    suspend fun unfollowArtist(channelId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM followed_artists WHERE channelId = :channelId)")
    fun isArtistFollowed(channelId: String): Flow<Boolean>

    @Query("SELECT * FROM followed_artists ORDER BY followedAt DESC")
    fun getAllFollowedArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT COUNT(*) FROM followed_artists")
    fun getFollowedArtistsCount(): Flow<Int>
}
