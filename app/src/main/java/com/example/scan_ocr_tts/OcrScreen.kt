package com.example.scan_ocr_tts

import android.content.Context

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults

import androidx.exifinterface.media.ExifInterface
import android.graphics.Matrix

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Tonality

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.io.File
import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.ui.unit.dp

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp

import java.util.Locale


import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    imageFile: File,
    pdfIdentity: String,   // 👈 AJOUT
    thresholdBias: Float,
    onThresholdChange: (Float) -> Unit,
    rectPadding: Float,
    onRectPaddingChange: (Float) -> Unit,
    contrastBoost: Float,
    speechRate: Float,
    onSpeechRateChange: (Float) -> Unit,
    onContrastBoostChange: (Float) -> Unit,

    onNext: () -> Unit,
    onLeavingScreen: () -> Unit,


    onPreviousPage: (() -> Unit)? = null,
    onNextPage: (() -> Unit)? = null,
    onGoToPage: ((Int) -> Unit)? = null,
    currentPageIndex: Int = 0,
    totalPages: Int = 1
)


{
    var recognizedText by remember { mutableStateOf("") }

    val context = LocalContext.current

    val prefs = context.applicationContext.getSharedPreferences("ocr_settings", Context.MODE_PRIVATE)

    val currentPdfPath = pdfIdentity

    var ttsAlreadyFinished by remember { mutableStateOf(false) }
    var pageAdvanceTriggered by remember { mutableStateOf(false) }

    val savedPdfPath = prefs.getString("lastPdfPath", null)
    val savedPage = prefs.getInt("lastPdfPage", 0)

    var lastRestoredPdf by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(pdfIdentity) {
        if (lastRestoredPdf != pdfIdentity) {

            val savedPdfPath = prefs.getString("lastPdfPath", null)
            val savedPage = prefs.getInt("lastPdfPage", 0)

            Log.d("PAGE_TRACE", "RESTORE check: pdfIdentity=$pdfIdentity savedPdfPath=$savedPdfPath savedPage=$savedPage")

            if (savedPdfPath == pdfIdentity) {
                Log.d("PAGE_TRACE", "RESTORE goTo savedPage=$savedPage")
                onGoToPage?.invoke(savedPage)
            } else {
                Log.d("PAGE_TRACE", "RESTORE new PDF → page 0")
                onGoToPage?.invoke(0)
            }

            lastRestoredPdf = pdfIdentity
        }
    }




    var contrastBoostMode by remember { mutableStateOf(false) }


    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalDisplayBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var rectangles by remember { mutableStateOf<MutableList<android.graphics.Rect>>(mutableListOf()) }

    var showTextScreen by remember { mutableStateOf(false) }


    var lastSpokenText by remember { mutableStateOf("") }

    var showControls by rememberSaveable { mutableStateOf(false) }
    var showProcessed by rememberSaveable { mutableStateOf(false) }


    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }

    // var speechRate by rememberSaveable { mutableStateOf(prefs.getFloat("speechRate", 1.0f)) }
    var detectedTtsLocale by remember { mutableStateOf<Locale?>(null) }

    LaunchedEffect(speechRate) {
        tts?.setSpeechRate(speechRate)
    }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.FRANCE
                tts?.setSpeechRate(speechRate)
