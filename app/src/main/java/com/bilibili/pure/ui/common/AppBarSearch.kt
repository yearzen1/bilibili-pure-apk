package com.bilibili.pure.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.delay

internal fun normalizeSearchQuery(query: String): String? =
    query.trim().takeIf { it.isNotEmpty() }

class AppBarSearchState {
    var isSearching by mutableStateOf(false)
        internal set
    var query by mutableStateOf("")
        private set
    internal val focusRequester = FocusRequester()

    fun enter() {
        isSearching = true
        query = ""
    }

    fun exit() {
        isSearching = false
        query = ""
    }

    internal fun onQueryChange(newQuery: String) {
        query = newQuery
    }
}

@Composable
fun rememberAppBarSearchState(): AppBarSearchState {
    val state = remember { AppBarSearchState() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.isSearching) {
        if (state.isSearching) {
            delay(200)
            state.focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    return state
}

@Composable
fun AppBarSearchTitle(
    state: AppBarSearchState,
    normalTitle: String,
    placeholder: String,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {}
) {
    if (state.isSearching) {
        val keyboardController = LocalSoftwareKeyboardController.current
        OutlinedTextField(
            value = state.query,
            onValueChange = {
                state.onQueryChange(it)
                onQueryChange(it)
            },
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(state.focusRequester),
            placeholder = { Text(placeholder) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSearch(normalizeSearchQuery(state.query) ?: "")
                keyboardController?.hide()
            }),
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = {
                        state.onQueryChange("")
                        onQueryChange("")
                        onSearch("")
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "清除")
                    }
                }
            }
        )
    } else {
        Text(text = normalTitle, modifier = modifier)
    }
}

@Composable
fun AppBarSearchActions(state: AppBarSearchState) {
    if (!state.isSearching) {
        IconButton(onClick = { state.enter() }) {
            Icon(Icons.Default.Search, contentDescription = "搜索")
        }
    }
}
