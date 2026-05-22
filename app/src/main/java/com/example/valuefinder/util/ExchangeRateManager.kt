package com.example.valuefinder.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL

/**
 * Manages exchange rates with local caching and optional live API refresh.
 * Caches rate for 24 hours before attempting refresh.
 */
object ExchangeRateManager {
    private const val TAG = "ExchangeRateManager"
    private const val PREF_NAME = "exchange_rates"
    private const val PREF_KEY_USD_TO_AUD_RATE = "usd_to_aud_rate"
    private const val PREF_KEY_LAST_RATE_UPDATE = "usd_to_aud_rate_timestamp"
    private const val CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000L // 24 hours
    private const val DEFAULT_USD_TO_AUD_RATE = 1.55 // Fallback default

    /** Get USD to AUD exchange rate (cached for 24 hours) */
    fun getUsdToAudRate(context: Context): Double {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // Read as String to preserve full Double precision (Float loses ~8 decimal places)
        val cachedRate = prefs.getString(PREF_KEY_USD_TO_AUD_RATE, null)
            ?.toDoubleOrNull()
            ?: DEFAULT_USD_TO_AUD_RATE
        val lastUpdate = prefs.getLong(PREF_KEY_LAST_RATE_UPDATE, 0L)
        val now = System.currentTimeMillis()

        // If cache is fresh, use it
        if (now - lastUpdate < CACHE_TTL_MILLIS) {
            Log.d(TAG, "Using cached USD→AUD rate: $cachedRate")
            return cachedRate
        }

        // Cache expired; attempt refresh (fire-and-forget)
        Log.d(TAG, "Exchange rate cache expired; attempting refresh (cached: $cachedRate)")
        refreshExchangeRateAsync(context)
        return cachedRate
    }

    /** Attempt to refresh rate from open.er-api.com (free, no API key required) */
    private fun refreshExchangeRateAsync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = URL("https://open.er-api.com/v6/latest/USD").readText()
                val rate = JSONObject(json)
                    .getJSONObject("rates")
                    .getDouble("AUD")
                if (rate > 0) {
                    updateRate(context, rate)
                    Log.d(TAG, "Exchange rate refreshed: 1 USD = $rate AUD")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to refresh exchange rate; using cached value", e)
            }
        }
    }

    /** Update cached exchange rate (stored as String to preserve Double precision) */
    fun updateRate(context: Context, rate: Double) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_KEY_USD_TO_AUD_RATE, rate.toString())
            .putLong(PREF_KEY_LAST_RATE_UPDATE, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Exchange rate updated to $rate")
    }
}
