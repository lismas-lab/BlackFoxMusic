package com.blackfox.music.data.local

import androidx.room.*
import com.blackfox.music.domain.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val duration: String = "",
    val durationSeconds: Long = 0,
    val thumbnailUrl: String = "",
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false,
    val downloadedAt: Long? = null,
    val isFavorite: Boolean = false,
    val isUserUploaded: Boolean = false,
    val favoriteOrderPosition: Int = 0
)

fun TrackEntity.toDomain(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    artistId = artistId,
    album = album,
    albumId = albumId,
    duration = duration,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    streamUrl = null,
    isDownloaded = isDownloaded,
    localFilePath = localFilePath,
    isUserUploaded = isUserUploaded
)

@Entity(tableName = "followed_artists")
data class ArtistEntity(
    @PrimaryKey val channelId: String,
    val name: String,
    val thumbnailUrl: String = "",
    val subscribers: String = "",
    val followedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val isFavoriteSystem: Boolean = false,
    val customCoverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("trackId")]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val addedAt: Long = System.currentTimeMillis(),
    val orderPosition: Int = 0
)

data class PlaylistWithTracks(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "playlistId",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistTrackCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "trackId"
        )
    )
    val tracks: List<TrackEntity>
)
