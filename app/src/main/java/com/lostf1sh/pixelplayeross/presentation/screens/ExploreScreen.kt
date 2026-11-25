package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.explore.ExploreAlbum
import com.lostf1sh.pixelplayeross.data.explore.ExploreArtist
import com.lostf1sh.pixelplayeross.data.explore.ExploreDownloadItem
import com.lostf1sh.pixelplayeross.data.explore.ExploreDownloadStatus
import com.lostf1sh.pixelplayeross.data.explore.ExplorePlaylist
import com.lostf1sh.pixelplayeross.data.explore.ExploreSong
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.rememberModalSheetState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ExploreDetailState
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ExploreFilterTab
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ExploreViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.sharedPlayerViewModel
import kotlinx.coroutines.launch
import java.util.Locale

private val EXPLORE_LANGUAGES = listOf("All", "Hindi", "English", "Punjabi", "Tamil", "Telugu")

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    paddingValuesParent: PaddingValues,
    onOpenDownloads: () -> Unit,
    playerViewModel: PlayerViewModel = sharedPlayerViewModel(),
    exploreViewModel: ExploreViewModel = hiltViewModel(),
) {
    val uiState by exploreViewModel.uiState.collectAsStateWithLifecycle()
    val detailState by exploreViewModel.detailState.collectAsStateWithLifecycle()
    val downloads by exploreViewModel.downloads.collectAsStateWithLifecycle()
    val downloadedIds by exploreViewModel.downloadedSongIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    val activeDownloadCount = downloads.count {
        it.status == ExploreDownloadStatus.QUEUED || it.status == ExploreDownloadStatus.DOWNLOADING
    }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val streamSongs: (List<ExploreSong>, ExploreSong, String) -> Unit = { queue, startSong, queueName ->
        scope.launch {
            val kbps = exploreViewModel.currentPreferredBitrateKbps()
            val resolvedStart = if (startSong.streamUrlsByQuality.isEmpty()) {
                exploreViewModel.resolvePlayableQueue(listOf(startSong)).firstOrNull() ?: startSong
            } else startSong
            val resolvedQueue = exploreViewModel.resolvePlayableQueue(queue)
            val playerSongs = (if (resolvedQueue.isNotEmpty()) resolvedQueue else listOf(resolvedStart))
                .filter { it.selectStreamUrl(kbps) != null }
                .map { it.toPlayerSong(kbps) }
            val startPlayerSong = playerSongs.firstOrNull { it.id == "saavn_${resolvedStart.id}" }
                ?: resolvedStart.toPlayerSong(kbps)
            if (startPlayerSong.contentUriString.isNotBlank()) {
                playerViewModel.showAndPlaySong(
                    song = startPlayerSong,
                    contextSongs = if (playerSongs.isNotEmpty()) playerSongs else listOf(startPlayerSong),
                    queueName = queueName,
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusBarTop + 12.dp,
                bottom = paddingValuesParent.calculateBottomPadding() + navBarBottom + MiniPlayerHeight + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Header row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.explore_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Stream & download 320 kbps with metadata & synced lyrics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onOpenDownloads,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        BadgedBox(
                            badge = {
                                if (activeDownloadCount > 0) {
                                    Badge { Text("$activeDownloadCount") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DownloadDone,
                                contentDescription = stringResource(R.string.explore_downloads_title),
                            )
                        }
                    }
                }
            }

            // Search bar
            item {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = exploreViewModel::onQueryChange,
                    placeholder = {
                        Text(
                            stringResource(R.string.explore_search_placeholder),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = exploreViewModel::clearSearch) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            exploreViewModel.submitSearch()
                        }
                    ),
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            if (uiState.query.isBlank()) {
                // Recent searches
                if (uiState.recentSearches.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            uiState.recentSearches.forEach { recent ->
                                FilterChip(
                                    selected = false,
                                    onClick = { exploreViewModel.submitSearch(recent) },
                                    label = { Text(recent) },
                                )
                            }
                        }
                    }
                }

                // Language / Mood filter chips
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EXPLORE_LANGUAGES.forEach { language ->
                            FilterChip(
                                selected = uiState.selectedLanguage == language,
                                onClick = { exploreViewModel.selectLanguage(language) },
                                label = { Text(language) },
                            )
                        }
                    }
                }

                if (uiState.isLoadingFeed) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (uiState.feedError != null) {
                    item {
                        ExploreErrorCard(
                            message = uiState.feedError.orEmpty(),
                            onRetry = { exploreViewModel.loadFeed(forceRefresh = true) },
                        )
                    }
                } else {
                    val feed = uiState.feed
                    if (feed.trendingSongs.isNotEmpty()) {
                        item {
                            ExploreSectionHeader(title = "Trending Songs")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(feed.trendingSongs, key = { "feed_song_${it.id}" }) { song ->
                                    ExploreSongSquareCard(
                                        song = song,
                                        downloadItem = downloads.firstOrNull { it.song.id == song.id },
                                        isDownloaded = downloadedIds.contains(song.id),
                                        onPlayClick = { streamSongs(feed.trendingSongs, song, "Explore: Trending") },
                                        onDownloadClick = { exploreViewModel.downloadSong(song) },
                                    )
                                }
                            }
                        }
                    }

                    if (feed.topAlbums.isNotEmpty()) {
                        item {
                            ExploreSectionHeader(title = "Top Albums")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(feed.topAlbums, key = { "feed_album_${it.id}" }) { album ->
                                    ExploreCollectionSquareCard(
                                        title = album.title,
                                        subtitle = album.artist,
                                        artworkUrl = album.artworkUrl,
                                        onClick = { exploreViewModel.openAlbum(album) },
                                    )
                                }
                            }
                        }
                    }

                    if (feed.featuredPlaylists.isNotEmpty()) {
                        item {
                            ExploreSectionHeader(title = "Featured Playlists")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(feed.featuredPlaylists, key = { "feed_pl_${it.id}" }) { playlist ->
                                    ExploreCollectionSquareCard(
                                        title = playlist.title,
                                        subtitle = playlist.subtitle,
                                        artworkUrl = playlist.artworkUrl,
                                        onClick = { exploreViewModel.openPlaylist(playlist) },
                                    )
                                }
                            }
                        }
                    }

                    if (feed.topArtists.isNotEmpty()) {
                        item {
                            ExploreSectionHeader(title = "Artists")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(feed.topArtists, key = { "feed_artist_${it.id}" }) { artist ->
                                    ExploreArtistCircleCard(
                                        artist = artist,
                                        onClick = { exploreViewModel.openArtist(artist) },
                                    )
                                }
                            }
                        }
                    }

                    if (feed.trendingSongs.isEmpty() &&
                        feed.topAlbums.isEmpty() &&
                        feed.featuredPlaylists.isEmpty() &&
                        feed.topArtists.isEmpty()
                    ) {
                        item {
                            ExploreErrorCard(
                                message = "No tracks returned from Explore catalog.",
                                onRetry = { exploreViewModel.loadFeed(forceRefresh = true) },
                            )
                        }
                    }
                }
            } else {
                // Search Filter Tabs
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ExploreFilterTab.entries.forEach { tab ->
                            FilterChip(
                                selected = uiState.selectedFilter == tab,
                                onClick = { exploreViewModel.selectFilterTab(tab) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }

                if (uiState.isSearching) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (uiState.searchError != null) {
                    item {
                        ExploreErrorCard(
                            message = uiState.searchError.orEmpty(),
                            onRetry = { exploreViewModel.submitSearch() },
                        )
                    }
                } else {
                    val res = uiState.searchResults
                    val showSongs = uiState.selectedFilter == ExploreFilterTab.ALL || uiState.selectedFilter == ExploreFilterTab.SONGS
                    val showAlbums = uiState.selectedFilter == ExploreFilterTab.ALL || uiState.selectedFilter == ExploreFilterTab.ALBUMS
                    val showPlaylists = uiState.selectedFilter == ExploreFilterTab.ALL || uiState.selectedFilter == ExploreFilterTab.PLAYLISTS
                    val showArtists = uiState.selectedFilter == ExploreFilterTab.ALL || uiState.selectedFilter == ExploreFilterTab.ARTISTS

                    if (showSongs && res.songs.isNotEmpty()) {
                        item { ExploreSectionHeader(title = "Songs") }
                        items(res.songs, key = { "res_song_${it.id}" }) { song ->
                            ExploreSongRowItem(
                                song = song,
                                downloadItem = downloads.firstOrNull { it.song.id == song.id },
                                isDownloaded = downloadedIds.contains(song.id),
                                onPlayClick = { streamSongs(res.songs, song, "Explore: ${uiState.query}") },
                                onDownloadClick = { exploreViewModel.downloadSong(song) },
                            )
                        }
                    }

                    if (showAlbums && res.albums.isNotEmpty()) {
                        item { ExploreSectionHeader(title = "Albums") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(res.albums, key = { "res_album_${it.id}" }) { album ->
                                    ExploreCollectionSquareCard(
                                        title = album.title,
                                        subtitle = album.artist,
                                        artworkUrl = album.artworkUrl,
                                        onClick = { exploreViewModel.openAlbum(album) },
                                    )
                                }
                            }
                        }
                    }

                    if (showPlaylists && res.playlists.isNotEmpty()) {
                        item { ExploreSectionHeader(title = "Playlists") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(res.playlists, key = { "res_pl_${it.id}" }) { playlist ->
                                    ExploreCollectionSquareCard(
                                        title = playlist.title,
                                        subtitle = playlist.subtitle,
                                        artworkUrl = playlist.artworkUrl,
                                        onClick = { exploreViewModel.openPlaylist(playlist) },
                                    )
                                }
                            }
                        }
                    }

                    if (showArtists && res.artists.isNotEmpty()) {
                        item { ExploreSectionHeader(title = "Artists") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(res.artists, key = { "res_artist_${it.id}" }) { artist ->
                                    ExploreArtistCircleCard(
                                        artist = artist,
                                        onClick = { exploreViewModel.openArtist(artist) },
                                    )
                                }
                            }
                        }
                    }

                    val hasVisibleSearchItems = (showSongs && res.songs.isNotEmpty()) ||
                        (showAlbums && res.albums.isNotEmpty()) ||
                        (showPlaylists && res.playlists.isNotEmpty()) ||
                        (showArtists && res.artists.isNotEmpty())
                    if (!hasVisibleSearchItems) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.search_no_results_found),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (detailState != ExploreDetailState.Hidden) {
        val sheetState = rememberModalSheetState()
        ModalBottomSheet(
            onDismissRequest = exploreViewModel::dismissDetail,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            ExploreDetailSheetContent(
                detailState = detailState,
                downloads = downloads,
                downloadedIds = downloadedIds,
                onStreamSong = { list, song, name ->
                    streamSongs(list, song, name)
                },
                onDownloadSong = { song, folder -> exploreViewModel.downloadSong(song, folder) },
                onDownloadAll = { name, songs -> exploreViewModel.downloadCollection(name, songs) },
                onOpenAlbum = exploreViewModel::openAlbum,
                onClose = exploreViewModel::dismissDetail,
            )
        }
    }
}

