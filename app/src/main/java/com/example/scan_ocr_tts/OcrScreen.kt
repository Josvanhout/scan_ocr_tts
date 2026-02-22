package com.example.scan_ocr_tts

import android.R.attr.enabled
import com.example.scan_ocr_tts.*

import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AmpStories
import androidx.compose.material.icons.filled.Analytics

import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DensityMedium
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow

import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Screenshot

import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.Transcribe
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.Locale

data class Bookmark(
    val pdfPath: String,
    val pageIndex: Int,
    val thresholdBias: Float,
    val rectPadding: Float,
    val contrastBoost: Float,
    val speechRate: Float,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class BookmarksData(
    val bookmarks: MutableList<Bookmark> = mutableListOf(),
    var dernierLivre: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    imageFile: File,
    pdfIdentity: String,   
    thresholdBias: Float,
    onThresholdChange: (Float) -> Unit,
    rectPadding: Float,
    onRectPaddingChange: (Float) -> Unit,
    contrastBoost: Float,
    speechRate: Float,
    onSpeechRateChange: (Float) -> Unit,
    onContrastBoostChange: (Float) -> Unit,
    highResScaleFactor: Float = 1.3f,
    onHighResScaleChange: ((Float) -> Unit)? = null,
    onNext: () -> Unit,

    onPreviousPage: (() -> Unit)? = null,
    onNextPage: (() -> Unit)? = null,
    onGoToPage: ((Int) -> Unit)? = null,
    currentPageIndex: Int = 0,
    totalPages: Int = 1,
    useHighRes: Boolean,  
    onUseHighResChange: (Boolean) -> Unit  

) {

    var showOcrEmptyWarning by remember { mutableStateOf(false) }

    var recognizedText by remember { mutableStateOf("") }

    val context = LocalContext.current

    LaunchedEffect(Unit) {

        try {
            val dummyBitmap = android.graphics.Bitmap.createBitmap(
                100, 100, android.graphics.Bitmap.Config.ARGB_8888
            )
            dummyBitmap.eraseColor(android.graphics.Color.WHITE)

            val dummyImage = com.google.mlkit.vision.common.InputImage.fromBitmap(dummyBitmap, 0)
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
            )

            recognizer.process(dummyImage)
                .addOnSuccessListener {
                    
                }
                .addOnFailureListener { e ->
                    
                }
        } catch (e: Exception) {
            
        }
    }

    val pdfFileName by remember(pdfIdentity) {
        mutableStateOf(
            try {
                
                val decodedPath = java.net.URLDecoder.decode(pdfIdentity, "UTF-8")
                
                File(decodedPath).nameWithoutExtension.ifEmpty { "Document PDF" }
            } catch (e: Exception) {
                
                pdfIdentity.substringAfterLast("/").substringBeforeLast(".")
                    .ifEmpty { "Document PDF" }
            }
        )
    }

    val prefs =
        context.applicationContext.getSharedPreferences("ocr_settings", Context.MODE_PRIVATE)

    val currentPdfPath = pdfIdentity

    var minWidthRatio by rememberSaveable { mutableStateOf(0.15f) } 
    val initialPreGrayAdjust = prefs.getFloat("preGrayAdjust", 0.0f)
    var preGrayAdjust by rememberSaveable { mutableStateOf(initialPreGrayAdjust) }
    var preGrayTTSAdjust by rememberSaveable { mutableStateOf(initialPreGrayAdjust) }

    var ttsAlreadyFinished by remember { mutableStateOf(false) }
    var pageAdvanceTriggered by remember { mutableStateOf(false) }

    val savedPdfPath = prefs.getString("lastPdfPath", null)
    val savedPage = prefs.getInt("lastPdfPage", 0)
    val savedMinWidthRatio = prefs.getFloat("minWidthRatio", 0.15f)
    val savedPreGrayAdjust = prefs.getFloat("preGrayAdjust", 0.0f)

    var OCR_lu by remember { mutableStateOf(false) }

    var lastRestoredPdf by rememberSaveable { mutableStateOf<String?>(null) }

    var all_selected by remember { mutableStateOf(false) }
    var customRectWidth by rememberSaveable { mutableStateOf(100f) }
    var customRectHeight by rememberSaveable { mutableStateOf(100f) }

    LaunchedEffect(pdfIdentity) {
        if (lastRestoredPdf != pdfIdentity) {

            val bookmarkData = getBookmarkFromJson(context, pdfIdentity)
            val savedPdfPath = bookmarkData["pdfPath"] ?: prefs.getString("lastPdfPath", null)
            val savedPage = if (savedPdfPath == pdfIdentity) {
                val pageStr = bookmarkData["pageIndex"]
                
                pageStr?.toIntOrNull() ?: 0
            } else 0

            Log.d(
                "BOOKMARK_DEBUG",
                "Comparison: savedPdfPath == pdfIdentity? ${savedPdfPath == pdfIdentity}"
            )

            if (savedPdfPath == pdfIdentity) {
                
                bookmarkData["thresholdBias"]?.toFloatOrNull()?.let(onThresholdChange)
                bookmarkData["rectPadding"]?.toFloatOrNull()?.let(onRectPaddingChange)
                bookmarkData["contrastBoost"]?.toFloatOrNull()?.let(onContrastBoostChange)
                bookmarkData["speechRate"]?.toFloatOrNull()?.let(onSpeechRateChange)
                minWidthRatio = bookmarkData["minWidthRatio"]?.toFloatOrNull() ?: 0.15f
                preGrayAdjust = bookmarkData["preGrayAdjust"]?.toFloatOrNull() ?: 0.0f
                preGrayTTSAdjust = bookmarkData["preGrayTTSAdjust"]?.toFloatOrNull() ?: 0.0f
                customRectWidth = bookmarkData["customRectWidth"]?.toFloatOrNull() ?: 100f
                customRectHeight = bookmarkData["customRectHeight"]?.toFloatOrNull() ?: 100f
                all_selected = bookmarkData["all_selected"]?.toBoolean() ?: false
                bookmarkData["useHighRes"]?.toBooleanStrictOrNull()?.let { savedUseHighRes ->
                    if (savedUseHighRes != useHighRes) {
                        onUseHighResChange(savedUseHighRes)
                    }
                }
                bookmarkData["highResScaleFactor"]?.toFloatOrNull()?.let { savedScale ->
                    onHighResScaleChange?.invoke(savedScale)
                    
                }

                onGoToPage?.invoke(savedPage)
            } else {
                
                onGoToPage?.invoke(0)
            }

            lastRestoredPdf = pdfIdentity
            
        }
    }

    var contrastBoostMode by rememberSaveable { mutableStateOf(!useHighRes) }

    LaunchedEffect(useHighRes) {
        if (useHighRes) {
            
        }
    }

    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalDisplayBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var rectangles by remember { mutableStateOf<MutableList<android.graphics.Rect>>(mutableListOf()) }
    var fullPageRect by remember { mutableStateOf<android.graphics.Rect?>(null) }

    var showTextScreen by remember { mutableStateOf(false) }

    var lastSpokenText by remember { mutableStateOf("") }

    var no_squares by remember { mutableStateOf(false) }
    var showControls by rememberSaveable { mutableStateOf(false) }
    var showControls2 by rememberSaveable { mutableStateOf(false) }
    var showProcessed by rememberSaveable { mutableStateOf(false) }

    var autoPlayEnabled by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }

    var detectedTtsLocale by remember { mutableStateOf<Locale?>(null) }

    var selectedRectIndices by remember { mutableStateOf(setOf<Int>()) }

    val handleRectanglesDeselection = {
        selectedRectIndices = emptySet()
    }

    fun updateCustomRect(bitmap: Bitmap?) {
        if (all_selected && bitmap != null) {
            val width = (bitmap.width * (customRectWidth / 100f)).toInt().coerceIn(10, bitmap.width)
            val height = (bitmap.height * (customRectHeight / 100f)).toInt().coerceIn(10, bitmap.height)

            val left = (bitmap.width - width) / 2
            val top = (bitmap.height - height) / 2

            fullPageRect = android.graphics.Rect(left, top, left + width, top + height)
            rectangles = mutableListOf(fullPageRect!!)
            selectedRectIndices = setOf(0)
        }
    }

    val toggleRectanglesSelection = {
        
        selectedRectIndices = if (selectedRectIndices.size == rectangles.size) {
            
            emptySet()
        } else {
            
            rectangles.indices.toSet() - selectedRectIndices
        }
        OCR_lu = false  
    }

    LaunchedEffect(speechRate) {
        tts?.setSpeechRate(speechRate)
    }

    LaunchedEffect(preGrayTTSAdjust) {
        OCR_lu = false  
        
    }

    DisposableEffect(Unit) {

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {

                val availableLangs = tts?.availableLanguages ?: emptySet()

                val defaultLocale = if (availableLangs.contains(Locale.FRANCE)) {
                    Locale.FRANCE
                } else if (availableLangs.contains(Locale("es", "ES"))) {
                    Locale("es", "ES")
                } else if (availableLangs.contains(Locale.US)) {
                    Locale.US
                } else {
                    Locale.getDefault()
                }

                tts?.language = defaultLocale

                tts?.setSpeechRate(speechRate)
                
                val engineInfo = tts?.defaultEngine

                val engines = tts?.engines
                engines?.forEach { engine ->
                    
                }

                tts?.setOnUtteranceProgressListener(object :
                    android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {

                        if (utteranceId == "FINAL_PART") {

                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                
                                isSpeaking = false
                                if (!autoPlayEnabled) {
                                    
                                    onNextPage?.invoke()

                                } else {
                                    
                                    autoPlayEnabled = false
                                    
                                    toggleRectanglesSelection()

                                    if (selectedRectIndices.isNotEmpty()) {
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            relaunchTts(
                                                tts = tts,
                                                selectedRectIndices = selectedRectIndices,
                                                rectangles = rectangles,
                                                originalDisplayBitmap = originalDisplayBitmap,
                                                speechRate = speechRate,
                                                detectedTtsLocale = detectedTtsLocale,
                                                preGrayTTSAdjust = preGrayTTSAdjust,
                                                onSpeechStateChange = { newState -> isSpeaking = newState },
                                                onLocaleDetected = { locale -> detectedTtsLocale = locale },
                                                onPageAdvanceReset = { pageAdvanceTriggered = false },
                                                onTextProcessed = { text -> lastSpokenText = text },
                                                onSetOcrLu = { OCR_lu = true },
                                                onOcrEmptyWarning = { showOcrEmptyWarning = it },
                                                context = context
                                            )
                                        }, 1000)
                                    }

                                }
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                    }
                })

            }
        }

        onDispose {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }

    var textBlocks by remember {
        mutableStateOf<List<TextBlock>>(emptyList())
    }

    LaunchedEffect(
        imageFile,
        thresholdBias,
        rectPadding,
        contrastBoost,
        contrastBoostMode,
        minWidthRatio,
        preGrayAdjust
    )
    {

        if (all_selected) {
            
            lastSpokenText = ""
            OCR_lu = false

            originalDisplayBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            displayBitmap = originalDisplayBitmap

            originalDisplayBitmap?.let { bmp ->
                updateCustomRect(bmp)
            }

            return@LaunchedEffect
        }

        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)

        val safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        originalDisplayBitmap = originalBitmap

        val effectiveContrast = contrastBoost * (if (contrastBoostMode) 1.35f else 1.0f)
        Log.d(
            "BOOST_DEBUG",
            "contrastBoost=$contrastBoost  boostMode=$contrastBoostMode  effective=$effectiveContrast"
        )

        val contrastBitmap = ImageProcessing.adjustContrast(originalBitmap, effectiveContrast)

        val (processedBitmap, detectedRects) = ImageProcessing.toAdaptiveThreshold(
            contrastBitmap,
            thresholdBias,
            contrastBoost,
            preGrayAdjust,
            minWidthRatio,
            skipDetection = no_squares,
            boostMode = contrastBoostMode
        )

        val boostedBitmap = processedBitmap

        rectangles = detectedRects.map {
            val pad = rectPadding.toInt()
            android.graphics.Rect(
                it.x - pad,
                it.y - pad,
                it.x + it.width + pad,
                it.y + it.height + pad
            )
        }.toMutableList()

        fullPageRect = android.graphics.Rect(0, 0, originalBitmap.width, originalBitmap.height)

        selectedRectIndices = rectangles.indices.toSet()

        displayBitmap = boostedBitmap

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val image = InputImage.fromBitmap(processedBitmap, 0)

        OcrProcessor.processImageWithMlKit(processedBitmap, 0) { ocrResult ->
            if (ocrResult.success) {

                textBlocks = ocrResult.blocks
                recognizedText = "Sélectionne les zones à garder, puis appuie sur le bouton."

            } else {
                
                recognizedText = "Erreur lors de la reconnaissance."
            }
        }

    }

    Column(modifier = Modifier.fillMaxSize()) {

        CenterAlignedTopAppBar(
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var pageInput by remember { mutableStateOf("") }
                    val keyboardController =
                        androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

                    Text(
                        text = "${currentPageIndex + 1} / $totalPages",
                        fontSize = 12.sp,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    pageInput = ""
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            )
                        }
                    )

                    androidx.compose.foundation.text.BasicTextField(
                        value = pageInput,
                        onValueChange = { pageInput = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = Color.Transparent),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                val page = pageInput.toIntOrNull()
                                if (page != null && page in 1..totalPages) {
                                    onGoToPage?.invoke(page - 1)
                                }
                                keyboardController?.hide()
                            }
                        ),
                        modifier = Modifier
                            .size(1.dp)
                            .focusRequester(focusRequester)
                            .alpha(0f)
                    )

                    FlipScreenButton()

                    IconButton(
                        onClick = { contrastBoostMode = !contrastBoostMode },
                        
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tonality,
                            contentDescription = "Boost contraste",
                            tint = if (contrastBoostMode) Color.Red else Color.White
                        )
                    }

                    IconButton(onClick = { showControls = !showControls }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Afficher les réglages",
                            tint = if (showControls) Color.Red else Color.White
                        )
                    }
                    
                    IconButton(onClick = { showControls2 = !showControls2 }) {
                        Icon(
                            imageVector = Icons.Default.DensityMedium,
                            contentDescription = "Afficher les réglages",
                            tint = if (showControls2) Color.Red else Color.White
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(verticalAlignment = Alignment.CenterVertically) {

                        IconButton(onClick = { showProcessed = !showProcessed }) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "Basculer image traitée",
                                tint = if (showProcessed) Color.Red else Color.White
                            )
                        }

                        IconButton(onClick = {
                            val pdfKey = imageFile.parentFile?.name ?: "defaultPdf"

                            prefs.edit()
                                .putFloat("thresholdBias", thresholdBias)
                                .putFloat("rectPadding", rectPadding)
                                .putFloat("contrastBoost", contrastBoost)
                                .putFloat("speechRate", speechRate)
                                .putFloat("minWidthRatio", minWidthRatio)
                                .putFloat("preGrayAdjust", preGrayAdjust)
                                .putString("lastPdfPath", currentPdfPath)
                                .putInt("lastPdfPage", currentPageIndex)
                                .apply()

                            saveBookmarkToJson(
                                context = context,
                                pdfPath = currentPdfPath,
                                pageIndex = currentPageIndex,
                                thresholdBias = thresholdBias,
                                rectPadding = rectPadding,
                                contrastBoost = contrastBoost,
                                speechRate = speechRate,
                                minWidthRatio = minWidthRatio,
                                preGrayAdjust = preGrayAdjust,
                                preGrayTTSAdjust = String.format(Locale.US, "%.2f", preGrayTTSAdjust).toFloat(),
                                useHighRes = useHighRes,
                                highResScaleFactor = highResScaleFactor,
                                customRectWidth = customRectWidth,      
                                customRectHeight = customRectHeight,    
                                all_selected = all_selected             
                            )

                            onNext()  
                        }) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Accueil"
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                    }

                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
            modifier = Modifier
                .statusBarsPadding()
                .height(36.dp)

        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D47A1)) 
                .padding(vertical = 4.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = pdfFileName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val showResult = recognizedText.isNotBlank() &&
                    recognizedText != "Sélectionne les zones à garder, puis appuie sur le bouton."

            if (!showResult) {

                val bmpToShow = if (showProcessed) displayBitmap else originalDisplayBitmap

                bmpToShow?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(rectangles, selectedRectIndices) {

                                detectTapGestures { tapOffset ->
                                    val imageAspect = bmp.width.toFloat() / bmp.height
                                    val canvasAspect = size.width / size.height

                                    val scale: Float
                                    val offsetX: Float
                                    val offsetY: Float

                                    if (imageAspect > canvasAspect) {
                                        scale = size.width.toFloat() / bmp.width
                                        offsetX = 0f
                                        offsetY = (size.height - bmp.height * scale) / 2f
                                    } else {
                                        scale = size.height.toFloat() / bmp.height
                                        offsetX = (size.width - bmp.width * scale) / 2f
                                        offsetY = 0f
                                    }

                                    rectangles.forEachIndexed { index, rect ->
                                        val left = rect.left * scale + offsetX
                                        val top = rect.top * scale + offsetY
                                        val right = rect.right * scale + offsetX
                                        val bottom = rect.bottom * scale + offsetY

                                        if (tapOffset.x in left..right && tapOffset.y in top..bottom) {
                                            
                                            selectedRectIndices =
                                                if (selectedRectIndices.contains(index)) {
                                                    selectedRectIndices - index  
                                                } else {
                                                    selectedRectIndices + index  
                                                }
                                            OCR_lu = false
                                            
                                            Log.d(
                                                "RectangleSelection",
                                                "Rectangle $index sélectionné : ${
                                                    selectedRectIndices.contains(index)
                                                }"
                                            )
                                        }
                                    }

                                }
                            }
                    ) {

                        val imageAspect = bmp.width.toFloat() / bmp.height
                        val canvasAspect = size.width / size.height

                        val scale: Float
                        val offsetX: Float
                        val offsetY: Float

                        if (imageAspect > canvasAspect) {
                            scale = size.width / bmp.width
                            offsetX = 0f
                            offsetY = (size.height - bmp.height * scale) / 2f
                        } else {
                            scale = size.height / bmp.height
                            offsetX = (size.width - bmp.width * scale) / 2f
                            offsetY = 0f
                        }
                        rectangles.forEachIndexed { index, rect ->

                            val left = rect.left * scale + offsetX
                            val top = rect.top * scale + offsetY
                            val right = rect.right * scale + offsetX
                            val bottom = rect.bottom * scale + offsetY

                            val isSelected = selectedRectIndices.contains(index)

                            val padding = if (isSelected) 6f else 0f

                            val drawLeft = left - padding
                            val drawTop = top - padding
                            val drawRight = right + padding
                            val drawBottom = bottom + padding

                            if (isSelected) {
                                
                                drawRect(
                                    color = Color.Yellow.copy(alpha = 0.35f),
                                    topLeft = Offset(drawLeft, drawTop),
                                    size = Size(drawRight - drawLeft, drawBottom - drawTop)
                                )
                            }

                            drawRect(
                                color = if (isSelected) Color.Red else Color.Gray,
                                topLeft = Offset(left, top),
                                size = Size(right - left, bottom - top),
                                style = Stroke(width = if (isSelected) 5f else 2f)
                            )

                        }
                    }

                }

            }

            if (!showResult) Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.Bottom
            ) {

                if (showControls2) {
                    Column {
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .background(
                                    color = Color(0xFFB71C1C),
                                    shape = RoundedCornerShape(8.dp)
                                )

                        ) {
                            Checkbox(
                                checked = useHighRes,
                                onCheckedChange = onUseHighResChange,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color.Black,
                                    uncheckedColor = Color.Black,
                                    checkmarkColor = Color.White
                                )
                            )
                            Text(
                                "High-res.",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp)
                            )

                            if (useHighRes) {
                                Text(
                                    text = String.format("%.1fx", highResScaleFactor),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 4.dp)
                                )

                                Slider(
                                    value = highResScaleFactor,
                                    onValueChange = { newValue ->
                                        onHighResScaleChange?.invoke(newValue)
                                    },
                                    valueRange = 1.1f..1.5f,
                                    steps = 3,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(24.dp)
                                )
                            }
                        }

                        Text(
                            text = "Frame margin : ${rectPadding.toInt()} px",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2E7D32))
                                .padding(vertical = 2.dp, horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Slider(
                            value = rectPadding,
                            onValueChange = onRectPaddingChange,
                            valueRange = 0f..18f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(24.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Reading speed : ${"%.2f".format(speechRate)}x",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2E7D32))
                                .padding(vertical = 2.dp, horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Slider(
                            value = speechRate,
                            onValueChange = onSpeechRateChange,
                            valueRange = 0.5f..1.5f,
                            steps = 19,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(24.dp)
                        )
                    }

                    if (all_selected) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Rectangle width: ${customRectWidth.toInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D47A1)) 
                                .padding(vertical = 2.dp, horizontal = 8.dp)
                        )

                        Slider(
                            value = customRectHeight,
                            onValueChange = { newValue ->
                                customRectHeight = newValue
                                originalDisplayBitmap?.let { bmp ->
                                    updateCustomRect(bmp)
                                }
                            },
                            valueRange = 10f..100f,
                            steps = 35,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(24.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Rectangle height: ${customRectHeight.toInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D47A1)) 
                                .padding(vertical = 2.dp, horizontal = 8.dp)
                        )

                        Slider(
                            value = customRectWidth,
                            onValueChange = { newValue ->
                                customRectWidth = newValue
                                originalDisplayBitmap?.let { bmp ->
                                    updateCustomRect(bmp)
                                }
                            },
                            valueRange = 10f..100f,
                            steps = 35,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(24.dp)
                        )

                    }

                }

                if (showControls) {
                    Column {

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                        }

                        Text(
                            text = "Gray preprocessing : ${"%.2f".format(preGrayAdjust)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D47A1))
                                .padding(vertical = 2.dp, horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = preGrayAdjust,
                            onValueChange = { preGrayAdjust = it },
                            valueRange = -1.0f..2.0f,
                            steps = 20,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .height(24.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Text sensitivity: ${"%.2f".format(contrastBoost)}",

                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D47A1))
                                .padding(vertical = 2.dp, horizontal = 8.dp)
                        )

                        Slider(
                            value = contrastBoost,
                            onValueChange = onContrastBoostChange,
                            valueRange = 0.1f..1.35f,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .height(24.dp)   
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Minimum white threshold: ${thresholdBias.toInt()}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D47A1))
                                .padding(vertical = 2.dp, horizontal = 8.dp)
                        )

                        Slider(
                            value = thresholdBias,
                            onValueChange = onThresholdChange,
                            valueRange = 0f..100f,  
                            steps = 19,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .height(24.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Minimum column width: ${(minWidthRatio * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D47A1))
                                .padding(vertical = 2.dp, horizontal = 8.dp)
                        )

                        Slider(
                            value = minWidthRatio,
                            onValueChange = { newValue ->
                                minWidthRatio = newValue
                                
                            },
                            valueRange = 0.05f..0.5f,
                            steps = 45,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .height(24.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Button(
                        onClick = { onPreviousPage?.invoke() },
                        enabled = currentPageIndex > 0 && onPreviousPage != null
                    ) {
                        Text("<")
                    }

                    Button(
                        onClick = {
                            if (isSpeaking) {
                                
                                tts?.stop()
                                isSpeaking = false
                            } else {
                                if (OCR_lu && lastSpokenText.isNotBlank()) {

                                    pageAdvanceTriggered = false
                                    tts?.language = detectedTtsLocale ?: Locale.FRENCH
                                    tts?.setSpeechRate(speechRate)
                                    
                                    speakLongText(tts, lastSpokenText, context)
                                    isSpeaking = true
                                } else {

                                    handleTtsButtonClick(
                                        isSpeaking = isSpeaking,
                                        tts = tts,
                                        selectedRectIndices = selectedRectIndices,
                                        rectangles = rectangles,
                                        originalDisplayBitmap = originalDisplayBitmap,
                                        speechRate = speechRate,
                                        detectedTtsLocale = detectedTtsLocale,
                                        onSpeechStateChange = { newState -> isSpeaking = newState },
                                        onLocaleDetected = { locale -> detectedTtsLocale = locale },
                                        onPageAdvanceReset = { pageAdvanceTriggered = false },onTextProcessed = { text ->
                                            lastSpokenText = text
                                            
                                        },
                                        onSetOcrLu = { OCR_lu = true },
                                        preGrayTTSAdjust = preGrayTTSAdjust,
                                        onOcrEmptyWarning = { showOcrEmptyWarning = it }
                                    )
                                }
                            }
                        }
                    ) {
                        Text(if (isSpeaking) "Stop" else "TTS")
                    }

                    Button(
                        onClick = { onNextPage?.invoke() },
                        enabled = currentPageIndex < totalPages - 1 && onNextPage != null
                    ) {
                        Text(">")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Checkbox(
                        checked = autoPlayEnabled,
                        onCheckedChange = {
                            autoPlayEnabled = it

                            if (it) {
                                if (isSpeaking) {
                                    
                                    tts?.stop()
                                    isSpeaking = false
                                } else {
                                    
                                    if (OCR_lu && lastSpokenText.isNotBlank()) {

                                        pageAdvanceTriggered = false
                                        tts?.language = detectedTtsLocale ?: Locale.FRENCH
                                        tts?.setSpeechRate(speechRate)
                                        speakLongText(tts, lastSpokenText, context)
                                        isSpeaking = true
                                    } else {

                                        handleTtsButtonClick(
                                            isSpeaking = isSpeaking,
                                            tts = tts,
                                            selectedRectIndices = selectedRectIndices,
                                            rectangles = rectangles,
                                            originalDisplayBitmap = originalDisplayBitmap,
                                            speechRate = speechRate,
                                            detectedTtsLocale = detectedTtsLocale,
                                            onSpeechStateChange = { newState -> isSpeaking = newState },
                                            onLocaleDetected = { locale -> detectedTtsLocale = locale },
                                            onPageAdvanceReset = { pageAdvanceTriggered = false },
                                            onTextProcessed = { text -> lastSpokenText = text },
                                            onSetOcrLu = { OCR_lu = true },
                                            preGrayTTSAdjust = preGrayTTSAdjust,
                                            onOcrEmptyWarning = { showOcrEmptyWarning = it }
                                        )
                                    }
                                }
                            }
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.Red,        
                            uncheckedColor = Color.Red,      
                            checkmarkColor = Color.White     
                        )
                    )

                    Checkbox(
                        checked = all_selected,
                        onCheckedChange = { isChecked ->
                            all_selected = isChecked
                            if (isChecked) {
                                
                                rectangles.clear()
                                
                                originalDisplayBitmap?.let { bmp ->
                                    val width = (bmp.width * (customRectWidth / 100f)).toInt().coerceIn(10, bmp.width)
                                    val height = (bmp.height * (customRectHeight / 100f)).toInt().coerceIn(10, bmp.height)

                                    val left = (bmp.width - width) / 2
                                    val top = (bmp.height - height) / 2

                                    fullPageRect = android.graphics.Rect(left, top, left + width, top + height)
                                    rectangles.add(fullPageRect!!)
                                    selectedRectIndices = setOf(0)
                                }
                            } else {
                                
                                rectangles.clear()
                                selectedRectIndices = emptySet()
                                fullPageRect = null
                            }
                            OCR_lu = false
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF006400),  
                            uncheckedColor = Color(0xFF006400),
                            checkmarkColor = Color.White
                        )
                    )

                    Checkbox(
                        checked = no_squares,
                        onCheckedChange = { no_squares = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.Blue,        
                            uncheckedColor = Color.Blue,      
                            checkmarkColor = Color.White      
                        )
                    )

                }

            }

        }

        if (recognizedText.isNotBlank() &&
            recognizedText != "Sélectionne les zones à garder, puis appuie sur le bouton."
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            )
            {

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = recognizedText,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = {
                        if (recognizedText.isNotBlank()) {
                            lastSpokenText = recognizedText
                            speakLongText(tts, recognizedText)
                            isSpeaking = true
                        }
                    }) {
                        Text("Play")
                    }

                    Button(onClick = {
                        tts?.stop()
                        isSpeaking = false
                    }) {
                        Text("Pause")
                    }

                    Button(onClick = {
                        tts?.stop()
                        isSpeaking = false
                        recognizedText = ""   

                    }) {
                        Text("Retour")
                    }

                }
            }

        }

    }

    if (showOcrEmptyWarning) {
        AlertDialog(
            onDismissRequest = { showOcrEmptyWarning = false },
            title = { Text("OCR vide") },
            text = {
                Text("Le réglage ROUGE 'Pré-traitement gris pour le TTS' doit être ajusté.\n\nEssayez une valeur entre -1 et 2.\n Commencez par 0.")
            },
            confirmButton = {
                Button(onClick = { showOcrEmptyWarning = false }) {
                    Text("OK")
                }
            }
        )
    }

}