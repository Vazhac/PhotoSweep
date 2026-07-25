package com.photosweep.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityLogicTest {

    @Test
    fun smartScanSignature_isStableAcrossPhotoOrdering() {
        val first = testPhoto(id = 1, sizeBytes = 1_000, dateTaken = 100)
        val second = testPhoto(id = 2, sizeBytes = 2_000, dateTaken = 200)

        val signatureA = smartScanSignature(
            photos = listOf(first, second),
            protectedPhotoIds = setOf(99L),
        )
        val signatureB = smartScanSignature(
            photos = listOf(second, first),
            protectedPhotoIds = setOf(99L),
        )

        assertEquals(signatureA, signatureB)
    }

    @Test
    fun duplicateGroups_groupsNormalizedNamesAndSortsNewestFirst() {
        val newest = testPhoto(id = 1, name = "Trip copy.jpg", dateTaken = 300, sizeBytes = 4_000_000)
        val oldest = testPhoto(id = 2, name = "Trip.jpg", dateTaken = 100, sizeBytes = 4_000_000)
        val unrelated = testPhoto(id = 3, name = "Another.jpg", dateTaken = 200, sizeBytes = 4_000_000)

        val groups = duplicateGroups(listOf(oldest, unrelated, newest))

        assertEquals(1, groups.size)
        assertEquals(listOf(newest.id, oldest.id), groups.single().photos.map { it.id })
    }

    @Test
    fun autoCleanBatches_includeExpectedCategoriesAndDeduplicateSelectedPhotos() {
        val screenshot = testPhoto(
            id = 1,
            name = "Screenshot_1.png",
            relativePath = "Pictures/Screenshots/",
            sizeBytes = 9_000_000,
        )
        val duplicateA = testPhoto(id = 2, name = "Trip.jpg", sizeBytes = 3_000_000, dateTaken = 500)
        val duplicateB = testPhoto(id = 3, name = "Trip copy.jpg", sizeBytes = 3_000_000, dateTaken = 400)
        val lowQuality = testPhoto(id = 4, name = "tiny.jpg", sizeBytes = 40_000, width = 320, height = 240)
        val selfie = testPhoto(id = 5, name = "portrait.jpg", sizeBytes = 2_000_000)

        val state = PhotoSweepUiState(
            allPhotos = listOf(screenshot, duplicateA, duplicateB, lowQuality, selfie),
            autoCleanSelection = setOf(
                AutoCleanCategory.Screenshots,
                AutoCleanCategory.LargeFiles,
                AutoCleanCategory.Duplicates,
                AutoCleanCategory.LowQuality,
                AutoCleanCategory.Selfies,
            ),
            smartScanSummary = SmartScanSummary(
                selfieIds = setOf(selfie.id),
                lowQualityIds = setOf(lowQuality.id),
            ),
        )

        val categories = availableAutoCleanCategories(state)
        val selectedIds = selectedAutoCleanPhotos(state).map { it.id }
        val batches = autoCleanBatches(state)

        assertTrue(categories.contains(AutoCleanCategory.Screenshots))
        assertTrue(categories.contains(AutoCleanCategory.LargeFiles))
        assertFalse(categories.contains(AutoCleanCategory.Duplicates))
        assertFalse(categories.contains(AutoCleanCategory.LowQuality))
        assertFalse(categories.contains(AutoCleanCategory.Selfies))
        assertTrue(batches.any { it.category == AutoCleanCategory.Duplicates && it.isLocked })
        assertTrue(batches.any { it.category == AutoCleanCategory.LowQuality && it.isLocked })
        assertTrue(batches.any { it.category == AutoCleanCategory.Selfies && it.isLocked })
        assertEquals(selectedIds.distinct().size, selectedIds.size)
    }

    @Test
    fun sessionPhotos_hidesVideosForFreeUsers() {
        val photo = testPhoto(id = 1, isVideo = false)
        val video = testPhoto(id = 2, name = "clip.mp4", isVideo = true)

        val freeState = PhotoSweepUiState(
            allPhotos = listOf(photo, video),
            isPremium = false,
        )
        val premiumState = freeState.copy(isPremium = true)

        assertEquals(listOf(photo.id), sessionPhotos(freeState).map { it.id })
        assertEquals(listOf(photo.id), sessionPhotos(premiumState).map { it.id })
    }

    @Test
    fun videosFilter_isAvailableOnlyToPremiumUsers() {
        assertFalse(availablePhotoFilters(isPremium = false).contains(PhotoFilter.Videos))
        assertTrue(availablePhotoFilters(isPremium = true).contains(PhotoFilter.Videos))
    }

    @Test
    fun videosFilter_returnsOnlyVideosForPremiumUsers() {
        val photo = testPhoto(id = 1)
        val firstVideo = testPhoto(id = 2, name = "first.mp4", isVideo = true)
        val protectedVideo = testPhoto(id = 3, name = "protected.mp4", isVideo = true)
        val state = PhotoSweepUiState(
            allPhotos = listOf(photo, firstVideo, protectedVideo),
            activeFilter = PhotoFilter.Videos,
            protectedPhotoIds = setOf(protectedVideo.id),
            isPremium = true,
        )

        assertEquals(listOf(firstVideo.id), sessionPhotos(state).map { it.id })
        assertTrue(sessionPhotos(state.copy(isPremium = false)).isEmpty())
    }

    @Test
    fun autoCleanBatches_lockPremiumCategoriesButKeepRealCounts() {
        val duplicates = listOf(
            testPhoto(id = 1, name = "Trip.jpg"),
            testPhoto(id = 2, name = "Trip copy.jpg", dateTaken = 2_000L),
        )
        val selfies = (3L..7L).map { id ->
            testPhoto(id = id, name = "selfie_$id.jpg")
        }
        val state = PhotoSweepUiState(
            allPhotos = duplicates + selfies,
            smartScanSummary = SmartScanSummary(selfieIds = selfies.map { it.id }.toSet()),
            isPremium = false,
        )

        val duplicateBatch = autoCleanBatches(state).first { it.category == AutoCleanCategory.Duplicates }
        val selfieBatch = autoCleanBatches(state).first { it.category == AutoCleanCategory.Selfies }

        assertTrue(duplicateBatch.isLocked)
        assertEquals(duplicateBatch.allPhotos.size, duplicateBatch.totalCount)
        assertTrue(duplicateBatch.photos.size <= 4)
        assertTrue(duplicateBatch.subtitle.contains("Premium"))
        assertTrue(duplicateBatch.subtitle.contains("${duplicateBatch.totalCount} found"))

        assertTrue(selfieBatch.isLocked)
        assertEquals(5, selfieBatch.totalCount)
        assertEquals(4, selfieBatch.photos.size)
        assertEquals(0, selectedAutoCleanPhotos(state).size)
    }

    @Test
    fun normalizeState_routesFinishedSessionToReview() {
        val photo = testPhoto(id = 1)
        val normalized = normalizeState(
            PhotoSweepUiState(
                allPhotos = listOf(photo),
                sessionStarted = true,
                screen = SessionScreen.Swipe,
                currentIndex = 1,
            )
        )

        assertEquals(SessionScreen.Review, normalized.screen)
        assertEquals(1, normalized.currentIndex)
    }

    @Test
    fun filterToExisting_removesMissingSmartScanIds() {
        val remaining = testPhoto(id = 1)
        val summary = SmartScanSummary(
            selfieIds = setOf(1, 2),
            documentIds = setOf(2, 3),
            memeIds = setOf(1, 3),
            lowQualityIds = setOf(3, 4),
        )

        val filtered = summary.filterToExisting(listOf(remaining))

        assertEquals(setOf(1L), filtered.selfieIds)
        assertTrue(filtered.documentIds.isEmpty())
        assertEquals(setOf(1L), filtered.memeIds)
        assertFalse(filtered.lowQualityIds.contains(4L))
    }

    @Test
    fun sessionPhotos_excludesProtectedPhotosAndUsesDuplicateGroups() {
        val newest = testPhoto(id = 1, name = "Trip copy.jpg", dateTaken = 2_000L)
        val oldest = testPhoto(id = 2, name = "Trip.jpg", dateTaken = 1_000L)
        val other = testPhoto(id = 3, name = "Other.jpg")

        val state = PhotoSweepUiState(
            allPhotos = listOf(newest, oldest, other),
            activeFilter = PhotoFilter.Duplicates,
            protectedPhotoIds = setOf(oldest.id),
        )

        assertTrue(sessionPhotos(state).isEmpty())
        assertEquals(
            listOf(newest.id, oldest.id),
            sessionPhotos(state.copy(protectedPhotoIds = emptySet())).map { it.id },
        )
    }

    @Test
    fun autoCleanBatches_unlockPremiumCategoriesAndIncludeTheirPhotos() {
        val first = testPhoto(id = 1, name = "Trip.jpg")
        val second = testPhoto(id = 2, name = "Trip copy.jpg", dateTaken = 2_000L)
        val state = PhotoSweepUiState(
            allPhotos = listOf(first, second),
            autoCleanSelection = setOf(AutoCleanCategory.Duplicates),
            isPremium = true,
        )

        val duplicates = autoCleanBatches(state).first { it.category == AutoCleanCategory.Duplicates }

        assertFalse(duplicates.isLocked)
        assertEquals(2, duplicates.photos.size)
        assertEquals(setOf(first.id, second.id), selectedAutoCleanPhotos(state).map { it.id }.toSet())
    }

    @Test
    fun autoCleanBatches_hidesVideosForFreeUsers() {
        val screenshot = testPhoto(id = 1, name = "Screenshot_1.png")
        val video = testPhoto(id = 2, name = "Screenshot_2.mp4", isVideo = true)

        val freeScreenshots = autoCleanBatches(
            PhotoSweepUiState(allPhotos = listOf(screenshot, video)),
        ).first { it.category == AutoCleanCategory.Screenshots }
        val premiumScreenshots = autoCleanBatches(
            PhotoSweepUiState(allPhotos = listOf(screenshot, video), isPremium = true),
        ).first { it.category == AutoCleanCategory.Screenshots }

        assertEquals(listOf(screenshot.id), freeScreenshots.allPhotos.map { it.id })
        assertEquals(setOf(screenshot.id, video.id), premiumScreenshots.allPhotos.map { it.id }.toSet())
    }

    @Test
    fun normalizeState_returnsHomeWhenNoSessionIsActive() {
        val normalized = normalizeState(
            PhotoSweepUiState(
                allPhotos = listOf(testPhoto(id = 1)),
                sessionStarted = false,
                screen = SessionScreen.Swipe,
                currentIndex = 1,
            ),
        )

        assertEquals(SessionScreen.Home, normalized.screen)
        assertEquals(1, normalized.currentIndex)
    }

    @Test
    fun reclaimableBytes_sumsOnlyMarkedPhotos() {
        val first = testPhoto(id = 1, sizeBytes = 2_000L)
        val second = testPhoto(id = 2, sizeBytes = 3_500L)

        assertEquals(
            5_500L,
            reclaimableBytes(PhotoSweepUiState(markedForDeletion = listOf(first, second))),
        )
    }

    @Test
    fun smartScanSignature_changesWhenProtectedPhotosChange() {
        val photo = testPhoto(id = 1)

        assertFalse(
            smartScanSignature(listOf(photo), emptySet()) ==
                smartScanSignature(listOf(photo), setOf(photo.id)),
        )
    }

    @Test
    fun libraryStorageBreakdown_usesExclusiveFolderGroups() {
        val screenshots = testPhoto(
            id = 1,
            name = "Screenshot_1.png",
            relativePath = "Pictures/Screenshots/",
            sizeBytes = 100L,
        )
        val camera = testPhoto(id = 2, relativePath = "DCIM/Camera/", sizeBytes = 200L)
        val download = testPhoto(id = 3, relativePath = "Download/", sizeBytes = 300L)
        val video = testPhoto(id = 4, name = "clip.mp4", isVideo = true, sizeBytes = 400L)

        val breakdown = libraryStorageBreakdown(listOf(screenshots, camera, download, video))
            .associate { it.label to it.bytes }

        assertEquals(100L, breakdown["Screenshots"])
        assertEquals(200L, breakdown["Camera"])
        assertEquals(300L, breakdown["Downloads"])
        assertEquals(400L, breakdown["Videos"])
        assertEquals(1_000L, breakdown.values.sum())
    }

    private fun testPhoto(
        id: Long,
        name: String = "IMG_$id.jpg",
        dateTaken: Long = 1_000L,
        sizeBytes: Long = 1_500_000L,
        width: Int = 1080,
        height: Int = 1920,
        relativePath: String? = "DCIM/Camera/",
        isVideo: Boolean = false,
    ): PhotoItem {
        return PhotoItem(
            id = id,
            uri = android.net.Uri.parse("content://photos/$id"),
            name = name,
            dateTaken = dateTaken,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            relativePath = relativePath,
            isVideo = isVideo,
        )
    }
}
