package com.wake.app

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "wake_prefs"
    private const val GEMINI_API_KEY = "gemini_api_key"
    private const val ENGINE_CHOICE = "engine_choice"

    fun geminiApiKey(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(GEMINI_API_KEY, null)

    fun setGeminiApiKey(context: Context, v: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(GEMINI_API_KEY, v)
            .apply()
    }

    fun engineChoice(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(ENGINE_CHOICE, "gemma")
            ?: "gemma"

    fun setEngineChoice(context: Context, v: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(ENGINE_CHOICE, v)
            .apply()
    }
}
