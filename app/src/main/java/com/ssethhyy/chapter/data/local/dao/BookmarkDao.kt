package com.ssethhyy.chapter.data.local.dao

import androidx.room.*
import com.ssethhyy.chapter.data.local.entities.Bookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY position ASC")
    fun getBookmarksForBook(bookId: Long): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)
}

