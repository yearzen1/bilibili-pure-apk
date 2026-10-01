package com.bilibili.pure.ui.settings

enum class FeedbackType(val label: String) {
    BUG("Bug反馈"),
    SUGGESTION("功能建议")
}

object FeedbackEmail {

    const val RECIPIENT = "1017714519@qq.com"

    fun isValid(content: String): Boolean = content.isNotBlank()

    fun subject(type: FeedbackType, version: String): String =
        "[${type.label}] bilibili-pure v$version"

    fun body(
        content: String,
        contact: String,
        version: String,
        androidVersion: String,
        deviceModel: String
    ): String = buildString {
        appendLine("反馈内容：")
        appendLine(content.trim())
        appendLine()
        if (contact.isNotBlank()) {
            appendLine("联系方式：${contact.trim()}")
            appendLine()
        }
        appendLine("——")
        appendLine("App 版本：v$version")
        appendLine("Android 版本：$androidVersion")
        append("设备型号：$deviceModel")
    }

    fun mailtoUri(): String = "mailto:$RECIPIENT"
}
