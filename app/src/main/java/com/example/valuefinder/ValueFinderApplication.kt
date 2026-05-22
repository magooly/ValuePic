package com.example.valuefinder

import android.app.Application
import android.util.Log

class ValueFinderApplication : Application() {
    companion object {
        private const val TAG = "ValueFinderApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate")
    }
}

