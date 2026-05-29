package com.bilibili.pure.ui.favorites

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.data.model.FavFolder
import com.bilibili.pure.data.model.FavResourceItem
import com.bilibili.pure.data.repository.BilibiliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val folders: List<FavFolder> = emptyList(),
    val resources: List<FavResourceItem> = emptyList(),
    val selectedFolderId: Long? = null,
    val selectedFolderTitle: String = "",
    val isLoadingFolders: Boolean = false,
    val isLoadingResources: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val currentPage: Int = 1
)

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BilibiliRepository()
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    private val prefs = application.getSharedPreferences("bili_prefs", 0)

    fun loadFolders() {
        val uid = prefs.getString("dede_userid", null)?.toLongOrNull() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFolders = true, error = null)
            repository.getFavFolders(uid)
                .onSuccess { folders ->
                    _uiState.value = _uiState.value.copy(
                        folders = folders,
                        isLoadingFolders = false
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "loadFolders failed", e)
                    _uiState.value = _uiState.value.copy(
                        isLoadingFolders = false,
                        error = e.message ?: "加载收藏夹失败"
                    )
                }
        }
    }

    fun selectFolder(folder: FavFolder) {
        _uiState.value = _uiState.value.copy(
            selectedFolderId = folder.id,
            selectedFolderTitle = folder.title,
            resources = emptyList(),
            isLoadingResources = true,
            error = null,
            currentPage = 1,
            hasMore = false
        )
        loadResources(folder.id, page = 1)
    }

    fun backToFolders() {
        _uiState.value = _uiState.value.copy(
            selectedFolderId = null,
            selectedFolderTitle = "",
            resources = emptyList()
        )
    }

    fun loadMore() {
        val state = _uiState.value
        val folderId = state.selectedFolderId ?: return
        if (state.isLoadingMore || !state.hasMore) return
        val nextPage = state.currentPage + 1
        loadResources(folderId, page = nextPage)
    }

    private fun loadResources(mediaId: Long, page: Int) {
        viewModelScope.launch {
            if (page == 1) {
                _uiState.value = _uiState.value.copy(isLoadingResources = true)
            } else {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
            }
            repository.getFavResources(mediaId, page = page)
                .onSuccess { (resources, hasMore) ->
                    val current = _uiState.value
                    _uiState.value = current.copy(
                        resources = if (page == 1) resources else current.resources + resources,
                        isLoadingResources = false,
                        isLoadingMore = false,
                        currentPage = page,
                        hasMore = hasMore
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "loadResources failed", e)
                    _uiState.value = _uiState.value.copy(
                        isLoadingResources = false,
                        isLoadingMore = false,
                        error = e.message ?: "加载收藏视频失败"
                    )
                }
        }
    }
}
