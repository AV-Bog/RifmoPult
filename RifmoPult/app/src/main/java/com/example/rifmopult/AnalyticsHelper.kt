package com.example.rifmopult

/*
 * Rifmopult – поэтический редактор с подбором рифм
 * Copyright (c) 2025 Arina Viktorovna Bogdanova
 *
 * MIT License
 */

import io.appmetrica.analytics.AppMetrica
import org.json.JSONObject

/**
 * Помощник для отправки анонимной статистики. Никаких личных данных не собирает!
 */
object AnalyticsHelper {
    fun trackScreenOpen(screenName: String) {
        val data = JSONObject().apply {
            put("screen", screenName)
            put("timestamp", System.currentTimeMillis())
        }.toString()

        AppMetrica.reportEvent("screen_open", data)
    }

    fun trackNoteSaved(noteLength: Int, editTimeSeconds: Long, isNewNote: Boolean) {
        val data = JSONObject().apply {
            put("note_length", noteLength)
            put("edit_time_seconds", editTimeSeconds)
            put("is_new_note", isNewNote)
            put("has_rhyme", noteLength > 0) // если есть текст, возможно есть рифма
        }.toString()

        AppMetrica.reportEvent("note_saved", data)
    }

    fun trackNoteDeleted(noteLength: Int, noteAgeHours: Long) {
        val data = JSONObject().apply {
            put("note_length", noteLength)
            put("note_age_hours", noteAgeHours)
        }.toString()

        AppMetrica.reportEvent("note_deleted", data)
    }

    fun trackRhymeSearch(word: String, resultsCount: Int, durationMs: Long, success: Boolean) {
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

    fun trackSettingChanged(settingName: String, newValue: String) {
        val data = JSONObject().apply {
            put("setting", settingName)
            put("value", newValue)
        }.toString()

        AppMetrica.reportEvent("setting_changed", data)
    }

    fun trackError(errorType: String, message: String) {
        val data = JSONObject().apply {
            put("error_type", errorType)
            put("message", message.take(200)) // ограничиваем длину
        }.toString()

        AppMetrica.reportEvent("app_error", data)
    }

    fun trackSessionEnd(sessionDurationSeconds: Long, screensVisited: Int) {
        val data = JSONObject().apply {
            put("session_duration_seconds", sessionDurationSeconds)
            put("screens_visited", screensVisited)
        }.toString()

        AppMetrica.reportEvent("session_end", data)
    }

    fun trackNoteOpened(noteLength: Int) {
        val data = JSONObject().apply {
            put("note_length", noteLength)
        }.toString()

        AppMetrica.reportEvent("note_opened", data)
    }

    /**
     * Отслеживание нажатия на кнопку
     * @param buttonId идентификатор кнопки (fab_add_note, btn_settings и т.д.)
     */
    fun trackButtonClick(buttonId: String) {
        val data = JSONObject().apply {
            put("button_id", buttonId)
            put("screen", "NotesActivity")
        }.toString()

        AppMetrica.reportEvent("button_click", data)
    }

    /**
     * Отслеживание выполнения поиска
     * @param queryLength длина поискового запроса
     * @param resultsCount количество найденных результатов
     */
    fun trackSearchPerformed(queryLength: Int, resultsCount: Int) {
        val data = JSONObject().apply {
            put("query_length", queryLength)
            put("results_count", resultsCount)
        }.toString()

        AppMetrica.reportEvent("search_performed", data)
    }

    /**
     * Общее событие без дополнительных данных
     * @param eventName название события
     */
    fun trackEvent(eventName: String) {
        AppMetrica.reportEvent(eventName, "")
    }
}