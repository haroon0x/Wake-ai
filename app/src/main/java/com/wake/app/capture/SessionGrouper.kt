package com.wake.app.capture

import com.wake.app.data.SOURCE_SCREEN_TEXT

class SessionGrouper(
    private val gapMillis: Long = 2 * 60 * 1000,
    private val appSwitchGapMillis: Long = 30 * 1000
) {
    private var currentSession: Long = 0
    private var lastTimestamp: Long = 0
    private var lastActivePkg: String? = null

    fun restore(sessionId: Long, timestamp: Long, activePkg: String?) {
        currentSession = sessionId
        lastTimestamp = timestamp
        lastActivePkg = activePkg
    }

    fun assign(timestamp: Long, source: String, pkg: String?): Long {
        val elapsed = timestamp - lastTimestamp
        val significantAppSwitch = source == SOURCE_SCREEN_TEXT &&
            lastActivePkg != null && pkg != lastActivePkg && elapsed > appSwitchGapMillis
        if (currentSession == 0L || elapsed > gapMillis || significantAppSwitch) {
            currentSession = timestamp
        }
        lastTimestamp = maxOf(lastTimestamp, timestamp)
        if (source == SOURCE_SCREEN_TEXT) lastActivePkg = pkg
        return currentSession
    }
}
