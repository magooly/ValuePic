package com.example.valuefinder

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import java.net.URL
import com.example.valuefinder.util.ExchangeRateManager
import com.example.valuefinder.util.RetryUtil

data class SearchResult(
    val title: String,
    val price: Double?,
    val url: String,
    val source: String,
    val description: String = "",
    val visitUrl: String = ""
)

data class ValuationResult(
    val estimatedValue: Double,
    val confidence: Float,
    val source: String,
    val results: List<SearchResult>
)

data class WebDescriptionResult(
    val oneLine: String,
    val fullDescription: String,
    val sourceUrl: String
)

enum class WebLookupFailureReason {
    EMPTY_QUERY,
    NETWORK,
    TIMEOUT,
    PARSE_CHANGED,
    NO_RESULTS
}

sealed class WebValuationOutcome {
    data class Success(val result: ValuationResult) : WebValuationOutcome()
    data class Failure(val reason: WebLookupFailureReason, val detail: String? = null) : WebValuationOutcome()
}

class WebValuationService(private val context: Context) {

    private val gson = Gson()
    private val googleCseApiKey: String = BuildConfig.GOOGLE_CSE_API_KEY.trim()
    private val googleCseCx: String = BuildConfig.GOOGLE_CSE_CX.trim()

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private fun isGoogleConfigured(): Boolean =
        googleCseApiKey.isNotBlank() && googleCseCx.isNotBlank()

    suspend fun searchForValue(
        itemName: String,
        description: String,
        detailedMode: Boolean
    ): ValuationResult? {
        return when (val outcome = searchForValueDetailed(itemName, description, detailedMode)) {
            is WebValuationOutcome.Success -> outcome.result
            is WebValuationOutcome.Failure -> null
        }
    }

    suspend fun searchForValueDetailed(
        itemName: String,
        description: String,
        detailedMode: Boolean
    ): WebValuationOutcome {
        // Wrap entire search with 30-second timeout to prevent UI freeze
        return RetryUtil.withOperationTimeout(timeoutMs = 30_000L) {
            withContext(Dispatchers.IO) {
                val searchQuery = listOf(itemName, description)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .trim()
                if (searchQuery.isBlank()) {
                    return@withContext WebValuationOutcome.Failure(WebLookupFailureReason.EMPTY_QUERY)
                }

                val ebayAttempt = runCatching {
                    searchEbay(
                        query = searchQuery,
                        maxResults = if (detailedMode) 12 else 6
                    )
                }
                val ebayResults = rankResultsForQuery(
                    results = ebayAttempt.getOrDefault(emptyList()),
                    query = searchQuery
                )
                val ebayError = ebayAttempt.exceptionOrNull()

                val ebayValuation = buildValuationResult(
                    source = ValueSource.EbayLive.key,
                    results = ebayResults
                )
                if (!detailedMode && ebayValuation != null) {
                    return@withContext WebValuationOutcome.Success(ebayValuation)
                }

                val ddgAttempt = runCatching {
                    searchDuckDuckGo(
                        query = searchQuery,
                        maxResults = if (detailedMode) 10 else 5
                    )
                }
                val ddgResults = rankResultsForQuery(
                    results = ddgAttempt.getOrDefault(emptyList()),
                    query = searchQuery
                )
                val ddgError = ddgAttempt.exceptionOrNull()

                // Keep free provider path as default. Google is opt-in fallback only.
                val googleAttempt = if (ddgResults.isEmpty() && isGoogleConfigured()) {
                    runCatching {
                        searchGoogleCustomSearch(
                            query = searchQuery,
                            maxResults = if (detailedMode) 10 else 5,
                            imageMode = true
                        )
                    }
                } else {
                    Result.success(emptyList())
                }
                val googleResults = rankResultsForQuery(
                    results = googleAttempt.getOrDefault(emptyList()),
                    query = searchQuery
                )
                val googleError = googleAttempt.exceptionOrNull()
                val fallbackResults = if (googleResults.isNotEmpty()) googleResults else ddgResults

                val fallbackSourceKey = if (googleResults.isNotEmpty()) {
                    ValueSource.Google.key
                } else {
                    ValueSource.DuckDuckGo.key
                }

                val combinedError = ebayError ?: googleError ?: ddgError

                if (detailedMode) {
                    val detailedResult = buildValuationResult(
                        source = if (googleResults.isNotEmpty()) {
                            ValueSource.EbayAndGoogle.key
                        } else {
                            ValueSource.EbayAndDDG.key
                        },
                        results = rankResultsForQuery(
                            results = (ebayResults + fallbackResults)
                                .distinctBy { it.visitUrl.ifBlank { it.url } },
                            query = searchQuery
                        )
                    )
                    if (detailedResult != null) {
                        WebValuationOutcome.Success(detailedResult)
                    } else {
                        failureOutcomeFor(combinedError)
                    }
                } else {
                    val fallbackResult = buildValuationResult(
                        source = fallbackSourceKey,
                        results = fallbackResults
                    )
                    if (fallbackResult != null) {
                        WebValuationOutcome.Success(fallbackResult)
                    } else {
                        failureOutcomeFor(combinedError)
                    }
                }
            }
        } ?: WebValuationOutcome.Failure(WebLookupFailureReason.TIMEOUT)
    }

