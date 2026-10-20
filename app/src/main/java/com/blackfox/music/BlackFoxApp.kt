package com.blackfox.music

import android.app.Application
import com.blackfox.music.data.download.AudioDownloadManager
import com.blackfox.music.data.local.MusicDatabase
import com.blackfox.music.service.AudioPlayerManager

class BlackFoxApp : Application() {

    lateinit var database: MusicDatabase
        private set

    lateinit var downloadManager: AudioDownloadManager
        private set

    lateinit var playerManager: AudioPlayerManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = MusicDatabase.getInstance(this)
        downloadManager = AudioDownloadManager(this, database.musicDao())
        playerManager = AudioPlayerManager(this)
    }
}
