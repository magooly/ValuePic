package com.example.valuefinder.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo

class PdfUriPrintAdapter(
    private val context: Context,
    private val pdfUri: Uri,
    private val documentName: String
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: android.print.PrintAttributes?,
        newAttributes: android.print.PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder(documentName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onWriteCancelled()
            return
        }

        runCatching {
            context.contentResolver.openInputStream(pdfUri)?.use { input ->
                ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to open PDF uri")
        }.fold(
            onSuccess = { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) },
            onFailure = { callback.onWriteFailed(it.message ?: "Print failed") }
        )
    }
}

