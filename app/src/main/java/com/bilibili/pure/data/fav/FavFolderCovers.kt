package com.bilibili.pure.data.fav

import com.bilibili.pure.data.model.FavFolder
import com.bilibili.pure.util.fixPic

internal fun extractFolderCovers(folders: List<FavFolder>): Map<Long, String> =
    folders.mapNotNull { folder ->
        val cover = folder.cover?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        folder.id to fixPic(cover)
    }.toMap()
