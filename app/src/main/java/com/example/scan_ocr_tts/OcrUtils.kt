package com.example.scan_ocr_tts

import android.net.ConnectivityManager
import android.net.NetworkInfo

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.Locale

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
        
        .replace("ﬁ", "fi")
        .replace("ﬂ", "fl")

        .replace(Regex("(?<=\\p{L})-\\s*\\n\\s*(?=\\p{L})"), "")

        .replace(Regex("\\b([A-ZÉÈÀÂÔÎÏÊÇŒ]{2,})(\\s+[A-ZÉÈÀÂÔÎÏÊÇŒ]{2,})+\\b")) { match ->
            val text = match.value
            
            if (text.last().isLetterOrDigit()) "$text." else text
        }

        .replace(Regex("\\([^()]*\\)", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("\\[[^\\[\\]]*\\]", RegexOption.DOT_MATCHES_ALL), "")

        .replace(Regex("(?<![.!?])\\n"), " ")

        .replace(Regex("\\s+"), " ")

        .replace(Regex("\\s+([,.!?;:])"), "$1")

        .replace(Regex("([,.!?;:])(\\p{L})"), "$1 $2")

        .replace(Regex("«\\s+"), "« ")
        .replace(Regex("\\s+»"), " »")

        .replace("’", "'")
        .replace("`", "'")

        .replace(Regex("\\b([ldjmstcq])\\s+(?=[aeiouh])"), "$1'")

        .replace(Regex("\\b([A-Za-z*]{2,8})\\s*(?:e|ᵉ|°|º)?\\s+si[èe]cle\\b", RegexOption.IGNORE_CASE)) { m ->
            val raw = m.groupValues[1]

                .replace(Regex("([xivlcdm])e$", RegexOption.IGNORE_CASE), "$1")  
                .replace(Regex("([xivlcdm])r$", RegexOption.IGNORE_CASE), "$1")  

            val roman = raw
                .replace('1', 'I')
                .replace('l', 'I')
                .replace('v', 'V')
                .replace('u', 'V')
                .replace('r', 'I')
                .replace("*", "")
                .uppercase()

            val n = romanToInt(roman)

            if (n in 1..50) "${n}e siècle" else m.value
        }

        .replace(Regex("\\bsiglo\\s+([A-Za-z*]{1,8})\\b", RegexOption.IGNORE_CASE)) { m ->
            val raw = m.groupValues[1]
                .replace(Regex("([xivlcdm])e$", RegexOption.IGNORE_CASE), "$1")
                .replace(Regex("([xivlcdm])r$", RegexOption.IGNORE_CASE), "$1")

            val roman = raw
                .replace('1', 'I')
                .replace('l', 'I')
                .replace('v', 'V')
                .replace('u', 'V')
                .replace('r', 'I')
                .replace("*", "")
                .uppercase()

            val n = romanToInt(roman)

            if (n in 1..50) "siglo $n" else m.value
        }

        .also {  }

        .lowercase(Locale.getDefault())

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

fun applyPreGrayAdjustment(bitmap: Bitmap, preGrayAdjust: Float): Bitmap {
    try {

        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)

        val grayMat = Mat()
        if (srcMat.channels() == 3) {
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGB2GRAY)
        } else if (srcMat.channels() == 4) {
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
        } else {
            srcMat.copyTo(grayMat)
        }

        val adjustedMat = Mat()

        val alpha = 1.0 
        val beta = preGrayAdjust * 255.0 

        grayMat.convertTo(adjustedMat, grayMat.type(), alpha, beta)

        val resultBitmap = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )

        Imgproc.cvtColor(adjustedMat, adjustedMat, Imgproc.COLOR_GRAY2RGBA)
        Utils.matToBitmap(adjustedMat, resultBitmap)

        srcMat.release()
        grayMat.release()
        adjustedMat.release()

        val pixelBefore = bitmap.getPixel(bitmap.width/2, bitmap.height/2)
        val pixelAfter = resultBitmap.getPixel(resultBitmap.width/2, resultBitmap.height/2)

        val rBefore = android.graphics.Color.red(pixelBefore)
        val gBefore = android.graphics.Color.green(pixelBefore)
        val bBefore = android.graphics.Color.blue(pixelBefore)

        val rAfter = android.graphics.Color.red(pixelAfter)
        val gAfter = android.graphics.Color.green(pixelAfter)
        val bAfter = android.graphics.Color.blue(pixelAfter)

        return resultBitmap

    } catch (e: Exception) {
        
        return bitmap 
    }
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
                else -> Locale.getDefault()  
            }

            tts?.language = locale
            onDetected(locale)
        }
        .addOnFailureListener {
            
            val defaultLocale = Locale.getDefault()
            
            tts?.language = defaultLocale
            onDetected(defaultLocale)
        }
}

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
    preGrayTTSAdjust: Float,
    useHighRes: Boolean,
    highResScaleFactor: Float,
    customRectWidth: Float,   
    customRectHeight: Float,
    all_selected: Boolean
) {
    try {

        val fileName = "bookmarks.json"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        file.parentFile?.mkdirs()

        val bookmarksList = mutableListOf<Map<String, Any>>()

        if (file.exists() && file.length() > 0) {
            try {
                val jsonString = file.readText()

                val bookmarkRegex = "\\{[^{}]*\"pdfPath\"[^{}]*\\}".toRegex()
                val matches = bookmarkRegex.findAll(jsonString)

                matches.forEach { match ->
                    val bookmarkStr = match.value
                    
                    val pdfPath = "\"pdfPath\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val pageIndex = "\"pageIndex\"\\s*:\\s*(\\d+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val thresholdBias = "\"thresholdBias\"\\s*:\\s*([\\d.]+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val rectPadding = "\"rectPadding\"\\s*:\\s*([\\d.]+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val contrastBoost = "\"contrastBoost\"\\s*:\\s*([\\d.]+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val speechRate = "\"speechRate\"\\s*:\\s*([\\d.]+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val minWidthRatio = "\"minWidthRatio\"\\s*:\\s*([\\d.]+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val preGrayAdjust = "\"preGrayAdjust\"\\s*:\\s*([\\d.-]+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val preGrayTTSAdjust = "\"preGrayTTSAdjust\"\\s*:\\s*([\\d.-]+)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    val allSelected = "\"all_selected\"\\s*:\\s*(true|false)".toRegex().find(bookmarkStr)?.groupValues?.get(1)
                    
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
                            "preGrayTTSAdjust" to (preGrayTTSAdjust?.toFloatOrNull() ?: 0.0f),
                            "useHighRes" to useHighRes,
                            "highResScaleFactor" to (highResScaleFactor ?: 1.3f),
                            "customRectWidth" to (customRectWidth?: 100f),
                            "customRectHeight" to (customRectHeight?: 100f),
                            "all_selected" to (allSelected?.toBoolean() ?: false)
                        ))
                        
                    }
                }

            } catch (e: Exception) {
                
            }
        }

        bookmarksList.removeAll { it["pdfPath"] == pdfPath }

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
            "preGrayTTSAdjust" to preGrayTTSAdjust,
            "useHighRes" to useHighRes,
            "highResScaleFactor" to highResScaleFactor,
            "customRectWidth" to customRectWidth,   
            "customRectHeight" to customRectHeight,
            "all_selected" to all_selected
        )

        if (existingIndex >= 0) {
            
            bookmarksList[existingIndex] = newBookmark
        } else {
            
            bookmarksList.add(newBookmark)
        }

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
            bookmarksJson.append("      \"preGrayTTSAdjust\": ${bookmark["preGrayTTSAdjust"]},\n")
            bookmarksJson.append("      \"useHighRes\": ${bookmark["useHighRes"]},\n")
            bookmarksJson.append("      \"all_selected\": ${bookmark["all_selected"]},\n")
            bookmarksJson.append("      \"customRectWidth\": ${bookmark["customRectWidth"]},\n")      
            bookmarksJson.append("      \"customRectHeight\": ${bookmark["customRectHeight"]},\n")    
            bookmarksJson.append("      \"highResScaleFactor\": ${bookmark["highResScaleFactor"]}\n")
            bookmarksJson.append("    }")
            if (index < bookmarksList.size - 1) bookmarksJson.append(",")
            bookmarksJson.append("\n")
        }

        bookmarksJson.append("  ],\n")
        bookmarksJson.append("  \"dernierLivre\": \"$pdfPath\"\n")
        bookmarksJson.append("}")

        file.writeText(bookmarksJson.toString())

        val savedContent = file.readText()

    } catch (e: Exception) {
        
        e.printStackTrace()
    }
}

