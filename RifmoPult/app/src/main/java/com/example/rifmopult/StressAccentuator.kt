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

object StressAccentuator {

    private const val ACUTE = '\u0301'
    private var db: SQLiteDatabase? = null
    private val requestMutex = kotlinx.coroutines.sync.Mutex()

    fun init(context: Context) {
        db = StressCacheHelper(context).writableDatabase
    }

    suspend fun getStressed(word: String): String? {
        android.util.Log.e("StressDebug", "word='$word' vowels=${countVowels(word.lowercase())} chars=${word.map{it.code}}")
        android.util.Log.d("StressDebug", "Word bytes: ${word.map { it.code }}")
        val lower = word.lowercase()

        if ('ё' in lower) return applyStressToYo(word)

        val vowelCount = countVowels(lower)
        if (vowelCount == 0) return word
        if (vowelCount == 1) return placeStressOnOnlyVowel(word)

        // Проверка кэша ТОЛЬКО для многосложных
        getFromCache(word)?.let {
            android.util.Log.d("StressDebug", "From cache: $it")
            return it
        }
        getFromCache(lower)?.let {
            val restored = restoreCase(word, it)
            android.util.Log.d("StressDebug", "From cache (lower): $restored")
            return restored
        }

        // Запрос к API для многосложных
        return requestMutex.withLock {
            getFromCache(word)?.let { return@withLock it }
            getFromCache(lower)?.let { return@withLock restoreCase(word, it) }

            val fromApi = fetchFromWiktionary(lower)
            if (fromApi != null) {
                val withCase = restoreCase(word, fromApi)
                saveToCache(word, withCase)
                saveToCache(lower, fromApi)
                withCase
            } else {
                // Фоллбэк для многосложных
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

    private fun placeStressOnOnlyVowel(word: String): String {
        val vowels = "аеёиоуыэюяАЕЁИОУЫЭЮЯ"
        val result = StringBuilder()

        for (ch in word) {
            result.append(ch)
            if (ch in vowels) {
                result.append(ACUTE)
                break
            }
        }

        android.util.Log.d("StressDebug", "placeStressOnOnlyVowel: '$word' -> '$result'")

        return result.toString()
    }

    private fun applyFallbackStress(word: String): String? {
        val vowels = "аеёиоуыэюя"
        val vowelPositions = word.indices.filter { word[it] in vowels }
        if (vowelPositions.size < 2) return null

        // Ударение на предпоследнюю гласную (не для всех слов конечно но что поделать  лучше так чем ничего)
        val stressPos = vowelPositions[vowelPositions.size - 2]
        return word.substring(0, stressPos + 1) + ACUTE + word.substring(stressPos + 1)
    }


    private fun applyStressToYo(word: String): String {
        val result = StringBuilder()
        for (ch in word) {
            result.append(ch)
            if (ch == 'ё' || ch == 'Ё') result.append(ACUTE)
        }
        return result.toString()
    }

    private suspend fun fetchFromWiktionary(word: String, retryCount: Int = 0): String? =
        withContext(Dispatchers.IO) {
            var connection: java.net.HttpURLConnection? = null
            try {
                val encoded = java.net.URLEncoder.encode(word, "UTF-8")
                val url = "https://ru.wiktionary.org/w/api.php" +
                        "?action=query&titles=$encoded&prop=revisions" +
                        "&rvprop=content&format=json&rvslots=main"

                android.util.Log.d("RhymeURL", url)

                connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("User-Agent", "Rifmopult/1.0 (Android; stress accentuator)")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode != 200) return@withContext null

                val response = connection.inputStream.bufferedReader().readText()
                android.util.Log.d("RhymeRawResponse", response)
                val json = JSONObject(response)

                val pages = json.getJSONObject("query").getJSONObject("pages")
                val pageId = pages.keys().next()
                val page = pages.getJSONObject(pageId)

                if (page.has("missing")) {
                    android.util.Log.d("StressDebug", "Missing in wiktionary: $word")
                    return@withContext null
                }

                val content = try {
                    page.getJSONArray("revisions")
                        .getJSONObject(0)
                        .getJSONObject("slots")
                        .getJSONObject("main")
                        .getString("*")
                } catch (e: Exception) {
                    return@withContext null
                }

                // Обработка перенаправления
                val redirectPattern = Regex("""#перенаправление \[\[([^]]+)]]""")
                val redirectMatch = redirectPattern.find(content)
                if (redirectMatch != null) {
                    val targetWord = redirectMatch.groupValues[1]
                    android.util.Log.d("StressFlow", "Redirect: $word -> $targetWord")
                    return@withContext fetchFromWiktionary(targetWord, retryCount)
                }

                // Поиск ударения в шаблоне по-слогам
                val syllablePattern = Regex("""\{\{по[-\s]слогам\|([^}|]+)\|([^}]+)\}\}""")
                val match = syllablePattern.find(content)

                if (match != null) {
                    val stressedWord = match.groupValues[1] + match.groupValues[2]
                    android.util.Log.d("StressFlow", "Found stressed: $stressedWord for $word")
                    return@withContext stressedWord.lowercase()
                }

                return@withContext null

            } catch (e: java.net.UnknownHostException) {
                android.util.Log.e("StressDebug", "Network error for $word: ${e.message}")
                return@withContext null
            } catch (e: Exception) {
                android.util.Log.e("StressDebug", "Error for $word: ${e.javaClass.simpleName}: ${e.message}")
                return@withContext null
            } finally {
                connection?.disconnect()
            }
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

        val result = StringBuilder()
        var stressPos = -1

        // Строим результат, сохраняя регистр из original
        for (i in originalLower.indices) {
            val origChar = original[i]
            var stressedChar = stressedLower[i]

            // Пропускаем символ ударения при проверке
            if (stressedLower[i] == ACUTE) {
                stressPos = result.length
                continue
            }

            result.append(
                if (origChar.isUpperCase()) stressedChar.uppercaseChar()
                else stressedChar.lowercaseChar()
            )
        }

        // Вставляем ударение на правильную позицию
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
        } catch (e: Exception) { null }
    }

    private fun saveToCache(word: String, stressed: String) {
        try {
            val values = android.content.ContentValues().apply {
                put("word", word)
                put("stressed", stressed)
            }
            db?.insertWithOnConflict(
                "stress_cache", null, values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        } catch (e: Exception) { }
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