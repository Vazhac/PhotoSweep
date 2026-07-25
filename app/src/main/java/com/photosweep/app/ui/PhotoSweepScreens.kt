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
                    onUnlockClick = viewModel::unlockPremium,
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
                    onDeleteSelected = { viewModel.requestDelete(context) },
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
                    isPremium = uiState.isPremium,
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
    var showOverview by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "PhotoSweep",
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Your library", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(totalPhotos.toString(), style = MaterialTheme.typography.displayMedium)
                Text(
                    if (totalPhotos == 1) "photo ready to review" else "photos ready to review",
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(
                    onClick = onStart,
                    enabled = matchingPhotos > 0,
                    modifier = Modifier.fillMaxWidth().height(52.dp).interactiveSurface(),
                ) {
                    Text(if (matchingPhotos > 0) "Review $matchingPhotos photos" else "No photos available")
                }
            }
        }
        Text("Browse photos", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PhotoFilter.entries.toList()) { filter: PhotoFilter ->
                AssistChip(
                    onClick = { onFilterSelected(filter) },
                    label = { Text(if (filter == activeFilter) "${filter.label} selected" else filter.label) },
                )
            }
        }
        Text(
            "$matchingPhotos in this filter${if (protectedCount > 0) " / $protectedCount protected" else ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (totalPhotos > 0 || markedCount > 0 || deletedCount > 0) {
            TextButton(
                onClick = { showOverview = !showOverview },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (showOverview) "Hide library overview" else "Show library overview")
            }
            if (showOverview) {
                StatsCard(
                    totalPhotos = totalPhotos,
                    matchingPhotos = matchingPhotos,
                    duplicateGroupCount = duplicateGroupCount,
                    markedCount = markedCount,
                    protectedCount = protectedCount,
                    deletedCount = deletedCount,
                    reclaimableBytes = reclaimableBytes,
                )
            }
        }
        if (autoCleanCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth().interactiveSurface(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cleanup suggestions", style = MaterialTheme.typography.titleMedium)
                    Text("Review $autoCleanCount suggestions and potentially free ${formatBytes(autoCleanBytes)}.")
                    Button(onClick = onAutoClean, modifier = Modifier.fillMaxWidth()) {
                        Text("Review suggestions")
                    }
                }
            }
        }
        if (onReviewMarked != null && markedCount > 0) {
            OutlinedButton(
                onClick = onReviewMarked,
                modifier = Modifier.fillMaxWidth().height(52.dp).interactiveSurface(),
            ) {
                Text("Review $markedCount marked Ã‚Â· ${formatBytes(reclaimableBytes)}")
            }
        }
        if (deletedCount > 0 || duplicateGroupCount > 0) {
            Text(
                listOfNotNull(
                    deletedCount.takeIf { it > 0 }?.let { "$it deleted" },
                    duplicateGroupCount.takeIf { it > 0 }?.let { "$it duplicate groups found" },
                ).joinToString(" / "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    onUnlockClick: () -> Unit,
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
        Text("Cleanup suggestions", style = MaterialTheme.typography.headlineMedium)
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
            AutoCleanCategoryCard(
                batch = batch,
                selected = selected,
                onToggleCategory = onToggleCategory,
                onUnlockClick = onUnlockClick,
            )
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
fun AutoCleanCategoryCard(
    batch: AutoCleanBatch,
    selected: Boolean,
    onToggleCategory: (AutoCleanCategory) -> Unit,
    onUnlockClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .let {
                if (batch.isLocked) {
                    it.clickable(onClick = onUnlockClick)
                } else {
                    it.interactiveSurface()
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = when {
                batch.isLocked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                selected -> MaterialTheme.colorScheme.surfaceContainerHighest
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(batch.title, style = MaterialTheme.typography.titleMedium)
                if (batch.isLocked) {
                    AssistChip(
                        onClick = onUnlockClick,
                        label = { Text("Premium") },
                    )
                } else {
                    AssistChip(
                        onClick = { onToggleCategory(batch.category) },
                        label = { Text(if (selected) "Included" else "Excluded") },
                    )
                }
            }
            Text(batch.subtitle, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${batch.totalCount} items | ${formatBytes(batch.bytes)}" +
                    if (batch.groupCount > 0) " | ${batch.groupCount} groups" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (batch.isLocked) {
                Text(
                    "Tap to unlock full cleanup for this category.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (
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
            AutoCleanPreviewRow(batch.photos.take(4))
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
    isPremium: Boolean,
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
            ) { Text("$markedCount marked", maxLines = 1) }
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
                    formatPhotoMetadata(photo, isPremium),
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
            "Swipe left to delete, swipe right to keep.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f).interactiveSurface()) { Text("Undo") }
            OutlinedButton(onClick = onProtect, modifier = Modifier.weight(1f).interactiveSurface()) { Text("Protect") }
        }
    }

    previewIndex?.let { selectedIndex ->
        PhotoViewerOverlay(
            photos = photos,
            initialIndex = selectedIndex,
            enableDecisionSwipe = true,
            isPremium = isPremium,
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
    isPremium: Boolean,
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
                        formatPhotoMetadata(photo, isPremium),
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

private fun formatPhotoMetadata(photo: PhotoItem, isPremium: Boolean): String {
    return if (isPremium) {
        "${formatBytes(photo.sizeBytes)} | ${formatDimensions(photo)} | ${formatDate(photo.dateTaken)}"
    } else {
        formatDate(photo.dateTaken)
    }
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
            isPremium = true,
            onDismiss = { previewIndex = null },
        )
    }
}
