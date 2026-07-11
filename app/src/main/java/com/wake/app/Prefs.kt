package com.wake.app

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "wake_prefs"
    private const val GEMINI_API_KEY = "gemini_api_key"
    private const val ENGINE_CHOICE = "engine_choice"
    private val DEFAULT_GEMINI_KEY = BuildConfig.GEMINI_DEFAULT_KEY

    fun geminiApiKey(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(GEMINI_API_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_GEMINI_KEY

    fun savedGeminiApiKey(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(GEMINI_API_KEY, null)
            .orEmpty()

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
