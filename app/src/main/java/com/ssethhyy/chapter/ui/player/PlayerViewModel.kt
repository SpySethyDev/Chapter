package com.ssethhyy.chapter.ui.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ssethhyy.chapter.data.local.AppDatabase
import com.ssethhyy.chapter.data.repository.SettingsRepository
import com.ssethhyy.chapter.data.repository.BookRepository
import androidx.media3.common.Metadata
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.id3.ApicFrame
import java.io.FileOutputStream
import java.io.File
import com.ssethhyy.chapter.data.local.entities.Book
import com.ssethhyy.chapter.data.local.entities.Bookmark
import com.ssethhyy.chapter.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PlayerUiState(
    val book: Book? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerMillis: Long = 0,
    val skipForwardDuration: Long = 30000,
    val skipBackwardDuration: Long = 10000,
    val isSilenceSkippingEnabled: Boolean = false,
    val chapters: List<ChapterInfo> = emptyList(),
    val currentChapterIndex: Int = -1,
    val isChapterSkipMode: Boolean = false,
    val nowPlayingColorMode: SettingsRepository.ColorMode = SettingsRepository.ColorMode.ARTWORK,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = androidx.media3.common.Player.REPEAT_MODE_OFF
)

data class ChapterInfo(
    val title: String,
    val startTime: Long,
    val duration: Long = 0,
    val filePath: String? = null
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BookRepository
    private val settingsRepository: SettingsRepository
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController? get() = try {
        if (controllerFuture?.isDone == true) controllerFuture?.get() else null
    } catch (e: Exception) {
        null
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var pendingLoadBook: Book? = null
    private var pendingPlayBook: Book? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val bookmarksForCurrentBook: StateFlow<List<Bookmark>> = _uiState
        .map { it.book?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id != null) repository.getBookmarksForBook(id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BookRepository(database.bookDao(), database.chapterDao(), database.bookmarkDao())
        settingsRepository = SettingsRepository(application)
        
        viewModelScope.launch {
            combine(
                settingsRepository.skipForwardDuration,
                settingsRepository.skipBackwardDuration,
                settingsRepository.isChapterSkipMode,
                settingsRepository.isSilenceSkippingEnabled,
                settingsRepository.playbackSpeed,
                settingsRepository.nowPlayingColorMode
            ) { params ->
                val forward = params[0] as Long
                val backward = params[1] as Long
                val chapterMode = params[2] as Boolean
                val silence = params[3] as Boolean
                val speed = params[4] as Float
                val colorMode = params[5] as SettingsRepository.ColorMode

                _uiState.update { 
                    it.copy(
                        skipForwardDuration = forward,
                        skipBackwardDuration = backward,
                        isChapterSkipMode = chapterMode,
                        isSilenceSkippingEnabled = silence,
                        playbackSpeed = speed,
                        nowPlayingColorMode = colorMode
                    )
                }
            }.collect()
        }

        try {
            Log.d("CHAPTER_DEBUG", "Initializing MediaController...")
            val sessionToken = SessionToken(application, ComponentName(application, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
            controllerFuture?.addListener({
                try {
                    val controller = controller
                    Log.d("CHAPTER_DEBUG", "MediaController buildAsync listener fired. Controller connected: ${controller?.isConnected}")
                    setupController()
                    
                    pendingPlayBook?.let {
                        Log.d("CHAPTER_DEBUG", "Playing pending book: ${it.title}")
                        playBook(it)
                        pendingPlayBook = null
                        pendingLoadBook = null
                    } ?: pendingLoadBook?.let {
                        Log.d("CHAPTER_DEBUG", "Loading pending book: ${it.title}")
                        loadBook(it, false)
                        pendingLoadBook = null
                    }
                } catch (e: Exception) {
                    Log.e("CHAPTER_DEBUG", "Error in MediaController listener", e)
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.e("CHAPTER_DEBUG", "Error initializing MediaController", e)
        }

        viewModelScope.launch {
            while (isActive) {
                try {
                    updateProgress()
                } catch (e: Exception) {
                    // Ignore transient errors during progress updates
                }
                delay(500)
            }
        }

        // Separate job for saving progress to avoid UI lag
        viewModelScope.launch {
            while (isActive) {
                try {
                    if (controller?.isPlaying == true) {
                        _uiState.value.book?.let { book ->
                            val currentPos = controller!!.currentPosition
                            repository.updatePlaybackPosition(book.id, currentPos)
                            settingsRepository.setLastPlayedBookId(book.id)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                delay(5000)
            }
        }
    }

    private fun setupController() {
        val controller = controller ?: return
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (!isPlaying) {
                    // Save position when paused
                    _uiState.value.book?.let { book ->
                        viewModelScope.launch {
                            repository.updatePlaybackPosition(book.id, controller.currentPosition)
                            settingsRepository.setLastPlayedBookId(book.id)
                        }
                    }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.update { it.copy(repeatMode = repeatMode) }
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                _uiState.update { it.copy(playbackSpeed = playbackParameters.speed) }
                viewModelScope.launch {
                    settingsRepository.setPlaybackSpeed(playbackParameters.speed)
                }
            }

            @OptIn(UnstableApi::class)
            override fun onMetadata(metadata: Metadata) {
                // Single-file books: Always update metadata from tags to ensure art and info are current
                if (controller?.mediaItemCount == 1) {
                    extractChapters(metadata)
                    updateInfoFromMetadata(metadata)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                updateProgress()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateMetadata()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("CHAPTER_CRASH", "Playback error: ${error.errorCodeName}", error)
            }
        })
        _uiState.update { it.copy(
            isPlaying = controller.isPlaying,
            duration = controller.duration.coerceAtLeast(0),
            playbackSpeed = controller.playbackParameters.speed,
            isShuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = controller.repeatMode
        ) }
        updateMetadata()
    }

    @OptIn(UnstableApi::class)
    private fun updateInfoFromMetadata(metadata: Metadata) {
        val currentBook = _uiState.value.book ?: return
        var updatedBook = currentBook

        for (i in 0 until metadata.length()) {
            val entry = metadata.get(i)
            
            // Handle ID3 Art (MP3/M4B)
            if (entry is ApicFrame) {
                val data = entry.pictureData
                if (data != null && updatedBook.coverArtPath == null) {
                    try {
                        val coversDir = File(getApplication<Application>().filesDir, "covers")
                        if (!coversDir.exists()) coversDir.mkdirs()
                        val coverFile = File(coversDir, "cover_${updatedBook.id}_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(coverFile).use { fos ->
                            fos.write(data)
                        }
                        updatedBook = updatedBook.copy(coverArtPath = coverFile.absolutePath)
                    } catch (e: Exception) {
                        Log.e("PLAYER_VM", "Failed to save cover from ID3 ApicFrame", e)
                    }
                }
            }

            // Handle ID3 text frames (MP3/M4B)
            if (entry is TextInformationFrame) {
                when (entry.id) {
                    "TIT2" -> if (updatedBook.title.contains("Unknown", true) || updatedBook.title.endsWith(".mp3", true) || updatedBook.title.endsWith(".m4b", true)) {
                        updatedBook = updatedBook.copy(title = entry.value)
                    }
                    "TPE1", "TPE2" -> if (updatedBook.author.contains("Unknown", true)) {
                        updatedBook = updatedBook.copy(author = entry.value)
                    }
                }
            }
        }

        if (updatedBook != currentBook) {
            _uiState.update { it.copy(book = updatedBook) }
            viewModelScope.launch {
                repository.updateBook(updatedBook)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun extractChapters(metadata: Metadata) {
        val newChapters = mutableListOf<ChapterInfo>()
        for (i in 0 until metadata.length()) {
            val entry = metadata.get(i)
            if (entry is ChapterFrame) {
                val title = (0 until entry.subFrameCount).map { entry.getSubFrame(it) }
                    .filterIsInstance<TextInformationFrame>()
                    .firstOrNull { it.id == "TIT2" }?.value 
                    ?: (0 until entry.subFrameCount).map { entry.getSubFrame(it) }
                        .filterIsInstance<TextInformationFrame>()
                        .firstOrNull()?.value 
                    ?: "Chapter ${newChapters.size + 1}"
                newChapters.add(ChapterInfo(title, entry.startTimeMs.toLong(), (entry.endTimeMs - entry.startTimeMs).toLong()))
            }
        }
        
        if (newChapters.isNotEmpty()) {
            val sortedChapters = newChapters.distinctBy { it.startTime }.sortedBy { it.startTime }
            _uiState.update { it.copy(chapters = sortedChapters) }
        } else {
            // Fallback to repository
            val book = _uiState.value.book
            if (book != null) {
                viewModelScope.launch {
                    repository.getChaptersForBook(book.id).collectLatest { dbChapters ->
                        if (dbChapters.isNotEmpty()) {
                            val converted = dbChapters.map { ChapterInfo(it.title, it.startOffset, it.duration, it.filePath) }
                            _uiState.update { it.copy(chapters = converted) }
                        }
                    }
                }
            }
        }
    }

    private fun updateMetadata() {
        val controller = controller ?: return
    }

    private fun updateProgress() {
        val controller = controller ?: return
        val chapters = _uiState.value.chapters
        
        // Calculate global position for multi-file books
        var globalPosition = 0L
        if (controller.mediaItemCount > 1 && chapters.isNotEmpty() && chapters.any { it.filePath != null }) {
            val currentIndex = controller.currentMediaItemIndex
            // For multi-file audiobooks, global position is the sum of previous files' durations
            // plus the current position within the current file.
            for (i in 0 until currentIndex) {
                if (i < chapters.size) {
                    globalPosition += chapters[i].duration
                }
            }
            globalPosition += controller.currentPosition
        } else {
            globalPosition = controller.currentPosition
        }

        val duration = if (controller.mediaItemCount > 1 && chapters.isNotEmpty() && chapters.any { it.filePath != null }) {
            _uiState.value.book?.duration ?: chapters.sumOf { it.duration }.coerceAtLeast(0)
        } else {
            controller.duration.coerceAtLeast(0)
        }
        
        val chapterIndex = chapters.indexOfLast { 
            val chapterStartTime = if (chapters.any { it.filePath != null }) {
                // For multi-file books, we need to calculate the global start time
                chapters.take(chapters.indexOf(it)).sumOf { c -> c.duration }
            } else {
                it.startTime
            }
            chapterStartTime <= globalPosition 
        }

        _uiState.update { it.copy(
            currentPosition = globalPosition,
            duration = duration,
            currentChapterIndex = chapterIndex
        ) }
    }

    fun loadBook(book: Book, startPlayback: Boolean = false) {
        if (startPlayback) {
            playBook(book)
            return
        }

        val currentController = controller
        if (currentController != null && currentController.isConnected) {
            val currentMediaItem = currentController.currentMediaItem
            if (currentMediaItem?.mediaId != book.id.toString()) {
                prepareBook(book, false)
            } else {
                _uiState.update { it.copy(book = book) }
                loadChapters(book.id)
            }
        } else {
            pendingLoadBook = book
            pendingPlayBook = null
        }
    }

    private fun loadChapters(bookId: Long) {
        viewModelScope.launch {
            repository.getChaptersForBook(bookId).collectLatest { dbChapters ->
                if (dbChapters.isNotEmpty()) {
                    val converted = dbChapters.map { ChapterInfo(it.title, it.startOffset, it.duration, it.filePath) }
                        .sortedBy { it.startTime }
                    _uiState.update { it.copy(chapters = converted) }
                    updateProgress() // Refresh chapter index
                }
            }
        }
    }

    fun prepareBook(book: Book, playWhenReady: Boolean) {
        val currentController = controller ?: return
        
        // Don't re-prepare if it's already the same book
        if (currentController.currentMediaItem?.mediaId == book.id.toString()) {
            if (playWhenReady && !currentController.isPlaying) {
                currentController.play()
            }
            return
        }

        val pathToPlay = if (book.filePath.isBlank()) {
            "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"
        } else {
            book.filePath
        }

        try {
            val uri = if (pathToPlay.startsWith("http")) {
                Uri.parse(pathToPlay)
            } else if (pathToPlay.startsWith("content://")) {
                Uri.parse(pathToPlay)
            } else {
                Uri.fromFile(File(pathToPlay))
            }
            
            val mediaItem = MediaItem.Builder()
                .setMediaId(book.id.toString())
                .setUri(uri)
                .build()

            currentController.setMediaItem(mediaItem)
            currentController.prepare()
            currentController.playWhenReady = playWhenReady
            
            _uiState.update { it.copy(book = book) }
            loadChapters(book.id)
            
            viewModelScope.launch {
                val bookFromDb = repository.getBookById(book.id)
                val lastPos = bookFromDb?.currentPosition ?: 0L
                currentController.seekTo(lastPos)
            }
        } catch (e: Exception) {
            Log.e("CHAPTER_CRASH", "Error in prepareBook", e)
        }
    }

    fun playBook(book: Book) {
        val currentController = controller
        if (currentController != null && currentController.isConnected) {
            prepareBook(book, true)
        } else {
            pendingPlayBook = book
            pendingLoadBook = null
        }
    }

    fun togglePlayPause() {
        val currentController = controller ?: return
        if (currentController.playbackState == Player.STATE_ENDED) {
            currentController.seekTo(0)
            currentController.play()
        } else {
            // Using playWhenReady is more reliable than isPlaying for toggling
            if (currentController.playWhenReady) {
                currentController.pause()
            } else {
                currentController.play()
            }
        }
    }

    fun setNowPlayingColorMode(mode: SettingsRepository.ColorMode) {
        _uiState.update { it.copy(nowPlayingColorMode = mode) }
        viewModelScope.launch {
            settingsRepository.setNowPlayingColorMode(mode)
        }
    }

    fun seekTo(position: Long) {
        val controller = controller ?: return
        val chapters = _uiState.value.chapters
        
        if (controller.mediaItemCount > 1 && chapters.isNotEmpty() && chapters.any { it.filePath != null }) {
            var currentGlobalStart = 0L
            var found = false
            for (i in chapters.indices) {
                val chapter = chapters[i]
                val chapterEnd = currentGlobalStart + chapter.duration
                
                if (position >= currentGlobalStart && position < chapterEnd) {
                    // This chapter contains the global position. 
                    // Since it's a multi-file book, we assume 1 chapter = 1 file in the playlist.
                    val offsetInFile = position - currentGlobalStart
                    controller.seekTo(i, offsetInFile)
                    found = true
                    break
                }
                currentGlobalStart += chapter.duration
            }
            
            if (!found) {
                controller.seekTo(position)
            }
        } else {
            controller.seekTo(position)
        }
        updateProgress()
    }

    fun skipForward() {
        val controller = controller ?: return
        val skipMillis = _uiState.value.skipForwardDuration
        
        if (controller.mediaItemCount > 1) {
             val currentPos = _uiState.value.currentPosition
             seekTo(currentPos + skipMillis)
        } else {
            controller.seekTo(controller.currentPosition + skipMillis)
        }
    }

    fun skipBackward() {
        val controller = controller ?: return
        val skipMillis = _uiState.value.skipBackwardDuration
        
        if (controller.mediaItemCount > 1) {
             val currentPos = _uiState.value.currentPosition
             seekTo((currentPos - skipMillis).coerceAtLeast(0))
        } else {
            controller.seekTo((controller.currentPosition - skipMillis).coerceAtLeast(0))
        }
    }

    fun toggleChapterSkipMode() {
        val newState = !_uiState.value.isChapterSkipMode
        _uiState.update { it.copy(isChapterSkipMode = newState) }
        viewModelScope.launch {
            settingsRepository.setChapterSkipMode(newState)
        }
    }

    fun setSkipDurations(forward: Long, backward: Long) {
        _uiState.update { it.copy(skipForwardDuration = forward, skipBackwardDuration = backward) }
        viewModelScope.launch {
            settingsRepository.setSkipForwardDuration(forward)
            settingsRepository.setSkipBackwardDuration(backward)
        }
    }

    fun toggleSilenceSkipping() {
        val newState = !_uiState.value.isSilenceSkippingEnabled
        _uiState.update { it.copy(isSilenceSkippingEnabled = newState) }
        
        controller?.let {
            val bundle = android.os.Bundle().apply {
                putBoolean("enabled", newState)
            }
            it.sendCustomCommand(androidx.media3.session.SessionCommand("TOGGLE_SILENCE_SKIPPING", android.os.Bundle.EMPTY), bundle)
            Log.d("CHAPTER_DEBUG", "Sent TOGGLE_SILENCE_SKIPPING: $newState")
        }
        
        viewModelScope.launch {
            settingsRepository.setSilenceSkippingEnabled(newState)
        }
    }

    fun addBookmark(note: String) {
        val book = _uiState.value.book ?: return
        val position = _uiState.value.currentPosition
        viewModelScope.launch {
            repository.insertBookmark(com.ssethhyy.chapter.data.local.entities.Bookmark(
                bookId = book.id,
                position = position,
                note = note,
                timestamp = System.currentTimeMillis()
            ))
        }
    }

    fun toggleFavorite() {
        val book = _uiState.value.book ?: return
        val updatedBook = book.copy(isFavorite = !book.isFavorite)
        _uiState.update { it.copy(book = updatedBook) }
        viewModelScope.launch {
            repository.updateBook(updatedBook)
        }
    }

    fun deleteBookmark(bookmark: com.ssethhyy.chapter.data.local.entities.Bookmark) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    fun toggleShuffle() {
        controller?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
        }
    }

    fun toggleRepeatMode() {
        controller?.let {
            val nextMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            it.repeatMode = nextMode
        }
    }

    fun addSleepTimer(minutes: Int) {
        val currentMillis = _uiState.value.sleepTimerMillis
        val newMinutes = (currentMillis / (60 * 1000)).toInt() + minutes
        setSleepTimer(newMinutes)
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _uiState.update { it.copy(sleepTimerMillis = 0) }
            return
        }

        val millis = minutes * 60 * 1000L
        _uiState.update { it.copy(sleepTimerMillis = millis) }

        sleepTimerJob = viewModelScope.launch {
            var remaining = millis
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _uiState.update { it.copy(sleepTimerMillis = remaining) }
            }
            controller?.pause()
            _uiState.update { it.copy(sleepTimerMillis = 0) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}

