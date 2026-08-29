package com.bilibili.pure.ui.detail

import com.bilibili.pure.data.model.CommentItem

private val NOTE_URL_PATTERN = Regex("note-app|cvid|/read/cv")

fun CommentItem.isNoteComment(): Boolean {
    if (noteCvid != 0L) return true
    val rc = replyControl
    if (rc != null && rc.isNote == true && rc.isNoteV2 == true) return true
    content.jumpUrl?.forEach { (key, j) ->
        if (key.startsWith("http") &&
            (key.contains("note-app") || key.contains("cvid") || key.contains("/read/cv"))
        ) return true
        val u = (j.pcUrl + " " + j.appSchema).lowercase()
        if (NOTE_URL_PATTERN.containsMatchIn(u)) return true
    }
    return false
}

fun CommentItem.getNoteCvid(): String? {
    if (noteCvid != 0L) return noteCvid.toString()
    if (noteCvidStr.isNotBlank() && noteCvidStr != "0") return noteCvidStr
    content.jumpUrl?.forEach { (key, _) ->
        if (key.contains("cvid=")) {
            val idx = key.indexOf("cvid=")
            val v = key.substring(idx + 5).substringBefore("&")
            if (v.isNotBlank()) return v
        }
    }
    return null
}

fun CommentItem.getNotePreview(): String {
    val noteLinkIdx = content.message.indexOf("https://www.bilibili.com/h5/note-app")
    val text = if (noteLinkIdx >= 0) content.message.substring(0, noteLinkIdx) else content.message
    return text.trim()
}
