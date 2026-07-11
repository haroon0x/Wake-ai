package com.wake.app.capture

class SessionGrouper(private val gapMillis: Long = 2 * 60 * 1000) {
    private var currentSession: Long = 0
    private var lastTimestamp: Long = 0

    fun assign(timestamp: Long): Long {
        if (currentSession == 0L || timestamp - lastTimestamp > gapMillis) {
            currentSession = timestamp
        }
        lastTimestamp = timestamp
        return currentSession
    }
}
