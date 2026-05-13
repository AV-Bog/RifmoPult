/*
 * Rifmopult – поэтический редактор с подбором рифм
 * Copyright (c) 2025 Arina Viktorovna Bogdanova
 *
 * MIT License
 */

package com.example.rifmopult

import android.util.Log
import io.appmetrica.analytics.AppMetrica
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.InternalAPI
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.URLEncoder

object RifmeNetParser {

    private const val TAG = "RifmovkaParser"

    private var lastRequestTime = 0L
    private const val MIN_REQUEST_INTERVAL_MS = 3000L

    @OptIn(InternalAPI::class)
    suspend fun fetchRhymes(word: String, limit: Int = 100): List<String> {
        if (word.isBlank()) return emptyList()

        val now = System.currentTimeMillis()
        val timeSinceLastRequest = now - lastRequestTime
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
            delay(MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest)
        }
        lastRequestTime = System.currentTimeMillis()

        val startTime = System.currentTimeMillis()
        val client = HttpClient()

        return try {
            val encodedWord = URLEncoder.encode(word.trim(), "UTF-8")
            val url = "https://rifmovka.ru/rifma/$encodedWord#similar"

            val response = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                header("Referer", "https://rifmovka.ru/")
            }

            val html = response.bodyAsText()
            val result = parseRhymes(html, limit)

            val durationMs = System.currentTimeMillis() - startTime
            Log.d(TAG, "Found ${result.size} rhymes in ${durationMs}ms")

            trackRhymeSearch(word, result.size, durationMs, success = result.isNotEmpty())
            result

        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            trackRhymeSearch(word, 0, System.currentTimeMillis() - startTime, success = false)
            emptyList()
        } finally {
            client.close()
        }
    }

    private fun parseRhymes(html: String, limit: Int): List<String> {
        val rhymes = mutableSetOf<String>()

        val simPageStart = html.indexOf("id=\"typeSimPage\"")
        if (simPageStart != -1) {
            val simPageEnd = html.indexOf("<div class=\"page\" id=\"typeIntPage\"", simPageStart)
            val simPageHtml = html.substring(simPageStart, if (simPageEnd == -1) html.length else simPageEnd)

            val liRegex = Regex("""<li[^>]*class="[^"]*vis[^"]*"[^>]*>([^<]+(?:<[^>]+>[^<]*</[^>]+>)*[^<]*)</li>""", RegexOption.IGNORE_CASE)

            for (match in liRegex.findAll(simPageHtml)) {
                val wordHtml = match.groupValues[1]
                    .replace(Regex("""<[^>]+>"""), "")
                    .trim()

                if (wordHtml.isNotEmpty() &&
                    wordHtml.length >= 3 &&
                    wordHtml.matches(Regex("""[а-яё\-]+"""))) {
                    rhymes.add(wordHtml)
                }
            }
        }

        return rhymes.take(limit).toList()
    }

    private fun trackRhymeSearch(word: String, resultsCount: Int, durationMs: Long, success: Boolean) {
        val data = JSONObject().apply {
            put("word", word)
            put("results_count", resultsCount)
            put("duration_ms", durationMs)
            put("success", success)
            put("source", "rifmovka.ru")
        }

        val eventName = if (success) "rhyme_search_success" else "rhyme_search_failed"
        AppMetrica.reportEvent(eventName, data.toString())
    }
}