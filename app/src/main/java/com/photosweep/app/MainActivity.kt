package com.photosweep.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : ComponentActivity() {

    private val viewModel: PhotoSweepViewModel by viewModels {
        PhotoSweepViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deleteLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) {
            viewModel.completePendingDelete(it.resultCode, this)
        }

        setContent {
            PhotoSweepTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        viewModel.loadPhotos()
                    }
                }

                LaunchedEffect(Unit) {
                    val permission = imagePermission()
                    if (ContextCompat.checkSelfPermission(this@MainActivity, permission) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.loadPhotos()
                    } else {
                        permissionLauncher.launch(permission)
                    }
                }

                PhotoSweepApp(viewModel = viewModel, onLaunchDeleteRequest = { pendingIntent ->
                    deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                })
            }
        }
    }

    private fun imagePermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
}

data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateTaken: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val relativePath: String?,
)

data class PhotoSweepUiState(
    val loading: Boolean = false,
    val allPhotos: List<PhotoItem> = emptyList(),
    val activeFilter: PhotoFilter = PhotoFilter.AllPhotos,
    val currentIndex: Int = 0,
    val markedForDeletion: List<PhotoItem> = emptyList(),
    val protectedPhotoIds: Set<Long> = emptySet(),
    val sessionStarted: Boolean = false,
    val screen: SessionScreen = SessionScreen.Home,
    val autoCleanSelection: Set<AutoCleanCategory> = AutoCleanCategory.entries.toSet(),
    val smartScanInProgress: Boolean = false,
    val smartScanProcessed: Int = 0,
    val smartScanTotal: Int = 0,
    val smartScanSummary: SmartScanSummary = SmartScanSummary(),
    val smartScanSignature: String? = null,
    val lastActionLabel: String? = null,
    val deletedCount: Int = 0,
)

enum class SessionScreen {
    Home,
    AutoCleanSummary,
    AutoCleanReview,
    Swipe,
    Review,
}

enum class AutoCleanCategory(val label: String) {
    Screenshots("Screenshots"),
    LargeFiles("Large files"),
    Duplicates("Duplicates"),
    SimilarShots("Similar shots"),
    LowQuality("Possible low-quality"),
    Selfies("Selfies"),
    Documents("Documents"),
    Memes("Memes / text-heavy"),
}

data class AutoCleanBatch(
    val category: AutoCleanCategory,
    val title: String,
    val subtitle: String,
    val photos: List<PhotoItem>,
    val bytes: Long,
    val groupCount: Int = 0,
)

data class SmartScanSummary(
    val selfieIds: Set<Long> = emptySet(),
    val documentIds: Set<Long> = emptySet(),
    val memeIds: Set<Long> = emptySet(),
    val lowQualityIds: Set<Long> = emptySet(),
)

private data class SmartPhotoAnalysis(
    val isSelfie: Boolean,
    val isDocument: Boolean,
    val isMeme: Boolean,
    val isLowQuality: Boolean,
)

private data class CachedSmartPhotoAnalysis(
    val signature: String,
    val analysis: SmartPhotoAnalysis,
)

enum class PhotoFilter(val label: String) {
    AllPhotos("All"),
    Duplicates("Duplicates"),
    LargeFiles("Large"),
    Screenshots("Screenshots"),
    OldPhotos("Old"),
}

private data class SwipeRecord(
    val photo: PhotoItem,
    val action: SwipeAction,
)

private enum class SwipeAction {
    KEEP,
    DELETE,
}

data class PersistedSessionState(
    val activeFilter: PhotoFilter = PhotoFilter.AllPhotos,
    val currentIndex: Int = 0,
    val markedForDeletionIds: Set<Long> = emptySet(),
    val protectedPhotoIds: Set<Long> = emptySet(),
    val sessionStarted: Boolean = false,
    val screen: SessionScreen = SessionScreen.Home,
    val smartScanProcessed: Int = 0,
    val smartScanTotal: Int = 0,
    val smartScanSummary: SmartScanSummary = SmartScanSummary(),
    val smartScanSignature: String? = null,
    val deletedCount: Int = 0,
)

class PhotoSweepRepository(private val context: Context) {

    private val smartAnalysisCache = mutableMapOf<Long, CachedSmartPhotoAnalysis>()
    private val prefs by lazy {
        context.getSharedPreferences("photosweep_session", Context.MODE_PRIVATE)
    }

    fun loadPhotos(): List<PhotoItem> {
        val photos = mutableListOf<PhotoItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.RELATIVE_PATH,
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
            val relativePathCol = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Photo"
                val dateTaken = cursor.getLong(dateCol)
                val sizeBytes = cursor.getLong(sizeCol)
                val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                val relativePath = if (relativePathCol >= 0) cursor.getString(relativePathCol) else null
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                photos += PhotoItem(
                    id = id,
                    uri = uri,
                    name = name,
                    dateTaken = dateTaken,
                    sizeBytes = sizeBytes,
                    width = width,
                    height = height,
                    relativePath = relativePath,
                )
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
        )
    )
    val uiState: StateFlow<PhotoSweepUiState> = _uiState.asStateFlow()

    private val _pendingDeleteRequest = MutableStateFlow<android.app.PendingIntent?>(null)
    val pendingDeleteRequest: StateFlow<android.app.PendingIntent?> = _pendingDeleteRequest.asStateFlow()

    private val swipeHistory = ArrayDeque<SwipeRecord>()
    private var pendingDeletionBatch: List<PhotoItem> = emptyList()

    private fun commitState(state: PhotoSweepUiState) {
        _uiState.value = state
        repository.persistSessionState(state)
    }

    fun loadPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            val previousState = _uiState.value
            _uiState.value = previousState.copy(loading = true)
            val photos = repository.loadPhotos()
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
            val scanPhotos = _uiState.value.allPhotos.filter { it.id !in _uiState.value.protectedPhotoIds }
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
        val current = _uiState.value.autoCleanSelection
        commitState(_uiState.value.copy(
            autoCleanSelection = if (category in current) current - category else current + category,
        ))
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

