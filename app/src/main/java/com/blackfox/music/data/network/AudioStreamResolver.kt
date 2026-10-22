package com.blackfox.music.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import java.util.concurrent.TimeUnit

object AudioStreamResolver {

    private var isInitialized = false
    private val httpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Кэш прямых ссылок на аудиопотоки (YouTube CDN ссылки живут 6 часов, кэшируем на 4 часа)
    data class CachedUrl(val url: String, val timestamp: Long)
    private val streamUrlCache = object : LinkedHashMap<String, CachedUrl>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedUrl>?): Boolean {
            return size > 150
        }
    }
    private const val CACHE_TTL_MS = 4 * 60 * 60 * 1000L // 4 часа

    @Synchronized
    fun init() {
        if (!isInitialized) {
            try {
                NewPipe.init(AppDownloader())
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun resolveAudioStreamUrl(
        videoId: String,
        forDownload: Boolean = false,
        onProgress: ((Int) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        // 0. Проверяем кэш предварительно разрешенных ссылок для мгновенного старта (0 мс)
        synchronized(streamUrlCache) {
            val cached = streamUrlCache[videoId]
            if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
                onProgress?.invoke(25)
                return@withContext cached.url
            }
        }

        // 1. Первоочередно извлекаем нативный аудиопоток напрямую из Google CDN через NewPipeExtractor
        // Нативный поток m4a (AAC 128 kbps) или webm (Opus 160 kbps) стартует мгновенно (< 0.5 сек),
        // весит в 3 раза меньше MP3 (~3-4 МБ вместо 11 МБ), обеспечивает наивысшую скорость Google CDN
        // и не тратит 20-30 секунд на сторонний сервер-конвертер (особенно критично при работе через VPN).
        try {
            init()
            onProgress?.invoke(10)
            val watchUrl = "https://www.youtube.com/watch?v=$videoId"
            val extractor = ServiceList.YouTube.getStreamExtractor(watchUrl) as YoutubeStreamExtractor
            extractor.fetchPage()

            val audioStreams = extractor.audioStreams
            if (!audioStreams.isNullOrEmpty()) {
                // Для загрузки отдаем приоритет нативному M4A (AAC) как самому быстрому и аппаратно аппаратному на Android
                val m4aStream = audioStreams.filter {
                    it.format?.suffix?.equals("m4a", ignoreCase = true) == true ||
                            it.codec?.contains("mp4a", ignoreCase = true) == true
                }.maxByOrNull { it.bitrate }

                val bestAudio = m4aStream ?: audioStreams.maxByOrNull { it.bitrate }
                val url = bestAudio?.content ?: bestAudio?.url
                if (!url.isNullOrBlank()) {
                    synchronized(streamUrlCache) {
                        streamUrlCache[videoId] = CachedUrl(url, System.currentTimeMillis())
                    }
                    onProgress?.invoke(25)
                    return@withContext url
                }
            }

            val hls = extractor.hlsUrl
            if (!hls.isNullOrBlank()) {
                synchronized(streamUrlCache) {
                    streamUrlCache[videoId] = CachedUrl(hls, System.currentTimeMillis())
                }
                onProgress?.invoke(25)
                return@withContext hls
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Для стриминга: если NewPipe не дал ссылку, сразу возвращаем null,
        // чтобы мгновенно включить WebView IFrame движок без зависаний.
        if (!forDownload) {
            return@withContext null
        }

        // 2. Резервный Fallback для скачивания: если NewPipe не смог отдать прямой поток
        // (например, при временных ограничениях CDN), обращаемся к стороннему Loader API
        try {
            val mp3Url = resolveFromLoaderApi(videoId, onProgress)
            if (!mp3Url.isNullOrBlank()) {
                return@withContext mp3Url
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        null
    }

    private suspend fun resolveFromLoaderApi(
        videoId: String,
        onProgress: ((Int) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            onProgress?.invoke(5)
            val initUrl = "https://loader.to/ajax/download.php?format=mp3&url=https://www.youtube.com/watch?v=$videoId"
            val req1 = Request.Builder()
                .url(initUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val resp1 = httpClient.newCall(req1).execute()
            val body1 = resp1.body?.string() ?: return@withContext null
            val json1 = JSONObject(body1)

            val directUrl = json1.optString("download_url")
            if (directUrl.isNotBlank() && directUrl.startsWith("http")) {
                onProgress?.invoke(25)
                return@withContext directUrl
            }

            val progressUrl = json1.optString("progress_url")
            if (progressUrl.isNotBlank() && progressUrl.startsWith("http")) {
                val maxAttempts = 16
                for (i in 0 until maxAttempts) {
                    delay(1200)
                    val prepProgress = 5 + ((i + 1) * 20 / maxAttempts)
                    onProgress?.invoke(prepProgress)

                    val req2 = Request.Builder()
                        .url(progressUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    val resp2 = httpClient.newCall(req2).execute()
                    val body2 = resp2.body?.string() ?: continue
                    val json2 = JSONObject(body2)
                    val readyUrl = json2.optString("download_url")
                    val success = json2.optInt("success", 0)
                    if (readyUrl.isNotBlank() && readyUrl.startsWith("http") && success == 1) {
                        onProgress?.invoke(25)
                        return@withContext readyUrl
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}
