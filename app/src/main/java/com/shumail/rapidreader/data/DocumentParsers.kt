package com.shumail.rapidreader.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class PdfDocumentResult(
    val title: String,
    val content: String,
    val localFilePath: String,
    val pageCount: Int
)

data class EpubDocumentResult(
    val title: String,
    val content: String,
    val chapters: List<String>,
    val localFilePath: String
)

data class WebDocumentResult(
    val title: String,
    val content: String,
    val url: String
)

/**
 * Unified facade for reading and extracting documents.
 * Delegates to modular components: PdfParser, EpubParser, WebScraper, and TextSanitizer.
 */
object DocumentParsers {

    suspend fun readTextFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open stream for URI: $uri")
        inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    suspend fun readPdfFromUri(context: Context, uri: Uri): PdfDocumentResult {
        return PdfParser.parse(context, uri)
    }

    suspend fun readEpubFromUri(context: Context, uri: Uri): EpubDocumentResult {
        return EpubParser.parse(context, uri)
    }

    suspend fun fetchArticleFromWeb(urlStr: String): WebDocumentResult {
        return WebScraper.scrape(urlStr)
    }

    fun stripHtml(html: String): String {
        return TextSanitizer.stripHtml(html)
    }

    fun saveUriToLocalFile(context: Context, uri: Uri, prefix: String, ext: String): File {
        val dir = File(context.filesDir, "imported_docs").apply { mkdirs() }
        val filename = "${prefix}_${System.currentTimeMillis()}.$ext"
        val destFile = File(dir, filename)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Could not read file from URI: $uri")

        return destFile
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
