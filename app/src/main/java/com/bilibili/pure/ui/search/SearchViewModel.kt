package com.bilibili.pure.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.data.model.SearchVideoItem
import com.bilibili.pure.data.repository.BilibiliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchSortOption(
    val value: String,
    val label: String
)

object SearchSort {
    val options = listOf(
        SearchSortOption("", "综合"),
        SearchSortOption("click", "最多播放"),
        SearchSortOption("pubdate", "最新发布"),
        SearchSortOption("dm", "最多弹幕"),
        SearchSortOption("stow", "最多收藏")
    )
}

data class SearchUiState(
    val query: String = "",
    val results: List<SearchVideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val sortBy: SearchSortOption = SearchSort.options[0]
)

class SearchViewModel(
    private val repository: BilibiliRepository = BilibiliRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun setSortBy(sort: SearchSortOption) {
        if (sort.value == _uiState.value.sortBy.value) return
        _uiState.value = _uiState.value.copy(sortBy = sort)
        search()
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return

        val order = _uiState.value.sortBy.value
        Log.d(BilibiliApp.TAG, "search: query=$query sort=$order")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, results = emptyList(), currentPage = 1)
            repository.search(query, page = 1, order = order)
                .onSuccess { (results, totalPages) ->
                    Log.d(BilibiliApp.TAG, "search success: ${results.size} results, pages=$totalPages")
                    _uiState.value = _uiState.value.copy(
                        results = results.distinctBy { it.bvid },
                        isLoading = false,
                        currentPage = 1,
                        hasMore = totalPages > 1
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "search error: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "未知错误"
                    )
                }
        }
    }

    fun nextSort() {
        val current = _uiState.value.sortBy
        val index = SearchSort.options.indexOf(current)
        if (index < SearchSort.options.lastIndex) {
            setSortBy(SearchSort.options[index + 1])
        }
    }

    fun prevSort() {
        val current = _uiState.value.sortBy
        val index = SearchSort.options.indexOf(current)
        if (index > 0) {
            setSortBy(SearchSort.options[index - 1])
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return

        val nextPage = state.currentPage + 1
        Log.d(BilibiliApp.TAG, "loadMore: page=$nextPage query=${state.query}")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            repository.search(state.query, page = nextPage, order = state.sortBy.value)
                .onSuccess { (results, totalPages) ->
                    val existingBvids = _uiState.value.results.map { it.bvid }.toSet()
                    val deduped = results.filter { it.bvid !in existingBvids }
                    _uiState.value = _uiState.value.copy(
                        results = _uiState.value.results + deduped,
                        isLoadingMore = false,
                        currentPage = nextPage,
                        hasMore = nextPage < totalPages
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "loadMore error: ${e.message}")
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
        }
    }
}
