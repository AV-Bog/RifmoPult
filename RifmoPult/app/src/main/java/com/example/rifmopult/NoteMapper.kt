/*
 * Rifmopult – поэтический редактор с подбором рифм
 * Copyright (c) 2025 Arina Viktorovna Bogdanova
 *
 * MIT License
 */

package com.example.rifmopult

fun Note.toEntity(): NoteEntity = NoteEntity(id, title, content, date)

fun NoteEntity.toNote(): Note = Note(id, title, content, date)