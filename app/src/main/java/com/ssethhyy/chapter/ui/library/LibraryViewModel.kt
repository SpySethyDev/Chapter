package com.ssethhyy.chapter.ui.library

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssethhyy.chapter.data.local.AppDatabase
import com.ssethhyy.chapter.data.local.entities.Book
import com.ssethhyy.chapter.data.local.entities.Chapter
import com.ssethhyy.chapter.data.repository.BookRepository
import com.ssethhyy.chapter.data.repository.SettingsRepository
import com.ssethhyy.chapter.scanner.AudiobookScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BookRepository
    private val settingsRepository: SettingsRepository
    private val scanner: AudiobookScanner = AudiobookScanner(application)

    val allBooks: StateFlow<List<Book>>
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val isScanInProgress = AtomicBoolean(false)
    private var lastScannedFolders: Set<String>? = null

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BookRepository(database.bookDao(), database.chapterDao(), database.bookmarkDao())
        settingsRepository = SettingsRepository(application)
        
        allBooks = repository.allBooks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Start scanning folders
        viewModelScope.launch {
            settingsRepository.libraryFolders.collect { folders ->
                scanLibraryFolders(folders)
            }
        }
    }

    private fun scanLibraryFolders(folders: Set<String>) {
        if (folders.isEmpty() || folders == lastScannedFolders) return
        if (!isScanInProgress.compareAndSet(false, true)) return

        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            lastScannedFolders = folders
            val context = getApplication<Application>().applicationContext
            val booksToImport = mutableListOf<Pair<Book, List<Chapter>>>()

            folders.forEach { folderUri ->
                try {
                    val rootFolder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                    if (rootFolder != null) {
                        processFolder(rootFolder, isRoot = true, booksToImport)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (booksToImport.isNotEmpty()) {
                repository.insertMultipleBooksWithChapters(booksToImport)
            }
            _isScanning.value = false
            isScanInProgress.set(false)
        }
    }

    private suspend fun processFolder(
        folder: DocumentFile,
        isRoot: Boolean,
        outBooks: MutableList<Pair<Book, List<Chapter>>>
    ) {
        val children = folder.listFiles()
        val audioFiles = children.filter { isAudioFile(it) }
        val imageFiles = children.filter { isImageFile(it) }

        // Find common cover art in this folder
        val folderCover = imageFiles.find { it.name?.startsWith("cover", ignoreCase = true) == true }
            ?: imageFiles.find { it.name?.startsWith("folder", ignoreCase = true) == true }
            ?: imageFiles.firstOrNull()

        if (isRoot) {
            // In the root folder, treat each audio file as a separate book
            audioFiles.forEach { file ->
                val baseName = file.name?.substringBeforeLast('.') ?: ""
                val specificCover = imageFiles.find { it.name?.startsWith(baseName, ignoreCase = true) == true } ?: folderCover
                importDocumentFile(file, specificCover, null, outBooks)
            }
            
            // Recurse into subfolders
            children.filter { it.isDirectory }.forEach { subFolder ->
                processFolder(subFolder, isRoot = false, outBooks)
            }
        } else {
            // In a subfolder
            if (audioFiles.isNotEmpty()) {
                if (audioFiles.size == 1) {
                    importDocumentFile(audioFiles.first(), folderCover, folder.name, outBooks)
                } else {
                    importMultipleFilesAsBook(folder.name ?: "Unknown Book", audioFiles, folderCover, outBooks)
                }
            } else {
                children.filter { it.isDirectory }.forEach { subFolder ->
                    processFolder(subFolder, isRoot = false, outBooks)
                }
            }
        }
    }

    private fun isAudioFile(file: DocumentFile): Boolean {
        val name = file.name?.lowercase() ?: ""
        return file.type?.startsWith("audio/") == true || 
               name.endsWith(".mp3") || name.endsWith(".m4b") || 
               name.endsWith(".m4a") || name.endsWith(".aac")
    }

    private fun isImageFile(file: DocumentFile): Boolean {
        val name = file.name?.lowercase() ?: ""
        return file.type?.startsWith("image/") == true || 
               name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
    }

    private suspend fun importDocumentFile(
        file: DocumentFile,
        coverImage: DocumentFile?,
        titleHint: String?,
        outBooks: MutableList<Pair<Book, List<Chapter>>>
    ) {
        val result = scanner.scanFileFromUri(file.uri, titleHint ?: file.name ?: "Unknown")
        if (result != null) {
            var (book, chapters) = result
            if (coverImage != null) {
                book = book.copy(coverArtPath = coverImage.uri.toString())
            }
            outBooks.add(book to chapters)
        }
    }

    private suspend fun importMultipleFilesAsBook(
        title: String,
        audioFiles: List<DocumentFile>,
        coverImage: DocumentFile?,
        outBooks: MutableList<Pair<Book, List<Chapter>>>
    ) {
        val sortedFiles = audioFiles.sortedBy { it.name }
        
        var totalDuration = 0L
        val chapters = mutableListOf<Chapter>()
        var currentOffset = 0L
        
        // Use first file for author metadata hint
        val firstFileResult = scanner.scanFileFromUri(sortedFiles.first().uri, title)
        val bookAuthor = firstFileResult?.first?.author ?: "Unknown Author"

        sortedFiles.forEach { file ->
            val duration = scanner.getDuration(file.uri)
            val fileName = file.name?.substringBeforeLast('.') ?: "Unknown Chapter"
            chapters.add(
                Chapter(
                    bookId = 0,
                    title = fileName,
                    startOffset = currentOffset,
                    duration = duration,
                    filePath = file.uri.toString()
                )
            )
            currentOffset += duration
            totalDuration += duration
        }
        
        val book = Book(
            title = title,
            author = bookAuthor,
            filePath = sortedFiles.first().uri.toString(), // Store first file as primary path
            coverArtPath = coverImage?.uri?.toString(),
            duration = totalDuration,
            lastPlayedTimestamp = System.currentTimeMillis()
        )
        
        outBooks.add(book to chapters)
    }
}

