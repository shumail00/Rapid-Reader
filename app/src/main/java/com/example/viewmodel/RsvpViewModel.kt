package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CoverImageManager
import com.example.data.DocumentParsers
import com.example.data.ReadingDocument
import com.example.data.ReadingSession
import com.example.data.SamplePreset
import com.example.engine.DocumentChapter
import com.example.engine.OrpColorOption
import com.example.engine.ReadingEngineMode
import com.example.engine.ReadingFontFamily
import com.example.engine.ReadingThemeMode
import com.example.engine.RsvpCalculator
import com.example.engine.RsvpWord
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScreenDestination {
    HOME,
    READER,
    COMPLETION_SUMMARY
}

data class RsvpUiState(
    val currentScreen: ScreenDestination = ScreenDestination.HOME,
    val activeDocumentId: Long? = null,
    val activeTitle: String = "",
    val activeSourceType: String = "typed", // "pdf", "epub", "web", "txt", "typed", "preset"
    val rawTextContent: String = "",
    val activeLocalFilePath: String? = null,
    val activeWebUrl: String? = null,
    val readingEngineMode: ReadingEngineMode = ReadingEngineMode.RSVP,
    val isPdfPageViewMode: Boolean = true, // When in PDF document view: true = Google Drive page-by-page renderer, false = word-by-word interactive text
    val isWebContainerMode: Boolean = false, // When in Web document view: true = live WebContainer, false = text/rsvp
    val pdfCurrentPage: Int = 0,
    val pdfTotalPages: Int = 0,
    val chapters: List<DocumentChapter> = emptyList(),
    val words: List<RsvpWord> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isCompleted: Boolean = false,
    val wpm: Int = 350,
    val chunkSize: Int = 1,
    val themeMode: ReadingThemeMode = ReadingThemeMode.DYNAMIC,
    val fontFamily: ReadingFontFamily = ReadingFontFamily.SANS_SERIF,
    val fontSizeSp: Float = 44f,
    val orpColor: OrpColorOption = OrpColorOption.DYNAMIC,
    val showGuides: Boolean = true,
    val showContextBar: Boolean = true,
    val rewindOnResume: Boolean = true,
    val punctuationPauseEnabled: Boolean = true,
    val isZenMode: Boolean = false,
    val isFocusMode: Boolean = false,
    val isHoldingToSlow: Boolean = false,
    val dynamicWpm: Int = 350,
    val isActiveSaved: Boolean = false,
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val errorMessage: String? = null,
    // Session Statistics
    val sessionWordsRead: Int = 0,
    val sessionStartTime: Long = 0L,
    val sessionDurationSeconds: Long = 0L
) {
    val currentWord: RsvpWord?
        get() = if (words.isNotEmpty() && currentIndex in words.indices) words[currentIndex] else null

    val activeChapter: DocumentChapter?
        get() = chapters.firstOrNull { it.containsWordIndex(currentIndex) } ?: chapters.firstOrNull()

    val activeChapterIndex: Int
        get() {
            val idx = chapters.indexOfFirst { it.containsWordIndex(currentIndex) }
            return if (idx >= 0) idx else 0
        }

    val currentChunk: List<RsvpWord>
        get() {
            if (words.isEmpty() || currentIndex !in words.indices) return emptyList()
            val end = (currentIndex + chunkSize).coerceAtMost(words.size)
            return words.subList(currentIndex, end)
        }

    val progress: Float
        get() = if (words.isNotEmpty()) (currentIndex.toFloat() / words.size.toFloat()).coerceIn(0f, 1f) else 0f

    val timeRemainingFormatted: String
        get() {
            val seconds = RsvpCalculator.estimateTimeRemainingSeconds(words, currentIndex, wpm)
            return RsvpCalculator.formatDuration(seconds)
        }

    val contextBefore: String
        get() {
            if (words.isEmpty() || currentIndex <= 0) return ""
            val start = (currentIndex - 3).coerceAtLeast(0)
            return words.subList(start, currentIndex).joinToString(" ") { it.fullDisplay }
        }

    val contextAfter: String
        get() {
            if (words.isEmpty() || currentIndex + chunkSize >= words.size) return ""
            val start = currentIndex + chunkSize
            val end = (start + 3).coerceAtMost(words.size)
            return words.subList(start, end).joinToString(" ") { it.fullDisplay }
        }
}

class RsvpViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val readingDao = db.readingDao()

    val savedDocuments: StateFlow<List<ReadingDocument>> = readingDao.getAllDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readingSessions: StateFlow<List<ReadingSession>> = readingDao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(RsvpUiState())
    val uiState: StateFlow<RsvpUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var playStartTime: Long = 0L
    private var currentRunningWpm: Float = 350f

    fun loadPreset(preset: SamplePreset) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Preparing reading drill...") }
            val parsedWords = RsvpCalculator.parseTextToRsvpWords(preset.content, _uiState.value.punctuationPauseEnabled)
            val extractedChapters = RsvpCalculator.extractChapters(preset.content, parsedWords)
            
            val doc = ReadingDocument(
                title = preset.title,
                content = preset.content,
                sourceType = "preset",
                currentWordIndex = 0,
                totalWords = parsedWords.size,
                preferredWpm = _uiState.value.wpm
            )
            val insertedId = readingDao.insertDocument(doc)

            _uiState.update {
                it.copy(
                    currentScreen = ScreenDestination.READER,
                    activeDocumentId = insertedId,
                    activeTitle = preset.title,
                    activeSourceType = "preset",
                    rawTextContent = preset.content,
                    activeLocalFilePath = null,
                    activeWebUrl = null,
                    readingEngineMode = ReadingEngineMode.RSVP,
                    chapters = extractedChapters,
                    words = parsedWords,
                    currentIndex = 0,
                    isPlaying = false,
                    isCompleted = false,
                    isLoading = false,
                    loadingMessage = null,
                    sessionWordsRead = 0,
                    sessionStartTime = System.currentTimeMillis()
                )
            }
        }
    }

    fun loadDirectText(title: String, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Processing text...") }
            val parsedWords = RsvpCalculator.parseTextToRsvpWords(content, _uiState.value.punctuationPauseEnabled)
            val extractedChapters = RsvpCalculator.extractChapters(content, parsedWords)
            val effectiveTitle = title.ifBlank { "Direct Text (${parsedWords.take(4).joinToString(" ") { it.fullDisplay }}...)" }
            
            val doc = ReadingDocument(
                title = effectiveTitle,
                content = content,
                sourceType = "typed",
                currentWordIndex = 0,
                totalWords = parsedWords.size,
                preferredWpm = _uiState.value.wpm
            )
            val insertedId = readingDao.insertDocument(doc)

            _uiState.update {
                it.copy(
                    currentScreen = ScreenDestination.READER,
                    activeDocumentId = insertedId,
                    activeTitle = effectiveTitle,
                    activeSourceType = "typed",
                    rawTextContent = content,
                    activeLocalFilePath = null,
                    activeWebUrl = null,
                    readingEngineMode = ReadingEngineMode.RSVP,
                    chapters = extractedChapters,
                    words = parsedWords,
                    currentIndex = 0,
                    isPlaying = false,
                    isCompleted = false,
                    isLoading = false,
                    loadingMessage = null,
                    sessionWordsRead = 0,
                    sessionStartTime = System.currentTimeMillis()
                )
            }
        }
    }

    fun loadFromUri(uri: Uri, type: String, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Opening $type document...") }
            try {
                when (type.lowercase()) {
                    "pdf" -> {
                        val pdfResult = DocumentParsers.readPdfFromUri(context, uri)
                        val originalFileName = getFileNameFromUri(context, uri) ?: pdfResult.title
                        val parsedWords = RsvpCalculator.parseTextToRsvpWords(pdfResult.content, _uiState.value.punctuationPauseEnabled)
                        val extractedChapters = RsvpCalculator.extractChapters(pdfResult.content, parsedWords)

                        // Render first page thumbnail via Android's native PdfRenderer into internal cache/storage
                        val firstPageCoverPath = CoverImageManager.generatePdfFirstPageThumbnail(context, File(pdfResult.localFilePath))

                        val doc = ReadingDocument(
                            title = originalFileName,
                            content = pdfResult.content,
                            sourceType = "pdf",
                            currentWordIndex = 0,
                            totalWords = parsedWords.size,
                            preferredWpm = _uiState.value.wpm,
                            coverImagePath = firstPageCoverPath,
                            localFilePath = pdfResult.localFilePath
                        )
                        val insertedId = readingDao.insertDocument(doc)

                        _uiState.update {
                            it.copy(
                                currentScreen = ScreenDestination.READER,
                                activeDocumentId = insertedId,
                                activeTitle = originalFileName,
                                activeSourceType = "pdf",
                                rawTextContent = pdfResult.content,
                                activeLocalFilePath = pdfResult.localFilePath,
                                activeWebUrl = null,
                                readingEngineMode = ReadingEngineMode.DOCUMENT,
                                isPdfPageViewMode = true,
                                pdfTotalPages = pdfResult.pageCount,
                                pdfCurrentPage = 0,
                                chapters = extractedChapters,
                                words = parsedWords,
                                currentIndex = 0,
                                isPlaying = false,
                                isCompleted = false,
                                isLoading = false,
                                loadingMessage = null,
                                errorMessage = null,
                                sessionWordsRead = 0,
                                sessionStartTime = System.currentTimeMillis()
                            )
                        }
                    }
                    "epub" -> {
                        val epubResult = DocumentParsers.readEpubFromUri(context, uri)
                        val originalFileName = getFileNameFromUri(context, uri) ?: epubResult.title
                        val parsedWords = RsvpCalculator.parseTextToRsvpWords(epubResult.content, _uiState.value.punctuationPauseEnabled)
                        val extractedChapters = RsvpCalculator.extractChapters(epubResult.content, parsedWords)

                        // Extract embedded cover image from EPUB manifest/archive
                        val extractedCoverPath = CoverImageManager.extractEpubCoverImage(context, File(epubResult.localFilePath))

                        val doc = ReadingDocument(
                            title = originalFileName,
                            content = epubResult.content,
                            sourceType = "epub",
                            currentWordIndex = 0,
                            totalWords = parsedWords.size,
                            preferredWpm = _uiState.value.wpm,
                            coverImagePath = extractedCoverPath,
                            localFilePath = epubResult.localFilePath
                        )
                        val insertedId = readingDao.insertDocument(doc)

                        _uiState.update {
                            it.copy(
                                currentScreen = ScreenDestination.READER,
                                activeDocumentId = insertedId,
                                activeTitle = originalFileName,
                                activeSourceType = "epub",
                                rawTextContent = epubResult.content,
                                activeLocalFilePath = epubResult.localFilePath,
                                activeWebUrl = null,
                                readingEngineMode = ReadingEngineMode.DOCUMENT,
                                chapters = extractedChapters,
                                words = parsedWords,
                                currentIndex = 0,
                                isPlaying = false,
                                isCompleted = false,
                                isLoading = false,
                                loadingMessage = null,
                                errorMessage = null,
                                sessionWordsRead = 0,
                                sessionStartTime = System.currentTimeMillis()
                            )
                        }
                    }
                    else -> {
                        val content = DocumentParsers.readTextFromUri(context, uri)
                        val fileName = getFileNameFromUri(context, uri) ?: "Document ($type)"
                        val parsedWords = RsvpCalculator.parseTextToRsvpWords(content, _uiState.value.punctuationPauseEnabled)
                        val extractedChapters = RsvpCalculator.extractChapters(content, parsedWords)

                        val doc = ReadingDocument(
                            title = fileName,
                            content = content,
                            sourceType = type,
                            currentWordIndex = 0,
                            totalWords = parsedWords.size,
                            preferredWpm = _uiState.value.wpm
                        )
                        val insertedId = readingDao.insertDocument(doc)

                        _uiState.update {
                            it.copy(
                                currentScreen = ScreenDestination.READER,
                                activeDocumentId = insertedId,
                                activeTitle = fileName,
                                activeSourceType = type,
                                rawTextContent = content,
                                activeLocalFilePath = null,
                                activeWebUrl = null,
                                readingEngineMode = ReadingEngineMode.RSVP,
                                chapters = extractedChapters,
                                words = parsedWords,
                                currentIndex = 0,
                                isPlaying = false,
                                isCompleted = false,
                                isLoading = false,
                                loadingMessage = null,
                                errorMessage = null,
                                sessionWordsRead = 0,
                                sessionStartTime = System.currentTimeMillis()
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to open $type file: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun loadFromWeb(url: String, openInWebContainer: Boolean = false) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Loading web page...") }
            try {
                val webResult = DocumentParsers.fetchArticleFromWeb(url)
                val parsedWords = RsvpCalculator.parseTextToRsvpWords(webResult.content, _uiState.value.punctuationPauseEnabled)
                val extractedChapters = RsvpCalculator.extractChapters(webResult.content, parsedWords)
                
                val doc = ReadingDocument(
                    title = webResult.title,
                    content = webResult.content,
                    sourceType = "web",
                    currentWordIndex = 0,
                    totalWords = parsedWords.size,
                    preferredWpm = _uiState.value.wpm,
                    webUrl = webResult.url
                )
                val insertedId = readingDao.insertDocument(doc)

                _uiState.update {
                    it.copy(
                        currentScreen = ScreenDestination.READER,
                        activeDocumentId = insertedId,
                        activeTitle = webResult.title,
                        activeSourceType = "web",
                        rawTextContent = webResult.content,
                        activeLocalFilePath = null,
                        activeWebUrl = webResult.url,
                        readingEngineMode = if (openInWebContainer) ReadingEngineMode.DOCUMENT else ReadingEngineMode.RSVP,
                        isWebContainerMode = openInWebContainer,
                        chapters = extractedChapters,
                        words = parsedWords,
                        currentIndex = 0,
                        isPlaying = false,
                        isCompleted = false,
                        isLoading = false,
                        loadingMessage = null,
                        errorMessage = null,
                        sessionWordsRead = 0,
                        sessionStartTime = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load web article: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun resumeDocument(doc: ReadingDocument) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Resuming document...") }
            val parsedWords = RsvpCalculator.parseTextToRsvpWords(doc.content, _uiState.value.punctuationPauseEnabled)
            val extractedChapters = RsvpCalculator.extractChapters(doc.content, parsedWords)
            val startIndex = doc.currentWordIndex.coerceIn(0, (parsedWords.size - 1).coerceAtLeast(0))

            _uiState.update {
                it.copy(
                    currentScreen = ScreenDestination.READER,
                    activeDocumentId = doc.id,
                    activeTitle = doc.title,
                    activeSourceType = doc.sourceType,
                    rawTextContent = doc.content,
                    activeLocalFilePath = doc.localFilePath,
                    activeWebUrl = doc.webUrl,
                    readingEngineMode = if (doc.sourceType == "pdf") ReadingEngineMode.DOCUMENT else ReadingEngineMode.RSVP,
                    isPdfPageViewMode = doc.sourceType == "pdf",
                    isWebContainerMode = false,
                    chapters = extractedChapters,
                    words = parsedWords,
                    currentIndex = startIndex,
                    wpm = doc.preferredWpm,
                    isPlaying = false,
                    isCompleted = doc.isCompleted,
                    isLoading = false,
                    loadingMessage = null,
                    sessionWordsRead = 0,
                    sessionStartTime = System.currentTimeMillis()
                )
            }
        }
    }

    fun setReadingEngineMode(mode: ReadingEngineMode) {
        if (mode == ReadingEngineMode.DOCUMENT && _uiState.value.isPlaying) {
            pause()
        }
        _uiState.update { it.copy(readingEngineMode = mode) }
    }

    fun toggleReadingEngineMode() {
        val nextMode = if (_uiState.value.readingEngineMode == ReadingEngineMode.RSVP) {
            ReadingEngineMode.DOCUMENT
        } else {
            ReadingEngineMode.RSVP
        }
        setReadingEngineMode(nextMode)
    }

    fun togglePdfPageViewMode() {
        _uiState.update { it.copy(isPdfPageViewMode = !it.isPdfPageViewMode) }
    }

    fun toggleWebContainerMode() {
        _uiState.update { it.copy(isWebContainerMode = !it.isWebContainerMode) }
    }

    fun startRsvpFromPdfPage(pageIndex: Int) {
        val chapters = _uiState.value.chapters
        // Try finding chapter matching this page
        val targetChapter = chapters.getOrNull(pageIndex) ?: chapters.firstOrNull()
        val targetWordIndex = targetChapter?.startWordIndex ?: 0
        jumpToWordAndStartRsvp(targetWordIndex)
    }

    fun jumpToWordAndStartRsvp(wordIndex: Int) {
        val maxIndex = (_uiState.value.words.size - 1).coerceAtLeast(0)
        val target = wordIndex.coerceIn(0, maxIndex)
        _uiState.update {
            it.copy(
                currentIndex = target,
                readingEngineMode = ReadingEngineMode.RSVP,
                isCompleted = false
            )
        }
        saveCurrentProgress()
        play()
    }

    fun jumpToWordInDocument(wordIndex: Int) {
        val maxIndex = (_uiState.value.words.size - 1).coerceAtLeast(0)
        val target = wordIndex.coerceIn(0, maxIndex)
        _uiState.update { it.copy(currentIndex = target) }
        saveCurrentProgress()
    }

    fun jumpToChapter(chapter: DocumentChapter) {
        val target = chapter.startWordIndex.coerceIn(0, (_uiState.value.words.size - 1).coerceAtLeast(0))
        _uiState.update {
            it.copy(
                currentIndex = target,
                isCompleted = false
            )
        }
        saveCurrentProgress()
    }

    fun deleteDocument(docId: Long) {
        viewModelScope.launch {
            try {
                val doc = readingDao.getDocumentById(docId)
                doc?.localFilePath?.let { path ->
                    val f = File(path)
                    if (f.exists()) f.delete()
                }
                doc?.coverImagePath?.let { path ->
                    val f = File(path)
                    if (f.exists()) f.delete()
                }
            } catch (ignored: Exception) {}
            readingDao.deleteDocumentById(docId)
        }
    }

    fun updateDocumentCover(docId: Long, imageUri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val oldDoc = readingDao.getDocumentById(docId)
                val persistentCoverPath = CoverImageManager.persistCustomCoverFromUri(context, imageUri, docId)
                if (persistentCoverPath != null) {
                    if (oldDoc?.coverImagePath != null && oldDoc.coverImagePath != persistentCoverPath) {
                        CoverImageManager.deleteCoverFile(oldDoc.coverImagePath)
                    }
                    readingDao.updateCoverImage(docId, persistentCoverPath)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to save cover: ${e.localizedMessage}") }
            }
        }
    }

    fun removeDocumentCover(docId: Long) {
        viewModelScope.launch {
            try {
                val doc = readingDao.getDocumentById(docId)
                CoverImageManager.deleteCoverFile(doc?.coverImagePath)
                readingDao.updateCoverImage(docId, null)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to remove cover: ${e.localizedMessage}") }
            }
        }
    }

    fun resetProgress(docId: Long) {
        viewModelScope.launch {
            readingDao.updateProgress(docId, 0, System.currentTimeMillis(), false)
        }
    }

    fun clearAllReadingSessions() {
        viewModelScope.launch {
            readingDao.clearAllSessions()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun toggleFavorite(docId: Long, isFavorite: Boolean? = null) {
        viewModelScope.launch {
            val targetFavorite = if (isFavorite != null) {
                isFavorite
            } else {
                val doc = readingDao.getDocumentById(docId) ?: return@launch
                !doc.isFavorite
            }
            readingDao.toggleFavorite(docId, targetFavorite)
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun onHoldToSlowStart() {
        _uiState.update { it.copy(isHoldingToSlow = true) }
    }

    fun onHoldToSlowEnd() {
        _uiState.update { it.copy(isHoldingToSlow = false) }
    }

    fun play() {
        val state = _uiState.value
        if (state.words.isEmpty()) return

        var startIndex = state.currentIndex
        // If completed or at end, start from beginning
        if (state.isCompleted || startIndex >= state.words.size) {
            startIndex = 0
        } else if (state.rewindOnResume && startIndex > 3) {
            // Rewind 3-5 words for immediate context recovery
            startIndex = (startIndex - 4).coerceAtLeast(0)
        }

        playStartTime = System.currentTimeMillis()
        currentRunningWpm = (_uiState.value.wpm * 0.45f).coerceIn(120f, 240f)

        _uiState.update {
            it.copy(
                isPlaying = true,
                currentIndex = startIndex,
                isCompleted = false,
                sessionStartTime = System.currentTimeMillis()
            )
        }

        startPlaybackLoop()
    }

    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false) }
        saveCurrentProgress()
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false, currentIndex = 0, isCompleted = false) }
        saveCurrentProgress()
    }

    fun skipForward(count: Int = 10) {
        val maxIndex = (_uiState.value.words.size - 1).coerceAtLeast(0)
        val newIndex = (_uiState.value.currentIndex + count).coerceIn(0, maxIndex)
        _uiState.update { it.copy(currentIndex = newIndex) }
        saveCurrentProgress()
    }

    fun skipBackward(count: Int = 10) {
        val maxIndex = (_uiState.value.words.size - 1).coerceAtLeast(0)
        val newIndex = (_uiState.value.currentIndex - count).coerceIn(0, maxIndex)
        _uiState.update { it.copy(currentIndex = newIndex) }
        saveCurrentProgress()
    }

    fun seekTo(index: Int) {
        val maxIndex = (_uiState.value.words.size - 1).coerceAtLeast(0)
        val target = index.coerceIn(0, maxIndex)
        _uiState.update { it.copy(currentIndex = target) }
        saveCurrentProgress()
    }

    fun setWpm(newWpm: Int) {
        val clamped = newWpm.coerceIn(100, 1500)
        _uiState.update { it.copy(wpm = clamped) }
        saveCurrentProgress()
    }

    fun setChunkSize(size: Int) {
        _uiState.update { it.copy(chunkSize = size.coerceIn(1, 3)) }
    }

    fun setThemeMode(mode: ReadingThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setFontFamily(font: ReadingFontFamily) {
        _uiState.update { it.copy(fontFamily = font) }
    }

    fun setFontSize(sizeSp: Float) {
        _uiState.update { it.copy(fontSizeSp = sizeSp.coerceIn(24f, 72f)) }
    }

    fun setOrpColor(color: OrpColorOption) {
        _uiState.update { it.copy(orpColor = color) }
    }

    fun toggleGuides() {
        _uiState.update { it.copy(showGuides = !it.showGuides) }
    }

    fun toggleContextBar() {
        _uiState.update { it.copy(showContextBar = !it.showContextBar) }
    }

    fun toggleRewindOnResume() {
        _uiState.update { it.copy(rewindOnResume = !it.rewindOnResume) }
    }

    fun togglePunctuationPause() {
        val newSetting = !_uiState.value.punctuationPauseEnabled
        _uiState.update { it.copy(punctuationPauseEnabled = newSetting) }
        val currentWords = _uiState.value.words
        if (currentWords.isNotEmpty()) {
            val text = currentWords.joinToString(" ") { it.fullDisplay }
            val reParsed = RsvpCalculator.parseTextToRsvpWords(text, newSetting)
            _uiState.update { it.copy(words = reParsed) }
        }
    }

    fun toggleZenMode() {
        _uiState.update { it.copy(isZenMode = !it.isZenMode, isFocusMode = !it.isZenMode) }
    }

    fun toggleFocusMode() {
        _uiState.update { it.copy(isFocusMode = !it.isFocusMode, isZenMode = !it.isFocusMode) }
    }

    fun setFocusMode(enabled: Boolean) {
        _uiState.update { it.copy(isFocusMode = enabled, isZenMode = enabled) }
    }

    fun resetAllProgress() {
        viewModelScope.launch {
            readingDao.clearAllSessions()
            _uiState.update {
                it.copy(
                    sessionWordsRead = 0,
                    currentIndex = 0,
                    isCompleted = false
                )
            }
        }
    }

    fun toggleSaveActiveDocument() {
        val docId = _uiState.value.activeDocumentId ?: return
        val newSaved = !_uiState.value.isActiveSaved
        _uiState.update { it.copy(isActiveSaved = newSaved) }
        viewModelScope.launch {
            readingDao.toggleFavorite(docId, newSaved)
        }
    }

    fun backToHome() {
        pause()
        _uiState.update { it.copy(currentScreen = ScreenDestination.HOME, isFocusMode = false, isZenMode = false) }
    }

    fun restartFromBeginning() {
        _uiState.update {
            it.copy(
                currentScreen = ScreenDestination.READER,
                currentIndex = 0,
                isCompleted = false,
                isPlaying = false,
                sessionWordsRead = 0,
                sessionStartTime = System.currentTimeMillis()
            )
        }
        play()
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (_uiState.value.isPlaying) {
                val state = _uiState.value
                val words = state.words
                val index = state.currentIndex
                val chunkSize = state.chunkSize
                val targetWpm = state.wpm.toFloat()

                if (index >= words.size) {
                    _uiState.update {
                        it.copy(
                            isPlaying = false,
                            isCompleted = true,
                            currentScreen = ScreenDestination.COMPLETION_SUMMARY,
                            sessionDurationSeconds = ((System.currentTimeMillis() - it.sessionStartTime) / 1000).coerceAtLeast(1)
                        )
                    }
                    saveCurrentProgress()
                    break
                }

                val now = System.currentTimeMillis()
                val elapsedSincePlay = now - playStartTime

                val effectiveTargetWpm = if (state.isHoldingToSlow) {
                    (targetWpm * 0.40f).coerceIn(110f, 200f)
                } else if (elapsedSincePlay < 2000L) {
                    val warmupRatio = (elapsedSincePlay / 2000.0).toFloat().coerceIn(0f, 1f)
                    val easeOutProgress = 1f - (1f - warmupRatio) * (1f - warmupRatio)
                    val initialWarmupWpm = (targetWpm * 0.48f).coerceIn(120f, 220f)
                    initialWarmupWpm + (targetWpm - initialWarmupWpm) * easeOutProgress
                } else {
                    targetWpm
                }

                currentRunningWpm += (effectiveTargetWpm - currentRunningWpm) * 0.32f
                val calculatedWpm = currentRunningWpm.coerceIn(80f, 1500f)

                if (Math.abs(state.dynamicWpm - calculatedWpm.toInt()) >= 5) {
                    _uiState.update { it.copy(dynamicWpm = calculatedWpm.toInt()) }
                }

                val currentWord = words[index]
                val baseMsPerWord = 60000.0 / calculatedWpm.toDouble()
                val wordMultiplier = if (state.punctuationPauseEnabled) {
                    RsvpCalculator.calculatePauseMultiplier(currentWord.original, currentWord.isParagraphBreak)
                } else {
                    val letterCount = currentWord.original.count { it.isLetterOrDigit() }
                    if (letterCount > 8) 1.2f else 1.0f
                }
                val sleepDurationMs = (baseMsPerWord * wordMultiplier * chunkSize).toLong().coerceAtLeast(20L)

                delay(sleepDurationMs)

                val nextIndex = index + chunkSize
                _uiState.update {
                    it.copy(
                        currentIndex = nextIndex.coerceAtMost(words.size),
                        sessionWordsRead = it.sessionWordsRead + chunkSize
                    )
                }

                if (nextIndex >= words.size) {
                    _uiState.update {
                        it.copy(
                            isPlaying = false,
                            isCompleted = true,
                            currentScreen = ScreenDestination.COMPLETION_SUMMARY,
                            sessionDurationSeconds = ((System.currentTimeMillis() - it.sessionStartTime) / 1000).coerceAtLeast(1)
                        )
                    }
                    saveCurrentProgress()
                    break
                }
            }
        }
    }

    private fun saveCurrentProgress() {
        val state = _uiState.value
        val docId = state.activeDocumentId ?: return
        viewModelScope.launch {
            readingDao.updateProgress(
                id = docId,
                index = state.currentIndex,
                timestamp = System.currentTimeMillis(),
                isCompleted = state.isCompleted
            )

            if (state.sessionWordsRead > 0) {
                val durationSec = ((System.currentTimeMillis() - state.sessionStartTime) / 1000).coerceAtLeast(1)
                val session = ReadingSession(
                    documentId = docId,
                    documentTitle = state.activeTitle,
                    sourceType = state.activeSourceType,
                    wordsRead = state.sessionWordsRead,
                    durationSeconds = durationSec,
                    averageWpm = state.wpm,
                    timestamp = System.currentTimeMillis()
                )
                readingDao.insertSession(session)
                _uiState.update {
                    it.copy(
                        sessionWordsRead = 0,
                        sessionStartTime = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }
}
