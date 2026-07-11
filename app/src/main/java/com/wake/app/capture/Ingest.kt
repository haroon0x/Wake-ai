package com.wake.app.capture

import android.util.Log
import com.wake.app.data.MemoryDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class Ingest(
    private val dao: MemoryDao,
    private val scope: CoroutineScope,
    private val dedupWindowMillis: Long = 15 * 1000
) {
    private val grouper = SessionGrouper()

    fun submit(raw: RawCapture) {
        val text = raw.text.trim()
        if (text.isEmpty()) return
        if (Exclusions.isExcluded(raw.pkg)) return

        scope.launch {
            val hash = raw.contentHash()
            if (dao.existsRecent(hash, raw.timestamp - dedupWindowMillis)) return@launch
            val sessionId = grouper.assign(raw.timestamp)
            val id = dao.insert(raw.toEvent(sessionId, hash))
            Log.d("WAKE", "ingest id=$id source=${raw.source} pkg=${raw.pkg} sender=${raw.sender} text=${text.take(60)}")
        }
    }
}
