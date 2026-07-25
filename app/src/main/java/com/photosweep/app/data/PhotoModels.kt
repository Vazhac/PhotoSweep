package com.photosweep.app

import android.net.Uri

data class PhotoItem(val id: Long, val uri: Uri, val name: String, val dateTaken: Long, val sizeBytes: Long, val width: Int, val height: Int, val relativePath: String?, val isVideo: Boolean = false)

object Premium {
    @Volatile var unlocked: Boolean = false
    fun isPremium() = unlocked
}

data class PhotoSweepUiState(val loading: Boolean = false, val allPhotos: List<PhotoItem> = emptyList(), val activeFilter: PhotoFilter = PhotoFilter.AllPhotos, val currentIndex: Int = 0, val markedForDeletion: List<PhotoItem> = emptyList(), val protectedPhotoIds: Set<Long> = emptySet(), val sessionStarted: Boolean = false, val screen: SessionScreen = SessionScreen.Home, val autoCleanSelection: Set<AutoCleanCategory> = AutoCleanCategory.entries.toSet(), val smartScanInProgress: Boolean = false, val smartScanProcessed: Int = 0, val smartScanTotal: Int = 0, val smartScanSummary: SmartScanSummary = SmartScanSummary(), val smartScanSignature: String? = null, val lastActionLabel: String? = null, val deletedCount: Int = 0, val isPremium: Boolean = false)

enum class SessionScreen { Home, AutoCleanSummary, AutoCleanReview, Swipe, Review }
enum class AutoCleanCategory(val label: String, val requiresPremium: Boolean) { Screenshots("Screenshots", false), LargeFiles("Large files", false), Duplicates("Duplicates", true), SimilarShots("Similar shots", true), LowQuality("Possible low-quality", true), Selfies("Selfies", true), Documents("Documents", true), Memes("Memes / text-heavy", true) }
data class AutoCleanBatch(val category: AutoCleanCategory, val title: String, val subtitle: String, val photos: List<PhotoItem>, val allPhotos: List<PhotoItem> = photos, val bytes: Long, val groupCount: Int = 0, val isLocked: Boolean = false, val totalCount: Int = allPhotos.size)
data class SmartScanSummary(val selfieIds: Set<Long> = emptySet(), val documentIds: Set<Long> = emptySet(), val memeIds: Set<Long> = emptySet(), val lowQualityIds: Set<Long> = emptySet())
internal data class SmartPhotoAnalysis(val isSelfie: Boolean, val isDocument: Boolean, val isMeme: Boolean, val isLowQuality: Boolean)
internal data class CachedSmartPhotoAnalysis(val signature: String, val analysis: SmartPhotoAnalysis)
enum class PhotoFilter(val label: String) { AllPhotos("All"), Videos("Videos"), Duplicates("Duplicates"), LargeFiles("Large"), Screenshots("Screenshots"), OldPhotos("Old") }
internal data class SwipeRecord(val photo: PhotoItem, val action: SwipeAction)
internal enum class SwipeAction { KEEP, DELETE }
data class PersistedSessionState(val activeFilter: PhotoFilter = PhotoFilter.AllPhotos, val currentIndex: Int = 0, val markedForDeletionIds: Set<Long> = emptySet(), val protectedPhotoIds: Set<Long> = emptySet(), val sessionStarted: Boolean = false, val screen: SessionScreen = SessionScreen.Home, val smartScanProcessed: Int = 0, val smartScanTotal: Int = 0, val smartScanSummary: SmartScanSummary = SmartScanSummary(), val smartScanSignature: String? = null, val deletedCount: Int = 0)
