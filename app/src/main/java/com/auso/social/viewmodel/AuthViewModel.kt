package com.auso.social.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auso.social.network.model.UserProfile
import com.auso.social.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for authentication state management
 */
class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    fun register(email: String, password: String, username: String) {
        if (!validateRegister(email, password, username)) return

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.register(email, password, username)
            _uiState.value = result.fold(
                onSuccess = {
                    _currentUser.value = it.user
                    _isLoggedIn.value = true
                    AuthUiState.Success(it.user)
                },
                onFailure = { AuthUiState.Error(it.message ?: "Error desconocido") }
            )
        }
    }

    fun login(email: String, password: String) {
        if (!validateLogin(email, password)) return

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.login(email, password)
            _uiState.value = result.fold(
                onSuccess = {
                    _currentUser.value = it.user
                    _isLoggedIn.value = true
                    AuthUiState.Success(it.user)
                },
                onFailure = { AuthUiState.Error(it.message ?: "Error desconocido") }
            )
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            val result = repository.getMyProfile()
            result.onSuccess {
                _currentUser.value = it.user
                _isLoggedIn.value = true
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    private fun validateRegister(email: String, password: String, username: String): Boolean {
        if (email.isBlank() || password.isBlank() || username.isBlank()) {
            _uiState.value = AuthUiState.Error("Completa todos los campos")
            return false
        }
        if (password.length < 6) {
            _uiState.value = AuthUiState.Error("La contraseña debe tener al menos 6 caracteres")
            return false
        }
        if (username.length < 3) {
            _uiState.value = AuthUiState.Error("El usuario debe tener al menos 3 caracteres")
            return false
        }
        return true
    }

    private fun validateLogin(email: String, password: String): Boolean {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Completa todos los campos")
            return false
        }
        return true
    }
}

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val user: UserProfile) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
