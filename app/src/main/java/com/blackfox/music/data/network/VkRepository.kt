package com.blackfox.music.data.network

import kotlinx.coroutines.Dispatchers
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
     * Получает список пользовательских плейлистов и треки внутри каждого из них
     */
    suspend fun fetchPlaylistsWithTracks(userId: Long): Result<List<VkPlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureGuestSession()

            // 1. Запрашиваем раздел плейлистов
            val form = FormBody.Builder()
                .add("act", "section")
                .add("al", "1")
                .add("claim", "0")
                .add("is_layer", "0")
                .add("owner_id", userId.toString())
                .add("section", "playlists")
                .build()

            val req = Request.Builder()
                .url("https://vk.com/al_audio.php")
                .post(form)
                .header("User-Agent", userAgent)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://vk.com/audios$userId")
                .build()

            val playlistsHtml = client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@runCatching emptyList()
                val bytes = response.body?.bytes() ?: byteArrayOf()
                val json = JSONObject(String(bytes, windows1251))
                val payload = json.optJSONArray("payload") ?: return@runCatching emptyList()
                if (payload.length() > 1) {
                    val arr = payload.optJSONArray(1)
                    if (arr != null && arr.length() > 0) arr.optString(0, "") else ""
                } else ""
            }

            if (playlistsHtml.isBlank()) {
                return@runCatching emptyList()
            }

            // 2. Ищем метаданные плейлистов в HTML
            // data-playlist-id="type_ownerId_id" data-title="Title" data-access-hash="hash"
            val playlists = mutableListOf<VkPlaylist>()
            val pattern = Pattern.compile("data-playlist-id=\"(?:playlist_)?(-?\\d+)_(\\d+)\"[^>]*?data-title=\"([^\"]*)\"(?:[^>]*?data-access-hash=\"([^\"]*)\")?")
            val matcher = pattern.matcher(playlistsHtml)

            while (matcher.find()) {
                val ownerId = matcher.group(1)?.toLongOrNull() ?: userId
                val plId = matcher.group(2)?.toLongOrNull() ?: continue
                val title = unescapeHtml(matcher.group(3) ?: "Плейлист")
                val accessHash = matcher.group(4) ?: ""

                if (plId > 0 && playlists.none { it.id == plId }) {
                    playlists.add(VkPlaylist(id = plId, ownerId = ownerId, title = title, accessHash = accessHash))
                }
            }

            // Запасной парсинг ссылок /music/playlist/{ownerId}_{plId}
            if (playlists.isEmpty()) {
                val linkPattern = Pattern.compile("href=\"/music/playlist/(-?\\d+)_(\\d+)(?:_([a-zA-Z0-9]+))?\"[^>]*>.*?<.*?class=\"[^\"]*title[^\"]*\"[^>]*>([^<]+)<", Pattern.DOTALL)
                val linkMatcher = linkPattern.matcher(playlistsHtml)
                while (linkMatcher.find()) {
                    val ownerId = linkMatcher.group(1)?.toLongOrNull() ?: userId
                    val plId = linkMatcher.group(2)?.toLongOrNull() ?: continue
                    val hash = linkMatcher.group(3) ?: ""
                    val title = unescapeHtml(linkMatcher.group(4) ?: "Плейлист")
                    if (plId > 0 && playlists.none { it.id == plId }) {
                        playlists.add(VkPlaylist(id = plId, ownerId = ownerId, title = title.trim(), accessHash = hash))
                    }
                }
            }

            // 3. Для каждого найденного плейлиста загружаем его треки
            val filledPlaylists = mutableListOf<VkPlaylist>()
            for (pl in playlists) {
                val tracks = fetchPlaylistTracks(pl.ownerId, pl.id, pl.accessHash)
                filledPlaylists.add(pl.copy(tracks = tracks))
            }

            filledPlaylists
        }
    }

    /**
     * Загружает треки конкретного плейлиста
     */
    private suspend fun fetchPlaylistTracks(ownerId: Long, playlistId: Long, accessHash: String): List<VkTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<VkTrack>()
        try {
            val form = FormBody.Builder()
                .add("act", "load_section")
                .add("al", "1")
                .add("claim", "0")
                .add("offset", "0")
                .add("owner_id", ownerId.toString())
                .add("playlist_id", playlistId.toString())
                .add("type", "playlist")
                .add("access_hash", accessHash)
                .add("is_loading_all", "1")
                .build()

            val req = Request.Builder()
                .url("https://vk.com/al_audio.php")
                .post(form)
                .header("User-Agent", userAgent)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val json = client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bytes = response.body?.bytes() ?: byteArrayOf()
                JSONObject(String(bytes, windows1251))
            }

            val payload = json.optJSONArray("payload") ?: return@withContext emptyList()
            if (payload.length() > 1) {
                val payloadData = payload.optJSONArray(1) ?: return@withContext emptyList()
                val firstObj = payloadData.optJSONObject(0) ?: return@withContext emptyList()
                val listArray = firstObj.optJSONArray("list") ?: return@withContext emptyList()

                for (i in 0 until listArray.length()) {
                    val trackArr = listArray.optJSONArray(i) ?: continue
                    parseVkTrackFromArray(trackArr)?.let { tracks.add(it) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        tracks
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
