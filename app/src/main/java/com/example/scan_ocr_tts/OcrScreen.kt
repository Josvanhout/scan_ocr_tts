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
    pdfIdentity: String,   // 👈 AJOUT
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




    // Vérification OCR au chargement de l'écran
    LaunchedEffect(Unit) {
        Log.d("OCR_CHECK", "Vérification OCR dans OcrScreen...")

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
                    Log.d("OCR_CHECK", "✓ OCR prêt dans OcrScreen")
                }
                .addOnFailureListener { e ->
                    Log.w("OCR_CHECK", "⚠ OCR pas prêt: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("OCR_CHECK", "❌ Erreur OCR: ${e.message}")
        }
    }



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


    var all_selected by remember { mutableStateOf(false) }
    var customRectWidth by rememberSaveable { mutableStateOf(100f) }
    var customRectHeight by rememberSaveable { mutableStateOf(100f) }

    LaunchedEffect(pdfIdentity) {
        if (lastRestoredPdf != pdfIdentity) {
            Log.d("BOOKMARK_DEBUG", "=== DÉBUT RESTAURATION ===")
            Log.d("BOOKMARK_DEBUG", "pdfIdentity: $pdfIdentity")

            // Restauration depuis le JSON
            val bookmarkData = getBookmarkFromJson(context, pdfIdentity)
            val savedPdfPath = bookmarkData["pdfPath"] ?: prefs.getString("lastPdfPath", null)
            val savedPage = if (savedPdfPath == pdfIdentity) {
                val pageStr = bookmarkData["pageIndex"]
                Log.d("BOOKMARK_DEBUG", "Valeur pageIndex lue = '$pageStr'")
                pageStr?.toIntOrNull() ?: 0
            } else 0

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
                    Log.d("BOOKMARK_DEBUG", "Restauration highResScaleFactor: $savedScale")
                }
                Log.d("BOOKMARK_DEBUG", "✓ RESTAURATION: Aller à page $savedPage")

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

    var contrastBoostMode by rememberSaveable { mutableStateOf(!useHighRes) }

    LaunchedEffect(useHighRes) {
        if (useHighRes) {
            // contrastBoostMode = false
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


    // var speechRate by rememberSaveable { mutableStateOf(prefs.getFloat("speechRate", 1.0f)) }
    var detectedTtsLocale by remember { mutableStateOf<Locale?>(null) }

    //    var disabledBlocks by remember { mutableStateOf(setOf<Int>()) }
    var selectedRectIndices by remember { mutableStateOf(setOf<Int>()) }

    // Fonction locale pour désélectionner les rectangles
    val handleRectanglesDeselection = {
        selectedRectIndices = emptySet()
    }

    // Fonction pour mettre à jour le rectangle personnalisé
    fun updateCustomRect(bitmap: Bitmap?) {
        if (all_selected && bitmap != null) {
            val width = (bitmap.width * (customRectWidth / 100f)).toInt().coerceIn(10, bitmap.width)
            val height = (bitmap.height * (customRectHeight / 100f)).toInt().coerceIn(10, bitmap.height)

            // Centrer le rectangle
            val left = (bitmap.width - width) / 2
            val top = (bitmap.height - height) / 2

            fullPageRect = android.graphics.Rect(left, top, left + width, top + height)
            rectangles = mutableListOf(fullPageRect!!)
            selectedRectIndices = setOf(0)
        }
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

                // ↓↓↓ AJOUTEZ ICI (après le if et avant tts?.language) ↓↓↓
                val availableLangs = tts?.availableLanguages ?: emptySet()
// Par défaut, utiliser fr_FR s'il existe
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
                Log.d("TTS_CHECK", "Locale TTS par défaut: $defaultLocale")

                tts?.setSpeechRate(speechRate)
                // Vérifier les capacités TTS
                val engineInfo = tts?.defaultEngine
                Log.d("TTS_CHECK", "Moteur TTS par défaut: $engineInfo")

                val engines = tts?.engines
                engines?.forEach { engine ->
                    Log.d("TTS_CHECK", "Moteur disponible: ${engine.name} - ${engine.label}")
                }

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
        Log.d("RESET_CHECK", "Entrée dans LaunchedEffect - all_selected=$all_selected")

        if (all_selected) {
            Log.d("RESET_CHECK", "Mode all_selected activé - forçage réinitialisation")
            lastSpokenText = ""
            OCR_lu = false
            Log.d("ALL_SELECTED", "Mode pleine page activé - détection automatique ignorée")
            // On garde l'image originale mais sans rectangles détectés
            originalDisplayBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            displayBitmap = originalDisplayBitmap

            originalDisplayBitmap?.let { bmp ->
                updateCustomRect(bmp)
            }

            // Ne pas exécuter le reste du traitement
            return@LaunchedEffect
        }

//        OCR_lu = false
//        lastSpokenText = ""

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
            minWidthRatio,
            skipDetection = no_squares,
            boostMode = contrastBoostMode
        )
        Log.d("PRE_GRAY_CALL", "Appel toAdaptiveThreshold avec preGrayAdjust = $preGrayAdjust")

//        val boostedBitmap = if (contrastBoostMode) {
//            ImageProcessing.strengthenText(processedBitmap)
//        } else {
//            processedBitmap
//        }
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

        // Créer un rectangle couvrant toute la page
        fullPageRect = android.graphics.Rect(0, 0, originalBitmap.width, originalBitmap.height)

        selectedRectIndices = rectangles.indices.toSet()

        displayBitmap = boostedBitmap

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
//        val options = TextRecognizerOptions.Builder()
//            .setEntityMode(TextRecognizerOptions.ENTITY_MODE_NONE)
//            .build()
//        val recognizer = TextRecognition.getClient(options)


        val image = InputImage.fromBitmap(processedBitmap, 0)

        OcrProcessor.processImageWithMlKit(processedBitmap, 0) { ocrResult ->
            if (ocrResult.success) {
                Log.d("NAV_DEBUG", "OCR blocs détectés: ${ocrResult.blocks.size}")
                // Maintenant nous utilisons directement nos TextBlock
                textBlocks = ocrResult.blocks
                recognizedText = "Sélectionne les zones à garder, puis appuie sur le bouton."

                // ← CORRECTION ICI : utiliser handleTtsButtonClick pour autoPlay
                // (gardez votre code existant ici si nécessaire)

            } else {
                Log.e("OCR_DEBUG", "Erreur OCR: ${ocrResult.error}")
                recognizedText = "Erreur lors de la reconnaissance."
            }
        }

//        lastSpokenText = ""
//        OCR_lu = false
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


//                    Button(onClick = {
//                        Log.d("TTS_TEST", "Test TTS avec SSML simple")
//
//                        // Test SSML très simple
//                        val ssmlText = """
//        <?xml version="1.0" encoding="UTF-8"?>
//        <speak>
//            Bonjour, ceci est un test avec SSML très simple.
//        </speak>
//    """.trimIndent()
//
//                        tts?.language = Locale.FRANCE
//                        tts?.setSpeechRate(speechRate)
//
//                        val result = tts?.speak(ssmlText, TextToSpeech.QUEUE_FLUSH, null, "test_ssml")
//                        Log.d("TTS_TEST", "Résultat SSML simple: $result")
//
//                        // Test 2 : Sans SSML pour comparer
//                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
//                            val simpleText = "Bonjour, ceci est un test sans SSML."
//                            val result2 = tts?.speak(simpleText, TextToSpeech.QUEUE_ADD, null, "test_no_ssml")
//                            Log.d("TTS_TEST", "Résultat sans SSML: $result2")
//                        }, 1000)
//                    }) {
//                        Text("Test SSML")
//                    }

// Contraste auto
                    IconButton(
                        onClick = { contrastBoostMode = !contrastBoostMode },
                        // enabled = !useHighRes
                    ) {
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
//                                imageVector = Icons.Default.Analytics,
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
                                preGrayTTSAdjust = String.format(Locale.US, "%.2f", preGrayTTSAdjust).toFloat(),
                                useHighRes = useHighRes,
                                highResScaleFactor = highResScaleFactor,
                                customRectWidth = customRectWidth,      // ← Remplacer 100f par customRectWidth
                                customRectHeight = customRectHeight,    // ← Remplacer 100f par customRectHeight
                                all_selected = all_selected             // ← Remplacer false par all_selected
                            )

                            onNext()  // ← RETOUR À L'ACCUEIL
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

                        // Pré-traitement gris pour le tts
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
//                        Spacer(modifier = Modifier.height(4.dp))
//
//                        Slider(
//                            value = preGrayTTSAdjust,
//                            onValueChange = { preGrayTTSAdjust = it },
//                            valueRange = -1.0f..2.0f,
//                            steps = 20,
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(horizontal = 16.dp)
//                                .height(24.dp)
//                        )
//
//                        Spacer(modifier = Modifier.height(16.dp))

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

// Curseurs pour le rectangle personnalisé (visible seulement si all_selected est coché)
                    if (all_selected) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Largeur rectangle: ${customRectWidth.toInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF9C27B0)) // Violet
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
                            text = "Hauteur rectangle: ${customRectHeight.toInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF9C27B0)) // Violet
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
                                    Log.d("TTS_TEXT", "Texte qui va être lu: $lastSpokenText")
                                    speakLongText(tts, lastSpokenText, context)
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
                                        onPageAdvanceReset = { pageAdvanceTriggered = false },onTextProcessed = { text ->
                                            lastSpokenText = text
                                            Log.d("TTS_UPDATE", "Nouveau texte enregistré: ${text.take(50)}...")
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


                    // ➡ PAGE SUIVANTE
                    Button(
                        onClick = { onNextPage?.invoke() },
                        enabled = currentPageIndex < totalPages - 1 && onNextPage != null
                    ) {
                        Text(">")
                    }

                    // Spacer(modifier = Modifier.width(8.dp))


                    Spacer(modifier = Modifier.width(8.dp))
//                    // Texte à gauche de la checkbox
//                    Text(
//                        text = "sel. frames",
//                        color = Color.White,
//                        fontSize = 8.sp,
//                        modifier = Modifier
//                            .padding(end = 2.dp)
//                            .background(
//                                color = Color(0xFF0047AB), // Remplacez par MaterialTheme.colorScheme.primary pour une correspondance parfaite
//                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50) // 50% pour un effet "pilule" comme vos boutons
//                            )
//                            .padding(horizontal = 12.dp, vertical = 6.dp) // Espacement interne pour le confort visuel
//                    )

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
                                        speakLongText(tts, lastSpokenText, context)
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
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.Red,        // Case rouge quand cochée
                            uncheckedColor = Color.Red,      // Case rouge quand non cochée
                            checkmarkColor = Color.White     // Coche blanche pour le contraste
                        )
                    )

                    Checkbox(
                        checked = all_selected,
                        onCheckedChange = { isChecked ->
                            all_selected = isChecked
                            if (isChecked) {
                                // Effacer tous les rectangles existants
                                rectangles.clear()
                                // Créer le rectangle personnalisé
                                originalDisplayBitmap?.let { bmp ->
                                    val width = (bmp.width * (customRectWidth / 100f)).toInt().coerceIn(10, bmp.width)
                                    val height = (bmp.height * (customRectHeight / 100f)).toInt().coerceIn(10, bmp.height)

                                    // Centrer le rectangle
                                    val left = (bmp.width - width) / 2
                                    val top = (bmp.height - height) / 2

                                    fullPageRect = android.graphics.Rect(left, top, left + width, top + height)
                                    rectangles.add(fullPageRect!!)
                                    selectedRectIndices = setOf(0)
                                }
                            } else {
                                // Mode normal : on vide tout
                                rectangles.clear()
                                selectedRectIndices = emptySet()
                                fullPageRect = null
                            }
                            OCR_lu = false
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF006400),  // Vert foncé
                            uncheckedColor = Color(0xFF006400),
                            checkmarkColor = Color.White
                        )
                    )

                    Checkbox(
                        checked = no_squares,
                        onCheckedChange = { no_squares = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.Blue,        // Case bleue quand cochée
                            uncheckedColor = Color.Blue,      // Case bleue quand non cochée
                            checkmarkColor = Color.White      // Coche blanche pour le contraste
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


