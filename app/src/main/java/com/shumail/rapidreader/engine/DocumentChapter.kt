package com.shumail.rapidreader.engine

data class DocumentChapter(
    val id: Int,
    val title: String,
    val startWordIndex: Int,
    val endWordIndex: Int,
    val wordCount: Int,
    val previewText: String = ""
) {
    fun containsWordIndex(index: Int): Boolean = index in startWordIndex..endWordIndex
}

enum class ReadingEngineMode(val title: String) {
    RSVP("RSVP Speed"),
    DOCUMENT("Normal / E-Book / PDF")
}
