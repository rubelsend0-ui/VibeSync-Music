package com.vibesync.music.data.repo

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.vibesync.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.Locale

interface LocalSearch {
    suspend fun search(query: String): List<Track>
}

class LocalMediaRepository(private val context: Context) : LocalSearch {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks

    companion object {
        val permission: String =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE

        fun formatOf(mime: String?, name: String?): String? {
            val ext = name?.substringAfterLast('.', "")?.lowercase(Locale.US).orEmpty()
            return when {
                ext == "m4a" || ext == "aac" || mime == "audio/mp4" || mime == "audio/aac" -> "AAC/M4A"
                ext == "mp3" || mime == "audio/mpeg" -> "MP3"
                ext == "flac" || mime == "audio/flac" -> "FLAC"
                ext == "wav" || mime == "audio/x-wav" || mime == "audio/wav" -> "WAV"
                ext == "ogg" || ext == "opus" || mime?.contains("ogg") == true -> "OGG/Opus"
                ext.isNotEmpty() -> ext.uppercase(Locale.US)
                else -> null
            }
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Reloads from MediaStore. Does nothing (and returns empty) when permission is missing. */
    suspend fun refresh() {
        if (!hasPermission()) { _tracks.value = emptyList(); return }
        _tracks.value = withContext(Dispatchers.IO) { query() }
    }

    private fun query(): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val out = ArrayList<Track>()
        try {
            context.contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val artist = c.getString(iArtist)?.takeIf { it != MediaStore.UNKNOWN_STRING } ?: "Unknown artist"
                    out.add(
                        Track(
                            id = id.toString(), provider = "local",
                            title = c.getString(iTitle) ?: c.getString(iName) ?: "Untitled",
                            artist = artist,
                            album = c.getString(iAlbum)?.takeIf { it != MediaStore.UNKNOWN_STRING },
                            durationMs = c.getLong(iDur),
                            artworkUrl = "content://media/external/audio/albumart/${c.getLong(iAlbumId)}",
                            streamUrl = ContentUris.withAppendedId(collection, id).toString(),
                            format = formatOf(c.getString(iMime), c.getString(iName)),
                            sizeBytes = c.getLong(iSize),
                            license = "Your own file",
                        ),
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return out
    }

    override suspend fun search(query: String): List<Track> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        if (_tracks.value.isEmpty() && hasPermission()) refresh()
        return _tracks.value.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || (it.album ?: "").lowercase().contains(q)
        }.take(30)
    }
}
