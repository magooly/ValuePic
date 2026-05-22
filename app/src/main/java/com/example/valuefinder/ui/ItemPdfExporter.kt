/*
 * Copyright (c) 2026 Wally Horsman.
 * All rights reserved.
 */

package com.example.valuefinder.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.valuefinder.MoneyUtils
import com.example.valuefinder.R
import com.example.valuefinder.ValuedItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

private const val TAG_ITEM_PDF_EXPORTER = "ItemPdfExporter"

/**
 * Builds a single-item PDF report and writes it to the app cache directory.
 *
 * @return a content [Uri] pointing at the generated PDF, or `null` if generation fails.
 */
fun buildItemPdfUri(item: ValuedItem, context: Context): Uri? {
    fun sanitizeFileComponent(raw: String): String =
        raw.trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "Item_${item.id}" }

    val entryDateText  = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(item.createdAtMillis))
    val valuedDateText = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(item.dateValued))
    val printedDateText = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())
    val reportDateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(item.createdAtMillis))
    val documentName = "ValuePics_${sanitizeFileComponent(item.itemName)}_Details_$reportDateStamp"
    val fileName = "$documentName.pdf"
    val outFile = File(context.cacheDir, fileName)

    val pageWidth  = 595
    val pageHeight = 842
    val left   = 40f
    val right  = 555f
    val bottom = 800f

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize  = 18f
        typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize  = 13f
        typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val textPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize =  9f }
    val maxTextWidth = right - left

    fun wrapLine(text: String, paint: Paint): List<String> {
        if (text.isBlank()) return listOf("")
        val words = text.split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return listOf("")
        val lines   = mutableListOf<String>()
        var current = words.first()
        for (i in 1 until words.size) {
            val candidate = "$current ${words[i]}"
            if (paint.measureText(candidate) <= maxTextWidth) current = candidate
            else { lines += current; current = words[i] }
        }
        lines += current
        return lines
    }

    fun decodeSampledBitmap(filePath: String, reqWidth: Int, reqHeight: Int): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, bounds)
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            var halfHeight = srcHeight / 2
            var halfWidth = srcWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val options = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize.coerceAtLeast(1)
        }
        return BitmapFactory.decodeFile(filePath, options)
    }

    fun extractWillRecipient(willText: String): String {
        val explicit = willText
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("recipient:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            .orEmpty()
        if (explicit.isNotBlank()) return explicit

        val viaTo = Regex("\\bto\\s+([A-Za-z][A-Za-z .'-]{1,60})", RegexOption.IGNORE_CASE)
            .find(willText)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
        return viaTo
    }

    fun firstOneLineDescription(): String {
        val source = item.shortAiDescription.ifBlank { item.itemDescription }
        val oneLine = source
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .replace(Regex("\\s+"), " ")
        return if (oneLine.length <= 90) oneLine else oneLine.take(87).trimEnd() + "..."
    }

    return runCatching {
        val doc = PdfDocument()
        var pageNum = 1
        var page    = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas  = page.canvas
        var y       = 48f

        fun finishAndNewPage() {
            canvas.drawText(context.getString(R.string.pdf_footer_page, pageNum), left, pageHeight - 24f, footerPaint)
            doc.finishPage(page)
            pageNum += 1
            page   = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            canvas = page.canvas
            y      = 48f
        }

        fun ensureSpace(required: Float) { if (y + required > bottom) finishAndNewPage() }

        fun drawHeading(text: String) {
            ensureSpace(24f)
            canvas.drawText(text, left, y, sectionPaint)
            y += 18f
        }

        fun drawParagraph(text: String) {
            wrapLine(text, textPaint).forEach { line ->
                ensureSpace(16f)
                canvas.drawText(line, left, y, textPaint)
                y += 14f
            }
        }

        // Title block
        wrapLine(context.getString(R.string.pdf_report_title, item.itemName), titlePaint).forEach { line ->
            canvas.drawText(line, left, y, titlePaint)
            y += 22f
        }
        drawParagraph(context.getString(R.string.pdf_label_record_entered, entryDateText))
        drawParagraph(context.getString(R.string.pdf_label_printed, printedDateText))
        y += 12f

        // Photo
        val photoFile   = File(item.photoPath)
        val imageMaxWidth  = right - left
        val imageMaxHeight = 220f
        val photoBitmap = if (photoFile.exists()) {
            decodeSampledBitmap(
                filePath = photoFile.absolutePath,
                reqWidth = imageMaxWidth.toInt().coerceAtLeast(1),
                reqHeight = imageMaxHeight.toInt().coerceAtLeast(1)
            )
        } else null
        if (photoBitmap != null) {
            ensureSpace(imageMaxHeight + 12f)
            val srcW   = photoBitmap.width.toFloat().coerceAtLeast(1f)
            val srcH   = photoBitmap.height.toFloat().coerceAtLeast(1f)
            val scale  = min(imageMaxWidth / srcW, imageMaxHeight / srcH)
            val drawW  = srcW * scale
            val drawH  = srcH * scale
            val drawLeft = left + (imageMaxWidth - drawW) / 2f
            val dest = android.graphics.RectF(drawLeft, y, drawLeft + drawW, y + drawH)
            canvas.drawBitmap(photoBitmap, null, dest, null)
            y = dest.bottom + 12f
            photoBitmap.recycle()
        } else {
            drawParagraph(context.getString(R.string.common_image_unavailable))
            y += 6f
        }

        // Item details
        drawHeading(context.getString(R.string.pdf_section_item))
        drawParagraph(context.getString(R.string.pdf_label_name, item.itemName))
        if (item.collectionName.isNotBlank()) drawParagraph(context.getString(R.string.pdf_label_collection, item.collectionName))
        if (item.tags.isNotBlank())            drawParagraph(context.getString(R.string.pdf_label_tags, item.tags))
        if (item.itemDescription.isNotBlank()) drawParagraph(context.getString(R.string.pdf_label_description, item.itemDescription))

        y += 8f
        drawHeading(context.getString(R.string.pdf_section_valuation))
        item.estimatedValue?.let { drawParagraph(context.getString(R.string.pdf_label_value_at_entry, MoneyUtils.formatAud(it))) }
        drawParagraph(context.getString(R.string.pdf_label_valuation_timestamp, valuedDateText))
        if (item.valueSource.isNotBlank()) drawParagraph(context.getString(R.string.pdf_label_value_source, item.valueSource))
        if (item.sourceUrl.isNotBlank())   drawParagraph(context.getString(R.string.pdf_label_source_url, item.sourceUrl))

        if (item.shortAiDescription.isNotBlank() || item.fullWebDescription.isNotBlank()) {
            y += 8f
            drawHeading(context.getString(R.string.pdf_section_web_notes))
            if (item.shortAiDescription.isNotBlank()) drawParagraph(context.getString(R.string.pdf_label_ai_summary, item.shortAiDescription))
            if (item.fullWebDescription.isNotBlank())  drawParagraph(item.fullWebDescription)
        }

        if (item.notes.isNotBlank()) {
            y += 8f
            drawHeading(context.getString(R.string.pdf_section_personal_notes))
            drawParagraph(item.notes)
        }

        val trimmedWill = item.willInstructions.trim()
        if (trimmedWill.isNotBlank()) {
            y += 8f
            drawHeading(context.getString(R.string.pdf_section_will))
            val recipient = extractWillRecipient(trimmedWill).ifBlank {
                context.getString(R.string.pdf_will_recipient_unknown)
            }
            val valueText = item.estimatedValue?.let { MoneyUtils.formatAud(it) }
                ?: context.getString(R.string.pdf_will_value_unknown)
            val descriptionText = firstOneLineDescription().ifBlank {
                context.getString(R.string.pdf_will_description_unknown)
            }

            drawParagraph(context.getString(R.string.pdf_label_will_item_column, item.itemName))
            drawParagraph(context.getString(R.string.pdf_label_will_value_column, valueText))
            drawParagraph(context.getString(R.string.pdf_label_will_description_column, descriptionText))
            drawParagraph(context.getString(R.string.pdf_label_will_beneficiary_column, recipient))
        }

        canvas.drawText(context.getString(R.string.pdf_footer_page, pageNum), left, pageHeight - 24f, footerPaint)
        doc.finishPage(page)
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }.onFailure { error ->
        Log.w(TAG_ITEM_PDF_EXPORTER, "Failed to build PDF for itemId=${item.id}", error)
    }.getOrNull()
}
