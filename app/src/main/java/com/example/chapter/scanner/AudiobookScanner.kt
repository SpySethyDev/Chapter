package com.example.chapter.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.chapter.data.local.entities.Book
import com.example.chapter.data.local.entities.Chapter
import java.io.File
import java.io.FileOutputStream

class AudiobookScanner(private val context: Context) {

    fun scanFile(file: File): Pair<Book, List<Chapter>>? {
        return scanFileFromUri(Uri.fromFile(file), file.nameWithoutExtension)
    }

    fun scanFileFromUri(uri: Uri, defaultTitle: String): Pair<Book, List<Chapter>>? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: defaultTitle
            val author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) 
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) 
                ?: "Unknown Author"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            
            val albumArt = retriever.embeddedPicture
            var coverArtPath: String? = null
            
            if (albumArt != null) {
                try {
                    val coversDir = File(context.filesDir, "covers")
                    if (!coversDir.exists()) coversDir.mkdirs()
                    
                    val coverFile = File(coversDir, "${title.replace(" ", "_")}_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(coverFile).use { fos ->
                        fos.write(albumArt)
                    }
                    coverArtPath = coverFile.absolutePath
                } catch (e: Exception) {
                    Log.e("CHAPTER_SCAN", "Failed to save cover art", e)
                }
            }

            val chapters = mutableListOf<Chapter>()
            
            val book = Book(
                title = title,
                author = author,
                filePath = uri.toString(),
                coverArtPath = coverArtPath,
                duration = duration,
                lastPlayedTimestamp = System.currentTimeMillis()
            )
            
            Pair(book, chapters)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            retriever.release()
        }
    }

    fun getDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }
}
