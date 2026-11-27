package com.drliki.MusicLi.data.explore

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

private const val SAAVN_MEDIA_DES_KEY = "38346591"
private val IMAGE_QUALITY_REGEX = Regex("""([_-])(50x50|150x150|500x500)(?=\.[^./?]+(?:$|\?))""")
private val BITRATE_SUFFIX_REGEX = Regex("""_(\d+)\.(mp4|m4a)(\?.*)?$""", RegexOption.IGNORE_CASE)

internal fun isNativeSaavnApiUrl(baseUrl: String): Boolean {
    val trimmed = baseUrl.trim().trimEnd('/')
    return trimmed.endsWith(".php", ignoreCase = true) ||
        trimmed.contains("jiosaavn.com/api.php", ignoreCase = true)
}

internal fun decryptSaavnMediaUrl(encryptedBase64: String?): String? {
    val cleaned = encryptedBase64?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val decodedBytes = Base64.getDecoder().decode(cleaned)
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        val keySpec = SecretKeySpec(SAAVN_MEDIA_DES_KEY.toByteArray(StandardCharsets.US_ASCII), "DES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        val plain = String(cipher.doFinal(decodedBytes), StandardCharsets.UTF_8).trim()
        if (plain.startsWith("http://")) {
            "https://${plain.removePrefix("http://")}"
        } else {
            plain
        }.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }.getOrNull()
}

internal fun buildDownloadUrlsFromDecrypted(
    decryptedUrl: String,
    has320Kbps: Boolean,
): List<SaavnDownloadUrlDto> {
    if (decryptedUrl.isBlank()) return emptyList()
    val match = BITRATE_SUFFIX_REGEX.find(decryptedUrl)
    if (match == null) {
        return listOf(SaavnDownloadUrlDto(quality = "320kbps", url = decryptedUrl))
    }
    val ext = match.groupValues[2]
    val query = match.groupValues[3]
    val qualities = if (has320Kbps) listOf(96, 160, 320) else listOf(96, 160)
    return qualities.map { kbps ->
        val url = BITRATE_SUFFIX_REGEX.replace(decryptedUrl, "_${kbps}.$ext$query")
        SaavnDownloadUrlDto(quality = "${kbps}kbps", url = url)
    }
}

internal fun buildSaavnImages(rawUrl: String?): List<SaavnImageDto> {
    val clean = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
    val httpsUrl = if (clean.startsWith("http://")) "https://${clean.removePrefix("http://")}" else clean
    if (!IMAGE_QUALITY_REGEX.containsMatchIn(httpsUrl)) {
        return listOf(SaavnImageDto(quality = "500x500", url = httpsUrl))
    }
    return listOf("50x50", "150x150", "500x500").map { size ->
        val upgraded = IMAGE_QUALITY_REGEX.replace(httpsUrl) { mr -> "${mr.groupValues[1]}$size" }
        SaavnImageDto(quality = size, url = upgraded)
    }
}

internal data class SaavnLaunchData(
    val trendingSongs: List<SaavnSongDto> = emptyList(),
    val newAlbums: List<SaavnAlbumSearchItemDto> = emptyList(),
    val charts: List<SaavnPlaylistsSearchResponse.Data.ItemDto> = emptyList(),
    val topPlaylists: List<SaavnPlaylistsSearchResponse.Data.ItemDto> = emptyList(),
    val artistRecos: List<SaavnArtistRefDto> = emptyList(),
)

internal data class SaavnNewReleasesResult(
    val songs: List<SaavnSongDto> = emptyList(),
    val albums: List<SaavnAlbumSearchItemDto> = emptyList(),
)

internal object SaavnNativeParser {

    fun parseSongSearchResponse(rawJson: String): SaavnSongSearchResponse {
        val root = JsonParser.parseString(rawJson).asJsonObjectOrNull()
            ?: return SaavnSongSearchResponse(success = false)
        val songs = root.objArray("results").mapNotNull(::parseSong)
        val total = root.strOrNull("total")?.toIntOrNull() ?: root.intOrNull("total") ?: songs.size
        val start = root.strOrNull("start")?.toIntOrNull() ?: root.intOrNull("start") ?: 0
        return SaavnSongSearchResponse(
            success = songs.isNotEmpty() || root.strOrNull("status") != "failure",
            data = SaavnSongSearchResponse.Data(total = total, start = start, results = songs),
        )
    }

