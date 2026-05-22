/*
 * Copyright (c) 2026 Wally Horsman.
 * All rights reserved.
 */

package com.example.valuefinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.valuefinder.ui.ValuePicsApp

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) android.util.Log.d(TAG, "onCreate called")

        try {
            if (savedInstanceState != null) {
                if (BuildConfig.DEBUG) android.util.Log.i(TAG, "Recovering from process death")
            }
            setContent {
                ValuePicsApp()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error initializing app composition; attempting recovery", e)
            // Optionally: show error UI, fall back to simple view, or retry
            // For now, propagate to allow system to show error dialog
            throw e
        }
    }

    override fun onStart() {
        super.onStart()
        if (BuildConfig.DEBUG) android.util.Log.d(TAG, "onStart called")
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.DEBUG) android.util.Log.d(TAG, "onResume called")
    }

    override fun onPause() {
        super.onPause()
        if (BuildConfig.DEBUG) android.util.Log.d(TAG, "onPause called")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (BuildConfig.DEBUG) android.util.Log.d(TAG, "onDestroy called")
    }
}

