package com.blackfox.music.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import java.util.concurrent.TimeUnit

object AudioStreamResolver {

    private var isInitialized = false
    private val httpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Кэш прямых ссылок на аудиопотоки (YouTube CDN ссылки живут 6 часов, кэшируем на 4 часа)
    data class CachedUrl(val url: String, val timestamp: Long)
    private val streamUrlCache = object : LinkedHashMap<String, CachedUrl>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedUrl>?): Boolean {
            return size > 250
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
        quality: String = if (forDownload) "320" else "standard",
        onProgress: ((Int) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        // 0. Проверяем кэш предварительно разрешенных ссылок для мгновенного старта (0 мс)
        val cacheKey = "$videoId:$quality:$forDownload"
        synchronized(streamUrlCache) {
            val cached = streamUrlCache[cacheKey]
            if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
                onProgress?.invoke(25)
                return@withContext cached.url
            }
        }

        // 1. ПЕРВООЧЕРЕДНО: Сверхбыстрый прямой опрос InnerTube API (клиент ANDROID_VR).
        // Отдает прямые незашифрованные Google CDN ссылки (itag 140 m4a / itag 251 opus)
        // за рекордные 300-600 мс БЕЗ парсинга HTML, тяжелых JS и сторонних конвертеров.
        val directUrl = resolveDirectInnerTubeStream(videoId, quality)
        if (!directUrl.isNullOrBlank()) {
            synchronized(streamUrlCache) {
                streamUrlCache[cacheKey] = CachedUrl(directUrl, System.currentTimeMillis())
            }
            onProgress?.invoke(25)
            return@withContext directUrl
        }

        // 2. ВТОРАЯ ОЧЕРЕДЬ: NewPipeExtractor со СТРОГИМ таймаутом 2 секунды.
        // Без жесткого таймаута устаревший парсер NewPipe при сетевых сбоях через VPN
        // блокирует поток на 35 секунд, создавая невыносимую задержку для пользователя.
        try {
            val newPipeUrl = withTimeoutOrNull(2000L) {
                init()
                onProgress?.invoke(10)
                val watchUrl = "https://www.youtube.com/watch?v=$videoId"
                val extractor = ServiceList.YouTube.getStreamExtractor(watchUrl) as YoutubeStreamExtractor
                extractor.fetchPage()

                val audioStreams = extractor.audioStreams
                if (!audioStreams.isNullOrEmpty()) {
                    val selectedAudio = selectAudioStream(audioStreams, quality, forDownload)
                    selectedAudio?.content ?: selectedAudio?.url
                } else {
                    extractor.hlsUrl
                }
            }

            if (!newPipeUrl.isNullOrBlank()) {
                synchronized(streamUrlCache) {
                    streamUrlCache[cacheKey] = CachedUrl(newPipeUrl, System.currentTimeMillis())
                }
                onProgress?.invoke(25)
                return@withContext newPipeUrl
            }
        } catch (e: Exception) {
            // Быстро переходим к проверенному резервному конвертеру
        }

        // 3. ТРЕТЬЯ ОЧЕРЕДЬ: Резервный Fallback через Loader API.
        // Для потокового онлайн-воспроизведения (!forDownload) ВСЕГДА используем m4a,
        // так как он готовится сервером за 2-3 секунды БЕЗ тяжелого ffmpeg-перекодирования в MP3.
        try {
            val fallbackUrl = resolveFromLoaderApi(videoId, quality, forDownload, onProgress)
            if (!fallbackUrl.isNullOrBlank()) {
                synchronized(streamUrlCache) {
                    streamUrlCache[cacheKey] = CachedUrl(fallbackUrl, System.currentTimeMillis())
                }
                return@withContext fallbackUrl
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        null
    }

    /**
     * Сверхбыстрый прямой опрос YouTube InnerTube API (клиент ANDROID_VR).
     * Возвращает прямую ссылку на поток Google CDN без шифрования за ~400-600 мс.
     */
    private fun resolveDirectInnerTubeStream(videoId: String, quality: String): String? {
        return try {
            val endpoint = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.60.19")
                        put("deviceModel", "Quest 3")
                        put("osName", "Android")
                        put("osVersion", "12")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("videoId", videoId)
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(endpoint)
                .post(body)
                .header("User-Agent", "Mozilla/5.0 (Android 12; Mobile; rv:68.0) Gecko/68.0 Firefox/68.0")
                .header("Content-Type", "application/json")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return null
            val respBody = resp.body?.string() ?: return null
            val json = JSONObject(respBody)

            val status = json.optJSONObject("playabilityStatus")?.optString("status")
            if (status != "OK") return null

            val streamingData = json.optJSONObject("streamingData") ?: return null
            val formats = streamingData.optJSONArray("adaptiveFormats") ?: return null

            data class DirectCandidate(val itag: Int, val mimeType: String, val bitrate: Int, val url: String)
            val candidates = mutableListOf<DirectCandidate>()

            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                val mime = f.optString("mimeType", "")
                if (!mime.startsWith("audio/")) continue
                val streamUrl = f.optString("url", "")
                if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                    val itag = f.optInt("itag", 0)
                    val bitrate = f.optInt("bitrate", 0)
                    candidates.add(DirectCandidate(itag, mime, bitrate, streamUrl))
                }
            }

            if (candidates.isEmpty()) return null

            val m4aCandidates = candidates.filter { it.mimeType.contains("audio/mp4") }

            val chosen = when (quality) {
                "low" -> candidates.minByOrNull { it.bitrate }
                "192", "standard" -> m4aCandidates.maxByOrNull { it.bitrate } ?: candidates.maxByOrNull { it.bitrate }
                else -> candidates.maxByOrNull { it.bitrate }
            }

            chosen?.url
        } catch (e: Exception) {
            null
        }
    }

    private fun selectAudioStream(
        audioStreams: List<org.schabi.newpipe.extractor.stream.AudioStream>,
        quality: String,
        forDownload: Boolean
    ): org.schabi.newpipe.extractor.stream.AudioStream? {
        if (audioStreams.isEmpty()) return null

        fun toKbps(s: org.schabi.newpipe.extractor.stream.AudioStream): Int {
            return if (s.bitrate > 1000) s.bitrate / 1000 else s.bitrate
        }

        val m4aStreams = audioStreams.filter {
            it.format?.suffix?.equals("m4a", ignoreCase = true) == true ||
                    it.codec?.contains("mp4a", ignoreCase = true) == true
        }

        return when (quality) {
            "low" -> {
                val lowStreams = audioStreams.filter { toKbps(it) in 30..90 }
                lowStreams.minByOrNull { it.bitrate }
                    ?: audioStreams.minByOrNull { it.bitrate }
            }
            "192", "standard" -> {
                val m4aStandard = m4aStreams.maxByOrNull { it.bitrate }
                m4aStandard
                    ?: audioStreams.filter { toKbps(it) in 100..192 }.maxByOrNull { it.bitrate }
                    ?: audioStreams.maxByOrNull { it.bitrate }
            }
            "256" -> {
                val highStreams = audioStreams.filter { toKbps(it) >= 200 }
                highStreams.maxByOrNull { it.bitrate }
                    ?: audioStreams.maxByOrNull { it.bitrate }
            }
            "320", "high" -> {
                audioStreams.maxByOrNull { it.bitrate }
            }
            else -> {
                m4aStreams.maxByOrNull { it.bitrate } ?: audioStreams.maxByOrNull { it.bitrate }
            }
        }
    }

    private suspend fun resolveFromLoaderApi(
        videoId: String,
        quality: String = "320",
        forDownload: Boolean = false,
        onProgress: ((Int) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            onProgress?.invoke(5)
            // При онлайн-стриминге (!forDownload) ВСЕГДА используем m4a:
            // YouTube уже имеет готовый AAC 128 kbps (размер ~3.5 МБ),
            // поэтому сервер отдает его моментально БЕЗ 30-секундного транскодинга ffmpeg!
            val format = if (!forDownload) {
                "m4a"
            } else {
                when (quality) {
                    "320", "256", "high" -> "mp3"
                    else -> "m4a"
                }
            }
            val initUrl = "https://loader.to/ajax/download.php?format=$format&url=https://www.youtube.com/watch?v=$videoId"
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
                val maxAttempts = 20
                for (i in 0 until maxAttempts) {
                    delay(650) // Быстрый интервал опроса 650 мс для мгновенного старта
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