private fun String?.toIdSet(): Set<Long> {
    return this
        ?.split(',')
        ?.mapNotNull { it.toLongOrNull() }
        ?.toSet()
        ?: emptySet()
}

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

private fun isMetadataLowQuality(photo: PhotoItem): Boolean {
    val pixelCount = photo.width * photo.height
    val tinyResolution = pixelCount in 1 until LowQualityMaxPixels
    val tinyFile = photo.sizeBytes in 1 until LowQualityMaxBytes
    val screenshotLike = photo.matches(PhotoFilter.Screenshots)
    return (tinyResolution || tinyFile) && !screenshotLike
}

private fun isLikelySelfie(
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

private fun PhotoItem.analysisSignature(): String {
    return listOf(id, sizeBytes, dateTaken, width, height, relativePath.orEmpty()).joinToString("|")
}

private fun normalizedPhotoName(name: String): String {
    return name
        .substringBeforeLast(".")
        .replace(Regex("""(?i)[\s_\-]*(copy|\(\d+\)|\d+)$"""), "")
        .trim()
        .lowercase()
}

private fun PhotoItem.matches(filter: PhotoFilter, nowMillis: Long = System.currentTimeMillis()): Boolean {
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
    val availablePhotos = state.allPhotos.filter { photo -> photo.id !in state.protectedPhotoIds }
    return if (state.activeFilter == PhotoFilter.Duplicates) {
        duplicateGroups(availablePhotos).flatMap { it.photos }
    } else {
        availablePhotos.filter { photo -> photo.matches(state.activeFilter) }
    }
}

internal fun autoCleanBatches(state: PhotoSweepUiState): List<AutoCleanBatch> {
    val availablePhotos = state.allPhotos.filter { photo -> photo.id !in state.protectedPhotoIds }
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

    return listOf(
        AutoCleanBatch(
            category = AutoCleanCategory.Screenshots,
            title = "Screenshot cleanup",
            subtitle = "Fast win for clearing app captures and receipts.",
            photos = screenshots,
            bytes = screenshots.sumOf { it.sizeBytes },
        ),
        AutoCleanBatch(
            category = AutoCleanCategory.LargeFiles,
            title = "Large photo cleanup",
            subtitle = "Targets photos above ${formatBytes(LargePhotoThresholdBytes)} first.",
            photos = largeFiles,
            bytes = largeFiles.sumOf { it.sizeBytes },
        ),
        AutoCleanBatch(
            category = AutoCleanCategory.Duplicates,
            title = "Duplicate cleanup",
            subtitle = "Probable duplicates grouped by filename and image metadata.",
            photos = duplicatePhotos,
            bytes = duplicatePhotos.sumOf { it.sizeBytes },
            groupCount = duplicateGroups.size,
        ),
        AutoCleanBatch(
            category = AutoCleanCategory.SimilarShots,
            title = "Similar shot suggestions",
            subtitle = "Lower-confidence groups taken close together with matching dimensions.",
            photos = similarShotPhotos,
            bytes = similarShotPhotos.sumOf { it.sizeBytes },
            groupCount = similarShotGroups.size,
        ),
        AutoCleanBatch(
            category = AutoCleanCategory.LowQuality,
            title = "Possible low-quality photos",
            subtitle = "Lower-confidence suggestions based on very small resolution or file size.",
            photos = (lowQualityPhotos + smartLowQualityPhotos).distinctBy { it.id },
            bytes = (lowQualityPhotos + smartLowQualityPhotos).distinctBy { it.id }.sumOf { it.sizeBytes },
        ),
        AutoCleanBatch(
            category = AutoCleanCategory.Selfies,
            title = "Selfie suggestions",
            subtitle = "Face-forward shots likely taken as selfies or close portraits.",
            photos = selfiePhotos,
            bytes = selfiePhotos.sumOf { it.sizeBytes },
        ),
        AutoCleanBatch(
            category = AutoCleanCategory.Documents,
            title = "Document suggestions",
            subtitle = "Text-heavy photos that look more like documents than camera shots.",
            photos = documentPhotos,
            bytes = documentPhotos.sumOf { it.sizeBytes },
        ),
        AutoCleanBatch(
            category = AutoCleanCategory.Memes,
            title = "Meme / text-heavy suggestions",
            subtitle = "Text-heavy images that are likely screenshots, memes, or social reposts.",
            photos = memePhotos,
            bytes = memePhotos.sumOf { it.sizeBytes },
        ),
    )
}

internal fun selectedAutoCleanBatches(state: PhotoSweepUiState): List<AutoCleanBatch> {
    return autoCleanBatches(state).filter { it.category in state.autoCleanSelection && it.photos.isNotEmpty() }
}

internal fun selectedAutoCleanPhotos(state: PhotoSweepUiState): List<PhotoItem> {
    return selectedAutoCleanBatches(state)
        .flatMap { it.photos }
        .distinctBy { it.id }
}

internal fun availableAutoCleanCategories(state: PhotoSweepUiState): Set<AutoCleanCategory> {
    return autoCleanBatches(state)
        .filter { it.photos.isNotEmpty() }
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

private fun Modifier.interactiveSurface(): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.985f
            hovered -> 1.01f
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "interactiveScale",
    )
    this
        .hoverable(interactionSource = interactionSource)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (pressed) 0.92f else 1f
        }
}

