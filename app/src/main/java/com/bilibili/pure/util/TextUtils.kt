package com.bilibili.pure.util

private val HTML_TAG_REGEX = Regex("<[^>]*>")
private val NUMERIC_ENTITY_REGEX = Regex("&#(\\d+);")
private val HEX_ENTITY_REGEX = Regex("&#[xX]([0-9a-fA-F]+);")

private val NAMED_ENTITIES = mapOf(
    "&quot;" to "\"",
    "&#34;" to "\"",
    "&#x22;" to "\"",
    "&apos;" to "'",
    "&#39;" to "'",
    "&#x27;" to "'",
    "&amp;" to "&",
    "&lt;" to "<",
    "&gt;" to ">",
    "&nbsp;" to " "
)

private fun decodeEntities(text: String): String {
    var result = text
    NAMED_ENTITIES.forEach { (entity, replacement) ->
        result = result.replace(entity, replacement)
    }
    result = HEX_ENTITY_REGEX.replace(result) { match ->
        match.groupValues[1].toIntOrNull(16)?.let { code ->
            code.toChar().toString()
        } ?: match.value
    }
    result = NUMERIC_ENTITY_REGEX.replace(result) { match ->
        match.groupValues[1].toIntOrNull()?.let { code ->
            code.toChar().toString()
        } ?: match.value
    }
    return result
}

fun cleanHtmlText(text: String): String = decodeEntities(HTML_TAG_REGEX.replace(text, ""))

fun decodeHtmlEntities(text: String): String = decodeEntities(text)