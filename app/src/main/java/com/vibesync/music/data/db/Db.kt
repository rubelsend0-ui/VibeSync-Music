package com.vibesync.music.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val trackKey: String, val json: String, val addedAt: Long)

@Entity(tableName = "history", indices = [Index("trackKey"), Index("playedAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackKey: String,
    val json: String,
    val playedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playlist_tracks",
    foreignKeys = [ForeignKey(
        entity = PlaylistEntity::class,
        parentColumns = ["id"],
        childColumns = ["playlistId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("playlistId")],
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val position: Int,
    val trackKey: String,
    val json: String,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(@PrimaryKey val term: String, val searchedAt: Long)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val trackKey: String,
    val json: String,
    val state: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val filePath: String,
    val error: String?,
    val format: String?,
    val createdAt: Long,
    val completedAt: Long?,
)

data class PlaylistCount(val playlistId: Long, val count: Int)

@Dao
interface LibraryDao {
    // favorites
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC") fun favorites(): Flow<List<FavoriteEntity>>
    @Query("SELECT trackKey FROM favorites") fun favoriteKeys(): Flow<List<String>>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackKey = :key)") suspend fun isFavorite(key: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFavorite(e: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE trackKey = :key") suspend fun removeFavorite(key: String)

    // history
    @Insert suspend fun addHistory(e: HistoryEntity)
    @Query("SELECT * FROM history ORDER BY playedAt DESC, id DESC LIMIT :limit") fun history(limit: Int): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM history ORDER BY playedAt DESC, id DESC LIMIT :limit") suspend fun historyOnce(limit: Int): List<HistoryEntity>
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY playedAt DESC LIMIT :keep)")
    suspend fun trimHistory(keep: Int)

    // playlists
    @Insert suspend fun insertPlaylist(p: PlaylistEntity): Long
    @Query("UPDATE playlists SET name = :name WHERE id = :id") suspend fun renamePlaylist(id: Long, name: String)
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: Long)
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC") fun playlists(): Flow<List<PlaylistEntity>>
    @Query("SELECT * FROM playlists WHERE id = :id") fun playlist(id: Long): Flow<PlaylistEntity?>
    @Query("SELECT playlistId AS playlistId, COUNT(*) AS count FROM playlist_tracks GROUP BY playlistId")
    fun playlistCounts(): Flow<List<PlaylistCount>>
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position")
    fun playlistTracks(id: Long): Flow<List<PlaylistTrackEntity>>
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position")
    suspend fun playlistTracksOnce(id: Long): List<PlaylistTrackEntity>
    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int
    @Insert suspend fun insertPlaylistTrack(e: PlaylistTrackEntity)
    @Query("DELETE FROM playlist_tracks WHERE id = :id") suspend fun removePlaylistTrack(id: Long)
    @Query("UPDATE playlist_tracks SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    // search history
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addSearch(e: SearchHistoryEntity)
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun recentSearches(limit: Int): Flow<List<SearchHistoryEntity>>
    @Query("DELETE FROM search_history") suspend fun clearSearches()
    @Query("DELETE FROM search_history WHERE term = :term") suspend fun deleteSearch(term: String)

    // downloads
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC") fun downloads(): Flow<List<DownloadEntity>>
    @Query("SELECT * FROM downloads") suspend fun downloadsOnce(): List<DownloadEntity>
    @Query("SELECT * FROM downloads WHERE trackKey = :key") suspend fun download(key: String): DownloadEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDownload(e: DownloadEntity)
    @Query("DELETE FROM downloads WHERE trackKey = :key") suspend fun deleteDownload(key: String)
    @Query("UPDATE downloads SET state = 'PAUSED' WHERE state IN ('RUNNING','QUEUED')")
    suspend fun pauseInterrupted()
    @Query("UPDATE downloads SET bytesDownloaded = :bytes, totalBytes = :total WHERE trackKey = :key")
    suspend fun progress(key: String, bytes: Long, total: Long)
}

@Database(
    entities = [
        FavoriteEntity::class, HistoryEntity::class, PlaylistEntity::class,
        PlaylistTrackEntity::class, SearchHistoryEntity::class, DownloadEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        /** Append real Migration objects here when the schema version is bumped. Never use destructive fallback. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "vibesync.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
