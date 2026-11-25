package com.lostf1sh.pixelplayeross.data.service

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.lostf1sh.pixelplayeross.data.database.EngagementDao
import com.lostf1sh.pixelplayeross.data.model.Album
import com.lostf1sh.pixelplayeross.data.model.Artist
import com.lostf1sh.pixelplayeross.data.model.Playlist
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.preferences.PlaylistPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import com.lostf1sh.pixelplayeross.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides a browsable and searchable Media3 library tree for Android Auto and external media browsers.
 */
@Singleton
class AutoMediaBrowseTree @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val engagementDao: EngagementDao
) {
    companion object {
        const val ROOT_ID = "auto_root"
        const val RECENTS_ID = "auto_recents"
        const val LIKED_ID = "auto_liked"
        const val ALBUMS_ID = "auto_albums"
        const val ARTISTS_ID = "auto_artists"
        const val PLAYLISTS_ID = "auto_playlists"
        const val ALL_SONGS_ID = "auto_all_songs"

        const val ALBUM_PREFIX = "auto_album:"
        const val ARTIST_PREFIX = "auto_artist:"
        const val PLAYLIST_PREFIX = "auto_playlist:"

        private const val RECENTLY_PLAYED_LIMIT = 100

        internal fun <T> paginate(items: List<T>, page: Int, pageSize: Int): List<T> {
            if (items.isEmpty() || page < 0 || pageSize <= 0) return items
            val fromIndex = (page.toLong() * pageSize.toLong()).coerceAtMost(items.size.toLong()).toInt()
            val toIndex = (fromIndex.toLong() + pageSize.toLong()).coerceAtMost(items.size.toLong()).toInt()
            return items.subList(fromIndex, toIndex)
        }

        internal fun isContainerId(mediaId: String): Boolean {
            return mediaId == ROOT_ID ||
                mediaId == RECENTS_ID ||
                mediaId == LIKED_ID ||
                mediaId == ALBUMS_ID ||
                mediaId == ARTISTS_ID ||
                mediaId == PLAYLISTS_ID ||
                mediaId == ALL_SONGS_ID ||
                mediaId.startsWith(ALBUM_PREFIX) ||
                mediaId.startsWith(ARTIST_PREFIX) ||
                mediaId.startsWith(PLAYLIST_PREFIX)
        }
    }

    fun getLibraryRootItem(): MediaItem {
        return buildFolderItem(
            mediaId = ROOT_ID,
            title = "PixelPlayer",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        )
    }

    suspend fun getChildren(
        parentId: String,
        page: Int,
        pageSize: Int
    ): LibraryResult<ImmutableList<MediaItem>> {
        val items: List<MediaItem> = when {
            parentId == ROOT_ID -> paginate(rootCategories(), page, pageSize)
            parentId == RECENTS_ID -> {
                val recentSongs = loadRecentlyPlayedSongs()
                paginate(recentSongs, page, pageSize).map(::buildPlayableSongItem)
            }
            parentId == LIKED_ID -> {
                val liked = musicRepository.getFavoriteSongsOnce()
                paginate(liked, page, pageSize).map(::buildPlayableSongItem)
            }
            parentId == ALBUMS_ID -> {
                val albums = musicRepository.getAllAlbumsOnce()
                paginate(albums, page, pageSize).map(::buildAlbumItem)
            }
            parentId == ARTISTS_ID -> {
                val artists = musicRepository.getAllArtistsOnce()
                paginate(artists, page, pageSize).map(::buildArtistItem)
            }
            parentId == PLAYLISTS_ID -> {
                val playlists = playlistPreferencesRepository.userPlaylistsFlow.first()
                paginate(playlists, page, pageSize).map(::buildPlaylistItem)
            }
            parentId == ALL_SONGS_ID -> {
                val allSongs = musicRepository.getAllSongsOnce()
                paginate(allSongs, page, pageSize).map(::buildPlayableSongItem)
            }
            parentId.startsWith(ALBUM_PREFIX) -> {
                val albumId = parentId.removePrefix(ALBUM_PREFIX).toLongOrNull()
                    ?: return LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                val songs = musicRepository.getSongsForAlbum(albumId).first()
                paginate(songs, page, pageSize).map(::buildPlayableSongItem)
            }
            parentId.startsWith(ARTIST_PREFIX) -> {
                val artistId = parentId.removePrefix(ARTIST_PREFIX).toLongOrNull()
                    ?: return LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                val songs = musicRepository.getSongsForArtist(artistId).first()
                paginate(songs, page, pageSize).map(::buildPlayableSongItem)
            }
            parentId.startsWith(PLAYLIST_PREFIX) -> {
                val playlistId = parentId.removePrefix(PLAYLIST_PREFIX)
                val playlist = playlistPreferencesRepository.userPlaylistsFlow.first()
                    .firstOrNull { it.id == playlistId }
                    ?: return LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                val songs = loadOrderedPlaylistSongs(playlist)
                paginate(songs, page, pageSize).map { song ->
                    MediaItemBuilder.withPlaylistId(buildPlayableSongItem(song), playlist.id)
                }
            }
            else -> return LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }
        return LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
    }

    suspend fun getItem(mediaId: String): LibraryResult<MediaItem> {
        rootCategories().firstOrNull { it.mediaId == mediaId }?.let {
            return LibraryResult.ofItem(it, null)
        }
        if (mediaId == ROOT_ID) {
            return LibraryResult.ofItem(getLibraryRootItem(), null)
        }
        if (mediaId.startsWith(ALBUM_PREFIX)) {
            val albumId = mediaId.removePrefix(ALBUM_PREFIX).toLongOrNull()
                ?: return LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            val album = musicRepository.getAllAlbumsOnce().firstOrNull { it.id == albumId }
                ?: return LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            return LibraryResult.ofItem(buildAlbumItem(album), null)
        }
        if (mediaId.startsWith(ARTIST_PREFIX)) {
            val artistId = mediaId.removePrefix(ARTIST_PREFIX).toLongOrNull()
                ?: return LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            val artist = musicRepository.getAllArtistsOnce().firstOrNull { it.id == artistId }
                ?: return LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            return LibraryResult.ofItem(buildArtistItem(artist), null)
        }
        if (mediaId.startsWith(PLAYLIST_PREFIX)) {
            val playlistId = mediaId.removePrefix(PLAYLIST_PREFIX)
            val playlist = playlistPreferencesRepository.userPlaylistsFlow.first()
                .firstOrNull { it.id == playlistId }
                ?: return LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            return LibraryResult.ofItem(buildPlaylistItem(playlist), null)
        }
        val song = musicRepository.getSongsByIdsOnce(listOf(mediaId)).firstOrNull()
            ?: return LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        return LibraryResult.ofItem(buildPlayableSongItem(song), null)
    }

    suspend fun searchCount(query: String): Int {
        val clean = query.trim()
        if (clean.isEmpty()) return 0
        return musicRepository.searchSongs(clean).first().size
    }

    suspend fun getSearchResult(
        query: String,
        page: Int,
        pageSize: Int
    ): LibraryResult<ImmutableList<MediaItem>> {
        val clean = query.trim()
        if (clean.isEmpty()) {
            return LibraryResult.ofItemList(ImmutableList.of(), null)
        }
        val matches = musicRepository.searchSongs(clean).first()
        val items = paginate(matches, page, pageSize).map(::buildPlayableSongItem)
        return LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
    }

    suspend fun expandContainerSongs(containerMediaId: String): List<Pair<Song, String?>>? {
        return when {
            containerMediaId == RECENTS_ID -> loadRecentlyPlayedSongs().map { it to null }
            containerMediaId == LIKED_ID -> musicRepository.getFavoriteSongsOnce().map { it to null }
            containerMediaId == ALL_SONGS_ID -> musicRepository.getAllSongsOnce().map { it to null }
            containerMediaId.startsWith(ALBUM_PREFIX) -> {
                val albumId = containerMediaId.removePrefix(ALBUM_PREFIX).toLongOrNull() ?: return null
                musicRepository.getSongsForAlbum(albumId).first().map { it to null }
            }
            containerMediaId.startsWith(ARTIST_PREFIX) -> {
                val artistId = containerMediaId.removePrefix(ARTIST_PREFIX).toLongOrNull() ?: return null
                musicRepository.getSongsForArtist(artistId).first().map { it to null }
            }
            containerMediaId.startsWith(PLAYLIST_PREFIX) -> {
                val playlistId = containerMediaId.removePrefix(PLAYLIST_PREFIX)
                val playlist = playlistPreferencesRepository.userPlaylistsFlow.first()
                    .firstOrNull { it.id == playlistId } ?: return null
                loadOrderedPlaylistSongs(playlist).map { it to playlist.id }
            }
            else -> null
        }
    }

    private suspend fun loadRecentlyPlayedSongs(): List<Song> {
        val recentIds = engagementDao.getRecentlyPlayedSongs(RECENTLY_PLAYED_LIMIT).map { it.songId }
        if (recentIds.isEmpty()) return emptyList()
        val byId = musicRepository.getSongsByIdsOnce(recentIds).associateBy { it.id }
        return recentIds.mapNotNull { byId[it] }
    }

    private suspend fun loadOrderedPlaylistSongs(playlist: Playlist): List<Song> {
        if (playlist.songIds.isEmpty()) return emptyList()
        val byId = musicRepository.getSongsByIdsOnce(playlist.songIds).associateBy { it.id }
        return playlist.songIds.mapNotNull { byId[it] }
    }

    private fun rootCategories(): List<MediaItem> = listOf(
        buildFolderItem(
            mediaId = RECENTS_ID,
            title = "Recently Played",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        ),
        buildFolderItem(
            mediaId = LIKED_ID,
            title = "Liked Songs",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
        ),
        buildFolderItem(
            mediaId = ALBUMS_ID,
            title = "Albums",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
        ),
        buildFolderItem(
            mediaId = ARTISTS_ID,
            title = "Artists",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
        ),
        buildFolderItem(
            mediaId = PLAYLISTS_ID,
            title = "Playlists",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
        ),
        buildFolderItem(
            mediaId = ALL_SONGS_ID,
            title = "All Songs",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        )
    )

    private fun buildFolderItem(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        artworkUri: Uri? = null,
        mediaType: Int
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .apply {
                if (!subtitle.isNullOrBlank()) setSubtitle(subtitle)
                if (artworkUri != null) setArtworkUri(artworkUri)
            }
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildAlbumItem(album: Album): MediaItem {
        return buildFolderItem(
            mediaId = "$ALBUM_PREFIX${album.id}",
            title = album.title,
            subtitle = album.artist,
            artworkUri = MediaItemBuilder.externalControllerArtworkUri(context, album.albumArtUriString),
            mediaType = MediaMetadata.MEDIA_TYPE_ALBUM
        )
    }

    private fun buildArtistItem(artist: Artist): MediaItem {
        val art = artist.customImageUri ?: artist.imageUrl
        return buildFolderItem(
            mediaId = "$ARTIST_PREFIX${artist.id}",
            title = artist.name,
            subtitle = "${artist.songCount} songs",
            artworkUri = MediaItemBuilder.externalControllerArtworkUri(context, art),
            mediaType = MediaMetadata.MEDIA_TYPE_ARTIST
        )
    }

    private fun buildPlaylistItem(playlist: Playlist): MediaItem {
        return buildFolderItem(
            mediaId = "$PLAYLIST_PREFIX${playlist.id}",
            title = playlist.name,
            subtitle = "${playlist.songIds.size} songs",
            artworkUri = MediaItemBuilder.externalControllerArtworkUri(context, playlist.coverImageUri),
            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
        )
    }

    @OptIn(UnstableApi::class)
    private fun buildPlayableSongItem(song: Song): MediaItem {
        val base = MediaItemBuilder.buildForExternalController(context, song)
        val metadata = base.mediaMetadata.buildUpon()
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        return base.buildUpon()
            .setMediaMetadata(metadata)
            .build()
    }
}
