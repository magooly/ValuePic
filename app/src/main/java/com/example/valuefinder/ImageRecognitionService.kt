package com.example.valuefinder

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class DetectionResult(
    val label: String,
    val confidence: Float
)

class ImageRecognitionService(private val context: Context) {

    companion object {
        /** Minimum confidence threshold for detected labels (0-1 scale; 55% = 0.55f) */
        private const val MIN_CONFIDENCE_THRESHOLD = 0.55f
        /** Maximum number of top labels to return */
        private const val MAX_LABELS_TO_RETURN = 6
    }

    suspend fun analyzeImage(bitmap: Bitmap): List<DetectionResult> {
        return withContext(Dispatchers.Default) {
            runCatching {
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                labeler.process(inputImage)
                    .await()
                    .filter { label: ImageLabel -> label.confidence >= MIN_CONFIDENCE_THRESHOLD }
                    .sortedByDescending { label: ImageLabel -> label.confidence }
                    .take(MAX_LABELS_TO_RETURN)
                    .map { label: ImageLabel -> DetectionResult(label.text, label.confidence) }
            }.getOrElse { exception ->
                Log.d("ImageRecognitionService", "Image labeling failed; returning empty", exception)
                emptyList()
            }
        }
    }

    suspend fun analyzeImage(filePath: String): List<DetectionResult> {
        val bitmap = PhotoUtils.decodeBitmap(filePath) ?: return emptyList()
        return analyzeImage(bitmap)
    }

    suspend fun detectObjects(bitmap: Bitmap): List<String> {
        return withContext(Dispatchers.Default) {
            analyzeImage(bitmap).map { it.label }
        }
    }
}
