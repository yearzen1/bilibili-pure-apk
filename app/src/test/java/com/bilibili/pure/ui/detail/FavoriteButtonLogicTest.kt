package com.bilibili.pure.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteButtonLogicTest {

    // --- favoriteActionForClick ---

    @Test
    fun clickWhenFavoritedTriggersCancelFavorite() {
        assertEquals(FavAction.CancelFavorite, favoriteActionForClick(isFavorited = true))
    }

    @Test
    fun clickWhenNotFavoritedOpensFolderPicker() {
        assertEquals(FavAction.OpenPicker, favoriteActionForClick(isFavorited = false))
    }

    // --- favoriteButtonEnabled ---

    @Test
    fun enabledWhenIdleAndNotLoading() {
        assertTrue(favoriteButtonEnabled(isToggling = false, loading = false))
    }

    @Test
    fun disabledWhileToggling() {
        assertFalse(favoriteButtonEnabled(isToggling = true, loading = false))
    }

    @Test
    fun disabledWhileStatusLoading() {
        assertFalse(favoriteButtonEnabled(isToggling = false, loading = true))
    }
}
