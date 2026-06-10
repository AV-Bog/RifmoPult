@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package com.example.rifmopult

/*
 * Rifmopult – поэтический редактор с подбором рифм
 * Copyright (c) 2025 Arina Viktorovna Bogdanova
 *
 * MIT License
 */

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object StressAccentuator {

    private const val ACUTE = '\u0301'
    private var db: SQLiteDatabase? = null
    private val requestMutex = kotlinx.coroutines.sync.Mutex()

    fun init(context: Context) {
        db = StressCacheHelper(context).writableDatabase
    }

    suspend fun getStressed(word: String): String? {
        android.util.Log.d("StressDebug", "word='$word' vowels=${countVowels(word.lowercase())}")

        val lower = word.lowercase()

        if ('ё' in lower) {
            val stressed = applyStressToYo(lower)
            val stressedWithCase = restoreCase(word, stressed)
            saveToCache(lower, stressed)
            saveToCache(word, stressedWithCase)
            return stressedWithCase
        }

        val vowelCount = countVowels(lower)

        // Если в слове одна гласная
        if (vowelCount == 1) {
            val vowels = "аеёиоуыэюя"
            val stressPos = lower.indexOfFirst { it in vowels }
            if (stressPos >= 0) {
                val stressed = lower.substring(0, stressPos + 1) + ACUTE + lower.substring(stressPos + 1)
                val withCase = restoreCase(word, stressed)
                saveToCache(word, withCase)
                saveToCache(lower, stressed)
                android.util.Log.d("StressDebug", "Short word (1 vowel): $withCase")
                return withCase
            }
        }

        // Если слово очень короткое (1-2 буквы) и есть гласная
        if (lower.length <= 2 && vowelCount > 0) {
            val vowels = "аеёиоуыэюя"
            val stressPos = lower.indexOfFirst { it in vowels }
            if (stressPos >= 0) {
                val stressed = lower.substring(0, stressPos + 1) + ACUTE + lower.substring(stressPos + 1)
                val withCase = restoreCase(word, stressed)
                saveToCache(word, withCase)
                saveToCache(lower, stressed)
                android.util.Log.d("StressDebug", "Very short word: $withCase")
                return withCase
            }
        }

        // Проверка кэша для всех слов
        getFromCache(word)?.let {
            android.util.Log.d("StressDebug", "From cache: $it")
            return it
        }
        getFromCache(lower)?.let {
            val restored = restoreCase(word, it)
            android.util.Log.d("StressDebug", "From cache (lower): $restored")
            return restored
        }

        return requestMutex.withLock {
            getFromCache(word)?.let { return@withLock it }
            getFromCache(lower)?.let { return@withLock restoreCase(word, it) }

            val fromApi = fetchFromMorpher(lower)
            if (fromApi != null) {
                val withCase = restoreCase(word, fromApi)
                saveToCache(word, withCase)
                saveToCache(lower, fromApi)
                withCase
            } else {
                val fallback = applyFallbackStress(lower)
                if (fallback != null) {
                    saveToCache(lower, fallback)
                    val withCase = restoreCase(word, fallback)
                    saveToCache(word, withCase)
                    withCase
                } else {
                    word
                }
            }
        }
    }

    private suspend fun fetchFromMorpher(word: String): String? =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://morpher.ru/ws3")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                // Отправляем слово в теле запроса
                connection.outputStream.use { os ->
                    os.write(word.toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    android.util.Log.e("StressDebug", "Morpher API error: $responseCode for word '$word'")
                    return@withContext null
                }

                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
                android.util.Log.d("StressDebug", "Morpher response for '$word': $response")

                // Парсим JSON ответ
                val json = JSONObject(response)
                val stressedWord = json.optString("string", null)

                if (stressedWord != null && stressedWord.isNotEmpty()) {
                    android.util.Log.d("StressDebug", "Morpher returned: '$stressedWord' for '$word'")
                    return@withContext stressedWord
                }

                return@withContext null

            } catch (e: Exception) {
                android.util.Log.e("StressDebug", "Error fetching from Morpher for '$word': ${e.message}")
                return@withContext null
            } finally {
                connection?.disconnect()
            }
        }

    private fun applyStressToYo(word: String): String {
        val result = StringBuilder()
        for (ch in word) {
            result.append(ch)
            if (ch == 'ё' || ch == 'Ё') result.append(ACUTE)
        }
        return result.toString()
    }

    private fun applyFallbackStress(word: String): String? {
        val vowels = "аеёиоуыэюя"
        val vowelPositions = word.indices.filter { word[it] in vowels }

        // Для односложных слов ставим ударение на единственную гласную!!!
        if (vowelPositions.size == 1) {
            val stressPos = vowelPositions[0]
            return word.substring(0, stressPos + 1) + ACUTE + word.substring(stressPos + 1)
        }

        // Для многосложных - на предпоследнюю гласную
        if (vowelPositions.size >= 2) {
            val stressPos = vowelPositions[vowelPositions.size - 2]
            return word.substring(0, stressPos + 1) + ACUTE + word.substring(stressPos + 1)
        }

        return null
    }

    // Восстановление регистра с сохранением позиции ударения
    private fun restoreCase(original: String, stressed: String): String {
        if (original == original.uppercase()) return stressed.uppercase()

        val originalLower = original.lowercase()
        val stressedLower = stressed.lowercase()

        // Если буквы не совпадают - возвращаем как есть
        if (originalLower != stressedLower.replace(ACUTE.toString(), "")) {
            return stressed
        }

        // Для однобуквенных слов - просто возвращаем stressed с правильным регистром
        if (original.length == 1) {
            return if (original[0].isUpperCase())
                stressed.uppercase()
            else
                stressed.lowercase()
        }

        val result = StringBuilder()
        var stressPos = -1

        for (i in originalLower.indices) {
            val origChar = original[i]
            val stressedChar = stressedLower[i]

            if (stressedLower[i] == ACUTE) {
                stressPos = result.length
                continue
            }

            result.append(
                if (origChar.isUpperCase()) stressedChar.uppercaseChar()
                else stressedChar.lowercaseChar()
            )
        }

        return if (stressPos >= 0) {
            result.insert(stressPos, ACUTE).toString()
        } else {
            result.toString()
        }
    }

    fun countVowels(word: String): Int {
        val vowels = "аеёиоуыэюя"
        return word.count { it in vowels }
    }

    fun getFromCache(word: String): String? {
        return try {
            val cursor = db?.query(
                "stress_cache", arrayOf("stressed"),
                "word = ?", arrayOf(word),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    internal fun saveToCache(word: String, stressed: String) {
        try {
            val values = android.content.ContentValues().apply {
                put("word", word)
                put("stressed", stressed)
            }
            db?.insertWithOnConflict(
                "stress_cache", null, values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        } catch (_: Exception) { }
    }

    fun buildStressedString(word: String, vowelIndex: Int): String {
        val vowels = "аеёиоуыэюя"
        val lower = word.lowercase()
        var currentVowelIdx = 0
        val result = StringBuilder()
        for (ch in lower) {
            result.append(ch)
            if (ch in vowels) {
                if (currentVowelIdx == vowelIndex) {
                    result.append(ACUTE)
                }
                currentVowelIdx++
            }
        }
        return result.toString()
    }

    fun getStressedSync(word: String): String? = runBlocking { getStressed(word) }

    class StressCacheHelper(context: Context) :
        SQLiteOpenHelper(context, "stress_cache.db", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE stress_cache (word TEXT PRIMARY KEY, stressed TEXT)")
        }

        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
            db.execSQL("DROP TABLE IF EXISTS stress_cache")
            onCreate(db)
        }
    }
}