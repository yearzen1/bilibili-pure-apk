package com.bilibili.pure.util

private val TAG_REGEX = Regex("<[^>]*>")
private val BR_REGEX = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val HEX_ENTITY_REGEX = Regex("&#[xX]([0-9a-fA-F]+);?")
private val NUMERIC_ENTITY_REGEX = Regex("&#(\\d+);?")

private val NAMED_ENTITIES = mapOf(
    "&quot;" to "\"",
    "&apos;" to "'",
    "&amp;" to "&",
    "&lt;" to "<",
    "&gt;" to ">",
    "&nbsp;" to " "
)

private fun decodeEntities(text: String): String {
    var result = text
    NAMED_ENTITIES.forEach { (entity, replacement) ->
        result = result.replace(entity, replacement, ignoreCase = true)
    }
    result = HEX_ENTITY_REGEX.replace(result) { match ->
        match.groupValues[1].toIntOrNull(16)?.takeIf { it in 0..0x10FFFF }
            ?.let { String(Character.toChars(it)) } ?: match.value
    }
    result = NUMERIC_ENTITY_REGEX.replace(result) { match ->
        match.groupValues[1].toIntOrNull()?.takeIf { it in 0..0x10FFFF }
            ?.let { String(Character.toChars(it)) } ?: match.value
    }
    return result
}

fun cleanHtmlText(text: String): String =
    decodeEntities(TAG_REGEX.replace(BR_REGEX.replace(text, "\n"), ""))

fun decodeHtmlEntities(text: String): String =
    decodeEntities(TAG_REGEX.replace(BR_REGEX.replace(text, "\n"), ""))

fun formatDuration(raw: String): String {
    val parts = raw.split(":")
    if (parts.size == 2) {
        val minutes = parts[0].toIntOrNull() ?: return raw
        val seconds = parts[1].toIntOrNull() ?: return raw
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }
    return raw
}