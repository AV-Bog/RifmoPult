package com.example.rifmopult

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val content: String,
    val date: String
)