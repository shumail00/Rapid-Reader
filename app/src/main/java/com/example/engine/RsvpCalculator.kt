package com.example.engine

object RsvpCalculator {

    fun parseTextToRsvpWords(text: String, punctuationPause: Boolean = true): List<RsvpWord> {
        if (text.isBlank()) return emptyList()

        val result = mutableListOf<RsvpWord>()
        val paragraphs = text.split(Regex("""\n+"""))
        var globalWordIndex = 0
        var currentSentenceIndex = 0

        for (paragraph in paragraphs) {
            val rawTokens = paragraph.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
            if (rawTokens.isEmpty()) continue

            for (i in rawTokens.indices) {
                val token = rawTokens[i]
                val isLastInParagraph = (i == rawTokens.lastIndex)

                val orpIndex = calculateOrpIndex(token)
                val prefix = if (orpIndex > 0 && orpIndex <= token.length) token.substring(0, orpIndex) else ""
                val orpChar = if (orpIndex < token.length) token.substring(orpIndex, orpIndex + 1) else ""
                val suffix = if (orpIndex + 1 < token.length) token.substring(orpIndex + 1) else ""

                val multiplier = if (punctuationPause) {
                    calculatePauseMultiplier(token, isLastInParagraph)
                } else {
                    1.0f
                }

                if (token.endsWith(".") || token.endsWith("!") || token.endsWith("?")) {
                    currentSentenceIndex++
                }

                result.add(
                    RsvpWord(
                        original = token,
                        prefix = prefix,
                        orpChar = orpChar,
                        suffix = suffix,
                        pauseMultiplier = multiplier,
                        isParagraphBreak = isLastInParagraph,
                        sentenceIndex = currentSentenceIndex,
                        wordIndex = globalWordIndex++
                    )
                )
            }
        }

        return result
    }

    /**
     * Calculates the Optimal Recognition Point (ORP) index within a token.
     * Spritz research ORP indices based on word length:
     * Length 0-1: 0
     * Length 2-5: 1
     * Length 6-9: 2
     * Length 10-13: 3
     * Length 14+: 4
     */
    fun calculateOrpIndex(token: String): Int {
        if (token.isEmpty()) return 0
        
        // Strip leading punctuation for ORP anchor finding
        var leadingPunctCount = 0
        while (leadingPunctCount < token.length && !token[leadingPunctCount].isLetterOrDigit()) {
            leadingPunctCount++
        }
        
        val effectiveLength = token.length - leadingPunctCount
        val relativeOrp = when {
            effectiveLength <= 1 -> 0
            effectiveLength in 2..5 -> 1
            effectiveLength in 6..9 -> 2
            effectiveLength in 10..13 -> 3
            else -> 4
        }
        
        return (leadingPunctCount + relativeOrp).coerceIn(0, token.length - 1)
    }

    /**
     * Variable dwell math for RSVP speed reading:
     * - Words with ., !, ? get 2.0x duration.
     * - Words with ,, ; get 1.5x duration.
     * - Words longer than 8 letters get 1.2x duration.
     */
    fun calculatePauseMultiplier(token: String, isLastInParagraph: Boolean = false): Float {
        var multiplier = 1.0f

        // 1. Words with ., !, ? get 2x duration
        if (token.any { it == '.' || it == '!' || it == '?' || it == '…' }) {
            multiplier = 2.0f
        }
        // 2. Words with ,, ; get 1.5x duration
        else if (token.any { it == ',' || it == ';' || it == ':' || it == '—' }) {
            multiplier = 1.5f
        }

        // 3. Words longer than 8 letters get 1.2x duration
        val letterCount = token.count { it.isLetterOrDigit() }
        if (letterCount > 8) {
            multiplier *= 1.2f
        }

        if (isLastInParagraph) {
            multiplier = maxOf(multiplier, 2.5f)
        }

        return multiplier
    }

