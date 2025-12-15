/*
 * Rifmopult – поэтический редактор с подбором рифм
 * Copyright (c) 2025 Arina Viktorovna Bogdanova
 *
 * MIT License
 */

package com.example.rifmopult

import org.junit.Assert.*
import org.junit.Test

class NoteSaveLogicTest {
    private val syllableHintRegex = """\s*·[0-9]+\s*$""".toRegex()

    private fun stripSyllableHints(text: String): String {
        return text.lines().joinToString("\n") { line ->
            line.replace(syllableHintRegex, "")
        }
    }

    private fun shouldSaveNote(
        originalTitle: String,
        originalContent: String,
        currentTitleWithHints: String,
        currentContentWithHints: String
    ): Boolean {
        val currentTitle = currentTitleWithHints
        val cleanContent = stripSyllableHints(currentContentWithHints).trim()

        val originalContentTrimmed = originalContent.trim()

        return (currentTitle != originalTitle) || (cleanContent != originalContentTrimmed)
    }

    @Test
    fun `shouldSaveNote returns true when title changed`() {
        val originalTitle = "Старый заголовок"
        val originalContent = "Текст"

        val currentTitle = "Новый заголовок"
        val currentContent = "Текст ·1"

        assertTrue(
            shouldSaveNote(originalTitle, originalContent, currentTitle, currentContent)
        )
    }

    @Test
    fun `shouldSaveNote returns true when content changed (even if hints differ)`() {
        val originalTitle = "Стих"
        val originalContent = "Мир\nЗефир"

        val currentTitle = "Стих"
        val currentContent = "Мир ·2\nНовый зефир ·4"

        assertTrue(
            shouldSaveNote(originalTitle, originalContent, currentTitle, currentContent)
        )
    }

    @Test
    fun `shouldSaveNote ignores syllable hints when comparing content`() {
        val originalTitle = "Стих"
        val originalContent = "Любовь\nИ вновь"

        val currentTitle = "Стих"
        val currentContent = "Любовь ·2\nИ вновь ·2"

        assertFalse(
            shouldSaveNote(originalTitle, originalContent, currentTitle, currentContent)
        )
    }

    @Test
    fun `shouldSaveNote detects change when only whitespace differs but content same`() {
        val originalTitle = "Стих"
        val originalContent = "Слово"

        val currentTitle = "Стих"
        val currentContent = " Слово ·1 \n"

        assertFalse(
            shouldSaveNote(originalTitle, originalContent, currentTitle, currentContent)
        )
    }

    @Test
    fun `shouldSaveNote detects real content change despite whitespace`() {
        val originalTitle = "Стих"
        val originalContent = "Слово"

        val currentTitle = "Стих"
        val currentContent = " Новое слово ·3 \n"

        assertTrue(
            shouldSaveNote(originalTitle, originalContent, currentTitle, currentContent)
        )
    }

    @Test
    fun `shouldSaveNote returns true for new empty note that got content`() {
        val originalTitle = ""
        val originalContent = ""

        val currentTitle = ""
        val currentContent = "Новый стих ·2"

        assertTrue(
            shouldSaveNote(originalTitle, originalContent, currentTitle, currentContent)
        )
    }
}