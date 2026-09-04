package com.shumail.rapidreader.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * High-performance EPUB Parser with full OPF manifest / spine ordering and chapter extraction.
 */
object EpubParser {

    suspend fun parse(context: Context, uri: Uri): EpubDocumentResult = withContext(Dispatchers.IO) {
        val localFile = DocumentParsers.saveUriToLocalFile(context, uri, "epub", "epub")
        val zipBytes = localFile.readBytes()

        // 1. Locate container.xml to identify rootfile OPF path
        val opfPath = findEpubOpfPath(zipBytes) ?: "OEBPS/content.opf"
        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

        // 2. Read OPF package file
        val opfContent = readZipEntryAsString(zipBytes, opfPath)
            ?: readZipEntryAsString(zipBytes, "content.opf")
            ?: readZipEntryAsString(zipBytes, "OEBPS/content.opf")

        var title = localFile.nameWithoutExtension.replace(Regex("""^epub_\d+_?"""), "").ifBlank { "EPUB Book" }
        val chapterOrderPaths = mutableListOf<String>()

        if (!opfContent.isNullOrBlank()) {
            // Extract book title
            val titleMatch = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", RegexOption.IGNORE_CASE).find(opfContent)
            if (titleMatch != null && titleMatch.groupValues[1].isNotBlank()) {
                title = TextSanitizer.stripHtml(titleMatch.groupValues[1])
            }

            // Map manifest item id -> href
            val manifestMap = mutableMapOf<String, String>()
            val itemRegex = Regex("""<item\s+[^>]*?id=["']([^"']+)["'][^>]*?href=["']([^"']+)["']|href=["']([^"']+)["'][^>]*?id=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            itemRegex.findAll(opfContent).forEach { match ->
                val id = match.groupValues[1].ifBlank { match.groupValues[4] }
                val href = match.groupValues[2].ifBlank { match.groupValues[3] }
                if (id.isNotBlank() && href.isNotBlank()) {
                    manifestMap[id] = href
                }
            }

            // Parse spine itemrefs to guarantee chronological reading order
            val itemrefRegex = Regex("""<itemref\s+[^>]*?idref=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            itemrefRegex.findAll(opfContent).forEach { match ->
                val idref = match.groupValues[1]
                val href = manifestMap[idref]
                if (href != null && !href.endsWith(".ncx") && !href.endsWith(".css")) {
                    val fullPath = if (href.startsWith("/")) href.removePrefix("/") else opfDir + href
                    chapterOrderPaths.add(fullPath)
                }
            }
        }

        // 3. Fallback: if spine missing or empty, read all .html/.xhtml files from ZIP
        val htmlEntries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && (name.endsWith(".html", ignoreCase = true) || name.endsWith(".xhtml", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true))) {
                    val content = zis.bufferedReader(Charsets.UTF_8).readText()
                    htmlEntries[name] = content
                    htmlEntries[name.removePrefix("/")] = content
                }
                entry = zis.nextEntry
            }
        }

        val chapters = mutableListOf<String>()
        val fullBookBuilder = StringBuilder()

        val orderedHtmlPaths = if (chapterOrderPaths.isNotEmpty()) {
            chapterOrderPaths
        } else {
            htmlEntries.keys.sorted()
        }

        var chapterCounter = 1
        for (path in orderedHtmlPaths) {
            // Check direct match or filename match
            val rawHtml = htmlEntries[path]
                ?: htmlEntries[path.substringAfterLast("/")]
                ?: htmlEntries.entries.firstOrNull { it.key.endsWith(path.substringAfterLast("/")) }?.value

            if (!rawHtml.isNullOrBlank()) {
                val (chapterTitle, chapterBody) = extractChapterFromHtml(rawHtml, chapterCounter)
                if (chapterBody.isNotBlank()) {
                    val formattedChapter = "# $chapterTitle\n\n$chapterBody"
                    chapters.add(formattedChapter)
                    fullBookBuilder.append(formattedChapter).append("\n\n")
                    chapterCounter++
                }
            }
        }

        val bookContent = if (fullBookBuilder.isNotBlank()) {
            fullBookBuilder.toString().trim()
        } else {
            "# $title\n\nCould not extract readable text from EPUB format."
        }

        EpubDocumentResult(
            title = title,
            content = bookContent,
            chapters = chapters,
            localFilePath = localFile.absolutePath
        )
    }

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

    private fun extractChapterFromHtml(html: String, defaultIndex: Int): Pair<String, String> {
        // Detect title in <title>, <h1>, or <h2>
        val headingMatch = Regex("""<h1[^>]*>(.*?)</h1>""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""<h2[^>]*>(.*?)</h2>""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""<title[^>]*>(.*?)</title>""", RegexOption.IGNORE_CASE).find(html)

        val chapterTitle = if (headingMatch != null && headingMatch.groupValues[1].isNotBlank()) {
            TextSanitizer.stripHtml(headingMatch.groupValues[1]).take(80)
        } else {
            "Chapter $defaultIndex"
        }

        val readableText = TextSanitizer.extractReadableBody(html)
        return Pair(chapterTitle, readableText)
    }
}
