package com.astral.splitter

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.astral.splitter.ui.theme.AstralSplitterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AstralSplitterTheme {
                AstralSplitterApp()
            }
        }
    }
}

enum class SplitMode { ByHeight, ByCount }

enum class OutputFormat(val mimeType: String, val extension: String, val compressFormat: Bitmap.CompressFormat) {
    PNG("image/png", "png", Bitmap.CompressFormat.PNG),
    JPEG("image/jpeg", "jpg", Bitmap.CompressFormat.JPEG)
}

data class ImageMetadata(
    val uris: List<Uri>,
    val width: Int,
    val height: Int,
    val overlaps: List<Int> = emptyList(),
    val seamPositions: List<Int> = emptyList(),
    val sourceHeights: List<Int> = emptyList()
)

data class PreviewState(
    val metadata: ImageMetadata,
    val cutPositions: List<Float>,
    val outputFormat: OutputFormat,
    val outputQuality: Int,
    val bitmap: Bitmap
)

data class StitchedSelection(
    val bitmap: Bitmap,
    val uris: List<Uri>,
    val overlaps: List<Int>,
    val seamPositions: List<Int>,
    val sourceHeights: List<Int>
)

fun StitchedSelection.toMetadata(): ImageMetadata = ImageMetadata(
    uris = uris,
    width = bitmap.width,
    height = bitmap.height,
    overlaps = overlaps,
    seamPositions = seamPositions,
    sourceHeights = sourceHeights
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstralSplitterApp() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }
    var stitchedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var splitMode by remember { mutableStateOf(SplitMode.ByHeight) }
    var splitValue by remember { mutableStateOf("") }
    var previewState by remember { mutableStateOf<PreviewState?>(null) }
    var outputFormat by remember { mutableStateOf(OutputFormat.PNG) }
    var outputQuality by remember { mutableStateOf(100) }
    var isLoadingImages by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        isLoadingImages = true
        coroutineScope.launch {
            val stitched = withContext(Dispatchers.IO) {
                runCatching { stitchSelectedImages(context, uris, smart = false) }
            }
            isLoadingImages = false
                stitched.onSuccess { selection ->
                    stitchedBitmap = selection.bitmap
                    metadata = selection.toMetadata()
                }.onFailure {
                stitchedBitmap = null
                metadata = null
                showToast(context, it.message ?: "Gagal memuat gambar")
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("AstralSplitter") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val currentPreview = previewState
        if (currentPreview == null) {
            SetupScreen(
                modifier = Modifier.padding(padding),
                metadata = metadata,
                splitMode = splitMode,
                splitValue = splitValue,
                isLoadingImage = isLoadingImages,
                previewBitmap = stitchedBitmap,
                onSplitModeChange = { splitMode = it },
                onSplitValueChange = { splitValue = it },
                outputFormat = outputFormat,
                outputQuality = outputQuality,
                onFormatChange = { outputFormat = it },
                onQualityChange = { outputQuality = it },
                onPickImage = {
                    pickImageLauncher.launch("image/*")
                },
                onProceed = {
                    val info = metadata
                    val currentBitmap = stitchedBitmap
                    if (info == null || currentBitmap == null) {
                        showToast(context, "Pilih gambar terlebih dahulu")
                        return@SetupScreen
                    }
                    val value = splitValue.toIntOrNull()
                    if (value == null || value <= 0) {
                        showToast(context, "Nilai potong tidak valid")
                        return@SetupScreen
                    }
                    val cuts = when (splitMode) {
                        SplitMode.ByHeight -> generateCutsByHeight(info.height, value)
                        SplitMode.ByCount -> generateCutsByCount(info.height, value)
                    }
                    if (cuts.isEmpty()) {
                        showToast(context, "Tidak ada potongan yang dibutuhkan")
                        return@SetupScreen
                    }
                    previewState = PreviewState(info, cuts, outputFormat, outputQuality, currentBitmap)
                }
            )
        } else {
            PreviewScreen(
                modifier = Modifier.padding(padding),
                state = currentPreview,
                snackbarHostState = snackbarHostState,
                onBack = { previewState = null },
                onCutsConfirmed = {
                    previewState = null
                },
                onStateChange = { updated ->
                    previewState = updated
                    metadata = updated.metadata
                    stitchedBitmap = updated.bitmap
                }
            )
        }
    }
}

