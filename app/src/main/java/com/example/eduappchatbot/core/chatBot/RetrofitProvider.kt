package com.example.eduappchatbot.core.chatBot

import com.example.eduappchatbot.BuildConfig
import com.example.eduappchatbot.utils.DebugLogger
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.google.gson.GsonBuilder

object RetrofitProvider {
    fun buildRetrofit(apiBaseUrl: String): Retrofit {
        val buildConfigUrl = BuildConfig.API_BASE_URL.trim()
        val base = buildConfigUrl.ifEmpty { apiBaseUrl.trim() }
        val normalized = base.trimEnd('/').ifEmpty {
            DebugLogger.errorLog("RetrofitProvider", "API base URL is empty. Set BuildConfig.API_BASE_URL or provide apiBaseUrl.")
            throw IllegalArgumentException("API base URL required")
        } + "/"

        // Set up logging interceptor logs every request and response
        val logging = HttpLoggingInterceptor { msg -> DebugLogger.debugLog("OkHttp", msg) }
        logging.level = HttpLoggingInterceptor.Level.BASIC

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val gson = GsonBuilder()
            .serializeNulls()
            .create()

        DebugLogger.debugLog("RetrofitProvider", "Retrofit base url: $normalized")

        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
}
