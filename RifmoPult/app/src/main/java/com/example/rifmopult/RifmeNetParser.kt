package com.example.rifmopult

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.jsoup.Jsoup
import java.net.URLEncoder

object RifmeNetParser {

    private const val BASE_URL = "https://rifme.net/r/"

    suspend fun fetchRhymes(word: String, limit: Int = 100): List<String> {
        if (word.isBlank()) return emptyList()

        val client = HttpClient()
        return try {
            val encodedWord = URLEncoder.encode(word.trim().lowercase(), "UTF-8")
            val url = "$BASE_URL$encodedWord"

            val html = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Android) AppleWebKit/537.36")
            }.bodyAsText()

            val doc = Jsoup.parse(html)
            val rhymes = mutableListOf<String>()

            doc.select("#tochnye li[data-w]").forEach { element ->
                val rhyme = element.attr("data-w").trim()
                if (rhyme.isNotEmpty() &&
                    !rhyme.contains("://") &&
                    rhyme != word &&
                    rhyme.all { it.isLetter() || it == '-' || it == '\'' }) {
                    rhymes.add(rhyme)
                    if (rhymes.size >= limit) return@forEach
                }
            }

            rhymes
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            client.close()
        }
    }
}