@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    metadata: ImageMetadata?,
    splitMode: SplitMode,
    splitValue: String,
    outputFormat: OutputFormat,
    outputQuality: Int,
    isLoadingImage: Boolean,
    previewBitmap: Bitmap?,
    onSplitModeChange: (SplitMode) -> Unit,
    onSplitValueChange: (String) -> Unit,
    onFormatChange: (OutputFormat) -> Unit,
    onQualityChange: (Int) -> Unit,
    onPickImage: () -> Unit,
    onProceed: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Pilih gambar yang ingin dipotong", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
            Text("Pilih Gambar")
        }
        if (isLoadingImage) {
            Text(
                text = "Memuat gambar...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        if (metadata != null && previewBitmap != null) {
            if (metadata.uris.size == 1) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                ) {
                    val aspectRatio = metadata.width.toFloat() / metadata.height.toFloat()
                    val displayWidth = maxWidth
                    val displayHeight = (displayWidth / aspectRatio).coerceAtMost(320.dp)
                    Box(
                        modifier = Modifier
                            .width(displayWidth)
                            .height(displayHeight)
                    ) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .matchParentSize()
                                .blur(18.dp)
                                .alpha(0.6f),
                            contentScale = ContentScale.Crop
                        )
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Pratinjau",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .width(displayWidth)
                                .height(displayHeight)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
            Text(
                text = "Ukuran gabungan: ${metadata.width} x ${metadata.height} px (${metadata.uris.size} gambar)",
                style = MaterialTheme.typography.bodyMedium
            )
            if (metadata.uris.size > 1) {
                Text(
                    text = "Preview awal tidak ditampilkan untuk banyak gambar. Cek di layar Atur Potongan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Text(text = "Opsi pemotongan", style = MaterialTheme.typography.titleMedium)
        RowedChips(splitMode = splitMode, onSplitModeChange = onSplitModeChange)
        OutlinedTextField(
            value = splitValue,
            onValueChange = onSplitValueChange,
            label = {
                Text(
                    if (splitMode == SplitMode.ByHeight) "Tinggi tiap potongan (px)" else "Jumlah potongan"
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Format output", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = outputFormat == OutputFormat.PNG,
                    onClick = { onFormatChange(OutputFormat.PNG) },
                    label = { Text("PNG") }
                )
                FilterChip(
                    selected = outputFormat == OutputFormat.JPEG,
                    onClick = { onFormatChange(OutputFormat.JPEG) },
                    label = { Text("JPG") }
                )
            }
            if (outputFormat == OutputFormat.JPEG) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Kualitas JPG (${outputQuality}%)", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = outputQuality.toFloat(),
                        onValueChange = { onQualityChange(it.roundToInt().coerceIn(1, 100)) },
                        valueRange = 1f..100f,
                        steps = 98,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Button(
            onClick = onProceed,
            modifier = Modifier.fillMaxWidth(),
            enabled = metadata != null && previewBitmap != null && !isLoadingImage
        ) {
            Text("Potong")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowedChips(splitMode: SplitMode, onSplitModeChange: (SplitMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pilih cara pemotongan:", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = splitMode == SplitMode.ByHeight,
                onClick = { onSplitModeChange(SplitMode.ByHeight) },
                label = { Text("Berdasarkan tinggi") }
            )
            FilterChip(
                selected = splitMode == SplitMode.ByCount,
                onClick = { onSplitModeChange(SplitMode.ByCount) },
                label = { Text("Berdasarkan jumlah") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    modifier: Modifier = Modifier,
    state: PreviewState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onCutsConfirmed: () -> Unit,
    onStateChange: (PreviewState) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var isSmartProcessing by remember { mutableStateOf(false) }
    var isRestitching by remember { mutableStateOf(false) }
    var isEditingStitch by remember { mutableStateOf(false) }
    var activeSeamIndex by remember { mutableStateOf<Int?>(null) }
    var topAnchor by remember { mutableStateOf(0f) }
    var bottomAnchor by remember { mutableStateOf(0f) }
    val cutPositions = remember(state.metadata, state.cutPositions) { mutableStateListOf<Float>().apply { addAll(state.cutPositions) } }
    val seamOverlaps = remember(state.metadata) {
        val expected = (state.metadata.uris.size - 1).coerceAtLeast(0)
        val existing = if (state.metadata.overlaps.isEmpty() && expected > 0) List(expected) { 0 } else state.metadata.overlaps
        mutableStateListOf<Int>().apply {
            addAll(existing.take(expected))
            repeat((expected - existing.size).coerceAtLeast(0)) { add(0) }
        }
    }

    fun applyOverlaps(updatedOverlaps: List<Int>, successMessage: String) {
        if (updatedOverlaps.size != (state.metadata.uris.size - 1).coerceAtLeast(0)) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Jumlah titik sambungan tidak sesuai")
            }
            return
        }
        coroutineScope.launch {
            isRestitching = true
            val stitched = withContext(Dispatchers.IO) {
                runCatching {
                    stitchSelectedImages(
                        context = context,
                        uris = state.metadata.uris,
                        smart = false,
                        customOverlaps = updatedOverlaps
                    )
                }
            }
            isRestitching = false
            stitched.onSuccess { selection ->
                val scale = selection.bitmap.height.toFloat() / state.bitmap.height.toFloat()
                val newCuts = cutPositions.map { it * scale }
                val updatedState = state.copy(
                    metadata = selection.toMetadata(),
                    cutPositions = newCuts,
                    bitmap = selection.bitmap
                )
                onStateChange(updatedState)
                snackbarHostState.showSnackbar(successMessage)
            }.onFailure {
                snackbarHostState.showSnackbar(it.message ?: "Gagal memperbarui sambungan")
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Atur Potongan") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        val sourceBitmap = state.bitmap
                        coroutineScope.launch {
                            isSaving = true
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val normalized = cutPositions.sorted()
                                    performCuts(context, sourceBitmap, normalized, state.outputFormat, state.outputQuality)
                                }
                            }
                            isSaving = false
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Berhasil menyimpan potongan ke folder Pictures/AstralSplitter")
                                onCutsConfirmed()
                            } else {
                                snackbarHostState.showSnackbar("Gagal memotong gambar: ${result.exceptionOrNull()?.message ?: "unknown"}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                ) {
                    Text(if (isSaving) "Memproses..." else "Potong")
                }
            }
        }
    ) { innerPadding ->
        val imageBitmap = state.bitmap
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .fillMaxSize()
        ) {
            if (state.metadata.uris.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isSmartProcessing = true
                                val stitched = withContext(Dispatchers.IO) {
                                    runCatching { stitchSelectedImages(context, state.metadata.uris, smart = true) }
                                }
                                isSmartProcessing = false
                                stitched.onSuccess { selection ->
                                    val scale = selection.bitmap.height.toFloat() / state.bitmap.height.toFloat()
                                    val newCuts = cutPositions.map { it * scale }
                                    val updatedState = state.copy(
                                        metadata = selection.toMetadata(),
                                        cutPositions = newCuts,
                                        bitmap = selection.bitmap
                                    )
                                    onStateChange(updatedState)
                                    snackbarHostState.showSnackbar("Smart stitch selesai")
                                }.onFailure {
                                    snackbarHostState.showSnackbar(it.message ?: "Smart stitch gagal")
                                }
                            }
                        },
                        enabled = !isSaving && !isSmartProcessing && !isRestitching,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isSmartProcessing) "Memproses Smart Stitch..." else "Smart Stitch")
                    }
                    Button(
                        onClick = {
                            activeSeamIndex = null
                            isEditingStitch = !isEditingStitch
                        },
                        enabled = !isSaving && !isSmartProcessing && !isRestitching,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isEditingStitch) "Selesai Edit Stitch" else "Edit Stitch Point")
                    }
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val displayWidth = maxWidth * 0.85f
                val displayHeight = if (imageBitmap.width > 0) {
                    displayWidth * imageBitmap.height.toFloat() / imageBitmap.width.toFloat()
                } else {
                    0.dp
                }
                val displayHeightPx = with(density) { displayHeight.toPx() }.coerceAtLeast(1f)
                val scale = imageBitmap.height.toFloat() / displayHeightPx
                val pieceHeights = remember(cutPositions.toList(), imageBitmap.height) {
                    val checkpoints = listOf(0f) + cutPositions.sorted() + listOf(imageBitmap.height.toFloat())
                    checkpoints.zipWithNext { start, end -> end - start }
                }
                val sliderTopHeights = remember(cutPositions.toList(), imageBitmap.height) {
                    cutPositions.mapIndexed { index, cut ->
                        val previous = if (index == 0) 0f else cutPositions[index - 1]
                        (cut - previous).coerceAtLeast(0f)
                    }
                }
                val sliderBottomHeights = remember(cutPositions.toList(), imageBitmap.height) {
                    cutPositions.mapIndexed { index, cut ->
                        val next = if (index == cutPositions.lastIndex) imageBitmap.height.toFloat() else cutPositions[index + 1]
                        (next - cut).coerceAtLeast(0f)
                    }
                }
                val seamPositions = remember(state.metadata.seamPositions, imageBitmap.height) {
                    state.metadata.seamPositions
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(displayHeight)
                        .background(Color.Black)
                        .padding(end = 12.dp)
                ) {
                    if (pieceHeights.isNotEmpty()) {
                        Text(
                            text = pieceHeights.joinToString(prefix = "Tinggi potongan: ", separator = " px, ", postfix = " px") {
                                it.toInt().coerceAtLeast(1).toString()
                            },
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(displayWidth)
                            .fillMaxHeight()
                            .align(Alignment.CenterStart)
                    ) {
                        Image(
                            bitmap = imageBitmap.asImageBitmap(),
                            contentDescription = "Gambar yang akan dipotong",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (isEditingStitch) {
                        seamPositions.forEachIndexed { index, seamStart ->
                            val displayOffset = seamStart / scale
                            val yOffset = with(density) { displayOffset.toDp() }
                            val topLimit = state.metadata.sourceHeights.getOrNull(index)?.coerceAtMost(2000) ?: 0
                            val bottomLimit = state.metadata.sourceHeights.getOrNull(index + 1)?.coerceAtMost(2000) ?: 0
                            val onStartEdit = {
                                activeSeamIndex = index
                                val currentOverlap = seamOverlaps.getOrNull(index)?.coerceAtLeast(0) ?: 0
                                val defaultTop = (currentOverlap / 2f).coerceIn(0f, topLimit.toFloat())
                                topAnchor = defaultTop
                                bottomAnchor = (currentOverlap - defaultTop).coerceIn(0f, bottomLimit.toFloat())
                            }
                            val onRedo = {
                                val updated = seamOverlaps.toMutableList()
                                updated[index] = 0
                                seamOverlaps[index] = 0
                                applyOverlaps(updated, "Sambungan direset")
                                activeSeamIndex = null
                            }
                            val onConfirm = {
                                val maxOverlap = minOf(topLimit, bottomLimit)
                                val overlapValue = (topAnchor + bottomAnchor).roundToInt().coerceIn(0, maxOverlap)
                                val updated = seamOverlaps.toMutableList()
                                updated[index] = overlapValue
                                seamOverlaps[index] = overlapValue
                                applyOverlaps(updated, "Posisi sambungan diperbarui")
                                activeSeamIndex = null
                            }
                            SeamMarker(
                                position = yOffset,
                                isActive = activeSeamIndex == index,
                                topValue = topAnchor.coerceAtMost(topLimit.toFloat()),
                                bottomValue = bottomAnchor.coerceAtMost(bottomLimit.toFloat()),
                                topRange = 0f..topLimit.toFloat(),
                                bottomRange = 0f..bottomLimit.toFloat(),
                                isBusy = isRestitching || isSmartProcessing || isSaving,
                                onStartEdit = onStartEdit,
                                onRedo = onRedo,
                                onCancel = {
                                    activeSeamIndex = null
                                    topAnchor = 0f
                                    bottomAnchor = 0f
                                },
                                onConfirm = onConfirm,
                                onTopChange = { topAnchor = it },
                                onBottomChange = { bottomAnchor = it }
                            )
                        }
                    } else {
                        cutPositions.forEachIndexed { index, positionPx ->
                            val displayOffset = positionPx / scale
                            val yOffset = with(density) { displayOffset.toDp() }
                            val topLabel = "${sliderTopHeights.getOrNull(index)?.toInt()?.coerceAtLeast(1) ?: 0} px"
                            val bottomLabel = "${sliderBottomHeights.getOrNull(index)?.toInt()?.coerceAtLeast(1) ?: 0} px"
                            SliderOverlay(
                                position = yOffset,
                                topLabel = topLabel,
                                bottomLabel = bottomLabel,
                                onDrag = { deltaPx ->
                                    val imageDelta = deltaPx * scale
                                    val previous = if (index == 0) 0f else cutPositions[index - 1] + 4f
                                    val next = if (index == cutPositions.lastIndex) imageBitmap.height.toFloat() else cutPositions[index + 1] - 4f
                                    val updated = (positionPx + imageDelta).coerceIn(previous, next)
                                    cutPositions[index] = updated
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (isEditingStitch) {
                Text(
                    text = "Gunakan ikon gunting di tiap sambungan untuk mengatur titik potong antar gambar.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                Text(
                    text = "Geser garis untuk mengatur posisi potong. Total bagian: ${cutPositions.size + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
fun SliderOverlay(position: Dp, topLabel: String, bottomLabel: String, onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = position - 64.dp)
            .height(128.dp)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> onDrag(delta) }
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SplitHandleBar(direction = HandleDirection.Up)
            SplitHandleBar(direction = HandleDirection.Down)
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = topLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(width = 44.dp, height = 40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            )
            Text(
                text = bottomLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun SplitHandleBar(
    direction: HandleDirection,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.secondary
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 4.dp)
    ) {
        val strokeWidth = 4.dp.toPx()
        val barY = size.height / 2f
        val centerX = size.width / 2f
        val handleWidth = 48.dp.toPx()
        val handleHeight = 16.dp.toPx()
        val connectorHeight = 8.dp.toPx()

        drawLine(
            color = color,
            start = Offset(0f, barY),
            end = Offset(size.width, barY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawRoundRect(
            color = color,
            topLeft = Offset(
                centerX - handleWidth / 2,
                if (direction == HandleDirection.Up) barY - strokeWidth / 2 - handleHeight - connectorHeight
                else barY + strokeWidth / 2 + connectorHeight
            ),
            size = Size(handleWidth, handleHeight),
            cornerRadius = CornerRadius(x = handleHeight / 2, y = handleHeight / 2)
        )

        drawRoundRect(
            color = color,
            topLeft = Offset(
                centerX - strokeWidth,
                if (direction == HandleDirection.Up) barY - connectorHeight - strokeWidth / 2 else barY + strokeWidth / 2
            ),
            size = Size(strokeWidth * 2, connectorHeight),
            cornerRadius = CornerRadius(x = strokeWidth, y = strokeWidth)
        )
    }
}

private enum class HandleDirection { Up, Down }

@Composable
fun SeamMarker(
    position: Dp,
    isActive: Boolean,
    topValue: Float,
    bottomValue: Float,
    topRange: ClosedFloatingPointRange<Float>,
    bottomRange: ClosedFloatingPointRange<Float>,
    isBusy: Boolean,
    onStartEdit: () -> Unit,
    onRedo: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onTopChange: (Float) -> Unit,
    onBottomChange: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = position - 56.dp)
            .height(120.dp)
    ) {
        Divider(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(),
            thickness = 3.dp,
            color = MaterialTheme.colorScheme.tertiary
        )
        Column(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isActive) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onConfirm, enabled = !isBusy) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = "Simpan titik sambungan")
                    }
                    IconButton(onClick = onCancel, enabled = !isBusy) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Batalkan editing")
                    }
                }
                Slider(
                    value = topValue,
                    onValueChange = onTopChange,
                    valueRange = topRange,
                    enabled = !isBusy,
                    modifier = Modifier
                        .height(42.dp)
                        .width(180.dp)
                        .rotate(-90f)
                )
                Slider(
                    value = bottomValue,
                    onValueChange = onBottomChange,
                    valueRange = bottomRange,
                    enabled = !isBusy,
                    modifier = Modifier
                        .height(42.dp)
                        .width(180.dp)
                        .rotate(-90f)
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onStartEdit, enabled = !isBusy) {
                        Icon(imageVector = Icons.Filled.ContentCut, contentDescription = "Edit titik sambungan")
                    }
                    IconButton(onClick = onRedo, enabled = !isBusy) {
                        Icon(imageVector = Icons.Filled.Redo, contentDescription = "Reset titik sambungan")
                    }
                }
            }
        }
    }
}

