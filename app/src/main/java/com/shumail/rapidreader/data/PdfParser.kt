package com.shumail.rapidreader.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dedicated PDF Parser integrating Apache PDFBox engine with native PdfRenderer and fallback decompression.
 */
object PdfParser {

    suspend fun parse(context: Context, uri: Uri): PdfDocumentResult = withContext(Dispatchers.IO) {
        val localFile = DocumentParsers.saveUriToLocalFile(context, uri, "pdf", "pdf")
        
        // 1. Query page count using Android native PdfRenderer
        var pageCount = 1
        try {
            val pfd = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
            if (pfd != null) {
                val renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount
                renderer.close()
                pfd.close()
            }
        } catch (_: Exception) {
            pageCount = 1
        }

        // 2. Primary Extraction: Apache PDFBox Engine
        var extractedText: String? = null
        try {
            extractedText = extractTextWithPdfBox(context, localFile, pageCount)
        } catch (_: Exception) {
            extractedText = null
        }

        // 3. Fallback Extraction: Multi-strategy Flate/CMap Decompressor
        if (extractedText.isNullOrBlank() || extractedText.length < 20) {
            try {
                val rawBytes = localFile.readBytes()
                extractedText = extractTextFromPdfBytes(rawBytes, pageCount)
            } catch (_: Exception) {
                extractedText = null
            }
        }

        val finalContent = if (!extractedText.isNullOrBlank()) {
            extractedText
        } else {
            val fallbackBuilder = StringBuilder()
            for (p in 1..pageCount) {
                fallbackBuilder.append("# Page $p\n\n[Page $p Content]\n\n")
            }
            fallbackBuilder.toString().trim()
        }

        val title = localFile.nameWithoutExtension.replace(Regex("""^pdf_\d+_?"""), "").ifBlank { "PDF Document" }

        PdfDocumentResult(
            title = title,
            content = finalContent,
            localFilePath = localFile.absolutePath,
            pageCount = pageCount
        )
    }

    private fun extractTextWithPdfBox(context: Context, file: File, reportedPages: Int): String? {
        PDFBoxResourceLoader.init(context)
        return PDDocument.load(file).use { pdDoc ->
            val numPages = pdDoc.numberOfPages.coerceAtLeast(reportedPages)
            val fullTextBuilder = StringBuilder()
            var totalLength = 0
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true

            for (p in 1..numPages) {
                stripper.startPage = p
                stripper.endPage = p
                val pageText = stripper.getText(pdDoc)?.trim() ?: ""
                if (pageText.isNotBlank()) {
                    fullTextBuilder.append("# Page $p\n\n")
                    fullTextBuilder.append(pageText).append("\n\n")
                    totalLength += pageText.length
                }
            }

            if (totalLength > 15) {
                fullTextBuilder.toString().trim()
            } else {
                null
            }
        }
    }