    private fun failureOutcomeFor(error: Throwable?): WebValuationOutcome {
        if (error == null) {
            return WebValuationOutcome.Failure(WebLookupFailureReason.NO_RESULTS)
        }
        return when (error) {
            is java.net.SocketTimeoutException,
            is java.net.SocketException -> WebValuationOutcome.Failure(WebLookupFailureReason.TIMEOUT, error.message)

            is org.jsoup.HttpStatusException,
            is java.net.UnknownHostException,
            is javax.net.ssl.SSLException -> WebValuationOutcome.Failure(WebLookupFailureReason.NETWORK, error.message)

            else -> WebValuationOutcome.Failure(WebLookupFailureReason.PARSE_CHANGED, error.message)
        }
    }

    suspend fun fetchFullDescription(
        itemName: String,
        hint: String,
        labels: List<String>
    ): WebDescriptionResult? = withContext(Dispatchers.IO) {
        val query = listOf(itemName, hint, labels.joinToString(" "))
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        if (query.isBlank()) return@withContext null

        val ddgMatches = rankResultsForQuery(
            results = runCatching { searchDuckDuckGo("$query what is it") }.getOrDefault(emptyList()),
            query = query
        )
        val webMatches = if (ddgMatches.isNotEmpty()) {
            ddgMatches
        } else if (isGoogleConfigured()) {
            rankResultsForQuery(
                results = runCatching {
                    searchGoogleCustomSearch(
                        query = "$query what is it",
                        maxResults = 6,
                        imageMode = true
                    )
                }.getOrDefault(emptyList()),
                query = query
            )
        } else {
            emptyList()
        }

        if (webMatches.isNotEmpty()) {
            val top = webMatches.first()
            val matchedDescription = top.description.ifBlank { top.title }
            val resolvedVisitUrl = top.visitUrl.ifBlank { top.url }
            val oneLine = "$matchedDescription. Approx value signals found online."
            val detail = buildString {
                appendLine("Item: $itemName")
                if (labels.isNotEmpty()) appendLine("Detected labels: ${labels.joinToString(", ")}")
                appendLine("Top web reference: ${top.title}")
                if (top.description.isNotBlank()) appendLine("Description: ${top.description}")
                appendLine("Source: ${top.source}")
                if (resolvedVisitUrl.isNotBlank()) appendLine("Visit: $resolvedVisitUrl")
                append("Use this as a preliminary description and verify condition/brand/model manually.")
            }
            return@withContext WebDescriptionResult(
                oneLine = oneLine.take(160),
                fullDescription = detail,
                sourceUrl = resolvedVisitUrl
            )
        }

        null
    }

    private fun buildValuationResult(
        source: String,
        results: List<SearchResult>
    ): ValuationResult? {
        // Get live (or cached) USD→AUD exchange rate
        val usdToAudRate = ExchangeRateManager.getUsdToAudRate(context)
        Log.d("WebValuationService", "Using USD→AUD rate: $usdToAudRate")
        
        // Normalize looked-up USD prices into AUD for a consistent app currency.
        val audResults = results.map { result ->
            result.copy(price = result.price?.times(usdToAudRate))
        }
        val prices = audResults.mapNotNull { it.price }.filter { it in 1.0..1_000_000.0 }
        if (prices.isEmpty()) return null

        val estimate = estimateValue(prices)
        val confidence = estimateConfidence(prices)
        return ValuationResult(
            estimatedValue = estimate,
            confidence = confidence,
            source = source,
            results = audResults
        )
    }