fun stitchSelectedImages(
    context: Context,
    uris: List<Uri>,
    smart: Boolean,
    customOverlaps: List<Int>? = null
): StitchedSelection {
    if (uris.isEmpty()) throw IllegalArgumentException("Tidak ada gambar yang dipilih")
    val bitmaps = uris.map { uri -> decodeBitmapSoft(context, uri) }
    val overlaps = customOverlaps ?: if (smart && uris.size > 1) {
        smartStitchPlan(bitmaps)
    } else {
        List(bitmaps.size - 1) { 0 }
    }
    return buildStitchedSelection(bitmaps, uris, overlaps)
}

fun decodeBitmapSoft(context: Context, uri: Uri): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
        decoder.isMutableRequired = false
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}

fun buildStitchedSelection(bitmaps: List<Bitmap>, uris: List<Uri>, overlaps: List<Int>): StitchedSelection {
    require(bitmaps.isNotEmpty()) { "Bitmap list tidak boleh kosong" }
    if (bitmaps.size == 1) return StitchedSelection(bitmaps.first(), uris, emptyList(), emptyList(), listOf(bitmaps.first().height))
    require(overlaps.size == bitmaps.size - 1) { "Jumlah overlap tidak sesuai" }

    val seamPositions = mutableListOf<Int>()
    var totalHeight = bitmaps.first().height
    for (index in 1 until bitmaps.size) {
        totalHeight += bitmaps[index].height - overlaps[index - 1]
    }
    val maxWidth = bitmaps.maxOf { it.width }
    val result = Bitmap.createBitmap(maxWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(result)

    var yOffset = 0
    canvas.drawBitmap(bitmaps.first(), 0f, yOffset.toFloat(), null)
    for (index in 1 until bitmaps.size) {
        yOffset += bitmaps[index - 1].height - overlaps[index - 1]
        seamPositions.add(yOffset)
        canvas.drawBitmap(bitmaps[index], 0f, yOffset.toFloat(), null)
    }
    val sourceHeights = bitmaps.map { it.height }
    return StitchedSelection(result, uris, overlaps, seamPositions, sourceHeights)
}

fun smartStitchPlan(bitmaps: List<Bitmap>): List<Int> {
    require(bitmaps.isNotEmpty()) { "Bitmap list tidak boleh kosong" }
    if (bitmaps.size == 1) return emptyList()

    val overlaps = MutableList(bitmaps.size - 1) { 0 }
    for (index in 1 until bitmaps.size) {
        overlaps[index - 1] = findVerticalOverlap(bitmaps[index - 1], bitmaps[index])
    }
    return overlaps
}

fun findVerticalOverlap(top: Bitmap, bottom: Bitmap, maxSearch: Int = 1600): Int {
    val usableWidth = minOf(top.width, bottom.width)
    val searchHeight = minOf(maxSearch, top.height, bottom.height)
    if (usableWidth <= 0 || searchHeight <= 0) return 0

    val sampleColumns = minOf(120, usableWidth)
    val columnStep = maxOf(1, usableWidth / sampleColumns)

    val topSignature = buildRowSignature(top, usableWidth, columnStep, searchHeight, fromBottom = true)
    val bottomSignature = buildRowSignature(bottom, usableWidth, columnStep, searchHeight, fromBottom = false)

    var bestOverlap = 0
    var bestScore = Float.MAX_VALUE
    for (overlap in 1..searchHeight) {
        val rowStep = maxOf(1, overlap / 24)
        var signatureDiff = 0f
        var signatureSamples = 0
        var rowIndex = 0
        val topStart = searchHeight - overlap
        while (rowIndex < overlap) {
            val topRow = topStart + rowIndex
            val bottomRow = rowIndex
            signatureDiff += abs(topSignature[topRow] - bottomSignature[bottomRow])
            signatureSamples++
            rowIndex += rowStep
        }
        if (signatureSamples == 0) continue
        val normalizedSignature = signatureDiff / signatureSamples

        val colorDiff = calculateColorBandDifference(top, bottom, usableWidth, overlap, columnStep)
        val edgeDiff = calculateEdgeDifference(top, bottom, usableWidth, overlap, columnStep)
        val texturePenalty = calculateTexturePenalty(topSignature, bottomSignature, topStart, overlap)
        val combinedScore = (normalizedSignature * 0.45f) + (colorDiff * 0.3f) + (edgeDiff * 0.2f) + texturePenalty

        if (combinedScore < bestScore) {
            bestScore = combinedScore
            bestOverlap = overlap
        }
    }

    if (bestOverlap == 0) return 0
    return refineOverlap(top, bottom, usableWidth, searchHeight, columnStep, bestOverlap)
}

fun refineOverlap(
    top: Bitmap,
    bottom: Bitmap,
    width: Int,
    maxOverlap: Int,
    columnStep: Int,
    initialBest: Int
): Int {
    var bestOverlap = initialBest
    var bestScore = Float.MAX_VALUE
    val start = maxOf(1, initialBest - 8)
    val end = minOf(maxOverlap, initialBest + 8)
    for (overlap in start..end) {
        val rowStep = maxOf(1, overlap / 20)
        var diffSum = 0f
        var rowsChecked = 0
        var rowIndex = 0
        while (rowIndex < overlap) {
            val topRow = top.height - overlap + rowIndex
            val bottomRow = rowIndex
            diffSum += sampledRowDifference(top, bottom, width, topRow, bottomRow, columnStep)
            rowsChecked++
            rowIndex += rowStep
        }
        if (rowsChecked == 0) continue
        val score = diffSum / rowsChecked
        if (score < bestScore) {
            bestScore = score
            bestOverlap = overlap
        }
    }
    return bestOverlap
}

fun buildRowSignature(bitmap: Bitmap, width: Int, columnStep: Int, sampleHeight: Int, fromBottom: Boolean): FloatArray {
    val signature = FloatArray(sampleHeight)
    val pixels = IntArray(width)
    val startRow = if (fromBottom) bitmap.height - sampleHeight else 0
    for (index in 0 until sampleHeight) {
        val row = startRow + index
        bitmap.getPixels(pixels, 0, width, 0, row, width, 1)
        var luminanceSum = 0f
        var samples = 0
        var columnIndex = 0
        while (columnIndex < width) {
            val color = pixels[columnIndex]
            val r = AndroidColor.red(color)
            val g = AndroidColor.green(color)
            val b = AndroidColor.blue(color)
            luminanceSum += (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            samples++
            columnIndex += columnStep
        }
        signature[index] = if (samples == 0) 0f else luminanceSum / samples
    }
    return signature
}

fun calculateColorBandDifference(top: Bitmap, bottom: Bitmap, width: Int, overlap: Int, columnStep: Int): Float {
    var diffSum = 0f
    var rowsChecked = 0
    val rowStep = maxOf(1, overlap / 16)
    var rowIndex = 0
    while (rowIndex < overlap) {
        val topRow = top.height - overlap + rowIndex
        val bottomRow = rowIndex
        diffSum += sampledRowDifference(top, bottom, width, topRow, bottomRow, columnStep)
        rowsChecked++
        rowIndex += rowStep
    }
    return if (rowsChecked == 0) Float.MAX_VALUE else diffSum / rowsChecked
}

fun calculateEdgeDifference(top: Bitmap, bottom: Bitmap, width: Int, overlap: Int, columnStep: Int): Float {
    var diffSum = 0f
    var rowsChecked = 0
    val rowStep = maxOf(1, overlap / 14)
    var rowIndex = 0
    while (rowIndex < overlap) {
        val topRow = top.height - overlap + rowIndex
        val bottomRow = rowIndex
        diffSum += sampledEdgeDifference(top, bottom, width, topRow, bottomRow, columnStep)
        rowsChecked++
        rowIndex += rowStep
    }
    return if (rowsChecked == 0) Float.MAX_VALUE else diffSum / rowsChecked
}

fun calculateTexturePenalty(
    topSignature: FloatArray,
    bottomSignature: FloatArray,
    topStart: Int,
    overlap: Int
): Float {
    if (overlap <= 4) return 0f
    var textureGap = 0f
    var samples = 0
    var rowIndex = 1
    while (rowIndex < overlap) {
        val topDelta = abs(topSignature[topStart + rowIndex] - topSignature[topStart + rowIndex - 1])
        val bottomDelta = abs(bottomSignature[rowIndex] - bottomSignature[rowIndex - 1])
        textureGap += abs(topDelta - bottomDelta)
        samples++
        rowIndex++
    }
    if (samples == 0) return 0f
    val normalizedGap = textureGap / samples
    return (normalizedGap * 0.35f).coerceAtMost(0.35f)
}

fun sampledRowDifference(
    top: Bitmap,
    bottom: Bitmap,
    width: Int,
    topY: Int,
    bottomY: Int,
    columnStep: Int
): Float {
    val topRow = IntArray(width)
    val bottomRow = IntArray(width)
    top.getPixels(topRow, 0, width, 0, topY, width, 1)
    bottom.getPixels(bottomRow, 0, width, 0, bottomY, width, 1)

    var diff = 0L
    var samples = 0
    var columnIndex = 0
    while (columnIndex < width) {
        val topColor = topRow[columnIndex]
        val bottomColor = bottomRow[columnIndex]
        diff += abs(AndroidColor.red(topColor) - AndroidColor.red(bottomColor))
        diff += abs(AndroidColor.green(topColor) - AndroidColor.green(bottomColor))
        diff += abs(AndroidColor.blue(topColor) - AndroidColor.blue(bottomColor))
        samples++
        columnIndex += columnStep
    }
    if (samples == 0) return Float.MAX_VALUE
    return diff.toFloat() / (samples * 3 * 255f)
}

fun sampledEdgeDifference(
    top: Bitmap,
    bottom: Bitmap,
    width: Int,
    topY: Int,
    bottomY: Int,
    columnStep: Int
): Float {
    val topRow = IntArray(width)
    val bottomRow = IntArray(width)
    top.getPixels(topRow, 0, width, 0, topY, width, 1)
    bottom.getPixels(bottomRow, 0, width, 0, bottomY, width, 1)

    var diff = 0f
    var samples = 0
    var columnIndex = columnStep
    while (columnIndex < width) {
        val prev = columnIndex - columnStep
        val topEdge = abs(AndroidColor.red(topRow[columnIndex]) - AndroidColor.red(topRow[prev])) +
            abs(AndroidColor.green(topRow[columnIndex]) - AndroidColor.green(topRow[prev])) +
            abs(AndroidColor.blue(topRow[columnIndex]) - AndroidColor.blue(topRow[prev]))
        val bottomEdge = abs(AndroidColor.red(bottomRow[columnIndex]) - AndroidColor.red(bottomRow[prev])) +
            abs(AndroidColor.green(bottomRow[columnIndex]) - AndroidColor.green(bottomRow[prev])) +
            abs(AndroidColor.blue(bottomRow[columnIndex]) - AndroidColor.blue(bottomRow[prev]))
        diff += abs(topEdge - bottomEdge)
        samples++
        columnIndex += columnStep
    }
    if (samples == 0) return Float.MAX_VALUE
    return diff / (samples * 255f * 3f)
}

suspend fun performCuts(
    context: Context,
    source: Bitmap,
    positions: List<Float>,
    format: OutputFormat,
    quality: Int
) {
    val sorted = positions.sorted()
    val extended = listOf(0f) + sorted + listOf(source.height.toFloat())
    for (index in 0 until extended.lastIndex) {
        val start = extended[index].toInt().coerceIn(0, source.height)
        val end = extended[index + 1].toInt().coerceIn(0, source.height)
        if (end - start <= 0) continue
        val segment = Bitmap.createBitmap(source, 0, start, source.width, end - start)
        saveBitmap(context, segment, index + 1, format, quality)
    }
}

fun generateCutsByHeight(imageHeight: Int, sliceHeight: Int): List<Float> {
    if (sliceHeight <= 0) return emptyList()
    val cuts = mutableListOf<Float>()
    var current = sliceHeight
    while (current < imageHeight) {
        cuts.add(current.toFloat())
        current += sliceHeight
    }
    return cuts
}

fun generateCutsByCount(imageHeight: Int, slices: Int): List<Float> {
    if (slices <= 1) return emptyList()
    val interval = imageHeight.toFloat() / slices
    return (1 until slices).map { it * interval }
}

fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

fun saveBitmap(context: Context, bitmap: Bitmap, index: Int, format: OutputFormat, quality: Int) {
    val resolver = context.contentResolver
    val filename = "AstralSplitter_${DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())}_$index.${format.extension}"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AstralSplitter")
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        ?: throw IOException("Tidak dapat membuat berkas output")
    resolver.openOutputStream(uri)?.use { outputStream ->
        val normalizedQuality = quality.coerceIn(1, 100)
        val compressionQuality = if (format == OutputFormat.JPEG) normalizedQuality else 100
        if (!bitmap.compress(format.compressFormat, compressionQuality, outputStream)) {
            throw IOException("Gagal menyimpan potongan")
        }
    }
}
