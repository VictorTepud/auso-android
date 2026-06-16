package com.auso.social.network

import com.auso.social.network.model.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton API client for AUSO backend communication
 */
object AusoApiClient {

    // Default: real device on same network as server
    // Change this to your computer's local IP (run `ip addr` or `hostname -I` on Linux)
    // For emulator use: http://10.0.2.2:8080/
    // Current server IP: 192.168.0.113 (PC local IP)
    var baseUrl: String = "http://192.168.0.113:8080/"
        set(value) {
            field = if (value.endsWith("/")) value else "$value/"
            rebuildRetrofit()
        }

    private var authToken: String? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                authToken?.let {
                    requestBuilder.header("Authorization", "Bearer $it")
                }
                chain.proceed(requestBuilder.build())
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private var _retrofit: Retrofit = buildRetrofit()

    val api: AusoApi by lazy { _retrofit.create(AusoApi::class.java) }

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun rebuildRetrofit() {
        _retrofit = buildRetrofit()
    }

    fun setToken(token: String?) {
        authToken = token
    }

    fun getToken(): String? = authToken
}
