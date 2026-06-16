package com.auso.social

import android.app.Application
import android.util.Log

class AusoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Try to load Rust native library (optional - only if compiled)
        try {
            System.loadLibrary("auso_core")
            Log.i("AusoApp", "Rust native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.w("AusoApp", "Rust native library not found - running in pure Kotlin mode")
        }
    }
}
