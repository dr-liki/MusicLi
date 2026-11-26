package com.drliki.MusicLi.data.preferences

/**
 * Shortcuts the user can show or hide in the app sidebar. Settings is always shown.
 * A saved custom set keeps its stored value, so a key added here only appears for users who
 * never edited their shortcuts; the others can turn it on in Edit shortcuts.
 */
object SidebarShortcutKeys {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val STATS = "stats"
    const val EQUALIZER = "equalizer"
    const val AUDIO_BOOKMARKS = "audio_bookmarks"

    val ALL: List<String> = listOf(HOME, EXPLORE, STATS, EQUALIZER, AUDIO_BOOKMARKS)
    val DEFAULT: Set<String> = ALL.toSet()
}
