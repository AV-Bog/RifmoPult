package com.example.rifmopult

/*
 * Rifmopult – поэтический редактор с подбором рифм
 * Copyright (c) 2025 Arina Viktorovna Bogdanova
 *
 * MIT License
 */

import android.app.Application
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

class RifmoPultApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = AppMetricaConfig.newConfigBuilder(BuildConfig.APPMETRICA_API_KEY)
            .withLogs()
            .build()
        AppMetrica.activate(this, config)

        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler())
    }
}