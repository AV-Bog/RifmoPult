/*
 * Rifmopult – поэтический редактор с подбором рифм
 * Copyright (c) 2025 Arina Viktorovna Bogdanova
 *
 * MIT License
 */

package com.example.rifmopult

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditLogicTest {

    private fun countSyllables(text: String): Int {
        val vowels = "аеёиоуыэюяАЕЁИОУЫЭЮЯ"
        return text.count { it in vowels }
    }

    private val syllableHintRegex = """\s*·[0-9]+\s*$""".toRegex()

    private fun stripSyllableHints(text: String): String {
        return text.lines().joinToString("\n") { line ->
            line.replace(syllableHintRegex, "")
        }
    }

    private fun addSyllableHints(text: String): String {
        return text.split('\n').joinToString("\n") { line ->
            val cleanLine = line.replace(syllableHintRegex, "")
            if (cleanLine.isBlank()) {
                cleanLine
            } else {
                val count = countSyllables(cleanLine)
                "$cleanLine ·$count"
            }
        }
    }

    private fun Char.isPunctuation(): Boolean {
        return this in setOf(
            '.',
            ',',
            '!',
            '?',
            ':',
            ';',
            '-',
            '—',
            '(',
            ')',
            '"',
            '\'',
            '…',
            '[',
            ']',
            '{',
            '}'
        )
    }


    @Test
    fun `countSyllables counts only Russian vowels`() {
        assertEquals(0, countSyllables(""))
        assertEquals(0, countSyllables("bcdf"))
        assertEquals(1, countSyllables("мир"))
        assertEquals(2, countSyllables("любовь"))
        assertEquals(3, countSyllables("Здравствуйте"))
        assertEquals(5, countSyllables("АеЁиО"))
    }

    @Test
    fun `stripSyllableHints removes ·N from end of lines`() {
        assertEquals("Привет", stripSyllableHints("Привет ·2"))
        assertEquals("Стих\nКонец", stripSyllableHints("Стих ·1\nКонец ·2"))
        assertEquals("Пусто\n\nТут", stripSyllableHints("Пусто ·1\n\nТут ·1"))
        assertEquals("Нет подсказок", stripSyllableHints("Нет подсказок"))
    }

    @Test
    fun `addSyllableHints re-calculates even if hint already exists`() {
        assertEquals("мир ·1", addSyllableHints("мир ·2"))
        assertEquals("любовь ·2", addSyllableHints("любовь ·5"))
    }

    @Test
    fun `isPunctuation returns true for punctuation`() {
        assertTrue('.'.isPunctuation())
        assertTrue(','.isPunctuation())
        assertTrue('!'.isPunctuation())
        assertTrue('?'.isPunctuation())
        assertTrue('—'.isPunctuation())
        assertTrue('('.isPunctuation())
        assertTrue(')'.isPunctuation())
        assertTrue('"'.isPunctuation())
    }

    @Test
    fun `isPunctuation returns false for non-punctuation`() {
        assertFalse('а'.isPunctuation())
        assertFalse('Z'.isPunctuation())
        assertFalse(' '.isPunctuation())
        assertFalse('1'.isPunctuation())
    }
}