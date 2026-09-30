package com.bilibili.pure.ui.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal fun shouldShowScrollToTop(firstVisibleIndex: Int, threshold: Int = 2): Boolean =
    firstVisibleIndex > threshold

@Composable
fun BoxScope.ScrollToTopFab(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    threshold: Int = 2
) {
    val visible by remember {
        derivedStateOf { shouldShowScrollToTop(listState.firstVisibleItemIndex, threshold) }
    }
    if (visible) {
        val scope = rememberCoroutineScope()
        FloatingActionButton(
            onClick = { scope.launch { listState.scrollToItem(0) } },
            modifier = modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "回到顶部")
        }
    }
}
