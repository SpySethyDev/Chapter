package com.example.chapter.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chapter.data.local.dao.BookDao
import com.example.chapter.data.local.entities.Book
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class DatabaseTest {
    private lateinit var bookDao: BookDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).allowMainThreadQueries().build()
        bookDao = db.bookDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeBookAndReadInList() = runBlocking {
        val book = Book(
            title = "Test Book",
            author = "Test Author",
            filePath = "/path/to/test.mp3",
            coverArtPath = null,
            duration = 1000L
        )
        bookDao.insertBook(book)
        val allBooks = bookDao.getBookByPath("/path/to/test.mp3")
        assertEquals(allBooks?.title, "Test Book")
    }
}
