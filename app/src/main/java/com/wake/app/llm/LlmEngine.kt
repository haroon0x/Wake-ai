package com.wake.app.llm

import kotlinx.coroutines.flow.Flow

interface LlmEngine {
    val name: String

    fun isReady(): Boolean

    fun generate(prompt: String): Flow<String>
}
