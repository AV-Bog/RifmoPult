package com.example.rifmopult

import java.io.Serializable

data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val date: String
) : Serializable