package com.example.valuefinder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Log
import java.io.File
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result type for bitmap loading operations.
 */
sealed class BitmapLoadResult {
    data class Success(val bitmap: Bitmap) : BitmapLoadResult()
    data class FileNotFound(val path: String) : BitmapLoadResult()
    data class CorruptedOrUnreadable(val path: String, val reason: String) : BitmapLoadResult()
    data class OutOfMemory(val path: String) : BitmapLoadResult()
}

/**
 * Configuration for PDF layout positioning and dimensions.
 * Centralizes all positioning values to prevent inconsistencies.
 */
data class PdfLayoutConfig(
    val pageWidth: Int = 595,
    val pageHeight: Int = 842,
    val topMargin: Float = 48f,
    val bottomMargin: Float = 42f,
    val leftMargin: Float = 40f,
    val rightMargin: Float = 40f,
    val imageSize: Float = 44f,
    val imageSpacing: Float = 10f,
    val lineHeight: Float = 16f,
    val sectionSpacing: Float = 24f,
    val includeThumbnails: Boolean = false
) {
    // Computed properties for cleanliness
    val contentWidth: Float get() = pageWidth - leftMargin - rightMargin
    val imageLeftX: Float get() = leftMargin
    val textLeftX: Float get() = if (includeThumbnails) imageLeftX + imageSize + imageSpacing else leftMargin
    val descriptionX: Float get() = if (includeThumbnails) leftMargin + 150f else leftMargin + 200f
    val valueRightX: Float get() = pageWidth - rightMargin
    val bottomLimit: Float get() = pageHeight - bottomMargin
    val minColumnWidth: Float = 80f

    fun validateLayout() {
        val textToDesc = descriptionX - textLeftX
        val descToValue = valueRightX - descriptionX - 60f

        require(textToDesc >= minColumnWidth) {
            "Item column too narrow: $textToDesc < $minColumnWidth"
        }
        require(descToValue >= minColumnWidth) {
            "Description column too narrow: $descToValue < $minColumnWidth"
        }
    }
}

/**
 * Typography configuration for PDF rendering.
 */
data class PdfTypographyConfig(
    val titleSize: Float = 18f,
    val subtitleSize: Float = 12f,
    val bodySize: Float = 10f,
    val titleLeading: Float = 22f,
    val bodyLeading: Float = 14f,
    val sectionBlockSpacing: Float = 24f,
    val rowLeading: Float = 16f,
    val placeholderIndicatorSize: Float = 16f
)

private fun formatAudWithBillsSuffix(item: ValuedItem, amount: Double): String {
    val base = MoneyUtils.formatAud(amount)
    val period = resolveBillsEnteredPeriod(item.collectionName, item.billsEnteredPeriod) ?: return base
    val suffix = when (period) {
        BillsPeriod.WEEKLY -> " / Week"
        BillsPeriod.MONTHLY -> " / Month"
        BillsPeriod.YEARLY -> " / Year"
    }
    return base + suffix
}

/**
 * Builds PDF collection summary reports in a pure data-layer style.
 * Encapsulates all canvas drawing logic away from the repository.
 */
internal class PdfReportBuilder {
    fun buildPdf(
        items: List<ValuedItem>,
        byCollection: Map<String, List<ValuedItem>>,
        collectionTotals: List<Pair<String, Double>>,
        metadataLines: List<String> = emptyList(),
        reportTitleOverride: String? = null,
        useWillInstructions: Boolean = false,
        includeThumbnails: Boolean = false
    ): ByteArray {
        val document = PdfDocument()
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val reportTitle = reportTitleOverride?.trim().takeUnless { it.isNullOrBlank() }
            ?: "ValuePics Collection Summary"
        val normalized = items.map { item ->
            item to item.collectionName.trim().ifBlank { "Uncategorized" }
        }
        val grandTotal = collectionTotals.sumOf { it.second }

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        val layoutConfig = PdfLayoutConfig(includeThumbnails = includeThumbnails)
        val typographyConfig = PdfTypographyConfig()
        layoutConfig.validateLayout()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = typographyConfig.titleSize
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = typographyConfig.subtitleSize
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = typographyConfig.bodySize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val imageBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            alpha = 120
        }

