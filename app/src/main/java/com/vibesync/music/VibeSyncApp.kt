package com.vibesync.music

import android.app.Application
import android.content.Context
import coil.imageLoader
import com.vibesync.music.data.db.AppDatabase
import com.vibesync.music.data.model.Track
import com.vibesync.music.data.remote.ApiFactory
import com.vibesync.music.data.remote.buildHttpClient
import com.vibesync.music.data.repo.CatalogRepository
import com.vibesync.music.data.repo.LibraryRepository
import com.vibesync.music.data.repo.LocalMediaRepository
import com.vibesync.music.data.repo.NetworkMonitor
import com.vibesync.music.data.repo.SettingsRepository
import com.vibesync.music.data.repo.vibeDataStore
import com.vibesync.music.download.DownloadManager
import com.vibesync.music.playback.EqualizerManager
import com.vibesync.music.playback.PlayerController
import com.vibesync.music.playback.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class VibeSyncApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}

/** Manual dependency container (kept simple on purpose: no annotation processing beyond Room). */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database = AppDatabase.build(context)
    val settings = SettingsRepository(context.vibeDataStore)
    val network = NetworkMonitor(context)
    private val http = buildHttpClient()
    val catalog = CatalogRepository(settings, ApiFactory(http), network)
    val library = LibraryRepository(database)
    val localMedia = LocalMediaRepository(context)
    val downloads = DownloadManager(
        context, database.dao(), settings, network, ioScope,
        http.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build(),
    )
    val player = PlayerController(context, appScope, library, settings)
    val equalizer = EqualizerManager(appScope, settings)
    val sleepTimer = SleepTimer(appScope) { player.pause() }
    val actions = TrackActions(this)

    fun start() {
        player.connect()
        equalizer.start()
        ioScope.launch { downloads.init() }
    }

    suspend fun clearCaches() = withContext(Dispatchers.IO) {
        context.imageLoader.diskCache?.clear()
        context.imageLoader.memoryCache?.clear()
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}

/** Cross-screen track actions; results are surfaced through [messages] (snackbar). */
class TrackActions(private val c: AppContainer) {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: Flow<String> get() = merge(_messages, c.player.messages)

    fun toggleFavorite(t: Track) { c.appScope.launch { c.library.toggleFavorite(t) } }

    fun download(t: Track) {
        c.appScope.launch {
            c.downloads.enqueue(t).fold(
                onSuccess = { _messages.tryEmit("Download started") },
                onFailure = { _messages.tryEmit(it.message ?: "Couldn't start the download") },
            )
        }
    }

    fun addToPlaylist(playlistId: Long, t: Track) {
        c.appScope.launch {
            c.library.addToPlaylist(playlistId, t)
            _messages.tryEmit("Added to playlist")
        }
    }

    fun createPlaylistAndAdd(name: String, t: Track?) {
        c.appScope.launch {
            runCatching {
                val id = c.library.createPlaylist(name)
                if (t != null) c.library.addToPlaylist(id, t)
            }.fold(
                onSuccess = { _messages.tryEmit(if (t != null) "Playlist created and track added" else "Playlist created") },
                onFailure = { _messages.tryEmit(it.message ?: "Couldn't create playlist") },
            )
        }
    }

    fun say(msg: String) { _messages.tryEmit(msg) }
}
