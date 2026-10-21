package com.blackfox.music.domain.model

data class Track(
    val id: String,                  // YouTube Video ID
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val duration: String = "",
    val durationSeconds: Long = 0,
    val thumbnailUrl: String = "",
    val streamUrl: String? = null,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val isUserUploaded: Boolean = false
)

data class Album(
    val browseId: String,            // browseId (MPREb_...)
    val title: String,
    val artist: String,
    val type: String = "Альбом",
    val year: String = "",
    val coverUrl: String = "",
    val trackCount: Int = 0,
    val tracks: List<Track> = emptyList()
)

data class Artist(
    val channelId: String,           // UC...
    val name: String,
    val subscribers: String = "",
    val avatarUrl: String = "",
    val topTracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val similarArtists: List<Artist> = emptyList(),
    val allSongsBrowseId: String? = null
)

data class Playlist(
    val id: Long = 0,
    val name: String,
    val isFavorite: Boolean = false,
    val customCoverUri: String? = null,
    val trackCount: Int = 0,
    val tracks: List<Track> = emptyList()
)
