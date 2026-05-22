package com.example.valuefinder.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object HowToHelper {

    private const val CACHE_NAME = "ValuePics_HowToUse.pdf"
    private const val CACHE_META_NAME = "ValuePics_HowToUse.version"
    private const val GUIDE_ASSET_NAME = "how_to.md"
    private const val DEFAULT_TITLE = "ValuePics - How To Guide"
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT = 40f
    private const val TOP = 48f
    private const val BOTTOM = 800f
    private const val RIGHT = 555f

    /**
     * Generates a how-to PDF in app cache and returns a FileProvider URI that can be shared with
     * external apps or the print manager.
     */
    fun getHowToPdfUri(context: Context): Uri {
        val cacheFile = File(context.cacheDir, CACHE_NAME)
        val metaFile = File(context.cacheDir, CACHE_META_NAME)
        val markdown = loadGuideMarkdown(context)
        val currentVersion = guideToken(context, markdown)
        val cachedVersion = runCatching { metaFile.readText(Charsets.UTF_8).trim() }.getOrDefault("")

        if (!cacheFile.exists() || cachedVersion != currentVersion) {
            createHowToPdf(cacheFile, markdown)
            runCatching { metaFile.writeText(currentVersion, Charsets.UTF_8) }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
    }

    private fun loadGuideMarkdown(context: Context): String {
        return context.assets.open(GUIDE_ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun guideToken(context: Context, markdown: String): String {
        return "${appVersionToken(context)}-${sha256(markdown).take(16)}"
    }

    private fun appVersionToken(context: Context): String {
        return runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            "${packageInfo.versionName}-$code"
        }.getOrDefault("unknown")
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun createHowToPdf(outFile: File, markdown: String) {
        val lines = markdown.lines()
        val title = lines.firstOrNull { it.trimStart().startsWith("# ") }
            ?.trimStart()
            ?.removePrefix("# ")
            ?.trim()
            .orEmpty()
            .ifBlank { DEFAULT_TITLE }

        val document = PdfDocument()
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = TOP

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subheadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
        }

        fun finishPage() {
            canvas?.drawText("Page $pageNumber", LEFT, PAGE_HEIGHT - 24f, footerPaint)
            page?.let { document.finishPage(it) }
            page = null
            canvas = null
        }

        fun newPage() {
            if (page != null) {
                finishPage()
            }
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page!!.canvas
            y = TOP
            canvas!!.drawText(title, LEFT, y, titlePaint)
            y += 24f
        }

        fun ensureSpace(required: Float) {
            if (page == null || y + required > BOTTOM) {
                newPage()
            }
        }

        fun wrapText(text: String, paint: Paint, availableWidth: Float): List<String> {
            if (text.isBlank()) return listOf("")
            val words = text.split(' ').filter { it.isNotBlank() }
            if (words.isEmpty()) return listOf("")

            val lines = mutableListOf<String>()
            var current = words.first()
            for (index in 1 until words.size) {
                val candidate = "$current ${words[index]}"
                if (paint.measureText(candidate) <= availableWidth) {
                    current = candidate
                } else {
                    lines += current
                    current = words[index]
                }
            }
            lines += current
            return lines
        }

        fun drawWrapped(text: String, paint: Paint, indent: Float = 0f) {
            val availableWidth = RIGHT - LEFT - indent
            wrapText(text, paint, availableWidth).forEach { line ->
                ensureSpace(16f)
                canvas!!.drawText(line, LEFT + indent, y, paint)
                y += 14f
            }
        }

        fun drawHeading(text: String) {
            ensureSpace(22f)
            canvas!!.drawText(text, LEFT, y, headingPaint)
            y += 18f
        }

        fun drawSubheading(text: String) {
            ensureSpace(18f)
            canvas!!.drawText(text, LEFT, y, subheadingPaint)
            y += 16f
        }

        fun drawBullet(text: String) {
            drawWrapped("• $text", textPaint, indent = 8f)
        }

        newPage()

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd()
            val trimmed = line.trim()

            if (index == 0 && trimmed.startsWith("# ")) return@forEachIndexed
            if (trimmed.isBlank()) {
                y += 6f
                return@forEachIndexed
            }

            when {
                trimmed == "---" -> y += 8f
                trimmed.startsWith("## ") -> drawHeading(trimmed.removePrefix("## ").trim())
                trimmed.startsWith("### ") -> drawSubheading(trimmed.removePrefix("### ").trim())
                trimmed.startsWith("- ") -> drawBullet(trimmed.removePrefix("- ").trim())
                trimmed.startsWith("* ") -> drawBullet(trimmed.removePrefix("* ").trim())
                Regex("^\\d+[.)]\\s+.*").matches(trimmed) -> drawWrapped(trimmed, textPaint)
                else -> drawWrapped(trimmed, textPaint)
            }
        }

        finishPage()
        FileOutputStream(outFile).use { output ->
            document.writeTo(output)
        }
        document.close()
    }
}

