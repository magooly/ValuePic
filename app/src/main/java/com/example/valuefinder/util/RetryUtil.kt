package com.example.valuefinder.util

import android.util.Log
import kotlin.math.min
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Retry helper with exponential backoff
 */
object RetryUtil {
    private const val TAG = "RetryUtil"

    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMillis: Long = 100,
        maxDelayMillis: Long = 5000,
        backoffMultiplier: Double = 2.0,
        shouldRetry: (Exception) -> Boolean = { true },
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        var delayMillis = initialDelayMillis

        repeat(maxAttempts) { attempt ->
            try {
                Log.d(TAG, "Attempt ${attempt + 1}/$maxAttempts")
                return block()
            } catch (e: Exception) {
                lastException = e

                if (attempt < maxAttempts - 1 && shouldRetry(e)) {
                    Log.w(TAG, "Attempt ${attempt + 1} failed, retrying in ${delayMillis}ms", e)
                    kotlinx.coroutines.delay(delayMillis)
                    delayMillis = min((delayMillis * backoffMultiplier).toLong(), maxDelayMillis)
                } else {
                    Log.e(TAG, "All $maxAttempts attempts failed", e)
                }
            }
        }

        // All attempts exhausted; throw the last exception with context
        throw lastException?.also { e ->
            Log.e(TAG, "Retry exhausted after $maxAttempts attempts: ${e.javaClass.simpleName}: ${e.message}", e)
        } ?: IllegalStateException("Failed after $maxAttempts attempts (no exception captured)")
    }

    /**
     * Predicate for determining if an operation should be retried
     * Returns false for fatal errors like authentication failures
     */
    fun isRetryable(exception: Exception): Boolean {
        return when {
            // Fatal errors - don't retry
            exception is IllegalArgumentException -> false
            exception is SecurityException -> false
            // Network and timeout errors - retry
            exception.message?.contains("timeout", ignoreCase = true) == true -> true
            exception.message?.contains("network", ignoreCase = true) == true -> true
            exception.message?.contains("connection", ignoreCase = true) == true -> true
            // Default to retrying
            else -> true
        }
    }

    /**
     * Executes a suspending block with a timeout, returning null if it times out.
     * Prevents UI hangs from network operations.
     * @param timeoutMs Timeout in milliseconds (default 30 seconds)
     * @param block The suspend function to execute
     * @return Result of block, or null if timeout occurred
     */
    suspend fun <T> withOperationTimeout(
        timeoutMs: Long = 30_000L, // 30 seconds default
        block: suspend () -> T
    ): T? {
        return try {
            withTimeoutOrNull(timeoutMs) {
                block()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Operation timed out after ${timeoutMs}ms", e)
            null
        }
    }
}
