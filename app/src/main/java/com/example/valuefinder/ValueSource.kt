package com.example.valuefinder

/**
 * Canonical value-source identifiers used throughout the app.
 *
 * Stored as the [key] string in the database; never store raw ad-hoc strings.
 * Use [fromKey] to deserialise and [key] to serialise.
 */
sealed class ValueSource(val key: String, val displayName: String) {
    object EbayLive       : ValueSource("ebay_live",        "eBay listings")
    object EbayAndDDG     : ValueSource("ebay_ddg",         "eBay + DuckDuckGo")
    object DuckDuckGo     : ValueSource("duckduckgo",       "DuckDuckGo price snippets")
    object Google         : ValueSource("google",           "Google search results")
    object EbayAndGoogle  : ValueSource("ebay_google",      "eBay + Google")
    object Manual         : ValueSource("manual",           "Manual entry")
    object User           : ValueSource("user",             "User sourced")
    object Unknown        : ValueSource("",                 "Unknown")
    /** Catch-all for legacy or unrecognised raw strings already in the DB. */
    data class Legacy(val raw: String) : ValueSource(raw, raw)

    companion object {
        fun fromKey(raw: String?): ValueSource {
            val k = raw?.trim().orEmpty()
            return when {
                k.equals(EbayLive.key,   ignoreCase = true) ||
                k.contains("ebay",       ignoreCase = true) &&
                !k.contains("duckduckgo",ignoreCase = true) -> EbayLive
                k.equals(EbayAndDDG.key, ignoreCase = true) ||
                k.contains("ebay",       ignoreCase = true) &&
                k.contains("duckduckgo", ignoreCase = true) -> EbayAndDDG
                k.equals(EbayAndGoogle.key, ignoreCase = true) ||
                k.contains("ebay",         ignoreCase = true) &&
                k.contains("google",       ignoreCase = true) -> EbayAndGoogle
                k.equals(DuckDuckGo.key, ignoreCase = true) ||
                k.contains("duckduckgo", ignoreCase = true) -> DuckDuckGo
                k.equals(Google.key, ignoreCase = true) ||
                k.contains("google", ignoreCase = true) -> Google
                k.equals(Manual.key,     ignoreCase = true) ||
                k.equals("manual",       ignoreCase = true) -> Manual
                k.equals(User.key,       ignoreCase = true) ||
                k.equals("user",         ignoreCase = true) -> User
                k.isBlank() -> Unknown
                else -> Legacy(k)
            }
        }

        /** Returns true when the source is web-automated (not user-entered). */
        fun ValueSource.isWebSourced(): Boolean = when (this) {
            is EbayLive, is EbayAndDDG, is DuckDuckGo, is Google, is EbayAndGoogle -> true
            else -> false
        }
    }
}

