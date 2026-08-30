package com.earlln.pianocode.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/** Loading, saving and sharing the sheet images the converter works on. */
object ImageIo {

    /** Anything larger than this is downscaled; OCR gains nothing from more pixels. */
    const val MAX_DIMENSION = 2400

    /**
     * Decodes [uri] into a bitmap no larger than [maxDimension], rotated the way the photo
     * was actually taken. Cameras record orientation in EXIF rather than in the pixels, so
     * skipping that step would hand the recogniser a sideways page.
     */
    fun loadBitmap(context: Context, uri: Uri, maxDimension: Int = MAX_DIMENSION): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (
            bounds.outWidth / (sampleSize * 2) >= maxDimension ||
            bounds.outHeight / (sampleSize * 2) >= maxDimension
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val rotation = resolver.openInputStream(uri)?.use { readRotation(it) } ?: 0
        val rotated = if (rotation == 0) {
            decoded
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it != decoded) decoded.recycle() }
        }

        val longest = maxOf(rotated.width, rotated.height)
        if (longest <= maxDimension) return rotated
        val scale = maxDimension.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            rotated,
            (rotated.width * scale).toInt().coerceAtLeast(1),
            (rotated.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != rotated) rotated.recycle()
        return scaled
    }

    private fun readRotation(stream: java.io.InputStream): Int =
        when (
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    /** Writes [bitmap] into the shared Pictures/PianoCode album. Returns its uri. */
    fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PianoCode")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            ?: return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    /**
     * A writable uri the camera app can save a captured page into.
     *
     * ACTION_IMAGE_CAPTURE hands the photo back through a uri we supply rather than in the
     * result, so the file has to exist — under FileProvider — before the camera is launched.
     */
    fun createCaptureUri(context: Context): Uri {
        val directory = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(directory, "sheet_${System.currentTimeMillis()}.jpg")
        file.createNewFile()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Builds a share intent for [bitmap] through the app's FileProvider. */
    fun shareIntent(context: Context, bitmap: Bitmap, fileName: String): Intent {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(directory, fileName)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
