package com.perfectlunacy.bailiwick.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Helper class for picking and processing photos.
 * Handles both gallery selection and camera capture.
 */
class PhotoPicker(private val fragment: Fragment) {

    companion object {
        private const val TAG = "PhotoPicker"
        private const val MAX_IMAGE_SIZE = 1024  // Max dimension in pixels
        private const val JPEG_QUALITY = 85
    }

    private var onPhotoSelected: ((Bitmap) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    // Content picker using GetContent contract (compatible with all Android versions)
    private val contentPickerLauncher: ActivityResultLauncher<String> =
        fragment.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleSelectedUri(it) } ?: onError?.invoke("No photo selected")
        }

    // Camera capture launcher
    private var tempPhotoUri: Uri? = null
    private val cameraLauncher: ActivityResultLauncher<Uri> =
        fragment.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                tempPhotoUri?.let { handleSelectedUri(it) }
            } else {
                onError?.invoke("Photo capture failed")
            }
        }

    // Camera permission launcher
    private val cameraPermissionLauncher: ActivityResultLauncher<String> =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCameraInternal()
            } else {
                onError?.invoke("Camera permission denied")
            }
        }

    /**
     * Pick a single photo from the gallery.
     */
    fun pickPhoto(
        onSelected: (Bitmap) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        this.onPhotoSelected = onSelected
        this.onError = onError
        contentPickerLauncher.launch("image/*")
    }

    /**
     * Take a photo with the camera.
     */
    fun takePhoto(
        onSelected: (Bitmap) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        this.onPhotoSelected = onSelected
        this.onError = onError

        val context = fragment.requireContext()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCameraInternal()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraInternal() {
        try {
            val context = fragment.requireContext()
            val tempFile = File.createTempFile("photo_", ".jpg", context.cacheDir)
            tempPhotoUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                tempFile
            )
            cameraLauncher.launch(tempPhotoUri!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch camera", e)
            onError?.invoke("Failed to launch camera: ${e.message}")
        }
    }

    private fun handleSelectedUri(uri: Uri) {
        try {
            val context = fragment.requireContext()
            val bitmap = loadAndProcessBitmap(context, uri)
            onPhotoSelected?.invoke(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process photo", e)
            onError?.invoke("Failed to process photo: ${e.message}")
        }
    }

    private fun loadAndProcessBitmap(context: Context, uri: Uri): Bitmap {
        // Load bitmap with size limits
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open input stream")

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        // Read dimensions first
        inputStream.use { BitmapFactory.decodeStream(it, null, options) }

        // Calculate sample size
        val sampleSize = calculateInSampleSize(options, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE)

        // Decode with sample size
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }

        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: throw IllegalStateException("Could not decode bitmap")

        // Apply EXIF rotation
        val rotatedBitmap = applyExifRotation(context, uri, bitmap)

        // Scale to max size if needed
        return scaleBitmap(rotatedBitmap, MAX_IMAGE_SIZE)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream.close()

            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotation == 0f) return bitmap

            val matrix = Matrix().apply { postRotate(rotation) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply EXIF rotation", e)
            return bitmap
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) return bitmap

        val scale = if (width > height) {
            maxSize.toFloat() / width
        } else {
            maxSize.toFloat() / height
        }

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Compress a bitmap for storage/upload.
     */
    fun compressBitmap(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return stream.toByteArray()
    }

    /**
     * Get an InputStream from compressed bitmap bytes.
     */
    fun getInputStream(bytes: ByteArray): InputStream {
        return ByteArrayInputStream(bytes)
    }
}