private fun homeBackgroundBrush(): Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF050505),
        Color(0xFF0C0C0D),
        Color(0xFF111214),
        Color(0xFF080808),
    )
)

@Composable
fun PhotoSweepTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFFF4F4F5),
        onPrimary = Color(0xFF09090B),
        secondary = Color(0xFFD4D4D8),
        onSecondary = Color(0xFF09090B),
        tertiary = Color(0xFFA1A1AA),
        background = Color(0xFF050505),
        onBackground = Color(0xFFF5F5F5),
        surface = Color(0xFF09090B),
        onSurface = Color(0xFFF5F5F5),
        surfaceVariant = Color(0xFF161618),
        onSurfaceVariant = Color(0xFFD4D4D8),
        surfaceContainerLow = Color(0xFF101113),
        surfaceContainerHighest = Color(0xFF18191C),
        inverseSurface = Color(0xFFF5F5F5),
        inverseOnSurface = Color(0xFF09090B),
        outline = Color(0xFF3F3F46),
        error = Color(0xFFFF6B6B),
    )
    MaterialTheme(colorScheme = colors) {
        Surface(color = colors.background) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSweepApp(
    viewModel: PhotoSweepViewModel,
    onLaunchDeleteRequest: (android.app.PendingIntent) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentSessionPhotos = remember(
        uiState.allPhotos,
        uiState.protectedPhotoIds,
        uiState.activeFilter,
    ) { sessionPhotos(uiState) }
    val autoCleanBatches = remember(
        uiState.allPhotos,
        uiState.protectedPhotoIds,
        uiState.smartScanSummary,
    ) { autoCleanBatches(uiState) }
    val selectedAutoCleanBatches = remember(
        autoCleanBatches,
        uiState.autoCleanSelection,
    ) { autoCleanBatches.filter { it.category in uiState.autoCleanSelection && it.photos.isNotEmpty() } }
    val selectedAutoCleanPhotos = remember(selectedAutoCleanBatches) {
        selectedAutoCleanBatches.flatMap { it.photos }.distinctBy { it.id }
    }
    val duplicateLookup = remember(
        uiState.allPhotos,
        uiState.protectedPhotoIds,
    ) {
        duplicateGroupLookup(
            uiState.allPhotos.filter { it.id !in uiState.protectedPhotoIds }
        )
    }
    val duplicateGroupCount = duplicateLookup.values.map { it.key }.distinct().size
    val pendingDeleteRequest by viewModel.pendingDeleteRequest.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.lastActionLabel) {
        uiState.lastActionLabel?.let { label ->
            snackbarHostState.showSnackbar(label)
        }
    }

    LaunchedEffect(pendingDeleteRequest) {
        pendingDeleteRequest?.let(onLaunchDeleteRequest)
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(homeBackgroundBrush()),
        ) {
            when {
                uiState.loading -> LoadingScreen(modifier = Modifier.padding(padding))
                !uiState.sessionStarted -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    totalPhotos = uiState.allPhotos.size,
                    matchingPhotos = currentSessionPhotos.size,
                    autoCleanCount = selectedAutoCleanPhotos.size,
                    autoCleanBytes = selectedAutoCleanPhotos.sumOf { it.sizeBytes },
                    duplicateGroupCount = duplicateGroupCount,
                    markedCount = uiState.markedForDeletion.size,
                    protectedCount = uiState.protectedPhotoIds.size,
                    deletedCount = uiState.deletedCount,
                    reclaimableBytes = reclaimableBytes(uiState),
                    activeFilter = uiState.activeFilter,
                    onFilterSelected = viewModel::setFilter,
                    onStart = viewModel::startSession,
                    onAutoClean = viewModel::showAutoCleanSummary,
                    onReviewMarked = if (uiState.markedForDeletion.isNotEmpty()) viewModel::showReview else null,
                )
                uiState.screen == SessionScreen.AutoCleanSummary -> AutoCleanSummaryScreen(
                    modifier = Modifier.padding(padding),
                    batches = autoCleanBatches,
                    smartScanInProgress = uiState.smartScanInProgress,
                    smartScanProcessed = uiState.smartScanProcessed,
                    smartScanTotal = uiState.smartScanTotal,
                    selectedCategories = uiState.autoCleanSelection,
                    selectedCount = selectedAutoCleanPhotos.size,
                    selectedBytes = selectedAutoCleanPhotos.sumOf { it.sizeBytes },
                    onToggleCategory = viewModel::toggleAutoCleanCategory,
                    onContinue = viewModel::showAutoCleanReview,
                    onBack = viewModel::exitAutoClean,
                    onStartSmartScan = viewModel::startSmartScan,
                )
                uiState.screen == SessionScreen.AutoCleanReview -> AutoCleanReviewScreen(
                    modifier = Modifier.padding(padding),
                    batches = selectedAutoCleanBatches,
                    selectedCount = selectedAutoCleanPhotos.size,
                    selectedBytes = selectedAutoCleanPhotos.sumOf { it.sizeBytes },
                    onBack = viewModel::showAutoCleanSummary,
                    onSendToReview = viewModel::markAutoCleanSelection,
                )
                uiState.screen == SessionScreen.Review || uiState.currentIndex >= currentSessionPhotos.size -> ReviewScreen(
                    modifier = Modifier.padding(padding),
                    photos = uiState.markedForDeletion,
                    reclaimableBytes = reclaimableBytes(uiState),
                    onRestore = viewModel::restorePhoto,
                    onRestoreAll = viewModel::restoreAllMarked,
                    onDeleteSelected = { showDeleteConfirm = true },
                    onDone = viewModel::finishReview,
                    onResume = if (uiState.currentIndex < currentSessionPhotos.size) viewModel::resumeSwipe else null,
                )
                else -> SwipeScreen(
                    modifier = Modifier.padding(padding),
                    photos = currentSessionPhotos,
                    photoIndex = uiState.currentIndex,
                    photo = currentSessionPhotos[uiState.currentIndex],
                    duplicateGroup = duplicateLookup[currentSessionPhotos[uiState.currentIndex].id],
                    currentIndex = uiState.currentIndex + 1,
                    total = currentSessionPhotos.size,
                    markedCount = uiState.markedForDeletion.size,
                    reclaimableBytes = reclaimableBytes(uiState),
                    onKeep = viewModel::keepCurrent,
                    onDelete = viewModel::markDeleteCurrent,
                    onProtect = viewModel::protectCurrent,
                    onUndo = viewModel::undo,
                    onReview = viewModel::showReview,
                    onExit = viewModel::finishReview,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete selected photos?") },
            text = { Text("This removes ${uiState.markedForDeletion.size} marked photos from the device gallery after Android confirmation.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.requestDelete(context)
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    totalPhotos: Int,
    matchingPhotos: Int,
    autoCleanCount: Int,
    autoCleanBytes: Long,
    duplicateGroupCount: Int,
    markedCount: Int,
    protectedCount: Int,
    deletedCount: Int,
    reclaimableBytes: Long,
    activeFilter: PhotoFilter,
    onFilterSelected: (PhotoFilter) -> Unit,
    onStart: () -> Unit,
    onAutoClean: () -> Unit,
    onReviewMarked: (() -> Unit)?,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState())
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "PhotoSweep",
            fontSize = 38.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .interactiveSurface()
                .animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            shape = RoundedCornerShape(30.dp),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Clean your library without losing control.", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Swipe manually, run Auto Clean, and review everything before Android confirms deletion.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AssistChip(onClick = {}, label = { Text("$totalPhotos library") })
                    AssistChip(onClick = {}, label = { Text("$markedCount marked") })
                }
            }
        }
        StatsCard(
            totalPhotos = totalPhotos,
            matchingPhotos = matchingPhotos,
            duplicateGroupCount = duplicateGroupCount,
            markedCount = markedCount,
            protectedCount = protectedCount,
            deletedCount = deletedCount,
            reclaimableBytes = reclaimableBytes,
        )
        Text("Browse by filter", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PhotoFilter.entries.toList()) { filter: PhotoFilter ->
                AssistChip(
                    onClick = { onFilterSelected(filter) },
                    label = { Text(if (filter == activeFilter) "• ${filter.label}" else filter.label) },
                )
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .interactiveSurface()
                .animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(26.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Auto Clean Mode", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (autoCleanCount > 0) {
                        "One-tap cleanup can queue $autoCleanCount photos and free ${formatBytes(autoCleanBytes)}."
                    } else {
                        "No high-confidence auto-clean candidates found right now."
                    },
                )
                Button(
                    onClick = onAutoClean,
                    enabled = autoCleanCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (autoCleanCount > 0) "Run Auto Clean on $autoCleanCount items" else "Auto Clean unavailable")
                }
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .interactiveSurface()
                .animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(26.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Current manual session", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (matchingPhotos > 0) {
                        "$matchingPhotos photos match the ${activeFilter.label.lowercase()} filter."
                    } else {
                        "No photos match the ${activeFilter.label.lowercase()} filter."
                    },
                )
                Text(
                    when (activeFilter) {
                        PhotoFilter.AllPhotos -> "Browse the full gallery."
                        PhotoFilter.Duplicates -> "Shows probable duplicate groups based on metadata and filename patterns."
                        PhotoFilter.LargeFiles -> "Prioritizes photos at least ${formatBytes(LargePhotoThresholdBytes)} each."
                        PhotoFilter.Screenshots -> "Targets screenshots by filename and folder."
                        PhotoFilter.OldPhotos -> "Shows photos older than about 6 months."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onStart,
            enabled = matchingPhotos > 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .interactiveSurface(),
        ) {
            Text(if (matchingPhotos > 0) "Start with $matchingPhotos photos" else "No photos in this filter")
        }
        if (onReviewMarked != null) {
            OutlinedButton(
                onClick = onReviewMarked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .interactiveSurface(),
            ) {
                Text("Review $markedCount marked photos")
            }
        }
    }
}