        fun drawRightText(text: String, rightX: Float, yPos: Float, paint: Paint) {
            val x = rightX - paint.measureText(text)
            canvas?.drawText(text, x, yPos, paint)
        }

        /** Safe draw helper — no-ops silently if canvas is not yet initialised. */
        fun draw(block: Canvas.() -> Unit) { canvas?.block() }

        fun startNewPageIfNeeded() {
            if (page != null) return
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(layoutConfig.pageWidth, layoutConfig.pageHeight, pageNumber).create())
            canvas = page?.canvas
            y = layoutConfig.topMargin
            reportTitle
                .lineSequence()
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }
                .forEach { line ->
                    canvas?.drawText(line, 40f, y, titlePaint)
                    y += typographyConfig.titleLeading
                }
        }

        fun ensureSpace(requiredHeight: Float) {
            startNewPageIfNeeded()
            if (y + requiredHeight <= layoutConfig.bottomLimit) return
            page?.let { document.finishPage(it) }
            page = null
            canvas = null
            startNewPageIfNeeded()
        }

        fun ellipsizeToWidth(text: String, maxWidth: Float, paint: Paint): String {
            val clean = text.ifBlank { "-" }
            if (paint.measureText(clean) <= maxWidth) return clean
            val ellipsis = "..."
            var end = clean.length
            while (end > 0) {
                val candidate = clean.substring(0, end) + ellipsis
                if (paint.measureText(candidate) <= maxWidth) return candidate
                end -= 1
            }
            return ellipsis
        }

        fun drawReportMetadata() {
            ensureSpace(64f + (metadataLines.size * typographyConfig.bodyLeading))
            draw { drawText("Generated: $generatedAt", 40f, y, textPaint) }
            y += typographyConfig.bodyLeading
            draw { drawText("Collections: ${collectionTotals.size}", 40f, y, textPaint) }
            y += typographyConfig.bodyLeading
            draw { drawText("Items: ${normalized.size}", 40f, y, textPaint) }
            y += typographyConfig.bodyLeading
            draw { drawText("Grand total: ${MoneyUtils.formatAud(grandTotal)}", 40f, y, textPaint) }
            metadataLines.forEach { line ->
                y += typographyConfig.bodyLeading
                draw { drawText(line, 40f, y, textPaint) }
            }
            y += typographyConfig.rowLeading + 2f
        }

        fun drawCollectionTotalsOverview() {
            ensureSpace(64f)
            draw { drawText("Collection Totals", layoutConfig.leftMargin, y, subtitlePaint) }
            y += 18f
            draw { drawText("Collection", layoutConfig.leftMargin, y, subtitlePaint) }
            drawRightText("Total Value", layoutConfig.valueRightX, y, subtitlePaint)
            y += 12f
            draw { drawLine(layoutConfig.leftMargin, y, 555f, y, textPaint) }
            y += 16f

            if (collectionTotals.isEmpty()) {
                ensureSpace(20f)
                draw { drawText("No collections yet.", layoutConfig.leftMargin, y, textPaint) }
                y += 16f
            }

            collectionTotals.forEach { (collectionName, collectionTotal) ->
                ensureSpace(18f)
                val collectionLabel = ellipsizeToWidth(collectionName, 360f, textPaint)
                draw { drawText(collectionLabel, layoutConfig.leftMargin, y, textPaint) }
                drawRightText(MoneyUtils.formatAud(collectionTotal), layoutConfig.valueRightX, y, textPaint)
                y += 16f
            }

            ensureSpace(24f)
            y += 2f
            draw { drawLine(layoutConfig.leftMargin, y, 555f, y, textPaint) }
            y += 16f
            draw { drawText("Grand Total", layoutConfig.leftMargin, y, subtitlePaint) }
            drawRightText(MoneyUtils.formatAud(grandTotal), layoutConfig.valueRightX, y, subtitlePaint)
            y += 20f
            y += typographyConfig.sectionBlockSpacing
        }

        fun drawSection(title: String, rows: List<ValuedItem>, showCollectionColumn: Boolean) {
            val minimumSectionStartSpace = if (includeThumbnails) 124f else 94f
            ensureSpace(minimumSectionStartSpace)
            draw { drawText("Collection: $title", 40f, y, subtitlePaint) }
            y += 18f
            val totalValue = rows.mapNotNull { it.estimatedValue }.sum()
            draw { drawText("Total value: ${MoneyUtils.formatAud(totalValue)}", 40f, y, subtitlePaint) }
            y += 18f

            if (showCollectionColumn) {
                if (includeThumbnails) {
                    draw { drawText("Photo", layoutConfig.imageLeftX, y, subtitlePaint) }
                    draw { drawText("Collection", layoutConfig.textLeftX, y, subtitlePaint) }
                    draw { drawText("Description", layoutConfig.descriptionX, y, subtitlePaint) }
                } else {
                    draw { drawText("Collection", 40f, y, subtitlePaint) }
                    draw { drawText("Item", 145f, y, subtitlePaint) }
                    draw { drawText("Description", 300f, y, subtitlePaint) }
                }
                drawRightText("Value", layoutConfig.valueRightX, y, subtitlePaint)
            } else {
                if (includeThumbnails) {
                    draw { drawText("Photo", layoutConfig.imageLeftX, y, subtitlePaint) }
                    draw { drawText("Item", layoutConfig.textLeftX, y, subtitlePaint) }
                    draw { drawText("Description", layoutConfig.descriptionX, y, subtitlePaint) }
                } else {
                    draw { drawText("Item", 40f, y, subtitlePaint) }
                    draw { drawText("Description", 245f, y, subtitlePaint) }
                }
                drawRightText("Value", layoutConfig.valueRightX, y, subtitlePaint)
            }
            y += 12f
            draw { drawLine(40f, y, 555f, y, textPaint) }
            y += 16f

            if (rows.isEmpty()) {
                ensureSpace(20f)
                draw { drawText("No items in this collection.", 40f, y, textPaint) }
                y += 16f
            }

            rows.forEach { item ->
                val rowHeight = if (includeThumbnails) layoutConfig.imageSize + 4f else typographyConfig.rowLeading + 2f
                ensureSpace(rowHeight)
                val description = if (useWillInstructions) {
                    item.willInstructions
                        .ifBlank { item.shortAiDescription.ifBlank { item.itemDescription } }
                } else {
                    item.shortAiDescription
                        .ifBlank { item.itemDescription }
                }
                    .replace("\n", " ")
                    .trim()
                    .ifBlank { "-" }
                if (includeThumbnails) {
                    val imageTop = y - layoutConfig.imageSize + 8f
                    val imageRect = RectF(layoutConfig.imageLeftX, imageTop, layoutConfig.imageLeftX + layoutConfig.imageSize, imageTop + layoutConfig.imageSize)

                    when (val result = decodeThumbnail(item.photoPath, layoutConfig.imageSize.toInt())) {
                        is BitmapLoadResult.Success -> {
                            draw { drawBitmap(result.bitmap, null, imageRect, null) }
                            result.bitmap.recycle()
                            draw { drawRect(imageRect, imageBorderPaint) }
                        }
                        is BitmapLoadResult.FileNotFound,
                        is BitmapLoadResult.CorruptedOrUnreadable,
                        is BitmapLoadResult.OutOfMemory -> {
                            // Draw placeholder with error indicator
                            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = 0xFFE0E0E0.toInt()  // Light gray
                            }
                            draw { drawRect(imageRect, bgPaint) }
                            draw { drawRect(imageRect, imageBorderPaint) }

                            // Draw indicator based on error type
                            val textPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                textSize = typographyConfig.placeholderIndicatorSize
                                color = 0xFF999999.toInt()
                                textAlign = Paint.Align.CENTER
                            }
                            val indicator = when (result) {
                                is BitmapLoadResult.FileNotFound -> "?"
                                is BitmapLoadResult.OutOfMemory -> "⚠"
                                else -> "!"
                            }
                            draw {
                                drawText(
                                    indicator,
                                    imageRect.centerX(),
                                    imageRect.centerY() + 5f,
                                    textPaint2
                                )
                            }
                        }
                    }
                }

                if (showCollectionColumn) {
                    val collectionLabel = item.collectionName.trim().ifBlank { "Uncategorized" }
                    if (includeThumbnails) {
                        draw { drawText(ellipsizeToWidth(collectionLabel, 205f, textPaint), layoutConfig.textLeftX, y, textPaint) }
                        draw { drawText(ellipsizeToWidth(description, 205f, textPaint), layoutConfig.descriptionX, y, textPaint) }
                    } else {
                        draw { drawText(ellipsizeToWidth(collectionLabel, 95f, textPaint), 40f, y, textPaint) }
                        draw { drawText(ellipsizeToWidth(item.itemName, 145f, textPaint), 145f, y, textPaint) }
                        draw { drawText(ellipsizeToWidth(description, 205f, textPaint), 300f, y, textPaint) }
                    }
                } else {
                    if (includeThumbnails) {
                        draw { drawText(ellipsizeToWidth(item.itemName, 205f, textPaint), layoutConfig.textLeftX, y, textPaint) }
                        draw { drawText(ellipsizeToWidth(description, 205f, textPaint), layoutConfig.descriptionX, y, textPaint) }
                    } else {
                        draw { drawText(ellipsizeToWidth(item.itemName, 195f, textPaint), 40f, y, textPaint) }
                        draw { drawText(ellipsizeToWidth(description, 260f, textPaint), 245f, y, textPaint) }
                    }
                }
                val valueLabel = item.estimatedValue?.let { formatAudWithBillsSuffix(item, it) } ?: "—"
                drawRightText(valueLabel, layoutConfig.valueRightX, y, textPaint)
                y += if (includeThumbnails) rowHeight else typographyConfig.rowLeading
            }
            y += typographyConfig.sectionBlockSpacing
        }

        drawReportMetadata()
        drawCollectionTotalsOverview()

        collectionTotals.forEach { (collectionName, _) ->
            drawSection(
                title = collectionName,
                rows = byCollection[collectionName].orEmpty(),
                showCollectionColumn = false
            )
        }

        page?.let { document.finishPage(it) }

        val bytes = ByteArrayOutputStream().use { out ->
            document.writeTo(out)
            out.toByteArray()
        }
        document.close()

        return bytes
    }

    private fun decodeThumbnail(path: String, targetPx: Int): BitmapLoadResult {
        val file = File(path.trim())

        // Check file exists
        if (!file.exists()) {
            Log.w("PdfBuilder", "Thumbnail file not found: $path")
            return BitmapLoadResult.FileNotFound(path)
        }
        if (!file.isFile) {
            Log.w("PdfBuilder", "Thumbnail path is not a file: $path")
            return BitmapLoadResult.FileNotFound(path)
        }

        try {
            // Decode bounds
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.w("PdfBuilder", "Thumbnail has invalid dimensions: ${bounds.outWidth}x${bounds.outHeight}")
                return BitmapLoadResult.CorruptedOrUnreadable(path, "Invalid dimensions")
            }

            // Calculate sample size
            var inSampleSize = 1
            val halfWidth = bounds.outWidth / 2
            val halfHeight = bounds.outHeight / 2
            while ((halfWidth / inSampleSize) >= targetPx && (halfHeight / inSampleSize) >= targetPx) {
                inSampleSize *= 2
            }

            // Decode with downsampling
            val options = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                ?: return BitmapLoadResult.CorruptedOrUnreadable(path, "Failed to decode")

            Log.d("PdfBuilder", "Loaded thumbnail: $path (${bitmap.width}x${bitmap.height})")
            return BitmapLoadResult.Success(bitmap)

        } catch (e: OutOfMemoryError) {
            Log.e("PdfBuilder", "OOM loading thumbnail: $path", e)
            return BitmapLoadResult.OutOfMemory(path)
        } catch (e: Exception) {
            Log.e("PdfBuilder", "Error loading thumbnail: $path", e)
            return BitmapLoadResult.CorruptedOrUnreadable(path, e.message ?: "Unknown error")
        }
    }
}
