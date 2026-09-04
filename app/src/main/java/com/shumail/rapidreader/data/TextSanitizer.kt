package com.shumail.rapidreader.data

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * Text sanitization and extraction utilities for PDF, HTML/Web, and EPUB.
 */
object TextSanitizer {

    /**
     * Strips HTML tags and unescapes common HTML entities.
     */
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

    /**
     * Cleans HTML body content, converting headers into Markdown headings and removing scripts/styles/nav elements.
     */
    fun extractReadableBody(html: String): String {
        var content = html
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
        
        val text = stripHtml(content)
        
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && (it.startsWith("#") || it.length > 15) }
            .joinToString("\n\n")
    }

    /**
     * Cleans PDF literal string escape characters and octal bytes.
     */
    fun cleanPdfEscapeChars(text: String): String {
        var res = text
            .replace("\\n", "\n")
            .replace("\\r", "")
            .replace("\\t", " ")
            .replace("\\b", "")
            .replace("\\f", "")
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")

        val octalRegex = Regex("""\\([0-7]{1,3})""")
        res = octalRegex.replace(res) { m ->
            try {
                val code = m.groupValues[1].toInt(8)
                if (code in 32..126 || code in 160..255) code.toChar().toString() else " "
            } catch (_: Exception) {
                " "
            }
        }

        val cleanChars = StringBuilder()
        for (c in res) {
            if (c.isLetterOrDigit() || c.isWhitespace() || c in "!?,.:;\"'()—-–/&@#%*+=[]{}<>%$") {
                cleanChars.append(c)
            }
        }
        return cleanChars.toString()
    }

    /**
     * Decodes PDF hexadecimal string using CMap lookup table or fallback encodings.
     */
    fun decodePdfHexString(rawHex: String, cMap: Map<String, String>): String {
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

    /**
     * Parses PDF /ToUnicode CMap tables into a character mapping.
     */
    fun parseToUnicodeCMap(cmapContent: String, cMap: MutableMap<String, String>) {
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

        val bfRangeRegex = Regex("""(\d+)\s+beginbfrange\s*(.*?)\s*endbfrange""", RegexOption.DOT_MATCHES_ALL)
        bfRangeRegex.findAll(cmapContent).forEach { match ->
            val inner = match.groupValues[2]
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

    fun indexOfSubarray(array: ByteArray, target: ByteArray, start: Int): Int {
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

    fun tryDecompressFlate(data: ByteArray): ByteArray? {
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
}
