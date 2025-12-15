/*
 * Rifmopult – поэтический редактор с подбором рифм
 * Copyright (c) 2025 Arina Viktorovna Bogdanova
 *
 * MIT License
 */
package com.example.rifmopult

import org.junit.Test

import org.junit.Assert.*

class PoemUpdateTes {
    class Poem(
        val id: String,
        var title: String,
        var content: String,
        var lastModified: Long = System.currentTimeMillis()
    ) {
        fun updateContent(newContent: String) {
            if (newContent != content) {
                content = newContent
                lastModified = System.currentTimeMillis()
            }
        }
    }

    @Test
    fun `updateContent updates lastModified when content changes`() {
        val initialTime = 1000L
        val poem = Poem("1", "Title", "Old content", initialTime)

        poem.updateContent("New content")

        assertNotEquals(initialTime, poem.lastModified)
        assertEquals("New content", poem.content)
    }

    @Test
    fun `updateContent does NOT update lastModified when content is unchanged`() {
        val initialTime = 2000L
        val poem = Poem("1", "Title", "Same content", initialTime)

        poem.updateContent("Same content")

        assertEquals(initialTime, poem.lastModified)
        assertEquals("Same content", poem.content)
    }
}