@Composable
fun AutoCleanSummaryScreen(
    modifier: Modifier = Modifier,
    batches: List<AutoCleanBatch>,
    smartScanInProgress: Boolean,
    smartScanProcessed: Int,
    smartScanTotal: Int,
    selectedCategories: Set<AutoCleanCategory>,
    selectedCount: Int,
    selectedBytes: Long,
    onToggleCategory: (AutoCleanCategory) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onStartSmartScan: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Text("Auto Clean", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text("Close") }
        }
        Text("Scan results combine high-confidence cleanup batches with lower-confidence suggestions. Choose what to include before reviewing.")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .interactiveSurface(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("AI-assisted sorting", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (smartScanInProgress) {
                        "Analyzing $smartScanProcessed of $smartScanTotal photos for selfies, documents, memes, and quality signals."
                    } else {
                        "On-device analysis adds selfie, document, meme, and quality suggestion buckets."
                    },
                )
                OutlinedButton(onClick = onStartSmartScan, enabled = !smartScanInProgress, modifier = Modifier.fillMaxWidth()) {
                    Text(if (smartScanInProgress) "Analyzing..." else "Run smart analysis")
                }
            }
        }
        batches.forEach { batch ->
            val selected = batch.category in selectedCategories
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .interactiveSurface()
                    .animateContentSize(),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(batch.title, style = MaterialTheme.typography.titleMedium)
                        AssistChip(
                            onClick = { onToggleCategory(batch.category) },
                            label = { Text(if (selected) "Included" else "Excluded") },
                        )
                    }
                    Text(batch.subtitle, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${batch.photos.size} items | ${formatBytes(batch.bytes)}" +
                            if (batch.groupCount > 0) " | ${batch.groupCount} groups" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (
                        batch.category == AutoCleanCategory.SimilarShots ||
                        batch.category == AutoCleanCategory.LowQuality ||
                        batch.category == AutoCleanCategory.Selfies ||
                        batch.category == AutoCleanCategory.Documents ||
                        batch.category == AutoCleanCategory.Memes
                    ) {
                        Text(
                            "Suggestion only: review carefully before deleting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    AutoCleanPreviewRow(batch.photos)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onContinue,
            enabled = selectedCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (selectedCount > 0) "Review $selectedCount items (${formatBytes(selectedBytes)})" else "Nothing selected")
        }
    }
}

