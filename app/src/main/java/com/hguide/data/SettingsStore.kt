package com.hguide.data

import android.content.Context
import com.hguide.core.TripContext

/** Greeting voice: a personId, or null = off. */
interface SettingsStore {
    var greetingVoicePersonId: String?
    var driverPersonId: String?
    var proximityRadiusM: Int
    var morningStartHour: Int
    var morningEndHour: Int
    var eveningStartHour: Int
    var eveningEndHour: Int
    var rawAudioRetentionDays: Int
    fun customGreetingPath(personId: String, ctx: TripContext): String?
    fun setCustomGreetingPath(personId: String, ctx: TripContext, path: String?)
    fun clear()
}

class InMemorySettings : SettingsStore {
    override var greetingVoicePersonId: String? = null
    override var driverPersonId: String? = null
    override var proximityRadiusM = 150
    override var morningStartHour = 5
    override var morningEndHour = 10
    override var eveningStartHour = 17
    override var eveningEndHour = 21
    override var rawAudioRetentionDays = 30
    private val custom = mutableMapOf<String, String>()
    override fun customGreetingPath(personId: String, ctx: TripContext) = custom["$personId/${ctx.name}"]
    override fun setCustomGreetingPath(personId: String, ctx: TripContext, path: String?) {
        if (path == null) custom.remove("$personId/${ctx.name}") else custom["$personId/${ctx.name}"] = path
    }
    override fun clear() { custom.clear() }
}

class PrefsSettings(context: Context) : SettingsStore {
    private val p = context.getSharedPreferences("hguide_settings", Context.MODE_PRIVATE)
    override var greetingVoicePersonId: String?
        get() = p.getString("greetingVoice", null)
        set(v) { p.edit().putString("greetingVoice", v).apply() }
    override var driverPersonId: String?
        get() = p.getString("driver", null)
        set(v) { p.edit().putString("driver", v).apply() }
    override var proximityRadiusM: Int
        get() = p.getInt("radius", 150)
        set(v) { p.edit().putInt("radius", v).apply() }
    override var morningStartHour: Int
        get() = p.getInt("mStart", 5)
        set(v) { p.edit().putInt("mStart", v).apply() }
    override var morningEndHour: Int
        get() = p.getInt("mEnd", 10)
        set(v) { p.edit().putInt("mEnd", v).apply() }
    override var eveningStartHour: Int
        get() = p.getInt("eStart", 17)
        set(v) { p.edit().putInt("eStart", v).apply() }
    override var eveningEndHour: Int
        get() = p.getInt("eEnd", 21)
        set(v) { p.edit().putInt("eEnd", v).apply() }
    override var rawAudioRetentionDays: Int
        get() = p.getInt("retention", 30)
        set(v) { p.edit().putInt("retention", v).apply() }
    override fun customGreetingPath(personId: String, ctx: TripContext) = p.getString("custom/$personId/${ctx.name}", null)
    override fun setCustomGreetingPath(personId: String, ctx: TripContext, path: String?) {
        p.edit().apply { if (path == null) remove("custom/$personId/${ctx.name}") else putString("custom/$personId/${ctx.name}", path) }.apply()
    }
    override fun clear() { p.edit().clear().apply() }
}
