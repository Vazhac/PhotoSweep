package com.photosweep.app

private const val LargePhotoThresholdBytes = 8L * 1024L * 1024L
private const val OldPhotoThresholdMillis = 180L * 24L * 60L * 60L * 1000L
private const val SimilarShotWindowMillis = 45L * 1000L
private const val LowQualityMaxPixels = 1600 * 1200
private const val LowQualityMaxBytes = 350L * 1024L
private const val SelfieMinFaceRatio = 0.07f

data class DuplicateGroup(
    val key: String,
    val photos: List<PhotoItem>,
)

internal fun duplicateGroups(photos: List<PhotoItem>): List<DuplicateGroup> {
    return photos
        .groupBy { photo ->
            listOf(
                normalizedPhotoName(photo.name),
                photo.sizeBytes.toString(),
                photo.width.toString(),
                photo.height.toString(),
            ).joinToString("|")
        }
        .mapNotNull { (key, groupedPhotos) ->
            if (groupedPhotos.size >= 2) {
                DuplicateGroup(
                    key = key,
                    photos = groupedPhotos.sortedByDescending { it.dateTaken },
                )
            } else {
                null
            }
        }
        .sortedByDescending { group -> group.photos.sumOf { it.sizeBytes } }
}

private fun duplicateGroupLookup(photos: List<PhotoItem>): Map<Long, DuplicateGroup> {
    return duplicateGroups(photos).flatMap { group ->
        group.photos.map { photo -> photo.id to group }
    }.toMap()
}

internal data class SimilarShotGroup(
    val photos: List<PhotoItem>,
)

internal fun similarShotGroups(photos: List<PhotoItem>): List<SimilarShotGroup> {
    val sorted = photos.sortedByDescending { it.dateTaken }
    if (sorted.isEmpty()) return emptyList()

    val groups = mutableListOf<MutableList<PhotoItem>>()
    var currentGroup = mutableListOf(sorted.first())

    for (index in 1 until sorted.size) {
        val previous = sorted[index - 1]
        val current = sorted[index]
        val sameApproxResolution = current.width == previous.width && current.height == previous.height
        val closeInTime = kotlin.math.abs(previous.dateTaken - current.dateTaken) <= SimilarShotWindowMillis
        if (sameApproxResolution && closeInTime) {
            currentGroup.add(current)
        } else {
            if (currentGroup.size >= 2) groups.add(currentGroup)
            currentGroup = mutableListOf(current)
        }
    }
    if (currentGroup.size >= 2) groups.add(currentGroup)

    return groups.map { group ->
        SimilarShotGroup(group.sortedByDescending { it.dateTaken })
    }
}

private fun possibleLowQualityPhotos(photos: List<PhotoItem>): List<PhotoItem> {
    return photos.filter(::isMetadataLowQuality).sortedBy { it.sizeBytes }
}

internal fun isMetadataLowQuality(photo: PhotoItem): Boolean {
    val pixelCount = photo.width * photo.height
    val tinyResolution = pixelCount in 1 until LowQualityMaxPixels
    val tinyFile = photo.sizeBytes in 1 until LowQualityMaxBytes
    val screenshotLike = photo.matches(PhotoFilter.Screenshots)
    return (tinyResolution || tinyFile) && !screenshotLike
}

internal fun isLikelySelfie(
    photo: PhotoItem,
    faceCount: Int,
    faceRatio: Float,
    hasCenteredFace: Boolean,
): Boolean {
    if (faceCount == 0) return false
    val portraitish = photo.height >= photo.width
    val fewFaces = faceCount <= 2
    return fewFaces && faceRatio >= SelfieMinFaceRatio && (hasCenteredFace || portraitish)
}

internal fun PhotoItem.analysisSignature(): String {
    return listOf(id, sizeBytes, dateTaken, width, height, relativePath.orEmpty()).joinToString("|")
}

private fun normalizedPhotoName(name: String): String {
    return name
        .substringBeforeLast(".")
        .replace(Regex("""(?i)[\s_\-]*(copy|\(\d+\)|\d+)$"""), "")
        .trim()
        .lowercase()
}

