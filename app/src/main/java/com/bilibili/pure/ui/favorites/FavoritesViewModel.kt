package com.bilibili.pure.ui.favorites

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.data.fav.extractFolderCovers
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
    val currentPage: Int = 1,
    val folderCovers: Map<Long, String> = emptyMap(),
    val searchKeyword: String? = null,
    val showCreateFolder: Boolean = false,
    val creatingFolder: Boolean = false,
    val showEditFolder: Boolean = false,
    val editingFolder: Boolean = false,
    val editDetailLoading: Boolean = false,
    val editTargetId: Long? = null,
    val editTitle: String = "",
    val editIntro: String = "",
    val editPrivacy: Int = 0,
    val showDeleteDialog: Boolean = false,
    val deletingFolders: Boolean = false,
    val deleteSelectedIds: Set<Long> = emptySet(),
    val opError: String? = null
)

internal fun shouldLoadFolders(currentFolders: List<FavFolder>): Boolean =
    currentFolders.isEmpty()

internal fun filterFolders(folders: List<FavFolder>, query: String): List<FavFolder> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return folders
    return folders.filter { it.title.lowercase().contains(q) }
}

internal fun validateFolderTitle(title: String): String? =
    if (title.isBlank()) "请输入收藏夹名称" else null

internal fun privacyFromAttr(attr: Int): Int = attr and 1

internal fun privacyLabel(attr: Int): String =
    if (privacyFromAttr(attr) == 1) "私密" else "公开"

internal fun deleteMediaIds(ids: List<Long>): String = ids.joinToString(",")

internal fun applyFolderEdit(
    folders: List<FavFolder>,
    id: Long,
    title: String,
    privacy: Int
): List<FavFolder> = folders.map {
    if (it.id == id) {
        it.copy(title = title, attr = (it.attr and 1.inv()) or (privacy and 1))
    } else {
        it
    }
}

internal fun applyFolderDelete(folders: List<FavFolder>, selectedIds: Set<Long>): List<FavFolder> =
    folders.filterNot { it.id in selectedIds }

internal fun toggleSelected(selected: Set<Long>, id: Long): Set<Long> =
    if (id in selected) selected - id else selected + id

