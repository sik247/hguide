package com.hguide.family

/** Tracks per-trip recording consent. Recording is only possible when every expected rider said yes. */
class ConsentManager {
    private class Session(val expected: List<String>, val answers: MutableMap<String, Boolean> = mutableMapOf())
    private val sessions = mutableMapOf<Long, Session>()

    fun begin(tripId: Long, expectedPersonIds: List<String>) {
        sessions[tripId] = Session(expectedPersonIds.distinct())
    }

    fun respond(tripId: Long, personId: String, consent: Boolean) {
        val s = sessions[tripId] ?: return
        if (personId in s.expected) s.answers[personId] = consent
    }

    fun expected(tripId: Long): List<String> = sessions[tripId]?.expected.orEmpty()
    fun answered(tripId: Long, personId: String): Boolean? = sessions[tripId]?.answers?.get(personId)
    fun anyRefused(tripId: Long): Boolean = sessions[tripId]?.answers?.values?.any { !it } == true
    fun consentedIds(tripId: Long): List<String> = sessions[tripId]?.answers?.filterValues { it }?.keys?.toList().orEmpty()

    /** The only gate in front of the microphone: a non-empty rider list where everyone explicitly consented. */
    fun allConsented(tripId: Long): Boolean {
        val s = sessions[tripId] ?: return false
        return s.expected.isNotEmpty() && s.expected.all { s.answers[it] == true }
    }

    /** Withdraws consent for the rest of the trip (e.g. "기록 그만"). */
    fun withdraw(tripId: Long) { sessions.remove(tripId) }
}
