package com.blackfox.music.data.local

import android.content.Context
import android.net.Uri
import com.blackfox.music.data.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object MediaLibraryBackupManager {

    suspend fun exportLibrary(
        context: Context,
        destinationUri: Uri,
        favorites: List<TrackEntity>,
        playlists: List<PlaylistWithTracks>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject().apply {
                put("version", 1)
                put("app", "BlackFoxMusic")
                put("exportedAt", System.currentTimeMillis())

                val favArray = JSONArray()
                favorites.forEach { track ->
                    val obj = JSONObject().apply {
                        put("id", track.id)
                        put("title", track.title)
                        put("artist", track.artist)
                        put("album", track.album ?: "")
                        put("duration", track.duration)
                        put("thumbnailUrl", track.thumbnailUrl)
                        put("order", track.favoriteOrderPosition)
                    }
                    favArray.put(obj)
                }
                put("favorites", favArray)

                val plArray = JSONArray()
                playlists.forEach { plWithTracks ->
                    val plObj = JSONObject().apply {
                        put("name", plWithTracks.playlist.name)
                        put("coverUri", plWithTracks.playlist.customCoverUri ?: "")
                        val tracksArray = JSONArray()
                        plWithTracks.tracks.forEachIndexed { idx, track ->
                            val tObj = JSONObject().apply {
                                put("id", track.id)
                                put("title", track.title)
                                put("artist", track.artist)
                                put("album", track.album ?: "")
                                put("duration", track.duration)
                                put("thumbnailUrl", track.thumbnailUrl)
                                put("order", idx)
                            }
                            tracksArray.put(tObj)
                        }
                        put("tracks", tracksArray)
                    }
                    plArray.put(plObj)
                }
                put("playlists", plArray)
            }

            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
                output.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importLibrary(
        context: Context,
        sourceUri: Uri,
        musicDao: MusicDao,
        playlistRepository: PlaylistRepository
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val stringBuilder = StringBuilder()
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line).append("\n")
                        line = reader.readLine()
                    }
                }
            }

            val root = JSONObject(stringBuilder.toString())
            var favRestored = 0
            var plRestored = 0

            // 1. Восстанавливаем Любимые треки
            val favArray = root.optJSONArray("favorites")
            if (favArray != null) {
                for (i in 0 until favArray.length()) {
                    val obj = favArray.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isBlank()) continue

                    val existing = musicDao.getTrackById(id)
                    val order = obj.optInt("order", i)
                    if (existing == null) {
                        musicDao.insertTrack(
                            TrackEntity(
                                id = id,
                                title = obj.optString("title"),
                                artist = obj.optString("artist"),
                                album = obj.optString("album").ifBlank { null },
                                duration = obj.optString("duration"),
                                thumbnailUrl = obj.optString("thumbnailUrl"),
                                isFavorite = true,
                                favoriteOrderPosition = order
                            )
                        )
                    } else {
                        musicDao.updateFavorite(id, true)
                        musicDao.updateFavoriteOrder(id, order)
                    }
                    favRestored++
                }
            }

            // 2. Восстанавливаем Плейлисты
            val plArray = root.optJSONArray("playlists")
            if (plArray != null) {
                for (i in 0 until plArray.length()) {
                    val plObj = plArray.getJSONObject(i)
                    val plName = plObj.optString("name", "Импортированный плейлист")
                    val coverUriStr = plObj.optString("coverUri")

                    val playlistId = playlistRepository.createPlaylist(plName)
                    if (coverUriStr.isNotBlank()) {
                        try {
                            playlistRepository.updatePlaylistCover(playlistId, Uri.parse(coverUriStr))
                        } catch (e: Exception) {
                            // ignore cover error
                        }
                    }

                    val tracksArray = plObj.optJSONArray("tracks") ?: JSONArray()
                    for (j in 0 until tracksArray.length()) {
                        val tObj = tracksArray.getJSONObject(j)
                        val id = tObj.optString("id")
                        if (id.isBlank()) continue

                        val existing = musicDao.getTrackById(id)
                        if (existing == null) {
                            musicDao.insertTrack(
                                TrackEntity(
                                    id = id,
                                    title = tObj.optString("title"),
                                    artist = tObj.optString("artist"),
                                    album = tObj.optString("album").ifBlank { null },
                                    duration = tObj.optString("duration"),
                                    thumbnailUrl = tObj.optString("thumbnailUrl"),
                                    isFavorite = false
                                )
                            )
                        }
                        val order = tObj.optInt("order", j)
                        musicDao.addTrackToPlaylist(
                            PlaylistTrackCrossRef(
                                playlistId = playlistId,
                                trackId = id,
                                orderPosition = order
                            )
                        )
                    }
                    plRestored++
                }
            }

            ImportResult(success = true, favoritesCount = favRestored, playlistsCount = plRestored)
        } catch (e: Exception) {
            e.printStackTrace()
            ImportResult(success = false, errorMessage = e.localizedMessage)
        }
    }
}

data class ImportResult(
    val success: Boolean,
    val favoritesCount: Int = 0,
    val playlistsCount: Int = 0,
    val errorMessage: String? = null
)
