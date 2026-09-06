package com.earlln.pianocode.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.roundToInt
import java.io.FileOutputStream

/** Loading, saving and sharing the sheet images the converter works on. */
object ImageIo {

    /** True when [uri] holds a PDF, by what the provider says or by the file's own header. */
    fun isPdf(context: Context, uri: Uri): Boolean {
        if (context.contentResolver.getType(uri) == "application/pdf") return true
        // Providers do not always declare a type, and a name is not evidence, so read the
        // five bytes every PDF starts with.
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(5)
                stream.read(header) == 5 && String(header, Charsets.US_ASCII) == "%PDF-"
            } ?: false
        }.getOrDefault(false)
    }

    /** How many pages [uri] has, or 0 when it is not a PDF this device can open. */
    fun pdfPageCount(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { it.pageCount }
        } ?: 0
    }.getOrDefault(0)

    /**
     * Draws one page of a PDF at working size.
     *
     * A PDF is a drawing rather than a photograph, so it is rendered at the size wanted
     * instead of being decoded and shrunk — the type comes out as sharp as the page allows,
     * which is the whole reason a PDF beats a photo of the same sheet.
     */
    private fun renderPdfPage(
        context: Context,
        uri: Uri,
        page: Int,
        maxDimension: Int,
    ): Bitmap? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) return@use null
                renderer.openPage(page.coerceIn(0, renderer.pageCount - 1)).use { pdfPage ->
                    val scale = maxDimension.toFloat() / maxOf(pdfPage.width, pdfPage.height)
                    val width = (pdfPage.width * scale).roundToInt().coerceAtLeast(1)
                    val height = (pdfPage.height * scale).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    // A PDF page is transparent where nothing is drawn. The recogniser and
                    // the renderer both expect paper, so it is given some.
                    Canvas(bitmap).drawColor(Color.WHITE)
                    pdfPage.render(
                        bitmap, null, null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                    )
                    bitmap
                }
            }
        }
    }.getOrNull()

    /**
     * Working size for a page.
     *
     * Chord symbols are among the smallest text on a lead sheet, so detail thrown away here
     * cannot be recovered by enlarging later — scaling an already-shrunk page only
     * interpolates. Kept high enough to leave the recogniser real pixels to read, and low
     * enough that a page and its rendered copy still fit in memory together.
     */
    const val MAX_DIMENSION = 3200

    /**
     * Decodes [uri] into a bitmap no larger than [maxDimension], rotated the way the photo
     * was actually taken. Cameras record orientation in EXIF rather than in the pixels, so
     * skipping that step would hand the recogniser a sideways page.
     */
    fun loadBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = MAX_DIMENSION,
        page: Int = 0,
    ): Bitmap? {
        val resolver = context.contentResolver
        if (isPdf(context, uri)) return renderPdfPage(context, uri, page, maxDimension)

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