    /**
     * Estimates reading time remaining in seconds taking punctuation pauses and WPM into account.
     */
    fun estimateTimeRemainingSeconds(words: List<RsvpWord>, startIndex: Int, wpm: Int): Int {
        if (startIndex >= words.size || wpm <= 0) return 0
        val baseMsPerWord = 60000.0 / wpm.toDouble()
        var totalMs = 0.0
        for (i in startIndex until words.size) {
            totalMs += baseMsPerWord * words[i].pauseMultiplier
        }
        return (totalMs / 1000.0).toInt()
    }

    fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) {
            "${mins}m ${secs}s"
        } else {
            "${secs}s"
        }
    }

    /**
     * Extracts chapters from parsed words and original text.
     */
    fun extractChapters(rawText: String, words: List<RsvpWord>): List<DocumentChapter> {
        if (words.isEmpty()) return emptyList()

        val chapters = mutableListOf<DocumentChapter>()
        val paragraphs = rawText.split(Regex("""\n+""")).filter { it.isNotBlank() }

        // Find chapter boundaries
        val chapterRegex = Regex("""^(?:#+\s*|CHAPTER\s+\d+|Chapter\s+\d+|Part\s+\d+|Section\s+\d+|Page\s+\d+|===|==)(.*)""", RegexOption.IGNORE_CASE)
        
        var wordCounter = 0
        var currentChapterTitle = "Chapter 1: Beginning"
        var currentChapterStart = 0
        var currentChapterText = StringBuilder()
        var chapterIndex = 1

        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            val tokens = trimmed.split(Regex("""\s+""")).filter { it.isNotBlank() }
            if (tokens.isEmpty()) continue

            val isHeading = chapterRegex.containsMatchIn(trimmed) || 
                (trimmed.length < 50 && (trimmed.startsWith("Chapter") || trimmed.startsWith("CHAPTER") || trimmed.startsWith("#")))

            if (isHeading && wordCounter > currentChapterStart + 40) {
                // Close previous chapter
                val endIdx = (wordCounter - 1).coerceAtLeast(currentChapterStart)
                val count = endIdx - currentChapterStart + 1
                chapters.add(
                    DocumentChapter(
                        id = chapterIndex,
                        title = currentChapterTitle,
                        startWordIndex = currentChapterStart,
                        endWordIndex = endIdx,
                        wordCount = count,
                        previewText = currentChapterText.toString().take(120).trim()
                    )
                )
                chapterIndex++
                currentChapterTitle = trimmed.replace(Regex("""^#+\s*|===|==\s*"""), "").trim().ifBlank { "Chapter $chapterIndex" }
                currentChapterStart = wordCounter
                currentChapterText.clear()
            }

            currentChapterText.append(trimmed).append("\n\n")
            wordCounter += tokens.size
        }

        // Add last or single chapter
        val lastEnd = (words.size - 1).coerceAtLeast(currentChapterStart)
        val lastCount = lastEnd - currentChapterStart + 1
        chapters.add(
            DocumentChapter(
                id = chapterIndex,
                title = currentChapterTitle,
                startWordIndex = currentChapterStart,
                endWordIndex = lastEnd,
                wordCount = lastCount,
                previewText = currentChapterText.toString().take(120).trim()
            )
        )

        // If only 1 chapter exists and document is long (> 350 words), segment into logical readable chapters
        if (chapters.size == 1 && words.size > 350) {
            chapters.clear()
            val targetChunkWords = 200
            var cIdx = 1
            var startW = 0
            while (startW < words.size) {
                val endW = (startW + targetChunkWords).coerceAtMost(words.size - 1)
                val preview = words.subList(startW, (startW + 15).coerceAtMost(words.size)).joinToString(" ") { it.fullDisplay }
                val titlePreview = words.subList(startW, (startW + 5).coerceAtMost(words.size)).joinToString(" ") { it.original }
                    .replace(Regex("""[^\w\s]"""), "").take(28).trim()
                
                chapters.add(
                    DocumentChapter(
                        id = cIdx,
                        title = "Chapter $cIdx: $titlePreview...",
                        startWordIndex = startW,
                        endWordIndex = endW,
                        wordCount = endW - startW + 1,
                        previewText = preview
                    )
                )
                cIdx++
                startW = endW + 1
            }
        }

        return chapters
    }
}
