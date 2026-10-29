package com.vibesync.music.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.vibesync.music.data.model.AppJson
import com.vibesync.music.data.model.Track
import com.vibesync.music.data.repo.LibraryRepository
import com.vibesync.music.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

data class PlayerState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val error: String? = null,
    val connected: Boolean = false,
)

@Serializable
private data class SavedPlayback(val tracks: List<Track>, val index: Int, val positionMs: Long)

/** The single shared playback controller for the whole app (UI, mini-player, queue). */
class PlayerController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val cache = HashMap<String, Track>()
    private var lastLoggedKey: String? = null
    private var lastSavedKey: String? = null
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = describe(error)) }
            controller?.let { c ->
                if (c.hasNextMediaItem()) { c.seekToNextMediaItem(); c.prepare() }
            }
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            try {
                onConnected(f.get())
            } catch (e: Exception) {
                future = null
                _state.update { it.copy(error = "Playback service is unavailable.") }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun onConnected(c: MediaController) {
        controller = c
        c.addListener(listener)
        _state.update { it.copy(connected = true) }
        refresh()
        scope.launch { restore(c) }
        ticker = scope.launch {
            var sinceSave = 0
            while (true) {
                delay(500)
                val ctl = controller ?: continue
                if (ctl.isPlaying) {
                    _state.update { it.copy(positionMs = ctl.currentPosition.coerceAtLeast(0)) }
                    sinceSave++
                    if (sinceSave >= 20) { sinceSave = 0; saveState() }
                }
            }
        }
    }

    private suspend fun restore(c: MediaController) {
        if (c.mediaItemCount > 0) return
        val json = settings.playbackState.first() ?: return
        val saved = runCatching { AppJson.json.decodeFromString(SavedPlayback.serializer(), json) }.getOrNull() ?: return
        val playable = saved.tracks.filter { it.canPlayInApp }
        if (playable.isEmpty()) return
        // Restores the queue and position paused; nothing is loaded from the network until the user presses play.
        c.setMediaItems(playable.map { it.toMediaItem() }, saved.index.coerceIn(0, playable.lastIndex), saved.positionMs)
    }

    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val queue = (0 until c.mediaItemCount).map { toTrack(c.getMediaItemAt(it)) }
        val track = item?.let { toTrack(it) }
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: track?.durationMs ?: 0L
        _state.update {
            it.copy(
                track = track, isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                positionMs = c.currentPosition.coerceAtLeast(0), durationMs = duration,
                shuffle = c.shuffleModeEnabled, repeat = c.repeatMode, queue = queue,
                index = c.currentMediaItemIndex,
                error = if (c.isPlaying) null else it.error,
            )
        }
        if (track != null && c.isPlaying && track.key != lastLoggedKey) {
            lastLoggedKey = track.key
            scope.launch { library.recordPlay(track) }
        }
        if (track != null && (!c.isPlaying || track.key != lastSavedKey)) {
            lastSavedKey = track.key
            saveState()
        }
    }

    private fun saveState() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val tracks = (0 until c.mediaItemCount).map { toTrack(c.getMediaItemAt(it)) }.take(200)
        val idx = c.currentMediaItemIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        val json = AppJson.json.encodeToString(SavedPlayback.serializer(), SavedPlayback(tracks, idx, c.currentPosition.coerceAtLeast(0)))
        scope.launch { settings.savePlaybackState(json) }
    }

    // ---- mapping
    private fun Track.toMediaItem(): MediaItem {
        cache[key] = this
        val extras = Bundle().apply { putString("track_json", AppJson.json.encodeToString(Track.serializer(), this@toMediaItem)) }
        val meta = MediaMetadata.Builder()
            .setTitle(title).setArtist(artist).setAlbumTitle(album)
            .setArtworkUri(artworkUrl?.let { Uri.parse(it) })
            .setIsPlayable(true).setExtras(extras).build()
        return MediaItem.Builder().setMediaId(key).setUri(streamUrl).setMediaMetadata(meta).build()
    }

    private fun toTrack(item: MediaItem): Track {
        cache[item.mediaId]?.let { return it }
        if (cache.size > 1000) cache.clear()
        val parsed = item.mediaMetadata.extras?.getString("track_json")
            ?.let { runCatching { AppJson.json.decodeFromString(Track.serializer(), it) }.getOrNull() }
        val t = parsed ?: Track(
            id = item.mediaId, provider = "unknown",
            title = item.mediaMetadata.title?.toString() ?: "Unknown",
            artist = item.mediaMetadata.artist?.toString() ?: "Unknown artist",
        )
        cache[item.mediaId] = t
        return t
    }

    private fun describe(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network problem while streaming. Check your connection."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The music source refused this request."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "This file is no longer available."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "This audio format isn't supported on your device."
        else -> "Playback error (${e.errorCodeName})."
    }

    // ---- commands
    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: run { _messages.tryEmit("Player is still starting. Try again in a moment."); return }
        val tapped = tracks.getOrNull(startIndex)
        if (tapped != null && !tapped.canPlayInApp) { _messages.tryEmit(unplayableReason(tapped)); return }
        val playable = tracks.filter { it.canPlayInApp }
        if (playable.isEmpty()) { _messages.tryEmit("None of these tracks can be played in the app."); return }
        var start = tapped?.let { t -> playable.indexOfFirst { it.key == t.key } }?.coerceAtLeast(0) ?: 0
        if (shuffle) start = playable.indices.random()
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(playable.map { it.toMediaItem() }, start, 0L)
        c.prepare()
        c.play()
    }

    fun playNext(t: Track) {
        val c = controller ?: return
        if (!t.canPlayInApp) { _messages.tryEmit(unplayableReason(t)); return }
        if (c.mediaItemCount == 0) playTracks(listOf(t)) else {
            c.addMediaItem(c.currentMediaItemIndex + 1, t.toMediaItem())
            _messages.tryEmit("Will play next")
        }
    }

    fun addToQueue(t: Track) {
        val c = controller ?: return
        if (!t.canPlayInApp) { _messages.tryEmit(unplayableReason(t)); return }
        if (c.mediaItemCount == 0) playTracks(listOf(t)) else {
            c.addMediaItem(t.toMediaItem())
            _messages.tryEmit("Added to queue")
        }
    }

    private fun unplayableReason(t: Track) = when {
        t.isDemo -> "Demo tracks have no audio."
        t.externalUrl != null -> "This source doesn't allow in-app playback. Use \"Open in browser\"."
        else -> "This track has no playable audio from its source."
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }
    fun pause() { controller?.pause() }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun skipTo(index: Int) { controller?.let { it.seekToDefaultPosition(index); it.play() } }
    fun removeFromQueue(index: Int) { controller?.removeMediaItem(index) }
    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }
    fun clearError() { _state.update { it.copy(error = null) } }
}
