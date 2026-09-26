package com.github.uiopak.lstcrc.plugin.utils

/** [value] as a double-quoted JavaScript string literal, for the scripts sent to Remote Robot. */
fun toJsStringLiteral(value: String): String = buildString(value.length + 2) {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}
