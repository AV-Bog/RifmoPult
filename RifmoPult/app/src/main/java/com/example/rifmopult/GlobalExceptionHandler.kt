package com.example.rifmopult

/*
 * Rifmopult – поэтический редактор с подбором рифм
 * Copyright (c) 2025 Arina Viktorovna Bogdanova
 *
 * MIT License
 */

import android.util.Log
import io.appmetrica.analytics.AppMetrica

class GlobalExceptionHandler : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        AppMetrica.reportError("UncaughtException", throwable)

        val errorData = org.json.JSONObject().apply {
            put("thread_name", thread.name)
            put("error_type", throwable.javaClass.simpleName)
            put("error_message", throwable.message?.take(500) ?: "No message")
        }.toString()

        AppMetrica.reportEvent("app_crash", errorData)

        defaultHandler?.uncaughtException(thread, throwable)
    }
}