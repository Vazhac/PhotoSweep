package com.photosweep.app

import android.app.Activity
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
class PhotoSweepViewModel(private val repository: PhotoSweepRepository) : ViewModel() {

    private val persistedState = repository.loadPersistedSessionState()
    private val _uiState = MutableStateFlow(
        PhotoSweepUiState(
            loading = true,
            activeFilter = persistedState.activeFilter,
            currentIndex = persistedState.currentIndex,
            protectedPhotoIds = persistedState.protectedPhotoIds,
            sessionStarted = persistedState.sessionStarted,
            screen = persistedState.screen,
            smartScanProcessed = persistedState.smartScanProcessed,
            smartScanTotal = persistedState.smartScanTotal,
            smartScanSummary = persistedState.smartScanSummary,
            smartScanSignature = persistedState.smartScanSignature,
            deletedCount = persistedState.deletedCount,
            isPremium = Premium.isPremium(),
        )
    )
    val uiState: StateFlow<PhotoSweepUiState> = _uiState.asStateFlow()

    private val _pendingDeleteRequest = MutableStateFlow<android.app.PendingIntent?>(null)
    val pendingDeleteRequest: StateFlow<android.app.PendingIntent?> = _pendingDeleteRequest.asStateFlow()

    private val swipeHistory = ArrayDeque<SwipeRecord>()
    private var pendingDeletionBatch: List<PhotoItem> = emptyList()

    private fun commitState(state: PhotoSweepUiState) {
        val syncedState = state.copy(isPremium = Premium.isPremium())
        _uiState.value = syncedState
        repository.persistSessionState(syncedState)
    }

