package com.example.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

data class PdfDocumentResult(
    val title: String,
    val content: String,
    val localFilePath: String,
    val pageCount: Int
)

data class EpubDocumentResult(
    val title: String,
    val content: String,
    val localFilePath: String
)

data class WebDocumentResult(
    val title: String,
    val content: String,
    val url: String
)

object DocumentParsers {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun saveUriToLocalFile(context: Context, uri: Uri, prefix: String, extension: String): File = withContext(Dispatchers.IO) {
        val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
        val targetFile = File(docsDir, "${prefix}_${System.currentTimeMillis()}.$extension")
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open file from Uri")
        inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        targetFile
    }

    suspend fun readTextFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        inputStream?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
        } ?: throw IllegalStateException("Could not open file stream")
    }

    /**
     * Reads PDF using industry-standard Apache PDFBox engine with native PdfRenderer and fallback decompression.
     * Extracts text page-by-page preserving chapter headers, and saves PDF locally for Drive-style viewer.
     */
    suspend fun readPdfFromUri(context: Context, uri: Uri): PdfDocumentResult = withContext(Dispatchers.IO) {
        val localFile = saveUriToLocalFile(context, uri, "pdf", "pdf")
        
        // Query page count using Android native PdfRenderer
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

        // 1. Primary Extraction: Heavy Apache PDFBox Engine
        var extractedText: String? = null
        try {
            extractedText = extractTextWithPdfBox(context, localFile, pageCount)
        } catch (e: Exception) {
            extractedText = null
        }

        // 2. Fallback Extraction: Multi-strategy Flate/CMap Decompressor
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

    /**
     * Extracts text page by page using Apache PDFBox Android engine.
     */
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

    /**
     * Comprehensive PDF text extractor:
     * 1. Decompresses Flate streams (zlib, raw deflate, and prefixed streams).
     * 2. Scans for and compiles /ToUnicode CMap tables (mapping CID/glyph hex codes to characters).
     * 3. Extracts text from literal strings (Tj, TJ, ', "), hexadecimal strings (<hex> Tj/TJ), kerning arrays, and Object Streams.
     */
    private fun extractTextFromPdfBytes(bytes: ByteArray, pageCount: Int): String {
        val streamTag = "stream".toByteArray(Charsets.US_ASCII)
        val endStreamTag = "endstream".toByteArray(Charsets.US_ASCII)

        var searchIndex = 0
        val decompressedStreamStrings = mutableListOf<String>()

        while (searchIndex < bytes.size) {
            val streamStart = indexOfSubarray(bytes, streamTag, searchIndex)
            if (streamStart == -1) break

            var actualDataStart = streamStart + streamTag.size
            // Skip CR and LF after "stream"
            while (actualDataStart < bytes.size && (bytes[actualDataStart] == '\r'.code.toByte() || bytes[actualDataStart] == '\n'.code.toByte())) {
                actualDataStart++
            }

            val streamEnd = indexOfSubarray(bytes, endStreamTag, actualDataStart)
            if (streamEnd == -1) break

            var actualDataEnd = streamEnd
            // Trim trailing CR and LF before "endstream"
            while (actualDataEnd > actualDataStart && (bytes[actualDataEnd - 1] == '\r'.code.toByte() || bytes[actualDataEnd - 1] == '\n'.code.toByte())) {
                actualDataEnd--
            }

            if (actualDataEnd > actualDataStart) {
                val compressedData = bytes.copyOfRange(actualDataStart, actualDataEnd)
                val decompressed = tryDecompressFlate(compressedData)
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
                parseToUnicodeCMap(streamStr, globalCMap)
            }
        }

        // Also check raw PDF bytes for CMaps if not inside streams
        val rawPdfString = String(bytes, Charsets.ISO_8859_1)
        if (rawPdfString.contains("beginbfchar") || rawPdfString.contains("beginbfrange")) {
            parseToUnicodeCMap(rawPdfString, globalCMap)
        }

        // Pass 2: Extract text from all streams
        val fullTextBuilder = StringBuilder()
        var foundTextCount = 0
        val sections = mutableListOf<String>()

        for (streamStr in decompressedStreamStrings) {
            // Skip pure image/font binary descriptor noise streams
            if (streamStr.contains("/Subtype /Image") || streamStr.contains("beginbfchar")) continue

            val textInStream = extractTextFromPostScript(streamStr, globalCMap)
            if (textInStream.isNotBlank() && textInStream.length > 5) {
                sections.add(textInStream)
                foundTextCount += textInStream.length
            }
        }

        // If decompressed streams yielded no text, try extracting from raw PDF text directly
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

        // Fallback for scanned graphic-only PDFs
        val fallbackBuilder = StringBuilder()
        for (page in 1..pageCount) {
            fallbackBuilder.append("# Page $page\n\n")
            fallbackBuilder.append("PDF Document Page $page. Use Google Drive page viewer to examine full visual layout or tap RSVP to speed read.\n\n")
        }
        return fallbackBuilder.toString().trim()
    }

    private fun tryDecompressFlate(data: ByteArray): ByteArray? {
        // 1. Try standard zlib wrapper
        try {
            val inflater = Inflater(false)
            inflater.setInput(data)
            val outputStream = ByteArrayOutputStream(data.size * 2)
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                outputStream.write(buffer, 0, count)
            }
            inflater.end()
            val result = outputStream.toByteArray()
            if (result.isNotEmpty()) return result
        } catch (_: Exception) {}

        // 2. Try raw deflate (nowrap = true)
        try {
            val inflater = Inflater(true)
            inflater.setInput(data)
            val outputStream = ByteArrayOutputStream(data.size * 2)
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                outputStream.write(buffer, 0, count)
            }
            inflater.end()
            val result = outputStream.toByteArray()
            if (result.isNotEmpty()) return result
        } catch (_: Exception) {}

        // 3. Scan for 0x78 zlib header if prefix bytes exist
        val maxScan = minOf(32, data.size - 4)
        for (i in 0 until maxScan) {
            if (data[i] == 0x78.toByte()) {
                try {
                    val inflater = Inflater(false)
                    inflater.setInput(data, i, data.size - i)
                    val outputStream = ByteArrayOutputStream(data.size * 2)
                    val buffer = ByteArray(8192)
                    while (!inflater.finished()) {
                        val count = inflater.inflate(buffer)
                        if (count == 0) {
                            if (inflater.needsInput() || inflater.needsDictionary()) break
                        }
                        outputStream.write(buffer, 0, count)
                    }
                    inflater.end()
                    val result = outputStream.toByteArray()
                    if (result.isNotEmpty()) return result
                } catch (_: Exception) {}
            }
        }

        return null
    }

    private fun parseToUnicodeCMap(cmapContent: String, cMap: MutableMap<String, String>) {
        // Parse beginbfchar .. endbfchar
        val bfCharRegex = Regex("""(\d+)\s+beginbfchar\s*(.*?)\s*endbfchar""", RegexOption.DOT_MATCHES_ALL)
        bfCharRegex.findAll(cmapContent).forEach { match ->
            val inner = match.groupValues[2]
            val entryRegex = Regex("""<([0-9a-fA-F]+)>\s+<([0-9a-fA-F]+)>""")
            entryRegex.findAll(inner).forEach { entry ->
                val code = entry.groupValues[1].uppercase()
                val unicodeHex = entry.groupValues[2]
                val decoded = decodeHexToUnicodeString(unicodeHex)
                if (decoded.isNotBlank()) {
                    cMap[code] = decoded
                }
            }
        }

        // Parse beginbfrange .. endbfrange
        val bfRangeRegex = Regex("""(\d+)\s+beginbfrange\s*(.*?)\s*endbfrange""", RegexOption.DOT_MATCHES_ALL)
        bfRangeRegex.findAll(cmapContent).forEach { match ->
            val inner = match.groupValues[2]
            // Form 1: <start> <end> <destStart>
            val rangeForm1 = Regex("""<([0-9a-fA-F]+)>\s+<([0-9a-fA-F]+)>\s+<([0-9a-fA-F]+)>""")
            rangeForm1.findAll(inner).forEach { entry ->
                try {
                    val start = entry.groupValues[1].toInt(16)
                    val end = entry.groupValues[2].toInt(16)
                    var dest = entry.groupValues[3].toInt(16)
                    val hexLen = entry.groupValues[1].length
                    for (code in start..end) {
                        val codeHex = code.toString(16).uppercase().padStart(hexLen, '0')
                        val charStr = String(Character.toChars(dest))
                        cMap[codeHex] = charStr
                        dest++
                    }
                } catch (_: Exception) {}
            }

            // Form 2: <start> <end> [ <dest1> <dest2> ... ]
            val rangeForm2 = Regex("""<([0-9a-fA-F]+)>\s+<([0-9a-fA-F]+)>\s+\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            rangeForm2.findAll(inner).forEach { entry ->
                try {
                    val start = entry.groupValues[1].toInt(16)
                    val end = entry.groupValues[2].toInt(16)
                    val hexLen = entry.groupValues[1].length
                    val dests = Regex("""<([0-9a-fA-F]+)>""").findAll(entry.groupValues[3]).map { it.groupValues[1] }.toList()
                    for ((idx, code) in (start..end).withIndex()) {
                        if (idx < dests.size) {
                            val codeHex = code.toString(16).uppercase().padStart(hexLen, '0')
                            val destStr = decodeHexToUnicodeString(dests[idx])
                            if (destStr.isNotBlank()) {
                                cMap[codeHex] = destStr
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun decodeHexToUnicodeString(hex: String): String {
        val clean = hex.trim()
        val sb = StringBuilder()
        if (clean.length % 4 == 0) {
            for (i in 0 until clean.length step 4) {
                try {
                    val code = clean.substring(i, i + 4).toInt(16)
                    if (code in 32..65533 && code != 65534) {
                        sb.append(code.toChar())
                    }
                } catch (_: Exception) {}
            }
        } else if (clean.length % 2 == 0) {
            for (i in 0 until clean.length step 2) {
                try {
                    val code = clean.substring(i, i + 2).toInt(16)
                    if (code in 32..255) {
                        sb.append(code.toChar())
                    }
                } catch (_: Exception) {}
            }
        }
        return sb.toString()
    }

    private fun decodePdfHexString(rawHex: String, cMap: Map<String, String>): String {
        if (rawHex.isBlank()) return ""
        val hex = if (rawHex.length % 2 != 0) rawHex + "0" else rawHex
        val sb = StringBuilder()

        // 1. Try matching with CMap
        if (cMap.isNotEmpty()) {
            var i = 0
            var allMapped = true
            val tempSb = StringBuilder()
            while (i < hex.length) {
                val quad = if (i + 4 <= hex.length) hex.substring(i, i + 4).uppercase() else null
                val pair = if (i + 2 <= hex.length) hex.substring(i, i + 2).uppercase() else null
                if (quad != null && cMap.containsKey(quad)) {
                    tempSb.append(cMap[quad])
                    i += 4
                } else if (pair != null && cMap.containsKey(pair)) {
                    tempSb.append(cMap[pair])
                    i += 2
                } else {
                    allMapped = false
                    break
                }
            }
            if (allMapped && tempSb.isNotBlank()) {
                return tempSb.toString()
            }
        }

        // 2. Try UTF-16BE decoding if length is multiple of 4
        if (hex.length % 4 == 0) {
            var isLikelyUtf16 = true
            val utf16Sb = StringBuilder()
            for (i in 0 until hex.length step 4) {
                val code = hex.substring(i, i + 4).toIntOrNull(16) ?: 0
                if (code in 32..65533 && code != 65534) {
                    utf16Sb.append(code.toChar())
                } else if (code == 0 || code == 9 || code == 10 || code == 13) {
                    utf16Sb.append(" ")
                } else {
                    isLikelyUtf16 = false
                    break
                }
            }
            if (isLikelyUtf16 && utf16Sb.isNotBlank()) {
                return utf16Sb.toString()
            }
        }

        // 3. Fallback: 1-byte ASCII / Latin1 decoding
        for (i in 0 until hex.length step 2) {
            val code = hex.substring(i, i + 2).toIntOrNull(16) ?: 0
            if (code in 32..126 || code in 160..255) {
                sb.append(code.toChar())
            } else if (code == 9 || code == 10 || code == 13) {
                sb.append(" ")
            }
        }

        return sb.toString()
    }

    private fun extractTextFromPostScript(content: String, cMap: Map<String, String>): String {
        val sb = StringBuilder()

        // Match all standard PDF text operators:
        // (LiteralString) Tj / TJ / ' / "
        // <HexString> Tj / TJ / ' / "
        // [ (Literal) 100 <Hex> ] TJ
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
                        val clean = cleanPdfEscapeChars(rawStr)
                        if (clean.isNotBlank()) sb.append(clean)
                    } else if (part.startsWith("<")) {
                        val rawHex = part.substring(1, part.length - 1).replace(Regex("""\s+"""), "")
                        val decoded = decodePdfHexString(rawHex, cMap)
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
                val clean = cleanPdfEscapeChars(rawStr)
                if (clean.isNotBlank()) {
                    sb.append(clean).append(if (op == "'" || op == "\"") "\n" else " ")
                }
            } else if (target.startsWith("<")) {
                val rawHex = target.substring(1, target.length - 1).replace(Regex("""\s+"""), "")
                val decoded = decodePdfHexString(rawHex, cMap)
                if (decoded.isNotBlank()) {
                    sb.append(decoded).append(if (op == "'" || op == "\"") "\n" else " ")
                }
            }
        }

        if (!hasMatched) {
            // Fallback for non-standard PostScript text: match any string literals
            val anyStringRegex = Regex("""\(((?:[^()\\]|\\.)*)\)""")
            anyStringRegex.findAll(content).forEach {
                val clean = cleanPdfEscapeChars(it.groupValues[1])
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

    private fun cleanPdfEscapeChars(text: String): String {
        var res = text
            .replace("\\n", "\n")
            .replace("\\r", "")
            .replace("\\t", " ")
            .replace("\\b", "")
            .replace("\\f", "")
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")

        // Replace octal escapes \ddd
        val octalRegex = Regex("""\\([0-7]{1,3})""")
        res = octalRegex.replace(res) { m ->
            try {
                val code = m.groupValues[1].toInt(8)
                if (code in 32..126 || code in 160..255) code.toChar().toString() else " "
            } catch (_: Exception) {
                " "
            }
        }

        // Filter out non-printable garbage
        val cleanChars = StringBuilder()
        for (c in res) {
            if (c.isLetterOrDigit() || c.isWhitespace() || c in "!?,.:;\"'()—-–/&@#%*+=[]{}<>%$") {
                cleanChars.append(c)
            }
        }
        return cleanChars.toString()
    }

    private fun indexOfSubarray(array: ByteArray, target: ByteArray, start: Int): Int {
        if (target.isEmpty() || start >= array.size) return -1
        for (i in start..array.size - target.size) {
            var found = true
            for (j in target.indices) {
                if (array[i + j] != target[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    /**
     * Reads EPUB files by parsing container.xml, OPF spine, and HTML chapters in order.
     */
    suspend fun readEpubFromUri(context: Context, uri: Uri): EpubDocumentResult = withContext(Dispatchers.IO) {
        val localFile = saveUriToLocalFile(context, uri, "epub", "epub")
        val rawBytes = localFile.readBytes()

        // 1. Find root OPF file from META-INF/container.xml
        val opfPath = findEpubOpfPath(rawBytes) ?: "OEBPS/content.opf"
        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

        // 2. Read OPF content
        val opfContent = readZipEntryAsString(rawBytes, opfPath)

        // 3. Extract title & spine
        var bookTitle = "EPUB Book"
        val spineHrefs = mutableListOf<String>()

        if (opfContent != null) {
            val titleMatch = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", RegexOption.IGNORE_CASE).find(opfContent)
            if (titleMatch != null) {
                bookTitle = stripHtml(titleMatch.groupValues[1]).ifBlank { "EPUB Book" }
            }

            // Parse manifest items: id -> href
            val manifestMap = mutableMapOf<String, String>()
            val itemRegex = Regex("""<item\s+[^>]*id=["']([^"']+)["'][^>]*href=["']([^"']+)["']|href=["']([^"']+)["'][^>]*id=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            itemRegex.findAll(opfContent).forEach { match ->
                val id = match.groupValues[1].ifBlank { match.groupValues[4] }
                val href = match.groupValues[2].ifBlank { match.groupValues[3] }
                if (id.isNotBlank() && href.isNotBlank()) {
                    manifestMap[id] = href
                }
            }

            // Parse spine itemrefs
            val itemrefRegex = Regex("""<itemref\s+[^>]*idref=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            itemrefRegex.findAll(opfContent).forEach { match ->
                val idref = match.groupValues[1]
                val href = manifestMap[idref]
                if (href != null && (href.endsWith(".html", true) || href.endsWith(".xhtml", true) || href.endsWith(".htm", true))) {
                    spineHrefs.add(opfDir + href)
                }
            }
        }

        // 4. Extract chapters in spine order (or fallback to any html entry in zip)
        val textBuilder = StringBuilder()
        var chapterIndex = 1

        if (spineHrefs.isNotEmpty()) {
            for (href in spineHrefs) {
                val chapterHtml = readZipEntryAsString(rawBytes, href)
                if (chapterHtml != null) {
                    val (chTitle, cleanText) = extractChapterFromHtml(chapterHtml, chapterIndex)
                    if (cleanText.length > 30) {
                        textBuilder.append("# ").append(chTitle).append("\n\n")
                        textBuilder.append(cleanText).append("\n\n")
                        chapterIndex++
                    }
                }
            }
        }

        if (textBuilder.isBlank()) {
            // Fallback: iterate all html entries in zip
            ZipInputStream(ByteArrayInputStream(rawBytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if ((name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm")) &&
                        !name.contains("container.xml") && !name.contains("toc.ncx")
                    ) {
                        val content = zip.bufferedReader(Charsets.UTF_8).readText()
                        val (chTitle, cleanText) = extractChapterFromHtml(content, chapterIndex)
                        if (cleanText.length > 30) {
                            textBuilder.append("# ").append(chTitle).append("\n\n")
                            textBuilder.append(cleanText).append("\n\n")
                            chapterIndex++
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val result = textBuilder.toString().trim()
        if (result.isBlank()) {
            throw IllegalStateException("No readable chapters found in this EPUB file")
        }

        EpubDocumentResult(
            title = bookTitle,
            content = result,
            localFilePath = localFile.absolutePath
        )
    }

    private fun findEpubOpfPath(zipBytes: ByteArray): String? {
        val containerXml = readZipEntryAsString(zipBytes, "META-INF/container.xml") ?: return null
        val match = Regex("""full-path=["']([^"']+\.opf)["']""", RegexOption.IGNORE_CASE).find(containerXml)
        return match?.groupValues?.get(1)
    }

    private fun readZipEntryAsString(zipBytes: ByteArray, targetName: String): String? {
        val normalizedTarget = targetName.replace("\\", "/").trimStart('/')
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name.replace("\\", "/").trimStart('/')
                if (entryName.equals(normalizedTarget, ignoreCase = true)) {
                    return zip.bufferedReader(Charsets.UTF_8).readText()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun extractChapterFromHtml(html: String, defaultIndex: Int): Pair<String, String> {
        val titleMatch = Regex("""<h[1-2][^>]*>(.*?)</h[1-2]>|<title>(.*?)</title>""", RegexOption.IGNORE_CASE).find(html)
        val rawTitle = titleMatch?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }?.let { stripHtml(it) }
        val chapterTitle = if (!rawTitle.isNullOrBlank() && rawTitle.length < 50) rawTitle.trim() else "Chapter $defaultIndex"

        val bodyText = extractReadableBody(html)
        return Pair(chapterTitle, bodyText)
    }

    suspend fun fetchArticleFromWeb(urlStr: String): WebDocumentResult = withContext(Dispatchers.IO) {
        var validUrl = urlStr.trim()
        if (!validUrl.startsWith("http://") && !validUrl.startsWith("https://")) {
            validUrl = "https://$validUrl"
        }

        val request = Request.Builder()
            .url(validUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code}: Failed to fetch web page")
        }

        val html = response.body?.string() ?: throw IllegalStateException("Empty response body")
        
        // Extract title
        val titleMatch = Regex("""<title>(.*?)</title>|<meta\s+property=["']og:title["']\s+content=["'](.*?)["']""", RegexOption.IGNORE_CASE).find(html)
        val rawTitle = titleMatch?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }?.let { stripHtml(it) } ?: "Web Article"
        val title = rawTitle.split("|", "-", "—", "•").firstOrNull()?.trim()?.ifBlank { "Web Article" } ?: "Web Article"

        val cleanedText = extractReadableBody(html)
        if (cleanedText.isBlank()) {
            throw IllegalStateException("Could not extract readable article text from this web page")
        }

        WebDocumentResult(
            title = title,
            content = cleanedText,
            url = validUrl
        )
    }

    private fun extractReadableBody(html: String): String {
        var content = html
        // Remove script, style, nav, footer, header, aside, noscript, svg
        content = content.replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
        content = content.replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
        content = content.replace(Regex("""<nav[\s\S]*?</nav>""", RegexOption.IGNORE_CASE), " ")
        content = content.replace(Regex("""<header[\s\S]*?</header>""", RegexOption.IGNORE_CASE), " ")
        content = content.replace(Regex("""<footer[\s\S]*?</footer>""", RegexOption.IGNORE_CASE), " ")
        content = content.replace(Regex("""<aside[\s\S]*?</aside>""", RegexOption.IGNORE_CASE), " ")
        content = content.replace(Regex("""<noscript[\s\S]*?</noscript>""", RegexOption.IGNORE_CASE), " ")
        content = content.replace(Regex("""<svg[\s\S]*?</svg>""", RegexOption.IGNORE_CASE), " ")
        content = content.replace(Regex("""<!--[\s\S]*?-->"""), " ")

        // Format Headings with markdown headers for chapter recognition
        content = content.replace(Regex("""<h1[^>]*>(.*?)</h1>""", RegexOption.IGNORE_CASE)) { "\n\n# " + stripHtml(it.groupValues[1]) + "\n\n" }
        content = content.replace(Regex("""<h2[^>]*>(.*?)</h2>""", RegexOption.IGNORE_CASE)) { "\n\n## " + stripHtml(it.groupValues[1]) + "\n\n" }
        content = content.replace(Regex("""<h3[^>]*>(.*?)</h3>""", RegexOption.IGNORE_CASE)) { "\n\n### " + stripHtml(it.groupValues[1]) + "\n\n" }

        // Replace block elements with linebreaks
        content = content.replace(Regex("""<(p|div|li|br|article|section|blockquote)[^>]*>""", RegexOption.IGNORE_CASE), "\n")
        
        // Strip all other HTML tags
        val text = stripHtml(content)
        
        // Normalize whitespace and paragraphs
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && (it.startsWith("#") || it.length > 15) }
            .joinToString("\n\n")
    }

    fun stripHtml(html: String): String {
        return html
            .replace(Regex("""<[^>]*>"""), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace(Regex("""[ \t]+"""), " ")
            .trim()
    }
}

data class SamplePreset(
    val title: String,
    val category: String,
    val readingTimeEst: String,
    val wordCount: Int,
    val content: String
)

object SamplePresets {
    val presets = listOf(
        SamplePreset(
            title = "The Shape of Attention",
            category = "Essay · PDF",
            readingTimeEst = "4 min",
            wordCount = 612,
            content = """
# Introduction to Attention

Attention is the rarest and purest form of generosity. In an age of infinite distractions, deep focus has become the ultimate superpower. 

When you read using Rapid Serial Visual Presentation, your visual cortex connects directly with meaning without the friction of eye saccades or vocalization. The words flow like thoughts, crystalline and immediate. 

# The Architecture of Consciousness

Consider how the mind constructs reality: every sensation, every idea, every memory is sculpted by where you choose to place your awareness. To master attention is to master the architecture of consciousness itself.

As you train your perception to absorb ideas at 400, 600, or 800 words per minute, you realize that speed is not about rushing; it is about eliminating noise. When the noise ceases, comprehension soars into absolute clarity and stillness.
            """.trimIndent()
        ),
        SamplePreset(
            title = "The Science of RSVP Speed Reading",
            category = "Speed Reading Drill",
            readingTimeEst = "1 min",
            wordCount = 280,
            content = """
# What is RSVP?

Rapid Serial Visual Presentation (RSVP) is a reading technique where words are flashed individually in a single fixed focal location on the screen. 

# Eye Saccades & Mechanics

When humans read traditional text on paper or screens, our eyes perform rapid involuntary jumps called saccades, followed by brief pauses called fixations. During these saccades, our visual cognitive system is effectively blind. In fact, up to eighty percent of total reading time is spent moving our eye muscles from word to word and searching for the next line!

# The Optimal Recognition Point

RSVP eliminates eye movement completely. By anchoring each word at its Optimal Recognition Point (ORP)—typically the letter located slightly to the left of the center—your foveal vision processes each word instantly without any physical eye movement. 

When you eliminate sub-vocalization and physical eye strain, reading speeds of 400 to 1000 words per minute become natural. Your brain comprehends concepts in rapid visual bursts, turning dense reading into an effortless, cinematic stream of thoughts and ideas.
            """.trimIndent()
        ),
        SamplePreset(
            title = "The Overview Effect: Earth from Space",
            category = "Science & Cosmos",
            readingTimeEst = "1.5 min",
            wordCount = 345,
            content = """
# The Pale Blue Dot

The Overview Effect is a profound cognitive shift in awareness reported by some astronauts and cosmonauts during spaceflight, often while viewing the Earth from orbit or from the lunar surface.

From space, the Earth appears as a tiny, fragile ball of life, hanging in the void, shielded and nourished by a paper-thin atmosphere. From this vantage point, national boundaries vanish, the conflicts that divide people become petty, and the need to create a planetary society with the united will to protect this pale blue dot becomes both obvious and imperative.

# Edgar Mitchell's Experience

Apollo 14 astronaut Edgar Mitchell described it with unforgettable clarity: 'You develop an instant global consciousness, a people orientation, an intense dissatisfaction with the state of the world, and a compulsion to do something about it. From out there on the moon, international politics look so petty. You want to grab a politician by the scruff of the neck and drag him a quarter of a million miles out and say: Look at that, you son of a gun.'

Experiencing this shift alters the way humans perceive existence, inspiring a deep sense of universal connection and planetary guardianship.
            """.trimIndent()
        ),
        SamplePreset(
            title = "Meditations on Focus & Time",
            category = "Philosophy",
            readingTimeEst = "1 min",
            wordCount = 250,
            content = """
# Marcus Aurelius on Time

Never let the future disturb you. You will meet it, if you have to, with the same weapons of reason which today arm you against the present.

Time is a sort of river of passing events, and strong is its current; no sooner is a thing brought to sight than it is swept away and another takes its place, and this too will be swept away.

# Living in the Present

Do not act as if you were going to live ten thousand years. Death hangs over you. While you live, while it is in your power, be good.

How much time he gains who does not look to see what his neighbor says or does or thinks, but only at what he does himself, to make it just and holy.

Confine yourself to the present. When you arise in the morning, think of what a precious privilege it is to be alive—to breathe, to think, to enjoy, to love.
            """.trimIndent()
        ),
        SamplePreset(
            title = "Quantum Computing & The Next Era",
            category = "Technology",
            readingTimeEst = "2 min",
            wordCount = 390,
            content = """
# The Qubit Paradigm

Quantum computers represent a fundamental shift in how information is processed, shifting the paradigm from classical bits to quantum bits or qubits. 

Unlike classical bits which exist strictly as zeros or ones, qubits leverage quantum superposition to represent both states simultaneously. Furthermore, quantum entanglement allows qubits to share correlated states instantaneously regardless of distance. 

# Real-World Applications

This empowers quantum machines to compute vast combinatorial solution spaces in parallel. Problems in molecular simulation, protein folding, cryptography, and global logistics optimization that would take traditional supercomputers thousands of years can potentially be solved in seconds. 

As we cross the threshold of quantum error correction and fault tolerance, society stands on the brink of revolutionary breakthroughs in drug discovery, clean energy materials, and artificial general intelligence architectures.
            """.trimIndent()
        )
    )
}