internal fun PhotoItem.matches(filter: PhotoFilter, nowMillis: Long = System.currentTimeMillis()): Boolean {
    return when (filter) {
        PhotoFilter.AllPhotos -> true
        PhotoFilter.Duplicates -> false
        PhotoFilter.LargeFiles -> sizeBytes >= LargePhotoThresholdBytes
        PhotoFilter.Screenshots -> {
            name.contains("screenshot", ignoreCase = true) ||
                (relativePath?.contains("screenshots", ignoreCase = true) == true)
        }
        PhotoFilter.OldPhotos -> {
            val takenAt = if (dateTaken > 0L) dateTaken else nowMillis
            nowMillis - takenAt >= OldPhotoThresholdMillis
        }
    }
}

internal fun sessionPhotos(state: PhotoSweepUiState): List<PhotoItem> {
    val availablePhotos = state.allPhotos.filter { photo ->
        photo.id !in state.protectedPhotoIds && (state.isPremium || !photo.isVideo)
    }
    return if (state.activeFilter == PhotoFilter.Duplicates) {
        duplicateGroups(availablePhotos).flatMap { it.photos }
    } else {
        availablePhotos.filter { photo -> photo.matches(state.activeFilter) }
    }
}

internal fun autoCleanBatches(state: PhotoSweepUiState): List<AutoCleanBatch> {
    val availablePhotos = state.allPhotos.filter { photo ->
        photo.id !in state.protectedPhotoIds && (state.isPremium || !photo.isVideo)
    }
    val photoById = availablePhotos.associateBy { it.id }
    val screenshots = availablePhotos.filter { it.matches(PhotoFilter.Screenshots) }
    val largeFiles = availablePhotos
        .filter { it.matches(PhotoFilter.LargeFiles) }
        .sortedByDescending { it.sizeBytes }
    val duplicateGroups = duplicateGroups(availablePhotos)
    val duplicatePhotos = duplicateGroups.flatMap { it.photos }.distinctBy { it.id }
    val similarShotGroups = similarShotGroups(availablePhotos)
    val similarShotPhotos = similarShotGroups.flatMap { it.photos }.distinctBy { it.id }
    val lowQualityPhotos = possibleLowQualityPhotos(availablePhotos)
    val smartLowQualityPhotos = state.smartScanSummary.lowQualityIds.mapNotNull(photoById::get).distinctBy { it.id }
    val selfiePhotos = state.smartScanSummary.selfieIds.mapNotNull(photoById::get).distinctBy { it.id }
    val documentPhotos = state.smartScanSummary.documentIds.mapNotNull(photoById::get).distinctBy { it.id }
    val memePhotos = state.smartScanSummary.memeIds.mapNotNull(photoById::get).distinctBy { it.id }

    fun batch(
        category: AutoCleanCategory,
        title: String,
        subtitle: String,
        photos: List<PhotoItem>,
        bytes: Long,
        groupCount: Int = 0,
    ): AutoCleanBatch {
        val isLocked = category.requiresPremium && !state.isPremium
        return AutoCleanBatch(
            category = category,
            title = title,
            subtitle = if (isLocked) "Premium Ãƒâ€šÃ‚Â· ${photos.size} found" else subtitle,
            photos = if (isLocked) photos.take(4) else photos,
            allPhotos = photos,
            bytes = bytes,
            groupCount = groupCount,
            isLocked = isLocked,
            totalCount = photos.size,
        )
    }

    return listOf(
        batch(
            category = AutoCleanCategory.Screenshots,
            title = "Screenshot cleanup",
            subtitle = "Fast win for clearing app captures and receipts.",
            photos = screenshots,
            bytes = screenshots.sumOf { it.sizeBytes },
        ),
        batch(
            category = AutoCleanCategory.LargeFiles,
            title = "Large photo cleanup",
            subtitle = "Targets photos above 8 MB first.",
            photos = largeFiles,
            bytes = largeFiles.sumOf { it.sizeBytes },
        ),
        batch(
            category = AutoCleanCategory.Duplicates,
            title = "Duplicate cleanup",
            subtitle = "Probable duplicates grouped by filename and image metadata.",
            photos = duplicatePhotos,
            bytes = duplicatePhotos.sumOf { it.sizeBytes },
            groupCount = duplicateGroups.size,
        ),
        batch(
            category = AutoCleanCategory.SimilarShots,
            title = "Similar shot suggestions",
            subtitle = "Lower-confidence groups taken close together with matching dimensions.",
            photos = similarShotPhotos,
            bytes = similarShotPhotos.sumOf { it.sizeBytes },
            groupCount = similarShotGroups.size,
        ),
        batch(
            category = AutoCleanCategory.LowQuality,
            title = "Possible low-quality photos",
            subtitle = "Lower-confidence suggestions based on very small resolution or file size.",
            photos = (lowQualityPhotos + smartLowQualityPhotos).distinctBy { it.id },
            bytes = (lowQualityPhotos + smartLowQualityPhotos).distinctBy { it.id }.sumOf { it.sizeBytes },
        ),
        batch(
            category = AutoCleanCategory.Selfies,
            title = "Selfie suggestions",
            subtitle = "Face-forward shots likely taken as selfies or close portraits.",
            photos = selfiePhotos,
            bytes = selfiePhotos.sumOf { it.sizeBytes },
        ),
        batch(
            category = AutoCleanCategory.Documents,
            title = "Document suggestions",
            subtitle = "Text-heavy photos that look more like documents than camera shots.",
            photos = documentPhotos,
            bytes = documentPhotos.sumOf { it.sizeBytes },
        ),
        batch(
            category = AutoCleanCategory.Memes,
            title = "Meme / text-heavy suggestions",
            subtitle = "Text-heavy images that are likely screenshots, memes, or social reposts.",
            photos = memePhotos,
            bytes = memePhotos.sumOf { it.sizeBytes },
        ),
    )
}

