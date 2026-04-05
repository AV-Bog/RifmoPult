/*
 * Rifmopult – поэтический редактор с подбором рифм
 * Copyright (c) 2025 Arina Viktorovna Bogdanova
 *
 * MIT License
 */

package com.example.rifmopult

import com.example.rifmopult.AnalyticsHelper.trackError
import com.example.rifmopult.AnalyticsHelper.trackRhymeSearch
import io.appmetrica.analytics.AppMetrica
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder

object RifmeNetParser {
    suspend fun fetchRhymes(word: String, limit: Int = 100): List<String> {
        if (word.isBlank()) return emptyList()

        val startTime = System.currentTimeMillis()
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

            val durationMs = System.currentTimeMillis() - startTime
            trackRhymeSearch(word, result.size, durationMs, success = true)

            result
        } catch (e: Exception) {
            e.printStackTrace()

            val durationMs = System.currentTimeMillis() - startTime

            trackRhymeSearch(word, 0, durationMs, success = false)
            trackError("rhyme_search", e.message ?: "Unknown error")

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

    private fun trackRhymeSearch(word: String, resultsCount: Int, durationMs: Long, success: Boolean) {
        val wordLength = word.length
        val firstLetter = if (word.isNotEmpty()) word.first().toString() else ""

        val data = JSONObject().apply {
            put("word_length", wordLength)
            put("first_letter", firstLetter)
            put("results_count", resultsCount)
            put("duration_ms", durationMs)
            put("success", success)
        }.toString()

        val eventName = if (success) "rhyme_search_success" else "rhyme_search_failed"
        AppMetrica.reportEvent(eventName, data)
    }

    private fun trackError(errorType: String, message: String) {
        val data = JSONObject().apply {
            put("error_type", errorType)
            put("message", message.take(200))
        }.toString()

        AppMetrica.reportEvent("app_error", data)
    }
}