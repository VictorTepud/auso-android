package com.auso.social.rust

/**
 * Bridge to Rust native code via JNI.
 * Rust handles crypto operations, password hashing, and token validation
 * for enhanced security (no plain-text passwords in JVM memory).
 */
object RustBridge {

    init {
        System.loadLibrary("auso_core")
    }

    /**
     * Hash a password using bcrypt via Rust
     */
    external fun hashPassword(password: String): String

    /**
     * Verify a password against a bcrypt hash via Rust
     */
    external fun verifyPassword(password: String, hash: String): Boolean

    /**
     * Validate JWT token locally via Rust (offline validation)
     */
    external fun validateToken(token: String, secret: String): Boolean

    /**
     * Generate a unique ID using Rust UUID v4
     */
    external fun generateId(): String

    /**
     * Sanitize user input to prevent XSS/injection
     */
    external fun sanitizeInput(input: String): String
}