fun getBookmarkFromJson(context: Context, targetPdfPath: String? = null): Map<String, String> {
    
    return try {
        val fileName = "bookmarks.json"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        if (!file.exists() || file.length() == 0L) {
            return emptyMap()
        }

        val jsonString = file.readText()

        val pdfPathToFind = targetPdfPath ?: {
            val dernierLivreRegex = "\"dernierLivre\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            dernierLivreRegex.find(jsonString)?.groupValues?.get(1)
        }()

        if (pdfPathToFind == null) {
            return emptyMap()
        }

        val escapedPath = Regex.escape(pdfPathToFind)
        val bookmarkRegex = ("\"pdfPath\"\\s*:\\s*\"$escapedPath\".*?" +
                "\"pageIndex\"\\s*:\\s*(\\d+).*?" +
                "\"thresholdBias\"\\s*:\\s*([\\d.]+).*?" +
                "\"rectPadding\"\\s*:\\s*([\\d.]+).*?" +
                "\"contrastBoost\"\\s*:\\s*([\\d.]+).*?" +
                "\"speechRate\"\\s*:\\s*([\\d.]+).*?" +
                "\"minWidthRatio\"\\s*:\\s*([\\d.]+).*?" +
                "\"preGrayAdjust\"\\s*:\\s*([\\d.-]+).*?" +
                "\"preGrayTTSAdjust\"\\s*:\\s*([\\d.Ee+-]+).*?" +
                "\"useHighRes\"\\s*:\\s*(true|false).*?" +
                "\"all_selected\"\\s*:\\s*(true|false).*?" +   
                "\"customRectWidth\"\\s*:\\s*([\\d.]+).*?" +
                "\"customRectHeight\"\\s*:\\s*([\\d.]+).*?" +
                "\"highResScaleFactor\"\\s*:\\s*([\\d.]+)").toRegex(RegexOption.DOT_MATCHES_ALL)

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
                "preGrayTTSAdjust" to (bookmarkMatch.groupValues.getOrNull(8) ?: "0.0"),
                "useHighRes" to (bookmarkMatch.groupValues.getOrNull(9) ?: "false"),
                "all_selected" to (bookmarkMatch.groupValues.getOrNull(10) ?: "false"),   
                "customRectWidth" to (bookmarkMatch.groupValues.getOrNull(11) ?: "100.0"), 
                "customRectHeight" to (bookmarkMatch.groupValues.getOrNull(12) ?: "100.0"), 
                "highResScaleFactor" to (bookmarkMatch.groupValues.getOrNull(13) ?: "1.3") 
            )
        } else {
            mapOf("pdfPath" to pdfPathToFind, "pageIndex" to "0")
        }

    } catch (e: Exception) {
        
        emptyMap()
    }
}

