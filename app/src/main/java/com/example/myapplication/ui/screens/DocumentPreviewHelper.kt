package com.example.myapplication.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

data class DocumentPageRef(
    val uri: Uri,
    val isPdf: Boolean,
    val pdfPageIndex: Int = 0,
    val totalPdfPages: Int = 1,
    val globalPageIndex: Int = 0,
    val fileName: String = "dokuments"
)

enum class DetectedHeaderPosition(val requiredRotationDegrees: Float, val labelLatvian: String) {
    TOP(0f, "Augšā (0°)"),
    RIGHT(270f, "Labajā pusē (270°)"),
    BOTTOM(180f, "Apakšā (180°)"),
    LEFT(90f, "Kreisajā pusē (90°)"),
    NOT_FOUND(0f, "Zilā josla netika atrasta")
}

object DocumentPreviewHelper {
    private const val TAG = "DocumentPreviewHelper"

    /**
     * Extracts all previewable pages across multiple Uris (supporting both Images and multi-page PDFs).
     */
    fun extractPageReferences(context: Context, uris: List<Uri>): List<DocumentPageRef> {
        val pageList = mutableListOf<DocumentPageRef>()
        var globalIndex = 0

        for (uri in uris) {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val isPdf = mimeType.contains("pdf", ignoreCase = true) || uri.toString().endsWith(".pdf", ignoreCase = true)
            val fileName = uri.lastPathSegment ?: "dokuments"

            if (isPdf) {
                var pfd: ParcelFileDescriptor? = null
                var renderer: PdfRenderer? = null
                try {
                    pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        renderer = PdfRenderer(pfd)
                        val pageCount = renderer.pageCount
                        for (p in 0 until pageCount) {
                            pageList.add(
                                DocumentPageRef(
                                    uri = uri,
                                    isPdf = true,
                                    pdfPageIndex = p,
                                    totalPdfPages = pageCount,
                                    globalPageIndex = globalIndex++,
                                    fileName = fileName
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open PDF $uri", e)
                } finally {
                    try {
                        renderer?.close()
                        pfd?.close()
                    } catch (_: Exception) {}
                }
            } else {
                // Image file
                pageList.add(
                    DocumentPageRef(
                        uri = uri,
                        isPdf = false,
                        globalPageIndex = globalIndex++,
                        fileName = fileName
                    )
                )
            }
        }

        return pageList
    }

    /**
     * Renders a specific DocumentPageRef into a Bitmap.
     */
    fun renderPageBitmap(context: Context, pageRef: DocumentPageRef): Bitmap? {
        return try {
            if (pageRef.isPdf) {
                val pfd = context.contentResolver.openFileDescriptor(pageRef.uri, "r") ?: return null
                val renderer = PdfRenderer(pfd)
                if (pageRef.pdfPageIndex >= renderer.pageCount) {
                    renderer.close()
                    pfd.close()
                    return null
                }
                val page = renderer.openPage(pageRef.pdfPageIndex)

                // Render at higher resolution for clear preview and OCR
                val scale = 2.0f
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                page.close()
                renderer.close()
                pfd.close()
                bitmap
            } else {
                val inputStream = context.contentResolver.openInputStream(pageRef.uri) ?: return null
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering bitmap for page", e)
            null
        }
    }

    /**
     * Analyzes a Bitmap to locate the blue header row and determines the necessary rotation angle (0, 90, 180, 270) to put it at the top.
     */
    fun detectBlueHeaderRotation(bitmap: Bitmap): DetectedHeaderPosition {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= 0 || height <= 0) return DetectedHeaderPosition.NOT_FOUND

        var topBlueCount = 0
        var bottomBlueCount = 0
        var leftBlueCount = 0
        var rightBlueCount = 0
        var totalBlueCount = 0

        // Step size for high performance sampling
        val stepX = (width / 100).coerceAtLeast(1)
        val stepY = (height / 100).coerceAtLeast(1)

        val hsv = FloatArray(3)

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)

                val hue = hsv[0]        // 0..360
                val saturation = hsv[1] // 0..1
                val value = hsv[2]      // 0..1

                // Hue range for Blue color band (~165° to 255°)
                val isBlue = (hue in 165.0f..255.0f) && (saturation >= 0.18f) && (value >= 0.20f)

                if (isBlue) {
                    totalBlueCount++

                    // Check border regions (top 25%, bottom 25%, left 25%, right 25%)
                    if (y < height * 0.25f) topBlueCount++
                    if (y > height * 0.75f) bottomBlueCount++
                    if (x < width * 0.25f) leftBlueCount++
                    if (x > width * 0.75f) rightBlueCount++
                }
            }
        }

        if (totalBlueCount < 12) {
            return DetectedHeaderPosition.NOT_FOUND
        }

        val counts = mapOf(
            DetectedHeaderPosition.TOP to topBlueCount,
            DetectedHeaderPosition.BOTTOM to bottomBlueCount,
            DetectedHeaderPosition.LEFT to leftBlueCount,
            DetectedHeaderPosition.RIGHT to rightBlueCount
        )

        val maxEntry = counts.maxByOrNull { it.value } ?: return DetectedHeaderPosition.NOT_FOUND

        return if (maxEntry.value > 4) {
            maxEntry.key
        } else {
            DetectedHeaderPosition.NOT_FOUND
        }
    }

    /**
     * Rotates a Bitmap by degrees (e.g., 90, 180, 270).
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f || degrees % 360f == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Converts a Bitmap (with rotation applied) to a Base64 encoded string for OCR.space API.
     */
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 85): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
