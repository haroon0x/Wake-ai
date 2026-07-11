package com.wake.app

import android.app.Application
import com.wake.app.capture.Ingest
import com.wake.app.data.MemoryDao
import com.wake.app.data.WakeDb
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    override fun onCreate() {
        super.onCreate()
        instance = this
        dao = WakeDb.get(this).memoryDao()
        ingest = Ingest(dao, scope)
        retriever = Retriever(dao)
    }

    companion object {
        lateinit var instance: WakeApp
            private set
    }
}
