package com.hguide.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-app log of what the car "said" / decided. Holds no transcript text from conversations. */
class EventLog(private val max: Int = 60) {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()
    fun add(line: String) { _lines.value = (listOf(line) + _lines.value).take(max) }
}
