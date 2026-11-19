package com.lostf1sh.pixelplayeross.presentation.components

import android.content.res.Configuration
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Album
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    showGradient: Boolean = true
) {
    if (albums.isEmpty()) return

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isCompactHeight = configuration.screenHeightDp < 600
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    if (useNavigationRail) {
        // Wide screen / Tablet layout: multi-browse card carousel
        val carouselState = rememberCarouselState { albums.size }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (albums.isNotEmpty() && !carouselState.isScrollInProgress) {
                        coroutineScope.launch {
                            carouselState.scrollToItem(carouselState.currentItem.coerceIn(0, albums.lastIndex))
                        }
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
                    if (!carouselState.isScrollInProgress) {
                        val nextItem = (carouselState.currentItem + 1) % albums.size
                        carouselState.animateScrollToItem(
                            nextItem,
                            animationSpec = tween(durationMillis = 850)
                        )
                    }
                }
            }
        }

        val preferredItemWidth = if (configuration.screenWidthDp >= 840) 360.dp else 300.dp
        val horizontalPadding = if (configuration.screenWidthDp >= 840) 24.dp else 16.dp

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp)
        ) {
            HorizontalMultiBrowseCarousel(
                state = carouselState,
                preferredItemWidth = preferredItemWidth,
                itemSpacing = 16.dp,
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                minSmallItemWidth = 48.dp,
                maxSmallItemWidth = 140.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
            ) { page ->
                val album = albums.getOrNull(page) ?: return@HorizontalMultiBrowseCarousel
                Card(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAlbumClick(album)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .maskClip(MaterialTheme.shapes.extraLarge),
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
                            contentScale = ContentScale.Crop
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

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            if (showAlbumName) {
                                Text(
                                    text = album.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (showArtistName) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = album.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (showPlayButton) {
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onPlayAlbum(album)
                                        },
                                        shape = RoundedCornerShape(percent = 50),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.home_discover_action_play),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (showYear && album.year > 0) {
                                        Text(
                                            text = album.year.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    if (album.songCount > 0) {
                                        Text(
                                            text = stringResource(R.string.home_discover_tracks_count, album.songCount),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Phone / Compact layout: uncontained hero carousel
        val carouselState = rememberCarouselState { albums.size }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (albums.isNotEmpty() && !carouselState.isScrollInProgress) {
                        coroutineScope.launch {
                            carouselState.scrollToItem(carouselState.currentItem.coerceIn(0, albums.lastIndex))
                        }
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(albums.size, autoScroll) {
            if (autoScroll && albums.size > 1) {
                if (!carouselState.isScrollInProgress) {
                    carouselState.scrollToItem(carouselState.currentItem.coerceIn(0, albums.lastIndex))
                }
                while (true) {
                    delay(4500)
                    if (!carouselState.isScrollInProgress) {
                        val currentItem = carouselState.currentItem
                        val nextItem = (currentItem + 1) % albums.size
                        carouselState.animateScrollToItem(
                            nextItem,
                            animationSpec = tween(durationMillis = 850)
                        )
                    }
                }
            }
        }

        val headerHeight = if (isCompactHeight) 280.dp else screenWidth

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(headerHeight)
        ) {
            HorizontalUncontainedCarousel(
                state = carouselState,
                itemWidth = screenWidth,
                itemSpacing = 0.dp,
                contentPadding = PaddingValues(0.dp),
                flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(state = carouselState),
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val album = albums.getOrNull(page) ?: return@HorizontalUncontainedCarousel

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
                        contentScale = ContentScale.Crop
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

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        if (showAlbumName) {
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (showArtistName) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = album.artist,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showPlayButton) {
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onPlayAlbum(album)
                                    },
                                    shape = RoundedCornerShape(percent = 50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.home_discover_action_play),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (showYear && album.year > 0) {
                                    Text(
                                        text = album.year.toString(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                                    )
                                }
                                if (album.songCount > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.home_discover_tracks_count, album.songCount),
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
