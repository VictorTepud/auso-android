package com.auso.social.rust

import android.util.Log

/**
 * Bridge to Rust native code via JNI.
 * Rust handles crypto operations, password hashing, and token validation
 * for enhanced security (no plain-text passwords in JVM memory).
 *
 * If Rust library is not compiled, all methods fall back to Kotlin implementations.
 */
object RustBridge {

    private var isRustAvailable: Boolean = false

    init {
        try {
            System.loadLibrary("auso_core")
            isRustAvailable = true
            Log.i("RustBridge", "Rust native library loaded")
        } catch (e: UnsatisfiedLinkError) {
            isRustAvailable = false
            Log.w("RustBridge", "Rust library not available, using Kotlin fallbacks")
        }
    }

    /**
     * Hash a password using bcrypt via Rust (fallback: returns input unchanged)
     */
    fun hashPassword(password: String): String {
        return if (isRustAvailable) {
            hashPasswordNative(password)
        } else {
            // Fallback: server handles hashing
            password
        }
    }

    /**
     * Verify a password against a bcrypt hash via Rust
     */
    fun verifyPassword(password: String, hash: String): Boolean {
        return if (isRustAvailable) {
            verifyPasswordNative(password, hash)
        } else {
            false // Server handles verification
        }
    }

    /**
     * Validate JWT token locally via Rust
     */
    fun validateToken(token: String, secret: String): Boolean {
        return if (isRustAvailable) {
            validateTokenNative(token, secret)
        } else {
            // Basic Kotlin fallback: check JWT has 3 parts
            token.split(".").size == 3
        }
    }

    /**
     * Generate a unique ID using Rust UUID v4
     */
    fun generateId(): String {
        return if (isRustAvailable) {
            generateIdNative()
        } else {
            java.util.UUID.randomUUID().toString()
        }
    }

    /**
     * Sanitize user input to prevent XSS/injection
     */
    fun sanitizeInput(input: String): String {
        return if (isRustAvailable) {
            sanitizeInputNative(input)
        } else {
            // Basic Kotlin fallback: strip HTML tags
            input.replace(Regex("<[^>]*>"), "")
                .trim()
        }
    }

    // Native methods (only called when Rust is available)
    private external fun hashPasswordNative(password: String): String
    private external fun verifyPasswordNative(password: String, hash: String): Boolean
    private external fun validateTokenNative(token: String, secret: String): Boolean
    private external fun generateIdNative(): String
    private external fun sanitizeInputNative(input: String): String
}
