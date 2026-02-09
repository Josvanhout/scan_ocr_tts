package com.example.scan_ocr_tts


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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DensityMedium
import androidx.compose.material.icons.filled.Home

import androidx.compose.material.icons.filled.ScreenRotation

import androidx.compose.material.icons.filled.Tonality
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
//    onLeavingScreen: () -> Unit,


    onPreviousPage: (() -> Unit)? = null,
    onNextPage: (() -> Unit)? = null,
    onGoToPage: ((Int) -> Unit)? = null,
    currentPageIndex: Int = 0,
    totalPages: Int = 1,
    useHighRes: Boolean,  // ← NOUVEAU paramètre
    onUseHighResChange: (Boolean) -> Unit  // ← NOUVEAU paramètre

) {

    // var scaleFactorEnabled by remember { mutableStateOf(false) }
    var showOcrEmptyWarning by remember { mutableStateOf(false) }

    var recognizedText by remember { mutableStateOf("") }

    val context = LocalContext.current

    // 👇 AJOUTER CES LIGNES ICI
    // Extraire le nom du fichier PDF à partir du chemin
    val pdfFileName by remember(pdfIdentity) {
        mutableStateOf(
            try {
                // Décoder les caractères URL (%2F, %20, etc.)
                val decodedPath = java.net.URLDecoder.decode(pdfIdentity, "UTF-8")
                // Extraire juste le nom du fichier
                File(decodedPath).nameWithoutExtension.ifEmpty { "Document PDF" }
            } catch (e: Exception) {
                // Fallback : prendre la dernière partie du chemin
                pdfIdentity.substringAfterLast("/").substringBeforeLast(".")
                    .ifEmpty { "Document PDF" }
            }
        )
    }


    val prefs =
        context.applicationContext.getSharedPreferences("ocr_settings", Context.MODE_PRIVATE)

    val currentPdfPath = pdfIdentity

    var minWidthRatio by rememberSaveable { mutableStateOf(0.15f) } // Valeur initiale = 15%
    val initialPreGrayAdjust = prefs.getFloat("preGrayAdjust", 0.0f)
    var preGrayAdjust by rememberSaveable { mutableStateOf(initialPreGrayAdjust) }
    var preGrayTTSAdjust by rememberSaveable { mutableStateOf(initialPreGrayAdjust) }


    var ttsAlreadyFinished by remember { mutableStateOf(false) }
    var pageAdvanceTriggered by remember { mutableStateOf(false) }


    val savedPdfPath = prefs.getString("lastPdfPath", null)
    val savedPage = prefs.getInt("lastPdfPage", 0)
    val savedMinWidthRatio = prefs.getFloat("minWidthRatio", 0.15f)
    val savedPreGrayAdjust = prefs.getFloat("preGrayAdjust", 0.0f)

    // Variables pour le cache OCR (au début de la fonction OcrScreen)
    var OCR_lu by remember { mutableStateOf(false) }


    var lastRestoredPdf by rememberSaveable { mutableStateOf<String?>(null) }

    // NOUVELLES VARIABLES
//    var lignes_tts by remember { mutableStateOf<List<String>>(emptyList()) }
//    var index_lise_tts by remember { mutableStateOf(0) }
//    var pause_tts by remember { mutableStateOf(false) }


    LaunchedEffect(pdfIdentity) {
        if (lastRestoredPdf != pdfIdentity) {
            Log.d("BOOKMARK_DEBUG", "=== DÉBUT RESTAURATION ===")
            Log.d("BOOKMARK_DEBUG", "pdfIdentity: $pdfIdentity")

            // Restauration depuis le JSON
            val bookmarkData = getBookmarkFromJson(context, pdfIdentity)
            val savedPdfPath = bookmarkData["pdfPath"] ?: prefs.getString("lastPdfPath", null)
            val savedPage =
                if (savedPdfPath == pdfIdentity) bookmarkData["pageIndex"]?.toIntOrNull()
                    ?: 0 else 0

            Log.d("BOOKMARK_DEBUG", "savedPdfPath: $savedPdfPath")
            Log.d("BOOKMARK_DEBUG", "savedPage: $savedPage")
            Log.d(
                "BOOKMARK_DEBUG",
                "Comparison: savedPdfPath == pdfIdentity? ${savedPdfPath == pdfIdentity}"
            )

            if (savedPdfPath == pdfIdentity) {
                // Appliquer les réglages sauvegardés
                bookmarkData["thresholdBias"]?.toFloatOrNull()?.let(onThresholdChange)
                bookmarkData["rectPadding"]?.toFloatOrNull()?.let(onRectPaddingChange)
                bookmarkData["contrastBoost"]?.toFloatOrNull()?.let(onContrastBoostChange)
                bookmarkData["speechRate"]?.toFloatOrNull()?.let(onSpeechRateChange)
                minWidthRatio = bookmarkData["minWidthRatio"]?.toFloatOrNull() ?: 0.15f
                preGrayAdjust = bookmarkData["preGrayAdjust"]?.toFloatOrNull() ?: 0.0f
                preGrayTTSAdjust = bookmarkData["preGrayTTSAdjust"]?.toFloatOrNull() ?: 0.0f

                Log.d("BOOKMARK_DEBUG", "✓ RESTAURATION: Aller à page $savedPage")
                onGoToPage?.invoke(savedPage)
            } else {
                Log.d("BOOKMARK_DEBUG", "✗ NOUVEAU PDF: Aller à page 0")
                onGoToPage?.invoke(0)
            }

            lastRestoredPdf = pdfIdentity
            Log.d("BOOKMARK_DEBUG", "=== FIN RESTAURATION ===")
        }
    }


    var contrastBoostMode by remember { mutableStateOf(true) }


    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalDisplayBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var rectangles by remember { mutableStateOf<MutableList<android.graphics.Rect>>(mutableListOf()) }

    var showTextScreen by remember { mutableStateOf(false) }


    var lastSpokenText by remember { mutableStateOf("") }

    var showControls by rememberSaveable { mutableStateOf(false) }
    var showControls2 by rememberSaveable { mutableStateOf(false) }
    var showProcessed by rememberSaveable { mutableStateOf(false) }

    var autoPlayEnabled by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }

    // var speechRate by rememberSaveable { mutableStateOf(prefs.getFloat("speechRate", 1.0f)) }
    var detectedTtsLocale by remember { mutableStateOf<Locale?>(null) }

    //    var disabledBlocks by remember { mutableStateOf(setOf<Int>()) }
    var selectedRectIndices by remember { mutableStateOf(setOf<Int>()) }

    // Fonction locale pour désélectionner les rectangles
    val handleRectanglesDeselection = {
        selectedRectIndices = emptySet()
    }

    // Fonction locale pour inverser la selection les rectangles
    val toggleRectanglesSelection = {
        // Inverser la sélection : ceux qui étaient sélectionnés deviennent non-sélectionnés et vice-versa
        selectedRectIndices = if (selectedRectIndices.size == rectangles.size) {
            // Si tout est sélectionné, tout désélectionner
            emptySet()
        } else {
            // Sinon, inverser la sélection
            rectangles.indices.toSet() - selectedRectIndices
        }
        OCR_lu = false  // Forcer re-OCR après changement de sélection
    }


    LaunchedEffect(speechRate) {
        tts?.setSpeechRate(speechRate)
    }

    LaunchedEffect(preGrayTTSAdjust) {
        OCR_lu = false  // ← FORCER le re-OCR quand preGrayTTSAdjust change
        Log.d("PRE_GRAY_TTS", "preGrayTTSAdjust changé, OCR_lu réinitialisé")
    }

    DisposableEffect(Unit) {


        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.FRANCE
                tts?.setSpeechRate(speechRate)


                tts?.setOnUtteranceProgressListener(object :
                    android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        Log.d("TTS_DEBUG", "onDone: $utteranceId")

                        if (utteranceId == "FINAL_PART") {
                            Log.d("TTS_DEBUG", "Lecture totale terminée, bascule sur le thread UI...")

                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                // AJOUT DE ?.invoke() pour corriger l'erreur de l'image 2
                                isSpeaking = false
                                if (!autoPlayEnabled) {
                                    // Mode lecture continue : avancer à la page suivante
                                    onNextPage?.invoke()

// en cours
                                } else {
                                    // Mode manuel : NE PAS avancer, mais décocher la case
                                    autoPlayEnabled = false
                                    // handleRectanglesDeselection()
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
        mutableStateOf<List<com.google.mlkit.vision.text.Text.TextBlock>>(
            emptyList()
        )
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
        OCR_lu = false

        Log.d("PDF_DEBUG", "OcrScreen reçoit imageFile = ${imageFile.absolutePath}")

        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)

        Log.d("PDF_DEBUG", "Bitmap chargé: ${bitmap.width}x${bitmap.height}")
        val safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        originalDisplayBitmap = originalBitmap

// 👉 NOUVEAU : passage en niveaux de gris avec OpenCV
        val effectiveContrast = contrastBoost * (if (contrastBoostMode) 1.35f else 1.0f)
        Log.d(
            "BOOST_DEBUG",
            "contrastBoost=$contrastBoost  boostMode=$contrastBoostMode  effective=$effectiveContrast"
        )

        val contrastBitmap = ImageProcessing.adjustContrast(originalBitmap, effectiveContrast)


        // val effectiveThreshold = if (contrastBoostMode) thresholdBias * 0.65f else thresholdBias

        val (processedBitmap, detectedRects) = ImageProcessing.toAdaptiveThreshold(
            contrastBitmap,
            thresholdBias,
            contrastBoost,
            preGrayAdjust,
            minWidthRatio  // <-- AJOUTER CE PARAMÈTRE
        )
        Log.d("PRE_GRAY_CALL", "Appel toAdaptiveThreshold avec preGrayAdjust = $preGrayAdjust")

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

                // ← CORRECTION ICI : utiliser handleTtsButtonClick pour autoPlay

            }
            .addOnFailureListener { e ->
                Log.e("OCR_DEBUG", "Erreur OCR", e)
                recognizedText = "Erreur lors de la reconnaissance."
            }


    }


// TOOLBAR
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

// Compteur pages
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


//                    Spacer(modifier = Modifier.width(8.dp))
//
//                    Spacer(modifier = Modifier.weight(1f))

// Sortie du fichier JSON dans logcat

//                    IconButton(onClick = {
//                        val file = File(
//                            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
//                            "bookmarks.json"
//                        )
//                        val content = if (file.exists()) file.readText() else "Fichier vide"
//                        Log.d("JSON_VIEWER", "Contenu JSON:\n$content")
//                    }) {
//                        Icon(
//                            imageVector = Icons.Default.Screenshot,
//                            contentDescription = "Log JSON",
//                            tint = Color.White
//                        )
//                    }

// Rotation de l'écran
                    FlipScreenButton()

// Contraste auto
                    IconButton(onClick = { contrastBoostMode = !contrastBoostMode }) {
                        Icon(
                            imageVector = Icons.Default.Tonality,
                            contentDescription = "Boost contraste",
                            tint = if (contrastBoostMode) Color.Red else Color.White
                        )
                    }

// Montre les curseurs
                    IconButton(onClick = { showControls = !showControls }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Afficher les réglages",
                            tint = if (showControls) Color.Red else Color.White
                        )
                    }
                    // News sliders
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

