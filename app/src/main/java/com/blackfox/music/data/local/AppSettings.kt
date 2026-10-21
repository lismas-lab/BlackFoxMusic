package com.blackfox.music.data.local

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AccentTheme(
    val key: String,
    val title: String,
    val emoji: String,
    val color: Color,
    val lightColor: Color
) {
    FOX_ORANGE("fox_orange", "Fox Orange", "🦊", Color(0xFFFF5E1E), Color(0xFFFF7A38)),
    CYBER_CYAN("cyber_cyan", "Cyber Cyan / Ice", "❄️", Color(0xFF00E5FF), Color(0xFF80F3FF)),
    NEON_VIOLET("neon_violet", "Neon Violet", "💜", Color(0xFFB388FF), Color(0xFFD1B3FF)),
    ACID_LIME("acid_lime", "Acid Lime", "⚡", Color(0xFF76FF03), Color(0xFFA2FF54)),
    MONOCHROME("monochrome", "Monochrome Glass", "🤍", Color(0xFFF5F5F7), Color(0xFFFFFFFF));

    val primary: Color get() = color

    companion object {
        fun fromKey(key: String): AccentTheme = entries.find { it.key == key } ?: FOX_ORANGE
    }
}

val LocalAccentTheme = compositionLocalOf { AccentTheme.FOX_ORANGE }

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("blackfox_preferences", Context.MODE_PRIVATE)

    private val _currentAccent = MutableStateFlow(
        AccentTheme.fromKey(prefs.getString(KEY_ACCENT, AccentTheme.FOX_ORANGE.key) ?: AccentTheme.FOX_ORANGE.key)
    )
    val currentAccent: StateFlow<AccentTheme> = _currentAccent.asStateFlow()

    private val _streamQuality = MutableStateFlow(
        prefs.getString(KEY_STREAM_QUALITY, "standard") ?: "standard"
    )
    val streamQuality: StateFlow<String> = _streamQuality.asStateFlow()

    private val _downloadQuality = MutableStateFlow(
        prefs.getString(KEY_DOWNLOAD_QUALITY, "320") ?: "320"
    )
    val downloadQuality: StateFlow<String> = _downloadQuality.asStateFlow()

    fun setAccent(theme: AccentTheme) {
        prefs.edit().putString(KEY_ACCENT, theme.key).apply()
        _currentAccent.value = theme
    }

    fun setStreamQuality(quality: String) {
        prefs.edit().putString(KEY_STREAM_QUALITY, quality).apply()
        _streamQuality.value = quality
    }

    fun setDownloadQuality(quality: String) {
        prefs.edit().putString(KEY_DOWNLOAD_QUALITY, quality).apply()
        _downloadQuality.value = quality
    }

    companion object {
        private const val KEY_ACCENT = "app_accent_theme"
        private const val KEY_STREAM_QUALITY = "audio_stream_quality"
        private const val KEY_DOWNLOAD_QUALITY = "audio_download_quality"
    }
}
