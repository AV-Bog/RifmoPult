/*
 * Rifmopult – поэтический редактор с подбором рифм
 * Copyright (c) 2025 Arina Viktorovna Bogdanova
 *
 * MIT License
 */

package com.example.rifmopult

import androidx.core.graphics.toColorInt

object RhymeSchemeAnalyzer {
    val RHYME_COLORS = listOf(
        "#80DEEA".toColorInt(), // голубой
        "#CE93D8".toColorInt(), // фиолетовый
        "#F48FB1".toColorInt(), // розовый
        "#A5D6A7".toColorInt(), // зелёный
        "#90CAF9".toColorInt(), // синий
        "#FFCC80".toColorInt(), // оранжевый
        "#EF9A9A".toColorInt(), // красный
        "#C5E1A5".toColorInt(), // светло-зелёный
        "#FFAB91".toColorInt(), // терракотовый
        "#9FA8DA".toColorInt()  // индиго
    )
    val NO_RHYME_COLOR = "#999999".toColorInt() // для нерифмующихся

    // Мин длина окончания для сравнения
    private const val MIN_ENDING_LENGTH = 2

    /**
     * Возвращает список цветов — по одному на каждую строку.
     */
    fun analyze(lines: List<String>): List<Int?> {
        val result = mutableListOf<Int?>()
        // Группы рифм в пределах текущей строфы: окончание -> индекс цвета
        var currentGroups = mutableMapOf<String, Int>()
        var colorIndex = 0
        // Сколько строк с одинаковым окончанием встретили
        val endingCount = mutableMapOf<String, Int>()

        for (line in lines) {
            if (line.isBlank()) {
                result.add(null)
                currentGroups = mutableMapOf()
                colorIndex = 0
                endingCount.clear()
                continue
            }

            val ending = extractEnding(line)
            if (ending == null) {
                result.add(NO_RHYME_COLOR)
                continue
            }

            val matchedEnding = currentGroups.keys.firstOrNull { existingEnding ->
                endingsMatch(ending, existingEnding)
            }

            if (matchedEnding != null) {
                result.add(RHYME_COLORS[currentGroups[matchedEnding]!!])
                endingCount[matchedEnding] = (endingCount[matchedEnding] ?: 1) + 1
            } else {
                currentGroups[ending] = colorIndex % 10
                endingCount[ending] = 1
                colorIndex++
                result.add(NO_RHYME_COLOR)
            }
        }

        // перекрашиваем строки у которых нашлась пара (те что были добавлены как "первые" в группе и получили серый)
        var lineIndex = 0
        var groupsForSecondPass = mutableMapOf<String, Int>()
        colorIndex = 0
        val endingCountFinal = mutableMapOf<String, Int>()

        for (line in lines) {
            if (line.isBlank()) {
                groupsForSecondPass = mutableMapOf()
                colorIndex = 0
                endingCountFinal.clear()
                lineIndex++
                continue
            }

            val ending = extractEnding(line)
            if (ending == null) {
                lineIndex++
                continue
            }

            val matchedEnding = groupsForSecondPass.keys.firstOrNull { endingsMatch(ending, it) }

            if (matchedEnding != null) {
                endingCountFinal[matchedEnding] = (endingCountFinal[matchedEnding] ?: 1) + 1
            } else {
                groupsForSecondPass[ending] = colorIndex % 10
                endingCountFinal[ending] = 1
                colorIndex++
            }
            lineIndex++
        }

        // Фин проход
        val finalResult = mutableListOf<Int?>()
        var finalGroups = mutableMapOf<String, Int>()
        var finalColorIndex = 0
        val finalEndingCount = mutableMapOf<String, Int>()

        for (line in lines) {
            if (line.isBlank()) {
                finalResult.add(null)
                finalGroups = mutableMapOf()
                finalColorIndex = 0
                finalEndingCount.clear()
                continue
            }

            val ending = extractEnding(line)
            if (ending == null) {
                finalResult.add(NO_RHYME_COLOR)
                continue
            }

            val matchedEnding = finalGroups.keys.firstOrNull { endingsMatch(ending, it) }

            if (matchedEnding != null) {
                val groupColorIndex = finalGroups[matchedEnding]!!
                finalResult.add(RHYME_COLORS[groupColorIndex])
                finalEndingCount[matchedEnding] = (finalEndingCount[matchedEnding] ?: 1) + 1
            } else {
                val newColorIndex = finalColorIndex % 10
                finalGroups[ending] = newColorIndex
                finalEndingCount[ending] = 1
                finalColorIndex++

                // Проверяем: встретится ли это окончание ещё раз в тексте?
                val totalOccurrences = countEndingOccurrences(ending, lines)
                if (totalOccurrences > 1) {
                    finalResult.add(RHYME_COLORS[newColorIndex])
                } else {
                    finalResult.add(NO_RHYME_COLOR)
                }
            }
        }

        return finalResult
    }

    /**
     * Считает сколько строк в lines имеют окончание совпадающее с данным,
     * учитывая уже построенные группы (чтобы не считать саму строку дважды).
     */
    private fun countEndingOccurrences(
        ending: String,
        lines: List<String>,
    ): Int {
        var count = 0
        for (line in lines) {
            if (line.isBlank()) continue
            val e = extractEnding(line) ?: continue
            if (endingsMatch(ending, e)) count++
        }
        return count
    }

    /**
     * Извлекает рифмующееся окончание из строки.
     * Берём последнее слово и его окончание (последние буквы начиная с последней гласной).
     */
    private fun extractEnding(line: String): String? {
        val cleanLine = line.trim()
        if (cleanLine.isEmpty()) return null

        val lastWord = cleanLine
            .split(" ", "\t")
            .lastOrNull { it.any { c -> c.isLetter() } }
            ?.filter { it.isLetter() }
            ?.lowercase()
            ?: return null

        if (lastWord.length < MIN_ENDING_LENGTH) return null

        val vowels = "аеёиоуыэюяaeiouy"
        val lastVowelIndex = lastWord.indexOfLast { it in vowels }

        if (lastVowelIndex < 0) return null

        return lastWord.substring(lastVowelIndex)
    }

    private fun endingsMatch(a: String, b: String): Boolean {
        if (a == b) return true
        // Минимальная общая длина для сравнения
        val minLen = minOf(a.length, b.length)
        if (minLen < MIN_ENDING_LENGTH) return false
        // Сравниваем последние minLen символов
        return a.takeLast(minLen) == b.takeLast(minLen)
    }
}