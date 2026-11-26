package com.drliki.MusicLi.presentation.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.size.Size
import com.drliki.MusicLi.R
import com.drliki.MusicLi.data.model.Album
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CIRCULAR_LOOP_MULTIPLIER = 1_000

internal fun circularCarouselPageCount(itemCount: Int): Int =
    if (itemCount <= 1) itemCount.coerceAtLeast(0) else itemCount * CIRCULAR_LOOP_MULTIPLIER

internal fun circularCarouselInitialPage(itemCount: Int): Int {
    if (itemCount <= 1) return 0
    val total = circularCarouselPageCount(itemCount)
    val mid = total / 2
    return mid - (mid % itemCount)
}

internal fun circularCarouselItemIndex(page: Int, itemCount: Int): Int {
    if (itemCount <= 0) return 0
    return Math.floorMod(page, itemCount)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoverCarouselSection(
    albums: ImmutableList<Album>,
    onAlbumClick: (Album) -> Unit,
    onPlayAlbum: (Album) -> Unit,
    modifier: Modifier = Modifier,
    useNavigationRail: Boolean = false,
    autoScroll: Boolean = true,
    showAlbumName: Boolean = true,
    showArtistName: Boolean = true,
    showYear: Boolean = true,
    showPlayButton: Boolean = true,
    showNextButton: Boolean = true,
    showQueueButton: Boolean = true,
    showGradient: Boolean = true,
    onPlayAlbumNext: (Album) -> Unit = {},
    onAddAlbumToQueue: (Album) -> Unit = {}
) {
    if (albums.isEmpty()) return

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isCompactHeight = configuration.screenHeightDp < 600
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val phoneTargetSize = remember(screenWidth, density) {
        val px = with(density) { screenWidth.roundToPx() }
        Size(px, px)
    }
    val preferredItemWidth = if (configuration.screenWidthDp >= 840) 360.dp else 300.dp
    val tabletTargetSize = remember(preferredItemWidth, density) {
        val wPx = with(density) { preferredItemWidth.roundToPx() }
        val hPx = with(density) { 290.dp.roundToPx() }
        Size(wPx, hPx)
    }

    val totalPages = circularCarouselPageCount(albums.size)
    val pagerState = rememberPagerState(
        initialPage = circularCarouselInitialPage(albums.size),
        pageCount = { totalPages }
    )

    // Re-center into the middle of the virtual loop if the user ever reaches near either end.
    LaunchedEffect(pagerState.settledPage, albums.size) {
        if (albums.size > 1 && totalPages > 0) {
            val page = pagerState.settledPage
            if (page < albums.size || page >= totalPages - albums.size) {
                val recentered = circularCarouselInitialPage(albums.size) +
                    circularCarouselItemIndex(page, albums.size)
                pagerState.scrollToPage(recentered)
            }
        }
    }

    DisposableEffect(lifecycleOwner, albums.size) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && albums.isNotEmpty() && !pagerState.isScrollInProgress) {
                coroutineScope.launch {
                    val safePage = pagerState.currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                    pagerState.scrollToPage(safePage)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(albums.size, autoScroll) {
        if (autoScroll && albums.size > 1) {
            while (true) {
                delay(4500)
                if (!pagerState.isScrollInProgress) {
                    val nextPage = (pagerState.currentPage + 1).coerceAtMost(totalPages - 1)
                    pagerState.animateScrollToPage(
                        page = nextPage,
                        animationSpec = tween(durationMillis = 850)
                    )
                }
            }
        }
    }

    val singlePageFling = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1)
    )

    if (useNavigationRail) {
        val horizontalPadding = if (configuration.screenWidthDp >= 840) 24.dp else 16.dp
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(preferredItemWidth),
            pageSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            beyondViewportPageCount = 2,
            flingBehavior = singlePageFling,
            key = { page -> "rail_${page}_${albums[circularCarouselItemIndex(page, albums.size)].id}" },
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp)
                .height(290.dp)
        ) { page ->
            val album = albums[circularCarouselItemIndex(page, albums.size)]
            Card(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAlbumClick(album)
                },
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SmartImage(
                        model = album.albumArtUriString,
                        contentDescription = stringResource(R.string.cd_album_art_for_title, album.title),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        targetSize = tabletTargetSize,
                        allowHardware = true
                    )

                    if (showGradient) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                        )
                                    )
                                )
                        )
                    }

                    DiscoverCardOverlayContent(
                        album = album,
                        isTablet = true,
                        showAlbumName = showAlbumName,
                        showArtistName = showArtistName,
                        showYear = showYear,
                        showPlayButton = showPlayButton,
                        showNextButton = showNextButton,
                        showQueueButton = showQueueButton,
                        onPlay = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayAlbum(album)
                        },
                        onPlayNext = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayAlbumNext(album)
                        },
                        onAddToQueue = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAddAlbumToQueue(album)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        }
    } else {
        val headerHeight = if (isCompactHeight) 280.dp else screenWidth
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(headerHeight)
        ) {
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fill,
                pageSpacing = 0.dp,
                contentPadding = PaddingValues(0.dp),
                beyondViewportPageCount = 1,
                flingBehavior = singlePageFling,
                key = { page -> "phone_${page}_${albums[circularCarouselItemIndex(page, albums.size)].id}" },
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val album = albums[circularCarouselItemIndex(page, albums.size)]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RectangleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAlbumClick(album)
                        }
                ) {
                    SmartImage(
                        model = album.albumArtUriString,
                        contentDescription = stringResource(R.string.cd_album_art_for_title, album.title),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        targetSize = phoneTargetSize,
                        allowHardware = true
                    )

                    if (showGradient) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.2f),
                                            Color.Transparent,
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        )
                    }

                    DiscoverCardOverlayContent(
                        album = album,
                        isTablet = false,
                        showAlbumName = showAlbumName,
                        showArtistName = showArtistName,
                        showYear = showYear,
                        showPlayButton = showPlayButton,
                        showNextButton = showNextButton,
                        showQueueButton = showQueueButton,
                        onPlay = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayAlbum(album)
                        },
                        onPlayNext = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayAlbumNext(album)
                        },
                        onAddToQueue = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAddAlbumToQueue(album)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoverCardOverlayContent(
    album: Album,
    isTablet: Boolean,
    showAlbumName: Boolean,
    showArtistName: Boolean,
    showYear: Boolean,
    showPlayButton: Boolean,
    showNextButton: Boolean,
    showQueueButton: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonHeight = if (isTablet) 38.dp else 40.dp
    val iconSize = if (isTablet) 16.dp else 18.dp
    val labelStyle = if (isTablet) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge
    val hPad = if (isTablet) 12.dp else 12.dp
    val vPad = if (isTablet) 6.dp else 8.dp

    Column(modifier = modifier) {
        if (showAlbumName) {
            Text(
                text = album.title,
                style = if (isTablet) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isTablet) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showArtistName) {
            Spacer(modifier = Modifier.height(if (isTablet) 2.dp else 4.dp))
            Text(
                text = album.artist,
                style = if (isTablet) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(if (isTablet) 12.dp else 16.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isTablet) 8.dp else 6.dp)
            ) {
                if (showPlayButton) {
                    Button(
                        onClick = onPlay,
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = vPad),
                        modifier = Modifier.height(buttonHeight)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.home_discover_action_play),
                            style = labelStyle,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (showNextButton) {
                    FilledTonalButton(
                        onClick = onPlayNext,
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = hPad, vertical = vPad),
                        modifier = Modifier.height(buttonHeight)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.home_discover_action_next),
                            style = labelStyle,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (showQueueButton) {
                    FilledTonalButton(
                        onClick = onAddToQueue,
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = hPad, vertical = vPad),
                        modifier = Modifier.height(buttonHeight)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.home_discover_action_queue),
                            style = labelStyle,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isTablet) 6.dp else 8.dp)
            ) {
                if (showYear && album.year > 0) {
                    Text(
                        text = album.year.toString(),
                        style = if (isTablet) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                        fontWeight = if (isTablet) FontWeight.Bold else FontWeight.Black,
                        color = if (isTablet) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                        }
                    )
                }
                if (album.songCount > 0) {
                    val countText = stringResource(R.string.home_discover_tracks_count, album.songCount)
                    if (isTablet) {
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = countText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoverCarouselPlaceholder(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Album,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.home_discover_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.home_discover_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            FilledTonalButton(
                onClick = onRefresh,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.home_empty_placeholder_refresh))
            }
        }
    }
}
