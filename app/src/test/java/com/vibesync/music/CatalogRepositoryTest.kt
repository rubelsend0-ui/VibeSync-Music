package com.vibesync.music

import com.vibesync.music.data.model.AppSettings
import com.vibesync.music.data.model.SearchType
import com.vibesync.music.data.remote.ApiFactory
import com.vibesync.music.data.remote.AppError
import com.vibesync.music.data.repo.CatalogRepository
import com.vibesync.music.data.repo.Connectivity
import com.vibesync.music.data.repo.SettingsSource
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CatalogRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { runCatching { server.shutdown() } }

    private fun repo(url: String, demo: Boolean = false, online: Boolean = true) = CatalogRepository(
        settings = object : SettingsSource {
            override suspend fun current() = AppSettings(backendUrl = url, demoMode = demo)
        },
        apiFactory = ApiFactory(OkHttpClient()),
        network = object : Connectivity {
            override fun isOnline() = online
            override fun isUnmeteredWifi() = true
        },
    )

    private val body = """{"query":"hi","items":[{"id":"7","provider":"jamendo","title":"Hello","artist":"A",
        "stream_url":"https://a.jamendo.com/x.mp3"}],"page":1,"has_more":false,"notices":[]}"""

    @Test fun parsesSearchResults() = runTest {
        server.enqueue(MockResponse().setBody(body).addHeader("Content-Type", "application/json"))
        val r = repo(server.url("/").toString()).search("hi", SearchType.TRACK, 1)
        assertTrue(r.isSuccess)
        assertEquals("Hello", r.getOrThrow().items.single().title)
        val req = server.takeRequest()
        assertEquals("/v1/search?q=hi&type=track&page=1&page_size=20&quality=auto&demo=false", req.path)
    }

    @Test fun failsWithNotConfiguredWhenNoBackend() = runTest {
        val r = repo("").search("hi", SearchType.TRACK, 1)
        assertTrue(r.exceptionOrNull() is AppError.NotConfigured)
    }

    @Test fun demoModeReturnsLabelledDemoDataWithoutBackend() = runTest {
        val page = repo("", demo = true).search("", SearchType.TRACK, 1).getOrThrow()
        assertTrue(page.demo)
        assertTrue(page.items.all { it.isDemo && !it.canPlayInApp })
    }

    @Test fun mapsServerErrorAfterRetries() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) }
        val r = repo(server.url("/").toString()).recommendations("trending")
        val err = r.exceptionOrNull()
        assertTrue(err is AppError.Server)
        assertEquals(500, (err as AppError.Server).code)
        assertEquals(3, server.requestCount)
    }

    @Test fun doesNotCrashWhenBackendIsDown() = runTest {
        val url = server.url("/").toString()
        server.shutdown()
        val r = repo(url).search("hi", SearchType.TRACK, 1)
        assertTrue(r.exceptionOrNull() is AppError.Network)
    }

    @Test fun reportsOfflineForRemoteBackend() = runTest {
        val r = repo("https://music.example.com/", online = false).search("hi", SearchType.TRACK, 1)
        assertTrue(r.exceptionOrNull() is AppError.Offline)
    }

    @Test fun invalidJsonBecomesFriendlyError() = runTest {
        server.enqueue(MockResponse().setBody("<html>nope</html>").addHeader("Content-Type", "application/json"))
        val r = repo(server.url("/").toString()).search("hi", SearchType.TRACK, 1)
        assertTrue(r.exceptionOrNull() is AppError.Invalid)
    }
}
