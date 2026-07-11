package com.wake.app

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "wake_prefs"
    private const val GEMINI_API_KEY = "gemini_api_key"
    private const val ENGINE_CHOICE = "engine_choice"
    private const val RETENTION_DAYS = "retention_days"
    private const val DEEP_RESEARCH_ACK = "deep_research_ack"

    fun geminiApiKey(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(GEMINI_API_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.WAKE_GEMINI_KEY

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
            .getInt(RETENTION_DAYS, 0)

    fun deepResearchAcknowledged(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(DEEP_RESEARCH_ACK, false)

    fun setDeepResearchAcknowledged(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(DEEP_RESEARCH_ACK, true)
            .apply()
    }

    fun setRetentionDays(context: Context, days: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(RETENTION_DAYS, days)
            .apply()
    }
}
