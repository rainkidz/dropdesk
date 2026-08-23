package com.snapsave.app

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface ApiService {

    @GET("api/healthz")
    suspend fun healthCheck(): Map<String, String>

    @POST("api/downloads/inspect")
    suspend fun inspect(@Body request: InspectRequest): InspectResponse

    @POST("api/downloads")
    suspend fun startDownload(@Body request: DownloadRequest): DownloadResponse

    @GET("api/downloads/{jobId}")
    suspend fun getDownloadStatus(@Path("jobId") jobId: String): DownloadStatus

    @DELETE("api/downloads/{jobId}")
    suspend fun deleteDownload(@Path("jobId") jobId: String)

    @POST("api/instagram/cookies")
    suspend fun setInstagramCookies(@Body body: Map<String, String>): Map<String, Any>

    @GET("api/instagram/cookies/status")
    suspend fun getInstagramCookiesStatus(): Map<String, Any>

    @DELETE("api/instagram/cookies")
    suspend fun deleteInstagramCookies(): Map<String, Any>
}

object ApiClient {

    private var baseUrl: String = ""
    private var api: ApiService? = null

    fun configure(serverUrl: String) {
        val sanitized = serverUrl.trimEnd('/')
        if (sanitized == baseUrl && api != null) return
        baseUrl = sanitized

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun getApi(): ApiService {
        if (api == null) {
            configure("http://10.0.2.2:5000")
        }
        return api!!
    }

    fun getBaseUrl(): String = baseUrl
}
