package com.bilibili.pure.ui.detail

import com.bilibili.pure.data.model.FavFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavFolderLogicTest {

    // --- parseDedeUserId ---

    @Test
    fun parsesDedeUserIdFromCookieString() {
        val cookies = "SESSDATA=abc; bili_jct=def; DedeUserID=1234567890"

        assertEquals(1234567890L, parseDedeUserId(cookies))
    }

    @Test
    fun parsesDedeUserIdWithoutSpaces() {
        val cookies = "DedeUserID=987654321;SESSDATA=abc;bili_jct=def"

        assertEquals(987654321L, parseDedeUserId(cookies))
    }

    @Test
    fun returnsNullWhenDedeUserIdMissing() {
        assertNull(parseDedeUserId("SESSDATA=abc; bili_jct=def"))
    }

    @Test
    fun returnsNullWhenDedeUserIdIsNotNumeric() {
        assertNull(parseDedeUserId("DedeUserID=abc; SESSDATA=x"))
    }

    @Test
    fun returnsNullForEmptyCookies() {
        assertNull(parseDedeUserId(""))
    }

    // --- pickDefaultFolderId ---

    @Test
    fun picksFolderWithDefaultBitEvenWhenNotFirst() {
        val folders = listOf(
            folder(id = 1, attr = 2),
            folder(id = 2, attr = 0)
        )

        assertEquals(2L, pickDefaultFolderId(folders))
    }

    @Test
    fun defaultBitZeroOnFirstFolderWins() {
        val folders = listOf(
            folder(id = 10, attr = 1),
            folder(id = 20, attr = 2)
        )

        assertEquals(10L, pickDefaultFolderId(folders))
    }

    @Test
    fun fallsBackToFirstFolderWhenNoDefaultMarked() {
        val folders = listOf(
            folder(id = 5, attr = 2),
            folder(id = 6, attr = 3)
        )

        assertEquals(5L, pickDefaultFolderId(folders))
    }

    @Test
    fun returnsNullForEmptyFolderList() {
        assertNull(pickDefaultFolderId(emptyList()))
    }

    // --- favDealParams (favor) ---

    @Test
    fun favorSingleFolderBuildsAddParams() {
        val params = favDealParams(favor = true, folderIds = listOf(101L))

        assertEquals(FavDealParams(addMediaIds = "101", delMediaIds = ""), params)
    }

    @Test
    fun favorMultipleFoldersJoinsWithComma() {
        val params = favDealParams(favor = true, folderIds = listOf(101L, 202L))

        assertEquals(FavDealParams(addMediaIds = "101,202", delMediaIds = ""), params)
    }

    @Test
    fun favorWithNoFoldersReturnsNull() {
        assertNull(favDealParams(favor = true, folderIds = emptyList()))
    }

    // --- favDealParams (cancel) ---

    @Test
    fun cancelBuildsDelParams() {
        val params = favDealParams(favor = false, folderIds = listOf(101L, 202L))

        assertEquals(FavDealParams(addMediaIds = "", delMediaIds = "101,202"), params)
    }

    @Test
    fun cancelWithNoFoldersReturnsNull() {
        assertNull(favDealParams(favor = false, folderIds = emptyList()))
    }

    // --- favouredFolderIds ---

    @Test
    fun extractsOnlyFavouredFolderIds() {
        val folders = listOf(
            folder(id = 1, favState = 1),
            folder(id = 2, favState = 0),
            folder(id = 3, favState = 2)
        )

        assertEquals(listOf(1L, 3L), favouredFolderIds(folders))
    }

    private fun folder(id: Long, attr: Int = 0, favState: Int = 0) = FavFolder(
        id = id,
        title = "folder-$id",
        mediaCount = 0,
        attr = attr,
        favState = favState
    )
}
