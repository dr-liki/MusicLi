package com.drliki.MusicLi.data.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoMediaBrowseTreeTest {

    @Test
    fun `paginate returns accurate page slices and handles bounds safely`() {
        val items = (1..10).toList()

        assertEquals(listOf(1, 2, 3), AutoMediaBrowseTree.paginate(items, page = 0, pageSize = 3))
        assertEquals(listOf(4, 5, 6), AutoMediaBrowseTree.paginate(items, page = 1, pageSize = 3))
        assertEquals(listOf(10), AutoMediaBrowseTree.paginate(items, page = 3, pageSize = 3))
        assertEquals(emptyList<Int>(), AutoMediaBrowseTree.paginate(items, page = 4, pageSize = 3))
        assertEquals(items, AutoMediaBrowseTree.paginate(items, page = 0, pageSize = 0))
    }

    @Test
    fun `isContainerId identifies browse folders and typed containers only`() {
        assertTrue(AutoMediaBrowseTree.isContainerId(AutoMediaBrowseTree.ROOT_ID))
        assertTrue(AutoMediaBrowseTree.isContainerId(AutoMediaBrowseTree.RECENTS_ID))
        assertTrue(AutoMediaBrowseTree.isContainerId(AutoMediaBrowseTree.LIKED_ID))
        assertTrue(AutoMediaBrowseTree.isContainerId(AutoMediaBrowseTree.ALBUMS_ID))
        assertTrue(AutoMediaBrowseTree.isContainerId(AutoMediaBrowseTree.ARTISTS_ID))
        assertTrue(AutoMediaBrowseTree.isContainerId(AutoMediaBrowseTree.PLAYLISTS_ID))
        assertTrue(AutoMediaBrowseTree.isContainerId(AutoMediaBrowseTree.ALL_SONGS_ID))
        assertTrue(AutoMediaBrowseTree.isContainerId("${AutoMediaBrowseTree.ALBUM_PREFIX}42"))
        assertTrue(AutoMediaBrowseTree.isContainerId("${AutoMediaBrowseTree.ARTIST_PREFIX}99"))
        assertTrue(AutoMediaBrowseTree.isContainerId("${AutoMediaBrowseTree.PLAYLIST_PREFIX}abc"))
        assertFalse(AutoMediaBrowseTree.isContainerId("123456"))
    }
}
