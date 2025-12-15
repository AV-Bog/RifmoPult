/*
 * Rifmopult – поэтический редактор с подбором рифм
 * Copyright (c) 2025 Arina Viktorovna Bogdanova
 *
 * MIT License
 */

package com.example.rifmopult

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.jsoup.Jsoup
import java.net.URLEncoder

object RifmeNetParser {

    suspend fun fetchRhymes(word: String, limit: Int = 100): List<String> {
        if (word.isBlank()) return emptyList()

        val client = HttpClient()
        return try {
            val encodedWord = URLEncoder.encode(word.trim().lowercase(), "UTF-8")
            val url = "https://rifme.net/r/$encodedWord"

            val html = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Android) AppleWebKit/537.36")
            }.bodyAsText()

            val doc = Jsoup.parse(html)
            val result = mutableListOf<String>()

            doc.select("#tochnye li[data-w]").forEach { element ->
                val rhyme = element.attr("data-w").trim()
                if (isValidRhyme(rhyme, word)) {
                    result.add(rhyme)
                    if (result.size >= limit) return@forEach
                }
            }

            if (result.size < limit) {
                doc.select("#meneestrogie li[data-w]").forEach { element ->
                    val rhyme = element.attr("data-w").trim()
                    if (isValidRhyme(rhyme, word)) {
                        result.add(rhyme)
                        if (result.size >= limit) return@forEach
                    }
                }
            }

            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            client.close()
        }
    }

    private fun isValidRhyme(rhyme: String, originalWord: String): Boolean {
        return rhyme.isNotEmpty() &&
                rhyme != originalWord &&
                !rhyme.contains("://") &&
                rhyme.all { it.isLetter() || it == '-' || it == '\'' }
    }
}