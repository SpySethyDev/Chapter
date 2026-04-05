package com.example.chapter.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chapter.data.local.AppDatabase
import com.example.chapter.data.repository.BookRepository
import com.example.chapter.data.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val bookRepository: BookRepository

    init {
        val database = AppDatabase.getDatabase(application)
        bookRepository = BookRepository(database.bookDao(), database.chapterDao(), database.bookmarkDao())
    }

    val libraryFolders: StateFlow<Set<String>> = settingsRepository.libraryFolders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val appFont: StateFlow<SettingsRepository.AppFont> = settingsRepository.appFont
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsRepository.AppFont.SYSTEM
        )

    fun setAppFont(font: SettingsRepository.AppFont) {
        viewModelScope.launch {
            settingsRepository.setAppFont(font)
        }
    }

    fun addFolder(uri: String) {
        viewModelScope.launch {
            settingsRepository.addLibraryFolder(uri)
        }
    }

    fun removeFolder(uri: String) {
        viewModelScope.launch {
            settingsRepository.removeLibraryFolder(uri)
        }
    }

    fun clearLibrary() {
        viewModelScope.launch {
            bookRepository.deleteAllBooks()
        }
    }
}
