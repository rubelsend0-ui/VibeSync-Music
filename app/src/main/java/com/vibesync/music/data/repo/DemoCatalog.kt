package com.vibesync.music.data.repo

import com.vibesync.music.data.model.SearchPage
import com.vibesync.music.data.model.Track

/**
 * Clearly labelled demo data for development and screenshots only.
 * Demo tracks carry no audio URL and are never playable.
 */
object DemoCatalog {
    private val names = listOf(
        "Neon Avenue" to "Aurora Static", "Midnight Circuit" to "Violet Harbor",
        "Glass Horizon" to "Aurora Static", "Slow Orbit" to "Paper Satellites",
        "Pulse Garden" to "Violet Harbor", "Low Tide Radio" to "Paper Satellites",
        "Afterglow Drive" to "Neon Cartel", "Quiet Machines" to "Neon Cartel",
        "Blue Hour" to "Aurora Static", "Static Bloom" to "Violet Harbor",
        "Lantern Run" to "Paper Satellites", "Echo Season" to "Neon Cartel",
    )

    private val all: List<Track> = names.mapIndexed { i, (title, artist) ->
        Track(
            id = "demo-$i", provider = "demo", title = title, artist = artist,
            album = "Demo Album ${(i / 3) + 1}", durationMs = (150_000L + i * 7_000L),
            playable = false, isDemo = true,
        )
    }

    fun page(query: String, category: String? = null): SearchPage {
        val q = query.trim().lowercase()
        val items = if (q.isEmpty()) all.shuffled(java.util.Random((category ?: "").hashCode().toLong()))
        else all.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || (it.album ?: "").lowercase().contains(q) }
        return SearchPage(
            query = category ?: query, items = items, hasMore = false, demo = true,
            notices = listOf("DEMO DATA: these are placeholder tracks for screenshots. They cannot be played."),
        )
    }
}
