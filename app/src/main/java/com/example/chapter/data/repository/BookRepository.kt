package com.example.chapter.data.repository

import com.example.chapter.data.local.dao.BookDao
import com.example.chapter.data.local.dao.ChapterDao
import com.example.chapter.data.local.dao.BookmarkDao
import com.example.chapter.data.local.entities.Book
import com.example.chapter.data.local.entities.Chapter
import com.example.chapter.data.local.entities.Bookmark
import kotlinx.coroutines.flow.Flow

class BookRepository(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: BookmarkDao
) {
    val allBooks: Flow<List<Book>> = bookDao.getAllBooks()

    suspend fun getBookById(id: Long): Book? = bookDao.getBookById(id)

    fun getChaptersForBook(bookId: Long): Flow<List<Chapter>> = chapterDao.getChaptersForBook(bookId)

    fun getBookmarksForBook(bookId: Long): Flow<List<Bookmark>> = bookmarkDao.getBookmarksForBook(bookId)

    suspend fun updateBook(book: Book) = bookDao.updateBook(book)

    suspend fun insertBookmark(bookmark: Bookmark) = bookmarkDao.insertBookmark(bookmark)

    suspend fun deleteBookmark(bookmark: Bookmark) = bookmarkDao.deleteBookmark(bookmark)

    suspend fun insertBookWithChapters(book: Book, chapters: List<Chapter>): Long {
        val existingBook = bookDao.getBookByPath(book.filePath) ?: bookDao.getBookByTitle(book.title)
        val bookId = if (existingBook != null) {
            val updatedBook = book.copy(
                id = existingBook.id,
                currentPosition = existingBook.currentPosition,
                isFavorite = existingBook.isFavorite
            )
            bookDao.updateBook(updatedBook)
            existingBook.id
        } else {
            bookDao.insertBook(book)
        }
        
        chapterDao.deleteChaptersForBook(bookId)
        chapterDao.insertChapters(chapters.map { it.copy(bookId = bookId) })
        return bookId
    }

    suspend fun insertMultipleBooksWithChapters(booksAndChapters: List<Pair<Book, List<Chapter>>>) {
        booksAndChapters.forEach { (book, chapters) ->
            insertBookWithChapters(book, chapters)
        }
    }

    suspend fun updatePlaybackPosition(bookId: Long, position: Long) {
        val book = bookDao.getBookById(bookId)
        book?.let {
            bookDao.updateBook(it.copy(
                currentPosition = position,
                lastPlayedTimestamp = System.currentTimeMillis()
            ))
        }
    }

    suspend fun deleteBook(book: Book) {
        bookDao.deleteBook(book)
    }

    suspend fun deleteAllBooks() {
        bookDao.deleteAllBooks()
    }
}
