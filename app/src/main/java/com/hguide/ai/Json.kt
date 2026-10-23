package com.hguide.ai

import org.json.JSONObject

/** LLMs sometimes wrap JSON in prose or code fences; take the outermost object. */
fun extractJsonObject(raw: String): JSONObject? {
    val s = raw.indexOf('{')
    val e = raw.lastIndexOf('}')
    if (s < 0 || e <= s) return null
    return runCatching { JSONObject(raw.substring(s, e + 1)) }.getOrNull()
}
