package com.blackfox.music.data.network

import com.blackfox.music.domain.model.Album
import com.blackfox.music.domain.model.Artist
import com.blackfox.music.domain.model.ArtistTracksPage
import com.blackfox.music.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Репозиторий для прямого взаимодействия с каталогом YouTube Music через InnerTube
 */
class InnerTubeRepository {

    private val innertubeBaseUrl = "https://music.youtube.com/youtubei/v1"
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun cleanThumbnail(url: String, size: Int = 1080): String {
        return if (url.contains("=w") && url.contains("-h")) {
            url.replace(Regex("=w\\d+-h\\d+[^?]*"), "=w$size-h$size-l90-rj")
        } else {
            url
        }
    }

    private suspend fun sendRequest(endpoint: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("$innertubeBaseUrl/$endpoint?prettyPrint=false")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("User-Agent", userAgent)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Referer", "https://music.youtube.com/")
        conn.setRequestProperty("Origin", "https://music.youtube.com")
        conn.doOutput = true

        val contextJson = JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", "1.20241021.01.00")
                put("hl", "ru")
                put("gl", "US")
            })
        }
        payload.put("context", contextJson)

        conn.outputStream.use { os ->
            os.write(payload.toString().toByteArray(Charsets.UTF_8))
        }

        val responseCode = conn.responseCode
        if (responseCode != 200) {
            throw RuntimeException("InnerTube HTTP Error: $responseCode")
        }

        val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        JSONObject(responseText)
    }

    suspend fun searchTracks(query: String, limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("query", query)
            put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D") // Фильтр только песни (Tracks)
        }
        val response = sendRequest("search", payload)
        val tracks = mutableListOf<Track>()

        val tabs = response.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs") ?: return@withContext emptyList()

        val sections = tabs.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return@withContext emptyList()

        for (i in 0 until sections.length()) {
            val section = sections.getJSONObject(i)
            val items = section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                ?: section.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                ?: continue

            for (j in 0 until items.length()) {
                val mir = items.getJSONObject(j).optJSONObject("musicResponsiveListItemRenderer") ?: continue
                val cols = mir.optJSONArray("flexColumns") ?: continue
                if (cols.length() == 0) continue

                // 1. Title
                val title = cols.getJSONObject(0)
                    .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    .getJSONObject("text")
                    .getJSONArray("runs")
                    .getJSONObject(0)
                    .getString("text")

                // 2. Artist, Album, Duration
                val detailsRuns = if (cols.length() > 1) {
                    cols.getJSONObject(1)
                        .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        .getJSONObject("text")
                        .optJSONArray("runs") ?: JSONArray()
                } else JSONArray()

                var artist = "Unknown"
                var artistId: String? = null
                var album: String? = null
                var albumId: String? = null
                var duration = ""

                val runTexts = mutableListOf<String>()
                for (k in 0 until detailsRuns.length()) {
                    val runObj = detailsRuns.getJSONObject(k)
                    val t = runObj.optString("text", "")
                    if (t != " • " && t.isNotBlank()) {
                        runTexts.add(t)
                    }
                    val bId = runObj.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                    if (artistId == null && !bId.isNullOrBlank() && bId.startsWith("UC")) {
                        artistId = bId
                    }
                    if (albumId == null && !bId.isNullOrBlank() && (bId.startsWith("MPREb_") || bId.startsWith("FEmusic_library_privately_owned_release_detail"))) {
                        albumId = bId
                    }
                }

                if (runTexts.isNotEmpty()) artist = runTexts[0]
                if (runTexts.size >= 2) {
                    if (runTexts.last().contains(":")) {
                        duration = runTexts.last()
                        if (runTexts.size >= 3) album = runTexts[1]
                    } else {
                        album = runTexts[1]
                    }
                }

                // 3. VideoId
                val videoId = mir.optJSONObject("overlay")
                    ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("musicPlayButtonRenderer")
                    ?.optJSONObject("playNavigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId")

                // 4. Thumbnail
                val thumbs = mir.optJSONObject("thumbnail")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                val rawThumb = if (thumbs != null && thumbs.length() > 0) {
                    thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                } else ""

                if (!videoId.isNullOrBlank()) {
                    tracks.add(
                        Track(
                            id = videoId,
                            title = title,
                            artist = artist,
                            artistId = artistId,
                            album = album,
                            albumId = albumId,
                            duration = duration,
                            thumbnailUrl = cleanThumbnail(rawThumb, 1080)
                        )
                    )
                    if (tracks.size >= limit) return@withContext tracks
                }
            }
        }
        tracks
    }

    suspend fun searchAlbums(query: String, limit: Int = 15): List<Album> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("query", query)
            put("params", "EgWKAQIYAWoKEAkQBRAKEAMQBA%3D%3D") // Фильтр только альбомы
        }
        val response = sendRequest("search", payload)
        val albums = mutableListOf<Album>()

        val tabs = response.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs") ?: return@withContext emptyList()

        val sections = tabs.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return@withContext emptyList()

        for (i in 0 until sections.length()) {
            val section = sections.getJSONObject(i)
            val items = section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                ?: section.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                ?: continue

            for (j in 0 until items.length()) {
                val mir = items.getJSONObject(j).optJSONObject("musicResponsiveListItemRenderer") ?: continue
                val cols = mir.optJSONArray("flexColumns") ?: continue
                if (cols.length() == 0) continue

                val title = cols.getJSONObject(0)
                    .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    .getJSONObject("text")
                    .getJSONArray("runs")
                    .getJSONObject(0)
                    .getString("text")

                var browseId = mir.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")

                if (browseId.isNullOrBlank()) {
                    browseId = cols.getJSONObject(0)
                        .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        .getJSONObject("text")
                        .getJSONArray("runs")
                        .getJSONObject(0)
                        .optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                }

                val detailsRuns = if (cols.length() > 1) {
                    cols.getJSONObject(1)
                        .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        .getJSONObject("text")
                        .optJSONArray("runs") ?: JSONArray()
                } else JSONArray()

                var artist = "Unknown"
                var year = ""
                var albumType = "Альбом"

                val yearPattern = Pattern.compile("^\\d{4}$")
                for (k in 0 until detailsRuns.length()) {
                    val t = detailsRuns.getJSONObject(k).optString("text", "")
                    when {
                        t in listOf("Альбом", "Сингл", "EP", "Album", "Single") -> albumType = t
                        yearPattern.matcher(t).matches() -> year = t
                        t != " • " && t.isNotBlank() -> artist = t
                    }
                }

                val thumbs = mir.optJSONObject("thumbnail")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                val rawThumb = if (thumbs != null && thumbs.length() > 0) {
                    thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                } else ""

                if (!browseId.isNullOrBlank()) {
                    albums.add(
                        Album(
                            browseId = browseId,
                            title = title,
                            artist = artist,
                            type = albumType,
                            year = year,
                            coverUrl = cleanThumbnail(rawThumb, 1080)
                        )
                    )
                    if (albums.size >= limit) return@withContext albums
                }
            }
        }
        albums
    }

    suspend fun getAlbumDetails(browseId: String): Album = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("browseId", browseId)
        }
        val response = sendRequest("browse", payload)
        var albumTitle = "Unknown Album"
        var artist = "Unknown Artist"
        var year = ""
        var cover = ""
        val tracks = mutableListOf<Track>()

        val tc = response.optJSONObject("contents")?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?: response.optJSONObject("contents")?.optJSONObject("singleColumnBrowseResultsRenderer")
        if (tc != null) {
            val tabs = tc.optJSONArray("tabs")
            val firstTabContents = tabs?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (firstTabContents != null && firstTabContents.length() > 0) {
                val header = firstTabContents.getJSONObject(0).optJSONObject("musicResponsiveHeaderRenderer")
                if (header != null) {
                    albumTitle = header.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", albumTitle) ?: albumTitle
                    val subRuns = header.optJSONObject("subtitle")?.optJSONArray("runs") ?: JSONArray()
                    val yearPattern = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b")
                    for (i in 0 until subRuns.length()) {
                        val txt = subRuns.getJSONObject(i).optString("text", "")
                        val m = yearPattern.matcher(txt)
                        when {
                            m.find() -> year = m.group(1) ?: txt
                            txt !in listOf(" • ", "Альбом", "Сингл", "EP", "Album", "Single") && txt.isNotBlank() -> artist = txt
                        }
                    }
                    val thumbs = header.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                    if (thumbs != null && thumbs.length() > 0) {
                        cover = cleanThumbnail(thumbs.getJSONObject(thumbs.length() - 1).optString("url"), 1080)
                    }
                }
            }

            // Треклист
            val secContents = tc.optJSONObject("secondaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?: firstTabContents
                ?: JSONArray()

            for (i in 0 until secContents.length()) {
                val shelf = secContents.getJSONObject(i).optJSONObject("musicShelfRenderer") ?: continue
                val items = shelf.optJSONArray("contents") ?: continue
                for (j in 0 until items.length()) {
                    val mir = items.getJSONObject(j).optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    val cols = mir.optJSONArray("flexColumns") ?: continue
                    val trackTitle = cols.getJSONObject(0)
                        .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        .getJSONObject("text")
                        .getJSONArray("runs")
                        .getJSONObject(0)
                        .getString("text")

                    var duration = ""
                    val fixedCols = mir.optJSONArray("fixedColumns")
                    if (fixedCols != null && fixedCols.length() > 0) {
                        duration = fixedCols.getJSONObject(0)
                            .getJSONObject("musicResponsiveListItemFixedColumnRenderer")
                            .getJSONObject("text")
                            .getJSONArray("runs")
                            .getJSONObject(0)
                            .optString("text", "")
                    }

                    val videoId = mir.optJSONObject("overlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId") ?: ""

                    tracks.add(
                        Track(
                            id = videoId,
                            title = trackTitle,
                            artist = artist,
                            album = albumTitle,
                            albumId = browseId,
                            duration = duration,
                            thumbnailUrl = cover
                        )
                    )
                }
                break
            }
        }

        Album(
            browseId = browseId,
            title = albumTitle,
            artist = artist,
            year = year,
            coverUrl = cover,
            trackCount = tracks.size,
            tracks = tracks
        )
    }

    suspend fun searchArtists(query: String, limit: Int = 15): List<Artist> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("query", query)
            put("params", "EgWKAQIgAWoKEAkQBRAKEAMQBA%3D%3D") // Фильтр только артисты
        }
        val response = sendRequest("search", payload)
        val artists = mutableListOf<Artist>()

        val tabs = response.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs") ?: return@withContext emptyList()

        val sections = tabs.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return@withContext emptyList()

        for (i in 0 until sections.length()) {
            val section = sections.getJSONObject(i)
            val items = section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                ?: section.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                ?: continue

            for (j in 0 until items.length()) {
                val mir = items.getJSONObject(j).optJSONObject("musicResponsiveListItemRenderer") ?: continue
                val cols = mir.optJSONArray("flexColumns") ?: continue
                if (cols.length() == 0) continue

                val name = cols.getJSONObject(0)
                    .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    .getJSONObject("text")
                    .getJSONArray("runs")
                    .getJSONObject(0)
                    .getString("text")

                var browseId = mir.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")

                if (browseId.isNullOrBlank()) {
                    browseId = cols.getJSONObject(0)
                        .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        .getJSONObject("text")
                        .getJSONArray("runs")
                        .getJSONObject(0)
                        .optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                }

                val detailsRuns = if (cols.length() > 1) {
                    cols.getJSONObject(1)
                        .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        .getJSONObject("text")
                        .optJSONArray("runs") ?: JSONArray()
                } else JSONArray()

                var subscribers = ""
                for (k in 0 until detailsRuns.length()) {
                    val t = detailsRuns.getJSONObject(k).optString("text", "")
                    if (t != " • " && t.isNotBlank() && t != "Исполнитель" && t != "Artist") {
                        subscribers = t
                    }
                }

                val thumbs = mir.optJSONObject("thumbnail")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                val rawThumb = if (thumbs != null && thumbs.length() > 0) {
                    thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                } else ""

                if (!browseId.isNullOrBlank()) {
                    artists.add(
                        Artist(
                            channelId = browseId,
                            name = name,
                            subscribers = subscribers,
                            avatarUrl = cleanThumbnail(rawThumb, 720)
                        )
                    )
                    if (artists.size >= limit) return@withContext artists
                }
            }
        }
        artists
    }

    suspend fun getArtistDetails(channelId: String): Artist = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("browseId", channelId)
        }
        val response = sendRequest("browse", payload)
        var artistName = "Unknown Artist"
        var subscribers = ""
        var avatarUrl = ""
        var allSongsBrowseId: String? = null
        var allSongsParams: String? = null
        val topTracks = mutableListOf<Track>()
        val albums = mutableListOf<Album>()
        val similarArtists = mutableListOf<Artist>()

        val header = response.optJSONObject("header")?.optJSONObject("musicImmersiveHeaderRenderer")
            ?: response.optJSONObject("header")?.optJSONObject("musicVisualHeaderRenderer")
        if (header != null) {
            artistName = header.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", artistName) ?: artistName
            subscribers = header.optJSONObject("subscriptionButton")
                ?.optJSONObject("subscribeButtonRenderer")
                ?.optJSONObject("subscriberCountText")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text", "") ?: ""
            val thumbs = header.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
            if (thumbs != null && thumbs.length() > 0) {
                avatarUrl = cleanThumbnail(thumbs.getJSONObject(thumbs.length() - 1).optString("url"), 1080)
            }
        }

        val tc = response.optJSONObject("contents")?.optJSONObject("singleColumnBrowseResultsRenderer")
        val tabs = tc?.optJSONArray("tabs")
        val contents = tabs?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: JSONArray()

        for (i in 0 until contents.length()) {
            val sec = contents.getJSONObject(i)
            val shelf = sec.optJSONObject("musicShelfRenderer")
            val carousel = sec.optJSONObject("musicCarouselShelfRenderer")

            // 1. Популярные треки (Top songs)
            if (shelf != null && topTracks.isEmpty()) {
                val ep = shelf.optJSONObject("bottomEndpoint")?.optJSONObject("browseEndpoint")
                    ?: shelf.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                    ?: shelf.optJSONObject("header")?.optJSONObject("musicHeaderRenderer")?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                    ?: shelf.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")?.optJSONObject("moreContentButton")?.optJSONObject("buttonRenderer")?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")

                if (ep != null) {
                    allSongsBrowseId = ep.optString("browseId")
                    allSongsParams = ep.optString("params").takeIf { it.isNotBlank() }
                }

                val items = shelf.optJSONArray("contents") ?: JSONArray()
                for (j in 0 until items.length()) {
                    val mir = items.getJSONObject(j).optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    val cols = mir.optJSONArray("flexColumns") ?: continue
                    val trackTitle = cols.getJSONObject(0)
                        .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        .getJSONObject("text")
                        .getJSONArray("runs")
                        .getJSONObject(0)
                        .getString("text")

                    var albumTitle: String? = null
                    var duration = ""
                    val detailsRuns = if (cols.length() > 1) {
                        cols.getJSONObject(1)
                            .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            .getJSONObject("text")
                            .optJSONArray("runs") ?: JSONArray()
                    } else JSONArray()

                    var albumId: String? = null
                    val runTexts = mutableListOf<String>()
                    for (k in 0 until detailsRuns.length()) {
                        val runObj = detailsRuns.getJSONObject(k)
                        val t = runObj.optString("text", "")
                        if (t != " • " && t.isNotBlank()) runTexts.add(t)
                        val bId = runObj.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId")
                        if (albumId == null && !bId.isNullOrBlank() && (bId.startsWith("MPREb_") || bId.startsWith("FEmusic_library_privately_owned_release_detail"))) {
                            albumId = bId
                        }
                    }
                    if (runTexts.size >= 2) {
                        albumTitle = runTexts[1]
                    }

                    val fixedCols = mir.optJSONArray("fixedColumns")
                    if (fixedCols != null && fixedCols.length() > 0) {
                        duration = fixedCols.getJSONObject(0)
                            .getJSONObject("musicResponsiveListItemFixedColumnRenderer")
                            .getJSONObject("text")
                            .getJSONArray("runs")
                            .getJSONObject(0)
                            .optString("text", "")
                    }

                    val videoId = mir.optJSONObject("overlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId") ?: ""

                    val thumbs = mir.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                    val rawThumb = if (thumbs != null && thumbs.length() > 0) {
                        thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                    } else avatarUrl

                    if (videoId.isNotBlank()) {
                        topTracks.add(
                            Track(
                                id = videoId,
                                title = trackTitle,
                                artist = artistName,
                                artistId = channelId,
                                album = albumTitle,
                                albumId = albumId,
                                duration = duration,
                                thumbnailUrl = cleanThumbnail(rawThumb, 720)
                            )
                        )
                    }
                }
            }

            // 2. Альбомы, Синглы и похожие артисты (Carousels)
            if (carousel != null) {
                val headerRuns = carousel.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    ?.optJSONObject("title")
                    ?.optJSONArray("runs") ?: JSONArray()
                val carouselTitle = (0 until headerRuns.length()).joinToString("") {
                    headerRuns.getJSONObject(it).optString("text", "")
                }

                val items = carousel.optJSONArray("contents") ?: JSONArray()
                val firstItem = if (items.length() > 0) items.getJSONObject(0).optJSONObject("musicTwoRowItemRenderer") else null
                val firstPageType = firstItem?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optJSONObject("browseEndpointContextSupportedConfigs")
                    ?.optJSONObject("browseEndpointContextMusicConfig")
                    ?.optString("pageType", "") ?: ""

                // Похожие артисты
                if (firstPageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                    carouselTitle.contains("Fans might also like", ignoreCase = true) ||
                    carouselTitle.contains("Similar", ignoreCase = true) ||
                    carouselTitle.contains("Похожие", ignoreCase = true) ||
                    carouselTitle.contains("нравится", ignoreCase = true)
                ) {
                    for (j in 0 until items.length()) {
                        val twoRow = items.getJSONObject(j).optJSONObject("musicTwoRowItemRenderer") ?: continue
                        val name = twoRow.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
                        val cid = twoRow.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId") ?: ""
                        val sub = twoRow.optJSONObject("subtitle")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
                        val thumbs = twoRow.optJSONObject("thumbnailRenderer")
                            ?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")
                            ?.optJSONArray("thumbnails")
                        val coverUrl = if (thumbs != null && thumbs.length() > 0) {
                            thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                        } else ""

                        if (cid.isNotBlank() && name.isNotBlank()) {
                            similarArtists.add(
                                Artist(
                                    channelId = cid,
                                    name = name,
                                    subscribers = sub,
                                    avatarUrl = cleanThumbnail(coverUrl, 720)
                                )
                            )
                        }
                    }
                } else if (carouselTitle.contains("Album", ignoreCase = true) ||
                    carouselTitle.contains("Single", ignoreCase = true) ||
                    carouselTitle.contains("Альбом", ignoreCase = true) ||
                    carouselTitle.contains("Сингл", ignoreCase = true) ||
                    carouselTitle.contains("EP", ignoreCase = true)
                ) {
                    for (j in 0 until items.length()) {
                        val twoRow = items.getJSONObject(j).optJSONObject("musicTwoRowItemRenderer") ?: continue
                        val albumTitle = twoRow.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
                        val browseId = twoRow.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId") ?: ""

                        var year = ""
                        val albumType = if (carouselTitle.contains("Single", ignoreCase = true) || carouselTitle.contains("Сингл", ignoreCase = true)) "Сингл" else "Альбом"
                        val subRuns = twoRow.optJSONObject("subtitle")?.optJSONArray("runs") ?: JSONArray()
                        val yearRegex = Regex("\\b(19\\d{2}|20\\d{2})\\b")
                        for (k in 0 until subRuns.length()) {
                            val st = subRuns.getJSONObject(k).optString("text", "")
                            val match = yearRegex.find(st)
                            if (match != null) {
                                year = match.value
                            }
                        }

                        val thumbs = twoRow.optJSONObject("thumbnailRenderer")
                            ?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")
                            ?.optJSONArray("thumbnails")
                        val coverUrl = if (thumbs != null && thumbs.length() > 0) {
                            thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                        } else ""

                        if (browseId.isNotBlank() && albumTitle.isNotBlank()) {
                            albums.add(
                                Album(
                                    browseId = browseId,
                                    title = albumTitle,
                                    artist = artistName,
                                    type = albumType,
                                    year = year,
                                    coverUrl = cleanThumbnail(coverUrl, 720)
                                )
                            )
                        }
                    }
                }
            }
        }

        Artist(
            channelId = channelId,
            name = artistName,
            subscribers = subscribers,
            avatarUrl = avatarUrl,
            topTracks = topTracks,
            albums = albums,
            similarArtists = similarArtists,
            allSongsBrowseId = allSongsBrowseId,
            allSongsParams = allSongsParams
        )
    }

    /**
     * Получение действительно новых релизов артиста (свежих синглов и последних альбомов) из API
     */
    suspend fun getArtistNewReleases(channelId: String, artistName: String): List<Track> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Track>()
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        try {
            val details = getArtistDetails(channelId)

            val singles = details.albums.filter {
                it.type.equals("Сингл", ignoreCase = true) ||
                it.type.equals("EP", ignoreCase = true) ||
                it.type.contains("Single", ignoreCase = true)
            }
            val albums = details.albums.filter {
                it.type.equals("Альбом", ignoreCase = true) ||
                it.type.contains("Album", ignoreCase = true)
            }

            val candidateReleases = mutableListOf<Album>()
            // В YouTube Music синглы и альбомы расположены в хронологическом порядке (сначала новые)
            candidateReleases.addAll(singles.take(2))
            candidateReleases.addAll(albums.take(1))

            if (candidateReleases.isEmpty()) {
                candidateReleases.addAll(details.albums.take(2))
            }

            for (rel in candidateReleases.take(3)) {
                val relYear = rel.year.toIntOrNull() ?: 0
                // Если у самого релиза уже спарсен старый год (< currentYear - 1), пропускаем
                if (relYear in 1 until (currentYear - 1)) {
                    continue
                }

                try {
                    val albumData = getAlbumDetails(rel.browseId)
                    val albumYear = albumData.year.toIntOrNull() ?: relYear
                    // Если релиз вышел более года назад, не считаем его свежей новинкой
                    if (albumYear in 1 until (currentYear - 1)) {
                        continue
                    }

                    for (tr in albumData.tracks.take(2)) {
                        result.add(
                            tr.copy(
                                artist = if (tr.artist.isBlank() || tr.artist == "Unknown Artist") artistName else tr.artist,
                                artistId = channelId,
                                album = rel.title,
                                albumId = rel.browseId
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        result.distinctBy { it.id }.take(6)
    }

    /**
     * Получение действительно новых релизов артиста строго за последний месяц (30 дней)
     * через открытый официальный Atom/RSS фид канала артиста YouTube.
     * Никаких старых релизов, альбомов или сторонних плейлистов: только реальные свежие публикации за 30 дней.
     */
    suspend fun getArtistNewReleasesLastMonth(channelId: String, artistName: String): List<Track> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Track>()
        val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
        val nowMs = System.currentTimeMillis()

        var resolvedChannelId = channelId
        if (!resolvedChannelId.startsWith("UC")) {
            try {
                val searchResult = searchArtists(artistName, limit = 3)
                val matched = searchResult.firstOrNull { it.name.equals(artistName, ignoreCase = true) }
                    ?: searchResult.firstOrNull()
                if (matched != null && matched.channelId.startsWith("UC")) {
                    resolvedChannelId = matched.channelId
                }
            } catch (_: Exception) {}
        }

        if (!resolvedChannelId.startsWith("UC")) {
            return@withContext emptyList()
        }

        try {
            val url = URL("https://www.youtube.com/feeds/videos.xml?channel_id=$resolvedChannelId")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.connectTimeout = 7000
            conn.readTimeout = 7000

            if (conn.responseCode == 200) {
                val factory = DocumentBuilderFactory.newInstance()
                factory.isNamespaceAware = true
                val builder = factory.newDocumentBuilder()
                val doc = builder.parse(conn.inputStream)

                val entries = doc.getElementsByTagName("entry")
                for (i in 0 until entries.length) {
                    val entry = entries.item(i) as? Element ?: continue

                    // 1. Проверяем дату публикации (published)
                    val publishedNodes = entry.getElementsByTagName("published")
                    val publishedStr = publishedNodes.item(0)?.textContent ?: continue
                    val publishedEpochMs = parseIsoDateToEpochMs(publishedStr)
                    val ageMs = nowMs - publishedEpochMs

                    // Фильтр строго за последние 30 дней
                    if (ageMs in 0..thirtyDaysMs) {
                        val videoIdNodes = entry.getElementsByTagNameNS("http://www.youtube.com/xml/schemas/2015", "videoId")
                        val videoId = videoIdNodes.item(0)?.textContent
                            ?: entry.getElementsByTagName("yt:videoId").item(0)?.textContent
                            ?: continue

                        val rawTitle = entry.getElementsByTagName("title").item(0)?.textContent ?: ""
                        if (rawTitle.isBlank() || rawTitle.contains("#shorts", ignoreCase = true) || rawTitle.contains("#short", ignoreCase = true)) {
                            continue
                        }

                        val cleanTitle = cleanVideoTitle(rawTitle, artistName)
                        val thumbUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                        result.add(
                            Track(
                                id = videoId,
                                title = cleanTitle,
                                artist = artistName,
                                artistId = resolvedChannelId,
                                album = "Новинка (за 30 дней)",
                                thumbnailUrl = cleanThumbnail(thumbUrl, 720)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        result.distinctBy { it.id }.take(6)
    }

    private fun parseIsoDateToEpochMs(isoDate: String): Long {
        return try {
            java.time.OffsetDateTime.parse(isoDate).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                sdf.parse(isoDate)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }

    private fun cleanVideoTitle(title: String, artistName: String): String {
        var clean = title
        if (clean.startsWith("$artistName - ", ignoreCase = true)) {
            clean = clean.substring("$artistName - ".length).trim()
        }
        clean = clean.replace(Regex("(?i)\\s*[\\[(]official\\s*(music\\s*video|video|audio|lyric\\s*video|visualizer|expanded\\s*video)[\\])]"), "")
        clean = clean.replace(Regex("(?i)\\s*[\\[(](lyric\\s*video|visualizer|audio)[\\])]"), "")
        return clean.trim()
    }

    suspend fun getArtistAllTracks(
        allSongsBrowseId: String,
        allSongsParams: String? = null,
        artistName: String,
        artistId: String
    ): ArtistTracksPage = withContext(Dispatchers.IO) {
        if (allSongsBrowseId.isNotBlank()) {
            try {
                val payload = JSONObject().apply {
                    put("browseId", allSongsBrowseId)
                    if (!allSongsParams.isNullOrBlank()) {
                        put("params", allSongsParams)
                    }
                }
                val response = sendRequest("browse", payload)
                val tracks = mutableListOf<Track>()
                var nextContinuationToken: String? = null
                var countString: String? = null

                // 1. Попытка извлечь заголовок/описание с числом песен
                val header = response.optJSONObject("header")?.optJSONObject("musicHeaderRenderer")
                    ?: response.optJSONObject("header")?.optJSONObject("musicDetailHeaderRenderer")
                    ?: response.optJSONObject("header")?.optJSONObject("musicResponsiveHeaderRenderer")
                val headerRuns = header?.optJSONObject("subtitle")?.optJSONArray("runs")
                    ?: header?.optJSONObject("secondSubtitle")?.optJSONArray("runs")
                if (headerRuns != null) {
                    for (s in 0 until headerRuns.length()) {
                        val text = headerRuns.getJSONObject(s).optString("text", "")
                        if (text.contains("песн", ignoreCase = true) || text.contains("song", ignoreCase = true) || text.contains("трек", ignoreCase = true)) {
                            countString = text
                        }
                    }
                }

                // 2. Ищем полки с песнями
                val tabContent = response.optJSONObject("contents")
                    ?.optJSONObject("singleColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")
                    ?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")

                val directShelf = tabContent?.optJSONObject("musicPlaylistShelfRenderer")
                    ?: tabContent?.optJSONObject("musicShelfRenderer")

                val shelvesToProcess = mutableListOf<JSONObject>()
                if (directShelf != null) {
                    shelvesToProcess.add(directShelf)
                }

                val contentsArray = response.optJSONObject("contents")
                    ?.optJSONObject("twoColumnBrowseResultsRenderer")
                    ?.optJSONObject("secondaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")
                    ?: tabContent?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents")

                if (contentsArray != null) {
                    for (i in 0 until contentsArray.length()) {
                        val sec = contentsArray.getJSONObject(i)
                        val s = sec.optJSONObject("musicPlaylistShelfRenderer")
                            ?: sec.optJSONObject("musicShelfRenderer")
                        if (s != null) shelvesToProcess.add(s)
                    }
                }

                for (shelf in shelvesToProcess) {
                    val contArray = shelf.optJSONArray("continuations")
                    if (contArray != null && contArray.length() > 0) {
                        nextContinuationToken = contArray.getJSONObject(0)
                            .optJSONObject("nextContinuationData")
                            ?.optString("continuation")
                            ?: contArray.getJSONObject(0)
                                .optJSONObject("reloadContinuationData")
                                ?.optString("continuation")
                    }

                    if (countString == null) {
                        val subRuns = shelf.optJSONObject("header")
                            ?.optJSONObject("musicHeaderRenderer")
                            ?.optJSONObject("subtitle")
                            ?.optJSONArray("runs")
                        if (subRuns != null) {
                            for (s in 0 until subRuns.length()) {
                                val text = subRuns.getJSONObject(s).optString("text", "")
                                if (text.contains("песн", ignoreCase = true) || text.contains("song", ignoreCase = true) || text.contains("трек", ignoreCase = true)) {
                                    countString = text
                                }
                            }
                        }
                    }

                    val items = shelf.optJSONArray("contents") ?: continue
                    for (j in 0 until items.length()) {
                        val mir = items.getJSONObject(j).optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        val track = parseTrackFromItemRenderer(mir, artistName, artistId)
                        if (track != null) {
                            tracks.add(track)
                        }
                    }
                }

                if (tracks.isNotEmpty()) {
                    val finalCountString = countString ?: if (nextContinuationToken != null) "${tracks.size}+ треков" else "${tracks.size} треков"
                    return@withContext ArtistTracksPage(
                        tracks = tracks,
                        continuationToken = nextContinuationToken,
                        totalCountString = finalCountString
                    )
                }
            } catch (e: Exception) {
                // fallback below
            }
        }

        // Fallback через поиск треков артиста с поддержкой continuation
        searchTracksWithContinuation("$artistName", artistName, artistId)
    }

    suspend fun searchTracksWithContinuation(query: String, defaultArtist: String, artistId: String): ArtistTracksPage = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("query", query)
                put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D") // Фильтр только песни (Tracks)
            }
            val response = sendRequest("search", payload)
            val tracks = mutableListOf<Track>()
            var nextToken: String? = null

            val tabs = response.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")
            val sections = tabs?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (sections != null) {
                for (i in 0 until sections.length()) {
                    val section = sections.getJSONObject(i)
                    val shelf = section.optJSONObject("musicShelfRenderer")
                        ?: section.optJSONObject("itemSectionRenderer")
                        ?: continue

                    val contArray = shelf.optJSONArray("continuations")
                    if (contArray != null && contArray.length() > 0) {
                        nextToken = contArray.getJSONObject(0)
                            .optJSONObject("nextContinuationData")
                            ?.optString("continuation")
                    }

                    val items = shelf.optJSONArray("contents") ?: continue
                    for (j in 0 until items.length()) {
                        val mir = items.getJSONObject(j).optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        val track = parseTrackFromItemRenderer(mir, defaultArtist, artistId)
                        if (track != null) {
                            tracks.add(track)
                        }
                    }
                }
            }
            val countStr = if (nextToken != null) "${tracks.size}+ треков" else "${tracks.size} треков"
            ArtistTracksPage(tracks = tracks, continuationToken = nextToken, totalCountString = countStr)
        } catch (e: Exception) {
            ArtistTracksPage()
        }
    }

    suspend fun getArtistTracksContinuation(continuationToken: String, artistName: String, artistId: String): ArtistTracksPage = withContext(Dispatchers.IO) {
        if (continuationToken.isBlank()) return@withContext ArtistTracksPage()
        try {
            val payload = JSONObject().apply {
                put("continuation", continuationToken)
            }
            var response = sendRequest("browse", payload)
            var shelfCont = response.optJSONObject("continuationContents")?.optJSONObject("musicPlaylistShelfContinuation")
                ?: response.optJSONObject("continuationContents")?.optJSONObject("musicShelfContinuation")
                ?: response.optJSONObject("continuationContents")?.optJSONObject("sectionListContinuation")

            if (shelfCont == null) {
                response = sendRequest("search", payload)
                shelfCont = response.optJSONObject("continuationContents")?.optJSONObject("musicPlaylistShelfContinuation")
                    ?: response.optJSONObject("continuationContents")?.optJSONObject("musicShelfContinuation")
                    ?: response.optJSONObject("continuationContents")?.optJSONObject("sectionListContinuation")
            }

            val tracks = mutableListOf<Track>()
            var nextToken: String? = null

            if (shelfCont != null) {
                val contArray = shelfCont.optJSONArray("continuations")
                if (contArray != null && contArray.length() > 0) {
                    nextToken = contArray.getJSONObject(0)
                        .optJSONObject("nextContinuationData")
                        ?.optString("continuation")
                        ?: contArray.getJSONObject(0)
                            .optJSONObject("reloadContinuationData")
                            ?.optString("continuation")
                }

                val items = shelfCont.optJSONArray("contents")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val itemObj = items.getJSONObject(i)
                        val mir = itemObj.optJSONObject("musicResponsiveListItemRenderer")
                            ?: itemObj.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")?.optJSONObject(0)?.optJSONObject("musicResponsiveListItemRenderer")
                            ?: continue
                        val track = parseTrackFromItemRenderer(mir, artistName, artistId)
                        if (track != null) {
                            tracks.add(track)
                        }
                    }
                }
            }
            ArtistTracksPage(tracks = tracks, continuationToken = nextToken)
        } catch (e: Exception) {
            ArtistTracksPage()
        }
    }

    private fun parseTrackFromItemRenderer(mir: JSONObject, defaultArtist: String, artistId: String): Track? {
        val cols = mir.optJSONArray("flexColumns") ?: return null
        if (cols.length() == 0) return null

        val trackTitle = cols.getJSONObject(0)
            .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
            .getJSONObject("text")
            .getJSONArray("runs")
            .getJSONObject(0)
            .getString("text")

        var albumTitle: String? = null
        var duration = ""
        val detailsRuns = if (cols.length() > 1) {
            cols.getJSONObject(1)
                .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                .getJSONObject("text")
                .optJSONArray("runs") ?: JSONArray()
        } else JSONArray()

        var albumId: String? = null
        val runTexts = mutableListOf<String>()
        for (k in 0 until detailsRuns.length()) {
            val runObj = detailsRuns.getJSONObject(k)
            val t = runObj.optString("text", "")
            if (t != " • " && t.isNotBlank()) runTexts.add(t)
            val bId = runObj.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
            if (albumId == null && !bId.isNullOrBlank() && (bId.startsWith("MPREb_") || bId.startsWith("FEmusic_library_privately_owned_release_detail"))) {
                albumId = bId
            }
        }
        if (runTexts.size >= 2) {
            albumTitle = runTexts[1]
        }

        val fixedCols = mir.optJSONArray("fixedColumns")
        if (fixedCols != null && fixedCols.length() > 0) {
            duration = fixedCols.getJSONObject(0)
                .getJSONObject("musicResponsiveListItemFixedColumnRenderer")
                .getJSONObject("text")
                .getJSONArray("runs")
                .getJSONObject(0)
                .optString("text", "")
        }

        val videoId = mir.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?: mir.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
            ?: mir.optJSONObject("doubleTapCommand")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
            ?: ""

        val thumbs = mir.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        val rawThumb = if (thumbs != null && thumbs.length() > 0) {
            thumbs.getJSONObject(thumbs.length() - 1).optString("url")
        } else ""

        if (videoId.isBlank()) return null

        return Track(
            id = videoId,
            title = trackTitle,
            artist = defaultArtist,
            artistId = artistId,
            album = albumTitle,
            albumId = albumId,
            duration = duration,
            thumbnailUrl = cleanThumbnail(rawThumb, 720)
        )
    }

    suspend fun getRadioQueue(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
                put("isAudioOnly", true)
                put("enablePersistentPlaylistPanel", true)
            }
            val response = sendRequest("next", payload)
            val queueItems = response.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicQueueRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("playlistPanelRenderer")
                ?.optJSONArray("contents") ?: JSONArray()

            val result = mutableListOf<Track>()
            for (i in 0 until queueItems.length()) {
                val item = queueItems.getJSONObject(i).optJSONObject("playlistPanelVideoRenderer") ?: continue
                val vid = item.optString("videoId")
                if (vid.isBlank()) continue

                val title = item.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
                val artistRuns = item.optJSONObject("longBylineText")?.optJSONArray("runs") ?: JSONArray()
                val artistName = if (artistRuns.length() > 0) artistRuns.getJSONObject(0).optString("text", "") else ""
                val duration = item.optJSONObject("lengthText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""

                val thumbs = item.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                val thumb = if (thumbs != null && thumbs.length() > 0) {
                    thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                } else ""

                result.add(
                    Track(
                        id = vid,
                        title = title,
                        artist = artistName,
                        duration = duration,
                        thumbnailUrl = cleanThumbnail(thumb, 720)
                    )
                )
            }
            if (result.isNotEmpty()) return@withContext result
        } catch (e: Exception) {
            // fallback
        }
        emptyList()
    }

    suspend fun getLyrics(trackId: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val nextPayload = JSONObject().apply {
                put("videoId", trackId)
            }
            val nextResponse = sendRequest("next", nextPayload)
            val tabs = nextResponse.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs") ?: return@withContext null

            var lyricsBrowseId: String? = null
            for (i in 0 until tabs.length()) {
                val tabRenderer = tabs.getJSONObject(i).optJSONObject("tabRenderer") ?: continue
                val bId = tabRenderer.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                if (!bId.isNullOrBlank() && bId.startsWith("MPLYt")) {
                    lyricsBrowseId = bId
                    break
                }
            }

            if (lyricsBrowseId.isNullOrBlank()) return@withContext null

            val lyricsPayload = JSONObject().apply {
                put("browseId", lyricsBrowseId)
            }
            val lyricsResponse = sendRequest("browse", lyricsPayload)
            val sec = lyricsResponse.optJSONObject("contents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return@withContext null

            for (i in 0 until sec.length()) {
                val shelf = sec.getJSONObject(i).optJSONObject("musicDescriptionShelfRenderer") ?: continue
                val descRuns = shelf.optJSONObject("description")?.optJSONArray("runs") ?: JSONArray()
                val textBuilder = StringBuilder()
                for (j in 0 until descRuns.length()) {
                    textBuilder.append(descRuns.getJSONObject(j).optString("text", ""))
                }
                val lyricsText = textBuilder.toString().trim()

                val footerRuns = shelf.optJSONObject("footer")?.optJSONArray("runs") ?: JSONArray()
                val footerBuilder = StringBuilder()
                for (j in 0 until footerRuns.length()) {
                    footerBuilder.append(footerRuns.getJSONObject(j).optString("text", ""))
                }
                val footerText = footerBuilder.toString().trim()

                if (lyricsText.isNotBlank()) {
                    return@withContext Pair(lyricsText, footerText)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val payload = JSONObject().apply {
                put("input", query)
            }
            val response = sendRequest("music/get_search_suggestions", payload)
            val suggestions = mutableListOf<String>()

            val contents = response.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("searchSuggestionsSectionRenderer")
                ?.optJSONArray("contents") ?: return@withContext emptyList()

            for (i in 0 until contents.length()) {
                val ssr = contents.getJSONObject(i).optJSONObject("searchSuggestionRenderer") ?: continue
                val runs = ssr.optJSONObject("suggestion")?.optJSONArray("runs") ?: JSONArray()
                val sb = StringBuilder()
                for (j in 0 until runs.length()) {
                    sb.append(runs.getJSONObject(j).optString("text", ""))
                }
                val text = sb.toString().trim()
                val fallbackQuery = ssr.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("searchEndpoint")
                    ?.optString("query", "") ?: ""
                val resText = if (text.isNotBlank()) text else fallbackQuery
                if (resText.isNotBlank() && !suggestions.contains(resText)) {
                    suggestions.add(resText)
                }
            }
            suggestions
        } catch (e: Exception) {
            emptyList()
        }
    }
}
