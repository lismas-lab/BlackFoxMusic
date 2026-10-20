package com.blackfox.music.data.download

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class DownloadProgress(
    val trackId: String,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val error: String? = null
)
