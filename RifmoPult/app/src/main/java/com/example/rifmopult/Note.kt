/*
 * Rifmopult – поэтический редактор с подбором рифм
 * Copyright (c) 2025 Arina Viktorovna Bogdanova
 *
 * MIT License
 */

package com.example.rifmopult

import java.io.Serializable

data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val date: String
) : Serializable