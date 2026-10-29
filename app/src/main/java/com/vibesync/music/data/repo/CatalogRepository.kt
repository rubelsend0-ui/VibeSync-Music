package com.vibesync.music.data.repo

import com.vibesync.music.data.model.AppSettings
import com.vibesync.music.data.model.ProviderInfo
import com.vibesync.music.data.model.SearchPage
import com.vibesync.music.data.model.SearchType
import com.vibesync.music.data.remote.ApiFactory
import com.vibesync.music.data.remote.AppError
import com.vibesync.music.data.remote.BackendApi
import com.vibesync.music.data.remote.toAppError
import com.vibesync.music.util.BackendUrl
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

interface SearchBackend {
    suspend fun search(query: String, type: SearchType, page: Int): Result<SearchPage>
}

class CatalogRepository(
    private val settings: SettingsSource,
    private val apiFactory: ApiFactory,
    private val network: Connectivity,
) : SearchBackend {

    private suspend fun <T> call(demo: () -> T, block: suspend (BackendApi, AppSettings) -> T): Result<T> {
        val s = settings.current()
        if (s.backendUrl.isBlank()) {
            return if (s.demoMode) Result.success(demo()) else Result.failure(AppError.NotConfigured())
        }
        if (!BackendUrl.isLoopbackOrLocal(s.backendUrl) && !network.isOnline()) {
            return Result.failure(AppError.Offline())
        }
        return try {
            val api = apiFactory.get(s.backendUrl)
            Result.success(withRetry { block(api, s) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e.toAppError())
        }
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val transient = e is IOException || (e is HttpException && e.code() >= 500)
                if (!transient || attempt >= 2) throw e
                attempt++
                delay(400L * attempt)
            }
        }
    }

    override suspend fun search(query: String, type: SearchType, page: Int): Result<SearchPage> =
        call(demo = { com.vibesync.music.data.repo.DemoCatalog.page(query) }) { api, s ->
            api.search(query, type.api, null, page, 20, s.audioQuality, s.demoMode)
        }

    suspend fun recommendations(category: String): Result<SearchPage> =
        call(demo = { DemoCatalog.page("", category) }) { api, s ->
            api.recommendations(category, null, 20, s.audioQuality, s.demoMode)
        }

    suspend fun providers(): Result<List<ProviderInfo>> =
        call(demo = { emptyList() }) { api, _ -> api.providers().providers }

    suspend fun health(): Result<String> =
        call(demo = { "demo" }) { api, _ -> api.health().status }
}