@Composable
fun AutoCleanReviewScreen(
    modifier: Modifier = Modifier,
    batches: List<AutoCleanBatch>,
    selectedCount: Int,
    selectedBytes: Long,
    onBack: () -> Unit,
    onSendToReview: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Batch Review", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text("These grouped batches will be queued for deletion review. You are still not deleting anything yet.")
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(batches, key = { it.category.name }) { batch ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .interactiveSurface(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(batch.title, style = MaterialTheme.typography.titleMedium)
                        Text(batch.subtitle, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${batch.photos.size} items | ${formatBytes(batch.bytes)}" +
                                if (batch.groupCount > 0) " | ${batch.groupCount} duplicate groups" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (
                            batch.category == AutoCleanCategory.SimilarShots ||
                            batch.category == AutoCleanCategory.LowQuality ||
                            batch.category == AutoCleanCategory.Selfies ||
                            batch.category == AutoCleanCategory.Documents ||
                            batch.category == AutoCleanCategory.Memes
                        ) {
                            Text(
                                "Lower-confidence suggestion batch.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        AutoCleanPreviewRow(batch.photos)
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Ready to queue", style = MaterialTheme.typography.titleMedium)
                Text("$selectedCount items", style = MaterialTheme.typography.bodyLarge)
                Text(formatBytes(selectedBytes), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(
            onClick = onSendToReview,
            enabled = selectedCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (selectedCount > 0) "Send $selectedCount items to review" else "Nothing selected")
        }
    }
}

@Composable
fun AutoCleanPreviewRow(photos: List<PhotoItem>) {
    val previewPhotos = photos.take(3)
    if (previewPhotos.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        previewPhotos.forEach { photo ->
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(88.dp),
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
fun SwipeScreen(
    modifier: Modifier = Modifier,
    photos: List<PhotoItem>,
    photoIndex: Int,
    photo: PhotoItem,
    duplicateGroup: DuplicateGroup?,
    currentIndex: Int,
    total: Int,
    markedCount: Int,
    reclaimableBytes: Long,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
    onProtect: () -> Unit,
    onUndo: () -> Unit,
    onReview: () -> Unit,
    onExit: () -> Unit,
) {
    val swipeScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val offsetX = remember(photo.id) { Animatable(0f) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val threshold = 260f
    val velocityThreshold = 2600f
    val horizontalDominanceThreshold = 1.35f
    val feedbackAlpha = (kotlin.math.abs(offsetX.value) / threshold).coerceIn(0f, 1f)
    val feedbackColor by animateColorAsState(
        targetValue = when {
            offsetX.value <= 0f -> Color(0x66D94B4B).copy(alpha = feedbackAlpha * 0.9f)
            else -> Color(0x6649C36B).copy(alpha = feedbackAlpha * 0.9f)
        },
        animationSpec = tween(120),
        label = "swipeFeedbackColor",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onExit,
                modifier = Modifier
                    .weight(1f)
                    .interactiveSurface(),
            ) { Text("Exit") }
            Text(
                text = "$currentIndex / $total",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = onReview,
                modifier = Modifier
                    .weight(1f)
                    .interactiveSurface(),
            ) { Text("Marked $markedCount", maxLines = 1) }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .interactiveSurface()
                .animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(photo.name, style = MaterialTheme.typography.titleLarge)
                Text(photo.relativePath ?: "Gallery", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${formatBytes(photo.sizeBytes)} | ${formatDimensions(photo)} | ${formatDate(photo.dateTaken)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (duplicateGroup != null) {
                    Text(
                        "Probable duplicate group: ${duplicateGroup.photos.size} photos",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    "$markedCount marked, ${formatBytes(reclaimableBytes)} ready to reclaim",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .interactiveSurface()
                .graphicsLayer {
                    translationX = offsetX.value
                    rotationZ = offsetX.value / 40f
                }
                .pointerInput(photo.id) {
                    var velocityTracker = VelocityTracker()
                    var thresholdHapticSent = false
                    var totalDragX = 0f
                    var totalDragY = 0f
                    detectDragGestures(
                        onDragStart = {
                            velocityTracker = VelocityTracker()
                            thresholdHapticSent = false
                            totalDragX = 0f
                            totalDragY = 0f
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDragEnd = {
                            val currentOffset = offsetX.value
                            val velocity = velocityTracker.calculateVelocity().x
                            val horizontalIntent = kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * horizontalDominanceThreshold
                            swipeScope.launch {
                                when {
                                    horizontalIntent && (currentOffset <= -threshold || velocity <= -velocityThreshold) -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        offsetX.animateTo(-1200f, animationSpec = tween(120))
                                        onDelete()
                                        offsetX.snapTo(0f)
                                    }
                                    horizontalIntent && (currentOffset >= threshold || velocity >= velocityThreshold) -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        offsetX.animateTo(1200f, animationSpec = tween(120))
                                        onKeep()
                                        offsetX.snapTo(0f)
                                    }
                                    else -> {
                                        offsetX.animateTo(
                                            0f,
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessHigh,
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                        swipeScope.launch {
                            val horizontalIntent = kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 0.9f
                            val appliedDrag = if (horizontalIntent) dragAmount.x * 0.9f else dragAmount.x * 0.18f
                            offsetX.snapTo(offsetX.value + appliedDrag)
                        }
                        if (!thresholdHapticSent && kotlin.math.abs(offsetX.value) >= threshold) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            thresholdHapticSent = true
                        } else if (thresholdHapticSent && kotlin.math.abs(offsetX.value) < threshold * 0.72f) {
                            thresholdHapticSent = false
                        }
                    }
                },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(feedbackColor)
                )
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(photo.id) {
                            detectTapGestures(
                                onTap = { previewIndex = photoIndex },
                                onDoubleTap = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onKeep()
                                },
                            )
                        },
                    contentScale = ContentScale.Fit,
                )


                if (offsetX.value <= -40f) {
                    SwipeBadge(
                        text = "DELETE",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .graphicsLayer { alpha = feedbackAlpha },
                    )
                }
                if (offsetX.value >= 40f) {
                    SwipeBadge(
                        text = "KEEP",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .graphicsLayer { alpha = feedbackAlpha },
                    )
                }
            }
        }
        Text(
            "Swipe left to mark for deletion, swipe right to keep, or use the actions below.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f).interactiveSurface()) { Text("Undo") }
            OutlinedButton(onClick = onProtect, modifier = Modifier.weight(1f).interactiveSurface()) { Text("Protect") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onDelete, modifier = Modifier.weight(1f).interactiveSurface()) { Text("Mark delete") }
            Button(onClick = onKeep, modifier = Modifier.weight(1f).interactiveSurface()) { Text("Keep") }
        }
    }

    previewIndex?.let { selectedIndex ->
        PhotoViewerOverlay(
            photos = photos,
            initialIndex = selectedIndex,
            enableDecisionSwipe = true,
            onKeep = {
                previewIndex = (photoIndex + 1).takeIf { it < photos.size }
                onKeep()
            },
            onDelete = {
                previewIndex = (photoIndex + 1).takeIf { it < photos.size }
                onDelete()
            },
            onUndo = {
                previewIndex = null
                onUndo()
            },
            onDismiss = { previewIndex = null },
        )
    }
}

@Composable
fun SwipeBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.inverseOnSurface,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun StatsCard(
    totalPhotos: Int,
    matchingPhotos: Int,
    duplicateGroupCount: Int,
    markedCount: Int,
    protectedCount: Int,
    deletedCount: Int,
    reclaimableBytes: Long,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .interactiveSurface()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Overview", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricBlock(label = "Library", value = totalPhotos.toString(), modifier = Modifier.weight(1f))
                MetricBlock(label = "In filter", value = matchingPhotos.toString(), modifier = Modifier.weight(1f))
                MetricBlock(label = "Marked", value = markedCount.toString(), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricBlock(label = "Duplicates", value = duplicateGroupCount.toString(), modifier = Modifier.weight(1f))
                MetricBlock(label = "Protected", value = protectedCount.toString(), modifier = Modifier.weight(1f))
                MetricBlock(label = "Deleted", value = deletedCount.toString(), modifier = Modifier.weight(1f))
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Reclaimable right now", style = MaterialTheme.typography.bodyMedium)
                    Text(formatBytes(reclaimableBytes), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun MetricBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PhotoViewerOverlay(
    photos: List<PhotoItem>,
    initialIndex: Int,
    enableDecisionSwipe: Boolean,
    onKeep: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onUndo: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var currentIndex by remember(photos, initialIndex) {
        mutableStateOf(initialIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)))
    }
    val overlayScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val dragOffsetX = remember { Animatable(0f) }
    var zoomScale by remember(currentIndex) { mutableStateOf(1f) }
    var zoomOffset by remember(currentIndex) { mutableStateOf(Offset.Zero) }
    val photo = photos.getOrNull(currentIndex) ?: return
    val swipeThreshold = 118f
    val velocityThreshold = 2100f
    val horizontalDominanceThreshold = 1.3f
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val navBarBottomInset = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val dynamicBottomInset = (configuration.screenHeightDp * 0.08f).dp
        .coerceIn(56.dp, 110.dp) + navBarBottomInset
    val cardRotation by animateFloatAsState(
        targetValue = (dragOffsetX.value / 44f).coerceIn(-18f, 18f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "viewerRotation",
    )
    val badgeAlpha by animateFloatAsState(
        targetValue = (kotlin.math.abs(dragOffsetX.value) / swipeThreshold).coerceIn(0f, 1f),
        animationSpec = tween(120),
        label = "viewerBadgeAlpha",
    )
    val deleteBadgeScale by animateFloatAsState(
        targetValue = if (dragOffsetX.value < 0f) 1f else 0.88f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "deleteBadgeScale",
    )
    val keepBadgeScale by animateFloatAsState(
        targetValue = if (dragOffsetX.value > 0f) 1f else 0.88f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "keepBadgeScale",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f)),
        ) {
            val imageModifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = dragOffsetX.value + zoomOffset.x
                    translationY = zoomOffset.y
                    rotationZ = cardRotation
                    scaleX = zoomScale
                    scaleY = zoomScale
                }
                .pointerInput(currentIndex, photos.size, enableDecisionSwipe) {
                    var velocityTracker = VelocityTracker()
                    var thresholdHapticSent = false
                    var totalDragX = 0f
                    var totalDragY = 0f
                    detectDragGestures(
                        onDragStart = {
                            velocityTracker = VelocityTracker()
                            thresholdHapticSent = false
                            totalDragX = 0f
                            totalDragY = 0f
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDragEnd = {
                            val currentOffset = dragOffsetX.value
                            val velocity = velocityTracker.calculateVelocity().x
                            val horizontalIntent = kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * horizontalDominanceThreshold
                            overlayScope.launch {
                                when {
                                    !enableDecisionSwipe && zoomScale > 1.01f -> Unit
                                    enableDecisionSwipe && horizontalIntent && (currentOffset <= -swipeThreshold || velocity <= -velocityThreshold) -> {
                                        dragOffsetX.animateTo(-1100f, animationSpec = tween(120))
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDelete?.invoke()
                                        dragOffsetX.snapTo(0f)
                                    }
                                    enableDecisionSwipe && horizontalIntent && (currentOffset >= swipeThreshold || velocity >= velocityThreshold) -> {
                                        dragOffsetX.animateTo(1100f, animationSpec = tween(120))
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onKeep?.invoke()
                                        dragOffsetX.snapTo(0f)
                                    }
                                    !enableDecisionSwipe && horizontalIntent && currentOffset <= -swipeThreshold && currentIndex < photos.lastIndex -> {
                                        dragOffsetX.animateTo(-1100f, animationSpec = tween(120))
                                        currentIndex += 1
                                        dragOffsetX.snapTo(0f)
                                    }
                                    !enableDecisionSwipe && horizontalIntent && currentOffset >= swipeThreshold && currentIndex > 0 -> {
                                        dragOffsetX.animateTo(1100f, animationSpec = tween(120))
                                        currentIndex -= 1
                                        dragOffsetX.snapTo(0f)
                                    }
                                    else -> {
                                        dragOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessMedium,
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        overlayScope.launch {
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y
                            if (!enableDecisionSwipe && zoomScale > 1.01f) {
                                zoomOffset += dragAmount
                            } else {
                                val horizontalIntent = kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 0.9f
                                val appliedDrag = if (horizontalIntent) dragAmount.x * 1.02f else dragAmount.x * 0.15f
                                dragOffsetX.snapTo(dragOffsetX.value + appliedDrag)
                            }
                        }
                        if (!thresholdHapticSent && kotlin.math.abs(dragOffsetX.value) >= swipeThreshold) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            thresholdHapticSent = true
                        } else if (thresholdHapticSent && kotlin.math.abs(dragOffsetX.value) < swipeThreshold * 0.72f) {
                            thresholdHapticSent = false
                        }
                    }
                }

            AsyncImage(
                model = photo.uri,
                contentDescription = photo.name,
                modifier = if (enableDecisionSwipe) {
                    imageModifier
                } else {
                    imageModifier
                        .pointerInput(currentIndex) {
                            detectTapGestures(
                                onLongPress = {
                                    if (zoomScale > 1.01f) {
                                        zoomScale = 1f
                                        zoomOffset = Offset.Zero
                                    } else {
                                        zoomScale = 2f
                                    }
                                },
                            )
                        }
                        .pointerInput(currentIndex) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (zoomScale * zoom).coerceIn(1f, 4f)
                                zoomScale = nextScale
                                zoomOffset = if (nextScale > 1.01f) zoomOffset + pan else Offset.Zero
                            }
                        }
                },
                contentScale = ContentScale.Fit,
            )

            if (badgeAlpha > 0f) {
                SwipeBadge(
                    text = "DELETE",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 84.dp, start = 20.dp)
                        .graphicsLayer {
                            alpha = if (dragOffsetX.value < 0f) badgeAlpha else 0f
                            scaleX = deleteBadgeScale
                            scaleY = deleteBadgeScale
                            rotationZ = -12f
                        },
                )
                SwipeBadge(
                    text = "KEEP",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 84.dp, end = 20.dp)
                        .graphicsLayer {
                            alpha = if (dragOffsetX.value > 0f) badgeAlpha else 0f
                            scaleX = keepBadgeScale
                            scaleY = keepBadgeScale
                            rotationZ = 12f
                        },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransparentOverlayButton(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${currentIndex + 1} / ${photos.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                if (enableDecisionSwipe && onUndo != null) {
                    TransparentOverlayButton(
                        text = "Undo",
                        onClick = onUndo,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }

            if (!enableDecisionSwipe && currentIndex > 0) {
                TransparentOverlayButton(
                    text = "<",
                    onClick = { currentIndex -= 1 },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp),
                )
            }
            if (!enableDecisionSwipe && currentIndex < photos.lastIndex) {
                TransparentOverlayButton(
                    text = ">",
                    onClick = { currentIndex += 1 },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                )
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = dynamicBottomInset),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = photo.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${formatBytes(photo.sizeBytes)} | ${formatDimensions(photo)} | ${formatDate(photo.dateTaken)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = photo.relativePath ?: "Gallery",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (enableDecisionSwipe) {
                        Text(
                            "Swipe left to mark delete, swipe right to keep. Use arrows to browse.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransparentOverlayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black.copy(alpha = 0.18f),
            contentColor = Color.White,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val decimals = if (value >= 10 || unitIndex == 0) 0 else 1
    return "%.${decimals}f %s".format(value, units[unitIndex])
}

private fun formatDimensions(photo: PhotoItem): String {
    return if (photo.width > 0 && photo.height > 0) {
        "${photo.width}x${photo.height}"
    } else {
        "Unknown size"
    }
}

private fun formatDate(dateTaken: Long): String {
    if (dateTaken <= 0L) return "Unknown date"
    return android.text.format.DateFormat.format("MMM d, yyyy", dateTaken).toString()
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}

private fun largestFaceRatio(
    faces: List<com.google.mlkit.vision.face.Face>,
    bitmap: Bitmap,
): Float {
    if (faces.isEmpty() || bitmap.width == 0 || bitmap.height == 0) return 0f
    val imageArea = bitmap.width * bitmap.height.toFloat()
    return faces.maxOf { face ->
        (face.boundingBox.width() * face.boundingBox.height()) / imageArea
    }
}

private fun hasCenteredPrimaryFace(
    faces: List<com.google.mlkit.vision.face.Face>,
    bitmap: Bitmap,
): Boolean {
    val primary = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return false
    if (bitmap.width == 0 || bitmap.height == 0) return false
    val centerX = primary.boundingBox.centerX() / bitmap.width.toFloat()
    val centerY = primary.boundingBox.centerY() / bitmap.height.toFloat()
    return centerX in 0.3f..0.7f && centerY in 0.25f..0.7f
}

private fun approximateSharpness(bitmap: Bitmap): Float {
    val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
    var diffSum = 0f
    var comparisons = 0
    for (y in 0 until scaled.height - 1) {
        for (x in 0 until scaled.width - 1) {
            val current = scaled.getPixel(x, y)
            val right = scaled.getPixel(x + 1, y)
            val down = scaled.getPixel(x, y + 1)
            diffSum += kotlin.math.abs(luminance(current) - luminance(right))
            diffSum += kotlin.math.abs(luminance(current) - luminance(down))
            comparisons += 2
        }
    }
    return if (comparisons == 0) 0f else diffSum / comparisons
}

private fun luminance(color: Int): Float {
    val red = (color shr 16) and 0xFF
    val green = (color shr 8) and 0xFF
    val blue = color and 0xFF
    return (0.299f * red) + (0.587f * green) + (0.114f * blue)
}

@Composable
fun ReviewScreen(
    modifier: Modifier = Modifier,
    photos: List<PhotoItem>,
    reclaimableBytes: Long,
    onRestore: (PhotoItem) -> Unit,
    onRestoreAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDone: () -> Unit,
    onResume: (() -> Unit)?,
) {
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Review", style = MaterialTheme.typography.headlineMedium)
        Text("${photos.size} photos marked for deletion")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .interactiveSurface()
                .animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Estimated space to reclaim", style = MaterialTheme.typography.titleMedium)
                Text(formatBytes(reclaimableBytes), style = MaterialTheme.typography.headlineSmall)
                Text("Preview anything before deleting. Restore individual photos or clear the whole batch.")
            }
        }

        if (photos.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Nothing marked. Nice and clean.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(photos, key = { it.id }) { photo ->
                    val itemIndex = photos.indexOfFirst { it.id == photo.id }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .interactiveSurface()
                            .animateContentSize()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = photo.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clickable { previewIndex = itemIndex.takeIf { it >= 0 } },
                                contentScale = ContentScale.Crop,
                            )
                            Text(
                                text = photo.name,
                                modifier = Modifier.padding(8.dp),
                                maxLines = 1,
                            )
                            Text(
                                text = "${formatBytes(photo.sizeBytes)} | ${formatDate(photo.dateTaken)}",
                                modifier = Modifier.padding(horizontal = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                            )
                            TextButton(onClick = { onRestore(photo) }) {
                                Text("Restore")
                            }
                        }
                    }
                }
            }
        }

        if (photos.isNotEmpty()) {
            OutlinedButton(onClick = onRestoreAll, modifier = Modifier.fillMaxWidth().interactiveSurface()) {
                Text("Restore all marked photos")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (onResume != null) {
                OutlinedButton(onClick = onResume, modifier = Modifier.weight(1f).interactiveSurface()) {
                    Text("Back to swiping")
                }
            } else {
                OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f).interactiveSurface()) {
                    Text("Finish")
                }
            }
            Button(
                onClick = onDeleteSelected,
                enabled = photos.isNotEmpty(),
                modifier = Modifier.weight(1f).interactiveSurface(),
            ) {
                Text("Delete selected")
            }
        }
        if (onResume != null) {
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().interactiveSurface()) {
                Text("Exit to home")
            }
        }
    }

    previewIndex?.let { selectedIndex ->
        PhotoViewerOverlay(
            photos = photos,
            initialIndex = selectedIndex,
            enableDecisionSwipe = false,
            onDismiss = { previewIndex = null },
        )
    }
}
