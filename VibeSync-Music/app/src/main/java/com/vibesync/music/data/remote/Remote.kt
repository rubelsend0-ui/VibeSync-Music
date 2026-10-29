package com.vibesync.music.data.remote

import com.vibesync.music.data.model.AppJson
import com.vibesync.music.data.model.HealthResponse
import com.vibesync.music.data.model.ProvidersResponse
import com.vibesync.music.data.model.SearchPage
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

interface BackendApi {
    @GET("v1/search")
    suspend fun search(
        @Query("q") q: String,
        @Query("type") type: String,
        @Query("provider") provider: String?,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("quality") quality: String,
        @Query("demo") demo: Boolean,
    ): SearchPage

    @GET("v1/recommendations")
    suspend fun recommendations(
        @Query("category") category: String,
        @Query("provider") provider: String?,
        @Query("page_size") pageSize: Int,
        @Query("quality") quality: String,
        @Query("demo") demo: Boolean,
    ): SearchPage

    @GET("v1/providers") suspend fun providers(): ProvidersResponse
    @GET("health") suspend fun health(): HealthResponse
}

sealed class AppError(message: String) : Exception(message) {
    class NotConfigured : AppError("The music backend isn't set up yet.")
    class Offline : AppError("You're offline. Check your connection and try again.")
    class Server(val code: Int) : AppError("The backend had a problem (HTTP $code).")
    class Network : AppError("Couldn't reach the backend. Check the URL and your connection.")
    class Invalid(msg: String) : AppError(msg)
}

fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is HttpException -> AppError.Server(code())
    is SerializationException -> AppError.Invalid("The backend sent an unexpected response.")
    is IOException -> AppError.Network()
    else -> AppError.Invalid(message ?: "Something went wrong.")
}

fun buildHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("User-Agent", "VibeSync-Android/1.0").build())
    }
    .build() // No logging interceptor on purpose: nothing sensitive is ever logged.

class ApiFactory(private val client: OkHttpClient) {
    private var cached: Pair<String, BackendApi>? = null

    @Synchronized
    fun get(baseUrl: String): BackendApi {
        cached?.let { if (it.first == baseUrl) return it.second }
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(AppJson.json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BackendApi::class.java)
        cached = baseUrl to api
        return api
    }
}
