package com.auso.social.repository

import com.auso.social.network.AusoApiClient
import com.auso.social.network.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for auth and user operations
 */
class AuthRepository {

    private val api = AusoApiClient.api

    suspend fun register(email: String, password: String, username: String): Result<AuthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.register(RegisterRequest(email, password, username))
                if (response.isSuccessful) {
                    val body = response.body()!!
                    AusoApiClient.setToken(body.token)
                    Result.success(body)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMsg = parseError(errorBody)
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Error de conexión: ${e.message}"))
            }
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.login(LoginRequest(email, password))
                if (response.isSuccessful) {
                    val body = response.body()!!
                    AusoApiClient.setToken(body.token)
                    Result.success(body)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMsg = parseError(errorBody)
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Error de conexión: ${e.message}"))
            }
        }
    }

    suspend fun getMyProfile(): Result<UserProfileResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = AusoApiClient.getToken() ?: return@withContext Result.failure(Exception("No autenticado"))
                val response = api.getMyProfile("Bearer $token")
                if (response.isSuccessful) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Error al obtener perfil"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Error de conexión: ${e.message}"))
            }
        }
    }

    private fun parseError(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return "Error desconocido"
        return try {
            val gson = com.google.gson.Gson()
            val error = gson.fromJson(errorBody, ApiError::class.java)
            error.message
        } catch (e: Exception) {
            "Error desconocido"
        }
    }
}
