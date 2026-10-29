package com.vibesync.music.download

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import com.vibesync.music.data.db.DownloadEntity
import com.vibesync.music.data.db.LibraryDao
import com.vibesync.music.data.model.AppJson
import com.vibesync.music.data.model.Track
import com.vibesync.music.data.remote.AppError
import com.vibesync.music.data.repo.Connectivity
import com.vibesync.music.data.repo.LocalMediaRepository
import com.vibesync.music.data.repo.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

enum class DownloadState { QUEUED, RUNNING, PAUSED, FAILED, COMPLETED }

data class DownloadItem(
    val track: Track,
    val state: DownloadState,
    val bytes: Long,
    val total: Long,
    val filePath: String,
    val error: String?,
    val format: String?,
    val createdAt: Long,
) {
    /** Real progress only; null when the server didn't report a size. */
    val progress: Float? get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
}

/**
 * Downloads only tracks that the provider explicitly marked downloadable (open licenses / authorized URLs).
 * Runs while the app process is alive; interrupted downloads are restored as PAUSED and can be resumed.
 */
class DownloadManager(
    private val context: Context,
    private val dao: LibraryDao,
    private val settings: SettingsRepository,
    private val network: Connectivity,
    private val scope: CoroutineScope,
    private val client: OkHttpClient,
) {
    private val jobs = ConcurrentHashMap<String, Job>()

    val directory: File by lazy {
        (context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "music")).also { it.mkdirs() }
    }

    val items: Flow<List<DownloadItem>> = dao.downloads().map { l -> l.mapNotNull { toItem(it) } }

    /** Completed downloads as playable local tracks (the offline library). */
    val offlineTracks: Flow<List<Track>> = items.map { l ->
        l.filter { it.state == DownloadState.COMPLETED }.map {
            it.track.copy(
                streamUrl = Uri.fromFile(File(it.filePath)).toString(),
                format = it.format ?: it.track.format,
                sizeBytes = it.total.takeIf { s -> s > 0 } ?: it.track.sizeBytes,
                playable = true,
            )
        }
    }

    private fun toItem(e: DownloadEntity): DownloadItem? {
        val t = runCatching { AppJson.json.decodeFromString(Track.serializer(), e.json) }.getOrNull() ?: return null
        val st = runCatching { DownloadState.valueOf(e.state) }.getOrDefault(DownloadState.FAILED)
        return DownloadItem(t, st, e.bytesDownloaded, e.totalBytes, e.filePath, e.error, e.format, e.createdAt)
    }

    suspend fun init() {
        dao.pauseInterrupted()
        reconcile()
    }

    /** Removes DB rows whose file disappeared so metadata never points at missing files. */
    suspend fun reconcile() {
        dao.downloadsOnce().filter { it.state == "COMPLETED" && !File(it.filePath).exists() }
            .forEach { dao.deleteDownload(it.trackKey) }
    }

    suspend fun storageUsedBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles()?.sumOf { it.length() } ?: 0L
    }

    suspend fun enqueue(track: Track): Result<Unit> {
        val url = track.downloadUrl
        if (!track.downloadable || url.isNullOrBlank()) {
            return Result.failure(AppError.Invalid("This source doesn't authorize downloading this track."))
        }
        if (!url.startsWith("https://")) return Result.failure(AppError.Invalid("Only secure (https) downloads are supported."))
        val existing = dao.download(track.key)
        if (existing != null) {
            return when (existing.state) {
                "COMPLETED" -> Result.failure(AppError.Invalid("Already downloaded."))
                "RUNNING", "QUEUED" -> Result.failure(AppError.Invalid("Already downloading."))
                else -> { start(track.key); Result.success(Unit) }
            }
        }
        val dup = dao.downloadsOnce().firstOrNull { e ->
            e.state == "COMPLETED" && runCatching { AppJson.json.decodeFromString(Track.serializer(), e.json) }.getOrNull()
                ?.let { it.title.equals(track.title, true) && it.artist.equals(track.artist, true) } == true
        }
        if (dup != null) return Result.failure(AppError.Invalid("A copy of this song is already downloaded."))

        val ext = guessExtension(url, track.format)
        val safe = (track.provider + "_" + track.id).replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        val file = File(directory, "$safe.$ext")
        dao.upsertDownload(
            DownloadEntity(
                trackKey = track.key, json = AppJson.json.encodeToString(Track.serializer(), track),
                state = "QUEUED", bytesDownloaded = 0, totalBytes = track.sizeBytes ?: 0, filePath = file.absolutePath,
                error = null, format = track.format, createdAt = System.currentTimeMillis(), completedAt = null,
            ),
        )
        start(track.key)
        return Result.success(Unit)
    }

    private fun guessExtension(url: String, format: String?): String {
        val fromUrl = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        if (fromUrl in setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")) return fromUrl
        return when (format?.uppercase()) { "FLAC" -> "flac"; "OGG", "OGG/OPUS" -> "ogg"; "WAV" -> "wav"; "AAC/M4A" -> "m4a"; else -> "mp3" }
    }

    fun start(key: String) {
        if (jobs[key]?.isActive == true) return
        val job = scope.launch(Dispatchers.IO) {
            try { run(key) } finally { jobs.remove(key, currentCoroutineContext().job) }
        }
        jobs[key] = job
    }

    suspend fun pause(key: String) {
        jobs[key]?.cancelAndJoin()
        dao.download(key)?.let { if (it.state == "RUNNING" || it.state == "QUEUED") dao.upsertDownload(it.copy(state = "PAUSED")) }
    }

    suspend fun resume(key: String) = start(key)
    suspend fun retry(key: String) = start(key)

    /** Cancels a running download or deletes a finished one, including its file. */
    suspend fun remove(key: String) {
        jobs[key]?.cancelAndJoin()
        dao.download(key)?.let {
            File(it.filePath).delete()
            File(it.filePath + ".part").delete()
        }
        dao.deleteDownload(key)
    }

    suspend fun clearFinishedHistory() {
        dao.downloadsOnce().filter { it.state == "FAILED" }.forEach { remove(it.trackKey) }
    }

    private suspend fun fail(key: String, message: String) {
        dao.download(key)?.let { dao.upsertDownload(it.copy(state = "FAILED", error = message)) }
    }

    private suspend fun run(key: String) {
        val e = dao.download(key) ?: return
        val track = runCatching { AppJson.json.decodeFromString(Track.serializer(), e.json) }.getOrNull()
        val url = track?.downloadUrl
        if (track == null || url == null) return fail(key, "Stored download data is corrupt.")
        val s = settings.current()
        if (!network.isOnline()) return fail(key, "No internet connection.")
        if (s.wifiOnlyDownloads && !network.isUnmeteredWifi()) {
            return fail(key, "Wi-Fi only is on and you're not on Wi-Fi. Change it in Settings or connect to Wi-Fi.")
        }
        val file = File(e.filePath)
        val part = File(e.filePath + ".part")
        var existing = if (part.exists()) part.length() else 0L
        dao.upsertDownload(e.copy(state = "RUNNING", error = null))

        val req = Request.Builder().url(url).apply { if (existing > 0) header("Range", "bytes=$existing-") }.build()
        val call = client.newCall(req)
        val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { resp ->
                if (resp.code == 416) { part.delete(); return fail(key, "The server couldn't resume. Tap retry to restart.") }
                if (!resp.isSuccessful) return fail(key, "The server answered HTTP ${resp.code}.")
                val body = resp.body ?: return fail(key, "The server sent an empty response.")
                val mime = body.contentType()?.toString().orEmpty()
                if (mime.startsWith("text/") || mime.contains("json") || mime.contains("html")) {
                    return fail(key, "The server returned a web page instead of audio.")
                }
                val append = resp.code == 206 && existing > 0
                if (!append) existing = 0
                val len = body.contentLength()
                val total = if (len > 0) existing + len else -1L
                var written = existing
                var lastReport = 0L
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(32 * 1024)
                    val input = body.byteStream()
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        written += n
                        currentCoroutineContext().job.let { if (!it.isActive) throw CancellationException("paused") }
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 300) { dao.progress(key, written, total); lastReport = now }
                    }
                }
                val size = part.length()
                if (total > 0 && size != total) {
                    part.delete()
                    return fail(key, "Integrity check failed: expected $total bytes but received $size.")
                }
                if (!looksLikeAudio(part)) {
                    part.delete()
                    return fail(key, "The downloaded file isn't valid audio.")
                }
                if (file.exists()) file.delete()
                if (!part.renameTo(file)) return fail(key, "Couldn't save the file to storage.")
                val fmt = LocalMediaRepository.formatOf(mime.ifBlank { null }, file.name) ?: e.format
                dao.upsertDownload(
                    dao.download(key)!!.copy(
                        state = "COMPLETED", bytesDownloaded = size, totalBytes = size, error = null,
                        format = fmt, completedAt = System.currentTimeMillis(),
                    ),
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (io: IOException) {
            if (!currentCoroutineContext().isActive) throw CancellationException("cancelled")
            fail(key, "Network error: ${io.message ?: "connection lost"}. Tap retry to continue.")
        } finally {
            handle.dispose()
        }
    }

    private fun looksLikeAudio(f: File): Boolean {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0L
        } catch (_: Exception) {
            false
        } finally {
            runCatching { r.release() }
        }
    }
}
