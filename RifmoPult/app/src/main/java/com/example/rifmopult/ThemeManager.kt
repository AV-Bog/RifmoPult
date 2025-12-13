package com.example.rifmopult

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    private val PREFS_NAME = "app_settings"
    private val KEY_THEME = "theme_mode"

    val MODE_SYSTEM = 0
    val MODE_LIGHT = 1
    val MODE_DARK = 2

    fun getSavedThemeMode(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME, MODE_SYSTEM)
    }

    fun saveThemeMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME, mode)
            .apply()
    }

    fun applyTheme(context: Context) {
        val mode = getSavedThemeMode(context)
        when (mode) {
            MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            MODE_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}