    fun parseTrendingSongs(rawJson: String): SaavnSongSearchResponse {
        val arr = extractArray(rawJson)
        val songs = arr.mapNotNull { it.asJsonObjectOrNull()?.let(::parseSong) }
        return SaavnSongSearchResponse(
            success = songs.isNotEmpty(),
            data = SaavnSongSearchResponse.Data(total = songs.size, start = 0, results = songs),
        )
    }

    fun parseAlbumsSearchResponse(rawJson: String): SaavnAlbumsSearchResponse {
        val root = JsonParser.parseString(rawJson).asJsonObjectOrNull()
            ?: return SaavnAlbumsSearchResponse(success = false)
        val albums = root.objArray("results").mapNotNull(::parseAlbumItem)
        val total = root.strOrNull("total")?.toIntOrNull() ?: root.intOrNull("total") ?: albums.size
        val start = root.strOrNull("start")?.toIntOrNull() ?: root.intOrNull("start") ?: 0
        return SaavnAlbumsSearchResponse(
            success = albums.isNotEmpty() || root.strOrNull("status") != "failure",
            data = SaavnAlbumsSearchResponse.Data(total = total, start = start, results = albums),
        )
    }

    fun parseTrendingAlbums(rawJson: String): SaavnAlbumsSearchResponse {
        val arr = extractArray(rawJson)
        val albums = arr.mapNotNull { it.asJsonObjectOrNull()?.let(::parseAlbumItem) }
        return SaavnAlbumsSearchResponse(
            success = albums.isNotEmpty(),
            data = SaavnAlbumsSearchResponse.Data(total = albums.size, start = 0, results = albums),
        )
    }

    fun parseArtistsSearchResponse(rawJson: String): SaavnArtistsSearchResponse {
        val root = JsonParser.parseString(rawJson).asJsonObjectOrNull()
            ?: return SaavnArtistsSearchResponse(success = false)
        val artists = root.objArray("results").mapNotNull(::parseArtistRef)
        val total = root.strOrNull("total")?.toIntOrNull() ?: root.intOrNull("total") ?: artists.size
        val start = root.strOrNull("start")?.toIntOrNull() ?: root.intOrNull("start") ?: 0
        return SaavnArtistsSearchResponse(
            success = artists.isNotEmpty() || root.strOrNull("status") != "failure",
            data = SaavnArtistsSearchResponse.Data(total = total, start = start, results = artists),
        )
    }

    fun parsePlaylistsSearchResponse(rawJson: String): SaavnPlaylistsSearchResponse {
        val root = JsonParser.parseString(rawJson).asJsonObjectOrNull()
            ?: return SaavnPlaylistsSearchResponse(success = false)
        val playlists = root.objArray("results").mapNotNull(::parsePlaylistItem)
        val total = root.strOrNull("total")?.toIntOrNull() ?: root.intOrNull("total") ?: playlists.size
        val start = root.strOrNull("start")?.toIntOrNull() ?: root.intOrNull("start") ?: 0
        return SaavnPlaylistsSearchResponse(
            success = playlists.isNotEmpty() || root.strOrNull("status") != "failure",
            data = SaavnPlaylistsSearchResponse.Data(total = total, start = start, results = playlists),
        )
    }

    fun parseTrendingPlaylists(rawJson: String): SaavnPlaylistsSearchResponse {
        val arr = extractArray(rawJson)
        val playlists = arr.mapNotNull { it.asJsonObjectOrNull()?.let(::parsePlaylistItem) }
        return SaavnPlaylistsSearchResponse(
            success = playlists.isNotEmpty(),
            data = SaavnPlaylistsSearchResponse.Data(total = playlists.size, start = 0, results = playlists),
        )
    }

    fun parseSongResponse(rawJson: String): SaavnSongResponse {
        val root = JsonParser.parseString(rawJson).asJsonObjectOrNull()
            ?: return SaavnSongResponse(success = false)
        val songs = when {
            root.has("songs") && root.get("songs").isJsonArray ->
                root.objArray("songs").mapNotNull(::parseSong)
            root.has("data") && root.get("data").isJsonArray ->
                root.objArray("data").mapNotNull(::parseSong)
            else -> root.entrySet()
                .mapNotNull { (_, v) -> v.asJsonObjectOrNull()?.let(::parseSong) }
        }
        return SaavnSongResponse(success = songs.isNotEmpty(), data = songs)
    }

