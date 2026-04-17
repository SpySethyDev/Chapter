package com.ssethhyy.chapter.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val SKIP_FORWARD_DURATION = longPreferencesKey("skip_forward_duration")
        val SKIP_BACKWARD_DURATION = longPreferencesKey("skip_backward_duration")
        val IS_CHAPTER_SKIP_MODE = booleanPreferencesKey("is_chapter_skip_mode")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val IS_SILENCE_SKIPPING_ENABLED = booleanPreferencesKey("is_silence_skipping_enabled")
        val NOW_PLAYING_COLOR_MODE = stringPreferencesKey("now_playing_color_mode")
        val APP_FONT_FAMILY = stringPreferencesKey("app_font_family")
        val LIBRARY_FOLDERS = stringSetPreferencesKey("library_folders")
        val LAST_PLAYED_BOOK_ID = longPreferencesKey("last_played_book_id")
    }

    enum class ColorMode { ARTWORK, SYSTEM }
    enum class AppFont(val displayName: String) { 
        SYSTEM("System"), 
        FIGTREE("Figtree"), 
        MONTSERRAT("Montserrat"), 
        GOOGLE_SANS("Google Sans") 
    }

    val skipForwardDuration: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SKIP_FORWARD_DURATION] ?: 30000L
    }

    val skipBackwardDuration: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SKIP_BACKWARD_DURATION] ?: 10000L
    }

    val isChapterSkipMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_CHAPTER_SKIP_MODE] ?: false
    }

    val playbackSpeed: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PLAYBACK_SPEED] ?: 1.0f
    }

    val isSilenceSkippingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_SILENCE_SKIPPING_ENABLED] ?: false
    }

    val nowPlayingColorMode: Flow<ColorMode> = context.dataStore.data.map { preferences ->
        val modeString = preferences[PreferencesKeys.NOW_PLAYING_COLOR_MODE] ?: ColorMode.ARTWORK.name
        try {
            ColorMode.valueOf(modeString)
        } catch (e: Exception) {
            ColorMode.ARTWORK
        }
    }

    val appFont: Flow<AppFont> = context.dataStore.data.map { preferences ->
        val fontString = preferences[PreferencesKeys.APP_FONT_FAMILY] ?: AppFont.GOOGLE_SANS.name
        try {
            AppFont.valueOf(fontString)
        } catch (e: Exception) {
            AppFont.GOOGLE_SANS
        }
    }

    val libraryFolders: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LIBRARY_FOLDERS] ?: emptySet()
    }

    val lastPlayedBookId: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_PLAYED_BOOK_ID]
    }

    suspend fun setSkipForwardDuration(duration: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SKIP_FORWARD_DURATION] = duration
        }
    }

    suspend fun setSkipBackwardDuration(duration: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SKIP_BACKWARD_DURATION] = duration
        }
    }

    suspend fun setChapterSkipMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_CHAPTER_SKIP_MODE] = enabled
        }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYBACK_SPEED] = speed
        }
    }

    suspend fun setSilenceSkippingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_SILENCE_SKIPPING_ENABLED] = enabled
        }
    }

    suspend fun setNowPlayingColorMode(mode: ColorMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOW_PLAYING_COLOR_MODE] = mode.name
        }
    }

    suspend fun setAppFont(font: AppFont) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_FONT_FAMILY] = font.name
        }
    }

    suspend fun addLibraryFolder(uri: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.LIBRARY_FOLDERS] ?: emptySet()
            preferences[PreferencesKeys.LIBRARY_FOLDERS] = current + uri
        }
    }

    suspend fun removeLibraryFolder(uri: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.LIBRARY_FOLDERS] ?: emptySet()
            preferences[PreferencesKeys.LIBRARY_FOLDERS] = current - uri
        }
    }

    suspend fun setLastPlayedBookId(bookId: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_PLAYED_BOOK_ID] = bookId
        }
    }
}

