package com.example.valuefinder.ui

import android.net.Uri
import androidx.annotation.VisibleForTesting

sealed interface AppDestination {
    val route: String

    companion object {
        const val DEEP_LINK_SCHEME = "valuepics"
    }

    data object List : AppDestination {
        override val route = "list"
    }

    data object Camera : AppDestination {
        const val BASE = "camera"
        const val ARG_ATTACH_ITEM_ID = "attachItemId"
        const val ARG_INITIAL_SOURCE = "initialSource"
        const val SOURCE_CAMERA = "camera"
        const val SOURCE_GALLERY = "gallery"
        override val route: String = BASE
        val routeWithArgs: String = "$BASE?$ARG_ATTACH_ITEM_ID={$ARG_ATTACH_ITEM_ID}&$ARG_INITIAL_SOURCE={$ARG_INITIAL_SOURCE}"

        fun createRoute(
            attachItemId: Int? = null,
            initialSource: String = SOURCE_CAMERA
        ): String {
            val attachPart = if (attachItemId != null && attachItemId > 0) {
                "$ARG_ATTACH_ITEM_ID=$attachItemId"
            } else {
                "$ARG_ATTACH_ITEM_ID=-1"
            }
            return "$BASE?$attachPart&$ARG_INITIAL_SOURCE=${Uri.encode(initialSource)}"
        }
    }

    data object Valuation : AppDestination {
        const val BASE = "valuation"
        const val ARG_PHOTO_PATH = "photoPath"
        const val ARG_PHOTO_SOURCE = "photoSource"
        override val route: String = "$BASE?$ARG_PHOTO_PATH={$ARG_PHOTO_PATH}&$ARG_PHOTO_SOURCE={$ARG_PHOTO_SOURCE}"
        const val DEEP_LINK_ROUTE_PATTERN: String =
            "$DEEP_LINK_SCHEME://$BASE?$ARG_PHOTO_PATH={$ARG_PHOTO_PATH}&$ARG_PHOTO_SOURCE={$ARG_PHOTO_SOURCE}"

        fun createRoute(photoPath: String, photoSource: String): String {
            return "$BASE?$ARG_PHOTO_PATH=${Uri.encode(photoPath)}&$ARG_PHOTO_SOURCE=${Uri.encode(photoSource)}"
        }

        @VisibleForTesting @Suppress("unused")
        fun createDeepLink(photoPath: String, photoSource: String): String {
            return "$DEEP_LINK_SCHEME://$BASE?$ARG_PHOTO_PATH=${Uri.encode(photoPath)}&$ARG_PHOTO_SOURCE=${Uri.encode(photoSource)}"
        }
    }

    data object Details : AppDestination {
        const val BASE = "details"
        const val ARG_ITEM_ID = "itemId"
        override val route: String = "$BASE/{$ARG_ITEM_ID}"
        const val DEEP_LINK_ROUTE_PATTERN: String =
            "$DEEP_LINK_SCHEME://$BASE/{$ARG_ITEM_ID}"

        fun createRoute(itemId: Int): String = "$BASE/$itemId"

        @VisibleForTesting @Suppress("unused")
        fun createDeepLink(itemId: Int): String = "$DEEP_LINK_SCHEME://$BASE/$itemId"
    }
}