    fun parseAlbumDetailResponse(rawJson: String): SaavnAlbumDetailResponse {
        val root = JsonParser.parseString(rawJson).asJsonObjectOrNull()
            ?: return SaavnAlbumDetailResponse(success = false)
        val albumId = root.strOrNull("albumid") ?: root.strOrNull("id")
        val albumName = root.strOrNull("title") ?: root.strOrNull("name")
        if (albumId.isNullOrBlank() && albumName.isNullOrBlank()) {
            return SaavnAlbumDetailResponse(success = false)
        }
        val songs = (root.objArray("songs").takeIf { it.isNotEmpty() } ?: root.objArray("list"))
            .mapNotNull(::parseSong)
        val moreInfo = root.objOrNull("more_info")
        val artistsGroup = parseArtistsGroup(root, moreInfo)
        return SaavnAlbumDetailResponse(
            success = true,
            data = SaavnAlbumDetailResponse.Data(
                id = albumId,
                name = albumName,
                url = root.strOrNull("perma_url"),
                description = root.strOrNull("header_desc") ?: root.strOrNull("subtitle"),
                year = root.strOrNull("year")?.toIntOrNull() ?: root.intOrNull("year"),
                language = root.strOrNull("language") ?: songs.firstOrNull()?.language,
                artists = artistsGroup,
                image = buildSaavnImages(root.strOrNull("image")),
                songs = songs,
            ),
        )
    }

    fun parsePlaylistDetailResponse(rawJson: String): SaavnPlaylistDetailResponse {
        val root = JsonParser.parseString(rawJson).asJsonObjectOrNull()
            ?: return SaavnPlaylistDetailResponse(success = false)
        val plId = root.strOrNull("id") ?: root.strOrNull("listid")
        val plName = root.strOrNull("title") ?: root.strOrNull("listname") ?: root.strOrNull("name")
        if (plId.isNullOrBlank() && plName.isNullOrBlank()) {
            return SaavnPlaylistDetailResponse(success = false)
        }
        val songs = (root.objArray("list").takeIf { it.isNotEmpty() } ?: root.objArray("songs"))
            .mapNotNull(::parseSong)
        val moreInfo = root.objOrNull("more_info")
        return SaavnPlaylistDetailResponse(
            success = true,
            data = SaavnPlaylistDetailResponse.Data(
                id = plId,
                name = plName,
                url = root.strOrNull("perma_url"),
                description = root.strOrNull("header_desc")
                    ?: root.strOrNull("subtitle")
                    ?: moreInfo?.strOrNull("description"),
                year = root.strOrNull("year")?.toIntOrNull() ?: root.intOrNull("year"),
                songCount = root.strOrNull("list_count")?.toIntOrNull()
                    ?: moreInfo?.strOrNull("song_count")?.toIntOrNull()
                    ?: songs.size,
                language = root.strOrNull("language") ?: moreInfo?.strOrNull("language"),
                artists = emptyList(),
                image = buildSaavnImages(root.strOrNull("image")),
                songs = songs,
            ),
        )
    }

    fun parseArtistDetailResponse(rawJson: String): SaavnArtistDetailResponse {
        val root = JsonParser.parseString(rawJson).asJsonObjectOrNull()
            ?: return SaavnArtistDetailResponse(success = false)
        val artistId = root.strOrNull("artistId") ?: root.strOrNull("id")
        val artistName = root.strOrNull("name") ?: root.strOrNull("title")
        if (artistId.isNullOrBlank() && artistName.isNullOrBlank()) {
            return SaavnArtistDetailResponse(success = false)
        }
        val topSongs = root.objArray("topSongs").mapNotNull(::parseSong)
        val topAlbums = root.objArray("topAlbums").mapNotNull(::parseAlbumItem)
        val singles = root.objArray("singles").mapNotNull(::parseAlbumItem)
        return SaavnArtistDetailResponse(
            success = true,
            data = SaavnArtistDetailResponse.Data(
                id = artistId,
                name = artistName,
                url = root.strOrNull("perma_url")
                    ?: root.objOrNull("urls")?.strOrNull("overview"),
                followerCount = root.strOrNull("follower_count")?.toIntOrNull()
                    ?: root.intOrNull("follower_count"),
                fanCount = root.strOrNull("fan_count")?.toIntOrNull() ?: root.intOrNull("fan_count"),
                dominantLanguage = root.strOrNull("dominantLanguage"),
                dominantType = root.strOrNull("dominantType") ?: root.strOrNull("subtitle"),
                image = buildSaavnImages(root.strOrNull("image")),
                topSongs = topSongs,
                topAlbums = topAlbums,
                singles = singles,
            ),
        )
    }