//                tts?.setSpeechRate(1.0f)
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        if (!pageAdvanceTriggered) {
                            pageAdvanceTriggered = true
                            isSpeaking = false
                            onNextPage?.invoke()
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



    var textBlocks by remember { mutableStateOf<List<com.google.mlkit.vision.text.Text.TextBlock>>(emptyList()) }
//    var disabledBlocks by remember { mutableStateOf(setOf<Int>()) }
    var selectedRectIndices by remember { mutableStateOf(setOf<Int>()) }




    LaunchedEffect(imageFile, thresholdBias, rectPadding, contrastBoost, contrastBoostMode)
    {


    Log.d("PDF_DEBUG", "OcrScreen reçoit imageFile = ${imageFile.absolutePath}")

        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)

        Log.d("PDF_DEBUG", "Bitmap chargé: ${bitmap.width}x${bitmap.height}")
        val safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        originalDisplayBitmap = originalBitmap

// 👉 NOUVEAU : passage en niveaux de gris avec OpenCV
        val effectiveContrast = contrastBoost * (if (contrastBoostMode) 1.35f else 1.0f)
        Log.d("BOOST_DEBUG", "contrastBoost=$contrastBoost  boostMode=$contrastBoostMode  effective=$effectiveContrast")

        val contrastBitmap = ImageProcessing.adjustContrast(originalBitmap, effectiveContrast)


        // val effectiveThreshold = if (contrastBoostMode) thresholdBias * 0.65f else thresholdBias

        val (processedBitmap, detectedRects) = ImageProcessing.toAdaptiveThreshold(
            contrastBitmap,
            thresholdBias,  // 👈 Utiliser thresholdBias comme seuil de blanc (20-80%)
            contrastBoost
        )

        val boostedBitmap = if (contrastBoostMode) {
            ImageProcessing.strengthenText(processedBitmap)
        } else {
            processedBitmap
        }


        rectangles = detectedRects.map {
            val pad = rectPadding.toInt()
            android.graphics.Rect(
                it.x - pad,
                it.y - pad,
                it.x + it.width + pad,
                it.y + it.height + pad
            )
        }.toMutableList()
        selectedRectIndices = rectangles.indices.toSet()

        displayBitmap = boostedBitmap

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(processedBitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                Log.d("NAV_DEBUG", "OCR blocs détectés: ${visionText.textBlocks.size}")
                textBlocks = visionText.textBlocks
                recognizedText = "Sélectionne les zones à garder, puis appuie sur le bouton."
            }
            .addOnFailureListener { e ->
                Log.e("OCR_DEBUG", "Erreur OCR", e)
                recognizedText = "Erreur lors de la reconnaissance."
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
                    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }


                    Text(
                        text = "${currentPageIndex + 1} / $totalPages",
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






                    Spacer(modifier = Modifier.weight(1f))


                        IconButton(onClick = { contrastBoostMode = !contrastBoostMode }) {
                            Icon(
                                imageVector = Icons.Default.Tonality, // icône réglages/boost
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



                    Spacer(modifier = Modifier.weight(1f))

                    Row(verticalAlignment = Alignment.CenterVertically) {

                        IconButton(onClick = { showProcessed = !showProcessed }) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "Basculer image traitée",
                                tint = if (showProcessed) Color.Red else Color.White
                            )
                        }

                        IconButton(onClick = { showTextScreen = true }) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = "Afficher le texte OCR",
                                tint = if (showProcessed) Color.Red else Color.White
                            )
                        }

                        if (showTextScreen) {
                            AlertDialog(
                                onDismissRequest = { showTextScreen = false },
                                title = { Text("Texte OCR") },
                                text = {
                                    Text(
                                        if (lastSpokenText.isNotBlank()) lastSpokenText
                                        else "Utilisez d'abord le bouton OCR"
                                    )
                                },
                                confirmButton = {
                                    Button(onClick = { showTextScreen = false }) {
                                        Text("OK")
                                    }
                                }
                            )
                        }


                        IconButton(onClick = {
                            if (isSpeaking) {
                                tts?.stop()
                                isSpeaking = false
                            } else {
                                if (lastSpokenText.isNotBlank()) {
                                    speakLongText(tts, lastSpokenText)
                                    isSpeaking = true
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Lecture OCR",
                                tint = if (isSpeaking) Color.Green else Color.White
                            )
                        }


                        IconButton(onClick = {
                            val pdfKey = imageFile.parentFile?.name ?: "defaultPdf"

                            prefs.edit()
                                .putFloat("thresholdBias", thresholdBias)
                                .putFloat("rectPadding", rectPadding)
                                .putFloat("contrastBoost", contrastBoost)
                                .putFloat("speechRate", speechRate)
                                .putString("lastPdfPath", currentPdfPath)
                                .putInt("lastPdfPage", currentPageIndex)
                                .apply()

                            onNext()
                        })
                         {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Accueil"
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))




                    }


                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
            modifier = Modifier
                .statusBarsPadding()
                .height(36.dp)

        )
        Box(modifier = Modifier.fillMaxSize()) {
            val showResult = recognizedText.isNotBlank() &&
                    recognizedText != "Sélectionne les zones à garder, puis appuie sur le bouton."

//            val showResult = false

            if (!showResult) {

                val bmpToShow = if (showProcessed) displayBitmap else originalDisplayBitmap

                bmpToShow?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )


                    // ✅ IMPORTANT : le Canvas ne doit exister QUE si bmp existe
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

                                        // Vérifier si l'utilisateur a cliqué sur ce rectangle
                                        if (tapOffset.x in left..right && tapOffset.y in top..bottom) {
                                            // Sélectionner ou désélectionner le rectangle
                                            selectedRectIndices =
                                                if (selectedRectIndices.contains(index)) {
                                                    selectedRectIndices - index  // Désélectionner
                                                } else {
                                                    selectedRectIndices + index  // Sélectionner
                                                }

                                            // Log pour vérifier si le rectangle a été sélectionné/désélectionné
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

                            // On agrandit un peu les rectangles sélectionnés pour qu'ils dépassent du texte
                            val padding = if (isSelected) 6f else 0f

                            val drawLeft = left - padding
                            val drawTop = top - padding
                            val drawRight = right + padding
                            val drawBottom = bottom + padding

                            if (isSelected) {
                                // Fond bien visible
                                drawRect(
                                    color = Color.Yellow.copy(alpha = 0.35f),
                                    topLeft = Offset(drawLeft, drawTop),
                                    size = Size(drawRight - drawLeft, drawBottom - drawTop)
                                )
                            }

                            // Contour
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

//                Text(
//                    text = "Sensibilité détection : ${thresholdBias.toInt()}",
//                    color = Color.White
//                )


                if (showControls) {
                    Column {

                        Text(
                            text = "Seuil blanc min : ${thresholdBias.toInt()}",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D47A1))
                                .padding(vertical = 2.dp, horizontal = 8.dp)
                        )

                        Slider(
                            value = thresholdBias,
                            onValueChange = onThresholdChange,
                            valueRange = 20f..50f,  // 👈 Changer la plage (pourcentage de blanc)
                            steps = 12,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .height(24.dp)
                        )


                Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Marge cadre : ${rectPadding.toInt()} px",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D47A1))
                                .padding(vertical = 2.dp, horizontal = 8.dp)
                        )

                Slider(
                    value = rectPadding,
                    onValueChange = onRectPaddingChange,
                    valueRange = 0f..18f,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .height(24.dp)   // ← réduit la hauteur visuelle
                )

                Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sensibilité texte : ${"%.2f".format(contrastBoost)}",

                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D47A1))
                                .padding(vertical = 2.dp, horizontal = 8.dp)
                        )

                Slider(
                    value = contrastBoost,
                    onValueChange = onContrastBoostChange,
                    valueRange = 0.9f..1.35f,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .height(24.dp)   // ← réduit la hauteur visuelle
                )

                Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Vitesse de lecture : ${"%.2f".format(speechRate)}x",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D47A1))
                                .padding(vertical = 2.dp, horizontal = 8.dp)
                        )

                Slider(
                    value = speechRate,
                    onValueChange = onSpeechRateChange,
                    valueRange = 0.5f..1.5f,
                    steps = 19,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .height(24.dp)   // ← réduit la hauteur visuelle
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

                        // ⬅ PAGE PRÉCÉDENTE
                        Button(
                            onClick = { onPreviousPage?.invoke() },
                            enabled = currentPageIndex > 0 && onPreviousPage != null
                        ) {
                            Text("<")
                        }

                        // 🔍 BOUTON OCR
                        // 🔍 BOUTON OCR
                        Button(
                            onClick = {
                                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                                val verticalTolerance = 60

//                                val selectedRects = selectedRectIndices
//                                    .map { rectangles[it] }
//                                    // .sortedWith(compareBy<Rect> { it.left }.thenBy { it.top })
//                                    .sortedWith { r1, r2 ->
//                                        val dy = kotlin.math.abs(r1.top - r2.top)
//
//                                        if (dy <= verticalTolerance) {
//                                            // Même ligne approximative → gauche à droite
//                                            r1.left - r2.left
//                                        } else {
//                                            // Lignes différentes → haut à bas
//                                            r1.top - r2.top
//                                        }
//                                    }
                                val horizontalTolerance = 40  // ou 50 selon ton image
                                val selectedRects = selectedRectIndices
                                    .map { rectangles[it] }
                                    .sortedWith { r1, r2 ->
                                        val dx = kotlin.math.abs(r1.left - r2.left)

                                        if (dx <= horizontalTolerance) {
                                            // Même colonne approximative → haut à bas
                                            r1.top - r2.top
                                        } else {
                                            // Colonnes différentes → gauche à droite
                                            r1.left - r2.left
                                        }
                                    }


                                if (originalDisplayBitmap == null || selectedRects.isEmpty()) return@Button

                                var pending = selectedRects.size
                                val collectedText = StringBuilder()

                                selectedRects.forEach { rect ->

                                    val safeLeft = rect.left.coerceAtLeast(0)
                                    val safeTop = rect.top.coerceAtLeast(0)
                                    val safeWidth = rect.width().coerceAtMost(originalDisplayBitmap!!.width - safeLeft)
                                    val safeHeight = rect.height().coerceAtMost(originalDisplayBitmap!!.height - safeTop)

                                    if (safeWidth <= 0 || safeHeight <= 0) {
                                        pending--
                                        return@forEach
                                    }

                                    val cropped = Bitmap.createBitmap(
                                        originalDisplayBitmap!!,
                                        safeLeft,
                                        safeTop,
                                        safeWidth,
                                        safeHeight
                                    )

                                    val image = InputImage.fromBitmap(cropped, 0)

                                    recognizer.process(image)
                                        .addOnSuccessListener { visionText ->
                                            collectedText.appendLine(visionText.text)
                                            pending--
                                            if (pending == 0) {
                                                val finalText = cleanOcrTextForTts(collectedText.toString())

                                                if (finalText.isNotBlank()) {
                                                    lastSpokenText = finalText

                                                    if (detectedTtsLocale == null) {
                                                        detectLanguageAndSetTts(finalText, tts) { locale ->
                                                            detectedTtsLocale = locale
                                                            ttsAlreadyFinished = false   // 🔹 RESET ICI
                                                            speakLongText(tts, finalText)
                                                            isSpeaking = true
                                                            pageAdvanceTriggered = false
                                                        }
                                                    } else {
                                                        tts?.language = detectedTtsLocale
                                                        ttsAlreadyFinished = false   // 🔹 RESET ICI
                                                        speakLongText(tts, finalText)
                                                        isSpeaking = true
                                                        pageAdvanceTriggered = false
                                                    }
                                                }

                                            }
                                        }
                                        .addOnFailureListener {
                                            pending--
                                        }
                                }
                            }
                        ) {
                            Text("OCR")
                        }



                        // ➡ PAGE SUIVANTE
                        Button(
                            onClick = { onNextPage?.invoke() },
                            enabled = currentPageIndex < totalPages - 1 && onNextPage != null
                        ) {
                            Text(">")
                        }
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

                    // 📜 Texte OCR scrollable
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

                    // 🎛️ Barre de boutons TTS
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
                            recognizedText = ""   // ⬅ retour à l’écran PDF avec sélection

                        }) {
                            Text("Retour")
                        }

                    }
                }






            }




        }

    }





    fun loadCorrectlyOrientedBitmap(path: String): Bitmap {
        val bitmap = BitmapFactory.decodeFile(path)

        val exif = ExifInterface(path)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
    }