// Bouton pour voir le texte
//                        IconButton(onClick = { showTextScreen = true }) {
//                            Icon(
//                                imageVector = Icons.Default.School,
//                                contentDescription = "Afficher le texte OCR",
//                                tint = if (showProcessed) Color.Red else Color.White
//                            )
//                        }
//
//                        if (showTextScreen) {
//                            AlertDialog(
//                                onDismissRequest = { showTextScreen = false },
//                                title = { Text("Texte OCR") },
//                                text = {
//                                    Text(
//                                        if (lastSpokenText.isNotBlank()) lastSpokenText
//                                        else "Utilisez d'abord le bouton TTS"
//                                    )
//                                },
//                                confirmButton = {
//                                    Button(onClick = { showTextScreen = false }) {
//                                        Text("OK")
//                                    }
//                                }
//                            )
//                        }





// Sliders visibles
                        IconButton(onClick = {
                            val pdfKey = imageFile.parentFile?.name ?: "defaultPdf"

                            // 1. Sauvegarde SharedPreferences (existant)
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

                            // 2. NOUVEAU : Sauvegarde JSON
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
                                preGrayTTSAdjust = String.format(Locale.US, "%.2f", preGrayTTSAdjust).toFloat()
                            )

                            onNext()  // ← RETOUR À L'ACCUEIL
                        }) {
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

// Label nom du pdf
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D47A1)) // Couleur de fond bleue
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
                                            OCR_lu = false
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


