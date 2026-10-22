package com.blackfox.music

import android.app.Application
import com.blackfox.music.data.download.AudioDownloadManager
import com.blackfox.music.data.local.AppSettings
import com.blackfox.music.data.local.MusicDatabase
import com.blackfox.music.data.repository.PlaylistRepository
import com.blackfox.music.service.AudioPlayerManager

class BlackFoxApp : Application() {

    lateinit var database: MusicDatabase
        private set

    lateinit var downloadManager: AudioDownloadManager
        private set

    lateinit var playlistRepository: PlaylistRepository
        private set

    lateinit var playerManager: AudioPlayerManager
        private set

    lateinit var appSettings: AppSettings
        private set

    override fun onCreate() {
        super.onCreate()
        appSettings = AppSettings(this)
        database = MusicDatabase.getInstance(this)
        downloadManager = AudioDownloadManager(this, database.musicDao(), appSettings)
        playlistRepository = PlaylistRepository(this, database.musicDao(), downloadManager)
        playerManager = AudioPlayerManager(this, appSettings)
    }
}
