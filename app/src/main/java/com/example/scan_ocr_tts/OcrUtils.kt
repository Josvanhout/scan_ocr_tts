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


        // Détection très tolérante d’un bloc avant "siècle"
        .replace(Regex("\\b([A-Za-z*]{2,8})\\s*(?:e|ᵉ|°|º)?\\s+si[èe]cle\\b", RegexOption.IGNORE_CASE)) { m ->
            val raw = m.groupValues[1]


                .replace(Regex("([xivlcdm])e$", RegexOption.IGNORE_CASE), "$1")  // "xie" → "xi"
                .replace(Regex("([xivlcdm])r$", RegexOption.IGNORE_CASE), "$1")  // "xir" → "xi"

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
    preGrayTTSAdjust: Float,
    useHighRes: Boolean
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
            "preGrayTTSAdjust" to preGrayTTSAdjust,
            "useHighRes" to useHighRes
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
            bookmarksJson.append("      \"preGrayTTSAdjust\": ${bookmark["preGrayTTSAdjust"]},\n")
            bookmarksJson.append("      \"useHighRes\": ${bookmark["useHighRes"]}\n")
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
                "\\s*\"preGrayTTSAdjust\"\\s*:\\s*([\\d.Ee+-]+)\\s*," +  // ← Ajouter \\s*, à la fin
                "\\s*\"useHighRes\"\\s*:\\s*(true|false|null)").toRegex()


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
                "useHighRes" to (bookmarkMatch.groupValues.getOrNull(9) ?: "null")
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
        // val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
//        val options = TextRecognizerOptions.Builder()
//            .setEntityMode(TextRecognizerOptions.ENTITY_MODE_NONE)
//            .build()
//        val recognizer = TextRecognition.getClient(options)

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

            OcrProcessor.extractTextFromRectangle(preprocessedBitmap, rect, 0) { extractedText ->
                collectedText.appendLine(extractedText)
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
                    } else {
                        Log.d("PRE_GRAY_TTS", "OCR a retourné du texte vide")
                        onOcrEmptyWarning?.invoke(true)
                        onSpeechStateChange(false)
                    }
                }
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


fun speakLongText(
    tts: TextToSpeech?,
    text: String,
    context: Context? = null,
    forceOnlineMode: Boolean = false
) {
    if (tts == null) return

    // Vérifier la connectivité
    var isOnline = true
    if (context != null) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
            isOnline = activeNetwork?.isConnectedOrConnecting == true
        } catch (e: Exception) {
            Log.e("TTS_DEBUG", "Erreur vérification réseau", e)
        }
    }

    // Décider du mode : offline sauf si forceOnlineMode ET réseau disponible
    val useOnlineMode = forceOnlineMode && isOnline

    Log.d("TTS_DEBUG", "Mode: ${if (useOnlineMode) "ONLINE (forcé)" else "OFFLINE (par défaut)"}")

    if (useOnlineMode) {
        Log.d("TTS_DEBUG", "Mode online demandé mais non implémenté, utilisation offline")
    }

    Log.d("TTS_DEBUG", "=== DÉBUT speakLongText (sans SSML) ===")
    Log.d("TTS_DEBUG", "Texte d'entrée (${text.length} chars): ${text.take(100)}...")

    // 1. NETTOYAGE DE BASE (COMME dans cleanOcrTextForTts mais adapté)
    val cleanText = text
        // Enlever toute balise XML résiduelle
        .replace("<[^>]*>".toRegex(), "")
        // Enlever les entités HTML
        .replace("&[a-z]+;".toRegex(), "")
        // Échappement des caractères spéciaux problématiques
        .replace("&", "et")
        .replace("\"", "")
        .replace("'", "'")
        .replace("<", "")
        .replace(">", "")
        // Remplacements spécifiques pour meilleure lecture
        .replace("...", ".")
        .replace("ndlr", "")
        // Normalisation des espaces
        .replace(Regex("\\s+"), " ")
        .trim()

    Log.d("TTS_DEBUG", "Texte nettoyé (${cleanText.length} chars): ${cleanText.take(100)}...")

    // 2. DIVISION EN PHRASES POUR PAUSES NATURELLES
    // Séparer par ponctuation de fin de phrase
    val sentences = cleanText.split(Regex("(?<=[.!?])\\s+"))

    Log.d("TTS_DEBUG", "Nombre de phrases: ${sentences.size}")

    // 3. ARRÊTER TOUTE LECTURE EN COURS
    tts.stop()
    Thread.sleep(100)

    // 4. LIRE CHAQUE PHRASE AVEC PAUSE ENTRE ELLES
    sentences.forEachIndexed { index, sentence ->
        // Ignorer les phrases vides
        if (sentence.isBlank()) return@forEachIndexed

        val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = if (index == sentences.size - 1) "FINAL_PART" else "SENTENCE_$index"

        // Parler la phrase
        val result = tts.speak(sentence, mode, null, utteranceId)
        Log.d("TTS_DEBUG", "Phrase $index: résultat=$result, '${sentence.take(30)}...'")

        // Ajouter une pause naturelle entre les phrases (sauf la dernière)
        if (index < sentences.size - 1) {
            // Pause plus courte que 500ms pour fluidité
            Thread.sleep(300)
        }
    }

    Log.d("TTS_DEBUG", "=== FIN speakLongText (sans SSML) ===")
}

