package com.blackfox.music.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class VkTrack(
    val id: Long,
    val ownerId: Long,
    val title: String,
    val artist: String,
    val durationSeconds: Int,
    val album: String = "",
    val coverUrl: String = ""
)

data class VkPlaylist(
    val id: Long,
    val ownerId: Long,
    val title: String,
    val accessHash: String = "",
    val tracks: List<VkTrack> = emptyList()
)

data class VkUserData(
    val userId: Long,
    val screenName: String,
    val favoriteTracks: List<VkTrack>,
    val playlists: List<VkPlaylist>
)

/**
 * Репозиторий для получения открытых аудиозаписей и плейлистов пользователя VK
 * без необходимости авторизации через аккаунт.
 */
class VkRepository {

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val windows1251 = Charset.forName("windows-1251")

    // Простой потокобезопасный CookieJar в памяти для сохранения гостевых кук VK (remixstlid, remixlgck и т.д.)
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val host = url.topPrivateDomain() ?: url.host
                val list = cookieStore.getOrPut(host) { mutableListOf() }
                synchronized(list) {
                    cookies.forEach { newCookie ->
                        list.removeAll { it.name == newCookie.name }
                        list.add(newCookie)
                    }
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val host = url.topPrivateDomain() ?: url.host
                val list = cookieStore[host] ?: return emptyList()
                return synchronized(list) { list.toList() }
            }
        })
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Инициализация гостевой сессии VK (получение необходимых cookie)
     */
    private suspend fun ensureGuestSession() = withContext(Dispatchers.IO) {
        val host = "vk.com"
        if (cookieStore[host].isNullOrEmpty()) {
            val req = Request.Builder()
                .url("https://vk.com")
                .header("User-Agent", userAgent)
                .build()
            client.newCall(req).execute().use { /* закрываем ответ, куки сохранены cookieJar */ }
        }
    }

    /**
     * Разрешает переданный пользователем ввод (числовой id, id123, @username, ссылка на профиль)
     * в числовой VK user_id
     */
    suspend fun resolveUserId(rawInput: String): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            var cleaned = rawInput.trim()
            if (cleaned.startsWith("@")) cleaned = cleaned.substring(1)
            cleaned = cleaned.replace("https://", "").replace("http://", "")
            cleaned = cleaned.replace("vk.com/", "").replace("m.vk.com/", "")
            cleaned = cleaned.replace("vk.ru/", "").replace("m.vk.ru/", "")
            cleaned = cleaned.trimEnd('/')

            if (cleaned.isBlank()) {
                throw IllegalArgumentException("Пожалуйста, введите ваш VK ID или имя пользователя")
            }

            // Если это просто число
            cleaned.toLongOrNull()?.let { return@runCatching it }

            // Если это id123456789
            val idPattern = Pattern.compile("^id(\\d+)$", Pattern.CASE_INSENSITIVE)
            val idMatcher = idPattern.matcher(cleaned)
            if (idMatcher.matches()) {
                return@runCatching idMatcher.group(1)!!.toLong()
            }

            // Это никнейм (screen_name). Запрашиваем страницу профиля VK и ищем object_id
            ensureGuestSession()
            val req = Request.Builder()
                .url("https://vk.com/$cleaned")
                .header("User-Agent", userAgent)
                .build()

            val pageHtml = client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Ошибка запроса к VK: код ${response.code}")
                }
                val bytes = response.body?.bytes() ?: byteArrayOf()
                String(bytes, windows1251)
            }

            // Ищем в prefetchCache utils.resolveScreenName
            val objIdPattern = Pattern.compile("\"object_id\":\\s*(\\d+)")
            val objMatcher = objIdPattern.matcher(pageHtml)
            if (objMatcher.find()) {
                return@runCatching objMatcher.group(1)!!.toLong()
            }

            // Запасной поиск loc / data-owner-id
            val locPattern = Pattern.compile("\"loc\":\\s*\"id(\\d+)\"")
            val locMatcher = locPattern.matcher(pageHtml)
            if (locMatcher.find()) {
                return@runCatching locMatcher.group(1)!!.toLong()
            }

            val ownerIdPattern = Pattern.compile("data-owner-id=\"(\\d+)\"")
            val ownerMatcher = ownerIdPattern.matcher(pageHtml)
            if (ownerMatcher.find()) {
                return@runCatching ownerMatcher.group(1)!!.toLong()
            }

            throw IllegalStateException("Не удалось определить ID пользователя VK по адресу '$rawInput'. Убедитесь, что страница существует.")
        }
    }

    /**
     * Получает все аудиозаписи пользователя из основного плейлиста (Любимое / Все треки)
     */
    suspend fun fetchFavoriteAudios(userId: Long): Result<List<VkTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureGuestSession()

            val allTracks = mutableListOf<VkTrack>()
            var offset = 0
            var hasMore = true

            while (hasMore) {
                val form = FormBody.Builder()
                    .add("act", "load_section")
                    .add("al", "1")
                    .add("claim", "0")
                    .add("offset", offset.toString())
                    .add("owner_id", userId.toString())
                    .add("playlist_id", "-1")
                    .add("type", "playlist")
                    .add("is_loading_all", "1")
                    .build()

                val req = Request.Builder()
                    .url("https://vk.com/al_audio.php")
                    .post(form)
                    .header("User-Agent", userAgent)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "https://vk.com/audios$userId")
                    .build()

                val jsonResponse = client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Ошибка запроса к VK: ${response.code}")
                    }
                    val bytes = response.body?.bytes() ?: byteArrayOf()
                    val text = String(bytes, windows1251)
                    JSONObject(text)
                }

                val payload = jsonResponse.optJSONArray("payload")
                    ?: throw IllegalStateException("Некорректный ответ от серверов VK")

                if (payload.length() <= 1) {
                    throw IllegalStateException("Аудиозаписи профиля скрыты настройками приватности")
                }

                val payloadData = payload.optJSONArray(1)
                if (payloadData == null || payloadData.length() == 0) {
                    break
                }

                val firstObj = payloadData.opt(0)
                if (firstObj !is JSONObject) {
                    throw IllegalStateException("Аудиозаписи этого пользователя закрыты настройками приватности VK. Откройте их в профиле перед миграцией.")
                }

                val listArray = firstObj.optJSONArray("list") ?: JSONArray()
                if (listArray.length() == 0) {
                    break
                }

                for (i in 0 until listArray.length()) {
                    val trackArr = listArray.optJSONArray(i) ?: continue
                    parseVkTrackFromArray(trackArr)?.let { allTracks.add(it) }
                }

                val isHasMore = firstObj.optInt("hasMore", 0) == 1
                val nextOffset = firstObj.optInt("nextOffset", 0)
                if (isHasMore && nextOffset > offset && listArray.length() > 0) {
                    offset = nextOffset
                } else {
                    hasMore = false
                }
            }

            allTracks
        }
    }

    /**
     * Получает список пользовательских плейлистов и треки внутри каждого из них.
     * Опрашивает идентификаторы плейлистов параллельными пачками в фоновом пуле.
     */
    suspend fun fetchPlaylistsWithTracks(userId: Long): Result<List<VkPlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureGuestSession()

            val discoveredPlaylists = mutableListOf<VkPlaylist>()
            var chunkStart = 1
            var consecutiveEmptyChunks = 0

            // Сканируем идентификаторы плейлистов параллельными пачками по 15 запросов
            while (chunkStart <= 120 && consecutiveEmptyChunks < 2) {
                val chunkEnd = chunkStart + 14
                val chunkResults = coroutineScope {
                    (chunkStart..chunkEnd).map { plId ->
                        async(Dispatchers.IO) {
                            probePlaylist(userId, plId.toLong())
                        }
                    }.awaitAll().filterNotNull()
                }

                if (chunkResults.isEmpty()) {
                    consecutiveEmptyChunks++
                } else {
                    consecutiveEmptyChunks = 0
                    for (pl in chunkResults) {
                        // Системный плейлист "Мне нравится" дублирует избранное,
                        // поэтому пользовательские плейлисты - это все остальные
                        val isFavSystem = pl.title.equals("Мне нравится", ignoreCase = true) ||
                                pl.title.equals("Любимые треки", ignoreCase = true) ||
                                pl.title.equals("Избранное", ignoreCase = true)
                        if (!isFavSystem && discoveredPlaylists.none { it.id == pl.id }) {
                            discoveredPlaylists.add(pl)
                        }
                    }
                }
                chunkStart += 15
            }

            discoveredPlaylists
        }
    }

    /**
     * Проверяет существование плейлиста и загружает все его треки
     */
    private suspend fun probePlaylist(ownerId: Long, playlistId: Long): VkPlaylist? = withContext(Dispatchers.IO) {
        try {
            val form = FormBody.Builder()
                .add("act", "load_section")
                .add("al", "1")
                .add("claim", "0")
                .add("offset", "0")
                .add("owner_id", ownerId.toString())
                .add("playlist_id", playlistId.toString())
                .add("type", "playlist")
                .add("is_loading_all", "1")
                .build()

            val req = Request.Builder()
                .url("https://vk.com/al_audio.php")
                .post(form)
                .header("User-Agent", userAgent)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://vk.com/audios$ownerId")
                .build()

            val json = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null
                JSONObject(String(bytes, windows1251))
            }

            val payload = json.optJSONArray("payload") ?: return@withContext null
            if (payload.length() <= 1) return@withContext null
            val payloadData = payload.optJSONArray(1) ?: return@withContext null
            if (payloadData.length() == 0) return@withContext null

            val firstObj = payloadData.opt(0)
            if (firstObj !is JSONObject) return@withContext null

            val title = unescapeHtml(firstObj.optString("title", ""))
            if (title.isBlank()) return@withContext null

            val tracks = mutableListOf<VkTrack>()
            val listArray = firstObj.optJSONArray("list") ?: JSONArray()
            for (i in 0 until listArray.length()) {
                val trackArr = listArray.optJSONArray(i) ?: continue
                parseVkTrackFromArray(trackArr)?.let { tracks.add(it) }
            }

            // Если плейлист содержит > 100 треков, догружаем следующие страницы
            var offset = firstObj.optInt("nextOffset", 0)
            var hasMore = firstObj.optInt("hasMore", 0) == 1
            while (hasMore && offset > 0) {
                val pageForm = FormBody.Builder()
                    .add("act", "load_section")
                    .add("al", "1")
                    .add("claim", "0")
                    .add("offset", offset.toString())
                    .add("owner_id", ownerId.toString())
                    .add("playlist_id", playlistId.toString())
                    .add("type", "playlist")
                    .add("is_loading_all", "1")
                    .build()

                val pageReq = Request.Builder()
                    .url("https://vk.com/al_audio.php")
                    .post(pageForm)
                    .header("User-Agent", userAgent)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()

                val pageJson = client.newCall(pageReq).execute().use { resp ->
                    if (!resp.isSuccessful) null
                    else {
                        val bytes = resp.body?.bytes() ?: byteArrayOf()
                        JSONObject(String(bytes, windows1251))
                    }
                }

                val pPayload = pageJson?.optJSONArray("payload")
                val pData = if (pPayload != null && pPayload.length() > 1) pPayload.optJSONArray(1) else null
                val pObj = pData?.optJSONObject(0)
                if (pObj != null) {
                    val pList = pObj.optJSONArray("list") ?: JSONArray()
                    for (i in 0 until pList.length()) {
                        val tArr = pList.optJSONArray(i) ?: continue
                        parseVkTrackFromArray(tArr)?.let { tracks.add(it) }
                    }
                    hasMore = pObj.optInt("hasMore", 0) == 1
                    offset = pObj.optInt("nextOffset", 0)
                } else {
                    hasMore = false
                }
            }

            VkPlaylist(
                id = playlistId,
                ownerId = ownerId,
                title = title,
                accessHash = firstObj.optString("accessHash", ""),
                tracks = tracks
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Парсинг сырого массива трека из JSON VK
     */
    private fun parseVkTrackFromArray(arr: JSONArray): VkTrack? {
        if (arr.length() < 6) return null
        val id = arr.optLong(0, 0)
        val ownerId = arr.optLong(1, 0)
        val rawTitle = arr.optString(3, "").trim()
        val rawArtist = arr.optString(4, "").trim()
        val duration = arr.optInt(5, 0)

        if (id == 0L || rawTitle.isBlank() || rawArtist.isBlank()) return null

        // В индексе 14 обычно лежат URL обложек через запятую
        val coversRaw = arr.optString(14, "")
        val coverUrl = if (coversRaw.isNotBlank()) coversRaw.split(",").firstOrNull()?.trim() ?: "" else ""

        // В индексе 16 часто лежит название альбома
        val rawAlbum = arr.optString(16, "").trim()

        return VkTrack(
            id = id,
            ownerId = ownerId,
            title = unescapeHtml(rawTitle),
            artist = unescapeHtml(rawArtist),
            durationSeconds = duration,
            album = unescapeHtml(rawAlbum),
            coverUrl = coverUrl
        )
    }

    private fun unescapeHtml(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }
}
