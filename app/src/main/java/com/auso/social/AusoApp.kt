package com.auso.social

import android.app.Application

class AusoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Rust native library
        System.loadLibrary("auso_core")
    }
}
