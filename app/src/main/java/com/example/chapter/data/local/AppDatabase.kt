package com.example.chapter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.chapter.data.local.dao.BookDao
import com.example.chapter.data.local.dao.ChapterDao
import com.example.chapter.data.local.dao.BookmarkDao
import com.example.chapter.data.local.entities.Book
import com.example.chapter.data.local.entities.Chapter
import com.example.chapter.data.local.entities.Bookmark

@Database(entities = [Book::class, Chapter::class, Bookmark::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chapter_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