    private fun estimateValue(prices: List<Double>): Double {
        val sorted = prices.sorted()
        val trimmed = when {
            sorted.size >= 5 -> sorted.drop(1).dropLast(1)
            else -> sorted
        }
        return trimmed.average()
    }

    private fun estimateConfidence(prices: List<Double>): Float {
        /**
         * Confidence score combines two factors:
         * 1. Quantity Score: More data points = higher confidence (0.1 to 0.45)
         * 2. Consistency Score: Lower price spread = higher confidence (0.1 to 0.5)
         *
         * Total score = quantity + consistency, clamped to [0.15, 0.9]
         *
         * Example: 5 prices ($100, $110, $105, $120, $95) with avg=$106
         * - Quantity: 5 items → 0.35
         * - Spread: ($120-$95)/$106 = 0.236 → consistency ≈ 0.35
         * - Total ≈ 0.70 (high confidence)
         */
        if (prices.isEmpty()) return 0f
        val average = prices.average()
        if (average <= 0.0) return 0f
        val spread = prices.max() - prices.min()
        val normalizedSpread = (spread / average).coerceAtLeast(0.0)
        val quantityScore = when {
            prices.size >= 6 -> 0.45f
            prices.size >= 4 -> 0.35f
            prices.size >= 2 -> 0.25f
            else -> 0.1f
        }
        val consistencyScore = (0.5f - normalizedSpread.toFloat() * 0.2f).coerceIn(0.1f, 0.5f)
        return (quantityScore + consistencyScore).coerceIn(0.15f, 0.9f)
    }

    private fun parsePrice(text: String): Double? {
        val candidates = extractPrices(text)
        return when {
            candidates.isEmpty() -> null
            candidates.size == 1 -> candidates.first()
            else -> candidates.average()
        }
    }

    internal fun extractPrices(text: String): List<Double> {
        val regex = Regex("""(?<!\w)(?:US\$|USD\s?|\$)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{2})?)""")
        return regex.findAll(text)
            .mapNotNull { match ->
                match.groupValues.getOrNull(1)
                    ?.replace(",", "")
                    ?.toDoubleOrNull()
            }
            .toList()
    }