// SLIDERS2
                if (showControls2) {
                    Column {
                        // Checkbox et label
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
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
                                "High-resolution PDF (scaleFactor 1.5)",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Pré-traitement gris pour le tts
                        Text(
                            text = "Gray preprocessing for TTS : ${"%.2f".format(preGrayTTSAdjust)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFB71C1C))
                                .padding(vertical = 2.dp, horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Slider(
                            value = preGrayTTSAdjust,
                            onValueChange = { preGrayTTSAdjust = it },
                            valueRange = -1.0f..2.0f,
                            steps = 20,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(24.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Marge des cadres
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

                        // Reading speed
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
                }


// SLIDERS1
                if (showControls) {
                    Column {

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
//                            Checkbox(
//                                checked = scaleFactorEnabled,
//                                onCheckedChange = { scaleFactorEnabled = it },
//                                colors = CheckboxDefaults.colors(
//                                    checkedColor = Color.Black,
//                                    uncheckedColor = Color.Black,
//                                    checkmarkColor = Color.White
//                                )
//                            )
//                            Text(
//                                "High-resolution PDF (scaleFactor 1.5)",
//                                color = Color.Black,
//                                fontWeight = FontWeight.Bold
//                            )
                        }

// Pré-traitement gris
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

//Seuil blanc minimum

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
                            valueRange = 0f..100f,  // 👈 Changer la plage (pourcentage de blanc)
                            steps = 19,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .height(24.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

// Sensibilité au texte
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
                                .height(24.dp)   // ← réduit la hauteur visuelle
                        )

                        Spacer(modifier = Modifier.height(8.dp))




//Largeur min des colonnes
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
                                // Optionnel : relancer le traitement ici ou via un bouton
                            },
                            valueRange = 0.05f..0.5f,
                            steps = 45,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .height(24.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

//                        // Pré-traitement gris pour le tts
//                        Text(
//                            text = "Gray preprocessing for TTS : ${"%.2f".format(preGrayTTSAdjust)}",
//                            color = Color.White,
//                            fontSize = 12.sp,
//                            fontWeight = FontWeight.ExtraBold,
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .background(Color(0xFFB71C1C))
//                                .padding(vertical = 2.dp, horizontal = 8.dp)
//                        )
//
//                        Spacer(modifier = Modifier.height(8.dp))

//                        Slider(
//                            value = preGrayTTSAdjust,
//                            onValueChange = { preGrayTTSAdjust = it },
//                            valueRange = -1.0f..2.0f,
//                            steps = 20,
//                            modifier = Modifier
//                                .padding(horizontal = 16.dp)
//                                .height(24.dp)
//                        )
//
//                        Spacer(modifier = Modifier.height(8.dp))

////Marge des cadres
//                        Text(
//                            text = "Frame margin : ${rectPadding.toInt()} px",
//                            color = Color.White,
//                            fontSize = 12.sp,
//                            fontWeight = FontWeight.ExtraBold,
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .background(Color(0xFF2E7D32))
//                                .padding(vertical = 2.dp, horizontal = 8.dp)
//                        )
//
//                        Slider(
//                            value = rectPadding,
//                            onValueChange = onRectPaddingChange,
//                            valueRange = 0f..18f,
//                            modifier = Modifier
//                                .padding(horizontal = 16.dp)
//                                .height(24.dp)   // ← réduit la hauteur visuelle
//                        )
//
//                        Spacer(modifier = Modifier.height(8.dp))

// Vitesse de lecture


//                        Text(
//                            text = "Reading speed : ${"%.2f".format(speechRate)}x",
//                            color = Color.White,
//                            fontSize = 12.sp,
//                            fontWeight = FontWeight.ExtraBold,
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .background(Color(0xFF2E7D32))
//                                .padding(vertical = 2.dp, horizontal = 8.dp)
//                        )
//
//                        Slider(
//                            value = speechRate,
//                            onValueChange = onSpeechRateChange,
//                            valueRange = 0.5f..1.5f,
//                            steps = 19,
//                            modifier = Modifier
//                                .padding(horizontal = 16.dp)
//                                .height(24.dp)   // ← réduit la hauteur visuelle
//                        )
//
//                        Spacer(modifier = Modifier.height(8.dp))
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

                    // 🔍 BOUTON OCR avec bascule TTS/Stop
                    Button(
                        onClick = {
                            if (isSpeaking) {
                                // Arrêter la lecture
                                tts?.stop()
                                isSpeaking = false
                            } else {
                                if (OCR_lu && lastSpokenText.isNotBlank()) {
                                    // OCR déjà fait → juste lire
                                    Log.d("NANDO", "TTS directe")
                                    pageAdvanceTriggered = false
                                    tts?.language = detectedTtsLocale ?: Locale.FRENCH
                                    tts?.setSpeechRate(speechRate)
                                    speakLongText(tts, lastSpokenText)
                                    isSpeaking = true
                                } else {
                                    // Premier OCR pour cette page
                                    Log.d("NANDO", "Premier OCR")
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
                    ) {
                        Text(if (isSpeaking) "Stop" else "TTS")
                    }


                    // ➡ PAGE SUIVANTE
                    Button(
                        onClick = { onNextPage?.invoke() },
                        enabled = currentPageIndex < totalPages - 1 && onNextPage != null
                    ) {
                        Text(">")
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    // Texte à gauche de la checkbox
                    Text(
                        text = "sel. frames",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .background(
                                color = Color(0xFF0047AB), // Remplacez par MaterialTheme.colorScheme.primary pour une correspondance parfaite
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50) // 50% pour un effet "pilule" comme vos boutons
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp) // Espacement interne pour le confort visuel
                    )

                    // La checkbox
                    Checkbox(
                        checked = autoPlayEnabled,
                        onCheckedChange = {
                            autoPlayEnabled = it

                            // Si on vient de cocher la case (it = true), lancer la lecture
                            if (it) {
                                if (isSpeaking) {
                                    // Si déjà en train de parler, arrêter
                                    tts?.stop()
                                    isSpeaking = false
                                } else {
                                    // Lancer la lecture TTS
                                    if (OCR_lu && lastSpokenText.isNotBlank()) {
                                        // OCR déjà fait → juste lire
                                        Log.d("NANDO", "TTS via case cochée (OCR existant)")
                                        pageAdvanceTriggered = false
                                        tts?.language = detectedTtsLocale ?: Locale.FRENCH
                                        tts?.setSpeechRate(speechRate)
                                        speakLongText(tts, lastSpokenText)
                                        isSpeaking = true
                                    } else {
                                        // Premier OCR pour cette page
                                        Log.d("NANDO", "Premier OCR via case cochée")
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
                        }
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

    // À la fin de la fonction OcrScreen, avant le dernier }
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

} // Fin de OcrScreen





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
                "en" -> Locale.ENGLISH
                "es" -> Locale("es", "ES")
                else -> Locale.getDefault()  // SIMPLE ET PROPRE
            }

            Log.d("LANG_DETECT", "Langue détectée: $languageCode -> $locale")
            tts?.language = locale
            onDetected(locale)
        }
        .addOnFailureListener {
            // En cas d'échec, utiliser la locale système
            val defaultLocale = Locale.getDefault()
            Log.e("LANG_DETECT", "Échec détection, utilisation locale système: $defaultLocale")
            tts?.language = defaultLocale
            onDetected(defaultLocale)
        }
}





// Ajoutez ceci AVANT saveBookmarkToJson


fun saveBookmarkToJson(
    context: Context,
    pdfPath: String,
    pageIndex: Int,
    thresholdBias: Float,
    rectPadding: Float,
    contrastBoost: Float,
    speechRate: Float,
    minWidthRatio: Float,
    preGrayAdjust: Float,
    preGrayTTSAdjust: Float
) {
    try {
        Log.d("BOOKMARK", "Sauvegarde JSON pour: $pdfPath page $pageIndex")

        val fileName = "bookmarks.json"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        file.parentFile?.mkdirs()

        // 1. Lire les signets existants
        // 1. Lire les signets existants
        val bookmarksList = mutableListOf<Map<String, Any>>()

        if (file.exists() && file.length() > 0) {
            try {
                val jsonString = file.readText()
                Log.d("BOOKMARK", "JSON existant: $jsonString")

                // Utiliser une approche simple mais fonctionnelle
                // Chercher tous les objets bookmark
                val bookmarkRegex = "\\{[^{}]*\"pdfPath\"[^{}]*\\}".toRegex()
                val matches = bookmarkRegex.findAll(jsonString)

                matches.forEach { match ->
                    val bookmarkStr = match.value
                    // Extraire toutes les propriétés
                    val pdfPath = "\"pdfPath\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val pageIndex = "\"pageIndex\"\\s*:\\s*(\\d+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val thresholdBias = "\"thresholdBias\"\\s*:\\s*([\\d.]+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val rectPadding = "\"rectPadding\"\\s*:\\s*([\\d.]+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val contrastBoost = "\"contrastBoost\"\\s*:\\s*([\\d.]+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val speechRate = "\"speechRate\"\\s*:\\s*([\\d.]+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val minWidthRatio = "\"minWidthRatio\"\\s*:\\s*([\\d.]+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val preGrayAdjust = "\"preGrayAdjust\"\\s*:\\s*([\\d.-]+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val preGrayTTSAdjust = "\"preGrayTTSAdjust\"\\s*:\\s*([\\d.-]+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    Log.d("BOOKMARK_DEBUG", "preGrayTTSAdjust extrait: $preGrayTTSAdjust")
                    if (pdfPath != null) {
                        bookmarksList.add(mapOf(
                            "pdfPath" to pdfPath,
                            "pageIndex" to (pageIndex?.toIntOrNull() ?: 0),
                            "thresholdBias" to (thresholdBias?.toFloatOrNull() ?: 40f),
                            "rectPadding" to (rectPadding?.toFloatOrNull() ?: 0f),
                            "contrastBoost" to (contrastBoost?.toFloatOrNull() ?: 1f),
                            "speechRate" to (speechRate?.toFloatOrNull() ?: 1f),
                            "minWidthRatio" to (minWidthRatio?.toFloatOrNull() ?: 0.15f),
                            "preGrayAdjust" to (preGrayAdjust?.toFloatOrNull() ?: 0.0f),
                            "preGrayTTSAdjust" to (preGrayTTSAdjust?.toFloatOrNull() ?: 0.0f)
                        ))
                        Log.d("BOOKMARK", "Lu: $pdfPath")
                    }
                }

                Log.d("BOOKMARK", "Trouvé ${bookmarksList.size} signets existants")
            } catch (e: Exception) {
                Log.e("BOOKMARK", "Erreur lecture JSON", e)
            }
        }

        // 2. Retirer l'ancienne entrée si elle existe
        bookmarksList.removeAll { it["pdfPath"] == pdfPath }

        // 3. Ajouter le nouveau signet
        // 3. Mettre à jour OU ajouter le nouveau signet
        val existingIndex = bookmarksList.indexOfFirst { it["pdfPath"] == pdfPath }
        val newBookmark = mapOf(
            "pdfPath" to pdfPath,
            "pageIndex" to pageIndex,
            "thresholdBias" to thresholdBias,
            "rectPadding" to rectPadding,
            "contrastBoost" to contrastBoost,
            "speechRate" to speechRate,
            "minWidthRatio" to minWidthRatio,
            "preGrayAdjust" to preGrayAdjust,
            "preGrayTTSAdjust" to preGrayTTSAdjust
        )

        if (existingIndex >= 0) {
            // Remplacer l'ancien
            bookmarksList[existingIndex] = newBookmark
        } else {
            // Ajouter un nouveau
            bookmarksList.add(newBookmark)
        }

        // 4. Construire le JSON final
        val bookmarksJson = StringBuilder()
        bookmarksJson.append("{\n")
        bookmarksJson.append("  \"bookmarks\": [\n")

        bookmarksList.forEachIndexed { index, bookmark ->
            bookmarksJson.append("    {\n")
            bookmarksJson.append("      \"pdfPath\": \"${bookmark["pdfPath"]}\",\n")
            bookmarksJson.append("      \"pageIndex\": ${bookmark["pageIndex"]},\n")
            bookmarksJson.append("      \"thresholdBias\": ${bookmark["thresholdBias"]},\n")
            bookmarksJson.append("      \"rectPadding\": ${bookmark["rectPadding"]},\n")
            bookmarksJson.append("      \"contrastBoost\": ${bookmark["contrastBoost"]},\n")
            bookmarksJson.append("      \"speechRate\": ${bookmark["speechRate"]},\n")
            bookmarksJson.append("      \"minWidthRatio\": ${bookmark["minWidthRatio"]},\n")
            bookmarksJson.append("      \"preGrayAdjust\": ${bookmark["preGrayAdjust"]},\n")
            bookmarksJson.append("      \"preGrayTTSAdjust\": ${bookmark["preGrayTTSAdjust"]}\n")
            bookmarksJson.append("    }")
            if (index < bookmarksList.size - 1) bookmarksJson.append(",")
            bookmarksJson.append("\n")
        }

        bookmarksJson.append("  ],\n")
        bookmarksJson.append("  \"dernierLivre\": \"$pdfPath\"\n")
        bookmarksJson.append("}")

        Log.d("BOOKMARK_DEBUG", "=== CONTENU JSON À SAUVEGARDER ===")
        Log.d("BOOKMARK_DEBUG", bookmarksJson.toString())  // ← Affiche le JSON complet
        Log.d("BOOKMARK_DEBUG", "================================")

        // 5. Sauvegarder
        file.writeText(bookmarksJson.toString())
        Log.d("BOOKMARK", "Fichier JSON mis à jour avec ${bookmarksList.size} signets")

        // Lire et logguer le JSON sauvegardé
        val savedContent = file.readText()
        Log.d("BOOKMARK", "=== CONTENU JSON SAUVÉ ===")
        Log.d("BOOKMARK", savedContent)
        Log.d("BOOKMARK", "==========================")

    } catch (e: Exception) {
        Log.e("BOOKMARK", "ERREUR CRITIQUE: ${e.message}")
        e.printStackTrace()
    }
}


fun getBookmarkFromJson(context: Context, targetPdfPath: String? = null): Map<String, String> {
    Log.d("BOOKMARK", "Recherche signet pour: $targetPdfPath")
    return try {
        val fileName = "bookmarks.json"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        if (!file.exists() || file.length() == 0L) {
            return emptyMap()
        }

        val jsonString = file.readText()

        // Si targetPdfPath est fourni, chercher CE livre spécifique
        // Sinon, chercher le dernier livre
        val pdfPathToFind = targetPdfPath ?: {
            val dernierLivreRegex = "\"dernierLivre\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            dernierLivreRegex.find(jsonString)?.groupValues?.get(1)
        }()

        if (pdfPathToFind == null) {
            return emptyMap()
        }

        // Chercher la page de ce livre
        val escapedPath = Regex.escape(pdfPathToFind)
        val bookmarkRegex = ("\"pdfPath\"\\s*:\\s*\"$escapedPath\"\\s*," +
                "\\s*\"pageIndex\"\\s*:\\s*(\\d+)\\s*," +
                "\\s*\"thresholdBias\"\\s*:\\s*([\\d.]+)\\s*," +
                "\\s*\"rectPadding\"\\s*:\\s*([\\d.]+)\\s*," +
                "\\s*\"contrastBoost\"\\s*:\\s*([\\d.]+)\\s*," +
                "\\s*\"speechRate\"\\s*:\\s*([\\d.]+)\\s*," +
                "\\s*\"minWidthRatio\"\\s*:\\s*([\\d.]+)\\s*," +
                "\\s*\"preGrayAdjust\"\\s*:\\s*([\\d.-]+)\\s*," +  // ← AJOUTER \\s*,
                "\\s*\"preGrayTTSAdjust\"\\s*:\\s*([\\d.Ee+-]+)").toRegex()  // ← NOUVELLE LIGNE


        val bookmarkMatch = bookmarkRegex.find(jsonString)

        if (bookmarkMatch != null) {
            mapOf(
                "pdfPath" to pdfPathToFind,
                "pageIndex" to (bookmarkMatch.groupValues.getOrNull(1) ?: "0"),
                "thresholdBias" to (bookmarkMatch.groupValues.getOrNull(2) ?: "40.0"),
                "rectPadding" to (bookmarkMatch.groupValues.getOrNull(3) ?: "0.0"),
                "contrastBoost" to (bookmarkMatch.groupValues.getOrNull(4) ?: "1.0"),
                "speechRate" to (bookmarkMatch.groupValues.getOrNull(5) ?: "1.0"),
                "minWidthRatio" to (bookmarkMatch.groupValues.getOrNull(6) ?: "0.15"),
                "preGrayAdjust" to (bookmarkMatch.groupValues.getOrNull(7) ?: "0.0"),
                "preGrayTTSAdjust" to (bookmarkMatch.groupValues.getOrNull(8) ?: "0.0")
            )
        } else {
            mapOf("pdfPath" to pdfPathToFind, "pageIndex" to "0")
        }

    } catch (e: Exception) {
        Log.e("BOOKMARK", "Erreur recherche signet", e)
        emptyMap()
    }
}


fun handleTtsButtonClick(
    isSpeaking: Boolean,
    tts: TextToSpeech?,
    selectedRectIndices: Set<Int>,
    rectangles: List<android.graphics.Rect>,
    originalDisplayBitmap: Bitmap?,
    speechRate: Float,
    detectedTtsLocale: Locale?,
    onSpeechStateChange: (Boolean) -> Unit,
    onLocaleDetected: (Locale) -> Unit,
    onPageAdvanceReset: () -> Unit,
    onTextProcessed: (String) -> Unit,
    onSetOcrLu: () -> Unit,
    preGrayTTSAdjust: Float,
    onOcrEmptyWarning: ((Boolean) -> Unit)? = null
) {
    if (isSpeaking) {
        // Si en train de parler → arrêter
        tts?.stop()
        onSpeechStateChange(false)
    } else {

        onSetOcrLu()
        // Si pas en train de parler → lancer l'OCR et TTS
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val verticalTolerance = 60
        val horizontalTolerance = 40
        val selectedRects = selectedRectIndices
            .map { rectangles[it] }
            .sortedWith { r1, r2 ->
                val dx = kotlin.math.abs(r1.left - r2.left)
                if (dx <= horizontalTolerance) {
                    r1.top - r2.top
                } else {
                    r1.left - r2.left
                }
            }

        if (originalDisplayBitmap == null || selectedRects.isEmpty()) return

        Log.d("PRE_GRAY_TTS", "Valeur du slider preGrayTTSAdjust avant traitement: $preGrayTTSAdjust")
        Log.d("PRE_GRAY_TTS", "originalDisplayBitmap dimensions: ${originalDisplayBitmap?.width}x${originalDisplayBitmap?.height}")

        // 👇 ÉTAPE CRUCIALE : Appliquer le prétraitement gris à l'image COMPLÈTE
        val preprocessedBitmap = if (preGrayTTSAdjust != 0.0f) {
            applyPreGrayAdjustment(originalDisplayBitmap, preGrayTTSAdjust)
        } else {
            originalDisplayBitmap
        }

        Log.d("PRE_GRAY_TTS", "Bitmap prétraité avec preGrayTTSAdjust = $preGrayTTSAdjust")

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
                preprocessedBitmap,
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
                            onTextProcessed(finalText)

                            if (detectedTtsLocale == null) {
                                detectLanguageAndSetTts(finalText, tts) { locale ->
                                    onLocaleDetected(locale)
                                    tts?.language = locale
                                    tts?.setSpeechRate(speechRate)
                                    onPageAdvanceReset()
                                    speakLongText(tts, finalText)
                                    onSpeechStateChange(true)
                                }
                            } else {
                                tts?.language = detectedTtsLocale
                                tts?.setSpeechRate(speechRate)
                                onPageAdvanceReset()
                                speakLongText(tts, finalText)
                                onSpeechStateChange(true)
                            }
                        } else {  // ← NOUVEAU ELSE POUR TEXTE VIDE
                            Log.d("PRE_GRAY_TTS", "OCR a retourné du texte vide")
                            onOcrEmptyWarning?.invoke(true)
                            onSpeechStateChange(false)
                        }
                    }
                }
                .addOnFailureListener {
                    pending--
                }
        }
    }
}


@Composable
fun FlipScreenButton() {
    // Etat de l'orientation de l'écran (true = portrait inversé, false = portrait normal)
    val (isFlipped, setIsFlipped) = remember { mutableStateOf(false) }
    val context = LocalContext.current

    IconButton(onClick = {
        val newState = !isFlipped
        setIsFlipped(newState)

        // Basculer entre portrait normal et portrait inversé
        val activity = context as? ComponentActivity
        activity?.let {
            if (newState) {
                // Portrait inversé (rotation 180°)
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            } else {
                // Portrait normal
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }) {
        Icon(
            imageVector = Icons.Default.ScreenRotation, // Icône appropriée pour rotation
            contentDescription = if (isFlipped)
                "Retour à l'orientation normale"
            else "Retourner l'écran (180°)",
            tint = if (isFlipped) Color.Red else Color.White
        )
    }
}

fun speakLongText(tts: TextToSpeech?, text: String) {
    if (tts == null) {
        Log.d("TTS_DEBUG", "speakLongText: tts est null")
        return
    }

    Log.d("TTS_DEBUG", "=== DÉBUT speakLongText ===")
    Log.d("TTS_DEBUG", "Texte d'entrée (${text.length} chars): ${text.take(100)}...")

    // 1. Échapper les caractères XML
    val escapedText = text
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("...", ". ")
        .replace("ndlr", "")

    // 2. Ajouter les pauses SSML
// 2. Ajouter les pauses SSML (une seule pause par fin de phrase ou ligne)
    val textWithPauses = escapedText

        .replace(Regex("\\.{2,}\\s*"), ",")
        // Remplace ponctuation (.!?) suivie d'espaces ou retours chariots par ponctuation + 1 break
        .replace(Regex("(?<!\\.)([.!?])(?!\\d)\\s*"), "$1 <break time=\"500ms\"/> ")
        // Remplace les sauts de ligne restants (sans ponctuation) par 1 break
        .replace(Regex("(?<!<break time=\"500ms\"/> )\\n"), " <break time=\"500ms\"/> ")

    // 3. CRÉER LE SSML CORRECT AVEC EN-TÊTE XML


    val zws = "\u200B\u200B\u200B" // Ajoutez cette ligne ici
    val baseText = "$zws$textWithPauses"




// 4. Diviser le texte en parties (en coupant après une balise break si possible)
    val maxLength = 500 //3500
    val parts = mutableListOf<String>()
    var remaining = baseText // On utilise baseText au lieu de ssmlText

    // parts.add(remaining)

    Log.d("TTS_DEBUG", "Nombre de parties: ${parts.size}")

    if (remaining.length <= maxLength) {
        parts.add(remaining)
        Log.d("TTS_DEBUG", "Texte court, pas de division nécessaire")
    } else {
        Log.d("TTS_DEBUG", "Division du texte nécessaire")

        var loopCount = 0

        while (remaining.length > maxLength) {
            loopCount++

            Log.d("TTS_DEBUG", "Boucle #$loopCount - remaining: ${remaining.length} chars")
            Log.d("TTS_DEBUG", "Boucle while - remaining.length: ${remaining.length}, maxLength: $maxLength")

            val searchWindow = remaining.substring(0, maxLength)
            Log.d("TTS_DEBUG", "searchWindow.length: ${searchWindow.length}")

            val lastBreakIndex = searchWindow.lastIndexOf("<break time=\"500ms\"/>")
            Log.d("TTS_DEBUG", "lastBreakIndex: $lastBreakIndex")


            val splitIndex = if (lastBreakIndex > 0) {
                // Couper après le break SSML
                lastBreakIndex + "<break time=\"500ms\"/>".length
            } else {
                // 2. Sinon chercher la fin d'une phrase
                val lastSentenceEnd = searchWindow.lastIndexOfAny(listOf(". ", "! ", "? "))
                if (lastSentenceEnd > 0) {
                    lastSentenceEnd + 1
                } else {
                    // 3. Sinon couper au dernier espace
                    val lastSpace = searchWindow.lastIndexOf(' ')
                    if (lastSpace > 0) lastSpace else maxLength
                }
            }

            // Ajouter la partie
            parts.add(remaining.substring(0, splitIndex))
            // Continuer avec le reste
            remaining = remaining.substring(splitIndex)
        }

        // Ajouter le dernier morceau
        if (remaining.isNotBlank()) {
            parts.add(remaining)
        }
        Log.d("TTS_DEBUG", "Boucle exécutée $loopCount fois")
    }



    Log.d("TTS_DEBUG", "Nombre de parties créées: ${parts.size}")
    parts.forEachIndexed { index, part ->
        Log.d("TTS_DEBUG", "Partie $index: ${part.length} caractères - début: ${part.take(50)}...")
    }


    // 5. Arrêter toute lecture en cours
    tts.stop()
    Thread.sleep(100) // Petit délai après stop

// 6. Envoyer chaque partie enveloppée dans son propre SSML
    parts.forEachIndexed { index, part ->
        val prefix = if (index == 0) zws else ""

        val safePart = """<?xml version="1.0" encoding="UTF-8"?>
    <speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis">
    $prefix$part
    </speak>"""

        // Définir l'ID
        val utteranceId = if (index == parts.size - 1) "FINAL_PART" else "OCR_PART_$index"

        // Définir le mode : Flush pour le premier, Add pour les suivants
        val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_STREAM, TextToSpeech.Engine.DEFAULT_STREAM.toString())
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "true")
        }

        Log.d("TTS_DEBUG", "Appel tts.speak() partie $index ($queueMode) avec ID: $utteranceId (${safePart.length} chars)")
        tts.speak(safePart, queueMode, params, utteranceId)

        // Petit délai entre les parties
        if (index < parts.size - 1) {
            Thread.sleep(50)
        }
    }

    Log.d("TTS_DEBUG", "=== FIN speakLongText ===")
}

fun handleTtsCompletion(
    utteranceId: String?,
    isSpeaking: Boolean,
    autoPlayEnabled: Boolean,
    onNextPage: (() -> Unit)?,
    onAutoPlayEnabledChange: (Boolean) -> Unit,
    onSelectedRectIndicesChange: (Set<Int>) -> Unit
) {
    if (utteranceId == "FINAL_PART") {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (!autoPlayEnabled) {
                onNextPage?.invoke()
            } else {
                // Mode manuel : NE PAS avancer, mais décocher la case
                onAutoPlayEnabledChange(false)
                // ET désélectionner tous les cadres
                onSelectedRectIndicesChange(emptySet())
            }
        }
    }
}

// Ajoutez cette fonction dans le fichier ImageProcessing.kt
fun applyPreGrayAdjustment(bitmap: Bitmap, preGrayAdjust: Float): Bitmap {
    try {
        Log.d("PRE_GRAY_TTS", "applyPreGrayAdjustment: preGrayAdjust = $preGrayAdjust")

        // 1. Convertir le bitmap en Mat OpenCV
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)

        // 2. Convertir en niveaux de gris si nécessaire
        val grayMat = Mat()
        if (srcMat.channels() == 3) {
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGB2GRAY)
        } else if (srcMat.channels() == 4) {
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
        } else {
            srcMat.copyTo(grayMat)
        }

        // 3. Appliquer l'ajustement de luminosité/contraste
        val adjustedMat = Mat()

        // Si preGrayAdjust est positif : éclaircir
        // Si preGrayAdjust est négatif : assombrir
        // Facteur de contraste fixe à 1.0, on ajuste seulement la luminosité
        val alpha = 1.0 // Facteur de contraste (inchangé)
        val beta = preGrayAdjust * 255.0 // Ajustement de luminosité

        grayMat.convertTo(adjustedMat, grayMat.type(), alpha, beta)

        // 4. Reconvertir en bitmap
        val resultBitmap = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )

        // Convertir le Mat gris en bitmap ARGB (3 canaux)
        Imgproc.cvtColor(adjustedMat, adjustedMat, Imgproc.COLOR_GRAY2RGBA)
        Utils.matToBitmap(adjustedMat, resultBitmap)

        // 5. Libérer la mémoire
        srcMat.release()
        grayMat.release()
        adjustedMat.release()


        val pixelBefore = bitmap.getPixel(bitmap.width/2, bitmap.height/2)
        val pixelAfter = resultBitmap.getPixel(resultBitmap.width/2, resultBitmap.height/2)

        Log.d("PRE_GRAY_TTS", "Pixel avant traitement: ${pixelBefore.toUInt().toString(16)}")
        Log.d("PRE_GRAY_TTS", "Pixel après traitement: ${pixelAfter.toUInt().toString(16)}")

// Calculer la différence
        val rBefore = android.graphics.Color.red(pixelBefore)
        val gBefore = android.graphics.Color.green(pixelBefore)
        val bBefore = android.graphics.Color.blue(pixelBefore)

        val rAfter = android.graphics.Color.red(pixelAfter)
        val gAfter = android.graphics.Color.green(pixelAfter)
        val bAfter = android.graphics.Color.blue(pixelAfter)

        Log.d("PRE_GRAY_TTS", "RGB avant: ($rBefore, $gBefore, $bBefore)")
        Log.d("PRE_GRAY_TTS", "RGB après: ($rAfter, $gAfter, $bAfter)")


        Log.d("PRE_GRAY_TTS", "applyPreGrayAdjustment: bitmap traité avec succès")
        return resultBitmap

    } catch (e: Exception) {
        Log.e("PRE_GRAY_TTS", "Erreur dans applyPreGrayAdjustment", e)
        return bitmap // Retourner l'original en cas d'erreur
    }
}