fun cleanOcrTextForTts(raw: String): String {
    return raw
        // Ligatures typographiques souvent mal lues
        .replace("ﬁ", "fi")
        .replace("ﬂ", "fl")

        // Mots coupés en fin de ligne (ex: "exem-\nple")
        .replace(Regex("(?<=\\p{L})-\\s*\\n\\s*(?=\\p{L})"), "")

        // 1️⃣ REPÉRAGE DES MOTS EN MAJUSCULES QUI SE SUIVENT
        .replace(Regex("\\b([A-ZÉÈÀÂÔÎÏÊÇŒ]{2,})(\\s+[A-ZÉÈÀÂÔÎÏÊÇŒ]{2,})+\\b")) { match ->
            val text = match.value
            // Ajouter un point à la fin si pas déjà de ponctuation
            if (text.last().isLetterOrDigit()) "$text." else text
        }


        // Sauts de ligne au milieu des phrases → espace
        .replace(Regex("(?<![.!?])\\n"), " ")

        // Espaces multiples
        .replace(Regex("\\s+"), " ")

        // Espaces avant ponctuation supprimés
        .replace(Regex("\\s+([,.!?;:])"), "$1")

        // Espace propre après ponctuation
        .replace(Regex("([,.!?;:])(\\p{L})"), "$1 $2")

        // Guillemets français mal espacés
        .replace(Regex("«\\s+"), "« ")
        .replace(Regex("\\s+»"), " »")

        // Apostrophes OCR foireuses
        .replace("’", "'")
        .replace("`", "'")

        // Cas très courant : "l es", "d es", "qu i"
        .replace(Regex("\\b([ldjmstcq])\\s+(?=[aeiouh])"), "$1'")

        // Conversion des siècles en chiffres arabes pour le TTS
        // Conversion des siècles (XIXe / XIXᵉ / XIX° / XIXº) -> 19e pour le TTS
        // Conversion robuste des siècles avant "siècle"
        // Détection très tolérante d’un bloc avant "siècle"
        .replace(Regex("\\b([A-Za-z*]{2,8})\\s*(?:e|ᵉ|°|º)?\\s+siècle\\b", RegexOption.IGNORE_CASE)) { m ->
            val raw = m.groupValues[1]

            Log.d("NAV_DEBUG", "siècle brut détecté = $raw")

            val roman = raw
                .replace('1', 'I')
                .replace('l', 'I')
                .replace('v', 'V')
                .replace('u', 'V')
                .replace('r', 'I')
                .replace("*", "")
                .uppercase()

            val n = romanToInt(roman)

            Log.d("NAV_DEBUG", "siècle normalisé = $roman → $n")

            if (n in 1..50) "${n}e siècle" else m.value
        }

        .also { Log.d("NAV_DEBUG", "APRES SIECLES = $it") }

        // 2️⃣ CONVERSION EN MINUSCULES (avant le trim final)
        .lowercase(Locale.getDefault())

        // Nettoyage final
        .trim()
}

