package com.wake.app.gemma

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.wake.app.llm.LlmEngine
import com.wake.app.llm.LlmException
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class GemmaEngine(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : LlmEngine {

    sealed interface State {
        data object Missing : State
        data object Loading : State
        data class Ready(val backend: String) : State
        data class Failed(val message: String) : State
    }

    override val name = "Gemma on-device"

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    @Volatile
    private var engine: Engine? = null

    private val _state = MutableStateFlow<State>(State.Missing)
    val stateFlow: StateFlow<State> = _state.asStateFlow()
    val state: State get() = _state.value

    suspend fun load(modelPath: String) {
        withContext(dispatcher) {
            mutex.withLock {
                val model = File(modelPath)
                if (!model.isFile || !model.canRead()) {
                    _state.value = State.Missing
                    return@withLock
                }

                _state.value = State.Loading
                engine?.close()
                engine = null

                val gpu = runCatching { createEngine(modelPath, Backend.GPU()) }
                if (gpu.isSuccess) {
                    engine = gpu.getOrThrow()
                    _state.value = State.Ready("GPU")
                    return@withLock
                }

                val cpu = runCatching { createEngine(modelPath, Backend.CPU()) }
                if (cpu.isSuccess) {
                    engine = cpu.getOrThrow()
                    _state.value = State.Ready("CPU")
                    return@withLock
                }

                _state.value = State.Failed(cpu.exceptionOrNull()?.message ?: "Model initialization failed.")
            }
        }
    }

    override fun isReady(): Boolean = state is State.Ready && engine != null

    override fun generate(prompt: String): Flow<String> = flow {
        mutex.withLock {
            val loadedEngine = engine ?: throw LlmException(stateMessage())
            try {
                loadedEngine.createConversation().use { conversation ->
                    conversation.sendMessageAsync(prompt).collect { message ->
                        emit(message.toString())
                    }
                }
            } catch (e: Exception) {
                if (e is LlmException) throw e
                throw LlmException("On-device generation failed: ${e.message ?: "unknown error"}", e)
            }
        }
    }.flowOn(dispatcher)

    fun stateMessage(): String = when (val current = state) {
        State.Missing -> "Model file missing ($MODEL_FILE)"
        State.Loading -> "Loading model"
        is State.Ready -> "Ready on ${current.backend}"
        is State.Failed -> "Model failed: ${current.message}"
    }

    private fun createEngine(modelPath: String, backend: Backend): Engine =
        Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = backend,
                cacheDir = appContext.cacheDir.path
            )
        ).also { it.initialize() }

    fun close() {
        engine?.close()
        engine = null
        _state.value = State.Missing
    }

    companion object {
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"

        fun defaultModelPath(context: Context): String =
            File(context.getExternalFilesDir(null), MODEL_FILE).absolutePath
    }
}