    fun parseLyricsResponse(rawJson: String): SaavnLyricsResponse {
        val root = JsonParser.parseString(rawJson).asJsonObjectOrNull()
            ?: return SaavnLyricsResponse(success = false)
        val lyrics = root.strOrNull("lyrics")?.takeIf { it.isNotBlank() }
            ?: return SaavnLyricsResponse(success = false)
        return SaavnLyricsResponse(
            success = true,
            data = SaavnLyricsDto(
                lyrics = lyrics,
                copyright = root.strOrNull("lyrics_copyright"),
                snippet = root.strOrNull("snippet"),
            ),
        )
    }

    fun parseLaunchData(rawJson: String): SaavnLaunchData {
        val root = JsonParser.parseString(rawJson).asJsonObjectOrNull() ?: return SaavnLaunchData()
        val trendingRaw = root.objArray("new_trending")
        val newAlbumsRaw = root.objArray("new_albums")

        val trendingSongs = (trendingRaw + newAlbumsRaw)
            .filter { it.strOrNull("type") == "song" }
            .mapNotNull(::parseSong)
            .distinctBy { it.id }

        val explicitAlbums = (newAlbumsRaw + trendingRaw)
            .filter { it.strOrNull("type") == "album" }
            .mapNotNull(::parseAlbumItem)
            .distinctBy { it.id }

        val singleAlbums = if (explicitAlbums.size < 6) {
            newAlbumsRaw
                .filter { it.strOrNull("type") == "song" }
                .mapNotNull(::parseSongAsAlbumItem)
        } else {
            emptyList()
        }

        val albums = (explicitAlbums + singleAlbums).distinctBy { it.id }
        val charts = root.objArray("charts").mapNotNull(::parsePlaylistItem)
        val topPlaylists = (
            root.objArray("top_playlists") +
                trendingRaw.filter { it.strOrNull("type") == "playlist" }
            )
            .mapNotNull(::parsePlaylistItem)
            .distinctBy { it.id }
        val artistRecos = root.objArray("artist_recos").mapNotNull(::parseArtistRef)

        return SaavnLaunchData(
            trendingSongs = trendingSongs,
            newAlbums = albums,
            charts = charts,
            topPlaylists = topPlaylists,
            artistRecos = artistRecos,
        )
    }

    fun parseNewReleases(rawJson: String): SaavnNewReleasesResult {
        val items = extractArray(rawJson).mapNotNull { it.asJsonObjectOrNull() }
        val songs = items.filter { it.strOrNull("type") == "song" }
            .mapNotNull(::parseSong)
            .distinctBy { it.id }
        val explicitAlbums = items.filter { it.strOrNull("type") == "album" }
            .mapNotNull(::parseAlbumItem)
        val singleAlbums = items.filter { it.strOrNull("type") == "song" }
            .mapNotNull(::parseSongAsAlbumItem)
        val albums = (explicitAlbums + singleAlbums).distinctBy { it.id }
        return SaavnNewReleasesResult(songs = songs, albums = albums)
    }

    private fun parseSongAsAlbumItem(obj: JsonObject): SaavnAlbumSearchItemDto? {
        val moreInfo = obj.objOrNull("more_info")
        val albumId = moreInfo?.strOrNull("album_id") ?: obj.strOrNull("albumid") ?: return null
        val title = moreInfo?.strOrNull("album") ?: obj.strOrNull("title") ?: obj.strOrNull("name")
            ?: return null
        return SaavnAlbumSearchItemDto(
            id = albumId,
            name = title,
            description = obj.strOrNull("subtitle"),
            url = moreInfo?.strOrNull("album_url") ?: obj.strOrNull("perma_url"),
            year = obj.strOrNull("year")?.toIntOrNull() ?: obj.intOrNull("year"),
            playCount = obj.strOrNull("play_count")?.toLongOrNull() ?: obj.longOrNull("play_count"),
            language = obj.strOrNull("language"),
            explicitContent = parseBooleanFlag(obj.get("explicit_content")),
            artists = parseArtistsGroup(obj, moreInfo),
            image = buildSaavnImages(obj.strOrNull("image")),
        )
    }