internal fun selectedAutoCleanBatches(state: PhotoSweepUiState): List<AutoCleanBatch> {
    return autoCleanBatches(state).filter { it.category in state.autoCleanSelection && !it.isLocked && it.allPhotos.isNotEmpty() }
}

internal fun selectedAutoCleanPhotos(state: PhotoSweepUiState): List<PhotoItem> {
    return selectedAutoCleanBatches(state)
        .flatMap { it.allPhotos }
        .distinctBy { it.id }
}

internal fun availableAutoCleanCategories(state: PhotoSweepUiState): Set<AutoCleanCategory> {
    return autoCleanBatches(state)
        .filter { !it.isLocked && it.allPhotos.isNotEmpty() }
        .mapTo(mutableSetOf()) { it.category }
}

internal fun smartScanSignature(
    photos: List<PhotoItem>,
    protectedPhotoIds: Set<Long>,
): String {
    var accumulator = 1125899906842597L
    photos
        .sortedBy { it.id }
        .forEach { photo ->
        accumulator = (accumulator * 31) + photo.id
        accumulator = (accumulator * 31) + photo.sizeBytes
        accumulator = (accumulator * 31) + photo.dateTaken
    }
    protectedPhotoIds.sorted().forEach { protectedId ->
        accumulator = (accumulator * 31) + protectedId
    }
    return "${photos.size}:${protectedPhotoIds.size}:$accumulator"
}

internal fun SmartScanSummary.filterToExisting(photos: List<PhotoItem>): SmartScanSummary {
    val validIds = photos.mapTo(mutableSetOf()) { it.id }
    return copy(
        selfieIds = selfieIds.filterTo(mutableSetOf()) { it in validIds },
        documentIds = documentIds.filterTo(mutableSetOf()) { it in validIds },
        memeIds = memeIds.filterTo(mutableSetOf()) { it in validIds },
        lowQualityIds = lowQualityIds.filterTo(mutableSetOf()) { it in validIds },
    )
}

private fun reclaimableBytes(state: PhotoSweepUiState): Long = state.markedForDeletion.sumOf { it.sizeBytes }

internal fun normalizeState(state: PhotoSweepUiState): PhotoSweepUiState {
    val currentPhotos = sessionPhotos(state)
    val normalizedIndex = state.currentIndex.coerceIn(0, currentPhotos.size)
    val normalizedScreen = when {
        !state.sessionStarted -> SessionScreen.Home
        normalizedIndex >= currentPhotos.size -> SessionScreen.Review
        state.screen == SessionScreen.Home -> SessionScreen.Swipe
        else -> state.screen
    }
    return state.copy(
        currentIndex = normalizedIndex,
        screen = normalizedScreen,
    )
}
