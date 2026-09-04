package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Robust Web Article Scraper with metadata extraction and content cleaning.
 */
object WebScraper {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun scrape(urlStr: String): WebDocumentResult = withContext(Dispatchers.IO) {
        val validUrl = if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            "https://$urlStr"
        } else {
            urlStr
        }

        val request = Request.Builder()
            .url(validUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("HTTP error ${response.code}: ${response.message}")
        }

        val html = response.body?.string() ?: throw IOException("Empty response body from URL")

        // 1. Extract Title
        val titleMatch = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.IGNORE_CASE).find(html)
        var title = titleMatch?.groupValues?.get(1)?.let { TextSanitizer.stripHtml(it) } ?: "Web Article"
        if (title.contains("|")) title = title.substringBeforeLast("|").trim()
        if (title.contains("—")) title = title.substringBeforeLast("—").trim()
        if (title.contains(" - ")) title = title.substringBeforeLast(" - ").trim()
        if (title.isBlank()) title = "Web Article"

        // 2. Extract and sanitize clean readable body
        val cleanedText = TextSanitizer.extractReadableBody(html)
        if (cleanedText.isBlank() || cleanedText.length < 30) {
            throw IllegalStateException("Could not extract readable article text from this web page")
        }

        WebDocumentResult(
            title = title,
            content = cleanedText,
            url = validUrl
        )
    }
}
