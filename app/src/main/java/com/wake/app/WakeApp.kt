package com.wake.app

import android.app.Application
import com.wake.app.capture.Ingest
import com.wake.app.data.MemoryDao
import com.wake.app.data.WakeDb
import com.wake.app.answer.GroundedAnswerer
import com.wake.app.gemma.GemmaEngine
import com.wake.app.llm.GeminiEngine
import com.wake.app.llm.LlmEngine
import com.wake.app.data.toByteArray
import com.wake.app.retrieval.Embedder
import com.wake.app.retrieval.Retriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WakeApp : Application() {

    lateinit var dao: MemoryDao
        private set
    lateinit var ingest: Ingest
        private set
    lateinit var retriever: Retriever
        private set
    lateinit var embedder: Embedder
        private set
    lateinit var gemmaEngine: GemmaEngine
        private set
    lateinit var geminiEngine: GeminiEngine
        private set
    lateinit var gemmaCloudEngine: GeminiEngine
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    override fun onCreate() {
        super.onCreate()
        instance = this
        dao = WakeDb.get(this).memoryDao()
        embedder = Embedder(this)
        ingest = Ingest(dao, scope, embedder, { Prefs.retentionDays(this) })
        retriever = Retriever(dao, embedder)
        gemmaEngine = GemmaEngine(this)
        geminiEngine = GeminiEngine(apiKeyProvider = { Prefs.geminiApiKey(this) })
        gemmaCloudEngine = GeminiEngine(
            apiKeyProvider = { Prefs.geminiApiKey(this) },
            model = GeminiEngine.GEMMA_CLOUD_MODEL,
            name = "Gemma cloud"
        )
        val modelPath = GemmaEngine.defaultModelPath(this)
        if (java.io.File(modelPath).exists()) {
            scope.launch { gemmaEngine.load(modelPath) }
        }
        scope.launch {
            applyRetention()
            if (!embedder.ensure()) return@launch
            while (true) {
                val events = dao.unembedded(50)
                if (events.isEmpty()) return@launch
                var embedded = 0
                events.forEach { event ->
                    runCatching {
                        embedder.embed(event.text)?.let {
                            dao.setEmbedding(event.id, it.toByteArray())
                            embedded++
                        }
                    }
                }
                if (embedded == 0) return@launch
            }
        }
    }

    fun currentEngine(): LlmEngine = when (Prefs.engineChoice(this)) {
        "gemini" -> geminiEngine
        "gemma_cloud" -> gemmaCloudEngine
        else -> gemmaEngine
    }

    fun answerer(): GroundedAnswerer = GroundedAnswerer(retriever, currentEngine())

    suspend fun applyRetention() {
        val days = Prefs.retentionDays(this)
        if (days > 0) dao.deleteOlderThan(System.currentTimeMillis() - days * 86_400_000L)
    }

    override fun onTerminate() {
        gemmaEngine.close()
        super.onTerminate()
    }

    companion object {
        lateinit var instance: WakeApp
            private set
    }
}
