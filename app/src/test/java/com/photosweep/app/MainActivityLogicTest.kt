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

        assertTrue(categories.contains(AutoCleanCategory.Screenshots))
        assertTrue(categories.contains(AutoCleanCategory.LargeFiles))
        assertTrue(categories.contains(AutoCleanCategory.Duplicates))
        assertTrue(categories.contains(AutoCleanCategory.LowQuality))
        assertTrue(categories.contains(AutoCleanCategory.Selfies))
        assertEquals(selectedIds.distinct().size, selectedIds.size)
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

    private fun testPhoto(
        id: Long,
        name: String = "IMG_$id.jpg",
        dateTaken: Long = 1_000L,
        sizeBytes: Long = 1_500_000L,
        width: Int = 1080,
        height: Int = 1920,
        relativePath: String? = "DCIM/Camera/",
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
        )
    }
}
