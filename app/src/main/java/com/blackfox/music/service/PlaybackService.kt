package com.blackfox.music.service

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.blackfox.music.BlackFoxApp
import com.blackfox.music.R

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val app = application as BlackFoxApp
        val session = app.playerManager.getOrCreateMediaSession(this)
        mediaSession = session
        addSession(session)

        val notificationProvider = DefaultMediaNotificationProvider(this)
        notificationProvider.setSmallIcon(R.drawable.ic_app_logo_liquid)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val app = application as BlackFoxApp
        if (!app.playerManager.isPlaying.value) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.let {
            removeSession(it)
        }
        mediaSession = null
        super.onDestroy()
    }
}
