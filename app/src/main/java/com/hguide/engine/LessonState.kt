package com.hguide.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether a driving lesson is in progress (enabled by voice or while parked). Silences memory replay when active. */
class LessonState {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()
    var lessonName: String = "일반 운전 연습"
        private set
    fun start(name: String = "일반 운전 연습") { lessonName = name; _active.value = true }
    fun stop() { _active.value = false }
}
