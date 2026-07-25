package com.photosweep.app

internal data class LibraryStorageSegment(
    val label: String,
    val bytes: Long,
)

internal fun libraryStorageBreakdown(photos: List<PhotoItem>): List<LibraryStorageSegment> {
    val groups = linkedMapOf(
        "Screenshots" to 0L,
        "Videos" to 0L,
        "Camera" to 0L,
        "Downloads" to 0L,
        "Other" to 0L,
    )
    photos.forEach { photo ->
        val path = photo.relativePath.orEmpty()
        val label = when {
            photo.isVideo -> "Videos"
            photo.matches(PhotoFilter.Screenshots) -> "Screenshots"
            path.contains("DCIM", ignoreCase = true) -> "Camera"
            path.contains("Download", ignoreCase = true) -> "Downloads"
            else -> "Other"
        }
        groups[label] = groups.getValue(label) + photo.sizeBytes
    }
    return groups.map { (label, bytes) -> LibraryStorageSegment(label, bytes) }
        .filter { it.bytes > 0L }
}
