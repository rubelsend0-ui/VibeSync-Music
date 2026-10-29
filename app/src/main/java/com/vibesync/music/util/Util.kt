package com.vibesync.music.util

import java.net.URI
import java.util.Locale

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

object BackendUrl {
    private val localHosts = setOf("localhost", "127.0.0.1", "10.0.2.2")

    fun isLoopbackOrLocal(url: String): Boolean =
        runCatching { URI(url).host?.lowercase() in localHosts }.getOrDefault(false)

    /** Returns a normalized URL ending with '/', "" to clear, or a failure with a user-readable reason. */
    fun normalize(raw: String): Result<String> {
        val text = raw.trim()
        if (text.isEmpty()) return Result.success("")
        val uri = runCatching { URI(text) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("That doesn't look like a valid URL."))
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme != "http" && scheme != "https" || host.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Enter a full URL such as https://my-backend.example.com"))
        }
        if (uri.userInfo != null) {
            return Result.failure(IllegalArgumentException("Credentials in the URL are not allowed."))
        }
        if (scheme == "http" && host !in localHosts) {
            return Result.failure(
                IllegalArgumentException("Use https://. Plain http is only allowed for localhost, 127.0.0.1 and 10.0.2.2."),
            )
        }
        return Result.success(text.trimEnd('/') + "/")
    }
}
