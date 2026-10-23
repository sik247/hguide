package com.hguide.engine

/** Live driving facts that gate memory replay; written by the trip controller, read by the replay controller. */
class DrivingGuards {
    @Volatile var turnSignalOn = false
    @Volatile var lastHarshDecelMs: Long? = null
}
