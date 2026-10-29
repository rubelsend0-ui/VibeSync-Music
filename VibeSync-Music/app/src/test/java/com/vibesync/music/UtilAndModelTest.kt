package com.vibesync.music

import com.vibesync.music.data.model.AppJson
import com.vibesync.music.data.model.SearchPage
import com.vibesync.music.data.model.Track
import com.vibesync.music.util.BackendUrl
import com.vibesync.music.util.formatBytes
import com.vibesync.music.util.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilAndModelTest {
    @Test fun formatsDurations() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("3:05", formatDuration(185_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }

    @Test fun formatsBytes() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.5 MB", formatBytes(1_572_864))
    }

    @Test fun backendUrlAcceptsHttpsAndNormalizes() {
        assertEquals("https://api.example.com/", BackendUrl.normalize(" https://api.example.com ").getOrThrow())
        assertEquals("", BackendUrl.normalize("   ").getOrThrow())
    }

    @Test fun backendUrlAllowsCleartextOnlyForLocalhosts() {
        assertTrue(BackendUrl.normalize("http://127.0.0.1:8000").isSuccess)
        assertTrue(BackendUrl.normalize("http://10.0.2.2:8000").isSuccess)
        assertTrue(BackendUrl.normalize("http://example.com").isFailure)
    }

    @Test fun backendUrlRejectsGarbageAndCredentials() {
        assertTrue(BackendUrl.normalize("not a url").isFailure)
        assertTrue(BackendUrl.normalize("ftp://example.com").isFailure)
        assertTrue(BackendUrl.normalize("https://user:pw@example.com").isFailure)
    }

    @Test fun parsesBackendJsonWithUnknownKeysAndNulls() {
        val json = """
            {"query":"x","page":2,"page_size":20,"has_more":true,"future_field":1,
             "items":[{"id":"1","provider":"jamendo","title":"Song","artist":null,"duration_ms":120000,
                       "stream_url":"https://x.jamendo.com/a.mp3","downloadable":true,"download_url":"https://x.jamendo.com/d.mp3","unknown":"y"}]}
        """.trimIndent()
        val page = AppJson.json.decodeFromString(SearchPage.serializer(), json)
        assertEquals(2, page.page)
        assertTrue(page.hasMore)
        val t = page.items.single()
        assertEquals("jamendo:1", t.key)
        assertEquals("Unknown artist", t.artist)
        assertTrue(t.canPlayInApp)
        assertTrue(t.downloadable)
    }

    @Test fun trackWithoutStreamUrlIsNotPlayable() {
        assertFalse(Track(id = "1", provider = "youtube", title = "t", playable = false).canPlayInApp)
        assertFalse(Track(id = "1", provider = "x", title = "t", streamUrl = null).canPlayInApp)
    }
}
