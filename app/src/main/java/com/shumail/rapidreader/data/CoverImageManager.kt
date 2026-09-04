package com.shumail.rapidreader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Dedicated manager for book cover thumbnails, native PDF rendering,
 * EPUB embedded cover extraction, and persistent internal storage copying.
 */
object CoverImageManager {

    private const val COVERS_DIR_NAME = "book_covers"
    private const val STANDARD_COVER_WIDTH = 600
    private const val STANDARD_COVER_HEIGHT = 900
    private const val STANDARD_COVER_RATIO = 2f / 3f

    /**
     * Renders the first page of a PDF document into a high-resolution 2:3 bitmap thumbnail
     * using Android's native PdfRenderer, and persists it into the app's internal files directory.
     */
    suspend fun generatePdfFirstPageThumbnail(context: Context, pdfFile: File): String? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() <= 0) return@withContext null

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        var bitmap: Bitmap? = null

        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY) ?: return@withContext null
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount <= 0) return@withContext null

            page = renderer.openPage(0)

            // Calculate dimensions targeting standard 2:3 book ratio (e.g. 600x900)
            val pageRatio = page.height.toFloat() / page.width.toFloat()
            val targetWidth = STANDARD_COVER_WIDTH
            val naturalHeight = (targetWidth * pageRatio).toInt().coerceIn(400, 1400)

            val renderedBitmap = Bitmap.createBitmap(targetWidth, naturalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(renderedBitmap)
            canvas.drawColor(android.graphics.Color.WHITE) // Guarantee opaque white backing

            page.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Center crop to standard 2:3 ratio so it displays cleanly in the library grid
            bitmap = centerCropToRatio(renderedBitmap, STANDARD_COVER_RATIO, STANDARD_COVER_WIDTH, STANDARD_COVER_HEIGHT)
            if (bitmap != renderedBitmap) {
                renderedBitmap.recycle()
            }

            val coversDir = getCoversDirectory(context)
            val destFile = File(coversDir, "pdf_thumb_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { bitmap?.recycle() } catch (_: Exception) {}
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Extracts the embedded cover image from an EPUB manifest/archive.
     * Persists the binary stream into the app's internal filesDir.
     * Returns the internal file path, or null if no cover was found in the archive.
     */
    suspend fun extractEpubCoverImage(context: Context, epubFile: File): String? = withContext(Dispatchers.IO) {
        if (!epubFile.exists() || epubFile.length() <= 0) return@withContext null

        try {
            val zipBytes = epubFile.readBytes()

            // 1. Locate container.xml to identify OPF path
            val opfPath = findEpubOpfPath(zipBytes) ?: "OEBPS/content.opf"
            val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

            val opfContent = readZipEntryAsString(zipBytes, opfPath)
                ?: readZipEntryAsString(zipBytes, "content.opf")
                ?: readZipEntryAsString(zipBytes, "OEBPS/content.opf")

            var coverHref: String? = null

            if (!opfContent.isNullOrBlank()) {
                // Check metadata: <meta name="cover" content="cover-image-id"/>
                val metaCover = Regex("""<meta\s+[^>]*?name=["']cover["'][^>]*?content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(opfContent)
                    ?: Regex("""<meta\s+[^>]*?content=["']([^"']+)["'][^>]*?name=["']cover["']""", RegexOption.IGNORE_CASE).find(opfContent)
                val coverId = metaCover?.groupValues?.get(1)

                if (coverId != null) {
                    val itemMatch = Regex("""<item\s+[^>]*?id=["']${Regex.escape(coverId)}["'][^>]*?href=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(opfContent)
                        ?: Regex("""<item\s+[^>]*?href=["']([^"']+)["'][^>]*?id=["']${Regex.escape(coverId)}["']""", RegexOption.IGNORE_CASE).find(opfContent)
                    coverHref = itemMatch?.groupValues?.get(1)
                }

                // Check item properties="cover-image"
                if (coverHref == null) {
                    val propMatch = Regex("""<item\s+[^>]*?properties=["'][^"']*?cover-image[^"']*?["'][^>]*?href=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(opfContent)
                        ?: Regex("""<item\s+[^>]*?href=["']([^"']+)["'][^>]*?properties=["'][^"']*?cover-image[^"']*?["']""", RegexOption.IGNORE_CASE).find(opfContent)
                    coverHref = propMatch?.groupValues?.get(1)
                }

                // Check item id="cover" or id="cover-image"
                if (coverHref == null) {
                    val fallbackItem = Regex("""<item\s+[^>]*?id=["'](?:cover|cover-image|book-cover)["'][^>]*?href=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(opfContent)
                    coverHref = fallbackItem?.groupValues?.get(1)
                }
            }

            var imageBytes: ByteArray? = null

            if (coverHref != null) {
                val fullPath = if (coverHref.startsWith("/")) coverHref.removePrefix("/") else opfDir + coverHref
                imageBytes = readZipEntryAsBytes(zipBytes, fullPath)
                    ?: readZipEntryAsBytes(zipBytes, coverHref)
                    ?: readZipEntryAsBytes(zipBytes, coverHref.substringAfterLast("/"))
            }

            // Fallback: Scan archive entries for any image whose name includes "cover"
            if (imageBytes == null) {
                imageBytes = findFirstZipImageContaining(zipBytes, "cover")
            }

            if (imageBytes != null && imageBytes.isNotEmpty()) {
                // Decode to check validity and ensure standard 2:3 ratio
                val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (decoded != null) {
                    val cropped = centerCropToRatio(decoded, STANDARD_COVER_RATIO, STANDARD_COVER_WIDTH, STANDARD_COVER_HEIGHT)
                    if (cropped != decoded) {
                        decoded.recycle()
                    }

                    val coversDir = getCoversDirectory(context)
                    val destFile = File(coversDir, "epub_cover_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")
                    FileOutputStream(destFile).use { out ->
                        cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    cropped.recycle()
                    return@withContext destFile.absolutePath
                }
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Copies and persists any user-selected image from gallery into app internal filesDir.
     * Applies an auto-crop to standard 2:3 ratio (e.g. 600x900) so screenshots and photos never distort.
     * DOES NOT rely on scoped storage URI permissions that break if original file is moved/deleted.
     */
    suspend fun persistCustomCoverFromUri(context: Context, uri: Uri, documentId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return@withContext null

            // Auto-crop to exact standard 2:3 ratio
            val croppedBitmap = centerCropToRatio(originalBitmap, STANDARD_COVER_RATIO, STANDARD_COVER_WIDTH, STANDARD_COVER_HEIGHT)
            if (croppedBitmap != originalBitmap) {
                originalBitmap.recycle()
            }

            val coversDir = getCoversDirectory(context)
            val destFile = File(coversDir, "custom_cover_${documentId}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(destFile).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            croppedBitmap.recycle()

            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Safely deletes an internal cover file when a cover is updated or removed.
     */
    fun deleteCoverFile(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.exists() && file.isFile) {
                file.delete()
            }
        } catch (_: Exception) {}
    }

    /**
     * Ensures the internal private book covers directory exists.
     */
    fun getCoversDirectory(context: Context): File {
        return File(context.filesDir, COVERS_DIR_NAME).apply { mkdirs() }
    }

    /**
     * Center crops a source bitmap to a given aspect ratio (e.g., 2:3)
     * and scales to the target dimensions smoothly.
     */
    fun centerCropToRatio(
        src: Bitmap,
        targetRatio: Float = STANDARD_COVER_RATIO,
        targetWidth: Int = STANDARD_COVER_WIDTH,
        targetHeight: Int = STANDARD_COVER_HEIGHT
    ): Bitmap {
        val srcWidth = src.width.toFloat()
        val srcHeight = src.height.toFloat()
        val srcRatio = srcWidth / srcHeight

        val cropX: Int
        val cropY: Int
        val cropWidth: Int
        val cropHeight: Int

        if (srcRatio > targetRatio) {
            // Source is wider than target ratio -> crop horizontal sides
            cropHeight = src.height
            cropWidth = (srcHeight * targetRatio).toInt().coerceAtMost(src.width)
            cropX = ((src.width - cropWidth) / 2).coerceAtLeast(0)
            cropY = 0
        } else {
            // Source is taller than target ratio -> crop top/bottom
            cropWidth = src.width
            cropHeight = (srcWidth / targetRatio).toInt().coerceAtMost(src.height)
            cropX = 0
            cropY = ((src.height - cropHeight) / 2).coerceAtLeast(0)
        }

        val safeWidth = cropWidth.coerceAtMost(src.width - cropX).coerceAtLeast(1)
        val safeHeight = cropHeight.coerceAtMost(src.height - cropY).coerceAtLeast(1)

        val cropped = Bitmap.createBitmap(src, cropX, cropY, safeWidth, safeHeight)
        if (cropped.width == targetWidth && cropped.height == targetHeight) {
            return cropped
        }

        val scaled = Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
        if (scaled != cropped && cropped != src) {
            cropped.recycle()
        }
        return scaled
    }

    // --- Private EPUB Zip Helpers ---

    private fun findEpubOpfPath(zipBytes: ByteArray): String? {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.equals("META-INF/container.xml", ignoreCase = true)) {
                    val text = zis.bufferedReader(Charsets.UTF_8).readText()
                    val match = Regex("""full-path=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(text)
                    if (match != null) {
                        return match.groupValues[1]
                    }
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    private fun readZipEntryAsString(zipBytes: ByteArray, targetName: String): String? {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.equals(targetName, ignoreCase = true) || entry.name.endsWith("/$targetName", ignoreCase = true)) {
                    return zis.bufferedReader(Charsets.UTF_8).readText()
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    private fun readZipEntryAsBytes(zipBytes: ByteArray, targetName: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.equals(targetName, ignoreCase = true) || entry.name.endsWith("/$targetName", ignoreCase = true)) {
                    return zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    private fun findFirstZipImageContaining(zipBytes: ByteArray, keyword: String): ByteArray? {
        val validExts = listOf(".jpg", ".jpeg", ".png", ".webp")
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (!entry.isDirectory && name.contains(keyword.lowercase()) && validExts.any { name.endsWith(it) }) {
                    return zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }
        return null
    }
}
