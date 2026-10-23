package com.hguide.speech

import com.hguide.core.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Typecast text-to-speech for emulator demo recordings (debug builds with a key in local.properties).
 * Stands in for the parent-voice clone vendor and the car's guide voice; results are cached by text so a rehearsal
 * run makes the recorded run instant. Never used on a real vehicle build.
 */
class TypecastTts(private val apiKey: String, private val cacheDir: File, private val model: String = "ssfm-v30") {
    val enabled get() = apiKey.isNotBlank()

    /** Returns an mp3 of [text] in [voiceId], or null on any failure (caller falls back). */
    suspend fun toFile(text: String, voiceId: String): File? = withContext(Dispatchers.IO) {
        if (!enabled || voiceId.isBlank()) return@withContext null
        val out = File(cacheDir, "${voiceId}_${(model + text).hashCode().toUInt()}.mp3")
        if (out.exists() && out.length() > 0) return@withContext out
        runCatching {
            val conn = URL("https://api.typecast.ai/v1/text-to-speech").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000; conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("X-API-KEY", apiKey)
            conn.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject()
                .put("voice_id", voiceId)
                .put("text", text)
                .put("model", model)
                .put("language", "kor")
                .put("prompt", JSONObject().put("emotion_preset", "normal").put("emotion_intensity", 1))
                .put("output", JSONObject().put("audio_format", "mp3"))
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) {
                log("typecast", "HTTP ${conn.responseCode}: ${conn.errorStream?.bufferedReader()?.readText()?.take(200)}")
                return@runCatching null
            }
            cacheDir.mkdirs()
            val tmp = File(out.path + ".part")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(out)
            out
        }.getOrElse { log("typecast", "failed: ${it.message}"); null }
    }
}

/** Which Typecast voice speaks for whom (ids come from local.properties via BuildConfig). */
data class DemoVoices(val dad: String, val mom: String, val guide: String) {
    fun forPerson(personId: String) = when (personId) { "dad" -> dad; "mom" -> mom; else -> guide }
}
