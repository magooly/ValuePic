package com.example.valuefinder.di

import android.content.Context
import com.example.valuefinder.ImageRecognitionService
import com.example.valuefinder.ValuePicsRepository
import com.example.valuefinder.util.SecurePreferencesManager

/**
 * Factory methods for creating app-level services and managers.
 *
 * Usage: Call from Compose `remember` blocks with application context:
 *   val repository = remember(appContext) { AppModule.createRepository(appContext) }
 *
 * Note: If implementing full dependency injection (e.g., Hilt), replace these
 * factory methods with annotated constructors and inject directly.
 */
object AppModule {
    fun createPreferencesManager(context: Context): SecurePreferencesManager =
        SecurePreferencesManager(context.applicationContext)

    fun createRepository(context: Context): ValuePicsRepository =
        ValuePicsRepository(context.applicationContext)

    fun createImageRecognitionService(context: Context): ImageRecognitionService =
        ImageRecognitionService(context.applicationContext)
}

