package com.astral.splitter

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.astral.splitter.ui.theme.AstralSplitterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

data class ImageMetadata(val uri: Uri, val width: Int, val height: Int)

data class PreviewState(
    val metadata: ImageMetadata,
    val cutPositions: List<Float>,
    val outputFormat: OutputFormat
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstralSplitterApp() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }
    var splitMode by remember { mutableStateOf(SplitMode.ByHeight) }
    var splitValue by remember { mutableStateOf("") }
    var previewState by remember { mutableStateOf<PreviewState?>(null) }
    var outputFormat by remember { mutableStateOf(OutputFormat.PNG) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val size = loadImageDimensions(context, uri)
            if (size != null) {
                metadata = ImageMetadata(uri, size.first, size.second)
            } else {
                metadata = null
                showToast(context, "Gagal membaca ukuran gambar")
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
                onSplitModeChange = { splitMode = it },
                onSplitValueChange = { splitValue = it },
                outputFormat = outputFormat,
                onFormatChange = { outputFormat = it },
                onPickImage = {
                    pickImageLauncher.launch("image/*")
                },
                onProceed = {
                    val info = metadata
                    if (info == null) {
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
                    previewState = PreviewState(info, cuts, outputFormat)
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
    onSplitModeChange: (SplitMode) -> Unit,
    onSplitValueChange: (String) -> Unit,
    onFormatChange: (OutputFormat) -> Unit,
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
        if (metadata != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(metadata.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Pratinjau",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
            Text(
                text = "Ukuran gambar: ${metadata.width} x ${metadata.height} px",
                style = MaterialTheme.typography.bodyMedium
            )
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
        }
        Button(onClick = onProceed, modifier = Modifier.fillMaxWidth(), enabled = metadata != null) {
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
    onCutsConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var bitmap by remember(state.metadata.uri) { mutableStateOf<Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val cutPositions = remember(state.cutPositions) { mutableStateListOf<Float>().apply { addAll(state.cutPositions) } }

    LaunchedEffect(state.metadata.uri) {
        bitmap = withContext(Dispatchers.IO) {
            loadBitmap(context, state.metadata.uri)
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
                        val sourceBitmap = bitmap
                        if (sourceBitmap == null) {
                            showToast(context, "Gambar belum siap")
                            return@Button
                        }
                        coroutineScope.launch {
                            isSaving = true
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val normalized = cutPositions.sorted()
                                    performCuts(context, sourceBitmap, normalized, state.outputFormat)
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
        val imageBitmap = bitmap
        if (imageBitmap == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Memuat gambar...")
            }
        } else {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .fillMaxSize()
            ) {
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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(displayHeight)
                            .background(Color.Black)
                            .padding(end = 12.dp)
                    ) {
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
                        cutPositions.forEachIndexed { index, positionPx ->
                            val displayOffset = positionPx / scale
                            val yOffset = with(density) { displayOffset.toDp() }
                            SliderOverlay(
                                position = yOffset,
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
                Spacer(modifier = Modifier.height(16.dp))
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
fun SliderOverlay(position: Dp, onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = position - 12.dp)
            .height(24.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consumePositionChange()
                        onDrag(dragAmount.y)
                    }
                )
            }
    ) {
        Divider(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(),
            thickness = 3.dp,
            color = MaterialTheme.colorScheme.secondary
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .align(Alignment.CenterEnd)
                .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.shapes.small)
        )
    }
}

suspend fun performCuts(
    context: Context,
    source: Bitmap,
    positions: List<Float>,
    format: OutputFormat
) {
    val sorted = positions.sorted()
    val extended = listOf(0f) + sorted + listOf(source.height.toFloat())
    for (index in 0 until extended.lastIndex) {
        val start = extended[index].toInt().coerceIn(0, source.height)
        val end = extended[index + 1].toInt().coerceIn(0, source.height)
        if (end - start <= 0) continue
        val segment = Bitmap.createBitmap(source, 0, start, source.width, end - start)
        saveBitmap(context, segment, index + 1, format)
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

fun loadImageDimensions(context: Context, uri: Uri): Pair<Int, Int>? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
    } catch (e: IOException) {
        null
    }
}

fun loadBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.isMutableRequired = false
        }
    } catch (e: Exception) {
        null
    }
}

fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

fun saveBitmap(context: Context, bitmap: Bitmap, index: Int, format: OutputFormat) {
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
        val quality = if (format == OutputFormat.JPEG) 95 else 100
        if (!bitmap.compress(format.compressFormat, quality, outputStream)) {
            throw IOException("Gagal menyimpan potongan")
        }
    }
}
