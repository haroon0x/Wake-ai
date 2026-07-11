package com.wake.app.agent

object CommitmentDetector {

    private val patterns = listOf(
        Regex("""\b\d{1,2}(:\d{2})?\s?(am|pm)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(tomorrow|tonight|this (evening|afternoon|weekend))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(on|by|before|until|next)\s+(mon|tues|wednes|thurs|fri|satur|sun)day\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(deadline|due (on|by|date)|don't forget|dont forget|remember to|reminder)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(meet(ing)?|call|appointment|interview|class|exam|flight|train|bus|movie|dinner|lunch)\s+(is\s+)?(at|on|tomorrow|tonight)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(let's|lets|see you|pick (me|you) up|join)\b.{0,30}\b(at|by)\s+\d""", RegexOption.IGNORE_CASE)
    )

    fun matches(text: String): Boolean {
        if (text.length < 8) return false
        return patterns.any { it.containsMatchIn(text) }
    }
}