internal fun nextSelectAll(selected: Set<Long>, allIds: List<Long>): Set<Long> =
    if (selected.containsAll(allIds) && allIds.isNotEmpty()) emptySet() else allIds.toSet()

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BilibiliRepository()
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    private val prefs = application.getSharedPreferences("bili_prefs", 0)

    fun loadFolders() {
        val uid = prefs.getString("dede_userid", null)?.toLongOrNull() ?: return
        if (!shouldLoadFolders(_uiState.value.folders)) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFolders = true, error = null)
            repository.getFavFolders(uid)
                .onSuccess { folders ->
                    _uiState.value = _uiState.value.copy(
                        folders = folders,
                        folderCovers = extractFolderCovers(folders),
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
            hasMore = false,
            searchKeyword = null
        )
        loadResources(folder.id, page = 1)
    }

    fun searchFav(keyword: String) {
        val folderId = _uiState.value.selectedFolderId ?: return
        _uiState.value = _uiState.value.copy(
            resources = emptyList(),
            isLoadingResources = true,
            error = null,
            currentPage = 1,
            hasMore = false,
            searchKeyword = keyword
        )
        loadResources(folderId, page = 1)
    }

    fun clearFavSearch() {
        val folderId = _uiState.value.selectedFolderId ?: return
        if (_uiState.value.searchKeyword == null) return
        _uiState.value = _uiState.value.copy(
            resources = emptyList(),
            isLoadingResources = true,
            error = null,
            currentPage = 1,
            hasMore = false,
            searchKeyword = null
        )
        loadResources(folderId, page = 1)
    }

    fun backToFolders() {
        _uiState.value = _uiState.value.copy(
            selectedFolderId = null,
            selectedFolderTitle = "",
            resources = emptyList(),
            searchKeyword = null
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
        val keyword = _uiState.value.searchKeyword
        viewModelScope.launch {
            if (page == 1) {
                _uiState.value = _uiState.value.copy(isLoadingResources = true)
            } else {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
            }
            repository.getFavResources(mediaId, page = page, keyword = keyword)
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

    fun openCreateFolder() {
        _uiState.value = _uiState.value.copy(showCreateFolder = true, opError = null)
    }

    fun dismissCreateFolder() {
        _uiState.value = _uiState.value.copy(showCreateFolder = false, creatingFolder = false)
    }

    fun createFavFolder(title: String, intro: String, privacy: Int) {
        val state = _uiState.value
        if (state.creatingFolder) return
        validateFolderTitle(title)?.let { _uiState.value = state.copy(opError = it); return }
        _uiState.value = state.copy(creatingFolder = true, opError = null)
        viewModelScope.launch {
            repository.addFavFolder(title = title.trim(), intro = intro, privacy = privacy)
                .onSuccess { newId ->
                    val s = _uiState.value
                    val folders = s.folders + FavFolder(
                        id = newId,
                        title = title.trim(),
                        mediaCount = 0,
                        attr = privacy
                    )
                    _uiState.value = s.copy(
                        creatingFolder = false,
                        showCreateFolder = false,
                        folders = folders,
                        folderCovers = extractFolderCovers(folders)
                    )
                    Log.d(BilibiliApp.TAG, "favFolder created: id=$newId title=${title.trim()}")
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "createFavFolder failed", e)
                    _uiState.value = _uiState.value.copy(
                        creatingFolder = false,
                        opError = e.message ?: "创建收藏夹失败"
                    )
                }
        }
    }

    fun startEditFolder(folder: FavFolder) {
        val targetId = folder.id
        _uiState.value = _uiState.value.copy(
            showEditFolder = true,
            editDetailLoading = true,
            editingFolder = false,
            editTargetId = targetId,
            editTitle = folder.title,
            editIntro = "",
            editPrivacy = privacyFromAttr(folder.attr),
            opError = null
        )
        viewModelScope.launch {
            repository.getFavFolderInfo(targetId)
                .onSuccess { info ->
                    val s = _uiState.value
                    if (s.editTargetId == targetId && s.showEditFolder) {
                        _uiState.value = s.copy(
                            editDetailLoading = false,
                            editIntro = info.intro ?: ""
                        )
                    }
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "startEditFolder load info failed", e)
                    val s = _uiState.value
                    if (s.editTargetId == targetId && s.showEditFolder) {
                        _uiState.value = s.copy(
                            showEditFolder = false,
                            editDetailLoading = false,
                            editTargetId = null,
                            opError = e.message ?: "获取收藏夹信息失败"
                        )
                    }
                }
        }
    }

    fun dismissEditFolder() {
        _uiState.value = _uiState.value.copy(
            showEditFolder = false,
            editingFolder = false,
            editDetailLoading = false,
            editTargetId = null
        )
    }

    fun updateEditTitle(title: String) {
        _uiState.value = _uiState.value.copy(editTitle = title)
    }

    fun updateEditIntro(intro: String) {
        _uiState.value = _uiState.value.copy(editIntro = intro)
    }

    fun updateEditPrivacy(privacy: Int) {
        _uiState.value = _uiState.value.copy(editPrivacy = privacy)
    }

    fun confirmEditFolder() {
        val s = _uiState.value
        if (s.editingFolder || s.editDetailLoading) return
        val target = s.editTargetId ?: return
        validateFolderTitle(s.editTitle)?.let { _uiState.value = s.copy(opError = it); return }
        val newTitle = s.editTitle.trim()
        _uiState.value = s.copy(editingFolder = true, opError = null)
        viewModelScope.launch {
            repository.editFavFolder(
                mediaId = target,
                title = newTitle,
                intro = s.editIntro,
                privacy = s.editPrivacy
            )
                .onSuccess {
                    val cur = _uiState.value
                    val folders = applyFolderEdit(cur.folders, target, newTitle, s.editPrivacy)
                    _uiState.value = cur.copy(
                        editingFolder = false,
                        showEditFolder = false,
                        editTargetId = null,
                        folders = folders,
                        folderCovers = extractFolderCovers(folders)
                    )
                    Log.d(BilibiliApp.TAG, "favFolder edited: id=$target title=$newTitle")
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "confirmEditFolder failed", e)
                    _uiState.value = _uiState.value.copy(
                        editingFolder = false,
                        opError = e.message ?: "修改收藏夹失败"
                    )
                }
        }
    }

    fun openDeleteDialog(folderId: Long? = null) {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = true,
            deletingFolders = false,
            deleteSelectedIds = folderId?.let { setOf(it) } ?: emptySet(),
            opError = null
        )
    }

    fun dismissDeleteDialog() {
        if (_uiState.value.deletingFolders) return
        _uiState.value = _uiState.value.copy(showDeleteDialog = false, deleteSelectedIds = emptySet())
    }

    fun toggleDeleteSelection(id: Long) {
        val s = _uiState.value
        _uiState.value = s.copy(deleteSelectedIds = toggleSelected(s.deleteSelectedIds, id))
    }

    fun toggleSelectAllFolders() {
        val s = _uiState.value
        _uiState.value = s.copy(
            deleteSelectedIds = nextSelectAll(s.deleteSelectedIds, s.folders.map { it.id })
        )
    }

    fun confirmDeleteFolders() {
        val s = _uiState.value
        if (s.deletingFolders || s.deleteSelectedIds.isEmpty()) return
        val ids = s.deleteSelectedIds
        _uiState.value = s.copy(deletingFolders = true, opError = null)
        viewModelScope.launch {
            repository.deleteFavFolders(ids.toList())
                .onSuccess {
                    val cur = _uiState.value
                    val folders = applyFolderDelete(cur.folders, ids)
                    _uiState.value = cur.copy(
                        deletingFolders = false,
                        showDeleteDialog = false,
                        deleteSelectedIds = emptySet(),
                        folders = folders,
                        folderCovers = extractFolderCovers(folders)
                    )
                    Log.d(BilibiliApp.TAG, "favFolders deleted: ids=$ids")
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "confirmDeleteFolders failed", e)
                    _uiState.value = _uiState.value.copy(
                        deletingFolders = false,
                        opError = e.message ?: "删除收藏夹失败"
                    )
                }
        }
    }

    fun clearOpError() {
        _uiState.value = _uiState.value.copy(opError = null)
    }
}
