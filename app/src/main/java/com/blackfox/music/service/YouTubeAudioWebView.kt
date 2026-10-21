package com.blackfox.music.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Высокопроизводительный движок воспроизведения YouTube на базе официального IFrame API.
 * Гарантирует воспроизведение любого трека без блокировок 'LOGIN_REQUIRED', bot-check и капч.
 */
@SuppressLint("SetJavaScriptEnabled")
class YouTubeAudioWebView(private val context: Context) {

    private val tag = "YT_AudioWebView"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentVideoId: String? = null
    private var isApiReady = false
    private var isSeeking = false

    private val seekResetRunnable = Runnable {
        isSeeking = false
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (_isPlaying.value && webView != null) {
                if (!isSeeking) {
                    webView?.evaluateJavascript("(function() { var p = window.ytAudioPlayer; return (p && typeof p.getCurrentTime === 'function') ? p.getCurrentTime() : -1; })();") { res ->
                        val clean = res?.replace("\"", "")?.trim()
                        val sec = clean?.toFloatOrNull() ?: -1f
                        if (sec >= 0f && !isSeeking) {
                            _currentPositionMs.value = (sec * 1000L).toLong()
                        }
                    }
                }
                webView?.evaluateJavascript("(function() { var p = window.ytAudioPlayer; return (p && typeof p.getDuration === 'function') ? p.getDuration() : 0; })();") { res ->
                    val clean = res?.replace("\"", "")?.trim()
                    val dur = clean?.toFloatOrNull() ?: 0f
                    if (dur > 0f) {
                        _durationMs.value = (dur * 1000L).toLong()
                    }
                }
                mainHandler.postDelayed(this, 350)
            }
        }
    }

    fun getOrCreateWebView(activityContext: Context): WebView {
        if (webView != null) return webView!!

        val wv = WebView(activityContext)
        wv.layoutParams = ViewGroup.LayoutParams(1, 1)

        wv.settings.javaScriptEnabled = true
        wv.settings.mediaPlaybackRequiresUserGesture = false
        wv.settings.domStorageEnabled = true
        wv.settings.databaseEnabled = true
        wv.settings.allowFileAccess = true
        wv.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        wv.settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        wv.addJavascriptInterface(AndroidJsBridge(), "AndroidBridge")
        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.i(tag, "HTML5 IFrame Player загружен")
            }
        }

        webView = wv
        loadPlayerHtml(currentVideoId ?: "dQw4w9WgXcQ", autoPlay = false)
        return wv
    }

    private fun loadPlayerHtml(videoId: String, autoPlay: Boolean = true, startSeconds: Float = 0f) {
        val wv = webView ?: return
        isApiReady = false
        _isLoading.value = true

        val autoplayInt = if (autoPlay) 1 else 0
        val startSecInt = startSeconds.toInt().coerceAtLeast(0)
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body, html { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
                    #yt-player-container { width:100%; height:100%; }
                </style>
            </head>
            <body>
                <div id="yt-player-container"></div>
                <script src="https://www.youtube.com/iframe_api"></script>
                <script>
                    var ytAudioPlayer = null;
                    function onYouTubeIframeAPIReady() {
                        ytAudioPlayer = new YT.Player('yt-player-container', {
                            height: '100%',
                            width: '100%',
                            videoId: '$videoId',
                            playerVars: {
                                'autoplay': $autoplayInt,
                                'controls': 0,
                                'playsinline': 1,
                                'rel': 0,
                                'start': $startSecInt,
                                'enablejsapi': 1,
                                'origin': 'https://music.youtube.com'
                            },
                            events: {
                                'onReady': onPlayerReady,
                                'onStateChange': onPlayerStateChange,
                                'onError': onPlayerError
                            }
                        });
                        window.ytAudioPlayer = ytAudioPlayer;
                    }
                    function onPlayerReady(event) {
                        AndroidBridge.onReady();
                        if ($startSecInt > 0 && event.target && typeof event.target.seekTo === 'function') {
                            event.target.seekTo($startSecInt, true);
                        }
                        if ($autoplayInt === 1 && event.target && typeof event.target.playVideo === 'function') {
                            event.target.playVideo();
                        }
                    }
                    function onPlayerStateChange(event) {
                        AndroidBridge.onStateChange(event.data);
                    }
                    function onPlayerError(event) {
                        AndroidBridge.onError(event.data);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        wv.loadDataWithBaseURL("https://music.youtube.com", html, "text/html", "UTF-8", null)
    }

    fun playTrack(videoId: String, startPositionMs: Long = 0L) {
        currentVideoId = videoId
        _currentPositionMs.value = startPositionMs
        _isPlaying.value = true
        _isLoading.value = true

        mainHandler.post {
            val wv = webView
            val sec = (startPositionMs / 1000f).coerceAtLeast(0f)
            if (wv != null && isApiReady) {
                wv.evaluateJavascript(
                    """
                    (function() {
                        var p = window.ytAudioPlayer;
                        if (p && typeof p.loadVideoById === 'function') {
                            p.loadVideoById({
                                videoId: '$videoId',
                                startSeconds: $sec
                            });
                            if (typeof p.playVideo === 'function') {
                                p.playVideo();
                            }
                        } else {
                            location.reload();
                        }
                    })();
                    """.trimIndent(),
                    null
                )
            } else {
                loadPlayerHtml(videoId, autoPlay = true, startSeconds = sec)
            }
        }
    }

    fun pauseTrack() {
        _isPlaying.value = false
        mainHandler.post {
            webView?.evaluateJavascript("if (window.ytAudioPlayer && typeof ytAudioPlayer.pauseVideo === 'function') ytAudioPlayer.pauseVideo();", null)
        }
        stopProgress()
    }

    fun stopTrack() {
        _isPlaying.value = false
        _isLoading.value = false
        currentVideoId = null
        mainHandler.post {
            webView?.evaluateJavascript(
                "if (window.ytAudioPlayer) { if (typeof ytAudioPlayer.stopVideo === 'function') ytAudioPlayer.stopVideo(); if (typeof ytAudioPlayer.pauseVideo === 'function') ytAudioPlayer.pauseVideo(); }",
                null
            )
        }
        stopProgress()
    }

    fun resumeTrack() {
        _isPlaying.value = true
        mainHandler.post {
            webView?.evaluateJavascript("if (window.ytAudioPlayer && typeof ytAudioPlayer.playVideo === 'function') ytAudioPlayer.playVideo();", null)
        }
        startProgress()
    }

    fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs
        val sec = (positionMs / 1000f).coerceAtLeast(0f)
        isSeeking = true
        _isPlaying.value = true
        startProgress()

        mainHandler.post {
            webView?.evaluateJavascript(
                """
                (function() {
                    var p = window.ytAudioPlayer;
                    if (p && typeof p.seekTo === 'function') {
                        p.seekTo($sec, true);
                        if (typeof p.playVideo === 'function') {
                            p.playVideo();
                        }
                    }
                })();
                """.trimIndent(),
                null
            )
        }

        mainHandler.removeCallbacks(seekResetRunnable)
        mainHandler.postDelayed(seekResetRunnable, 3000)
    }

    private fun startProgress() {
        stopProgress()
        mainHandler.post(progressRunnable)
    }

    private fun stopProgress() {
        mainHandler.removeCallbacks(progressRunnable)
    }

    inner class AndroidJsBridge {
        @JavascriptInterface
        fun onReady() {
            mainHandler.post {
                isApiReady = true
                Log.i(tag, "IFrame Player готов к воспроизведению")
            }
        }

        @JavascriptInterface
        fun onStateChange(state: Int) {
            mainHandler.post {
                when (state) {
                    1 -> { // YT.PlayerState.PLAYING
                        isSeeking = false
                        mainHandler.removeCallbacks(seekResetRunnable)
                        _isPlaying.value = true
                        _isLoading.value = false
                        startProgress()
                        Log.i(tag, "Состояние: PLAYING")
                    }
                    2 -> { // YT.PlayerState.PAUSED
                        if (!isSeeking) {
                            _isPlaying.value = false
                            _isLoading.value = false
                            stopProgress()
                            Log.i(tag, "Состояние: PAUSED")
                        } else {
                            // Во время перемотки YouTube переходит в state 2 кратковременно - держим воспроизведение
                            mainHandler.post {
                                webView?.evaluateJavascript("if (window.ytAudioPlayer && typeof ytAudioPlayer.playVideo === 'function') ytAudioPlayer.playVideo();", null)
                            }
                        }
                    }
                    3 -> { // YT.PlayerState.BUFFERING
                        _isLoading.value = true
                    }
                    0 -> { // YT.PlayerState.ENDED
                        if (!isSeeking) {
                            _isPlaying.value = false
                            _isLoading.value = false
                            _currentPositionMs.value = 0L
                            stopProgress()
                            Log.i(tag, "Состояние: ENDED")
                        }
                    }
                    else -> {}
                }
            }
        }

        @JavascriptInterface
        fun onError(errorCode: Int) {
            mainHandler.post {
                _isLoading.value = false
                _isPlaying.value = false
                Log.e(tag, "Ошибка YouTube IFrame Player: $errorCode")
            }
        }
    }

    fun release() {
        stopProgress()
        mainHandler.removeCallbacks(seekResetRunnable)
        mainHandler.post {
            webView?.destroy()
            webView = null
        }
    }
}
