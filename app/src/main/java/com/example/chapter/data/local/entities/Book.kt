package com.example.chapter.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val filePath: String,
    val coverArtPath: String?,
    val duration: Long,
    val currentPosition: Long = 0,
    val lastPlayedTimestamp: Long = 0,
    val isFavorite: Boolean = false
)
