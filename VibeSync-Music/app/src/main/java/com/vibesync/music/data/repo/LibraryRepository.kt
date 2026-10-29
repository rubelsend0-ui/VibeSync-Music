package com.vibesync.music.data.repo

import androidx.room.withTransaction
import com.vibesync.music.data.db.AppDatabase
import com.vibesync.music.data.db.FavoriteEntity
import com.vibesync.music.data.db.HistoryEntity
import com.vibesync.music.data.db.PlaylistEntity
import com.vibesync.music.data.db.PlaylistTrackEntity
import com.vibesync.music.data.db.SearchHistoryEntity
import com.vibesync.music.data.model.AppJson
import com.vibesync.music.data.model.PlaylistItem
import com.vibesync.music.data.model.PlaylistSummary
import com.vibesync.music.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

interface SearchHistory {
    val recentSearches: Flow<List<String>>
    suspend fun recordSearch(term: String)
    suspend fun clearSearches()
    suspend fun deleteSearch(term: String)
}

class LibraryRepository(private val db: AppDatabase) : SearchHistory {
    private val dao = db.dao()

    private fun enc(t: Track) = AppJson.json.encodeToString(Track.serializer(), t)
    private fun dec(s: String): Track? =
        runCatching { AppJson.json.decodeFromString(Track.serializer(), s) }.getOrNull()

    // ---- favorites
    val favorites: Flow<List<Track>> = dao.favorites().map { l -> l.mapNotNull { dec(it.json) } }
    val favoriteKeys: Flow<Set<String>> = dao.favoriteKeys().map { it.toSet() }

    suspend fun toggleFavorite(t: Track) {
        if (dao.isFavorite(t.key)) dao.removeFavorite(t.key)
        else dao.addFavorite(FavoriteEntity(t.key, enc(t), System.currentTimeMillis()))
    }

    // ---- history
    fun history(limit: Int = 100): Flow<List<Track>> = dao.history(limit).map { l ->
        l.mapNotNull { dec(it.json) }.distinctBy { it.key }
    }

    suspend fun historyOnce(limit: Int = 200): List<Track> =
        dao.historyOnce(limit).mapNotNull { dec(it.json) }

    suspend fun recordPlay(t: Track) {
        dao.addHistory(HistoryEntity(trackKey = t.key, json = enc(t), playedAt = System.currentTimeMillis()))
        dao.trimHistory(500)
    }

    suspend fun clearHistory() = dao.clearHistory()

    // ---- search history
    override val recentSearches: Flow<List<String>> = dao.recentSearches(15).map { l -> l.map { it.term } }
    override suspend fun recordSearch(term: String) {
        val t = term.trim()
        if (t.length >= 2) dao.addSearch(SearchHistoryEntity(t, System.currentTimeMillis()))
    }
    override suspend fun clearSearches() = dao.clearSearches()
    override suspend fun deleteSearch(term: String) = dao.deleteSearch(term)

    // ---- playlists
    val playlists: Flow<List<PlaylistSummary>> =
        combine(dao.playlists(), dao.playlistCounts()) { lists, counts ->
            val map = counts.associate { it.playlistId to it.count }
            lists.map { PlaylistSummary(it.id, it.name, map[it.id] ?: 0) }
        }

    fun playlistName(id: Long): Flow<String?> = dao.playlist(id).map { it?.name }

    fun playlistItems(id: Long): Flow<List<PlaylistItem>> = dao.playlistTracks(id).map { l ->
        l.mapNotNull { e -> dec(e.json)?.let { PlaylistItem(e.id, it) } }
    }

    private fun validName(name: String): String {
        val n = name.trim()
        require(n.isNotEmpty()) { "Playlist name can't be empty." }
        require(n.length <= 60) { "Playlist name is too long (max 60 characters)." }
        return n
    }

    suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = validName(name), createdAt = System.currentTimeMillis()))

    suspend fun renamePlaylist(id: Long, name: String) = dao.renamePlaylist(id, validName(name))
    suspend fun deletePlaylist(id: Long) = dao.deletePlaylist(id)

    suspend fun addToPlaylist(playlistId: Long, t: Track) {
        val pos = dao.maxPosition(playlistId) + 1
        dao.insertPlaylistTrack(PlaylistTrackEntity(playlistId = playlistId, position = pos, trackKey = t.key, json = enc(t)))
    }

    suspend fun removeFromPlaylist(entryId: Long) = dao.removePlaylistTrack(entryId)

    suspend fun movePlaylistItem(playlistId: Long, from: Int, to: Int) = db.withTransaction {
        val list = dao.playlistTracksOnce(playlistId).toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return@withTransaction
        list.add(to, list.removeAt(from))
        list.forEachIndexed { i, e -> dao.setPosition(e.id, i) }
    }
}
