package com.hguide.ai

interface LlmProvider {
    /** Returns raw JSON text (the schema is defined by the prompt). */
    suspend fun completeJson(system: String, user: String): String
}

data class Prompt(val system: String, val user: String)

/** Loads `## System` / `## User` markdown prompts and fills `{placeholders}`. */
class PromptLibrary(private val read: (String) -> String) {
    private val cache = mutableMapOf<String, Prompt>()

    fun load(name: String): Prompt = cache.getOrPut(name) {
        val text = read("prompts/$name.md")
        val sysIdx = text.indexOf("## System")
        val userIdx = text.indexOf("## User")
        require(sysIdx >= 0 && userIdx > sysIdx) { "malformed prompt $name" }
        Prompt(
            system = text.substring(sysIdx + "## System".length, userIdx).trim(),
            user = text.substring(userIdx + "## User".length).trim(),
        )
    }

    fun fill(name: String, vars: Map<String, String>): Prompt {
        val p = load(name)
        return Prompt(apply(p.system, vars), apply(p.user, vars))
    }

    private fun apply(t: String, vars: Map<String, String>): String =
        vars.entries.fold(t) { acc, (k, v) -> acc.replace("{$k}", v) }
}

object PromptNames {
    const val HIGHLIGHT = "highlight_extract"
    const val GREETING = "greeting"
    const val COACH = "coach_cue"
    const val PLACE_INTRO = "place_intro"
}
