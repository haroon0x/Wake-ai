package com.wake.app

import android.app.Application
import com.wake.app.capture.Ingest
import com.wake.app.data.MemoryDao
import com.wake.app.data.WakeDb
import com.wake.app.answer.GroundedAnswerer
import com.wake.app.gemma.GemmaEngine
import com.wake.app.llm.GeminiEngine
import com.wake.app.llm.LlmEngine
import com.wake.app.retrieval.Retriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WakeApp : Application() {

    lateinit var dao: MemoryDao
        private set
    lateinit var ingest: Ingest
        private set
    lateinit var retriever: Retriever
        private set
    lateinit var gemmaEngine: GemmaEngine
        private set
    lateinit var geminiEngine: GeminiEngine
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    override fun onCreate() {
        super.onCreate()
        instance = this
        dao = WakeDb.get(this).memoryDao()
        ingest = Ingest(dao, scope)
        retriever = Retriever(dao)
        gemmaEngine = GemmaEngine()
        geminiEngine = GeminiEngine(apiKeyProvider = { Prefs.geminiApiKey(this) })
        val modelPath = GemmaEngine.defaultModelPath(this)
        if (java.io.File(modelPath).exists()) {
            scope.launch { gemmaEngine.load(modelPath) }
        }
    }

    fun currentEngine(): LlmEngine =
        if (Prefs.engineChoice(this) == "gemini") geminiEngine else gemmaEngine

    fun answerer(): GroundedAnswerer = GroundedAnswerer(retriever, currentEngine())

    companion object {
        lateinit var instance: WakeApp
            private set
    }
}
