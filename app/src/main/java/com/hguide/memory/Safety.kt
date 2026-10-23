package com.hguide.memory

/** Second line of defence behind the prompt's "don't pick" list (money, health, conflict, gossip). */
object Sensitivity {
    private val words = listOf("소리 지", "소리쳤", "왜 그랬", "짜증", "싸우", "다퉜", "화가 나", "위험", "죽을", "병원", "아프", "돈", "월급", "대출", "빚", "욕", "험담", "무서")
    fun isSensitive(text: String) = words.any { it in text }
}
