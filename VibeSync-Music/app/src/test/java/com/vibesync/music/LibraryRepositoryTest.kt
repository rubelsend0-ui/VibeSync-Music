package com.vibesync.music

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vibesync.music.data.db.AppDatabase
import com.vibesync.music.data.model.Track
import com.vibesync.music.data.repo.LibraryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: LibraryRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = LibraryRepository(db)
    }

    @After fun tearDown() { db.close() }

    private fun t(id: String) = Track(id = id, provider = "local", title = "Song $id", artist = "Artist")

    @Test fun togglesFavorites() = runBlocking {
        repo.toggleFavorite(t("1"))
        assertEquals(listOf("local:1"), repo.favorites.first().map { it.key })
        repo.toggleFavorite(t("1"))
        assertTrue(repo.favorites.first().isEmpty())
    }

    @Test fun playlistCrudAndReorder() = runBlocking {
        val id = repo.createPlaylist("  Road trip  ")
        repo.addToPlaylist(id, t("1")); repo.addToPlaylist(id, t("2")); repo.addToPlaylist(id, t("3"))
        assertEquals("Road trip", repo.playlistName(id).first())
        assertEquals(3, repo.playlists.first().single().trackCount)

        repo.movePlaylistItem(id, from = 2, to = 0)
        assertEquals(listOf("3", "1", "2"), repo.playlistItems(id).first().map { it.track.id })

        val entry = repo.playlistItems(id).first()[1]
        repo.removeFromPlaylist(entry.entryId)
        assertEquals(listOf("3", "2"), repo.playlistItems(id).first().map { it.track.id })

        repo.renamePlaylist(id, "Renamed")
        assertEquals("Renamed", repo.playlists.first().single().name)

        repo.deletePlaylist(id)
        assertTrue(repo.playlists.first().isEmpty())
        assertTrue(repo.playlistItems(id).first().isEmpty())
    }

    @Test fun rejectsInvalidPlaylistNames() = runBlocking {
        val r1 = runCatching { repo.createPlaylist("   ") }
        val r2 = runCatching { repo.createPlaylist("x".repeat(61)) }
        assertTrue(r1.isFailure && r2.isFailure)
    }

    @Test fun historyAndSearchHistoryCanBeCleared() = runBlocking {
        repo.recordPlay(t("1")); repo.recordPlay(t("1")); repo.recordPlay(t("2"))
        assertEquals(listOf("2", "1"), repo.history(10).first().map { it.id }) // distinct, newest first
        repo.clearHistory()
        assertTrue(repo.history(10).first().isEmpty())

        repo.recordSearch("jazz"); repo.recordSearch("a") // too short, ignored
        assertEquals(listOf("jazz"), repo.recentSearches.first())
        repo.clearSearches()
        assertTrue(repo.recentSearches.first().isEmpty())
    }
}
