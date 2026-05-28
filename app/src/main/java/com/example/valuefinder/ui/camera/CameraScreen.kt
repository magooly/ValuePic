package com.example.valuefinder.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.valuefinder.PhotoUtils
import com.example.valuefinder.R
import com.example.valuefinder.ui.ThemeMode
import com.example.valuefinder.ui.AppDestination
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

private enum class ClipHandle {
    MOVE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

private const val MAX_GALLERY_BATCH_ITEMS = 20

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CameraScreen(
    onPhotoCapture: (String, String, Boolean) -> Unit,
    photoTargetSizeKb: Int,
    onPhotoTargetSizeChange: (Int) -> Unit,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    appTier: com.example.valuefinder.AppTier = com.example.valuefinder.AppTier.PERSONAL,
    onAppTierSelected: (com.example.valuefinder.AppTier) -> Unit = {},
    supportsMultiGalleryImport: Boolean,
    initialSource: String,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val reviewScrollState = rememberScrollState()
    var currentPhotoPath by remember { mutableStateOf<String?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var reviewPhotoPath by remember { mutableStateOf<String?>(null) }
    var reviewPhotoSource by remember { mutableStateOf("camera") }
    var currentGalleryUri by remember { mutableStateOf<Uri?>(null) }
    var pendingGalleryUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var galleryBatchTotal by remember { mutableStateOf(0) }
    var galleryBatchImported by remember { mutableStateOf(0) }
    var galleryBatchSkipped by remember { mutableStateOf(0) }
    var galleryBatchFailed by remember { mutableStateOf(0) }
    // Holds the original full-resolution camera file before it is compressed,
    // so Gallery save can preserve the unmodified shot. Cleaned up on dismiss/confirm.
    var originalPhotoTempPath by remember { mutableStateOf<String?>(null) }
    var doNotIncludeInGallery by remember { mutableStateOf(false) }
    var pendingGallerySavePath by remember { mutableStateOf<String?>(null) }
    var clipLeft by remember { mutableStateOf(0f) }
    var clipRight by remember { mutableStateOf(0f) }
    var clipTop by remember { mutableStateOf(0f) }
    var clipBottom by remember { mutableStateOf(0f) }
    var previewWidthPx by remember { mutableStateOf(1f) }
    var previewHeightPx by remember { mutableStateOf(1f) }
    var reviewImageWidthPx by remember { mutableStateOf(1f) }
    var reviewImageHeightPx by remember { mutableStateOf(1f) }
    var reviewImageVersion by remember { mutableIntStateOf(0) }
    var hasAutoLaunchedInitialSource by remember { mutableStateOf(false) }
    var activeClipHandle by remember { mutableStateOf(ClipHandle.MOVE) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var handleInsetPreset by remember {
        mutableStateOf(PhotoUtils.getCropHandleInsetPreset(context))
    }

    // Gallery-save permission (only required on Android 9 / API 28 and below).
    var hasGalleryPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasGalleryPermission = granted
        if (granted) {
            val path = pendingGallerySavePath ?: originalPhotoTempPath ?: reviewPhotoPath
            if (path != null) {
                val ok = PhotoUtils.saveToGallery(context, path)
                Toast.makeText(
                    context,
                    if (ok) context.getString(R.string.camera_saved_to_gallery)
                    else context.getString(R.string.camera_save_gallery_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
            pendingGallerySavePath = null
        } else {
            pendingGallerySavePath = null
            Toast.makeText(context, context.getString(R.string.camera_save_gallery_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(reviewPhotoPath, reviewImageVersion) {
        val path = reviewPhotoPath ?: return@LaunchedEffect
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth > 0 && opts.outHeight > 0) {
            reviewImageWidthPx = opts.outWidth.toFloat()
            reviewImageHeightPx = opts.outHeight.toFloat()
        } else {
            reviewImageWidthPx = 1f
            reviewImageHeightPx = 1f
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val path = currentPhotoPath
        if (success && path != null) {
            // Copy the original BEFORE optimizing so "Save to Gallery" gets the full-res shot.
            val tempFile = File(context.cacheDir, "gallery_original_${System.currentTimeMillis()}.jpg")
            runCatching { File(path).copyTo(tempFile, overwrite = true) }
            originalPhotoTempPath?.let { runCatching { File(it).delete() } } // clean up any old temp
            originalPhotoTempPath = if (tempFile.exists()) tempFile.absolutePath else null
            val optimized = PhotoUtils.optimizeExistingImageFile(context, path)
            if (!optimized) {
                Toast.makeText(context, context.getString(R.string.camera_photo_optimize_failed), Toast.LENGTH_SHORT).show()
            }
            reviewPhotoPath = path
            reviewPhotoSource = "camera"
            doNotIncludeInGallery = false
            clipLeft = 0f
            clipRight = 0f
            clipTop = 0f
            clipBottom = 0f
        }
    }

    fun clearGalleryBatchState() {
        currentGalleryUri = null
        pendingGalleryUris = emptyList()
        galleryBatchTotal = 0
        galleryBatchImported = 0
        galleryBatchSkipped = 0
        galleryBatchFailed = 0
    }

    fun loadGalleryUriForReview(uri: Uri): Boolean {
        val localPath = PhotoUtils.copyUriToImageFile(context, uri) ?: return false
        currentGalleryUri = uri
        reviewPhotoPath = localPath
        reviewPhotoSource = "gallery"
        doNotIncludeInGallery = true
        clipLeft = 0f
        clipRight = 0f
        clipTop = 0f
        clipBottom = 0f
        return true
    }

    fun processNextQueuedGalleryUri(orCancelWhenDone: Boolean) {
        while (pendingGalleryUris.isNotEmpty()) {
            val nextUri = pendingGalleryUris.first()
            pendingGalleryUris = pendingGalleryUris.drop(1)
            if (loadGalleryUriForReview(nextUri)) {
                return
            }
            galleryBatchFailed += 1
        }

        if (galleryBatchTotal > 0) {
            val completed = galleryBatchImported + galleryBatchSkipped + galleryBatchFailed
            val summary = context.getString(
                R.string.camera_gallery_batch_complete,
                galleryBatchImported,
                galleryBatchSkipped,
                galleryBatchFailed,
                completed,
                galleryBatchTotal
            )
            Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
        }

        clearGalleryBatchState()
        if (orCancelWhenDone) {
            onCancel()
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) {
            clearGalleryBatchState()
            reviewPhotoPath = null
            currentGalleryUri = null
            return@rememberLauncherForActivityResult
        }
        clearGalleryBatchState()
        if (!loadGalleryUriForReview(uri)) {
            Toast.makeText(context, context.getString(R.string.camera_gallery_import_failed), Toast.LENGTH_LONG).show()
            reviewPhotoPath = null
            currentGalleryUri = null
        }
    }

    val multiGalleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_GALLERY_BATCH_ITEMS)
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) {
            clearGalleryBatchState()
            reviewPhotoPath = null
            currentGalleryUri = null
            return@rememberLauncherForActivityResult
        }
        val distinct = uris.distinct()
        val limited = distinct.take(MAX_GALLERY_BATCH_ITEMS)
        if (distinct.size > MAX_GALLERY_BATCH_ITEMS) {
            Toast.makeText(
                context,
                context.getString(R.string.camera_gallery_batch_limited, MAX_GALLERY_BATCH_ITEMS),
                Toast.LENGTH_LONG
            ).show()
        }
        clearGalleryBatchState()
        galleryBatchTotal = limited.size
        pendingGalleryUris = limited
        processNextQueuedGalleryUri(orCancelWhenDone = true)
    }

    val legacyMultiGalleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) {
            clearGalleryBatchState()
            reviewPhotoPath = null
            currentGalleryUri = null
            return@rememberLauncherForActivityResult
        }
        val distinct = uris.distinct()
        val limited = distinct.take(MAX_GALLERY_BATCH_ITEMS)
        if (distinct.size > MAX_GALLERY_BATCH_ITEMS) {
            Toast.makeText(
                context,
                context.getString(R.string.camera_gallery_batch_limited, MAX_GALLERY_BATCH_ITEMS),
                Toast.LENGTH_LONG
            ).show()
        }
        clearGalleryBatchState()
        galleryBatchTotal = limited.size
        pendingGalleryUris = limited
        processNextQueuedGalleryUri(orCancelWhenDone = true)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            val file = PhotoUtils.createImageFile(context)
            currentPhotoPath = file.absolutePath
            takePictureLauncher.launch(PhotoUtils.getPhotoUri(context, file))
        }
    }

    fun launchCamera() {
        if (hasCameraPermission) {
            val file = PhotoUtils.createImageFile(context)
            currentPhotoPath = file.absolutePath
            takePictureLauncher.launch(PhotoUtils.getPhotoUri(context, file))
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchGallery() {
        if (supportsMultiGalleryImport) {
            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                multiGalleryPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } else {
                legacyMultiGalleryPickerLauncher.launch("image/*")
            }
        } else {
            galleryPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    LaunchedEffect(initialSource, reviewPhotoPath, hasAutoLaunchedInitialSource) {
        if (hasAutoLaunchedInitialSource || reviewPhotoPath != null) return@LaunchedEffect
        hasAutoLaunchedInitialSource = true
        if (initialSource == AppDestination.Camera.SOURCE_GALLERY) {
            launchGallery()
        }
    }

    fun retryReviewCapture() {
        originalPhotoTempPath?.let { runCatching { File(it).delete() } }
        originalPhotoTempPath = null
        pendingGallerySavePath = null
        reviewPhotoPath = null
        if (reviewPhotoSource == "camera") {
            launchCamera()
            return
        }

        val uri = currentGalleryUri
        if (uri != null && loadGalleryUriForReview(uri)) {
            return
        }
        if (galleryBatchTotal > 0) {
            galleryBatchFailed += 1
            processNextQueuedGalleryUri(orCancelWhenDone = true)
            return
        }
        launchGallery()
    }

    fun skipCurrentGalleryItem() {
        val path = reviewPhotoPath
        if (path != null && reviewPhotoSource == "gallery") {
            runCatching { File(path).delete() }
        }
        reviewPhotoPath = null
        currentGalleryUri = null
        originalPhotoTempPath?.let { runCatching { File(it).delete() } }
        originalPhotoTempPath = null
        pendingGallerySavePath = null
        galleryBatchSkipped += 1
        processNextQueuedGalleryUri(orCancelWhenDone = true)
    }

    fun cancelGalleryBatch() {
        val path = reviewPhotoPath
        if (path != null && reviewPhotoSource == "gallery") {
            runCatching { File(path).delete() }
        }
        reviewPhotoPath = null
        currentGalleryUri = null
        clearGalleryBatchState()
        onCancel()
    }

    fun rotateReviewPhoto(clockwise: Boolean) {
        val path = reviewPhotoPath ?: return
        val rotated = PhotoUtils.rotatePhotoFile(
            context = context,
            photoPath = path,
            degrees = if (clockwise) 90 else -90
        )
        if (!rotated) {
            Toast.makeText(context, context.getString(R.string.camera_rotate_failed), Toast.LENGTH_SHORT).show()
            return
        }

        // Rotation changes the preview orientation and dimensions, so reset the clip box.
        clipLeft = 0f
        clipRight = 0f
        clipTop = 0f
        clipBottom = 0f
        reviewImageVersion += 1
    }

    fun confirmReviewCapture(keepCameraOpenAfterConfirm: Boolean = false) {
        val finalPath = reviewPhotoPath ?: return
        val hasClipAdjustment = clipLeft > 0f || clipRight > 0f || clipTop > 0f || clipBottom > 0f
        if (hasClipAdjustment) {
            // Translate selection from preview-fit coordinates back to source image ratios.
            val previewW = previewWidthPx.coerceAtLeast(1f)
            val previewH = previewHeightPx.coerceAtLeast(1f)
            val imageW = reviewImageWidthPx.coerceAtLeast(1f)
            val imageH = reviewImageHeightPx.coerceAtLeast(1f)
            val scale = min(previewW / imageW, previewH / imageH)
            val drawnW = imageW * scale
            val drawnH = imageH * scale
            val offsetX = (previewW - drawnW) / 2f
            val offsetY = (previewH - drawnH) / 2f

            val selLeft = previewW * clipLeft
            val selTop = previewH * clipTop
            val selRight = previewW * (1f - clipRight)
            val selBottom = previewH * (1f - clipBottom)

            val mappedLeft = ((selLeft - offsetX) / drawnW).coerceIn(0f, 1f)
            val mappedTop = ((selTop - offsetY) / drawnH).coerceIn(0f, 1f)
            val mappedRight = ((selRight - offsetX) / drawnW).coerceIn(0f, 1f)
            val mappedBottom = ((selBottom - offsetY) / drawnH).coerceIn(0f, 1f)

            val cropped = PhotoUtils.cropPhotoFile(
                context = context,
                photoPath = finalPath,
                leftRatio = mappedLeft,
                topRatio = mappedTop,
                rightRatio = (1f - mappedRight).coerceIn(0f, 1f),
                bottomRatio = (1f - mappedBottom).coerceIn(0f, 1f)
            )
            if (!cropped) {
                Toast.makeText(
                    context,
                    context.getString(R.string.camera_clip_failed),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }
        val finalFile = File(finalPath)
        if (!finalFile.exists() || finalFile.length() <= 0L) {
            Toast.makeText(
                context,
                context.getString(R.string.camera_clip_failed),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (reviewPhotoSource == "camera" && !doNotIncludeInGallery) {
            val galleryPath = originalPhotoTempPath ?: finalPath
            if (hasGalleryPermission) {
                val ok = PhotoUtils.saveToGallery(context, galleryPath)
                Toast.makeText(
                    context,
                    if (ok) context.getString(R.string.camera_saved_to_gallery)
                    else context.getString(R.string.camera_save_gallery_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                pendingGallerySavePath = galleryPath
                galleryPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        val hasPendingGalleryBatchItems = reviewPhotoSource == "gallery" &&
            galleryBatchTotal > 0 &&
            pendingGalleryUris.isNotEmpty()
        val shouldKeepCameraOpen = when (reviewPhotoSource) {
            "camera" -> keepCameraOpenAfterConfirm
            else -> hasPendingGalleryBatchItems
        }

        if (reviewPhotoSource == "gallery" && galleryBatchTotal > 0) {
            galleryBatchImported += 1
        }

        val savedKb = runCatching { File(finalPath).length() / 1024L }.getOrDefault(0L)
        val targetKb = PhotoUtils.getPhotoTargetSizeKb(context).toLong()
        val sizeMsg = "Saved ${savedKb}KB (target ${targetKb}KB)"
        Toast.makeText(context, sizeMsg, Toast.LENGTH_LONG).show()
        Toast.makeText(context, sizeMsg, Toast.LENGTH_SHORT).show()

        onPhotoCapture(finalPath, reviewPhotoSource, shouldKeepCameraOpen)
        originalPhotoTempPath?.let { runCatching { File(it).delete() } }
        originalPhotoTempPath = null
        currentGalleryUri = null

        if (reviewPhotoSource == "camera" && shouldKeepCameraOpen) {
            reviewPhotoPath = null
            launchCamera()
            return
        }

        if (hasPendingGalleryBatchItems) {
            reviewPhotoPath = null
            processNextQueuedGalleryUri(orCancelWhenDone = true)
            return
        }

        if (reviewPhotoSource == "gallery" && galleryBatchTotal > 0) {
            val completed = galleryBatchImported + galleryBatchSkipped + galleryBatchFailed
            val summary = context.getString(
                R.string.camera_gallery_batch_complete,
                galleryBatchImported,
                galleryBatchSkipped,
                galleryBatchFailed,
                completed,
                galleryBatchTotal
            )
            Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
            clearGalleryBatchState()
        }
    }

    // When reviewing a captured image, back returns to capture options first.
    BackHandler {
        if (reviewPhotoPath != null) {
            if (reviewPhotoSource == "gallery" && galleryBatchTotal > 0) {
                cancelGalleryBatch()
                return@BackHandler
            }
            originalPhotoTempPath?.let { runCatching { File(it).delete() } }
            originalPhotoTempPath = null
            reviewPhotoPath = null
            currentGalleryUri = null
        } else {
            onCancel()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (reviewPhotoPath != null) {
                            stringResource(R.string.camera_review_title)
                        } else {
                            stringResource(R.string.camera_add_photo_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (reviewPhotoPath != null) {
                            if (reviewPhotoSource == "gallery" && galleryBatchTotal > 0) {
                                cancelGalleryBatch()
                            } else {
                                pendingGallerySavePath = null
                                reviewPhotoPath = null
                                currentGalleryUri = null
                            }
                        } else {
                            onCancel()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.list_cd_more_actions))
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            modifier = Modifier.navigationBarsPadding()
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_theme_mode_light)) },
                                onClick = {
                                    onThemeModeSelected(ThemeMode.LIGHT)
                                    showOverflowMenu = false
                                },
                                trailingIcon = {
                                    if (themeMode == ThemeMode.LIGHT) Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_theme_mode_dark)) },
                                onClick = {
                                    onThemeModeSelected(ThemeMode.DARK)
                                    showOverflowMenu = false
                                },
                                trailingIcon = {
                                    if (themeMode == ThemeMode.DARK) Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_theme_mode_system)) },
                                onClick = {
                                    onThemeModeSelected(ThemeMode.SYSTEM)
                                    showOverflowMenu = false
                                },
                                trailingIcon = {
                                    if (themeMode == ThemeMode.SYSTEM) Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (reviewPhotoPath != null) {
                val isGalleryBatchActive = reviewPhotoSource == "gallery" && galleryBatchTotal > 0
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isGalleryBatchActive) {
                        val currentIndex = (galleryBatchImported + galleryBatchSkipped + galleryBatchFailed + 1)
                            .coerceAtMost(galleryBatchTotal)
                        Text(
                            text = stringResource(
                                R.string.camera_gallery_batch_progress,
                                currentIndex,
                                galleryBatchTotal,
                                galleryBatchImported,
                                galleryBatchSkipped,
                                galleryBatchFailed
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { retryReviewCapture() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Text(
                                stringResource(R.string.camera_retry),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = { confirmReviewCapture() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Text(
                                stringResource(R.string.common_ok),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (reviewPhotoSource == "camera") {
                            OutlinedButton(
                                onClick = { confirmReviewCapture(keepCameraOpenAfterConfirm = true) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    stringResource(R.string.camera_ok_add_another),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (isGalleryBatchActive) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { skipCurrentGalleryItem() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    stringResource(R.string.camera_skip_photo),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = { cancelGalleryBatch() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    stringResource(R.string.camera_cancel_batch),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (reviewPhotoPath == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = stringResource(R.string.camera_icon_description),
                        modifier = Modifier
                            .size(72.dp)
                            .clickable { launchCamera() }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.camera_instruction))
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { launchCamera() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.camera_take_photo))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.camera_take_photo))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.common_or),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val galleryActionText = if (supportsMultiGalleryImport) {
                        stringResource(R.string.camera_choose_gallery_photos)
                    } else {
                        stringResource(R.string.camera_choose_gallery_photo)
                    }
                    OutlinedButton(
                        onClick = { launchGallery() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = galleryActionText)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(galleryActionText)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onCancel) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        } else {
            val clipWidthPct = (1f - clipLeft - clipRight).coerceIn(0f, 1f)
            val clipHeightPct = (1f - clipTop - clipBottom).coerceIn(0f, 1f)
            val cropHandleColor = MaterialTheme.colorScheme.secondary
            val desiredInsetDp = handleInsetPreset.insetDp
            val reviewAspectRatio = (reviewImageWidthPx / reviewImageHeightPx)
                .takeIf { it.isFinite() && it > 0f }
                ?: 1f

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(reviewScrollState)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.camera_review_instruction),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { rotateReviewPhoto(clockwise = false) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.camera_rotate_left),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = { rotateReviewPhoto(clockwise = true) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.camera_rotate_right),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    // Keep the whole photo + crop handles visible above the pinned bottom actions.
                    val maxPreviewHeight = (screenHeight * 0.45f).coerceAtLeast(220.dp)
                    val availableWidth = this.maxWidth
                    val fittedHeight = (availableWidth / reviewAspectRatio)
                        .coerceAtMost(maxPreviewHeight)
                        .coerceAtLeast(180.dp)
                    val fittedWidth = (fittedHeight * reviewAspectRatio)
                        .coerceAtMost(availableWidth)

                    Card(
                        modifier = Modifier
                            .width(fittedWidth)
                            .height(fittedHeight)
                            .align(Alignment.Center)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(reviewPhotoPath!!))
                                .memoryCacheKey("${reviewPhotoPath}_${reviewImageVersion}")
                                .diskCacheKey("${reviewPhotoPath}_${reviewImageVersion}")
                                .build(),
                            contentDescription = stringResource(R.string.details_item_photo),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { size ->
                                    previewWidthPx = size.width.toFloat().coerceAtLeast(1f)
                                    previewHeightPx = size.height.toFloat().coerceAtLeast(1f)
                                }
                                .pointerInput(previewWidthPx, previewHeightPx, handleInsetPreset) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            val left = previewWidthPx * clipLeft
                                            val top = previewHeightPx * clipTop
                                            val right = previewWidthPx * (1f - clipRight)
                                            val bottom = previewHeightPx * (1f - clipBottom)
                                            val selectionWidth = (right - left).coerceAtLeast(1f)
                                            val selectionHeight = (bottom - top).coerceAtLeast(1f)
                                            val desiredInset = desiredInsetDp.dp.toPx()
                                            val insetX = min(desiredInset, selectionWidth / 3f)
                                            val insetY = min(desiredInset, selectionHeight / 3f)
                                            val topLeftHandle = Offset(left + insetX, top + insetY)
                                            val topRightHandle = Offset(right - insetX, top + insetY)
                                            val bottomLeftHandle = Offset(left + insetX, bottom - insetY)
                                            val bottomRightHandle = Offset(right - insetX, bottom - insetY)
                                            val thresholdPx = 48.dp.toPx()
                                            fun near(x: Float, y: Float): Boolean {
                                                val dx = start.x - x
                                                val dy = start.y - y
                                                return (dx * dx + dy * dy) <= (thresholdPx * thresholdPx)
                                            }
                                            activeClipHandle = when {
                                                near(topLeftHandle.x, topLeftHandle.y) -> ClipHandle.TOP_LEFT
                                                near(topRightHandle.x, topRightHandle.y) -> ClipHandle.TOP_RIGHT
                                                near(bottomLeftHandle.x, bottomLeftHandle.y) -> ClipHandle.BOTTOM_LEFT
                                                near(bottomRightHandle.x, bottomRightHandle.y) -> ClipHandle.BOTTOM_RIGHT
                                                else -> ClipHandle.MOVE
                                            }

                                            // If selection is full-frame, MOVE appears frozen (nothing can move).
                                            // Treat first drag as a corner resize based on touch quadrant.
                                            val isAlmostFullFrame =
                                                (1f - clipLeft - clipRight) >= 0.99f &&
                                                    (1f - clipTop - clipBottom) >= 0.99f
                                            if (isAlmostFullFrame && activeClipHandle == ClipHandle.MOVE) {
                                                activeClipHandle = when {
                                                    start.x < previewWidthPx / 2f && start.y < previewHeightPx / 2f -> ClipHandle.TOP_LEFT
                                                    start.x >= previewWidthPx / 2f && start.y < previewHeightPx / 2f -> ClipHandle.TOP_RIGHT
                                                    start.x < previewWidthPx / 2f && start.y >= previewHeightPx / 2f -> ClipHandle.BOTTOM_LEFT
                                                    else -> ClipHandle.BOTTOM_RIGHT
                                                }
                                            }
                                        },
                                        onDragEnd = { activeClipHandle = ClipHandle.MOVE },
                                        onDragCancel = { activeClipHandle = ClipHandle.MOVE }
                                    ) { _, dragAmount ->
                                        if (previewWidthPx <= 1f || previewHeightPx <= 1f) return@detectDragGestures

                                        val minSize = 0.1f
                                        val currentWidth = 1f - clipLeft - clipRight
                                        val currentHeight = 1f - clipTop - clipBottom
                                        val dxRatio = dragAmount.x / previewWidthPx
                                        val dyRatio = dragAmount.y / previewHeightPx

                                        when (activeClipHandle) {
                                            ClipHandle.MOVE -> {
                                                val newLeft = (clipLeft + dxRatio).coerceIn(0f, 1f - currentWidth)
                                                val newTop = (clipTop + dyRatio).coerceIn(0f, 1f - currentHeight)
                                                clipLeft = newLeft
                                                clipRight = 1f - currentWidth - newLeft
                                                clipTop = newTop
                                                clipBottom = 1f - currentHeight - newTop
                                            }

                                            ClipHandle.TOP_LEFT -> {
                                                clipLeft = (clipLeft + dxRatio).coerceIn(0f, 1f - clipRight - minSize)
                                                clipTop = (clipTop + dyRatio).coerceIn(0f, 1f - clipBottom - minSize)
                                            }

                                            ClipHandle.TOP_RIGHT -> {
                                                clipRight = (clipRight - dxRatio).coerceIn(0f, 1f - clipLeft - minSize)
                                                clipTop = (clipTop + dyRatio).coerceIn(0f, 1f - clipBottom - minSize)
                                            }

                                            ClipHandle.BOTTOM_LEFT -> {
                                                clipLeft = (clipLeft + dxRatio).coerceIn(0f, 1f - clipRight - minSize)
                                                clipBottom = (clipBottom - dyRatio).coerceIn(0f, 1f - clipTop - minSize)
                                            }

                                            ClipHandle.BOTTOM_RIGHT -> {
                                                clipRight = (clipRight - dxRatio).coerceIn(0f, 1f - clipLeft - minSize)
                                                clipBottom = (clipBottom - dyRatio).coerceIn(0f, 1f - clipTop - minSize)
                                            }
                                        }
                                    }
                                }
                        ) {
                            val left = size.width * clipLeft
                            val top = size.height * clipTop
                            val right = size.width * (1f - clipRight)
                            val bottom = size.height * (1f - clipBottom)
                            val selectionWidth = (right - left).coerceAtLeast(1f)
                            val selectionHeight = (bottom - top).coerceAtLeast(1f)

                            // Dim outside the selection window.
                            drawRect(
                                color = Color.Black.copy(alpha = 0.35f),
                                topLeft = Offset.Zero,
                                size = Size(size.width, top.coerceAtLeast(0f))
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.35f),
                                topLeft = Offset(0f, bottom.coerceAtMost(size.height)),
                                size = Size(size.width, (size.height - bottom).coerceAtLeast(0f))
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.35f),
                                topLeft = Offset(0f, top),
                                size = Size(left.coerceAtLeast(0f), selectionHeight)
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.35f),
                                topLeft = Offset(right.coerceAtMost(size.width), top),
                                size = Size((size.width - right).coerceAtLeast(0f), selectionHeight)
                            )

                            drawRect(
                                color = Color.Yellow,
                                topLeft = Offset(left, top),
                                size = Size(selectionWidth, selectionHeight),
                                style = Stroke(width = 4f)
                            )

                            val handleRadius = 12.dp.toPx()
                            val handleStrokeWidth = 2.5f
                            val desiredInset = desiredInsetDp.dp.toPx()
                            val insetX = min(desiredInset, selectionWidth / 3f)
                            val insetY = min(desiredInset, selectionHeight / 3f)
                            // Keep handles inside the crop area so bottom corners are easier to grab.
                            listOf(
                                Offset(left + insetX, top + insetY),
                                Offset(right - insetX, top + insetY),
                                Offset(left + insetX, bottom - insetY),
                                Offset(right - insetX, bottom - insetY)
                            ).forEach { center ->
                                drawCircle(
                                    color = cropHandleColor.copy(alpha = 0.85f),
                                    radius = handleRadius,
                                    center = center
                                )
                                drawCircle(
                                    color = Color.Black.copy(alpha = 0.35f),
                                    radius = handleRadius,
                                    center = center,
                                    style = Stroke(width = handleStrokeWidth)
                                )
                            }
                        }
                    }
                    }
                }


                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.camera_clip_title), style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    stringResource(
                        R.string.camera_clip_current,
                        (clipWidthPct * 100f).roundToInt(),
                        (clipHeightPct * 100f).roundToInt()
                    ),
                    style = MaterialTheme.typography.labelSmall
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    stringResource(R.string.camera_handle_inset_label),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = handleInsetPreset == PhotoUtils.CropHandleInsetPreset.SMALL,
                        onClick = {
                            handleInsetPreset = PhotoUtils.CropHandleInsetPreset.SMALL
                            PhotoUtils.setCropHandleInsetPreset(context, handleInsetPreset)
                        },
                        label = { Text(stringResource(R.string.camera_handle_inset_small)) }
                    )
                    FilterChip(
                        selected = handleInsetPreset == PhotoUtils.CropHandleInsetPreset.MEDIUM,
                        onClick = {
                            handleInsetPreset = PhotoUtils.CropHandleInsetPreset.MEDIUM
                            PhotoUtils.setCropHandleInsetPreset(context, handleInsetPreset)
                        },
                        label = { Text(stringResource(R.string.camera_handle_inset_medium)) }
                    )
                    FilterChip(
                        selected = handleInsetPreset == PhotoUtils.CropHandleInsetPreset.LARGE,
                        onClick = {
                            handleInsetPreset = PhotoUtils.CropHandleInsetPreset.LARGE
                            PhotoUtils.setCropHandleInsetPreset(context, handleInsetPreset)
                        },
                        label = { Text(stringResource(R.string.camera_handle_inset_large)) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    stringResource(R.string.list_menu_section_photo_size),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = photoTargetSizeKb == PhotoUtils.PHOTO_SIZE_SMALL_KB,
                        onClick = { onPhotoTargetSizeChange(PhotoUtils.PHOTO_SIZE_SMALL_KB) },
                        label = { Text(stringResource(R.string.camera_photo_size_200kb)) }
                    )
                    FilterChip(
                        selected = photoTargetSizeKb == PhotoUtils.PHOTO_SIZE_BALANCED_KB,
                        onClick = { onPhotoTargetSizeChange(PhotoUtils.PHOTO_SIZE_BALANCED_KB) },
                        label = { Text(stringResource(R.string.camera_photo_size_300kb)) }
                    )
                    FilterChip(
                        selected = photoTargetSizeKb == PhotoUtils.PHOTO_SIZE_HIGH_KB,
                        onClick = { onPhotoTargetSizeChange(PhotoUtils.PHOTO_SIZE_HIGH_KB) },
                        label = { Text(stringResource(R.string.camera_photo_size_500kb)) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.camera_photo_size_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Keep only clip-reset here; primary actions are above to avoid nav-button overlap.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipLeft = 0f
                            clipRight = 0f
                            clipTop = 0f
                            clipBottom = 0f
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Text(
                            stringResource(R.string.camera_clip_reset),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Camera shots are included in Gallery by default. Allow opt-out only for camera shots.
                    if (reviewPhotoSource == "camera") {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Checkbox(
                                checked = doNotIncludeInGallery,
                                onCheckedChange = { doNotIncludeInGallery = it }
                            )
                            Text(
                                text = stringResource(R.string.camera_do_not_include_in_gallery),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

