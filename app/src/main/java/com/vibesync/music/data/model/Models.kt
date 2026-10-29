package com.vibesync.music.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
object AppJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }
}

/** Normalized track used across the app, the backend API and persistence. */
@Serializable
data class Track(
    val id: String,
    val provider: String,
    val title: String,
    val artist: String = "Unknown artist",
    val album: String? = null,
    @SerialName("duration_ms") val durationMs: Long = 0,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("external_url") val externalUrl: String? = null,
    val license: String? = null,
    val format: String? = null,
    @SerialName("bitrate_kbps") val bitrateKbps: Int? = null,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    val playable: Boolean = true,
    val downloadable: Boolean = false,
    val preview: Boolean = false,
    @SerialName("is_demo") val isDemo: Boolean = false,
) {
    val key: String get() = "$provider:$id"
    val canPlayInApp: Boolean get() = playable && !streamUrl.isNullOrBlank()
}

@Serializable
data class SearchPage(
    val query: String = "",
    val items: List<Track> = emptyList(),
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 20,
    @SerialName("has_more") val hasMore: Boolean = false,
    val notices: List<String> = emptyList(),
    val demo: Boolean = false,
)

@Serializable
data class ProviderInfo(
    val name: String,
    @SerialName("display_name") val displayName: String,
    val configured: Boolean,
    val capabilities: List<String> = emptyList(),
    @SerialName("setup_hint") val setupHint: String? = null,
    val notes: String? = null,
)

@Serializable
data class ProvidersResponse(val providers: List<ProviderInfo> = emptyList())

@Serializable
data class HealthResponse(val status: String = "", val version: String = "")

enum class SearchType(val api: String, val label: String) {
    TRACK("track", "Songs"), ARTIST("artist", "Artists"), ALBUM("album", "Albums")
}

enum class ThemePref { DARK, LIGHT, SYSTEM }

data class AppSettings(
    val theme: ThemePref = ThemePref.DARK,
    val accentIndex: Int = 0,
    val backendUrl: String = "",
    val wifiOnlyDownloads: Boolean = true,
    val demoMode: Boolean = false,
    val audioQuality: String = "auto",
)

data class PlaylistSummary(val id: Long, val name: String, val trackCount: Int)
data class PlaylistItem(val entryId: Long, val track: Track)
