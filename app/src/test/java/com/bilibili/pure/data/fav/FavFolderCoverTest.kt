package com.bilibili.pure.data.fav

import com.bilibili.pure.data.model.FavFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavFolderCoverTest {

    @Test
    fun extractsCoverForFolderWithCover() {
        val folders = listOf(
            folder(id = 1, cover = "https://i0.hdslb.com/a.jpg")
        )

        val covers = extractFolderCovers(folders)

        assertEquals(mapOf(1L to "https://i0.hdslb.com/a.jpg"), covers)
    }

    @Test
    fun normalizesHttpToHttps() {
        val folders = listOf(
            folder(id = 1, cover = "http://i1.hdslb.com/a.jpg")
        )

        val covers = extractFolderCovers(folders)

        assertEquals("https://i1.hdslb.com/a.jpg", covers[1L])
    }

    @Test
    fun normalizesProtocolRelativeUrl() {
        val folders = listOf(
            folder(id = 1, cover = "//i0.hdslb.com/a.jpg")
        )

        val covers = extractFolderCovers(folders)

        assertEquals("https://i0.hdslb.com/a.jpg", covers[1L])
    }

    @Test
    fun skipsFolderWithNullCover() {
        val folders = listOf(
            folder(id = 1, cover = null),
            folder(id = 2, cover = "https://i0.hdslb.com/b.jpg")
        )

        val covers = extractFolderCovers(folders)

        assertEquals(mapOf(2L to "https://i0.hdslb.com/b.jpg"), covers)
    }

    @Test
    fun skipsFolderWithBlankCover() {
        val folders = listOf(
            folder(id = 1, cover = "   "),
            folder(id = 2, cover = "")
        )

        val covers = extractFolderCovers(folders)

        assertTrue(covers.isEmpty())
    }

    @Test
    fun keepsAllFoldersWithCover() {
        val folders = listOf(
            folder(id = 1, cover = "https://i0.hdslb.com/a.jpg"),
            folder(id = 2, cover = "https://i0.hdslb.com/b.jpg"),
            folder(id = 3, cover = null)
        )

        val covers = extractFolderCovers(folders)

        assertEquals(2, covers.size)
        assertEquals("https://i0.hdslb.com/b.jpg", covers[2L])
    }

    private fun folder(id: Long, cover: String?) = FavFolder(
        id = id,
        title = "folder-$id",
        mediaCount = 1,
        cover = cover
    )
}
