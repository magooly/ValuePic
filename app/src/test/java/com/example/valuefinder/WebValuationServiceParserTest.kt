package com.example.valuefinder

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebValuationServiceParserTest {

    private val service = WebValuationService(RuntimeEnvironment.getApplication())

    @Test
    fun parseEbayDocument_extractsTitlePriceAndUrl() {
        val html = """
            <html><body>
              <ul>
                <li class='s-item'>
                  <a class='s-item__link' href='https://www.ebay.com/itm/123'>link</a>
                  <div class='s-item__title'>Vintage Watch</div>
                  <div class='s-item__price'>US $129.99</div>
                </li>
                <li class='s-item'>
                  <a class='s-item__link' href='https://www.ebay.com/itm/456'>link</a>
                  <div class='s-item__title'>No Price Item</div>
                </li>
              </ul>
            </body></html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://www.ebay.com")
        val results = service.parseEbayDocument(doc, maxResults = 10)

        assertEquals(1, results.size)
        assertEquals("Vintage Watch", results[0].title)
        assertEquals("eBay", results[0].source)
        assertTrue(results[0].url.contains("/itm/123"))
        assertEquals(129.99, results[0].price ?: 0.0, 0.001)
    }

    @Test
    fun parseDuckDuckGoDocument_extractsPriceFromTitleAndSnippet() {
        val html = """
            <html><body>
              <div class='result'>
                <a class='result__a' href='https://example.com/item-1'>Antique Radio</a>
                <div class='result__title'>Antique Radio price guide</div>
                <div class='result__snippet'>Recent sale around US $75.00 in average condition</div>
              </div>
              <div class='result'>
                <a class='result__a' href='https://example.com/item-2'>No price</a>
                <div class='result__title'>No price mentioned</div>
                <div class='result__snippet'>Unknown</div>
              </div>
            </body></html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://html.duckduckgo.com")
        val results = service.parseDuckDuckGoDocument(doc, maxResults = 10)

        assertEquals(1, results.size)
        assertEquals("DuckDuckGo", results[0].source)
        assertEquals(75.0, results[0].price ?: 0.0, 0.001)
        assertTrue(results[0].url.contains("example.com/item-1"))
        assertEquals("Recent sale around US $75.00 in average condition", results[0].description)
        assertTrue(results[0].visitUrl.contains("example.com/item-1"))
    }

    @Test
    fun parseDuckDuckGoDocument_resolvesVisitUrlFromRedirectLink() {
        val html = """
            <html><body>
              <div class='result'>
                <a class='result__a' href='https://duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fvisit-page'>Match</a>
                <div class='result__title'>Match title</div>
                <div class='result__snippet'>US $45.00 from listing</div>
              </div>
            </body></html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://html.duckduckgo.com")
        val results = service.parseDuckDuckGoDocument(doc, maxResults = 10)

        assertEquals(1, results.size)
        assertEquals("https://example.com/visit-page", results[0].visitUrl)
    }

    @Test
    fun parseDuckDuckGoDocument_maxResultsLimitsOutput() {
        val html = buildString {
            append("<html><body>")
            repeat(5) { i ->
                append("""
                    <div class='result'>
                      <a class='result__a' href='https://example.com/$i'>Item $i</a>
                      <div class='result__title'>Item $i</div>
                      <div class='result__snippet'>US $$${i + 10}.00</div>
                    </div>
                """.trimIndent())
            }
            append("</body></html>")
        }

        val doc = Jsoup.parse(html, "https://html.duckduckgo.com")
        val results = service.parseDuckDuckGoDocument(doc, maxResults = 3)

        assertEquals(3, results.size)
    }

    @Test
    fun parseGoogleCustomSearchJson_extractsDescriptionAndVisitUrl() {
        val json = """
            {
              "items": [
                {
                  "title": "Vintage Radio listing",
                  "snippet": "Sold around US $120.00 in good condition",
                  "link": "https://example.com/vintage-radio",
                  "displayLink": "example.com"
                },
                {
                  "title": "No price item",
                  "snippet": "No price info",
                  "link": "https://example.com/no-price"
                }
              ]
            }
        """.trimIndent()

        val results = service.parseGoogleCustomSearchJson(json, maxResults = 10)

        assertEquals(1, results.size)
        assertEquals("Vintage Radio listing", results[0].title)
        assertEquals("Sold around US $120.00 in good condition", results[0].description)
        assertEquals("https://example.com/vintage-radio", results[0].visitUrl)
        assertTrue(results[0].source.contains("Google"))
        assertEquals(120.0, results[0].price ?: 0.0, 0.001)
    }
}

