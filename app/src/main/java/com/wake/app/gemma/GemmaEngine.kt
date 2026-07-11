package com.wake.app.gemma

import android.content.Context
import com.wake.app.llm.LlmEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class GemmaEngine(
    private val fallbackEnabled: Boolean = true,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : LlmEngine {

    override val name = "Gemma"

    @Volatile
    private var ready = false

    suspend fun load(modelPath: String) {
        withContext(dispatcher) {
            if (fallbackEnabled) {
                ready = modelPath.isNotBlank()
                return@withContext
            }

            // TODO(litert): Copy the exact Gradle coordinate and LiteRT-LM model-loading class and method names from the official google-ai-edge/LiteRT-LM Android sample.
            // TODO(litert): Copy the GPU/OpenCL backend selection API from the official sample, falling back to CPU if GPU creation fails.
            // TODO(litert): Store the loaded model and backend for session creation.
            ready = false
        }
    }

    override fun isReady(): Boolean = ready

    override fun generate(prompt: String): Flow<String> = flow {
        if (!ready) return@flow

        if (fallbackEnabled) {
            "LiteRT-LM fallback: $prompt".chunked(24).forEach { emit(it) }
            return@flow
        }

        // TODO(litert): Copy the LiteRT-LM session creation API and generation configuration from the official google-ai-edge/LiteRT-LM Android sample.
        // TODO(litert): Copy the streaming generation API from the official sample and emit each token or text chunk here.
    }.flowOn(dispatcher)

    companion object {
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"

        fun defaultModelPath(context: Context): String =
            java.io.File(context.getExternalFilesDir(null), MODEL_FILE).absolutePath
    }
}
