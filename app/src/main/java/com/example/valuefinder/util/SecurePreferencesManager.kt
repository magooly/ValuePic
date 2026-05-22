package com.example.valuefinder.util

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * Centralized preferences wrapper.
 *
 * This keeps preference access in one place without changing the app's
 * current persistence behavior.
 */
class SecurePreferencesManager(
    context: Context
) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREF_FILE_NAME,
        Context.MODE_PRIVATE
    )

    fun getString(key: String, defaultValue: String = ""): String {
        return preferences.getString(key, defaultValue) ?: defaultValue
    }

    fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return preferences.getBoolean(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return preferences.getLong(key, defaultValue)
    }

    fun putLong(key: String, value: Long) {
        preferences.edit().putLong(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return preferences.getInt(key, defaultValue)
    }

    fun putInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }

    fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    fun hasNotesPin(): Boolean {
        return getString(PREF_NOTES_PIN_HASH).isNotBlank()
    }

    fun setNotesPin(pin: String) {
        putString(PREF_NOTES_PIN_HASH, hashPin(pin))
    }

    fun verifyNotesPin(pin: String): Boolean {
        val storedHash = getString(PREF_NOTES_PIN_HASH)
        if (storedHash.isBlank()) return false
        return storedHash == hashPin(pin)
    }

    fun clearNotesPin() {
        remove(PREF_NOTES_PIN_HASH)
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    companion object {
        private const val PREF_FILE_NAME = "valuepics_secure_prefs"
        const val PREF_THEME_MODE = "theme_mode"
        const val PREF_DARK_MODE = "dark_mode_enabled"
        const val PREF_PHOTO_TARGET_SIZE = "photo_target_size_kb"
        const val PREF_LAST_BACKUP = "last_backup_millis"
        const val PREF_NOTES_PIN_HASH = "notes_pin_hash"
    }
}

