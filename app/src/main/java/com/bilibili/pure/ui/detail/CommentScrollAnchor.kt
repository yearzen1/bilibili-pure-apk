package com.bilibili.pure.ui.detail

/**
 * 收起回复时的滚动补偿目标 index。
 *
 * LazyList 收起时删除 "r_*"/"collapse_*" item，若 viewport 第一个可见 item 的 key
 * 被删除则按裸 index rebind 导致跳变。返回非 null 表示应先 scrollToItem(目标) 再收起
 * （删除发生在父评论之后，父评论 index 前后不变）。null 表示无需补偿（= 现状行为）。
 */
internal fun collapseScrollTargetIndex(
    rpid: Long,
    isPinned: Boolean,
    threadChildRpids: List<Long>,
    firstVisibleKey: Any?,
    firstVisibleIndex: Int
): Int? {
    val key = firstVisibleKey ?: return null
    val parentKey: Any = if (isPinned) "pinned_$rpid" else rpid
    if (key == parentKey) return null

    val collapseKey = if (isPinned) "pinned_collapse_$rpid" else "collapse_$rpid"
    if (key == collapseKey) {
        if (threadChildRpids.isEmpty()) return null
        return firstVisibleIndex - (threadChildRpids.size + 1)
    }

    val replyPrefix = if (isPinned) "pinned_r_$rpid:" else "r_$rpid:"
    if (key is String && key.startsWith(replyPrefix)) {
        val child = key.substring(replyPrefix.length).toLongOrNull() ?: return null
        val pos = threadChildRpids.indexOf(child)
        if (pos < 0) return null
        return firstVisibleIndex - (pos + 1)
    }

    return null
}

/** 评论头部 item 的 LazyColumn index：前 7 个固定 item（0-6），可选合集占 7。 */
internal fun commentsHeaderIndex(hasUgcSeason: Boolean): Int = if (hasUgcSeason) 8 else 7
