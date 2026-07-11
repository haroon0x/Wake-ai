package com.wake.app

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "wake_prefs"
    private const val GEMINI_API_KEY = "gemini_api_key"
    private const val ENGINE_CHOICE = "engine_choice"
    private const val RETENTION_DAYS = "retention_days"

    fun geminiApiKey(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(GEMINI_API_KEY, null)
            ?.takeIf { it.isNotBlank() }
            .orEmpty()

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
            .getString(ENGINE_CHOICE, "gemma_cloud")
            ?: "gemma_cloud"

    fun setEngineChoice(context: Context, v: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(ENGINE_CHOICE, v)
            .apply()
    }

    fun retentionDays(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(RETENTION_DAYS, 30)

    fun setRetentionDays(context: Context, days: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(RETENTION_DAYS, days)
            .apply()
    }
}
