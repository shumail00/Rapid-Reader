package com.shumail.rapidreader.engine

data class RsvpWord(
    val original: String,
    val prefix: String,
    val orpChar: String,
    val suffix: String,
    val pauseMultiplier: Float = 1.0f,
    val isParagraphBreak: Boolean = false,
    val sentenceIndex: Int = 0,
    val wordIndex: Int = 0
) {
    val fullDisplay: String
        get() = "$prefix$orpChar$suffix"
}

enum class ReadingThemeMode(val title: String) {
    DYNAMIC("Material You"),
    OLED_DARK("OLED Black"),
    WARM_SEPIA("Warm Sepia"),
    MINT_FOCUS("Mint Focus"),
    SOLARIZED_DARK("Solarized Dark")
}

enum class ReadingFontFamily(val title: String) {
    SANS_SERIF("Sans-Serif (Modern)"),
    SERIF("Serif (Classic)"),
    MONOSPACE("Monospace (Clean)"),
    CURSIVE("Humanist")
}

enum class OrpColorOption(val title: String, val hexCode: Long) {
    DYNAMIC("Material You Dynamic", 0L),
    RED("Vibrant Red", 0xFFFF3B30),
    CORAL("Electric Coral", 0xFFFF6F00),
    EMERALD("Mint Emerald", 0xFF00C853),
    CYAN("Cyan Focus", 0xFF00E5FF),
    MAGENTA("Neon Purple", 0xFFE040FB)
}