fun speakLongText1(
    tts: TextToSpeech?,
    text: String,
    context: Context? = null,
    forceOnlineMode: Boolean = false  // ← NOUVEAU paramètre
) {
    if (tts == null) return

    // Vérifier la connectivité
    var isOnline = true
    if (context != null) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
            isOnline = activeNetwork?.isConnectedOrConnecting == true
        } catch (e: Exception) {
            Log.e("TTS_DEBUG", "Erreur vérification réseau", e)
        }
    }

    // Décider du mode : offline sauf si forceOnlineMode ET réseau disponible
    val useOnlineMode = forceOnlineMode && isOnline

    Log.d("TTS_DEBUG", "Mode: ${if (useOnlineMode) "ONLINE (forcé)" else "OFFLINE (par défaut)"}")

    if (useOnlineMode) {
        // ONLINE : Appeler votre ancienne fonction SSML (à recréer)
        // speakLongTextOriginal(tts, text) // À implémenter si nécessaire
        Log.d("TTS_DEBUG", "Mode online demandé mais non implémenté, utilisation offline")
    }

    // TOUJOURS utiliser offline par défaut (votre code actuel)
    // Nettoyer mais garder la ponctuation pour les pauses
    val cleanText = text
        .replace("<[^>]*>".toRegex(), "") // Enlever balises XML
        .replace("&[a-z]+;".toRegex(), "") // Enlever entités HTML
        .replace(Regex("\\s+"), " ")
        .trim()

    // Diviser en phrases pour ajouter des pauses
    val sentences = cleanText.split(Regex("(?<=[.!?])\\s+"))

    Log.d("TTS_DEBUG", "Nombre de phrases: ${sentences.size}")

    // Parler chaque phrase avec pause entre elles
    sentences.forEachIndexed { index, sentence ->
        val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = if (index == sentences.size - 1) "FINAL_PART" else "SENTENCE_$index"

        // Parler la phrase
        val result = tts.speak(sentence, mode, null, utteranceId)
        Log.d("TTS_DEBUG", "Phrase $index: résultat=$result, '${sentence.take(30)}...'")

        // Ajouter une pause APRÈS la phrase (sauf la dernière)
        if (index < sentences.size - 1) {
            Thread.sleep(300) // Pause de 300ms entre phrases
        }
    }
}

fun speakLongText2(tts: TextToSpeech?, text: String) {

    Log.d("TTS_DEBUG", "=== speakLongText DÉBUT ===")
    Log.d("TTS_DEBUG", "tts is null: ${tts == null}, text length: ${text.length}")

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
//    val baseText = "\u200B$textWithPauses"
    val baseText = textWithPauses


    Log.d("TTS_DEBUG", "zws chars: '${zws.map { it.code.toString(16) }}'")
    Log.d("TTS_DEBUG", "zws length: ${zws.length}")

// ET AJOUTEZ APRÈS la ligne 'val baseText = "$zws$textWithPauses"' :
    Log.d("TTS_DEBUG", "baseText premiers 10 chars codes: ${baseText.take(10).map { it.code.toString(16) }}")


// 4. Diviser le texte en parties (en coupant après une balise break si possible)
    val maxLength = 500 //3500
    val parts = mutableListOf<String>()
    var remaining = baseText // On utilise baseText au lieu de ssmlText

    // parts.add(remaining)

    Log.d("TTS_DEBUG", "Nombre de parties: ${parts.size}")

    if (remaining.length <= maxLength) {
        parts.add(remaining)
        Log.d("TTS_DEBUG", "Texte court, pas de division nécessaire")

        Log.d("TTS_DEBUG", "baseText.length: ${baseText.length}, maxLength: $maxLength")
        Log.d("TTS_DEBUG", "Condition: baseText.length <= maxLength = ${baseText.length <= maxLength}")

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
//        val prefix = if (index == 0) "\u200B" else ""  // Un seul ZWS
        val prefix = ""

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

        Log.d("TTS_DEBUG", "SSML généré (premiers 200 chars): ${safePart.take(200)}")

        tts.language = Locale("es", "ES")
        Log.d("TTS_DEBUG", "Locale forcée à: ${tts.language}")
//        tts.speak(safePart, queueMode, params, utteranceId)
        val speakResult = tts.speak(safePart, queueMode, params, utteranceId)
        Log.d("TTS_DEBUG", "Résultat tts.speak(): $speakResult (0=SUCCESS, -1=ERROR)")

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