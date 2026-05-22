package com.example.valuefinder.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.valuefinder.R
import java.io.File
import java.net.URLEncoder

enum class LookupProvider {
    BING,
    GOOGLE
}

fun openExternalUrl(context: Context, rawUrl: String): Boolean {
    val clean = rawUrl.trim()
    if (clean.isBlank()) return false
    val resolved = if (clean.startsWith("http://") || clean.startsWith("https://")) clean else "https://$clean"
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resolved)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.isSuccess
}

fun openManualLookupPage(
    context: Context,
    photoPath: String,
    provider: LookupProvider,
    itemName: String,
    itemDescription: String
): Boolean {
    val imageUri = runCatching {
        val imageFile = File(photoPath)
        if (!imageFile.exists()) return@runCatching null
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    }.getOrNull()

    if (imageUri != null) {
        fun buildShareIntent(packageName: String? = null): Intent {
            return Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                clipData = ClipData.newUri(context.contentResolver, "lookup_image", imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!packageName.isNullOrBlank()) setPackage(packageName)
            }
        }

        val directPackages = when (provider) {
            LookupProvider.BING -> listOf("com.microsoft.bing")
            LookupProvider.GOOGLE -> listOf(
                "com.google.ar.lens",
                "com.google.android.googlequicksearchbox"
            )
        }

        val launchedDirect = directPackages.any { pkg ->
            runCatching { context.startActivity(buildShareIntent(pkg)) }.isSuccess
        }
        if (launchedDirect) return true

        val launchedChooser = runCatching {
            val chooser = Intent.createChooser(
                buildShareIntent(),
                context.getString(R.string.lookup_share_image_chooser_title)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }.isSuccess
        if (launchedChooser) return true
    }

    val query = listOf(itemName.trim(), itemDescription.trim())
        .filter { it.isNotBlank() }
        .joinToString(" ")
    val target = when (provider) {
        LookupProvider.BING -> {
            if (query.isBlank()) {
                "https://www.bing.com/visualsearch"
            } else {
                val encoded = URLEncoder.encode(query, "UTF-8")
                "https://www.bing.com/images/search?q=$encoded"
            }
        }
        LookupProvider.GOOGLE -> {
            if (query.isBlank()) {
                "https://www.google.com/imghp"
            } else {
                val encoded = URLEncoder.encode(query, "UTF-8")
                "https://www.google.com/search?tbm=isch&q=$encoded"
            }
        }
    }
    return openExternalUrl(context, target)
}

