package com.drliki.MusicLi.presentation.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscoverCarouselCircularTest {

    @Test
    fun `empty and single album carousels do not loop virtually`() {
        assertEquals(0, circularCarouselPageCount(0))
        assertEquals(1, circularCarouselPageCount(1))
        assertEquals(0, circularCarouselInitialPage(0))
        assertEquals(0, circularCarouselInitialPage(1))
        assertEquals(0, circularCarouselItemIndex(42, 0))
        assertEquals(0, circularCarouselItemIndex(42, 1))
    }

    @Test
    fun `multi-album circular carousel starts aligned and wraps in both directions`() {
        val count = 7
        val totalPages = circularCarouselPageCount(count)
        val initialPage = circularCarouselInitialPage(count)

        assertTrue(totalPages > count * 100)
        assertEquals(0, circularCarouselItemIndex(initialPage, count))
        // Swiping backward from the first album wraps smoothly to the last album.
        assertEquals(count - 1, circularCarouselItemIndex(initialPage - 1, count))
        // Swiping forward past the last album wraps smoothly to the first album.
        assertEquals(0, circularCarouselItemIndex(initialPage + count, count))
        assertEquals(1, circularCarouselItemIndex(initialPage + count + 1, count))
    }
}
