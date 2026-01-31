package com.yumzy.admin.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix

import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

object ImageUploadHelper {

    private val storage = Firebase.storage

    // Image quality and size configurations
    private const val COMPRESSION_QUALITY = 80
    private const val MAX_SUBCATEGORY_SIZE = 150 // 400x400 for small circle images
    private const val MAX_STORE_ITEM_SIZE = 600 // 800x800 for product images
    private const val MAX_RESTAURANT_SIZE = 400 // 1000x1000 for restaurant banners

    /**
     * Resizes and uploads an image to Firebase Storage
     * @param uri The local URI of the image to upload
     * @param folder The folder path in Firebase Storage
     * @param imageType Type of image to determine resize dimensions
     * @return The download URL of the uploaded image
     */
    suspend fun uploadImage(context: Context, uri: Uri, folder: String, imageType: ImageType): String {
        // Generate a unique filename
        val filename = "${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child("$folder/$filename")

        // Resize image based on type
        val resizedBitmap = when (imageType) {
            ImageType.SUBCATEGORY -> resizeImage(context, uri, MAX_SUBCATEGORY_SIZE)
            ImageType.STORE_ITEM -> resizeImage(context, uri, MAX_STORE_ITEM_SIZE)
            ImageType.RESTAURANT -> resizeImage(context, uri, MAX_RESTAURANT_SIZE)
        }

        // Convert bitmap to compressed byte array
        val byteArrayOutputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, byteArrayOutputStream)
        val imageData = byteArrayOutputStream.toByteArray()

        // Upload the compressed image
        storageRef.putBytes(imageData).await()

        // Get the download URL
        return storageRef.downloadUrl.await().toString()
    }

    /**
     * Replaces an old image with a new resized one
     * @param oldImageUrl The URL of the image to delete
     * @param newImageUri The URI of the new image to upload
     * @param folder The folder path in Firebase Storage
     * @param imageType Type of image to determine resize dimensions
     * @return The download URL of the new image
     */
    suspend fun replaceImage(
        context: Context,
        oldImageUrl: String?,
        newImageUri: Uri,
        folder: String,
        imageType: ImageType
    ): String {
        // Delete old image if exists
        oldImageUrl?.let { deleteImage(it) }

        // Upload new resized image
        return uploadImage(context, newImageUri, folder, imageType)
    }

    /**
     * Deletes an image from Firebase Storage given its URL
     * @param imageUrl The full download URL of the image
     */
    suspend fun deleteImage(imageUrl: String) {
        try {
            if (imageUrl.isNotBlank() && imageUrl.contains("firebase")) {
                val storageRef = storage.getReferenceFromUrl(imageUrl)
                storageRef.delete().await()
            }
        } catch (e: Exception) {
            // Image might not exist or URL is invalid, ignore
        }
    }

    /**
     * Resizes an image while maintaining aspect ratio
     * @param uri The image URI
     * @param maxSize Maximum width/height for the resized image
     * @return Resized Bitmap
     */
    private fun resizeImage(context: Context, uri: Uri, maxSize: Int): Bitmap {
        var inputStream: InputStream? = null
        try {
            // First, decode with bounds to get dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, maxSize, maxSize)
            options.inJustDecodeBounds = false

            // Decode with sampling
            inputStream = context.contentResolver.openInputStream(uri)
            var bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Further resize if needed and handle orientation
            bitmap = rotateImageIfRequired(context, bitmap!!, uri)
            return scaleBitmap(bitmap, maxSize)

        } catch (e: Exception) {
            throw RuntimeException("Error resizing image: ${e.message}")
        } finally {
            inputStream?.close()
        }
    }

    /**
     * Calculate the sampling factor to reduce memory usage
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Scale bitmap to fit within max dimensions while maintaining aspect ratio
     */
    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

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
     * Rotate image if required based on EXIF orientation
     */
    private fun rotateImageIfRequired(context: Context, bitmap: Bitmap, uri: Uri): Bitmap {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val exif = inputStream?.let { ExifInterface(it) }
            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            return when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            return bitmap
        }
    }

    /**
     * Rotate bitmap by specified degrees
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    enum class ImageType {
        SUBCATEGORY,    // Small images for subcategories (400x400)
        STORE_ITEM,     // Medium images for store items (800x800)
        RESTAURANT      // Large images for restaurants (1000x1000)
    }
}