fun speakLongText(
    tts: TextToSpeech?,
    text: String,
    context: Context? = null,
    forceOnlineMode: Boolean = false
) {
    if (tts == null) return

    var isOnline = true
    if (context != null) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
            isOnline = activeNetwork?.isConnectedOrConnecting == true
        } catch (e: Exception) {
            
        }
    }

    val useOnlineMode = forceOnlineMode && isOnline

    val cleanText = text
        .replace("<[^>]*>".toRegex(), "")
        .replace("&[a-z]+;".toRegex(), "")
        .replace("&", "et")
        .replace("\"", "")
        .replace("'", "'")
        .replace("<", "")
        .replace(">", "")
        .replace("...", ".")
        .replace("ndlr", "")
        .replace(Regex("\\s+"), " ")
        .trim()

    val sentences = cleanText.split(Regex("(?<=[.!?])\\s+"))

    tts.stop()

    if (sentences.isNotEmpty() && sentences[0].isNotBlank()) {
        val utteranceId = if (sentences.size == 1) "FINAL_PART" else "SENTENCE_0"
        tts.speak(sentences[0], TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    var currentIndex = 0

    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    val runnable = object : Runnable {
        override fun run() {
            currentIndex++
            if (currentIndex < sentences.size && sentences[currentIndex].isNotBlank()) {
                val utteranceId = if (currentIndex == sentences.size - 1) "FINAL_PART" else "SENTENCE_$currentIndex"
                tts.speak(sentences[currentIndex], TextToSpeech.QUEUE_ADD, null, utteranceId)
                handler.postDelayed(this, 100)
            }
        }
    }

    handler.postDelayed(runnable, 500)
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
        
        tts?.stop()
        onSpeechStateChange(false)
    } else {

        onSetOcrLu()

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

        Log.d(
            "PRE_GRAY_TTS",
            "Valeur du slider preGrayTTSAdjust avant traitement: $preGrayTTSAdjust"
        )
        Log.d(
            "PRE_GRAY_TTS",
            "originalDisplayBitmap dimensions: ${originalDisplayBitmap?.width}x${originalDisplayBitmap?.height}"
        )

        val preprocessedBitmap = if (preGrayTTSAdjust != 0.0f) {
            applyPreGrayAdjustment(originalDisplayBitmap, preGrayTTSAdjust)
        } else {
            originalDisplayBitmap
        }

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

            OcrProcessor.extractTextFromRectangle(preprocessedBitmap, rect, 0) { extractedText ->
                collectedText.appendLine(extractedText)
                pending--

                if (pending == 0) {
                    val finalText = cleanOcrTextForTts(collectedText.toString())

                    if (pending == 0) {
                        val finalText = cleanOcrTextForTts(collectedText.toString())

                        if (finalText.isNotBlank()) {
                            onTextProcessed(finalText)
                            onSpeechStateChange(true)

                            if (detectedTtsLocale == null) {
                                detectLanguageAndSetTts(finalText, tts) { locale ->
                                    onLocaleDetected(locale)
                                    tts?.language = locale
                                    tts?.setSpeechRate(speechRate)
                                    onPageAdvanceReset()
                                    speakLongText(tts, finalText, context = null)
                                }
                            } else {
                                tts?.language = detectedTtsLocale
                                tts?.setSpeechRate(speechRate)
                                onPageAdvanceReset()
                                speakLongText(tts, finalText, context = null)
                            }
                        } else {
                            
                            onOcrEmptyWarning?.invoke(true)
                            onSpeechStateChange(false)
                        }
                    }
                }
            }
        }
    }

}

    @Composable
    fun FlipScreenButton() {
        
        val (isFlipped, setIsFlipped) = remember { mutableStateOf(false) }
        val context = LocalContext.current

        IconButton(onClick = {
            val newState = !isFlipped
            setIsFlipped(newState)

            val activity = context as? ComponentActivity
            activity?.let {
                if (newState) {
                    
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                } else {
                    
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
        }) {
            Icon(
                imageVector = Icons.Default.ScreenRotation, 
                contentDescription = if (isFlipped)
                    "Retour à l'orientation normale"
                else "Retourner l'écran (180°)",
                tint = if (isFlipped) Color.Red else Color.White
            )
        }
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
                    
                    onAutoPlayEnabledChange(false)
                    
                    onSelectedRectIndicesChange(emptySet())
                }
            }
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
            
            return
        }

        onSetOcrLu.invoke()

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