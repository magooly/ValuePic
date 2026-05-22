package com.example.valuefinder.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurePreferencesManagerTest {
    private lateinit var preferencesManager: SecurePreferencesManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        preferencesManager = SecurePreferencesManager(context)
        preferencesManager.clear()
    }

    @After
    fun tearDown() {
        preferencesManager.clear()
    }

    @Test
    fun testPutAndGetString() {
        // Given
        val key = "test_key"
        val value = "test_value"

        // When
        preferencesManager.putString(key, value)
        val result = preferencesManager.getString(key)

        // Then
        assert(result == value)
    }

    @Test
    fun testPutAndGetBoolean() {
        // Given
        val key = "test_bool"
        val value = true

        // When
        preferencesManager.putBoolean(key, value)
        val result = preferencesManager.getBoolean(key)

        // Then
        assert(result == value)
    }

    @Test
    fun testPutAndGetInt() {
        // Given
        val key = "test_int"
        val value = 42

        // When
        preferencesManager.putInt(key, value)
        val result = preferencesManager.getInt(key)

        // Then
        assert(result == value)
    }

    @Test
    fun testPutAndGetLong() {
        // Given
        val key = "test_long"
        val value = 12345L

        // When
        preferencesManager.putLong(key, value)
        val result = preferencesManager.getLong(key)

        // Then
        assert(result == value)
    }

    @Test
    fun testRemoveKey() {
        // Given
        val key = "test_remove"
        preferencesManager.putString(key, "value")

        // When
        preferencesManager.remove(key)
        val result = preferencesManager.getString(key, "default")

        // Then
        assert(result == "default")
    }

    @Test
    fun testDefaultValues() {
        // Given
        val stringKey = "nonexistent_string"
        val boolKey = "nonexistent_bool"
        val intKey = "nonexistent_int"

        // When
        val stringResult = preferencesManager.getString(stringKey, "default_string")
        val boolResult = preferencesManager.getBoolean(boolKey, false)
        val intResult = preferencesManager.getInt(intKey, -1)

        // Then
        assert(stringResult == "default_string")
        assert(boolResult == false)
        assert(intResult == -1)
    }
}