    suspend fun searchEbay(query: String, maxResults: Int = 8): List<SearchResult> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.ebay.com/sch/i.html?_nkw=$encodedQuery&LH_BIN=1&_sop=15"
        val document = getDocument(url)
        parseEbayDocument(document, maxResults)
    }

    internal fun parseEbayDocument(document: Document, maxResults: Int = 8): List<SearchResult> {
        return document.select("li.s-item")
            .mapNotNull { item ->
                val title = item.selectFirst(".s-item__title")?.text().orEmpty()
                val priceText = item.selectFirst(".s-item__price")?.text().orEmpty()
                val href = item.selectFirst(".s-item__link")?.attr("abs:href").orEmpty()
                if (title.isBlank() || href.isBlank()) return@mapNotNull null
                SearchResult(
                    title = title,
                    price = parsePrice(priceText),
                    url = href,
                    source = "eBay",
                    description = title,
                    visitUrl = href
                )
            }
            .filter { it.price != null }
            .take(maxResults)
    }

    suspend fun searchDuckDuckGo(query: String, maxResults: Int = 6): List<SearchResult> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode("$query price", "UTF-8")
        val url = "https://html.duckduckgo.com/html/?q=$encodedQuery"
        val document = getDocument(url)
        parseDuckDuckGoDocument(document, maxResults)
    }

    suspend fun searchGoogleCustomSearch(
        query: String,
        maxResults: Int = 6,
        imageMode: Boolean = true
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        if (!isGoogleConfigured()) return@withContext emptyList()
        val clampedMax = maxResults.coerceIn(1, 10)
        val encodedQuery = URLEncoder.encode("$query price", "UTF-8")
        val imageParam = if (imageMode) "&searchType=image" else ""
        val url =
            "https://www.googleapis.com/customsearch/v1?key=$googleCseApiKey&cx=$googleCseCx&q=$encodedQuery&num=$clampedMax$imageParam"
        val json = URL(url).readText(Charsets.UTF_8)
        parseGoogleCustomSearchJson(json, clampedMax)
    }

    internal fun parseGoogleCustomSearchJson(json: String, maxResults: Int = 6): List<SearchResult> {
        val root = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val items = root.getAsJsonArray("items") ?: return emptyList()
        return items.mapNotNull { element ->
            val obj = element?.asJsonObject ?: return@mapNotNull null
            val title = obj.get("title")?.asString.orEmpty()
            val snippet = obj.get("snippet")?.asString.orEmpty()
            val link = obj.get("link")?.asString.orEmpty()
            val displayLink = obj.get("displayLink")?.asString.orEmpty()
            val price = parsePrice("$title $snippet")
            if (title.isBlank() || link.isBlank() || price == null) return@mapNotNull null
            SearchResult(
                title = title,
                price = price,
                url = link,
                source = if (displayLink.isBlank()) "Google" else "Google ($displayLink)",
                description = snippet.ifBlank { title },
                visitUrl = link
            )
        }.take(maxResults)
    }

    internal fun parseDuckDuckGoDocument(document: Document, maxResults: Int = 6): List<SearchResult> {
        return document.select("div.result")
            .mapNotNull { result ->
                val title = result.selectFirst(".result__title")?.text().orEmpty()
                val snippet = result.selectFirst(".result__snippet")?.text().orEmpty()
                val href = result.selectFirst("a.result__a")?.attr("abs:href").orEmpty()
                val visitUrl = extractVisitUrlFromDuckDuckGoHref(href)
                val combinedText = "$title $snippet"
                val price = parsePrice(combinedText)
                if (title.isBlank() || href.isBlank() || price == null) return@mapNotNull null
                SearchResult(
                    title = title,
                    price = price,
                    url = href,
                    source = "DuckDuckGo",
                    description = snippet.ifBlank { title },
                    visitUrl = visitUrl
                )
            }
            .take(maxResults)
    }

    private fun extractVisitUrlFromDuckDuckGoHref(href: String): String {
        if (href.isBlank()) return ""
        val encodedTarget = href.substringAfter("uddg=", "")
            .substringBefore('&')
        if (encodedTarget.isBlank()) return href
        return runCatching {
            URLDecoder.decode(encodedTarget, Charsets.UTF_8.name())
        }.getOrDefault(href)
    }

    private fun rankResultsForQuery(results: List<SearchResult>, query: String): List<SearchResult> {
        if (results.size <= 1) return results
        val tokens = query.lowercase()
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 3 }
            .distinct()

        val trustedHosts = listOf(
            "ebay.", "etsy.", "gumtree.", "facebook.", "amazon.", "shopify.", "chairish.", "1stdibs."
        )

        fun score(result: SearchResult): Int {
            val text = listOf(result.title, result.description)
                .joinToString(" ")
                .lowercase()
            val host = extractHost(result.visitUrl.ifBlank { result.url })
            val tokenHits = tokens.count { token -> text.contains(token) }
            val trustedBonus = if (trustedHosts.any { marker -> host.contains(marker) }) 3 else 0
            val sourceBonus = when {
                result.source.contains("ebay", ignoreCase = true) -> 2
                result.source.contains("duckduckgo", ignoreCase = true) -> 1
                else -> 0
            }
            val priceBonus = if (result.price != null) 2 else 0
            val urlBonus = if (result.visitUrl.ifBlank { result.url }.startsWith("http", ignoreCase = true)) 1 else 0
            return tokenHits * 2 + trustedBonus + sourceBonus + priceBonus + urlBonus
        }

        return results.sortedWith(
            compareByDescending<SearchResult> { score(it) }
                .thenByDescending { it.price != null }
        )
    }

    private fun extractHost(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        return runCatching { URI(rawUrl).host.orEmpty().lowercase() }.getOrDefault("")
    }

    private fun getDocument(url: String): Document {
        return Jsoup.connect(url)
            .userAgent(userAgent)
            .timeout(8_000)
            .followRedirects(true)
            .get()
    }
}