    private fun parseSong(obj: JsonObject): SaavnSongDto? {
        val id = obj.strOrNull("id")?.takeIf { it.isNotBlank() } ?: return null
        val moreInfo = obj.objOrNull("more_info")
        val title = obj.strOrNull("title") ?: obj.strOrNull("song") ?: obj.strOrNull("name")
            ?: return null
        val albumId = moreInfo?.strOrNull("album_id") ?: obj.strOrNull("albumid")
        val albumName = moreInfo?.strOrNull("album") ?: obj.strOrNull("album")
        val duration = (moreInfo?.strOrNull("duration") ?: obj.strOrNull("duration"))?.toDoubleOrNull()
            ?: moreInfo?.doubleOrNull("duration")
            ?: obj.doubleOrNull("duration")
        val encryptedMediaUrl = moreInfo?.strOrNull("encrypted_media_url")
            ?: obj.strOrNull("encrypted_media_url")
        val has320 = parseBooleanFlag(moreInfo?.get("320kbps") ?: obj.get("320kbps"))
        val decrypted = decryptSaavnMediaUrl(encryptedMediaUrl)
        val downloadUrls = if (decrypted != null) {
            buildDownloadUrlsFromDecrypted(decrypted, has320)
        } else {
            emptyList()
        }
        val explicit = parseBooleanFlag(obj.get("explicit_content"))
        val hasLyrics = parseBooleanFlag(moreInfo?.get("has_lyrics") ?: obj.get("has_lyrics"))
        val artistsGroup = parseArtistsGroup(obj, moreInfo)
        val lyricsText = moreInfo?.strOrNull("lyrics_snippet")
        return SaavnSongDto(
            id = id,
            name = title,
            type = obj.strOrNull("type") ?: "song",
            year = obj.strOrNull("year"),
            releaseDate = moreInfo?.strOrNull("release_date") ?: obj.strOrNull("release_date"),
            duration = duration,
            label = moreInfo?.strOrNull("label") ?: obj.strOrNull("label"),
            explicitContent = explicit,
            playCount = obj.strOrNull("play_count")?.toLongOrNull() ?: obj.longOrNull("play_count"),
            language = obj.strOrNull("language"),
            hasLyrics = hasLyrics,
            lyrics = lyricsText?.let { SaavnLyricsDto(snippet = it) },
            url = obj.strOrNull("perma_url"),
            copyright = moreInfo?.strOrNull("copyright_text") ?: obj.strOrNull("copyright_text"),
            album = if (!albumId.isNullOrBlank() || !albumName.isNullOrBlank()) {
                SaavnAlbumRefDto(id = albumId, name = albumName, url = moreInfo?.strOrNull("album_url"))
            } else {
                null
            },
            artists = artistsGroup,
            image = buildSaavnImages(obj.strOrNull("image")),
            downloadUrl = downloadUrls,
        )
    }

    private fun parseAlbumItem(obj: JsonObject): SaavnAlbumSearchItemDto? {
        val id = obj.strOrNull("id") ?: obj.strOrNull("albumid") ?: return null
        val title = obj.strOrNull("title") ?: obj.strOrNull("name") ?: return null
        val moreInfo = obj.objOrNull("more_info")
        return SaavnAlbumSearchItemDto(
            id = id,
            name = title,
            description = obj.strOrNull("header_desc") ?: obj.strOrNull("subtitle"),
            url = obj.strOrNull("perma_url"),
            year = obj.strOrNull("year")?.toIntOrNull() ?: obj.intOrNull("year"),
            playCount = obj.strOrNull("play_count")?.toLongOrNull() ?: obj.longOrNull("play_count"),
            language = obj.strOrNull("language"),
            explicitContent = parseBooleanFlag(obj.get("explicit_content")),
            artists = parseArtistsGroup(obj, moreInfo),
            image = buildSaavnImages(obj.strOrNull("image")),
        )
    }

