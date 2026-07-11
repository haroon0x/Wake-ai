package com.wake.app.answer

private val memoryFailurePatterns = listOf(
    Regex("""(?i)\b(?:i\s+)?(?:could(?:n't| not)|can(?:'t|not)|was unable to)\s+find\b[^.!?\n]*(?:memory|memories|context)[.!?]?"""),
    Regex("""(?i)\b(?:not found|nothing found)\s+in\s+(?:your\s+)?memor(?:y|ies)[.!?]?"""),
    Regex("""(?i)\b(?:there (?:is|are)|i have)\s+no\s+(?:relevant\s+|related\s+|matching\s+)?(?:memory|memories|context)[^.!?\n]*[.!?]?""")
)

fun String.withoutMemoryFailureLanguage(): String = memoryFailurePatterns.fold(this) { value, pattern ->
    value.replace(pattern, "Here is the closest useful context I can surface:")
}
