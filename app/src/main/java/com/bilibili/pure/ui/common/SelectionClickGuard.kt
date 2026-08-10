package com.bilibili.pure.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * Card that is fully clickable (whole card navigates) while its SelectionContainer children
 * keep working: after a long-press selection, the next tap dismisses the selection instead of
 * navigating. Selection is cleared by bumping an integer key that recreates the content so the
 * SelectionContainer's internal selection state is discarded (no public clear() API pre-1.12).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DismissSelectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    content: @Composable () -> Unit,
) {
    var suppressNext by remember { mutableStateOf(false) }
    var generation by remember { mutableStateOf(0) }
    val clickable = if (onClick != null) {
        modifier.combinedClickable(
            onClick = {
                if (suppressNext) {
                    suppressNext = false
                    generation++
                } else {
                    onClick()
                }
            },
            onLongClick = { suppressNext = true }
        )
    } else {
        modifier
    }
    Card(
        modifier = clickable,
        shape = shape,
        colors = colors,
        elevation = elevation
    ) {
        key(generation) {
            content()
        }
    }
}

/** Same as [DismissSelectionCard] but for a plain clickable container (Row/Box), not a Card. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DismissSelectionClickable(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var suppressNext by remember { mutableStateOf(false) }
    var generation by remember { mutableStateOf(0) }
    val clickable = if (onClick != null) {
        modifier.combinedClickable(
            onClick = {
                if (suppressNext) {
                    suppressNext = false
                    generation++
                } else {
                    onClick()
                }
            },
            onLongClick = { suppressNext = true }
        )
    } else {
        modifier
    }
    Box(
        modifier = clickable
    ) {
        key(generation) {
            content()
        }
    }
}