@Composable
private fun ExploreSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun ExploreSongSquareCard(
    song: ExploreSong,
    downloadItem: ExploreDownloadItem?,
    isDownloaded: Boolean,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onPlayClick),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = song.artworkUrl ?: song.thumbnailUrl,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                ArtworkDownloadBadgeButton(
                    downloadItem = downloadItem,
                    isDownloaded = isDownloaded,
                    onClick = onDownloadClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }
            Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ExploreSongRowItem(
    song: ExploreSong,
    downloadItem: ExploreDownloadItem?,
    isDownloaded: Boolean,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    subfolder: String? = null,
) {
    Surface(
        onClick = onPlayClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = song.thumbnailUrl ?: song.artworkUrl,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                ArtworkDownloadBadgeButton(
                    downloadItem = downloadItem,
                    isDownloaded = isDownloaded,
                    onClick = onDownloadClick,
                    sizeDp = 24,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        if (song.durationSeconds > 0) {
                            append(formatDurationSec(song.durationSeconds)).append(" • ")
                        }
                        append(song.artist)
                        if (song.albumName.isNotBlank()) {
                            append(" • ").append(song.albumName)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ExploreCollectionSquareCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick),
    ) {
        Column {
            AsyncImage(
                model = artworkUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ExploreArtistCircleCard(
    artist: ExploreArtist,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        AsyncImage(
            model = artist.artworkUrl,
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtworkDownloadBadgeButton(
    downloadItem: ExploreDownloadItem?,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 30,
) {
    val active = downloadItem?.status == ExploreDownloadStatus.QUEUED ||
        downloadItem?.status == ExploreDownloadStatus.DOWNLOADING
    val bgColor = when {
        isDownloaded -> MaterialTheme.colorScheme.primary
        active -> Color.Black.copy(alpha = 0.72f)
        else -> Color.Black.copy(alpha = 0.65f)
    }
    Surface(
        onClick = { if (!isDownloaded && !active) onClick() },
        shape = CircleShape,
        color = bgColor,
        modifier = modifier.size(sizeDp.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                active -> {
                    val progress = downloadItem?.progress ?: 0f
                    CircularProgressIndicator(
                        progress = { progress.coerceIn(0.1f, 1f) },
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size((sizeDp - 8).dp),
                    )
                }
                isDownloaded -> {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.explore_downloaded),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size((sizeDp - 10).dp),
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.explore_download_song),
                        tint = Color.White,
                        modifier = Modifier.size((sizeDp - 10).dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreDetailSheetContent(
    detailState: ExploreDetailState,
    downloads: List<ExploreDownloadItem>,
    downloadedIds: Set<String>,
    onStreamSong: (List<ExploreSong>, ExploreSong, String) -> Unit,
    onDownloadSong: (ExploreSong, String?) -> Unit,
    onDownloadAll: (String, List<ExploreSong>) -> Unit,
    onOpenAlbum: (ExploreAlbum) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp, max = 640.dp)
            .padding(bottom = 24.dp),
    ) {
        when (detailState) {
            ExploreDetailState.Hidden -> Unit
            is ExploreDetailState.Loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(detailState.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is ExploreDetailState.Error -> {
                ExploreErrorCard(message = detailState.message, onRetry = onClose)
            }
            is ExploreDetailState.AlbumDetail -> {
                val album = detailState.album
                CollectionDetailHeader(
                    title = album.title,
                    subtitle = "${album.artist}${if (album.year > 0) " • ${album.year}" else ""}",
                    artworkUrl = album.artworkUrl,
                    onPlayAll = {
                        album.songs.firstOrNull()?.let { first ->
                            onStreamSong(album.songs, first, album.title)
                        }
                    },
                    onDownloadAll = { onDownloadAll(album.title, album.songs) },
                )
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(album.songs, key = { "det_album_song_${it.id}" }) { song ->
                        ExploreSongRowItem(
                            song = song,
                            downloadItem = downloads.firstOrNull { it.song.id == song.id },
                            isDownloaded = downloadedIds.contains(song.id),
                            onPlayClick = { onStreamSong(album.songs, song, album.title) },
                            onDownloadClick = { onDownloadSong(song, album.title) },
                            subfolder = album.title,
                        )
                    }
                }
            }
            is ExploreDetailState.PlaylistDetail -> {
                val playlist = detailState.playlist
                CollectionDetailHeader(
                    title = playlist.title,
                    subtitle = playlist.subtitle,
                    artworkUrl = playlist.artworkUrl,
                    onPlayAll = {
                        playlist.songs.firstOrNull()?.let { first ->
                            onStreamSong(playlist.songs, first, playlist.title)
                        }
                    },
                    onDownloadAll = { onDownloadAll(playlist.title, playlist.songs) },
                )
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(playlist.songs, key = { "det_pl_song_${it.id}" }) { song ->
                        ExploreSongRowItem(
                            song = song,
                            downloadItem = downloads.firstOrNull { it.song.id == song.id },
                            isDownloaded = downloadedIds.contains(song.id),
                            onPlayClick = { onStreamSong(playlist.songs, song, playlist.title) },
                            onDownloadClick = { onDownloadSong(song, playlist.title) },
                            subfolder = playlist.title,
                        )
                    }
                }
            }
            is ExploreDetailState.ArtistDetail -> {
                val artist = detailState.artist
                CollectionDetailHeader(
                    title = artist.name,
                    subtitle = artist.role,
                    artworkUrl = artist.artworkUrl,
                    onPlayAll = {
                        artist.topSongs.firstOrNull()?.let { first ->
                            onStreamSong(artist.topSongs, first, artist.name)
                        }
                    },
                    onDownloadAll = { onDownloadAll(artist.name, artist.topSongs) },
                )
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    if (artist.topSongs.isNotEmpty()) {
                        item { ExploreSectionHeader("Top Songs") }
                        items(artist.topSongs, key = { "det_art_song_${it.id}" }) { song ->
                            ExploreSongRowItem(
                                song = song,
                                downloadItem = downloads.firstOrNull { it.song.id == song.id },
                                isDownloaded = downloadedIds.contains(song.id),
                                onPlayClick = { onStreamSong(artist.topSongs, song, artist.name) },
                                onDownloadClick = { onDownloadSong(song, null) },
                            )
                        }
                    }
                    if (artist.topAlbums.isNotEmpty()) {
                        item { Spacer(Modifier.height(12.dp)) }
                        item { ExploreSectionHeader("Albums") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(artist.topAlbums, key = { "det_art_alb_${it.id}" }) { album ->
                                    ExploreCollectionSquareCard(
                                        title = album.title,
                                        subtitle = album.artist,
                                        artworkUrl = album.artworkUrl,
                                        onClick = { onOpenAlbum(album) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionDetailHeader(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onPlayAll: () -> Unit,
    onDownloadAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPlayAll,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play")
                }
                FilledTonalButton(
                    onClick = onDownloadAll,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.explore_download_all))
                }
            }
        }
    }
}

@Composable
private fun ExploreErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retry")
            }
        }
    }
}

private fun formatDurationSec(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", m, s)
}