    private fun extractTextFromPdfBytes(bytes: ByteArray, pageCount: Int): String {
        val streamTag = "stream".toByteArray(Charsets.US_ASCII)
        val endStreamTag = "endstream".toByteArray(Charsets.US_ASCII)

        var searchIndex = 0
        val decompressedStreamStrings = mutableListOf<String>()

        while (searchIndex < bytes.size) {
            val streamStart = TextSanitizer.indexOfSubarray(bytes, streamTag, searchIndex)
            if (streamStart == -1) break

            var actualDataStart = streamStart + streamTag.size
            while (actualDataStart < bytes.size && (bytes[actualDataStart] == '\r'.code.toByte() || bytes[actualDataStart] == '\n'.code.toByte())) {
                actualDataStart++
            }

            val streamEnd = TextSanitizer.indexOfSubarray(bytes, endStreamTag, actualDataStart)
            if (streamEnd == -1) break

            var actualDataEnd = streamEnd
            while (actualDataEnd > actualDataStart && (bytes[actualDataEnd - 1] == '\r'.code.toByte() || bytes[actualDataEnd - 1] == '\n'.code.toByte())) {
                actualDataEnd--
            }

            if (actualDataEnd > actualDataStart) {
                val compressedData = bytes.copyOfRange(actualDataStart, actualDataEnd)
                val decompressed = TextSanitizer.tryDecompressFlate(compressedData)
                if (decompressed != null && decompressed.isNotEmpty()) {
                    decompressedStreamStrings.add(String(decompressed, Charsets.ISO_8859_1))
                } else {
                    decompressedStreamStrings.add(String(compressedData, Charsets.ISO_8859_1))
                }
            }

            searchIndex = streamEnd + endStreamTag.size
        }

        // Pass 1: Build global /ToUnicode CMap mapping
        val globalCMap = mutableMapOf<String, String>()
        for (streamStr in decompressedStreamStrings) {
            if (streamStr.contains("beginbfchar") || streamStr.contains("beginbfrange")) {
                TextSanitizer.parseToUnicodeCMap(streamStr, globalCMap)
            }
        }

        val rawPdfString = String(bytes, Charsets.ISO_8859_1)
        if (rawPdfString.contains("beginbfchar") || rawPdfString.contains("beginbfrange")) {
            TextSanitizer.parseToUnicodeCMap(rawPdfString, globalCMap)
        }

        // Pass 2: Extract text from all streams
        val fullTextBuilder = StringBuilder()
        var foundTextCount = 0
        val sections = mutableListOf<String>()

        for (streamStr in decompressedStreamStrings) {
            if (streamStr.contains("/Subtype /Image") || streamStr.contains("beginbfchar")) continue

            val textInStream = extractTextFromPostScript(streamStr, globalCMap)
            if (textInStream.isNotBlank() && textInStream.length > 5) {
                sections.add(textInStream)
                foundTextCount += textInStream.length
            }
        }

        if (foundTextCount < 30) {
            val rawExtracted = extractTextFromPostScript(rawPdfString, globalCMap)
            if (rawExtracted.isNotBlank() && rawExtracted.length > 30) {
                sections.add(rawExtracted)
                foundTextCount += rawExtracted.length
            }
        }

        if (sections.isNotEmpty() && foundTextCount > 20) {
            for ((index, sectionText) in sections.withIndex()) {
                val headerName = if (index < pageCount) "Page ${index + 1}" else "Section ${index + 1}"
                fullTextBuilder.append("# $headerName\n\n")
                fullTextBuilder.append(sectionText).append("\n\n")
            }
            val res = fullTextBuilder.toString().trim()
            if (res.isNotBlank()) {
                return res
            }
        }

        val fallbackBuilder = StringBuilder()
        for (page in 1..pageCount) {
            fallbackBuilder.append("# Page $page\n\n")
            fallbackBuilder.append("PDF Document Page $page. Use Google Drive page viewer to examine full visual layout or tap RSVP to speed read.\n\n")
        }
        return fallbackBuilder.toString().trim()
    }

    private fun extractTextFromPostScript(content: String, cMap: Map<String, String>): String {
        val sb = StringBuilder()

        val opRegex = Regex("""(\((?:[^()\\]|\\.)*\)|<[0-9a-fA-F\s]+>|\[(?:[^\[\]\\]|\\.)*\])\s*(Tj|TJ|'|")""")
        val matches = opRegex.findAll(content)

        var hasMatched = false
        for (match in matches) {
            hasMatched = true
            val target = match.groupValues[1].trim()
            val op = match.groupValues[2]

            if (target.startsWith("[")) {
                val inner = target.substring(1, target.length - 1)
                val subRegex = Regex("""(\((?:[^()\\]|\\.)*\)|<[0-9a-fA-F\s]+>|[-+]?\d*\.?\d+)""")
                subRegex.findAll(inner).forEach { subMatch ->
                    val part = subMatch.groupValues[1].trim()
                    if (part.startsWith("(")) {
                        val rawStr = part.substring(1, part.length - 1)
                        val clean = TextSanitizer.cleanPdfEscapeChars(rawStr)
                        if (clean.isNotBlank()) sb.append(clean)
                    } else if (part.startsWith("<")) {
                        val rawHex = part.substring(1, part.length - 1).replace(Regex("""\s+"""), "")
                        val decoded = TextSanitizer.decodePdfHexString(rawHex, cMap)
                        if (decoded.isNotBlank()) sb.append(decoded)
                    } else {
                        val num = part.toFloatOrNull()
                        if (num != null && num < -120) {
                            sb.append(" ")
                        }
                    }
                }
                sb.append(" ")
            } else if (target.startsWith("(")) {
                val rawStr = target.substring(1, target.length - 1)
                val clean = TextSanitizer.cleanPdfEscapeChars(rawStr)
                if (clean.isNotBlank()) {
                    sb.append(clean).append(if (op == "'" || op == "\"") "\n" else " ")
                }
            } else if (target.startsWith("<")) {
                val rawHex = target.substring(1, target.length - 1).replace(Regex("""\s+"""), "")
                val decoded = TextSanitizer.decodePdfHexString(rawHex, cMap)
                if (decoded.isNotBlank()) {
                    sb.append(decoded).append(if (op == "'" || op == "\"") "\n" else " ")
                }
            }
        }

        if (!hasMatched) {
            val anyStringRegex = Regex("""\(((?:[^()\\]|\\.)*)\)""")
            anyStringRegex.findAll(content).forEach {
                val clean = TextSanitizer.cleanPdfEscapeChars(it.groupValues[1])
                if (clean.isNotBlank() && clean.length > 1) {
                    sb.append(clean).append(" ")
                }
            }
        }

        return sb.toString()
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }
}