fun romanToInt(roman: String): Int {
    val map = mapOf(
        'I' to 1, 'V' to 5, 'X' to 10,
        'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000
    )

    var total = 0
    var prev = 0

    for (c in roman.reversed()) {
        val value = map[c] ?: return 0
        if (value < prev) total -= value else total += value
        prev = value
    }
    return total
}
fun detectLanguageAndSetTts(
    text: String,
    tts: TextToSpeech?,
    onDetected: (Locale) -> Unit
) {
    val identifier = com.google.mlkit.nl.languageid.LanguageIdentification.getClient()

    identifier.identifyLanguage(text)
        .addOnSuccessListener { languageCode ->
            val locale = when (languageCode) {
                "fr" -> Locale.FRENCH
                "es" -> Locale("es", "ES")
                else -> Locale.FRENCH // langue par défaut
            }

            tts?.language = locale
            onDetected(locale)
        }
        .addOnFailureListener {
            tts?.language = Locale.FRENCH
            onDetected(Locale.FRENCH)
        }
}

//fun speakLongText(tts: TextToSpeech?, text: String) {
//    if (tts == null) return
//
//    val maxLength = 3500  // sécurité sous la limite Android (~4000)
//
//    val parts = text.chunked(maxLength)
//
//    tts.stop()
//
//    parts.forEachIndexed { index, part ->
//        tts.speak(
//            part,
//
//            if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
//            null,
//            "OCR_PART_$index"
//        )
//    }
//}



fun speakLongText(tts: TextToSpeech?, text: String) {
    if (tts == null) return

    val maxLength = 3500
    val parts = text.chunked(maxLength)

    tts.stop()

    parts.forEachIndexed { index, part ->
        val safePart =
            if (index == 0) "\u200B\u200B\u200B$part" else part // 3 zero-width spaces

        tts.speak(
            safePart,
            if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            null,
            "OCR_PART_$index"
        )
    }
}
