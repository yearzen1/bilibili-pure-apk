package com.bilibili.pure.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec)
    else "%d:%02d".format(m, sec)
}

fun formatCount(count: Long): String = when {
    count >= 100_000_000 -> "${count / 100_000_000}亿"
    count >= 10_000 -> "${count / 10_000}万"
    else -> count.toString()
}

fun fixPic(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.startsWith("http://") -> "https:${url.removePrefix("http:")}"
    else -> url
}

fun formatPubdate(seconds: Long): String {
    if (seconds <= 0) return ""
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        .format(Date(seconds * 1000))
}

fun formatPubdateCompact(
    seconds: Long,
    nowSeconds: Long = System.currentTimeMillis() / 1000
): String {
    if (seconds <= 0) return ""
    val diff = nowSeconds - seconds
    return when {
        diff < 0 -> formatPubdateShortYear(seconds, nowSeconds)
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60}分钟前"
        diff < 86400 -> "${diff / 3600}小时前"
        diff < 604800 -> "${diff / 86400}天前"
        else -> formatPubdateShortYear(seconds, nowSeconds)
    }
}

private fun formatPubdateShortYear(seconds: Long, nowSeconds: Long): String {
    val pattern = if (sameYear(seconds, nowSeconds)) "MM-dd" else "yyyy-MM-dd"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(seconds * 1000))
}

private fun sameYear(seconds: Long, nowSeconds: Long): Boolean {
    val date = Calendar.getInstance().apply { timeInMillis = seconds * 1000 }
    val now = Calendar.getInstance().apply { timeInMillis = nowSeconds * 1000 }
    return date.get(Calendar.YEAR) == now.get(Calendar.YEAR)
}