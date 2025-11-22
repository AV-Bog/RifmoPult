package com.example.rifmopult

fun Note.toEntity(): NoteEntity = NoteEntity(id, title, content, date)

fun NoteEntity.toNote(): Note = Note(id, title, content, date)