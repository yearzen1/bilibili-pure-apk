package com.bilibili.pure.ui.favorites

import com.bilibili.pure.data.model.FavFolder
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
}
