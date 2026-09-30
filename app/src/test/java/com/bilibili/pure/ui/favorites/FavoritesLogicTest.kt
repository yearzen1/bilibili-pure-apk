package com.bilibili.pure.ui.favorites

import com.bilibili.pure.data.model.FavFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesLogicTest {

    @Test
    fun loadsWhenFoldersEmpty() {
        assertTrue(shouldLoadFolders(emptyList()))
    }

    @Test
    fun skipsWhenFoldersAlreadyLoaded() {
        val folders = listOf(
            FavFolder(id = 1, title = "default", mediaCount = 3)
        )

        assertFalse(shouldLoadFolders(folders))
    }

    @Test
    fun filterFoldersBlankReturnsAll() {
        val folders = listOf(
            FavFolder(id = 1, title = "稍后看", mediaCount = 1),
            FavFolder(id = 2, title = "默认收藏夹", mediaCount = 1)
        )

        assertEquals(2, filterFolders(folders, "  ").size)
    }

    @Test
    fun filterFoldersMatchesTitleCaseInsensitive() {
        val folders = listOf(
            FavFolder(id = 1, title = "My Anime List", mediaCount = 1),
            FavFolder(id = 2, title = "默认收藏夹", mediaCount = 1)
        )

        val filtered = filterFolders(folders, "anime")

        assertEquals(1, filtered.size)
        assertEquals(1L, filtered[0].id)
    }

    @Test
    fun filterFoldersNoMatchReturnsEmpty() {
        val folders = listOf(FavFolder(id = 1, title = "默认收藏夹", mediaCount = 1))

        assertTrue(filterFolders(folders, "不存在").isEmpty())
    }
}