    private fun parsePlaylistItem(obj: JsonObject): SaavnPlaylistsSearchResponse.Data.ItemDto? {
        val id = obj.strOrNull("id") ?: obj.strOrNull("listid") ?: return null
        val title = obj.strOrNull("title") ?: obj.strOrNull("listname") ?: obj.strOrNull("name") ?: return null
        val moreInfo = obj.objOrNull("more_info")
        return SaavnPlaylistsSearchResponse.Data.ItemDto(
            id = id,
            name = title,
            type = obj.strOrNull("type") ?: "playlist",
            url = obj.strOrNull("perma_url"),
            image = buildSaavnImages(obj.strOrNull("image")),
            songCount = obj.strOrNull("numsongs")?.toIntOrNull()
                ?: obj.strOrNull("list_count")?.toIntOrNull()
                ?: obj.strOrNull("count")?.toIntOrNull()
                ?: obj.intOrNull("count")
                ?: moreInfo?.strOrNull("song_count")?.toIntOrNull(),
            language = moreInfo?.strOrNull("language") ?: obj.strOrNull("language"),
            explicitContent = parseBooleanFlag(obj.get("explicit_content")),
        )
    }

    private fun parseArtistRef(obj: JsonObject): SaavnArtistRefDto? {
        val id = obj.strOrNull("id") ?: obj.strOrNull("artistId") ?: return null
        val name = obj.strOrNull("name") ?: obj.strOrNull("title") ?: return null
        val rawRole = obj.strOrNull("role") ?: obj.strOrNull("subtitle")
        val cleanRole = if (rawRole.equals("Artist Radio", ignoreCase = true)) "Artist" else rawRole
        return SaavnArtistRefDto(
            id = id,
            name = name,
            role = cleanRole,
            type = obj.strOrNull("type") ?: "artist",
            image = buildSaavnImages(obj.strOrNull("image")),
            url = obj.strOrNull("perma_url"),
        )
    }

    private fun parseArtistsGroup(obj: JsonObject, moreInfo: JsonObject?): SaavnArtistsGroupDto? {
        val artistMap = moreInfo?.objOrNull("artistMap") ?: obj.objOrNull("artistMap")
        if (artistMap != null) {
            val primary = artistMap.objArray("primary_artists").mapNotNull(::parseArtistRef)
            val featured = artistMap.objArray("featured_artists").mapNotNull(::parseArtistRef)
            val all = artistMap.objArray("artists").mapNotNull(::parseArtistRef)
            if (primary.isNotEmpty() || featured.isNotEmpty() || all.isNotEmpty()) {
                return SaavnArtistsGroupDto(
                    primary = primary,
                    featured = featured,
                    all = all.ifEmpty { (primary + featured).distinctBy { it.name } },
                )
            }
        }
        val rawPrimary = (moreInfo?.strOrNull("music") ?: obj.strOrNull("primary_artists") ?: obj.strOrNull("subtitle"))
            .orEmpty()
        val parsed = rawPrimary.split(",")
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { SaavnArtistRefDto(name = it) }
        return if (parsed.isEmpty()) null else SaavnArtistsGroupDto(primary = parsed, all = parsed)
    }

    private fun parseBooleanFlag(el: JsonElement?): Boolean {
        if (el == null || el.isJsonNull) return false
        if (el.isJsonPrimitive) {
            val prim = el.asJsonPrimitive
            if (prim.isBoolean) return prim.asBoolean
            val s = prim.asString.trim().lowercase()
            return s == "true" || s == "1" || s == "yes"
        }
        return false
    }

    private fun extractArray(rawJson: String): List<JsonElement> {
        val el = JsonParser.parseString(rawJson)
        return when {
            el.isJsonArray -> el.asJsonArray.toList()
            el.isJsonObject -> {
                val obj = el.asJsonObject
                when {
                    obj.has("results") && obj.get("results").isJsonArray -> obj.getAsJsonArray("results").toList()
                    obj.has("data") && obj.get("data").isJsonArray -> obj.getAsJsonArray("data").toList()
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun JsonElement?.asJsonObjectOrNull(): JsonObject? =
        if (this != null && isJsonObject) asJsonObject else null

    private fun JsonObject.objOrNull(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.objArray(key: String): List<JsonObject> {
        val el = get(key) ?: return emptyList()
        if (!el.isJsonArray) return emptyList()
        return el.asJsonArray.mapNotNull { it.asJsonObjectOrNull() }
    }

    private fun JsonObject.strOrNull(key: String): String? {
        val el = get(key) ?: return null
        return if (el.isJsonPrimitive) el.asString.takeIf { it.isNotBlank() } else null
    }

    private fun JsonObject.intOrNull(key: String): Int? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()

    private fun JsonObject.longOrNull(key: String): Long? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asLong }.getOrNull()

    private fun JsonObject.doubleOrNull(key: String): Double? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asDouble }.getOrNull()
}
