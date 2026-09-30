package com.bilibili.pure.ui.favorites

import com.bilibili.pure.data.model.FavFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun validateFolderTitleBlankReturnsError() {
        assertEquals("请输入收藏夹名称", validateFolderTitle(""))
        assertEquals("请输入收藏夹名称", validateFolderTitle("   "))
    }

    @Test
    fun validateFolderTitleValidReturnsNull() {
        assertNull(validateFolderTitle("我的收藏"))
        assertNull(validateFolderTitle(" 稍后看 "))
    }

    @Test
    fun privacyFromAttrMasksPrivateBit() {
        assertEquals(0, privacyFromAttr(0))
        assertEquals(1, privacyFromAttr(1))
        assertEquals(1, privacyFromAttr(23))
        assertEquals(0, privacyFromAttr(22))
        assertEquals(1, privacyFromAttr(131))
    }

    @Test
    fun deleteMediaIdsJoinsWithComma() {
        assertEquals("1,2,3", deleteMediaIds(listOf(1L, 2L, 3L)))
        assertEquals("42", deleteMediaIds(listOf(42L)))
    }

    @Test
    fun deleteMediaIdsEmptyReturnsBlank() {
        assertEquals("", deleteMediaIds(emptyList()))
    }

    @Test
    fun applyFolderEditUpdatesTitleAndPrivacyBitOnly() {
        val folders = listOf(
            FavFolder(id = 1, title = "a", mediaCount = 0, attr = 23),
            FavFolder(id = 2, title = "b", mediaCount = 0, attr = 22)
        )

        val out = applyFolderEdit(folders, id = 1, title = "new", privacy = 0)

        assertEquals("new", out[0].title)
        assertEquals(22, out[0].attr)
        assertEquals(22, out[1].attr)
        assertEquals("b", out[1].title)
    }

    @Test
    fun applyFolderEditPrivacySetPreservesOtherAttrBits() {
        val folders = listOf(FavFolder(id = 1, title = "a", mediaCount = 0, attr = 22))

        val out = applyFolderEdit(folders, id = 1, title = "a", privacy = 1)

        assertEquals(23, out[0].attr)
    }

    @Test
    fun applyFolderDeleteRemovesOnlySelected() {
        val folders = listOf(
            FavFolder(id = 1, title = "a", mediaCount = 0),
            FavFolder(id = 2, title = "b", mediaCount = 0),
            FavFolder(id = 3, title = "c", mediaCount = 0)
        )

        val out = applyFolderDelete(folders, setOf(1L, 3L))

        assertEquals(listOf(2L), out.map { it.id })
    }

    @Test
    fun toggleSelectedAddsAndRemoves() {
        assertEquals(setOf(1L), toggleSelected(emptySet(), 1L))
        assertEquals(emptySet<Long>(), toggleSelected(setOf(1L), 1L))
    }

    @Test
    fun nextSelectAllTogglesBetweenAllAndEmpty() {
        val ids = listOf(1L, 2L, 3L)

        assertEquals(setOf(1L, 2L, 3L), nextSelectAll(emptySet(), ids))
        assertEquals(emptySet<Long>(), nextSelectAll(setOf(1L, 2L, 3L), ids))
        assertEquals(setOf(1L, 2L, 3L), nextSelectAll(setOf(2L), ids))
    }
}