fun relaunchTts(
    tts: TextToSpeech?,
    selectedRectIndices: Set<Int>,
    rectangles: List<android.graphics.Rect>,
    originalDisplayBitmap: Bitmap?,
    speechRate: Float,
    detectedTtsLocale: Locale?,
    preGrayTTSAdjust: Float,
    onSpeechStateChange: (Boolean) -> Unit,
    onLocaleDetected: (Locale) -> Unit,
    onPageAdvanceReset: () -> Unit,
    onTextProcessed: (String) -> Unit,
    onSetOcrLu: () -> Unit,
    onOcrEmptyWarning: ((Boolean) -> Unit)? = null,
    context: Context? = null
) {
    if (selectedRectIndices.isEmpty()) {
        Log.d("RELAUNCH_TTS", "Aucun rectangle sélectionné, pas de relance")
        return
    }

    // Forcer un nouvel OCR
    onSetOcrLu.invoke()

    Log.d("RELAUNCH_TTS", "Relance TTS (${selectedRectIndices.size} rectangles)")

    handleTtsButtonClick(
        isSpeaking = false,
        tts = tts,
        selectedRectIndices = selectedRectIndices,
        rectangles = rectangles,
        originalDisplayBitmap = originalDisplayBitmap,
        speechRate = speechRate,
        detectedTtsLocale = detectedTtsLocale,
        onSpeechStateChange = onSpeechStateChange,
        onLocaleDetected = onLocaleDetected,
        onPageAdvanceReset = onPageAdvanceReset,
        onTextProcessed = onTextProcessed,
        onSetOcrLu = onSetOcrLu,
        preGrayTTSAdjust = preGrayTTSAdjust,
        onOcrEmptyWarning = onOcrEmptyWarning
    )
}