    fun loadPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            val previousState = _uiState.value
            _uiState.value = previousState.copy(loading = true)
            val photos = repository.loadPhotos(includeVideos = Premium.isPremium())
            val markedIds = if (previousState.markedForDeletion.isNotEmpty() || previousState.allPhotos.isNotEmpty()) {
                previousState.markedForDeletion.mapTo(mutableSetOf()) { it.id }
            } else {
                repository.loadPersistedSessionState().markedForDeletionIds
            }
            val currentSmartScanSignature = smartScanSignature(photos, previousState.protectedPhotoIds)
            val canReuseSmartScan = previousState.smartScanSignature == currentSmartScanSignature
            val reusedProcessed = if (canReuseSmartScan) {
                previousState.smartScanProcessed.takeIf { it > 0 } ?: photos.size
            } else {
                0
            }
            val refreshedState = PhotoSweepUiState(
                loading = false,
                allPhotos = photos,
                activeFilter = previousState.activeFilter,
                currentIndex = previousState.currentIndex,
                markedForDeletion = markedIds.mapNotNull { markedId ->
                    photos.find { it.id == markedId }
                },
                protectedPhotoIds = previousState.protectedPhotoIds.filterTo(mutableSetOf()) { protectedId ->
                    photos.any { it.id == protectedId }
                },
                sessionStarted = previousState.sessionStarted,
                screen = previousState.screen,
                autoCleanSelection = previousState.autoCleanSelection,
                smartScanInProgress = false,
                smartScanProcessed = reusedProcessed.coerceAtMost(photos.size),
                smartScanTotal = if (canReuseSmartScan && previousState.smartScanTotal > 0) {
                    previousState.smartScanTotal.coerceAtLeast(reusedProcessed).coerceAtMost(photos.size)
                } else {
                    photos.size
                },
                smartScanSummary = if (canReuseSmartScan) previousState.smartScanSummary.filterToExisting(photos) else SmartScanSummary(),
                smartScanSignature = if (canReuseSmartScan) currentSmartScanSignature else null,
                lastActionLabel = previousState.lastActionLabel,
                deletedCount = previousState.deletedCount,
                isPremium = Premium.isPremium(),
            )
            commitState(normalizeState(refreshedState))
            swipeHistory.clear()
        }
    }

    fun startSession() {
        commitState(normalizeState(_uiState.value.copy(
            sessionStarted = true,
            currentIndex = 0,
            screen = SessionScreen.Swipe,
        )))
    }

    fun setFilter(filter: PhotoFilter) {
        commitState(normalizeState(_uiState.value.copy(
            activeFilter = filter,
            currentIndex = 0,
        )))
    }

    fun showAutoCleanSummary() {
        commitState(_uiState.value.copy(
            sessionStarted = true,
            screen = SessionScreen.AutoCleanSummary,
            autoCleanSelection = availableAutoCleanCategories(_uiState.value),
        ))
    }

    fun startSmartScan() {
        val state = _uiState.value
        if (state.smartScanInProgress || state.allPhotos.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val scanPhotos = _uiState.value.allPhotos.filter {
                it.id !in _uiState.value.protectedPhotoIds && !it.isVideo
            }
            val currentSignature = smartScanSignature(_uiState.value.allPhotos, _uiState.value.protectedPhotoIds)
            if (_uiState.value.smartScanSignature == currentSignature && _uiState.value.smartScanProcessed > 0) {
                return@launch
            }
            commitState(_uiState.value.copy(
                smartScanInProgress = true,
                smartScanProcessed = 0,
                smartScanTotal = scanPhotos.size,
                lastActionLabel = "Analyzing library for smart cleanup",
            ))
            try {
                val summary = repository.analyzePhotos(scanPhotos) { processed, total ->
                    commitState(_uiState.value.copy(
                        smartScanInProgress = true,
                        smartScanProcessed = processed,
                        smartScanTotal = total,
                    ))
                }
                val withSummary = _uiState.value.copy(
                    smartScanInProgress = false,
                    smartScanProcessed = scanPhotos.size,
                    smartScanTotal = scanPhotos.size,
                    smartScanSummary = summary,
                    smartScanSignature = currentSignature,
                    lastActionLabel = "Smart cleanup analysis finished",
                )
                commitState(withSummary.copy(
                    autoCleanSelection = withSummary.autoCleanSelection + availableAutoCleanCategories(withSummary),
                ))
            } catch (_: Exception) {
                commitState(_uiState.value.copy(
                    smartScanInProgress = false,
                    smartScanSignature = null,
                    lastActionLabel = "Smart cleanup analysis failed",
                ))
            }
        }
    }

    fun toggleAutoCleanCategory(category: AutoCleanCategory) {
        if (category.requiresPremium && !_uiState.value.isPremium) return
        val current = _uiState.value.autoCleanSelection
        commitState(_uiState.value.copy(
            autoCleanSelection = if (category in current) current - category else current + category,
        ))
    }

    fun unlockPremium() {
        Premium.unlocked = true
        commitState(normalizeState(_uiState.value.copy(
            autoCleanSelection = availableAutoCleanCategories(_uiState.value.copy(isPremium = true)),
            lastActionLabel = "Premium unlocked",
        )))
    }

    fun showAutoCleanReview() {
        commitState(_uiState.value.copy(
            sessionStarted = true,
            screen = SessionScreen.AutoCleanReview,
        ))
    }

    fun markAutoCleanSelection() {
        val state = _uiState.value
        val selectedPhotos = selectedAutoCleanPhotos(state)
        if (selectedPhotos.isEmpty()) return
        commitState(normalizeState(state.copy(
            markedForDeletion = (state.markedForDeletion + selectedPhotos).distinctBy { it.id },
            screen = SessionScreen.Review,
            sessionStarted = true,
            lastActionLabel = "Queued ${selectedPhotos.size} auto-clean photo${if (selectedPhotos.size == 1) "" else "s"}",
        )))
    }

    fun keepCurrent() {
        val state = _uiState.value
        val photo = sessionPhotos(state).getOrNull(state.currentIndex) ?: return
        swipeHistory.addLast(SwipeRecord(photo, SwipeAction.KEEP))
        commitState(normalizeState(state.copy(
            currentIndex = state.currentIndex + 1,
            lastActionLabel = "Kept ${photo.name}",
            screen = SessionScreen.Swipe,
        )))
    }

    fun markDeleteCurrent() {
        val state = _uiState.value
        val photo = sessionPhotos(state).getOrNull(state.currentIndex) ?: return
        swipeHistory.addLast(SwipeRecord(photo, SwipeAction.DELETE))
        commitState(normalizeState(state.copy(
            currentIndex = state.currentIndex + 1,
            markedForDeletion = (state.markedForDeletion + photo).distinctBy { it.id },
            lastActionLabel = "Marked ${photo.name} for deletion",
            screen = SessionScreen.Swipe,
        )))
    }

    fun protectCurrent() {
        val state = _uiState.value
        val photo = sessionPhotos(state).getOrNull(state.currentIndex) ?: return
        commitState(normalizeState(state.copy(
            protectedPhotoIds = state.protectedPhotoIds + photo.id,
            markedForDeletion = state.markedForDeletion.filterNot { it.id == photo.id },
            lastActionLabel = "Protected ${photo.name}",
            screen = SessionScreen.Swipe,
        )))
    }

    fun undo() {
        val last = swipeHistory.removeLastOrNull() ?: return
        val state = _uiState.value
        val newMarked = if (last.action == SwipeAction.DELETE) {
            state.markedForDeletion.filterNot { it.id == last.photo.id }
        } else {
            state.markedForDeletion
        }
        commitState(normalizeState(state.copy(
            currentIndex = (state.currentIndex - 1).coerceAtLeast(0),
            markedForDeletion = newMarked,
            lastActionLabel = "Undid ${last.photo.name}",
            screen = SessionScreen.Swipe,
        )))
    }

    fun restorePhoto(photo: PhotoItem) {
        commitState(_uiState.value.copy(
            markedForDeletion = _uiState.value.markedForDeletion.filterNot { it.id == photo.id },
        ))
    }

    fun restoreAllMarked() {
        commitState(_uiState.value.copy(
            markedForDeletion = emptyList(),
            lastActionLabel = "Cleared marked photos",
        ))
    }

    fun requestDelete(context: Context) {
        val targets = _uiState.value.markedForDeletion
        if (targets.isEmpty()) return

        pendingDeletionBatch = targets
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = targets.map { it.uri }
            val request = MediaStore.createDeleteRequest(context.contentResolver, uris)
            _pendingDeleteRequest.value = request
        } else {
            context.contentResolver.deleteMany(targets)
            finalizeDeleted(targets)
            loadPhotos()
        }
    }

    fun completePendingDelete(resultCode: Int, context: Context) {
        if (pendingDeletionBatch.isEmpty()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (resultCode != Activity.RESULT_OK) {
            _pendingDeleteRequest.value = null
            pendingDeletionBatch = emptyList()
            commitState(_uiState.value.copy(lastActionLabel = "Deletion cancelled"))
            return
        }
        finalizeDeleted(pendingDeletionBatch)
        pendingDeletionBatch = emptyList()
        _pendingDeleteRequest.value = null
        loadPhotos()
    }

    private fun finalizeDeleted(targets: List<PhotoItem>) {
        val state = _uiState.value
        commitState(normalizeState(state.copy(
            markedForDeletion = emptyList(),
            deletedCount = state.deletedCount + targets.size,
            lastActionLabel = "Deleted ${targets.size} photo${if (targets.size == 1) "" else "s"}",
            sessionStarted = false,
            screen = SessionScreen.Home,
            currentIndex = 0,
        )))
        swipeHistory.clear()
    }

    fun finishReview() {
        commitState(normalizeState(_uiState.value.copy(
            sessionStarted = false,
            screen = SessionScreen.Home,
            currentIndex = 0,
        )))
    }

    fun exitAutoClean() {
        commitState(_uiState.value.copy(
            sessionStarted = false,
            screen = SessionScreen.Home,
        ))
    }

    fun showReview() {
        commitState(normalizeState(_uiState.value.copy(
            sessionStarted = true,
            screen = SessionScreen.Review,
        )))
    }

    fun resumeSwipe() {
        commitState(normalizeState(_uiState.value.copy(
            sessionStarted = true,
            screen = SessionScreen.Swipe,
        )))
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PhotoSweepViewModel(PhotoSweepRepository(context)) as T
        }
    }
}

private fun android.content.ContentResolver.deleteMany(photos: List<PhotoItem>) {
    photos.forEach { delete(it.uri, null, null) }
}


