package com.example.valuefinder.ui

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [AppDestination] route and deep-link construction helpers.
 *
 * Verifies that routes and deep links are correctly built, that arguments
 * round-trip through URI encoding/decoding, and that the registered nav-graph
 * pattern constants contain the expected placeholder tokens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppNavFlowTest {

    // -------------------------------------------------------------------------
    // Valuation destination
    // -------------------------------------------------------------------------

    @Test
    fun `valuation route starts with correct base`() {
        val route = AppDestination.Valuation.createRoute("any/path.jpg", "camera")
        assertTrue(route.startsWith("${AppDestination.Valuation.BASE}?"))
    }

    @Test
    fun `valuation deep link starts with correct scheme and base`() {
        val deepLink = AppDestination.Valuation.createDeepLink("any/path.jpg", "camera")
        assertTrue(deepLink.startsWith("${AppDestination.DEEP_LINK_SCHEME}://${AppDestination.Valuation.BASE}?"))
    }

    @Test
    fun `valuation route encodes and decodes arguments - happy path`() {
        val photoPath = "C:/photos/My Watch 1.jpg"
        val source = "gallery"

        val route = AppDestination.Valuation.createRoute(photoPath, source)
        val routeUri = Uri.parse("${AppDestination.DEEP_LINK_SCHEME}://${AppDestination.Valuation.BASE}?" + route.substringAfter('?'))

        assertEquals(photoPath, routeUri.getQueryParameter(AppDestination.Valuation.ARG_PHOTO_PATH))
        assertEquals(source, routeUri.getQueryParameter(AppDestination.Valuation.ARG_PHOTO_SOURCE))
    }

    @Test
    fun `valuation deep link encodes and decodes arguments - happy path`() {
        val photoPath = "C:/photos/My Watch 1.jpg"
        val source = "gallery"

        val deepLink = AppDestination.Valuation.createDeepLink(photoPath, source)
        val deepLinkUri = Uri.parse(deepLink)

        assertEquals(AppDestination.DEEP_LINK_SCHEME, deepLinkUri.scheme)
        assertEquals(AppDestination.Valuation.BASE, deepLinkUri.host)
        assertEquals(photoPath, deepLinkUri.getQueryParameter(AppDestination.Valuation.ARG_PHOTO_PATH))
        assertEquals(source, deepLinkUri.getQueryParameter(AppDestination.Valuation.ARG_PHOTO_SOURCE))
    }

    @Test
    fun `valuation route handles special characters in path`() {
        val photoPath = "C:/photos/Watch & Ring = 100% off.jpg"
        val source = "gallery"

        val route = AppDestination.Valuation.createRoute(photoPath, source)
        val routeUri = Uri.parse("${AppDestination.DEEP_LINK_SCHEME}://${AppDestination.Valuation.BASE}?" + route.substringAfter('?'))

        assertEquals(photoPath, routeUri.getQueryParameter(AppDestination.Valuation.ARG_PHOTO_PATH))
    }

    @Test
    fun `valuation route handles unicode in path`() {
        val photoPath = "/sdcard/DCIM/表 コレクション.jpg"
        val source = "camera"

        val route = AppDestination.Valuation.createRoute(photoPath, source)
        val routeUri = Uri.parse("${AppDestination.DEEP_LINK_SCHEME}://${AppDestination.Valuation.BASE}?" + route.substringAfter('?'))

        assertEquals(photoPath, routeUri.getQueryParameter(AppDestination.Valuation.ARG_PHOTO_PATH))
    }

    @Test
    fun `valuation route handles empty strings`() {
        val route = AppDestination.Valuation.createRoute("", "")
        val routeUri = Uri.parse("${AppDestination.DEEP_LINK_SCHEME}://${AppDestination.Valuation.BASE}?" + route.substringAfter('?'))

        assertEquals("", routeUri.getQueryParameter(AppDestination.Valuation.ARG_PHOTO_PATH))
        assertEquals("", routeUri.getQueryParameter(AppDestination.Valuation.ARG_PHOTO_SOURCE))
    }

    @Test
    fun `valuation DEEP_LINK_ROUTE_PATTERN contains arg placeholders`() {
        val pattern = AppDestination.Valuation.DEEP_LINK_ROUTE_PATTERN
        assertTrue(
            "Pattern must contain photoPath placeholder",
            pattern.contains("{${AppDestination.Valuation.ARG_PHOTO_PATH}}")
        )
        assertTrue(
            "Pattern must contain photoSource placeholder",
            pattern.contains("{${AppDestination.Valuation.ARG_PHOTO_SOURCE}}")
        )
        assertTrue(
            "Pattern must start with deep-link scheme",
            pattern.startsWith("${AppDestination.DEEP_LINK_SCHEME}://")
        )
    }

    // -------------------------------------------------------------------------
    // Details destination
    // -------------------------------------------------------------------------

    @Test
    fun `details deep link contains expected item id`() {
        val itemId = 42
        val deepLink = AppDestination.Details.createDeepLink(itemId)
        val uri = Uri.parse(deepLink)

        assertEquals(AppDestination.DEEP_LINK_SCHEME, uri.scheme)
        assertEquals(AppDestination.Details.BASE, uri.host)
        assertEquals("/$itemId", uri.path)
        assertEquals("${AppDestination.Details.BASE}/$itemId", AppDestination.Details.createRoute(itemId))
    }

    @Test
    fun `details route and deep link work for id zero`() {
        val itemId = 0
        val route = AppDestination.Details.createRoute(itemId)
        val deepLink = AppDestination.Details.createDeepLink(itemId)

        assertEquals("${AppDestination.Details.BASE}/0", route)
        assertTrue(deepLink.endsWith("/0"))
    }

    @Test
    fun `details route and deep link work for negative id`() {
        val itemId = -1
        val route = AppDestination.Details.createRoute(itemId)
        val deepLink = AppDestination.Details.createDeepLink(itemId)

        assertEquals("${AppDestination.Details.BASE}/-1", route)
        assertTrue(deepLink.endsWith("/-1"))
    }

    @Test
    fun `details DEEP_LINK_ROUTE_PATTERN contains arg placeholder`() {
        val pattern = AppDestination.Details.DEEP_LINK_ROUTE_PATTERN
        assertTrue(
            "Pattern must contain itemId placeholder",
            pattern.contains("{${AppDestination.Details.ARG_ITEM_ID}}")
        )
        assertTrue(
            "Pattern must start with deep-link scheme",
            pattern.startsWith("${AppDestination.DEEP_LINK_SCHEME}://")
        )
    }

    // -------------------------------------------------------------------------
    // Static route properties
    // -------------------------------------------------------------------------

    @Test
    fun `List route equals list`() {
        assertEquals("list", AppDestination.List.route)
    }

    @Test
    fun `Camera route equals camera`() {
        assertEquals("camera", AppDestination.Camera.route)
    }
}
