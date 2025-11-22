package com.example.rifmopult

import androidx.room.*

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY id DESC")
    suspend fun getAllNotes(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)
}