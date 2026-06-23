package com.auso.social.network

import com.auso.social.network.model.*
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Singleton API client for AUSO backend communication
 */
object AusoApiClient {

    // Default: real device on same network as server
    // Change this to your computer's local IP (run `ip addr` or `hostname -I` on Linux)
    // For emulator use: http://10.0.2.2:8080/
    // Current server IP: 192.168.10.107 (PC local IP)
    var baseUrl: String = "http://192.168.10.107:8080/"
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

    /**
     * Build a full URL from a relative path, avoiding double slashes
     */
    fun fullUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val base = baseUrl.trimEnd('/')
        val relative = path.trimStart('/')
        return "$base/$relative"
    }

    /**
     * Convert a content URI to a MultipartBody.Part for file uploads
     */
    fun uriToMultipartPart(context: Context, uri: Uri, paramName: String = "file"): MultipartBody.Part? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            
            // Get file name
            val fileName = getFileName(context, uri) ?: "upload_${System.currentTimeMillis()}.jpg"
            
            // Copy to temp file
            val tempFile = File.createTempFile("upload_", ".jpg", context.cacheDir)
            tempFile.outputStream().use { output -> inputStream.copyTo(output) }
            inputStream.close()
            
            val mimeType = contentResolver.getType(uri) ?: "image/*"
            val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData(paramName, fileName, requestBody)
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
