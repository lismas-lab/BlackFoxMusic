package com.blackfox.music.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object UserUploadedAudioImporter {

    suspend fun importAudioUris(
        context: Context,
        uris: List<Uri>,
        musicDao: MusicDao
    ): List<TrackEntity> = withContext(Dispatchers.IO) {
        val storageDir = File(context.filesDir, "uploaded_audio").apply { if (!exists()) mkdirs() }
        val importedList = mutableListOf<TrackEntity>()

        for (uri in uris) {
            try {
                val displayName = getFileName(context, uri) ?: "audio_${System.currentTimeMillis()}.mp3"
                val fileExt = displayName.substringAfterLast('.', "mp3")
                val uniqueId = "user_" + UUID.randomUUID().toString().replace("-", "").take(16)
                val targetAudioFile = File(storageDir, "$uniqueId.$fileExt")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetAudioFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: continue

                // Извлечение метаданных (ID3 тегов)
                var title = displayName.substringBeforeLast('.')
                var artist = "Неизвестный исполнитель"
                var album = "Загруженные аудио"
                var durationStr = "0:00"
                var coverPath: String? = null

                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(targetAudioFile.absolutePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let {
                        title = it
                    }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let {
                        artist = it
                    }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let {
                        album = it
                    }

                    val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    if (durMs > 0) {
                        val totalSec = durMs / 1000
                        val min = totalSec / 60
                        val sec = totalSec % 60
                        durationStr = "$min:${if (sec < 10) "0$sec" else "$sec"}"
                    }

                    val pictureBytes = retriever.embeddedPicture
                    if (pictureBytes != null && pictureBytes.isNotEmpty()) {
                        val coverFile = File(storageDir, "${uniqueId}_cover.jpg")
                        FileOutputStream(coverFile).use { it.write(pictureBytes) }
                        coverPath = coverFile.absolutePath
                    }
                } catch (e: Exception) {
                    // Fallback на базовые значения
                } finally {
                    try { retriever.release() } catch (ignored: Exception) {}
                }

                val track = TrackEntity(
                    id = uniqueId,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = durationStr,
                    thumbnailUrl = coverPath ?: "",
                    localFilePath = targetAudioFile.absolutePath,
                    isDownloaded = true,
                    downloadedAt = System.currentTimeMillis(),
                    isFavorite = false,
                    isUserUploaded = true
                )

                musicDao.insertTrack(track)
                importedList.add(track)
            } catch (e: Exception) {
                // пропускаем ошибочный файл
            }
        }

        importedList
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) return cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                // fallback
            }
        }
        return uri.path?.substringAfterLast('/')
    }
}