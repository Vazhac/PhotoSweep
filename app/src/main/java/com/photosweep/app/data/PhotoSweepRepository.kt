package com.photosweep.app

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
class PhotoSweepRepository(private val context: Context) {

    private val smartAnalysisCache = mutableMapOf<Long, CachedSmartPhotoAnalysis>()
    private val prefs by lazy {
        context.getSharedPreferences("photosweep_session", Context.MODE_PRIVATE)
    }

    fun loadPhotos(includeVideos: Boolean): List<PhotoItem> {
        val photos = loadMediaItems(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false)
        if (includeVideos) {
            photos += loadMediaItems(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true)
        }
        return photos.sortedByDescending { it.dateTaken }
    }

    private fun loadMediaItems(uri: Uri, isVideo: Boolean): MutableList<PhotoItem> {
        val photos = mutableListOf<PhotoItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_TAKEN} DESC"

        runCatching {
            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val relativePathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val mediaStoreId = cursor.getLong(idCol)
                    photos += PhotoItem(
                        id = if (isVideo) -mediaStoreId else mediaStoreId,
                        uri = Uri.withAppendedPath(uri, mediaStoreId.toString()),
                        name = cursor.getString(nameCol) ?: if (isVideo) "Video" else "Photo",
                        dateTaken = cursor.getLong(dateCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        width = if (widthCol >= 0) cursor.getInt(widthCol) else 0,
                        height = if (heightCol >= 0) cursor.getInt(heightCol) else 0,
                        relativePath = if (relativePathCol >= 0) cursor.getString(relativePathCol) else null,
                        isVideo = isVideo,
                    )
                }
            }
        }
        return photos
    }

    suspend fun analyzePhotos(
        photos: List<PhotoItem>,
        onProgress: (Int, Int) -> Unit,
    ): SmartScanSummary {
        val faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )
        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val selfieIds = mutableSetOf<Long>()
        val documentIds = mutableSetOf<Long>()
        val memeIds = mutableSetOf<Long>()
        val lowQualityIds = mutableSetOf<Long>()

        try {
            photos.forEachIndexed { index, photo ->
                if ((index + 1) % 12 == 0 || index == photos.lastIndex) {
                    onProgress(index + 1, photos.size)
                }

                val signature = photo.analysisSignature()
                val cached = smartAnalysisCache[photo.id]?.takeIf { it.signature == signature }?.analysis
                val analysis = if (cached != null) {
                    cached
                } else {
                    val bitmap = runCatching {
                        context.contentResolver.loadThumbnail(photo.uri, Size(384, 384), null)
                    }.getOrNull() ?: return@forEachIndexed

                    val image = InputImage.fromBitmap(bitmap, 0)
                    val faces = runCatching { faceDetector.process(image).await() }.getOrDefault(emptyList())
                    val recognizedText = runCatching { textRecognizer.process(image).await() }.getOrNull()
                    val totalTextLength = recognizedText?.text?.filterNot(Char::isWhitespace)?.length ?: 0
                    val blurScore = approximateSharpness(bitmap)
                    val faceRatio = largestFaceRatio(faces, bitmap)
                    val hasCenteredFace = hasCenteredPrimaryFace(faces, bitmap)
                    val screenshotLike = photo.matches(PhotoFilter.Screenshots)

                    SmartPhotoAnalysis(
                        isSelfie = isLikelySelfie(photo, faces.size, faceRatio, hasCenteredFace),
                        isDocument = totalTextLength >= 90 && faces.isEmpty(),
                        isMeme = totalTextLength >= 30 && (screenshotLike || totalTextLength >= 160),
                        isLowQuality = isMetadataLowQuality(photo) || blurScore < 14f,
                    ).also { computed ->
                        smartAnalysisCache[photo.id] = CachedSmartPhotoAnalysis(
                            signature = signature,
                            analysis = computed,
                        )
                    }
                }

                if (analysis.isSelfie) {
                    selfieIds += photo.id
                }
                if (analysis.isDocument) {
                    documentIds += photo.id
                }
                if (analysis.isMeme && !analysis.isDocument) {
                    memeIds += photo.id
                }
                if (analysis.isLowQuality) {
                    lowQualityIds += photo.id
                }
            }
        } finally {
            faceDetector.close()
            textRecognizer.close()
        }

        return SmartScanSummary(
            selfieIds = selfieIds,
            documentIds = documentIds,
            memeIds = memeIds,
            lowQualityIds = lowQualityIds,
        )
    }

    fun loadPersistedSessionState(): PersistedSessionState {
        return PersistedSessionState(
            activeFilter = runCatching {
                PhotoFilter.valueOf(prefs.getString("active_filter", PhotoFilter.AllPhotos.name)!!)
            }.getOrDefault(PhotoFilter.AllPhotos),
            currentIndex = prefs.getInt("current_index", 0),
            markedForDeletionIds = prefs.getString("marked_ids", null).toIdSet(),
            protectedPhotoIds = prefs.getString("protected_ids", null).toIdSet(),
            sessionStarted = prefs.getBoolean("session_started", false),
            screen = runCatching {
                SessionScreen.valueOf(prefs.getString("screen", SessionScreen.Home.name)!!)
            }.getOrDefault(SessionScreen.Home),
            smartScanProcessed = prefs.getInt("smart_scan_processed", 0),
            smartScanTotal = prefs.getInt("smart_scan_total", 0),
            smartScanSummary = SmartScanSummary(
                selfieIds = prefs.getString("smart_selfie_ids", null).toIdSet(),
                documentIds = prefs.getString("smart_document_ids", null).toIdSet(),
                memeIds = prefs.getString("smart_meme_ids", null).toIdSet(),
                lowQualityIds = prefs.getString("smart_low_quality_ids", null).toIdSet(),
            ),
            smartScanSignature = prefs.getString("smart_scan_signature", null),
            deletedCount = prefs.getInt("deleted_count", 0),
        )
    }

    fun persistSessionState(state: PhotoSweepUiState) {
        prefs.edit()
            .putString("active_filter", state.activeFilter.name)
            .putInt("current_index", state.currentIndex)
            .putString("marked_ids", state.markedForDeletion.joinToString(",") { it.id.toString() })
            .putString("protected_ids", state.protectedPhotoIds.joinToString(","))
            .putBoolean("session_started", state.sessionStarted)
            .putString("screen", state.screen.name)
            .putInt("smart_scan_processed", state.smartScanProcessed)
            .putInt("smart_scan_total", state.smartScanTotal)
            .putString("smart_selfie_ids", state.smartScanSummary.selfieIds.joinToString(","))
            .putString("smart_document_ids", state.smartScanSummary.documentIds.joinToString(","))
            .putString("smart_meme_ids", state.smartScanSummary.memeIds.joinToString(","))
            .putString("smart_low_quality_ids", state.smartScanSummary.lowQualityIds.joinToString(","))
            .putString("smart_scan_signature", state.smartScanSignature)
            .putInt("deleted_count", state.deletedCount)
            .apply()
    }
}

private suspend fun <T> Task<T>.await(): T = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}

private fun String?.toIdSet(): Set<Long> = this
    ?.split(',')
    ?.mapNotNull { it.toLongOrNull() }
    ?.toSet()
    ?: emptySet()
